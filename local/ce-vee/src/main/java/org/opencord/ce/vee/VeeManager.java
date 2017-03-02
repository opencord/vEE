package org.opencord.ce.vee;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.apache.felix.scr.annotations.Service;
import org.onlab.packet.Ethernet;
import org.onlab.packet.VlanId;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.DeviceId;
import org.onosproject.net.Link;
import org.onosproject.net.PortNumber;
import org.onosproject.net.driver.Driver;
import org.onosproject.net.driver.DriverService;
import org.onosproject.net.flow.DefaultFlowRule;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.FlowRule;
import org.onosproject.net.flow.FlowRuleService;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.flow.criteria.Criteria;
import org.onosproject.net.flow.criteria.Criterion;
import org.onosproject.net.flow.instructions.Instruction;
import org.onosproject.net.flow.instructions.Instructions;
import org.onosproject.net.flow.instructions.L2ModificationInstruction;
import org.onosproject.net.flowobjective.DefaultFilteringObjective;
import org.onosproject.net.flowobjective.DefaultForwardingObjective;
import org.onosproject.net.flowobjective.DefaultNextObjective;
import org.onosproject.net.flowobjective.FilteringObjective;
import org.onosproject.net.flowobjective.FlowObjectiveService;
import org.onosproject.net.flowobjective.ForwardingObjective;
import org.onosproject.net.flowobjective.NextObjective;
import org.onosproject.net.flowobjective.Objective;
import org.onosproject.net.link.LinkService;
import org.onosproject.net.meter.Band;
import org.onosproject.net.meter.DefaultBand;
import org.onosproject.net.meter.DefaultMeterRequest;
import org.onosproject.net.meter.Meter;
import org.onosproject.net.meter.MeterId;
import org.onosproject.net.meter.MeterRequest;
import org.onosproject.net.meter.MeterService;
import org.opencord.ce.api.models.CarrierEthernetForwardingConstruct;
import org.opencord.ce.api.models.CarrierEthernetNetworkInterface;
import org.opencord.ce.api.models.CarrierEthernetUni;
import org.opencord.ce.api.services.VeeService;

import org.slf4j.Logger;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.slf4j.LoggerFactory.getLogger;
import static org.opencord.ce.api.models.CarrierEthernetNetworkInterface.Type;

/**
 * Class used to control Ethernet Edge nodes according to the OpenFlow (1.3 and above) protocol.
 */
@Component(immediate = true)
@Service(value = VeeService.class)
public class VeeManager implements VeeService {
    private static final int PRIORITY = 50000;
    private static final String APP_NAME = "org.opencord.ce.vee";

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected FlowRuleService flowRuleService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected MeterService meterService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected FlowObjectiveService flowObjectiveService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected LinkService linkService;

    // FIXME slightly better way to detect OF-DPA issues
    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected DriverService drivers;

    private final Logger log = getLogger(getClass());

    private ApplicationId appId;

    // FIXME: We don't need to store the submitted meters as we can get them from the service
    private Map<CarrierEthernetForwardingConstruct, Set<Pair<DeviceId, MeterId>>> fcMeterMap =
            new ConcurrentHashMap<>();
    // Store submitted flow objects as the service does not allow query operations
    private final Map<CarrierEthernetForwardingConstruct, LinkedList<Pair<DeviceId, Objective>>> flowObjectiveMap =
            new ConcurrentHashMap<>();

    private final Map<CarrierEthernetForwardingConstruct, DeviceId> eeDeviceMap = new ConcurrentHashMap<>();

    @Activate
    protected void activate() {
        appId = coreService.registerApplication(APP_NAME);

        // TODO: start packet intercept for untagged traffic?
        log.info("Started");
    }

    @Deactivate
    protected void deactivate() {

    }

