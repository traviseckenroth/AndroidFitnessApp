package com.example.myapplication.util

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.SecureRandom
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DatabasePassphraseManager @Inject constructor(
    @ApplicationContext context: Context
) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences = EncryptedSharedPreferences.create(
        context,
        "database_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun getPassphrase(): String {
        val storedPassphrase = sharedPreferences.getString("db_passphrase", null)
        return if (storedPassphrase != null) {
            storedPassphrase
        } else {
            val newPassphrase = generatePassphrase()
            val encoded = Base64.getEncoder().encodeToString(newPassphrase)
            sharedPreferences.edit()
                .putString("db_passphrase", encoded)
                .apply()
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
