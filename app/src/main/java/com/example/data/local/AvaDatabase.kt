package com.example.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.Junction
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "tracks")
data class TrackEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val mediaUri: String,
    val artworkUri: String,
    val isFavorite: Boolean = false,
    val isImported: Boolean = false,
    val genre: String = "الکترونیک",
    val accentColorHex: Long = 0xFFFF6B47L,
    val addedAt: Long = System.currentTimeMillis(),
    val lastPlayedAt: Long = 0L
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val artworkUri: String,
    val isBuiltIn: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "playlist_tracks",
    primaryKeys = ["playlistId", "trackId"],
    indices = [Index("trackId")]
)
data class PlaylistTrackCrossRef(
    val playlistId: String,
    val trackId: String,
    val sortOrder: Int = 0
)

data class PlaylistWithTracks(
    @Embedded val playlist: PlaylistEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = PlaylistTrackCrossRef::class,
            parentColumn = "playlistId",
            entityColumn = "trackId"
        )
    )
    val tracks: List<TrackEntity>
)

@Dao
interface AvaDao {
    @Query("SELECT * FROM tracks ORDER BY isImported DESC, addedAt ASC")
    fun observeAllTracks(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks ORDER BY isImported DESC, addedAt ASC")
    suspend fun getAllTracksOnce(): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE isFavorite = 1 ORDER BY addedAt ASC")
    fun observeFavoriteTracks(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE isFavorite = 1 ORDER BY addedAt ASC")
    suspend fun getFavoriteTracksOnce(): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE lastPlayedAt > 0 ORDER BY lastPlayedAt DESC LIMIT 10")
    fun observeRecentTracks(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE id = :trackId LIMIT 1")
    suspend fun getTrackById(trackId: String): TrackEntity?

    @Query("SELECT COUNT(*) FROM tracks")
    suspend fun getTrackCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTracks(tracks: List<TrackEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTracksIfAbsent(tracks: List<TrackEntity>)

    @Query("UPDATE tracks SET isFavorite = :isFavorite WHERE id = :trackId")
    suspend fun setFavorite(trackId: String, isFavorite: Boolean)

    @Query("UPDATE tracks SET lastPlayedAt = :timestamp WHERE id = :trackId")
    suspend fun updateLastPlayed(trackId: String, timestamp: Long)

    @Query("DELETE FROM tracks WHERE id = :trackId AND isImported = 1")
    suspend fun deleteImportedTrack(trackId: String)

    @Transaction
    @Query("SELECT * FROM playlists ORDER BY isBuiltIn DESC, createdAt ASC")
    fun observePlaylistsWithTracks(): Flow<List<PlaylistWithTracks>>

    @Transaction
    @Query("SELECT * FROM playlists ORDER BY isBuiltIn DESC, createdAt ASC")
    suspend fun getPlaylistsWithTracksOnce(): List<PlaylistWithTracks>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPlaylistIfAbsent(playlist: PlaylistEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistTrackCrossRef(crossRef: PlaylistTrackCrossRef)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun removeTrackFromPlaylist(playlistId: String, trackId: String)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun clearPlaylistTracks(playlistId: String)

    @Query("DELETE FROM playlists WHERE id = :playlistId AND isBuiltIn = 0")
    suspend fun deleteCustomPlaylist(playlistId: String)
}

@Database(
    entities = [TrackEntity::class, PlaylistEntity::class, PlaylistTrackCrossRef::class],
    version = 1,
    exportSchema = false
)
abstract class AvaDatabase : RoomDatabase() {
    abstract fun avaDao(): AvaDao

    companion object {
        @Volatile
        private var INSTANCE: AvaDatabase? = null

        fun getInstance(context: Context): AvaDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AvaDatabase::class.java,
                    "ava_player.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
