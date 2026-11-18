package com.example

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AlternateEmail
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PersonOutline
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.auth.DriveAuthOutcome
import com.example.auth.GoogleSessionHelper
import com.example.auth.UserAccount
import com.example.auth.UserAccountRepository
import com.example.auth.authStateFlow
import com.example.diagnostics.CrashReporter
import com.example.diagnostics.DiagnosticLogRepository
import com.example.diagnostics.configureAppCheck
import com.example.drive.DriveDbSnapshot
import com.example.player.AppTab
import com.example.player.AutoSpeedVolumeManager
import com.example.player.FavoriteTracksRepository
import com.example.player.RepeatModeOption
import com.example.player.StoredMusicPrefs
import com.example.player.TrackFilter
import com.example.player.TrackReactionSummary
import com.example.player.TrackSocialRepository
import com.example.player.VoiceSearchHelper
import com.example.ui.components.AccountDriveDialog
import com.example.ui.components.AiVoiceSearchDialog
import com.example.ui.components.AutoSpeedVolumeDialog
import com.example.ui.components.AvaSearchField
import com.example.ui.components.CrashReportDialog
import com.example.ui.components.DiagnosticsHistoryDialog
import com.example.ui.components.DrivingModeOverlay
import com.example.ui.components.FullNowPlayingSheet
import com.example.ui.components.MiniPlayerBar
import com.example.ui.components.StudioHeroBanner
import com.example.ui.components.TrackRowItem
import com.example.ui.components.TrackSocialBottomSheet
import com.example.ui.screens.AuthGateScreen
import com.example.ui.screens.FavoritesScreen
import com.example.ui.screens.OnlineMusicScreen
import com.example.ui.theme.AvaPlayerTheme
import com.example.ui.viewmodel.MusicPlayerViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        volumeControlStream = AudioManager.STREAM_MUSIC
        configureAppCheck(this, intent)
        enableEdgeToEdge()
        setContent {
            AvaPlayerTheme(darkTheme = true) {
                AvaAuthRoot()
            }
        }
    }
}

private sealed interface AccountLoadState {
    data object Loading : AccountLoadState
    data object NeedsUsername : AccountLoadState
    data class Ready(val account: UserAccount) : AccountLoadState
    data class Error(val message: String) : AccountLoadState
}

