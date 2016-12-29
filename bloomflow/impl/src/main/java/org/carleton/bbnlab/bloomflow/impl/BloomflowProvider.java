/*
 * Copyright © 2016 Alexander Craig and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.carleton.bbnlab.bloomflow.impl;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.NotificationPublishService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BloomflowProvider {

    private static final Logger LOG = LoggerFactory.getLogger(BloomflowProvider.class);

    private final DataBroker dataBroker;
    private final NotificationPublishService notificationService;

    public BloomflowProvider(final DataBroker dataBroker,
            final NotificationPublishService notificationService) {
        this.dataBroker = dataBroker;
        this.notificationService = notificationService;
    }

    /**
     * Method called when the blueprint container is created.
     */
    public void init() {
        // this.notificationService.registerInterestListener(this); // Deprecated method
        LOG.info("BloomflowProvider Session Initiated");
    }

    /**
     * Method called when the blueprint container is destroyed.
     */
    public void close() {
        LOG.info("BloomflowProvider Closed");
    }
}
