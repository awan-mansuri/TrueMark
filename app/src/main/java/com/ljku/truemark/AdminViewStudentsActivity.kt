package com.ljku.truemark

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.*
import com.ljku.truemark.database.UserEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class AdminViewStudentsActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: StudentAdapter
    private lateinit var noStudentsText: TextView
    private lateinit var sessionManager: SessionManager
    
    private val firebaseUsers = FirebaseDatabase.getInstance().getReference("users")
    private val firebaseProfiles = FirebaseDatabase.getInstance().getReference("student_profiles")
    private var studentsListener: ValueEventListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sessionManager = SessionManager(applicationContext)

        val currentUser = sessionManager.getLoggedInUser()
        if (currentUser?.role?.equals("ADMIN", ignoreCase = true) != true) {
            finish()
            return
        }

        setContentView(R.layout.activity_admin_view_students)

        recyclerView = findViewById(R.id.studentsRecyclerView)
        noStudentsText = findViewById(R.id.noStudentsText)

        // Set up back button
        findViewById<android.widget.ImageView>(R.id.backButton).setOnClickListener {
            finish()
        }

        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = StudentAdapter(emptyList()) { student ->
            val intent = Intent(this, AdminAddStudentDetailsActivity::class.java)
            intent.putExtra("STUDENT_ID", student.id)
            intent.putExtra("STUDENT_NAME", student.name)
            startActivity(intent)
        }
        recyclerView.adapter = adapter

        startRealtimeStudentListener()
    }

    private fun startRealtimeStudentListener() {
        val currentAdmin = sessionManager.getLoggedInUser()
        val isDefaultAdmin = currentAdmin?.email == "admin@truemark.com"

        studentsListener = firebaseUsers.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                lifecycleScope.launch(Dispatchers.IO) {
                    val studentsList = mutableListOf<UserWithProfileStatus>()
                    
                    for (child in snapshot.children) {
                        val user = child.getValue(UserEntity::class.java)
                        if (user != null && user.role.equals("Student", ignoreCase = true)) {
                            if (isDefaultAdmin || user.createdBy == currentAdmin?.id) {
                                
                                try {
                                    val profileSnapshot = firebaseProfiles.child(user.id.toString()).get().await()
                                    val rollNo = profileSnapshot.child("rollNumber").getValue(String::class.java)
                                    
                                    studentsList.add(UserWithProfileStatus(
                                        id = user.id,
                                        name = user.name,
                                        email = user.email,
                                        rollNumber = rollNo ?: "Not Assigned",
                                        profileImage = user.profileImage
                                    ))
                                } catch (e: Exception) {
                                    studentsList.add(UserWithProfileStatus(
                                        id = user.id,
                                        name = user.name,
                                        email = user.email,
                                        rollNumber = "Not Assigned",
                                        profileImage = user.profileImage
                                    ))
                                }
                            }
                        }
                    }

                    withContext(Dispatchers.Main) {
                        if (studentsList.isEmpty()) {
                            noStudentsText.visibility = View.VISIBLE
                            recyclerView.visibility = View.GONE
                        } else {
                            noStudentsText.visibility = View.GONE
                            recyclerView.visibility = View.VISIBLE
                            adapter.updateData(studentsList)
                        }
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@AdminViewStudentsActivity, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        studentsListener?.let { firebaseUsers.removeEventListener(it) }
    }
}
