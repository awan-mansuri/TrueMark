package com.ljku.truemark

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.ljku.truemark.database.AttendanceSessionEntity
import com.ljku.truemark.database.GroupEntity
import com.ljku.truemark.database.UserEntity

class FacultyStartSessionActivity : AppCompatActivity() {

    private lateinit var subjectSpinner: Spinner
    private lateinit var durationRadioGroup: RadioGroup
    private lateinit var btnStartSession: Button
    private lateinit var sessionManager: SessionManager
    private var currentUser: UserEntity? = null
    
    private var groupId: Int = -1
    private var subjectsString: String? = null
    private var currentGroup: GroupEntity? = null
    private val sessionsRef = FirebaseDatabase.getInstance().getReference("attendance_sessions")
    private val groupsRef = FirebaseDatabase.getInstance().getReference("groups")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_faculty_start_session)

        sessionManager = SessionManager(applicationContext)
        currentUser = sessionManager.getLoggedInUser()
        
        groupId = intent.getIntExtra("GROUP_ID", -1)
        subjectsString = intent.getStringExtra("SUBJECTS")

        if (groupId == -1 || currentUser == null) {
            finish()
            return
        }

        subjectSpinner = findViewById(R.id.subjectSpinner)
        durationRadioGroup = findViewById(R.id.durationRadioGroup)
        btnStartSession = findViewById(R.id.btnStartSession)
        findViewById<ImageView>(R.id.backButton).setOnClickListener { finish() }

        setupSubjectSpinner()
        loadGroupData()
        
        btnStartSession.setOnClickListener {
            startAttendanceSession()
        }
    }

    private fun setupSubjectSpinner() {
        val subjects = subjectsString?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
        if (subjects.isEmpty()) {
            Toast.makeText(this, "No subjects found.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, subjects)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        subjectSpinner.adapter = adapter
    }

    private fun startAttendanceSession() {
        val selectedSubject = subjectSpinner.selectedItem.toString()

        val checkedId = durationRadioGroup.checkedRadioButtonId
        val duration = when (checkedId) {
            R.id.radio1 -> 1
            R.id.radio2 -> 2
            R.id.radio5 -> 5
            R.id.radio10 -> 10
            R.id.radioUnlimited -> 0 
            else -> -1
        }
        
        Log.d("FacultyStartSession", "Selected radio ID: $checkedId, Duration: $duration")

        if (duration < 0) {
            Toast.makeText(this, "Please select duration", Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(this, "Starting Attendance Session...", Toast.LENGTH_SHORT).show()

        val memberIds = currentGroup?.memberIds?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
        if (memberIds.isNotEmpty()) {
            NotificationUtils.sendAttendanceNotification(
                subject = selectedSubject,
                facultyName = currentUser!!.name,
                groupId = groupId.toString(),
                memberIds = memberIds
            )
        }

        val intent = Intent(this, FacultySessionActivity::class.java)
        intent.putExtra("GROUP_ID", groupId)
        intent.putExtra("SUBJECT", selectedSubject)
        intent.putExtra("DURATION", duration)
        startActivity(intent)
        finish() // Close the start screen
    }

    private fun loadGroupData() {
        groupsRef.child(groupId.toString()).child("details").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                currentGroup = snapshot.getValue(GroupEntity::class.java)
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }
}
