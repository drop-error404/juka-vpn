package com.julogic.jukavpn.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.julogic.jukavpn.JukaVpnApplication
import com.julogic.jukavpn.utils.NotificationHelper

class VpnActionReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "VpnActionReceiver"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Received action: ${intent.action}")
        
        val app = context.applicationContext as? JukaVpnApplication
        val connectionManager = app?.connectionManager
        
        when (intent.action) {
            NotificationHelper.ACTION_CONNECT -> {
                Log.d(TAG, "Connect action received")
                connectionManager?.quickConnect()
            }
            NotificationHelper.ACTION_DISCONNECT -> {
                Log.d(TAG, "Disconnect action received")
                connectionManager?.disconnect()
            }
            NotificationHelper.ACTION_OPEN -> {
                Log.d(TAG, "Open action received")
                // Open main activity - handled by pending intent in notification
            }
        }
    }
}
