package steam.nova.app.features.profile.customization

import android.content.Context
import android.net.Uri
import android.util.Log
import io.github.libxposed.api.XposedModule
import org.json.JSONObject
import steam.nova.app.features.ui.settings.SteamModuleSettings
import java.util.UUID
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** Private RN bridge: public catalog reads and local appearance settings only. */
internal object SteamProfileCustomizationBridge {
    val prefix = "steamnova-profile-${UUID.randomUUID().toString().replace("-", "")}://request?payload="
    @Volatile var available = false
        private set
    private var repository: SteamProfileCustomizationRepository? = null
    private val io = ThreadPoolExecutor(2, 2, 30, TimeUnit.SECONDS, ArrayBlockingQueue(24)) { task ->
        Thread(task, "SteamNovaProfile").apply { isDaemon = true }
    }

    private val local = ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS, ArrayBlockingQueue(16)) { task ->
        Thread(task, "SteamNovaProfileStorage").apply { isDaemon = true }
    }

    fun install(module: XposedModule, loader: ClassLoader, context: () -> Context?) {
        if (available) return
        val intent = Class.forName("com.facebook.react.modules.intent.IntentModule", false, loader)
        val promise = Class.forName("com.facebook.react.bridge.Promise", false, loader)
        val resolve = promise.getMethod("resolve", Any::class.java)
        val open = intent.getDeclaredMethod("openURL", String::class.java, promise)
        module.hook(open).setId("steamnova.profile.customization").intercept { chain ->
            val url = chain.args[0] as? String
            if (url == null || !url.startsWith(prefix)) return@intercept chain.proceed()
            val receiver = chain.args[1]
            fun reply(result: JSONObject) { runCatching { resolve.invoke(receiver, result.toString()) } }
            try {
                require(url.length <= 96 * 1024)
                val args = JSONObject(Uri.decode(url.substring(prefix.length)))
                val app: Context = context.invoke()?.applicationContext ?: error("Steam context unavailable")
                check(SteamModuleSettings.isProfileCustomizationEnabled(app))
                (if (args.optString("operation") in setOf("load", "save", "avatars")) local else io).execute {
                    try {
                        val repo = synchronized(this) {
                            repository ?: SteamProfileCustomizationRepository(app).also { repository = it }
                        }
                        reply(JSONObject().put("ok", true).put("data", repo.execute(args)))
                    } catch (error: Exception) {
                        // No profile IDs, queries, URLs, credentials or full server payloads in logs.
                        module.log(Log.WARN, "SteamNovaProfile", "request failed: ${error.javaClass.simpleName}")
                        reply(JSONObject().put("ok", false).put("error", "REQUEST_FAILED"))
                    }
                }
            } catch (_: Exception) { reply(JSONObject().put("ok", false).put("error", "UNAVAILABLE")) }
            null
        }
        available = true
    }
}
