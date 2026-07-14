package com.ljku.truemark

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.ljku.truemark.database.AttendanceRecordEntity
import java.text.SimpleDateFormat
import java.util.*

class AttendanceMonitorAdapter(
    private var records: List<AttendanceRecordEntity>,
    private val onStatusToggleClick: (AttendanceRecordEntity) -> Unit
) : RecyclerView.Adapter<AttendanceMonitorAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.studentName)
        val rollNo: TextView = view.findViewById(R.id.studentRollNo)
        val time: TextView = view.findViewById(R.id.markTime)
        val btnAbsent: TextView = view.findViewById(R.id.btnAbsent)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_attendance_monitor, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val record = records[position]
        holder.name.text = record.studentName
        holder.rollNo.text = "Roll No: ${record.studentRollNo}"
        val sdf = SimpleDateFormat("hh:mm:ss a", Locale.getDefault())
        holder.time.text = sdf.format(Date(record.timestamp))

        // Set button state based on attendance status
        if (record.isPresent) {
            holder.btnAbsent.text = "Mark Absent"
            holder.btnAbsent.setTextColor(Color.parseColor("#F44336"))
            holder.name.setTextColor(Color.BLACK)
            holder.rollNo.setTextColor(Color.GRAY)
            holder.itemView.setBackgroundColor(Color.parseColor("#FFFFFF"))
        } else {
            holder.btnAbsent.text = "Mark Present"
            holder.btnAbsent.setTextColor(Color.parseColor("#4CAF50"))
            holder.name.setTextColor(Color.LTGRAY)
            holder.rollNo.setTextColor(Color.LTGRAY)
            holder.itemView.setBackgroundColor(Color.parseColor("#F5F5F5"))
        }

        // Set click listener with proper state management
        holder.btnAbsent.setOnClickListener {
            // Prevent multiple rapid clicks
            holder.btnAbsent.isEnabled = false
            holder.btnAbsent.postDelayed({ holder.btnAbsent.isEnabled = true }, 500)
            
            onStatusToggleClick(record)
        }
    }

    override fun getItemCount() = records.size

    fun updateRecords(newRecords: List<AttendanceRecordEntity>) {
        records = newRecords
        notifyDataSetChanged()
    }
}
