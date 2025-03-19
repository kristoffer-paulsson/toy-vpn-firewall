package com.example.toyvpn;

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
