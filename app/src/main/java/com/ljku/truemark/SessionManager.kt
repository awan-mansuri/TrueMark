package com.ljku.truemark

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.ljku.truemark.database.UserEntity

class SessionManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("TrueMarkPrefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun saveUser(user: UserEntity) {
        val userJson = gson.toJson(user)
        prefs.edit().putString("user_session", userJson).apply()
    }

    fun getLoggedInUser(): UserEntity? {
        val userJson = prefs.getString("user_session", null)
        return gson.fromJson(userJson, UserEntity::class.java)
    }

    fun isLoggedIn(): Boolean {
        return getLoggedInUser() != null
    }

    fun logout() {
        prefs.edit().remove("user_session").apply()
    }
}