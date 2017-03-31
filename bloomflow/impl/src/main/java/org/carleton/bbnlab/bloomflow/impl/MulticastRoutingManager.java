/*
 * Copyright © 2016 Alexander Craig and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.carleton.bbnlab.bloomflow.impl;

import com.google.common.base.Optional;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.sal.binding.api.NotificationProviderService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketProcessingService;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Link;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author mininet
 *
 */
public class MulticastRoutingManager {
    private class WeightedEdge {
        public Link link;
        public NodeId sourceNode;
        public NodeId destNode;
        public int weight;

        public WeightedEdge(Link link, int weight) {
            this.link = link;
            this.sourceNode = link.getSource().getSourceNode();
            this.destNode = link.getDestination().getDestNode();
            this.weight = weight;
        }
    }

    private class ShortestPathNode {
        public NodeId node;
        public NodeId shortestPathSource;
        public List<NodeId> shortestPathNodes;
        public List<Link> shortestPathLinks;

        public ShortestPathNode(NodeId node, NodeId shortestPathSource) {
            this.node = node;
            this.shortestPathSource = shortestPathSource;
            this.shortestPathNodes = new ArrayList<>();
            this.shortestPathLinks = new ArrayList<>();
        }
    }

    private static final Logger LOG = LoggerFactory.getLogger(MulticastRoutingManager.class);

    private final DataBroker dataBroker;
    private final NotificationProviderService notificationService;
    private final PacketProcessingService packetProcessingService;

    public MulticastRoutingManager(final DataBroker dataBroker,
            final NotificationProviderService notificationService,
            final PacketProcessingService packetProcessingService) {

        this.dataBroker = dataBroker;
        this.notificationService = notificationService;
        this.packetProcessingService = packetProcessingService;
    }

    public void getTopologyTest() {
        List<Link> linkList = new ArrayList<>();
        List<NodeId> nodeList = new ArrayList<>();

        ReadOnlyTransaction readOnlyTransaction = this.dataBroker.newReadOnlyTransaction();
        InstanceIdentifier<NetworkTopology> topoIdentifier = InstanceIdentifier.builder(NetworkTopology.class).build();
        try {
            Optional<NetworkTopology> dataObjectOptional = null;
            dataObjectOptional = readOnlyTransaction.read(LogicalDatastoreType.OPERATIONAL, topoIdentifier).get();
            if (dataObjectOptional.isPresent()) {
                NetworkTopology topo = dataObjectOptional.get();
                LOG.debug("getTopologyTest() - topo =\n" + topo);
                // TODO: Following assumes only one Topology object exists... need to determine in what conditions this assumption may be violated
                for(Link link : topo.getTopology().get(0).getLink()) {
                    linkList.add(link);

                    if(!nodeList.contains(link.getSource().getSourceNode())) {
                        nodeList.add(link.getSource().getSourceNode());
                    }
                    if(!nodeList.contains(link.getDestination().getDestNode())) {
                        nodeList.add(link.getDestination().getDestNode());
                    }
                }

                String linkStr = "getToplogyTest() - Discovered links:";
                for(Link link : linkList) {
                    linkStr += "\n" + link.getSource().getSourceNode() + " --> " + link.getDestination().getDestNode();
                }
                LOG.info(linkStr);

                String nodeStr = "getToplogyTest() - Discovered nodes:";
                for(NodeId node : nodeList) {
                    nodeStr += "\n" + node;
                }
                LOG.info(nodeStr);
            }
        } catch (InterruptedException e) {
            LOG.error("Failed to read nodes from Operation data store.");
            readOnlyTransaction.close();
            throw new RuntimeException("Failed to read nodes from Operation data store.", e);
        } catch (ExecutionException e) {
            LOG.error("Failed to read nodes from Operation data store.");
            readOnlyTransaction.close();
            throw new RuntimeException("Failed to read nodes from Operation data store.", e);
        }
        readOnlyTransaction.close();
    }
}
