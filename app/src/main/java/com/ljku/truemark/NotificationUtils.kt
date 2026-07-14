package com.ljku.truemark

import android.util.Log
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.RemoteMessage
import com.ljku.truemark.database.UserEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

object NotificationUtils {
    
    private val firebaseDatabase = FirebaseDatabase.getInstance()
    
    fun sendGroupMessageNotification(
        groupName: String,
        senderName: String,
        message: String,
        groupId: String,
        senderId: String,
        memberIds: List<String>
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Send to each group member (except sender)
                for (memberId in memberIds) {
                    if (memberId != senderId) {
                        sendNotificationToUser(
                            userId = memberId,
                            title = "New Message in $groupName",
                            message = "$senderName: $message",
                            type = "GROUP_MESSAGE",
                            data = mapOf(
                                "groupName" to groupName,
                                "senderName" to senderName,
                                "message" to message,
                                "groupId" to groupId,
                                "type" to "GROUP_MESSAGE"
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("NotificationUtils", "Error sending group message notification: ${e.message}")
            }
        }
    }
    
    fun sendAttendanceNotification(
        subject: String,
        facultyName: String,
        groupId: String,
        memberIds: List<String>
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Send to each group member
                for (memberId in memberIds) {
                    sendNotificationToUser(
                        userId = memberId,
                        title = "Attendance Started",
                        message = "$facultyName has started attendance for $subject",
                        type = "ATTENDANCE_STARTED",
                        data = mapOf(
                            "subject" to subject,
                            "facultyName" to facultyName,
                            "groupId" to groupId,
                            "type" to "ATTENDANCE_STARTED"
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e("NotificationUtils", "Error sending attendance notification: ${e.message}")
            }
        }
    }
    
    private suspend fun sendNotificationToUser(
        userId: String,
        title: String,
        message: String,
        type: String,
        data: Map<String, String>
    ) {
        try {
            // Get user's FCM token
            val userSnapshot = firebaseDatabase.getReference("users")
                .child(userId)
                .get()
                .await()
            
            val user = userSnapshot.getValue(UserEntity::class.java)
            if (user?.fcmToken != null) {
                // Store notification in database for backup
                storeNotificationInDatabase(user.id, title, message, type, data)
                
                // Send FCM push notification
                sendFCMNotification(user.fcmToken!!, title, message, data)
                
                Log.d("NotificationUtils", "Notification sent to user $userId: $title")
            } else {
                Log.w("NotificationUtils", "No FCM token found for user $userId")
            }
        } catch (e: Exception) {
            Log.e("NotificationUtils", "Error sending notification to user $userId: ${e.message}")
        }
    }
    
    private suspend fun storeNotificationInDatabase(
        userId: Int,
        title: String,
        message: String,
        type: String,
        data: Map<String, String>
    ) {
        try {
            val notificationId = firebaseDatabase.getReference("notifications")
                .child(userId.toString())
                .push().key ?: System.currentTimeMillis().toString()
            
            val notification = mapOf(
                "id" to notificationId.hashCode(),
                "userId" to userId,
                "title" to title,
                "message" to message,
                "type" to type,
                "timestamp" to System.currentTimeMillis(),
                "data" to data
            )
            
            firebaseDatabase.getReference("notifications")
                .child(userId.toString())
                .child(notificationId)
                .setValue(notification)
                .await()
        } catch (e: Exception) {
            Log.e("NotificationUtils", "Error storing notification in database: ${e.message}")
        }
    }
    
    private suspend fun sendFCMNotification(
        fcmToken: String,
        title: String,
        message: String,
        data: Map<String, String>
    ) {
        try {
            // Combine all data including title and body
            val allData = data + mapOf(
                "title" to title,
                "body" to message,
                "click_action" to "FLUTTER_NOTIFICATION_CLICK"
            )
            
            // Create the notification message
            val notificationMessage = RemoteMessage.Builder(fcmToken)
                .setData(allData)
                .build()
            
            // Store notification in database for the client to pick up
            // This is the modern approach - store in database and let client listen
            storeNotificationForClient(fcmToken, allData)
            
            Log.d("NotificationUtils", "FCM notification data stored for client pickup")
        } catch (e: Exception) {
            Log.e("NotificationUtils", "Error sending FCM notification: ${e.message}")
        }
    }
    
    private suspend fun storeNotificationForClient(fcmToken: String, data: Map<String, String>) {
        try {
            // Store notification in a special node for real-time delivery
            val notificationRef = FirebaseDatabase.getInstance()
                .getReference("push_notifications")
                .child(fcmToken)
                .push()
            
            notificationRef.setValue(mapOf(
                "data" to data,
                "timestamp" to System.currentTimeMillis(),
                "delivered" to false
            )).await()
            
            // Clean up old notifications (keep only last 50 per token)
            cleanupOldNotifications(fcmToken)
            
        } catch (e: Exception) {
            Log.e("NotificationUtils", "Error storing notification for client: ${e.message}")
        }
    }
    
    private suspend fun cleanupOldNotifications(fcmToken: String) {
        try {
            val notificationsRef = FirebaseDatabase.getInstance()
                .getReference("push_notifications")
                .child(fcmToken)
            
            notificationsRef.get().await().children
                .sortedByDescending { it.child("timestamp").getValue(Long::class.java) }
                .drop(50)
                .forEach { it.ref.removeValue() }
                
        } catch (e: Exception) {
            Log.e("NotificationUtils", "Error cleaning up old notifications: ${e.message}")
        }
    }
}
