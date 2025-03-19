package com.example.toyvpn;

public class TcpHandler {

    protected final ConnectionManager connectionManager;

    public TcpHandler(ConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
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
