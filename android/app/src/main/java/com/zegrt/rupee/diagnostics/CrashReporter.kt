package com.zegrt.rupee.diagnostics

import android.content.Context
import com.zegrt.rupee.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant
import kotlin.system.exitProcess

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
    private const val MAX_BYTES = 256 * 1024 // 256 KB; trim oldest if larger

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { append(appContext, thread, throwable) }
            previousHandler?.uncaughtException(thread, throwable)
            // If the previous handler didn't kill us (rare), bail explicitly so we
            // don't leave the process in a half-dead state.
            exitProcess(2)
        }
    }

    fun logFile(context: Context): File = File(context.applicationContext.filesDir, FILE)

    fun readLog(context: Context): String = runCatching { logFile(context).readText() }
        .getOrDefault("")

    fun clearLog(context: Context) {
        runCatching { logFile(context).delete() }
    }

    private fun append(context: Context, thread: Thread, throwable: Throwable) {
        val file = logFile(context)
        // Trim file from the front if it's getting big; keep approx the last MAX_BYTES.
        if (file.exists() && file.length() > MAX_BYTES) {
            val keep = file.readBytes().takeLast(MAX_BYTES / 2).toByteArray()
            file.writeBytes(keep)
        }
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        val entry = buildString {
            appendLine("---")
            appendLine("when: ${Instant.now()}")
            appendLine("app: ${BuildConfig.VERSION_NAME} (code ${BuildConfig.VERSION_CODE})")
            appendLine("debug: ${BuildConfig.DEBUG}")
            appendLine("thread: ${thread.name}")
            appendLine("device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
            appendLine("android: ${android.os.Build.VERSION.RELEASE} (sdk ${android.os.Build.VERSION.SDK_INT})")
            append(sw.toString())
        }
        file.appendText(entry)
    }
}
