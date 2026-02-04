package com.example.myapplication.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import aws.sdk.kotlin.services.cognitoidentityprovider.CognitoIdentityProviderClient
import aws.sdk.kotlin.services.cognitoidentityprovider.model.AttributeType
import aws.sdk.kotlin.services.cognitoidentityprovider.model.AuthFlowType
import aws.sdk.kotlin.services.cognitoidentityprovider.model.ChallengeNameType
import aws.sdk.kotlin.services.cognitoidentityprovider.model.ConfirmSignUpRequest
import aws.sdk.kotlin.services.cognitoidentityprovider.model.InitiateAuthRequest
import aws.sdk.kotlin.services.cognitoidentityprovider.model.RespondToAuthChallengeRequest
import aws.sdk.kotlin.services.cognitoidentityprovider.model.SignUpRequest
import com.example.myapplication.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.authDataStore by preferencesDataStore(name = "auth_prefs")

sealed class LoginResult {
    object Success : LoginResult()
    object NewPasswordRequired : LoginResult()
    data class Error(val message: String) : LoginResult()
}

sealed class SignUpResult {
    object Success : SignUpResult()
    object Confirmed : SignUpResult()
    data class Error(val message: String) : SignUpResult()
}

@Singleton
class AuthRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val ID_TOKEN_KEY = stringPreferencesKey("id_token")
    private val REFRESH_TOKEN_KEY = stringPreferencesKey("refresh_token") // <--- NEW

    private var currentSession: String? = null
    private var currentUsername: String? = null

    private val cognitoClient = CognitoIdentityProviderClient {
        region = BuildConfig.AWS_REGION
    }

    // --- AUTO LOGIN (KEEP SIGNED IN) ---
    suspend fun autoLogin(): Boolean {
        // 1. Get the saved Refresh Token
        val refreshToken = context.authDataStore.data.map { it[REFRESH_TOKEN_KEY] }.first()

        if (refreshToken.isNullOrBlank()) return false // No saved session

        return try {
            // 2. Ask AWS for a new ID Token using the Refresh Token
            val request = InitiateAuthRequest {
                authFlow = AuthFlowType.RefreshTokenAuth
                clientId = BuildConfig.COGNITO_CLIENT_ID
                authParameters = mapOf("REFRESH_TOKEN" to refreshToken)
            }

            val response = cognitoClient.initiateAuth(request)

            if (response.authenticationResult?.idToken != null) {
                // 3. Success! Save the new valid ID token.
                // Note: We keep the old Refresh Token unless AWS sends a new one.
                val newIdToken = response.authenticationResult?.idToken!!
                val newRefreshToken = response.authenticationResult?.refreshToken // Might be null

                saveTokens(newIdToken, newRefreshToken)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false // Token likely expired or revoked
        }
    }

    // --- SIGN IN ---
    suspend fun signIn(username: String, password: String): LoginResult {
        return try {
            val request = InitiateAuthRequest {
                authFlow = AuthFlowType.UserPasswordAuth
                clientId = BuildConfig.COGNITO_CLIENT_ID
                authParameters = mapOf("USERNAME" to username, "PASSWORD" to password)
            }
            val response = cognitoClient.initiateAuth(request)

            if (response.authenticationResult?.idToken != null) {
                // SAVE BOTH TOKENS
                saveTokens(
                    response.authenticationResult?.idToken!!,
                    response.authenticationResult?.refreshToken
                )
                return LoginResult.Success
            }
            if (response.challengeName == ChallengeNameType.NewPasswordRequired) {
                currentSession = response.session
                currentUsername = username
                return LoginResult.NewPasswordRequired
            }
            LoginResult.Error("Unknown login state")
        } catch (e: Exception) {
            LoginResult.Error(e.localizedMessage ?: "Login failed")
        }
    }

    // --- COMPLETE PASSWORD CHANGE ---
    suspend fun completeNewPasswordChallenge(newPassword: String): LoginResult {
        if (currentSession == null || currentUsername == null) return LoginResult.Error("Session expired")
        return try {
            val request = RespondToAuthChallengeRequest {
                challengeName = ChallengeNameType.NewPasswordRequired
                clientId = BuildConfig.COGNITO_CLIENT_ID
                session = currentSession
                challengeResponses = mapOf("USERNAME" to currentUsername!!, "NEW_PASSWORD" to newPassword)
            }
            val response = cognitoClient.respondToAuthChallenge(request)
            if (response.authenticationResult?.idToken != null) {
                saveTokens(
                    response.authenticationResult?.idToken!!,
                    response.authenticationResult?.refreshToken
                )
                LoginResult.Success
            } else {
                LoginResult.Error("Failed to set new password")
            }
        } catch (e: Exception) {
            LoginResult.Error(e.localizedMessage ?: "Error setting password")
        }
    }

    // --- SIGN UP ---
    suspend fun signUp(name: String, email: String, pass: String): SignUpResult {
        return try {
            val request = SignUpRequest {
                clientId = BuildConfig.COGNITO_CLIENT_ID
                username = email
                password = pass
                userAttributes = listOf(
                    AttributeType { this.name = "email"; this.value = email },
                    AttributeType { this.name = "name"; this.value = name }
                )
            }
            cognitoClient.signUp(request)
            SignUpResult.Success
        } catch (e: Exception) {
            SignUpResult.Error(e.localizedMessage ?: "Sign up failed")
        }
    }

    // --- CONFIRM CODE ---
    suspend fun confirmUser(email: String, code: String): SignUpResult {
        return try {
            val request = ConfirmSignUpRequest {
                clientId = BuildConfig.COGNITO_CLIENT_ID
                username = email
                confirmationCode = code
            }
            cognitoClient.confirmSignUp(request)
            SignUpResult.Confirmed
        } catch (e: Exception) {
            SignUpResult.Error(e.localizedMessage ?: "Confirmation failed")
        }
    }

    suspend fun getIdToken(): String? = context.authDataStore.data.map { it[ID_TOKEN_KEY] }.first()

    suspend fun logout() {
        context.authDataStore.edit { prefs ->
            prefs.remove(ID_TOKEN_KEY)
            prefs.remove(REFRESH_TOKEN_KEY) // Essential for "Keep Signed In" to stop
        }
    }

    private suspend fun saveTokens(idToken: String, refreshToken: String?) {
        context.authDataStore.edit { prefs ->
            prefs[ID_TOKEN_KEY] = idToken
            if (refreshToken != null) {
                prefs[REFRESH_TOKEN_KEY] = refreshToken
            }
        }
    }
}