package adapter

data class LogItem(
    val id: String,
    val event: String,
    val userName: String,
    val timestamp: Long
)