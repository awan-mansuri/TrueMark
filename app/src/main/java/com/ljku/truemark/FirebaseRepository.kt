package com.ljku.truemark



import android.util.Log

import com.google.firebase.database.*

import kotlinx.coroutines.tasks.await

import java.util.*



class FirebaseRepository {

    private val database = FirebaseDatabase.getInstance()

    private val sessionsRef = database.getReference("attendance_sessions")

    private val attendanceRef = database.getReference("attendance_records")



    suspend fun createAttendanceSession(

        teacherId: Int, 

        teacherName: String,

        subject: String,

        groupId: Int,

        durationMinutes: Int

    ): String? {

        return try {

            val sessionId = System.currentTimeMillis().toString()

            val session = hashMapOf(

                "id" to sessionId.toLong(),

                "facultyId" to teacherId,

                "facultyName" to teacherName,

                "subject" to subject,

                "groupId" to groupId,

                "isActive" to true,

                "startTime" to System.currentTimeMillis(),

                "durationMinutes" to durationMinutes,

                "currentQrData" to sessionId

            )

            sessionsRef.child(sessionId).setValue(session).await()

            sessionId

        } catch (e: Exception) {

            android.util.Log.e("FirebaseRepository", "Error creating session", e)

            null

        }

    }



    suspend fun updateSessionQR(sessionId: String): String? {

        return try {

            // Get session details to include subject in QR data

            val sessionSnapshot = sessionsRef.child(sessionId).get().await()

            val subject = sessionSnapshot.child("subject").value as? String ?: "Unknown"

            val timestamp = System.currentTimeMillis()

            

            // Create subject-specific QR data format: "sessionId_subject_timestamp"

            val newQrData = "${sessionId}_${subject}_${timestamp}"

            sessionsRef.child(sessionId).child("currentQrData").setValue(newQrData).await()

            sessionsRef.child(sessionId).child("qrTimestamp").setValue(timestamp).await()

            newQrData

        } catch (e: Exception) {

            android.util.Log.e("FirebaseRepository", "Error updating QR", e)

            null

        }

    }



    suspend fun getActiveSessions(groupIds: List<Int>): List<Map<String, Any>> {

        return try {

            val snapshot = sessionsRef.get().await()

            

            val currentTime = System.currentTimeMillis()

            snapshot.children.mapNotNull { child ->

                val data = child.value

                if (data !is Map<*, *>) return@mapNotNull null

                @Suppress("UNCHECKED_CAST")

                val typedData = data as Map<String, Any>

                

                val isActive = typedData["isActive"] as? Boolean ?: false

                val startTime = typedData["startTime"] as? Long ?: 0L

                val duration = (typedData["durationMinutes"] as? Long ?: 0L).toInt()

                val sessionGroupId = (typedData["groupId"] as? Long)?.toInt() ?: -1

                

                if (!isActive) return@mapNotNull null

                if (!groupIds.contains(sessionGroupId)) return@mapNotNull null

                

                data

            }

        } catch (e: Exception) {

            android.util.Log.e("FirebaseRepository", "Error getting active sessions", e)

            emptyList()

        }

    }



    suspend fun stopSession(sessionId: String) {

        try {

            sessionsRef.child(sessionId).child("isActive").setValue(false).await()

        } catch (e: Exception) {

            android.util.Log.e("FirebaseRepository", "Error stopping session", e)

        }

    }



    fun listenToAttendanceCount(sessionId: String, onUpdate: (Int, List<Map<String, Any>>) -> Unit): ValueEventListener {

        val listener = object : ValueEventListener {

            override fun onDataChange(snapshot: DataSnapshot) {

                val attendanceRecords = mutableListOf<Map<String, Any>>()

                for (child in snapshot.children) {

                    val childSessionId = child.child("sessionId").value?.toString()

                    if (childSessionId == sessionId) {

                        val studentName = child.child("studentName").value as? String ?: "Unknown"

                        val studentRollNo = child.child("studentRollNo").value as? String ?: "N/A"

                        val timestamp = child.child("timestamp").value as? Long ?: System.currentTimeMillis()

                        

                        attendanceRecords.add(mapOf(

                            "studentName" to studentName,

                            "studentRollNo" to studentRollNo,

                            "timestamp" to timestamp

                        ))

                    }

                }

                

                // Sort by timestamp (latest first)

                val sortedRecords = attendanceRecords.sortedByDescending { it["timestamp"] as Long }

                onUpdate(sortedRecords.size, sortedRecords)

            }

            override fun onCancelled(error: DatabaseError) {

                android.util.Log.e("FirebaseRepository", "Error listening to attendance", error.toException())

            }

        }

        attendanceRef.addValueEventListener(listener)

        return listener

    }



