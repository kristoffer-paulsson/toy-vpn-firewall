package com.example.toyvpn;

import android.net.VpnService;
import android.os.ParcelFileDescriptor;

import com.example.toyvpn.tcpip.Packet;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.Selector;
import java.util.Enumeration;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

import kotlin.Triple;

public class PassthroughGateway implements Gateway {

    private final ConcurrentHashMap<String, Connection> connections = new ConcurrentHashMap<>();

    private ConnectionManager connectionManager;

    private final VpnService vpnService;

    private final ParcelFileDescriptor vpnFileDescriptor;

    private FileChannel vpnInput;

    private FileChannel vpnOutput;

    private final BlockingQueue<Triple<ByteBuffer, String, CONN_OP>> remoteInput = new LinkedBlockingQueue<>();

    private final BlockingQueue<Triple<Packet, String, CONN_OP>> remoteOutput = new LinkedBlockingQueue<>();

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

    @Override
    public void startup() {
        FileDescriptor descriptor = vpnFileDescriptor.getFileDescriptor();
        vpnInput = new FileInputStream(descriptor).getChannel();
        vpnOutput = new FileOutputStream(descriptor).getChannel();

        connectionManager = new ConnectionManager();

        executorService = Executors.newFixedThreadPool(2);
        executorService.execute(new LocalWorker(this));
        executorService.execute(new ConnectionManager.RemoteWorker(vpnService, remoteInput, remoteOutput));
    }

    @Override
    public void shutdown() {
        Enumeration<String> tags = connections.keys();
        while (tags.hasMoreElements()) {
            closeHandler(tags.nextElement());
        }

        try {
            vpnFileDescriptor.close();

            vpnInput.close();
            vpnOutput.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        executorService.shutdown();
    }

    public void closeHandler(String tag) {
        remoteOutput.offer(new Triple<>(new Packet(), tag, CONN_OP.CLOSE));
    }

    public VpnService getVpnService() {
        return vpnService;
    }

    public FileChannel getVpnInput() {
        return vpnInput;
    }

    public FileChannel getVpnOutput() {
        return vpnOutput;
    }

    static class LocalWorker implements Runnable {

        private final PassthroughGateway gateway;

        LocalWorker(PassthroughGateway gateway) {
            this.gateway = gateway;
        }

        @Override
        public void run() {
            ByteBuffer output = ByteBuffer.allocateDirect(1 << 14);
            while (!Thread.interrupted()) {
                try {
                    boolean again = false;
                    if(!gateway.remoteInput.isEmpty()) {
                        Triple<ByteBuffer, String, CONN_OP> triple = gateway.remoteInput.take();
                        String tag = triple.getSecond();
                        if(gateway.connections.containsKey(tag)){
                            Connection conn = Objects.requireNonNull(
                                    gateway.connections.get(tag)
                            );
                            ByteBuffer throughPut = triple.getFirst();
                            throughPut.flip();
                            int writtenBytes = 0;
                            while (throughPut.hasRemaining()) {
                                writtenBytes += gateway.vpnOutput.write(throughPut);
                            }
                            conn.incomingBytes += writtenBytes;
                            if(writtenBytes > 0) conn.incomingCount++;
                            if(triple.getThird() == CONN_OP.CLOSE) {
                                gateway.connections.remove(triple.getSecond());
                            }
                        } else {
                            // TODO "Security event, connection non-existent"
                        }
                        again = true;
                    }

                    output.clear();
                    int readBytes = 0;
                    if ((readBytes = gateway.vpnInput.read(output)) > 0) {
                        output.flip();
                        Packet packet = new Packet(output.duplicate());

                        if(packet.isTCP()) {
                            String tag = Connection.packetToTag(packet);
                            Connection conn;
                            CONN_OP type;
                            if(gateway.connections.containsKey(tag)){
                                conn = Objects.requireNonNull(gateway.connections.get(tag));
                                type = CONN_OP.SEND;
                            } else {
                                conn = new Connection();
                                conn.tag = tag;
                                conn.remote = packet.ip4Header.destinationAddress;
                                conn.local = packet.ip4Header.sourceAddress;
                                gateway.connections.put(tag, conn);
                                type = CONN_OP.OPEN;
                            }
                            conn.outgoingBytes += readBytes;
                            conn.outgoingCount++;
                            gateway.remoteOutput.offer(new Triple<>(packet, tag, type));
                        } else {
                            // TODO "Non-TCP packages currently silently dropped"
                        }
                        again = true;
                    }

                    if(!again) {
                        Thread.sleep(50);
                    }
                } catch (InterruptedException | IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }
}
