package com.ljku.truemark

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.firebase.database.*
import com.ljku.truemark.database.GroupEntity
import com.ljku.truemark.database.UserEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

class ManualAttendanceActivity : AppCompatActivity() {

    private lateinit var groupSpinner: Spinner
    private lateinit var subjectSpinner: Spinner
    private lateinit var loadStudentsButton: MaterialButton
    private lateinit var allAbsentButton: MaterialButton
    private lateinit var allPresentButton: MaterialButton
    private lateinit var doneButton: MaterialButton
    private lateinit var studentsRecyclerView: RecyclerView
    private lateinit var bulkActionsLayout: View
    private lateinit var progressBar: View
    private lateinit var noStudentsText: View

    private lateinit var sessionManager: SessionManager
    private var currentUser: UserEntity? = null
    private val database = FirebaseDatabase.getInstance()
    private val groupsRef = database.getReference("groups")
    private val usersRef = database.getReference("users")
    private val sessionsRef = database.getReference("attendance_sessions")
    private val recordsRef = database.getReference("attendance_records")

    private var groupsList = mutableListOf<GroupEntity>()
    private var subjectsList = mutableListOf<String>()
    private var studentsList = mutableListOf<ManualAttendanceStudent>()
    private lateinit var adapter: ManualAttendanceAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manual_attendance)

        sessionManager = SessionManager(applicationContext)
        currentUser = sessionManager.getLoggedInUser()

        if (currentUser == null) {
            finish()
            return
        }

        initViews()
        setupSpinners()
        loadGroups()

        findViewById<ImageView>(R.id.backButton).setOnClickListener { finish() }

        loadStudentsButton.setOnClickListener { loadStudents() }
        allAbsentButton.setOnClickListener { adapter.markAllAbsent() }
        allPresentButton.setOnClickListener { adapter.markAllPresent() }
        doneButton.setOnClickListener { saveAttendance() }
    }

    private fun initViews() {
        groupSpinner = findViewById(R.id.groupSpinner)
        subjectSpinner = findViewById(R.id.subjectSpinner)
        loadStudentsButton = findViewById(R.id.loadStudentsButton)
        allAbsentButton = findViewById(R.id.allAbsentButton)
        allPresentButton = findViewById(R.id.allPresentButton)
        doneButton = findViewById(R.id.doneButton)
        studentsRecyclerView = findViewById(R.id.studentsRecyclerView)
        bulkActionsLayout = findViewById(R.id.bulkActionsLayout)
        progressBar = findViewById(R.id.progressBar)
        noStudentsText = findViewById(R.id.noStudentsText)

        studentsRecyclerView.layoutManager = LinearLayoutManager(this)
        adapter = ManualAttendanceAdapter(emptyList())
        studentsRecyclerView.adapter = adapter
    }

    private fun setupSpinners() {
        groupSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position >= 0 && position < groupsList.size) {
                    loadSubjectsForGroup(groupsList[position])
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun loadGroups() {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                progressBar.visibility = View.VISIBLE
                val snapshot = groupsRef.get().await()
                groupsList.clear()

                for (groupSnapshot in snapshot.children) {
                    val group = groupSnapshot.child("details").getValue(GroupEntity::class.java)
                    if (group != null) {
                        val memberIds = group.memberIds.split(",").map { it.trim() }
                        if (memberIds.contains(currentUser!!.id.toString())) {
                            groupsList.add(group)
                        }
                    }
                }

                val groupNames = groupsList.map { it.groupName }
                val groupAdapter = ArrayAdapter(this@ManualAttendanceActivity, android.R.layout.simple_spinner_item, groupNames)
                groupAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                groupSpinner.adapter = groupAdapter

                if (groupsList.isNotEmpty()) {
                    loadSubjectsForGroup(groupsList[0])
                }
            } catch (e: Exception) {
                Toast.makeText(this@ManualAttendanceActivity, "Error loading groups", Toast.LENGTH_SHORT).show()
            } finally {
                progressBar.visibility = View.GONE
            }
        }
    }

    private fun loadSubjectsForGroup(group: GroupEntity) {
        subjectsList.clear()
        group.subjects?.let {
            subjectsList.addAll(it.split(",").map { s -> s.trim() }.filter { s -> s.isNotEmpty() })
        }

        val subjectAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, subjectsList)
        subjectAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        subjectSpinner.adapter = subjectAdapter
    }

    private fun loadStudents() {
        val groupPosition = groupSpinner.selectedItemPosition
        if (groupPosition < 0 || groupPosition >= groupsList.size) {
            Toast.makeText(this, "Please select a group", Toast.LENGTH_SHORT).show()
            return
        }

        val selectedGroup = groupsList[groupPosition]
        val memberIds = selectedGroup.memberIds.split(",").map { it.trim() }

        CoroutineScope(Dispatchers.Main).launch {
            try {
                progressBar.visibility = View.VISIBLE
                studentsList.clear()

                val snapshot = usersRef.get().await()
                val profilesSnapshot = FirebaseDatabase.getInstance().getReference("student_profiles").get().await()
                
                for (child in snapshot.children) {
                    val user = child.getValue(UserEntity::class.java)
                    if (user != null && memberIds.contains(user.id.toString())) {
                        if (user.role.equals("Student", ignoreCase = true)) {
                            // Get roll number from student_profiles
                            val profile = profilesSnapshot.child(user.id.toString()).getValue(com.ljku.truemark.database.StudentProfileEntity::class.java)
                            val rollNumber = profile?.rollNumber?.takeIf { it.isNotBlank() } ?: user.id.toString()
                            studentsList.add(ManualAttendanceStudent(user, true, rollNumber))
                        }
                    }
                }

                // Sort students by roll number (numeric sorting)
                studentsList.sortBy { it.rollNumber.toIntOrNull() ?: Int.MAX_VALUE }

                adapter.updateStudents(studentsList)

                if (studentsList.isEmpty()) {
                    noStudentsText.visibility = View.VISIBLE
                    studentsRecyclerView.visibility = View.GONE
                    bulkActionsLayout.visibility = View.GONE
                    doneButton.visibility = View.GONE
                } else {
                    noStudentsText.visibility = View.GONE
                    studentsRecyclerView.visibility = View.VISIBLE
                    bulkActionsLayout.visibility = View.VISIBLE
                    doneButton.visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                Toast.makeText(this@ManualAttendanceActivity, "Error loading students", Toast.LENGTH_SHORT).show()
            } finally {
                progressBar.visibility = View.GONE
            }
        }
    }

    private fun saveAttendance() {
        val groupPosition = groupSpinner.selectedItemPosition
        val subjectPosition = subjectSpinner.selectedItemPosition

        if (groupPosition < 0 || groupPosition >= groupsList.size) {
            Toast.makeText(this, "Please select a group", Toast.LENGTH_SHORT).show()
            return
        }

        if (subjectPosition < 0 || subjectPosition >= subjectsList.size) {
            Toast.makeText(this, "Please select a subject", Toast.LENGTH_SHORT).show()
            return
        }

        val selectedGroup = groupsList[groupPosition]
        val selectedSubject = subjectsList[subjectPosition]
        val students = adapter.getStudents()

        val progressDialog = AlertDialog.Builder(this)
            .setView(ProgressBar(this))
            .setMessage("Saving attendance...")
            .setCancelable(false)
            .create()
        progressDialog.show()

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val sessionId = System.currentTimeMillis()
                val currentTime = System.currentTimeMillis()

                // Create attendance session
                val session = hashMapOf<String, Any>(
                    "id" to sessionId,
                    "groupId" to selectedGroup.id,
                    "facultyId" to currentUser!!.id,
                    "subject" to selectedSubject,
                    "startTime" to currentTime,
                    "durationMinutes" to 0,
                    "isActive" to false
                )
                sessionsRef.child(sessionId.toString()).setValue(session).await()

                // Create attendance records for each student
                for (student in students) {
                    val recordId = recordsRef.push().key ?: System.currentTimeMillis().toString()
                    val record = hashMapOf<String, Any>(
                        "id" to recordId.hashCode(),
                        "sessionId" to sessionId,
                        "studentId" to student.student.id,
                        "studentName" to student.student.name,
                        "studentRollNo" to student.student.id.toString(),
                        "timestamp" to currentTime,
                        "present" to student.isPresent,
                        "isPresent" to student.isPresent,
                        "subject" to selectedSubject,
                        "markedBy" to "FACULTY",
                        "status" to if (student.isPresent) "PRESENT" else "ABSENT"
                    )
                    recordsRef.child(recordId).setValue(record).await()
                }

                progressDialog.dismiss()
                Toast.makeText(this@ManualAttendanceActivity, "Attendance saved successfully!", Toast.LENGTH_SHORT).show()
                finish()
            } catch (e: Exception) {
                progressDialog.dismiss()
                Toast.makeText(this@ManualAttendanceActivity, "Error saving attendance: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
