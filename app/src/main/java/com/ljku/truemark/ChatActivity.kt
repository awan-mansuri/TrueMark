package com.ljku.truemark

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.*
import com.ljku.truemark.database.GroupEntity
import com.ljku.truemark.database.MessageEntity
import com.ljku.truemark.database.UserEntity
import java.io.File
import java.io.FileOutputStream

class ChatActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var messageAdapter: MessageAdapter
    private lateinit var messageEditText: EditText
    private lateinit var sendBtn: View
    private lateinit var attachBtn: ImageView
    private lateinit var groupNameText: TextView
    private lateinit var groupStatusText: TextView
    private lateinit var backBtn: ImageView
    private lateinit var infoBtn: ImageView
    private lateinit var titleContainer: LinearLayout
    private lateinit var inputCard: View
    
    private lateinit var sessionManager: SessionManager
    private var currentUser: UserEntity? = null
    private var groupId: Int = -1
    private var groupName: String? = null
    private var currentGroup: GroupEntity? = null
    
    private lateinit var messagesDb: DatabaseReference
    private lateinit var groupDb: DatabaseReference
    private lateinit var groupsRef: DatabaseReference
    private var messagesListener: ValueEventListener? = null
    private var groupListener: ValueEventListener? = null

    private val pickMedia = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data
            if (uri != null) {
                uploadMedia(uri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        sessionManager = SessionManager(applicationContext)
        currentUser = sessionManager.getLoggedInUser()
        groupId = intent.getIntExtra("GROUP_ID", -1)
        groupName = intent.getStringExtra("GROUP_NAME")

        if (currentUser == null || groupId == -1) {
            finish()
            return
        }
        
        messagesDb = FirebaseDatabase.getInstance().getReference("groups").child(groupId.toString()).child("messages")
        groupDb = FirebaseDatabase.getInstance().getReference("groups").child(groupId.toString()).child("details")
        groupsRef = FirebaseDatabase.getInstance().getReference("groups")

        recyclerView = findViewById(R.id.messagesRecyclerView)
        messageEditText = findViewById(R.id.messageEditText)
        sendBtn = findViewById(R.id.sendBtn)
        attachBtn = findViewById(R.id.attachBtn)
        groupNameText = findViewById(R.id.chatGroupName)
        groupStatusText = findViewById(R.id.groupStatus)
        backBtn = findViewById(R.id.backBtn)
        infoBtn = findViewById(R.id.groupInfoBtn)
        titleContainer = findViewById(R.id.groupTitleContainer)
        inputCard = findViewById(R.id.inputCard)

        groupNameText.text = groupName

        recyclerView.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        
        messageAdapter = MessageAdapter(
            emptyList(), 
            currentUser!!.id,
            currentUser!!.role, // Add user role
            onMessageLongClick = { message -> showDeleteMessageDialog(message) },
            onMediaClick = { message -> 
                if (!message.mediaUri.isNullOrEmpty()) {
                    try {
                        showImageOptionsDialog(message.mediaUri, message.mediaType ?: "IMAGE")
                    } catch (e: Exception) {
                        Toast.makeText(this@ChatActivity, "Error opening image: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
        recyclerView.adapter = messageAdapter

        startRealtimeListeners()
        setupClickListeners()
        
        // Mark all messages in this group as read when chat is opened
        markMessagesAsRead()
    }

    private fun setupClickListeners() {
        val openGroupInfo = {
            val intent = Intent(this, GroupDetailsActivity::class.java)
            intent.putExtra("GROUP_ID", groupId)
            startActivity(intent)
        }
        
        titleContainer.setOnClickListener { openGroupInfo() }
        infoBtn.setOnClickListener { openGroupInfo() }
        backBtn.setOnClickListener { finish() }
        
        sendBtn.setOnClickListener {
            val text = messageEditText.text.toString().trim()
            if (text.isNotEmpty()) sendMessage(text)
        }

        attachBtn.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT)
            intent.type = "image/*"
            pickMedia.launch(intent)
        }
    }

    private fun startRealtimeListeners() {
        messagesListener = messagesDb.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val messages = mutableListOf<MessageEntity>()
                for (child in snapshot.children) {
                    val msg = child.getValue(MessageEntity::class.java)
                    msg?.let { messages.add(it) }
                }
                // Sort messages by timestamp to show oldest first (like WhatsApp)
                val sortedMessages = messages.sortedBy { it.timestamp }
                messageAdapter.updateMessages(sortedMessages)
                if (messages.isNotEmpty()) {
                    recyclerView.smoothScrollToPosition(messages.size - 1)
                }
                
                // Mark new incoming messages as read if the user is currently in the chat
                markMessagesAsRead()
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        groupListener = groupDb.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val group = snapshot.getValue(GroupEntity::class.java)
                currentGroup = group
                if (group != null) {
                    // Total members counting: include all except "system_admin"
                    val usersRef = FirebaseDatabase.getInstance().getReference("users")
                    val memberIds = group.memberIds.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    
                    var actualMemberCount = 0
                    var processedCount = 0
                    
                    if (memberIds.isEmpty()) {
                        groupStatusText.text = "0 members • tap for info"
                    } else {
                        for (uid in memberIds) {
                            usersRef.child(uid).addListenerForSingleValueEvent(object : ValueEventListener {
                                override fun onDataChange(userSnapshot: DataSnapshot) {
                                    val user = userSnapshot.getValue(UserEntity::class.java)
                                    // Count all users except the ones with "system_admin" role
                                    if (user != null && !user.role.equals("system_admin", ignoreCase = true)) {
                                        actualMemberCount++
                                    }
                                    processedCount++
                                    if (processedCount == memberIds.size) {
                                        groupStatusText.text = "$actualMemberCount members • tap for info"
                                    }
                                }
                                override fun onCancelled(error: DatabaseError) {}
                            })
                        }
                    }
                    
                    val adminIds = group.adminIds.split(",").map { it.trim() }
                    val canMessage = !group.onlyAdminsCanMessage || adminIds.contains(currentUser!!.id.toString()) || currentUser!!.role == "ADMIN"
                    inputCard.visibility = if (canMessage) View.VISIBLE else View.GONE
                    sendBtn.visibility = if (canMessage) View.VISIBLE else View.GONE
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun markMessagesAsRead() {
        messagesDb.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (child in snapshot.children) {
                    val isRead = child.child("isRead").getValue(Boolean::class.java) ?: true
                    val read = child.child("read").getValue(Boolean::class.java) ?: true
                    val senderId = child.child("senderId").getValue(Int::class.java) ?: -1
                    if (senderId != currentUser!!.id && (!isRead || !read)) {
                        // Set both fields to true to ensure consistency
                        child.ref.child("isRead").setValue(true)
                        child.ref.child("read").setValue(true)
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun sendMessage(text: String, mediaUri: String? = null, mediaType: String? = null) {
        val messageId = messagesDb.push().key ?: return
        val message = MessageEntity(
            id = messageId.hashCode(), 
            groupId = groupId,
            senderId = currentUser!!.id,
            senderName = currentUser!!.name,
            messageText = text,
            mediaUri = mediaUri,
            mediaType = mediaType,
            timestamp = System.currentTimeMillis(),
            isRead = false // Note: Entity uses 'isRead' but we use 'read' in DB for consistency with Boolean patterns
        )
        val msgMap = mapOf(
            "id" to message.id,
            "groupId" to message.groupId,
            "senderId" to message.senderId,
            "senderName" to message.senderName,
            "messageText" to message.messageText,
            "mediaUri" to message.mediaUri,
            "mediaType" to message.mediaType,
            "timestamp" to message.timestamp,
            "isRead" to false,
            "read" to false
        )
        messagesDb.child(messageId).setValue(msgMap)
        messageEditText.setText("")
        
        // Send notification to group members using NotificationUtils
        val memberIds = currentGroup?.memberIds?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
        NotificationUtils.sendGroupMessageNotification(
            groupName = groupName ?: "Group",
            senderName = currentUser!!.name,
            message = text,
            groupId = groupId.toString(),
            senderId = currentUser!!.id.toString(),
            memberIds = memberIds
        )
    }

    private fun uploadMedia(uri: Uri) {
        try {
            // Convert URI to base64 string
            val inputStream = contentResolver.openInputStream(uri)
            val bytes = inputStream?.readBytes()
            inputStream?.close()
            
            if (bytes != null) {
                // Limit image size to avoid large messages
                val maxSize = 1024 * 1024 // 1MB
                val compressedBytes = if (bytes.size > maxSize) {
                    // Simple compression - in production, you'd want better image compression
                    bytes.copyOf(maxSize)
                } else {
                    bytes
                }
                
                val base64Image = Base64.encodeToString(compressedBytes, Base64.DEFAULT)
                sendMessage("", "data:image/jpeg;base64,$base64Image", "image")
                Toast.makeText(this, "Image attached successfully", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Failed to read image", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to process image: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showDeleteMessageDialog(message: MessageEntity) {
        // Check if current user has permission to delete messages
        val canDelete = currentUser?.role?.let { role ->
            role.equals("ADMIN", ignoreCase = true) || 
            role.equals("system_admin", ignoreCase = true) || 
            role.equals("FACULTY", ignoreCase = true)
        } ?: false

        if (!canDelete) {
            Toast.makeText(this, "Only admin and faculty can delete messages", Toast.LENGTH_SHORT).show()
            return
        }

        messagesDb.orderByChild("timestamp").equalTo(message.timestamp.toDouble())
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val key = snapshot.children.firstOrNull()?.key
                    if (key != null) {
                        AlertDialog.Builder(this@ChatActivity)
                            .setTitle("Delete Message?")
                            .setMessage("Are you sure you want to delete this message?")
                            .setPositiveButton("Delete") { _, _ -> 
                                messagesDb.child(key).removeValue()
                                Toast.makeText(this@ChatActivity, "Message deleted", Toast.LENGTH_SHORT).show()
                            }
                            .setNegativeButton("Cancel", null).show()
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    override fun onResume() {
        super.onResume()
        markMessagesAsRead()
    }

    override fun onDestroy() {
        super.onDestroy()
        messagesListener?.let { messagesDb.removeEventListener(it) }
        groupListener?.let { groupDb.removeEventListener(it) }
    }

    private fun showImageOptionsDialog(mediaUri: String, mediaType: String) {
        val options = arrayOf("Open With...", "Share", "Save to Device")
        
        AlertDialog.Builder(this)
            .setTitle("Image Options")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> openInExternalGallery(mediaUri)
                    1 -> shareImage(mediaUri)
                    2 -> saveImageToDevice(mediaUri)
                }
            }
            .show()
    }
    
    private fun openInExternalGallery(mediaUri: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW)
            
            if (mediaUri.startsWith("data:image/")) {
                // Handle base64 images - save to temp file first
                val tempFile = createTempImageFile(mediaUri)
                val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", tempFile)
                intent.setDataAndType(uri, "image/*")
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } else {
                // Handle URI images
                intent.setDataAndType(Uri.parse(mediaUri), "image/*")
            }
            
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(Intent.createChooser(intent, "Open Image With"))
        } catch (e: Exception) {
            Toast.makeText(this, "Error opening in gallery: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun shareImage(mediaUri: String) {
        try {
            val intent = Intent(Intent.ACTION_SEND)
            
            if (mediaUri.startsWith("data:image/")) {
                // Handle base64 images - save to temp file first
                val tempFile = createTempImageFile(mediaUri)
                val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", tempFile)
                intent.putExtra(Intent.EXTRA_STREAM, uri)
                intent.type = "image/*"
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } else {
                // Handle URI images
                intent.putExtra(Intent.EXTRA_STREAM, Uri.parse(mediaUri))
                intent.type = "image/*"
            }
            
            startActivity(Intent.createChooser(intent, "Share Image"))
        } catch (e: Exception) {
            Toast.makeText(this, "Error sharing image: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun saveImageToDevice(mediaUri: String) {
        try {
            if (mediaUri.startsWith("data:image/")) {
                // Handle base64 images
                val base64Data = mediaUri.substring(mediaUri.indexOf(",") + 1)
                val decodedBytes = Base64.decode(base64Data, Base64.DEFAULT)
                val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                
                if (bitmap != null) {
                    val filename = "image_${System.currentTimeMillis()}.jpg"
                    val contentValues = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                        put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
                    }
                    
                    val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                    if (uri != null) {
                        val outputStream = contentResolver.openOutputStream(uri)
                        outputStream?.use { stream ->
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
                        }
                        Toast.makeText(this, "Image saved to gallery", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                // Handle URI images - copy to gallery
                Toast.makeText(this, "URI images save not implemented yet", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error saving image: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun createTempImageFile(mediaUri: String): File {
        val base64Data = mediaUri.substring(mediaUri.indexOf(",") + 1)
        val decodedBytes = Base64.decode(base64Data, Base64.DEFAULT)
        val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
        
        val tempFile = File(cacheDir, "temp_image_${System.currentTimeMillis()}.jpg")
        val outputStream = FileOutputStream(tempFile)
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
        outputStream.close()
        
        return tempFile
    }
}
