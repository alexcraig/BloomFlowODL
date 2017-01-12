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
import org.opendaylight.controller.md.sal.binding.api.ReadWriteTransaction;
import org.opendaylight.controller.sal.binding.api.NotificationProviderService;

import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.concepts.Registration;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpVersion;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l2.types.rev130827.EtherType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.node.NodeConnector;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.node.NodeConnectorKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.service.rev130819.SalFlowService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.service.rev130819.AddFlowInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.service.rev130819.AddFlowOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.service.rev130819.FlowTableRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.FlowBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.FlowKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.Flow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.Table;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.TableKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.FlowModFlags;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.OutputPortValues;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.flow.InstructionsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.flow.MatchBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.FlowRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.instruction.ApplyActionsCaseBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.instruction.apply.actions._case.ApplyActions;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.instruction.apply.actions._case.ApplyActionsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.Instruction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.InstructionBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.InstructionKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.OutputActionCaseBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.output.action._case.OutputActionBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.Action;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.ActionBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.ActionKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.ethernet.match.fields.EthernetDestinationBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.ethernet.match.fields.EthernetSourceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.ethernet.match.fields.EthernetTypeBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.match.EthernetMatch;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.match.EthernetMatchBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.match.IpMatch;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.match.IpMatchBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketProcessingService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketProcessingListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketReceived;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.RawPacket;

