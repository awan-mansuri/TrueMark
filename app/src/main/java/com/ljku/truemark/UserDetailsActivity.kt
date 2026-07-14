package com.ljku.truemark

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.google.firebase.database.*
import com.ljku.truemark.database.StudentProfileEntity
import com.ljku.truemark.database.UserEntity

class UserDetailsActivity : AppCompatActivity() {

    private lateinit var profileImage: ImageView
    private lateinit var userNameText: TextView
    private lateinit var roleBadge: MaterialButton
    private lateinit var emailText: TextView
    private lateinit var mobileText: TextView
    private lateinit var personalEmailText: TextView

    // Academic info views
    private lateinit var academicInfoHeader: TextView
    private lateinit var enrollmentCard: View
    private lateinit var enrollmentText: TextView
    private lateinit var rollCard: View
    private lateinit var rollText: TextView
    private lateinit var deptCard: View
    private lateinit var deptText: TextView
    private lateinit var semCard: View
    private lateinit var semText: TextView
    private lateinit var divCard: View
    private lateinit var divText: TextView
    private lateinit var batchCard: View
    private lateinit var batchText: TextView
    private lateinit var dobCard: View
    private lateinit var dobText: TextView

    private val firebaseDatabase = FirebaseDatabase.getInstance()
    private val usersRef = firebaseDatabase.getReference("users")
    private val profilesRef = firebaseDatabase.getReference("student_profiles")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_details)

        // Get user ID from intent
        val userId = intent.getIntExtra("USER_ID", -1)
        if (userId == -1) {
            Toast.makeText(this, "Invalid user", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        initViews()
        setupClickListeners()
        loadUserDetails(userId)
    }

    private fun initViews() {
        profileImage = findViewById(R.id.profileImage)
        userNameText = findViewById(R.id.userNameText)
        roleBadge = findViewById(R.id.roleBadge)
        emailText = findViewById(R.id.emailText)
        mobileText = findViewById(R.id.mobileText)
        personalEmailText = findViewById(R.id.personalEmailText)

        // Academic info views
        academicInfoHeader = findViewById(R.id.academicInfoHeader)
        enrollmentCard = findViewById(R.id.enrollmentCard)
        enrollmentText = findViewById(R.id.enrollmentText)
        rollCard = findViewById(R.id.rollCard)
        rollText = findViewById(R.id.rollText)
        deptCard = findViewById(R.id.deptCard)
        deptText = findViewById(R.id.deptText)
        semCard = findViewById(R.id.semCard)
        semText = findViewById(R.id.semText)
        divCard = findViewById(R.id.divCard)
        divText = findViewById(R.id.divText)
        batchCard = findViewById(R.id.batchCard)
        batchText = findViewById(R.id.batchText)
        dobCard = findViewById(R.id.dobCard)
        dobText = findViewById(R.id.dobText)
    }

    private fun setupClickListeners() {
        findViewById<ImageView>(R.id.backButton).setOnClickListener { finish() }
    }

    private fun loadUserDetails(userId: Int) {
        usersRef.child(userId.toString()).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val user = snapshot.getValue(UserEntity::class.java)
                if (user != null) {
                    displayUserDetails(user)

                    // If student, load academic details
                    if (user.role.equals("Student", ignoreCase = true)) {
                        loadStudentProfile(userId)
                    } else {
                        hideAcademicInfo()
                    }
                } else {
                    Toast.makeText(this@UserDetailsActivity, "User not found", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@UserDetailsActivity, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun displayUserDetails(user: UserEntity) {
        // Set name
        userNameText.text = user.name

        // Set role badge
        val role = user.role.uppercase()
        roleBadge.text = role
        roleBadge.setBackgroundColor(
            when {
                role.contains("STUDENT") -> getColor(R.color.colorPrimary)
                role.contains("FACULTY") -> getColor(R.color.darkBlue)
                else -> getColor(R.color.colorPrimary)
            }
        )

        // Load profile image
        if (!user.profileImage.isNullOrEmpty()) {
            Glide.with(this)
                .load(user.profileImage)
                .placeholder(R.drawable.ic_profile)
                .error(R.drawable.ic_profile)
                .circleCrop()
                .into(profileImage)
        } else {
            profileImage.setImageResource(R.drawable.ic_profile)
        }

        // Set personal info
        emailText.text = user.email
        mobileText.text = user.mobile ?: "Not provided"
        personalEmailText.text = user.personalEmail ?: "Not provided"
    }

    private fun loadStudentProfile(userId: Int) {
        profilesRef.child(userId.toString()).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val profile = snapshot.getValue(StudentProfileEntity::class.java)
                if (profile != null) {
                    displayAcademicInfo(profile)
                } else {
                    hideAcademicInfo()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                hideAcademicInfo()
            }
        })
    }

    private fun displayAcademicInfo(profile: StudentProfileEntity) {
        academicInfoHeader.visibility = View.VISIBLE

        enrollmentCard.visibility = View.VISIBLE
        enrollmentText.text = profile.enrollmentNo

        rollCard.visibility = View.VISIBLE
        rollText.text = profile.rollNumber

        deptCard.visibility = View.VISIBLE
        deptText.text = profile.department

        semCard.visibility = View.VISIBLE
        semText.text = profile.semester

        divCard.visibility = View.VISIBLE
        divText.text = profile.division

        batchCard.visibility = View.VISIBLE
        batchText.text = profile.batch

        dobCard.visibility = View.VISIBLE
        dobText.text = profile.dateOfBirth
    }

    private fun hideAcademicInfo() {
        academicInfoHeader.visibility = View.GONE
        enrollmentCard.visibility = View.GONE
        rollCard.visibility = View.GONE
        deptCard.visibility = View.GONE
        semCard.visibility = View.GONE
        divCard.visibility = View.GONE
        batchCard.visibility = View.GONE
        dobCard.visibility = View.GONE
    }
}
