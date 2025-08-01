package com.meshchat.android.network

import com.meshchat.android.data.Message
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Network message types
 */
enum class MessageType {
    HANDSHAKE,
    HANDSHAKE_RESPONSE,
    KEY_EXCHANGE,
    ENCRYPTED_MESSAGE,
    USER_LIST,
    HEARTBEAT,
    FILE_TRANSFER
}

/**
 * Network message wrapper
 */
@Serializable
data class NetworkMessage(
    val type: MessageType,
    val senderId: String,
    val data: String,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toJson(): String = Json.encodeToString(this)
    
    companion object {
        fun fromJson(json: String): NetworkMessage = Json.decodeFromString(json)
    }
}

/**
 * Handshake data for initial connection
 */
@Serializable
data class HandshakeData(
    val userId: String,
    val username: String,
    val publicKey: String
) {
    fun toJson(): String = Json.encodeToString(this)
    
    companion object {
        fun fromJson(json: String): HandshakeData = Json.decodeFromString(json)
    }
}

/**
 * Handshake response data with encrypted session key
 */
@Serializable
data class HandshakeResponseData(
    val userId: String,
    val username: String,
    val publicKey: String,
    val encryptedSessionKey: String
) {
    fun toJson(): String = Json.encodeToString(this)
    
    companion object {
        fun fromJson(json: String): HandshakeResponseData = Json.decodeFromString(json)
    }
}

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
    val messageType: Message.Type
) {
    fun toJson(): String = Json.encodeToString(this)
    
    companion object {
        fun fromJson(json: String): EncryptedMessageData = Json.decodeFromString(json)
    }
}

/**
 * Network events
 */
sealed class NetworkEvent {
    object NetworkStopped : NetworkEvent()
    data class ServerStarted(val port: Int) : NetworkEvent()
    data class ConnectedToServer(val host: String, val port: Int) : NetworkEvent()
    data class PeerConnected(val peerId: String) : NetworkEvent()
    data class PeerDisconnected(val peerId: String) : NetworkEvent()
    data class UserJoined(val username: String) : NetworkEvent()
    data class UserLeft(val username: String) : NetworkEvent()
    data class ConnectionError(val error: String) : NetworkEvent()
}