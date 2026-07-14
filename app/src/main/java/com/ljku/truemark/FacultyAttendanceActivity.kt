package com.ljku.truemark

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView

class FacultyAttendanceActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_faculty_attendance)

        val backButton: ImageView = findViewById(R.id.backButton)
        val throughStudentsCard: MaterialCardView = findViewById(R.id.throughStudentsCard)
        val manualAttendanceCard: MaterialCardView = findViewById(R.id.manualAttendanceCard)

        backButton.setOnClickListener { finish() }

        throughStudentsCard.setOnClickListener {
            startActivity(Intent(this, FacultyAttendanceGroupsActivity::class.java))
        }

        manualAttendanceCard.setOnClickListener {
            startActivity(Intent(this, ManualAttendanceActivity::class.java))
        }
    }
}
