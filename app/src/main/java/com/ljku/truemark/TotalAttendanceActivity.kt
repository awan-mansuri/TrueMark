package com.ljku.truemark

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.*
import com.ljku.truemark.database.AttendanceSessionEntity
import com.ljku.truemark.database.GroupEntity
import com.ljku.truemark.database.UserEntity

class TotalAttendanceActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var noDataText: TextView
    private lateinit var groupSpinner: Spinner
    private lateinit var groupSelectionCard: CardView
    private lateinit var sessionManager: SessionManager
    private var currentUser: UserEntity? = null
    private val sessionsRef = FirebaseDatabase.getInstance().getReference("attendance_sessions")
    private val recordsRef = FirebaseDatabase.getInstance().getReference("attendance_records")
    
    private var myGroups = mutableListOf<GroupEntity>()
    private var selectedGroupId: Int = -1
    private val PREFS_NAME = "AttendancePrefs"
    private val LAST_GROUP_KEY = "last_selected_group"

    private fun parseLong(value: Any?): Long? {
        return when (value) {
            is Long -> value
            is Int -> value.toLong()
            is Double -> value.toLong()
            is String -> value.toLongOrNull()
            is Number -> value.toLong()
            else -> null
        }
    }

    private fun parseBoolean(value: Any?): Boolean {
        return when (value) {
            is Boolean -> value
            is String -> value.equals("true", ignoreCase = true)
            is Number -> value.toInt() != 0
            else -> false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_total_attendance)

        sessionManager = SessionManager(applicationContext)
        currentUser = sessionManager.getLoggedInUser()

        if (currentUser == null) {
            finish()
            return
        }

        recyclerView = findViewById(R.id.summaryRecyclerView)
        noDataText = findViewById(R.id.noDataText)
        groupSpinner = findViewById(R.id.groupSpinner)
        
        groupSelectionCard = findViewById(R.id.groupSelectionCard)
        
        val titleTextView: TextView = findViewById(R.id.pageTitleText)
        val backButton: ImageView = findViewById(R.id.backButton)
        backButton.setOnClickListener { finish() }

        recyclerView.layoutManager = LinearLayoutManager(this)
        
        // Load saved group preference
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        selectedGroupId = prefs.getInt(LAST_GROUP_KEY, -1)
        
        if (currentUser!!.role.equals("Faculty", ignoreCase = true)) {
            titleTextView.text = "Class Summary"
            loadFacultyGroups()
        } else {
            titleTextView.text = "My Attendance"
            loadStudentGroups()
        }
    }

    private fun loadStudentGroups() {
        val groupsRef = FirebaseDatabase.getInstance().getReference("groups")
        groupsRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(groupSnapshot: DataSnapshot) {
                myGroups.clear()
                for (child in groupSnapshot.children) {
                    val group = child.child("details").getValue(GroupEntity::class.java)
                    if (group != null) {
                        val memberIds = group.memberIds.split(",").map { it.trim() }
                        if (memberIds.contains(currentUser!!.id.toString())) {
                            myGroups.add(group)
                        }
                    }
                }
                
                if (myGroups.isEmpty()) {
                    groupSelectionCard.visibility = View.GONE
                    groupSpinner.visibility = View.GONE
                    noDataText.visibility = View.VISIBLE
                    recyclerView.visibility = View.GONE
                    return
                }
                
                // Sort groups by name
                myGroups.sortBy { it.groupName }
                
                // Create adapter for spinner
                val groupNames = myGroups.map { it.groupName }.toMutableList()
                val adapter = ArrayAdapter(this@TotalAttendanceActivity, android.R.layout.simple_spinner_item, groupNames)
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                groupSpinner.adapter = adapter
                
                // Set listener
                groupSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                        val selectedGroup = myGroups[position]
                        selectedGroupId = selectedGroup.id
                        // Save preference
                        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                            .edit()
                            .putInt(LAST_GROUP_KEY, selectedGroupId)
                            .apply()
                        loadStudentSummaryForGroup(selectedGroup)
                    }
                    override fun onNothingSelected(parent: AdapterView<*>?) {}
                }
                
                // Select saved group or first group
                val savedIndex = myGroups.indexOfFirst { it.id == selectedGroupId }
                if (savedIndex >= 0) {
                    groupSpinner.setSelection(savedIndex)
                } else {
                    groupSpinner.setSelection(0)
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }
    
    private fun loadStudentSummaryForGroup(group: GroupEntity) {
        sessionsRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(sessionSnapshot: DataSnapshot) {
                val allSessions = mutableListOf<AttendanceSessionEntity>()
                for (child in sessionSnapshot.children) {
                    val session = child.getValue(AttendanceSessionEntity::class.java)
                    if (session != null && session.groupId == group.id) {
                        allSessions.add(session)
                    }
                }

                recordsRef.addValueEventListener(object : ValueEventListener {
                    override fun onDataChange(recordSnapshot: DataSnapshot) {
                        val myPresentSessionIds = mutableSetOf<Long>()

                        for (child in recordSnapshot.children) {
                            val recordStudentId = parseLong(child.child("studentId").value)
                            if (recordStudentId?.toInt() != currentUser!!.id) continue

                            val sid = parseLong(child.child("sessionId").value) ?: continue
                            val isPresent = parseBoolean(child.child("isPresent").value) ||
                                    parseBoolean(child.child("present").value)
                            if (isPresent) {
                                myPresentSessionIds.add(sid)
                            }
                        }

                        val currentTime = System.currentTimeMillis()
                        val validSessions = allSessions.filter { session ->
                            val isPresent = myPresentSessionIds.contains(session.id)
                            val isExpired = if (session.durationMinutes > 0) {
                                currentTime > (session.startTime + (session.durationMinutes * 60 * 1000L) + 60000L)
                            } else {
                                false
                            }
                            !session.isActive || isExpired || isPresent
                        }

                        val subjects = validSessions.map { it.subject }.distinct()
                        val summaryList = mutableListOf<AttendanceSummary>()

                        for (subject in subjects) {
                            val subjectSessions = validSessions.filter { it.subject == subject }
                            val totalLectures = subjectSessions.size
                            val presentCount = subjectSessions.count { myPresentSessionIds.contains(it.id) }

                            if (totalLectures > 0) {
                                summaryList.add(AttendanceSummary(
                                    subject = subject,
                                    totalLectures = totalLectures,
                                    present = presentCount,
                                    absent = totalLectures - presentCount,
                                    percentage = (presentCount * 100.0 / totalLectures)
                                ))
                            }
                        }
                        updateUI(summaryList, true)
                    }
                    override fun onCancelled(error: DatabaseError) {}
                })
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun loadFacultyGroups() {
        val groupsRef = FirebaseDatabase.getInstance().getReference("groups")
        groupsRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(groupSnapshot: DataSnapshot) {
                myGroups.clear()
                for (child in groupSnapshot.children) {
                    val group = child.child("details").getValue(GroupEntity::class.java)
                    if (group != null) {
                        val memberIds = group.memberIds.split(",").map { it.trim() }
                        // Show groups where faculty is a member or faculty created sessions
                        if (memberIds.contains(currentUser!!.id.toString()) || group.createdByUserId == currentUser!!.id) {
                            myGroups.add(group)
                        }
                    }
                }
                
                if (myGroups.isEmpty()) {
                    groupSelectionCard.visibility = View.GONE
                    groupSpinner.visibility = View.GONE
                    noDataText.visibility = View.VISIBLE
                    recyclerView.visibility = View.GONE
                    return
                }
                
                // Check if faculty has any attendance sessions at all
                sessionsRef.orderByChild("facultyId").equalTo(currentUser!!.id.toDouble()).addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        if (!snapshot.exists() || snapshot.childrenCount == 0L) {
                            // No sessions found - hide everything
                            groupSelectionCard.visibility = View.GONE
                            groupSpinner.visibility = View.GONE
                            noDataText.visibility = View.VISIBLE
                            recyclerView.visibility = View.GONE
                            return
                        }
                        
                        // Has sessions - show group selection
                        groupSelectionCard.visibility = View.VISIBLE
                        groupSpinner.visibility = View.VISIBLE
                        
                        // Sort groups by name
                        myGroups.sortBy { it.groupName }
                        
                        // Create adapter for spinner
                        val groupNames = myGroups.map { it.groupName }.toMutableList()
                        val adapter = ArrayAdapter(this@TotalAttendanceActivity, android.R.layout.simple_spinner_item, groupNames)
                        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                        groupSpinner.adapter = adapter
                        
                        // Set listener
                        groupSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                                val selectedGroup = myGroups[position]
                                selectedGroupId = selectedGroup.id
                                // Save preference
                                getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                                    .edit()
                                    .putInt(LAST_GROUP_KEY, selectedGroupId)
                                    .apply()
                                loadFacultySummaryForGroup(selectedGroup)
                            }
                            override fun onNothingSelected(parent: AdapterView<*>?) {}
                        }
                        
                        // Select saved group or first group
                        val savedIndex = myGroups.indexOfFirst { it.id == selectedGroupId }
                        if (savedIndex >= 0) {
                            groupSpinner.setSelection(savedIndex)
                        } else {
                            groupSpinner.setSelection(0)
                        }
                    }
                    override fun onCancelled(error: DatabaseError) {}
                })
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }
    
    private fun loadFacultySummaryForGroup(group: GroupEntity) {
        sessionsRef.orderByChild("facultyId").equalTo(currentUser!!.id.toDouble()).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val sessions = mutableListOf<AttendanceSessionEntity>()
                for (child in snapshot.children) {
                    val session = child.getValue(AttendanceSessionEntity::class.java)
                    // Only include sessions from selected group
                    if (session != null && session.groupId == group.id) {
                        sessions.add(session)
                    }
                }

                val subjects = sessions.map { it.subject }.distinct()
                val summaryList = mutableListOf<AttendanceSummary>()

                for (subject in subjects) {
                    val subjectSessions = sessions.filter { it.subject == subject }
                    summaryList.add(AttendanceSummary(
                        subject = subject,
                        totalLectures = subjectSessions.size,
                        present = 0,
                        absent = 0,
                        percentage = 0.0
                    ))
                }
                
                // If no data for this group, hide recycler and show no data
                if (summaryList.isEmpty()) {
                    noDataText.visibility = View.VISIBLE
                    recyclerView.visibility = View.GONE
                } else {
                    noDataText.visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE
                    updateUI(summaryList, false)
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun updateUI(list: List<AttendanceSummary>, isStudent: Boolean) {
        if (list.isEmpty()) {
            noDataText.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
            // Hide group selection for students when no records
            if (isStudent) {
                groupSelectionCard.visibility = View.GONE
            }
        } else {
            noDataText.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
            // Show group selection for students when records exist
            if (isStudent) {
                groupSelectionCard.visibility = View.VISIBLE
            }
            val adapter = AttendanceSummaryAdapter(list, isStudent) { summary ->
                if (isStudent) {
                    val intent = Intent(this, SubjectAttendanceDetailActivity::class.java)
                    intent.putExtra("SUBJECT_NAME", summary.subject)
                    intent.putExtra("STUDENT_ID", currentUser!!.id)
                    intent.putExtra("GROUP_ID", selectedGroupId)
                    startActivity(intent)
                }
            }
            recyclerView.adapter = adapter
        }
    }
}
