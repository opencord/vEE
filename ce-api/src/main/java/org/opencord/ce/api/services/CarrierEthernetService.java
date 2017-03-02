package org.opencord.ce.api.services;

import com.google.common.annotations.Beta;
import org.opencord.ce.api.models.CarrierEthFcId;
import org.opencord.ce.api.models.CarrierEthernetForwardingConstruct;

import java.util.Map;


/**
 * Service interface for provisioning of Carrier Ethernet connections.
 */
@Beta
public interface CarrierEthernetService {

    /**
     * Get the map containing all installed FCs.
     *
     * @return the FC map
     */
    Map<CarrierEthFcId, CarrierEthernetForwardingConstruct> fcMap();

    /**
     * Get an installed FC using its id.
     *
     * @param fcId the FC id
     * @return the FC representation or null if the EVC doesn't exist
     */
    CarrierEthernetForwardingConstruct getFc(CarrierEthFcId fcId);

    /**
     * Installs all resources associated with a specific FC.
     *
     * @param fc the FC to install
     * @return the FC that was installed
     */
    CarrierEthernetForwardingConstruct installFc(CarrierEthernetForwardingConstruct fc);

    /**
     * Re-establish connectivity for an existing FC.
     *
     * @param fc the updated FC representation
     * @return the possibly modified FC that was installed or null if updated FC could not be installed
     */
    CarrierEthernetForwardingConstruct updateFc(CarrierEthernetForwardingConstruct fc);

    /**
     * Removes all resources associated with a specific FC.
     *
     * @param fcId the FC id
     * @return the FC that was removed or null if removal failed
     */
    CarrierEthernetForwardingConstruct removeFc(CarrierEthFcId fcId);

    /**
     * Removes all resources associated with the application.
     *
     * This will be called either from the deactivate method or as a response to a CLI command.
     */
    void removeAllFcs();

    /**
     * Creates bandwidth profiles at the UNIs of an FC.
     *
     * @param fc the forwarding construct
     */
    void createBandwidthProfiles(CarrierEthernetForwardingConstruct fc);

    /**
     * Applies bandwidth profiles to the UNIs of an FC.
     *
     * @param fc the forwarding construct
     */
    void applyBandwidthProfiles(CarrierEthernetForwardingConstruct fc);

    /**
     * Removes bandwidth profiles from the UNIs of an FC.
     *
     * @param fc the forwarding construct
     */
    void removeBandwidthProfiles(CarrierEthernetForwardingConstruct fc);

    /**
     * Establishes connectivity for the provided FC by installing all necessary forwarding rules.
     *
     * @param fc the forwarding construct
     */
    void setupConnectivity(CarrierEthernetForwardingConstruct fc);

    /**
     * Removes connectivity for the provided FC by removing all installed forwarding rules.
     *
     * @param fc the forwarding construct
     */
    void removeConnectivity(CarrierEthernetForwardingConstruct fc);

}
