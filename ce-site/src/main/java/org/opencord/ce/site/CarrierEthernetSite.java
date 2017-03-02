package org.opencord.ce.site;


import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.apache.felix.scr.annotations.Service;
import org.onlab.packet.VlanId;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.DefaultLink;
import org.onosproject.net.Device;
import org.onosproject.net.Link;
import org.onosproject.net.Path;
import org.onosproject.net.config.ConfigFactory;
import org.onosproject.net.config.NetworkConfigEvent;
import org.onosproject.net.config.NetworkConfigListener;
import org.onosproject.net.config.NetworkConfigRegistry;
import org.onosproject.net.config.NetworkConfigService;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.provider.ProviderId;
import org.onosproject.net.topology.PathService;
import org.onosproject.net.topology.TopologyService;
import org.opencord.ce.api.models.CarrierEthFcId;
import org.opencord.ce.api.models.CarrierEthernetConnection;
import org.opencord.ce.api.models.CarrierEthernetForwardingConstruct;
import org.opencord.ce.api.models.CarrierEthernetGenericNi;
import org.opencord.ce.api.models.CarrierEthernetLogicalTerminationPoint;
import org.opencord.ce.api.models.CarrierEthernetNetworkInterface;
import org.opencord.ce.api.models.CarrierEthernetSpanningTreeWeight;
import org.opencord.ce.api.models.CarrierEthernetUni;
import org.opencord.ce.api.models.CarrierEthernetVirtualConnection;
import org.opencord.ce.api.models.PortVlanConfig;
import org.opencord.ce.api.services.CarrierEthernetService;
import org.opencord.ce.api.services.VcpeService;
import org.opencord.ce.api.services.VeeService;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static org.onosproject.net.DefaultEdgeLink.createEdgeLink;
import static org.onosproject.net.config.basics.SubjectFactories.CONNECT_POINT_SUBJECT_FACTORY;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Carrier Ethernet provisioner of connectivity for forwarding constructs and bandwidth profiles.
 */
@Component(immediate = true)
@Service
public class CarrierEthernetSite implements CarrierEthernetService {

    private final Logger log = getLogger(getClass());

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected PathService pathService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected TopologyService topologyService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected VcpeService vcpeService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected VeeService veeService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected DeviceService deviceService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected NetworkConfigService networkConfigService;

    // Keeps track of the next S-VLAN tag the app will try to use
    private static short nextVlanId = 1;


    // TODO: Implement distributed store for Forwarding Constructs
    // The installed Forwarding Constructs
    private final Map<CarrierEthFcId, CarrierEthernetForwardingConstruct> fcMap = new ConcurrentHashMap<>();


    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected NetworkConfigRegistry cfgRegistry;

    private final List<ConfigFactory<?, ?>> factories = ImmutableList.of(
            new ConfigFactory<ConnectPoint, PortVlanConfig>(CONNECT_POINT_SUBJECT_FACTORY,
                    PortVlanConfig.class, PortVlanConfig.CONFIG_KEY) {
                @Override
                public PortVlanConfig createConfig() {
                    return new PortVlanConfig();
                }
            });

    // Map of connect points and corresponding VLAN tag
    private final Map<ConnectPoint, VlanId> portVlanMap = new ConcurrentHashMap<>();

    private NetworkConfigListener netcfgListener = new InternalNetworkConfigListener();

    @Activate
    protected void activate() {
        // TODO: rules to redirect for untagged traffic to controller?
        networkConfigService.addListener(netcfgListener);
        factories.forEach(cfgRegistry::registerConfigFactory);
    }

    @Deactivate
    protected void deactivate() {
        networkConfigService.removeListener(netcfgListener);
        factories.forEach(cfgRegistry::unregisterConfigFactory);
        removeAllFcs();
    }

    @Override
    public CarrierEthernetForwardingConstruct getFc(CarrierEthFcId fcId) {
        return ((fcMap.get(fcId) == null) ? null : fcMap.get(fcId));
    }

    @Override
    public Map<CarrierEthFcId, CarrierEthernetForwardingConstruct> fcMap() {
        return ImmutableMap.copyOf(fcMap);
    }


