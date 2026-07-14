package com.ljku.truemark

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.*
import com.ljku.truemark.database.AttendanceSessionEntity
import com.ljku.truemark.database.UserEntity
import java.text.SimpleDateFormat
import java.util.*

class AttendanceHistoryAdapter(
    private val facultySessions: List<AttendanceSessionEntity>?,
    private val studentItems: List<AttendanceHistoryItem>?
) : RecyclerView.Adapter<AttendanceHistoryAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val subjectText: TextView = view.findViewById(R.id.historySubjectText)
        val timeText: TextView = view.findViewById(R.id.historyTimeText)
        val statusText: TextView = view.findViewById(R.id.historyStatusText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_attendance_history, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val sdf = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
        
        if (facultySessions != null) {
            val session = facultySessions[position]
            holder.subjectText.text = session.subject
            holder.timeText.text = sdf.format(Date(session.startTime))
            holder.statusText.text = "CONDUCTED"
            holder.statusText.setTextColor(Color.parseColor("#4CAF50"))
        } else if (studentItems != null) {
            val item = studentItems[position]
            holder.subjectText.text = item.session.subject
            
            val dateTimeStr = sdf.format(Date(item.session.startTime))
            holder.timeText.text = dateTimeStr
            
            val usersRef = FirebaseDatabase.getInstance().getReference("users")
            usersRef.child(item.session.facultyId.toString()).addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val faculty = snapshot.getValue(UserEntity::class.java)
                    if (faculty != null) {
                        holder.timeText.text = "$dateTimeStr | By: ${faculty.name}"
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            })
            
            if (item.isPresent) {
                holder.statusText.text = "PRESENT"
                holder.statusText.setTextColor(Color.parseColor("#4CAF50"))
            } else {
                holder.statusText.text = "ABSENT"
                holder.statusText.setTextColor(Color.RED)
            }
        }
    }

    override fun getItemCount(): Int {
        return facultySessions?.size ?: studentItems?.size ?: 0
    }
}
