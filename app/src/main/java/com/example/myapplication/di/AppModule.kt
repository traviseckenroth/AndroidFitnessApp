package com.example.myapplication.di

import android.content.Context
import android.os.StrictMode
import android.util.Log
import androidx.room.Room
import com.example.myapplication.BuildConfig
import com.example.myapplication.data.local.AppDatabase
import com.example.myapplication.data.local.MemoryDao
import com.example.myapplication.data.local.WorkoutDao
import com.example.myapplication.data.remote.CognitoCredentialsProvider
import com.example.myapplication.data.repository.AuthRepository
import com.example.myapplication.util.DatabasePassphraseManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
@Suppress("unused")
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
        passphraseManager: DatabasePassphraseManager
    ): AppDatabase {
        val dbName = "workout_database"
        
        // Fix: StrictMode policy violation; ~duration=68 ms: android.os.strictmode.DiskReadViolation
        // Initial database check, passphrase retrieval, and verification involve disk I/O 
        // which can happen on the main thread during DI.
        val oldPolicy = StrictMode.allowThreadDiskReads()

        try {
            val passphrase = passphraseManager.getPassphrase()
            
            // Ensure SQLCipher libraries are loaded before any DB operation
            SQLiteDatabase.loadLibs(context)
            
            val dbFile = context.getDatabasePath(dbName)
            if (dbFile.exists()) {
                try {
                    // Verify if the database can be opened with the current passphrase.
                    val db = SQLiteDatabase.openDatabase(
                        dbFile.absolutePath,
                        passphrase,
                        null,
                        SQLiteDatabase.OPEN_READONLY
                    )
                    try {
                        db.rawQuery("SELECT count(*) FROM sqlite_master", null).use { cursor ->
                            cursor.moveToFirst()
                        }
                    } finally {
                        db.close()
                    }
                } catch (e: Exception) {
                    Log.e("AppModule", "Failed to open database, it might be corrupted or unencrypted. Deleting...", e)
                    context.deleteDatabase(dbName)
                    context.getDatabasePath("$dbName-wal").delete()
                    context.getDatabasePath("$dbName-shm").delete()
                }
            }

            // SupportFactory can take a String or a ByteArray. Using ByteArray for broad compatibility.
            val factory = SupportFactory(passphrase.toByteArray())
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                dbName
            )
                .openHelperFactory(factory)
                .fallbackToDestructiveMigration(true)
                .build()
        } finally {
            StrictMode.setThreadPolicy(oldPolicy)
            // Note: oldWritePolicy might be same as oldPolicy depending on implementation, 
            // but setThreadPolicy restores the whole policy anyway.
            // StrictMode.allowThreadDiskWrites returns the old policy.
        }
    }

    @Provides
    @Singleton
    fun provideWorkoutDao(database: AppDatabase): WorkoutDao {
        return database.workoutDao()
    }

    @Provides
    @Singleton
    fun provideMemoryDao(database: AppDatabase): MemoryDao {
        return database.memoryDao()
    }

    @Provides
    @Singleton
    fun provideCognitoCredentialsProvider(
        authRepository: AuthRepository
    ): CognitoCredentialsProvider {
        return CognitoCredentialsProvider(
            authRepository = authRepository,
            identityPoolId = BuildConfig.COGNITO_IDENTITY_POOL_ID,
            region = BuildConfig.AWS_REGION
        )
    }
}
