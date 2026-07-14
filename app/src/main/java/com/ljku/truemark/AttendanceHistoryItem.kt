package com.ljku.truemark

import com.ljku.truemark.database.AttendanceSessionEntity

data class AttendanceHistoryItem(
    val session: AttendanceSessionEntity,
    val isPresent: Boolean
)
