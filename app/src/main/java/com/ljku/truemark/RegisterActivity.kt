package com.ljku.truemark

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.ljku.truemark.database.UserEntity
import com.ljku.truemark.database.StudentProfileEntity

class RegisterActivity : AppCompatActivity() {
    
    private lateinit var sessionManager: SessionManager
    private val usersRef = FirebaseDatabase.getInstance().getReference("users")
    private val profilesRef = FirebaseDatabase.getInstance().getReference("student_profiles")
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)
        sessionManager = SessionManager(applicationContext)

        val nameEditText: TextInputEditText = findViewById(R.id.name)
        val enrollmentEditText: TextInputEditText = findViewById(R.id.enrollmentRegister)
        val enrollmentInputLayout: TextInputLayout = findViewById(R.id.enrollmentInputLayout)
        val personalEmailEditText: TextInputEditText = findViewById(R.id.personalEmailRegister)
        val mobileEditText: TextInputEditText = findViewById(R.id.mobileNumber)
        val passwordEditText: TextInputEditText = findViewById(R.id.passwordRegister)
        val confirmPasswordEditText: TextInputEditText = findViewById(R.id.confirmPasswordRegister)
        val roleToggleGroup: MaterialButtonToggleGroup = findViewById(R.id.roleToggleGroup)
        val registerButton: Button = findViewById(R.id.registerButton)
        val loginTextView: TextView = findViewById(R.id.loginTextView)

        roleToggleGroup.addOnButtonCheckedListener { group, checkedId, isChecked ->
            if (isChecked) {
                if (checkedId == R.id.facultyButton) {
                    enrollmentInputLayout.visibility = View.GONE
                } else {
                    enrollmentInputLayout.visibility = View.VISIBLE
                }
            }
        }

        registerButton.setOnClickListener {
            val name = nameEditText.text.toString().trim()
            val enrollment = enrollmentEditText.text.toString().trim()
            val personalEmail = personalEmailEditText.text.toString().trim()
            val mobile = mobileEditText.text.toString().trim()
            val password = passwordEditText.text.toString()
            val confirmPassword = confirmPasswordEditText.text.toString()
            
            val selectedRoleId = roleToggleGroup.checkedButtonId
            val role = if (selectedRoleId == R.id.facultyButton) "Faculty" else "Student"

            if (name.isEmpty() || personalEmail.isEmpty() || mobile.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (role == "Student") {
                if (enrollment.length != 12 || !enrollment.all { it.isDigit() }) {
                    Toast.makeText(this, "Enrollment Number must be exactly 12 digits", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
            }

            if (mobile.length != 10 || !mobile.all { it.isDigit() }) {
                Toast.makeText(this, "Please enter a valid 10-digit mobile number", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (password != confirmPassword) {
                Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val emailToRegister = if (role == "Student") "$enrollment@tm.com" else personalEmail
            
            // Check all duplicates before registering
            checkForDuplicatesAndRegister(name, emailToRegister, mobile, password, role, personalEmail, enrollment)
        }

        loginTextView.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }
    }

    private fun checkForDuplicatesAndRegister(name: String, email: String, mobile: String, password: String, role: String, personalEmail: String, enrollment: String) {
        // For students, check enrollment FIRST (since it determines the email/loginId)
        if (role == "Student") {
            profilesRef.orderByChild("enrollmentNo").equalTo(enrollment).addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(enrollmentSnapshot: DataSnapshot) {
                    if (enrollmentSnapshot.exists()) {
                        Toast.makeText(this@RegisterActivity, "Enrollment number already registered", Toast.LENGTH_SHORT).show()
                        return
                    }
                    // Enrollment OK, now check email and mobile
                    checkEmailAndMobile(name, email, mobile, password, role, personalEmail, enrollment)
                }
                override fun onCancelled(error: DatabaseError) {}
            })
        } else {
            // Faculty - check email and mobile only
            checkEmailAndMobile(name, email, mobile, password, role, personalEmail, enrollment)
        }
    }

    private fun checkEmailAndMobile(name: String, email: String, mobile: String, password: String, role: String, personalEmail: String, enrollment: String) {
        // Check if name already exists
        usersRef.orderByChild("name").equalTo(name).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(nameSnapshot: DataSnapshot) {
                if (nameSnapshot.exists()) {
                    Toast.makeText(this@RegisterActivity, "Name already registered", Toast.LENGTH_SHORT).show()
                    return
                }
                
                // Check if email already exists
                usersRef.orderByChild("email").equalTo(email).addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(emailSnapshot: DataSnapshot) {
                        if (emailSnapshot.exists()) {
                            Toast.makeText(this@RegisterActivity, "Already registered with this email.", Toast.LENGTH_SHORT).show()
                            return
                        }
                        
                        // Check if mobile already exists
                        usersRef.orderByChild("mobile").equalTo(mobile).addListenerForSingleValueEvent(object : ValueEventListener {
                            override fun onDataChange(mobileSnapshot: DataSnapshot) {
                                if (mobileSnapshot.exists()) {
                                    Toast.makeText(this@RegisterActivity, "Mobile number already registered", Toast.LENGTH_SHORT).show()
                                    return
                                }
                                
                                // All checks passed, register user
                                registerUser(name, email, mobile, password, role, personalEmail, enrollment)
                            }
                            override fun onCancelled(error: DatabaseError) {}
                        })
                    }
                    override fun onCancelled(error: DatabaseError) {}
                })
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun registerUser(name: String, email: String, mobile: String, password: String, role: String, personalEmail: String, enrollment: String) {
        // All duplicate checks already done, proceed to register
        val userId = System.currentTimeMillis().toInt()
        val user = UserEntity(
            id = userId,
            name = name, 
            email = email, 
            password = password, 
            role = role, 
            mobile = mobile,
            personalEmail = personalEmail
        )
        
        usersRef.child(userId.toString()).setValue(user).addOnSuccessListener {
            if (role == "Student") {
                val profile = StudentProfileEntity(userId = userId, enrollmentNo = enrollment)
                profilesRef.child(userId.toString()).setValue(profile)
            }
            
            sessionManager.saveUser(user)
            Toast.makeText(this@RegisterActivity, "Registration successful!", Toast.LENGTH_SHORT).show()
            val intent = if (role.equals("ADMIN", ignoreCase = true)) Intent(this@RegisterActivity, AdminHomeActivity::class.java) else Intent(this@RegisterActivity, HomeActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }.addOnFailureListener { e ->
            Toast.makeText(this@RegisterActivity, "Registration failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun redirectToLogin(view: View) {
        startActivity(Intent(this, LoginActivity::class.java))
    }
}
