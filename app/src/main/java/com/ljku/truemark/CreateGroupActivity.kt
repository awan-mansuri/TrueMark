package com.ljku.truemark

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.*
import com.ljku.truemark.database.GroupEntity
import com.ljku.truemark.database.NotificationEntity
import com.ljku.truemark.database.UserEntity

class CreateGroupActivity : AppCompatActivity() {

    private lateinit var groupNameEditText: EditText
    private lateinit var subjectsEditText: EditText
    private lateinit var membersRecyclerView: RecyclerView
    private lateinit var createBtn: Button
    private lateinit var memberAdapter: MemberAdapter
    private lateinit var sessionManager: SessionManager
    private var currentUser: UserEntity? = null
    
    private var isEditMode = false
    private var groupId = -1
    private val firebaseDatabase = FirebaseDatabase.getInstance()
    private val firebaseGroups = firebaseDatabase.getReference("groups")
    private val firebaseUsers = firebaseDatabase.getReference("users")
    private val firebaseNotifications = firebaseDatabase.getReference("notifications")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_group)

        sessionManager = SessionManager(applicationContext)
        currentUser = sessionManager.getLoggedInUser()

        if (currentUser == null) {
            finish()
            return
        }

        groupNameEditText = findViewById(R.id.groupNameEditText)
        subjectsEditText = findViewById(R.id.subjectsEditText)
        membersRecyclerView = findViewById(R.id.membersRecyclerView)
        createBtn = findViewById(R.id.createBtn)
        
        // Back button handler
        findViewById<ImageView>(R.id.backButton).setOnClickListener { finish() }

        isEditMode = intent.getBooleanExtra("EDIT_MODE", false)
        groupId = intent.getIntExtra("GROUP_ID", -1)

        membersRecyclerView.layoutManager = LinearLayoutManager(this)
        
        if (isEditMode) {
            createBtn.text = "Update Group"
            loadGroupData()
        } else {
            loadAllUsers()
        }

        createBtn.setOnClickListener {
            val groupName = groupNameEditText.text.toString().trim()
            val subjects = subjectsEditText.text.toString().trim()
            val selectedMemberIds = memberAdapter.getSelectedMemberIds()

            if (groupName.isEmpty()) {
                Toast.makeText(this, "Please enter group name", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (selectedMemberIds.isEmpty()) {
                Toast.makeText(this, "Please select at least one member", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val finalMembers = if (!selectedMemberIds.split(",").contains(currentUser!!.id.toString())) {
                "$selectedMemberIds,${currentUser!!.id}"
            } else {
                selectedMemberIds
            }

            if (isEditMode) {
                updateGroup(groupName, subjects, finalMembers)
            } else {
                createNewGroup(groupName, subjects, finalMembers)
            }
        }
    }

    private fun loadAllUsers() {
        firebaseUsers.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val users = mutableListOf<UserEntity>()
                val isDefaultAdmin = currentUser?.email == "admin@truemark.com"
                
                for (child in snapshot.children) {
                    val user = child.getValue(UserEntity::class.java)
                    if (user != null && user.id != currentUser?.id) {
                        if (isDefaultAdmin || user.createdBy == currentUser?.id) {
                            if (user.email != "admin@truemark.com" && !user.name.isNullOrBlank() && user.name != "-") {
                                users.add(user)
                            }
                        }
                    }
                }
                
                memberAdapter = MemberAdapter(users)
                membersRecyclerView.adapter = memberAdapter
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun loadGroupData() {
        firebaseGroups.child(groupId.toString()).child("details").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(groupSnapshot: DataSnapshot) {
                val group = groupSnapshot.getValue(GroupEntity::class.java)
                if (group != null) {
                    groupNameEditText.setText(group.groupName)
                    subjectsEditText.setText(group.subjects ?: "")
                    val selectedIds = group.memberIds.split(",").mapNotNull { it.toIntOrNull() }
                    
                    firebaseUsers.addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            val users = mutableListOf<UserEntity>()
                            val isDefaultAdmin = currentUser?.email == "admin@truemark.com"
                            for (child in snapshot.children) {
                                val user = child.getValue(UserEntity::class.java)
                                if (user != null && user.id != currentUser?.id) {
                                    if (isDefaultAdmin || user.createdBy == currentUser?.id) {
                                        if (user.email != "admin@truemark.com" && !user.name.isNullOrBlank() && user.name != "-") {
                                            users.add(user)
                                        }
                                    }
                                }
                            }
                            memberAdapter = MemberAdapter(users, selectedIds)
                            membersRecyclerView.adapter = memberAdapter
                        }
                        override fun onCancelled(error: DatabaseError) {}
                    })
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun createNewGroup(name: String, subjects: String, members: String) {
        val newGroupId = System.currentTimeMillis().toInt()
        val group = GroupEntity(
            id = newGroupId,
            groupName = name,
            createdByUserId = currentUser!!.id,
            creatorName = currentUser!!.name,
            memberIds = members,
            adminIds = currentUser!!.id.toString(),
            subjects = if (subjects.isEmpty()) null else subjects
        )
        
        firebaseGroups.child(newGroupId.toString()).child("details").setValue(group).addOnSuccessListener {
            val memberIdList = members.split(",").mapNotNull { it.toIntOrNull() }
            memberIdList.forEach { id ->
                val notificationId = firebaseNotifications.child(id.toString()).push().key ?: System.currentTimeMillis().toString()
                val notification = NotificationEntity(
                    id = notificationId.hashCode(),
                    userId = id,
                    title = "New Group Created",
                    message = "You have been added to group: $name",
                    type = "CHAT",
                    timestamp = System.currentTimeMillis()
                )
                firebaseNotifications.child(id.toString()).child(notificationId).setValue(notification)
            }
            Toast.makeText(this@CreateGroupActivity, "Group created successfully", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun updateGroup(name: String, subjects: String, members: String) {
        firebaseGroups.child(groupId.toString()).child("details").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val existingGroup = snapshot.getValue(GroupEntity::class.java)
                if (existingGroup != null) {
                    val updatedGroup = existingGroup.copy(
                        groupName = name, 
                        memberIds = members,
                        subjects = if (subjects.isEmpty()) null else subjects
                    )
                    firebaseGroups.child(groupId.toString()).child("details").setValue(updatedGroup).addOnSuccessListener {
                        Toast.makeText(this@CreateGroupActivity, "Group updated successfully", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }
}
