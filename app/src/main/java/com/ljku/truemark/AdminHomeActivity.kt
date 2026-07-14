package com.ljku.truemark

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.ljku.truemark.database.UserEntity

class AdminHomeActivity : AppCompatActivity() {

    private lateinit var sessionManager: SessionManager
    private var currentUser: UserEntity? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        sessionManager = SessionManager(applicationContext)
        currentUser = sessionManager.getLoggedInUser()

        if (currentUser == null) {
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            finish()
            return
        }

        setContentView(R.layout.activity_admin_home)
        
        val adminLogoutButton: ImageView = findViewById(R.id.adminLogoutButton)
        val addUserCard: CardView = findViewById(R.id.addUserCard)
        val viewStudentsCard: CardView = findViewById(R.id.viewStudentsCard)
        val manageUsersCard: CardView = findViewById(R.id.manageUsersCard)
        val manageGroupsCard: CardView = findViewById(R.id.manageGroupsCard)
        val manageAdminsCard: CardView = findViewById(R.id.manageAdminsCard)
        val viewProfileButton: Button = findViewById(R.id.viewProfileButton)
        val addAdminFab: FloatingActionButton = findViewById(R.id.addAdminFab)

        // Only System Admin can see "Add New Admin" FAB
        if (currentUser?.email?.equals("admin@truemark.com", ignoreCase = true) == true) {
            addAdminFab.visibility = View.VISIBLE
        } else {
            addAdminFab.visibility = View.GONE
        }

        // Only System Admin can see "Manage Admins"
        if (currentUser?.email?.equals("admin@truemark.com", ignoreCase = true) == true) {
            manageAdminsCard.visibility = View.VISIBLE
        } else {
            manageAdminsCard.visibility = View.GONE
        }

        refreshAdminData()

        addAdminFab.setOnClickListener { showActionMenu() }
        addUserCard.setOnClickListener { startActivity(Intent(this, AdminAddUserActivity::class.java)) }
        viewStudentsCard.setOnClickListener { startActivity(Intent(this, AdminViewStudentsActivity::class.java)) }
        manageUsersCard.setOnClickListener { startActivity(Intent(this, AdminViewUsersActivity::class.java)) }
        manageAdminsCard.setOnClickListener { startActivity(Intent(this, AdminManageAdminsActivity::class.java)) }
        manageGroupsCard.setOnClickListener { startActivity(Intent(this, ChatListActivity::class.java)) }
        viewProfileButton.setOnClickListener { startActivity(Intent(this, ProfileActivity::class.java)) }

        adminLogoutButton.setOnClickListener {
            sessionManager.logout()
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }

    private fun showActionMenu() {
        val isSystemAdmin = currentUser?.email?.equals("admin@truemark.com", ignoreCase = true) == true
        val options = if (isSystemAdmin) {
            arrayOf("Create a Group", "Add New Admin")
        } else {
            arrayOf("Create a Group")
        }
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Select Action")
        builder.setItems(options) { _, which ->
            when (options[which]) {
                "Create a Group" -> startActivity(Intent(this, CreateGroupActivity::class.java))
                "Add New Admin" -> startActivity(Intent(this, AdminAddAdminActivity::class.java))
            }
        }
        builder.show()
    }

    override fun onResume() {
        super.onResume()
        refreshAdminData()
    }

    private fun refreshAdminData() {
        currentUser = sessionManager.getLoggedInUser()
        val adminNameText: TextView = findViewById(R.id.adminNameText)
        val adminEmailText: TextView = findViewById(R.id.adminEmailText)

        currentUser?.let {
            adminNameText.text = it.name
            adminEmailText.text = it.email
        }
    }
}
