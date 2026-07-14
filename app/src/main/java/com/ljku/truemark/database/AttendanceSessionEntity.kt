package com.ljku.truemark.database

import com.google.firebase.database.PropertyName

data class AttendanceSessionEntity(
    val id: Long = 0L,
    val groupId: Int = 0,
    val facultyId: Int = 0,
    val facultyName: String = "",
    val subject: String = "",
    val startTime: Long = System.currentTimeMillis(),
    val durationMinutes: Int = 0,
    @get:PropertyName("isActive")
    @set:PropertyName("isActive")
    var isActive: Boolean = false
)