private sealed interface PendingDriveAction {
    data class RegisterUsername(val username: String) : PendingDriveAction
    data object SaveDrive : PendingDriveAction
    data object RestoreDrive : PendingDriveAction
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun AvaAuthRoot() {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val app = remember(context) { context.applicationContext as AvaMusicApplication }
    val scope = rememberCoroutineScope()

    val storedPrefs by app.preferences.prefsFlow.collectAsStateWithLifecycle(
        initialValue = StoredMusicPrefs()
    )
    val diagnosticLogs by CrashReporter.logsFlow.collectAsStateWithLifecycle()
    var isDiagnosticsDialogOpen by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(app) {
        CrashReporter.getReports(app)
    }

    val isFirebaseReady = remember(app) { GoogleSessionHelper.isFirebaseConfigured(app) }
    val auth = remember(isFirebaseReady) {
        if (isFirebaseReady) runCatching { Firebase.auth }.getOrNull() else null
    }
    val userAccountRepository = remember(app, isFirebaseReady) {
        if (isFirebaseReady) runCatching { UserAccountRepository(app) }.getOrNull() else null
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (storedPrefs.isGuestMode) {
            key("guest") {
                val guestViewModel: MusicPlayerViewModel = viewModel(
                    key = "guest",
                    factory = viewModelFactory {
                        initializer {
                            val application = checkNotNull(this[APPLICATION_KEY]) as AvaMusicApplication
                            MusicPlayerViewModel(
                                appContext = application,
                                preferences = application.preferences,
                                repository = application.repository,
                                trendingRepository = application.trendingRepository,
                                userAccountRepository = null,
                                driveDbManager = application.driveDbManager,
                                currentUserId = "guest",
                                autoSpeedVolumeManager = application.autoSpeedVolumeManager,
                                favoriteTracksRepository = null,
                                trackSocialRepository = null
                            )
                        }
                    }
                )
                val guestLabel = stringResource(R.string.guest_username_badge)
                val guestAccount = remember(guestLabel) {
                    UserAccount(
                        id = "guest",
                        userId = "guest",
                        username = guestLabel,
                        usernameKey = "guest"
                    )
                }
                SimpleMusicPlayerScreen(
                    viewModel = guestViewModel,
                    userAccount = guestAccount,
                    userEmail = "",
                    isDriveAuthorized = false,
                    isGuestMode = true,
                    onUpgradeToGoogle = {
                        scope.launch { app.preferences.setGuestMode(false) }
                    },
                    diagnosticCount = diagnosticLogs.size,
                    onOpenDiagnostics = { isDiagnosticsDialogOpen = true },
                    onRequestDriveAuth = {},
                    onSaveToDrive = {},
                    onRestoreFromDrive = {},
                    onSignOut = {
                        scope.launch { app.preferences.setGuestMode(false) }
                    }
                )
            }
        } else if (auth == null || userAccountRepository == null) {
            var configError by remember {
                mutableStateOf<String?>(
                    "پیکربندی Firebase (google-services.json) در این نسخه فعال نیست."
                )
            }
            AuthGateScreen(
                isSignedInWithGoogle = false,
                userEmail = null,
                isBusy = false,
                statusLabel = null,
                errorMessage = configError,
                usernameInput = "",
                usernameError = null,
                isCheckingAvailability = false,
                isUsernameAvailable = null,
                isDriveAuthorized = false,
                onGoogleSignInClick = {
                    val missingKeys = listOf("google_app_id", "google_api_key", "default_web_client_id").filter { key ->
                        val id = app.resources.getIdentifier(key, "string", app.packageName)
                        id == 0 || app.getString(id).isBlank()
                    }
                    val uiMsg = "تنظیمات Firebase داخل این APK موجود نیست (خروجی گیت‌هاب بدون google-services.json ساخته شده است). از منوی AI Studio گزینه Generate APK را بزنید یا سکرت GOOGLE_SERVICES_JSON را در گیت‌هاب اضافه کنید."
                    configError = uiMsg
                    CrashReporter.recordNonFatal(
                        tag = "FIREBASE_CONFIG_MISSING",
                        message = "$uiMsg | missing=${missingKeys.joinToString(",")}",
                        context = app
                    )
                },
                onGuestSignInClick = {
                    scope.launch { app.preferences.setGuestMode(true) }
                },
                onUsernameChange = {},
                onAuthorizeDriveClick = {},
                onRegisterUsernameSubmit = {},
                onSwitchGoogleAccount = {},
                diagnosticCount = diagnosticLogs.size,
                onOpenDiagnostics = { isDiagnosticsDialogOpen = true }
            )
        } else {

    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)

    var isAuthBusy by remember { mutableStateOf(false) }
    var authStatusLabel by remember { mutableStateOf<String?>(null) }
    var authError by remember { mutableStateOf<String?>(null) }

    var usernameInput by rememberSaveable { mutableStateOf("") }
    var usernameFormatError by remember { mutableStateOf<String?>(null) }
    var isCheckingAvailability by remember { mutableStateOf(false) }
    var isUsernameAvailable by remember { mutableStateOf<Boolean?>(null) }

    var driveAccessToken by remember { mutableStateOf<String?>(null) }
    var pendingDriveAction by remember { mutableStateOf<PendingDriveAction?>(null) }
    var hasAttemptedSilentLogin by remember { mutableStateOf(false) }
    var promptedDriveResolutionForUid by remember { mutableStateOf<String?>(null) }

    suspend fun completeRegistrationWithDrive(
        uid: String,
        email: String,
        rawUsername: String,
        token: String?
    ) {
        isAuthBusy = true
        authError = null
        authStatusLabel = "در حال ثبت نام کاربری یکتا…"

        val regResult = userAccountRepository.registerUsername(
            userId = uid,
            rawUsername = rawUsername
        )
        regResult.onSuccess { account ->
            if (!token.isNullOrBlank()) {
                authStatusLabel = "در حال ساخت دیتابیس در گوگل درایو…"
                val existingSync = app.driveDbManager.syncFromGoogleDrive(token, null).getOrNull()
                if (existingSync != null) {
                    val (existingFileId, existingSnap) = existingSync
                    app.preferences.restoreFromDriveSnapshot(
                        favoriteIds = existingSnap.favoriteIds,
                        favoriteOnlineTracks = existingSnap.favoriteOnlineTracks,
                        shuffleEnabled = existingSnap.shuffleEnabled,
                        repeatMode = existingSnap.repeatMode,
                        lastTrackId = existingSnap.lastTrackId
                    )
                    userAccountRepository.updateDriveFileId(uid, existingFileId)
                } else {
                    val storedPrefs = app.preferences.prefsFlow.first()
                    val initialSnapshot = DriveDbSnapshot(
                        uid = uid,
                        username = account.username,
                        email = email,
                        favoriteIds = storedPrefs.favoriteIds,
                        favoriteOnlineTracks = storedPrefs.favoriteOnlineTracks,
                        shuffleEnabled = storedPrefs.shuffleEnabled,
                        repeatMode = RepeatModeOption.OFF,
                        lastTrackId = storedPrefs.lastTrackId,
                        updatedAtEpochMs = System.currentTimeMillis()
                    )
                    val uploadResult = app.driveDbManager.uploadToGoogleDrive(token, initialSnapshot)
                    uploadResult.onSuccess { fileId ->
                        userAccountRepository.updateDriveFileId(uid, fileId)
                    }.onFailure { err ->
                        authError = "نام کاربری ثبت شد، اما ساخت دیتابیس در گوگل درایو خطا داد: ${err.localizedMessage ?: ""}"
                    }
                }
            }
        }.onFailure { err ->
            authError = err.localizedMessage ?: "ثبت نام کاربری ناموفق بود."
        }
        isAuthBusy = false
        authStatusLabel = null
    }

    val driveResolutionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { activityResult ->
        val act = activity ?: return@rememberLauncherForActivityResult
        if (activityResult.resultCode == Activity.RESULT_OK) {
            val token = GoogleSessionHelper.extractDriveTokenFromIntent(act, activityResult.data)
            if (!token.isNullOrBlank()) {
                driveAccessToken = token
                authError = null
                val pending = pendingDriveAction
                pendingDriveAction = null
                val signedUser = auth.currentUser
                if (pending is PendingDriveAction.RegisterUsername && signedUser != null) {
                    scope.launch {
                        completeRegistrationWithDrive(
                            uid = signedUser.uid,
                            email = signedUser.email.orEmpty(),
                            rawUsername = pending.username,
                            token = token
                        )
                    }
                }
            } else {
                pendingDriveAction = null
                authError = "توکن دسترسی گوگل درایو دریافت نشد."
            }
        } else {
            val pending = pendingDriveAction
            pendingDriveAction = null
            if (pending is PendingDriveAction.RegisterUsername && auth.currentUser != null) {
                authError = "برای ذخیره دیتابیس داخل گوگل درایو، تأیید دسترسی درایو لازم است."
            }
        }
    }

    fun requestDriveAuthorization(
        actionAfterAuth: PendingDriveAction? = null,
        launchPromptIfNeeded: Boolean = true
    ) {
        val act = activity ?: return
        scope.launch {
            when (val outcome = GoogleSessionHelper.authorizeDrive(act)) {
                is DriveAuthOutcome.Authorized -> {
                    driveAccessToken = outcome.accessToken
                    if (actionAfterAuth is PendingDriveAction.RegisterUsername) {
                        val signedUser = auth.currentUser
                        if (signedUser != null) {
                            completeRegistrationWithDrive(
                                uid = signedUser.uid,
                                email = signedUser.email.orEmpty(),
                                rawUsername = actionAfterAuth.username,
                                token = outcome.accessToken
                            )
                        }
                    } else {
                        pendingDriveAction = actionAfterAuth
                    }
                }
                is DriveAuthOutcome.NeedsResolution -> {
                    if (launchPromptIfNeeded) {
                        pendingDriveAction = actionAfterAuth
                        driveResolutionLauncher.launch(
                            IntentSenderRequest.Builder(outcome.pendingIntent.intentSender).build()
                        )
                    }
                }
                is DriveAuthOutcome.Failed -> {
                    authError = outcome.message
                }
            }
        }
    }

    // Automatic silent Google Sign-In attempt once on cold start (skipped in Guest Mode)
    LaunchedEffect(Unit) {
        if (app.preferences.prefsFlow.first().isGuestMode) return@LaunchedEffect
        if (!hasAttemptedSilentLogin && auth.currentUser == null && activity != null) {
            hasAttemptedSilentLogin = true
            isAuthBusy = true
            authStatusLabel = "در حال بررسی حساب گوگل…"
            GoogleSessionHelper.attemptSilentSignIn(activity)
            isAuthBusy = false
            authStatusLabel = null
        }
    }

    val user = currentUser
    if (user == null) {
        LaunchedEffect(Unit) {
            driveAccessToken = null
            pendingDriveAction = null
            promptedDriveResolutionForUid = null
            usernameInput = ""
            usernameFormatError = null
            isUsernameAvailable = null
        }
        AuthGateScreen(
            isSignedInWithGoogle = false,
            userEmail = null,
            isBusy = isAuthBusy,
            statusLabel = authStatusLabel,
            errorMessage = authError,
            usernameInput = usernameInput,
            usernameError = usernameFormatError,
            isCheckingAvailability = isCheckingAvailability,
            isUsernameAvailable = isUsernameAvailable,
            isDriveAuthorized = false,
            onGoogleSignInClick = {
                val act = activity ?: return@AuthGateScreen
                scope.launch {
                    isAuthBusy = true
                    authError = null
                    authStatusLabel = "در حال اتصال به حساب گوگل…"
                    val signInResult = GoogleSessionHelper.signInInteractiveWithGoogle(act)
                    signInResult.onFailure { err ->
                        authError = err.localizedMessage ?: "ورود با گوگل انجام نشد."
                    }
                    isAuthBusy = false
                    authStatusLabel = null
                }
            },
            onGuestSignInClick = {
                isAuthBusy = false
                authStatusLabel = null
                authError = null
                scope.launch { app.preferences.setGuestMode(true) }
            },
            onUsernameChange = {},
            onAuthorizeDriveClick = {},
            onRegisterUsernameSubmit = {},
            onSwitchGoogleAccount = {},
            diagnosticCount = diagnosticLogs.size,
            onOpenDiagnostics = { isDiagnosticsDialogOpen = true }
        )
    } else {
        // Automatically back up unsynced crash & non-fatal reports to Cloud Firestore once signed in
        val attemptedCloudSyncIds = remember(user.uid) { mutableSetOf<String>() }
        LaunchedEffect(user.uid, diagnosticLogs) {
            val pendingSync = diagnosticLogs.filter {
                !it.syncedToCloud && it.reportId !in attemptedCloudSyncIds
            }
            if (pendingSync.isNotEmpty()) {
                pendingSync.forEach { attemptedCloudSyncIds.add(it.reportId) }
                val diagRepo = runCatching { DiagnosticLogRepository(app) }.getOrNull()
                if (diagRepo != null) {
                    val syncedIds = mutableSetOf<String>()
                    pendingSync.forEach { report ->
                        val res = diagRepo.uploadReport(user.uid, report)
                        if (res.isSuccess) {
                            syncedIds.add(report.reportId)
                        }
                    }
                    if (syncedIds.isNotEmpty()) {
                        CrashReporter.markSyncedToCloud(app, syncedIds)
                    }
                }
            }
        }
        // Observe user's Firestore account document (/users/{uid})
        val accountStateFlow = remember(user.uid) {
            userAccountRepository.observeUserAccount(user.uid)
                .map<UserAccount?, AccountLoadState> { account ->
                    if (account == null) AccountLoadState.NeedsUsername
                    else AccountLoadState.Ready(account)
                }
                .catch { err ->
                    emit(AccountLoadState.Error(err.localizedMessage ?: "خطا در خواندن حساب کاربری"))
                }
        }
        val accountState by accountStateFlow.collectAsStateWithLifecycle(initialValue = AccountLoadState.Loading)

        // Automatically request Drive token once per authenticated session
        LaunchedEffect(user.uid) {
            if (driveAccessToken == null && promptedDriveResolutionForUid != user.uid && activity != null) {
                promptedDriveResolutionForUid = user.uid
                requestDriveAuthorization(actionAfterAuth = null, launchPromptIfNeeded = true)
            }
        }

        // Debounced uniqueness & format validation while typing the 1..4 char username
        LaunchedEffect(usernameInput, accountState) {
            if (accountState !is AccountLoadState.NeedsUsername) return@LaunchedEffect
            val trimmed = usernameInput.trim()
            if (trimmed.isEmpty()) {
                usernameFormatError = null
                isUsernameAvailable = null
                isCheckingAvailability = false
                return@LaunchedEffect
            }
            val formatErr = UserAccountRepository.validateUsername(trimmed)
            if (formatErr != null) {
                usernameFormatError = formatErr
                isUsernameAvailable = null
                isCheckingAvailability = false
                return@LaunchedEffect
            }
            usernameFormatError = null
            isCheckingAvailability = true
            delay(300L)
            val availRes = userAccountRepository.isUsernameAvailable(trimmed)
            isUsernameAvailable = availRes.getOrNull()
            availRes.onFailure {
                usernameFormatError = it.localizedMessage
            }
            isCheckingAvailability = false
        }

        when (val state = accountState) {
            is AccountLoadState.Loading -> {
                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                    contentWindowInsets = WindowInsets.safeDrawing
                ) { padding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            CircularProgressIndicator()
                            Text(
                                text = stringResource(R.string.auth_checking_account),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            is AccountLoadState.NeedsUsername -> {
                AuthGateScreen(
                    isSignedInWithGoogle = true,
                    userEmail = user.email,
                    isBusy = isAuthBusy,
                    statusLabel = authStatusLabel,
                    errorMessage = authError,
                    usernameInput = usernameInput,
                    usernameError = usernameFormatError,
                    isCheckingAvailability = isCheckingAvailability,
                    isUsernameAvailable = isUsernameAvailable,
                    isDriveAuthorized = !driveAccessToken.isNullOrBlank(),
                    onGoogleSignInClick = {},
                    onUsernameChange = { raw ->
                        authError = null
                        usernameInput = raw
                    },
                    onAuthorizeDriveClick = {
                        requestDriveAuthorization(actionAfterAuth = null, launchPromptIfNeeded = true)
                    },
                    onRegisterUsernameSubmit = {
                        val trimmed = usernameInput.trim()
                        val err = UserAccountRepository.validateUsername(trimmed)
                        if (err != null) {
                            usernameFormatError = err
                            return@AuthGateScreen
                        }
                        val token = driveAccessToken
                        if (token.isNullOrBlank()) {
                            requestDriveAuthorization(
                                actionAfterAuth = PendingDriveAction.RegisterUsername(trimmed),
                                launchPromptIfNeeded = true
                            )
                        } else {
                            scope.launch {
                                completeRegistrationWithDrive(
                                    uid = user.uid,
                                    email = user.email.orEmpty(),
                                    rawUsername = trimmed,
                                    token = token
                                )
                            }
                        }
                    },
                    onSwitchGoogleAccount = {
                        scope.launch {
                            isAuthBusy = true
                            driveAccessToken = null
                            app.preferences.clearUserSessionData()
                            app.driveDbManager.deleteLocalDatabaseFile()
                            GoogleSessionHelper.signOutCompletely(app)
                            isAuthBusy = false
                        }
                    },
                    diagnosticCount = diagnosticLogs.size,
                    onOpenDiagnostics = { isDiagnosticsDialogOpen = true }
                )
            }

            is AccountLoadState.Error -> {
                AuthGateScreen(
                    isSignedInWithGoogle = true,
                    userEmail = user.email,
                    isBusy = isAuthBusy,
                    statusLabel = authStatusLabel,
                    errorMessage = state.message,
                    usernameInput = usernameInput,
                    usernameError = usernameFormatError,
                    isCheckingAvailability = false,
                    isUsernameAvailable = null,
                    isDriveAuthorized = !driveAccessToken.isNullOrBlank(),
                    onGoogleSignInClick = {},
                    onUsernameChange = { usernameInput = it },
                    onAuthorizeDriveClick = {
                        requestDriveAuthorization(actionAfterAuth = null, launchPromptIfNeeded = true)
                    },
                    onRegisterUsernameSubmit = {},
                    onSwitchGoogleAccount = {
                        scope.launch {
                            driveAccessToken = null
                            GoogleSessionHelper.signOutCompletely(app)
                        }
                    },
                    diagnosticCount = diagnosticLogs.size,
                    onOpenDiagnostics = { isDiagnosticsDialogOpen = true }
                )
            }

            is AccountLoadState.Ready -> {
                key(user.uid) {
                    val playerViewModel: MusicPlayerViewModel = viewModel(
                        key = user.uid,
                        factory = viewModelFactory {
                            initializer {
                                val application = checkNotNull(this[APPLICATION_KEY]) as AvaMusicApplication
                                MusicPlayerViewModel(
                                    appContext = application,
                                    preferences = application.preferences,
                                    repository = application.repository,
                                    trendingRepository = application.trendingRepository,
                                    userAccountRepository = UserAccountRepository(application),
                                    driveDbManager = application.driveDbManager,
                                    currentUserId = user.uid,
                                    autoSpeedVolumeManager = application.autoSpeedVolumeManager,
                                    favoriteTracksRepository = FavoriteTracksRepository(application),
                                    trackSocialRepository = TrackSocialRepository(application)
                                )
                            }
                        }
                    )

                    val userEmail = user.email.orEmpty()
                    LaunchedEffect(driveAccessToken, state.account) {
                        playerViewModel.updateDriveSessionContext(
                            accessToken = driveAccessToken,
                            account = state.account,
                            email = userEmail
                        )
                        val pending = pendingDriveAction
                        val token = driveAccessToken
                        if (!token.isNullOrBlank() && pending != null) {
                            when (pending) {
                                PendingDriveAction.SaveDrive -> {
                                    pendingDriveAction = null
                                    playerViewModel.syncCurrentDatabaseToDrive(token, state.account, userEmail)
                                }
                                PendingDriveAction.RestoreDrive -> {
                                    pendingDriveAction = null
                                    playerViewModel.restoreFromGoogleDrive(token, state.account, userEmail)
                                }
                                is PendingDriveAction.RegisterUsername -> {
                                    pendingDriveAction = null
                                }
                            }
                        }
                    }

                    SimpleMusicPlayerScreen(
                        viewModel = playerViewModel,
                        userAccount = state.account,
                        userEmail = userEmail,
                        isDriveAuthorized = !driveAccessToken.isNullOrBlank(),
                        isGuestMode = false,
                        onUpgradeToGoogle = {},
                        diagnosticCount = diagnosticLogs.size,
                        onOpenDiagnostics = { isDiagnosticsDialogOpen = true },
                        onRequestDriveAuth = {
                            requestDriveAuthorization(actionAfterAuth = null, launchPromptIfNeeded = true)
                        },
                        onSaveToDrive = {
                            val token = driveAccessToken
                            if (token.isNullOrBlank()) {
                                requestDriveAuthorization(
                                    actionAfterAuth = PendingDriveAction.SaveDrive,
                                    launchPromptIfNeeded = true
                                )
                            } else {
                                playerViewModel.syncCurrentDatabaseToDrive(token, state.account, userEmail)
                            }
                        },
                        onRestoreFromDrive = {
                            val token = driveAccessToken
                            if (token.isNullOrBlank()) {
                                requestDriveAuthorization(
                                    actionAfterAuth = PendingDriveAction.RestoreDrive,
                                    launchPromptIfNeeded = true
                                )
                            } else {
                                playerViewModel.restoreFromGoogleDrive(token, state.account, userEmail)
                            }
                        },
                        onSignOut = {
                            scope.launch {
                                driveAccessToken = null
                                pendingDriveAction = null
                                app.preferences.clearUserSessionData()
                                app.driveDbManager.deleteLocalDatabaseFile()
                                GoogleSessionHelper.signOutCompletely(app)
                            }
                        }
                    )
                }
            }
        }
    }
    } // end else (auth != null)

        val pendingFatalCrash = remember(diagnosticLogs) {
            diagnosticLogs.firstOrNull { it.isFatal && !it.acknowledged }
        }
        if (pendingFatalCrash != null) {
            CrashReportDialog(
                report = pendingFatalCrash,
                onDismiss = { CrashReporter.acknowledgeAllFatalCrashes(app) }
            )
        }

        if (isDiagnosticsDialogOpen) {
            DiagnosticsHistoryDialog(
                reports = diagnosticLogs,
                onClearLogs = { CrashReporter.clearAllReports(app) },
                onDismiss = { isDiagnosticsDialogOpen = false }
            )
        }
    }
}

@Composable
fun SimpleMusicPlayerScreen(
    viewModel: MusicPlayerViewModel,
    userAccount: UserAccount,
    userEmail: String,
    isDriveAuthorized: Boolean,
    isGuestMode: Boolean = false,
    onUpgradeToGoogle: () -> Unit = {},
    diagnosticCount: Int,
    onOpenDiagnostics: () -> Unit,
    onRequestDriveAuth: () -> Unit,
    onSaveToDrive: () -> Unit,
    onRestoreFromDrive: () -> Unit,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val activeTab by viewModel.activeTab.collectAsStateWithLifecycle()
    val tracks by viewModel.filteredTracks.collectAsStateWithLifecycle()
    val allTracks by viewModel.allTracks.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val activeFilter by viewModel.activeFilter.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()

    val onlineSubSection by viewModel.onlineSubSection.collectAsStateWithLifecycle()
    val trendingTracks by viewModel.trendingTracks.collectAsStateWithLifecycle()
    val trendingCategory by viewModel.trendingCategory.collectAsStateWithLifecycle()
    val trendingPreset by viewModel.trendingPreset.collectAsStateWithLifecycle()
    val trendingSearchQuery by viewModel.trendingSearchQuery.collectAsStateWithLifecycle()
    val isLoadingTrending by viewModel.isLoadingTrending.collectAsStateWithLifecycle()
    val trendingError by viewModel.trendingError.collectAsStateWithLifecycle()

    val discoverTracks by viewModel.discoverTracks.collectAsStateWithLifecycle()
    val discoverStyle by viewModel.discoverStyle.collectAsStateWithLifecycle()
    val discoverSearchQuery by viewModel.discoverSearchQuery.collectAsStateWithLifecycle()
    val isLoadingDiscover by viewModel.isLoadingDiscover.collectAsStateWithLifecycle()
    val discoverError by viewModel.discoverError.collectAsStateWithLifecycle()

    val trackSocialSummaries by viewModel.trackSocialSummaries.collectAsStateWithLifecycle()
    val activeSocialTrack by viewModel.activeSocialTrack.collectAsStateWithLifecycle()
    val activeTrackSummary by viewModel.activeTrackSummary.collectAsStateWithLifecycle()
    val activeTrackComments by viewModel.activeTrackComments.collectAsStateWithLifecycle()
    val isLoadingComments by viewModel.isLoadingComments.collectAsStateWithLifecycle()
    val isSubmittingComment by viewModel.isSubmittingComment.collectAsStateWithLifecycle()
    val commentsError by viewModel.commentsError.collectAsStateWithLifecycle()

    val isSyncingDrive by viewModel.isSyncingDrive.collectAsStateWithLifecycle()
    val driveSyncMessage by viewModel.driveSyncMessage.collectAsStateWithLifecycle()
    val favoriteCount by viewModel.favoriteCount.collectAsStateWithLifecycle()
    val favoritesUiState by viewModel.favoritesUiState.collectAsStateWithLifecycle()
    val favoritesSearchQuery by viewModel.favoritesSearchQuery.collectAsStateWithLifecycle()
    val aiVoiceState by viewModel.aiVoiceState.collectAsStateWithLifecycle()
    val autoSpeedVolumeState by viewModel.autoSpeedVolumeState.collectAsStateWithLifecycle()

    var isNowPlayingOpen by rememberSaveable { mutableStateOf(false) }
    var isAccountDialogOpen by rememberSaveable { mutableStateOf(false) }
    var isAutoSpeedDialogOpen by rememberSaveable { mutableStateOf(false) }
    var isDrivingModeOpen by rememberSaveable { mutableStateOf(false) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { results ->
            viewModel.refreshAutoSpeedPermission()
            val granted = results[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                results[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            if (granted) {
                viewModel.setSpeedVolumeEnabled(true)
                viewModel.syncSpeedVolumeBackgroundService()
            }
        }
    )

    LaunchedEffect(
        playerState.isPlaying,
        autoSpeedVolumeState.isActiveForDriving,
        autoSpeedVolumeState.hasLocationPermission
    ) {
        if (playerState.isPlaying &&
            autoSpeedVolumeState.isActiveForDriving &&
            autoSpeedVolumeState.hasLocationPermission
        ) {
            viewModel.syncSpeedVolumeBackgroundService()
        }
    }

    val requestAutoSpeedLocationPermission: () -> Unit = {
        locationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    val audioPermission = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
    }

    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { results ->
            if (results[audioPermission] == true) {
                viewModel.scanDeviceMusic(showResultMessage = true)
            }
        }
    )

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
        onResult = { uris ->
            if (uris.isNotEmpty()) {
                viewModel.importAudioFiles(uris)
            }
        }
    )

    var voiceSearchTargetTab by rememberSaveable { mutableStateOf(AppTab.LIBRARY) }
    val systemSpeechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val rawSpoken = result.data
                    ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                    ?.firstOrNull()
                    ?.trim()
                if (!rawSpoken.isNullOrBlank()) {
                    viewModel.analyzeSpokenTextWithAi(rawSpoken, voiceSearchTargetTab)
                } else {
                    viewModel.showStatusMessage(context.getString(R.string.voice_search_no_match))
                }
            }
        }
    )

    val launchSystemSpeechForAi: () -> Unit = {
        val prompt = if (voiceSearchTargetTab == AppTab.ONLINE) {
            context.getString(R.string.voice_search_prompt_trending)
        } else {
            context.getString(R.string.voice_search_prompt_library)
        }
        val speechIntent = VoiceSearchHelper.buildSpeechIntent(prompt)
        try {
            systemSpeechLauncher.launch(speechIntent)
        } catch (_: ActivityNotFoundException) {
            viewModel.showStatusMessage(context.getString(R.string.voice_search_unavailable))
        }
    }

    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            if (granted) {
                viewModel.startAiVoiceRecording(voiceSearchTargetTab)
            } else {
                viewModel.showAiVoicePermissionRequired()
            }
        }
    )

    val launchVoiceSearch: (AppTab) -> Unit = { targetTab ->
        voiceSearchTargetTab = targetTab
        val micGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (micGranted) {
            viewModel.startAiVoiceRecording(targetTab)
        } else {
            recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val triggerScan: () -> Unit = {
        val granted = ContextCompat.checkSelfPermission(
            context,
            audioPermission
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            viewModel.scanDeviceMusic(showResultMessage = true)
        } else {
            permissionsLauncher.launch(arrayOf(audioPermission))
        }
    }

    LaunchedEffect(Unit) {
        val audioGranted = ContextCompat.checkSelfPermission(
            context,
            audioPermission
        ) == PackageManager.PERMISSION_GRANTED
        if (audioGranted) {
            viewModel.scanDeviceMusic(showResultMessage = false)
        } else {
            val toRequest = buildList {
                add(audioPermission)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val notifGranted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
                    if (!notifGranted) add(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            if (toRequest.isNotEmpty()) {
                runCatching { permissionsLauncher.launch(toRequest.toTypedArray()) }
            }
        }
    }

    LaunchedEffect(statusMessage) {
        statusMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessage()
        }
    }

    val activeTrack = playerState.currentTrack
    val progress = if (playerState.durationMs > 0L) {
        (playerState.positionMs.toFloat() / playerState.durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val primaryGlow = MaterialTheme.colorScheme.primary
    val tertiaryGlow = MaterialTheme.colorScheme.tertiary

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (activeTrack != null) {
                        MiniPlayerBar(
                            track = activeTrack,
                            isPlaying = playerState.isPlaying,
                            progress = progress,
                            onExpand = { isNowPlayingOpen = true },
                            onTogglePlayPause = viewModel::togglePlayPause,
                            onSkipNext = viewModel::skipNext,
                            applyNavigationBarPadding = false
                        )
                    }
                    HorizontalDivider(
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                    )
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        tonalElevation = 0.dp
                    ) {
                        val navItemColors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        NavigationBarItem(
                            selected = activeTab == AppTab.LIBRARY,
                            onClick = { viewModel.selectTab(AppTab.LIBRARY) },
                            colors = navItemColors,
                            icon = {
                                Icon(
                                    imageVector = Icons.Rounded.LibraryMusic,
                                    contentDescription = stringResource(R.string.tab_library)
                                )
                            },
                            label = { Text(stringResource(R.string.tab_library)) },
                            modifier = Modifier.testTag("nav_tab_library")
                        )
                        NavigationBarItem(
                            selected = activeTab == AppTab.FAVORITES,
                            onClick = { viewModel.selectTab(AppTab.FAVORITES) },
                            colors = navItemColors,
                            icon = {
                                Icon(
                                    imageVector = if (activeTab == AppTab.FAVORITES) {
                                        Icons.Rounded.Favorite
                                    } else {
                                        Icons.Rounded.FavoriteBorder
                                    },
                                    contentDescription = stringResource(R.string.tab_favorites)
                                )
                            },
                            label = { Text(stringResource(R.string.tab_favorites)) },
                            modifier = Modifier.testTag("nav_tab_favorites")
                        )
                        NavigationBarItem(
                            selected = activeTab == AppTab.ONLINE,
                            onClick = { viewModel.selectTab(AppTab.ONLINE) },
                            colors = navItemColors,
                            icon = {
                                Icon(
                                    imageVector = Icons.Rounded.Public,
                                    contentDescription = stringResource(R.string.tab_online)
                                )
                            },
                            label = { Text(stringResource(R.string.tab_online)) },
                            modifier = Modifier.testTag("nav_tab_online")
                        )
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(primaryGlow.copy(alpha = 0.11f), Color.Transparent),
                                center = Offset(size.width * 0.85f, size.height * 0.08f),
                                radius = size.maxDimension * 0.48f
                            )
                        )
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(tertiaryGlow.copy(alpha = 0.08f), Color.Transparent),
                                center = Offset(size.width * 0.12f, size.height * 0.55f),
                                radius = size.maxDimension * 0.42f
                            )
                        )
                    }
                    .padding(innerPadding),
                contentAlignment = Alignment.TopCenter
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 680.dp)
                ) {
                    when (activeTab) {
                        AppTab.LIBRARY -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // Studio Hero Banner Header with @username + Quick Library Actions
                                StudioHeroBanner(
                                    modifier = Modifier.padding(top = 8.dp),
                                    accentColor = MaterialTheme.colorScheme.primary
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            modifier = Modifier.weight(1f),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(44.dp)
                                                    .clip(RoundedCornerShape(14.dp))
                                                    .background(
                                                        Brush.linearGradient(
                                                            listOf(
                                                                MaterialTheme.colorScheme.primary,
                                                                MaterialTheme.colorScheme.secondary
                                                            )
                                                        )
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.MusicNote,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onPrimary
                                                )
                                            }
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = stringResource(R.string.music_player_title),
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "${allTracks.size} آهنگ در کتابخانه",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Surface(
                                                onClick = { isAccountDialogOpen = true },
                                                shape = RoundedCornerShape(14.dp),
                                                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.88f),
                                                modifier = Modifier
                                                    .height(40.dp)
                                                    .border(
                                                        1.dp,
                                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
                                                        RoundedCornerShape(14.dp)
                                                    )
                                                    .testTag("account_profile_button")
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 10.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = if (isGuestMode) {
                                                            Icons.Rounded.PersonOutline
                                                        } else {
                                                            Icons.Rounded.AlternateEmail
                                                        },
                                                        contentDescription = "حساب کاربری",
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Text(
                                                        text = userAccount.username,
                                                        style = MaterialTheme.typography.labelLarge,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    if (!isGuestMode) {
                                                        if (isSyncingDrive) {
                                                            CircularProgressIndicator(
                                                                modifier = Modifier.size(14.dp),
                                                                strokeWidth = 2.dp
                                                            )
                                                        } else {
                                                            Icon(
                                                                imageVector = if (isDriveAuthorized) {
                                                                    Icons.Rounded.CloudDone
                                                                } else {
                                                                    Icons.Rounded.CloudOff
                                                                },
                                                                contentDescription = null,
                                                                tint = if (isDriveAuthorized) {
                                                                    MaterialTheme.colorScheme.tertiary
                                                                } else {
                                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                                },
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            }

                                            Surface(
                                                shape = CircleShape,
                                                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.8f),
                                                modifier = Modifier.size(48.dp)
                                            ) {
                                                IconButton(
                                                    onClick = triggerScan,
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .testTag("scan_music_button")
                                                ) {
                                                    if (isScanning) {
                                                        CircularProgressIndicator(
                                                            modifier = Modifier.size(18.dp),
                                                            strokeWidth = 2.dp
                                                        )
                                                    } else {
                                                        Icon(
                                                            imageVector = Icons.Rounded.Refresh,
                                                            contentDescription = stringResource(R.string.action_scan_music),
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                    }
                                                }
                                            }
                                            Surface(
                                                shape = CircleShape,
                                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                                                modifier = Modifier.size(48.dp)
                                            ) {
                                                IconButton(
                                                    onClick = { audioPickerLauncher.launch(arrayOf("audio/*")) },
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .testTag("add_audio_button")
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.Add,
                                                        contentDescription = stringResource(R.string.action_add_files),
                                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                // Sleek Pill Search Bar
                                AvaSearchField(
                                    value = searchQuery,
                                    onValueChange = viewModel::setSearchQuery,
                                    placeholderText = stringResource(R.string.search_hint),
                                    voiceContentDescription = stringResource(R.string.voice_search_cd),
                                    onVoiceSearchClick = { launchVoiceSearch(AppTab.LIBRARY) },
                                    inputTestTag = "search_music_input",
                                    voiceTestTag = "voice_search_library_button"
                                )

                                // Styled Filter Chips + Android Auto Speed Volume Trigger
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TrackFilter.entries.forEach { filter ->
                                        val selected = activeFilter == filter
                                        FilterChip(
                                            selected = selected,
                                            onClick = { viewModel.setFilter(filter) },
                                            label = { Text(filter.labelFa) },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = if (filter == TrackFilter.FAVORITES) {
                                                        Icons.Rounded.Favorite
                                                    } else {
                                                        Icons.Rounded.LibraryMusic
                                                    },
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            },
                                            shape = RoundedCornerShape(14.dp),
                                            colors = FilterChipDefaults.filterChipColors(
                                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                                selectedLeadingIconColor = MaterialTheme.colorScheme.primary
                                            ),
                                            modifier = Modifier.testTag("filter_${filter.name}")
                                        )
                                    }

                                    FilterChip(
                                        selected = isDrivingModeOpen,
                                        onClick = {
                                            viewModel.syncSpeedVolumeBackgroundService()
                                            isDrivingModeOpen = true
                                        },
                                        label = { Text(stringResource(R.string.driving_mode_button)) },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Rounded.Speed,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        },
                                        shape = RoundedCornerShape(14.dp),
                                        colors = FilterChipDefaults.filterChipColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                            selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                            selectedLabelColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                            selectedLeadingIconColor = MaterialTheme.colorScheme.tertiary
                                        ),
                                        modifier = Modifier.testTag("driving_mode_button")
                                    )

                                    FilterChip(
                                        selected = autoSpeedVolumeState.isActiveForDriving &&
                                            autoSpeedVolumeState.hasLocationPermission,
                                        onClick = {
                                            viewModel.refreshAutoSpeedPermission()
                                            isAutoSpeedDialogOpen = true
                                        },
                                        label = { Text(stringResource(R.string.auto_speed_volume_chip)) },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Rounded.DirectionsCar,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        },
                                        shape = RoundedCornerShape(14.dp),
                                        colors = FilterChipDefaults.filterChipColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                            selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                            selectedLabelColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                            selectedLeadingIconColor = MaterialTheme.colorScheme.tertiary
                                        ),
                                        modifier = Modifier.testTag("auto_speed_volume_button")
                                    )
                                }

                                // Song List or Empty State
                                if (tracks.isEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxWidth(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(26.dp),
                                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 16.dp)
                                                .border(
                                                    1.dp,
                                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                                                    RoundedCornerShape(26.dp)
                                                )
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(24.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(12.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(64.dp)
                                                        .clip(RoundedCornerShape(20.dp))
                                                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = if (activeFilter == TrackFilter.FAVORITES) {
                                                            Icons.Rounded.Favorite
                                                        } else {
                                                            Icons.Rounded.MusicNote
                                                        },
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(34.dp)
                                                    )
                                                }
                                                Text(
                                                    text = if (activeFilter == TrackFilter.FAVORITES) {
                                                        stringResource(R.string.empty_favorites_title)
                                                    } else {
                                                        stringResource(R.string.empty_title)
                                                    },
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    textAlign = TextAlign.Center
                                                )
                                                Text(
                                                    text = if (activeFilter == TrackFilter.FAVORITES) {
                                                        stringResource(R.string.empty_favorites_subtitle)
                                                    } else {
                                                        stringResource(R.string.empty_subtitle)
                                                    },
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    textAlign = TextAlign.Center
                                                )

                                                if (activeFilter == TrackFilter.ALL) {
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Button(
                                                        onClick = triggerScan,
                                                        shape = RoundedCornerShape(14.dp),
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .height(48.dp)
                                                            .testTag("empty_scan_button")
                                                    ) {
                                                        Icon(Icons.Rounded.Refresh, contentDescription = null)
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Text(stringResource(R.string.action_scan_music))
                                                    }
                                                    OutlinedButton(
                                                        onClick = { audioPickerLauncher.launch(arrayOf("audio/*")) },
                                                        shape = RoundedCornerShape(14.dp),
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .height(48.dp)
                                                            .testTag("empty_pick_file_button")
                                                    ) {
                                                        Icon(Icons.Rounded.Add, contentDescription = null)
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Text(stringResource(R.string.action_add_files))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(bottom = 24.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        items(tracks, key = { it.id }) { track ->
                                            TrackRowItem(
                                                track = track,
                                                isCurrent = activeTrack?.id == track.id,
                                                isPlaying = playerState.isPlaying,
                                                onClick = { viewModel.playTrack(track) },
                                                onToggleFavorite = { viewModel.toggleFavorite(track) },
                                                onRemoveImported = if (track.isImported) {
                                                    { viewModel.removeImportedTrack(track) }
                                                } else null
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        AppTab.FAVORITES -> {
                            FavoritesScreen(
                                uiState = favoritesUiState,
                                searchQuery = favoritesSearchQuery,
                                currentTrackId = activeTrack?.id,
                                isPlaying = playerState.isPlaying,
                                isGuestMode = isGuestMode,
                                onSearchQueryChange = viewModel::setFavoritesSearchQuery,
                                onVoiceSearchClick = { launchVoiceSearch(AppTab.FAVORITES) },
                                onPlayTrack = viewModel::playFavoriteTrack,
                                onPlayAll = { viewModel.playAllFavorites(shuffle = false) },
                                onShuffleAll = { viewModel.playAllFavorites(shuffle = true) },
                                onRemoveFavorite = viewModel::toggleFavorite,
                                onRetryCloud = viewModel::retryCloudFavorites,
                                onUpgradeToGoogle = onUpgradeToGoogle
                            )
                        }

                        AppTab.ONLINE -> {
                            OnlineMusicScreen(
                                subSection = onlineSubSection,
                                onSelectSubSection = viewModel::selectOnlineSubSection,
                                trendingTracks = trendingTracks,
                                selectedCategory = trendingCategory,
                                selectedPreset = trendingPreset,
                                trendingSearchQuery = trendingSearchQuery,
                                isLoadingTrending = isLoadingTrending,
                                trendingError = trendingError,
                                onSelectTrendingCategory = viewModel::setTrendingCategory,
                                onSelectTrendingPreset = viewModel::setTrendingPreset,
                                onTrendingSearchQueryChange = viewModel::setTrendingSearchQuery,
                                onSubmitTrendingSearch = viewModel::submitTrendingSearch,
                                onClearTrendingSearch = viewModel::clearTrendingSearch,
                                onRefreshTrending = { viewModel.loadTrendingTracks(forceRefresh = true) },
                                onPlayTrendingTrack = viewModel::playTrendingTrack,
                                discoverTracks = discoverTracks,
                                selectedDiscoverStyle = discoverStyle,
                                discoverSearchQuery = discoverSearchQuery,
                                isLoadingDiscover = isLoadingDiscover,
                                discoverError = discoverError,
                                onSelectDiscoverStyle = viewModel::setDiscoverStyle,
                                onDiscoverSearchQueryChange = viewModel::setDiscoverSearchQuery,
                                onSubmitDiscoverSearch = viewModel::submitDiscoverSearch,
                                onClearDiscoverSearch = viewModel::clearDiscoverSearch,
                                onRefreshDiscover = { viewModel.loadDiscoverTracks(forceRefresh = true) },
                                onPlayDiscoverTrack = viewModel::playDiscoverTrack,
                                currentTrackId = activeTrack?.id,
                                isPlaying = playerState.isPlaying,
                                socialSummaries = trackSocialSummaries,
                                onVoiceSearchClick = { launchVoiceSearch(AppTab.ONLINE) },
                                onToggleFavorite = viewModel::toggleFavorite,
                                onToggleReaction = viewModel::toggleTrackReaction,
                                onOpenComments = viewModel::openTrackSocialSheet
                            )
                        }
                    }
                }
            }
        }

        AiVoiceSearchDialog(
            state = aiVoiceState,
            targetTab = voiceSearchTargetTab,
            onStopAndAnalyze = { viewModel.stopRecordingAndAnalyzeWithAi(voiceSearchTargetTab) },
            onCancelRecording = viewModel::cancelAiVoiceRecording,
            onRetryRecord = { launchVoiceSearch(voiceSearchTargetTab) },
            onRequestMicPermission = {
                recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            },
            onUseSystemSpeech = {
                viewModel.dismissAiVoiceSearch()
                launchSystemSpeechForAi()
            },
            onApplyToLibrary = { res ->
                viewModel.applyAiSearchResult(res, AppTab.LIBRARY, closeDialog = true)
            },
            onApplyToTrending = { res ->
                viewModel.applyAiSearchResult(res, AppTab.ONLINE, closeDialog = true)
            },
            onDismiss = viewModel::dismissAiVoiceSearch
        )

        if (isAccountDialogOpen) {
            AccountDriveDialog(
                username = userAccount.username,
                userEmail = userEmail,
                driveFileId = userAccount.driveFileId,
                isDriveAuthorized = isDriveAuthorized,
                isSyncingDrive = isSyncingDrive,
                driveSyncMessage = driveSyncMessage,
                favoriteCount = favoriteCount,
                isGuestMode = isGuestMode,
                onUpgradeToGoogle = {
                    isAccountDialogOpen = false
                    onUpgradeToGoogle()
                },
                diagnosticCount = diagnosticCount,
                onOpenDiagnostics = {
                    isAccountDialogOpen = false
                    onOpenDiagnostics()
                },
                onDismiss = { isAccountDialogOpen = false },
                onAuthorizeDrive = onRequestDriveAuth,
                onSaveToDrive = onSaveToDrive,
                onRestoreFromDrive = onRestoreFromDrive,
                onSignOut = {
                    isAccountDialogOpen = false
                    onSignOut()
                }
            )
        }

        if (isAutoSpeedDialogOpen) {
            AutoSpeedVolumeDialog(
                state = autoSpeedVolumeState,
                onToggleEnabled = { enabled ->
                    viewModel.setSpeedVolumeEnabled(enabled)
                    if (enabled && !AutoSpeedVolumeManager.hasLocationPermission(context)) {
                        requestAutoSpeedLocationPermission()
                    } else if (enabled) {
                        viewModel.syncSpeedVolumeBackgroundService()
                    }
                },
                onToggleOnlyAndroidAuto = { onlyAndroidAuto ->
                    viewModel.setOnlyWhenAndroidAutoConnected(onlyAndroidAuto)
                },
                onSelectSensitivity = viewModel::setSpeedVolumeSensitivity,
                onRequestLocationPermission = requestAutoSpeedLocationPermission,
                onDismiss = { isAutoSpeedDialogOpen = false },
                onOpenDrivingMode = {
                    viewModel.syncSpeedVolumeBackgroundService()
                    isDrivingModeOpen = true
                }
            )
        }

        AnimatedVisibility(
            visible = isNowPlayingOpen && activeTrack != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            if (activeTrack != null) {
                FullNowPlayingSheet(
                    track = activeTrack,
                    isPlaying = playerState.isPlaying,
                    positionMs = playerState.positionMs,
                    durationMs = playerState.durationMs,
                    shuffleEnabled = playerState.shuffleEnabled,
                    repeatMode = playerState.repeatMode,
                    onCollapse = { isNowPlayingOpen = false },
                    onTogglePlayPause = viewModel::togglePlayPause,
                    onSkipNext = viewModel::skipNext,
                    onSkipPrevious = viewModel::skipPrevious,
                    onSeekTo = viewModel::seekTo,
                    onToggleShuffle = viewModel::toggleShuffle,
                    onCycleRepeat = viewModel::cycleRepeatMode,
                    onToggleFavorite = { viewModel.toggleFavorite(activeTrack) },
                    isAutoSpeedActive = autoSpeedVolumeState.isActiveForDriving &&
                        autoSpeedVolumeState.hasLocationPermission,
                    onOpenAutoSpeedVolume = {
                        viewModel.refreshAutoSpeedPermission()
                        isAutoSpeedDialogOpen = true
                    },
                    onOpenDrivingMode = {
                        viewModel.syncSpeedVolumeBackgroundService()
                        isDrivingModeOpen = true
                    },
                    socialSummary = trackSocialSummaries[activeTrack.id] ?: TrackReactionSummary(),
                    onToggleReaction = { reaction ->
                        viewModel.toggleTrackReaction(activeTrack, reaction)
                    },
                    onOpenComments = {
                        viewModel.openTrackSocialSheet(activeTrack)
                    }
                )
            }
        }

        AnimatedVisibility(
            visible = isDrivingModeOpen,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            DrivingModeOverlay(
                speedState = autoSpeedVolumeState,
                currentTrack = activeTrack,
                isPlaying = playerState.isPlaying,
                progress = progress,
                onTogglePlayPause = viewModel::togglePlayPause,
                onSkipNext = viewModel::skipNext,
                onSkipPrevious = viewModel::skipPrevious,
                onToggleSpeedAuto = { enabled ->
                    viewModel.setSpeedVolumeEnabled(enabled)
                    if (enabled && !AutoSpeedVolumeManager.hasLocationPermission(context)) {
                        requestAutoSpeedLocationPermission()
                    } else if (enabled) {
                        viewModel.syncSpeedVolumeBackgroundService()
                    }
                },
                onSelectSensitivity = viewModel::setSpeedVolumeSensitivity,
                onRequestLocationPermission = requestAutoSpeedLocationPermission,
                onOpenSettings = {
                    viewModel.refreshAutoSpeedPermission()
                    isAutoSpeedDialogOpen = true
                },
                onClose = { isDrivingModeOpen = false }
            )
        }

        val socialTrack = activeSocialTrack
        if (socialTrack != null) {
            TrackSocialBottomSheet(
                track = socialTrack,
                summary = activeTrackSummary,
                comments = activeTrackComments,
                isLoadingComments = isLoadingComments,
                isSubmittingComment = isSubmittingComment,
                commentsError = commentsError,
                isGuestMode = isGuestMode,
                onToggleReaction = { reaction ->
                    viewModel.toggleTrackReaction(socialTrack, reaction)
                },
                onSubmitComment = { commentText ->
                    viewModel.submitTrackComment(socialTrack, commentText)
                },
                onDeleteComment = { commentId ->
                    viewModel.deleteTrackComment(socialTrack, commentId)
                },
                onUpgradeToGoogle = {
                    viewModel.closeTrackSocialSheet()
                    onUpgradeToGoogle()
                },
                onDismiss = viewModel::closeTrackSocialSheet
            )
        }
    }
}
