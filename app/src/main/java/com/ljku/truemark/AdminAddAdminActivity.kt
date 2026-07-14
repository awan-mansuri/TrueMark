package com.ljku.truemark

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.database.*
import com.ljku.truemark.database.UserEntity

class AdminAddAdminActivity : AppCompatActivity() {

    private val usersRef = FirebaseDatabase.getInstance().getReference("users")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_add_admin)

        val nameInput: TextInputEditText = findViewById(R.id.nameInput)
        val emailInput: TextInputEditText = findViewById(R.id.emailInput)
        val bioInput: TextInputEditText = findViewById(R.id.bioInput)
        val mobileInput: TextInputEditText = findViewById(R.id.mobileInput)
        val passwordInput: TextInputEditText = findViewById(R.id.passwordInput)
        val confirmPasswordInput: TextInputEditText = findViewById(R.id.confirmPasswordInput)
        val createAdminButton: Button = findViewById(R.id.createAdminButton)
        val backToDashboard: TextView = findViewById(R.id.backToDashboard)

        backToDashboard.setOnClickListener { finish() }

        createAdminButton.setOnClickListener {
            val name = nameInput.text.toString().trim()
            val email = emailInput.text.toString().trim()
            val bio = bioInput.text.toString().trim()
            val mobile = mobileInput.text.toString().trim()
            val password = passwordInput.text.toString()
            val confirmPassword = confirmPasswordInput.text.toString()

            if (name.isEmpty() || email.isEmpty() || mobile.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (mobile.length != 10 || !mobile.all { it.isDigit() }) {
                Toast.makeText(this, "Please enter a valid 10-digit mobile number", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (password.length < 6) {
                Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (password != confirmPassword) {
                Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            checkAndCreateAdmin(name, email, bio, mobile, password)
        }
    }

    private fun checkAndCreateAdmin(name: String, email: String, bio: String, mobile: String, password: String) {
        usersRef.orderByChild("email").equalTo(email).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    Toast.makeText(this@AdminAddAdminActivity, "Email already exists!", Toast.LENGTH_SHORT).show()
                } else {
                    val adminId = System.currentTimeMillis().toInt()
                    val newAdmin = UserEntity(
                        id = adminId,
                        name = name,
                        email = email,
                        password = password,
                        role = "ADMIN",
                        mobile = mobile,
                        bio = bio
                    )
                    usersRef.child(adminId.toString()).setValue(newAdmin).addOnSuccessListener {
                        Toast.makeText(this@AdminAddAdminActivity, "Admin created and saved successfully!", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@AdminAddAdminActivity, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }
}
