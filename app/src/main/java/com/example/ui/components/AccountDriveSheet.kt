package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AlternateEmail
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R

@Composable
fun AccountDriveDialog(
    username: String,
    userEmail: String,
    driveFileId: String?,
    isDriveAuthorized: Boolean,
    isSyncingDrive: Boolean,
    driveSyncMessage: String?,
    favoriteCount: Int,
    diagnosticCount: Int = 0,
    onOpenDiagnostics: () -> Unit = {},
    onDismiss: () -> Unit,
    onAuthorizeDrive: () -> Unit,
    onSaveToDrive: () -> Unit,
    onRestoreFromDrive: () -> Unit,
    onSignOut: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Storage,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(R.string.drive_account_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AlternateEmail,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = username,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.testTag("account_dialog_username")
                            )
                        }
                        if (userEmail.isNotBlank()) {
                            Text(
                                text = userEmail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "تعداد علاقه‌مندی‌های ذخیره شده: $favoriteCount آهنگ",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (!driveFileId.isNullOrBlank()) {
                                "دیتابیس SQLite در گوگل درایو فعال است"
                            } else {
                                "دیتابیس هنوز روی گوگل درایو آپلود نشده است"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }

                if (!driveSyncMessage.isNullOrBlank()) {
                    Text(
                        text = driveSyncMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                if (!isDriveAuthorized) {
                    Button(
                        onClick = onAuthorizeDrive,
                        enabled = !isSyncingDrive,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("dialog_authorize_drive_button")
                    ) {
                        Icon(Icons.Rounded.LockOpen, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.drive_authorize_button))
                    }
                } else {
                    Button(
                        onClick = onSaveToDrive,
                        enabled = !isSyncingDrive,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("dialog_save_drive_button")
                    ) {
                        if (isSyncingDrive) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("در حال همگام‌سازی…")
                        } else {
                            Icon(Icons.Rounded.CloudUpload, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.drive_sync_save))
                        }
                    }

                    OutlinedButton(
                        onClick = onRestoreFromDrive,
                        enabled = !isSyncingDrive,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("dialog_restore_drive_button")
                    ) {
                        Icon(Icons.Rounded.CloudDownload, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.drive_sync_restore))
                    }
                }

                OutlinedButton(
                    onClick = onOpenDiagnostics,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("dialog_diagnostics_button")
                ) {
                    Icon(Icons.Rounded.BugReport, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (diagnosticCount > 0) {
                            "${stringResource(R.string.diagnostics_title)} ($diagnosticCount)"
                        } else {
                            stringResource(R.string.diagnostics_title)
                        }
                    )
                }

                OutlinedButton(
                    onClick = onSignOut,
                    enabled = !isSyncingDrive,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("dialog_sign_out_button")
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.auth_sign_out))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("بستن")
            }
        }
    )
}
