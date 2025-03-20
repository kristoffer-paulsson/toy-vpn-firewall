/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.toyvpn;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.VpnService;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.widget.Toast;

import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;


public class ToyVpnService extends VpnService implements Handler.Callback {
    private static final String TAG = ToyVpnService.class.getSimpleName();
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private ParcelFileDescriptor vpnFileDescriptor = null;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private static ToyVpnService instance;


    private final String NOTIFICATION_CHANNEL_ID = "ToyVpn";

    private static final String VPN_ADDRESS = "10.0.0.2"; // Only IPv4 support for now

    private static final String VPN_ROUTE = "0.0.0.0"; // Intercept everything

    public static final String ACTION_CONNECT = "com.example.toyvpn.START";

    public static final String ACTION_DISCONNECT = "com.example.toyvpn.STOP";

    private PendingIntent pendingIntent = null;

    private NotificationManager notificationManager;

    public static String getLocalIpAddress() {
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress()) {
                        return inetAddress.getHostAddress();
                    }
                }
            }
        } catch (SocketException ex) {
            Log.e("SocketException", ex.toString());
        }
        return null;
    }

    private void setupVPN() {
        try {
            if (vpnFileDescriptor != null) {
                Builder builder = new Builder();
                builder.addAddress(Objects.requireNonNull(getLocalIpAddress()), 32); // VPN_ADDRESS
                builder.addRoute(VPN_ROUTE, 0);
                //builder.addDnsServer(Config.dns);
                //if (Config.testLocal) {builder.addAllowedApplication("com.mocyx.basic_client");}
                vpnFileDescriptor = builder
                        .setSession(getString(R.string.app_name))
                        .setConfigureIntent(null)
                        .establish();
            } else {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(ToyVpnService.this, "Vpn connection already established", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        } catch (Exception e) {
            Log.e(TAG, "error", e);
            System.exit(0);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        notificationManager = (NotificationManager) getSystemService(
                NOTIFICATION_SERVICE);
        notificationManager.createNotificationChannel(new NotificationChannel(
                NOTIFICATION_CHANNEL_ID, NOTIFICATION_CHANNEL_ID,
                NotificationManager.IMPORTANCE_DEFAULT));
        // The handler is only used to show messages.
        //if (mHandler == null) {
        //    mHandler = new Handler(this);
        //}

        // Create the intent to "configure" the connection (just start ToyVpnClient).
        pendingIntent = PendingIntent.getActivity(this, 0, new Intent(this, ToyVpnClient.class),
                PendingIntent.FLAG_UPDATE_CURRENT);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Toast.makeText(this, intent.getAction(), Toast.LENGTH_SHORT).show();
        if (intent != null && ACTION_DISCONNECT.equals(intent.getAction())) {
            disconnect();
            return START_NOT_STICKY;
        } else {
            connect();
            return START_STICKY;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        disconnect();
    }

    @Override
    public boolean handleMessage(Message message) {
        Toast.makeText(this, message.what, Toast.LENGTH_SHORT).show();
        //if (message.what != R.string.disconnected) {
        //    updateForegroundNotification(message.what);
        //}
        return true;
    }

    private void connect() {
        final SharedPreferences prefs = getSharedPreferences(ToyVpnClient.Prefs.NAME, MODE_PRIVATE);
        //updateForegroundNotification(R.string.connecting);
        startForeground(1, new Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_vpn)
                .setContentText(getString(R.string.connecting))
                .setContentIntent(pendingIntent)
                .build());


        Toast.makeText(this, "Connect service!", Toast.LENGTH_SHORT).show();

        // Become a foreground service. Background services can be VPN services too, but they can
        // be killed by background check before getting a chance to receive onRevoke().
        //mHandler.sendEmptyMessage(R.string.connecting);

        // Extract information from the shared preferences.
        //startConnection(new ToyVpnConnection(this, mNextConnectionId.getAndIncrement()));
    }


    private void disconnect() {
        Toast.makeText(this, "Disconnect service!", Toast.LENGTH_SHORT).show();
        try {
            vpnFileDescriptor.close();
        } catch (IOException e) {
            Log.e(TAG, Objects.requireNonNull(e.getMessage()));
        }
        stopForeground(true);
    }
}
