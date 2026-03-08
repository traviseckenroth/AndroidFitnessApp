package com.example.myapplication.util

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CredentialManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private var sharedPreferences = createSharedPreferences()

    private fun createSharedPreferences(): android.content.SharedPreferences {
        val fileName = "secure_creds"
        return try {
            EncryptedSharedPreferences.create(
                context,
                fileName,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e("CredentialManager", "Error creating EncryptedSharedPreferences, resetting...", e)
            context.deleteSharedPreferences(fileName)
            EncryptedSharedPreferences.create(
                context,
                fileName,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }
    }

    fun saveCredentials(username: String, password: String) {
        // FIX: Using the KTX edit block (automatically applies changes)
        sharedPreferences.edit {
            putString("saved_username", username)
            putString("saved_password", password)
        }
    }

    fun getCredentials(): Pair<String?, String?> {
        val u = try { sharedPreferences.getString("saved_username", null) } catch (e: Exception) { null }
        val p = try { sharedPreferences.getString("saved_password", null) } catch (e: Exception) { null }
        return u to p
    }

    fun clearCredentials() {
        // FIX: Using the KTX edit block
        sharedPreferences.edit {
            clear()
        }
    }

    fun hasCredentials(): Boolean = try { sharedPreferences.contains("saved_username") } catch (e: Exception) { false }
}
