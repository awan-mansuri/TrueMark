package com.ljku.truemark

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.*
import com.ljku.truemark.database.AttendanceSessionEntity
import com.ljku.truemark.database.GroupEntity
import com.ljku.truemark.database.UserEntity

class AttendanceHistoryActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var noHistoryText: TextView
    private lateinit var sessionManager: SessionManager
    private var currentUser: UserEntity? = null

    private val database = FirebaseDatabase.getInstance()
    private val sessionsRef = database.getReference("attendance_sessions")
    private val recordsRef = database.getReference("attendance_records")
    private val groupsRef = database.getReference("groups")
    
    private var recordsListener: ValueEventListener? = null
    private var sessionsListener: ValueEventListener? = null
    
    private var myGroupIds = mutableListOf<Int>()
    private var presentSessionIds = mutableSetOf<Long>()
    private var allSessions = mutableListOf<AttendanceSessionEntity>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_attendance_history)

        sessionManager = SessionManager(applicationContext)
        currentUser = sessionManager.getLoggedInUser()

        if (currentUser == null) {
            finish()
            return
        }

        recyclerView = findViewById(R.id.historyRecyclerView)
        noHistoryText = findViewById(R.id.noHistoryText)
        findViewById<ImageView>(R.id.backButton).setOnClickListener { finish() }

        recyclerView.layoutManager = LinearLayoutManager(this)

        loadHistory()
    }

    private fun loadHistory() {
        val user = currentUser!!
        if (user.role.equals("Faculty", ignoreCase = true)) {
            loadFacultyHistory(user.id)
        } else {
            loadStudentHistory(user.id)
        }
    }

    private fun loadFacultyHistory(facultyId: Int) {
        sessionsListener = sessionsRef.orderByChild("facultyId")
            .equalTo(facultyId.toDouble())
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val sessions = mutableListOf<AttendanceSessionEntity>()
                    for (child in snapshot.children) {
                        child.getValue(AttendanceSessionEntity::class.java)?.let { sessions.add(it) }
                    }
                    sessions.sortByDescending { it.startTime }
                    updateAdapter(sessions, null)
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun loadStudentHistory(studentId: Int) {
        groupsRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(groupSnapshot: DataSnapshot) {
                myGroupIds.clear()
                for (child in groupSnapshot.children) {
                    val group = child.child("details").getValue(GroupEntity::class.java)
                    if (group != null) {
                        val memberIds = group.memberIds.split(",").map { it.trim() }
                        if (memberIds.contains(studentId.toString())) {
                            myGroupIds.add(group.id)
                        }
                    }
                }
                // Start both listeners independently for real-time updates
                startRecordsListener(studentId)
                startSessionsListener()
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun startRecordsListener(studentId: Int) {
        // Remove old listener if exists
        recordsListener?.let { recordsRef.removeEventListener(it) }
        
        recordsListener = recordsRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                presentSessionIds.clear()
                Log.d("AttendanceHistory", "Records updated for studentId: $studentId")
                
                for (child in snapshot.children) {
                    val recStudentId = child.child("studentId").value?.toString()?.toLongOrNull()
                    val sid = child.child("sessionId").value
                    val isPresentVal = child.child("isPresent").getValue(Boolean::class.java) ?: false
                    val presentVal = child.child("present").getValue(Boolean::class.java) ?: false
                    val isPresent = isPresentVal || presentVal
                    
                    val sessionIdLong = when (sid) {
                        is String -> sid.toLongOrNull()
                        is Long -> sid
                        else -> null
                    }
                    
                    if (recStudentId?.toInt() == studentId && sessionIdLong != null && isPresent) {
                        presentSessionIds.add(sessionIdLong)
                    }
                }
                Log.d("AttendanceHistory", "Present sessions: ${presentSessionIds.size}")
                refreshStudentHistoryUI()
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun startSessionsListener() {
        // Remove old listener if exists
        sessionsListener?.let { sessionsRef.removeEventListener(it) }
        
        sessionsListener = sessionsRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                allSessions.clear()
                for (child in snapshot.children) {
                    child.getValue(AttendanceSessionEntity::class.java)?.let { session ->
                        if (myGroupIds.contains(session.groupId)) {
                            allSessions.add(session)
                        }
                    }
                }
                Log.d("AttendanceHistory", "Sessions updated: ${allSessions.size}")
                refreshStudentHistoryUI()
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun refreshStudentHistoryUI() {
        val historyItems = mutableListOf<AttendanceHistoryItem>()
        val currentTime = System.currentTimeMillis()

        for (session in allSessions) {
            val isPresent = presentSessionIds.contains(session.id)
            
            val isExpired = if (session.durationMinutes > 0) {
                // Keep a 60-second grace period mathematically so faculty's app has time to securely close the session first.
                // This guarantees the student never prematurely sees "ABSENT" due to clock drift if the faculty timer is exactly on zero.
                currentTime > (session.startTime + (session.durationMinutes * 60 * 1000L) + 60000L)
            } else {
                false
            }
            
            Log.d("AttendanceHistory", "Session: ${session.id}, Subject: ${session.subject}, Active: ${session.isActive}, Present: $isPresent")
            
            // FIX: Show in history if session has been closed by faculty (!isActive) OR mathematically expired with grace period OR student already marked participation
            if (!session.isActive || isExpired || isPresent) {
                historyItems.add(AttendanceHistoryItem(session, isPresent))
            }
        }

        historyItems.sortByDescending { it.session.startTime }
        updateAdapter(null, historyItems)
    }

    private fun updateAdapter(
        sessions: List<AttendanceSessionEntity>?,
        historyItems: List<AttendanceHistoryItem>?
    ) {
        if ((sessions.isNullOrEmpty()) && (historyItems.isNullOrEmpty())) {
            noHistoryText.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            noHistoryText.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
            recyclerView.adapter = AttendanceHistoryAdapter(sessions, historyItems)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        recordsListener?.let { recordsRef.removeEventListener(it) }
        sessionsListener?.let { sessionsRef.removeEventListener(it) }
    }
}
