package com.example.toyvpn;

import android.app.PendingIntent;
import android.content.Intent;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.concurrent.ExecutorService;



public class PassthroughVpnService extends VpnService {
    private static final String TAG = PassthroughVpnService.class.getSimpleName();
    private static final String VPN_ADDRESS = "10.0.0.2"; // Only IPv4 support for now
    private static final String VPN_ROUTE = "0.0.0.0"; // Intercept everything

    //private static final String[] DNS = {"8.8.8.8", "8.8.4.4"};


    private ParcelFileDescriptor vpnInterface = null;
    private PendingIntent pendingIntent;

    private ExecutorService executorService;

    @Override
    public void onCreate() {
        super.onCreate();
        setupVPN();

        Log.i(TAG, "Started");
    }

    private void setupVPN() {
        try {
            if (vpnInterface == null) {
                Builder builder = new Builder();
                builder.addAddress(VPN_ADDRESS, 32);
                builder.addRoute(VPN_ROUTE, 0);
                //builder.addDnsServer(Config.dns);
                /*if (Config.testLocal) {
                    builder.addAllowedApplication("com.example.toyvpn");
                }*/
                vpnInterface = builder
                        .setSession(getString(R.string.app_name))
                        .setConfigureIntent(pendingIntent)
                        .establish();
            }
        } catch (Exception e) {
            Log.e(TAG, "error", e);
            System.exit(0);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        Log.i(TAG, "Stopped");
    }

    public static String getLocalIpAddress() {
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress()) {
                        return inetAddress.getHostAddress().toString();
                    }
                }
            }
        } catch (SocketException ex) {
            Log.e("SocketException", ex.toString());
        }
        return null;
    }
}
