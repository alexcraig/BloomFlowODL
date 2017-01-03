/*
 * Copyright © 2016 Alexander Craig and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.carleton.bbnlab.bloomflow.impl;

import java.util.Collection;
import javax.annotation.Nonnull;
import java.util.Set;
import java.util.HashSet;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.sal.binding.api.NotificationProviderService;
import org.opendaylight.yangtools.concepts.Registration;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketProcessingService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketProcessingListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketReceived;
import org.opendaylight.controller.md.sal.binding.api.DataObjectModification.ModificationType;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.binding.api.DataTreeChangeListener;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.controller.md.sal.binding.api.DataTreeIdentifier;
import org.opendaylight.controller.md.sal.binding.api.DataTreeModification;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.Table;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId;

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
        LOG.info("init() - Called");
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

        LOG.info("init() - Returning");
    }

    /**
     * Method called when the blueprint container is destroyed.
     */
    public void close() {
        LOG.info("close() - Called");

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

        LOG.info("close() - Returning");
    }

    @Override
    public void onPacketReceived(PacketReceived notification) {
        LOG.info("onPacketReceived() - Called");
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
        LOG.info("onSwitchAppeared() - Called");


        tablePath = appearedTablePath;
        nodePath = tablePath.firstIdentifierOf(Node.class);
        nodeId = nodePath.firstKeyOf(Node.class, NodeKey.class).getId();

        if (!this.observedNodes.contains(nodeId)) {
            LOG.info("onSwitchAppeared() - Observed nodeId: " + nodeId);
        }
        this.observedNodes.add(nodeId);
    }
}
