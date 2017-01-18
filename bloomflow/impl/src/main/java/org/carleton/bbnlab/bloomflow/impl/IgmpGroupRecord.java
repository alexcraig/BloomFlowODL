/*
 * Copyright © 2016 Alexander Craig and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.carleton.bbnlab.bloomflow.impl;

import java.nio.ByteOrder;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//======================================================================
//
//                        IGMP v3 - Group Record
//
//   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//   |  Record Type  |  Aux Data Len |     Number of Sources (N)     |
//   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//   |                       Multicast Address                       |
//   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//   |                       Source Address [1]                      |
//   +-                                                             -+
//   |                       Source Address [2]                      |
//   +-                                                             -+
//   .                               .                               .
//   .                               .                               .
//   .                               .                               .
//   +-                                                             -+
//   |                       Source Address [N]                      |
//   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//   |                                                               |
//   .                                                               .
//   .                         Auxiliary Data                        .
//   .                                                               .
//   |                                                               |
//   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//
//======================================================================

public class IgmpGroupRecord {
    private static final Logger LOG = LoggerFactory.getLogger(IgmpGroupRecord.class);

    public enum RecordType {
        MODE_IS_INCLUDE,
        MODE_IS_EXCLUDE,
        CHANGE_TO_INCLUDE_MODE,
        CHANGE_TO_EXCLUDE_MODE,
        ALLOW_NEW_SOURCES,
        BLOCK_OLD_SOURCES,
        UNIMITIALIZED_RECORD,
        UNKNOWN_TYPE
    }

    private static final int GROUP_RECORD_HEADER_LEN = 8;

    private RecordType recordType;
    private byte auxDataLen;
    private char numSources;
    private InetAddress mcastAddress;
    private List<InetAddress> sourceAddresses;

    private int recordLenBytes;

    public IgmpGroupRecord() {
        recordType = RecordType.UNIMITIALIZED_RECORD;
        auxDataLen = 0;
        numSources = 0;
        mcastAddress = null;
        sourceAddresses = new ArrayList<InetAddress>();
        recordLenBytes = 0;
    }

    public static byte getRecordTypeChar(RecordType type) {
        switch (type) {
            case MODE_IS_INCLUDE:
                return 1;
            case MODE_IS_EXCLUDE:
                return 2;
            case CHANGE_TO_INCLUDE_MODE:
                return 3;
            case CHANGE_TO_EXCLUDE_MODE:
                return 4;
            case ALLOW_NEW_SOURCES:
                return 5;
            case BLOCK_OLD_SOURCES:
                return 6;
            default:
                return 0;
        }
    }

    public IgmpGroupRecord(final byte[] payloadBytes) {
        this();
        parseRecord(payloadBytes);
    }

    // Returns number of bytes used for packed record
    public int packRecord(ByteBuffer outputBuf) {
        int numBytes = 0;

        if (this.auxDataLen > 0) {
            LOG.warn("packRecord() - Binary packing is not supported for group record with auxDataLen > 0 "
                    + ", auxData will be omitted from packed message.");
        }

        outputBuf.order(ByteOrder.BIG_ENDIAN);
        outputBuf.put(IgmpGroupRecord.getRecordTypeChar(this.recordType));
        numBytes += 1;
        outputBuf.put((byte)0);
        numBytes += 1;
        outputBuf.putChar(this.numSources);
        numBytes += 2;
        outputBuf.put(this.getMcastAddress().getAddress());
        numBytes += 4;

        for(InetAddress sourceAddr : this.sourceAddresses) {
            outputBuf.put(sourceAddr.getAddress());
            numBytes += 4;
        }

        return numBytes;
    }

    // Returns number of bytes read
    public int parseRecord(final byte[] payloadBytes) {
        byte[] ipReadBytes = new byte[4];
        ByteBuffer readBuffer = ByteBuffer.wrap(payloadBytes);

        int recordTypeChar = readBuffer.get();
        switch (recordTypeChar) {
            case 1:  recordType = RecordType.MODE_IS_INCLUDE;
                     break;
            case 2:  recordType = RecordType.MODE_IS_EXCLUDE;
                     break;
            case 3:  recordType = RecordType.CHANGE_TO_INCLUDE_MODE;
                     break;
            case 4:  recordType = RecordType.CHANGE_TO_EXCLUDE_MODE;
                     break;
            case 5:  recordType = RecordType.ALLOW_NEW_SOURCES;
                     break;
            case 6:  recordType = RecordType.BLOCK_OLD_SOURCES;
                     break;
            default: recordType = RecordType.UNKNOWN_TYPE;
                     break;
        }

        auxDataLen = readBuffer.get();
        numSources = readBuffer.getChar();

        readBuffer.get(ipReadBytes);
        try {
            mcastAddress = InetAddress.getByAddress(ipReadBytes);
        } catch(Exception e) {
            LOG.error("parseRecord() - Exception getting mcast IP address [{}]", e.getMessage(), e);
        }

        for(int i = 0; i < numSources; i++) {
            readBuffer.get(ipReadBytes);
            try {
                InetAddress sourceAddress = InetAddress.getByAddress(ipReadBytes);
                sourceAddresses.add(sourceAddress);
            } catch(Exception e) {
                LOG.error("parseRecord() - Exception getting mcast IP address [{}]", e.getMessage(), e);
            }
        }
        recordLenBytes = GROUP_RECORD_HEADER_LEN + (numSources * 4);
        return recordLenBytes;
    }

    public String debugStr() {
        StringBuffer str = new StringBuffer();
        str.append("IGMPv3 Group Record\n");
        str.append("Type: " + recordType + "\tNumSources: " + numSources + "\n");
        str.append("MCastAddress: " + mcastAddress + "\n");
        str.append("Source Addresses:\n");
        for (InetAddress sourceAddress : sourceAddresses) {
            str.append(sourceAddress + "\n");
        }
        return str.toString();
    }

    /**
     * @return the recordType
     */
    public RecordType getRecordType() {
        return recordType;
    }

    /**
     * @param recordType the recordType to set
     */
    public void setRecordType(RecordType recordType) {
        this.recordType = recordType;
    }

    /**
     * @return the auxDataLen
     */
    public byte getAuxDataLen() {
        return auxDataLen;
    }

    /**
     * @param auxDataLen the auxDataLen to set
     */
    public void setAuxDataLen(byte auxDataLen) {
        this.auxDataLen = auxDataLen;
    }

    /**
     * @return the numSources
     */
    public char getNumSources() {
        return numSources;
    }

    /**
     * @param numSources the numSources to set
     */
    public void setNumSources(char numSources) {
        this.numSources = numSources;
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
     * @return the sourceAddresses
     */
    public List<InetAddress> getSourceAddresses() {
        return sourceAddresses;
    }

    public Set<InetAddress> getSourceAddressSet() {
        return new HashSet(sourceAddresses);
    }

    /**
     * @param sourceAddresses the sourceAddresses to set
     */
    public void setSourceAddresses(List<InetAddress> sourceAddresses) {
        this.sourceAddresses = sourceAddresses;
    }

    /**
     * @return the recordLenBytes
     */
    public int getRecordLenBytes() {
        return recordLenBytes;
    }

    /**
     * @param recordLenBytes the recordLenBytes to set
     */
    public void setRecordLenBytes(int recordLenBytes) {
        this.recordLenBytes = recordLenBytes;
    }
}
