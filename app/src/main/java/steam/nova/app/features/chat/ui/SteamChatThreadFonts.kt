package steam.nova.app.features.chat.ui

import android.content.Context
import android.graphics.Typeface
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.TimeUnit

/** Reuse Steam's web fonts in host-native text. Called on the bridge IO executor, never at startup. */
internal object SteamChatThreadFonts {
    private var registered: JSONObject? = null
    private val http by lazy {
        OkHttpClient.Builder().callTimeout(5, TimeUnit.SECONDS).build()
    }

    @Synchronized
    fun load(context: Context, loader: ClassLoader): JSONObject {
        registered?.let { return it }
        val managerClass = sequenceOf(
            "com.facebook.react.common.assets.ReactFontManager",
            "com.facebook.react.views.text.ReactFontManager",
        ).firstNotNullOfOrNull { runCatching { Class.forName(it, false, loader) }.getOrNull() }
            ?: return JSONObject()
        val manager = managerClass.getMethod("getInstance").invoke(null)
        val setTypeface = managerClass.getMethod("setTypeface", String::class.java, Int::class.javaPrimitiveType, Typeface::class.java)
        val directory = File(context.cacheDir, "millennium-chat-fonts-v1").apply { mkdirs() }
        val result = JSONObject()
        for ((key, weight) in listOf("regular" to "Regular", "medium" to "Medium", "bold" to "Bold")) {
            runCatching {
                val file = File(directory, "MotivaSans-$weight.ttf")
                if (!file.isFile || file.length() == 0L) {
                    val request = Request.Builder().url("https://community.akamai.steamstatic.com/public/shared/fonts/MotivaSans-$weight.ttf?v=4.015").build()
                    http.newCall(request).execute().use { response ->
                        check(response.isSuccessful)
                        val body = response.body
                        require(body.contentLength() <= 2 * 1024 * 1024)
                        val bytes = body.byteStream().use { input ->
                            val output = ByteArrayOutputStream()
                            val buffer = ByteArray(8192)
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                require(output.size() + count <= 2 * 1024 * 1024)
                                output.write(buffer, 0, count)
                            }
                            output.toByteArray()
                        }
                        require(bytes.size in 12..2 * 1024 * 1024)
                        val pending = File(directory, "MotivaSans-$weight.part")
                        try {
                            pending.writeBytes(bytes)
                            // Validate before making a downloaded file eligible for later cache hits.
                            Typeface.createFromFile(pending)
                            check(pending.renameTo(file))
                        } finally {
                            pending.delete()
                        }
                    }
                }
                val family = "MillenniumChatMotiva$weight"
                setTypeface.invoke(manager, family, Typeface.NORMAL, Typeface.createFromFile(file))
                result.put(key, family)
            }
        }
        if (result.length() == 3) registered = result
        return result
    }
}