import org.opendaylight.openflowplugin.api.OFConstants;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class BloomflowProvider implements PacketProcessingListener, DataTreeChangeListener<Table> {

    private static final Logger LOG = LoggerFactory.getLogger(BloomflowProvider.class);

    private final DataBroker dataBroker;
    private final NotificationProviderService notificationService;
    private final PacketProcessingService packetProcessingService;

    private Registration packetInRegistration;
    private ListenerRegistration<DataTreeChangeListener> dataTreeChangeListenerRegistration;

    private NodeId nodeId;
    private InstanceIdentifier<Node> nodePath;
    private InstanceIdentifier<Table> tablePath;

    private Set<NodeId> observedNodes;

    private final AtomicLong flowIdInc = new AtomicLong();
    private final AtomicLong flowCookieInc = new AtomicLong(0x2a00000000000000L);

    public BloomflowProvider(final DataBroker dataBroker,
            final NotificationProviderService notificationService,
            final PacketProcessingService packetProcessingService) {
        this.dataBroker = dataBroker;
        this.notificationService = notificationService;
        this.packetProcessingService = packetProcessingService;
    }

    /**
     * Method called when the blueprint container is created.
     */
    public void init() {
        LOG.debug("init() - Called");
        this.observedNodes = new HashSet<>();

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
        LOG.debug("onPacketReceived() - Called");
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
                LOG.info("onPacketReceived() - IPv4 Packet contains IGMP payload");
                IgmpPacket receivedIgmp = new IgmpPacket(
                        Arrays.copyOfRange(payload,
                                PacketUtils.ETHERNET_HEADER_LEN + ipHeaderLenBytes,
                                payload.length));
                LOG.info("onPacketReceived() - Decoded IGMP message:\n" + receivedIgmp.debugStr());
                if (receivedIgmp.getMessageType() == IgmpPacket.MessageType.UNKNOWN_TYPE) {
                    String headerHex = "0x ";
                    for (byte b : Arrays.copyOfRange(payload,
                            0,
                            PacketUtils.ETHERNET_HEADER_LEN + ipHeaderLenBytes)) {
                        headerHex = headerHex + String.format("%02x", b) + " ";
                    }
                    LOG.info("onPacketReceived() - eth + ip header bytes:\n" + headerHex);

                    String igmpHex = "0x ";
                    for (byte b : Arrays.copyOfRange(payload,
                            PacketUtils.ETHERNET_HEADER_LEN + ipHeaderLenBytes,
                            payload.length)) {
                        igmpHex = igmpHex + String.format("%02x", b) + " ";
                    }
                    LOG.info("onPacketReceived() - igmp bytes:\n" + igmpHex);
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
        final short igmpProtocol = 0x2;

        int priority = 0;

        tablePath = appearedTablePath;
        nodePath = tablePath.firstIdentifierOf(Node.class);
        nodeId = nodePath.firstKeyOf(Node.class, NodeKey.class).getId();

        if (!this.observedNodes.contains(nodeId)) {
            LOG.info("onSwitchAppeared() - Observed nodeId: " + nodeId);

            FlowId flowId = new FlowId(String.valueOf(flowIdInc.getAndIncrement()));
            FlowKey flowKey = new FlowKey(flowId);
            InstanceIdentifier<Flow> flowPath = tablePath.child(Flow.class, flowKey);

            short tableId = tablePath.firstKeyOf(Table.class, TableKey.class).getId();

            // Install flow to forward all IGMP packets to controller
            FlowBuilder allToCtrlFlow = new FlowBuilder().setTableId(tableId).setFlowName(
                    "allPacketsToCtrl").setId(flowId)
                    .setKey(new FlowKey(flowId));

            MatchBuilder matchBuilder = new MatchBuilder();

            EthernetMatchBuilder ethMatchBuilder = new EthernetMatchBuilder();
            EthernetTypeBuilder ethTypeBuilder = new EthernetTypeBuilder();
            EtherType ethType = new EtherType(0x0800L);
            ethMatchBuilder.setEthernetType(ethTypeBuilder.setType(ethType).build());
            matchBuilder.setEthernetMatch(ethMatchBuilder.build());

            IpMatchBuilder ipMatchBuilder = new IpMatchBuilder();
            ipMatchBuilder.setIpProto(IpVersion.Ipv4);
            ipMatchBuilder.setIpProtocol(igmpProtocol);
            matchBuilder.setIpMatch(ipMatchBuilder.build());

            // Create output action -> send to controller
            OutputActionBuilder output = new OutputActionBuilder();
            output.setMaxLength(Integer.valueOf(0xffff));
            Uri controllerPort = new Uri(OutputPortValues.CONTROLLER.toString());
            output.setOutputNodeConnector(controllerPort);

            ActionBuilder ab = new ActionBuilder();
            ab.setAction(new OutputActionCaseBuilder().setOutputAction(output.build()).build());
            ab.setOrder(0);
            ab.setKey(new ActionKey(0));

            List<Action> actionList = new ArrayList<>();
            actionList.add(ab.build());

            // Create an Apply Action
            ApplyActionsBuilder aab = new ApplyActionsBuilder();
            aab.setAction(actionList);

            // Wrap our Apply Action in an Instruction
            InstructionBuilder ib = new InstructionBuilder();
            ib.setInstruction(new ApplyActionsCaseBuilder().setApplyActions(aab.build()).build());
            ib.setOrder(0);
            ib.setKey(new InstructionKey(0));

            // Put our Instruction in a list of Instructions
            InstructionsBuilder isb = new InstructionsBuilder();
            List<Instruction> instructions = new ArrayList<>();
            instructions.add(ib.build());
            isb.setInstruction(instructions);

            allToCtrlFlow
                .setMatch(matchBuilder.build())
                .setInstructions(isb.build())
                .setPriority(priority)
                .setBufferId(OFConstants.OFP_NO_BUFFER)
                .setHardTimeout(0)
                .setIdleTimeout(0)
                .setFlags(new FlowModFlags(false, false, false, false, false));

            // DataBroker Approach
            // ===================
            ReadWriteTransaction addFlowTransaction = dataBroker.newReadWriteTransaction();
            addFlowTransaction.put(LogicalDatastoreType.CONFIGURATION, flowPath, allToCtrlFlow.build(), true);
            addFlowTransaction.submit();

            LOG.info("onSwitchAppeared() - Created forward IGMP packets to controller flow");
        }
        this.observedNodes.add(nodeId);
    }
}
