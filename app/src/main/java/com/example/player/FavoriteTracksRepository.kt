package com.example.player

import android.content.Context
import androidx.annotation.Keep
import com.example.R
import com.example.auth.OperationType
import com.example.auth.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ServerTimestamp
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest

@Keep
@IgnoreExtraProperties
data class FirestoreFavoriteTrack(
    @DocumentId val id: String = "",
    val userId: String = "",
    val favoriteId: String = "",
    val trackId: String = "",
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val durationMs: Long = 0L,
    val mediaUri: String = "",
    val artworkUri: String = "",
    val importedTrack: Boolean = false,
    val onlineTrack: Boolean = false,
    val remixTrack: Boolean = false,
    val instaTrend: Boolean = false,
    val fullTrack: Boolean = true,
    val addedAtMs: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toMusicTrack(): MusicTrack {
        val resolvedId = trackId.ifBlank { favoriteId.ifBlank { id } }
        return MusicTrack(
            id = resolvedId,
            title = title.ifBlank { "آهنگ بدون نام" },
            artist = artist.ifBlank { "هنرمند ناشناس" },
            album = album,
            durationMs = durationMs.coerceAtLeast(0L),
            mediaUri = mediaUri,
            artworkUri = artworkUri,
            isFavorite = true,
            isImported = importedTrack,
            isOnline = onlineTrack,
            isRemix = remixTrack,
            isInstaTrend = instaTrend,
            isFullTrack = fullTrack,
            sourceLabel = ""
        )
    }
}

class FavoriteTracksRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun authenticatedUidOrNull(expectedUserId: String): String? {
        if (expectedUserId.isBlank() || expectedUserId == "guest") return null
        val authUid = runCatching { Firebase.auth.currentUser?.uid }.getOrNull()
        return authUid?.takeIf { it == expectedUserId }
    }

    fun observeFavorites(userId: String): Flow<List<MusicTrack>> {
        val verifiedUid = authenticatedUidOrNull(userId) ?: return flowOf(emptyList())
        val collectionPath = "$USERS_COLLECTION/$verifiedUid/$FAVORITES_SUBCOLLECTION"
        val query = db.collection(USERS_COLLECTION)
            .document(verifiedUid)
            .collection(FAVORITES_SUBCOLLECTION)
            .orderBy("addedAtMs", Query.Direction.DESCENDING)

        return query.snapshots(MetadataChanges.INCLUDE)
            .map { snapshot ->
                snapshot.documents.mapNotNull { doc ->
                    doc.toObject(
                        FirestoreFavoriteTrack::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )?.toMusicTrack()
                }
            }
            .catch { error ->
                handleFirestoreError(error, OperationType.LIST, collectionPath)
                throw error
            }
    }

    suspend fun saveFavorite(userId: String, track: MusicTrack): Result<Unit> {
        val verifiedUid = authenticatedUidOrNull(userId)
            ?: return Result.failure(IllegalStateException("کاربر وارد حساب گوگل نشده است."))
        val favoriteId = toFirestoreDocId(track.id)
        val docRef = db.collection(USERS_COLLECTION)
            .document(verifiedUid)
            .collection(FAVORITES_SUBCOLLECTION)
            .document(favoriteId)

        return try {
            val existingSnap = docRef.get().await()
            if (existingSnap.exists()) {
                docRef.update(buildUpdatePayload(track)).await()
            } else {
                docRef.set(buildCreatePayload(verifiedUid, favoriteId, track)).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun removeFavorite(userId: String, trackId: String): Result<Unit> {
        val verifiedUid = authenticatedUidOrNull(userId)
            ?: return Result.failure(IllegalStateException("کاربر وارد حساب گوگل نشده است."))
        val favoriteId = toFirestoreDocId(trackId)
        val docRef = db.collection(USERS_COLLECTION)
            .document(verifiedUid)
            .collection(FAVORITES_SUBCOLLECTION)
            .document(favoriteId)

        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun syncMissingFavorites(userId: String, tracks: List<MusicTrack>): Result<Int> {
        val verifiedUid = authenticatedUidOrNull(userId)
            ?: return Result.failure(IllegalStateException("کاربر وارد حساب گوگل نشده است."))
        if (tracks.isEmpty()) return Result.success(0)

        val colRef = db.collection(USERS_COLLECTION)
            .document(verifiedUid)
            .collection(FAVORITES_SUBCOLLECTION)

        return try {
            var uploadedCount = 0
            val baseTime = System.currentTimeMillis()
            tracks.chunked(400).forEach { chunk ->
                val batch = db.batch()
                chunk.forEachIndexed { idx, track ->
                    val favoriteId = toFirestoreDocId(track.id)
                    val docRef = colRef.document(favoriteId)
                    batch.set(
                        docRef,
                        buildCreatePayload(
                            userId = verifiedUid,
                            favoriteId = favoriteId,
                            track = track,
                            addedAtMs = (baseTime - idx).coerceAtLeast(0L)
                        )
                    )
                    uploadedCount++
                }
                batch.commit().await()
            }
            Result.success(uploadedCount)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, colRef.path)
            Result.failure(e)
        }
    }

    companion object {
        const val USERS_COLLECTION = "users"
        const val FAVORITES_SUBCOLLECTION = "favorites"
        private val VALID_DOC_ID_REGEX = Regex("^[a-zA-Z0-9_\\-]+$")

        fun toFirestoreDocId(rawTrackId: String): String {
            val trimmed = rawTrackId.trim()
            if (trimmed.length in 1..128 && VALID_DOC_ID_REGEX.matches(trimmed)) {
                return trimmed
            }
            val digest = MessageDigest.getInstance("SHA-256")
                .digest((trimmed.ifEmpty { "empty" }).toByteArray(Charsets.UTF_8))
            val hex = digest.joinToString("") { "%02x".format(it) }
            return "fav_${hex.take(64)}"
        }

        fun buildCreatePayload(
            userId: String,
            favoriteId: String,
            track: MusicTrack,
            addedAtMs: Long = System.currentTimeMillis()
        ): Map<String, Any> = mapOf(
            "userId" to userId,
            "favoriteId" to favoriteId,
            "trackId" to track.id.ifBlank { favoriteId }.take(512),
            "title" to track.title.ifBlank { "آهنگ بدون نام" }.take(300),
            "artist" to track.artist.ifBlank { "هنرمند ناشناس" }.take(200),
            "album" to track.album.take(200),
            "durationMs" to track.durationMs.coerceAtLeast(0L),
            "mediaUri" to track.mediaUri.take(2048),
            "artworkUri" to track.artworkUri.take(2048),
            "importedTrack" to track.isImported,
            "onlineTrack" to track.isOnline,
            "remixTrack" to track.isRemix,
            "instaTrend" to track.isInstaTrend,
            "fullTrack" to track.isFullTrack,
            "addedAtMs" to addedAtMs.coerceAtLeast(0L),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        fun buildUpdatePayload(track: MusicTrack): Map<String, Any> = mapOf(
            "title" to track.title.ifBlank { "آهنگ بدون نام" }.take(300),
            "artist" to track.artist.ifBlank { "هنرمند ناشناس" }.take(200),
            "album" to track.album.take(200),
            "durationMs" to track.durationMs.coerceAtLeast(0L),
            "mediaUri" to track.mediaUri.take(2048),
            "artworkUri" to track.artworkUri.take(2048),
            "importedTrack" to track.isImported,
            "onlineTrack" to track.isOnline,
            "remixTrack" to track.isRemix,
            "instaTrend" to track.isInstaTrend,
            "fullTrack" to track.isFullTrack,
            "updatedAt" to FieldValue.serverTimestamp()
        )
    }
}