    @Override
    public void setNodeForwarding(CarrierEthernetForwardingConstruct fc, CarrierEthernetNetworkInterface ingressNi,
                                  Set<CarrierEthernetNetworkInterface> egressNiSet) {

        if (ingressNi == null || egressNiSet.isEmpty()) {
            log.error("There needs to be at least one ingress and one egress NI to set forwarding.");
            return;
        }
        // if the ingress interface is a UNI, this call is for the provider-managed CPE
        if (ingressNi.type().equals(Type.UNI)) {
            return;
        }

        flowObjectiveMap.putIfAbsent(fc, new LinkedList<>());
        eeDeviceMap.putIfAbsent(fc, ingressNi.cp().deviceId());

        // TODO: Get created FlowObjectives from this method
        createFlowObjectives(fc, ingressNi, egressNiSet);
    }

    /**
     * Creates and submits FlowObjectives into the device vesting the role of Ethernet Edge.
     *
     * @param fc the FC representation
     * @param ingressNi the ingress network interface
     * @param  egressNiSet the set of egress NIs
     */
    private void createFlowObjectives(CarrierEthernetForwardingConstruct fc, CarrierEthernetNetworkInterface ingressNi,
                                      Set<CarrierEthernetNetworkInterface> egressNiSet) {
        DeviceId deviceId = ingressNi.cp().deviceId();
        PortNumber portNumber = ingressNi.cp().port();

        /////////////////////////////////////////
        // Prepare and submit filtering objective
        /////////////////////////////////////////

        FilteringObjective.Builder filterObjBuilder = DefaultFilteringObjective.builder()
                .permit().fromApp(appId)
                .withPriority(PRIORITY)
                .withKey(Criteria.matchInPort(portNumber));

        TrafficTreatment.Builder filterTreatmentBuilder = DefaultTrafficTreatment.builder();

        // In general, nodes would match on the VLAN tag assigned to the EVC/FC
        Criterion filterVlanIdCriterion = Criteria.matchVlanId(fc.vlanId());

        filterObjBuilder.addCondition(filterVlanIdCriterion);

        // Obtain related Meter (if it exists) and add it in the treatment in case it may be used
        fcMeterMap.get(fc).forEach(devMeterPair -> {
            if (devMeterPair.getLeft().equals(deviceId)) {
                filterTreatmentBuilder.meter(devMeterPair.getRight());
            }
        });

        // Do not add meta if there are no instructions (i.e. if not first)
        if (!(ingressNi.type().equals(Type.GENERIC))) {
            filterObjBuilder.withMeta(filterTreatmentBuilder.build());
        }

        flowObjectiveService.filter(deviceId, filterObjBuilder.add());
        flowObjectiveMap.get(fc).addFirst(Pair.of(deviceId, filterObjBuilder.add()));

        ////////////////////////////////////////////////////
        // Prepare and submit next and forwarding objectives
        ////////////////////////////////////////////////////

        TrafficSelector.Builder fwdSelectorBuilder = DefaultTrafficSelector.builder()
                .matchVlanId(fc.vlanId())
                .matchInPort(portNumber);

        if (isOfDpa(deviceId)) {
            // workaround for OF-DPA
            fwdSelectorBuilder.matchEthType(Ethernet.TYPE_IPV4);
        }

        TrafficSelector fwdSelector = fwdSelectorBuilder.build();

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
            Instruction outInstruction = Instructions.createOutput(portNumber);
            nextTreatmentBuilder.add(outInstruction);
            nextObjectiveBuider.addTreatment(nextTreatmentBuilder.build());
        });

        NextObjective nextObjective = nextObjectiveBuider.add();

        // Setting higher priority to fwd/next objectives to bypass filter in case of match conflict in OVS switches
        ForwardingObjective fwdObjective = DefaultForwardingObjective.builder()
                .fromApp(appId)
                .makePermanent()
                .withFlag(ForwardingObjective.Flag.VERSATILE)
                .withPriority(PRIORITY + 1)
                .withSelector(fwdSelector)
                .nextStep(nextId)
                .add();

        flowObjectiveService.next(deviceId, nextObjective);
        // Add all NextObjectives at the end of the list so that they will be removed last
        flowObjectiveMap.get(fc).addLast(Pair.of(deviceId, nextObjective));

        flowObjectiveService.forward(deviceId, fwdObjective);
        flowObjectiveMap.get(fc).addFirst(Pair.of(deviceId, fwdObjective));
    }

    @Override
    public void createBandwidthProfileResources(CarrierEthernetForwardingConstruct fc, CarrierEthernetUni uni) {
        // Create meters and add them to global MeterId map
        Set<Pair<DeviceId, MeterId>> meters;

        if (fcMeterMap.containsKey(fc)) {
            meters = fcMeterMap.get(fc);
        } else {
            meters = new HashSet<>();
            fcMeterMap.put(fc, meters);
        }

        meters.addAll(submitMeters(fc, uni));
    }

    private boolean isOfDpa(DeviceId deviceId) {
        Driver driver = drivers.getDriver(deviceId);
        return driver != null &&
                driver.swVersion().contains("OF-DPA");
    }

    // Maybe we still need this method to later modify the QoS profile of an FC
    @Override
    public void applyBandwidthProfileResources(CarrierEthernetForwardingConstruct fc, CarrierEthernetUni uni) {

        DeviceId deviceId = eeDeviceMap.get(fc);
        if (deviceId == null) {
            log.error("Trying to apply bandwidth profile rules" +
                    "to not existing device/meters map");
            return;
        }

        // Do not apply meters to NETCONF-controlled switches here since they should have been applied in the pipeline
        // FIXME: Is there a better way to check this?
        if (deviceId.uri().getScheme().equals("netconf")) {
            return;
        }

        // Do not apply meters to OFDPA 2.0 switches since they are not currently supported
        if (isOfDpa(deviceId)) {
            return;
        }

        // Get installed flows with the same appId/deviceId with IN_PORT = UNI port which push the FC vlanId
        List<FlowRule> flowRuleList =
                StreamSupport.stream(flowRuleService.getFlowEntries(deviceId).spliterator(), false)
                        .filter(flowRule -> flowRule.appId() == appId.id()
                                && getPushedVlanFromTreatment(flowRule.treatment()).equals(fc.vlanId()))
                        .collect(Collectors.toList());

        // Apply meters to flows
        for (FlowRule flowRule : flowRuleList) {
            // Need to add to the flow the meters associated with the same device
            Set<Pair<DeviceId, MeterId>> deviceMeterIdSet = new HashSet<>();

            fcMeterMap.get(fc).forEach(devMeterPair -> {
                if (devMeterPair.getLeft().equals(flowRule.deviceId())) {
                    deviceMeterIdSet.add(devMeterPair);
                }
            });
            // Modify and submit flow rule only if there are meters to add
            if (!deviceMeterIdSet.isEmpty()) {
                FlowRule newFlowRule = addMetersToFlowRule(flowRule, deviceMeterIdSet);
                flowRuleService.applyFlowRules(newFlowRule);
            }
        }
    }

    private VlanId getPushedVlanFromTreatment(TrafficTreatment treatment) {
        boolean pushVlan = false;
        VlanId pushedVlan = null;
        for (Instruction instruction : treatment.allInstructions()) {
            if (instruction.type().equals(Instruction.Type.L2MODIFICATION)) {
                L2ModificationInstruction l2ModInstr = (L2ModificationInstruction) instruction;
                if (l2ModInstr.subtype().equals(L2ModificationInstruction.L2SubType.VLAN_PUSH)) {
                    pushVlan = true;
                } else if (l2ModInstr.subtype().equals(L2ModificationInstruction.L2SubType.VLAN_ID) && pushVlan) {
                    pushedVlan = ((L2ModificationInstruction.ModVlanIdInstruction) instruction).vlanId();
                }
            }
        }
        return pushedVlan != null ? pushedVlan : VlanId.NONE;
    }


    /**
     * Creates and submits a meter with the required bands for a UNI.
     *
     * @param uni the UNI descriptor
     * @return set of meter ids of the meters created
     */
    private Set<Pair<DeviceId, MeterId>> submitMeters(CarrierEthernetForwardingConstruct fc,
                                                      CarrierEthernetUni uni) {
        Set<Pair<DeviceId, MeterId>> meters = new HashSet<>();
        DeviceId deviceId = getDevice(fc, uni);

        uni.bwps().forEach(bwp -> {
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
                        .forDevice(deviceId)
                        .fromApp(appId)
                        .withUnit(Meter.Unit.KB_PER_SEC)
                        .withBands(bandSet);

                if (uni.bwp().cbs() != 0 || uni.bwp().ebs() != 0) {
                    meterRequestBuilder.burst();
                }

                // Submit meter request and store
                meter = meterService.submit(meterRequestBuilder.add());
                // FIXME: use correct device id

                meters.add(new ImmutablePair<>(deviceId, meter.id()));
            }
        });

        return meters;
    }

    private FlowRule addMetersToFlowRule(FlowRule flowRule,  Set<Pair<DeviceId, MeterId>> deviceMeterIdSet) {

        TrafficTreatment.Builder tBuilder = DefaultTrafficTreatment
                .builder(flowRule.treatment());

        deviceMeterIdSet.forEach(deviceMeterPair ->
            tBuilder.meter(deviceMeterPair.getRight()).
                    transition(flowRule.treatment().tableTransition().tableId()));

        return createFlowRule(flowRule.deviceId(), flowRule.priority(),
                flowRule.selector(), tBuilder.build(), flowRule.tableId());
    }

    @Override
    public void removeBandwidthProfileResources(CarrierEthernetForwardingConstruct fc, CarrierEthernetUni uni) {
        removeMeters(fc, uni);
    }

    /**
     * Removes the meters associated with a specific UNI of an FC.
     *
     * @param fc the forwarding construct
     * @param uni the UNI descriptor
     * */
    private void removeMeters(CarrierEthernetForwardingConstruct fc, CarrierEthernetUni uni) {

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

    /**
     * Removes all flow objectives installed by the application which are associated with a specific FC.
     *
     * @param fc ForwardingConcrtuct object
     */
    @Override
    public void removeAllForwardingResources(CarrierEthernetForwardingConstruct fc) {
        // Note: A Flow Rule cannot be shared by multiple FCs due to different VLAN or CE-VLAN ID match.
        List<Pair<DeviceId, Objective>> flowObjectives = flowObjectiveMap.remove(fc);
        // NextObjectives will be removed after all other Objectives
        // TODO: filter also on elements of pair
        flowObjectives.stream()
                .filter(Objects::nonNull)
                .forEach(pair ->
                        flowObjectiveService.apply(pair.getLeft(), pair.getRight().copy().remove()));
    }

    private FlowRule createFlowRule(DeviceId deviceId, int priority,
                                    TrafficSelector selector, TrafficTreatment treatment, int tableId) {
        return DefaultFlowRule.builder()
                .fromApp(appId)
                .forDevice(deviceId)
                .makePermanent()
                .withPriority(priority)
                .withSelector(selector)
                .withTreatment(treatment)
                .forTable(tableId)
                .build();
    }

    /**
     * !! Caution !! This method is strictly tied to the expected topology
     * view of this {@link VeeService} implementation.
     * The UNI is expected to be in the CPE node. The CPE node shall be connected only
     * with the EE device (exception are multi path home gateway).
     *
     * @param fc forwarding construct
     * @param uni User to Network Interface
     * @return the EE device ID
     */
    private DeviceId getDevice(CarrierEthernetForwardingConstruct fc, CarrierEthernetUni uni) {
        if (eeDeviceMap.get(fc) != null) {
            return eeDeviceMap.get(fc);
        }
        // find the (only) egress link of the UNI device
        Optional<Link> optLink = linkService.getDeviceEgressLinks(uni.cp().deviceId())
                .stream().findFirst();
        if (optLink.isPresent()) {
            // the link destination should be our target EE device
            DeviceId deviceId = optLink.get().dst().deviceId();
            eeDeviceMap.put(fc, deviceId);
            return deviceId;
        } else {
            throw new IllegalStateException("UNI device not connected to network");
        }
    }
}
