package com.ljku.truemark.database

data class NotificationEntity(
    val id: Int = 0,
    val userId: Int = 0,
    val title: String = "",
    val message: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false,
    val type: String = "" // e.g., "LOGIN", "CHAT", "ACADEMIC"
)
