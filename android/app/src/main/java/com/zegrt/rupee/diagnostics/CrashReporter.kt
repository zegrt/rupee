package com.zegrt.rupee.diagnostics

import android.content.Context
import android.os.Build
import com.zegrt.rupee.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant

/**
 * Single-tester crash capture without an SDK. Installs a global uncaught-exception
 * handler that appends a stacktrace to a file in the app's internal storage. The
 * Debug screen exposes "view" and "email" actions; users can ship the log to the
 * dev email address.
 *
 * Not a substitute for Crashlytics/Sentry once Rupee has more than a few testers,
 * but lets us collect signal from friends-and-family without setting up a Firebase
 * project or asking testers to install anything extra.
 */
object CrashReporter {

    private const val FILE = "crash-log.txt"
    private const val MAX_LINES = 4_000 // keep approx the most recent entries

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { append(appContext, thread, throwable) }
            // Delegate process termination to the existing handler (Android's default
            // UEH does this). Avoids overriding any custom recovery a host runtime
            // may have installed.
            previousHandler?.uncaughtException(thread, throwable)
        }
    }

    fun logFile(context: Context): File = File(context.applicationContext.filesDir, FILE)

    /**
     * Read the crash log. Returns:
     *   - `Result.success("")` if the file doesn't exist (no crashes yet)
     *   - `Result.success(content)` if the file exists and reads cleanly
     *   - `Result.failure(throwable)` if the file exists but can't be read
     *
     * The old API returned `""` for both "no crashes" and "read failed",
     * which meant the debug screen couldn't tell a user with no log apart
     * from one whose file is permission-denied or corrupted. Callers that
     * don't care about the distinction can fall back via [readLogOrEmpty].
     */
    fun readLog(context: Context): Result<String> {
        val file = logFile(context)
        if (!file.exists()) return Result.success("")
        return runCatching { file.readText() }
    }

    /**
     * Convenience for call sites that genuinely don't care which kind of
     * empty they got. Preserves the pre-L3 API shape for anything that
     * isn't ready to handle the Result.
     */
    fun readLogOrEmpty(context: Context): String =
        readLog(context).getOrDefault("")

    fun clearLog(context: Context) {
        runCatching { logFile(context).delete() }
    }

    private fun append(context: Context, thread: Thread, throwable: Throwable) {
        val file = logFile(context)
        // Trim line-wise rather than byte-wise so we never split a UTF-8 codepoint or
        // a mid-entry stack frame. Cheap on a few-MB worst case.
        if (file.exists()) {
            val lines = file.readLines()
            if (lines.size > MAX_LINES) {
                file.writeText(lines.takeLast(MAX_LINES / 2).joinToString("\n") + "\n")
            }
        }
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        val entry = buildString {
            appendLine("---")
            appendLine("when: ${Instant.now()}")
            appendLine("app: ${BuildConfig.VERSION_NAME} (code ${BuildConfig.VERSION_CODE})")
            appendLine("debug: ${BuildConfig.DEBUG}")
            appendLine("thread: ${thread.name}")
            appendLine("device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("android: ${Build.VERSION.RELEASE} (sdk ${Build.VERSION.SDK_INT})")
            append(sw.toString())
        }
        file.appendText(entry)
    }
}
