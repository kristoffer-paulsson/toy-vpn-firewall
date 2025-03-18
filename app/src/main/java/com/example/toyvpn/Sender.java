package com.example.toyvpn;

import com.example.toyvpn.tcpip.IpUtil;
import com.example.toyvpn.tcpip.Packet;
import com.example.toyvpn.tcpip.TCBStatus;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

import kotlin.Triple;


public class Sender {

    private final ConnectionManager connectionManager;

    Sender(ConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    public static final int HEADER_SIZE = Packet.IP4_HEADER_SIZE + Packet.TCP_HEADER_SIZE;

    public String packetToTag(Packet packet) {
        InetAddress destinationAddress = packet.ip4Header.destinationAddress;
        Packet.TCPHeader tcpHeader = packet.tcpHeader;
        //Log.d(TAG, String.format("get pack %d tcp " + tcpHeader.printSimple() + " ", currentPacket.packId));
        int destinationPort = tcpHeader.destinationPort;
        int sourcePort = tcpHeader.sourcePort;
        return destinationAddress.getHostAddress() + ":" + destinationPort + ":" + sourcePort;
    }

    public ConnectionHandler start(
            Packet packet,
            String tag
    ) throws IOException {
        ConnectionHandler handler;
        //if (!connections.containsKey(tag)) {
            handler = new ConnectionHandler(connectionManager);
            handler.sourceAddress = new InetSocketAddress(
                    packet.ip4Header.sourceAddress,
                    packet.tcpHeader.sourcePort
            );
            handler.destinationAddress = new InetSocketAddress(
                    packet.ip4Header.destinationAddress,
                    packet.tcpHeader.destinationPort
            );
            handler.destSocket = SocketChannel.open();
            //objAttrUtil.setAttr(pipe.remote, "type", "remote");
            //objAttrUtil.setAttr(pipe.remote, "pipe", pipe);
            handler.destSocket.configureBlocking(false);
            handler.selectionKey = handler.destSocket.register(
                    connectionManager.getSelector(),
                    SelectionKey.OP_CONNECT,
                    handler
            );
            //objAttrUtil.setAttr(pipe.remote, "key", key);
            //very important, protect
            connectionManager.getVpnService().protect(handler.destSocket.socket());
            boolean b1 = handler.destSocket.connect(handler.destinationAddress);
            handler.timestamp = System.currentTimeMillis();
            //Log.i(TAG, String.format("initPipe %s %s", pipe.destinationAddress, b1));
            handler.tag = tag;
        /*    synchronized (Collections.unmodifiableMap(connections)) {
                connections.put(tag, handler);
            }
        } else {
            synchronized (Collections.unmodifiableMap(connections)) {
                handler = connections.get(tag);
            }
        }*/
        return handler;
    }

    public void sendPacket(ConnectionHandler handler, Packet packet) {
        boolean end = false;
        Packet.TCPHeader tcpHeader = packet.tcpHeader;
        if (tcpHeader.isSYN()) {
            handleSyn(packet, handler);
            end = true;
        }
        if (!end && tcpHeader.isRST()) {
            handleRst(packet, handler);
            return;
        }
        if (!end && tcpHeader.isFIN()) {
            handleFin(packet, handler);
            end = true;
        }
        if (!end && tcpHeader.isACK()) {
            handleAck(packet, handler);
        }
    }

    private void handleSyn(Packet packet, ConnectionHandler handler) {
        if (handler.tcbStatus == TCBStatus.SYN_SENT) {
            handler.tcbStatus = TCBStatus.SYN_RECEIVED;
            //Log.i(TAG, String.format("handleSyn %s %s", pipe.destinationAddress, pipe.tcbStatus));
        }
        //Log.i(TAG, String.format("handleSyn  %d %d", pipe.tunnelId, packet.packId));
        Packet.TCPHeader tcpHeader = packet.tcpHeader;
        if (handler.synCount == 0) {
            handler.mySequenceNum = 1;
            handler.theirSequenceNum = tcpHeader.sequenceNumber;
            handler.myAcknowledgementNum = tcpHeader.sequenceNumber + 1;
            handler.theirAcknowledgementNum = tcpHeader.acknowledgementNumber;
            sendTcpPack(handler, (byte) (Packet.TCPHeader.SYN | Packet.TCPHeader.ACK), null);
        } else {
            handler.myAcknowledgementNum = tcpHeader.sequenceNumber + 1;
        }
        handler.synCount += 1;
    }

    private void handleRst(Packet packet, ConnectionHandler handler) {
        //Log.i(TAG, String.format("handleRst %d", pipe.tunnelId));
        handler.upActive = false;
        handler.downActive = false;
        cleanPipe(handler);
        handler.tcbStatus = TCBStatus.CLOSE_WAIT;
    }

    private void handleFin(Packet packet, ConnectionHandler handler) {
        //Log.i(TAG, String.format("handleFin %d", handler.tunnelId));
        handler.myAcknowledgementNum = packet.tcpHeader.sequenceNumber + 1;
        handler.theirAcknowledgementNum = packet.tcpHeader.acknowledgementNumber;
        // tod
        sendTcpPack(handler, (byte) (Packet.TCPHeader.ACK), null);
        closeUpStream(handler);
        handler.tcbStatus = TCBStatus.CLOSE_WAIT;

        //Log.i(TAG, String.format("handleFin %s %s", handler.destinationAddress, handler.tcbStatus));
    }

    private void handleAck(Packet packet, ConnectionHandler handler) {
        if (handler.tcbStatus == TCBStatus.SYN_RECEIVED) {
            handler.tcbStatus = TCBStatus.ESTABLISHED;

            //Log.i(TAG, String.format("handleAck %s %s", handler.destinationAddress, handler.tcbStatus));
        }

        /*if (Config.logAck) {
            Log.d(TAG, String.format("handleAck %d ", packet.packId));
        }*/

        Packet.TCPHeader tcpHeader = packet.tcpHeader;
        int payloadSize = packet.backingBuffer.remaining();

        if (payloadSize == 0) {
            return;
        }

        long newAck = tcpHeader.sequenceNumber + payloadSize;
        if (newAck <= handler.myAcknowledgementNum) {
            /*if (Config.logAck) {
                Log.d(TAG, String.format("handleAck duplicate ack", pipe.myAcknowledgementNum, newAck));
            }*/
            return;
        }

        handler.myAcknowledgementNum = tcpHeader.sequenceNumber;
        handler.theirAcknowledgementNum = tcpHeader.acknowledgementNumber;

        handler.myAcknowledgementNum += payloadSize;
        //TODO
        handler.remoteOutBuffer.put(packet.backingBuffer);
        handler.remoteOutBuffer.flip();
        tryFlushWrite(handler);
        sendTcpPack(handler, (byte) Packet.TCPHeader.ACK, null);
        System.currentTimeMillis();
    }

    private void sendTcpPack(ConnectionHandler handler, byte flag, byte[] data) {
        int dataLen = 0;
        if (data != null) {
            dataLen = data.length;
        }
        Packet packet = IpUtil.buildTcpPacket(handler.destinationAddress, handler.sourceAddress, flag,
                handler.myAcknowledgementNum, handler.mySequenceNum, handler.packId);
        handler.packId += 1;
        ByteBuffer byteBuffer = ByteBuffer.allocate(HEADER_SIZE + dataLen);
        //
        byteBuffer.position(HEADER_SIZE);
        if (data != null) {
            if (byteBuffer.remaining() < data.length) {
                System.currentTimeMillis();
            }
            byteBuffer.put(data);
        }
        //
        packet.updateTCPBuffer(byteBuffer, flag, handler.mySequenceNum, handler.myAcknowledgementNum, dataLen);
        byteBuffer.position(HEADER_SIZE + dataLen);

        connectionManager.getRemoteInput().offer(new Triple<>(byteBuffer, handler.tag, Gateway.CONN_OP.SEND));

        if ((flag & (byte) Packet.TCPHeader.SYN) != 0) {
            handler.mySequenceNum += 1;
        }
        if ((flag & (byte) Packet.TCPHeader.FIN) != 0) {
            handler.mySequenceNum += 1;
        }
        if ((flag & (byte) Packet.TCPHeader.ACK) != 0) {
            handler.mySequenceNum += dataLen;
        }
    }

    private boolean tryFlushWrite(ConnectionHandler handler) {
        try {
            ByteBuffer buffer = handler.remoteOutBuffer;
            if (handler.destSocket.socket().isOutputShutdown() && buffer.remaining() != 0) {
                sendTcpPack(handler, (byte) (Packet.TCPHeader.FIN | Packet.TCPHeader.ACK), null);
                buffer.compact();
                return false;
            }
            if (!handler.destSocket.isConnected()) {
                //Log.i(TAG, "not yet connected");
                SelectionKey key = handler.selectionKey;
                int ops = key.interestOps() | SelectionKey.OP_WRITE;
                key.interestOps(ops);
                System.currentTimeMillis();
                buffer.compact();
                return false;
            }
            while (buffer.hasRemaining()) {
                int n = 0;
                n = handler.destSocket.write(buffer);
                if (n > 4000) {
                    System.currentTimeMillis();
                }
                //Log.i(TAG, String.format("tryFlushWrite write %s", n));
                if (n <= 0) {
                    //Log.i(TAG, "write fail");
                    //
                    SelectionKey key = handler.selectionKey;
                    int ops = key.interestOps() | SelectionKey.OP_WRITE;
                    key.interestOps(ops);
                    System.currentTimeMillis();
                    buffer.compact();
                    return false;
                }
            }
            buffer.clear();
            if (!handler.upActive) {
                handler.destSocket.shutdownOutput();
            }
            return true;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void closeUpStream(ConnectionHandler handler) {
        //Log.i(TAG, String.format("closeUpStream %d", pipe.tunnelId));
        try {
            if (handler.destSocket != null && handler.destSocket.isOpen()) {
                if (handler.destSocket.isConnected()) {
                    handler.destSocket.shutdownOutput();
                }

            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        //Log.i(TAG, String.format("closeUpStream %d", pipe.tunnelId));
        handler.upActive = false;

        if (isClosedTunnel(handler)) {
            cleanPipe(handler);
        }
    }

    private static boolean isClosedTunnel(ConnectionHandler handler) {
        return !handler.upActive && !handler.downActive;
    }

    private static void cleanPipe(ConnectionHandler handler) {
        try {
            if (handler.destSocket != null && handler.destSocket.isOpen()) {
                handler.destSocket.close();
            }
            handler.cleanup();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
