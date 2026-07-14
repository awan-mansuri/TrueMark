package com.ljku.truemark

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.*
import com.ljku.truemark.database.GroupEntity
import com.ljku.truemark.database.UserEntity

class FacultyGroupStudentsActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var noStudentsText: TextView
    private lateinit var groupNameTitle: TextView
    private lateinit var btnMarkAttendance: Button
    
    private var groupId: Int = -1
    private var groupName: String? = null
    private val groupsRef = FirebaseDatabase.getInstance().getReference("groups")
    private val usersRef = FirebaseDatabase.getInstance().getReference("users")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_faculty_group_students)

        groupId = intent.getIntExtra("GROUP_ID", -1)
        groupName = intent.getStringExtra("GROUP_NAME")

        recyclerView = findViewById(R.id.studentsRecyclerView)
        noStudentsText = findViewById(R.id.noStudentsText)
        groupNameTitle = findViewById(R.id.groupNameTitle)
        btnMarkAttendance = findViewById(R.id.btnMarkAttendance)
        val backButton: ImageView = findViewById(R.id.backButton)

        groupNameTitle.text = groupName
        backButton.setOnClickListener { finish() }

        recyclerView.layoutManager = LinearLayoutManager(this)
        
        btnMarkAttendance.setOnClickListener {
            groupsRef.child(groupId.toString()).child("details").addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val group = snapshot.getValue(GroupEntity::class.java)
                    if (group?.subjects.isNullOrEmpty()) {
                        Toast.makeText(this@FacultyGroupStudentsActivity, "No subjects found for this group. Edit group to add subjects.", Toast.LENGTH_LONG).show()
                    } else {
                        val intent = Intent(this@FacultyGroupStudentsActivity, FacultyStartSessionActivity::class.java)
                        intent.putExtra("GROUP_ID", groupId)
                        intent.putExtra("SUBJECTS", group?.subjects)
                        startActivity(intent)
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            })
        }

        loadStudents()
    }

    private fun loadStudents() {
        groupsRef.child(groupId.toString()).child("details").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val group = snapshot.getValue(GroupEntity::class.java)
                if (group != null) {
                    val memberIds = group.memberIds.split(",").mapNotNull { it.trim() }
                    usersRef.addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(userSnapshot: DataSnapshot) {
                            val students = mutableListOf<UserEntity>()
                            for (id in memberIds) {
                                val user = userSnapshot.child(id).getValue(UserEntity::class.java)
                                if (user != null && user.role.equals("Student", ignoreCase = true)) {
                                    students.add(user)
                                }
                            }

                            if (students.isEmpty()) {
                                noStudentsText.visibility = View.VISIBLE
                                recyclerView.visibility = View.GONE
                            } else {
                                noStudentsText.visibility = View.GONE
                                recyclerView.visibility = View.VISIBLE
                                recyclerView.adapter = UserAdapter(students, {}, {}, {}, {}, false)
                            }
                        }

                        override fun onCancelled(error: DatabaseError) {}
                    })
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }
}
