package com.example.drive

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.diagnostics.CrashReporter
import com.example.player.MusicTrack
import com.example.player.RepeatModeOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class DriveDbSnapshot(
    val uid: String,
    val username: String,
    val email: String,
    val favoriteIds: Set<String>,
    val favoriteOnlineTracks: List<MusicTrack>,
    val shuffleEnabled: Boolean,
    val repeatMode: RepeatModeOption,
    val lastTrackId: String?,
    val updatedAtEpochMs: Long
)

class GoogleDriveDatabaseManager(
    private val context: Context,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .build()
) {

    fun databaseFile(): File = context.getDatabasePath(DB_FILE_NAME)

    suspend fun writeSnapshotToSqlite(snapshot: DriveDbSnapshot): File = withContext(Dispatchers.IO) {
        val dbFile = databaseFile()
        dbFile.parentFile?.mkdirs()
        val helper = AvaDriveSqliteHelper(context)
        val db = helper.writableDatabase
        try {
            db.beginTransaction()
            try {
                db.delete(TABLE_PROFILE, null, null)
                db.insertOrThrow(
                    TABLE_PROFILE,
                    null,
                    ContentValues().apply {
                        put("uid", snapshot.uid)
                        put("username", snapshot.username)
                        put("email", snapshot.email)
                        put("updated_at", snapshot.updatedAtEpochMs)
                    }
                )

                val onlineById = snapshot.favoriteOnlineTracks.associateBy { it.id }
                db.delete(TABLE_FAVORITES, null, null)
                snapshot.favoriteIds.forEach { trackId ->
                    val track = onlineById[trackId]
                    db.insertOrThrow(
                        TABLE_FAVORITES,
                        null,
                        ContentValues().apply {
                            put("track_id", trackId)
                            put("title", track?.title ?: "")
                            put("artist", track?.artist ?: "")
                            put("album", track?.album ?: "")
                            put("duration_ms", track?.durationMs ?: 0L)
                            put("media_uri", track?.mediaUri ?: "")
                            put("artwork_uri", track?.artworkUri ?: "")
                            put("is_online", if (track?.isOnline == true) 1 else 0)
                            put("is_remix", if (track?.isRemix == true) 1 else 0)
                            put("is_insta_trend", if (track?.isInstaTrend == true) 1 else 0)
                            put("is_full_track", if (track?.isFullTrack != false) 1 else 0)
                            put("updated_at", snapshot.updatedAtEpochMs)
                        }
                    )
                }

                db.delete(TABLE_SETTINGS, null, null)
                insertSetting(db, KEY_SHUFFLE, snapshot.shuffleEnabled.toString())
                insertSetting(db, KEY_REPEAT, snapshot.repeatMode.name)
                snapshot.lastTrackId?.let { insertSetting(db, KEY_LAST_TRACK, it) }

                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        } finally {
            db.close()
            helper.close()
        }
        dbFile
    }

    suspend fun readSnapshotFromSqlite(): DriveDbSnapshot? = withContext(Dispatchers.IO) {
        val dbFile = databaseFile()
        if (!dbFile.exists() || dbFile.length() == 0L) return@withContext null

        val helper = AvaDriveSqliteHelper(context)
        val db = helper.readableDatabase
        try {
            var uid = ""
            var username = ""
            var email = ""
            var updatedAt = 0L

            db.rawQuery(
                "SELECT uid, username, email, updated_at FROM $TABLE_PROFILE LIMIT 1",
                null
            ).use { cursor ->
                if (cursor.moveToFirst()) {
                    uid = cursor.getString(0).orEmpty()
                    username = cursor.getString(1).orEmpty()
                    email = cursor.getString(2).orEmpty()
                    updatedAt = cursor.getLong(3)
                } else {
                    return@withContext null
                }
            }

            val favoriteIds = linkedSetOf<String>()
            val onlineTracks = mutableListOf<MusicTrack>()
            db.rawQuery(
                """
                SELECT track_id, title, artist, album, duration_ms, media_uri, artwork_uri,
                       is_online, is_remix, is_insta_trend, is_full_track
                FROM $TABLE_FAVORITES
                ORDER BY updated_at DESC
                """.trimIndent(),
                null
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val trackId = cursor.getString(0).orEmpty()
                    if (trackId.isNotBlank()) {
                        favoriteIds.add(trackId)
                        val isOnline = cursor.getInt(7) == 1
                        val mediaUri = cursor.getString(5).orEmpty()
                        if (isOnline && mediaUri.isNotBlank()) {
                            onlineTracks.add(
                                MusicTrack(
                                    id = trackId,
                                    title = cursor.getString(1).orEmpty(),
                                    artist = cursor.getString(2).orEmpty(),
                                    album = cursor.getString(3).orEmpty(),
                                    durationMs = cursor.getLong(4),
                                    mediaUri = mediaUri,
                                    artworkUri = cursor.getString(6).orEmpty(),
                                    isFavorite = true,
                                    isOnline = true,
                                    isRemix = cursor.getInt(8) == 1,
                                    isInstaTrend = cursor.getInt(9) == 1,
                                    isFullTrack = cursor.getInt(10) == 1,
                                    sourceLabel = ""
                                )
                            )
                        }
                    }
                }
            }

            val settings = mutableMapOf<String, String>()
            db.rawQuery("SELECT pref_key, pref_value FROM $TABLE_SETTINGS", null).use { cursor ->
                while (cursor.moveToNext()) {
                    settings[cursor.getString(0).orEmpty()] = cursor.getString(1).orEmpty()
                }
            }

            val shuffleEnabled = settings[KEY_SHUFFLE]?.toBooleanStrictOrNull() ?: false
            val repeatMode = settings[KEY_REPEAT]
                ?.let { runCatching { RepeatModeOption.valueOf(it) }.getOrNull() }
                ?: RepeatModeOption.OFF
            val lastTrackId = settings[KEY_LAST_TRACK]?.takeIf { it.isNotBlank() }

            DriveDbSnapshot(
                uid = uid,
                username = username,
                email = email,
                favoriteIds = favoriteIds,
                favoriteOnlineTracks = onlineTracks,
                shuffleEnabled = shuffleEnabled,
                repeatMode = repeatMode,
                lastTrackId = lastTrackId,
                updatedAtEpochMs = updatedAt
            )
        } finally {
            db.close()
            helper.close()
        }
    }

    suspend fun uploadToGoogleDrive(
        accessToken: String,
        snapshot: DriveDbSnapshot,
        knownFileId: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val dbFile = writeSnapshotToSqlite(snapshot)
            val sqliteBytes = dbFile.readBytes()
            val existingFileId = knownFileId?.takeIf { it.isNotBlank() }
                ?: findDatabaseFileId(accessToken)

            if (existingFileId != null) {
                val updateUrl = "$DRIVE_UPLOAD_BASE/files/$existingFileId?uploadType=media&fields=id"
                val request = Request.Builder()
                    .url(updateUrl)
                    .addHeader("Authorization", "Bearer $accessToken")
                    .patch(sqliteBytes.toRequestBody(SQLITE_MIME.toMediaType()))
                    .build()

                client.newCall(request).execute().use { response ->
                    val bodyStr = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        error("Drive update failed (${response.code}): $bodyStr")
                    }
                    val json = JSONObject(bodyStr)
                    json.optString("id").ifBlank { existingFileId }
                }
            } else {
                val metadataJson = JSONObject().apply {
                    put("name", DB_FILE_NAME)
                    put("mimeType", SQLITE_MIME)
                    put("parents", JSONArray(listOf("appDataFolder")))
                }.toString()

                val multipartBody = MultipartBody.Builder()
                    .setType("multipart/related".toMediaType())
                    .addPart(
                        metadataJson.toRequestBody("application/json; charset=UTF-8".toMediaType())
                    )
                    .addPart(
                        sqliteBytes.toRequestBody(SQLITE_MIME.toMediaType())
                    )
                    .build()

                val createUrl = "$DRIVE_UPLOAD_BASE/files?uploadType=multipart&fields=id"
                val request = Request.Builder()
                    .url(createUrl)
                    .addHeader("Authorization", "Bearer $accessToken")
                    .post(multipartBody)
                    .build()

                client.newCall(request).execute().use { response ->
                    val bodyStr = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        error("Drive create failed (${response.code}): $bodyStr")
                    }
                    val id = JSONObject(bodyStr).optString("id")
                    require(id.isNotBlank()) { "Google Drive did not return file ID" }
                    id
                }
            }
        }.onFailure { err ->
            CrashReporter.recordNonFatal("DRIVE_UPLOAD", err, context = context)
        }
    }

    suspend fun syncFromGoogleDrive(
        accessToken: String,
        knownFileId: String? = null
    ): Result<Pair<String, DriveDbSnapshot>?> = withContext(Dispatchers.IO) {
        runCatching {
            val fileId = knownFileId?.takeIf { it.isNotBlank() }
                ?: findDatabaseFileId(accessToken)
                ?: return@runCatching null

            val downloadUrl = "$DRIVE_API_BASE/files/$fileId?alt=media"
            val request = Request.Builder()
                .url(downloadUrl)
                .addHeader("Authorization", "Bearer $accessToken")
                .get()
                .build()

            val downloadedBytes = client.newCall(request).execute().use { response ->
                if (response.code == 404) return@runCatching null
                if (!response.isSuccessful) {
                    val err = response.body?.string().orEmpty()
                    error("Drive download failed (${response.code}): $err")
                }
                response.body?.bytes() ?: ByteArray(0)
            }

            if (downloadedBytes.isEmpty()) return@runCatching null

            val dbFile = databaseFile()
            dbFile.parentFile?.mkdirs()
            // Remove WAL/SHM sidecar files before replacing with remote SQLite binary
            File(dbFile.path + "-wal").delete()
            File(dbFile.path + "-shm").delete()
            File(dbFile.path + "-journal").delete()
            dbFile.writeBytes(downloadedBytes)

            val snapshot = readSnapshotFromSqlite() ?: return@runCatching null
            fileId to snapshot
        }.onFailure { err ->
            CrashReporter.recordNonFatal("DRIVE_SYNC", err, context = context)
        }
    }

    fun deleteLocalDatabaseFile() {
        val dbFile = databaseFile()
        dbFile.delete()
        File(dbFile.path + "-wal").delete()
        File(dbFile.path + "-shm").delete()
        File(dbFile.path + "-journal").delete()
    }

    private fun findDatabaseFileId(accessToken: String): String? {
        val query = URLEncoder.encode("name = '$DB_FILE_NAME' and trashed = false", "UTF-8")
        val url = "$DRIVE_API_BASE/files?spaces=appDataFolder&q=$query&fields=files(id,name,modifiedTime)&pageSize=1"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $accessToken")
            .get()
            .build()

        return client.newCall(request).execute().use { response ->
            val bodyStr = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                error("Drive query failed (${response.code}): $bodyStr")
            }
            val files = JSONObject(bodyStr).optJSONArray("files")
            if (files != null && files.length() > 0) {
                files.getJSONObject(0).optString("id").takeIf { it.isNotBlank() }
            } else {
                null
            }
        }
    }

    private fun insertSetting(db: SQLiteDatabase, key: String, value: String) {
        db.insertOrThrow(
            TABLE_SETTINGS,
            null,
            ContentValues().apply {
                put("pref_key", key)
                put("pref_value", value)
            }
        )
    }

    private class AvaDriveSqliteHelper(context: Context) :
        SQLiteOpenHelper(context, DB_FILE_NAME, null, DB_VERSION) {

        override fun onConfigure(db: SQLiteDatabase) {
            super.onConfigure(db)
            // Keep a single self-contained SQLite file for atomic Drive upload/download
            db.disableWriteAheadLogging()
        }

        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS $TABLE_PROFILE (
                    uid TEXT PRIMARY KEY,
                    username TEXT NOT NULL,
                    email TEXT NOT NULL,
                    updated_at INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS $TABLE_FAVORITES (
                    track_id TEXT PRIMARY KEY,
                    title TEXT NOT NULL,
                    artist TEXT NOT NULL,
                    album TEXT NOT NULL,
                    duration_ms INTEGER NOT NULL,
                    media_uri TEXT NOT NULL,
                    artwork_uri TEXT NOT NULL,
                    is_online INTEGER NOT NULL,
                    is_remix INTEGER NOT NULL,
                    is_insta_trend INTEGER NOT NULL,
                    is_full_track INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS $TABLE_SETTINGS (
                    pref_key TEXT PRIMARY KEY,
                    pref_value TEXT NOT NULL
                )
                """.trimIndent()
            )
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            db.execSQL("DROP TABLE IF EXISTS $TABLE_PROFILE")
            db.execSQL("DROP TABLE IF EXISTS $TABLE_FAVORITES")
            db.execSQL("DROP TABLE IF EXISTS $TABLE_SETTINGS")
            onCreate(db)
        }
    }

    companion object {
        const val DB_FILE_NAME = "ava_music_drive_db.sqlite"
        private const val DB_VERSION = 1
        private const val SQLITE_MIME = "application/x-sqlite3"
        private const val DRIVE_API_BASE = "https://www.googleapis.com/drive/v3"
        private const val DRIVE_UPLOAD_BASE = "https://www.googleapis.com/upload/drive/v3"

        private const val TABLE_PROFILE = "user_profile"
        private const val TABLE_FAVORITES = "favorite_tracks"
        private const val TABLE_SETTINGS = "app_settings"

        private const val KEY_SHUFFLE = "shuffle_enabled"
        private const val KEY_REPEAT = "repeat_mode"
        private const val KEY_LAST_TRACK = "last_track_id"
    }
}
