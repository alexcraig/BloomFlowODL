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
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/* Class representing the group record state maintained by an IGMPv3 multicast router
 *
 * Multicast routers implementing IGMPv3 keep state per group per attached network.  This group state consists of a
 * filter-mode, a list of sources, and various timers.  For each attached network running IGMP, a multicast router
 * records the desired reception state for that network.  That state conceptually consists of a set of records of the
 * form:
 *
 * (multicast address, group timer, filter-mode, (source records))
 *
 * Each source record is of the form:
 *
 * (source address, source timer)
 */
public class MulticastMembershipRecord {
    public class SourceRecord {
        private InetAddress sourceAddress;
        private int sourceTimer;

        public SourceRecord(InetAddress sourceAddress, int sourceTimer)  {
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
        public int getSourceTimer() {
            return sourceTimer;
        }

        /**
         * @param sourceTimer the sourceTimer to set
         */
        public void setSourceTimer(int sourceTimer) {
            this.sourceTimer = sourceTimer;
        }
    }

    private InetAddress mcastAddress;
    private int groupTimer;
    private IgmpGroupRecord.RecordType filterMode;
    private List<SourceRecord> xSourceRecords;
    private List<SourceRecord> ySourceRecords;

    public MulticastMembershipRecord(InetAddress mcastAddress, int initialTimerValue) {
        this.mcastAddress = mcastAddress;
        groupTimer = initialTimerValue;
        filterMode = IgmpGroupRecord.RecordType.MODE_IS_INCLUDE;
        xSourceRecords = new ArrayList<SourceRecord>();
        ySourceRecords = new ArrayList<SourceRecord>();
    }

    /**
     * Returns the current source timer for the specified IP address, or 0 if the specified IP is not known by this group record.
     */
    public int getCurrSourceTimer(InetAddress ipAddress) {
        for (SourceRecord record : xSourceRecords) {
            if (record.getSourceAddress() == ipAddress) {
                return record.getSourceTimer();
            }
        }

        return 0;
    }

    /**
     * Returns the set of addresses in the X set of source records (see RFC 3376)
     * Note: When in INCLUDE mode, all sources are stored in the X set.
     */
    public Set<InetAddress> getXAddressSet() {
        Set<InetAddress> returnSet = new HashSet<InetAddress>();

        for (SourceRecord record : xSourceRecords) {
            returnSet.add(record.getSourceAddress());
        }

        return returnSet;
    }

    /**
     * Returns the set of addresses in the Y set of source records (see RFC 3376)
     * Note: When in INCLUDE mode, his set should always be empty.
     */
    public Set<InetAddress> getYAddressSet() {
        Set<InetAddress> returnSet = new HashSet<InetAddress>();

        for (SourceRecord record : ySourceRecords) {
            returnSet.add(record.getSourceAddress());
        }

        return returnSet;
    }

    /**
     * Removes the source record with the specified IP address from the group record
     */
    public boolean removeSourceRecord(InetAddress ipAddress) {
        boolean removed  = false;

        Iterator<SourceRecord> itr = xSourceRecords.iterator();
        while(itr.hasNext()){
            SourceRecord xRecord = itr.next();
            if (xRecord.sourceAddress == ipAddress) {
                itr.remove();
                removed = true;
            }
        }

        itr = ySourceRecords.iterator();
        while(itr.hasNext()){
            SourceRecord yRecord = itr.next();
            if (yRecord.sourceAddress == ipAddress) {
                itr.remove();
                removed = true;
            }
        }

        return removed;
    }

    /**
     * @return the mcastAddress
     */
    public InetAddress getMcastAddress() {
        return mcastAddress;
    }

    /**
     * @param mcastAddress the mcastAddress to set
     */
    public void setMcastAddress(InetAddress mcastAddress) {
        this.mcastAddress = mcastAddress;
    }

    /**
     * @return the groupTimer
     */
    public int getGroupTimer() {
        return groupTimer;
    }

    /**
     * @param groupTimer the groupTimer to set
     */
    public void setGroupTimer(int groupTimer) {
        this.groupTimer = groupTimer;
    }

    /**
     * @return the filterMode
     */
    public IgmpGroupRecord.RecordType getFilterMode() {
        return filterMode;
    }

    /**
     * @param filterMode the filterMode to set
     */
    public void setFilterMode(IgmpGroupRecord.RecordType filterMode) {
        this.filterMode = filterMode;
    }
}
