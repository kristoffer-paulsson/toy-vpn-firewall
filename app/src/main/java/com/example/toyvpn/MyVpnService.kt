package com.example.toyvpn

import android.net.VpnService
import android.content.Intent
import android.os.ParcelFileDescriptor
import android.util.Log
import android.widget.Toast
import java.net.NetworkInterface
import java.net.SocketException

class MyVpnService : VpnService() {
    private var vpnInterface: ParcelFileDescriptor? = null

    private var gateway: PassthroughGateway? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Handle start command
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        vpnInterface?.close()
    }

    fun startVpn() {
        val builder = Builder()
        // Configure the VPN interface
        vpnInterface = builder.setSession("MyVPN")
            .addAddress("10.0.0.2", 24)
            .addRoute("0.0.0.0", 0)
            .setSession(getString(R.string.app_name))
            .setConfigureIntent(null!!)
            .establish()

        gateway = PassthroughGateway(this, vpnInterface)
        gateway!!.startup()
    }

    fun stopVpn() {
        gateway!!.shutdown()
        vpnInterface?.close()
        vpnInterface = null
    }

    companion object {
        fun getLocalIpAddress(): String? {
            try {
                val en = NetworkInterface.getNetworkInterfaces()
                while (en.hasMoreElements()) {
                    val intf = en.nextElement()
                    val enumIpAddr = intf.inetAddresses
                    while (enumIpAddr.hasMoreElements()) {
                        val inetAddress = enumIpAddr.nextElement()
                        if (!inetAddress.isLoopbackAddress) {
                            return inetAddress.hostAddress
                        }
                    }
                }
            } catch (ex: SocketException) {
                Log.e("SocketException", ex.toString())
            }
            return null
        }
    }
}