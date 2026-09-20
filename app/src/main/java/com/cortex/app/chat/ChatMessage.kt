package com.cortex.app.chat

data class ChatMessage(
    val id: Long,
    val role: Role,
    var text: String = "",
    val imagePath: String? = null,
    val guidePath: String? = null,
    val condition: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    var step: Int = 0,
    var totalSteps: Int = 0,
    var previewPath: String? = null,
    var inProgress: Boolean = false,
    var error: Boolean = false
) {
    enum class Role { USER, ASSISTANT_TEXT, ASSISTANT_IMAGE, PROGRESS }
}
