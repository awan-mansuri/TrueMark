package com.ljku.truemark

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.firebase.database.*
import com.ljku.truemark.database.GroupEntity
import com.ljku.truemark.database.UserEntity
import com.ljku.truemark.database.NotificationEntity

class GroupDetailsActivity : AppCompatActivity() {

    private lateinit var groupNameText: TextView
    private lateinit var createdByText: TextView
    private lateinit var subjectsText: TextView
    private lateinit var membersRecyclerView: RecyclerView
    private lateinit var memberAdapter: UserAdapter
    private lateinit var sessionManager: SessionManager
    private lateinit var settingsCard: CardView
    private lateinit var adminOptionsCard: CardView
    private lateinit var onlyAdminsCanMessageSwitch: SwitchCompat
    private lateinit var addMemberBtn: MaterialButton
    private lateinit var removeAllMembersBtn: MaterialButton
    private lateinit var deleteGroupBtn: MaterialButton
    private lateinit var editGroupBtn: ImageView
    
    private var groupId: Int = -1
    private var currentUser: UserEntity? = null
    private var currentGroup: GroupEntity? = null
    
    private val groupsRef = FirebaseDatabase.getInstance().getReference("groups")
    private val usersRef = FirebaseDatabase.getInstance().getReference("users")
    private val notificationsRef = FirebaseDatabase.getInstance().getReference("notifications")
    private val sessionsRef = FirebaseDatabase.getInstance().getReference("attendance_sessions")
    private val recordsRef = FirebaseDatabase.getInstance().getReference("attendance_records")
    private val messagesRef = FirebaseDatabase.getInstance().getReference("messages")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_group_details)

        sessionManager = SessionManager(applicationContext)
        currentUser = sessionManager.getLoggedInUser()
        
        groupId = intent.getIntExtra("GROUP_ID", -1)
        if (groupId == -1 || currentUser == null) {
            finish()
            return
        }

        groupNameText = findViewById(R.id.detailsGroupName)
        createdByText = findViewById(R.id.detailsCreatedBy)
        subjectsText = findViewById(R.id.detailsSubjects)
        membersRecyclerView = findViewById(R.id.groupMembersRecyclerView)
        settingsCard = findViewById(R.id.settingsCard)
        adminOptionsCard = findViewById(R.id.adminOptionsCard)
        onlyAdminsCanMessageSwitch = findViewById(R.id.onlyAdminsCanMessageSwitch)
        addMemberBtn = findViewById(R.id.addMemberBtn)
        removeAllMembersBtn = findViewById(R.id.removeAllMembersBtn)
        deleteGroupBtn = findViewById(R.id.deleteGroupBtn)
        editGroupBtn = findViewById(R.id.editGroupNameBtn)

        membersRecyclerView.layoutManager = LinearLayoutManager(this)
        
        addMemberBtn.setOnClickListener { showAddMemberDialog() }
        removeAllMembersBtn.setOnClickListener { showRemoveAllConfirmation() }
        deleteGroupBtn.setOnClickListener { showDeleteGroupConfirmation() }
        editGroupBtn.setOnClickListener { showEditGroupDialog() }

        // Set up back button
        findViewById<ImageView>(R.id.backButton).setOnClickListener {
            finish()
        }

        // Make status bar transparent and extend header under it
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = ContextCompat.getColor(this, android.R.color.transparent)
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false

        // Handle status bar insets for notch/edge-to-edge screens
        val headerContentContainer = findViewById<LinearLayout>(R.id.headerContentContainer)
        ViewCompat.setOnApplyWindowInsetsListener(headerContentContainer) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars())
            view.setPadding(16.dpToPx(), insets.top + 16.dpToPx(), 16.dpToPx(), 16.dpToPx())
            windowInsets
        }

        loadGroupDetails()
    }

    private fun loadGroupDetails() {
        groupsRef.child(groupId.toString()).child("details").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                currentGroup = snapshot.getValue(GroupEntity::class.java)
                if (currentGroup != null) {
                    val memberIdsList = currentGroup!!.memberIds.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    val adminIdsList = currentGroup!!.adminIds.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    
                    usersRef.addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(userSnapshot: DataSnapshot) {
                            val members = mutableListOf<UserEntity>()
                            for (id in memberIdsList) {
                                userSnapshot.child(id).getValue(UserEntity::class.java)?.let { members.add(it) }
                            }

                            // Fetch roll numbers for students
                            val studentProfilesRef = FirebaseDatabase.getInstance().getReference("student_profiles")
                            studentProfilesRef.addListenerForSingleValueEvent(object : ValueEventListener {
                                override fun onDataChange(profilesSnapshot: DataSnapshot) {
                                    val rollNumbersMap = mutableMapOf<Int, String>()
                                    for (member in members) {
                                        if (member.role.equals("Student", ignoreCase = true)) {
                                            val profile = profilesSnapshot.child(member.id.toString()).getValue(com.ljku.truemark.database.StudentProfileEntity::class.java)
                                            val rollNumber = profile?.rollNumber?.takeIf { it.isNotBlank() }
                                            if (rollNumber != null) {
                                                rollNumbersMap[member.id] = rollNumber
                                            }
                                        }
                                    }

                                    val isUserGroupAdmin = adminIdsList.contains(currentUser!!.id.toString()) || 
                                                         currentUser!!.role.equals("ADMIN", true)

                                    groupNameText.text = currentGroup!!.groupName
                                    createdByText.text = "Created by: ${currentGroup!!.creatorName}"
                                    subjectsText.text = "Subjects: ${currentGroup!!.subjects ?: "None"}"
                                    
                                    if (isUserGroupAdmin) {
                                        settingsCard.visibility = View.VISIBLE
                                        adminOptionsCard.visibility = View.VISIBLE
                                        addMemberBtn.visibility = View.VISIBLE
                                        editGroupBtn.visibility = View.VISIBLE
                                        onlyAdminsCanMessageSwitch.isChecked = currentGroup!!.onlyAdminsCanMessage
                                        
                                        onlyAdminsCanMessageSwitch.setOnCheckedChangeListener { _, isChecked ->
                                            updateMessagingRestriction(isChecked)
                                        }
                                    } else {
                                        settingsCard.visibility = View.GONE
                                        adminOptionsCard.visibility = View.GONE
                                        addMemberBtn.visibility = View.GONE
                                    }

                                    val sortedMembers = members.sortedWith(compareBy<UserEntity> {
                                        !adminIdsList.contains(it.id.toString())
                                    }.thenBy {
                                        when (it.role.lowercase()) {
                                            "faculty" -> 0
                                            "student" -> 1
                                            else -> 2
                                        }
                                    }.thenBy {
                                        // Sort students by roll number if available
                                        if (it.role.equals("Student", ignoreCase = true)) {
                                            rollNumbersMap[it.id]?.toIntOrNull() ?: Int.MAX_VALUE
                                        } else {
                                            0 // Non-students keep their original order
                                        }
                                    }.thenBy { it.name })

                                    memberAdapter = UserAdapter(sortedMembers,
                                        onUserClick = { user ->
                                            if (isUserGroupAdmin) {
                                                showMemberOptions(user, adminIdsList.mapNotNull { it.toIntOrNull() })
                                            }
                                        },
                                        showActions = false,
                                        rollNumbers = rollNumbersMap,
                                        groupAdminIds = adminIdsList.mapNotNull { it.toIntOrNull() }
                                    ) 
                                    membersRecyclerView.adapter = memberAdapter
                                }
                                override fun onCancelled(error: DatabaseError) {}
                            })
                        }
                        override fun onCancelled(error: DatabaseError) {}
                    })
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun showAddMemberDialog() {
        usersRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val currentMemberIds = currentGroup?.memberIds?.split(",")?.map { it.trim() } ?: emptyList()
                val isDefaultAdmin = currentUser?.email == "admin@truemark.com"
                
                val availableUsers = mutableListOf<UserEntity>()
                for (child in snapshot.children) {
                    val user = child.getValue(UserEntity::class.java)
                    if (user != null && user.id != currentUser?.id && !currentMemberIds.contains(user.id.toString())) {
                        // Filter out users with "-" or blank names
                        if (user.name == "-" || user.name.isBlank()) continue
                        if (isDefaultAdmin || user.createdBy == currentUser?.id) {
                            if (user.email != "admin@truemark.com" && !user.role.equals("ADMIN", true)) {
                                availableUsers.add(user)
                            }
                        }
                    }
                }

                if (availableUsers.isEmpty()) {
                    Toast.makeText(this@GroupDetailsActivity, "No more users available to add", Toast.LENGTH_SHORT).show()
                    return
                }

                val userNames = availableUsers.map { it.name }.toTypedArray()
                val selectedItems = BooleanArray(userNames.size) { false }
                val chosenUsers = mutableListOf<UserEntity>()

                AlertDialog.Builder(this@GroupDetailsActivity)
                    .setTitle("Add Members")
                    .setMultiChoiceItems(userNames, selectedItems) { _, which, isChecked ->
                        if (isChecked) chosenUsers.add(availableUsers[which])
                        else chosenUsers.remove(availableUsers[which])
                    }
                    .setPositiveButton("Add") { _, _ ->
                        if (chosenUsers.isNotEmpty()) addChosenMembers(chosenUsers)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun addChosenMembers(newUsers: List<UserEntity>) {
        currentGroup?.let { group ->
            val currentIds = group.memberIds.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
            val addedUsers = mutableListOf<UserEntity>()
            newUsers.forEach { user ->
                if (!currentIds.contains(user.id.toString())) {
                    currentIds.add(user.id.toString())
                    addedUsers.add(user)
                    sendNotification(user.id, "Added to Group", "You have been added to group: ${group.groupName}")
                }
            }
            val updatedGroup = group.copy(memberIds = currentIds.joinToString(","))
            groupsRef.child(groupId.toString()).child("details").setValue(updatedGroup).addOnSuccessListener {
                Toast.makeText(this@GroupDetailsActivity, "${addedUsers.size} members added", Toast.LENGTH_SHORT).show()
                // Send system messages for added users
                val adminName = currentUser?.name ?: "Admin"
                addedUsers.forEach { user ->
                    val messageText = "$adminName added ${user.name}"
                    sendSystemMessage(groupId, messageText)
                }
            }
        }
    }

    private fun showRemoveAllConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Remove All Members")
            .setMessage("Are you sure you want to remove all members except yourself?")
            .setPositiveButton("Remove All") { _, _ -> removeAllMembers() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun removeAllMembers() {
        currentGroup?.let { group ->
            val updatedGroup = group.copy(
                memberIds = currentUser!!.id.toString(),
                adminIds = currentUser!!.id.toString()
            )
            groupsRef.child(groupId.toString()).child("details").setValue(updatedGroup).addOnSuccessListener {
                Toast.makeText(this, "All members removed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showDeleteGroupConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Delete Group")
            .setMessage("This will delete the group permanently for all members. Are you sure?")
            .setPositiveButton("Delete") { _, _ -> deleteGroup() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteGroup() {
        // First, find and delete all attendance sessions and records for this group
        sessionsRef.orderByChild("groupId").equalTo(groupId.toDouble()).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val sessionIds = mutableListOf<String>()
                val deleteTasks = mutableListOf<com.google.android.gms.tasks.Task<Void>>()
                
                // Collect all session IDs for this group
                for (sessionSnapshot in snapshot.children) {
                    sessionSnapshot.key?.let { sessionIds.add(it) }
                    // Delete the session
                    deleteTasks.add(sessionSnapshot.ref.removeValue())
                }
                
                // Delete all attendance records for these sessions
                // Records are stored with key: "{sessionId}_{studentId}"
                for (sessionId in sessionIds) {
                    recordsRef.orderByChild("sessionId").equalTo(sessionId.toDouble()).addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(recordsSnapshot: DataSnapshot) {
                            for (record in recordsSnapshot.children) {
                                record.ref.removeValue()
                            }
                        }
                        override fun onCancelled(error: DatabaseError) {}
                    })
                }
                
                // Delete the group itself after sessions are deleted
                com.google.android.gms.tasks.Tasks.whenAll(deleteTasks).addOnCompleteListener {
                    groupsRef.child(groupId.toString()).removeValue().addOnSuccessListener {
                        Toast.makeText(this@GroupDetailsActivity, "Group and all attendance data deleted", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {
                // If query fails, just delete the group
                groupsRef.child(groupId.toString()).removeValue().addOnSuccessListener {
                    Toast.makeText(this@GroupDetailsActivity, "Group deleted", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        })
    }

    private fun updateMessagingRestriction(onlyAdmins: Boolean) {
        currentGroup?.let { group ->
            val updatedGroup = group.copy(onlyAdminsCanMessage = onlyAdmins)
            groupsRef.child(groupId.toString()).child("details").setValue(updatedGroup)
        }
    }

    private fun showMemberOptions(user: UserEntity, currentAdmins: List<Int>) {
        // Don't show options for current admin themselves
        if (user.id == currentUser!!.id) {
            Toast.makeText(this, "You cannot modify your own membership", Toast.LENGTH_SHORT).show()
            return
        }
        
        val options = mutableListOf<String>()
        val isAdmin = currentAdmins.contains(user.id)
        if (isAdmin) options.add("Dismiss as Admin") else options.add("Make Group Admin")
        options.add("Remove from Group")

        AlertDialog.Builder(this)
            .setTitle(user.name)
            .setItems(options.toTypedArray()) { _, which ->
                when (which) {
                    0 -> {
                        // First option: Make/Dismiss Admin
                        if (isAdmin) {
                            toggleAdminStatus(user, false)
                        } else {
                            toggleAdminStatus(user, true)
                        }
                    }
                    1 -> {
                        // Second option: Remove from Group
                        showRemoveMemberConfirmation(user)
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showRemoveMemberConfirmation(user: UserEntity) {
        AlertDialog.Builder(this)
            .setTitle("Remove Member")
            .setMessage("Are you sure you want to remove ${user.name} from this group?")
            .setPositiveButton("Remove") { _, _ -> removeMemberFromGroup(user) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun toggleAdminStatus(user: UserEntity, makeAdmin: Boolean) {
        currentGroup?.let { group ->
            val adminList = group.adminIds.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
            if (makeAdmin) {
                if (!adminList.contains(user.id.toString())) adminList.add(user.id.toString())
                sendNotification(user.id, "New Responsibility", "You have been made an admin of group: ${group.groupName}")
            } else {
                adminList.remove(user.id.toString())
            }
            val updatedGroup = group.copy(adminIds = adminList.joinToString(","))
            groupsRef.child(groupId.toString()).child("details").setValue(updatedGroup)
        }
    }

    private fun removeMemberFromGroup(user: UserEntity) {
        currentGroup?.let { group ->
            val memberList = group.memberIds.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
            memberList.remove(user.id.toString())
            val adminList = group.adminIds.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
            adminList.remove(user.id.toString())
            val updatedGroup = group.copy(memberIds = memberList.joinToString(","), adminIds = adminList.joinToString(","))
            groupsRef.child(groupId.toString()).child("details").setValue(updatedGroup).addOnSuccessListener {
                // Send system message about member removal
                val adminName = currentUser?.name ?: "Admin"
                val messageText = "$adminName removed ${user.name}"
                sendSystemMessage(groupId, messageText)
                Toast.makeText(this@GroupDetailsActivity, "${user.name} removed from group", Toast.LENGTH_SHORT).show()
            }.addOnFailureListener {
                Toast.makeText(this@GroupDetailsActivity, "Failed to remove member", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun sendSystemMessage(groupId: Int, messageText: String) {
        val messageId = System.currentTimeMillis().toInt()
        val message = com.ljku.truemark.database.MessageEntity(
            id = messageId,
            groupId = groupId,
            senderId = 0, // System message
            senderName = "System",
            messageText = messageText,
            messageType = "SYSTEM",
            timestamp = System.currentTimeMillis()
        )
        // Store in groups/{groupId}/messages so ChatActivity can display it
        groupsRef.child(groupId.toString()).child("messages").child(messageId.toString()).setValue(message)
    }

    private fun sendNotification(userId: Int, title: String, message: String) {
        val notificationId = notificationsRef.child(userId.toString()).push().key ?: System.currentTimeMillis().toString()
        val notification = NotificationEntity(
            id = notificationId.hashCode(),
            userId = userId,
            title = title,
            message = message,
            type = "CHAT",
            timestamp = System.currentTimeMillis()
        )
        notificationsRef.child(userId.toString()).child(notificationId).setValue(notification)
    }

    private fun showEditGroupDialog() {
        currentGroup?.let { group ->
            val dialogView = layoutInflater.inflate(R.layout.dialog_edit_group, null)
            val nameInput = dialogView.findViewById<android.widget.EditText>(R.id.editGroupNameInput)
            val subjectsInput = dialogView.findViewById<android.widget.EditText>(R.id.editGroupSubjectsInput)

            nameInput.setText(group.groupName)
            subjectsInput.setText(group.subjects ?: "")

            AlertDialog.Builder(this)
                .setTitle("Edit Group")
                .setView(dialogView)
                .setPositiveButton("Save") { _, _ ->
                    val newName = nameInput.text.toString().trim()
                    val newSubjects = subjectsInput.text.toString().trim()

                    if (newName.isNotEmpty()) {
                        val updatedGroup = group.copy(
                            groupName = newName,
                            subjects = newSubjects
                        )
                        groupsRef.child(groupId.toString()).child("details").setValue(updatedGroup)
                            .addOnSuccessListener {
                                Toast.makeText(this, "Group updated successfully", Toast.LENGTH_SHORT).show()
                            }
                            .addOnFailureListener {
                                Toast.makeText(this, "Failed to update group", Toast.LENGTH_SHORT).show()
                            }
                    } else {
                        Toast.makeText(this, "Group name cannot be empty", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun Int.dpToPx(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }
}
