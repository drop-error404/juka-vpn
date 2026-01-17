package com.julogic.jukavpn.utils

import android.content.Context
import android.util.Base64
import android.util.Log
import com.julogic.jukavpn.data.ServerRepository
import com.julogic.jukavpn.models.Server
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Manages VPN subscription URLs for automatic server updates
 */
class SubscriptionManager(private val context: Context) {
    
    companion object {
        private const val TAG = "SubscriptionManager"
        private const val PREFS_NAME = "subscriptions"
        private const val KEY_SUBSCRIPTIONS = "subscription_list"
        private const val CONNECT_TIMEOUT = 15000
        private const val READ_TIMEOUT = 30000
    }
    
    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    private val importExportManager = ImportExportManager(context)
    private val repository = ServerRepository(context)
    
    data class Subscription(
        val id: String = java.util.UUID.randomUUID().toString(),
        val name: String,
        val url: String,
        val autoUpdate: Boolean = true,
        val updateIntervalHours: Int = 24,
        val lastUpdated: Long = 0,
        val serverCount: Int = 0,
        val isEnabled: Boolean = true
    )
    
    data class UpdateResult(
        val subscription: Subscription,
        val newServers: Int,
        val updatedServers: Int,
        val removedServers: Int,
        val error: String? = null
    )
    
    interface UpdateListener {
        fun onUpdateStarted(subscription: Subscription)
        fun onUpdateProgress(subscription: Subscription, progress: Float)
        fun onUpdateCompleted(result: UpdateResult)
        fun onUpdateFailed(subscription: Subscription, error: String)
    }
    
    private var updateListener: UpdateListener? = null
    
    fun setUpdateListener(listener: UpdateListener) {
        this.updateListener = listener
    }
    
    // ==================== SUBSCRIPTION MANAGEMENT ====================
    
