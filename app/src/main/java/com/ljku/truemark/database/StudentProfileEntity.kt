package com.ljku.truemark.database

data class StudentProfileEntity(
    val id: Int = 0,
    val userId: Int = 0,
    val enrollmentNo: String = "",
    val rollNumber: String = "",
    val division: String = "",
    val batch: String = "",
    val department: String = "",
    val semester: String = "",
    val dateOfBirth: String = ""
)
