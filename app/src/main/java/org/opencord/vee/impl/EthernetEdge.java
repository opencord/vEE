package org.opencord.vee.impl;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.apache.felix.scr.annotations.Service;
import org.onlab.packet.Ethernet;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.ecord.carrierethernet.api.CarrierEthernetPacketNodeService;
import org.onosproject.ecord.carrierethernet.app.CarrierEthernetForwardingConstruct;
import org.onosproject.ecord.carrierethernet.app.CarrierEthernetNetworkInterface;
import org.onosproject.ecord.carrierethernet.app.CarrierEthernetUni;
import org.onosproject.event.AbstractListenerManager;
import org.onosproject.net.DeviceId;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.flow.criteria.Criteria;
import org.onosproject.net.flow.criteria.Criterion;
import org.onosproject.net.flow.instructions.Instruction;
import org.onosproject.net.flow.instructions.Instructions;
import org.onosproject.net.flowobjective.DefaultFilteringObjective;
import org.onosproject.net.flowobjective.DefaultForwardingObjective;
import org.onosproject.net.flowobjective.DefaultNextObjective;
import org.onosproject.net.flowobjective.FilteringObjective;
import org.onosproject.net.flowobjective.FlowObjectiveService;
import org.onosproject.net.flowobjective.ForwardingObjective;
import org.onosproject.net.flowobjective.NextObjective;
import org.onosproject.net.flowobjective.Objective;
import org.onosproject.net.meter.Band;
import org.onosproject.net.meter.DefaultBand;
import org.onosproject.net.meter.DefaultMeterRequest;
import org.onosproject.net.meter.Meter;
import org.onosproject.net.meter.MeterId;
import org.onosproject.net.meter.MeterRequest;
import org.onosproject.net.meter.MeterService;
import org.onosproject.net.topology.TopologyService;
import org.opencord.cordconfig.CordConfigEvent;
import org.opencord.cordconfig.CordConfigListener;
import org.opencord.cordconfig.CordConfigService;
import org.opencord.olt.AccessDeviceEvent;
import org.opencord.olt.AccessDeviceListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents Ethernet Edge, which applies bandwidth profiles and serves as authentication entry point.
 *
 * May also contain CPE functionality later on.
 */
