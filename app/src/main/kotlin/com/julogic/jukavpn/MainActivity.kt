package com.julogic.jukavpn

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.julogic.jukavpn.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnConnect.setOnClickListener {
            val intent = VpnService.prepare(this)
            if (intent != null) {
                startActivityForResult(intent, 0)
            } else {
                startVpn()
            }
        }

        binding.btnDisconnect.setOnClickListener {
            stopVpn()
        }
    }

    private fun startVpn() {
        val intent = Intent(this, V2rayVpnService::class.java)
        startService(intent)
        binding.tvStatus.text = "VPN Conectado"
    }

    private fun stopVpn() {
        val intent = Intent(this, V2rayVpnService::class.java)
        stopService(intent)
        binding.tvStatus.text = "VPN Desconectado"
    }
}