package com.ljku.truemark.database

import com.google.firebase.database.PropertyName

data class AttendanceRecordEntity(
    val id: Int = 0,
    val sessionId: Long = 0L,
    val studentId: Int = 0,
    val studentName: String = "",
    val studentRollNo: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    @get:PropertyName("present")
    @set:PropertyName("present")
    var isPresent: Boolean = true
)
