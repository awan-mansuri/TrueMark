package com.ljku.truemark

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class PendingStudent(
    val studentId: Int,
    val studentName: String,
    val studentRollNo: String,
    val scanTime: Long
)

class PendingAttendanceAdapter(
    private var students: MutableList<PendingStudent>,
    private val onMarkAbsentClick: (PendingStudent, Int) -> Unit
) : RecyclerView.Adapter<PendingAttendanceAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.studentName)
        val rollNo: TextView = view.findViewById(R.id.studentRollNo)
        val time: TextView = view.findViewById(R.id.scanTime)
        val btnMarkAbsent: TextView = view.findViewById(R.id.btnMarkAbsent)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_pending_attendance, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val student = students[position]
        holder.name.text = student.studentName
        holder.rollNo.text = "Roll: ${student.studentRollNo}"
        
        val sdf = java.text.SimpleDateFormat("hh:mm:ss a", java.util.Locale.getDefault())
        holder.time.text = "Scanned: ${sdf.format(java.util.Date(student.scanTime))}"

        holder.btnMarkAbsent.setOnClickListener {
            holder.btnMarkAbsent.isEnabled = false
            onMarkAbsentClick(student, position)
        }
    }

    override fun getItemCount() = students.size

    fun addStudent(student: PendingStudent) {
        if (students.none { it.studentId == student.studentId }) {
            students.add(student)
            notifyItemInserted(students.size - 1)
        }
    }

    fun removeStudent(position: Int) {
        if (position >= 0 && position < students.size) {
            students.removeAt(position)
            notifyItemRemoved(position)
            notifyItemRangeChanged(position, students.size - position)
        }
    }

    fun getPendingStudents(): List<PendingStudent> {
        return students.toList()
    }

    fun clear() {
        val size = students.size
        students.clear()
        notifyItemRangeRemoved(0, size)
    }
}
