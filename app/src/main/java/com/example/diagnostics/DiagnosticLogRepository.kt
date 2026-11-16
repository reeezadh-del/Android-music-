package com.example.diagnostics

import android.content.Context
import com.example.R
import com.example.auth.OperationType
import com.example.auth.handleFirestoreError
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ServerTimestamp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

@IgnoreExtraProperties
data class CloudDiagnosticLog(
    @DocumentId val id: String = "",
    val userId: String = "",
    val reportId: String = "",
    @get:PropertyName("isFatal")
    @field:PropertyName("isFatal")
    val isFatal: Boolean = false,
    val tag: String = "",
    val errorType: String = "",
    val message: String = "",
    val stackTrace: String = "",
    val deviceInfo: String = "",
    val appVersion: String = "",
    val firebaseConfigured: Boolean = false,
    val timestampMs: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null
)

class DiagnosticLogRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    suspend fun uploadReport(userId: String, report: DiagnosticReport): Result<Unit> {
        val docRef = db.collection(USERS_COLLECTION)
            .document(userId)
            .collection(DIAGNOSTIC_LOGS_SUBCOLLECTION)
            .document(report.reportId)

        val payload = mapOf(
            "userId" to userId,
            "reportId" to report.reportId,
            "isFatal" to report.isFatal,
            "tag" to report.tag.ifBlank { "GENERAL" }.take(64),
            "errorType" to report.errorType.ifBlank { "DiagnosticEvent" }.take(160),
            "message" to report.message.take(1000),
            "stackTrace" to report.stackTrace.take(6000),
            "deviceInfo" to report.deviceInfo.ifBlank { "Unknown Android Device" }.take(300),
            "appVersion" to report.appVersion.ifBlank { "1.0" }.take(64),
            "firebaseConfigured" to report.firebaseConfigured,
            "timestampMs" to report.timestampMs.coerceAtLeast(0L),
            "createdAt" to FieldValue.serverTimestamp()
        )

        return try {
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun fetchRecentCloudLogs(userId: String, limitCount: Long = 20L): Result<List<CloudDiagnosticLog>> {
        val colRef = db.collection(USERS_COLLECTION)
            .document(userId)
            .collection(DIAGNOSTIC_LOGS_SUBCOLLECTION)
        return try {
            val snap = colRef
                .orderBy("timestampMs", Query.Direction.DESCENDING)
                .limit(limitCount)
                .get()
                .await()
            Result.success(snap.toObjects(CloudDiagnosticLog::class.java))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, colRef.path)
            Result.failure(e)
        }
    }

    companion object {
        private const val USERS_COLLECTION = "users"
        private const val DIAGNOSTIC_LOGS_SUBCOLLECTION = "diagnosticLogs"
    }
}