    fun getAllSubscriptions(): List<Subscription> {
        val json = prefs.getString(KEY_SUBSCRIPTIONS, "[]") ?: "[]"
        return try {
            val array = JSONArray(json)
            (0 until array.length()).mapNotNull { i ->
                parseSubscriptionFromJson(array.getJSONObject(i))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    fun addSubscription(name: String, url: String, autoUpdate: Boolean = true): Subscription {
        val subscription = Subscription(
            name = name,
            url = url,
            autoUpdate = autoUpdate
        )
        
        val subscriptions = getAllSubscriptions().toMutableList()
        subscriptions.add(subscription)
        saveSubscriptions(subscriptions)
        
        return subscription
    }
    
    fun updateSubscription(subscription: Subscription) {
        val subscriptions = getAllSubscriptions().toMutableList()
        val index = subscriptions.indexOfFirst { it.id == subscription.id }
        if (index >= 0) {
            subscriptions[index] = subscription
            saveSubscriptions(subscriptions)
        }
    }
    
    fun deleteSubscription(subscriptionId: String) {
        val subscriptions = getAllSubscriptions().filter { it.id != subscriptionId }
        saveSubscriptions(subscriptions)
    }
    
    private fun saveSubscriptions(subscriptions: List<Subscription>) {
        val array = JSONArray()
        subscriptions.forEach { sub ->
            array.put(subscriptionToJson(sub))
        }
        prefs.edit().putString(KEY_SUBSCRIPTIONS, array.toString()).apply()
    }
    
    // ==================== UPDATE OPERATIONS ====================
    
    /**
     * Update servers from a subscription URL
     */
    suspend fun updateSubscription(subscription: Subscription): UpdateResult {
        return withContext(Dispatchers.IO) {
            try {
                updateListener?.onUpdateStarted(subscription)
                
                val content = fetchSubscriptionContent(subscription.url)
                updateListener?.onUpdateProgress(subscription, 0.3f)
                
                val result = importExportManager.importFromUri(content)
                updateListener?.onUpdateProgress(subscription, 0.8f)
                
                when (result) {
                    is ImportExportManager.ImportResult.Success -> {
                        val updatedSubscription = subscription.copy(
                            lastUpdated = System.currentTimeMillis(),
                            serverCount = result.servers.size
                        )
                        updateSubscription(updatedSubscription)
                        
                        UpdateResult(
                            subscription = updatedSubscription,
                            newServers = result.servers.size,
                            updatedServers = 0,
                            removedServers = 0
                        ).also {
                            updateListener?.onUpdateCompleted(it)
                        }
                    }
                    is ImportExportManager.ImportResult.Error -> {
                        UpdateResult(
                            subscription = subscription,
                            newServers = 0,
                            updatedServers = 0,
                            removedServers = 0,
                            error = result.message
                        ).also {
                            updateListener?.onUpdateFailed(subscription, result.message)
                        }
                    }
                    else -> {
                        UpdateResult(
                            subscription = subscription,
                            newServers = 0,
                            updatedServers = 0,
                            removedServers = 0,
                            error = "Unexpected result"
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error updating subscription", e)
                val errorMessage = e.message ?: "Unknown error"
                updateListener?.onUpdateFailed(subscription, errorMessage)
                
                UpdateResult(
                    subscription = subscription,
                    newServers = 0,
                    updatedServers = 0,
                    removedServers = 0,
                    error = errorMessage
                )
            }
        }
    }
    
    /**
     * Update all enabled subscriptions
     */
    suspend fun updateAllSubscriptions(): List<UpdateResult> {
        return withContext(Dispatchers.IO) {
            getAllSubscriptions()
                .filter { it.isEnabled }
                .map { subscription ->
                    updateSubscription(subscription)
                }
        }
    }
    
    /**
     * Check and update subscriptions that are due for auto-update
     */
    suspend fun checkAndUpdateDueSubscriptions(): List<UpdateResult> {
        return withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            
            getAllSubscriptions()
                .filter { it.isEnabled && it.autoUpdate }
                .filter { subscription ->
                    val intervalMs = subscription.updateIntervalHours * 60 * 60 * 1000L
                    now - subscription.lastUpdated >= intervalMs
                }
                .map { subscription ->
                    updateSubscription(subscription)
                }
        }
    }
    
    /**
     * Fetch content from subscription URL
     */
    private fun fetchSubscriptionContent(urlString: String): String {
        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection
        
        return try {
            connection.apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT
                readTimeout = READ_TIMEOUT
                setRequestProperty("User-Agent", "JukaVPN/1.0")
                setRequestProperty("Accept", "*/*")
            }
            
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw Exception("HTTP ${connection.responseCode}: ${connection.responseMessage}")
            }
            
            connection.inputStream.bufferedReader().readText()
        } finally {
            connection.disconnect()
        }
    }
    
    // ==================== JSON HELPERS ====================
    
    private fun subscriptionToJson(subscription: Subscription): JSONObject {
        return JSONObject().apply {
            put("id", subscription.id)
            put("name", subscription.name)
            put("url", subscription.url)
            put("autoUpdate", subscription.autoUpdate)
            put("updateIntervalHours", subscription.updateIntervalHours)
            put("lastUpdated", subscription.lastUpdated)
            put("serverCount", subscription.serverCount)
            put("isEnabled", subscription.isEnabled)
        }
    }
    
    private fun parseSubscriptionFromJson(json: JSONObject): Subscription? {
        return try {
            Subscription(
                id = json.getString("id"),
                name = json.getString("name"),
                url = json.getString("url"),
                autoUpdate = json.optBoolean("autoUpdate", true),
                updateIntervalHours = json.optInt("updateIntervalHours", 24),
                lastUpdated = json.optLong("lastUpdated", 0),
                serverCount = json.optInt("serverCount", 0),
                isEnabled = json.optBoolean("isEnabled", true)
            )
        } catch (e: Exception) {
            null
        }
    }
}
