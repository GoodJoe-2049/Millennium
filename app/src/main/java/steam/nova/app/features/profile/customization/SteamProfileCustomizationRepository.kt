package steam.nova.app.features.profile.customization

import android.content.Context
import android.util.AtomicFile
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/** Keeps saved schemes outside Steam's clearable cache; fetches catalog pages on demand. */
internal class SteamProfileCustomizationRepository(context: Context) {
    private val root = File(context.filesDir, "steamnova-profile-customization").apply { mkdirs() }
    private val catalog = File(root, "catalog-v1").apply { mkdirs() }
    private val http = OkHttpClient.Builder().connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(18, TimeUnit.SECONDS).callTimeout(25, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).build()
    private val classes = setOf(3, 8, 13, 14, 15)

    fun execute(args: JSONObject): JSONObject = when (args.getString("operation")) {
        "load" -> synchronized(this) {
            val file = schemeFile(args)
            val scheme = if (file.exists() || File(file.path + ".bak").exists())
                cleanScheme(JSONObject(String(AtomicFile(file).readFully(), Charsets.UTF_8))) else JSONObject()
            JSONObject().put("scheme", scheme)
        }
        "save" -> synchronized(this) {
            val scheme = cleanScheme(args.getJSONObject("scheme"))
            write(schemeFile(args), scheme)
            JSONObject().put("scheme", scheme)
        }
        "avatars" -> synchronized(this) {
            val profiles = JSONObject()
            // Include AtomicFile backups, and skip a damaged scheme without hiding the others.
            root.listFiles().orEmpty().map { it.name.removeSuffix(".bak") }.distinct()
                .filter { it.matches(Regex("[0-9]{17}\\.json")) }.forEach { name ->
                    val scheme = runCatching {
                        cleanScheme(JSONObject(String(AtomicFile(File(root, name)).readFully(), Charsets.UTF_8)))
                    }.getOrNull() ?: return@forEach
                    val media = JSONObject()
                    for (key in listOf("avatar", "frame")) {
                        val item = scheme.optJSONObject(key) ?: continue
                        media.put(key, JSONObject().put("image", item.optString("image"))
                            .put("poster", item.optString("poster")))
                    }
                    if (media.length() > 0) profiles.put(name.removeSuffix(".json"), media)
                }
            JSONObject().put("profiles", profiles)
        }
        "catalog" -> {
            val kind = args.getInt("kind"); require(kind in classes)
            val cursor = args.optString("cursor"); require(cursor.length <= 1024)
            val appid = args.optInt("appid"); require(appid >= 0)
            val input = JSONObject().put("community_item_classes", JSONArray().put(kind))
                .put("count", 200).put("cursor", cursor)
            if (appid > 0) input.put("appids", JSONArray().put(appid))
            query(input, language(args), args.optBoolean("refresh"))
        }
        "bundle" -> {
            val ids = args.getJSONArray("ids"); require(ids.length() in 1..16)
            for (i in 0 until ids.length()) require(ids.getLong(i) in 1..0xffffffffL)
            query(JSONObject().put("definitionids", ids).put("count", 100), language(args), false)
        }
        "games" -> {
            val term = args.getString("query").trim(); require(term.length in 1..100)
            val url = "https://store.steampowered.com/api/storesearch/".toHttpUrl().newBuilder()
                .addQueryParameter("term", term).addQueryParameter("l", language(args)).addQueryParameter("cc", "US").build()
            cached("games:$url", false) {
                val items = fetch(url.toString()).optJSONArray("items") ?: JSONArray()
                val result = JSONArray()
                for (i in 0 until items.length()) {
                    val item = items.getJSONObject(i)
                    if (item.optString("type") == "app") result.put(JSONObject()
                        .put("id", item.getInt("id")).put("name", item.getString("name").take(200)))
                }
                JSONObject().put("items", result)
            }
        }
        else -> error("Unsupported operation")
    }

