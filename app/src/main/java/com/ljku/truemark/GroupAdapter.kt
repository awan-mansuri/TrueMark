package com.ljku.truemark

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.*
import com.ljku.truemark.database.GroupEntity
import com.ljku.truemark.database.MessageEntity

class GroupAdapter(
    private var groups: List<GroupEntity>,
    private val onGroupClick: (GroupEntity) -> Unit,
    private val onEditClick: (GroupEntity) -> Unit,
    private val currentUserId: Int,
    private val currentUserRole: String?
) : RecyclerView.Adapter<GroupAdapter.GroupViewHolder>() {

    class GroupViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nameText: TextView = view.findViewById(R.id.groupNameText)
        val lastMessageText: TextView = view.findViewById(R.id.lastMessageText)
        val unreadCountBadge: TextView = view.findViewById(R.id.unreadCountBadge)
        val editIcon: ImageView = view.findViewById(R.id.editGroupIcon)
        val groupIcon: ImageView = view.findViewById(R.id.groupIcon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GroupViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_group, parent, false)
        return GroupViewHolder(view)
    }

    override fun onBindViewHolder(holder: GroupViewHolder, position: Int) {
        val group = groups[position]
        holder.nameText.text = group.groupName

        val messagesRef = FirebaseDatabase.getInstance().getReference("groups").child(group.id.toString()).child("messages")
        
        messagesRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                var unreadCount = 0
                var lastMsg: MessageEntity? = null
                
                for (child in snapshot.children) {
                    // Try both field names to handle Firebase serialization differences
                    val isReadField = child.child("isRead").getValue(Boolean::class.java) ?: true
                    val readField = child.child("read").getValue(Boolean::class.java) ?: true
                    val senderId = child.child("senderId").getValue(Int::class.java) ?: -1
                    
                    val msg = child.getValue(MessageEntity::class.java)
                    if (msg != null) {
                        lastMsg = msg
                        // Only count as unread if BOTH fields are false
                        if (!isReadField && !readField && senderId != currentUserId) {
                            unreadCount++
                        }
                    }
                }

                if (unreadCount > 0) {
                    holder.unreadCountBadge.text = unreadCount.toString()
                    holder.unreadCountBadge.visibility = View.VISIBLE
                } else {
                    holder.unreadCountBadge.visibility = View.GONE
                }

                if (lastMsg != null) {
                    holder.lastMessageText.text = "${lastMsg.senderName}: ${lastMsg.messageText ?: "Media"}"
                    holder.lastMessageText.visibility = View.VISIBLE
                } else {
                    holder.lastMessageText.text = "No messages yet"
                    holder.lastMessageText.visibility = View.VISIBLE
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        })

        if (group.createdByUserId == currentUserId || currentUserRole?.equals("ADMIN", ignoreCase = true) == true) {
            holder.editIcon.visibility = View.VISIBLE
            holder.editIcon.setOnClickListener { onEditClick(group) }
        } else {
            holder.editIcon.visibility = View.GONE
        }

        // Set click listener for group icon to open chat
        holder.groupIcon.setOnClickListener {
            onGroupClick(group)
        }

        // Set click listener for the entire item (excluding icon to prevent conflicts)
        holder.itemView.setOnClickListener { 
            onGroupClick(group) 
        }
    }

    override fun getItemCount() = groups.size

    fun updateGroups(newGroups: List<GroupEntity>) {
        groups = newGroups
        notifyDataSetChanged()
    }
}
