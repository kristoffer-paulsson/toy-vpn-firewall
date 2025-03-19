package com.example.toyvpn;

import static com.example.toyvpn.Sender.HEADER_SIZE;

import com.example.toyvpn.tcpip.IpUtil;
import com.example.toyvpn.tcpip.Packet;
import com.example.toyvpn.tcpip.TCBStatus;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;

import kotlin.Triple;

public class Receiver extends TcpHandler {

    Receiver(ConnectionManager connectionManager) {
        super(connectionManager);
    }

    public void doAccept(ServerSocketChannel serverChannel) throws Exception {
        throw new RuntimeException("");
    }

    public void doRead(ConnectionHandler handler) throws Exception {
        ByteBuffer buffer = ByteBuffer.allocate(4 * 1024);
        String quitType = "";

        //NioSingleThreadTcpHandler.TcpPipe pipe = (NioSingleThreadTcpHandler.TcpPipe) objAttrUtil.getAttr(handler, "pipe");

        while (true) {
            buffer.clear();
            int n = BioUtil.read(handler.destSocket, buffer);
            //Log.i(TAG, String.format("read %s", n));
            if (n == -1) {
                quitType = "fin";
                break;
            } else if (n == 0) {
                break;
            } else {
                if (handler.tcbStatus != TCBStatus.CLOSE_WAIT) {
                    buffer.flip();
                    byte[] data = new byte[buffer.remaining()];
                    buffer.get(data);
                    sendTcpPack(handler, (byte) (Packet.TCPHeader.ACK), data);
                }
            }
        }
        if (quitType.equals("fin")) {
            closeDownStream(handler);
        }
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

    public void doConnect(ConnectionHandler handler) throws Exception {
        //Log.i(TAG, String.format("tick %s", tick));
        //
        //String type = (String) objAttrUtil.getAttr(handler, "type");
        //NioSingleThreadTcpHandler.TcpPipe pipe = (NioSingleThreadTcpHandler.TcpPipe) objAttrUtil.getAttr(handler, "pipe");
        //SelectionKey key = (SelectionKey) objAttrUtil.getAttr(handler, "key");
        //if (type.equals("remote")) {
        boolean b1 = handler.destSocket.finishConnect();
        //Log.i(TAG, String.format("connect %s %s %s", pipe.destinationAddress, b1,System.currentTimeMillis()-pipe.timestamp));
        handler.timestamp=System.currentTimeMillis();
        handler.remoteOutBuffer.flip();
        handler.selectionKey.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
        //}

    }

    public void doWrite(ConnectionHandler handler) throws Exception {
        //Log.i(TAG, String.format("tick %s", tick));
        //NioSingleThreadTcpHandler.TcpPipe pipe = (NioSingleThreadTcpHandler.TcpPipe) objAttrUtil.getAttr(socketChannel, "pipe");
        boolean flushed = tryFlushWrite(handler);
        if (flushed) {
            //SelectionKey key1 = (SelectionKey) objAttrUtil.getAttr(socketChannel, "key");
            handler.selectionKey.interestOps(SelectionKey.OP_READ);
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

    public void closeRst(ConnectionHandler handler) {
        //Log.i(TAG, String.format("closeRst %d", handler.tunnelId));
        cleanPipe(handler);
        sendTcpPack(handler, (byte) Packet.TCPHeader.RST, null);
        handler.upActive = false;
        handler.downActive = false;
    }

    private void closeDownStream(ConnectionHandler handler) throws Exception {
        //Log.i(TAG, String.format("closeDownStream %d", pipe.tunnelId));
        if (handler.destSocket != null && handler.destSocket.isConnected()) {
            handler.destSocket.shutdownInput();
            int ops = handler.selectionKey.interestOps() & (~SelectionKey.OP_READ);
            handler.selectionKey.interestOps(ops);
        }

        sendTcpPack(handler, (byte) (Packet.TCPHeader.FIN | Packet.TCPHeader.ACK), null);
        handler.downActive = false;
        if (isClosedTunnel(handler)) {
            cleanPipe(handler);
        }
    }
}
