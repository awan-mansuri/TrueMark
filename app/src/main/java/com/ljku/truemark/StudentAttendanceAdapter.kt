package com.ljku.truemark

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.*
import com.ljku.truemark.database.AttendanceSessionEntity
import java.text.SimpleDateFormat
import java.util.*

class StudentAttendanceAdapter(
    private var sessions: List<AttendanceSessionEntity>,
    private val studentId: Int,
    private val onAttendClick: (AttendanceSessionEntity) -> Unit
) : RecyclerView.Adapter<StudentAttendanceAdapter.ViewHolder>() {

    // Cache to store attendance status to avoid flickering
    private val attendanceStatusCache = mutableMapOf<String, Boolean>()

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val subjectText: TextView = view.findViewById(R.id.sessionSubjectText)
        val facultyText: TextView = view.findViewById(R.id.sessionFacultyText)
        val timeText: TextView = view.findViewById(R.id.sessionTimeText)
        val attendBtn: Button = view.findViewById(R.id.btnMarkPresent)
    }

    fun updateSessions(newSessions: List<AttendanceSessionEntity>) {
        sessions = newSessions
        notifyDataSetChanged()
    }

    fun getSessions(): List<AttendanceSessionEntity> = sessions

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_student_attendance, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val session = sessions[position]
        val recordKey = "${session.id}_$studentId"
        
        holder.subjectText.text = session.subject
        val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
        holder.timeText.text = "Started at: ${sdf.format(Date(session.startTime))}"
        holder.facultyText.text = "Faculty: ${session.facultyName}"

        // Check cache first to avoid flickering
        val cachedStatus = attendanceStatusCache[recordKey]
        if (cachedStatus != null) {
            updateButtonState(holder, cachedStatus, session)
        } else {
            // Set default state while loading
            holder.attendBtn.isEnabled = true
            holder.attendBtn.alpha = 1.0f
            holder.attendBtn.text = "Scan QR"
            holder.attendBtn.setBackgroundColor(Color.parseColor("#4CAF50"))
            holder.attendBtn.setOnClickListener { onAttendClick(session) }

            // Check Firebase in background
            val recordsRef = FirebaseDatabase.getInstance().getReference("attendance_records")
            recordsRef.child(recordKey).addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    // Check if record exists AND student is marked present (isPresent = true)
                    val isMarked = if (snapshot.exists()) {
                        val isPresent = snapshot.child("isPresent").value as? Boolean
                            ?: snapshot.child("present").value as? Boolean
                            ?: false
                        isPresent
                    } else {
                        false
                    }
                    attendanceStatusCache[recordKey] = isMarked
                    if (holder.bindingAdapterPosition == position) {
                        updateButtonState(holder, isMarked, session)
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            })
        }
    }

    private fun updateButtonState(holder: ViewHolder, isMarked: Boolean, session: AttendanceSessionEntity) {
        if (isMarked) {
            holder.attendBtn.text = "Attendance Marked"
            holder.attendBtn.isEnabled = false
            holder.attendBtn.alpha = 0.5f
            holder.attendBtn.setBackgroundColor(Color.parseColor("#BDBDBD"))
            holder.attendBtn.setOnClickListener(null)
        } else {
            holder.attendBtn.isEnabled = true
            holder.attendBtn.alpha = 1.0f
            holder.attendBtn.text = "Scan QR"
            holder.attendBtn.setBackgroundColor(Color.parseColor("#4CAF50"))
            holder.attendBtn.setOnClickListener { onAttendClick(session) }
        }
    }

    override fun getItemCount() = sessions.size
}
