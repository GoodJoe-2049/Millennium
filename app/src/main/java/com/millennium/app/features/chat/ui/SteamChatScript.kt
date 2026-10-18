package com.millennium.app.features.chat.ui

import org.json.JSONObject

internal object SteamChatScript {
    fun source(prefix: String): String = "var chatBridgePrefix = ${JSONObject.quote(prefix)};\n" +
            SteamChatStoreScript.source() + "\n" + SteamChatMediaScript.source() + "\n" +
            SteamChatActionsScript.source() + "\n" + SteamChatGroupScript.source() + "\n" + SteamChatUiScript.source() + "\n" + SteamChatNavigationScript.source()
}
