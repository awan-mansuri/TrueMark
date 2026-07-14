package com.ljku.truemark.database

data class ProfileImageEntity(
    val userId: Int = 0,
    val imageData: String = "", // Changed to String (Base64 or URL) for Firebase compatibility if needed, but keeping it simple. Actually, Firebase usually stores images in Storage.
    val timestamp: Long = System.currentTimeMillis()
)
