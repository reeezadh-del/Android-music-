package com.example.auth

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.MutableContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.GetCredentialUnsupportedException
import androidx.credentials.exceptions.NoCredentialException
import com.example.diagnostics.CrashReporter
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.GetSignInIntentRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
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
import kotlinx.coroutines.withTimeoutOrNull
import java.security.MessageDigest

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

    internal const val EXPECTED_DEBUG_SHA1_HEX = "711537411f7ce2dd0750814c33c4bf703ecfcb32"

    private val DRIVE_SCOPES = listOf(
        Scope("https://www.googleapis.com/auth/drive.appdata"),
        Scope("https://www.googleapis.com/auth/drive.file")
    )

    fun isFirebaseConfigured(context: Context): Boolean {
        return runCatching {
            val appCtx = context.applicationContext
            if (FirebaseApp.getApps(appCtx).isNotEmpty()) true
            else FirebaseApp.initializeApp(appCtx) != null
        }.getOrDefault(false)
    }

    private fun resolveWebClientId(context: Context): String {
        val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        return if (resId != 0) context.getString(resId) else ""
    }

    @Suppress("DEPRECATION")
    fun getAppSigningSha1Hex(context: Context): String? {
        return runCatching {
            val pm = context.packageManager
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val pkgInfo = pm.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES
                )
                val signingInfo = pkgInfo.signingInfo
                if (signingInfo == null) {
                    null
                } else if (signingInfo.hasMultipleSigners()) {
                    signingInfo.apkContentsSigners
                } else {
                    signingInfo.signingCertificateHistory
                }
            } else {
                pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures
            }
            val certBytes = signatures?.firstOrNull()?.toByteArray() ?: return null
            val digest = MessageDigest.getInstance("SHA-1").digest(certBytes)
            digest.joinToString("") { "%02x".format(it) }
        }.getOrNull()
    }

    fun formatSha1WithColons(hex: String): String {
        val clean = hex.trim().lowercase().replace(":", "")
        if (clean.length < 2) return clean.uppercase()
        return clean.chunked(2).joinToString(":") { it.uppercase() }
    }

    fun isExpectedDebugSha1(runtimeSha1Hex: String?): Boolean {
        if (runtimeSha1Hex.isNullOrBlank()) return true
        return runtimeSha1Hex.trim().lowercase().replace(":", "") == EXPECTED_DEBUG_SHA1_HEX
    }

    fun buildCancellationErrorMessage(
        rawErrorMessage: String?,
        runtimeSha1Hex: String?
    ): String {
        if (!runtimeSha1Hex.isNullOrBlank() && !isExpectedDebugSha1(runtimeSha1Hex)) {
            val colonSha1 = formatSha1WithColons(runtimeSha1Hex)
            return "امضای این نسخه از برنامه (SHA-1) با کلید ثبت‌شده در فایل فایربیس مطابقت ندارد و گوگل پس از انتخاب حساب آن را لغو کرد.\n" +
                "لطفاً APK را با debug.keystore پروژه بسازید یا این SHA-1 را در Firebase ثبت کنید:\n$colonSha1"
        }
        val cleaned = rawErrorMessage?.trim().orEmpty()
        val isGenericUserCancel = cleaned.isEmpty() ||
            cleaned.equals("activity is cancelled by the user.", ignoreCase = true) ||
            cleaned.equals("activity is cancelled by the user", ignoreCase = true)
        return if (!isGenericUserCancel) {
            "ورود با گوگل لغو شد ($cleaned). می‌توانید از «روش جایگزین ورود گوگل» در زیر استفاده کنید."
        } else {
            "ورود با پنجره گوگل لغو شد یا تکمیل نگردید. اگر روی نام حساب زدید و پیام لغو دیدید، اتصال اینترنت/فیلترشکن را بررسی کرده و دکمه «تلاش با روش جایگزین گوگل» را بزنید."
        }
    }

    suspend fun attemptSilentSignIn(activity: Activity): Result<FirebaseUser?> {
        return try {
            if (!isFirebaseConfigured(activity)) return Result.success(null)
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
            val activityCtx = MutableContextWrapper(activity)
            val result = withTimeoutOrNull(4_000L) {
                credentialManager.getCredential(activityCtx, request)
            } ?: return Result.success(null)
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
        } catch (t: Throwable) {
            CrashReporter.recordNonFatal("AUTH_SILENT", t, context = activity)
            Result.failure(t)
        }
    }

    private suspend fun signInWithCredentialManagerOption(
        activity: Activity,
        request: GetCredentialRequest
    ): Result<FirebaseUser> {
        val credentialManager = CredentialManager.create(activity)
        val activityCtx = MutableContextWrapper(activity)
        val result = credentialManager.getCredential(activityCtx, request)
        val credential = result.credential
        return if (credential is CustomCredential &&
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
            val msg = "نوع اعتبارنامه گوگل نامعتبر است: ${credential.type}"
            CrashReporter.recordNonFatal("AUTH_CREDENTIAL_TYPE", message = msg, context = activity)
            Result.failure(IllegalStateException("نوع اعتبارنامه گوگل نامعتبر است."))
        }
    }

    suspend fun signInInteractiveWithGoogle(activity: Activity): Result<FirebaseUser> {
        val runtimeSha1 = getAppSigningSha1Hex(activity)
        return try {
            if (!isFirebaseConfigured(activity)) {
                val msg = "پیکربندی سرویس احراز هویت گوگل در این نسخه موجود نیست."
                CrashReporter.recordNonFatal("AUTH_CONFIG", message = msg, context = activity)
                return Result.failure(IllegalStateException(msg))
            }
            val webClientId = resolveWebClientId(activity)
            if (webClientId.isBlank()) {
                val msg = "پیکربندی ورود گوگل (google-services.json) یافت نشد."
                CrashReporter.recordNonFatal("AUTH_WEB_CLIENT_ID", message = msg, context = activity)
                return Result.failure(IllegalStateException(msg))
            }

            val signInOption = GetSignInWithGoogleOption.Builder(webClientId).build()
            val primaryRequest = GetCredentialRequest.Builder()
                .addCredentialOption(signInOption)
                .build()

            try {
                signInWithCredentialManagerOption(activity, primaryRequest)
            } catch (primaryUnavailable: GetCredentialException) {
                if (primaryUnavailable is NoCredentialException ||
                    primaryUnavailable is GetCredentialUnsupportedException ||
                    primaryUnavailable is GetCredentialProviderConfigurationException
                ) {
                    val googleIdOption = GetGoogleIdOption.Builder()
                        .setFilterByAuthorizedAccounts(false)
                        .setServerClientId(webClientId)
                        .setAutoSelectEnabled(false)
                        .build()
                    val fallbackRequest = GetCredentialRequest.Builder()
                        .addCredentialOption(googleIdOption)
                        .build()
                    signInWithCredentialManagerOption(activity, fallbackRequest)
                } else {
                    throw primaryUnavailable
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: GetCredentialCancellationException) {
            val rawMsg = e.errorMessage?.toString()
            CrashReporter.recordNonFatal(
                tag = "AUTH_CANCELLED",
                throwable = e,
                message = "type=${e.type} err=$rawMsg pkg=${activity.packageName} sha1=$runtimeSha1",
                context = activity
            )
            Result.failure(
                IllegalStateException(
                    buildCancellationErrorMessage(
                        rawErrorMessage = rawMsg,
                        runtimeSha1Hex = runtimeSha1
                    )
                )
            )
        } catch (e: GetCredentialException) {
            val rawMsg = e.errorMessage?.toString()
            CrashReporter.recordNonFatal(
                tag = "AUTH_CREDENTIAL",
                throwable = e,
                message = "type=${e.type} msg=$rawMsg pkg=${activity.packageName} sha1=$runtimeSha1",
                context = activity
            )
            val uiMsg = if (!runtimeSha1.isNullOrBlank() && !isExpectedDebugSha1(runtimeSha1)) {
                buildCancellationErrorMessage(rawMsg, runtimeSha1)
            } else {
                rawMsg ?: "خطا در ورود با گوگل"
            }
            Result.failure(IllegalStateException(uiMsg))
        } catch (t: Throwable) {
            CrashReporter.recordNonFatal("AUTH_SIGN_IN", t, context = activity)
            Result.failure(t)
        }
    }

    @Suppress("DEPRECATION")
    suspend fun buildLegacyGoogleSignInIntentSender(activity: Activity): Result<PendingIntent> {
        return try {
            if (!isFirebaseConfigured(activity)) {
                return Result.failure(IllegalStateException("پیکربندی Firebase در این نسخه فعال نیست."))
            }
            val webClientId = resolveWebClientId(activity)
            if (webClientId.isBlank()) {
                return Result.failure(IllegalStateException("شناسه کلاینت گوگل (default_web_client_id) یافت نشد."))
            }
            val req = GetSignInIntentRequest.builder()
                .setServerClientId(webClientId)
                .build()
            val pendingIntent = Identity.getSignInClient(activity)
                .getSignInIntent(req)
                .await()
            Result.success(pendingIntent)
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            CrashReporter.recordNonFatal("AUTH_LEGACY_INTENT", t, context = activity)
            Result.failure(
                IllegalStateException(
                    t.localizedMessage ?: "سرویس جایگزین ورود گوگل در دسترس نیست."
                )
            )
        }
    }

    @Suppress("DEPRECATION")
    suspend fun completeLegacyGoogleSignIn(activity: Activity, data: Intent?): Result<FirebaseUser> {
        val runtimeSha1 = getAppSigningSha1Hex(activity)
        return try {
            val credential = Identity.getSignInClient(activity).getSignInCredentialFromIntent(data)
            val idToken = credential.googleIdToken
            if (idToken.isNullOrBlank()) {
                val msg = "توکن احراز هویت گوگل دریافت نشد."
                CrashReporter.recordNonFatal("AUTH_LEGACY_EMPTY_TOKEN", message = msg, context = activity)
                return Result.failure(IllegalStateException(msg))
            }
            val authResult = Firebase.auth
                .signInWithCredential(GoogleAuthProvider.getCredential(idToken, null))
                .await()
            val user = authResult.user
                ?: return Result.failure(IllegalStateException("اطلاعات کاربر گوگل دریافت نشد."))
            Result.success(user)
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiException) {
            CrashReporter.recordNonFatal(
                tag = "AUTH_LEGACY_API",
                throwable = e,
                message = "code=${e.statusCode} pkg=${activity.packageName} sha1=$runtimeSha1 msg=${e.message}",
                context = activity
            )
            val uiMsg = when (e.statusCode) {
                CommonStatusCodes.DEVELOPER_ERROR -> {
                    val colonSha1 = runtimeSha1?.let(::formatSha1WithColons) ?: "نامشخص"
                    "خطای پیکربندی گوگل (کد ۱۰): SHA-1 این فایل APK با Firebase هم‌خوانی ندارد.\nSHA-1 فعلی: $colonSha1"
                }
                CommonStatusCodes.NETWORK_ERROR ->
                    "خطای شبکه در اتصال به سرورهای گوگل (کد ۷). اینترنت یا فیلترشکن را بررسی کنید."
                CommonStatusCodes.CANCELED ->
                    "ورود با گوگل توسط کاربر یا سیستم متوقف شد (کد ۱۶)."
                12501 -> "انتخاب حساب گوگل لغو شد (کد ۱۲۵۰۱)."
                12500 -> "خطای سرویس‌های Google Play هنگام ورود (کد ۱۲۵۰۰). Google Play Services را به‌روزرسانی کنید."
                else -> "ورود با گوگل ناموفق بود (کد ${e.statusCode}): ${e.localizedMessage ?: ""}"
            }
            Result.failure(IllegalStateException(uiMsg))
        } catch (t: Throwable) {
            CrashReporter.recordNonFatal("AUTH_LEGACY_COMPLETE", t, context = activity)
            Result.failure(t)
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
                else -> {
                    val msg = "دسترسی به گوگل درایو صادر نشد."
                    CrashReporter.recordNonFatal("DRIVE_AUTH", message = msg, context = activity)
                    DriveAuthOutcome.Failed(msg)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            CrashReporter.recordNonFatal("DRIVE_AUTH", t, context = activity)
            DriveAuthOutcome.Failed(t.localizedMessage ?: "خطا در دریافت مجوز گوگل درایو")
        }
    }

    fun extractDriveTokenFromIntent(activity: Activity, data: Intent?): String? {
        if (data == null) return null
        return runCatching {
            Identity.getAuthorizationClient(activity)
                .getAuthorizationResultFromIntent(data)
                .accessToken
        }.onFailure { err ->
            CrashReporter.recordNonFatal("DRIVE_INTENT_TOKEN", err, context = activity)
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
            runCatching {
                Firebase.auth.signOut()
            }
        }
    }
}
