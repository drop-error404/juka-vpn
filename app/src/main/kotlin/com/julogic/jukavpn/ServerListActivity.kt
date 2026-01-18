package com.julogic.jukavpn

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.julogic.jukavpn.adapter.ServerAdapter
import com.julogic.jukavpn.data.ServerRepository
import com.julogic.jukavpn.databinding.ActivityServerListBinding
import com.julogic.jukavpn.databinding.DialogAddServerBinding
import com.julogic.jukavpn.models.Server
import com.julogic.jukavpn.utils.ImportExportManager
import com.julogic.jukavpn.utils.LatencyTester
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ServerListActivity : AppCompatActivity(), ServerAdapter.ServerClickListener {

    private lateinit var binding: ActivityServerListBinding
    private lateinit var serverRepository: ServerRepository
    private lateinit var latencyTester: LatencyTester
    private lateinit var importExportManager: ImportExportManager
    private lateinit var serverAdapter: ServerAdapter
    
    private var allServers: List<Server> = emptyList()
    private var filteredServers: List<Server> = emptyList()
    private var currentFilter: ServerFilter = ServerFilter.ALL
    
    enum class ServerFilter { ALL, FAVORITES, RECENT }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityServerListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Initialize
        serverRepository = ServerRepository(this)
        latencyTester = LatencyTester()
        importExportManager = ImportExportManager(this)
        
        setupToolbar()
        setupRecyclerView()
        setupFab()
        setupChips()
        
        loadServers()
    }
    
    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            title = getString(R.string.server_list)
        }
        
        binding.toolbar.setNavigationOnClickListener {
            onBackPressed()
        }
    }
    
    private fun setupRecyclerView() {
        serverAdapter = ServerAdapter(
            context = this,
            servers = mutableListOf(),
            selectedServerId = serverRepository.getSelectedServer()?.id,
            listener = this
        )
        
        binding.rvServers.apply {
            layoutManager = LinearLayoutManager(this@ServerListActivity)
            adapter = serverAdapter
            setHasFixedSize(true)
        }
    }
    
    private fun setupFab() {
        binding.fabAddServer.setOnClickListener {
            showAddServerDialog()
        }
    }
    
    private fun setupChips() {
        binding.chipAll.setOnClickListener {
            currentFilter = ServerFilter.ALL
            updateChipSelection()
            applyFilter()
        }
        
        binding.chipFavorites.setOnClickListener {
            currentFilter = ServerFilter.FAVORITES
            updateChipSelection()
            applyFilter()
        }
        
        binding.chipRecent.setOnClickListener {
            currentFilter = ServerFilter.RECENT
            updateChipSelection()
            applyFilter()
        }
    }
    
    private fun updateChipSelection() {
        binding.chipAll.isChecked = currentFilter == ServerFilter.ALL
        binding.chipFavorites.isChecked = currentFilter == ServerFilter.FAVORITES
        binding.chipRecent.isChecked = currentFilter == ServerFilter.RECENT
    }
    
    private fun loadServers() {
        allServers = serverRepository.getAllServers()
        applyFilter()
    }
    
    private fun applyFilter() {
        filteredServers = when (currentFilter) {
            ServerFilter.ALL -> allServers
            ServerFilter.FAVORITES -> allServers.filter { it.isFavorite }
            ServerFilter.RECENT -> allServers
                .filter { it.lastUsedAt != null }
                .sortedByDescending { it.lastUsedAt }
                .take(10)
        }
        
        serverAdapter.updateServers(filteredServers)
        updateEmptyState()
    }
    
    private fun applySearch(query: String) {
        if (query.isEmpty()) {
            applyFilter()
            return
        }
        
        val searchLower = query.lowercase()
        filteredServers = allServers.filter { server ->
            server.name.lowercase().contains(searchLower) ||
            server.countryName.lowercase().contains(searchLower) ||
            server.address.lowercase().contains(searchLower) ||
            server.protocol.name.lowercase().contains(searchLower)
        }
        
        serverAdapter.updateServers(filteredServers)
        updateEmptyState()
    }
    
    private fun updateEmptyState() {
        if (filteredServers.isEmpty()) {
            binding.emptyState.visibility = View.VISIBLE
            binding.rvServers.visibility = View.GONE
        } else {
            binding.emptyState.visibility = View.GONE
            binding.rvServers.visibility = View.VISIBLE
        }
    }
    
    // ==================== SERVER CLICK LISTENER ====================
    
    override fun onServerClick(server: Server) {
        // Select server and return to main
        serverRepository.selectServer(server)
        
        setResult(RESULT_OK, Intent().apply {
            putExtra("server_id", server.id)
        })
        finish()
    }
    
    override fun onServerLongClick(server: Server) {
        showServerOptionsDialog(server)
    }
    
    override fun onFavoriteClick(server: Server) {
        val updatedServer = server.copy(isFavorite = !server.isFavorite)
        serverRepository.saveServer(updatedServer)
        
        // Refresh list
        loadServers()
        
        val message = if (updatedServer.isFavorite) {
            "Adicionado aos favoritos"
        } else {
            "Removido dos favoritos"
        }
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
    
    override fun onTestLatency(server: Server) {
        lifecycleScope.launch {
            Toast.makeText(this@ServerListActivity, R.string.server_latency_testing, Toast.LENGTH_SHORT).show()
            
            val latency = withContext(Dispatchers.IO) {
                latencyTester.testLatency(server.address, server.port)
            }
            
            val updatedServer = server.copy(latency = latency)
            serverRepository.saveServer(updatedServer)
            
            // Refresh list
            loadServers()
            
            if (latency > 0) {
                Toast.makeText(
                    this@ServerListActivity,
                    getString(R.string.server_latency_ms, latency),
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                Toast.makeText(this@ServerListActivity, R.string.server_latency_timeout, Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    // ==================== DIALOGS ====================
    
    private fun showAddServerDialog() {
        val dialogBinding = DialogAddServerBinding.inflate(layoutInflater)
        
        AlertDialog.Builder(this)
            .setTitle(R.string.add_server)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.action_save) { _, _ ->
                val config = dialogBinding.etServerConfig.text.toString().trim()
                val remark = dialogBinding.etServerRemark.text.toString().trim()
                
                if (config.isNotEmpty()) {
                    importServer(config, remark.ifEmpty { null })
                } else {
                    Toast.makeText(this, R.string.error_invalid_config, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }
    
    private fun importServer(config: String, customName: String?) {
        lifecycleScope.launch {
            val servers = withContext(Dispatchers.IO) {
                importExportManager.parseConfigs(config)
            }
            
            if (servers.isNotEmpty()) {
                servers.forEach { server ->
                    val finalServer = if (customName != null) {
                        server.copy(name = customName)
                    } else {
                        server
                    }
                    serverRepository.saveServer(finalServer)
                }
                
                loadServers()
                Toast.makeText(
                    this@ServerListActivity,
                    getString(R.string.import_success),
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                Toast.makeText(
                    this@ServerListActivity,
                    getString(R.string.import_invalid_format),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
    
    private fun showServerOptionsDialog(server: Server) {
        val options = arrayOf(
            getString(R.string.action_edit),
            getString(R.string.action_test),
            getString(R.string.action_copy),
            if (server.isFavorite) "Remover dos favoritos" else "Adicionar aos favoritos",
            getString(R.string.action_delete)
        )
        
        AlertDialog.Builder(this)
            .setTitle(server.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showEditServerDialog(server)
                    1 -> onTestLatency(server)
                    2 -> copyServerConfig(server)
                    3 -> onFavoriteClick(server)
                    4 -> showDeleteConfirmation(server)
                }
            }
            .show()
    }
    
    private fun showEditServerDialog(server: Server) {
        val editText = EditText(this).apply {
            setText(server.name)
            hint = getString(R.string.server_remark_hint)
        }
        
        AlertDialog.Builder(this)
            .setTitle(R.string.edit_server)
            .setView(editText)
            .setPositiveButton(R.string.action_save) { _, _ ->
                val newName = editText.text.toString().trim()
                if (newName.isNotEmpty()) {
                    val updatedServer = server.copy(name = newName)
                    serverRepository.saveServer(updatedServer)
                    loadServers()
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }
    
    private fun copyServerConfig(server: Server) {
        // TODO: Generate and copy config URI
        Toast.makeText(this, R.string.export_success, Toast.LENGTH_SHORT).show()
    }
    
    private fun showDeleteConfirmation(server: Server) {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_server)
            .setMessage(R.string.confirm_delete_server)
            .setPositiveButton(R.string.action_delete) { _, _ ->
                serverRepository.deleteServer(server.id)
                loadServers()
                Toast.makeText(this, "Servidor eliminado", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }
    
    // ==================== MENU ====================
    
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_server_list, menu)
        
        val searchItem = menu.findItem(R.id.action_search)
        val searchView = searchItem.actionView as? SearchView
        
        searchView?.apply {
            queryHint = getString(R.string.search_hint)
            setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean {
                    query?.let { applySearch(it) }
                    return true
                }
                
                override fun onQueryTextChange(newText: String?): Boolean {
                    newText?.let { applySearch(it) }
                    return true
                }
            })
        }
        
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_test_all -> {
                testAllServers()
                true
            }
            R.id.action_sort -> {
                showSortDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    private fun testAllServers() {
        if (allServers.isEmpty()) {
            Toast.makeText(this, R.string.empty_servers, Toast.LENGTH_SHORT).show()
            return
        }
        
        Toast.makeText(this, "Testando ${allServers.size} servidores...", Toast.LENGTH_SHORT).show()
        
        lifecycleScope.launch {
            allServers.forEachIndexed { index, server ->
                val latency = withContext(Dispatchers.IO) {
                    latencyTester.testLatency(server.address, server.port)
                }
                
                val updatedServer = server.copy(latency = latency)
                serverRepository.saveServer(updatedServer)
                
                // Update progress
                serverAdapter.updateServerLatency(server.id, latency)
            }
            
            loadServers()
            Toast.makeText(this@ServerListActivity, "Teste concluído!", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun showSortDialog() {
        val options = arrayOf(
            "Por nome",
            "Por latência",
            "Por país",
            "Por protocolo",
            "Recentes primeiro"
        )
        
        AlertDialog.Builder(this)
            .setTitle(R.string.action_sort)
            .setItems(options) { _, which ->
                allServers = when (which) {
                    0 -> allServers.sortedBy { it.name }
                    1 -> allServers.sortedBy { if (it.latency > 0) it.latency else Long.MAX_VALUE }
                    2 -> allServers.sortedBy { it.countryName }
                    3 -> allServers.sortedBy { it.protocol.name }
                    4 -> allServers.sortedByDescending { it.lastUsedAt ?: 0 }
                    else -> allServers
                }
                applyFilter()
            }
            .show()
    }
}
