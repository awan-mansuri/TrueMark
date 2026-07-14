package com.ljku.truemark

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.*
import com.ljku.truemark.database.UserEntity

class AdminManageAdminsActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: UserAdapter
    private lateinit var noAdminsText: TextView
    private lateinit var sessionManager: SessionManager
    private val usersRef = FirebaseDatabase.getInstance().getReference("users")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_manage_admins)

        sessionManager = SessionManager(this)
        val currentUser = sessionManager.getLoggedInUser()
        
        if (currentUser?.email != "admin@truemark.com") {
            finish()
            return
        }

        recyclerView = findViewById(R.id.adminsRecyclerView)
        noAdminsText = findViewById(R.id.noAdminsTextView)

        // Back button click handler
        findViewById<ImageView>(R.id.backButton).setOnClickListener { finish() }

        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = UserAdapter(emptyList(),
            onEditClick = { admin ->
                val intent = Intent(this, EditProfileActivity::class.java)
                intent.putExtra("TARGET_USER_ID", admin.id)
                startActivity(intent)
            },
            onDeleteClick = { admin ->
                showDeleteConfirmation(admin)
            },
            showActions = true
        )
        recyclerView.adapter = adapter

        setupAdminsListener()
    }

    private fun setupAdminsListener() {
        usersRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val admins = mutableListOf<UserEntity>()
                for (child in snapshot.children) {
                    val user = child.getValue(UserEntity::class.java)
                    if (user != null && user.role == "ADMIN" && user.email != "admin@truemark.com") {
                        admins.add(user)
                    }
                }
                
                if (admins.isEmpty()) {
                    noAdminsText.visibility = View.VISIBLE
                    recyclerView.visibility = View.GONE
                } else {
                    noAdminsText.visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE
                    adapter.updateUsers(admins)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@AdminManageAdminsActivity, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun showDeleteConfirmation(admin: UserEntity) {
        AlertDialog.Builder(this)
            .setTitle("Delete Admin")
            .setMessage("Are you sure you want to delete admin ${admin.name}? This will also delete their access.")
            .setPositiveButton("Delete") { _, _ ->
                deleteAdmin(admin)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteAdmin(admin: UserEntity) {
        usersRef.child(admin.id.toString()).removeValue().addOnSuccessListener {
            Toast.makeText(this@AdminManageAdminsActivity, "Admin deleted", Toast.LENGTH_SHORT).show()
        }
    }
}
