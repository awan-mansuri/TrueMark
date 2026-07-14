package com.ljku.truemark

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.firebase.database.*
import com.ljku.truemark.database.UserEntity

class AdminViewUsersActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var userAdapter: UserAdapter
    private lateinit var noUsersText: TextView
    private lateinit var searchEditText: EditText
    private lateinit var roleFilterToggleGroup: MaterialButtonToggleGroup
    private lateinit var sortToggleGroup: MaterialButtonToggleGroup
    private lateinit var sessionManager: SessionManager
    
    private var allUsers: List<UserEntity> = emptyList()
    private var creatorNamesMap = mutableMapOf<Int, String>()
    private val firebaseUsers = FirebaseDatabase.getInstance().getReference("users")
    private var usersListener: ValueEventListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_view_users)

        sessionManager = SessionManager(this)
        recyclerView = findViewById(R.id.usersRecyclerView)
        noUsersText = findViewById(R.id.noUsersTextView)
        searchEditText = findViewById(R.id.searchEditText)
        roleFilterToggleGroup = findViewById(R.id.roleFilterToggleGroup)
        sortToggleGroup = findViewById(R.id.sortToggleGroup)

        // Make status bar transparent and extend header under it
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = ContextCompat.getColor(this, android.R.color.transparent)
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false

        // Back button click handler
        findViewById<ImageView>(R.id.backButton).setOnClickListener { finish() }

        // Handle status bar insets for notch/edge-to-edge screens
        val headerContentContainer = findViewById<LinearLayout>(R.id.headerContentContainer)
        ViewCompat.setOnApplyWindowInsetsListener(headerContentContainer) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars())
            view.setPadding(16.dpToPx(), insets.top + 16.dpToPx(), 16.dpToPx(), 16.dpToPx())
            windowInsets
        }

        recyclerView.layoutManager = LinearLayoutManager(this)
        val currentAdmin = sessionManager.getLoggedInUser()
        val isSystemAdmin = currentAdmin?.email?.equals("admin@truemark.com", ignoreCase = true) == true
        
        userAdapter = UserAdapter(emptyList(), 
            onEditClick = { user ->
                val intent = Intent(this, EditProfileActivity::class.java)
                intent.putExtra("TARGET_USER_ID", user.id)
                startActivity(intent)
            },
            onDeleteClick = { user -> showDeleteConfirmation(user) },
            onStudentDetailClick = { user ->
                val intent = Intent(this, AdminAddStudentDetailsActivity::class.java)
                intent.putExtra("STUDENT_ID", user.id)
                intent.putExtra("STUDENT_NAME", user.name)
                startActivity(intent)
            },
            onUserClick = { user ->
                // Open unified user details page for both Student and Faculty
                val intent = Intent(this, UserDetailsActivity::class.java)
                intent.putExtra("USER_ID", user.id)
                startActivity(intent)
            },
            showActions = true,
            disableRowClick = false,
            creatorNames = creatorNamesMap,
            currentAdminId = currentAdmin?.id,
            showAddedBy = isSystemAdmin
        )
        recyclerView.adapter = userAdapter

        setupRealtimeUserListener()

        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { applyFilters() }
            override fun afterTextChanged(s: Editable?) {}
        })

        roleFilterToggleGroup.addOnButtonCheckedListener { _, _, _ -> applyFilters() }
        sortToggleGroup.addOnButtonCheckedListener { _, _, _ -> applyFilters() }
    }

    private fun setupRealtimeUserListener() {
        val currentAdmin = sessionManager.getLoggedInUser()
        val isDefaultAdmin = currentAdmin?.email == "admin@truemark.com"

        // First, fetch all users to build creator names map
        usersListener = firebaseUsers.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // Build creator names map first
                creatorNamesMap.clear()
                for (child in snapshot.children) {
                    val user = child.getValue(UserEntity::class.java)
                    if (user != null) {
                        creatorNamesMap[user.id] = user.name
                    }
                }
                
                val usersList = mutableListOf<UserEntity>()
                for (child in snapshot.children) {
                    val user = child.getValue(UserEntity::class.java)
                    if (user != null) {
                        if (isDefaultAdmin || user.createdBy == currentAdmin?.id) {
                            if (!user.role.equals("admin", ignoreCase = true)) {
                                usersList.add(user)
                            }
                        }
                    }
                }
                
                allUsers = usersList
                applyFilters()
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@AdminViewUsersActivity, "Sync Error: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun showDeleteConfirmation(user: UserEntity) {
        AlertDialog.Builder(this)
            .setTitle("Delete User")
            .setMessage("Delete ${user.name}?")
            .setPositiveButton("Delete") { _, _ -> deleteUser(user) }
            .setNegativeButton("Cancel", null).show()
    }

    private fun deleteUser(user: UserEntity) {
        val studentProfilesRef = FirebaseDatabase.getInstance().getReference("student_profiles")
        
        firebaseUsers.child(user.id.toString()).removeValue().addOnSuccessListener {
            // Also delete student profile if exists
            studentProfilesRef.child(user.id.toString()).removeValue()
            Toast.makeText(this, "User deleted", Toast.LENGTH_SHORT).show()
        }.addOnFailureListener {
            Toast.makeText(this, "Delete failed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun applyFilters() {
        val query = searchEditText.text.toString().lowercase()
        val selectedRoleId = roleFilterToggleGroup.checkedButtonId
        val selectedRole = if (selectedRoleId == R.id.filterFacultyButton) "Faculty" else "Student"

        var filteredList = allUsers.filter { user ->
            val matchesRole = user.role.equals(selectedRole, ignoreCase = true)
            val matchesQuery = user.name.lowercase().contains(query) || user.email.lowercase().contains(query)
            matchesRole && matchesQuery
        }

        filteredList = if (sortToggleGroup.checkedButtonId == R.id.sortZToAButton) filteredList.sortedByDescending { it.name.lowercase() }
        else filteredList.sortedBy { it.name.lowercase() }

        userAdapter.updateUsers(filteredList)
        
        if (filteredList.isEmpty()) {
            noUsersText.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            noUsersText.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        usersListener?.let { firebaseUsers.removeEventListener(it) }
    }

    private fun Int.dpToPx(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }
}
