/*
 * Copyright © 2016 Alexander Craig and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.carleton.bbnlab.bloomflow.impl;

import java.net.InetAddress;
import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author mininet
 *
 */
public class PacketUtils {
    private static final Logger LOG = LoggerFactory.getLogger(PacketUtils.class);

    private final static int PACKET_OFFSET_ETHERTYPE = 12;
    private final static int PACKET_OFFSET_IP = 14;
    private final static int PACKET_OFFSET_IP_SRC = PACKET_OFFSET_IP+12;
    private final static int PACKET_OFFSET_IP_DST = PACKET_OFFSET_IP+16;

    public static final int ETHERTYPE_IPV4 = 0x0800;
    public static final int ETHERTYPE_IPV4_W_VLAN = 0x8100;
    public static final int ETHERTYPE_ARP = 0x0806;
    public static final int ETHERTYPE_LLDP = 0x88CC;

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

}
