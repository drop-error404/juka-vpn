package com.julogic.jukavpn.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.julogic.jukavpn.R
import com.julogic.jukavpn.models.Protocol
import com.julogic.jukavpn.models.Server
import com.julogic.jukavpn.utils.CountryUtils

class ServerAdapter(
    private val context: Context,
    private var servers: MutableList<Server>,
    private var selectedServerId: String? = null,
    private val listener: ServerClickListener
) : RecyclerView.Adapter<ServerAdapter.ServerViewHolder>() {

    interface ServerClickListener {
        fun onServerClick(server: Server)
        fun onServerLongClick(server: Server)
        fun onFavoriteClick(server: Server)
        fun onTestLatency(server: Server)
    }

    inner class ServerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivFlag: ImageView = itemView.findViewById(R.id.ivServerFlag)
        val tvName: TextView = itemView.findViewById(R.id.tvServerName)
        val tvLocation: TextView = itemView.findViewById(R.id.tvServerLocation)
        val tvProtocol: TextView = itemView.findViewById(R.id.tvServerProtocol)
        val tvLatency: TextView = itemView.findViewById(R.id.tvServerLatency)
        val ivLatencyIndicator: View = itemView.findViewById(R.id.ivLatencyIndicator)
        val ivFavorite: ImageButton = itemView.findViewById(R.id.btnFavorite)
        val ivSelected: ImageView = itemView.findViewById(R.id.ivServerSelected)
        val btnMore: ImageButton = itemView.findViewById(R.id.btnServerMore)

        fun bind(server: Server, isSelected: Boolean) {
            // Server info
            tvName.text = server.name
            tvLocation.text = server.countryName
            tvProtocol.text = server.getProtocolDisplayName()

            // Flag
            val flagResId = CountryUtils.getFlagResource(context, server.countryCode)
            ivFlag.setImageResource(flagResId)

            // Protocol badge color
            val protocolColor = getProtocolColor(server.protocol)
            tvProtocol.backgroundTintList = ContextCompat.getColorStateList(context, protocolColor)

            // Latency
            if (server.latency > 0) {
                tvLatency.visibility = View.VISIBLE
                ivLatencyIndicator.visibility = View.VISIBLE
                tvLatency.text = context.getString(R.string.server_latency_ms, server.latency)
                
                val latencyColor = getLatencyColor(server.latency)
                tvLatency.setTextColor(ContextCompat.getColor(context, latencyColor))
                ivLatencyIndicator.backgroundTintList = ContextCompat.getColorStateList(context, latencyColor)
            } else {
                tvLatency.visibility = View.GONE
                ivLatencyIndicator.visibility = View.GONE
            }

            // Favorite icon
            ivFavorite.setImageResource(
                if (server.isFavorite) R.drawable.ic_favorite_filled
                else R.drawable.ic_favorite_outline
            )
            ivFavorite.setColorFilter(
                ContextCompat.getColor(
                    context,
                    if (server.isFavorite) R.color.accent else R.color.text_secondary
                )
            )

            // Selected indicator
            ivSelected.visibility = if (isSelected) View.VISIBLE else View.GONE
            
            // Background highlight for selected
            itemView.setBackgroundResource(
                if (isSelected) R.drawable.bg_server_item_selected
                else R.drawable.bg_server_item
            )

            // Click listeners
            itemView.setOnClickListener {
                listener.onServerClick(server)
            }

            itemView.setOnLongClickListener {
                listener.onServerLongClick(server)
                true
            }

            ivFavorite.setOnClickListener {
                listener.onFavoriteClick(server)
            }

            btnMore.setOnClickListener {
                listener.onServerLongClick(server)
            }
        }

        private fun getProtocolColor(protocol: Protocol): Int {
            return when (protocol) {
                Protocol.VMESS -> R.color.protocol_vmess
                Protocol.VLESS -> R.color.protocol_vless
                Protocol.TROJAN -> R.color.protocol_trojan
                Protocol.SHADOWSOCKS -> R.color.protocol_shadowsocks
                Protocol.SSH -> R.color.protocol_ssh
                Protocol.UDP -> R.color.primary
            }
        }

        private fun getLatencyColor(latency: Long): Int {
            return when {
                latency < 100 -> R.color.latency_excellent
                latency < 200 -> R.color.latency_good
                latency < 400 -> R.color.latency_medium
                latency < 800 -> R.color.latency_poor
                else -> R.color.latency_bad
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ServerViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_server, parent, false)
        return ServerViewHolder(view)
    }

    override fun onBindViewHolder(holder: ServerViewHolder, position: Int) {
        val server = servers[position]
        val isSelected = server.id == selectedServerId
        holder.bind(server, isSelected)
    }

    override fun getItemCount(): Int = servers.size

    fun updateServers(newServers: List<Server>) {
        val diffCallback = ServerDiffCallback(servers, newServers)
        val diffResult = DiffUtil.calculateDiff(diffCallback)
        
        servers.clear()
        servers.addAll(newServers)
        
        diffResult.dispatchUpdatesTo(this)
    }

    fun updateSelectedServer(serverId: String?) {
        val oldSelectedId = selectedServerId
        selectedServerId = serverId

        // Refresh old and new selected items
        if (oldSelectedId != null) {
            val oldIndex = servers.indexOfFirst { it.id == oldSelectedId }
            if (oldIndex != -1) notifyItemChanged(oldIndex)
        }

        if (serverId != null) {
            val newIndex = servers.indexOfFirst { it.id == serverId }
            if (newIndex != -1) notifyItemChanged(newIndex)
        }
    }

    fun updateServerLatency(serverId: String, latency: Long) {
        val index = servers.indexOfFirst { it.id == serverId }
        if (index != -1) {
            servers[index] = servers[index].copy(latency = latency)
            notifyItemChanged(index)
        }
    }

    private class ServerDiffCallback(
        private val oldList: List<Server>,
        private val newList: List<Server>
    ) : DiffUtil.Callback() {

        override fun getOldListSize(): Int = oldList.size

        override fun getNewListSize(): Int = newList.size

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition].id == newList[newItemPosition].id
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition] == newList[newItemPosition]
        }
    }
}