    @Override
    public void setupConnectivity(CarrierEthernetForwardingConstruct fc) {

        boolean allPairsConnected = true;

        HashMap<CarrierEthernetNetworkInterface, HashSet<CarrierEthernetNetworkInterface>> ingressEgressNiMap =
                new HashMap<>();

        // Temporary set for iterating through LTP pairs
        Set<CarrierEthernetLogicalTerminationPoint> tempLtpSet = new HashSet<>(fc.ltpSet());

        // Temporary set for indicating which LTPs were finally included
        Set<CarrierEthernetLogicalTerminationPoint> usedLtpSet = new HashSet<>();

        Iterator<CarrierEthernetLogicalTerminationPoint> ltpIt1 = tempLtpSet.iterator();
        while (ltpIt1.hasNext()) {

            CarrierEthernetLogicalTerminationPoint ltp1 = ltpIt1.next();

            // Iterate through all the remaining NIs
            Iterator<CarrierEthernetLogicalTerminationPoint> ltpIt2 = tempLtpSet.iterator();
            while (ltpIt2.hasNext()) {

                CarrierEthernetLogicalTerminationPoint ltp2 = ltpIt2.next();

                // Skip equals
                if (ltp1.equals(ltp2)) {
                    continue;
                }

                // Do not establish connectivity between leaf NIs (applies to Rooted_Multipoint)
                // FIXME: Use proper LTP roles
                if (ltp1.role().equals(CarrierEthernetLogicalTerminationPoint.Role.LEAF)
                        && ltp2.role().equals(CarrierEthernetLogicalTerminationPoint.Role.LEAF)) {
                    continue;
                }

                // Update the ingress-egress NI map based on the calculated paths
                if (!updateIngressEgressNiMap(ltp1.ni(), ltp2.ni(), ingressEgressNiMap,
                        fc.congruentPaths(), fc.type())) {
                    allPairsConnected = false;
                    continue;
                }

                // Indicate that connection for at least one NI pair has been established
                fc.setState(CarrierEthernetForwardingConstruct.State.ACTIVE);

                // Add NIs to the set of NIs used by the EVC
                usedLtpSet.add(ltp1);
                usedLtpSet.add(ltp2);
            }
            // Remove NI from temporary set so that each pair is visited only once
            ltpIt1.remove();
        }

        // Establish connectivity using the ingressEgressNiMap
        ingressEgressNiMap.keySet().forEach(srcNi -> {
            // Set forwarding only on packet switches
            if (deviceService.getDevice(srcNi.cp().deviceId()).type().equals(Device.Type.SWITCH)) {
                vcpeService.setNodeForwarding(fc, srcNi, ingressEgressNiMap.get(srcNi));
                veeService.setNodeForwarding(fc, srcNi, ingressEgressNiMap.get(srcNi));
            }
        });

        // Update the NI set, based on the NIs actually used
        fc.setLtpSet(usedLtpSet);

        if (fc.isActive()) {
            if (!allPairsConnected) {
                fc.setState(CarrierEthernetConnection.State.PARTIAL);
            }
        }
    }

    /**
     * Select feasible link paths between two NIs in both directions and update
     * ingressEgressNiMap accordingly.
     *
     * @param ni1                the first NI
     * @param ni2                the second NI
     * @param ingressEgressNiMap the method will add here any ingress-egress NI associations
     * @param congruentPaths     if true indicates that n1->n2 will follow the same path as n2->n1
     * @return true if the path was updated and false if a path could not be found in any of the directions
     */
    private boolean updateIngressEgressNiMap(CarrierEthernetNetworkInterface ni1, CarrierEthernetNetworkInterface ni2,
                                             HashMap<CarrierEthernetNetworkInterface,
                                                     HashSet<CarrierEthernetNetworkInterface>> ingressEgressNiMap,
                                             boolean congruentPaths, CarrierEthernetVirtualConnection.Type evcType) {

        // Find the paths for both directions at the same time, so that we can skip the pair if needed
        List<Link> forwardLinks = generateLinkList(ni1.cp(), ni2.cp(), evcType);
        List<Link> backwardLinks =
                congruentPaths ? generateInverseLinkList(forwardLinks) : generateLinkList(ni2.cp(), ni1.cp(), evcType);

        // Skip this UNI pair if no feasible path could be found
        if (forwardLinks == null || (backwardLinks == null)) {
            log.warn("There are no feasible paths between {} and {}.",
                    ni1.cp().deviceId(), ni2.cp().deviceId());
            return false;
        }

        // Populate the ingress/egress NI map for the forward and backward paths
        populateIngressEgressNiMap(ni1, ni2, forwardLinks, ingressEgressNiMap);
        populateIngressEgressNiMap(ni2, ni1, backwardLinks, ingressEgressNiMap);

        return true;
    }

