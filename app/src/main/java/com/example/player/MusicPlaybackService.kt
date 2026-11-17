package com.example.player

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.BackgroundExecutor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.example.AvaMusicApplication
import com.example.MainActivity
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.Executor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.guava.future

@OptIn(UnstableApi::class)
class MusicPlaybackService : MediaLibraryService() {

    private var player: ExoPlayer? = null
    private var mediaLibrarySession: MediaLibrarySession? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Volatile
    private var cachedTrendingTracks: List<MusicTrack> = emptyList()

    override fun onCreate() {
        super.onCreate()
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        val exoPlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()

        val activityIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        player = exoPlayer
        mediaLibrarySession = buildMediaLibrarySessionSafely(exoPlayer, activityIntent)
        (application as? AvaMusicApplication)?.autoSpeedVolumeManager?.apply {
            attachPlayer(exoPlayer)
            startMonitoringConnection()
        }
    }

    private fun buildMediaLibrarySessionSafely(
        exoPlayer: ExoPlayer,
        activityIntent: PendingIntent
    ): MediaLibrarySession {
        val builder = MediaLibrarySession.Builder(
            this,
            exoPlayer,
            AutoLibrarySessionCallback()
        ).setSessionActivity(activityIntent)

        if (hasCarConnectionProvider(this)) {
            return builder.build()
        }

        // Media3's MediaSessionLegacyStub unconditionally instantiates
        // AndroidAutoConnectionStateObserver on API 33+ Google/emulator devices and queries
        // content://androidx.car.app.connection on BackgroundExecutor without checking if the
        // provider exists, causing ActivityThread to log an error when Android Auto is absent.
        val previousExecutor = BackgroundExecutor.get()
        val guardedExecutor = Executor { runnable ->
            if (!isCarConnectionObserverTask(runnable)) {
                previousExecutor.execute(runnable)
            }
        }
        BackgroundExecutor.set(guardedExecutor)
        return try {
            builder.build()
        } finally {
            BackgroundExecutor.set(previousExecutor)
        }
    }

