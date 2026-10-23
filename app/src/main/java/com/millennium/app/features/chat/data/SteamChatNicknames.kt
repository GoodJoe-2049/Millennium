package com.millennium.app.features.chat.data

import com.millennium.app.features.chat.protocol.SteamChatProtocol as P
import org.json.JSONObject
import java.util.concurrent.Executors

/** Friend remarks are account-scoped and never delay opening a conversation. */
internal class SteamChatNicknames(
    private val checkAccount: () -> Unit,
    private val fetchHttp: () -> P.Fields,
    private val fetchCm: () -> P.Fields,
    private val onChange: (JSONObject) -> Unit,
) : AutoCloseable {
    private val executor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "MillenniumChatNicknames").apply { isDaemon = true }
    }
    private val lock = Any()
    @Volatile private var names: Map<String, String>? = null
    private var closed = false
    private var loading = false
    private var nextRefresh = 0L

    fun name(id: String): String = names?.get(id).orEmpty()

    fun snapshot(): JSONObject? = names?.let { JSONObject(it) }

    fun refresh() = synchronized(lock) {
        if (closed || loading || System.nanoTime() / 1_000_000 < nextRefresh) return@synchronized
        loading = true
        executor.execute {
            val result = runCatching {
                checkAccount()
                val fields = runCatching(fetchHttp).getOrElse { checkAccount(); fetchCm() }
                parse(fields).also { checkAccount() }
            }
            var changed: JSONObject? = null
            synchronized(lock) {
                loading = false
                if (!closed) {
                    nextRefresh = System.nanoTime() / 1_000_000 + if (result.isSuccess) 300_000 else 60_000
                    result.onSuccess { value ->
                        if (names != value) { names = value; changed = JSONObject(value) }
                    }
                }
            }
            changed?.let(onChange)
        }
    }

    override fun close() = synchronized(lock) {
        closed = true
        names = null
        executor.shutdownNow()
        Unit
    }

    companion object {
        internal fun parse(fields: P.Fields): Map<String, String> = buildMap {
            fields.messages(1).forEach { entry ->
                val accountId = entry.number(1)
                val nickname = entry.text(2).trim()
                if (accountId in 1..0xffffffffL && nickname.isNotEmpty()) {
                    put((76561197960265728L + accountId).toString(), nickname)
                }
            }
        }
    }
}
