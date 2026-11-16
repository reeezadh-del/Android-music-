package com.example.diagnostics

import android.content.Context
import android.os.Build
import com.example.BuildConfig
import com.google.firebase.FirebaseApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class DiagnosticReport(
    val reportId: String,
    val isFatal: Boolean,
    val tag: String,
    val errorType: String,
    val message: String,
    val stackTrace: String,
    val deviceInfo: String,
    val appVersion: String,
    val firebaseConfigured: Boolean,
    val timestampMs: Long,
    val acknowledged: Boolean = false,
    val syncedToCloud: Boolean = false
)

object CrashReporter {

    private const val LOG_FILE_NAME = "ava_diagnostic_logs.json"
    private const val MAX_REPORTS = 20
    private const val MAX_STACK_TRACE_CHARS = 5000
    private const val MAX_MESSAGE_CHARS = 900

    private val lock = Any()
    private val inHandler = ThreadLocal<Boolean>()

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var installed = false

    @Volatile
    private var previousHandler: Thread.UncaughtExceptionHandler? = null

    private val _logsFlow = MutableStateFlow<List<DiagnosticReport>>(emptyList())
    val logsFlow: StateFlow<List<DiagnosticReport>> = _logsFlow.asStateFlow()

    fun install(context: Context) {
        val appCtx = context.applicationContext ?: context
        appContext = appCtx
        synchronized(lock) {
            _logsFlow.value = readReportsLocked(appCtx)
            if (installed) return
            installed = true
            val existing = Thread.getDefaultUncaughtExceptionHandler()
            if (existing !is AvaUncaughtHandler) {
                previousHandler = existing
                Thread.setDefaultUncaughtExceptionHandler(AvaUncaughtHandler())
            }
        }
    }

    fun recordFatalCrash(
        context: Context,
        throwable: Throwable,
        threadName: String = Thread.currentThread().name
    ): DiagnosticReport {
        val appCtx = context.applicationContext ?: context
        val report = buildReport(
            context = appCtx,
            isFatal = true,
            tag = "UNCAUGHT_CRASH ($threadName)",
            throwable = throwable,
            customMessage = throwable.message
        )
        synchronized(lock) {
            val current = readReportsLocked(appCtx).toMutableList()
            current.add(0, report)
            val trimmed = current.take(MAX_REPORTS)
            writeReportsLocked(appCtx, trimmed, fsync = true)
            _logsFlow.value = trimmed
        }
        return report
    }

    fun recordNonFatal(
        tag: String,
        throwable: Throwable? = null,
        message: String? = null,
        context: Context? = appContext
    ) {
        val appCtx = context?.applicationContext ?: context ?: appContext ?: return
        val combinedMessage = listOfNotNull(
            message?.takeIf { it.isNotBlank() },
            throwable?.message?.takeIf { it.isNotBlank() }
        ).distinct().joinToString(" | ")

        val report = buildReport(
            context = appCtx,
            isFatal = false,
            tag = tag,
            throwable = throwable,
            customMessage = combinedMessage.ifBlank { tag }
        )
        synchronized(lock) {
            val current = readReportsLocked(appCtx).toMutableList()
            current.add(0, report)
            val trimmed = current.take(MAX_REPORTS)
            writeReportsLocked(appCtx, trimmed, fsync = false)
            _logsFlow.value = trimmed
        }
    }

    fun getReports(context: Context): List<DiagnosticReport> {
        val appCtx = context.applicationContext ?: context
        synchronized(lock) {
            val loaded = readReportsLocked(appCtx)
            _logsFlow.value = loaded
            return loaded
        }
    }

    fun getPendingFatalCrash(context: Context): DiagnosticReport? {
        return getReports(context).firstOrNull { it.isFatal && !it.acknowledged }
    }

    fun getUnsyncedReports(context: Context): List<DiagnosticReport> {
        return getReports(context).filter { !it.syncedToCloud }
    }

    fun acknowledgeAllFatalCrashes(context: Context) {
        val appCtx = context.applicationContext ?: context
        synchronized(lock) {
            val updated = readReportsLocked(appCtx).map { report ->
                if (report.isFatal && !report.acknowledged) report.copy(acknowledged = true) else report
            }
            writeReportsLocked(appCtx, updated, fsync = false)
            _logsFlow.value = updated
        }
    }

    fun markSyncedToCloud(context: Context, syncedIds: Set<String>) {
        if (syncedIds.isEmpty()) return
        val appCtx = context.applicationContext ?: context
        synchronized(lock) {
            val updated = readReportsLocked(appCtx).map { report ->
                if (report.reportId in syncedIds) report.copy(syncedToCloud = true) else report
            }
            writeReportsLocked(appCtx, updated, fsync = false)
            _logsFlow.value = updated
        }
    }

    fun clearAllReports(context: Context) {
        val appCtx = context.applicationContext ?: context
        synchronized(lock) {
            logFile(appCtx).delete()
            _logsFlow.value = emptyList()
        }
    }

    fun formatSingleReport(report: DiagnosticReport): String {
        val formattedTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            .format(Date(report.timestampMs))
        return buildString {
            appendLine("=== گزارش خطای پخش‌کننده آوا ===")
            appendLine("ID: ${report.reportId}")
            appendLine("نوع: ${if (report.isFatal) "CRASH (بسته شدن ناگهانی)" else "NON-FATAL ERROR"}")
            appendLine("زمان: $formattedTime")
            appendLine("بخش: ${report.tag}")
            appendLine("نسخه برنامه: ${report.appVersion}")
            appendLine("دستگاه: ${report.deviceInfo}")
            appendLine("وضعیت Firebase: ${if (report.firebaseConfigured) "فعال" else "پیکربندی نشده"}")
            appendLine("کلاس خطا: ${report.errorType}")
            appendLine("پیام: ${report.message}")
            appendLine("--- StackTrace ---")
            appendLine(report.stackTrace)
        }.trim()
    }

