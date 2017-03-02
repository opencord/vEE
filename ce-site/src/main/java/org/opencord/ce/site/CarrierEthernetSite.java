package org.opencord.ce.site;


import com.google.common.collect.ImmutableMap;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.apache.felix.scr.annotations.Service;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.DefaultLink;
import org.onosproject.net.Device;
import org.onosproject.net.Link;
import org.onosproject.net.Path;
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
import org.opencord.ce.api.services.CarrierEthernetService;
import org.opencord.ce.api.services.VcpeService;
import org.opencord.ce.api.services.VeeService;
import org.opencord.cordconfig.CordConfigEvent;
import org.opencord.cordconfig.CordConfigListener;
import org.opencord.cordconfig.CordConfigService;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.onosproject.net.DefaultEdgeLink.createEdgeLink;
import static org.slf4j.LoggerFactory.getLogger;
import static org.opencord.ce.api.models.CarrierEthernetVirtualConnection.Type;
import static org.opencord.ce.api.models.CarrierEthernetForwardingConstruct.State;

/**
 * Carrier Ethernet provisioner of connectivity for forwarding constructs and bandwidth profiles.
 */
@Component(immediate = true)
@Service
public class CarrierEthernetSite implements CarrierEthernetService {

    private final Logger log = getLogger(getClass());

    // Keeps track of the next S-VLAN tag the app will try to use
    private static short nextVlanId = 1;

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
    protected CordConfigService cordConfig;

    private CordConfigListener cordConfigListener = new InnerConfigListener();

    // TODO: Implement distributed store for Forwarding Constructs
    // The installed Forwarding Constructs
    private final Map<CarrierEthFcId, CarrierEthernetForwardingConstruct> fcMap = new ConcurrentHashMap<>();

    @Activate
    protected void activate() {
        // TODO: start packet intercept for untagged traffic?
        cordConfig.addListener(cordConfigListener);
    }

    @Deactivate
    protected void deactivate() {
        cordConfig.removeListener(cordConfigListener);
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
                // TODO: (FRA) call here the Fabric control Service ??
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
                                             boolean congruentPaths, Type evcType) {

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
                                        Type evcType) {
        Set<Path> paths;
        Path path = null;

        if (!cp1.deviceId().equals(cp2.deviceId())) {
            // If cp1 and cp2 are not on the same device a path must be found
            if (evcType.equals(Type.POINT_TO_POINT)) {
                // For point-to-point connectivity use pre-calculated paths to make sure the shortest paths are chosen
                paths = pathService.getPaths(cp1.deviceId(), cp2.deviceId());
            } else {
                // Recalculate path so that it's over the pre-calculated spanning tree
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
            veeService.createBandwidthProfileResources(fc, uni);
        });
    }

    @Override
    public void applyBandwidthProfiles(CarrierEthernetForwardingConstruct fc) {
        //  TODO: Select node manager depending on device protocol
        fc.uniSet().forEach(uni -> {
            veeService.applyBandwidthProfileResources(fc, uni);
        });
    }

    @Override
    public void removeBandwidthProfiles(CarrierEthernetForwardingConstruct fc) {
        //  TODO: Select node manager depending on device protocol
        fc.ltpSet().forEach((ltp -> {
            if (ltp.ni().type().equals(CarrierEthernetNetworkInterface.Type.UNI)) {
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
            removeFc(fcId);
        }
        return installFc(fc);
    }

    @Override
    public void removeAllFcs() {
        fcMap.keySet().forEach(this::removeFc);
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
        if (fc.state().equals(State.ACTIVE)) {
            // Apply BWP-related resources (e.g. Meters) to the packet switches
            applyBandwidthProfiles(fc);

            fcMap.put(new CarrierEthFcId(fc.id()), fc);
        } else {
            removeBandwidthProfiles(fc);
        }

        return fc;
    }

    /**
     * Listens for configuration events of access devices.
     */
    private class InnerConfigListener implements CordConfigListener {

        @Override
        public void event(CordConfigEvent event) {
            // install default flow rule on added devices
        }

        @Override
        public boolean isRelevant(CordConfigEvent event) {
            return event.type() == CordConfigEvent.Type.ACCESS_DEVICE_ADDED ||
                    event.type() == CordConfigEvent.Type.ACCESS_DEVICE_REMOVED ||
                    event.type() == CordConfigEvent.Type.ACCESS_DEVICE_UPDATED;
        }
    }
}

