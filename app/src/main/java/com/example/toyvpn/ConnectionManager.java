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

    ConnectionManager() {}

    static class RemoteWorker implements Runnable {

        private final ConcurrentHashMap<String, ConnectionHandler> connections = new ConcurrentHashMap<>();

        private VpnService vpnService;

        private BlockingQueue<Triple<ByteBuffer, String, Gateway.CONN_OP>> remoteInput;

        private final BlockingQueue<Triple<Packet, String, Gateway.CONN_OP>> remoteOutput;

        private final Selector selector;

        RemoteWorker(
                VpnService vpnService,
                BlockingQueue<Triple<ByteBuffer, String, Gateway.CONN_OP>> remoteInput,
                BlockingQueue<Triple<Packet, String, Gateway.CONN_OP>> remoteOutput
        ) {
            this.vpnService = vpnService;
            this.remoteInput = remoteInput;
            this.remoteOutput = remoteOutput;
            try {
                this.selector = SelectorProvider.provider().openSelector();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void run() {
            while (!Thread.interrupted()) {
                try {
                    Triple<Packet, String, Gateway.CONN_OP> triple = remoteOutput.take();
                    Gateway.CONN_OP type = triple.getThird();
                    if(type == Gateway.CONN_OP.CLOSE) {
                        // TODO "Close opeend connections when prompted."
                    } else if (type == Gateway.CONN_OP.OPEN) {
                        // TODO "Open new connection if required."
                    }
                    // TODO "Just dispatch the package on a handler"
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }
}
