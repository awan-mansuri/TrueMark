package com.ljku.truemark

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.*
import com.ljku.truemark.database.NotificationEntity
import com.ljku.truemark.database.UserEntity

class NotificationActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: NotificationAdapter
    private lateinit var noNotificationsText: TextView
    private lateinit var clearAllBtn: TextView
    private lateinit var sessionManager: SessionManager
    private var currentUser: UserEntity? = null
    
    private lateinit var firebaseRef: DatabaseReference
    private var notificationListener: ValueEventListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notification)

        sessionManager = SessionManager(applicationContext)
        currentUser = sessionManager.getLoggedInUser()

        if (currentUser == null) {
            finish()
            return
        }

        firebaseRef = FirebaseDatabase.getInstance().getReference("notifications").child(currentUser!!.id.toString())

        recyclerView = findViewById(R.id.notificationsRecyclerView)
        noNotificationsText = findViewById(R.id.noNotificationsText)
        clearAllBtn = findViewById(R.id.clearAllBtn)
        val backButton: ImageView = findViewById(R.id.backButton)

        backButton.setOnClickListener { finish() }

        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = NotificationAdapter(emptyList())
        recyclerView.adapter = adapter

        startRealtimeNotificationListener()
        
        // Mark as read when activity starts
        markNotificationsAsRead()

        clearAllBtn.setOnClickListener {
            showClearAllConfirmation()
        }
    }

    private fun startRealtimeNotificationListener() {
        notificationListener = firebaseRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val notifications = mutableListOf<NotificationEntity>()
                for (notifSnapshot in snapshot.children) {
                    val notif = notifSnapshot.getValue(NotificationEntity::class.java)
                    if (notif != null) notifications.add(notif)
                }
                
                notifications.sortByDescending { it.timestamp }

                if (notifications.isEmpty()) {
                    noNotificationsText.visibility = View.VISIBLE
                    recyclerView.visibility = View.GONE
                    clearAllBtn.visibility = View.GONE
                } else {
                    noNotificationsText.visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE
                    clearAllBtn.visibility = View.VISIBLE
                    adapter.updateData(notifications)
                }
                
                // Keep marking as read if new notifications arrive while looking at the screen
                markNotificationsAsRead()
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun showClearAllConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Clear Notifications")
            .setMessage("Are you sure you want to delete all notifications?")
            .setPositiveButton("Clear All") { _, _ ->
                clearNotifications()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun clearNotifications() {
        firebaseRef.removeValue().addOnSuccessListener {
            Toast.makeText(this@NotificationActivity, "All notifications cleared", Toast.LENGTH_SHORT).show()
        }
    }

    private fun markNotificationsAsRead() {
        // Find all unread notifications and update them to read
        firebaseRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val updates = mutableMapOf<String, Any>()
                
                for (child in snapshot.children) {
                    val notificationKey = child.key ?: continue
                    
                    // Try both field names to handle Firebase serialization differences
                    val isReadField = child.child("isRead").getValue(Boolean::class.java) ?: true
                    val readField = child.child("read").getValue(Boolean::class.java) ?: true
                    
                    if (!isReadField) {
                        updates["$notificationKey/isRead"] = true
                    }
                    if (!readField) {
                        updates["$notificationKey/read"] = true
                    }
                }
                
                // Batch update all notifications at once
                if (updates.isNotEmpty()) {
                    firebaseRef.updateChildren(updates).addOnSuccessListener {
                        // Force a refresh after successful update
                        runOnUiThread {
                            // Trigger a manual refresh by restarting the listener
                            notificationListener?.let { firebaseRef.removeEventListener(it) }
                            startRealtimeNotificationListener()
                        }
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    override fun onResume() {
        super.onResume()
        // Ensure notifications are marked as read if user returns to this screen
        markNotificationsAsRead()
    }

    override fun onDestroy() {
        super.onDestroy()
        notificationListener?.let { firebaseRef.removeEventListener(it) }
    }
}
