package com.ljku.truemark

import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Base64
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import com.ljku.truemark.database.StudentProfileEntity
import com.ljku.truemark.database.UserEntity

class AdminAddStudentDetailsActivity : AppCompatActivity() {

    private var studentId: Int = -1
    private lateinit var sessionManager: SessionManager
    private var isEditMode = false
    private var selectedImageUri: Uri? = null
    private lateinit var profileImageView: ImageView
    private lateinit var editImageIcon: ImageView
    private var currentStudent: UserEntity? = null
    private val firebaseDatabase = FirebaseDatabase.getInstance()
    private val firebaseProfiles = firebaseDatabase.getReference("student_profiles")
    private val firebaseUsers = firebaseDatabase.getReference("users")

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            val cachedUri = copyUriToCache(uri)
            if (cachedUri != null) {
                selectedImageUri = cachedUri
                profileImageView.setImageURI(cachedUri)
            }
        }
    }

    private fun copyUriToCache(sourceUri: Uri): Uri? {
        return try {
            val fileName = "temp_${System.currentTimeMillis()}.jpg"
            val cacheFile = java.io.File(cacheDir, fileName)
            contentResolver.openInputStream(sourceUri)?.use { input ->
                java.io.FileOutputStream(cacheFile).use { output ->
                    input.copyTo(output)
                }
            }
            if (cacheFile.length() > 0) Uri.fromFile(cacheFile) else null
        } catch (e: Exception) { null }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sessionManager = SessionManager(applicationContext)

        val currentUser = sessionManager.getLoggedInUser()
        if (currentUser?.role?.equals("ADMIN", ignoreCase = true) != true) {
            finish()
            return
        }

        setContentView(R.layout.activity_admin_add_student_details)

        studentId = intent.getIntExtra("STUDENT_ID", -1)
        val studentName = intent.getStringExtra("STUDENT_NAME") ?: "Student"

        if (studentId == -1) {
            finish()
            return
        }

        findViewById<TextView>(R.id.studentNameHeader).text = "Student: $studentName"
        profileImageView = findViewById(R.id.studentDetailProfileImage)
        editImageIcon = findViewById(R.id.editImageIcon)
        val imageContainer: FrameLayout = findViewById(R.id.studentDetailImageContainer)

        val enrollInput = findViewById<TextInputEditText>(R.id.enrollInput)
        val rollInput = findViewById<TextInputEditText>(R.id.rollInput)
        val deptInput = findViewById<TextInputEditText>(R.id.deptInput)
        val semInput = findViewById<TextInputEditText>(R.id.semInput)
        val divInput = findViewById<TextInputEditText>(R.id.divInput)
        val batchInput = findViewById<TextInputEditText>(R.id.batchInput)
        val dobInput = findViewById<TextInputEditText>(R.id.dobInput)
        val saveButton = findViewById<MaterialButton>(R.id.saveButton)
        val backButton = findViewById<TextView>(R.id.backButton)
        val backButtonTop = findViewById<ImageView>(R.id.backButtonTop)
        val editRecordButton = findViewById<MaterialButton>(R.id.editRecordButton)

        dobInput.addTextChangedListener(object : TextWatcher {
            private var isFormatting = false
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (isFormatting) return
                isFormatting = true
                val input = s.toString().replace("/", "")
                val formatted = StringBuilder()
                for (i in input.indices) {
                    formatted.append(input[i])
                    if ((i == 1 || i == 3) && i != input.lastIndex) formatted.append("/")
                }
                if (formatted.length > 10) s?.replace(0, s.length, formatted.substring(0, 10))
                else s?.replace(0, s.length, formatted.toString())
                isFormatting = false
            }
        })

        val inputs = listOf(rollInput, deptInput, semInput, divInput, batchInput, dobInput)

        fun toggleEdit(enabled: Boolean) {
            isEditMode = enabled
            inputs.forEach { it.isEnabled = enabled }
            enrollInput.isEnabled = false 
            saveButton.visibility = if (enabled) View.VISIBLE else View.GONE
            editRecordButton.visibility = if (enabled) View.GONE else View.VISIBLE
            editImageIcon.visibility = if (enabled) View.VISIBLE else View.GONE
            imageContainer.isClickable = enabled
        }

        toggleEdit(false)

        imageContainer.setOnClickListener {
            if (isEditMode) {
                pickImage.launch("image/*")
            }
        }

        editRecordButton.setOnClickListener { toggleEdit(true) }

        // Enable caching for faster loading
        firebaseProfiles.keepSynced(true)
        firebaseUsers.keepSynced(true)

        // Fetch student details from Firebase
        firebaseUsers.child(studentId.toString()).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                currentStudent = snapshot.getValue(UserEntity::class.java)
                currentStudent?.profileImage?.let { imageUrl ->
                    if (imageUrl.isNotEmpty()) {
                        // Use Glide to load image from URL (Firebase Storage or base64)
                        if (imageUrl.startsWith("data:image/")) {
                            // Handle base64 encoded images
                            try {
                                val base64Data = imageUrl.substring(imageUrl.indexOf(",") + 1)
                                val decodedBytes = Base64.decode(base64Data, Base64.DEFAULT)
                                val bitmap = android.graphics.BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                                if (bitmap != null) {
                                    profileImageView.setImageBitmap(bitmap)
                                } else {
                                    profileImageView.setImageResource(R.drawable.ic_profile)
                                }
                            } catch (e: Exception) {
                                profileImageView.setImageResource(R.drawable.ic_profile)
                            }
                        } else {
                            // Handle Firebase Storage URLs and other remote URLs using Glide
                            Glide.with(this@AdminAddStudentDetailsActivity)
                                .load(imageUrl)
                                .placeholder(R.drawable.ic_profile)
                                .error(R.drawable.ic_profile)
                                .circleCrop()
                                .into(profileImageView)
                        }
                    } else {
                        profileImageView.setImageResource(R.drawable.ic_profile)
                    }
                } ?: run {
                    profileImageView.setImageResource(R.drawable.ic_profile)
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        firebaseProfiles.child(studentId.toString()).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val profile = snapshot.getValue(StudentProfileEntity::class.java)
                profile?.let {
                    enrollInput.setText(it.enrollmentNo)
                    rollInput.setText(it.rollNumber)
                    deptInput.setText(it.department)
                    semInput.setText(it.semester)
                    divInput.setText(it.division)
                    batchInput.setText(it.batch)
                    dobInput.setText(it.dateOfBirth)
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        saveButton.setOnClickListener {
            val enroll = enrollInput.text.toString().trim()
            val roll = rollInput.text.toString().trim()
            val dept = deptInput.text.toString().trim()
            val sem = semInput.text.toString().trim()
            val div = divInput.text.toString().trim()
            val batch = batchInput.text.toString().trim()
            val dob = dobInput.text.toString().trim()

            if (enroll.isEmpty() || roll.isEmpty() || dept.isEmpty() || sem.isEmpty() || div.isEmpty() || batch.isEmpty() || dob.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Check if new image selected and needs upload
            if (selectedImageUri != null) {
                Toast.makeText(this, "Uploading image...", Toast.LENGTH_SHORT).show()
                uploadProfileImageToFirebase(
                    studentId = studentId,
                    imageUri = selectedImageUri!!,
                    onSuccess = { downloadUrl ->
                        // Update user with new image URL
                        currentStudent = currentStudent?.copy(profileImage = downloadUrl)
                        firebaseUsers.child(studentId.toString()).setValue(currentStudent)
                        
                        // Save profile data
                        saveStudentProfile(studentId, enroll, roll, dept, sem, div, batch, dob)
                    },
                    onFailure = { error ->
                        android.util.Log.e("AdminStudentDetails", "Image upload failed: $error")
                        Toast.makeText(this, "Image upload failed: $error", Toast.LENGTH_LONG).show()
                        // Still save profile without new image
                        saveStudentProfile(studentId, enroll, roll, dept, sem, div, batch, dob)
                    }
                )
            } else {
                // No new image, just save profile
                saveStudentProfile(studentId, enroll, roll, dept, sem, div, batch, dob)
            }
        }

        backButton.setOnClickListener { finish() }
        backButtonTop.setOnClickListener { finish() }
    }

    /**
     * Saves student profile to Firebase Database
     */
    private fun saveStudentProfile(studentId: Int, enroll: String, roll: String, dept: String, sem: String, div: String, batch: String, dob: String) {
        val newProfile = StudentProfileEntity(
            userId = studentId, enrollmentNo = enroll, rollNumber = roll,
            department = dept, semester = sem, division = div, batch = batch, dateOfBirth = dob
        )
        
        firebaseProfiles.child(studentId.toString()).setValue(newProfile).addOnSuccessListener {
            Toast.makeText(this@AdminAddStudentDetailsActivity, "Profile updated successfully", Toast.LENGTH_SHORT).show()
            // Exit edit mode and refresh UI
            isEditMode = false
            finish()
        }.addOnFailureListener {
            Toast.makeText(this@AdminAddStudentDetailsActivity, "Failed to update profile", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Uploads profile image to Firebase Storage using putFile
     * Works reliably with cached file URIs
     */
    private fun uploadProfileImageToFirebase(
        studentId: Int,
        imageUri: Uri,
        onSuccess: (String) -> Unit,
        onFailure: (String) -> Unit
    ) {
        val timestamp = System.currentTimeMillis()
        val uniqueFileName = "student_${studentId}_${timestamp}.jpg"
        val storagePath = "profile_images/$uniqueFileName"
        val storageRef = FirebaseStorage.getInstance().reference.child(storagePath)
        
        // Verify file has data
        val file = java.io.File(imageUri.path ?: "")
        if (!file.exists() || file.length() == 0L) {
            onFailure("Image file is empty")
            return
        }
        
        android.util.Log.d("AdminStudentDetails", "Uploading ${file.length()} bytes to: $storagePath")

        val metadata = StorageMetadata.Builder()
            .setContentType("image/jpeg")
            .build()

        storageRef.putFile(imageUri, metadata)
            .addOnSuccessListener { 
                storageRef.downloadUrl
                    .addOnSuccessListener { uri ->
                        android.util.Log.d("AdminStudentDetails", "Upload success: $uri")
                        onSuccess(uri.toString())
                    }
                    .addOnFailureListener { e ->
                        android.util.Log.e("AdminStudentDetails", "Download URL failed: ${e.message}, falling back to base64")
                        val base64Str = uriToBase64(imageUri)
                        if (base64Str != null) {
                            onSuccess(base64Str)
                        } else {
                            onFailure("Failed to get download URL: ${e.message}")
                        }
                    }
            }
            .addOnFailureListener { exception ->
                android.util.Log.e("AdminStudentDetails", "Upload failed: ${exception.message}, falling back to base64")
                val base64Str = uriToBase64(imageUri)
                if (base64Str != null) {
                    onSuccess(base64Str)
                } else {
                    onFailure("Upload failed: ${exception.message}")
                }
            }
    }

    private fun uriToBase64(uri: Uri): String? {
        return try {
            val inputStream = contentResolver.openInputStream(uri)
            val bytes = inputStream?.readBytes()
            inputStream?.close()
            
            if (bytes != null) {
                // Limit image size to avoid large database entries
                val maxSize = 2 * 1024 * 1024 // 2MB
                val compressedBytes = if (bytes.size > maxSize) {
                    bytes.copyOf(maxSize)
                } else {
                    bytes
                }
                
                val base64Image = Base64.encodeToString(compressedBytes, Base64.DEFAULT)
                "data:image/jpeg;base64,$base64Image"
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}
