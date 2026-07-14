package com.ljku.truemark

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.ljku.truemark.database.NotificationEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NotificationAdapter(
    private var notifications: List<NotificationEntity>
) : RecyclerView.Adapter<NotificationAdapter.NotificationViewHolder>() {

    class NotificationViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val titleText: TextView = view.findViewById(R.id.notificationTitle)
        val messageText: TextView = view.findViewById(R.id.notificationMessage)
        val timeText: TextView = view.findViewById(R.id.notificationTime)
        val typeIcon: ImageView = view.findViewById(R.id.notificationIcon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotificationViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_notification, parent, false)
        return NotificationViewHolder(view)
    }

    override fun onBindViewHolder(holder: NotificationViewHolder, position: Int) {
        val notification = notifications[position]
        holder.titleText.text = notification.title
        holder.messageText.text = notification.message
        
        val time = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(notification.timestamp))
        holder.timeText.text = time

        // Set icon based on type
        val iconRes = when (notification.type) {
            "LOGIN" -> R.drawable.ic_lock
            "CHAT" -> R.drawable.ic_chat
            "ACADEMIC" -> R.drawable.ic_calendar
            else -> R.drawable.ic_notifications
        }
        holder.typeIcon.setImageResource(iconRes)
    }

    override fun getItemCount() = notifications.size

    fun updateData(newNotifications: List<NotificationEntity>) {
        notifications = newNotifications
        notifyDataSetChanged()
    }
}
