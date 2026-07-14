package com.ljku.truemark

data class UserWithProfileStatus(
    val id: Int,
    val name: String,
    val email: String,
    val rollNumber: String?,
    val profileImage: String? = null
)
