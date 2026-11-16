package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.rounded.AlternateEmail
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.auth.UserAccountRepository

@Composable
fun AuthGateScreen(
    isSignedInWithGoogle: Boolean,
    userEmail: String?,
    isBusy: Boolean,
    statusLabel: String?,
    errorMessage: String?,
    usernameInput: String,
    usernameError: String?,
    isCheckingAvailability: Boolean,
    isUsernameAvailable: Boolean?,
    isDriveAuthorized: Boolean,
    onGoogleSignInClick: () -> Unit,
    onUsernameChange: (String) -> Unit,
    onAuthorizeDriveClick: () -> Unit,
    onRegisterUsernameSubmit: () -> Unit,
    onSwitchGoogleAccount: () -> Unit,
    diagnosticCount: Int = 0,
    onOpenDiagnostics: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.14f),
                            MaterialTheme.colorScheme.background
                        )
                    )
                )
                .padding(innerPadding)
                .imePadding()
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            if (diagnosticCount > 0) {
                IconButton(
                    onClick = onOpenDiagnostics,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 8.dp)
                        .size(48.dp)
                        .testTag("auth_diagnostics_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.BugReport,
                        contentDescription = stringResource(R.string.diagnostics_title),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Animated App Logo & Name
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    AnimatedAuthLogo()

                    Text(
                        text = stringResource(R.string.music_player_title),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                if (!isSignedInWithGoogle) {
                    Button(
                        onClick = onGoogleSignInClick,
                        enabled = !isBusy,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("google_sign_in_button")
                    ) {
                        if (isBusy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(statusLabel ?: "در حال اتصال…")
                        } else {
                            Icon(Icons.AutoMirrored.Rounded.Login, contentDescription = null)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = stringResource(R.string.auth_google_sign_in),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else {
                    // Step 2: Unique <=4 char username creation after Google Sign-In
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (!userEmail.isNullOrBlank()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = userEmail,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(
                                    onClick = onSwitchGoogleAccount,
                                    enabled = !isBusy,
                                    modifier = Modifier.testTag("switch_google_account_button")
                                ) {
                                    Text(
                                        text = stringResource(R.string.auth_switch_account),
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = usernameInput,
                            onValueChange = { raw ->
                                if (raw.length <= UserAccountRepository.MAX_USERNAME_LENGTH) {
                                    onUsernameChange(raw)
                                }
                            },
                            label = { Text(stringResource(R.string.auth_username_label)) },
                            placeholder = { Text(stringResource(R.string.auth_username_hint)) },
                            leadingIcon = {
                                Icon(Icons.Rounded.AlternateEmail, contentDescription = null)
                            },
                            trailingIcon = {
                                when {
                                    isCheckingAvailability -> {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp
                                        )
                                    }
                                    isUsernameAvailable == true && usernameError == null -> {
                                        Icon(
                                            imageVector = Icons.Rounded.CheckCircle,
                                            contentDescription = "قابل استفاده",
                                            tint = MaterialTheme.colorScheme.tertiary
                                        )
                                    }
                                    usernameError != null || isUsernameAvailable == false -> {
                                        Icon(
                                            imageVector = Icons.Rounded.ErrorOutline,
                                            contentDescription = "نامعتبر یا تکراری",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            },
                            supportingText = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    val helperText = when {
                                        usernameError != null -> usernameError
                                        isUsernameAvailable == true -> "این نام کاربری آزاد است"
                                        isUsernameAvailable == false -> "این نام کاربری قبلاً انتخاب شده است"
                                        else -> stringResource(R.string.auth_username_helper)
                                    }
                                    Text(
                                        text = helperText,
                                        color = when {
                                            usernameError != null || isUsernameAvailable == false ->
                                                MaterialTheme.colorScheme.error
                                            isUsernameAvailable == true ->
                                                MaterialTheme.colorScheme.tertiary
                                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = "${usernameInput.length}/${UserAccountRepository.MAX_USERNAME_LENGTH}",
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            },
                            isError = usernameError != null || isUsernameAvailable == false,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.None,
                                keyboardType = KeyboardType.Ascii,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    focusManager.clearFocus()
                                    onRegisterUsernameSubmit()
                                }
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("username_register_input")
                        )

                        if (!isDriveAuthorized) {
                            OutlinedButton(
                                onClick = onAuthorizeDriveClick,
                                enabled = !isBusy,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("authorize_drive_button")
                            ) {
                                Icon(Icons.Rounded.Lock, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("تأیید دسترسی گوگل درایو")
                            }
                        }

                        Button(
                            onClick = {
                                focusManager.clearFocus()
                                onRegisterUsernameSubmit()
                            },
                            enabled = !isBusy &&
                                usernameInput.isNotBlank() &&
                                usernameError == null &&
                                isUsernameAvailable != false,
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .testTag("register_username_button")
                        ) {
                            if (isBusy) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(statusLabel ?: "در حال ثبت…")
                            } else {
                                Icon(Icons.Rounded.CloudDone, contentDescription = null)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = stringResource(R.string.auth_register_submit),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                AnimatedVisibility(visible = !errorMessage.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = errorMessage.orEmpty(),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AnimatedAuthLogo(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "auth_logo_motion")

    val scale by transition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "logo_breathe"
    )

    val floatOffsetPx by transition.animateFloat(
        initialValue = -8f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "logo_float"
    )

    val ringRotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring_rotation"
    )

    val wavePulseA by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_pulse_a"
    )

    val wavePulseB by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
            initialStartOffset = StartOffset(1300)
        ),
        label = "wave_pulse_b"
    )

    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary

    Box(
        modifier = modifier.size(164.dp),
        contentAlignment = Alignment.Center
    ) {
        // Expanding sound-wave rings behind the logo
        Canvas(modifier = Modifier.fillMaxSize()) {
            val baseRadius = size.minDimension * 0.34f
            val maxExtra = size.minDimension * 0.16f

            drawCircle(
                color = primaryColor.copy(alpha = (1f - wavePulseA) * 0.36f),
                radius = baseRadius + maxExtra * wavePulseA,
                style = Stroke(width = 2.5.dp.toPx())
            )
            drawCircle(
                color = secondaryColor.copy(alpha = (1f - wavePulseB) * 0.28f),
                radius = baseRadius + maxExtra * wavePulseB,
                style = Stroke(width = 2.dp.toPx())
            )
        }

        // Rotating sweep-gradient aura frame
        Box(
            modifier = Modifier
                .size(120.dp)
                .graphicsLayer {
                    translationY = floatOffsetPx
                    scaleX = scale
                    scaleY = scale
                    rotationZ = ringRotation
                }
                .clip(RoundedCornerShape(34.dp))
                .background(
                    Brush.sweepGradient(
                        listOf(
                            primaryColor.copy(alpha = 0.85f),
                            secondaryColor.copy(alpha = 0.65f),
                            tertiaryColor.copy(alpha = 0.5f),
                            primaryColor.copy(alpha = 0.85f)
                        )
                    )
                )
        )

        // Floating crisp app logo inside the glowing frame
        Box(
            modifier = Modifier
                .size(110.dp)
                .graphicsLayer {
                    translationY = floatOffsetPx
                    scaleX = scale
                    scaleY = scale
                }
                .clip(RoundedCornerShape(30.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(30.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_ava_music_fg_1794792983705),
                contentDescription = stringResource(R.string.music_player_title),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
