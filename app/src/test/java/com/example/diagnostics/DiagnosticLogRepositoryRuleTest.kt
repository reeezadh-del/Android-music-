package com.example.diagnostics

import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class DiagnosticLogRepositoryRuleTest : FirestoreEmulatorTestBase() {

    private fun uniqueEmail(prefix: String): String =
        "$prefix-${UUID.randomUUID().toString().take(8)}@test.com"

    private fun sampleReport(reportId: String, isFatal: Boolean = true): DiagnosticReport {
        return DiagnosticReport(
            reportId = reportId,
            isFatal = isFatal,
            tag = "UNCAUGHT_CRASH",
            errorType = "java.lang.IllegalStateException",
            message = "Sample real-device error",
            stackTrace = "java.lang.IllegalStateException: Sample\n\tat com.example.MainActivity.onCreate(MainActivity.kt:42)",
            deviceInfo = "Samsung SM-S918B | Android 14 (API 34) | arm64-v8a",
            appVersion = "1.0 (1)",
            firebaseConfigured = true,
            timestampMs = 1700000123456L,
            acknowledged = !isFatal,
            syncedToCloud = false
        )
    }

    @Test
    fun uploadReport_andFetchRecent_succeedsForOwner() = runBlocking {
        val aliceUid = signInTestUser(uniqueEmail("diag-alice"))
        val repository = DiagnosticLogRepository(firestore)
        val reportId = "rep_${UUID.randomUUID().toString().replace("-", "").take(10)}"

        val uploadRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.uploadReport(aliceUid, sampleReport(reportId))
        }
        assertTrue("Expected uploadReport success, got ${uploadRes.exceptionOrNull()}", uploadRes.isSuccess)

        val listRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.fetchRecentCloudLogs(aliceUid)
        }
        assertTrue("Expected fetchRecentCloudLogs success, got ${listRes.exceptionOrNull()}", listRes.isSuccess)
        val logs = listRes.getOrThrow()
        assertTrue(logs.any { it.reportId == reportId && it.userId == aliceUid && it.isFatal })
    }

    @Test
    fun diagnosticLogs_crossUserReadAndWrite_failWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(uniqueEmail("diag-owner"))
        val repository = DiagnosticLogRepository(firestore)
        val reportId = "rep_${UUID.randomUUID().toString().replace("-", "").take(10)}"

        val ownerUpload = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.uploadReport(aliceUid, sampleReport(reportId))
        }
        assertTrue(ownerUpload.isSuccess)

        // Different authenticated user must be denied both list and create under Alice's path
        signInTestUser(uniqueEmail("diag-intruder"))

        val crossList = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.fetchRecentCloudLogs(aliceUid)
        }
        assertTrue(crossList.isFailure)
        val listEx = crossList.exceptionOrNull()?.unwrapFirestoreException()
        assertNotNull(listEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, listEx?.code)

        val crossUpload = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.uploadReport(aliceUid, sampleReport("rep_intruder_1"))
        }
        assertTrue(crossUpload.isFailure)
        val writeEx = crossUpload.exceptionOrNull()?.unwrapFirestoreException()
        assertNotNull(writeEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, writeEx?.code)
    }

    @Test
    fun diagnosticLogs_unauthenticated_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repository = DiagnosticLogRepository(firestore)

        val res = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.fetchRecentCloudLogs("some_uid_999")
        }
        assertTrue(res.isFailure)
        val firestoreEx = res.exceptionOrNull()?.unwrapFirestoreException()
        assertNotNull(firestoreEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx?.code)
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 5000L
    }
}
