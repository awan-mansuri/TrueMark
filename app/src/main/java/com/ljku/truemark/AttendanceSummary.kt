package com.ljku.truemark

data class AttendanceSummary(
    val subject: String,
    val totalLectures: Int,
    val present: Int,
    val absent: Int,
    val percentage: Double
)
