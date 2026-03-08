package com.example.myapplication.util

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.SecureRandom
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DatabasePassphraseManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private var sharedPreferences = createSharedPreferences()

    private fun createSharedPreferences(): android.content.SharedPreferences {
        val fileName = "database_prefs"
        return try {
            EncryptedSharedPreferences.create(
                context,
                fileName,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e("PassphraseManager", "Error creating EncryptedSharedPreferences, resetting...", e)
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

    fun getPassphrase(): String {
        val storedPassphrase = try {
            sharedPreferences.getString("db_passphrase", null)
        } catch (e: Exception) {
            Log.e("PassphraseManager", "Error reading passphrase, resetting...", e)
            sharedPreferences = createSharedPreferences()
            null
        }

        return if (storedPassphrase != null) {
            storedPassphrase
        } else {
            val newPassphrase = generatePassphrase()
            val encoded = Base64.getEncoder().encodeToString(newPassphrase)
            sharedPreferences.edit {
                putString("db_passphrase", encoded)
            }
            encoded
        }
    }

    private fun generatePassphrase(): ByteArray {
        val random = SecureRandom()
        val passphrase = ByteArray(32)
        random.nextBytes(passphrase)
        return passphrase
    }
}
