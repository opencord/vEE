package org.opencord.ce.api.models;

import org.onlab.packet.VlanId;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.config.Config;

import java.util.Optional;

/**
 * Configuration information for edge connect point and corresponding VLAN tag.
 */
public class PortVlanConfig extends Config<ConnectPoint> {
    public static final String CONFIG_KEY = "portVlan";

    public static final String S_TAG_KEY = "s-tag";

    public Optional<VlanId> portVlanId() {
        String s = get(S_TAG_KEY, null);
        if (s == null) {
            return Optional.empty();
        }
        return Optional.of(VlanId.vlanId(Short.valueOf(s)));
    }

    public PortVlanConfig portVlanId(VlanId vlanId) {
        if (vlanId == null) {
            return (PortVlanConfig) setOrClear(S_TAG_KEY, (String) null);
        }
        return (PortVlanConfig) setOrClear(S_TAG_KEY, String.valueOf(vlanId.toShort()));
    }
}