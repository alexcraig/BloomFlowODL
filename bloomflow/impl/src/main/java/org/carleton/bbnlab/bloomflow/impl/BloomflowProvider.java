/*
 * Copyright © 2016 Alexander Craig and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.carleton.bbnlab.bloomflow.impl;

import java.util.Collection;
import java.util.Set;
import java.util.HashSet;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.Nonnull;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataObjectModification.ModificationType;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.binding.api.DataTreeChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataTreeIdentifier;
import org.opendaylight.controller.md.sal.binding.api.DataTreeModification;
import org.opendaylight.controller.sal.binding.api.NotificationProviderService;

import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.concepts.Registration;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.node.NodeConnector;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.node.NodeConnectorKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.Table;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketProcessingService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketProcessingListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketReceived;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class BloomflowProvider implements PacketProcessingListener, DataTreeChangeListener<Table> {
    private static final Logger LOG = LoggerFactory.getLogger(BloomflowProvider.class);
    private static final int FIRST_FLOW_ID = 2534;    // Arbitrarily selected

    // IGMP Config Params - Move these into configuration database once the required parameters are finalized
    public final int igmpRobustness;
    public final int igmpQueryInterval;
    public final int igmpQueryResponseInterval;
    public final double igmpGroupMembershipInterval;
    public final double igmpOtherQuerierPresentInterval;
    public final int igmpStartupQueryInterval;
    public final int igmpStartupQueryCount;
    public final int igmpLastMemberQueryCount;
    public final int igmpLastMemberQueryInterval;
    public final int igmpLastMemberQueryTime;
    public final int igmpUnsolicitedReportInterval;


    private final DataBroker dataBroker;
    private final NotificationProviderService notificationService;
    private final PacketProcessingService packetProcessingService;

    private Registration packetInRegistration;
    private ListenerRegistration<DataTreeChangeListener> dataTreeChangeListenerRegistration;


    private Set<InstanceIdentifier<Node>> observedNodes;

    private final AtomicLong flowIdInc = new AtomicLong(FIRST_FLOW_ID);

    private List<IgmpSwitchManager> managedSwitches;
    private final MulticastRoutingManager mcastRoutingManager;

    public BloomflowProvider(final DataBroker dataBroker,
            final NotificationProviderService notificationService,
            final PacketProcessingService packetProcessingService) {
        this.dataBroker = dataBroker;
        this.notificationService = notificationService;
        this.packetProcessingService = packetProcessingService;

        this.mcastRoutingManager = new MulticastRoutingManager(dataBroker, notificationService, packetProcessingService, this);

        igmpRobustness = 2;
        igmpQueryInterval = 125;
        igmpQueryResponseInterval = 100;
        igmpGroupMembershipInterval = igmpRobustness * igmpQueryInterval / (igmpQueryResponseInterval * 0.1);
        igmpOtherQuerierPresentInterval = igmpRobustness * igmpQueryInterval / (igmpQueryResponseInterval * 0.1 / 2);
        igmpStartupQueryInterval = igmpQueryInterval / 4;
        igmpStartupQueryCount = igmpRobustness;
        igmpLastMemberQueryCount = igmpRobustness;
        igmpLastMemberQueryInterval = 10;
        igmpLastMemberQueryTime = igmpLastMemberQueryInterval / igmpLastMemberQueryCount;
        igmpUnsolicitedReportInterval = 1;
    }

    /**
     * Method called when the blueprint container is created.
     */
    public void init() {
        LOG.debug("init() - Called");
        this.observedNodes = new HashSet<>();
        this.managedSwitches = new ArrayList<>();

        // this.notificationService.registerNotificationListener(this); // Deprecated method
        packetInRegistration = notificationService.registerNotificationListener(this);
        LOG.info("init() - Attached as listener to NotificationProviderService");

        final InstanceIdentifier<Table> instanceIdentifier = InstanceIdentifier.create(Nodes.class)
                .child(Node.class)
                .augmentation(FlowCapableNode.class)
                .child(Table.class);
        final DataTreeIdentifier<Table> dataTreeIdentifier = new DataTreeIdentifier(LogicalDatastoreType.OPERATIONAL, instanceIdentifier);
        this.dataTreeChangeListenerRegistration = this.dataBroker.registerDataTreeChangeListener(dataTreeIdentifier, this);
        LOG.info("init() - Registered as DataTreeChangeListener");

        LOG.debug("init() - Returning");
    }

    /**
     * Method called when the blueprint container is destroyed.
     */
    public void close() {
        LOG.debug("close() - Called");

        try {
            packetInRegistration.close();
        } catch (Exception e) {
            LOG.warn("close() - Closing packetInRegistration failed: {}", e.getMessage());
            LOG.debug("close() - Closing packetInRegistration failed..", e);
        }

        try {
            dataTreeChangeListenerRegistration.close();
        } catch (Exception e) {
            LOG.warn("close() - Failed to close dataTreeChangeListenerRegistration: {}", e.getMessage());
            LOG.debug("close() - Failed to close dataTreeChangeListenerRegistration..", e);
        }

        LOG.debug("close() - Returning");
    }

    @Override
    public void onPacketReceived(PacketReceived notification) {
        InstanceIdentifier<Node> ingressNode = notification.getIngress().getValue().firstIdentifierOf(Node.class);
        InstanceIdentifier<NodeConnector> ingressPort = notification.getIngress().getValue().firstIdentifierOf(NodeConnector.class);
        // LOG.info("onPacketReceived()\ningressNodeII = " + ingressNode + "\ningressPortII = " + ingressPort);

        // NodeId ingressNodeTest = new NodeId(ingressNode.firstKeyOf(Node.class, NodeKey.class).getId());
        // LOG.info("NodeId converted from PacketReceived notification: " + ingressNodeTest.toString());

        // Below code uses deprecated NodeId and NodeConnectorId
        // NodeId ingressNode = notification.getIngress().getValue().firstIdentifierOf(Node.class).firstKeyOf(Node.class, NodeKey.class).getId();
        // NodeConnectorId ingressPort = notification.getIngress().getValue().firstIdentifierOf(NodeConnector.class).firstKeyOf(NodeConnector.class, NodeConnectorKey.class).getId();
       //  LOG.debug("onPacketReceived() - Recieved PacketIn from (Node: " + ingressNode.firstKeyOf(Node.class, NodeKey.class).getId()
        //         + ", Port: " + ingressPort.firstKeyOf(NodeConnector.class, NodeConnectorKey.class).getId() + ")");

        byte[] payload = notification.getPayload();

        // Decode the received packet into Ethernet/IP
        char ethType = PacketUtils.getEtherType(payload);
        if (ethType == PacketUtils.ETHERTYPE_IPV4) {
            LOG.info("onPacketReceived() - Got IPv4 Packet");
            // Ensure IP version is 4 (already specified by ethertype, but should be consistent with IP header)
            byte ipVersion = PacketUtils.getIpVersion(payload);
            int ipHeaderLenBytes = PacketUtils.getIpHeaderLengthBytes(payload);
            if (ipVersion != 4) {
                LOG.warn("onPacketReceived() - IPv4 Packet specified wrong version in IPv4 header: " + ipVersion);
            }
            LOG.info("onPacketReceived() - " + PacketUtils.getSrcIpStr(payload) + " -> "
                    + PacketUtils.getDstIpStr(payload));
            // Check IP protocol field to see if this is an IGMP packet
            byte ipProto = PacketUtils.getIpProtocol(payload);
            if (ipProto == PacketUtils.IP_PROTO_IGMP) {
                LOG.info("onPacketReceived() - IPv4 Packet contains IGMP payload (Node: " + ingressNode.firstKeyOf(Node.class, NodeKey.class).getId()
                        + ", Port: " + ingressPort.firstKeyOf(NodeConnector.class, NodeConnectorKey.class).getId() + ")");
                IgmpPacket receivedIgmp = new IgmpPacket(
                        Arrays.copyOfRange(payload,
                                PacketUtils.ETHERNET_HEADER_LEN + ipHeaderLenBytes,
                                payload.length));

                /*
                LOG.info("onPacketReceived - IGMP message bytes (pre-decoding)\n0x " +
                                PacketUtils.byteString(
                                        Arrays.copyOfRange(payload,
                                                PacketUtils.ETHERNET_HEADER_LEN + ipHeaderLenBytes,
                                                payload.length),
                                        payload.length - (PacketUtils.ETHERNET_HEADER_LEN + ipHeaderLenBytes)));
                 */

                boolean foundIngressSwitch = false;
                for (IgmpSwitchManager managedSwitch : this.managedSwitches) {
                    if (managedSwitch.getNodeIdentifier().equals(ingressNode)) {
                        managedSwitch.processIgmpPacket(receivedIgmp, notification, ipHeaderLenBytes);
                        LOG.info(managedSwitch.debugStr());
                        foundIngressSwitch = true;
                        break;
                    }
                }
                if (!foundIngressSwitch) {
                    LOG.warn("onPacketReceived() - Decoded IGMP packet from unknown node: " +
                            ingressNode.firstKeyOf(Node.class, NodeKey.class).getId());
                }

                // this.mcastRoutingManager.getTopologyTest();

            } else {
                // Check if the packet is destined to a multicast IP address
                InetAddress dstAddr = PacketUtils.getDstIp(payload);
                InetAddress srcAddr = PacketUtils.getSrcIp(payload);
                if (dstAddr.isMulticastAddress()) {
                    // DBEUG
                    this.getReceptionPorts(dstAddr, srcAddr);
                }
            }
        } else if (ethType == PacketUtils.ETHERTYPE_IPV4_W_VLAN) {
            LOG.debug("onPacketReceived() - Got 802.1q VLAN tagged frame");
        } else if (ethType == PacketUtils.ETHERTYPE_ARP) {
            LOG.debug("onPacketReceived() - Got ARP frame");
        } else if (ethType == PacketUtils.ETHERTYPE_LLDP) {
            LOG.debug("onPacketReceived() - Got LLDP frame");
        } else {
            LOG.info("onPacketReceived() - Got packet with unknown ethType: " + String.valueOf(ethType));
            String ethTypeHex = "0x ";
            for (byte b : Arrays.copyOfRange(payload, 0, 26)) {
                ethTypeHex = ethTypeHex + String.format("%02x", b) + " ";
            }
            LOG.info("onPacketReceived() - payload bytes:\n" + ethTypeHex);
        }
    }

    public FlowId getNextFlowId() {
        return new FlowId(String.valueOf(flowIdInc.getAndIncrement()));
    }

    public DataBroker getDataBroker() {
        return this.dataBroker;
    }

    @Override
    public void onDataTreeChanged(@Nonnull Collection<DataTreeModification<Table>> modifications) {
        Short requiredTableId = 0;

        for (DataTreeModification modification : modifications) {
            if (modification.getRootNode().getModificationType() == ModificationType.SUBTREE_MODIFIED) {
                DataObject table = modification.getRootNode().getDataAfter();
                if (table instanceof Table) {
                    Table tableSure = (Table) table;
                    LOG.trace("table: {}", table);

                    if (requiredTableId.equals(tableSure.getId())) {
                        InstanceIdentifier<Table> tablePath = modification.getRootPath().getRootIdentifier();
                        this.onSwitchAppeared(tablePath);
                    }
                }
            }
        }
    }

    public synchronized void onSwitchAppeared(InstanceIdentifier<Table> appearedTablePath) {
        LOG.debug("onSwitchAppeared() - Called");

        InstanceIdentifier<Table> tablePath = appearedTablePath;
        InstanceIdentifier<Node> nodePath = tablePath.firstIdentifierOf(Node.class);

        boolean newSwitch = true;
        for (IgmpSwitchManager sw : this.managedSwitches) {
            if (sw.getNodeIdentifier().equals(nodePath)) {
                newSwitch = false;
                break;
            }
        }

        if (newSwitch) {
            LOG.info("onSwitchAppeared() - Observed new node: " + nodePath.firstKeyOf(Node.class, NodeKey.class).getId());

            IgmpSwitchManager switchManager = new IgmpSwitchManager(nodePath, this);
            switchManager.installIgmpMonitoringFlow(appearedTablePath);
            this.managedSwitches.add(switchManager);
        }
    }

    public MulticastRoutingManager getMcastRoutingManager() {
        return this.mcastRoutingManager;
    }

    public Set<NodeConnectorId> getReceptionPorts(InetAddress mcastDstAddr, InetAddress srcAddr) {
        Set<NodeConnectorId> portSet = new HashSet<>();
        for (IgmpSwitchManager switchManager : this.managedSwitches) {
            Set<NodeConnectorId> switchPortSet = switchManager.getReceptionPorts(mcastDstAddr, srcAddr);
            portSet.addAll(switchPortSet);
        }

        String debugStr = "getReceptionPorts(" + mcastDstAddr + ", " + srcAddr + ") =";
        for (NodeConnectorId portId : portSet) {
            debugStr += "\n" + portId;
        }
        LOG.info(debugStr);

        return portSet;
    }
}
