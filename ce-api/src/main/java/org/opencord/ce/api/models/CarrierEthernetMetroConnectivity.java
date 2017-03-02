package org.opencord.ce.api.models;

import org.onosproject.newoptical.api.OpticalConnectivityId;
import org.onosproject.newoptical.api.OpticalPathEvent;

/**
 * Represents a metro optical connection.
 */

class CarrierEthernetMetroConnectivity {

    // TODO: In the future this may be replaced by a connectivity intent
    // FIXME: Need to keep a set of OpticalConnectivityIds

    private OpticalConnectivityId id;
    private OpticalPathEvent.Type status;

    public CarrierEthernetMetroConnectivity(OpticalConnectivityId id, OpticalPathEvent.Type status) {
        this.id = id;
        this.status = status;
    }

    public OpticalConnectivityId id() {
        return this.id;
    }

    public OpticalPathEvent.Type status() {
        return this.status;
    }

    public void setId(OpticalConnectivityId id) {
        this.id = id;
    }

    public void setStatus(OpticalPathEvent.Type status) {
        this.status = status;
    }

}
