package com.ljku.truemark

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.database.*
import com.ljku.truemark.database.AttendanceSessionEntity
import com.ljku.truemark.database.GroupEntity
import com.ljku.truemark.database.StudentProfileEntity
import com.ljku.truemark.database.UserEntity

class ProfileActivity : AppCompatActivity() {

    private lateinit var profileImageView: ImageView
    private var currentUser: UserEntity? = null
    private lateinit var sessionManager: SessionManager
    private val firebaseDatabase = FirebaseDatabase.getInstance()
    private val usersRef = firebaseDatabase.getReference("users")
    private val profilesRef = firebaseDatabase.getReference("student_profiles")
    private val sessionsRef = firebaseDatabase.getReference("attendance_sessions")
    private val groupsRef = firebaseDatabase.getReference("groups")

    private var activeSessionsListener: ValueEventListener? = null
    private var hasActiveSession = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)
        sessionManager = SessionManager(applicationContext)
        
        profileImageView = findViewById(R.id.profileImageView)
        val editProfileButton: Button = findViewById(R.id.editProfileButton)
        val changePasswordButton: Button = findViewById(R.id.changePasswordButton)
        val logoutButton: Button = findViewById(R.id.logoutProfileButton)
        val backButton: ImageView = findViewById(R.id.backButton)

        backButton.setOnClickListener { finish() }

        // Hide Edit Profile button for non-admin users
        val user = sessionManager.getLoggedInUser()
        if (user?.role?.equals("ADMIN", ignoreCase = true) != true) {
            editProfileButton.visibility = View.GONE
        }

        refreshProfileData()
        observeActiveSessions()

        editProfileButton.setOnClickListener {
            if (user?.role?.equals("ADMIN", ignoreCase = true) == true) {
                startActivity(Intent(this, EditProfileActivity::class.java))
            }
        }
        changePasswordButton.setOnClickListener { showChangePasswordDialog() }
        
        logoutButton.setOnClickListener {
            val user = currentUser ?: return@setOnClickListener
            // Block logout for students during active sessions
            if (user.role.equals("Student", ignoreCase = true) && hasActiveSession) {
                Toast.makeText(this, "Cannot logout during active attendance session", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            performLogout()
        }
    }

    private fun observeActiveSessions() {
        val user = sessionManager.getLoggedInUser() ?: return
        if (!user.role.equals("Student", ignoreCase = true)) return

        groupsRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(groupSnapshot: DataSnapshot) {
                val myGroupIds = mutableListOf<Int>()
                for (child in groupSnapshot.children) {
                    val group = child.child("details").getValue(GroupEntity::class.java)
                    if (group != null) {
                        val memberIds = group.memberIds.split(",").map { it.trim() }
                        if (memberIds.contains(user.id.toString())) {
                            myGroupIds.add(group.id)
                        }
                    }
                }

                activeSessionsListener = sessionsRef.addValueEventListener(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        var activeFound = false
                        val currentTime = System.currentTimeMillis()

                        for (sessionSnapshot in snapshot.children) {
                            val session = sessionSnapshot.getValue(AttendanceSessionEntity::class.java) ?: continue
                            
                            if (session.isActive && myGroupIds.contains(session.groupId)) {
                                activeFound = true
                                break
                            }
                        }
                        
                        hasActiveSession = activeFound
                        updateLogoutButtonState()
                    }

                    override fun onCancelled(error: DatabaseError) {}
                })
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun updateLogoutButtonState() {
        val logoutButton: Button = findViewById(R.id.logoutProfileButton)
        val user = currentUser ?: return
        
        // Only restrict logout for students with active sessions
        if (user.role.equals("Student", ignoreCase = true) && hasActiveSession) {
            logoutButton.alpha = 0.5f
            logoutButton.isEnabled = false
            logoutButton.text = "Logout (Disabled - Active Session)"
        } else {
            logoutButton.alpha = 1.0f
            logoutButton.isEnabled = true
            logoutButton.text = "Logout"
        }
    }

    override fun onResume() {
        super.onResume()
        refreshProfileData()
    }

    private fun refreshProfileData() {
        currentUser = sessionManager.getLoggedInUser()
        if (currentUser == null) {
            finish()
            return
        }

        val fullNameTextView: TextView = findViewById(R.id.fullNameTextView)
        val roleTextView: TextView = findViewById(R.id.roleProfileTextView)
        val idTextView: TextView = findViewById(R.id.idTextView)
        val idLabel: TextView = findViewById(R.id.idLabel)
        val mobileTextView: TextView = findViewById(R.id.mobileTextView)
        
        val academicContainer: View = findViewById(R.id.academicDetailsContainer)
        val rollNoTextView: TextView = findViewById(R.id.rollNoTextView)
        val departmentTextView: TextView = findViewById(R.id.departmentTextView)
        val batchTextView: TextView = findViewById(R.id.batchTextView)
        val dobTextView: TextView = findViewById(R.id.dobTextView)

        fullNameTextView.text = currentUser?.name
        roleTextView.text = "Role: ${currentUser?.role}"
        mobileTextView.text = if (currentUser?.mobile.isNullOrEmpty()) "Not Provided" else currentUser?.mobile

        if (currentUser?.role?.equals("ADMIN", ignoreCase = true) == true) {
            idLabel.text = "Admin ID"
            idTextView.text = currentUser?.email
            academicContainer.visibility = View.GONE
        } else if (currentUser?.role?.equals("Student", ignoreCase = true) == true) {
            idLabel.text = "Student ID"
            idTextView.text = currentUser?.email?.substringBefore("@")
            academicContainer.visibility = View.VISIBLE
            loadStudentAcademicDetails(rollNoTextView, departmentTextView, batchTextView, dobTextView)
        } else {
            idLabel.text = "User ID"
            idTextView.text = currentUser?.email
            academicContainer.visibility = View.GONE
        }

        loadProfileImage()
    }

    private fun loadStudentAcademicDetails(rollView: TextView, deptView: TextView, batchView: TextView, dobView: TextView) {
        val userId = currentUser?.id ?: return
        profilesRef.child(userId.toString()).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val profile = snapshot.getValue(StudentProfileEntity::class.java)
                if (profile != null) {
                    rollView.text = if (profile.rollNumber.isEmpty()) "Not Assigned" else profile.rollNumber
                    deptView.text = if (profile.department.isEmpty()) "N/A" else "${profile.department} / Sem ${profile.semester}"
                    batchView.text = if (profile.division.isEmpty()) "N/A" else "${profile.division} / ${profile.batch}"
                    dobView.text = if (profile.dateOfBirth.isEmpty()) "N/A" else profile.dateOfBirth
                } else {
                    rollView.text = "Not Assigned"
                    deptView.text = "N/A"
                    batchView.text = "N/A"
                    dobView.text = "N/A"
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun loadProfileImage() {
        val profileImageUri = currentUser?.profileImage
        if (!profileImageUri.isNullOrEmpty()) {
            Glide.with(this)
                .load(profileImageUri)
                .placeholder(R.drawable.ic_profile)
                .error(R.drawable.ic_profile)
                .centerCrop()
                .into(profileImageView)
        } else {
            displayDefaultIcon()
        }
    }

    private fun displayDefaultIcon() {
        profileImageView.scaleType = ImageView.ScaleType.CENTER_INSIDE
        profileImageView.setImageResource(R.drawable.ic_profile)
        profileImageView.setColorFilter(ContextCompat.getColor(this, R.color.white))
        profileImageView.background = ContextCompat.getDrawable(this, R.drawable.circular_background)
        profileImageView.setPadding(20, 20, 20, 20)
    }

    private fun performLogout() {
        sessionManager.logout()
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun showChangePasswordDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_change_password, null)
        val oldPwdEdit = dialogView.findViewById<TextInputEditText>(R.id.oldPasswordEditText)
        val newPwdEdit = dialogView.findViewById<TextInputEditText>(R.id.newPasswordEditText)
        val confirmPwdEdit = dialogView.findViewById<TextInputEditText>(R.id.confirmPasswordEditText)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("Change", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            val button = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            button.setOnClickListener {
                val oldPwd = oldPwdEdit.text.toString().trim()
                val newPwd = newPwdEdit.text.toString().trim()
                val confirmPwd = confirmPwdEdit.text.toString().trim()

                if (oldPwd.isEmpty() || newPwd.isEmpty() || confirmPwd.isEmpty()) {
                    Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                if (oldPwd != currentUser?.password) {
                    Toast.makeText(this, "Current password is incorrect", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                if (newPwd.length < 6) {
                    Toast.makeText(this, "New password must be at least 6 characters", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                if (newPwd != confirmPwd) {
                    Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                updatePasswordInFirebase(newPwd, dialog)
            }
        }
        dialog.show()
    }

    private fun updatePasswordInFirebase(newPwd: String, dialog: AlertDialog) {
        currentUser?.let { user ->
            usersRef.child(user.id.toString()).child("password").setValue(newPwd).addOnSuccessListener {
                val updatedUser = user.copy(password = newPwd)
                sessionManager.saveUser(updatedUser)
                currentUser = updatedUser
                Toast.makeText(this@ProfileActivity, "Password updated successfully", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        activeSessionsListener?.let {
            sessionsRef.removeEventListener(it)
        }
    }
}
