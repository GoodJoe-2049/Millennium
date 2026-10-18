package com.millennium.app.features.chat.data

import com.millennium.app.features.chat.network.SteamChatFailure
import com.millennium.app.features.chat.protocol.SteamChatProtocol as P
import org.json.JSONArray
import org.json.JSONObject

/** Group administration stays separate from message delivery and rendering. */
internal class SteamChatGroupAdmin(private val repository: SteamChatRepository) {
    private fun call(method: String, body: P.Writer) = repository.call("ChatRoom.$method", body)
    private fun id(value: String): Long = value.toULong().also { require(it > 0u) }.toLong()
    private fun steamId(value: String): Long { require(value.matches(Regex("7656119\\d{10}"))); return value.toLong() }
    private fun name(args: JSONObject) = args.getString("name").trim().also { require(it.length in 1..64) }
    private fun unsigned(value: Long) = java.lang.Long.toUnsignedString(value)
    private fun person(value: Long) = if (value == 0L) "" else (76561197960265728L + value).toString()

    fun snapshot(args: JSONObject): JSONObject {
        val group = args.getString("group")
        fun request() = P.Writer().number(1, id(group))
        val state = call("GetChatRoomGroupState", request()).messages(1).firstOrNull() ?: throw SteamChatFailure("REQUEST_FAILED")
        val header = state.messages(1).firstOrNull() ?: throw SteamChatFailure("REQUEST_FAILED")
        fun members(field: Int) = JSONArray(state.messages(field).map { row -> JSONObject()
            .put("id", person(row.number(1))).put("rank", row.number(4)).put("state", row.number(3))
            .put("roles", JSONArray(row.numbers(7).map(::unsigned))).put("expires", row.number(6)) })
        val permissions = header.messages(19).associateBy { unsigned(it.number(1)) }
        val roles = header.messages(18).sortedBy { it.number(3) }.map { role ->
            val roleId = unsigned(role.number(1)); val actions = permissions[roleId]
            JSONObject().put("id", roleId).put("name", role.text(2)).put("permissions", JSONObject().apply {
                for (field in 2..12) put(field.toString(), actions?.number(field, if (field == 7 || field == 8) 1 else 0) == 1L)
            })
        }
        val result = JSONObject().put("group", group).put("name", header.text(2)).put("tagline", header.text(15))
            .put("avatar", SteamChatRepository.avatar(header.bytes(25)).ifBlank { SteamChatRepository.avatar(header.bytes(16)) }).put("members", members(2)).put("kicked", members(7)).put("roles", JSONArray(roles))
        val errors = JSONArray()
        listOf("invites" to "GetInviteList", "bans" to "GetBanList", "links" to "GetInviteLinksForGroup").forEach { (key, method) ->
            runCatching { call(method, request()).messages(1).map { row ->
                if (key == "links") JSONObject().put("code", row.text(1)).put("creator", unsigned(row.number(2)))
                    .put("expires", row.number(3)).put("room", unsigned(row.number(4)))
                else JSONObject().put("id", person(row.number(1))).put("actor", person(row.number(2)))
                    .put("timestamp", row.number(3)).put("reason", row.text(4))
            } }.onSuccess { result.put(key, JSONArray(it)) }.onFailure { result.put(key, JSONArray()); errors.put(key) }
        }
        result.put("unavailable", errors)
        return result
    }

    fun execute(args: JSONObject): JSONObject {
        val action = args.getString("action")
        if (action == "createGroup") {
            val request = P.Writer().text(3, name(args))
            val invitees = args.optJSONArray("invitees") ?: JSONArray()
            require(invitees.length() <= 100)
            for (index in 0 until invitees.length()) request.fixed64(4, steamId(invitees.getString(index)))
            val group = call("CreateChatRoomGroup", request).number(1)
            require(group != 0L)
            return JSONObject().put("group", unsigned(group))
        }
        val body = P.Writer().number(1, id(args.getString("group")))
        fun room(field: Int = 2) { body.number(field, id(args.getString("room"))) }
        fun member(field: Int = 2) { body.fixed64(field, steamId(args.getString("member"))) }
        fun role(field: Int = 2) { body.number(field, id(args.getString("role"))) }
        val method = when (action) {
            "invite" -> { member(); room(3); "InviteFriendToChatRoomGroup" }
            "revokeInvite" -> { member(); "RevokeInviteToGroup" }
            "renameGroup" -> { body.text(2, name(args)); "RenameChatRoomGroup" }
            "tagline" -> { body.text(2, args.getString("text").take(128)); "SetChatRoomGroupTagline" }
            "avatar" -> {
                val hash = args.getString("sha"); require(hash.matches(Regex("[a-fA-F0-9]{40}")))
                body.bytes(2, hash.chunked(2).map { it.toInt(16).toByte() }.toByteArray()); "SetChatRoomGroupAvatar"
            }
            "createChannel" -> { body.text(2, name(args)).number(3, 0); "CreateChatRoom" }
            "renameChannel" -> { room(); body.text(3, name(args)); "RenameChatRoom" }
            "deleteChannel" -> { room(); "DeleteChatRoom" }
            "reorderChannel" -> { room(); args.optString("after").takeIf { it.isNotEmpty() }?.let { body.number(3, id(it)) }; "ReorderChatRoom" }
            "createLink" -> {
                val seconds = args.optLong("seconds", 86400); require(seconds in 0..4_294_967_295L)
                body.number(2, seconds); args.optString("room").takeIf { it.isNotBlank() }?.let { body.number(3, id(it)) }; "CreateInviteLink"
            }
            "deleteLink" -> { body.text(2, args.getString("code").also { require(it.length in 1..128) }); "DeleteInviteLink" }
            "ban" -> { member(); body.number(3, if (args.getBoolean("banned")) 1 else 0); "SetUserBanState" }
            "kick", "mute" -> {
                member(); body.number(3, args.optLong("seconds", 3600).also { require(it in 0..Int.MAX_VALUE.toLong()) })
                if (action == "kick") "KickUserFromGroup" else "MuteUserInGroup"
            }
            "createRole" -> { body.text(2, name(args)); "CreateRole" }
            "renameRole" -> { role(); body.text(3, name(args)); "RenameRole" }
            "deleteRole" -> { role(); "DeleteRole" }
            "rolePermissions" -> {
                role(); val permissions = args.getJSONObject("permissions")
                val values = P.Writer().number(1, id(args.getString("role")))
                for (field in 2..12) values.number(field, if (permissions.getBoolean(field.toString())) 1 else 0)
                body.bytes(4, values.build()); "ReplaceRoleActions"
            }
            "addRole", "removeRole" -> { role(3); member(4); if (action == "addRole") "AddRoleToUser" else "DeleteRoleFromUser" }
            else -> throw SteamChatFailure("INVALID_OPERATION")
        }
        val response = call(method, body)
        return if (action == "createLink") JSONObject().put("code", response.text(1)).put("seconds", response.number(2)) else JSONObject()
    }
}