    private fun language(args: JSONObject) = if (args.optString("language") == "schinese") "schinese" else "english"
    private fun schemeFile(args: JSONObject): File {
        val id = args.getString("profile"); require(id.matches(Regex("[0-9]{17}")))
        return File(root, "$id.json")
    }

    private fun query(input: JSONObject, language: String, refresh: Boolean): JSONObject {
        input.put("language", language).put("include_direct_purchase_disabled", true)
        val url = "https://api.steampowered.com/ILoyaltyRewardsService/QueryRewardItems/v1/".toHttpUrl().newBuilder()
            .addQueryParameter("input_json", input.toString()).build()
        return cached("catalog:$input", refresh) {
            val response = fetch(url.toString()).getJSONObject("response")
            val definitions = response.optJSONArray("definitions") ?: JSONArray()
            val items = JSONArray()
            for (i in 0 until definitions.length()) {
                val definition = definitions.getJSONObject(i)
                if (definition.optInt("community_item_class") in classes) items.put(normalize(definition))
            }
            JSONObject().put("items", items).put("total", response.optInt("total_count"))
                .put("cursor", if (definitions.length() == 0) "" else response.optString("next_cursor"))
        }.also { result ->
            // Catalog v1 can still contain the old frame mapping, including offline fallback pages.
            val items = result.getJSONArray("items")
            for (i in 0 until items.length()) normalizeFrameMedia(items.getJSONObject(i))
        }
    }

    private fun normalize(def: JSONObject): JSONObject {
        val data = def.getJSONObject("community_item_data")
        val appid = def.getInt("appid"); val kind = def.getInt("community_item_class")
        fun asset(key: String): String {
            val name = data.optString(key)
            if (name.isEmpty()) return ""
            require(name.matches(Regex("[A-Za-z0-9_-]+\\.(jpg|jpeg|png|apng|gif|webp|webm|mp4)", RegexOption.IGNORE_CASE)))
            return "https://shared.fastly.steamstatic.com/community_assets/images/items/$appid/$name"
        }
        val large = asset("item_image_large"); val small = asset("item_image_small")
        val theme = data.optString("profile_theme_id")
        return JSONObject().put("id", def.getLong("defid").toString()).put("appid", appid).put("kind", kind)
            .put("itemtype", def.getInt("community_item_type"))
            .put("name", data.optString("item_title", data.optString("item_name")).take(300))
            // Steam uses the small asset for animated frames/avatars and large for their still image.
            .put("image", if (kind == 14 || kind == 15) small.ifEmpty { large } else large.ifEmpty { small })
            .put("poster", large.ifEmpty { small })
            .put("thumb", if (kind == 8 || kind == 14) small.ifEmpty { large } else large.ifEmpty { small })
            .put("webm", asset("item_movie_webm")).put("mp4", asset("item_movie_mp4"))
            .put("tiled", data.optBoolean("tiled"))
            .put("theme", if (theme == "GameProfile") "GameProfileTheme"
            else if (theme.matches(Regex("[A-Za-z0-9_]+"))) "${theme}Theme" else "")
            .put("bundle", def.optJSONArray("bundle_defids") ?: JSONArray())
    }

    private fun normalizeFrameMedia(item: JSONObject) {
        if (item.optInt("kind") != 14) return
        // Older catalogs and saved schemes put the animation in thumb and the still image in image.
        // Corrected items already have image == thumb, so repeated reads leave them unchanged.
        val animation = item.optString("thumb")
        val image = item.optString("image")
        if (animation.isNotEmpty() && animation != image) {
            item.put("image", animation).put("poster", image.ifEmpty { item.optString("poster") })
        }
    }

