package com.meshchat.android.data

import kotlinx.serialization.Serializable

/**
 * Message data class
 */
@Serializable
data class Message(
    val id: String,
    val content: String,
    val senderId: String,
    val senderName: String,
    val timestamp: Long,
    val type: Type = Type.TEXT,
    val isFromCurrentUser: Boolean = false
) {
    @Serializable
    enum class Type {
        TEXT,
        IMAGE,
        FILE,
        SYSTEM
    }
    
    /**
     * Get formatted timestamp
     */
    fun getFormattedTime(): String {
        val date = java.util.Date(timestamp)
        val format = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
        return format.format(date)
    }
    
    /**
     * Get formatted date
     */
    fun getFormattedDate(): String {
        val date = java.util.Date(timestamp)
        val format = java.text.SimpleDateFormat("MMM dd", java.util.Locale.getDefault())
        return format.format(date)
    }
    
    /**
     * Check if message is from today
     */
    fun isFromToday(): Boolean {
        val today = java.util.Calendar.getInstance()
        val messageDate = java.util.Calendar.getInstance().apply {
            timeInMillis = timestamp
        }
        
        return today.get(java.util.Calendar.YEAR) == messageDate.get(java.util.Calendar.YEAR) &&
                today.get(java.util.Calendar.DAY_OF_YEAR) == messageDate.get(java.util.Calendar.DAY_OF_YEAR)
    }
}