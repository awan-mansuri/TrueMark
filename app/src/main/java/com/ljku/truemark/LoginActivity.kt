package com.ljku.truemark

import android.content.Intent
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
import com.ljku.truemark.database.NotificationEntity
import com.ljku.truemark.database.UserEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LoginActivity : AppCompatActivity() {

    private lateinit var sessionManager: SessionManager
    private val activityLogsRef = FirebaseDatabase.getInstance().getReference("activity_logs")
    private val usersRef = FirebaseDatabase.getInstance().getReference("users")
    private val notificationsRef = FirebaseDatabase.getInstance().getReference("notifications")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        sessionManager = SessionManager(applicationContext)
        
        if (sessionManager.isLoggedIn()) {
            val user = sessionManager.getLoggedInUser()
            val intent = if (user?.role?.equals("admin", ignoreCase = true) == true || user?.role?.equals("system_admin", ignoreCase = true) == true) {
                Intent(this, AdminHomeActivity::class.java)
            } else {
                Intent(this, HomeActivity::class.java)
            }
            startActivity(intent)
            finish()
        }

        val enrollmentEditText: TextInputEditText = findViewById(R.id.enrollmentLogin)
        val passwordEditText: TextInputEditText = findViewById(R.id.password)
        val loginButton: Button = findViewById(R.id.loginButton)
        val registerTextView: TextView = findViewById(R.id.registerTextView)
        val forgotPasswordTextView: TextView = findViewById(R.id.forgotPassword)

        loginButton.setOnClickListener {
            val input = enrollmentEditText.text.toString().trim()
            val password = passwordEditText.text.toString().trim()
            
            if (input.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please enter your ID and password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val emailToLogin = if (input.contains("@")) input else "$input@tm.com"
            queryFirebaseForUser(emailToLogin, password)
        }

        registerTextView.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        forgotPasswordTextView.setOnClickListener {
            startActivity(Intent(this, ForgotPasswordActivity::class.java))
        }
    }

    private fun queryFirebaseForUser(email: String, password: String) {
        usersRef.orderByChild("email").equalTo(email).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    var userFound = false
                    for (userSnapshot in snapshot.children) {
                        val firebaseUser = userSnapshot.getValue(UserEntity::class.java)
                        if (firebaseUser != null && firebaseUser.password == password) {
                            userFound = true
                            handleSuccessfulLogin(firebaseUser)
                            return
                        }
                    }
                    if (!userFound) {
                        Toast.makeText(this@LoginActivity, "Incorrect Password", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this@LoginActivity, "User not found.", Toast.LENGTH_LONG).show()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@LoginActivity, "Database error: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun handleSuccessfulLogin(user: UserEntity) {
        val currentTime = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
        val currentDateTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

        // Insert notification in Firebase
        val notificationId = notificationsRef.child(user.id.toString()).push().key ?: System.currentTimeMillis().toString()
        val notification = NotificationEntity(
            id = notificationId.hashCode(),
            userId = user.id,
            title = "New Login Detected",
            message = "You successfully logged in at $currentTime",
            type = "LOGIN",
            timestamp = System.currentTimeMillis()
        )
        notificationsRef.child(user.id.toString()).child(notificationId).setValue(notification)

        val logId = activityLogsRef.push().key ?: ""
        val logData = mapOf(
            "userId" to user.id, "userName" to user.name, "email" to user.email,
            "role" to user.role, "timestamp" to currentDateTime, "action" to "LOGIN"
        )
        activityLogsRef.child(logId).setValue(logData)
        usersRef.child(user.id.toString()).child("lastLogin").setValue(currentDateTime)

        // Save session and navigate
        sessionManager.saveUser(user)
        Toast.makeText(this@LoginActivity, "Login Successful!", Toast.LENGTH_SHORT).show()
        val intent = if (user.role.equals("admin", ignoreCase = true) || user.role.equals("system_admin", ignoreCase = true)) {
            Intent(this@LoginActivity, AdminHomeActivity::class.java)
        } else {
            Intent(this@LoginActivity, HomeActivity::class.java)
        }
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