    private fun cleanScheme(input: JSONObject): JSONObject {
        val output = JSONObject()
        for ((key, kind) in mapOf("background" to 3, "special" to 8, "mini" to 13, "frame" to 14, "avatar" to 15)) {
            if (!input.has(key)) continue
            val item = input.getJSONObject(key); require(item.getInt("kind") == kind)
            val clean = JSONObject().put("kind", kind).put("id", item.getString("id"))
                .put("appid", item.getInt("appid")).put("itemtype", item.optInt("itemtype")).put("name", item.optString("name").take(300))
            require(clean.getString("id").matches(Regex("[0-9]{1,10}")))
            require(clean.getInt("appid") > 0)
            for (field in listOf("image", "poster", "thumb", "webm", "mp4")) {
                val url = item.optString(field)
                require(url.isEmpty() || url.matches(Regex("https://shared\\.(fastly|akamai)\\.steamstatic\\.com/community_assets/images/items/[0-9]+/[A-Za-z0-9_-]+\\.(jpg|jpeg|png|apng|gif|webp|webm|mp4)", RegexOption.IGNORE_CASE)))
                clean.put(field, url)
            }
            normalizeFrameMedia(clean)
            val theme = item.optString("theme"); require(theme.length <= 100 && (theme.isEmpty() || theme.matches(Regex("[A-Za-z0-9_]+Theme"))))
            val colors = JSONObject()
            val supplied = item.optJSONObject("colors") ?: JSONObject()
            for (property in THEME_COLORS) {
                if (!supplied.has(property)) continue
                val color = supplied.getString(property)
                require(color.length <= 100 && color.matches(Regex("(?:rgba?|hsla?)\\([0-9.,% /+\\-]+\\)|#[0-9a-fA-F]{3,8}|transparent")))
                colors.put(property, color)
            }
            if (theme == "GameProfileTheme") require(colors.has("--gradient-background"))
            clean.put("colors", colors)
            clean.put("theme", theme).put("tiled", item.optBoolean("tiled"))
            output.put(key, clean)
        }
        return output
    }

    private fun fetch(url: String): JSONObject = http.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
        check(response.isSuccessful) { "HTTP ${response.code}" }
        val body = response.body
        require(body.contentLength() <= 4 * 1024 * 1024)
        val source = body.source(); require(!source.request(4 * 1024 * 1024L + 1)) { "Response too large" }
        JSONObject(source.readUtf8())
    }

    private fun cached(key: String, refresh: Boolean, load: () -> JSONObject): JSONObject {
        val name = MessageDigest.getInstance("SHA-256").digest(key.toByteArray()).joinToString("") { "%02x".format(it.toInt() and 255) }
        val file = File(catalog, "$name.json")
        val old = synchronized(this) { runCatching { JSONObject(String(AtomicFile(file).readFully(), Charsets.UTF_8)) }.getOrNull() }
        if (!refresh && old != null && System.currentTimeMillis() - old.optLong("time") < TimeUnit.HOURS.toMillis(24))
            return old.getJSONObject("data").put("stale", false)
        return try {
            val data = load()
            synchronized(this) {
                write(file, JSONObject().put("time", System.currentTimeMillis()).put("data", data))
                val files = catalog.listFiles()?.filter { it.extension == "json" }?.sortedByDescending { it.lastModified() }.orEmpty()
                var bytes = 0L
                files.forEach { bytes += it.length(); if (bytes > 24 * 1024 * 1024) AtomicFile(it).delete() }
            }
            data.put("stale", false)
        } catch (error: Exception) {
            old?.getJSONObject("data")?.put("stale", true) ?: throw error
        }
    }

    private companion object {
        val THEME_COLORS = setOf("--gradient-right", "--gradient-left", "--gradient-background",
            "--gradient-background-right", "--gradient-background-left", "--color-showcase-header",
            "--gradient-showcase-header-left", "--btn-background", "--btn-background-hover", "--btn-outline")
    }

    private fun write(file: File, data: JSONObject) {
        val atomic = AtomicFile(file); val output = atomic.startWrite()
        try { output.write(data.toString().toByteArray()); atomic.finishWrite(output) }
        catch (error: Exception) { atomic.failWrite(output); throw error }
    }
}
