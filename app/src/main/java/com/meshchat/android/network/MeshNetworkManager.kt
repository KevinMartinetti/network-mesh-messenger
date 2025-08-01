package com.meshchat.android.network

import android.util.Log
import com.meshchat.android.crypto.CryptoManager
import com.meshchat.android.data.Message
import com.meshchat.android.data.User
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.*
import java.net.*
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.SecretKey

/**
 * MeshNetworkManager handles TCP mesh networking with P2P connections
 */
class MeshNetworkManager(
    private val cryptoManager: CryptoManager
) {
    companion object {
        private const val TAG = "MeshNetworkManager"
        private const val DEFAULT_PORT = 8080
        private const val BUFFER_SIZE = 8192
        private const val CONNECTION_TIMEOUT = 10000
        private const val HEARTBEAT_INTERVAL = 30000L
    }
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // Network state
    private var serverSocket: ServerSocket? = null
    private var isServer = false
    private var isRunning = false
    
    // Connected peers
    private val connectedPeers = ConcurrentHashMap<String, PeerConnection>()
    private val sessionKeys = ConcurrentHashMap<String, SecretKey>()
    
    // Flow for network events
    private val _networkEvents = MutableSharedFlow<NetworkEvent>()
    val networkEvents: SharedFlow<NetworkEvent> = _networkEvents.asSharedFlow()
    
    // Flow for incoming messages
    private val _incomingMessages = MutableSharedFlow<Message>()
    val incomingMessages: SharedFlow<Message> = _incomingMessages.asSharedFlow()
    
    // Flow for user list updates
    private val _connectedUsers = MutableStateFlow<List<User>>(emptyList())
    val connectedUsers: StateFlow<List<User>> = _connectedUsers.asStateFlow()
    
    private var currentUser: User? = null
    
    /**
     * Start as server (host mode)
     */
    suspend fun startAsServer(port: Int = DEFAULT_PORT, username: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                if (isRunning) {
                    return@withContext Result.failure(IllegalStateException("Network already running"))
                }
                
                currentUser = User(
                    id = generateUserId(),
                    username = username,
                    publicKey = cryptoManager.getPublicKeyString(),
                    isHost = true
                )
                
                serverSocket = ServerSocket(port)
                isServer = true
                isRunning = true
                
                Log.d(TAG, "Server started on port $port")
                _networkEvents.emit(NetworkEvent.ServerStarted(port))
                
                // Start accepting connections
                scope.launch { acceptConnections() }
                
                // Start heartbeat
                scope.launch { startHeartbeat() }
                
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start server", e)
                Result.failure(e)
            }
        }
    }
    
    /**
     * Connect to existing server (client mode)
     */
    suspend fun connectToServer(host: String, port: Int = DEFAULT_PORT, username: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                if (isRunning) {
                    return@withContext Result.failure(IllegalStateException("Network already running"))
                }
                
                currentUser = User(
                    id = generateUserId(),
                    username = username,
                    publicKey = cryptoManager.getPublicKeyString(),
                    isHost = false
                )
                
                val socket = Socket()
                socket.connect(InetSocketAddress(host, port), CONNECTION_TIMEOUT)
                
                val peerId = "${socket.inetAddress.hostAddress}:${socket.port}"
                val peerConnection = PeerConnection(socket, peerId)
                
                connectedPeers[peerId] = peerConnection
                isRunning = true
                
                Log.d(TAG, "Connected to server at $host:$port")
                _networkEvents.emit(NetworkEvent.ConnectedToServer(host, port))
                
                // Start listening for messages from this peer
                scope.launch { listenToPeer(peerConnection) }
                
                // Send handshake
                sendHandshake(peerConnection)
                
                // Start heartbeat
                scope.launch { startHeartbeat() }
                
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to connect to server", e)
                Result.failure(e)
            }
        }
    }
    
    /**
     * Accept incoming connections (server mode)
     */
    private suspend fun acceptConnections() {
        while (isRunning && isServer) {
            try {
                val socket = serverSocket?.accept() ?: break
                val peerId = "${socket.inetAddress.hostAddress}:${socket.port}"
                val peerConnection = PeerConnection(socket, peerId)
                
                connectedPeers[peerId] = peerConnection
                
                Log.d(TAG, "New peer connected: $peerId")
                _networkEvents.emit(NetworkEvent.PeerConnected(peerId))
                
                // Start listening to this peer
                scope.launch { listenToPeer(peerConnection) }
                
            } catch (e: Exception) {
                if (isRunning) {
                    Log.e(TAG, "Error accepting connection", e)
                }
            }
        }
    }
    
    /**
     * Listen for messages from a specific peer
     */
    private suspend fun listenToPeer(peerConnection: PeerConnection) {
        try {
            val reader = BufferedReader(InputStreamReader(peerConnection.socket.getInputStream()))
            
            while (isRunning && !peerConnection.socket.isClosed) {
                val line = reader.readLine() ?: break
                
                try {
                    val networkMessage = NetworkMessage.fromJson(line)
                    handleNetworkMessage(networkMessage, peerConnection)
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing message from ${peerConnection.peerId}", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error listening to peer ${peerConnection.peerId}", e)
        } finally {
            disconnectPeer(peerConnection.peerId)
        }
    }
    
    /**
     * Handle incoming network messages
     */
    private suspend fun handleNetworkMessage(networkMessage: NetworkMessage, peerConnection: PeerConnection) {
        when (networkMessage.type) {
            MessageType.HANDSHAKE -> handleHandshake(networkMessage, peerConnection)
            MessageType.HANDSHAKE_RESPONSE -> handleHandshakeResponse(networkMessage, peerConnection)
            MessageType.KEY_EXCHANGE -> handleKeyExchange(networkMessage, peerConnection)
            MessageType.ENCRYPTED_MESSAGE -> handleEncryptedMessage(networkMessage, peerConnection)
            MessageType.USER_LIST -> handleUserList(networkMessage)
            MessageType.HEARTBEAT -> handleHeartbeat(peerConnection)
            MessageType.FILE_TRANSFER -> handleFileTransfer(networkMessage, peerConnection)
        }
    }
    
    /**
     * Send handshake to establish connection
     */
    private suspend fun sendHandshake(peerConnection: PeerConnection) {
        val handshakeData = HandshakeData(
            userId = currentUser?.id ?: "",
            username = currentUser?.username ?: "",
            publicKey = cryptoManager.getPublicKeyString()
        )
        
        val message = NetworkMessage(
            type = MessageType.HANDSHAKE,
            senderId = currentUser?.id ?: "",
            data = handshakeData.toJson()
        )
        
        sendToPeer(message, peerConnection)
    }
    
    /**
     * Handle handshake from peer
     */
    private suspend fun handleHandshake(networkMessage: NetworkMessage, peerConnection: PeerConnection) {
        val handshakeData = HandshakeData.fromJson(networkMessage.data)
        
        // Create session key for this peer
        val sessionKey = cryptoManager.generateSessionKey()
        sessionKeys[peerConnection.peerId] = sessionKey
        
        // Encrypt session key with peer's public key
        val encryptedSessionKey = cryptoManager.encryptSessionKey(sessionKey, handshakeData.publicKey)
        
        // Send response with encrypted session key
        val responseData = HandshakeResponseData(
            userId = currentUser?.id ?: "",
            username = currentUser?.username ?: "",
            publicKey = cryptoManager.getPublicKeyString(),
            encryptedSessionKey = encryptedSessionKey
        )
        
        val response = NetworkMessage(
            type = MessageType.HANDSHAKE_RESPONSE,
            senderId = currentUser?.id ?: "",
            data = responseData.toJson()
        )
        
        sendToPeer(response, peerConnection)
        
        // Update user list
        updateConnectedUsers()
        
        _networkEvents.emit(NetworkEvent.UserJoined(handshakeData.username))
    }
    
    /**
     * Handle handshake response
     */
    private suspend fun handleHandshakeResponse(networkMessage: NetworkMessage, peerConnection: PeerConnection) {
        val responseData = HandshakeResponseData.fromJson(networkMessage.data)
        
        // Decrypt session key
        val sessionKey = cryptoManager.decryptSessionKey(responseData.encryptedSessionKey)
        sessionKeys[peerConnection.peerId] = sessionKey
        
        // Update user list
        updateConnectedUsers()
        
        _networkEvents.emit(NetworkEvent.UserJoined(responseData.username))
    }
    
    /**
     * Handle encrypted message
     */
    private suspend fun handleEncryptedMessage(networkMessage: NetworkMessage, peerConnection: PeerConnection) {
        val sessionKey = sessionKeys[peerConnection.peerId] ?: return
        
        try {
            val encryptedMessageData = EncryptedMessageData.fromJson(networkMessage.data)
            val encryptedMessage = com.meshchat.android.crypto.EncryptedMessage(
                data = encryptedMessageData.encryptedContent,
                iv = encryptedMessageData.iv
            )
            
            val decryptedContent = cryptoManager.decryptMessage(encryptedMessage, sessionKey)
            
            // Verify signature
            val isValid = cryptoManager.verifySignature(
                decryptedContent,
                encryptedMessageData.signature,
                encryptedMessageData.senderPublicKey
            )
            
            if (isValid) {
                val message = Message(
                    id = encryptedMessageData.messageId,
                    content = decryptedContent,
                    senderId = networkMessage.senderId,
                    senderName = encryptedMessageData.senderName,
                    timestamp = encryptedMessageData.timestamp,
                    type = encryptedMessageData.messageType
                )
                
                _incomingMessages.emit(message)
                
                // Forward to other peers if we're the server
                if (isServer) {
                    forwardMessageToOtherPeers(networkMessage, peerConnection.peerId)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error decrypting message", e)
        }
    }
    
    /**
     * Send encrypted message to all peers
     */
    suspend fun sendMessage(content: String, messageType: Message.Type = Message.Type.TEXT): Result<Unit> {
        return try {
            val currentUser = this.currentUser ?: return Result.failure(IllegalStateException("User not set"))
            
            val messageId = generateMessageId()
            val timestamp = System.currentTimeMillis()
            
            // Sign the message
            val signature = cryptoManager.signMessage(content)
            
            connectedPeers.values.forEach { peerConnection ->
                val sessionKey = sessionKeys[peerConnection.peerId] ?: return@forEach
                
                // Encrypt message
                val encryptedMessage = cryptoManager.encryptMessage(content, sessionKey)
                
                val encryptedMessageData = EncryptedMessageData(
                    messageId = messageId,
                    encryptedContent = encryptedMessage.data,
                    iv = encryptedMessage.iv,
                    signature = signature,
                    senderPublicKey = cryptoManager.getPublicKeyString(),
                    senderName = currentUser.username,
                    timestamp = timestamp,
                    messageType = messageType
                )
                
                val networkMessage = NetworkMessage(
                    type = MessageType.ENCRYPTED_MESSAGE,
                    senderId = currentUser.id,
                    data = encryptedMessageData.toJson()
                )
                
                sendToPeer(networkMessage, peerConnection)
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error sending message", e)
            Result.failure(e)
        }
    }
    
    /**
     * Send message to specific peer
     */
    private suspend fun sendToPeer(message: NetworkMessage, peerConnection: PeerConnection) {
        try {
            val writer = PrintWriter(peerConnection.socket.getOutputStream(), true)
            writer.println(message.toJson())
        } catch (e: Exception) {
            Log.e(TAG, "Error sending to peer ${peerConnection.peerId}", e)
            disconnectPeer(peerConnection.peerId)
        }
    }
    
    /**
     * Forward message to other peers (server mode)
     */
    private suspend fun forwardMessageToOtherPeers(message: NetworkMessage, excludePeerId: String) {
        connectedPeers.values.forEach { peerConnection ->
            if (peerConnection.peerId != excludePeerId) {
                sendToPeer(message, peerConnection)
            }
        }
    }
    
    /**
     * Disconnect specific peer
     */
    private suspend fun disconnectPeer(peerId: String) {
        connectedPeers[peerId]?.let { peerConnection ->
            try {
                peerConnection.socket.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error closing peer connection", e)
            }
            
            connectedPeers.remove(peerId)
            sessionKeys.remove(peerId)
            
            _networkEvents.emit(NetworkEvent.PeerDisconnected(peerId))
            updateConnectedUsers()
        }
    }
    
    /**
     * Update connected users list
     */
    private suspend fun updateConnectedUsers() {
        val users = mutableListOf<User>()
        
        currentUser?.let { users.add(it) }
        
        // Add connected peers (this would need peer info storage)
        // For now, we'll just emit the current user
        
        _connectedUsers.emit(users)
    }
    
    /**
     * Start heartbeat to maintain connections
     */
    private suspend fun startHeartbeat() {
        while (isRunning) {
            delay(HEARTBEAT_INTERVAL)
            
            if (connectedPeers.isEmpty()) continue
            
            val heartbeatMessage = NetworkMessage(
                type = MessageType.HEARTBEAT,
                senderId = currentUser?.id ?: "",
                data = ""
            )
            
            connectedPeers.values.forEach { peerConnection ->
                sendToPeer(heartbeatMessage, peerConnection)
            }
        }
    }
    
    /**
     * Handle heartbeat from peer
     */
    private suspend fun handleHeartbeat(peerConnection: PeerConnection) {
        // Update last seen time or respond to heartbeat
        Log.d(TAG, "Heartbeat received from ${peerConnection.peerId}")
    }
    
    /**
     * Handle user list updates
     */
    private suspend fun handleUserList(networkMessage: NetworkMessage) {
        // Handle user list synchronization
    }
    
    /**
     * Handle file transfer
     */
    private suspend fun handleFileTransfer(networkMessage: NetworkMessage, peerConnection: PeerConnection) {
        // Handle file transfer messages
    }
    
    /**
     * Stop networking
     */
    suspend fun stop() {
        isRunning = false
        
        // Close all peer connections
        connectedPeers.values.forEach { peerConnection ->
            try {
                peerConnection.socket.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error closing peer connection", e)
            }
        }
        
        // Close server socket
        serverSocket?.close()
        
        // Clear state
        connectedPeers.clear()
        sessionKeys.clear()
        serverSocket = null
        isServer = false
        
        _networkEvents.emit(NetworkEvent.NetworkStopped)
        
        // Cancel all coroutines
        scope.cancel()
    }
    
    /**
     * Generate unique user ID
     */
    private fun generateUserId(): String {
        return "user_${System.currentTimeMillis()}_${(1000..9999).random()}"
    }
    
    /**
     * Generate unique message ID
     */
    private fun generateMessageId(): String {
        return "msg_${System.currentTimeMillis()}_${(1000..9999).random()}"
    }
    
    /**
     * Get current network status
     */
    fun getNetworkStatus(): NetworkStatus {
        return NetworkStatus(
            isRunning = isRunning,
            isServer = isServer,
            connectedPeersCount = connectedPeers.size,
            currentUser = currentUser
        )
    }
}

/**
 * Represents a connection to a peer
 */
data class PeerConnection(
    val socket: Socket,
    val peerId: String
)

/**
 * Network status information
 */
data class NetworkStatus(
    val isRunning: Boolean,
    val isServer: Boolean,
    val connectedPeersCount: Int,
    val currentUser: User?
)