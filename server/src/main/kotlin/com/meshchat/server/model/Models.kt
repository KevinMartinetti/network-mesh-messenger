package com.meshchat.server.model

import kotlinx.serialization.Serializable
import java.time.Instant

/**
 * User model
 */
@Serializable
data class User(
    val id: String,
    val username: String,
    val publicKey: String,
    val isHost: Boolean = false,
    val isOnline: Boolean = true,
    val lastSeen: Long = System.currentTimeMillis(),
    val connectionId: String? = null,
    val ipAddress: String? = null
) {
    fun getDisplayName(): String {
        return if (isHost) "$username (Host)" else username
    }
    
    fun getInitials(): String {
        return username.split(" ")
            .mapNotNull { it.firstOrNull()?.uppercaseChar() }
            .take(2)
            .joinToString("")
            .ifEmpty { username.take(2).uppercase() }
    }
}

/**
 * Message model
 */
@Serializable
data class Message(
    val id: String,
    val content: String,
    val senderId: String,
    val senderName: String,
    val timestamp: Long,
    val type: MessageType = MessageType.TEXT,
    val roomId: String? = null,
    val isEncrypted: Boolean = true
) {
    @Serializable
    enum class MessageType {
        TEXT,
        IMAGE,
        FILE,
        SYSTEM,
        HANDSHAKE,
        HEARTBEAT
    }
}

/**
 * Network message wrapper for protocol communication
 */
@Serializable
data class NetworkMessage(
    val type: NetworkMessageType,
    val senderId: String,
    val data: String,
    val timestamp: Long = System.currentTimeMillis(),
    val messageId: String? = null
)

@Serializable
enum class NetworkMessageType {
    HANDSHAKE,
    HANDSHAKE_RESPONSE,
    KEY_EXCHANGE,
    ENCRYPTED_MESSAGE,
    USER_LIST,
    HEARTBEAT,
    FILE_TRANSFER,
    ERROR,
    DISCONNECT
}

/**
 * Handshake data for initial connection
 */
@Serializable
data class HandshakeData(
    val userId: String,
    val username: String,
    val publicKey: String,
    val clientVersion: String? = null
)

/**
 * Handshake response data
 */
@Serializable
data class HandshakeResponseData(
    val userId: String,
    val username: String,
    val publicKey: String,
    val encryptedSessionKey: String,
    val serverVersion: String,
    val maxMessageSize: Int = 8192
)

/**
 * Encrypted message data
 */
@Serializable
data class EncryptedMessageData(
    val messageId: String,
    val encryptedContent: String,
    val iv: String,
    val signature: String,
    val senderPublicKey: String,
    val senderName: String,
    val timestamp: Long,
    val messageType: Message.MessageType
)

/**
 * User list data
 */
@Serializable
data class UserListData(
    val users: List<User>,
    val totalUsers: Int,
    val onlineUsers: Int
)

/**
 * Error data
 */
@Serializable
data class ErrorData(
    val code: String,
    val message: String,
    val details: String? = null
)

/**
 * Connection info
 */
data class ConnectionInfo(
    val id: String,
    val userId: String?,
    val username: String?,
    val ipAddress: String,
    val port: Int,
    val connectedAt: Instant,
    val lastActivity: Instant,
    val isAuthenticated: Boolean = false,
    val bytesReceived: Long = 0,
    val bytesSent: Long = 0,
    val messagesReceived: Long = 0,
    val messagesSent: Long = 0
)

/**
 * Room model for group chats
 */
@Serializable
data class Room(
    val id: String,
    val name: String,
    val description: String? = null,
    val createdBy: String,
    val createdAt: Long,
    val isPublic: Boolean = true,
    val maxUsers: Int = 100,
    val currentUsers: Int = 0
)

/**
 * Server statistics
 */
data class ServerStats(
    val uptime: Long,
    val totalConnections: Long,
    val currentConnections: Int,
    val authenticatedConnections: Int,
    val totalMessages: Long,
    val messagesPerSecond: Double,
    val bytesTransferred: Long,
    val memoryUsage: Long,
    val cpuUsage: Double
)