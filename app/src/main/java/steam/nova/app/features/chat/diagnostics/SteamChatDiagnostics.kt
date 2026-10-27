package steam.nova.app.features.chat.diagnostics

import android.os.Process
import android.os.SystemClock
import android.util.Log
import steam.nova.app.features.chat.network.SteamChatFailure
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

    fun effect(stage: String, detail: String = "") {
        write(Log.INFO, "effect.$stage", "[chat-effect-diag-1] thread=${operation(Thread.currentThread().name)} $detail", 0)
    }

    /** Messages are allowed only for the public-resource effect pipeline, never authenticated chat I/O. */
    fun effectFailure(stage: String, error: Throwable, detail: String = "", publicResource: Boolean = false) {
        runCatching {
            val event = System.nanoTime().toString(36)
            val prefix = "[chat-effect-diag-1] failure=$event"
            write(Log.WARN, "effect.$stage.failed", "$prefix thread=${operation(Thread.currentThread().name)} $detail ${summary(error)}", 0)
            var cause: Throwable? = error
            val seen = HashSet<Throwable>()
            repeat(8) { depth ->
                val current = cause ?: return@runCatching
                if (!seen.add(current)) return@runCatching
                val message = if (publicResource) current.message.orEmpty()
                    .replace(Regex("https?://\\S+"), "[url]")
                    .map { if (it.isISOControl()) ' ' else it }.joinToString("").take(600) else "omitted"
                write(Log.WARN, "effect.$stage.cause", "$prefix cause=$depth type=${current.javaClass.name} message=$message", 0)
                current.stackTrace.take(48).toList().chunked(3).forEachIndexed { part, frames ->
                    write(Log.WARN, "effect.$stage.stack", "$prefix cause=$depth part=$part ${frames.joinToString(" < ")}", 0)
                }
                cause = current.cause
            }
        }
    }

    fun <T> effectTrace(stage: String, detail: String = "", block: () -> T): T {
        val started = SystemClock.elapsedRealtime()
        val step = System.nanoTime().toString(36)
        effect("$stage.begin", "step=$step $detail")
        try {
            return block().also { effect("$stage.end", "step=$step elapsedMs=${SystemClock.elapsedRealtime() - started} $detail") }
        } catch (error: Throwable) {
            effect("$stage.failed", "step=$step elapsedMs=${SystemClock.elapsedRealtime() - started} $detail ${summary(error)}")
            throw error
        }
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
