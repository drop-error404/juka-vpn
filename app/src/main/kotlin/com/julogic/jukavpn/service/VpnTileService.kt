package com.julogic.jukavpn.service

import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import androidx.annotation.RequiresApi
import com.julogic.jukavpn.JukaVpnApplication
import com.julogic.jukavpn.MainActivity
import com.julogic.jukavpn.R
import com.julogic.jukavpn.models.ConnectionState

@RequiresApi(Build.VERSION_CODES.N)
class VpnTileService : TileService() {
    
    companion object {
        private const val TAG = "VpnTileService"
    }
    
    private val connectionManager by lazy {
        (application as? JukaVpnApplication)?.connectionManager
    }
    
    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }
    
    override fun onClick() {
        super.onClick()
        Log.d(TAG, "Tile clicked")
        
        val manager = connectionManager
        if (manager == null) {
            Log.e(TAG, "Connection manager not available")
            return
        }
        
        when (manager.getCurrentState()) {
            ConnectionState.CONNECTED, ConnectionState.CONNECTING -> {
                manager.disconnect()
            }
            ConnectionState.DISCONNECTED, ConnectionState.ERROR -> {
                // Check if VPN permission is needed
                val prepareIntent = manager.prepareVpn()
                if (prepareIntent != null) {
                    // Need to open activity for VPN permission
                    val activityIntent = Intent(this, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        putExtra("action", "connect")
                    }
                    startActivityAndCollapse(activityIntent)
                } else {
                    manager.quickConnect()
                }
            }
            else -> {}
        }
        
        updateTile()
    }
    
    private fun updateTile() {
        val tile = qsTile ?: return
        val manager = connectionManager
        
        if (manager == null) {
            tile.state = Tile.STATE_UNAVAILABLE
            tile.label = "Juka VPN"
            tile.updateIcon(R.drawable.ic_vpn_tile)
            tile.updateTile()
            return
        }
        
        when (manager.getCurrentState()) {
            ConnectionState.CONNECTED -> {
                tile.state = Tile.STATE_ACTIVE
                tile.label = manager.getCurrentServer()?.name ?: "Connected"
                tile.updateIcon(R.drawable.ic_vpn_connected)
            }
            ConnectionState.CONNECTING -> {
                tile.state = Tile.STATE_ACTIVE
                tile.label = "Connecting..."
                tile.updateIcon(R.drawable.ic_vpn_connecting)
            }
            ConnectionState.DISCONNECTING -> {
                tile.state = Tile.STATE_INACTIVE
                tile.label = "Disconnecting..."
                tile.updateIcon(R.drawable.ic_vpn_connecting)
            }
            ConnectionState.DISCONNECTED, ConnectionState.ERROR -> {
                tile.state = Tile.STATE_INACTIVE
                tile.label = "Juka VPN"
                tile.updateIcon(R.drawable.ic_vpn_disconnected)
            }
        }
        
        tile.updateTile()
    }
    
    private fun Tile.updateIcon(resId: Int) {
        icon = Icon.createWithResource(this@VpnTileService, resId)
    }
}
