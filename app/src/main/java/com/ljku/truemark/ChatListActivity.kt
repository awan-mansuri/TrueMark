package com.ljku.truemark

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.database.*
import com.ljku.truemark.database.GroupEntity
import com.ljku.truemark.database.UserEntity

class ChatListActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var groupAdapter: GroupAdapter
    private lateinit var noGroupsText: TextView
    private lateinit var createGroupFab: FloatingActionButton
    private lateinit var sessionManager: SessionManager
    private var currentUser: UserEntity? = null
    
    private lateinit var database: DatabaseReference
    private var groupsListener: ValueEventListener? = null
    private var totalUnreadCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat_list)

        sessionManager = SessionManager(applicationContext)
        currentUser = sessionManager.getLoggedInUser()

        if (currentUser == null) {
            finish()
            return
        }

        database = FirebaseDatabase.getInstance().getReference("groups")

        recyclerView = findViewById(R.id.groupsRecyclerView)
        noGroupsText = findViewById(R.id.noGroupsTextView)
        createGroupFab = findViewById(R.id.createGroupFab)
        val backButton: ImageView = findViewById(R.id.backButton)

        backButton.setOnClickListener { finish() }

        if (currentUser?.role?.equals("ADMIN", ignoreCase = true) == true || 
            currentUser?.role?.equals("FACULTY", ignoreCase = true) == true) {
            createGroupFab.visibility = View.VISIBLE
        } else {
            createGroupFab.visibility = View.GONE
        }

        recyclerView.layoutManager = LinearLayoutManager(this)
        groupAdapter = GroupAdapter(
            emptyList(),
            onGroupClick = { group ->
                try {
                    val intent = Intent(this, ChatActivity::class.java)
                    intent.putExtra("GROUP_ID", group.id)
                    intent.putExtra("GROUP_NAME", group.groupName)
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(this, "Error opening chat: ${e.message}", Toast.LENGTH_SHORT).show()
                    Log.e("ChatList", "Failed to open chat for group ${group.id}", e)
                }
            },
            onEditClick = { group ->
                val intent = Intent(this, CreateGroupActivity::class.java)
                intent.putExtra("EDIT_MODE", true)
                intent.putExtra("GROUP_ID", group.id)
                startActivity(intent)
            },
            currentUserId = currentUser!!.id,
            currentUserRole = currentUser!!.role
        )
        recyclerView.adapter = groupAdapter

        createGroupFab.setOnClickListener {
            startActivity(Intent(this, CreateGroupActivity::class.java))
        }

        startGroupsRealtimeListener()
        
        // Clear chat badge when user opens chat list
        clearChatBadge()
    }
    
    private fun clearChatBadge() {
        // Simple approach: Clear badge when user accesses chat
        // Badge will be updated by GroupAdapter if there are unread messages
        try {
            val bottomNavigationView = findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottomNavigationView)
            if (bottomNavigationView != null) {
                val badge = bottomNavigationView.getOrCreateBadge(R.id.nav_chat)
                badge.isVisible = false
            }
        } catch (e: Exception) {
            // Bottom navigation might not exist in this activity, that's fine
        }
    }

    private fun startGroupsRealtimeListener() {
        groupsListener = database.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val groups = mutableListOf<GroupEntity>()
                val isSystemAdmin = currentUser?.email?.equals("admin@truemark.com", ignoreCase = true) == true
                val isAdmin = currentUser?.role?.equals("ADMIN", ignoreCase = true) == true
                
                for (groupSnapshot in snapshot.children) {
                    val group = groupSnapshot.child("details").getValue(GroupEntity::class.java)
                    if (group != null) {
                        val memberIds = group.memberIds.split(",").map { it.trim() }
                        
                        when {
                            // System Admin can see ALL groups
                            isSystemAdmin -> {
                                groups.add(group)
                            }
                            // Regular Admin can only see groups they CREATED
                            isAdmin -> {
                                if (group.createdByUserId == currentUser!!.id) {
                                    groups.add(group)
                                }
                            }
                            // Non-admin users can see groups they are members of
                            memberIds.contains(currentUser!!.id.toString()) -> {
                                groups.add(group)
                            }
                        }
                    }
                }

                if (groups.isEmpty()) {
                    noGroupsText.visibility = View.VISIBLE
                    recyclerView.visibility = View.GONE
                } else {
                    noGroupsText.visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE
                    groupAdapter.updateGroups(groups)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@ChatListActivity, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        groupsListener?.let { database.removeEventListener(it) }
    }
}
