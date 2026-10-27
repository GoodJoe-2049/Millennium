package steam.nova.app.features.chat.data

import android.content.Context
import android.util.AtomicFile
import steam.nova.app.features.chat.network.SteamChatFailure
import steam.nova.app.features.chat.network.SteamChatHttp
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Public, localized store metadata. Never send account credentials with a game-card request. */
internal class SteamChatGameInfo(context: Context) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "millennium-chat-games.json"))
    private val lock = Any()
    private var loaded = false
    private val entries = linkedMapOf<String, JSONObject>()

    // The bridge's dedicated game-info worker serializes these requests away from chat traffic.
    fun resolve(rawId: String, rawLanguage: String): JSONObject = synchronized(lock) {
        val appId = rawId.toLongOrNull()?.takeIf { it in 1..0xffffffffL }?.toString()
            ?: throw SteamChatFailure("INVALID_APP_ID")
        val language = if (rawLanguage == "schinese") "schinese" else "english"
        val key = "$language:$appId"
        if (!loaded) {
            loaded = true
            runCatching {
                val bytes = file.openRead().use { require(it.channel.size() <= 1024 * 1024); it.readBytes() }
                require(bytes.size <= 1024 * 1024)
                val saved = JSONObject(bytes.toString(Charsets.UTF_8))
                saved.keys().asSequence().take(128).forEach { name -> saved.optJSONObject(name)?.let { entries[name] = it } }
            }
        }
        val previous = entries[key]
        val now = System.currentTimeMillis()
        if (previous != null && now - previous.optLong("savedAt") in 0 until 7L * 24 * 60 * 60 * 1000) {
            return@synchronized JSONObject(previous.toString())
        }
        val value = try {
            val url = "https://store.steampowered.com/api/appdetails".toHttpUrl().newBuilder()
                .addQueryParameter("appids", appId).addQueryParameter("l", language).addQueryParameter("filters", "basic").build()
            val response = SteamChatHttp.json(Request.Builder().url(url).build())
            parse(appId, response).put("savedAt", now)
        } catch (error: Exception) {
            if (previous != null) return@synchronized JSONObject(previous.toString())
            throw error
        }
        entries.remove(key); entries[key] = value
        while (entries.size > 128) entries.remove(entries.keys.first())
        // A disk-cache failure must not hide metadata that was successfully fetched.
        runCatching {
            val data = JSONObject(entries).toString().toByteArray()
            val output = file.startWrite()
            try { output.write(data); file.finishWrite(output) }
            catch (error: Exception) { file.failWrite(output); throw error }
        }
        JSONObject(value.toString())
    }

    companion object {
        internal fun parse(appId: String, response: JSONObject): JSONObject {
            val item = response.optJSONObject(appId) ?: throw SteamChatFailure("GAME_UNAVAILABLE")
            val data = item.optJSONObject("data")
            if (!item.optBoolean("success") || data == null) throw SteamChatFailure("GAME_UNAVAILABLE")
            if (data.has("steam_appid") && data.optString("steam_appid") != appId) throw SteamChatFailure("GAME_UNAVAILABLE")
            val artwork = listOf("header_image", "capsule_image", "capsule_imagev5").mapNotNull { field ->
                val url = data.optString(field).takeIf { it.length <= 2048 }?.toHttpUrlOrNull() ?: return@mapNotNull null
                url.toString().takeIf {
                    url.scheme == "https" && url.port == 443 && url.username.isEmpty() && url.password.isEmpty() &&
                            (url.host.endsWith(".steamstatic.com") || url.host.endsWith(".akamaihd.net"))
                }
            }.distinct()
            return JSONObject().put("appId", appId).put("name", data.optString("name").trim().take(256))
                .put("artwork", JSONArray(artwork))
        }
    }
}
