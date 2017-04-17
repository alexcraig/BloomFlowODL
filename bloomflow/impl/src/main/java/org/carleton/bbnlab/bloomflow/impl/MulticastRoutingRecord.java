/*
 * Copyright © 2016 Alexander Craig and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.carleton.bbnlab.bloomflow.impl;

import java.net.InetAddress;
import java.util.HashSet;
import java.util.Set;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev130712.NodeId;

/**
 * @author mininet
 *
 */
public class MulticastRoutingRecord {
    public InetAddress srcAddr;
    public NodeConnectorId ingressPort;
    public NodeId ingressNode;
    public InetAddress dstMcastAddr;
    public Set<NodeId> installedFlowNodes;
    public MulticastRoutingManager routingManager;
    public FlowId flowId;

    public MulticastRoutingRecord(InetAddress srcAddr, NodeConnectorId ingressPort, NodeId ingressNode,
            InetAddress dstMcastAddr, MulticastRoutingManager routingManager) {
        this.srcAddr = srcAddr;
        this.ingressPort = ingressPort;
        this.dstMcastAddr = dstMcastAddr;
        this.routingManager = routingManager;
        this.installedFlowNodes = new HashSet<>();
        this.flowId = this.routingManager.getBloomflowProvider().getNextFlowId();
    }

    public void installOpenflowRules() {

    }

    public void removeOpenflowRules() {

    }
}