    private void populateIngressEgressNiMap(CarrierEthernetNetworkInterface srcNi,
                                            CarrierEthernetNetworkInterface dstNi,
                                            List<Link> linkList,
                                            HashMap<CarrierEthernetNetworkInterface,
                                                    HashSet<CarrierEthernetNetworkInterface>> ingressEgressNiMap
    ) {
        // FIXME: Fix the method - avoid generating GENERIC NIs if not needed
        // Add the src and destination NIs as well as the associated Generic NIs
        ingressEgressNiMap.putIfAbsent(srcNi, new HashSet<>());
        // Add last hop entry only if srcNi, dstNi aren't on same device (in which case srcNi, ingressNi would coincide)
        if (!srcNi.cp().deviceId().equals(dstNi.cp().deviceId())) {
            // If srcNi, dstNi are not on the same device, create mappings to/from new GENERIC NIs
            ingressEgressNiMap.get(srcNi).add(new CarrierEthernetGenericNi(linkList.get(1).src(), null));
            CarrierEthernetGenericNi ingressNi =
                    new CarrierEthernetGenericNi(linkList.get(linkList.size() - 2).dst(), null);
            ingressEgressNiMap.putIfAbsent(ingressNi, new HashSet<>());
            ingressEgressNiMap.get(ingressNi).add(dstNi);
        } else {
            // If srcNi, dstNi are on the same device, this is the only mapping that will be created
            ingressEgressNiMap.get(srcNi).add(dstNi);
        }

        // Go through the links and create/add the intermediate NIs
        for (int i = 1; i < linkList.size() - 2; i++) {
            CarrierEthernetGenericNi ingressNi = new CarrierEthernetGenericNi(linkList.get(i).dst(), null);
            ingressEgressNiMap.putIfAbsent(ingressNi, new HashSet<>());
            ingressEgressNiMap.get(ingressNi).add(new CarrierEthernetGenericNi(linkList.get(i + 1).src(), null));
        }
    }

    private List<Link> generateLinkList(ConnectPoint cp1, ConnectPoint cp2,
                                        CarrierEthernetVirtualConnection.Type evcType) {
        Set<Path> paths;
        Path path = null;

        if (!cp1.deviceId().equals(cp2.deviceId())) {
            // If cp1 and cp2 are not on the same device a path must be found
            if (evcType.equals(CarrierEthernetVirtualConnection.Type.POINT_TO_POINT)) {
                // For point-to-point connectivity use pre-calculated paths to make sure the shortest paths are chosen
                paths = pathService.getPaths(cp1.deviceId(), cp2.deviceId());
            } else {
                // Recalculate path so that it's over the pre-calculated spanning tree
                // FIXME: Find a more efficient way (avoid recalculating paths)
                paths = pathService.getPaths(cp1.deviceId(), cp2.deviceId(),
                        new CarrierEthernetSpanningTreeWeight(topologyService));
            }

            // Just select any of the returned paths
            // TODO: Select path in more sophisticated way and return null if any of the constraints cannot be met
            path = paths.iterator().hasNext() ? paths.iterator().next() : null;

            if (path == null) {
                return null;
            }
        }

        List<Link> links = new ArrayList<>();
        links.add(createEdgeLink(cp1, true));
        if (!cp1.deviceId().equals(cp2.deviceId())) {
            links.addAll(path.links());
        }
        links.add(createEdgeLink(cp2, false));

        return links;
    }

