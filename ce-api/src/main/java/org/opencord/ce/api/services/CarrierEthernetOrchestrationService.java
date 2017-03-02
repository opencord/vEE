package org.opencord.ce.api.services;

import org.onosproject.net.ConnectPoint;
import org.opencord.ce.api.models.CarrierEthernetLogicalTerminationPoint;
import org.opencord.ce.api.models.CarrierEthernetUni;
import org.opencord.ce.api.models.CarrierEthernetVirtualConnection;

import java.util.Map;
import java.util.Set;

import static org.opencord.ce.api.models.CarrierEthernetNetworkInterface.Type;

/**
 * Service interface for orchestrating of Carrier Ethernet connections requests.
 */
public interface CarrierEthernetOrchestrationService {

    /**
     * Returns the map of installed EVCs.
     *
     * @return map of installed EVCs
     */
    Map<String, CarrierEthernetVirtualConnection> evcMap();

    /**
     * Get an installed EVC using its id.
     *
     * @param evcId the EVC id
     * @return the EVC representation or null if the EVC doesn't exist
     */
    CarrierEthernetVirtualConnection getEvc(String evcId);

    // TODO: Add method to remove a UNI from an already installed EVC

    /**
     * Establish connectivity according to the EVC type (E-Line, E-Tree, E-LAN) and the EVC parameters.
     *
     * @param evc the EVC representation
     * @return the (potentially modified) EVC that was installed or null in case of failure
     */
    CarrierEthernetVirtualConnection installEvc(CarrierEthernetVirtualConnection evc);

    /**
     * Re-establish connectivity for an existing EVC.
     *
     * @param evc the updated EVC definition
     * @return the (potentially modified) EVC that was installed or null if EVC connectivity could not be established
     */
    CarrierEthernetVirtualConnection updateEvc(CarrierEthernetVirtualConnection evc);

    /**
     * Removes all resources associated with a specific installed EVC.
     *
     * @param evcId the EVC id
     */
    void removeEvc(String evcId);

    /**
     * Removes all installed EVCs and the associated resources.
     * <p>
     * This will be called either from the deactivate method or as a response to a CLI/REST command.
     */
    void removeAllEvcs();

    /**
     * Get the map containing all global UNIs.
     *
     * @return the global UNI map
     */
    Map<String, CarrierEthernetUni> getUniMap();

    /**
     * Adds a potential UNI to the global UNI map if they are not already there.
     *
     * @param uni the potential UNI to add to global UNI map
     * @return the UNI that was added or null if UNI existed already
     */
    CarrierEthernetUni addGlobalUni(CarrierEthernetUni uni);

    /**
     * Remove an UNI from the set of global UNIs.
     *
     * @param uniId the id of the UNI to be removed
     * @return the UNI that was removed or null in case of failure (didn't exist of refCount was not 0)
     */
    CarrierEthernetUni removeGlobalUni(String uniId);

    // TODO: Add removeAllGlobalUnis method (or command only?)

    /**
     * Creates a new UNI associated with the provided connect point.
     *
     * Conditions for validating an UNI:
     * - ConnectPoint deviceId and Port are valid
     * - Port is enabled
     *
     * @param cp the connect point to be associated with the generated UNI
     * @return a new validated UNI or null if the validation failed
     */
    CarrierEthernetUni generateUni(ConnectPoint cp);

    /**
     * Returns all potential UNIs from the topology.
     *
     * @param excludeAdded indicates that UNIs already added in the UNI map should not be in the returned set
     * @param includeRemoved indicates that UNIs explicitly removed from the UNI map should be in the returned set
     * @return set of all potential UNIs in the topology
     */
    Set<CarrierEthernetUni> getUnisFromTopo(boolean excludeAdded, boolean includeRemoved);

    /**
     * Get the map containing all global LTPs.
     *
     * @return the global LTP map
     */
    Map<String, CarrierEthernetLogicalTerminationPoint> ltpMap();

    /**
     * Adds a potential LTP and its UNI or pair INNI to the global LTP/UNI maps if they are not already there.
     *
     * @param ltp the potential LTP to add to global LTP map
     * @return the LTP that was added or null if it already existed
     */
    CarrierEthernetLogicalTerminationPoint addGlobalLtp(CarrierEthernetLogicalTerminationPoint ltp);

    /**
     * Remove an LTP from the set of global LTPs, as well as the corresponding INNI LTP at the other end of the link.
     *
     * @param ltpId the id of the LTP to be removed
     * @return the LTP that was removed or null in case of failure (didn't exist of refCount was not 0)
     */
    CarrierEthernetLogicalTerminationPoint removeGlobalLtp(String ltpId);

    // TODO: Add removeAllGlobalLtps method (or command only?)

    /**
     * Creates a new LTP of the provided type and associated with the provided connect point.
     *
     * Conditions for validating an LTP:
     * - ConnectPoint deviceId and Port are valid
     * - Port is enabled
     *
     * @param cp the connect point to be associated with the generated LTP
     * @param ltpType the type of the LTP to be generated (UNI/INNI/ENNI)
     * @return a new validated LTP or null if the validation failed
     */
    CarrierEthernetLogicalTerminationPoint generateLtp(ConnectPoint cp, Type ltpType);

    /**
     * Returns all potential LTPs from the topology.
     *
     * @param excludeAdded indicates that LTPs already added in the LTP map should not be in the returned set
     * @param includeRemoved indicates that LTPs explicitly removed from the LTP map should be in the returned set
     * @return set of all potential LTPs in the topology
     */
    Set<CarrierEthernetLogicalTerminationPoint> getLtpsFromTopo(boolean excludeAdded, boolean includeRemoved);
}
