package com.millennium.app.features.chat.diagnostics

import android.os.Process
import android.os.SystemClock
import android.util.Log
import com.millennium.app.features.chat.network.SteamChatFailure
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.CompletionException
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeoutException

/** Diagnostic metadata only: never pass credentials, URLs, request bodies or message text. */
internal object SteamChatDiagnostics {
    private const val TAG = "MillenniumChat"
    @Volatile private var logger: ((Int, String) -> Unit)? = null
    private val recent = linkedMapOf<String, Long>()

    fun install(logger: (Int, String) -> Unit) { this.logger = logger }

    fun operation(value: String): String = value.takeIf {
        it.length in 1..80 && it.all { c -> c.isLetterOrDigit() || c in "._#" }
    } ?: "unknown"

    fun quiet(operation: String): Boolean = operation in setOf("events", "visibility", "threadState", "uploadProgress")

    fun info(stage: String, detail: String = "", quiet: Boolean = false) {
        write(Log.INFO, stage, detail, if (quiet) 15_000 else 0)
    }

    fun failure(stage: String, error: Throwable) {
        write(Log.WARN, stage, summary(error), 5_000)
    }

    fun unwrap(error: Throwable): Throwable {
        var current = error
        repeat(8) {
            if (current !is ExecutionException && current !is CompletionException && current !is InvocationTargetException) return current
            val cause = current.cause ?: return current
            if (cause === current) return current
            current = cause
        }
        return current
    }

    fun errorCode(error: Throwable): String = when (val cause = unwrap(error)) {
        is SteamChatFailure -> cause.code
        is TimeoutException -> "TIMEOUT"
        else -> "REQUEST_FAILED"
    }

    fun summary(error: Throwable): String {
        val cause = unwrap(error)
        val frames = cause.stackTrace.take(6).joinToString("<") { "${it.className}.${it.methodName}:${it.lineNumber}" }
        // Throwable.message and Throwable.toString() can contain authenticated request URLs.
        return "code=${operation(errorCode(error))} type=${cause.javaClass.name} wrapper=${error.javaClass.simpleName} frames=[$frames]"
    }

    fun <T> trace(stage: String, quiet: Boolean = false, block: () -> T): T {
        val started = SystemClock.elapsedRealtime()
        info("$stage.begin", quiet = quiet)
        try {
            return block().also { info("$stage.end", "elapsedMs=${SystemClock.elapsedRealtime() - started}", quiet) }
        } catch (error: Throwable) {
            failure("$stage.failed", error)
            throw error
        }
    }

    private fun write(priority: Int, stage: String, detail: String, interval: Long) {
        // Logging must not change the outcome of a chat request.
        runCatching {
            val now = SystemClock.elapsedRealtime()
            if (interval > 0) synchronized(recent) {
                val key = "$stage:$priority"
                val previous = recent[key]
                if (previous != null && now - previous < interval) return
                recent[key] = now
                while (recent.size > 256) recent.remove(recent.keys.first())
            }
            val line = "[chat-diag-3] pid=${Process.myPid()} $stage $detail"
            val sink = logger
            if (sink == null) Log.println(priority, TAG, line) else sink(priority, line)
        }
    }
}
