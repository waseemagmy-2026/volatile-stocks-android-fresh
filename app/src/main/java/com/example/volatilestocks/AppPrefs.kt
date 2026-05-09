package com.example.volatilestocks

import android.content.Context
import android.content.SharedPreferences

class AppPrefs(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("volatile_stocks_prefs", Context.MODE_PRIVATE)

    fun saveApiKey(value: String) {
        prefs.edit().putString("api_key", value).apply()
    }

    fun getApiKey(): String {
        return prefs.getString("api_key", "") ?: ""
    }

    fun saveString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    fun getString(key: String, defaultValue: String = ""): String {
        return prefs.getString(key, defaultValue) ?: defaultValue
    }
}
