package com.ljku.truemark

import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.database.*
import com.ljku.truemark.database.UserEntity
import com.ljku.truemark.database.StudentProfileEntity

class AdminAddUserActivity : AppCompatActivity() {

    private lateinit var sessionManager: SessionManager
    private val database = FirebaseDatabase.getInstance()
    private val usersRef = database.getReference("users")
    private val profilesRef = database.getReference("student_profiles")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_add_user)

        val backButton: ImageView = findViewById(R.id.backButton)
        backButton.setOnClickListener { finish() }

        sessionManager = SessionManager(this)
        val imageContainer: FrameLayout = findViewById(R.id.profileImageContainer)
        val nameEditText: TextInputEditText = findViewById(R.id.addUserName)
        val enrollmentEditText: TextInputEditText = findViewById(R.id.addUserEnrollment)
        val enrollmentInputLayout: TextInputLayout = findViewById(R.id.enrollmentInputLayout)
        val emailEditText: TextInputEditText = findViewById(R.id.addUserEmail)
        val mobileEditText: TextInputEditText = findViewById(R.id.addUserMobile)
        val passwordEditText: TextInputEditText = findViewById(R.id.addUserPassword)
        val roleToggleGroup: MaterialButtonToggleGroup = findViewById(R.id.addUserRoleToggleGroup)
        val createButton: MaterialButton = findViewById(R.id.adminCreateUserButton)

        // Toggle Enrollment field based on role
        roleToggleGroup.addOnButtonCheckedListener { group, checkedId, isChecked ->
            if (isChecked) {
                if (checkedId == R.id.addFacultyButton) {
                    enrollmentInputLayout.visibility = View.GONE
                } else {
                    enrollmentInputLayout.visibility = View.VISIBLE
                }
            }
        }

        // Profile image is static - no click action
        imageContainer.isClickable = false

        // Back button click handler
        findViewById<ImageView>(R.id.backButton).setOnClickListener { finish() }

        createButton.setOnClickListener {
            val name = nameEditText.text.toString().trim()
            val enrollment = enrollmentEditText.text.toString().trim()
            val email = emailEditText.text.toString().trim()
            val mobile = mobileEditText.text.toString().trim()
            val password = passwordEditText.text.toString().trim()
            
            val selectedRoleId = roleToggleGroup.checkedButtonId
            val role = if (selectedRoleId == R.id.addFacultyButton) "Faculty" else "Student"

            if (name.isEmpty() || email.isEmpty() || mobile.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (role == "Student" && enrollment.isEmpty()) {
                Toast.makeText(this, "Please enter enrollment number", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (role == "Student" && enrollment.length != 12) {
                Toast.makeText(this, "Enrollment number must be exactly 12 digits", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val loginId = if (role == "Student") "$enrollment@tm.com" else email
            val currentAdmin = sessionManager.getLoggedInUser()

            // Check for duplicates before creating user
            checkForDuplicatesAndCreateUser(name, loginId, mobile, password, role, email, enrollment, currentAdmin?.id)
        }
    }

    private fun checkForDuplicatesAndCreateUser(name: String, loginId: String, mobile: String, password: String, role: String, personalEmail: String, enrollment: String, adminId: Int?) {
        // First, keep data fresh by clearing any cached data
        usersRef.keepSynced(true)
        profilesRef.keepSynced(true)
        
        // For students, check enrollment FIRST (since it determines the loginId)
        if (role == "Student") {
            // Check enrollment directly by child path for more reliable results
            profilesRef.child(enrollment).addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(enrollmentSnapshot: DataSnapshot) {
                    if (enrollmentSnapshot.exists()) {
                        // Also check if the user actually exists
                        val userId = enrollmentSnapshot.child("userId").value?.toString()
                        if (userId != null) {
                            usersRef.child(userId).addListenerForSingleValueEvent(object : ValueEventListener {
                                override fun onDataChange(userSnapshot: DataSnapshot) {
                                    if (userSnapshot.exists()) {
                                        Toast.makeText(this@AdminAddUserActivity, "Enrollment number already registered", Toast.LENGTH_SHORT).show()
                                    } else {
                                        // Profile exists but user doesn't - clean up stale profile
                                        profilesRef.child(enrollment).removeValue()
                                        checkEmailAndMobileForAdmin(name, loginId, mobile, password, role, personalEmail, enrollment, adminId)
                                    }
                                }
                                override fun onCancelled(error: DatabaseError) {}
                            })
                        } else {
                            Toast.makeText(this@AdminAddUserActivity, "Enrollment number already registered", Toast.LENGTH_SHORT).show()
                        }
                        return
                    }
                    // Enrollment OK, now check email and mobile
                    checkEmailAndMobileForAdmin(name, loginId, mobile, password, role, personalEmail, enrollment, adminId)
                }
                override fun onCancelled(error: DatabaseError) {}
            })
        } else {
            // Faculty - check email and mobile only
            checkEmailAndMobileForAdmin(name, loginId, mobile, password, role, personalEmail, enrollment, adminId)
        }
    }

    private fun checkEmailAndMobileForAdmin(name: String, loginId: String, mobile: String, password: String, role: String, personalEmail: String, enrollment: String, adminId: Int?) {
        // Check if email/loginId already exists by direct lookup
        usersRef.child(loginId.hashCode().toString()).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    Toast.makeText(this@AdminAddUserActivity, "Email already registered", Toast.LENGTH_SHORT).show()
                    return
                }
                
                // Check using orderByChild as backup
                usersRef.orderByChild("email").equalTo(loginId).addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(emailSnapshot: DataSnapshot) {
                        val hasEmailConflict = emailSnapshot.children.any { 
                            it.getValue(UserEntity::class.java)?.email == loginId 
                        }
                        if (hasEmailConflict) {
                            Toast.makeText(this@AdminAddUserActivity, "Email already registered", Toast.LENGTH_SHORT).show()
                            return
                        }
                        
                        // Check if mobile already exists
                        checkMobileDuplicate(name, loginId, mobile, password, role, personalEmail, enrollment, adminId)
                    }
                    override fun onCancelled(error: DatabaseError) {}
                })
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }
    
    private fun checkMobileDuplicate(name: String, loginId: String, mobile: String, password: String, role: String, personalEmail: String, enrollment: String, adminId: Int?) {
        usersRef.orderByChild("mobile").equalTo(mobile).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(mobileSnapshot: DataSnapshot) {
                val hasMobileConflict = mobileSnapshot.children.any { 
                    val userMobile = it.child("mobile").value as? String
                    userMobile == mobile
                }
                if (hasMobileConflict) {
                    Toast.makeText(this@AdminAddUserActivity, "Mobile number already registered", Toast.LENGTH_SHORT).show()
                    return
                }
                
                // All checks passed, create user (without profile image)
                val newUserId = System.currentTimeMillis().toInt()
                saveUserToDatabase(newUserId, name, loginId, mobile, password, role, personalEmail, enrollment, adminId, null)
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun saveUserToDatabase(userId: Int, name: String, loginId: String, mobile: String, password: String, role: String, personalEmail: String, enrollment: String, adminId: Int?, imageUrl: String?) {
        val user = UserEntity(
            id = userId,
            name = name, 
            email = loginId, 
            password = password, 
            role = role, 
            mobile = mobile,
            personalEmail = personalEmail,
            createdBy = adminId,
            profileImage = imageUrl
        )
        
        usersRef.child(userId.toString()).setValue(user).addOnSuccessListener {
            if (role == "Student") {
                val emptyProfile = StudentProfileEntity(
                    userId = userId,
                    enrollmentNo = enrollment,
                    rollNumber = "",
                    department = "",
                    semester = "",
                    division = "",
                    batch = "",
                    dateOfBirth = ""
                )
                profilesRef.child(userId.toString()).setValue(emptyProfile)
            }
            Toast.makeText(this@AdminAddUserActivity, "$role added successfully!", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