    private List<Link> generateInverseLinkList(List<Link> originalLinks) {

        if (originalLinks == null) {
            return null;
        }

        List<Link> inverseLinks = new ArrayList<>();

        inverseLinks.add(createEdgeLink(originalLinks.get(originalLinks.size() - 1).src(), true));

        for (int i = originalLinks.size() - 2; i > 0; i--) {
            // FIXME: Check again the Link creation parameters
            inverseLinks.add(DefaultLink.builder()
                    .src(originalLinks.get(i).dst())
                    .dst(originalLinks.get(i).src())
                    .type(Link.Type.DIRECT)
                    .providerId(new ProviderId("none", "none"))
                    .build());
        }
        inverseLinks.add(createEdgeLink(originalLinks.get(0).dst(), false));

        return inverseLinks;
    }

    @Override
    public void removeConnectivity(CarrierEthernetForwardingConstruct fc) {
        veeService.removeAllForwardingResources(fc);
        vcpeService.removeAllForwardingResources(fc);
    }

    @Override
    public void createBandwidthProfiles(CarrierEthernetForwardingConstruct fc) {
        fc.uniSet().forEach(uni -> {
            vcpeService.createBandwidthProfileResources(fc, uni);
            veeService.createBandwidthProfileResources(fc, uni);
        });
    }

    @Override
    public void applyBandwidthProfiles(CarrierEthernetForwardingConstruct fc) {
        //  TODO: Select node manager depending on device protocol
        fc.uniSet().forEach(uni -> {
            vcpeService.applyBandwidthProfileResources(fc, uni);
            veeService.applyBandwidthProfileResources(fc, uni);
        });
    }

    @Override
    public void removeBandwidthProfiles(CarrierEthernetForwardingConstruct fc) {
        //  TODO: Select node manager depending on device protocol
        fc.ltpSet().forEach((ltp -> {
            if (ltp.ni().type().equals(CarrierEthernetNetworkInterface.Type.UNI)) {
                vcpeService.removeBandwidthProfileResources(fc, (CarrierEthernetUni) ltp.ni());
                veeService.removeBandwidthProfileResources(fc, (CarrierEthernetUni) ltp.ni());
            }
        }));
    }

    @Override
    public CarrierEthernetForwardingConstruct updateFc(CarrierEthernetForwardingConstruct fc) {
        // Just checking again
        CarrierEthFcId fcId = new CarrierEthFcId(fc.id());
        if (fcMap.containsKey(fcId)) {
            log.info("Updating existing FC {}", fc.id());
            // Keep the VLAN ID of the original FC
            fc.setVlanId(fcMap.get(fcId).vlanId());
            // FIXME: Currently FC update only possible for standalone FCs
            removeFc(fcId);
        }
        return installFc(fc);
    }

    @Override
    public void removeAllFcs() {
        fcMap.keySet().forEach(fcId -> removeFc(fcId));
    }

    @Override
    public CarrierEthernetForwardingConstruct removeFc(CarrierEthFcId fcId) {
        if (fcMap.containsKey(fcId)) {
            CarrierEthernetForwardingConstruct fc = fcMap.get(fcId);
            if (fc.refCount().get() != 0) {
                log.warn("Could not remove FC {}: RefCount is not zero", fc.id());
                return null;
            }
            removeConnectivity(fc);
            removeBandwidthProfiles(fc);
            // Avoid excessively incrementing FC VLAN ids
            nextVlanId = (fcMap.get(fcId).vlanId().toShort() < nextVlanId ?
                    fcMap.get(fcId).vlanId().toShort() :
                    nextVlanId);
            fcMap.remove(fcId);
            return fc;
        }
        return null;
    }

