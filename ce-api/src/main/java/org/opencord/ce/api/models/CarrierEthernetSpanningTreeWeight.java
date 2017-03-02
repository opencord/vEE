package org.opencord.ce.api.models;

import org.onosproject.net.ConnectPoint;
import org.onosproject.net.topology.LinkWeight;
import org.onosproject.net.topology.TopologyEdge;
import org.onosproject.net.topology.TopologyService;
import org.slf4j.Logger;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Weight class to cause path selection only on the pre-calculated spanning tree.
 */
public class CarrierEthernetSpanningTreeWeight implements LinkWeight {

    private final Logger log = getLogger(getClass());

    protected TopologyService topologyService = null;

    public CarrierEthernetSpanningTreeWeight(TopologyService topologyService) {
        this.topologyService = topologyService;
    }

    @Override
    public double weight(TopologyEdge edge) {
        if (!isBroadCastPoint(edge.link().src()) || !isBroadCastPoint(edge.link().dst())) {
            return -1;
        } else {
            return 1;
        }
    }

    /**
     * Checks if a connect point is on the pre-calculated spanning tree.
     *
     * @param cp the connect point to check
     * @return true if the connect point is on the spanning tree and false otherwise
     */
    private boolean isBroadCastPoint(ConnectPoint cp) {
        // TODO: Get topology snapshot so that same spanning tree is used by all pairs if topology changes
        if (cp == null) {
            log.info("Cp is null!");
        }
        if (topologyService == null) {
            log.info("topologyservice is null!");
        }
        return topologyService.isBroadcastPoint(topologyService.currentTopology(), cp);
    }
}
