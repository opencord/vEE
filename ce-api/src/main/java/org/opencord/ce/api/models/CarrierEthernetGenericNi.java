package org.opencord.ce.api.models;


import org.onlab.packet.VlanId;
import org.onosproject.net.ConnectPoint;

import static com.google.common.base.MoreObjects.toStringHelper;

/**
 * Representation of a Generic Carrier Ethernet NI.
 * Class is only meant to be used for establishing forwarding in CarrierEthernetPacketNodeManagers
 */
public class CarrierEthernetGenericNi extends CarrierEthernetNetworkInterface {

    public enum Role {

        NONE("None");

        private String value;

        Role(String value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return value;
        }
    }

    public CarrierEthernetGenericNi(ConnectPoint connectPoint, String uniCfgId) {
        super(connectPoint, Type.GENERIC, uniCfgId);
    }

    @Override
    public Role role() {
        return Role.NONE;
    }

    /**
     * Always returns null, since CE-VLAN IDs are not associated with Generic NIs.
     *
     * @return null
     */
    @Override
    public VlanId ceVlanId() {
        return null;
    }

    /**
     * Always returns null, since S-TAGs are not associated with Generic NIs.
     *
     * @return null
     */
    @Override
    public VlanId sVlanId() {
        return null;
    }

    /**
     * Dummy implementation of abstract method (for generic NI type there is no concept of EVC vs. global NIs).
     *
     * @param gni a generic NI
     */
    @Override
    public void addEcNi(CarrierEthernetNetworkInterface gni) {}

    /**
     * Dummy implementation of abstract method (for generic NI type there is no concept of EVC vs. global NIs).
     *
     * @param gni a generic NI
     */
    @Override
    public void removeEcNi(CarrierEthernetNetworkInterface gni) {}

    /**
     * Dummy implementation of abstract method (for generic NI type there is no concept of EVC vs. global NIs).
     *
     * @param gni a generic NI
     * @return true
     */
    @Override
    public boolean validateEcNi(CarrierEthernetNetworkInterface gni) {
        return true;
    }

    @Override
    public String toString() {

        return toStringHelper(this)
                .add("id", this.id)
                .add("cfgId", this.cfgId)
                .add("refCount", refCount)
                .add("capacity", this.capacity)
                .add("usedCapacity", this.usedCapacity).toString();
    }

}

