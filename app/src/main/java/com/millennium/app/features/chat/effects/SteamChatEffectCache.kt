package com.millennium.app.features.chat.effects

import android.util.AtomicFile
import com.millennium.app.features.chat.diagnostics.SteamChatDiagnostics
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/** Versioned data only: never serializes Rhino scopes, functions or a generated playback. */
internal class SteamChatEffectCache(private val directory: File) {
    fun read(revision: String): JSONObject? = runCatching {
        val key = key(revision)
        val envelope = readJson(AtomicFile(File(directory, "parsed-$key.json.gz")), MAX_BYTES)
        require(envelope.getInt("schema") == 1 && envelope.getString("key") == key) { "Effect parse cache version mismatch" }
        val payload = envelope.getString("payload")
        require(hash(payload) == envelope.getString("sha256")) { "Effect parse cache checksum mismatch" }
        JSONObject(payload).also { SteamChatDiagnostics.effect("parsed.read", "revision=${revision.take(12)} chars=${payload.length}") }
    }.onFailure {
        if (it is FileNotFoundException) SteamChatDiagnostics.effect("parsed.miss", "revision=${revision.take(12)}")
        else SteamChatDiagnostics.effectFailure("parsed.read", it, publicResource = true)
    }.getOrNull()

    fun write(revision: String, parsed: JSONObject) {
        val payload = parsed.toString()
        val key = key(revision)
        val envelope = JSONObject().put("schema", 1).put("key", key)
            .put("sha256", hash(payload)).put("payload", payload)
        writeJson(AtomicFile(File(directory, "parsed-$key.json.gz")), envelope, MAX_BYTES)
        SteamChatDiagnostics.effect("parsed.save", "revision=${revision.take(12)} chars=${payload.length}")
    }

    fun prune(revisions: Set<String>) {
        val keep = revisions.map { "parsed-${key(it)}.json.gz" }.toSet()
        directory.listFiles()?.filter { it.name.matches(Regex("parsed-[a-f0-9]{64}\\.json\\.gz")) && it.name !in keep }
            ?.forEach { AtomicFile(it).delete() }
    }

    private fun key(revision: String) = hash("$adapterRevision\n$revision")

    companion object {
        private const val MAX_BYTES = 64 * 1024 * 1024
        // Bump when Kotlin compiler/scene validation semantics or the serialized data format change.
        private const val ADAPTER_VERSION = "parsed-1/compiler-1/scene-1/rhino-1.9.1/acorn-8.15.0"
        private val adapterRevision by lazy {
            hash(ADAPTER_VERSION + "\n" + SteamChatEffectAcorn.source + "\n" +
                    SteamChatEffectProgram.source() + "\n" + SteamChatEffectCss.source())
        }

        fun hash(value: String): String = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 0xff) }

        fun revision(data: JSONObject): String {
            val digest = MessageDigest.getInstance("SHA-256")
            fun add(value: String) {
                val bytes = value.toByteArray(Charsets.UTF_8)
                digest.update("${bytes.size}:".toByteArray(Charsets.UTF_8))
                digest.update(bytes)
            }
            add(data.getString("main"))
            add(data.getString("css"))
            val files = data.getJSONObject("files")
            files.keys().asSequence().sorted().forEach { url -> add(url); add(files.getString(url)) }
            return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
        }

        fun readJson(file: AtomicFile, limit: Int): JSONObject = file.openRead().use { input ->
            GZIPInputStream(input).use { gzip ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = gzip.read(buffer)
                    if (count < 0) break
                    require(output.size() + count <= limit) { "Effect cache exceeds size limit" }
                    output.write(buffer, 0, count)
                }
                JSONObject(output.toString(Charsets.UTF_8.name()))
            }
        }

        fun writeJson(file: AtomicFile, data: JSONObject, limit: Int) {
            val bytes = data.toString().toByteArray(Charsets.UTF_8)
            require(bytes.size <= limit) { "Effect cache exceeds size limit" }
            val output = file.startWrite()
            try {
                val gzip = GZIPOutputStream(output)
                gzip.write(bytes); gzip.finish(); gzip.flush()
                file.finishWrite(output)
            } catch (error: Throwable) {
                file.failWrite(output)
                throw error
            }
        }
    }
}
