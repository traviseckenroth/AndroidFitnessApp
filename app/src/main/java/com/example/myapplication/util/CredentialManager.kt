package com.example.myapplication.util

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CredentialManager @Inject constructor(
    @ApplicationContext context: Context
) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences = EncryptedSharedPreferences.create(
        context,
        "secure_creds",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveCredentials(username: String, password: String) {
        sharedPreferences.edit()
            .putString("saved_username", username)
            .putString("saved_password", password)
            .apply()
    }

    fun getCredentials(): Pair<String?, String?> {
        val u = sharedPreferences.getString("saved_username", null)
        val p = sharedPreferences.getString("saved_password", null)
        return u to p
    }

    fun clearCredentials() {
        sharedPreferences.edit().clear().apply()
    }

    fun hasCredentials(): Boolean = sharedPreferences.contains("saved_username")
}
