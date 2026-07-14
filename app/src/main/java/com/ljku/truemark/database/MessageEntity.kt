package com.ljku.truemark.database

data class MessageEntity(
    val id: Int = 0,
    val groupId: Int = 0,
    val senderId: Int = 0,
    val senderName: String = "",
    val messageText: String? = null,
    val mediaUri: String? = null,
    val mediaType: String? = null,
    val messageType: String = "TEXT", // TEXT, SYSTEM
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false
)
