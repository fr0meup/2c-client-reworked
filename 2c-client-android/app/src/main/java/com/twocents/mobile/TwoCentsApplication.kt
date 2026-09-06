package com.twocents.mobile

import android.app.ActivityManager
import android.app.Application
import android.os.Build
import android.os.Process
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant
import java.time.format.DateTimeFormatter

/** Persistent, process-wide crash recorder installed before any Activity or Compose code runs. */
class TwoCentsApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppCrashLogs.install(this)
    }
}

object AppCrashLogs {
    private const val Tag = "TwoCentsCrash"
    private const val MaxFiles = 8
    private const val MaxTraceBytes = 256 * 1024
    private var installed = false

    @Synchronized
    fun install(application: Application) {
        if (installed) return
        installed = true
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { writeThrowable(application, thread, throwable) }
                .onFailure { Log.e(Tag, "Crash recorder failed", it) }
            previous?.uncaughtException(thread, throwable) ?: Process.killProcess(Process.myPid())
        }
        runCatching { recordPreviousProcessExit(application) }
            .onFailure { Log.w(Tag, "Unable to inspect previous process exit", it) }
    }

    private fun writeThrowable(application: Application, thread: Thread, throwable: Throwable) {
        val stack = StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }.toString()
        val report = buildString {
            appendLine("timestamp=${Instant.now()}")
            appendLine("kind=uncaught_exception")
            appendLine("process=${application.packageName}")
            appendLine("thread=${thread.name} (${thread.id})")
            appendLine("android=${Build.VERSION.RELEASE} sdk=${Build.VERSION.SDK_INT}")
            appendLine("device=${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("app=${appVersion(application)}")
            appendLine()
            append(stack.take(MaxTraceBytes))
        }
        Log.e(Tag, report)
        writeReport(application, "crash", report)
    }

    private fun recordPreviousProcessExit(application: Application) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val manager = application.getSystemService(ActivityManager::class.java) ?: return
        val exit = manager.getHistoricalProcessExitReasons(application.packageName, 0, 4)
            .firstOrNull { it.reason in setOf(
                android.app.ApplicationExitInfo.REASON_CRASH,
                android.app.ApplicationExitInfo.REASON_CRASH_NATIVE,
                android.app.ApplicationExitInfo.REASON_ANR,
            ) } ?: return
        val preferences = application.getSharedPreferences("crash-log-state", Application.MODE_PRIVATE)
        if (preferences.getLong("last_exit_timestamp", 0L) >= exit.timestamp) return
        val trace = runCatching {
            exit.traceInputStream?.bufferedReader()?.use { it.readText().take(MaxTraceBytes) }
        }.getOrNull().orEmpty()
        val report = buildString {
            appendLine("timestamp=${Instant.ofEpochMilli(exit.timestamp)}")
            appendLine("kind=previous_process_exit")
            appendLine("reason=${exit.reason}")
            appendLine("status=${exit.status}")
            appendLine("importance=${exit.importance}")
            appendLine("description=${exit.description.orEmpty()}")
            appendLine("app=${appVersion(application)}")
            if (trace.isNotBlank()) { appendLine(); append(trace) }
        }
        writeReport(application, "exit", report)
        preferences.edit().putLong("last_exit_timestamp", exit.timestamp).apply()
    }

    private fun writeReport(application: Application, prefix: String, report: String) {
        val directory = (application.getExternalFilesDir("crash-logs") ?: File(application.filesDir, "crash-logs"))
            .apply { mkdirs() }
        val stamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now()).replace(':', '-').replace('.', '-')
        File(directory, "$prefix-$stamp-${Process.myPid()}.log").writeText(report)
        directory.listFiles()?.filter(File::isFile)?.sortedByDescending(File::lastModified)
            ?.drop(MaxFiles)?.forEach(File::delete)
    }

    @Suppress("DEPRECATION")
    private fun appVersion(application: Application): String {
        val info = application.packageManager.getPackageInfo(application.packageName, 0)
        val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else info.versionCode.toLong()
        return "${info.versionName} ($code)"
    }
}