    suspend fun getAttendanceStats(): Map<String, Any> {

        return try {

            val snapshot = attendanceRef.get().await()

            val totalPresent = snapshot.childrenCount.toInt()

            val totalStudents = 50 // Adjust based on your total students

            val percentage = if (totalStudents > 0) (totalPresent.toFloat() / totalStudents) * 100 else 0f

            

            mapOf(

                "totalPresent" to totalPresent,

                "percentage" to percentage

            )

        } catch (e: Exception) {

            android.util.Log.e("FirebaseRepository", "Error getting attendance stats", e)

            mapOf(

                "totalPresent" to 0,

                "percentage" to 0f

            )

        }

    }



    suspend fun markAttendance(qrData: String, studentId: Int, studentName: String, studentRollNo: String = "N/A"): String {

        return try {

            Log.d("FirebaseRepository", "QR scanned: $qrData")

            

            // Parse QR data format: "sessionId_subject_timestamp"

            val qrParts = qrData.split("_")

            if (qrParts.size < 3) {

                Log.d("FirebaseRepository", "Invalid QR format: $qrData")

                return "INVALID_QR_FORMAT"

            }

            

            val qrSessionId = qrParts[0]

            val qrSubject = qrParts[1]

            val qrTimestamp = qrParts[2].toLongOrNull()

            

            Log.d("FirebaseRepository", "QR Parsed: sessionId=$qrSessionId, subject=$qrSubject, timestamp=$qrTimestamp")

            

            // Check if QR is expired (15 seconds)

            val currentTime = System.currentTimeMillis()

            if (qrTimestamp != null && (currentTime - qrTimestamp) > 15000) {

                Log.d("FirebaseRepository", "QR code expired: $qrData")

                return "QR_EXPIRED"

            }

            

            // Fetch the specific session

            val sessionSnapshot = sessionsRef.child(qrSessionId).get().await()

            if (!sessionSnapshot.exists()) {

                Log.d("FirebaseRepository", "Session not found: $qrSessionId")

                return "SESSION_NOT_FOUND"

            }

            

            val sessionSubject = sessionSnapshot.child("subject").value as? String

            val sessionIsActive = sessionSnapshot.child("isActive").value as? Boolean ?: false

            val sessionIdFromDb = sessionSnapshot.child("id").getValue(Long::class.java)

            

            Log.d("FirebaseRepository", "Session found: id=$sessionIdFromDb, subject=$sessionSubject, active=$sessionIsActive")

            

            // Validate subject match

            if (sessionSubject != qrSubject) {

                Log.d("FirebaseRepository", "Subject mismatch: QR=$qrSubject, Session=$sessionSubject")

                return "SUBJECT_MISMATCH"

            }

            

            // Check if session is active

            if (!sessionIsActive) {

                Log.d("FirebaseRepository", "Session $qrSessionId is not active")

                return "SESSION_INACTIVE"

            }

            

            // Unique key for attendance record: sessionId_studentId

            val recordId = "${qrSessionId}_$studentId"

            

            // Check if already marked

            val existingRecord = attendanceRef.child(recordId).get().await()

            if (existingRecord.exists()) {

                Log.d("FirebaseRepository", "Attendance already marked for $studentId in session $qrSessionId")

                return "ALREADY_MARKED"

            }



            // Mark attendance

            val sessionIdLong = qrSessionId.toLong()

            val attendance = mutableMapOf<String, Any>()

            attendance["id"] = recordId.hashCode()

            attendance["sessionId"] = sessionIdLong

            attendance["studentId"] = studentId

            attendance["studentName"] = studentName

            attendance["studentRollNo"] = studentRollNo

            attendance["status"] = "PENDING"

            attendance["isPresent"] = false

            attendance["present"] = false

            attendance["timestamp"] = System.currentTimeMillis()

            attendance["subject"] = qrSubject

            attendance["verificationMethod"] = "QR_CODE"

            

            Log.d("FirebaseRepository", "Saving attendance with ID: $recordId")

            attendanceRef.child(recordId).setValue(attendance).await()

            

            "SUCCESS"

        } catch (e: Exception) {

            android.util.Log.e("FirebaseRepository", "Error marking attendance", e)

            "ERROR"

        }

    }

}