    fun formatAllReports(reports: List<DiagnosticReport>): String {
        if (reports.isEmpty()) return "هیچ خطایی ثبت نشده است."
        return reports.take(5).joinToString("\n\n") { formatSingleReport(it) }
    }

    private fun buildReport(
        context: Context,
        isFatal: Boolean,
        tag: String,
        throwable: Throwable?,
        customMessage: String?
    ): DiagnosticReport {
        val rawTrace = if (throwable != null) {
            val sw = StringWriter()
            PrintWriter(sw).use { pw -> throwable.printStackTrace(pw) }
            sw.toString()
        } else {
            val callerTrace = Throwable("Diagnostic snapshot: $tag")
            val sw = StringWriter()
            PrintWriter(sw).use { pw -> callerTrace.printStackTrace(pw) }
            sw.toString()
        }

        val errorClass = throwable?.javaClass?.name ?: "DiagnosticEvent"
        val abi = Build.SUPPORTED_ABIS?.firstOrNull().orEmpty().ifBlank { "unknown" }
        val manufacturer = Build.MANUFACTURER.orEmpty().trim()
        val model = Build.MODEL.orEmpty().trim()
        val release = Build.VERSION.RELEASE.orEmpty().ifBlank { "?" }
        val deviceSummary = "$manufacturer $model | Android $release (API ${Build.VERSION.SDK_INT}) | $abi"
            .trim()
            .take(290)
        val appVer = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})".take(60)
        val firebaseReady = runCatching { FirebaseApp.getApps(context).isNotEmpty() }.getOrDefault(false)
        val safeId = "rep_${System.currentTimeMillis()}_${UUID.randomUUID().toString().replace("-", "").take(8)}"

        return DiagnosticReport(
            reportId = safeId,
            isFatal = isFatal,
            tag = tag.take(60),
            errorType = errorClass.take(155),
            message = customMessage.orEmpty().take(MAX_MESSAGE_CHARS),
            stackTrace = rawTrace.take(MAX_STACK_TRACE_CHARS),
            deviceInfo = deviceSummary,
            appVersion = appVer,
            firebaseConfigured = firebaseReady,
            timestampMs = System.currentTimeMillis(),
            acknowledged = !isFatal,
            syncedToCloud = false
        )
    }

    private fun logFile(context: Context): File = File(context.filesDir, LOG_FILE_NAME)

    private fun readReportsLocked(context: Context): List<DiagnosticReport> {
        val file = logFile(context)
        if (!file.exists() || file.length() == 0L) return emptyList()
        return runCatching {
            val raw = file.readText(Charsets.UTF_8)
            val arr = JSONArray(raw)
            buildList(arr.length()) {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    add(
                        DiagnosticReport(
                            reportId = obj.optString("reportId"),
                            isFatal = obj.optBoolean("isFatal", false),
                            tag = obj.optString("tag"),
                            errorType = obj.optString("errorType"),
                            message = obj.optString("message"),
                            stackTrace = obj.optString("stackTrace"),
                            deviceInfo = obj.optString("deviceInfo"),
                            appVersion = obj.optString("appVersion"),
                            firebaseConfigured = obj.optBoolean("firebaseConfigured", false),
                            timestampMs = obj.optLong("timestampMs", 0L),
                            acknowledged = obj.optBoolean("acknowledged", true),
                            syncedToCloud = obj.optBoolean("syncedToCloud", false)
                        )
                    )
                }
            }.filter { it.reportId.isNotBlank() }
        }.getOrDefault(emptyList())
    }

    private fun writeReportsLocked(context: Context, reports: List<DiagnosticReport>, fsync: Boolean) {
        runCatching {
            val file = logFile(context)
            file.parentFile?.mkdirs()
            val arr = JSONArray()
            reports.forEach { r ->
                arr.put(
                    JSONObject().apply {
                        put("reportId", r.reportId)
                        put("isFatal", r.isFatal)
                        put("tag", r.tag)
                        put("errorType", r.errorType)
                        put("message", r.message)
                        put("stackTrace", r.stackTrace)
                        put("deviceInfo", r.deviceInfo)
                        put("appVersion", r.appVersion)
                        put("firebaseConfigured", r.firebaseConfigured)
                        put("timestampMs", r.timestampMs)
                        put("acknowledged", r.acknowledged)
                        put("syncedToCloud", r.syncedToCloud)
                    }
                )
            }
            val bytes = arr.toString().toByteArray(Charsets.UTF_8)
            FileOutputStream(file, false).use { out ->
                out.write(bytes)
                out.flush()
                if (fsync) {
                    runCatching { out.fd.sync() }
                }
            }
        }
    }

    private class AvaUncaughtHandler : Thread.UncaughtExceptionHandler {
        override fun uncaughtException(thread: Thread, throwable: Throwable) {
            if (inHandler.get() == true) {
                previousHandler?.uncaughtException(thread, throwable)
                return
            }
            inHandler.set(true)
            try {
                appContext?.let { ctx ->
                    recordFatalCrash(ctx, throwable, thread.name)
                }
            } catch (_: Throwable) {
                // Never mask original exception
            } finally {
                inHandler.set(false)
                previousHandler?.uncaughtException(thread, throwable)
            }
        }
    }
}
