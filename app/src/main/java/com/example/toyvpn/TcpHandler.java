package com.example.toyvpn;

import static com.example.toyvpn.Sender.HEADER_SIZE;

import com.example.toyvpn.tcpip.IpUtil;
import com.example.toyvpn.tcpip.Packet;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;

import kotlin.Triple;

public class TcpHandler {

    protected final ConnectionManager connectionManager;

    public TcpHandler(ConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    protected void sendTcpPack(ConnectionHandler handler, byte flag, byte[] data) {
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

    protected boolean tryFlushWrite(ConnectionHandler handler) {
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

    protected static boolean isClosedTunnel(ConnectionHandler handler) {
        return !handler.upActive && !handler.downActive;
    }

    protected static void cleanPipe(ConnectionHandler handler) {
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
