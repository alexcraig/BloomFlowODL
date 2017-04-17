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
import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
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
    public static final int STATIC_EDGE_WEIGHT = 1;

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
        public NodeId nodeId;
        public NodeId shortestPathSource;
        // public List<NodeId> shortestPathNodes;
        public List<NodeId> path;
        public int pathCost;

        public ShortestPathNode(NodeId nodeId, NodeId shortestPathSource, int pathCost) {
            this.nodeId = nodeId;
            this.shortestPathSource = shortestPathSource;
            this.path = new ArrayList<>();
            this.pathCost = pathCost;
        }

        public ShortestPathNode(NodeId nodeId, NodeId shortestPathSource, int pathCost, List<NodeId> path) {
            this.nodeId = nodeId;
            this.shortestPathSource = shortestPathSource;
            this.path = path;
            this.pathCost = pathCost;
        }

        public String debugStr() {
            String debugStr = "\nPath from " + this.shortestPathSource + " to " + this.nodeId + ":\n";
            for (NodeId node : this.path) {
                debugStr = debugStr + "\t" + node + "\n";
            }
            debugStr = debugStr + "Path cost: " + this.pathCost + "\n";
            return debugStr;
        }
    }

    public class PathCostComparator implements Comparator<ShortestPathNode>
    {
        @Override
        public int compare(ShortestPathNode x, ShortestPathNode y)
        {
            // Assume neither string is null. Real code should
            // probably be more robust
            // You could also just return x.length() - y.length(),
            // which would be more efficient.
            if (x.pathCost < y.pathCost)
            {
                return -1;
            }
            if (x.pathCost > y.pathCost)
            {
                return 1;
            }
            return 0;
        }
    }

    private static final Logger LOG = LoggerFactory.getLogger(MulticastRoutingManager.class);

    private final DataBroker dataBroker;
    private final NotificationProviderService notificationService;
    private final PacketProcessingService packetProcessingService;
    private final BloomflowProvider bloomflowProvider;

    public MulticastRoutingManager(final DataBroker dataBroker,
            final NotificationProviderService notificationService,
            final PacketProcessingService packetProcessingService,
            final BloomflowProvider bloomflowProvider) {
        this.dataBroker = dataBroker;
        this.notificationService = notificationService;
        this.packetProcessingService = packetProcessingService;
        this.bloomflowProvider = bloomflowProvider;
    }

    public Map<NodeId, Map<NodeId, WeightedEdge>> genWeightedEdgeMap(NetworkTopology topo) {
        Map<NodeId, Map<NodeId, WeightedEdge>> edgeMap = new HashMap<>();
        String debugStr = "\n";

        for(Link link : topo.getTopology().get(0).getLink()) {
            NodeId srcNode = link.getSource().getSourceNode();
            NodeId dstNode = link.getDestination().getDestNode();

            if (!edgeMap.containsKey(srcNode)) {
                Map<NodeId, WeightedEdge> srcMap = new HashMap<>();
                edgeMap.put(srcNode, srcMap);
            }

            debugStr = debugStr + "edgeMap[" + srcNode + "][" + dstNode + "]\n";
            edgeMap.get(srcNode).put(dstNode, new WeightedEdge(link, STATIC_EDGE_WEIGHT));
        }

        LOG.debug(debugStr);
        return edgeMap;
    }


    public Set<NodeId> genNodeSet(NetworkTopology topo) {
        Set<NodeId> nodeSet = new HashSet<>();

        for(Link link : topo.getTopology().get(0).getLink()) {
            if(!nodeSet.contains(link.getSource().getSourceNode())) {
                nodeSet.add(link.getSource().getSourceNode());
            }

            if(!nodeSet.contains(link.getDestination().getDestNode())) {
                nodeSet.add(link.getDestination().getDestNode());
            }
        }

        return nodeSet;
    }


    public Map<NodeId, ShortestPathNode> calcShortestPathTrees(NodeId srcNode,
            Map<NodeId, Map<NodeId, WeightedEdge>> edgeMap, Set<NodeId> nodeSet) {

        Map<NodeId, ShortestPathNode> shortestPathMap = new HashMap<>();

        Comparator<ShortestPathNode> comparator = new PathCostComparator();
        PriorityQueue<ShortestPathNode> queue =
            new PriorityQueue<>(nodeSet.size(), comparator);

        queue.add(new ShortestPathNode(srcNode, srcNode, 0));
        Set<NodeId> seen = new HashSet<>();

        // String debugStr = "";

        try {
            while (queue.size() > 0) {
                ShortestPathNode node1 = queue.poll();
                // debugStr = debugStr + "node1 = " + node1.nodeId + "\n";

                if (!seen.contains(node1.nodeId)) {
                    seen.add(node1.nodeId);
                    List<NodeId> node1Path = new ArrayList<>(node1.path);
                    node1Path.add(node1.nodeId);
                    node1.path = node1Path;
                    shortestPathMap.put(node1.nodeId, node1);

                    Map<NodeId, WeightedEdge> connectedNodeMap = edgeMap.get(node1.nodeId);

                    for (Map.Entry<NodeId, WeightedEdge> entry : connectedNodeMap.entrySet()) {
                        NodeId node2 = entry.getKey();
                        WeightedEdge edge = entry.getValue();
                        // debugStr = debugStr + "\tnode2 = " + node2 + "\n";

                        if(!seen.contains(node2)) {
                            int node2PathCost = node1.pathCost + edge.weight;
                            queue.add(new ShortestPathNode(node2, srcNode, node2PathCost, node1Path));
                        }
                    }
                }
            }
        } catch (NullPointerException e) {
            LOG.error("Error: " + e.getMessage());
            LOG.error("Check that network is fully connected.");
        }

        // LOG.debug(debugStr);
        return shortestPathMap;
    }


    public void getTopologyTest() {
        List<Link> linkList = new ArrayList<>();
        List<NodeId> nodeList = new ArrayList<>();
        NodeId firstObservedNode = null;

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

                        if (firstObservedNode == null) {
                            firstObservedNode = link.getSource().getSourceNode();
                        }
                    }

                    if(!nodeList.contains(link.getDestination().getDestNode())) {
                        nodeList.add(link.getDestination().getDestNode());
                    }
                }

                String linkStr = "getToplogyTest() - Discovered links:";
                for(Link link : linkList) {
                    linkStr += "\n" + link.getSource().getSourceNode() + " --> " + link.getDestination().getDestNode();
                }
                LOG.debug(linkStr);

                String nodeStr = "getToplogyTest() - Discovered nodes:";
                for(NodeId node : nodeList) {
                    nodeStr += "\n" + node;
                }
                LOG.debug(nodeStr);

                // Debug - Test out shortest path calculation
                Set<NodeId> nodeSet = genNodeSet(topo);
                Map<NodeId, Map<NodeId, WeightedEdge>> edgeMap = genWeightedEdgeMap(topo);
                Map<NodeId, ShortestPathNode> shortestPathMap = calcShortestPathTrees(firstObservedNode, edgeMap, nodeSet);
                for (ShortestPathNode path : shortestPathMap.values()) {
                    LOG.info(path.debugStr());
                }

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

    public void processMulticastGroupEvent(MulticastGroupEvent mcastEvent) {
        LOG.info("MulticastRoutingManager received MulticastGroupEvent");
    }

    public BloomflowProvider getBloomflowProvider() {
        return this.bloomflowProvider;
    }
}
