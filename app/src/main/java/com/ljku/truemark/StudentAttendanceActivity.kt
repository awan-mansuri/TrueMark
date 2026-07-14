package com.ljku.truemark

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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

class StudentAttendanceActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var noSessionsText: TextView
    private lateinit var sessionManager: SessionManager
    private var currentUser: UserEntity? = null
    
    private lateinit var sessionsRef: DatabaseReference
    private var sessionsListener: ValueEventListener? = null
    private var myGroupIds = mutableListOf<Int>()
    private val refreshHandler = Handler(Looper.getMainLooper())
    
    // Auto-refresh the list every 2 seconds for faster updates
    private val refreshRunnable = object : Runnable {
        override fun run() {
            refreshActiveSessionsList()
            refreshHandler.postDelayed(this, 2000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sessionManager = SessionManager(applicationContext)
        currentUser = sessionManager.getLoggedInUser()

        if (currentUser == null) {
            finish()
            return
        }

        setContentView(R.layout.activity_student_attendance)

        recyclerView = findViewById(R.id.activeSessionsRecyclerView)
        noSessionsText = findViewById(R.id.noSessionsText)
        findViewById<ImageView>(R.id.backButton).setOnClickListener { finish() }

        recyclerView.layoutManager = LinearLayoutManager(this)
        
        // Create adapter once and reuse it
        val adapter = StudentAttendanceAdapter(emptyList(), currentUser!!.id) { session ->
            val intent = Intent(this@StudentAttendanceActivity, StudentScannerActivity::class.java)
            intent.putExtra("SESSION_ID", session.id.toString())
            startActivity(intent)
        }
        recyclerView.adapter = adapter
        
        sessionsRef = FirebaseDatabase.getInstance().getReference("attendance_sessions")
        
        loadMyGroupsAndStartListener()
    }

    private fun loadMyGroupsAndStartListener() {
        val groupsRef = FirebaseDatabase.getInstance().getReference("groups")
        groupsRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                myGroupIds.clear()
                for (groupSnapshot in snapshot.children) {
                    val details = groupSnapshot.child("details").getValue(GroupEntity::class.java)
                    if (details != null) {
                        val memberIds = details.memberIds.split(",").map { it.trim() }
                        if (memberIds.contains(currentUser!!.id.toString())) {
                            myGroupIds.add(details.id)
                        }
                    }
                }
                startRealtimeAttendanceListener()
                refreshHandler.post(refreshRunnable)
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun startRealtimeAttendanceListener() {
        sessionsListener = sessionsRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                refreshActiveSessionsList(snapshot)
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun refreshActiveSessionsList(snapshot: DataSnapshot? = null) {
        val activeSessionsList = mutableListOf<AttendanceSessionEntity>()
        val currentTime = System.currentTimeMillis()

        val dataSnapshot = snapshot ?: return // If no snapshot, wait for listener

        for (sessionSnapshot in dataSnapshot.children) {
            val session = sessionSnapshot.getValue(AttendanceSessionEntity::class.java) ?: continue
            
            // Must be ACTIVE based on Faculty status
            if (session.isActive && 
                (myGroupIds.contains(session.groupId) || currentUser!!.role.equals("ADMIN", true))) {
                activeSessionsList.add(session)
            }
        }

        runOnUiThread {
            val adapter = recyclerView.adapter as? StudentAttendanceAdapter
            if (activeSessionsList.isEmpty()) {
                noSessionsText.visibility = View.VISIBLE
                recyclerView.visibility = View.GONE
            } else {
                noSessionsText.visibility = View.GONE
                recyclerView.visibility = View.VISIBLE
                
                adapter?.updateSessions(activeSessionsList)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        sessionsListener?.let { sessionsRef.removeEventListener(it) }
        refreshHandler.removeCallbacks(refreshRunnable)
    }
}
