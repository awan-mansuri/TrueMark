package com.ljku.truemark

import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import com.ljku.truemark.database.MessageEntity
import java.io.File
import java.io.ByteArrayInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MessageAdapter(
    private var messages: List<MessageEntity>,
    private val currentUserId: Int,
    private val currentUserRole: String,
    private val onMessageLongClick: (MessageEntity) -> Unit,
    private val onMediaClick: (MessageEntity) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_SENT = 1
        private const val VIEW_TYPE_RECEIVED = 2
        private const val VIEW_TYPE_SYSTEM = 3
    }

    class SentMessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val messageText: TextView = view.findViewById(R.id.sentMessageText)
        val mediaImage: ImageView = view.findViewById(R.id.sentMediaImage)
        val timeText: TextView = view.findViewById(R.id.sentTimeText)
    }

    class ReceivedMessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val senderName: TextView = view.findViewById(R.id.receivedSenderName)
        val messageText: TextView = view.findViewById(R.id.receivedMessageText)
        val mediaImage: ImageView = view.findViewById(R.id.receivedMediaImage)
        val timeText: TextView = view.findViewById(R.id.receivedTimeText)
    }

    class SystemMessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val messageText: TextView = view.findViewById(R.id.systemMessageText)
    }

    override fun getItemViewType(position: Int): Int {
        val message = messages[position]
        return when {
            message.messageType == "SYSTEM" -> VIEW_TYPE_SYSTEM
            message.senderId == currentUserId -> VIEW_TYPE_SENT
            else -> VIEW_TYPE_RECEIVED
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_SENT -> {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_message_sent, parent, false)
                SentMessageViewHolder(view)
            }
            VIEW_TYPE_SYSTEM -> {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_message_system, parent, false)
                SystemMessageViewHolder(view)
            }
            else -> {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_message_received, parent, false)
                ReceivedMessageViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messages[position]
        val time = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(message.timestamp))

        // Only allow admin and faculty to delete messages
        val canDeleteMessage = currentUserRole.equals("ADMIN", ignoreCase = true) ||
                             currentUserRole.equals("system_admin", ignoreCase = true) ||
                             currentUserRole.equals("FACULTY", ignoreCase = true)

        holder.itemView.setOnLongClickListener {
            if (canDeleteMessage && message.messageType != "SYSTEM") {
                onMessageLongClick(message)
                true
            } else {
                false // Students cannot delete messages
            }
        }

        if (holder is SentMessageViewHolder) {
            bind(holder.messageText, holder.mediaImage, holder.timeText, null, message, time)
        } else if (holder is ReceivedMessageViewHolder) {
            bind(holder.messageText, holder.mediaImage, holder.timeText, holder.senderName, message, time)
        } else if (holder is SystemMessageViewHolder) {
            holder.messageText.text = message.messageText
            // System messages cannot be deleted
            holder.itemView.setOnLongClickListener(null)
        }
    }

    private fun bind(
        text: TextView,
        image: ImageView,
        timeView: TextView,
        sender: TextView?,
        message: MessageEntity,
        time: String
    ) {
        sender?.text = message.senderName
        timeView.text = time

        if (!message.messageText.isNullOrEmpty()) {
            text.text = message.messageText
            text.visibility = View.VISIBLE
        } else {
            text.visibility = View.GONE
        }

        if (!message.mediaUri.isNullOrEmpty()) {
            image.visibility = View.VISIBLE
            
            // Set click listener first
            image.setOnClickListener { 
                onMediaClick(message)
            }
            
            if (message.mediaType == "VIDEO") {
                image.setImageResource(R.drawable.ic_attendance) 
                image.setColorFilter(android.graphics.Color.GRAY)
            } else if (message.mediaType == "DOCUMENT") {
                image.setImageResource(R.drawable.ic_calendar)
                image.clearColorFilter()
                text.text = "Document: ${message.messageText ?: "File"}"
                text.visibility = View.VISIBLE
            } else {
                image.clearColorFilter()
                image.visibility = View.VISIBLE // Ensure visibility before loading
                
                try {
                    // Handle base64 encoded images
                    if (message.mediaUri.startsWith("data:image/")) {
                        val base64Data = message.mediaUri.substring(message.mediaUri.indexOf(",") + 1)
                        val decodedBytes = Base64.decode(base64Data, Base64.DEFAULT)
                        val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                        if (bitmap != null) {
                            image.setImageBitmap(bitmap)
                        } else {
                            // Show placeholder if bitmap is null
                            image.setImageResource(R.drawable.ic_attendance)
                        }
                    } else {
                        // Handle file path images (fallback)
                        val imgFile = File(message.mediaUri)
                        if (imgFile.exists()) {
                            val myBitmap = BitmapFactory.decodeFile(imgFile.absolutePath)
                            if (myBitmap != null) {
                                image.setImageBitmap(myBitmap)
                            } else {
                                image.setImageResource(R.drawable.ic_attendance)
                            }
                        } else {
                            image.setImageResource(R.drawable.ic_attendance)
                        }
                    }
                } catch (e: Exception) {
                    // Show placeholder on error but keep image clickable
                    image.setImageResource(R.drawable.ic_attendance)
                    e.printStackTrace()
                }
            }
        } else {
            image.visibility = View.GONE
        }
    }

    override fun getItemCount() = messages.size

    fun updateMessages(newMessages: List<MessageEntity>) {
        messages = newMessages
        notifyDataSetChanged()
    }
}