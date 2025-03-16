package com.example.toyvpn;

import static android.content.ContentValues.TAG;

import android.util.Log;

import com.example.toyvpn.tcpip.Packet;
import com.example.toyvpn.tcpip.TCBStatus;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class ConnectionHandler {
    static AtomicInteger connIds = new AtomicInteger(0);
    public final int connId = connIds.addAndGet(1);

    public long mySequenceNum = 0;
    public long theirSequenceNum = 0;
    public long myAcknowledgementNum = 0;
    public long theirAcknowledgementNum = 0;

    public TCBStatus tcbStatus = TCBStatus.SYN_SENT;
    public BlockingQueue<Packet> connectionInputQueue = new ArrayBlockingQueue<Packet>(1024);
    public InetSocketAddress sourceAddress;
    public InetSocketAddress destinationAddress;
    public SocketChannel destSocket;
    private final PassthroughGateway gateway;
    private Selector selector;
    BlockingQueue<ByteBuffer> networkToDeviceQueue;

    public int packId = 1;

    public boolean upActive = true;
    public boolean downActive = true;
    public String connName;

    public SelectionKey selectionKey;

    ConnectionHandler(PassthroughGateway gateway, Packet packet, String ipAndPort) {
        this.gateway = gateway;
        this.sourceAddress = new InetSocketAddress(
                packet.ip4Header.sourceAddress, packet.tcpHeader.sourcePort);
        this.destinationAddress = new InetSocketAddress(
                packet.ip4Header.destinationAddress, packet.tcpHeader.destinationPort);
        connName = ipAndPort;
    }

    public void connectRemote() {
        try {
            //connect
            destSocket = SelectorProvider.provider().openSocketChannel();//SocketChannel.open();
            destSocket.configureBlocking(false);
            gateway.getVpnService().protect(destSocket.socket());

            selectionKey = destSocket.register(gateway.getSelector(), SelectionKey.OP_READ, this);

            Long ts = System.currentTimeMillis();
            destSocket.socket().connect(destinationAddress, 5000);
            Long te = System.currentTimeMillis();

            Log.i(TAG, String.format("connectRemote %d cost %d  remote %s", connId, te - ts, destinationAddress.toString()));

        } catch (IOException e) {
            Log.e(TAG, e.getMessage(), e);
        }
    }
}
