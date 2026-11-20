package com.example.data

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.example.BuildConfig
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

data class GoogleSignInIdentity(
    val email: String,
    val displayName: String,
    val photoUrl: String,
    val idToken: String
)

sealed class CredentialSignInResult {
    data class Success(val identity: GoogleSignInIdentity) : CredentialSignInResult()
    data class Cancelled(val message: String) : CredentialSignInResult()
    data class Error(val message: String) : CredentialSignInResult()
}

sealed class GoogleScopeAuthResult {
    data class Authorized(val accessToken: String) : GoogleScopeAuthResult()
    data class NeedsUserConsent(val pendingIntent: PendingIntent) : GoogleScopeAuthResult()
    data class Error(val message: String) : GoogleScopeAuthResult()
}

object GoogleAuthHelper {

    val REQUIRED_SCOPES = listOf(
        Scope("https://www.googleapis.com/auth/drive.file"),
        Scope("https://www.googleapis.com/auth/spreadsheets"),
        Scope("https://www.googleapis.com/auth/userinfo.email"),
        Scope("https://www.googleapis.com/auth/userinfo.profile")
    )

    suspend fun signInWithCredentialManager(activity: Activity): CredentialSignInResult {
        val clientId = BuildConfig.GOOGLE_OAUTH_CLIENT_ID
        if (clientId.isBlank()) {
            return CredentialSignInResult.Error(
                "Konfigurasi Google OAuth Client ID belum tersedia. Pastikan firebase-applet-config.json sudah terpasang."
            )
        }

        val credentialManager = CredentialManager.create(activity)

        val primaryResult = attemptSignInWithOption(
            activity = activity,
            credentialManager = credentialManager,
            request = GetCredentialRequest.Builder()
                .addCredentialOption(
                    GetSignInWithGoogleOption.Builder(clientId).build()
                )
                .build()
        )

        if (primaryResult !is CredentialSignInResult.Error || !primaryResult.message.startsWith("NO_CREDENTIAL")) {
            return primaryResult
        }

        val fallbackOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(clientId)
            .setAutoSelectEnabled(false)
            .build()

        return attemptSignInWithOption(
            activity = activity,
            credentialManager = credentialManager,
            request = GetCredentialRequest.Builder()
                .addCredentialOption(fallbackOption)
                .build()
        )
    }

    private suspend fun attemptSignInWithOption(
        activity: Activity,
        credentialManager: CredentialManager,
        request: GetCredentialRequest
    ): CredentialSignInResult {
        return try {
            val result = credentialManager.getCredential(
                context = activity,
                request = request
            )
            val credential = result.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleId = GoogleIdTokenCredential.createFrom(credential.data)
                CredentialSignInResult.Success(
                    GoogleSignInIdentity(
                        email = googleId.id,
                        displayName = googleId.displayName ?: googleId.givenName ?: googleId.id.substringBefore("@"),
                        photoUrl = googleId.profilePictureUri?.toString().orEmpty(),
                        idToken = googleId.idToken
                    )
                )
            } else {
                CredentialSignInResult.Error("Tipe kredensial Google tidak dikenali.")
            }
        } catch (e: GetCredentialCancellationException) {
            CredentialSignInResult.Cancelled("Login Google dibatalkan oleh pengguna.")
        } catch (e: NoCredentialException) {
            CredentialSignInResult.Error("NO_CREDENTIAL: Tidak ada akun Google yang tersedia di perangkat.")
        } catch (e: GetCredentialException) {
            CredentialSignInResult.Error(e.errorMessage?.toString() ?: e.localizedMessage ?: "Gagal masuk dengan akun Google.")
        } catch (e: Exception) {
            CredentialSignInResult.Error(e.localizedMessage ?: "Terjadi kesalahan saat masuk ke akun Google.")
        }
    }

    suspend fun authorizeDriveAndSheets(activity: Activity): GoogleScopeAuthResult {
        val authorizationRequest = AuthorizationRequest.builder()
            .setRequestedScopes(REQUIRED_SCOPES)
            .build()

        return suspendCancellableCoroutine { continuation ->
            Identity.getAuthorizationClient(activity)
                .authorize(authorizationRequest)
                .addOnSuccessListener { authorizationResult ->
                    if (!continuation.isActive) return@addOnSuccessListener
                    val pendingIntent = authorizationResult.pendingIntent
                    val accessToken = authorizationResult.accessToken
                    when {
                        authorizationResult.hasResolution() && pendingIntent != null -> {
                            continuation.resume(GoogleScopeAuthResult.NeedsUserConsent(pendingIntent))
                        }
                        !accessToken.isNullOrBlank() -> {
                            continuation.resume(GoogleScopeAuthResult.Authorized(accessToken))
                        }
                        else -> {
                            continuation.resume(
                                GoogleScopeAuthResult.Error("Token akses Google Drive & Sheets tidak diterima.")
                            )
                        }
                    }
                }
                .addOnFailureListener { e ->
                    if (continuation.isActive) {
                        continuation.resume(
                            GoogleScopeAuthResult.Error(
                                e.localizedMessage ?: "Gagal meminta izin akses Google Drive & Google Sheets."
                            )
                        )
                    }
                }
        }
    }

    suspend fun invalidateAccessToken(context: Context, token: String?) {
        if (token.isNullOrBlank()) return
        withContext(Dispatchers.IO) {
            runCatching {
                GoogleAuthUtil.clearToken(context.applicationContext, token)
            }
        }
    }

    fun extractAccessTokenFromIntent(activity: Activity, data: Intent?): String? {
        return try {
            val result = Identity.getAuthorizationClient(activity)
                .getAuthorizationResultFromIntent(data)
            result.accessToken
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun clearCredentialState(context: Context) {
        try {
            CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
