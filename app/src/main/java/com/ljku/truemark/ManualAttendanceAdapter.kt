package com.ljku.truemark

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.ljku.truemark.database.UserEntity

data class ManualAttendanceStudent(
    val student: UserEntity,
    var isPresent: Boolean = true,
    var rollNumber: String = ""
)

class ManualAttendanceAdapter(
    private var students: List<ManualAttendanceStudent>
) : RecyclerView.Adapter<ManualAttendanceAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.studentName)
        val rollNo: TextView = view.findViewById(R.id.studentRollNo)
        val radioGroup: RadioGroup = view.findViewById(R.id.attendanceRadioGroup)
        val radioAbsent: RadioButton = view.findViewById(R.id.radioAbsent)
        val radioPresent: RadioButton = view.findViewById(R.id.radioPresent)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_manual_attendance, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = students[position]
        holder.name.text = item.student.name
        holder.rollNo.text = "Roll: ${item.rollNumber.ifEmpty { "N/A" }}"

        // Set initial state without triggering listener
        holder.radioGroup.setOnCheckedChangeListener(null)
        if (item.isPresent) {
            holder.radioPresent.isChecked = true
        } else {
            holder.radioAbsent.isChecked = true
        }

        // Set listener for changes
        holder.radioGroup.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.radioAbsent -> item.isPresent = false
                R.id.radioPresent -> item.isPresent = true
            }
        }
    }

    override fun getItemCount() = students.size

    fun updateStudents(newStudents: List<ManualAttendanceStudent>) {
        students = newStudents
        notifyDataSetChanged()
    }

    fun getStudents(): List<ManualAttendanceStudent> = students

    fun markAllAbsent() {
        students.forEach { it.isPresent = false }
        notifyDataSetChanged()
    }

    fun markAllPresent() {
        students.forEach { it.isPresent = true }
        notifyDataSetChanged()
    }
}
