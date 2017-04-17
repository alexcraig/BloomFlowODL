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
import java.util.Map;
import java.util.Set;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev130712.NodeId;

/**
 * Event which encapsulates the desired reception state for all multicast addresses / ports on a single IGMP enabled switch
 */
public class MulticastGroupEvent {
    NodeId receivingSwitch;
    // Note: An empty set in the map represents desired reception from all sources (i.e., non-source specific multicast)
    Map<InetAddress, Map<NodeConnectorId, Set<InetAddress>>> desiredReceptionState;

    public MulticastGroupEvent(NodeId receivingSwitch, Map<InetAddress, Map<NodeConnectorId, Set<InetAddress>>> desiredReceptionState) {
        this.receivingSwitch = receivingSwitch;
        this.desiredReceptionState = desiredReceptionState;
    }

    public static Set<NodeConnectorId> getReceptionPorts(InetAddress mcastDstAddr, InetAddress srcAddr,
            Map<InetAddress, Map<NodeConnectorId, Set<InetAddress>>> desiredReceptionState) {
        Set<NodeConnectorId> portSet = new HashSet<>();

        if (desiredReceptionState != null && desiredReceptionState.keySet().contains(mcastDstAddr)) {
            Map<NodeConnectorId, Set<InetAddress>> groupReceptionState = desiredReceptionState.get(mcastDstAddr);
            for (NodeConnectorId portId : groupReceptionState.keySet()) {
                if (groupReceptionState.get(portId).size() == 0 || groupReceptionState.get(portId).contains(srcAddr)) {
                    portSet.add(portId);
                }
            }
        }

        return portSet;
    }

    public static boolean equalReceptionState(Map<InetAddress, Map<NodeConnectorId, Set<InetAddress>>> state1,
            Map<InetAddress, Map<NodeConnectorId, Set<InetAddress>>> state2) {
        if (!state1.keySet().equals(state2.keySet())) {
            return false;
        }

        for (InetAddress mcastAddr : state1.keySet()) {
            if (!state1.get(mcastAddr).keySet().equals(state2.get(mcastAddr).keySet())) {
                return false;
            }

            for(NodeConnectorId portId : state1.get(mcastAddr).keySet()) {
                if (!state1.get(mcastAddr).get(portId).equals(state2.get(mcastAddr).get(portId))) {
                    return false;
                }
            }
        }

        return true;
    }

    public static String receptionStateDebugStr(NodeId receivingSwitch, Map<InetAddress, Map<NodeConnectorId, Set<InetAddress>>> desiredReceptionState) {
        String debugStr = "\n===== MulticastGroupEvent: Switch: " + receivingSwitch;
        for (InetAddress mcastAddress : desiredReceptionState.keySet()) {
            debugStr += "\nMcast Group Addr: " + mcastAddress;
            for (NodeConnectorId portId : desiredReceptionState.get(mcastAddress).keySet()) {
                debugStr += "\nPort: " + portId;
                if (desiredReceptionState.get(mcastAddress).get(portId).size() == 0) {
                    debugStr += "\n\tALL SOURCES";
                } else {
                    for (InetAddress srcAddr : desiredReceptionState.get(mcastAddress).get(portId)) {
                        debugStr += "\n\t" + srcAddr;
                    }
                }
            }
        }
        return debugStr;
    }

    public String debugStr() {
        return MulticastGroupEvent.receptionStateDebugStr(this.receivingSwitch, this.desiredReceptionState);
    }
}
