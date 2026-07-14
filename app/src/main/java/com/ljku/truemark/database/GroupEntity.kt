package com.ljku.truemark.database

data class GroupEntity(
    val id: Int = 0,
    val groupName: String = "",
    val createdByUserId: Int = 0,
    val creatorName: String = "",
    val memberIds: String = "", // Comma separated list of user IDs
    val adminIds: String = "",  // Comma separated list of user IDs who are group admins
    val onlyAdminsCanMessage: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val subjects: String? = null
)
