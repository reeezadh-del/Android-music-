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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.EnumMap
import java.util.UUID

@Keep
@IgnoreExtraProperties
data class FirestoreTrackReaction(
    @DocumentId val id: String = "",
    val userId: String = "",
    val trackKey: String = "",
    val trackTitle: String = "",
    val emojiType: String = "",
    val authorUsername: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class FirestoreTrackComment(
    @DocumentId val id: String = "",
    val commentId: String = "",
    val trackKey: String = "",
    val trackTitle: String = "",
    val userId: String = "",
    val authorUsername: String = "",
    val text: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toUi(currentUid: String?): TrackCommentUi? {
        val resolvedId = commentId.ifBlank { id }
        if (resolvedId.isBlank() || text.isBlank()) return null
        val createdMillis = createdAt?.toDate()?.time ?: System.currentTimeMillis()
        return TrackCommentUi(
            commentId = resolvedId,
            trackKey = trackKey,
            userId = userId,
            authorUsername = authorUsername.ifBlank { "کاربر" },
            text = text,
            createdAtMs = createdMillis,
            isOwnComment = !currentUid.isNullOrBlank() && userId == currentUid
        )
    }
}

class TrackSocialRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun authenticatedUidOrNull(): String? {
        return runCatching { Firebase.auth.currentUser?.uid }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
    }

    private fun requireAuthenticatedUid(): String {
        return authenticatedUidOrNull()
            ?: throw IllegalStateException("برای ثبت ری‌اکت یا نظر لطفاً با حساب گوگل وارد شوید.")
    }

    fun observeTrackReactionSummary(trackKey: String): Flow<TrackReactionSummary> {
        val uid = authenticatedUidOrNull()
        if (uid.isNullOrBlank() || trackKey.isBlank()) {
            return flowOf(TrackReactionSummary(trackKey = trackKey))
        }
        val reactionsCol = db.collection(TRACKS_COLLECTION)
            .document(trackKey)
            .collection(REACTIONS_SUBCOLLECTION)
        return reactionsCol.snapshots(MetadataChanges.INCLUDE)
            .map { snapshot ->
                val list = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(
                        FirestoreTrackReaction::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                aggregateReactions(trackKey = trackKey, reactions = list, currentUid = uid)
            }
            .catch { error ->
                if (authenticatedUidOrNull() == uid) {
                    handleFirestoreError(error, OperationType.LIST, reactionsCol.path)
                }
                throw error
            }
    }

    fun observeTrackComments(trackKey: String): Flow<List<TrackCommentUi>> {
        val uid = authenticatedUidOrNull()
        if (uid.isNullOrBlank() || trackKey.isBlank()) {
            return flowOf(emptyList())
        }
        val commentsCol = db.collection(TRACKS_COLLECTION)
            .document(trackKey)
            .collection(COMMENTS_SUBCOLLECTION)
        return commentsCol
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(MAX_COMMENTS_PER_TRACK)
            .snapshots(MetadataChanges.INCLUDE)
            .map { snapshot ->
                snapshot.documents.mapNotNull { doc ->
                    doc.toObject(
                        FirestoreTrackComment::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )?.toUi(uid)
                }
            }
            .catch { error ->
                if (authenticatedUidOrNull() == uid) {
                    handleFirestoreError(error, OperationType.LIST, commentsCol.path)
                }
                throw error
            }
    }

    suspend fun fetchTrackSummary(track: MusicTrack): TrackReactionSummary? {
        val uid = authenticatedUidOrNull() ?: return null
        val trackKey = toTrackKey(track)
        val trackDoc = db.collection(TRACKS_COLLECTION).document(trackKey)
        return try {
            val reactionsSnap = trackDoc.collection(REACTIONS_SUBCOLLECTION).get().await()
            val commentsSnap = trackDoc.collection(COMMENTS_SUBCOLLECTION)
                .limit(MAX_COMMENTS_PER_TRACK)
                .get()
                .await()
            val reactions = reactionsSnap.documents.mapNotNull { doc ->
                doc.toObject(
                    FirestoreTrackReaction::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            aggregateReactions(
                trackKey = trackKey,
                reactions = reactions,
                currentUid = uid,
                commentCount = commentsSnap.size()
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, trackDoc.path)
            null
        }
    }

    suspend fun fetchSummariesForTracks(tracks: List<MusicTrack>): Map<String, TrackReactionSummary> {
        val uid = authenticatedUidOrNull() ?: return emptyMap()
        if (tracks.isEmpty()) return emptyMap()
        val subset = tracks.take(15)
        return try {
            coroutineScope {
                subset.map { track ->
                    async {
                        val summary = fetchTrackSummary(track)
                        if (summary != null) track.id to summary else null
                    }
                }.awaitAll().filterNotNull().toMap()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (authenticatedUidOrNull() == uid) {
                handleFirestoreError(e, OperationType.LIST, TRACKS_COLLECTION)
            }
            emptyMap()
        }
    }

    suspend fun toggleReaction(
        track: MusicTrack,
        reaction: TrackReactionType,
        authorUsername: String
    ): Result<TrackReactionType?> {
        var docPath = TRACKS_COLLECTION
        return try {
            val uid = requireAuthenticatedUid()
            val trackKey = toTrackKey(track)
            val docRef = db.collection(TRACKS_COLLECTION)
                .document(trackKey)
                .collection(REACTIONS_SUBCOLLECTION)
                .document(uid)
            docPath = docRef.path

            val existing = docRef.get().await()
            if (existing.exists()) {
                val currentType = TrackReactionType.fromRawOrNull(existing.getString("emojiType"))
                if (currentType == reaction) {
                    docRef.delete().await()
                    Result.success(null)
                } else {
                    docRef.update(
                        buildReactionUpdatePayload(
                            reaction = reaction,
                            trackTitle = track.title,
                            authorUsername = authorUsername
                        )
                    ).await()
                    Result.success(reaction)
                }
            } else {
                docRef.set(
                    buildReactionCreatePayload(
                        userId = uid,
                        trackKey = trackKey,
                        trackTitle = track.title,
                        reaction = reaction,
                        authorUsername = authorUsername
                    )
                ).await()
                Result.success(reaction)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun addComment(
        track: MusicTrack,
        rawText: String,
        authorUsername: String
    ): Result<TrackCommentUi> {
        var docPath = TRACKS_COLLECTION
        return try {
            val uid = requireAuthenticatedUid()
            val cleanedText = sanitizeCommentText(rawText)
                ?: return Result.failure(IllegalArgumentException("متن نظر نمی‌تواند خالی باشد."))
            val trackKey = toTrackKey(track)
            val commentId = generateCommentId()
            val safeAuthor = sanitizeAuthorLabel(authorUsername)
            val docRef = db.collection(TRACKS_COLLECTION)
                .document(trackKey)
                .collection(COMMENTS_SUBCOLLECTION)
                .document(commentId)
            docPath = docRef.path

            docRef.set(
                buildCommentCreatePayload(
                    commentId = commentId,
                    trackKey = trackKey,
                    trackTitle = track.title,
                    userId = uid,
                    authorUsername = safeAuthor,
                    text = cleanedText
                )
            ).await()

            Result.success(
                TrackCommentUi(
                    commentId = commentId,
                    trackKey = trackKey,
                    userId = uid,
                    authorUsername = safeAuthor,
                    text = cleanedText,
                    createdAtMs = System.currentTimeMillis(),
                    isOwnComment = true
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteComment(track: MusicTrack, commentId: String): Result<Unit> {
        var docPath = TRACKS_COLLECTION
        return try {
            requireAuthenticatedUid()
            require(commentId.isNotBlank()) { "شناسه نظر معتبر نیست." }
            val trackKey = toTrackKey(track)
            val docRef = db.collection(TRACKS_COLLECTION)
                .document(trackKey)
                .collection(COMMENTS_SUBCOLLECTION)
                .document(commentId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    companion object {
        const val TRACKS_COLLECTION = "tracks"
        const val REACTIONS_SUBCOLLECTION = "reactions"
        const val COMMENTS_SUBCOLLECTION = "comments"
        const val MAX_COMMENT_LENGTH = 500
        private const val MAX_COMMENTS_PER_TRACK = 100L

        fun toTrackKey(track: MusicTrack): String {
            return FavoriteTracksRepository.toFirestoreDocId(track.id)
        }

        fun sanitizeTrackTitle(rawTitle: String): String {
            return rawTitle.trim().ifBlank { "آهنگ بدون نام" }.take(300)
        }

        fun sanitizeAuthorLabel(rawAuthor: String): String {
            return rawAuthor.trim().ifBlank { "کاربر" }.take(32)
        }

        fun sanitizeCommentText(rawText: String): String? {
            val trimmed = rawText.trim()
            if (trimmed.isEmpty()) return null
            return trimmed.take(MAX_COMMENT_LENGTH)
        }

        fun generateCommentId(): String {
            return "cmt_${UUID.randomUUID().toString().replace("-", "").take(24)}"
        }

        fun aggregateReactions(
            trackKey: String,
            reactions: List<FirestoreTrackReaction>,
            currentUid: String?,
            commentCount: Int = 0
        ): TrackReactionSummary {
            val counts = EnumMap<TrackReactionType, Int>(TrackReactionType::class.java)
            var mine: TrackReactionType? = null
            reactions.forEach { reaction ->
                val type = TrackReactionType.fromRawOrNull(reaction.emojiType) ?: return@forEach
                counts[type] = (counts[type] ?: 0) + 1
                val docUser = reaction.userId.ifBlank { reaction.id }
                if (!currentUid.isNullOrBlank() && docUser == currentUid) {
                    mine = type
                }
            }
            return TrackReactionSummary(
                trackKey = trackKey,
                counts = counts.toMap(),
                userReaction = mine,
                commentCount = commentCount.coerceAtLeast(0)
            )
        }

        fun buildReactionCreatePayload(
            userId: String,
            trackKey: String,
            trackTitle: String,
            reaction: TrackReactionType,
            authorUsername: String
        ): Map<String, Any> = mapOf(
            "userId" to userId,
            "trackKey" to trackKey,
            "trackTitle" to sanitizeTrackTitle(trackTitle),
            "emojiType" to reaction.name,
            "authorUsername" to sanitizeAuthorLabel(authorUsername),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        fun buildReactionUpdatePayload(
            reaction: TrackReactionType,
            trackTitle: String,
            authorUsername: String
        ): Map<String, Any> = mapOf(
            "emojiType" to reaction.name,
            "trackTitle" to sanitizeTrackTitle(trackTitle),
            "authorUsername" to sanitizeAuthorLabel(authorUsername),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        fun buildCommentCreatePayload(
            commentId: String,
            trackKey: String,
            trackTitle: String,
            userId: String,
            authorUsername: String,
            text: String
        ): Map<String, Any> = mapOf(
            "commentId" to commentId,
            "trackKey" to trackKey,
            "trackTitle" to sanitizeTrackTitle(trackTitle),
            "userId" to userId,
            "authorUsername" to sanitizeAuthorLabel(authorUsername),
            "text" to (sanitizeCommentText(text) ?: " "),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
    }
}
