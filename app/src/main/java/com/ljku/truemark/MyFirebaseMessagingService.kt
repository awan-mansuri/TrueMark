package com.ljku.truemark

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.ljku.truemark.database.AttendanceSessionEntity
import com.ljku.truemark.database.MessageEntity
import com.ljku.truemark.database.GroupEntity
import com.ljku.truemark.database.UserEntity

class MyFirebaseMessagingService : FirebaseMessagingService() {

    private var pushNotificationListener: ValueEventListener? = null

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCM", "New FCM token: $token")
        // Sync token to Firebase under the logged-in user if available
        val userId = SessionManager(applicationContext).getLoggedInUser()?.id
        if (userId != null) {
            FirebaseDatabase.getInstance().getReference("users")
                .child(userId.toString())
                .child("fcmToken")
                .setValue(token)
        }
        
        // Start listening for push notifications for this token
        startPushNotificationListener(token)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clean up push notification listener
        pushNotificationListener?.let {
            FirebaseDatabase.getInstance().getReference("push_notifications")
                .removeEventListener(it)
        }
    }

    private fun startPushNotificationListener(fcmToken: String) {
        pushNotificationListener = FirebaseDatabase.getInstance()
            .getReference("push_notifications")
            .child(fcmToken)
            .orderByChild("delivered")
            .equalTo(false)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    for (notificationSnapshot in snapshot.children) {
                        val dataMap = notificationSnapshot.child("data").getValue(Map::class.java)
                        if (dataMap != null) {
                            // Safely cast to Map<String, String>
                            @Suppress("UNCHECKED_CAST")
                            val data = dataMap as? Map<String, String>
                            if (data != null) {
                                // Extract notification data
                                val title = data["title"] ?: "TrueMark"
                                val body = data["body"] ?: "New notification"
                                val type = data["type"] ?: "general"
                                
                                // Send local notification
                                sendNotification(title, body, type)
                                
                                // Mark as delivered
                                notificationSnapshot.ref.child("delivered").setValue(true)
                            }
                        }
                    }
                }
                override fun onCancelled(error: DatabaseError) {
                    Log.e("FCM", "Push notification listener cancelled: ${error.message}")
                }
            })
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d("FCM", "Message received: ${remoteMessage.data}")
        
        // Handle data messages for attendance and group notifications
        remoteMessage.data?.let { data ->
            when {
                data["type"] == "ATTENDANCE_STARTED" -> {
                    // Faculty started attendance session
                    val subject = data["subject"] ?: "Unknown"
                    val facultyName = data["facultyName"] ?: "Faculty"
                    sendNotification(
                        "Attendance Started", 
                        "$facultyName has started attendance for $subject",
                        "attendance"
                    )
                }
                
                data["type"] == "GROUP_MESSAGE" -> {
                    // New message in group
                    val groupName = data["groupName"] ?: "Group"
                    val senderName = data["senderName"] ?: "Someone"
                    val messageText = data["message"] ?: "New message"
                    sendNotification(
                        "New Message in $groupName", 
                        "$senderName: $messageText",
                        "message"
                    )
                }
                
                data["type"] == "ATTENDANCE_MARKED" -> {
                    // Student marked attendance (for faculty)
                    val studentName = data["studentName"] ?: "Student"
                    val subject = data["subject"] ?: "Subject"
                    sendNotification(
                        "Attendance Marked", 
                        "$studentName marked attendance for $subject",
                        "attendance"
                    )
                }
            }
        }
        
        // Handle notification messages (fallback)
        remoteMessage.notification?.let {
            sendNotification(it.title ?: "TrueMark", it.body ?: "")
        }
    }

    private fun sendNotification(title: String, messageBody: String, notificationType: String = "general") {
        val intent = when (notificationType) {
            "message" -> Intent(this, ChatListActivity::class.java)
            "attendance" -> Intent(this, StudentAttendanceActivity::class.java)
            else -> Intent(this, SplashActivity::class.java)
        }
        
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pendingIntent = PendingIntent.getActivity(this, System.currentTimeMillis().toInt(), intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE)

        val channelId = "truemark_notifications"
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.logo)
            .setContentTitle(title)
            .setContentText(messageBody)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "TrueMark Notifications", NotificationManager.IMPORTANCE_HIGH)
            channel.description = "Notifications for attendance and group messages"
            notificationManager.createNotificationChannel(channel)
        }

        val notificationId = System.currentTimeMillis().toInt()
        notificationManager.notify(notificationId, notificationBuilder.build())
        Log.d("FCM", "Notification sent: $title - $messageBody")
    }
}
