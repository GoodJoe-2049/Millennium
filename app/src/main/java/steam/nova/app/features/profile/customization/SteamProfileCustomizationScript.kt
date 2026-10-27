package steam.nova.app.features.profile.customization

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.Base64
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.createBitmap
import org.json.JSONObject
import steam.nova.app.R
import steam.nova.app.core.ModuleLocalization
import java.io.ByteArrayOutputStream

internal object SteamProfileCustomizationScript {
    @Volatile private var cachedAnimationPlayer: String? = null

    fun animationPlayer(context: Context): String = cachedAnimationPlayer ?: ModuleLocalization.resources(context)
        .openRawResource(R.raw.profile_customization_lottie).bufferedReader(Charsets.UTF_8).use { it.readText() }
        .also { cachedAnimationPlayer = it }

    fun configuration(context: Context): JSONObject {
        val labels = JSONObject()
        for ((name, id) in mapOf(
            "title" to R.string.module_profile_customization,
            "close" to R.string.profile_custom_close,
            "resize" to R.string.profile_custom_resize,
            "background" to R.string.profile_custom_background,
            "avatar" to R.string.profile_custom_avatar,
            "frame" to R.string.profile_custom_frame,
            "special" to R.string.profile_custom_special,
            "mini" to R.string.profile_custom_mini,
            "search" to R.string.profile_custom_search,
            "byName" to R.string.profile_custom_by_name,
            "byGame" to R.string.profile_custom_by_game,
            "original" to R.string.profile_custom_original,
            "restore" to R.string.profile_custom_restore,
            "save" to R.string.profile_custom_save,
            "saving" to R.string.profile_custom_saving,
            "saved" to R.string.profile_custom_saved,
            "loading" to R.string.profile_custom_loading,
            "error" to R.string.profile_custom_error,
            "retry" to R.string.profile_custom_retry,
            "more" to R.string.profile_custom_more,
            "continueSearch" to R.string.profile_custom_continue_search,
            "noMatches" to R.string.profile_custom_no_matches,
            "scan" to R.string.profile_custom_scan,
            "cached" to R.string.profile_custom_cached,
            "refresh" to R.string.profile_custom_refresh,
            "miniPreview" to R.string.profile_custom_mini_preview,
            "bundleError" to R.string.profile_custom_bundle_error,
        )) labels.put(name, ModuleLocalization.text(context, id))
        val resources = ModuleLocalization.resources(context)
        val animations = JSONObject()
        for ((name, id) in mapOf(
            "filter" to R.raw.profile_customization_filter_toggle,
            "refresh" to R.raw.profile_customization_refresh,
            "panel" to R.raw.profile_customization_panel_toggle,
        )) {
            val json = resources.openRawResource(id).bufferedReader(Charsets.UTF_8).use { it.readText() }
            animations.put(name, JSONObject(json))
        }
        val drawable = checkNotNull(ResourcesCompat.getDrawable(resources, R.drawable.ic_steam_profile_customization, null))
        val bitmap = createBitmap(72, 72)
        val icon = try {
            drawable.setBounds(0, 0, 72, 72); drawable.draw(Canvas(bitmap))
            ByteArrayOutputStream().use { output ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
                "data:image/png;base64," + Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
            }
        } finally { bitmap.recycle() }
        return JSONObject().put("labels", labels).put("icon", icon).put("animations", animations)
            .put("language", if (resources.configuration.locales[0].language == "zh") "schinese" else "english")
    }
}
