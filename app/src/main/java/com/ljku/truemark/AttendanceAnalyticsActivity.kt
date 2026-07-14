package com.ljku.truemark

import android.graphics.Color
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.ljku.truemark.databinding.ActivityAttendanceAnalyticsBinding
import kotlinx.coroutines.launch

class AttendanceAnalyticsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAttendanceAnalyticsBinding
    private val repository = FirebaseRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAttendanceAnalyticsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        loadAnalytics()
    }

    private fun loadAnalytics() {
        lifecycleScope.launch {
            val stats = repository.getAttendanceStats()
            val totalPresent = stats["totalPresent"] as Int
            val percentage = stats["percentage"] as Float

            binding.tvTotalStudents.text = "Total Present Today: $totalPresent"
            binding.tvAttendancePercentage.text = "${"%.1f".format(percentage)}%"
            
            setupBarChart()
        }
    }

    private fun setupBarChart() {
        val entries = ArrayList<BarEntry>()
        // Dummy weekly data: Mon-Fri
        entries.add(BarEntry(0f, 45f))
        entries.add(BarEntry(1f, 38f))
        entries.add(BarEntry(2f, 50f))
        entries.add(BarEntry(3f, 42f))
        entries.add(BarEntry(4f, 48f))

        val dataSet = BarDataSet(entries, "Attendance")
        dataSet.color = Color.parseColor("#4CAF50")
        
        val barData = BarData(dataSet)
        binding.barChart.data = barData
        binding.barChart.invalidate()
        
        binding.barChart.description.isEnabled = false
        binding.barChart.xAxis.setDrawGridLines(false)
    }
}
