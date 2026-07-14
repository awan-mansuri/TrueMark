package com.ljku.truemark

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import android.util.Log
import androidx.lifecycle.lifecycleScope
import com.google.firebase.database.FirebaseDatabase
import com.ljku.truemark.database.StudentProfileEntity
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.ljku.truemark.databinding.ActivityStudentScannerBinding
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class StudentScannerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStudentScannerBinding
    private val repository = FirebaseRepository()
    private var isScanning = true
    private var sessionId: String? = null

    private val requestCameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            startScanner()
        } else {
            Toast.makeText(this, "Camera permission is required to scan QR", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStudentScannerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sessionId = intent.getStringExtra("SESSION_ID")
        
        // Start scanner directly without verification code
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startScanner()
        } else {
            requestCameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startScanner() {
        binding.barcodeScanner.setStatusText("")
        binding.barcodeScanner.decodeContinuous(object : BarcodeCallback {
            override fun barcodeResult(result: BarcodeResult) {
                if (isScanning && result.text != null) {
                    val scannedData = result.text
                    // Pass scanned QR data to mark attendance
                    // The repository will verify if it's a valid session
                    markAttendance(scannedData)
                }
            }
        })
    }

    private fun markAttendance(id: String) {
        isScanning = false
        runOnUiThread {
            binding.scannerProgress.visibility = View.VISIBLE
            binding.tvScanStatus.text = "Processing Attendance..."
        }

        lifecycleScope.launch {
            val user = SessionManager(this@StudentScannerActivity).getLoggedInUser()
            var studentRollNo = "N/A"
            if (user != null) {
                try {
                    val profileSnapshot = FirebaseDatabase.getInstance()
                        .getReference("student_profiles")
                        .child(user.id.toString())
                        .get()
                        .await()
                    val profile = profileSnapshot.getValue(StudentProfileEntity::class.java)
                    studentRollNo = profile?.rollNumber?.takeIf { it.isNotBlank() } ?: "N/A"
                } catch (e: Exception) {
                    Log.w("StudentScanner", "Could not fetch roll number", e)
                }
            }
            Log.d("StudentScanner", "QR scanned: $id, Student: ${user?.id}")
            // Use markAttendance for QR data lookup (currentQrData)
            val result = repository.markAttendance(
                id,
                studentId = user?.id ?: 0,
                studentName = user?.name ?: "Unknown",
                studentRollNo = studentRollNo
            )
            Log.d("StudentScanner", "Mark attendance result: $result")
            
            when (result) {
                "SUCCESS" -> {
                    Toast.makeText(this@StudentScannerActivity, "Attendance Marked Successfully!", Toast.LENGTH_LONG).show()
                    finish()
                }
                "ALREADY_MARKED" -> {
                    Toast.makeText(this@StudentScannerActivity, "Attendance already marked.", Toast.LENGTH_LONG).show()
                    finish()
                }
                "SUBJECT_MISMATCH" -> {
                    Toast.makeText(this@StudentScannerActivity, "Invalid QR Code! This QR is for a different subject.", Toast.LENGTH_LONG).show()
                    isScanning = true
                    runOnUiThread {
                        binding.scannerProgress.visibility = View.GONE
                        binding.tvScanStatus.text = "Align QR Code in the window"
                    }
                }
                "QR_EXPIRED" -> {
                    Toast.makeText(this@StudentScannerActivity, "QR Code Expired! Please scan the new QR code.", Toast.LENGTH_LONG).show()
                    isScanning = true
                    runOnUiThread {
                        binding.scannerProgress.visibility = View.GONE
                        binding.tvScanStatus.text = "Align QR Code in the window"
                    }
                }
                "SESSION_NOT_FOUND", "SESSION_INACTIVE" -> {
                    Toast.makeText(this@StudentScannerActivity, "Invalid Session! Session not found or inactive.", Toast.LENGTH_LONG).show()
                    isScanning = true
                    runOnUiThread {
                        binding.scannerProgress.visibility = View.GONE
                        binding.tvScanStatus.text = "Align QR Code in the window"
                    }
                }
                "INVALID_QR_FORMAT" -> {
                    Toast.makeText(this@StudentScannerActivity, "Invalid QR Code Format!", Toast.LENGTH_SHORT).show()
                    isScanning = true
                    runOnUiThread {
                        binding.scannerProgress.visibility = View.GONE
                        binding.tvScanStatus.text = "Align QR Code in the window"
                    }
                }
                else -> {
                    Toast.makeText(this@StudentScannerActivity, "Invalid QR or Session Expired", Toast.LENGTH_SHORT).show()
                    isScanning = true
                    runOnUiThread {
                        binding.scannerProgress.visibility = View.GONE
                        binding.tvScanStatus.text = "Align QR Code in the window"
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            binding.barcodeScanner.resume()
        }
    }

    override fun onPause() {
        super.onPause()
        binding.barcodeScanner.pause()
    }
}
