package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.diagnostics.CrashReporter
import com.example.diagnostics.DiagnosticReport
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun copyReportToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    cm.setPrimaryClip(ClipData.newPlainText("AvaMusicCrashReport", text))
}

private fun shareReportText(context: Context, text: String) {
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "گزارش خطای Ava Music")
        putExtra(Intent.EXTRA_TEXT, text)
    }
    val chooser = Intent.createChooser(sendIntent, "ارسال گزارش خطا")
    chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(chooser) }
}

@Composable
fun CrashReportDialog(
    report: DiagnosticReport,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val formattedReport = remember(report.reportId) { CrashReporter.formatSingleReport(report) }
    var copied by remember(report.reportId) { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.WarningAmber,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
                Text(
                    text = stringResource(R.string.crash_dialog_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.crash_dialog_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = report.errorType,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                        if (report.message.isNotBlank()) {
                            Text(
                                text = report.message,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = report.deviceInfo,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 80.dp, max = 140.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant,
                            RoundedCornerShape(12.dp)
                        )
                        .verticalScroll(rememberScrollState())
                        .horizontalScroll(rememberScrollState())
                        .padding(10.dp)
                ) {
                    Text(
                        text = formattedReport,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    copyReportToClipboard(context, formattedReport)
                    copied = true
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .height(48.dp)
                    .testTag("crash_copy_button")
            ) {
                Icon(
                    imageVector = if (copied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (copied) "کپی شد" else stringResource(R.string.crash_copy_action))
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                    onClick = { shareReportText(context, formattedReport) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("crash_share_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Share,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.crash_share_action))
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("crash_dismiss_button")
                ) {
                    Text("بستن")
                }
            }
        }
    )
}

@Composable
fun DiagnosticsHistoryDialog(
    reports: List<DiagnosticReport>,
    onClearLogs: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var copiedAll by remember(reports.size) { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.BugReport,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(R.string.diagnostics_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (reports.isEmpty()) {
                    Text(
                        text = stringResource(R.string.diagnostics_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 210.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(reports, key = { it.reportId }) { report ->
                            val timeText = remember(report.timestampMs) {
                                SimpleDateFormat("MM-dd HH:mm:ss", Locale.US)
                                    .format(Date(report.timestampMs))
                            }
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = if (report.isFatal) "CRASH • ${report.tag}" else report.tag,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = if (report.isFatal) {
                                                MaterialTheme.colorScheme.error
                                            } else {
                                                MaterialTheme.colorScheme.primary
                                            },
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (report.syncedToCloud) {
                                            Icon(
                                                imageVector = Icons.Rounded.CloudDone,
                                                contentDescription = "همگام‌شده در ابر",
                                                tint = MaterialTheme.colorScheme.tertiary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = "${report.errorType}: ${report.message}",
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "$timeText • ${report.deviceInfo}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                copyReportToClipboard(context, CrashReporter.formatAllReports(reports))
                                copiedAll = true
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("diagnostics_copy_all_button")
                        ) {
                            Icon(
                                imageVector = if (copiedAll) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (copiedAll) "کپی شد" else "کپی گزارش‌ها")
                        }

                        OutlinedButton(
                            onClick = { shareReportText(context, CrashReporter.formatAllReports(reports)) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("diagnostics_share_all_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Share,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("ارسال")
                        }
                    }

                    TextButton(
                        onClick = onClearLogs,
                        modifier = Modifier
                            .align(Alignment.End)
                            .testTag("diagnostics_clear_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteSweep,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.diagnostics_clear))
                    }
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
