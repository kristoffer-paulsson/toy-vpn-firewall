package com.example.toyvpn;

import android.net.VpnService;
import android.widget.Toast;

import java.nio.channels.FileChannel;


public class BlockGateway implements Gateway {

    @Override
    public VpnService getVpnService() {
        return null;
    }

    @Override
    public FileChannel getVpnInput() {
        return null;
    }

    @Override
    public FileChannel getVpnOutput() {
        return null;
    }

    @Override
    public void startup() {

    }

    @Override
    public void shutdown() {

    }
}
