package com.example.toyvpn.tcpip;

import java.nio.ByteBuffer;

public class ByteBufferPool {
    private static final int BUFFER_SIZE = 16384; // XXX: Is this ideal?

    public static ByteBuffer acquire() {
        return ByteBuffer.allocate(BUFFER_SIZE);
    }
}

