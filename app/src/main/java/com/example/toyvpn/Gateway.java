package com.example.toyvpn;

import android.net.VpnService;
import java.nio.channels.FileChannel;

public interface Gateway {

    public VpnService getVpnService();

    public FileChannel getVpnInput();

    public FileChannel getVpnOutput();

    public void startup();

    public void shutdown();
}
