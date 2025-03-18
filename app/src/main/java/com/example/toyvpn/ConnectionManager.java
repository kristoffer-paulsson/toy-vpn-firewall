package com.example.toyvpn;

import android.net.VpnService;

import com.example.toyvpn.tcpip.Packet;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Selector;
import java.nio.channels.spi.SelectorProvider;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;

import kotlin.Triple;

public class ConnectionManager {

    private final ConcurrentHashMap<String, ConnectionHandler> connections = new ConcurrentHashMap<>();

    private final VpnService vpnService;

    private final BlockingQueue<Triple<ByteBuffer, String, Gateway.CONN_OP>> remoteInput;

    private final BlockingQueue<Triple<Packet, String, Gateway.CONN_OP>> remoteOutput;

    private Selector selector = null;

    private Sender sender = null;

    public ConcurrentHashMap<String, ConnectionHandler> getConnections() {
        return connections;
    }

    public VpnService getVpnService() {
        return vpnService;
    }

    public BlockingQueue<Triple<ByteBuffer, String, Gateway.CONN_OP>> getRemoteInput() {
        return remoteInput;
    }

    public Selector getSelector() {
        return selector;
    }

    public Sender getSender() {
        return sender;
    }

    ConnectionManager(
            VpnService vpnService,
            BlockingQueue<Triple<ByteBuffer, String, Gateway.CONN_OP>> remoteInput,
            BlockingQueue<Triple<Packet, String, Gateway.CONN_OP>> remoteOutput
    ) {
        this.vpnService = vpnService;
        this.remoteInput = remoteInput;
        this.remoteOutput = remoteOutput;

    }

    private void setup() {
        try {
            if(selector == null)
                this.selector = SelectorProvider.provider().openSelector();
            if(sender == null)
                this.sender = new Sender(this);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public ConnectionWorker getConnectionWorker() {
        setup();
        return new ConnectionWorker(this);
    }

    static class ConnectionWorker implements Runnable {

        private final ConnectionManager connectionManager;

        ConnectionWorker(ConnectionManager connectionManager) {
            this.connectionManager = connectionManager;
        }

        @Override
        public void run() {
            Sender sender = connectionManager.getSender();
            while (!Thread.interrupted()) {
                try {
                    Triple<Packet, String, Gateway.CONN_OP> triple = connectionManager.remoteOutput.take();
                    String tag = triple.getSecond();
                    Gateway.CONN_OP type = triple.getThird();
                    ConnectionHandler handler;
                    ConcurrentHashMap<String, ConnectionHandler> connections = connectionManager.getConnections();

                    if(type == Gateway.CONN_OP.CLOSE) {
                        synchronized (connections) {
                            handler = connections.get(tag);
                        }
                        // TODO "closeRst package"
                    } else if(type == Gateway.CONN_OP.SEND) {
                        synchronized (connections) {
                            handler = connections.get(tag);
                        }
                        sender.sendPacket(handler, triple.getFirst());
                    } else if (type == Gateway.CONN_OP.OPEN) {
                        handler = sender.start(triple.getFirst(), tag);
                        synchronized (connections) {
                            connections.put(tag, handler);
                        }
                        sender.sendPacket(handler, triple.getFirst());
                    }
                    connectionManager.selector.wakeup();
                } catch (InterruptedException | IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    public SocketWorker getSocketWorker() {
        setup();
        return new SocketWorker(this);
    }

    static class SocketWorker implements Runnable {

        private final ConnectionManager connectionManager;

        SocketWorker(ConnectionManager connectionManager) {
            this.connectionManager = connectionManager;
        }

        @Override
        public void run() {
            Sender sender = connectionManager.getSender();
            while (!Thread.interrupted()) {

            }
        }
    }
}