    @Override
    public CarrierEthernetForwardingConstruct installFc(CarrierEthernetForwardingConstruct fc) {

        // If FC already exists, remove it and reestablish with new parameters
        if (fc.id() != null && fcMap.containsKey(new CarrierEthFcId(fc.id()))) {
            return updateFc(fc);
        } else {
            fc.setId(null);
        }

        // Create BW profiles first so that they will be available if needed during the connectivity phase
        createBandwidthProfiles(fc);

        setupConnectivity(fc);

        // If connectivity was not successful, then do not register the FC and do not apply BW profiles
        // If not, the BW profiles that were created earlier need to be removed
        if (fc.state().equals(CarrierEthernetForwardingConstruct.State.ACTIVE)) {
            // Apply BWP-related resources (e.g. Meters) to the packet switches
            applyBandwidthProfiles(fc);


            fcMap.put(new CarrierEthFcId(fc.id()), fc);
        } else {
            removeBandwidthProfiles(fc);
        }

        return fc;
    }

    /**
     * Returns the unique S-TAGs currently used by FCs across the CE network.
     *
     * @return the S-TAGs currently used
     */
    private Set<VlanId> usedVlans() {
        return fcMap.values().stream().map(CarrierEthernetForwardingConstruct::vlanId)
                .collect(Collectors.toSet());
    }

    /**
     * Generates a new vlanId excluding the provided ones.
     *
     * @param excludedVlans the vlanIds that are not allowed
     * @return the generated vlanId; null if none found
     */
    private VlanId generateVlanId(Set<VlanId> excludedVlans) {
        // If all vlanIds are being used return null, else try to find the next available one
        if (excludedVlans.size() <  VlanId.MAX_VLAN - 1) {
            while (excludedVlans.contains(VlanId.vlanId(nextVlanId))) {
                // Get next valid short
                nextVlanId = (nextVlanId >= VlanId.MAX_VLAN || nextVlanId <= 0 ?
                        1 : (short) (nextVlanId + 1));
            }
        }
        return excludedVlans.contains(VlanId.vlanId(nextVlanId)) ?
                null : VlanId.vlanId(nextVlanId);
    }

    /**
     * Generates a unique FC id in the context of the CE app.
     *
     * @param fc the FC representation
     * @return the generated FC id or null if none found
     */
    private String generateFcId(CarrierEthernetForwardingConstruct fc) {

        // TODO: Add different connectivity types

        return "FC-" + fc.vlanId().toString();
    }


    private class InternalNetworkConfigListener implements NetworkConfigListener {

        /**
         * Negative events.
         */
        private final EnumSet<NetworkConfigEvent.Type> negative
                = EnumSet.of(NetworkConfigEvent.Type.CONFIG_UNREGISTERED,
                NetworkConfigEvent.Type.CONFIG_REMOVED);

        /**
         * Actual configuration events.
         */
        private final EnumSet<NetworkConfigEvent.Type> actualConfig
                = EnumSet.of(NetworkConfigEvent.Type.CONFIG_ADDED,
                NetworkConfigEvent.Type.CONFIG_REMOVED,
                NetworkConfigEvent.Type.CONFIG_UPDATED);

        @Override
        public boolean isRelevant(NetworkConfigEvent event) {
            return event.configClass().equals(PortVlanConfig.class) &&
                    actualConfig.contains(event.type());
        }

        @Override
        public void event(NetworkConfigEvent event) {

            if (!isRelevant(event)) {
                return;
            }

            ConnectPoint cp = (ConnectPoint) event.subject();
            PortVlanConfig config = networkConfigService.getConfig(cp, PortVlanConfig.class);

            if (config == null) {
                log.info("VLAN tag config is removed from port {}", cp);
                portVlanMap.remove(cp);
                return;
            }

            if (config.portVlanId().isPresent() && !negative.contains(event.type())) {
                VlanId assignedVlan = config.portVlanId().get();
                if (usedVlans().contains(assignedVlan)) {
                    log.warn("VLAN tag {} is already used in the CE network", assignedVlan);
                } else {
                    log.info("VLAN tag {} is assigned to port {}", assignedVlan, cp);
                    portVlanMap.put(cp, assignedVlan);
                }
            } else {
                log.info("VLAN tag is removed from port {}", cp);
                portVlanMap.remove(cp);
            }
        }
    }

}

