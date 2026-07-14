package com.ljku.truemark

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.*
import com.ljku.truemark.database.GroupEntity
import com.ljku.truemark.database.UserEntity

class FacultyAttendanceGroupsActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var noGroupsText: TextView
    private lateinit var sessionManager: SessionManager
    private var currentUser: UserEntity? = null
    private val groupsRef = FirebaseDatabase.getInstance().getReference("groups")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_faculty_attendance_groups)

        sessionManager = SessionManager(applicationContext)
        currentUser = sessionManager.getLoggedInUser()

        if (currentUser == null) {
            finish()
            return
        }

        recyclerView = findViewById(R.id.groupsRecyclerView)
        noGroupsText = findViewById(R.id.noGroupsTextView)
        val backButton: ImageView = findViewById(R.id.backButton)

        backButton.setOnClickListener { finish() }

        recyclerView.layoutManager = LinearLayoutManager(this)
        
        loadGroups()
    }

    private fun loadGroups() {
        groupsRef.addListenerForSingleValueEvent(object : ValueEventListener {
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
                            // Faculty/Non-admin users can see groups they are members of
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
                    
                    val adapter = GroupAdapter(
                        groups,
                        onGroupClick = { group ->
                            val intent = Intent(this@FacultyAttendanceGroupsActivity, FacultyGroupStudentsActivity::class.java)
                            intent.putExtra("GROUP_ID", group.id)
                            intent.putExtra("GROUP_NAME", group.groupName)
                            intent.putExtra("SUBJECTS", group.subjects)
                            startActivity(intent)
                        },
                        onEditClick = {},
                        currentUserId = currentUser!!.id,
                        currentUserRole = currentUser!!.role
                    )
                    recyclerView.adapter = adapter
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }
}
