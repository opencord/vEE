package org.opencord.ce.api.services;

import com.google.common.annotations.Beta;
import org.opencord.ce.api.models.CarrierEthernetForwardingConstruct;
import org.opencord.ce.api.models.CarrierEthernetNetworkInterface;
import org.opencord.ce.api.models.CarrierEthernetUni;

import java.util.Set;

/**
 * Service interface used to control Carrier Ethernet nodes according to their control protocol.
 */
@Beta
public interface VeeService {

    /**
     * Creates and submits FlowObjectives depending on role of the device in the FC and ingress/egress NI types.
     *
     * @param fc the forwarding construct
     * @param srcNi the source network interface
     * @param dstNiSet the set of destination network interfaces
     */
    void setNodeForwarding(CarrierEthernetForwardingConstruct fc, CarrierEthernetNetworkInterface srcNi,
                           Set<CarrierEthernetNetworkInterface> dstNiSet);

    /**
     * Creates and stores meters based on the UNI's bandwidth profile.
     *
     * @param fc the forwarding construct
     * @param uni the user to network interface
     */
    void createBandwidthProfileResources(CarrierEthernetForwardingConstruct fc, CarrierEthernetUni uni);

    /**
     * Applies meters to flows.
     *
     * @param fc the forwarding construct
     * @param uni the user to network interface
     */
    void applyBandwidthProfileResources(CarrierEthernetForwardingConstruct fc, CarrierEthernetUni uni);

    /**
     * Removes the meters associated with a specific UNI of an FC.
     *
     * @param fc the forwarding construct
     * @param uni the user to network interface
     */
    void removeBandwidthProfileResources(CarrierEthernetForwardingConstruct fc, CarrierEthernetUni uni);

    /**
     * Removes all installed flow objectives associated with a specific FC.
     *
     * @param fc the forwarding construct
     */
    void removeAllForwardingResources(CarrierEthernetForwardingConstruct fc);

}
