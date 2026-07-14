package com.ljku.truemark

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.ljku.truemark.database.UserEntity

class ForgotPasswordActivity : AppCompatActivity() {

    private val usersRef = FirebaseDatabase.getInstance().getReference("users")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_forgot_password)

        val enrollmentEditText: TextInputEditText = findViewById(R.id.enrollmentForgot)
        val newPasswordEditText: TextInputEditText = findViewById(R.id.newPasswordForgot)
        val confirmPasswordEditText: TextInputEditText = findViewById(R.id.confirmNewPasswordForgot)
        val resetPasswordButton: Button = findViewById(R.id.resetPasswordButton)
        val backToLogin: TextView = findViewById(R.id.backToLogin)

        resetPasswordButton.setOnClickListener {
            val input = enrollmentEditText.text.toString().trim()
            val newPassword = newPasswordEditText.text.toString().trim()
            val confirmPassword = confirmPasswordEditText.text.toString().trim()

            if (input.isEmpty() || newPassword.isEmpty() || confirmPassword.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val identifier = if (input.length == 12 && input.all { it.isDigit() }) {
                "$input@tm.com"
            } else {
                input
            }

            if (newPassword.length < 6) {
                Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (newPassword != confirmPassword) {
                Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            resetPassword(identifier, newPassword)
        }

        backToLogin.setOnClickListener {
            finish()
        }
    }

    private fun resetPassword(identifier: String, newPassword: String) {
        usersRef.orderByChild("email").equalTo(identifier).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    for (userSnapshot in snapshot.children) {
                        val user = userSnapshot.getValue(UserEntity::class.java)
                        if (user != null) {
                            userSnapshot.ref.child("password").setValue(newPassword).addOnSuccessListener {
                                Toast.makeText(this@ForgotPasswordActivity, "Password reset successfully!", Toast.LENGTH_SHORT).show()
                                finish()
                            }
                        }
                    }
                } else {
                    val msg = if (identifier.contains("@tm.com")) "Enrollment Number not found" else "User not found"
                    Toast.makeText(this@ForgotPasswordActivity, msg, Toast.LENGTH_SHORT).show()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@ForgotPasswordActivity, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }
}
