package com.ljku.truemark

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.firebase.database.*
import com.ljku.truemark.database.AttendanceSessionEntity
import java.text.SimpleDateFormat
import java.util.*

class SubjectAttendanceDetailActivity : AppCompatActivity() {

    private lateinit var subjectNameText: TextView
    private lateinit var totalLecturesText: TextView
    private lateinit var presentCountText: TextView
    private lateinit var absentCountText: TextView
    private lateinit var percentageText: TextView
    private lateinit var requiredLecturesText: TextView
    private lateinit var lineChart: LineChart
    private lateinit var backButton: ImageView
    
    private val sessionsRef = FirebaseDatabase.getInstance().getReference("attendance_sessions")
    private val recordsRef = FirebaseDatabase.getInstance().getReference("attendance_records")
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_subject_attendance_detail)
        
        val subjectName = intent.getStringExtra("SUBJECT_NAME") ?: return
        val studentId = intent.getIntExtra("STUDENT_ID", -1)
        val groupId = intent.getIntExtra("GROUP_ID", -1)
        
        if (studentId == -1 || groupId == -1) {
            finish()
            return
        }
        
        initViews()
        subjectNameText.text = subjectName
        backButton.setOnClickListener { finish() }
        
        loadAttendanceDetails(subjectName, studentId, groupId)
    }
    
    private fun initViews() {
        subjectNameText = findViewById(R.id.subjectNameText)
        totalLecturesText = findViewById(R.id.totalLecturesText)
        presentCountText = findViewById(R.id.presentCountText)
        absentCountText = findViewById(R.id.absentCountText)
        percentageText = findViewById(R.id.percentageText)
        requiredLecturesText = findViewById(R.id.requiredLecturesText)
        lineChart = findViewById(R.id.attendanceLineChart)
        backButton = findViewById(R.id.backButton)
        
        setupLineChart()
    }
    
    private fun setupLineChart() {
        lineChart.apply {
            description.isEnabled = false
            setTouchEnabled(true)
            setDrawGridBackground(false)
            
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                granularity = 1f
                setDrawGridLines(false)
            }
            
            axisLeft.apply {
                setDrawGridLines(true)
                axisMinimum = 0f
            }
            
            axisRight.isEnabled = false
            legend.isEnabled = true
        }
    }
    
    private fun loadAttendanceDetails(subject: String, studentId: Int, groupId: Int) {
        sessionsRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(sessionSnapshot: DataSnapshot) {
                val subjectSessions = mutableListOf<AttendanceSessionEntity>()
                
                for (child in sessionSnapshot.children) {
                    val session = child.getValue(AttendanceSessionEntity::class.java)
                    if (session != null && session.groupId == groupId && session.subject == subject) {
                        subjectSessions.add(session)
                    }
                }
                
                // Sort by start time
                subjectSessions.sortBy { it.startTime }
                
                recordsRef.addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(recordSnapshot: DataSnapshot) {
                        val presentSessionIds = mutableSetOf<Long>()
                        
                        for (child in recordSnapshot.children) {
                            val sid = child.child("sessionId").value?.toString()?.toLongOrNull() ?: continue
                            val sidInt = child.child("studentId").value?.toString()?.toIntOrNull() ?: continue
                            
                            if (sidInt == studentId) {
                                val isPresent = child.child("isPresent").value?.toString()?.toBoolean()
                                    ?: child.child("present").value?.toString()?.toBoolean()
                                    ?: false
                                if (isPresent) {
                                    presentSessionIds.add(sid)
                                }
                            }
                        }
                        
                        val totalLectures = subjectSessions.size
                        val presentCount = subjectSessions.count { presentSessionIds.contains(it.id) }
                        val absentCount = totalLectures - presentCount
                        val percentage = if (totalLectures > 0) {
                            (presentCount * 100.0 / totalLectures)
                        } else 0.0
                        
                        // Update UI
                        totalLecturesText.text = totalLectures.toString()
                        presentCountText.text = presentCount.toString()
                        absentCountText.text = absentCount.toString()
                        percentageText.text = String.format("%.1f%%", percentage)
                        
                        // Calculate required lectures for 50% if below threshold
                        if (percentage < 50.0 && totalLectures > 0) {
                            val requiredLectures = calculateRequiredLecturesFor50Percent(presentCount, totalLectures)
                            requiredLecturesText.text = "Need $requiredLectures more lectures to reach 50% attendance"
                            requiredLecturesText.visibility = View.VISIBLE
                        } else {
                            requiredLecturesText.visibility = View.GONE
                        }
                        
                        // Setup line graph
                        setupLineGraph(subjectSessions, presentSessionIds)
                    }
                    
                    override fun onCancelled(error: DatabaseError) {}
                })
            }
            
            override fun onCancelled(error: DatabaseError) {}
        })
    }
    
    private fun calculateRequiredLecturesFor50Percent(present: Int, total: Int): Int {
        // Formula: (present + x) / (total + x) >= 0.5
        // present + x >= 0.5 * (total + x)
        // present + x >= 0.5 * total + 0.5 * x
        // x - 0.5 * x >= 0.5 * total - present
        // 0.5 * x >= 0.5 * total - present
        // x >= total - 2 * present
        val required = total - 2 * present
        return if (required > 0) required else 0
    }
    
    private fun setupLineGraph(sessions: List<AttendanceSessionEntity>, presentSessionIds: Set<Long>) {
        if (sessions.isEmpty()) return
        
        val entries = mutableListOf<Entry>()
        val dateLabels = mutableListOf<String>()
        
        var cumulativePresent = 0
        val sdf = SimpleDateFormat("dd/MM", Locale.getDefault())
        
        sessions.forEachIndexed { index, session ->
            if (presentSessionIds.contains(session.id)) {
                cumulativePresent++
            }
            val percentage = (cumulativePresent * 100.0 / (index + 1)).toFloat()
            entries.add(Entry(index.toFloat(), percentage))
            dateLabels.add(sdf.format(Date(session.startTime)))
        }
        
        val dataSet = LineDataSet(entries, "Attendance % Over Time").apply {
            color = resources.getColor(R.color.colorPrimary, null)
            setCircleColor(resources.getColor(R.color.colorAccent, null))
            lineWidth = 2f
            circleRadius = 4f
            setDrawValues(false)
            mode = LineDataSet.Mode.LINEAR
        }
        
        lineChart.data = LineData(dataSet)
        
        lineChart.xAxis.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                val index = value.toInt()
                return if (index >= 0 && index < dateLabels.size) dateLabels[index] else ""
            }
        }
        
        lineChart.invalidate()
    }
}
