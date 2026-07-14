package com.ljku.truemark

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.database.*
import com.ljku.truemark.database.UserEntity

class SplashActivity : AppCompatActivity() {

    private lateinit var sessionManager: SessionManager
    private var usersRef: DatabaseReference? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            setContentView(R.layout.activity_splash)
            sessionManager = SessionManager(applicationContext)
            
            // Initialize Firebase Reference safely
            usersRef = FirebaseDatabase.getInstance().getReference("users")
            
            bootstrapAdminAndProceed()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Initial Setup Error: ${e.message}", Toast.LENGTH_LONG).show()
            // Fallback to check session even if bootstrap fails
            checkSession()
        }
    }

    private fun bootstrapAdminAndProceed() {
        // Safe check for Firebase reference
        val ref = usersRef ?: run {
            checkSession()
            return
        }

        ref.orderByChild("role").equalTo("admin").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) {
                    val adminId = System.currentTimeMillis().toInt()
                    val defaultAdmin = UserEntity(
                        id = adminId,
                        name = "System Admin",
                        email = "admin@truemark.com",
                        password = "admin123",
                        role = "admin",
                        mobile = "0000000000",
                        bio = "Primary system administrator for TrueMark."
                    )
                    ref.child(adminId.toString()).setValue(defaultAdmin)
                }

                Handler(Looper.getMainLooper()).postDelayed({
                    checkSession()
                }, 2000)
            }

            override fun onCancelled(error: DatabaseError) {
                // If there's a permission error or network issue, still proceed to session check
                checkSession()
            }
        })
    }

    private fun checkSession() {
        try {
            if (sessionManager.isLoggedIn()) {
                val user = sessionManager.getLoggedInUser()
                val intent = if (user?.role?.equals("admin", ignoreCase = true) == true || 
                                user?.role?.equals("system_admin", ignoreCase = true) == true) {
                    Intent(this, AdminHomeActivity::class.java)
                } else {
                    Intent(this, HomeActivity::class.java)
                }
                startActivity(intent)
                finish()
            } else {
                val intent = Intent(this, LoginActivity::class.java)
                startActivity(intent)
                finish()
            }
        } catch (e: Exception) {
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            finish()
        }
    }
}
