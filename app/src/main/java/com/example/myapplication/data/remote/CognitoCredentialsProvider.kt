package com.example.myapplication.data.remote

import aws.sdk.kotlin.services.cognitoidentity.CognitoIdentityClient
import aws.sdk.kotlin.services.cognitoidentity.model.GetCredentialsForIdentityRequest
import aws.sdk.kotlin.services.cognitoidentity.model.GetIdRequest
import aws.smithy.kotlin.runtime.auth.awscredentials.Credentials
import aws.smithy.kotlin.runtime.auth.awscredentials.CredentialsProvider
import aws.smithy.kotlin.runtime.collections.Attributes
import aws.smithy.kotlin.runtime.time.Instant
import com.example.myapplication.BuildConfig // Make sure to import this
import com.example.myapplication.data.repository.AuthRepository // Import your Repo

// Update constructor to take AuthRepository
class CognitoCredentialsProvider(
    private val authRepository: AuthRepository,
    private val identityPoolId: String,
    private val region: String
) : CredentialsProvider {

    override suspend fun resolve(attributes: Attributes): Credentials {
        val idToken = authRepository.getIdToken()
            ?: throw IllegalStateException("User not logged in! Cannot access Bedrock.")

        // THE KEY CHANGE: Map the User Pool to the Identity Pool
        // Format: cognito-idp.<region>.amazonaws.com/<user_pool_id>
        val providerKey = "cognito-idp.${region}.amazonaws.com/${BuildConfig.COGNITO_USER_POOL_ID}"
        val loginsMap = mapOf(providerKey to idToken)

        CognitoIdentityClient { region = this@CognitoCredentialsProvider.region }.use { cognito ->

            // 1. Get Identity ID (Authenticated)
            val idResponse = cognito.getId(GetIdRequest {
                identityPoolId = this@CognitoCredentialsProvider.identityPoolId
                logins = loginsMap // <--- PASS TOKEN HERE
            })
            val myIdentityId = idResponse.identityId

            // 2. Get AWS Credentials (Authenticated)
            val credsResponse = cognito.getCredentialsForIdentity(GetCredentialsForIdentityRequest {
                identityId = myIdentityId
                logins = loginsMap // <--- PASS TOKEN HERE
            })

            val rawCreds = credsResponse.credentials
                ?: throw IllegalStateException("No credentials returned")

            return Credentials(
                accessKeyId = rawCreds.accessKeyId ?: "",
                secretAccessKey = rawCreds.secretKey ?: "",
                sessionToken = rawCreds.sessionToken,
                expiration = rawCreds.expiration ?: Instant.now()
            )
        }
    }
}