package com.example.myapplication.data.remote

import android.util.Log
import aws.sdk.kotlin.services.cognitoidentity.CognitoIdentityClient
import aws.sdk.kotlin.services.cognitoidentity.model.GetCredentialsForIdentityRequest
import aws.sdk.kotlin.services.cognitoidentity.model.GetIdRequest
import aws.sdk.kotlin.services.cognitoidentity.model.InvalidIdentityPoolConfigurationException
import aws.smithy.kotlin.runtime.auth.awscredentials.Credentials
import aws.smithy.kotlin.runtime.auth.awscredentials.CredentialsProvider
import aws.smithy.kotlin.runtime.collections.Attributes
import aws.smithy.kotlin.runtime.http.engine.okhttp.OkHttpEngine
import aws.smithy.kotlin.runtime.time.Instant
import com.example.myapplication.BuildConfig
import com.example.myapplication.data.repository.AuthRepository
import kotlin.time.Duration.Companion.seconds

class CognitoCredentialsProvider(
    private val authRepository: AuthRepository,
    private val identityPoolId: String,
    private val region: String
) : CredentialsProvider {

    private val cognitoClient by lazy {
        CognitoIdentityClient {
            region = this@CognitoCredentialsProvider.region
            httpClient = OkHttpEngine {
                connectTimeout = 20.seconds
                socketReadTimeout = 20.seconds
            }
        }
    }

    override suspend fun resolve(attributes: Attributes): Credentials {
        return try {
            val idToken = authRepository.getIdToken()
            val userPoolId = BuildConfig.COGNITO_USER_POOL_ID
            
            val loginsMap = if (!idToken.isNullOrBlank() && !userPoolId.isNullOrBlank() && userPoolId != "null") {
                val providerKey = "cognito-idp.${region}.amazonaws.com/$userPoolId"
                mapOf(providerKey to idToken)
            } else {
                Log.d("CognitoProvider", "No ID Token found, attempting unauthenticated access.")
                null
            }

            // 1. Get Identity ID
            val idResponse = cognitoClient.getId(GetIdRequest {
                identityPoolId = this@CognitoCredentialsProvider.identityPoolId
                logins = loginsMap
            })
            val myIdentityId = idResponse.identityId 
                ?: throw IllegalStateException("Identity ID was null")

            // 2. Get AWS Credentials
            val credsResponse = cognitoClient.getCredentialsForIdentity(GetCredentialsForIdentityRequest {
                identityId = myIdentityId
                logins = loginsMap
            })

            val rawCreds = credsResponse.credentials
                ?: throw IllegalStateException("No credentials returned from Cognito")

            Credentials(
                accessKeyId = rawCreds.accessKeyId ?: "",
                secretAccessKey = rawCreds.secretKey ?: "",
                sessionToken = rawCreds.sessionToken,
                expiration = rawCreds.expiration ?: Instant.now()
            )
        } catch (e: InvalidIdentityPoolConfigurationException) {
            Log.e("CognitoProvider", "CRITICAL: Identity Pool Configuration Error!")
            Log.e("CognitoProvider", "Please check AWS Console: Cognito -> Identity Pools -> ${identityPoolId} -> Edit.")
            Log.e("CognitoProvider", "Ensure both 'Authenticated role' and 'Unauthenticated role' are assigned and have valid Trust Relationships.")
            throw e
        } catch (e: Exception) {
            Log.e("CognitoProvider", "Failed to resolve credentials: ${e.message}", e)
            throw e
        }
    }
}
