package ai.openclaw.androidagent.models

data class Message(
    val id: String = System.currentTimeMillis().toString(),
    val content: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)
