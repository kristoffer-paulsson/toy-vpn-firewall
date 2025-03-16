package com.example.toyvpn;

import android.net.VpnService;
import android.os.ParcelFileDescriptor;

import com.example.toyvpn.tcpip.Packet;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.spi.SelectorProvider;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PassthroughGateway implements Gateway {

    private final ConcurrentHashMap<String, ConnectionHandler> conn = new ConcurrentHashMap<>();

    private final VpnService vpnService;

    private final ParcelFileDescriptor vpnFileDescriptor;

    private FileChannel vpnInput = null;

    private FileChannel vpnOutput = null;

    private Selector selector = null;

    private ExecutorService executorService;


    PassthroughGateway(VpnService vpnService, ParcelFileDescriptor vpnFileDescriptor){
        this.vpnService = vpnService;
        this.vpnFileDescriptor = vpnFileDescriptor;
    }

    public void sendPacket(ConnectionHandler handler, Packet packet) {
        synchronized (handler) {

        }
    }

    public void receivePacket(ConnectionHandler handler) {
        synchronized (handler) {

        }
    }

    /*public void cancel() {
        try {
            destSocket.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            selectionKey.cancel();
        }
    }*/

    @Override
    public void startup() {
        FileDescriptor descriptor = vpnFileDescriptor.getFileDescriptor();
        vpnInput = new FileInputStream(descriptor).getChannel();
        vpnOutput = new FileOutputStream(descriptor).getChannel();

        try {
            this.selector = SelectorProvider.provider().openSelector();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        executorService = Executors.newFixedThreadPool(2);
        executorService.execute(new DownStreamWorker(this));
        executorService.execute(new UpStreamWorker(this));
    }

    @Override
    public void shutdown() {
        try {
            vpnFileDescriptor.close();

            vpnInput.close();
            vpnOutput.close();
            selector.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        executorService.shutdown();
    }

    public VpnService getVpnService() {
        return vpnService;
    }

    public Selector getSelector() {
        return selector;
    }

    public FileChannel getVpnInput() {
        return vpnInput;
    }

    public FileChannel getVpnOutput() {
        return vpnOutput;
    }

    public static String getIpAndPort(Packet packet) {
        InetAddress destinationAddress = packet.ip4Header.destinationAddress;
        Packet.TCPHeader tcpHeader = packet.tcpHeader;
        //Log.d(TAG, String.format("get pack %d tcp " + tcpHeader.printSimple() + " ", currentPacket.packId));
        int destinationPort = tcpHeader.destinationPort;
        int sourcePort = tcpHeader.sourcePort;
        return destinationAddress.getHostAddress() + ":" + destinationPort + ":" + sourcePort;
    }

    static class UpStreamWorker implements Runnable {

        private final PassthroughGateway gateway;

        UpStreamWorker(PassthroughGateway gateway) {
            this.gateway = gateway;
        }

        @Override
        public void run() {
            FileChannel vpnInput = gateway.vpnInput;
            ConcurrentHashMap<String, ConnectionHandler> conn = gateway.conn;

            ByteBuffer bufferToNetwork = ByteBuffer.allocateDirect(16384);

            while (!Thread.interrupted()) {
                bufferToNetwork.clear();
                try {
                    int readBytes = vpnInput.read(bufferToNetwork);
                    if(readBytes > 0) {
                        bufferToNetwork.flip();
                        Packet packet = new Packet(bufferToNetwork);

                        if(!packet.isTCP())
                            continue;

                        String ipAndPort = getIpAndPort(packet);
                        synchronized (conn) {
                            if(!conn.containsKey(ipAndPort)) {
                                ConnectionHandler connectionHandler = new ConnectionHandler(gateway, packet, ipAndPort);
                                connectionHandler.connectRemote();
                                conn.put(ipAndPort, connectionHandler);
                            }
                            gateway.sendPacket(Objects.requireNonNull(conn.get(ipAndPort)), packet);
                        }
                    } else {
                        try {
                            Thread.sleep(10);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    static class DownStreamWorker implements Runnable {

        private final PassthroughGateway gateway;

        DownStreamWorker(PassthroughGateway gateway) {
            this.gateway = gateway;
        }

        @Override
        public void run() {
            Selector selector = gateway.selector;
            while (!Thread.interrupted()) {
                try {
                    int n = selector.select();

                    Set<SelectionKey> selectedKeys = selector.selectedKeys();
                    Iterator<SelectionKey> keyIterator = selectedKeys.iterator();

                    while (keyIterator.hasNext()) {
                        SelectionKey key = keyIterator.next();
                        if(key.isReadable()) {
                            gateway.receivePacket((ConnectionHandler) key.attachment());
                        }
                        keyIterator.remove();
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }
}
