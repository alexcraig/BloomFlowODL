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
import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author mininet
 *
 */
public class PacketUtils {
    private static final Logger LOG = LoggerFactory.getLogger(PacketUtils.class);

    public static final int ETHERNET_HEADER_LEN = 14;    // Assumes no VLAN tag
    public static final int ETHERNET_CHECKSUM_LEN = 4;
    public static final int IPV4_HEADER_LEN = 20; // Assumes no content in options field

    public static final int ETHERTYPE_IPV4 = 0x0800;
    public static final int ETHERTYPE_IPV4_W_VLAN = 0x8100;
    public static final int ETHERTYPE_ARP = 0x0806;
    public static final int ETHERTYPE_LLDP = 0x88CC;

    public static final int IP_PROTO_IGMP = 0x02;

    // Note: IP offset constants assume no VLAN tag
    private final static int PACKET_OFFSET_ETHERTYPE = 12;
    private final static int PACKET_OFFSET_IP = 14;
    private final static int PACKET_OFFSET_IP_SRC = PACKET_OFFSET_IP + 12;
    private final static int PACKET_OFFSET_IP_DST = PACKET_OFFSET_IP + 16;
    private final static int PACKET_OFFSET_IP_PROTO = PACKET_OFFSET_IP + 9;


    private PacketUtils() {
        // Disable instantiation of this class
    }

    public static char getEtherType(final byte[] rawPacket) {
        final byte[] etherTypeBytes = Arrays.copyOfRange(rawPacket, PACKET_OFFSET_ETHERTYPE, PACKET_OFFSET_ETHERTYPE+2);
        return packChar(etherTypeBytes);
    }

    public static byte[] getEtherTypeBytes(final byte[] rawPacket) {
        return Arrays.copyOfRange(rawPacket, PACKET_OFFSET_ETHERTYPE, PACKET_OFFSET_ETHERTYPE+2);
    }

    public static byte getIpVersion(final byte[] rawPacket)
    {
        final byte[] ipVersionBytes = Arrays.copyOfRange(rawPacket, PACKET_OFFSET_IP, PACKET_OFFSET_IP + 1);
        return (byte)(ipVersionBytes[0] >> 4);
    }

    public static int getIpHeaderLengthBytes(final byte[] rawPacket)
    {
        final byte[] ipVersionBytes = Arrays.copyOfRange(rawPacket, PACKET_OFFSET_IP, PACKET_OFFSET_IP + 1);
        return (ipVersionBytes[0] & 0x0F) * 4;
    }

    public static byte getIpProtocol(final byte[] rawPacket)
    {
        final byte[] ipProtoBytes = Arrays.copyOfRange(rawPacket, PACKET_OFFSET_IP_PROTO, PACKET_OFFSET_IP_PROTO + 1);
        return ipProtoBytes[0];
    }

    public static String getSrcIpStr(final byte[] rawPacket) {
        final byte[] ipSrcBytes = Arrays.copyOfRange(rawPacket, PACKET_OFFSET_IP_SRC, PACKET_OFFSET_IP_SRC+4);
        String pktSrcIpStr = null;
        try {
            pktSrcIpStr = InetAddress.getByAddress(ipSrcBytes).getHostAddress();
        } catch(Exception e) {
            LOG.error("Exception getting Src IP address [{}]", e.getMessage(), e);
        }

        return pktSrcIpStr;
    }

    public static String getDstIpStr(final byte[] rawPacket) {
        final byte[] ipDstBytes = Arrays.copyOfRange(rawPacket, PACKET_OFFSET_IP_DST, PACKET_OFFSET_IP_DST+4);
        String pktDstIpStr = null;
        try {
            pktDstIpStr = InetAddress.getByAddress(ipDstBytes).getHostAddress();
        } catch(Exception e) {
            LOG.error("Exception getting Dst IP address [{}]", e.getMessage(), e);
        }

        return pktDstIpStr;
    }

    public static short packShort(byte[] bytes) {
        short val = (short) 0;
        for (int i = 0; i < 2; i++) {
          val <<= 8;
          val |= bytes[i] & 0xff;
        }

        return val;
    }

    public static char packChar(byte[] bytes) {
        char val = (short) 0;
        for (int i = 0; i < 2; i++) {
          val <<= 8;
          val |= bytes[i] & 0xff;
        }

        return val;
    }

    public static int packInt(byte[] bytes) {
        return ByteBuffer.wrap(bytes).getInt();
    }

    public static String byteString(byte[] bytes, int length) {
        String returnStr = "";
        for(int i = 0; i < length; i++) {
            returnStr += String.format("%02x ", bytes[i]);
        }
        return returnStr;
    }

}
