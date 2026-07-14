package com.ljku.truemark

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.database.*
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import com.ljku.truemark.database.UserEntity

class EditProfileActivity : AppCompatActivity() {

    private lateinit var sessionManager: SessionManager
    private var targetUserId: Int = -1
    private var selectedImageUri: Uri? = null
    private lateinit var profileImageView: ImageView
    private lateinit var imageContainer: FrameLayout
    private val usersRef = FirebaseDatabase.getInstance().getReference("users")

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            val cachedUri = copyUriToCache(uri)
            if (cachedUri != null) {
                selectedImageUri = cachedUri
                profileImageView.setImageURI(cachedUri)
                Log.d("EditProfile", "Image cached: $cachedUri")
            } else {
                Toast.makeText(this, "Failed to process image", Toast.LENGTH_SHORT).show()
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
        setContentView(R.layout.activity_edit_profile)

        sessionManager = SessionManager(applicationContext)
        profileImageView = findViewById(R.id.editProfileImage)
        imageContainer = findViewById(R.id.editProfileImageContainer)
        
        targetUserId = intent.getIntExtra("TARGET_USER_ID", -1)
        val currentUser = sessionManager.getLoggedInUser()
        
        if (targetUserId == -1) {
            if (currentUser == null) {
                finish()
                return
            }
            targetUserId = currentUser.id
        }

        // Load target user data to check role
        usersRef.child(targetUserId.toString()).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val targetUser = snapshot.getValue(UserEntity::class.java)
                
                // Check if current user is System Admin
                val isSystemAdmin = currentUser?.email?.equals("admin@truemark.com", ignoreCase = true) == true
                
                // Check if current admin created this user (for non-system admins)
                val isCreator = targetUser?.createdBy == currentUser?.id
                
                // Show password field for System Admin OR if admin created this user
                val canEditPassword = isSystemAdmin || isCreator
                
                if (targetUser?.role?.equals("ADMIN", ignoreCase = true) == true) {
                    imageContainer.visibility = View.VISIBLE
                    imageContainer.setOnClickListener {
                        pickImage.launch("image/*")
                    }
                } else {
                    imageContainer.visibility = View.GONE
                }
                
                // Show password field if admin can edit password
                val passwordInputLayout: com.google.android.material.textfield.TextInputLayout = findViewById(R.id.passwordInputLayout)
                if (canEditPassword) {
                    passwordInputLayout.visibility = View.VISIBLE
                    
                    // Custom password visibility toggle
                    passwordInputLayout.setEndIconOnClickListener {
                        val passwordField: TextInputEditText = findViewById(R.id.editPassword)
                        if (passwordField.inputType == android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD) {
                            // Hide password, show closed eye
                            passwordField.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
                            passwordInputLayout.endIconDrawable = getDrawable(R.drawable.ic_eye_off)
                        } else {
                            // Show password, show open eye
                            passwordField.inputType = android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                            passwordInputLayout.endIconDrawable = getDrawable(R.drawable.ic_eye)
                        }
                        passwordField.setSelection(passwordField.text?.length ?: 0)
                    }
                }
                
                // Load profile data after role check
                loadProfileData(targetUser, canEditPassword)
            }
            
            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@EditProfileActivity, "Error loading user data", Toast.LENGTH_SHORT).show()
                finish()
            }
        })

        val nameEditText: TextInputEditText = findViewById(R.id.editName)
        val mobileEditText: TextInputEditText = findViewById(R.id.editMobileNumber)
        val passwordEditText: TextInputEditText = findViewById(R.id.editPassword)
        val saveButton: MaterialButton = findViewById(R.id.saveChangesButton)
        val backButton: ImageView = findViewById(R.id.backButton)

        backButton.setOnClickListener { finish() }

        saveButton.setOnClickListener {
            val newName = nameEditText.text.toString().trim()
            val newMobile = mobileEditText.text.toString().trim()
            val newPassword = passwordEditText.text.toString().trim()

            if (newName.isEmpty() || newMobile.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (newMobile.length != 10 || !newMobile.all { it.isDigit() }) {
                Toast.makeText(this, "Please enter a valid 10-digit mobile number", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Check permission and update
            usersRef.child(targetUserId.toString()).addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val userToUpdate = snapshot.getValue(UserEntity::class.java)
                    val isSystemAdmin = currentUser?.email?.equals("admin@truemark.com", ignoreCase = true) == true
                    val canEditPassword = isSystemAdmin || (userToUpdate?.createdBy == currentUser?.id)
                    updateUserProfile(newName, newMobile, if (canEditPassword) newPassword else null)
                }
                override fun onCancelled(error: DatabaseError) {}
            })
        }
    }

    private fun loadProfileData(targetUser: UserEntity?, canEditPassword: Boolean = false) {
        if (targetUser != null) {
            val nameField: TextInputEditText = findViewById(R.id.editName)
            val mobileField: TextInputEditText = findViewById(R.id.editMobileNumber)
            val passwordField: TextInputEditText = findViewById(R.id.editPassword)
            
            nameField.setText(targetUser.name)
            mobileField.setText(targetUser.mobile)
            
            // Load password if admin can edit it
            if (canEditPassword) {
                passwordField.setText(targetUser.password)
            }
            
            targetUser.profileImage?.let {
                try {
                    profileImageView.setImageURI(Uri.parse(it))
                    selectedImageUri = Uri.parse(it)
                } catch (e: Exception) {
                    profileImageView.setImageResource(R.drawable.ic_profile)
                }
            }
        }
    }

    private fun updateUserProfile(newName: String, newMobile: String, newPassword: String? = null) {
        usersRef.child(targetUserId.toString()).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val userToUpdate = snapshot.getValue(UserEntity::class.java)
                if (userToUpdate != null) {
                    // Update password only if provided (System Admin only), otherwise keep old
                    val updatedPassword = if (!newPassword.isNullOrEmpty()) newPassword else userToUpdate.password
                    
                    // Check if new image needs to be uploaded
                    if (selectedImageUri != null) {
                        Toast.makeText(this@EditProfileActivity, "Uploading image...", Toast.LENGTH_SHORT).show()
                        
                        uploadProfileImage(
                            userId = targetUserId,
                            imageUri = selectedImageUri!!,
                            onSuccess = { downloadUrl ->
                                val updatedUser = userToUpdate.copy(
                                    name = newName,
                                    mobile = newMobile,
                                    password = updatedPassword,
                                    profileImage = downloadUrl
                                )
                                saveUserToDatabase(updatedUser)
                            },
                            onFailure = { error ->
                                Log.e("EditProfile", "Image upload failed: $error")
                                Toast.makeText(this@EditProfileActivity, "Image upload failed: $error", Toast.LENGTH_LONG).show()
                                // Save without new image
                                val updatedUser = userToUpdate.copy(
                                    name = newName,
                                    mobile = newMobile,
                                    password = updatedPassword
                                )
                                saveUserToDatabase(updatedUser)
                            }
                        )
                    } else {
                        // No new image, update other fields only
                        val updatedUser = userToUpdate.copy(
                            name = newName,
                            mobile = newMobile,
                            password = updatedPassword
                        )
                        saveUserToDatabase(updatedUser)
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    /**
     * Saves user to Firebase Database and updates session
     */
    private fun saveUserToDatabase(updatedUser: UserEntity) {
        usersRef.child(targetUserId.toString()).setValue(updatedUser).addOnSuccessListener {
            val loggedInUser = sessionManager.getLoggedInUser()
            if (loggedInUser?.id == targetUserId) {
                sessionManager.saveUser(updatedUser)
            }
            Toast.makeText(this@EditProfileActivity, "Profile updated successfully", Toast.LENGTH_SHORT).show()
            finish()
        }.addOnFailureListener { error ->
            Log.e("EditProfile", "Failed to update profile: ${error.message}")
            Toast.makeText(this@EditProfileActivity, "Failed to update profile", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Uploads profile image to Firebase Storage using putFile
     * Works reliably with cached file URIs
     */
    private fun uploadProfileImage(
        userId: Int,
        imageUri: Uri,
        onSuccess: (String) -> Unit,
        onFailure: (String) -> Unit
    ) {
        val timestamp = System.currentTimeMillis()
        val uniqueFileName = "profile_${userId}_${timestamp}.jpg"
        val storagePath = "profile_images/$uniqueFileName"
        val storageRef = FirebaseStorage.getInstance().reference.child(storagePath)

        // Verify file has data
        val file = java.io.File(imageUri.path ?: "")
        if (!file.exists() || file.length() == 0L) {
            onFailure("Image file is empty")
            return
        }

        Log.d("EditProfile", "Uploading ${file.length()} bytes to: $storagePath")

        val metadata = StorageMetadata.Builder()
            .setContentType("image/jpeg")
            .build()

        storageRef.putFile(imageUri, metadata)
            .addOnSuccessListener { taskSnapshot ->
                Log.d("EditProfile", "Upload successful: ${taskSnapshot.totalByteCount} bytes")
                storageRef.downloadUrl
                    .addOnSuccessListener { uri ->
                        Log.d("EditProfile", "Download URL: $uri")
                        onSuccess(uri.toString())
                    }
                    .addOnFailureListener { e ->
                        Log.e("EditProfile", "Download URL failed: ${e.message}, falling back to base64")
                        val base64Str = uriToBase64(imageUri)
                        if (base64Str != null) {
                            onSuccess(base64Str)
                        } else {
                            onFailure("Failed to get download URL: ${e.message}")
                        }
                    }
            }
            .addOnFailureListener { exception ->
                Log.e("EditProfile", "Upload failed: ${exception.message}, falling back to base64")
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
                val maxSize = 2 * 1024 * 1024 // 2MB
                val compressedBytes = if (bytes.size > maxSize) {
                    bytes.copyOf(maxSize)
                } else {
                    bytes
                }
                
                val base64Image = android.util.Base64.encodeToString(compressedBytes, android.util.Base64.DEFAULT)
                "data:image/jpeg;base64,$base64Image"
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}
