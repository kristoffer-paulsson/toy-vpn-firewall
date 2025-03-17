package com.example.toyvpn;


import com.example.toyvpn.tcpip.Packet;

import java.net.InetAddress;

public class Connection {


    public String tag;

    public InetAddress local;

    public InetAddress remote;

    public int incomingCount = 0;

    public int outgoingCount = 0;

    public long incomingBytes = 0;

    public long outgoingBytes = 0;

    public static String packetToTag(Packet packet) {
        InetAddress destinationAddress = packet.ip4Header.destinationAddress;
        Packet.TCPHeader tcpHeader = packet.tcpHeader;
        int destinationPort = tcpHeader.destinationPort;
        int sourcePort = tcpHeader.sourcePort;
        return destinationAddress.getHostAddress() + ":" + destinationPort + ":" + sourcePort;
    }
}
