package com.julogic.jukavpn

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import libv2ray.Libv2ray
import libv2ray.V2RayCallback
import libv2ray.V2RayPoint

class V2rayVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private lateinit var point: V2RayPoint

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        setupVpn()
        return START_STICKY
    }

    override fun onDestroy() {
        vpnInterface?.close()
        super.onDestroy()
    }

    private fun setupVpn() {
        val lib = Libv2ray()
        point = lib.newV2RayPoint()
        point.setCallback(object : V2RayCallback {
            override fun onEmitStatus(status: Int) {
                // status logs
            }

            override fun protect(fd: Int): Boolean {
                return true
            }
        })

        val builder = Builder()
        builder.addAddress("10.0.0.2", 32)
        builder.addRoute("0.0.0.0", 0)
        vpnInterface = builder.setSession("Juka VPN").establish()

        point.start() // inicia a conexão real
    }
}