package com.ljku.truemark

import android.app.Application
import com.google.firebase.database.FirebaseDatabase

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Enable Firebase Offline Persistence
        // This allows the app to store data locally when offline 
        // and sync automatically when internet is back.
        try {
            FirebaseDatabase.getInstance().setPersistenceEnabled(true)
            
            // Keep specific nodes synced even when app is not active
            val usersRef = FirebaseDatabase.getInstance().getReference("users")
            val groupsRef = FirebaseDatabase.getInstance().getReference("groups")
            val profilesRef = FirebaseDatabase.getInstance().getReference("student_profiles")
            usersRef.keepSynced(true)
            groupsRef.keepSynced(true)
            profilesRef.keepSynced(true)
        } catch (e: Exception) {
            // Persistence must be set before any other usage of the database
            e.printStackTrace()
        }
    }
}
