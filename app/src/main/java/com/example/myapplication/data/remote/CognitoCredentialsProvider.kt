package com.example.myapplication.data.remote

import aws.sdk.kotlin.services.cognitoidentity.CognitoIdentityClient
import aws.sdk.kotlin.services.cognitoidentity.model.GetCredentialsForIdentityRequest
import aws.sdk.kotlin.services.cognitoidentity.model.GetIdRequest
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

    override suspend fun resolve(attributes: Attributes): Credentials {
        val idToken = authRepository.getIdToken()
            ?: throw IllegalStateException("User not logged in! Cannot access AWS services.")

        val providerKey = "cognito-idp.${region}.amazonaws.com/${BuildConfig.COGNITO_USER_POOL_ID}"
        val loginsMap = mapOf(providerKey to idToken)

        // Use a configured engine with explicit timeouts to prevent launch hangs
        val engine = OkHttpEngine {
            connectTimeout = 20.seconds
            socketReadTimeout = 20.seconds
        }

        CognitoIdentityClient { 
            region = this@CognitoCredentialsProvider.region 
            httpClient = engine
        }.use { cognito ->

            // 1. Get Identity ID (Authenticated)
            val idResponse = cognito.getId(GetIdRequest {
                identityPoolId = this@CognitoCredentialsProvider.identityPoolId
                logins = loginsMap
            })
            val myIdentityId = idResponse.identityId

            // 2. Get AWS Credentials (Authenticated)
            val credsResponse = cognito.getCredentialsForIdentity(GetCredentialsForIdentityRequest {
                identityId = myIdentityId
                logins = loginsMap
            })

            val rawCreds = credsResponse.credentials
                ?: throw IllegalStateException("No credentials returned from Cognito")

            return Credentials(
                accessKeyId = rawCreds.accessKeyId ?: "",
                secretAccessKey = rawCreds.secretKey ?: "",
                sessionToken = rawCreds.sessionToken,
                expiration = rawCreds.expiration ?: Instant.now()
            )
        }
    }
}
