package com.example.auth

import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class UserAccountRepositoryRuleTest : FirestoreEmulatorTestBase() {

    private fun uniqueShortUsername(): String {
        val raw = UUID.randomUUID().toString().replace("-", "").take(3)
        return "u$raw"
    }

    private fun uniqueEmail(prefix: String): String =
        "$prefix-${UUID.randomUUID().toString().take(8)}@test.com"

    @Test
    fun registerUsername_validMax4Chars_succeedsAndEnforcesCaseInsensitiveUniqueness() = runBlocking {
        val aliceUid = signInTestUser(uniqueEmail("alice"))
        val repository = UserAccountRepository(firestore)
        val handle = uniqueShortUsername().uppercase() // e.g. "U1A2"

        val regResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.registerUsername(aliceUid, handle, "drive_init_1")
        }
        assertTrue("Expected success but got ${regResult.exceptionOrNull()}", regResult.isSuccess)
        val account = regResult.getOrThrow()
        assertEquals(aliceUid, account.userId)
        assertEquals(handle, account.username)
        assertEquals(handle.lowercase(), account.usernameKey)
        assertEquals("drive_init_1", account.driveFileId)

        // Second user trying the same handle in lowercase must be blocked
        val bobUid = signInTestUser(uniqueEmail("bob"))
        val bobAvailability = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.isUsernameAvailable(handle.lowercase())
        }
        assertTrue(bobAvailability.isSuccess)
        assertFalse(bobAvailability.getOrThrow())

        val duplicateResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.registerUsername(bobUid, handle.lowercase())
        }
        assertTrue(duplicateResult.isFailure)
        assertTrue(duplicateResult.exceptionOrNull() is UsernameAlreadyTakenException)
    }

    @Test
    fun registerUsername_longerThan4Chars_isRejected() = runBlocking {
        val aliceUid = signInTestUser(uniqueEmail("longuser"))
        val repository = UserAccountRepository(firestore)

        val res = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.registerUsername(aliceUid, "abcde")
        }
        assertTrue(res.isFailure)
        assertTrue(res.exceptionOrNull() is InvalidUsernameException)
    }

    @Test
    fun updateDriveFileId_ownerSucceeds_andCrossUserReadFailsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(uniqueEmail("owner"))
        val repository = UserAccountRepository(firestore)
        val handle = uniqueShortUsername()

        val reg = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.registerUsername(aliceUid, handle)
        }
        assertTrue(reg.isSuccess)

        val updateRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.updateDriveFileId(aliceUid, "drive_sqlite_99")
        }
        assertTrue("Expected updateDriveFileId success, got ${updateRes.exceptionOrNull()}", updateRes.isSuccess)

        val observed = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeUserAccount(aliceUid).first { it?.driveFileId == "drive_sqlite_99" }
        }
        assertNotNull(observed)
        assertEquals("drive_sqlite_99", observed?.driveFileId)

        // Cross-user access fails with PERMISSION_DENIED
        signInTestUser(uniqueEmail("intruder"))
        val crossRead = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.getUserAccount(aliceUid)
        }
        assertTrue(crossRead.isFailure)
        val firestoreEx = crossRead.exceptionOrNull()?.unwrapFirestoreException()
        assertNotNull(firestoreEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx?.code)
    }

    @Test
    fun observeUserAccount_unauthenticated_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repository = UserAccountRepository(firestore)

        var caught: Exception? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.observeUserAccount("some_uid_123").first()
            }
        } catch (e: Exception) {
            caught = e
        }

        assertNotNull("Expected PERMISSION_DENIED exception for unauthenticated caller", caught)
        val firestoreEx = caught!!.unwrapFirestoreException()
        assertNotNull("Expected nested FirebaseFirestoreException, got $caught", firestoreEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx?.code)
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}
