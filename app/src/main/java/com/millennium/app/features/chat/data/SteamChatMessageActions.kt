package com.millennium.app.features.chat.data

import com.millennium.app.features.chat.network.SteamChatFailure
import com.millennium.app.features.chat.protocol.SteamChatProtocol as P
import org.json.JSONObject
import java.util.Locale

/** Field mappings follow Monica Steam's message action services. */
internal class SteamChatMessageActions(private val repository: SteamChatRepository) {
    fun execute(operation: String, args: JSONObject): JSONObject {
        val target = SteamChatRepository.Target.parse(args)
        if (operation == "typing") {
            if (target.peer != null) repository.call("FriendMessages.SendMessage", P.Writer()
                .fixed64(1, target.peer.toLong()).number(2, 2).number(4, 1))
            return JSONObject()
        }
        val time = args.getLong("timestamp")
        val ordinal = args.getLong("ordinal")
        require(time > 0 && ordinal in 0 until Int.MAX_VALUE.toLong())
        val group = target.peer == null
        when (operation) {
            "reaction" -> {
                val name = args.getString("name").trim().trim(':')
                require(name.isNotBlank() && name.length <= 160)
                val type = args.optInt("type", 1)
                require(type == 1 || type == 2)
                val body = if (group) target.writer().number(3, time).number(4, ordinal)
                else P.Writer().fixed64(1, target.peer.toLong()).number(2, time).number(3, ordinal)
                val field = if (group) 5 else 4
                body.number(field, type.toLong()).text(field + 1, if (type == 1) ":$name:" else name)
                    .number(field + 2, if (args.optBoolean("add", true)) 1 else 0)
                repository.call(if (group) "ChatRoom.UpdateMessageReaction" else "FriendMessages.UpdateMessageReaction", body)
            }
            "report" -> {
                val sender = args.getString("sender")
                require(sender.matches(Regex("7656119\\d{10}")) && sender != repository.account.id)
                val reason = args.getInt("reason")
                require(reason in setOf(2, 3, 13, 28))
                val body = if (group) target.writer().fixed64(3, sender.toLong())
                    .number(4, time).number(5, ordinal).number(6, reason.toLong())
                    .text(7, args.getString("text").take(8000)).text(8, Locale.getDefault().language).number(9, 5)
                else P.Writer().fixed64(1, sender.toLong()).fixed64(2, repository.account.id.toLong())
                    .number(3, time).number(4, ordinal).number(5, reason.toLong())
                    .text(6, args.getString("text").take(8000)).text(7, Locale.getDefault().language)
                repository.call(if (group) "ChatRoom.ReportMessage" else "FriendMessages.ReportMessage", body)
            }
            "deleteMessage" -> {
                if (!group) throw SteamChatFailure("NOT_SUPPORTED")
                repository.call("ChatRoom.DeleteChatMessages", target.writer().bytes(3,
                    P.Writer().number(1, time).number(2, ordinal).build()))
            }
            else -> throw SteamChatFailure("INVALID_OPERATION")
        }
        return JSONObject()
    }
}
