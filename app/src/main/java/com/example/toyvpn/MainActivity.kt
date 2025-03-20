package com.example.toyvpn

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private var vpnServiceIntent: Intent? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val buttonConnect: Button = findViewById(R.id.button_connect)
        val buttonDisconnect: Button = findViewById(R.id.button_disconnect)

        buttonConnect.setOnClickListener {
            startVpnService()
        }

        buttonDisconnect.setOnClickListener {
            stopVpnService()
        }
    }

    private fun startVpnService() {
        vpnServiceIntent = Intent(this, MyVpnService::class.java)
        val prepareIntent = VpnService.prepare(this)
        if (prepareIntent != null) {
            startActivityForResult(prepareIntent, 0)
        } else {
            onActivityResult(0, RESULT_OK, null)
        }
    }

    private fun stopVpnService() {
        vpnServiceIntent?.let {
            stopService(it)
            Toast.makeText(this, "Disconnected", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK) {
            vpnServiceIntent?.let {
                startService(it)
                Toast.makeText(this, "Connected", Toast.LENGTH_SHORT).show()
            }
        }
    }
}