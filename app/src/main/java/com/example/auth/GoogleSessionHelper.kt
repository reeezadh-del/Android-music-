package com.example.auth

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
import com.example.R
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

sealed interface DriveAuthOutcome {
    data class Authorized(val accessToken: String) : DriveAuthOutcome
    data class NeedsResolution(val pendingIntent: PendingIntent) : DriveAuthOutcome
    data class Failed(val message: String) : DriveAuthOutcome
}

internal fun com.google.firebase.auth.FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = com.google.firebase.auth.FirebaseAuth.AuthStateListener { firebaseAuth ->
        trySend(firebaseAuth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

object GoogleSessionHelper {

    private val DRIVE_SCOPES = listOf(
        Scope("https://www.googleapis.com/auth/drive.appdata"),
        Scope("https://www.googleapis.com/auth/drive.file")
    )

    private fun resolveWebClientId(context: Context): String {
        val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        return if (resId != 0) context.getString(resId) else ""
    }

    suspend fun attemptSilentSignIn(activity: Activity): Result<FirebaseUser?> {
        return try {
            val webClientId = resolveWebClientId(activity)
            if (webClientId.isBlank()) return Result.success(null)
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(true)
                .setServerClientId(webClientId)
                .setAutoSelectEnabled(true)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val credentialManager = CredentialManager.create(activity)
            val result = credentialManager.getCredential(activity, request)
            val credential = result.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleCred = GoogleIdTokenCredential.createFrom(credential.data)
                val authResult = Firebase.auth
                    .signInWithCredential(GoogleAuthProvider.getCredential(googleCred.idToken, null))
                    .await()
                Result.success(authResult.user)
            } else {
                Result.success(null)
            }
        } catch (_: NoCredentialException) {
            Result.success(null)
        } catch (_: GetCredentialCancellationException) {
            Result.success(null)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signInInteractiveWithGoogle(activity: Activity): Result<FirebaseUser> {
        return try {
            val webClientId = resolveWebClientId(activity)
            if (webClientId.isBlank()) {
                return Result.failure(IllegalStateException("پیکربندی ورود گوگل (google-services.json) یافت نشد."))
            }
            val signInOption = GetSignInWithGoogleOption.Builder(webClientId).build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(signInOption)
                .build()

            val credentialManager = CredentialManager.create(activity)
            val result = credentialManager.getCredential(activity, request)
            val credential = result.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleCred = GoogleIdTokenCredential.createFrom(credential.data)
                val authResult = Firebase.auth
                    .signInWithCredential(GoogleAuthProvider.getCredential(googleCred.idToken, null))
                    .await()
                val user = authResult.user
                    ?: return Result.failure(IllegalStateException("اطلاعات کاربر گوگل دریافت نشد."))
                Result.success(user)
            } else {
                Result.failure(IllegalStateException("نوع اعتبارنامه گوگل نامعتبر است."))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: GetCredentialCancellationException) {
            Result.failure(IllegalStateException("ورود با گوگل لغو شد."))
        } catch (e: GetCredentialException) {
            Result.failure(IllegalStateException(e.errorMessage?.toString() ?: "خطا در ورود با گوگل"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun authorizeDrive(activity: Activity): DriveAuthOutcome {
        return try {
            val authRequest = AuthorizationRequest.builder()
                .setRequestedScopes(DRIVE_SCOPES)
                .build()
            val result = Identity.getAuthorizationClient(activity)
                .authorize(authRequest)
                .await()

            val token = result.accessToken
            val resolution = result.pendingIntent
            when {
                !token.isNullOrBlank() -> DriveAuthOutcome.Authorized(token)
                result.hasResolution() && resolution != null -> DriveAuthOutcome.NeedsResolution(resolution)
                else -> DriveAuthOutcome.Failed("دسترسی به گوگل درایو صادر نشد.")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            DriveAuthOutcome.Failed(e.localizedMessage ?: "خطا در دریافت مجوز گوگل درایو")
        }
    }

    fun extractDriveTokenFromIntent(activity: Activity, data: Intent?): String? {
        if (data == null) return null
        return runCatching {
            Identity.getAuthorizationClient(activity)
                .getAuthorizationResultFromIntent(data)
                .accessToken
        }.getOrNull()
    }

    suspend fun signOutCompletely(context: Context) {
        withContext(NonCancellable) {
            runCatching {
                CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
            }
            runCatching {
                Identity.getSignInClient(context).signOut().await()
            }
            Firebase.auth.signOut()
        }
    }
}
