package com.julogic.jukavpn

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.navigation.NavigationView
import com.julogic.jukavpn.data.ServerRepository
import com.julogic.jukavpn.databinding.ActivityMainBinding
import com.julogic.jukavpn.models.ConnectionState
import com.julogic.jukavpn.models.ConnectionStats
import com.julogic.jukavpn.models.Server
import com.julogic.jukavpn.service.VpnConnectionManager
import com.julogic.jukavpn.utils.CountryUtils
import com.julogic.jukavpn.utils.ImportExportManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var vpnManager: VpnConnectionManager
    private lateinit var serverRepository: ServerRepository
    private lateinit var importExportManager: ImportExportManager
    
    private var pulseAnimator: ValueAnimator? = null
    private var rotationAnimator: ObjectAnimator? = null
    
    // Activity result launchers
    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            connectToVpn()
        } else {
            Toast.makeText(this, R.string.error_vpn_permission, Toast.LENGTH_SHORT).show()
        }
    }
    
    private val serverListLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.getStringExtra("server_id")?.let { serverId ->
                val server = serverRepository.getServerById(serverId)
                server?.let { selectServer(it) }
            }
        }
    }
    
    private val importFileLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            lifecycleScope.launch {
                val count = importExportManager.importFromFile(it)
                Toast.makeText(
                    this@MainActivity,
                    if (count > 0) getString(R.string.import_success) else getString(R.string.import_failed),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Initialize managers
        vpnManager = VpnConnectionManager.getInstance(this)
        serverRepository = ServerRepository(this)
        importExportManager = ImportExportManager(this)
        
        setupToolbar()
        setupDrawer()
        setupConnectionButton()
        setupServerSelector()
        observeConnectionState()
        
        // Load selected server
        serverRepository.getSelectedServer()?.let { updateServerDisplay(it) }
    }
    
    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
    }
    
    private fun setupDrawer() {
        val toggle = ActionBarDrawerToggle(
            this,
            binding.drawerLayout,
            binding.toolbar,
            R.string.cd_menu,
            R.string.cd_close
        )
        binding.drawerLayout.addDrawerListener(toggle)
        toggle.syncState()
        
        binding.navView.setNavigationItemSelectedListener(this)
    }
    
    private fun setupConnectionButton() {
        binding.btnConnect.setOnClickListener {
            when (vpnManager.getCurrentState()) {
                ConnectionState.DISCONNECTED, ConnectionState.ERROR -> {
                    requestVpnPermissionAndConnect()
                }
                ConnectionState.CONNECTED -> {
                    showDisconnectConfirmation()
                }
                ConnectionState.CONNECTING, ConnectionState.DISCONNECTING -> {
                    // Ignore clicks during state transition
                }
            }
        }
    }
    
    private fun setupServerSelector() {
        binding.serverSelector.setOnClickListener {
            val intent = Intent(this, ServerListActivity::class.java)
            serverListLauncher.launch(intent)
        }
    }
    
    private fun observeConnectionState() {
        lifecycleScope.launch {
            vpnManager.connectionState.collectLatest { state ->
                updateConnectionUI(state)
            }
        }
        
        lifecycleScope.launch {
            vpnManager.currentServer.collectLatest { server ->
                server?.let { updateServerDisplay(it) }
            }
        }
        
        lifecycleScope.launch {
            vpnManager.connectionStats.collectLatest { stats ->
                updateStatsDisplay(stats)
            }
        }
    }
    
    private fun updateConnectionUI(state: ConnectionState) {
        runOnUiThread {
            when (state) {
                ConnectionState.DISCONNECTED -> {
                    binding.tvConnectionStatus.text = getString(R.string.status_disconnected)
                    binding.tvConnectionStatus.setTextColor(getColor(R.color.status_disconnected))
                    binding.btnConnect.setBackgroundResource(R.drawable.bg_button_connect)
                    binding.ivConnectionIcon.setImageResource(R.drawable.ic_vpn_disconnected)
                    binding.ivConnectionIcon.setColorFilter(getColor(R.color.status_disconnected))
                    binding.tvConnectionHint.text = getString(R.string.btn_tap_to_connect)
                    binding.progressConnection.visibility = View.GONE
                    stopAnimations()
                    resetStatsDisplay()
                }
                
                ConnectionState.CONNECTING -> {
                    binding.tvConnectionStatus.text = getString(R.string.status_connecting)
                    binding.tvConnectionStatus.setTextColor(getColor(R.color.status_connecting))
                    binding.btnConnect.setBackgroundResource(R.drawable.bg_button_connecting)
                    binding.ivConnectionIcon.setImageResource(R.drawable.ic_vpn_connecting)
                    binding.ivConnectionIcon.setColorFilter(getColor(R.color.status_connecting))
                    binding.tvConnectionHint.text = getString(R.string.btn_connecting)
                    binding.progressConnection.visibility = View.VISIBLE
                    startConnectingAnimation()
                }
                
                ConnectionState.CONNECTED -> {
                    binding.tvConnectionStatus.text = getString(R.string.status_connected)
                    binding.tvConnectionStatus.setTextColor(getColor(R.color.status_connected))
                    binding.btnConnect.setBackgroundResource(R.drawable.bg_button_disconnect)
                    binding.ivConnectionIcon.setImageResource(R.drawable.ic_vpn_connected)
                    binding.ivConnectionIcon.setColorFilter(getColor(R.color.status_connected))
                    binding.tvConnectionHint.text = getString(R.string.btn_tap_to_disconnect)
                    binding.progressConnection.visibility = View.GONE
                    stopAnimations()
                    startConnectedPulse()
                }
                
                ConnectionState.DISCONNECTING -> {
                    binding.tvConnectionStatus.text = getString(R.string.status_disconnecting)
                    binding.tvConnectionStatus.setTextColor(getColor(R.color.status_connecting))
                    binding.progressConnection.visibility = View.VISIBLE
                }
                
                ConnectionState.ERROR -> {
                    binding.tvConnectionStatus.text = getString(R.string.status_error)
                    binding.tvConnectionStatus.setTextColor(getColor(R.color.status_error))
                    binding.btnConnect.setBackgroundResource(R.drawable.bg_button_connect)
                    binding.ivConnectionIcon.setImageResource(R.drawable.ic_vpn_error)
                    binding.ivConnectionIcon.setColorFilter(getColor(R.color.status_error))
                    binding.tvConnectionHint.text = getString(R.string.btn_tap_to_connect)
                    binding.progressConnection.visibility = View.GONE
                    stopAnimations()
                }
            }
        }
    }
    
    private fun updateServerDisplay(server: Server) {
        binding.tvServerName.text = server.name
        binding.tvServerLocation.text = server.countryName
        binding.tvServerProtocol.text = server.getProtocolDisplayName()
        
        // Set flag
        val flagResId = CountryUtils.getFlagResource(this, server.countryCode)
        binding.ivServerFlag.setImageResource(flagResId)
        
        // Set latency
        if (server.latency > 0) {
            binding.tvServerLatency.text = getString(R.string.server_latency_ms, server.latency)
            binding.tvServerLatency.visibility = View.VISIBLE
            binding.ivLatencyIndicator.visibility = View.VISIBLE
            
            val latencyColor = getLatencyColor(server.latency)
            binding.tvServerLatency.setTextColor(latencyColor)
            binding.ivLatencyIndicator.setColorFilter(latencyColor)
        } else {
            binding.tvServerLatency.visibility = View.GONE
            binding.ivLatencyIndicator.visibility = View.GONE
        }
    }
    
    private fun updateStatsDisplay(stats: ConnectionStats) {
        if (vpnManager.isConnected()) {
            binding.statsContainer.visibility = View.VISIBLE
            
            // Download speed
            binding.tvDownloadSpeed.text = formatBytes(stats.downloadSpeed)
            binding.tvTotalDownload.text = formatBytes(stats.totalDownload)
            
            // Upload speed
            binding.tvUploadSpeed.text = formatBytes(stats.uploadSpeed)
            binding.tvTotalUpload.text = formatBytes(stats.totalUpload)
            
            // Session time
            binding.tvSessionTime.text = formatDuration(stats.connectedAt)
        } else {
            binding.statsContainer.visibility = View.GONE
        }
    }
    
    private fun resetStatsDisplay() {
        binding.tvDownloadSpeed.text = "0 B/s"
        binding.tvTotalDownload.text = "0 B"
        binding.tvUploadSpeed.text = "0 B/s"
        binding.tvTotalUpload.text = "0 B"
        binding.tvSessionTime.text = "00:00:00"
    }
    
    private fun requestVpnPermissionAndConnect() {
        val selectedServer = serverRepository.getSelectedServer()
        if (selectedServer == null) {
            Toast.makeText(this, R.string.no_server_selected, Toast.LENGTH_SHORT).show()
            serverListLauncher.launch(Intent(this, ServerListActivity::class.java))
            return
        }
        
        val vpnIntent = VpnService.prepare(this)
        if (vpnIntent != null) {
            vpnPermissionLauncher.launch(vpnIntent)
        } else {
            connectToVpn()
        }
    }
    
    private fun connectToVpn() {
        val server = serverRepository.getSelectedServer()
        if (server != null) {
            vpnManager.connect(server)
        } else {
            Toast.makeText(this, R.string.no_server_selected, Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun showDisconnectConfirmation() {
        AlertDialog.Builder(this)
            .setTitle(R.string.btn_disconnect)
            .setMessage(R.string.confirm_disconnect)
            .setPositiveButton(R.string.action_yes) { _, _ ->
                vpnManager.disconnect()
            }
            .setNegativeButton(R.string.action_no, null)
            .show()
    }
    
    private fun selectServer(server: Server) {
        serverRepository.selectServer(server)
        updateServerDisplay(server)
    }
    
    // ==================== ANIMATIONS ====================
    
    private fun startConnectingAnimation() {
        stopAnimations()
        
        rotationAnimator = ObjectAnimator.ofFloat(binding.ivConnectionIcon, "rotation", 0f, 360f).apply {
            duration = 2000
            repeatCount = ObjectAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }
    
    private fun startConnectedPulse() {
        pulseAnimator = ValueAnimator.ofFloat(1f, 1.1f, 1f).apply {
            duration = 2000
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { animator ->
                val scale = animator.animatedValue as Float
                binding.btnConnect.scaleX = scale
                binding.btnConnect.scaleY = scale
            }
            start()
        }
    }
    
    private fun stopAnimations() {
        pulseAnimator?.cancel()
        pulseAnimator = null
        
        rotationAnimator?.cancel()
        rotationAnimator = null
        
        binding.btnConnect.scaleX = 1f
        binding.btnConnect.scaleY = 1f
        binding.ivConnectionIcon.rotation = 0f
    }
    
    // ==================== NAVIGATION ====================
    
    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_home -> {
                // Already on home
            }
            R.id.nav_servers -> {
                serverListLauncher.launch(Intent(this, ServerListActivity::class.java))
            }
            R.id.nav_import -> {
                showImportDialog()
            }
            R.id.nav_export -> {
                exportServers()
            }
            R.id.nav_subscription -> {
                showSubscriptionDialog()
            }
            R.id.nav_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
            }
            R.id.nav_about -> {
                startActivity(Intent(this, AboutActivity::class.java))
            }
        }
        
        binding.drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }
    
    private fun showImportDialog() {
        val options = arrayOf(
            getString(R.string.import_from_clipboard),
            getString(R.string.import_from_qr),
            getString(R.string.import_from_file)
        )
        
        AlertDialog.Builder(this)
            .setTitle(R.string.import_title)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> importFromClipboard()
                    1 -> importFromQR()
                    2 -> importFileLauncher.launch("*/*")
                }
            }
            .show()
    }
    
    private fun importFromClipboard() {
        lifecycleScope.launch {
            val count = importExportManager.importFromClipboard()
            Toast.makeText(
                this@MainActivity,
                if (count > 0) getString(R.string.import_success) else getString(R.string.import_failed),
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    
    private fun importFromQR() {
        // TODO: Implement QR code scanning
        Toast.makeText(this, "QR Scanner não implementado", Toast.LENGTH_SHORT).show()
    }
    
    private fun exportServers() {
        lifecycleScope.launch {
            val success = importExportManager.exportAllToClipboard()
            Toast.makeText(
                this@MainActivity,
                if (success) getString(R.string.export_success) else getString(R.string.export_failed),
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    
    private fun showSubscriptionDialog() {
        // TODO: Show subscription management dialog
        Toast.makeText(this, "Subscrições", Toast.LENGTH_SHORT).show()
    }
    
    override fun onBackPressed() {
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        stopAnimations()
    }
    
    // ==================== UTILITY METHODS ====================
    
    private fun getLatencyColor(latency: Long): Int {
        return when {
            latency < 100 -> getColor(R.color.latency_excellent)
            latency < 200 -> getColor(R.color.latency_good)
            latency < 400 -> getColor(R.color.latency_medium)
            latency < 800 -> getColor(R.color.latency_poor)
            else -> getColor(R.color.latency_bad)
        }
    }
    
    private fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024))
            else -> String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024))
        }
    }
    
    private fun formatDuration(connectedAt: Long): String {
        if (connectedAt == 0L) return "00:00:00"
        
        val elapsed = (System.currentTimeMillis() - connectedAt) / 1000
        val hours = elapsed / 3600
        val minutes = (elapsed % 3600) / 60
        val seconds = elapsed % 60
        
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }
}
