package org.opencord.ce.api.models;

import org.onlab.util.Identifier;

/**
 * {@link CarrierEthernetForwardingConstruct} identifier.
 */
public class CarrierEthFcId extends Identifier<String> {

    public CarrierEthFcId(String fcId) {
        super(fcId);
    }
}