@Service
@Component(immediate = true)
public class EthernetEdge extends AbstractListenerManager<AccessDeviceEvent, AccessDeviceListener>
        implements CarrierEthernetPacketNodeService {

    private final Logger log = LoggerFactory.getLogger(EthernetEdge.class);
    private static final String APP_NAME = "org.opencord.vee";
    private static final int PRIORITY = 50000;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CordConfigService cordConfig;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected FlowObjectiveService flowObjectiveService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected MeterService meterService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected TopologyService topologyService;

    private ApplicationId appId;
    private CordConfigListener cordConfigListener = new InnerConfigListener();
    // FIXME: We don't need to store the submitted meters as we can get them from the service
    private Map<CarrierEthernetForwardingConstruct, Set<Pair<DeviceId, MeterId>>> fcMeterMap = new ConcurrentHashMap<>();
    // Store submitted flow objects as the service does not allow query operations
    private final Map<CarrierEthernetForwardingConstruct, LinkedList<Pair<DeviceId, Objective>>> flowObjectiveMap = new ConcurrentHashMap<>();

    // packet listener
    // listen for incoming & unknown traffic on the access side

    @Activate
    public void activate() {
        appId = coreService.registerApplication(APP_NAME);
        cordConfig.addListener(cordConfigListener);
        log.info("Started");
    }

    @Deactivate
    public void deactivate() {
        cordConfig.removeListener(cordConfigListener);
        log.info("Stopped");
    }

    @Override
    // Applies to UNI
    public void setNodeForwarding(CarrierEthernetForwardingConstruct fc, CarrierEthernetNetworkInterface srcNi, Set<CarrierEthernetNetworkInterface> dstNiSet) {
        if (srcNi == null || dstNiSet.isEmpty()) {
            log.error("There needs to be at least one ingress and one egress NI to set forwarding.");
            return;
        }

        flowObjectiveMap.putIfAbsent(fc, new LinkedList<>());

        createFlowObjectives(fc, srcNi, dstNiSet);
    }

    private void createFlowObjectives(CarrierEthernetForwardingConstruct fc, CarrierEthernetNetworkInterface ingressNi,
                                      Set<CarrierEthernetNetworkInterface> egressNiSet) {

        // Prepare and submit filtering objective
        // FIXME: Do we need to set priority?
        FilteringObjective.Builder filteringObjectiveBuilder = DefaultFilteringObjective.builder()
                .permit()
                .fromApp(appId)
                .withPriority(PRIORITY)
                .withKey(Criteria.matchInPort(ingressNi.cp().port()));

        TrafficTreatment.Builder filterTreatmentBuilder = DefaultTrafficTreatment.builder();

        // In general, nodes would match on the VLAN tag assigned to the EVC/FC
        Criterion filterVlanIdCriterion = Criteria.matchVlanId(fc.vlanId());

        if ((ingressNi.type().equals(CarrierEthernetNetworkInterface.Type.INNI))
                || (ingressNi.type().equals(CarrierEthernetNetworkInterface.Type.ENNI))) {
            // TODO: Check TPID? Also: Is is possible to receive untagged pkts at an INNI/ENNI?
            // Source node of an FC should match on S-TAG if it's an INNI/ENNI
            filterVlanIdCriterion = Criteria.matchVlanId(ingressNi.sVlanId());
            // Translate S-TAG to the one used in the current FC
            filterTreatmentBuilder.setVlanId(fc.vlanId());
        } else if (ingressNi.type().equals(CarrierEthernetNetworkInterface.Type.UNI)) {
            // Source node of an FC should match on CE-VLAN ID (if present) if it's a UNI
            filterVlanIdCriterion = Criteria.matchVlanId(ingressNi.ceVlanId());
            // Obtain related Meter (if it exists) and add it in the treatment in case it may be used
            deviceMeterIdMap.get(fc.id()).forEach(deviceMeterId -> {
                if (deviceMeterId.deviceId().equals(ingressNi.cp().deviceId())) {
                    filterTreatmentBuilder.meter(deviceMeterId.meterId());
                }
            });
            // Push S-TAG of current FC on top of existing CE-VLAN ID
            filterTreatmentBuilder.pushVlan().setVlanId(fc.vlanId());
        }

        filteringObjectiveBuilder.addCondition(filterVlanIdCriterion);

        // Do not add meta if there are no instructions (i.e. if not first)
        if (!(ingressNi.type().equals(CarrierEthernetNetworkInterface.Type.GENERIC))) {
            filteringObjectiveBuilder.withMeta(filterTreatmentBuilder.build());
        }

        flowObjectiveService.filter(ingressNi.cp().deviceId(), filteringObjectiveBuilder.add());
        flowObjectiveMap.get(fc.id()).addFirst(Pair.of(ingressNi.cp().deviceId(), filteringObjectiveBuilder.add()));

        // Prepare and submit next and forwarding objectives
        TrafficSelector fwdSelector = DefaultTrafficSelector.builder()
                .matchVlanId(fc.vlanId())
                .matchInPort(ingressNi.cp().port())
                .matchEthType(Ethernet.TYPE_IPV4)
                .build();

        Integer nextId = flowObjectiveService.allocateNextId();

        NextObjective.Type nextType = egressNiSet.size() == 1 ?
                NextObjective.Type.SIMPLE : NextObjective.Type.BROADCAST;

        // Setting higher priority to fwd/next objectives to bypass filter in case of match conflict in OVS switches
        NextObjective.Builder nextObjectiveBuider = DefaultNextObjective.builder()
                .fromApp(appId)
                .makePermanent()
                .withType(nextType)
                .withPriority(PRIORITY + 1)
                .withMeta(fwdSelector)
                .withId(nextId);

        egressNiSet.forEach(egressNi -> {
            // TODO: Check if ingressNi and egressNi are on the same device?
            TrafficTreatment.Builder nextTreatmentBuilder = DefaultTrafficTreatment.builder();
            // If last NI in FC is not UNI,
            // keep the existing S-TAG - it will be translated at the entrance of the next FC
            if (egressNi.type().equals(CarrierEthernetNetworkInterface.Type.UNI)) {
                nextTreatmentBuilder.popVlan();
            }
            Instruction outInstruction = Instructions.createOutput(egressNi.cp().port());
            nextTreatmentBuilder.add(outInstruction);
            nextObjectiveBuider.addTreatment(nextTreatmentBuilder.build());
        });

        NextObjective nextObjective = nextObjectiveBuider.add();

        // Setting higher priority to fwd/next objectives to bypass filter in case of match conflict in OVS switches
        ForwardingObjective forwardingObjective = DefaultForwardingObjective.builder()
                .fromApp(appId)
                .makePermanent()
                .withFlag(ForwardingObjective.Flag.VERSATILE)
                .withPriority(PRIORITY + 1)
                .withSelector(fwdSelector)
                .nextStep(nextId)
                .add();

        flowObjectiveService.next(ingressNi.cp().deviceId(), nextObjective);
        // Add all NextObjectives at the end of the list so that they will be removed last
        flowObjectiveMap.get(fc.id()).addLast(Pair.of(ingressNi.cp().deviceId(), nextObjective));

        flowObjectiveService.forward(ingressNi.cp().deviceId(), forwardingObjective);
        flowObjectiveMap.get(fc.id()).addFirst(Pair.of(ingressNi.cp().deviceId(), forwardingObjective));
    }

    @Override
    // Applies to ingress ports on EE switch
    public void createBandwidthProfileResources(CarrierEthernetForwardingConstruct fc, CarrierEthernetUni uni) {
        Set<Pair<DeviceId, MeterId>> meters;

        if (fcMeterMap.containsKey(fc)) {
            meters = fcMeterMap.get(fc);
        } else {
            meters = new HashSet<>();
            fcMeterMap.put(fc, meters);
        }

        meters.addAll(submitMeters(uni));
    }

    /**
     * Creates and submits a set of meter requests based on the UNI bandwidth profile, and returns the set of submitted meters.
     *
     * @param uni the CE UNI
     * @return set of pairs (device ID, meter ID)
     */
    private Set<Pair<DeviceId, MeterId>> submitMeters(CarrierEthernetUni uni) {
        Set<Pair<DeviceId, MeterId>> meters = new HashSet();

        uni.bwps().forEach( bwp -> {
            long longCir = (long) (uni.bwp().cir().bps() / 8000);
            long longEir = (long) (uni.bwp().eir().bps() / 8000);

            MeterRequest.Builder meterRequestBuilder;
            Meter meter;
            Band.Builder bandBuilder;

            Set<Band> bandSet = new HashSet<>();

            // If EIR is zero do not create the REMARK meter
            if (longEir != 0) {
                // Mark frames that exceed CIR as Best Effort
                bandBuilder = DefaultBand.builder()
                        .ofType(Band.Type.REMARK)
                        .withRate(longCir)
                        .dropPrecedence((short) 0);

                if (uni.bwp().cbs() != 0) {
                    bandBuilder.burstSize(uni.bwp().cbs());
                }

                bandSet.add(bandBuilder.build());
            }

            // If CIR is zero do not create the DROP meter
            if (longCir != 0) {
                // Drop all frames that exceed CIR + EIR
                bandBuilder = DefaultBand.builder()
                        .ofType(Band.Type.DROP)
                        .withRate(longCir + longEir);

                if (uni.bwp().cbs() != 0 || uni.bwp().ebs() != 0) {
                    // FIXME: Use CBS and EBS correctly according to MEF specs
                    bandBuilder.burstSize(uni.bwp().cbs() + uni.bwp().ebs());
                }

                bandSet.add(bandBuilder.build());
            }

            // Create meter only if at least one band was created
            if (!bandSet.isEmpty()) {
                meterRequestBuilder = DefaultMeterRequest.builder()
                        // FIXME: figure out how UNI and EE are connected and apply the meter to the EE
                        .forDevice(uni.cp().deviceId())
                        .fromApp(appId)
                        .withUnit(Meter.Unit.KB_PER_SEC)
                        .withBands(bandSet);

                if (uni.bwp().cbs() != 0 || uni.bwp().ebs() != 0) {
                    meterRequestBuilder.burst();
                }

                // Submit meter request and store
                meter = meterService.submit(meterRequestBuilder.add());
                // FIXME: use correct device id

                meters.add(new ImmutablePair<>(uni.cp().deviceId(), meter.id()));
            }
        });

        return meters;
    }

    @Override
    public void applyBandwidthProfileResources(CarrierEthernetForwardingConstruct fc, CarrierEthernetUni uni) {
        // Intentional no-op, meters are installed together with the flow in setNodeForwarding()
        // QoS enforcement is done on Ethernet Edge, not on UNI
    }

    @Override
    public void removeBandwidthProfileResources(CarrierEthernetForwardingConstruct fc, CarrierEthernetUni uni) {
        if (!fcMeterMap.containsKey(fc)) {
            return;
        }

        Set<Pair<DeviceId, MeterId>> ids = fcMeterMap.get(fc);

        // Rebuild meter request based on existing meter, withdraw the request, and remove it from our internal storage
        ids.stream()
                .map(id -> meterService.getMeter(id.getLeft(), id.getRight()))
                .filter(Objects::nonNull)
                .forEach(meter -> {
                    MeterRequest.Builder request = DefaultMeterRequest.builder()
                            .fromApp(meter.appId())
                            .forDevice(meter.deviceId())
                            .withUnit(meter.unit())
                            .withBands(meter.bands());
                    if (uni.bwp().cbs() != 0 || uni.bwp().ebs() != 0) {
                        request.burst();
                    }
                    meterService.withdraw(request.remove(), meter.id());

                    ids.remove(new ImmutablePair<>(meter.deviceId(), meter.id()));
                });
    }

    @Override
    public void removeAllForwardingResources(CarrierEthernetForwardingConstruct fc) {
        List<Pair<DeviceId, Objective>> flowObjectives = flowObjectiveMap.remove(fc);

        // TODO: filter also on elements of pair
        flowObjectives.stream()
                .filter(Objects::nonNull)
                .forEach(pair -> {
                    flowObjectiveService.apply(pair.getLeft(), pair.getRight().copy().remove());
        });
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
