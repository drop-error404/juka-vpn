package com.julogic.jukavpn

import android.app.Application
import android.util.Log
import com.julogic.jukavpn.service.VpnConnectionManager

class JukaVpnApplication : Application() {
    
    companion object {
        private const val TAG = "JukaVpnApp"
        
        lateinit var instance: JukaVpnApplication
            private set
    }
    
    lateinit var connectionManager: VpnConnectionManager
        private set
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        
        Log.i(TAG, "Juka VPN Application started")
        
        // Initialize connection manager
        connectionManager = VpnConnectionManager.getInstance(this)
    }
}
