/*
 * Copyright © 2016 Alexander Craig and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.carleton.bbnlab.bloomflow.impl;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.opendaylight.controller.md.sal.binding.api.ReadWriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.openflowplugin.api.OFConstants;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpVersion;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.OutputActionCaseBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.output.action._case.OutputActionBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.Action;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.ActionBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.ActionKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.Table;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.TableKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.Flow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.FlowBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.FlowKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.FlowModFlags;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.OutputPortValues;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.flow.InstructionsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.flow.MatchBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.instruction.ApplyActionsCaseBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.instruction.apply.actions._case.ApplyActionsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.Instruction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.InstructionBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.InstructionKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.node.NodeConnector;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.node.NodeConnectorKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l2.types.rev130827.EtherType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.ethernet.match.fields.EthernetTypeBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.match.EthernetMatchBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.match.IpMatchBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketReceived;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IgmpSwitchManager {
    private static final Logger LOG = LoggerFactory.getLogger(IgmpSwitchManager.class);

    private NodeId nodeId;
    private Map<NodeConnectorId, Map<InetAddress, MulticastMembershipRecord>> multicastRecords;
    private BloomflowProvider provider;

    // TODO: These lists should be populated by querying the inventory module. For now, we simply enable IGMP support
    // on any port over which an IGMP message has previously been received.
    private List<NodeConnectorId> ports;
    private List<NodeConnectorId> igmpEnabledPorts;

    public IgmpSwitchManager(NodeId nodeId, BloomflowProvider provider) {
        this.nodeId = nodeId;
        this.provider = provider;
        ports = new ArrayList<NodeConnectorId>();
        igmpEnabledPorts = new ArrayList<NodeConnectorId>();
        multicastRecords = new HashMap<NodeConnectorId, Map<InetAddress, MulticastMembershipRecord>>();
    }

    public String debugStr() {
        String returnStr = "\nIGMP Switch Manager Debug State:\nNode Id: " + nodeId.getValue() + "\n";
        for (NodeConnectorId port : multicastRecords.keySet()) {
            returnStr += "Membership Records for Port: " + port.getValue() + "\n";
            for (InetAddress mcastAddr : multicastRecords.get(port).keySet()) {
                returnStr += multicastRecords.get(port).get(mcastAddr).debugStr();
            }
        }
        return returnStr;
    }

    public void processIgmpPacket(IgmpPacket igmpPacket, PacketReceived packetIn, int ipHeaderLenBytes) {
        byte[] payload = packetIn.getPayload();
        NodeConnectorId ingressPort = packetIn.getIngress().getValue().firstIdentifierOf(NodeConnector.class).firstKeyOf(NodeConnector.class, NodeConnectorKey.class).getId();
        this.addIgmpPort(ingressPort);

        LOG.info(this.nodeId.getValue() + " processIgmpPacket() - Decoded IGMP message:\n" + igmpPacket.debugStr());

        try {
            if (igmpPacket.getMessageType() == IgmpPacket.MessageType.UNKNOWN_TYPE) {
                String headerHex = "0x ";
                for (byte b : Arrays.copyOfRange(payload,
                        0,
                        PacketUtils.ETHERNET_HEADER_LEN + ipHeaderLenBytes)) {
                    headerHex = headerHex + String.format("%02x", b) + " ";
                }
                LOG.info("processIgmpPacket() - eth + ip header bytes:\n" + headerHex);

                String igmpHex = "0x ";
                for (byte b : Arrays.copyOfRange(payload,
                        PacketUtils.ETHERNET_HEADER_LEN + ipHeaderLenBytes,
                        payload.length)) {
                    igmpHex = igmpHex + String.format("%02x", b) + " ";
                }
                LOG.info("processIgmpPacket() - igmp bytes:\n" + igmpHex);
            } else if (igmpPacket.getMessageType() == IgmpPacket.MessageType.MEMBERSHIP_REPORT_V3) {
                for (IgmpGroupRecord record : igmpPacket.getGroupRecords()) {
                    if (record.getRecordType() == IgmpGroupRecord.RecordType.MODE_IS_INCLUDE
                            || record.getRecordType() == IgmpGroupRecord.RecordType.MODE_IS_EXCLUDE) {
                        this.processCurrentStateRecord(record, packetIn, ingressPort);
                    } else if (record.getRecordType() == IgmpGroupRecord.RecordType.CHANGE_TO_INCLUDE_MODE
                            || record.getRecordType() == IgmpGroupRecord.RecordType.CHANGE_TO_EXCLUDE_MODE
                            || record.getRecordType() == IgmpGroupRecord.RecordType.ALLOW_NEW_SOURCES
                            || record.getRecordType() == IgmpGroupRecord.RecordType.BLOCK_OLD_SOURCES) {
                        this.processStateChangeRecord(record, packetIn, ingressPort);
                    }
                }
            } else if (igmpPacket.getMessageType() == IgmpPacket.MessageType.MEMBERSHIP_QUERY_V3
                    && igmpPacket.getSuppressRouterProcessing() == false
                    && (!igmpPacket.getAddress().equals(InetAddress.getByName("0.0.0.0")))) {
                // TODO: Implement
                LOG.info("processIgmpPacket() - MEMBERSHIP_QUERY_V3 not yet supported");
            }
        } catch (Exception e) {
            LOG.error("processIgmpPacket() - Exception getting IP address [{}]", e.getMessage(), e);
        }
    }

    /**
     * Creates a MulticastMembershipRecord from the specific PacketIn event and associated IgmpGroupRecord read from the packet.
     *
     * If the record did not already exist it is initialized with the provided group timer. If it did exist, the existing record IS NOT modified.
     */
    public MulticastMembershipRecord createMcastMembershipRecord(IgmpGroupRecord record, PacketReceived packetIn, double groupTimer) {
        NodeConnectorId ingressPort = packetIn.getIngress().getValue().firstIdentifierOf(NodeConnector.class).firstKeyOf(NodeConnector.class, NodeConnectorKey.class).getId();

        if (!multicastRecords.keySet().contains(ingressPort)) {
            multicastRecords.put(ingressPort, new HashMap<InetAddress, MulticastMembershipRecord>());
        }

        if (!multicastRecords.get(ingressPort).keySet().contains(record.getMcastAddress())) {
            MulticastMembershipRecord newRecord = new MulticastMembershipRecord(record.getMcastAddress(), groupTimer);
            multicastRecords.get(ingressPort).put(record.getMcastAddress(), newRecord);
        }

        return multicastRecords.get(ingressPort).get(record.getMcastAddress());
    }

    /**
     * Processes current state IGMP membership reports according to the following table (See RFC 3376):
     *
     * +--------------+--------------+--------------------+----------------------+
     * | Router State | Report Rec'd | New Router State   |     Actions          |
     * +==============+==============+====================+======================+
     * | INCLUDE (A)  | IS_IN (B)    | INCLUDE (A+B)      |     (B)=GMI          |
     * +--------------+--------------+--------------------+----------------------+
     * | INCLUDE (A)  | IS_EX (B)    | EXCLUDE (A*B,B-A)  |    (B-A)=0,          |
     * |              |              |                    |    Delete (A-B),     |
     * |              |              |                    |    Group Timer=GMI   |
     * +--------------+--------------+--------------------+----------------------+
     * | EXCLUDE (X,Y)| IS_IN (A)    | EXCLUDE (X+A,Y-A)  |    (A)=GMI           |
     * +--------------+--------------+--------------------+----------------------+
     * | EXCLUDE (X,Y)| IS_EX (A)    | EXCLUDE (A-Y,Y*A)  |    (A-X-Y)=GMI,      |
     * |              |              |                    |    Delete (X-A),     |
     * |              |              |                    |    Delete (Y-A),     |
     * |              |              |                    |    Group Timer=GMI   |
     * +--------------+--------------+--------------------+----------------------+
     *
     * Note: When the group is in INCLUDE mode, the set of addresses is stored in
     * the same list as the X set when in EXCLUDE mode
     *
     */
    public void processCurrentStateRecord(IgmpGroupRecord packetRecord, PacketReceived packetIn,
            NodeConnectorId ingressPort) {
        LOG.info("processCurrentStateRecord() - Called");
        MulticastMembershipRecord switchRecord = this.createMcastMembershipRecord(packetRecord, packetIn,
                this.provider.igmpGroupMembershipInterval);
        List<SourceRecord> newXSourceRecords = new ArrayList<SourceRecord>();
        List<SourceRecord> newYSourceRecords = new ArrayList<SourceRecord>();
        Set<InetAddress> recordAddresses = packetRecord.getSourceAddressSet();

        if (switchRecord.getFilterMode() == IgmpGroupRecord.RecordType.MODE_IS_INCLUDE) {
            if (packetRecord.getRecordType() == IgmpGroupRecord.RecordType.MODE_IS_INCLUDE) {
                // ==== Switch State: MODE_IS_INCLUDE, Message: MODE_IS_INCLUDE ====
            } else if (packetRecord.getRecordType() == IgmpGroupRecord.RecordType.MODE_IS_EXCLUDE) {
                // ==== Switch State: MODE_IS_INCLUDE, Message: MODE_IS_EXCLUDE ====
            }
        } else if (switchRecord.getFilterMode() == IgmpGroupRecord.RecordType.MODE_IS_EXCLUDE) {
            if (packetRecord.getRecordType() == IgmpGroupRecord.RecordType.MODE_IS_INCLUDE) {
                // ==== Switch State: MODE_IS_EXCLUDE, Message: MODE_IS_INCLUDE ====
            } else if (packetRecord.getRecordType() == IgmpGroupRecord.RecordType.MODE_IS_EXCLUDE) {
                // ==== Switch State: MODE_IS_EXCLUDE, Message: MODE_IS_EXCLUDE ====
            }
        }
    }

    /**
     * Processes state change IGMP membership reports according to the following table (See RFC 3376):
     *
     *
     * +--------------+--------------+-------------------+-----------------------+
     * | Router State | Report Rec'd | New Router State  |   Actions             |
     * +==============+==============+===================+=======================+
     * | INCLUDE (A)  | ALLOW (B)    | INCLUDE (A+B)     |   (B)=GMI             |
     * +--------------+--------------+-------------------+-----------------------+
     * | INCLUDE (A)  |  BLOCK (B)   | INCLUDE (A)       |   Send Q(G,A*B)       |
     * +--------------+--------------+-------------------+-----------------------+
     * | INCLUDE (A)  | TO_EX (B)    | EXCLUDE (A*B,B-A) |   (B-A)=0,            |
     * |              |              |                   |   Delete (A-B),       |
     * |              |              |                   |   Send Q(G,A*B),      |
     * |              |              |                   |   Group Timer=GMI     |
     * +--------------+--------------+-------------------+-----------------------+
     * | INCLUDE (A)  | TO_IN (B)    | INCLUDE (A+B)     |   (B)=GMI,            |
     * |              |              |                   |   Send Q(G,A-B)       |
     * +--------------+--------------+-------------------+-----------------------+
     * | EXCLUDE (X,Y)| ALLOW (A)    | EXCLUDE (X+A,Y-A) |   (A)=GMI             |
     * +--------------+--------------+-------------------+-----------------------+
     * | EXCLUDE (X,Y)| BLOCK (A)    | EXCLUDE(X+(A-Y),Y)|   (A-X-Y)=Group Timer,|
     * |              |              |                   |   Send Q(G,A-Y)       |
     * +--------------+--------------+-------------------+-----------------------+
     * | EXCLUDE (X,Y)| TO_EX (A)    | EXCLUDE (A-Y,Y*A) |   (A-X-Y)=Group Timer,|
     * |              |              |                   |   Delete (X-A),       |
     * |              |              |                   |   Delete (Y-A),       |
     * |              |              |                   |   Send Q(G,A-Y),      |
     * |              |              |                   |   Group Timer=GMI     |
     * +--------------+--------------+-------------------+-----------------------+
     * | EXCLUDE (X,Y)| TO_IN (A)    | EXCLUDE (X+A,Y-A) |   (A)=GMI,            |
     * |              |              |                   |   Send Q(G,X-A),      |
     * |              |              |                   |   Send Q(G)           |
     * +--------------+--------------+-------------------+-----------------------+
     *
     * Note: When the group is in INCLUDE mode, the set of addresses is stored in the same list as the X set when
     * in EXCLUDE mode
     */
    public void processStateChangeRecord(IgmpGroupRecord packetRecord, PacketReceived packetIn,
            NodeConnectorId ingressPort) {
        LOG.info("processStateChangeRecord() - Called");
        MulticastMembershipRecord switchRecord = this.createMcastMembershipRecord(packetRecord, packetIn,
                this.provider.igmpGroupMembershipInterval);
        List<SourceRecord> newXSourceRecords = new ArrayList<SourceRecord>();
        List<SourceRecord> newYSourceRecords = new ArrayList<SourceRecord>();
        Set<InetAddress> recordAddresses = packetRecord.getSourceAddressSet();
        Set<InetAddress> newXSet = new HashSet<InetAddress>();
        Set<InetAddress> newYSet = new HashSet<InetAddress>();

        if (switchRecord.getFilterMode() == IgmpGroupRecord.RecordType.MODE_IS_INCLUDE) {
            if (packetRecord.getRecordType() == IgmpGroupRecord.RecordType.ALLOW_NEW_SOURCES) {
                // ==== Switch State: MODE_IS_INCLUDE, Message: ALLOW_NEW_SOURCES ====
                newXSet = new HashSet<InetAddress>(switchRecord.getXAddressSet());
                newXSet.addAll(recordAddresses);
                for (InetAddress addr : newXSet) {
                    if (recordAddresses.contains(addr)) {
                        newXSourceRecords.add(new SourceRecord(addr, this.provider.igmpGroupMembershipInterval));
                    } else {
                        newXSourceRecords.add(new SourceRecord(addr, switchRecord.getCurrSourceTimer(addr)));
                    }
                }
                switchRecord.setXSourceRecords(newXSourceRecords);
                switchRecord.setYSourceRecords(newYSourceRecords);
            } else if (packetRecord.getRecordType() == IgmpGroupRecord.RecordType.BLOCK_OLD_SOURCES) {
                // ==== Switch State: MODE_IS_INCLUDE, Message: BLOCK_OLD_SOURCES ====
            } else if (packetRecord.getRecordType() == IgmpGroupRecord.RecordType.CHANGE_TO_EXCLUDE_MODE) {
                // ==== Switch State: MODE_IS_INCLUDE, Message: CHANGE_TO_EXCLUDE_MODE ====
                switchRecord.setFilterMode(IgmpGroupRecord.RecordType.MODE_IS_EXCLUDE);
                newXSet = new HashSet<InetAddress>(switchRecord.getXAddressSet());
                newXSet.retainAll(recordAddresses);
                newYSet = new HashSet<InetAddress>(recordAddresses);
                newYSet.removeAll(switchRecord.getXAddressSet());
                for (InetAddress addr : newXSet) {
                    newXSourceRecords.add(new SourceRecord(addr, switchRecord.getCurrSourceTimer(addr)));
                }
                for (InetAddress addr : newYSet) {
                    newYSourceRecords.add(new SourceRecord(addr, 0));
                }
                switchRecord.setXSourceRecords(newXSourceRecords);
                switchRecord.setYSourceRecords(newYSourceRecords);
                switchRecord.setGroupTimer(this.provider.igmpGroupMembershipInterval);

                // TODO: Send: Q(G, A*B)
                // self.send_group_and_source_specific_query(event.port, igmp_group_record.multicast_address, router_group_record, new_x_set)

            } else if (packetRecord.getRecordType() == IgmpGroupRecord.RecordType.CHANGE_TO_INCLUDE_MODE) {
                // ==== Switch State: MODE_IS_INCLUDE, Message: CHANGE_TO_INCLUDE_MODE ====
                switchRecord.setFilterMode(IgmpGroupRecord.RecordType.MODE_IS_INCLUDE);
                newXSet = new HashSet<InetAddress>(switchRecord.getXAddressSet());
                newXSet.addAll(recordAddresses);

                for (InetAddress addr : newXSet) {
                    if (recordAddresses.contains(addr)) {
                        newXSourceRecords.add(new SourceRecord(addr, this.provider.igmpGroupMembershipInterval));
                    } else {
                        newXSourceRecords.add(new SourceRecord(addr, switchRecord.getCurrSourceTimer(addr)));
                    }
                }
                switchRecord.setXSourceRecords(newXSourceRecords);
                switchRecord.setYSourceRecords(newYSourceRecords);

                // TODO: Send Q(G,A-B)
                // query_addr_set = router_group_record.get_x_addr_set() - igmp_record_addresses
                // self.send_group_and_source_specific_query(event.port, igmp_group_record.multicast_address, router_group_record, query_addr_set)
            }
        } else if (switchRecord.getFilterMode() == IgmpGroupRecord.RecordType.MODE_IS_EXCLUDE) {
            if (packetRecord.getRecordType() == IgmpGroupRecord.RecordType.ALLOW_NEW_SOURCES) {
                // ==== Switch State: MODE_IS_EXCLUDE, Message: ALLOW_NEW_SOURCES ====
            } else if (packetRecord.getRecordType() == IgmpGroupRecord.RecordType.BLOCK_OLD_SOURCES) {
                // ==== Switch State: MODE_IS_EXCLUDE, Message: BLOCK_OLD_SOURCES ====
            } else if (packetRecord.getRecordType() == IgmpGroupRecord.RecordType.CHANGE_TO_EXCLUDE_MODE) {
                // ==== Switch State: MODE_IS_EXCLUDE, Message: CHANGE_TO_EXCLUDE_MODE ====
                switchRecord.setFilterMode(IgmpGroupRecord.RecordType.MODE_IS_EXCLUDE);
                newXSet = new HashSet(recordAddresses);
                newXSet.removeAll(switchRecord.getYAddressSet());
                newYSet = new HashSet(switchRecord.getYAddressSet());
                newYSet.retainAll(recordAddresses);

                HashSet<InetAddress> groupTimerSet = new HashSet<InetAddress>(recordAddresses);
                groupTimerSet.removeAll(switchRecord.getXAddressSet());
                groupTimerSet.removeAll(switchRecord.getYAddressSet());

                for (InetAddress addr : newXSet) {
                    if (groupTimerSet.contains(addr)) {
                        newXSourceRecords.add(new SourceRecord(addr, switchRecord.getGroupTimer()));
                    } else {
                        newXSourceRecords.add(new SourceRecord(addr, switchRecord.getCurrSourceTimer(addr)));
                    }
                }
                for (InetAddress addr : newYSet) {
                    newYSourceRecords.add(new SourceRecord(addr, switchRecord.getCurrSourceTimer(addr)));
                }
                switchRecord.setGroupTimer(this.provider.igmpGroupMembershipInterval);
                switchRecord.setXSourceRecords(newXSourceRecords);
                switchRecord.setYSourceRecords(newYSourceRecords);

                // TODO: self.send_group_and_source_specific_query(event.port, igmp_group_record.multicast_address, router_group_record, new_x_set)

            } else if (packetRecord.getRecordType() == IgmpGroupRecord.RecordType.CHANGE_TO_INCLUDE_MODE) {
                // ==== Switch State: MODE_IS_EXCLUDE, Message: CHANGE_TO_INCLUDE_MODE ====
                switchRecord.setFilterMode(IgmpGroupRecord.RecordType.MODE_IS_INCLUDE);

                newXSet = new HashSet<InetAddress>(switchRecord.getXAddressSet());
                newXSet.addAll(recordAddresses);
                newYSet = new HashSet<InetAddress>(switchRecord.getYAddressSet());
                newYSet.removeAll(recordAddresses);

                for (InetAddress addr : newXSet) {
                    if (recordAddresses.contains(addr)) {
                        newXSourceRecords.add(new SourceRecord(addr, this.provider.igmpGroupMembershipInterval));
                    } else {
                        newXSourceRecords.add(new SourceRecord(addr, switchRecord.getCurrSourceTimer(addr)));
                    }
                }
                for (InetAddress addr : newYSet) {
                    newYSourceRecords.add(new SourceRecord(addr, 0));
                }
                switchRecord.setXSourceRecords(newXSourceRecords);
                switchRecord.setYSourceRecords(newYSourceRecords);

                // TODO: Send Q(G, X-A)
                // self.send_group_and_source_specific_query(event.port, igmp_group_record.multicast_address, router_group_record, query_addr_set)
                // TODO: # Send Q(G)
                // self.send_group_specific_query(event.port, igmp_group_record.multicast_address, router_group_record)
            }
        }

        if (switchRecord.getFilterMode() == IgmpGroupRecord.RecordType.MODE_IS_INCLUDE && newXSourceRecords.isEmpty()) {
            this.removeGroupRecord(ingressPort, packetRecord.getMcastAddress());
        }
    }

    public void removeGroupRecord(NodeConnectorId port, InetAddress mcastAddr) {
        if (this.multicastRecords.get(port) != null) {
            if (this.multicastRecords.get(port).get(mcastAddr) != null) {
                this.multicastRecords.get(port).remove(mcastAddr);

                if (this.multicastRecords.get(port).isEmpty()) {
                    this.multicastRecords.remove(port);
                }
            }
        }
    }

    public void installIgmpMonitoringFlow(InstanceIdentifier<Table> tablePath) {
        final short igmpProtocol = 0x2;
        final int priority = 0;

        FlowId flowId = provider.getNextFlowId();
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
        ReadWriteTransaction addFlowTransaction = provider.getDataBroker().newReadWriteTransaction();
        addFlowTransaction.put(LogicalDatastoreType.CONFIGURATION, flowPath, allToCtrlFlow.build(), true);
        addFlowTransaction.submit();

        LOG.info("installIgmpMonitoringFlow() - Created forward IGMP packets to controller flow");
    }

    public void addIgmpPort(NodeConnectorId port) {
        if (!this.ports.contains(port)) {
            ports.add(port);
            LOG.info("addIgmpPort() - Added port: " + port);
        }

        if (!this.igmpEnabledPorts.contains(port)) {
            igmpEnabledPorts.add(port);
            LOG.info("addIgmpPort() - Added IGMP enabled port: " + port);
        }
    }

    /**
     * @return the nodeId
     */
    public NodeId getNodeId() {
        return nodeId;
    }
}
