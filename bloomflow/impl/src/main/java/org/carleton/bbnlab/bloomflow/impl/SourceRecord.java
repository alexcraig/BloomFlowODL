/*
 * Copyright © 2016 Alexander Craig and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.carleton.bbnlab.bloomflow.impl;

import java.net.InetAddress;

/* Class representing the source record state maintained by the MulticastMembershipRecord class. */
public class SourceRecord {
    private InetAddress sourceAddress;
    private double sourceTimer;

    public SourceRecord(InetAddress sourceAddress, double sourceTimer)  {
        this.sourceAddress = sourceAddress;
        this.sourceTimer = sourceTimer;
    }

    /**
     * @return the sourceAddress
     */
    public InetAddress getSourceAddress() {
        return sourceAddress;
    }

    /**
     * @param sourceAddress the sourceAddress to set
     */
    public void setSourceAddress(InetAddress sourceAddress) {
        this.sourceAddress = sourceAddress;
    }

    /**
     * @return the sourceTimer
     */
    public double getSourceTimer() {
        return sourceTimer;
    }

    /**
     * @param sourceTimer the sourceTimer to set
     */
    public void setSourceTimer(int sourceTimer) {
        this.sourceTimer = sourceTimer;
    }

    public String debugStr() {
        return "\t\t[" + sourceAddress + ", " + sourceTimer + "]\n";
    }
}
