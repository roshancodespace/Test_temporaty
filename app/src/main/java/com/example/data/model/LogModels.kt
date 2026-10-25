package com.example.data.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

enum class LogLevel {
    INFO,
    SUCCESS,
    WARN,
    ERROR,
    KEYSTROKE
}

data class LogEntry(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: String = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date()),
    val level: LogLevel,
    val message: String
)
