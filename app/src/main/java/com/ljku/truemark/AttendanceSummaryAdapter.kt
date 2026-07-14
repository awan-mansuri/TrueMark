package com.ljku.truemark

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale

class AttendanceSummaryAdapter(
    private val summaries: List<AttendanceSummary>,
    private val showFullDetails: Boolean,
    private val onItemClick: ((AttendanceSummary) -> Unit)? = null
) : RecyclerView.Adapter<AttendanceSummaryAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val subjectName: TextView = view.findViewById(R.id.subjectName)
        val totalLectures: TextView = view.findViewById(R.id.totalLectures)
        val presentCount: TextView = view.findViewById(R.id.presentCount)
        val absentCount: TextView = view.findViewById(R.id.absentCount)
        val percentageText: TextView = view.findViewById(R.id.percentageText)
        
        val detailsContainer: View = view.findViewById(R.id.detailsContainer)
        val percentageContainer: View = view.findViewById(R.id.percentageContainer)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_attendance_summary, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val summary = summaries[position]
        holder.subjectName.text = summary.subject
        holder.totalLectures.text = summary.totalLectures.toString()

        if (showFullDetails) {
            holder.detailsContainer.visibility = View.VISIBLE
            holder.percentageContainer.visibility = View.VISIBLE
            holder.presentCount.text = summary.present.toString()
            holder.absentCount.text = summary.absent.toString()
            holder.percentageText.text = String.format(Locale.getDefault(), "%.1f%%", summary.percentage)
        } else {
            holder.detailsContainer.visibility = View.GONE
            holder.percentageContainer.visibility = View.GONE
        }
        
        // Set click listener
        holder.itemView.setOnClickListener {
            onItemClick?.invoke(summary)
        }
    }

    override fun getItemCount() = summaries.size
}
