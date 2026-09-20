package com.cortex.app.chat

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Persists chat history as JSON in app storage; images live in filesDir/images. */
class ChatStore(private val context: Context) {

    private val file: File get() = File(context.filesDir, "chat_history.json")
    private val maxMessages = 200

    fun load(): MutableList<ChatMessage> {
        if (!file.exists()) return mutableListOf()
        return try {
            val arr = JSONArray(file.readText())
            val list = mutableListOf<ChatMessage>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val path = if (o.has("imagePath")) o.getString("imagePath") else null
                if (path != null && !File(path).exists()) continue
                list.add(
                    ChatMessage(
                        id = o.getLong("id"),
                        role = ChatMessage.Role.valueOf(o.getString("role")),
                        text = if (o.has("text")) o.getString("text") else "",
                        imagePath = path,
                        guidePath = if (o.has("guidePath")) o.getString("guidePath") else null,
                        condition = if (o.has("condition")) o.getString("condition") else null,
                        timestamp = if (o.has("timestamp")) o.getLong("timestamp") else 0L
                    )
                )
            }
            list
        } catch (_: Throwable) {
            mutableListOf()
        }
    }

    fun save(messages: List<ChatMessage>) {
        try {
            val arr = JSONArray()
            val recent = messages.takeLast(maxMessages)
            for (m in recent) {
                if (m.role == ChatMessage.Role.PROGRESS) continue
                val o = JSONObject()
                o.put("id", m.id)
                o.put("role", m.role.name)
                if (m.text.isNotEmpty()) o.put("text", m.text)
                m.imagePath?.let { o.put("imagePath", it) }
                m.guidePath?.let { o.put("guidePath", it) }
                m.condition?.let { o.put("condition", it) }
                o.put("timestamp", m.timestamp)
                arr.put(o)
            }
            File(context.filesDir, "chat_history.json.tmp").let { tmp ->
                tmp.writeText(arr.toString())
                if (file.exists()) file.delete()
                tmp.renameTo(file)
            }
        } catch (_: Throwable) {
        }
    }

    fun clear() {
        file.delete()
    }
}
