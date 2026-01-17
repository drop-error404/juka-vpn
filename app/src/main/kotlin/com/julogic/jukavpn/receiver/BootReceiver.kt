package com.julogic.jukavpn.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.julogic.jukavpn.JukaVpnApplication

class BootReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "BootReceiver"
        private const val PREFS_NAME = "juka_vpn_prefs"
        private const val KEY_AUTO_CONNECT = "auto_connect_on_boot"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            
            Log.d(TAG, "Device boot completed")
            
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val autoConnect = prefs.getBoolean(KEY_AUTO_CONNECT, false)
            
            if (autoConnect) {
                Log.d(TAG, "Auto-connect enabled, starting VPN")
                
                // Delay to allow system to fully boot
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    try {
                        val app = context.applicationContext as? JukaVpnApplication
                        app?.connectionManager?.quickConnect()
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to auto-connect", e)
                    }
                }, 5000)
            }
        }
    }
}
