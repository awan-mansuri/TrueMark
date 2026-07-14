package com.ljku.truemark

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.ljku.truemark.UserWithProfileStatus

class StudentAdapter(
    private var students: List<UserWithProfileStatus>,
    private val onEditClick: (UserWithProfileStatus) -> Unit
) : RecyclerView.Adapter<StudentAdapter.StudentViewHolder>() {

    class StudentViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nameText: TextView = view.findViewById(R.id.studentName)
        val emailText: TextView = view.findViewById(R.id.studentEmail)
        val rollNoText: TextView = view.findViewById(R.id.rollNumberText)
        val editIcon: ImageView = view.findViewById(R.id.editIcon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StudentViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_student_row, parent, false)
        return StudentViewHolder(view)
    }

    override fun onBindViewHolder(holder: StudentViewHolder, position: Int) {
        val student = students[position]
        holder.nameText.text = student.name
        holder.emailText.text = student.email
        holder.rollNoText.text = if (student.rollNumber.isNullOrEmpty()) "Roll No: Not Assigned" else "Roll No: ${student.rollNumber}"
        
        holder.itemView.setOnClickListener { onEditClick(student) }
        holder.editIcon.setOnClickListener { onEditClick(student) }
    }

    override fun getItemCount() = students.size

    fun updateData(newStudents: List<UserWithProfileStatus>) {
        students = newStudents
        notifyDataSetChanged()
    }
}
