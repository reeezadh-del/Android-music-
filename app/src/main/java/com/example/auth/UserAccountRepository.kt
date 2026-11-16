package com.example.auth

import android.content.Context
import com.example.R
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.Locale

class UsernameAlreadyTakenException(username: String) :
    IllegalStateException("نام کاربری «$username» قبلاً ثبت شده است.")

class InvalidUsernameException(message: String) : IllegalArgumentException(message)

class UserAccountRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    fun observeUserAccount(userId: String): Flow<UserAccount?> {
        val docRef = db.collection(USERS_COLLECTION).document(userId)
        return docRef.snapshots()
            .map { snapshot ->
                if (!snapshot.exists()) null
                else snapshot.toObject(
                    UserAccount::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .catch { error ->
                handleFirestoreError(error, OperationType.GET, docRef.path)
                throw error
            }
    }

    suspend fun getUserAccount(userId: String): Result<UserAccount?> {
        val docRef = db.collection(USERS_COLLECTION).document(userId)
        return try {
            val snapshot = docRef.get().await()
            val account = if (snapshot.exists()) {
                snapshot.toObject(
                    UserAccount::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            } else null
            Result.success(account)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun isUsernameAvailable(rawUsername: String): Result<Boolean> {
        val validationError = validateUsername(rawUsername)
        if (validationError != null) {
            return Result.failure(InvalidUsernameException(validationError))
        }
        val key = normalizeKey(rawUsername)
        val docRef = db.collection(USERNAMES_COLLECTION).document(key)
        return try {
            val snapshot = docRef.get().await()
            Result.success(!snapshot.exists())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun registerUsername(
        userId: String,
        rawUsername: String,
        driveFileId: String? = null
    ): Result<UserAccount> {
        val trimmed = rawUsername.trim()
        val validationError = validateUsername(trimmed)
        if (validationError != null) {
            return Result.failure(InvalidUsernameException(validationError))
        }

        val key = normalizeKey(trimmed)
        val usernameRef = db.collection(USERNAMES_COLLECTION).document(key)
        val userRef = db.collection(USERS_COLLECTION).document(userId)

        return try {
            // Pre-check availability to provide an immediate friendly message before committing batch
            val existingClaim = usernameRef.get().await()
            if (existingClaim.exists()) {
                val ownerUid = existingClaim.getString("userId")
                if (ownerUid != userId) {
                    return Result.failure(UsernameAlreadyTakenException(trimmed))
                }
            }

            val claimData = mapOf(
                "userId" to userId,
                "username" to trimmed,
                "usernameKey" to key,
                "createdAt" to FieldValue.serverTimestamp()
            )

            val accountData = buildMap<String, Any> {
                put("userId", userId)
                put("username", trimmed)
                put("usernameKey", key)
                if (!driveFileId.isNullOrBlank()) {
                    put("driveFileId", driveFileId)
                }
                put("createdAt", FieldValue.serverTimestamp())
                put("updatedAt", FieldValue.serverTimestamp())
            }

            val batch = db.batch()
            batch.set(usernameRef, claimData)
            batch.set(userRef, accountData)
            batch.commit().await()

            val createdSnapshot = userRef.get().await()
            val account = createdSnapshot.toObject(
                UserAccount::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: UserAccount(
                id = userId,
                userId = userId,
                username = trimmed,
                usernameKey = key,
                driveFileId = driveFileId
            )
            Result.success(account)
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirebaseFirestoreException) {
            handleFirestoreError(e, OperationType.WRITE, userRef.path)
            if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                val claimAfter = runCatching { usernameRef.get().await() }.getOrNull()
                if (claimAfter != null && claimAfter.exists() && claimAfter.getString("userId") != userId) {
                    return Result.failure(UsernameAlreadyTakenException(trimmed))
                }
            }
            Result.failure(e)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, userRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateDriveFileId(userId: String, driveFileId: String): Result<Unit> {
        val userRef = db.collection(USERS_COLLECTION).document(userId)
        return try {
            val payload = mapOf(
                "driveFileId" to driveFileId,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            userRef.update(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, userRef.path)
            Result.failure(e)
        }
    }

    companion object {
        const val MAX_USERNAME_LENGTH = 4
        const val USERS_COLLECTION = "users"
        const val USERNAMES_COLLECTION = "usernames"
        private val USERNAME_REGEX = Regex("^[a-zA-Z0-9_]{1,4}$")

        fun normalizeKey(raw: String): String =
            raw.trim().lowercase(Locale.ROOT)

        fun validateUsername(raw: String): String? {
            val trimmed = raw.trim()
            return when {
                trimmed.isEmpty() -> "نام کاربری نمی‌تواند خالی باشد."
                trimmed.length > MAX_USERNAME_LENGTH -> "نام کاربری حداکثر باید ۴ کاراکتر باشد."
                !USERNAME_REGEX.matches(trimmed) -> "فقط حروف انگلیسی، عدد یا _ مجاز است (حداکثر ۴ کاراکتر)."
                else -> null
            }
        }
    }
}