    companion object {
        const val ACTION_SET_SPEED_VOLUME = "com.example.player.ACTION_SET_SPEED_VOLUME"
        const val EXTRA_SPEED_KMH = "extra_speed_kmh"
        const val EXTRA_VOLUME_FRACTION = "extra_volume_fraction"

        internal const val CAR_CONNECTION_AUTHORITY = "androidx.car.app.connection"
        private const val CAR_CONNECTION_OBSERVER_PREFIX =
            "androidx.media3.session.AndroidAutoConnectionStateObserver"

        internal fun hasCarConnectionProvider(context: Context): Boolean {
            return context.packageManager.resolveContentProvider(CAR_CONNECTION_AUTHORITY, 0) != null
        }

        internal fun isCarConnectionObserverTask(runnable: Runnable): Boolean {
            if (runnable.javaClass.name.startsWith(CAR_CONNECTION_OBSERVER_PREFIX)) {
                return true
            }
            return Thread.currentThread().stackTrace.any { frame ->
                frame.className.startsWith(CAR_CONNECTION_OBSERVER_PREFIX)
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return mediaLibrarySession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val activePlayer = player
        if (activePlayer == null || !activePlayer.playWhenReady || activePlayer.mediaItemCount == 0) {
            pauseAllPlayersAndStopSelf()
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        SpeedVolumeService.stop(this)
        val appManager = (application as? AvaMusicApplication)?.autoSpeedVolumeManager
        player?.let { appManager?.detachPlayer(it) }
        appManager?.stopMonitoringConnection()
        mediaLibrarySession?.run {
            player.release()
            release()
        }
        mediaLibrarySession = null
        player = null
        super.onDestroy()
    }

    private suspend fun loadLocalAndFavoriteCatalog(): Pair<List<MusicTrack>, List<MusicTrack>> {
        val app = application as? AvaMusicApplication
            ?: return emptyList<MusicTrack>() to emptyList()
        val prefs = app.preferences.prefsFlow.first()
        val localTracks = app.repository.loadAllTracks(prefs.favoriteIds, prefs.importedUris)
        val favoriteTracks = (localTracks.filter { it.isFavorite } + prefs.favoriteOnlineTracks)
            .distinctBy { it.id }
        return localTracks to favoriteTracks
    }

    private suspend fun loadTrendingCatalog(): List<MusicTrack> {
        cachedTrendingTracks.takeIf { it.isNotEmpty() }?.let { return it }
        val app = application as? AvaMusicApplication ?: return emptyList()
        val favIds = app.preferences.prefsFlow.first().favoriteIds
        val fetched = app.trendingRepository.fetchTrendingTracks(
            preset = TrendingPreset.HOT,
            category = TrendingCategory.ALL,
            customQuery = "",
            favoriteIds = favIds
        ).getOrDefault(emptyList())
        if (fetched.isNotEmpty()) {
            cachedTrendingTracks = fetched
        }
        return fetched
    }

    private suspend fun loadAllSearchableTracks(): List<MusicTrack> {
        val (localTracks, favoriteTracks) = loadLocalAndFavoriteCatalog()
        return (localTracks + favoriteTracks + cachedTrendingTracks).distinctBy { it.id }
    }

    private suspend fun resolvePlayableItem(requestedItem: MediaItem): MediaItem {
        if (requestedItem.localConfiguration != null) {
            return requestedItem
        }
        val match = loadAllSearchableTracks().firstOrNull { it.id == requestedItem.mediaId }
        return if (match != null) {
            AutoMediaLibraryTree.toPlayableMediaItem(match)
        } else {
            requestedItem
        }
    }

    private inner class AutoLibrarySessionCallback : MediaLibrarySession.Callback {

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS
                .buildUpon()
                .add(SessionCommand(ACTION_SET_SPEED_VOLUME, Bundle.EMPTY))
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(sessionCommands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction == ACTION_SET_SPEED_VOLUME) {
                val volumeFraction = args.getFloat(EXTRA_VOLUME_FRACTION, 1.0f).coerceIn(0.1f, 1.0f)
                val speedKmh = args.getFloat(EXTRA_SPEED_KMH, 0f).coerceAtLeast(0f)
                player?.volume = volumeFraction
                (application as? AvaMusicApplication)
                    ?.autoSpeedVolumeManager
                    ?.onServiceVolumeApplied(speedKmh, volumeFraction)
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            return super.onCustomCommand(session, controller, customCommand, args)
        }

        override fun onPostConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ) {
            (application as? AvaMusicApplication)
                ?.autoSpeedVolumeManager
                ?.updateConnectedControllers(session)
        }

        override fun onDisconnected(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ) {
            (application as? AvaMusicApplication)
                ?.autoSpeedVolumeManager
                ?.updateConnectedControllers(session)
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {
            return Futures.immediateFuture(
                LibraryResult.ofItem(AutoMediaLibraryTree.buildRootItem(), params)
            )
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            return serviceScope.future {
                val items = when (parentId) {
                    AutoMediaLibraryTree.ROOT_ID -> AutoMediaLibraryTree.buildCategoryItems()
                    AutoMediaLibraryTree.NODE_ALL_SONGS -> {
                        val (localTracks, _) = loadLocalAndFavoriteCatalog()
                        localTracks.map(AutoMediaLibraryTree::toPlayableMediaItem)
                    }
                    AutoMediaLibraryTree.NODE_FAVORITES -> {
                        val (_, favoriteTracks) = loadLocalAndFavoriteCatalog()
                        favoriteTracks.map(AutoMediaLibraryTree::toPlayableMediaItem)
                    }
                    AutoMediaLibraryTree.NODE_TRENDING -> {
                        loadTrendingCatalog().map(AutoMediaLibraryTree::toPlayableMediaItem)
                    }
                    else -> emptyList()
                }
                val paged = AutoMediaLibraryTree.paginate(items, page, pageSize)
                LibraryResult.ofItemList(ImmutableList.copyOf(paged), params)
            }
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String
        ): ListenableFuture<LibraryResult<MediaItem>> {
            return serviceScope.future {
                val item = when (mediaId) {
                    AutoMediaLibraryTree.ROOT_ID -> AutoMediaLibraryTree.buildRootItem()
                    AutoMediaLibraryTree.NODE_ALL_SONGS,
                    AutoMediaLibraryTree.NODE_FAVORITES,
                    AutoMediaLibraryTree.NODE_TRENDING -> {
                        AutoMediaLibraryTree.buildCategoryItems().first { it.mediaId == mediaId }
                    }
                    else -> {
                        loadAllSearchableTracks()
                            .firstOrNull { it.id == mediaId }
                            ?.let(AutoMediaLibraryTree::toPlayableMediaItem)
                    }
                }
                if (item != null) {
                    LibraryResult.ofItem(item, null)
                } else {
                    LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE)
                }
            }
        }

        override fun onSearch(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<Void>> {
            return serviceScope.future {
                val matches = AutoMediaLibraryTree.filterTracks(loadAllSearchableTracks(), query)
                session.notifySearchResultChanged(browser, query, matches.size, params)
                LibraryResult.ofVoid()
            }
        }

        override fun onGetSearchResult(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            return serviceScope.future {
                val matches = AutoMediaLibraryTree.filterTracks(loadAllSearchableTracks(), query)
                    .map(AutoMediaLibraryTree::toPlayableMediaItem)
                val paged = AutoMediaLibraryTree.paginate(matches, page, pageSize)
                LibraryResult.ofItemList(ImmutableList.copyOf(paged), params)
            }
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): ListenableFuture<MutableList<MediaItem>> {
            return serviceScope.future {
                mediaItems.map { resolvePlayableItem(it) }.toMutableList()
            }
        }

        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            return serviceScope.future {
                if (mediaItems.size == 1 && mediaItems.first().localConfiguration == null) {
                    val requestedId = mediaItems.first().mediaId
                    val catalog = loadAllSearchableTracks()
                    val targetIdx = catalog.indexOfFirst { it.id == requestedId }
                    if (targetIdx >= 0) {
                        val queueItems = catalog.map(AutoMediaLibraryTree::toPlayableMediaItem)
                        return@future MediaSession.MediaItemsWithStartPosition(
                            queueItems,
                            targetIdx,
                            startPositionMs
                        )
                    }
                }
                val resolved = mediaItems.map { resolvePlayableItem(it) }
                val safeIndex = if (startIndex in resolved.indices) startIndex else C.INDEX_UNSET
                MediaSession.MediaItemsWithStartPosition(resolved, safeIndex, startPositionMs)
            }
        }

        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            return serviceScope.future {
                val app = application as? AvaMusicApplication
                val prefs = app?.preferences?.prefsFlow?.first()
                val (localTracks, favoriteTracks) = loadLocalAndFavoriteCatalog()
                val allTracks = (localTracks + favoriteTracks).distinctBy { it.id }
                if (allTracks.isEmpty()) {
                    return@future MediaSession.MediaItemsWithStartPosition(emptyList(), 0, 0L)
                }
                val resumeIdx = prefs?.lastTrackId
                    ?.let { lastId -> allTracks.indexOfFirst { it.id == lastId } }
                    ?.takeIf { it >= 0 }
                    ?: 0
                MediaSession.MediaItemsWithStartPosition(
                    allTracks.map(AutoMediaLibraryTree::toPlayableMediaItem),
                    resumeIdx,
                    0L
                )
            }
        }
    }
}
