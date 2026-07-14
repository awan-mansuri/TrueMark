package com.ljku.truemark

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.database.*
import com.ljku.truemark.database.AttendanceSessionEntity
import com.ljku.truemark.database.GroupEntity
import com.ljku.truemark.database.NotificationEntity
import com.ljku.truemark.database.UserEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HomeActivity : AppCompatActivity() {
    
    private lateinit var profileImageView: ImageView
    private lateinit var bottomNavigationView: BottomNavigationView
    private lateinit var sessionManager: SessionManager
    private var currentUser: UserEntity? = null
    private val firebaseDatabase = FirebaseDatabase.getInstance()
    private val notificationsRef = firebaseDatabase.getReference("notifications")
    private val sessionsRef = firebaseDatabase.getReference("attendance_sessions")
    private val groupsRef = firebaseDatabase.getReference("groups")
    
    private var notificationsListener: ValueEventListener? = null
    private var chatBadgeListener: ValueEventListener? = null
    private var profileImageListener: ValueEventListener? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sessionManager = SessionManager(applicationContext)
        currentUser = sessionManager.getLoggedInUser()

        if (currentUser?.role?.equals("Admin", ignoreCase = true) == true || 
            currentUser?.role?.equals("system_admin", ignoreCase = true) == true) {
            startActivity(Intent(this, AdminHomeActivity::class.java))
            finish()
            return
        }

        if (currentUser == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_home)

        val welcomeTextView: TextView = findViewById(R.id.welcomeTextView)
        val roleBadge: TextView = findViewById(R.id.roleBadge)
        val statusTextView: TextView = findViewById(R.id.statusTextView)
        val settingsIcon: ImageView = findViewById(R.id.settingsIcon)
        profileImageView = findViewById(R.id.profileImageView)
        
        val markAttendanceCard: View = findViewById(R.id.markAttendanceCard)
        val historyCard: View = findViewById(R.id.historyCard)
        val profileCard: View = findViewById(R.id.profileCard)
        val summaryCard: View = findViewById(R.id.summaryCard)
        
        bottomNavigationView = findViewById(R.id.bottomNavigationView)

        loadUserData(welcomeTextView, roleBadge)
        fetchLatestUserData()
        loadProfileImage()
        setupProfileImageListener()
        setupBottomNavigation()
        observeUnreadNotifications()

        profileImageView.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        settingsIcon.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        statusTextView.visibility = View.VISIBLE
        statusTextView.text = "Status: Logged in successfully"
        Handler(Looper.getMainLooper()).postDelayed({
            statusTextView.visibility = View.GONE
        }, 3000)

        markAttendanceCard.setOnClickListener {
            val user = currentUser ?: return@setOnClickListener
            if (user.role.equals("Faculty", ignoreCase = true)) {
                startActivity(Intent(this, FacultyAttendanceActivity::class.java))
            } else if (user.role.equals("Student", ignoreCase = true)) {
                startActivity(Intent(this, StudentAttendanceActivity::class.java))
            }
        }

        historyCard.setOnClickListener {
            startActivity(Intent(this, AttendanceHistoryActivity::class.java))
        }

        profileCard.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        summaryCard.setOnClickListener {
            startActivity(Intent(this, TotalAttendanceActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        bottomNavigationView.selectedItemId = R.id.nav_home
        
        currentUser = sessionManager.getLoggedInUser()
        if (currentUser != null) {
            val welcomeTextView: TextView = findViewById(R.id.welcomeTextView)
            val roleBadge: TextView = findViewById(R.id.roleBadge)
            loadUserData(welcomeTextView, roleBadge)
            loadProfileImage()
            
            // Refresh chat badge when returning to home screen
            refreshChatBadge()
        }
    }
    
    private fun refreshChatBadge() {
        // Clear the chat badge when returning to home screen
        // The badge will be updated again when new messages arrive
        val badge = bottomNavigationView.getOrCreateBadge(R.id.nav_chat)
        badge.isVisible = false
        badge.clearNumber()
    }
    
    private fun setupBottomNavigation() {
        bottomNavigationView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> true
                R.id.nav_chat -> {
                    startActivity(Intent(this, ChatListActivity::class.java))
                    true
                }
                R.id.nav_notifications -> {
                    startActivity(Intent(this, NotificationActivity::class.java))
                    true
                }
                R.id.nav_attendance -> {
                    val user = currentUser ?: return@setOnItemSelectedListener false
                    if (user.role.equals("Faculty", ignoreCase = true)) {
                        startActivity(Intent(this, FacultyAttendanceActivity::class.java))
                    } else if (user.role.equals("Student", ignoreCase = true)) {
                        startActivity(Intent(this, StudentAttendanceActivity::class.java))
                    }
                    true
                }
                R.id.nav_profile -> {
                    startActivity(Intent(this, ProfileActivity::class.java))
                    true
                }
                else -> false
            }
        }
    }

    private fun observeUnreadChatMessages() {
        val userId = currentUser?.id ?: return
        chatBadgeListener = groupsRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                var totalUnreadCount = 0
                val userGroups = mutableListOf<Int>()
                
                // First, get all groups the user belongs to
                for (groupSnapshot in snapshot.children) {
                    val group = groupSnapshot.getValue(GroupEntity::class.java)
                    if (group != null) {
                        // Check if user is a member of this group
                        val membersRef = groupsRef.child(group.id.toString()).child("members")
                        membersRef.addListenerForSingleValueEvent(object : ValueEventListener {
                            override fun onDataChange(membersSnapshot: DataSnapshot) {
                                var isMember = false
                                for (member in membersSnapshot.children) {
                                    val memberUserId = member.getValue(Int::class.java)
                                    if (memberUserId == userId) {
                                        isMember = true
                                        break
                                    }
                                }
                                
                                if (isMember) {
                                    // Count unread messages in this group
                                    val messagesRef = groupsRef.child(group.id.toString()).child("messages")
                                    messagesRef.addListenerForSingleValueEvent(object : ValueEventListener {
                                        override fun onDataChange(messagesSnapshot: DataSnapshot) {
                                            var groupUnreadCount = 0
                                            for (messageSnapshot in messagesSnapshot.children) {
                                                val senderId = messageSnapshot.child("senderId").getValue(Int::class.java) ?: -1
                                                if (senderId != userId) {
                                                    val isReadField = messageSnapshot.child("isRead").getValue(Boolean::class.java) ?: true
                                                    val readField = messageSnapshot.child("read").getValue(Boolean::class.java) ?: true
                                                    if (!isReadField && !readField) {
                                                        groupUnreadCount++
                                                    }
                                                }
                                            }
                                            
                                            // Update badge with total unread count
                                            val badge = bottomNavigationView.getOrCreateBadge(R.id.nav_chat)
                                            if (groupUnreadCount > 0) {
                                                badge.isVisible = true
                                                badge.number = groupUnreadCount
                                            } else {
                                                badge.isVisible = false
                                            }
                                        }
                                        override fun onCancelled(error: DatabaseError) {}
                                    })
                                }
                            }
                            override fun onCancelled(error: DatabaseError) {}
                        })
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun observeUnreadNotifications() {
        val userId = currentUser?.id ?: return
        notificationsListener = notificationsRef.child(userId.toString()).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                var unreadCount = 0
                for (child in snapshot.children) {
                    // Try both field names to handle Firebase serialization differences
                    val isReadField = child.child("isRead").getValue(Boolean::class.java) ?: true
                    val readField = child.child("read").getValue(Boolean::class.java) ?: true
                    
                    if (!isReadField || !readField) {
                        unreadCount++
                    }
                }
                val badge = bottomNavigationView.getOrCreateBadge(R.id.nav_notifications)
                if (unreadCount > 0) {
                    badge.isVisible = true
                    badge.number = unreadCount
                } else {
                    badge.isVisible = false
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun fetchLatestUserData() {
        val user = currentUser ?: return
        
        Log.d("ProfileImage", "Fetching latest user data from Firebase for user: ${user.id}")
        
        FirebaseDatabase.getInstance().getReference("users")
            .child(user.id.toString())
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val latestUser = snapshot.getValue(UserEntity::class.java)
                    if (latestUser != null) {
                        Log.d("ProfileImage", "Latest user data fetched from Firebase")
                        Log.d("ProfileImage", "Latest profile image: ${latestUser.profileImage?.take(50)}...")
                        
                        // Update the current user's data with latest from Firebase
                        sessionManager.saveUser(latestUser)
                        currentUser = latestUser
                        
                        // Update UI with latest data
                        loadUserData(findViewById(R.id.welcomeTextView), findViewById(R.id.roleBadge))
                        loadProfileImage()
                    } else {
                        Log.w("ProfileImage", "No user data found in Firebase for user: ${user.id}")
                    }
                }
                
                override fun onCancelled(error: DatabaseError) {
                    Log.e("ProfileImage", "Failed to fetch latest user data: ${error.message}")
                }
            })
    }

    private fun loadUserData(welcomeTextView: TextView, roleBadge: TextView) {
        currentUser?.let {
            welcomeTextView.text = "Welcome, ${it.name}"
            roleBadge.text = it.role.uppercase()
        }
    }

    private fun setupProfileImageListener() {
        val user = currentUser ?: return
        
        // Listen for complete user data changes in real-time
        profileImageListener = FirebaseDatabase.getInstance().getReference("users")
            .child(user.id.toString())
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val updatedUser = snapshot.getValue(UserEntity::class.java)
                    if (updatedUser != null) {
                        // Check if profile image changed
                        if (updatedUser.profileImage != user.profileImage) {
                            Log.d("ProfileImage", "Profile image updated from Firebase")
                            Log.d("ProfileImage", "Old: ${user.profileImage?.take(50)}...")
                            Log.d("ProfileImage", "New: ${updatedUser.profileImage?.take(50)}...")
                            
                            // Update the current user's data
                            sessionManager.saveUser(updatedUser)
                            currentUser = updatedUser
                            
                            // Reload the profile image
                            loadProfileImage()
                        }
                    }
                }
                override fun onCancelled(error: DatabaseError) {
                    Log.e("ProfileImage", "Failed to listen for profile changes: ${error.message}")
                }
            })
    }
    
    private fun loadProfileImage() {
        val user = currentUser ?: return
        
        if (!user.profileImage.isNullOrEmpty()) {
            try {
                Log.d("ProfileImage", "Loading profile image: ${user.profileImage.take(50)}...")
                
                // Handle base64 encoded images
                if (user.profileImage.startsWith("data:image/")) {
                    val base64Data = user.profileImage.substring(user.profileImage.indexOf(",") + 1)
                    val decodedBytes = Base64.decode(base64Data, Base64.DEFAULT)
                    
                    // Decode with options to handle different image formats
                    val options = BitmapFactory.Options().apply {
                        inJustDecodeBounds = true
                    }
                    BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size, options)
                    
                    // Calculate sample size for memory efficiency
                    options.inSampleSize = calculateInSampleSize(options, 200, 200)
                    options.inJustDecodeBounds = false
                    
                    val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size, options)
                    if (bitmap != null) {
                        Log.d("ProfileImage", "Base64 image decoded successfully: ${bitmap.width}x${bitmap.height}")
                        profileImageView.post {
                            profileImageView.setImageBitmap(bitmap)
                            styleProfileImage()
                        }
                    } else {
                        Log.w("ProfileImage", "Failed to decode base64 image")
                        profileImageView.post {
                            displayDefaultIcon()
                        }
                    }
                } else {
                    // Handle URI images (fallback for old images)
                    profileImageView.post {
                        try {
                            Log.d("ProfileImage", "Loading URI image: ${user.profileImage}")
                            profileImageView.setImageURI(Uri.parse(user.profileImage))
                            styleProfileImage()
                        } catch (e: Exception) {
                            Log.e("ProfileImage", "Failed to load URI image", e)
                            displayDefaultIcon()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("ProfileImage", "Error loading profile image", e)
                profileImageView.post {
                    displayDefaultIcon()
                }
            }
        } else {
            Log.d("ProfileImage", "No profile image found")
            profileImageView.post {
                displayDefaultIcon()
            }
        }
    }
    
    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1
        
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        
        return inSampleSize
    }

    private fun styleProfileImage() {
        profileImageView.scaleType = ImageView.ScaleType.CENTER_CROP
        profileImageView.setColorFilter(null)
        profileImageView.background = null
        profileImageView.setPadding(0, 0, 0, 0)
        profileImageView.post {
            profileImageView.clipToOutline = true
            profileImageView.outlineProvider = object : android.view.ViewOutlineProvider() {
                override fun getOutline(view: android.view.View, outline: android.graphics.Outline) {
                    outline.setOval(0, 0, view.width, view.height)
                }
            }
            profileImageView.invalidate() // Force redraw
        }
    }
    
    private fun displayDefaultIcon() {
        profileImageView.scaleType = ImageView.ScaleType.CENTER_INSIDE
        profileImageView.setImageResource(R.drawable.ic_profile)
        profileImageView.setColorFilter(ContextCompat.getColor(this, R.color.white))
        profileImageView.background = ContextCompat.getDrawable(this, R.drawable.circular_background)
        profileImageView.setPadding(12, 12, 12, 12)
        profileImageView.clipToOutline = false
        profileImageView.outlineProvider = null
    }

    override fun onDestroy() {
        super.onDestroy()
        notificationsListener?.let {
            currentUser?.let { user ->
                notificationsRef.child(user.id.toString()).removeEventListener(it)
            }
        }
        chatBadgeListener?.let {
            groupsRef.removeEventListener(it)
        }
        profileImageListener?.let {
            currentUser?.let { user ->
                FirebaseDatabase.getInstance().getReference("users")
                    .child(user.id.toString())
                    .child("profileImage")
                    .removeEventListener(it)
            }
        }
    }
}
