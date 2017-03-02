package org.opencord.ce.vcpe;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.apache.felix.scr.annotations.Service;
import org.onlab.packet.EthType;
import org.onlab.packet.Ethernet;
import org.onlab.packet.VlanId;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.DeviceId;
import org.onosproject.net.driver.Driver;
import org.onosproject.net.driver.DriverService;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.FlowRuleService;
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
import org.onosproject.net.meter.MeterService;
import org.opencord.ce.api.models.CarrierEthernetForwardingConstruct;
import org.opencord.ce.api.models.CarrierEthernetNetworkInterface;
import org.opencord.ce.api.models.CarrierEthernetUni;
import org.opencord.ce.api.services.VcpeService;
import org.slf4j.Logger;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.slf4j.LoggerFactory.getLogger;
import static org.opencord.ce.api.models.CarrierEthernetNetworkInterface.Type;

/**
 * vCPE implementation.
 */
@Component(immediate = true)
@Service
public class VcpeManager implements VcpeService {
    private static final int PRIORITY = 50000;
    private static final String APP_NAME = "org.opencord.ce.vcpe";

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected FlowRuleService flowRuleService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected MeterService meterService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected FlowObjectiveService flowObjectiveService;

    // FIXME slightly better way to detect OF-DPA issues
    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected DriverService drivers;

    private final Logger log = getLogger(getClass());

    private static ApplicationId appId;

    // Store submitted flow objects as the service does not allow query operations
    private final Map<CarrierEthernetForwardingConstruct, LinkedList<Pair<DeviceId, Objective>>> flowObjectiveMap =
            new ConcurrentHashMap<>();

    @Activate
    protected void activate() {
        appId = coreService.registerApplication(APP_NAME);
    }

    @Deactivate
    protected void deactivate() {}

    @Override
    public void setNodeForwarding(CarrierEthernetForwardingConstruct fc, CarrierEthernetNetworkInterface ingressNi,
                                  Set<CarrierEthernetNetworkInterface> egressNiSet) {

        if (ingressNi == null || egressNiSet.isEmpty()) {
            log.error("There needs to be at least one ingress and one egress NI to set forwarding.");
            return;
        }
        // if the ingress interface is not a UNI, this call cannot be directed to a provider-managed CPE
        if (!ingressNi.type().equals(Type.UNI)) {
            return;
        }

        flowObjectiveMap.putIfAbsent(fc, new LinkedList<>());

        // TODO: Get created FlowObjectives from this method
        createFlowObjectives(fc, ingressNi, egressNiSet);
    }

    /**
     * Creates and submits FlowObjectives into the device vesting the role of CPE.
     *
     * @param fc the FC representation
     * @param ingressNi the ingress network interface
     * @param  egressNiSet the set of egress NIs
     */
    private void createFlowObjectives(CarrierEthernetForwardingConstruct fc, CarrierEthernetNetworkInterface ingressNi,
                                      Set<CarrierEthernetNetworkInterface> egressNiSet) {

        /////////////////////////////////////////
        // Prepare and submit filtering objective
        /////////////////////////////////////////

        FilteringObjective.Builder filteringObjectiveBuilder = DefaultFilteringObjective.builder()
                .permit().fromApp(appId)
                .withPriority(PRIORITY)
                .withKey(Criteria.matchInPort(ingressNi.cp().port()));

        TrafficTreatment.Builder filterTreatmentBuilder = DefaultTrafficTreatment.builder();

        // In general, nodes would match on the VLAN tag assigned to the EVC/FC
        Criterion filterVlanIdCriterion = Criteria.matchVlanId(fc.vlanId());

        // Source node of an FC should match on CE-VLAN ID (if present) if it's a UNI
        filterVlanIdCriterion = Criteria.matchVlanId(ingressNi.ceVlanId());

        // If a CE-VLAN-ID exists on the incoming packet then push an S-TAG of current FC on top
        // otherwise push it on as a C-tag
        if (ingressNi.ceVlanId() != null && ingressNi.ceVlanId() != VlanId.NONE) {
            filterTreatmentBuilder.pushVlan(EthType.EtherType.QINQ.ethType()).setVlanId(fc.vlanId());
        } else {
            filterTreatmentBuilder.pushVlan().setVlanId(fc.vlanId());
        }

        filteringObjectiveBuilder.addCondition(filterVlanIdCriterion);

        // Do not add meta if there are no instructions (i.e. if not first)
        if (!(ingressNi.type().equals(Type.GENERIC))) {
            filteringObjectiveBuilder.withMeta(filterTreatmentBuilder.build());
        }

        flowObjectiveService.filter(ingressNi.cp().deviceId(), filteringObjectiveBuilder.add());
        flowObjectiveMap.get(fc).addFirst(Pair.of(ingressNi.cp().deviceId(), filteringObjectiveBuilder.add()));

        ////////////////////////////////////////////////////
        // Prepare and submit next and forwarding objectives
        ////////////////////////////////////////////////////

        TrafficSelector.Builder fwdSelectorBuilder = DefaultTrafficSelector.builder()
                .matchVlanId(fc.vlanId())
                .matchInPort(ingressNi.cp().port());

        if (isOfDpa(ingressNi.cp().deviceId())) {
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
        flowObjectiveMap.get(fc).addLast(Pair.of(ingressNi.cp().deviceId(), nextObjective));

        flowObjectiveService.forward(ingressNi.cp().deviceId(), forwardingObjective);
        flowObjectiveMap.get(fc).addFirst(Pair.of(ingressNi.cp().deviceId(), forwardingObjective));
    }

    @Override
    public void createBandwidthProfileResources(CarrierEthernetForwardingConstruct fc, CarrierEthernetUni uni) {
        // currently not implemented for vCPE
    }

    @Override
    public void applyBandwidthProfileResources(CarrierEthernetForwardingConstruct fc, CarrierEthernetUni uni) {
        //
    }

    @Override
    public void removeBandwidthProfileResources(CarrierEthernetForwardingConstruct fc, CarrierEthernetUni uni) {
        //
    }

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

    private boolean isOfDpa(DeviceId deviceId) {
        Driver driver = drivers.getDriver(deviceId);
        return driver != null &&
                driver.swVersion().contains("OF-DPA");
    }



}
