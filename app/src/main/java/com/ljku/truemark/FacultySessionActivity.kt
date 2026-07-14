package com.ljku.truemark

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.*
import com.ljku.truemark.database.AttendanceRecordEntity
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class FacultySessionActivity : AppCompatActivity() {

    private lateinit var timerText: TextView
    private lateinit var pendingCountText: TextView
    private lateinit var btnStop: Button
    private lateinit var btnDone: Button
    private lateinit var qrImageView: ImageView
    private lateinit var qrCountdownText: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var pendingAdapter: PendingAttendanceAdapter
    
    private var sessionId: String? = null
    private var subject: String? = null
    private var duration: Int = -1
    private var groupId: Int = 0
    private var qrTimer: CountDownTimer? = null
    private var sessionTimer: CountDownTimer? = null
    private var isAttendanceActive = true
    
    private val repository = FirebaseRepository()
    private val recordsRef = FirebaseDatabase.getInstance().getReference("attendance_records")
    private var recordsListener: ValueEventListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_faculty_session)

        subject = intent.getStringExtra("SUBJECT") ?: "Attendance"
        duration = intent.getIntExtra("DURATION", 15)
        groupId = intent.getIntExtra("GROUP_ID", 0)

        findViewById<TextView>(R.id.sessionSubjectTitle).text = subject
        timerText = findViewById(R.id.timerText)
        pendingCountText = findViewById(R.id.pendingCountText)
        btnStop = findViewById(R.id.btnStopAttendance)
        btnDone = findViewById(R.id.btnDone)
        qrImageView = findViewById(R.id.ivQRCode)
        qrCountdownText = findViewById(R.id.tvCountdown)
        recyclerView = findViewById(R.id.attendanceRecyclerView)

        recyclerView.layoutManager = LinearLayoutManager(this)
        pendingAdapter = PendingAttendanceAdapter(mutableListOf()) { student, position ->
            markStudentAsAbsent(student, position)
        }
        recyclerView.adapter = pendingAdapter

        qrImageView.visibility = View.VISIBLE
        qrCountdownText.visibility = View.VISIBLE

        startNewAttendanceSession()

        btnStop.setOnClickListener {
            stopSession()
        }

        btnDone.setOnClickListener {
            finish()
        }
    }

    private fun markStudentAsAbsent(student: PendingStudent, position: Int) {
        lifecycleScope.launch {
            try {
                val sid = sessionId ?: return@launch
                val recordId = "${sid}_${student.studentId}"
                
                // Delete the pending record so student won't be marked present on session end
                recordsRef.child(recordId).removeValue().await()
                
                // Remove from UI
                pendingAdapter.removeStudent(position)
                updatePendingCount()
                
                Toast.makeText(this@FacultySessionActivity, "${student.studentName} marked as absent", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e("FacultySession", "Error marking absent", e)
                pendingAdapter.notifyItemChanged(position)
            }
        }
    }

    private fun updatePendingCount() {
        val count = pendingAdapter.getPendingStudents().size
        pendingCountText.text = "Pending Students: $count"
    }

    private fun startNewAttendanceSession() {
        lifecycleScope.launch {
            try {
                qrCountdownText.text = "Initializing session..."
                
                // Get current logged in faculty details
                val sessionManager = SessionManager(applicationContext)
                val faculty = sessionManager.getLoggedInUser()
                
                sessionId = repository.createAttendanceSession(
                    teacherId = faculty?.id ?: 1, 
                    teacherName = faculty?.name ?: "Faculty", 
                    subject = subject ?: "Subject", 
                    groupId = intent.getIntExtra("GROUP_ID", 1), 
                    durationMinutes = duration
                )
                
                if (sessionId != null) {
                    startQRRefreshCycle()
                    if (duration > 0) {
                        startSessionTimer()
                    } else {
                        timerText.text = "Session: Unlimited Time"
                    }
                    startRealtimeAttendanceListener()
                } else {
                    qrCountdownText.text = "Failed to create session"
                    Toast.makeText(this@FacultySessionActivity, "Failed to create session.", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Log.e("FacultySession", "Error starting session", e)
                Toast.makeText(this@FacultySessionActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun startQRRefreshCycle() {
        qrTimer?.cancel()
        
        lifecycleScope.launch {
            val sid = sessionId ?: return@launch
            val qrData = repository.updateSessionQR(sid)
            if (qrData != null) {
                val initialBitmap = QRGenerator.generateQRCode(qrData)
                if (initialBitmap != null) {
                    qrImageView.setImageBitmap(initialBitmap)
                    qrImageView.visibility = View.VISIBLE
                }
            }
        }
        
        startQRRefreshTimer()
    }

    private fun refreshQR() {
        val sid = sessionId ?: return
        lifecycleScope.launch {
            try {
                val qrData = repository.updateSessionQR(sid)
                if (qrData != null) {
                    val bitmap = QRGenerator.generateQRCode(qrData)
                    if (bitmap != null) {
                        qrImageView.setImageBitmap(bitmap)
                        qrImageView.visibility = View.VISIBLE
                    }
                }
                
                if (isAttendanceActive) {
                    startQRRefreshTimer()
                }
            } catch (e: Exception) {
                Log.e("FacultySession", "Error refreshing QR", e)
            }
        }
    }

    private fun startQRRefreshTimer() {
        qrTimer?.cancel()
        qrTimer = object : CountDownTimer(15000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                qrCountdownText.text = "QR refresh in: ${millisUntilFinished / 1000}s"
            }

            override fun onFinish() {
                if (isAttendanceActive) refreshQR()
            }
        }.start()
    }

    private fun startSessionTimer() {
        sessionTimer?.cancel()
        sessionTimer = object : CountDownTimer((duration * 60 * 1000).toLong(), 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val mins = (millisUntilFinished / 1000) / 60
                val secs = (millisUntilFinished / 1000) % 60
                timerText.text = String.format("Session Ends in: %02d:%02d", mins, secs)
            }

            override fun onFinish() {
                // Keep session active for 5 extra minutes after timer ends for late scans
                // or close it immediately as per your requirement
                stopSession()
            }
        }.start()
    }

    private fun startRealtimeAttendanceListener() {
        val sid = sessionId ?: return
        
        recordsListener = recordsRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!isAttendanceActive) return
                
                for (child in snapshot.children) {
                    val recordId = child.key ?: continue
                    if (!recordId.startsWith("${sid}_")) continue
                    
                    val status = child.child("status").value as? String
                    if (status == "PENDING") {
                        val studentId = child.child("studentId").value?.toString()?.toIntOrNull() ?: continue
                        val studentName = child.child("studentName").value as? String ?: "Unknown"
                        val studentRollNo = child.child("studentRollNo").value as? String ?: "N/A"
                        val timestamp = child.child("timestamp").value as? Long ?: System.currentTimeMillis()
                        
                        val pendingStudent = PendingStudent(studentId, studentName, studentRollNo, timestamp)
                        pendingAdapter.addStudent(pendingStudent)
                        updatePendingCount()
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun stopSession() {
        isAttendanceActive = false
        qrTimer?.cancel()
        sessionTimer?.cancel()
        
        sessionId?.let { sid ->
            lifecycleScope.launch {
                try {
                    // Mark all pending students as PRESENT
                    val pendingStudents = pendingAdapter.getPendingStudents()
                    for (student in pendingStudents) {
                        val recordId = "${sid}_${student.studentId}"
                        val updates = hashMapOf<String, Any>(
                            "status" to "PRESENT",
                            "isPresent" to true,
                            "present" to true
                        )
                        recordsRef.child(recordId).updateChildren(updates).await()
                    }
                    
                    // Stop the session
                    repository.stopSession(sid)
                    
                    Toast.makeText(this@FacultySessionActivity, 
                        "Session closed. ${pendingStudents.size} students marked present.", 
                        Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Log.e("FacultySession", "Error finalizing attendance", e)
                }
            }
        }
        
        btnStop.visibility = View.GONE
        btnDone.visibility = View.VISIBLE
        qrImageView.visibility = View.GONE
        qrCountdownText.text = "Session Closed"
        timerText.text = "Session Finished"
    }

    override fun onDestroy() {
        super.onDestroy()
        qrTimer?.cancel()
        sessionTimer?.cancel()
        recordsListener?.let { recordsRef.removeEventListener(it) }
    }
}
