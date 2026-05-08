package com.example.volatilestocks

import android.content.Context

class AppPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("volatile_stocks_prefs", Context.MODE_PRIVATE)

    fun saveApiKey(value: String) {
        prefs.edit().putString("api_key", value).apply()
    }

    fun getApiKey(): String = prefs.getString("api_key", "") ?: ""

    fun saveString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    fun getString(key: String, defaultValue: String): String = prefs.getString(key, defaultValue) ?: defaultValue

    fun saveDouble(key: String, value: Double) {
        prefs.edit().putString(key, value.toString()).apply()
    }

    fun getDouble(key: String, defaultValue: Double): Double {
        return prefs.getString(key, defaultValue.toString())?.toDoubleOrNull() ?: defaultValue
    }
}
