/*
 * Copyright © 2016 Alexander Craig and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.carleton.bbnlab.bloomflow.impl;

import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//======================================================================
//
//                 IGMP v3 - Membership Query Message
//
//  0                   1                   2                   3
//  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
// +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
// |  Type = 0x11  | Max Resp Code |           Checksum            |
// +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
// |                         Group Address                         |
// +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
// | Resv  |S| QRV |     QQIC      |     Number of Sources (N)     |
// +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
// |                       Source Address [1]                      |
// +-                                                             -+
// |                       Source Address [2]                      |
// +-                              .                              -+
// .                               .                               .
// .                               .                               .
// +-                                                             -+
// |                       Source Address [N]                      |
// +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//
//======================================================================
//
//              IGMP v3 - Membership Report Message
//
// 0                   1                   2                   3
// 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
// +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
// |  Type = 0x22  |    Reserved   |           Checksum            |
// +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
// |           Reserved            |  Number of Group Records (M)  |
// +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
// |                                                               |
// .                                                               .
// .                        Group Record [1]                       .
// .                                                               .
// |                                                               |
// +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
// |                                                               |
// .                                                               .
// .                        Group Record [2]                       .
// .                                                               .
// |                                                               |
// +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
// |                               .                               |
// .                               .                               .
// |                               .                               |
// +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
// |                                                               |
// .                                                               .
// .                        Group Record [M]                       .
// .                                                               .
// |                                                               |
// +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//
//======================================================================

public class IgmpPacket {
    private static final Logger LOG = LoggerFactory.getLogger(IgmpPacket.class);

    public enum MessageType {
        UNIMITIALIZED_MESSAGE,
        MEMBERSHIP_QUERY,
        MEMBERSHIP_QUERY_V1,
        MEMBERSHIP_QUERY_V2,
        MEMBERSHIP_QUERY_V3,
        MEMBERSHIP_REPORT_V1,
        MEMBERSHIP_REPORT_V2,
        MEMBERSHIP_REPORT_V3,
        LEAVE_GROUP_V2,
        UNKNOWN_TYPE
    }

    public final String IGMP_ADDRESS = "224.0.0.22";
    public final String IGMP_V3_ALL_SYSTEMS_ADDRESS = "224.0.0.1";

    private final int MIN_PACKET_LEN = 8;
    private final int TYPE_FIELD_LEN = 1;
    private final int V3_QUERY_HDR_LEN = 12;
    private final int V3_REPORT_HDR_LEN = 8;

    private final char FLAG_MEMBERSHIP_QUERY     = 0x11;
    private final char FLAG_MEMBERSHIP_REPORT_V1 = 0x12;
    private final char FLAG_MEMBERSHIP_REPORT_V2 = 0x16;
    private final char FLAG_MEMBERSHIP_REPORT_V3 = 0x22;
    private final char FLAG_LEAVE_GROUP_V2       = 0x17;

    private byte versionAndType;
    private MessageType messageType;
    private byte maxResponseTime;
    private boolean suppressRouterProcessing;
    private byte qrv;
    private byte qqic;
    private char csum;
    private InetAddress address;
    private char numSources;
    private List<InetAddress> sourceAddresses;
    private char numGroupRecords;
    private List<IgmpGroupRecord> groupRecords;
    private byte[] extra;    // Stores extra binary data at end of message, not currently used

    private int dataLenBytes;

    public IgmpPacket() {
        dataLenBytes = 0;
        versionAndType = 0;
        messageType = MessageType.UNIMITIALIZED_MESSAGE;
        maxResponseTime = 0;
        suppressRouterProcessing = false;
        qrv = 0;
        qqic = 0;
        csum = 0;
        address = null;
        numSources = 0;
        sourceAddresses = new ArrayList<InetAddress>();
        numGroupRecords = 0;
        groupRecords = new ArrayList<IgmpGroupRecord>();
        extra = null;
    }

    public IgmpPacket(final byte[] payloadBytes) {
        this();
        parseMessage(payloadBytes);
    }

    public int parseMessage(final byte[] payloadBytes) {
        byte[] ipReadBytes = new byte[4];
        int numBytesProcessed = 0;

        ByteBuffer readBuffer = ByteBuffer.wrap(payloadBytes);
        dataLenBytes = payloadBytes.length;
        versionAndType = readBuffer.get();
        numBytesProcessed += 1;

        if (versionAndType == FLAG_MEMBERSHIP_REPORT_V3) {
            // Read v3 Membership Report specific fields
            messageType = MessageType.MEMBERSHIP_REPORT_V3;
            readBuffer.get();    // Skip 1 byte
            csum = readBuffer.getChar();
            readBuffer.getChar(); // Skip 2 bytes
            numGroupRecords = readBuffer.getChar();
            numBytesProcessed += 7;

            // Read in group records
            for(int i = 0; i < numGroupRecords; i++) {
                IgmpGroupRecord newRecord = new IgmpGroupRecord();
                numBytesProcessed += newRecord.parseRecord(Arrays.copyOfRange(payloadBytes,
                        numBytesProcessed, payloadBytes.length));
                groupRecords.add(newRecord);
            }
        } else {
            // Read fields shared by all IGMP message versions other than V3 Membership Reports
            maxResponseTime = readBuffer.get();
            if (maxResponseTime >= 128) {
                LOG.warn("parseMessage() - IGMP packet parsed with floating point max response time - CURRENTLY UNSUPPORTED");
            }
            csum = readBuffer.getChar();
            readBuffer.get(ipReadBytes);
            try {
                address = InetAddress.getByAddress(ipReadBytes);
            } catch(Exception e) {
                LOG.error("parseMessage() - Exception getting IP address [{}]", e.getMessage(), e);
            }
            numBytesProcessed += 7;

            // Determine the message type
            if (versionAndType == FLAG_MEMBERSHIP_QUERY && dataLenBytes == 8 && maxResponseTime == 0) {
                messageType = MessageType.MEMBERSHIP_QUERY_V1;
                extra = Arrays.copyOfRange(payloadBytes, numBytesProcessed, payloadBytes.length);
            } else if (versionAndType == FLAG_MEMBERSHIP_QUERY && dataLenBytes == 8 && maxResponseTime != 0) {
                messageType = MessageType.MEMBERSHIP_QUERY_V2;
                extra = Arrays.copyOfRange(payloadBytes, numBytesProcessed, payloadBytes.length);
            } else if (versionAndType == FLAG_MEMBERSHIP_QUERY && dataLenBytes >= 12) {
                messageType = MessageType.MEMBERSHIP_QUERY_V3;
                byte sFlagQrv = 0;
                sFlagQrv = readBuffer.get();
                qqic = readBuffer.get();
                numSources = readBuffer.getChar();
                numBytesProcessed += 4;
                if (qqic >= 128) {
                    LOG.warn("parseMessage() - IGMP packet parsed with floating point qqic - CURRENTLY UNSUPPORTED");
                }
                suppressRouterProcessing = (sFlagQrv & 0x08) > 0 ? true : false;
                qrv = (byte)(sFlagQrv & 0x07);

                for(int i = 0; i < numSources; i++) {
                    InetAddress newSourceAddress = null;
                    readBuffer.get(ipReadBytes);
                    numBytesProcessed += 4;
                    try {
                        newSourceAddress = InetAddress.getByAddress(ipReadBytes);
                        sourceAddresses.add(newSourceAddress);
                    } catch(Exception e) {
                        LOG.error("parseMessage() - Exception getting source IP address [{}]", e.getMessage(), e);
                    }
                }
            } else {
                messageType = MessageType.UNKNOWN_TYPE;
            }
        }

        // TODO: Implement checksum verification once packing is implemented

        extra = Arrays.copyOfRange(payloadBytes, numBytesProcessed, payloadBytes.length);
        return numBytesProcessed;
    }

    public String debugStr() {
        StringBuilder str = new StringBuilder();
        str.append("IGMP Message Type: " + messageType + "\n");
        if (messageType == MessageType.MEMBERSHIP_REPORT_V3) {
            for (IgmpGroupRecord record : groupRecords) {
                str.append(record.debugStr());
            }
        } else if (messageType == MessageType.UNKNOWN_TYPE) {
            str.append("Version and Type Field: " + versionAndType);
        } else {
            str.append("Address: " + address);
        }
        return str.toString();
    }

    public MessageType getMessageType() {
        return messageType;
    }
}
