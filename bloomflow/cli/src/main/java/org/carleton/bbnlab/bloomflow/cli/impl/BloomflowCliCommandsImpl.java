/*
 * Copyright © 2016 Alexander Craig and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.carleton.bbnlab.bloomflow.cli.impl;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.carleton.bbnlab.bloomflow.cli.api.BloomflowCliCommands;

public class BloomflowCliCommandsImpl implements BloomflowCliCommands {

    private static final Logger LOG = LoggerFactory.getLogger(BloomflowCliCommandsImpl.class);
    private final DataBroker dataBroker;

    public BloomflowCliCommandsImpl(final DataBroker db) {
        this.dataBroker = db;
        LOG.info("BloomflowCliCommandImpl initialized");
    }

    @Override
    public Object testCommand(Object testArgument) {
        return "This is a test implementation of test-command";
    }
}