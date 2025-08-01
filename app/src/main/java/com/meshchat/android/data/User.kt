package com.meshchat.android.data

import kotlinx.serialization.Serializable

/**
 * User data class
 */
@Serializable
data class User(
    val id: String,
    val username: String,
    val publicKey: String,
    val isHost: Boolean = false,
    val isOnline: Boolean = true,
    val lastSeen: Long = System.currentTimeMillis()
) {
    /**
     * Get display name for user
     */
    fun getDisplayName(): String {
        return if (isHost) "$username (Host)" else username
    }
    
    /**
     * Get user status
     */
    fun getStatus(): String {
        return if (isOnline) "Online" else "Last seen ${getLastSeenTime()}"
    }
    
    /**
     * Get formatted last seen time
     */
    private fun getLastSeenTime(): String {
        val now = System.currentTimeMillis()
        val diff = now - lastSeen
        
        return when {
            diff < 60_000 -> "just now"
            diff < 3600_000 -> "${diff / 60_000} minutes ago"
            diff < 86400_000 -> "${diff / 3600_000} hours ago"
            else -> "${diff / 86400_000} days ago"
        }
    }
    
    /**
     * Get initials for avatar
     */
    fun getInitials(): String {
        return username.split(" ")
            .mapNotNull { it.firstOrNull()?.uppercaseChar() }
            .take(2)
            .joinToString("")
            .ifEmpty { username.take(2).uppercase() }
    }
}