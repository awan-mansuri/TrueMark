package com.ljku.truemark.database

data class UserEntity(
    val id: Int = 0,
    val name: String = "",
    val email: String = "", 
    val password: String = "",
    val role: String = "",
    val mobile: String? = null,
    val personalEmail: String? = null,
    val createdBy: Int? = null,
    val profileImage: String? = null,
    val bio: String? = null,
    val fcmToken: String? = null
)
