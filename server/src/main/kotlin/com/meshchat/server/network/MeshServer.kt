package com.meshchat.server.network

import com.meshchat.server.config.ServerSettings
import com.meshchat.server.crypto.ServerCryptoManager
import com.meshchat.server.model.*
import com.meshchat.server.service.MessageService
import com.meshchat.server.service.MetricsService
import com.meshchat.server.service.UserService
import io.github.oshai.kotlinlogging.KotlinLogging
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.*
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.codec.LineBasedFrameDecoder
import io.netty.handler.codec.string.StringDecoder
import io.netty.handler.codec.string.StringEncoder
import io.netty.handler.timeout.IdleStateHandler
import io.netty.util.CharsetUtil
import kotlinx.coroutines.*
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

private val logger = KotlinLogging.logger {}

/**
 * High-performance TCP mesh server using Netty
 * Handles multiple concurrent connections with proper resource management
 */
class MeshServer(
    private val config: ServerSettings,
    private val cryptoManager: ServerCryptoManager,
    private val userService: UserService,
    private val messageService: MessageService,
    private val metricsService: MetricsService
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private var bossGroup: EventLoopGroup? = null
    private var workerGroup: EventLoopGroup? = null
    private var serverChannel: Channel? = null
    
    // Connection management
    private val connections = ConcurrentHashMap<String, ClientConnection>()
    private val connectionIdCounter = AtomicLong(0)
    
    // Statistics
    private val startTime = System.currentTimeMillis()
    private val totalConnections = AtomicLong(0)
    private val totalMessages = AtomicLong(0)
    
    /**
     * Start the mesh server
     */
    suspend fun start() {
        withContext(Dispatchers.IO) {
            try {
                bossGroup = NioEventLoopGroup(1)
                workerGroup = NioEventLoopGroup(config.workerThreads)
                
                val bootstrap = ServerBootstrap()
                bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel::class.java)
                    .option(ChannelOption.SO_BACKLOG, 1024)
                    .option(ChannelOption.SO_REUSEADDR, true)
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .childOption(ChannelOption.TCP_NODELAY, true)
                    .childOption(ChannelOption.SO_RCVBUF, config.bufferSize)
                    .childOption(ChannelOption.SO_SNDBUF, config.bufferSize)
                    .childHandler(object : ChannelInitializer<SocketChannel>() {
                        override fun initChannel(ch: SocketChannel) {
                            val pipeline = ch.pipeline()
                            
                            // Idle state handler for connection timeout
                            pipeline.addLast("idleStateHandler", 
                                IdleStateHandler(
                                    config.connectionTimeout.seconds,
                                    config.heartbeatInterval.seconds,
                                    0,
                                    TimeUnit.SECONDS
                                )
                            )
                            
                            // Frame decoder for line-based protocol
                            pipeline.addLast("frameDecoder", LineBasedFrameDecoder(8192))
                            
                            // String codec
                            pipeline.addLast("stringDecoder", StringDecoder(CharsetUtil.UTF_8))
                            pipeline.addLast("stringEncoder", StringEncoder(CharsetUtil.UTF_8))
                            
                            // Business logic handler
                            pipeline.addLast("meshHandler", MeshChannelHandler(this@MeshServer))
                        }
                    })
                
                val channelFuture = bootstrap.bind(config.host, config.port).sync()
                serverChannel = channelFuture.channel()
                
                logger.info { "🌐 MeshServer started on ${config.host}:${config.port}" }
                logger.info { "⚙️ Worker threads: ${config.workerThreads}" }
                logger.info { "🔗 Max connections: ${config.maxConnections}" }
                
                // Start background tasks
                startBackgroundTasks()
                
            } catch (e: Exception) {
                logger.error(e) { "❌ Failed to start server" }
                stop()
                throw e
            }
        }
    }
    
    /**
     * Stop the mesh server
     */
    suspend fun stop() {
        withContext(Dispatchers.IO) {
            logger.info { "🛑 Stopping MeshServer..." }
            
            try {
                // Cancel background tasks
                scope.cancel()
                
                // Close all client connections
                connections.values.forEach { connection ->
                    try {
                        connection.channel.close().sync()
                    } catch (e: Exception) {
                        logger.warn(e) { "Error closing connection ${connection.id}" }
                    }
                }
                connections.clear()
                
                // Close server channel
                serverChannel?.close()?.sync()
                
                // Shutdown event loops
                bossGroup?.shutdownGracefully()?.sync()
                workerGroup?.shutdownGracefully()?.sync()
                
                logger.info { "✅ MeshServer stopped" }
                
            } catch (e: Exception) {
                logger.error(e) { "❌ Error during server shutdown" }
            }
        }
    }
    
    /**
     * Handle new client connection
     */
    fun onClientConnected(channel: Channel) {
        val connectionId = "conn_${connectionIdCounter.incrementAndGet()}"
        val remoteAddress = channel.remoteAddress() as InetSocketAddress
        
        if (connections.size >= config.maxConnections) {
            logger.warn { "🚫 Max connections reached, rejecting ${remoteAddress.address.hostAddress}" }
            sendError(channel, "MAX_CONNECTIONS", "Server is full")
            channel.close()
            return
        }
        
        val connection = ClientConnection(
            id = connectionId,
            channel = channel,
            ipAddress = remoteAddress.address.hostAddress,
            port = remoteAddress.port,
            connectedAt = System.currentTimeMillis()
        )
        
        connections[connectionId] = connection
        totalConnections.incrementAndGet()
        
        logger.info { "🔗 Client connected: $connectionId from ${connection.ipAddress}" }
        metricsService.recordConnection()
    }
    
    /**
     * Handle client disconnection
     */
    fun onClientDisconnected(channel: Channel) {
        val connection = findConnectionByChannel(channel)
        if (connection != null) {
            connections.remove(connection.id)
            
            // Clean up user data
            connection.userId?.let { userId ->
                userService.setUserOffline(userId)
                cryptoManager.removeClientData(userId)
            }
            
            logger.info { "🔌 Client disconnected: ${connection.id}" }
            metricsService.recordDisconnection()
            
            // Notify other clients
            if (connection.userId != null) {
                broadcastUserLeft(connection.username ?: "Unknown")
            }
        }
    }
    
    /**
     * Handle incoming message from client
     */
    suspend fun onMessageReceived(channel: Channel, message: String) {
        val connection = findConnectionByChannel(channel) ?: return
        
        try {
            connection.lastActivity = System.currentTimeMillis()
            connection.messagesReceived++
            connection.bytesReceived += message.length
            
            val networkMessage = NetworkMessage.fromJson(message)
            
            when (networkMessage.type) {
                NetworkMessageType.HANDSHAKE -> handleHandshake(connection, networkMessage)
                NetworkMessageType.ENCRYPTED_MESSAGE -> handleEncryptedMessage(connection, networkMessage)
                NetworkMessageType.HEARTBEAT -> handleHeartbeat(connection, networkMessage)
                NetworkMessageType.DISCONNECT -> handleDisconnect(connection)
                else -> {
                    logger.warn { "🚫 Unsupported message type: ${networkMessage.type} from ${connection.id}" }
                }
            }
            
            totalMessages.incrementAndGet()
            metricsService.recordMessage()
            
        } catch (e: Exception) {
            logger.error(e) { "❌ Error processing message from ${connection.id}" }
            sendError(channel, "INVALID_MESSAGE", "Failed to process message")
        }
    }
    
    /**
     * Handle handshake message
     */
    private suspend fun handleHandshake(connection: ClientConnection, networkMessage: NetworkMessage) {
        try {
            val handshakeData = HandshakeData.fromJson(networkMessage.data)
            
            // Store client information
            connection.userId = handshakeData.userId
            connection.username = handshakeData.username
            connection.isAuthenticated = true
            
            // Store client's public key
            cryptoManager.storeClientPublicKey(handshakeData.userId, handshakeData.publicKey)
            
            // Generate session key
            val sessionKey = cryptoManager.generateSessionKey(handshakeData.userId)
            val encryptedSessionKey = cryptoManager.encryptSessionKey(sessionKey, handshakeData.userId)
            
            // Create user record
            val user = User(
                id = handshakeData.userId,
                username = handshakeData.username,
                publicKey = handshakeData.publicKey,
                connectionId = connection.id,
                ipAddress = connection.ipAddress
            )
            
            userService.addUser(user)
            
            // Send handshake response
            val responseData = HandshakeResponseData(
                userId = "server",
                username = "MeshServer",
                publicKey = cryptoManager.getServerPublicKey(),
                encryptedSessionKey = encryptedSessionKey,
                serverVersion = "1.0.0"
            )
            
            val response = NetworkMessage(
                type = NetworkMessageType.HANDSHAKE_RESPONSE,
                senderId = "server",
                data = responseData.toJson()
            )
            
            sendMessage(connection.channel, response)
            
            logger.info { "🤝 Handshake completed for user: ${handshakeData.username} (${handshakeData.userId})" }
            
            // Broadcast user joined
            broadcastUserJoined(handshakeData.username)
            
            // Send current user list
            sendUserList(connection)
            
        } catch (e: Exception) {
            logger.error(e) { "❌ Handshake failed for connection ${connection.id}" }
            sendError(connection.channel, "HANDSHAKE_FAILED", e.message ?: "Unknown error")
        }
    }
    
    /**
     * Handle encrypted message
     */
    private suspend fun handleEncryptedMessage(connection: ClientConnection, networkMessage: NetworkMessage) {
        if (!connection.isAuthenticated || connection.userId == null) {
            sendError(connection.channel, "NOT_AUTHENTICATED", "Please complete handshake first")
            return
        }
        
        try {
            val encryptedData = EncryptedMessageData.fromJson(networkMessage.data)
            
            // Decrypt message
            val sessionKey = cryptoManager.getSessionKey(connection.userId!!)
            if (sessionKey == null) {
                sendError(connection.channel, "NO_SESSION_KEY", "Session key not found")
                return
            }
            
            val encryptedMessage = com.meshchat.server.crypto.EncryptedMessage(
                data = encryptedData.encryptedContent,
                iv = encryptedData.iv
            )
            
            val decryptedContent = cryptoManager.decryptMessage(encryptedMessage, sessionKey)
            
            // Verify signature
            val isValidSignature = cryptoManager.verifySignature(
                decryptedContent,
                encryptedData.signature,
                connection.userId!!
            )
            
            if (!isValidSignature) {
                logger.warn { "🚫 Invalid signature from ${connection.userId}" }
                sendError(connection.channel, "INVALID_SIGNATURE", "Message signature verification failed")
                return
            }
            
            // Create message record
            val message = Message(
                id = encryptedData.messageId,
                content = decryptedContent,
                senderId = connection.userId!!,
                senderName = connection.username ?: "Unknown",
                timestamp = encryptedData.timestamp,
                type = encryptedData.messageType
            )
            
            // Store message
            messageService.storeMessage(message)
            
            // Broadcast to other clients
            broadcastMessage(message, connection.userId!!)
            
            logger.debug { "📝 Message from ${connection.username}: ${decryptedContent.take(50)}..." }
            
        } catch (e: Exception) {
            logger.error(e) { "❌ Failed to handle encrypted message from ${connection.id}" }
            sendError(connection.channel, "MESSAGE_FAILED", "Failed to process message")
        }
    }
    
    /**
     * Handle heartbeat message
     */
    private fun handleHeartbeat(connection: ClientConnection, networkMessage: NetworkMessage) {
        connection.lastActivity = System.currentTimeMillis()
        
        // Send heartbeat response
        val response = NetworkMessage(
            type = NetworkMessageType.HEARTBEAT,
            senderId = "server",
            data = ""
        )
        
        sendMessage(connection.channel, response)
    }
    
    /**
     * Handle disconnect message
     */
    private fun handleDisconnect(connection: ClientConnection) {
        logger.info { "👋 Client ${connection.username} requested disconnect" }
        connection.channel.close()
    }
    
    /**
     * Broadcast message to all authenticated clients except sender
     */
    private suspend fun broadcastMessage(message: Message, excludeUserId: String) {
        val authenticatedConnections = connections.values.filter { 
            it.isAuthenticated && it.userId != excludeUserId 
        }
        
        authenticatedConnections.forEach { connection ->
            try {
                val userId = connection.userId ?: return@forEach
                val sessionKey = cryptoManager.getSessionKey(userId) ?: return@forEach
                
                // Encrypt message for this client
                val encryptedMessage = cryptoManager.encryptMessage(message.content, sessionKey)
                val signature = cryptoManager.signMessage(message.content)
                
                val encryptedData = EncryptedMessageData(
                    messageId = message.id,
                    encryptedContent = encryptedMessage.data,
                    iv = encryptedMessage.iv,
                    signature = signature,
                    senderPublicKey = cryptoManager.getServerPublicKey(),
                    senderName = message.senderName,
                    timestamp = message.timestamp,
                    messageType = message.type
                )
                
                val networkMessage = NetworkMessage(
                    type = NetworkMessageType.ENCRYPTED_MESSAGE,
                    senderId = message.senderId,
                    data = encryptedData.toJson(),
                    messageId = message.id
                )
                
                sendMessage(connection.channel, networkMessage)
                connection.messagesSent++
                
            } catch (e: Exception) {
                logger.error(e) { "❌ Failed to broadcast message to ${connection.id}" }
            }
        }
    }
    
    /**
     * Broadcast user joined notification
     */
    private suspend fun broadcastUserJoined(username: String) {
        val systemMessage = Message(
            id = "sys_${System.currentTimeMillis()}",
            content = "$username joined the chat",
            senderId = "system",
            senderName = "System",
            timestamp = System.currentTimeMillis(),
            type = Message.MessageType.SYSTEM
        )
        
        broadcastMessage(systemMessage, "system")
    }
    
    /**
     * Broadcast user left notification
     */
    private suspend fun broadcastUserLeft(username: String) {
        val systemMessage = Message(
            id = "sys_${System.currentTimeMillis()}",
            content = "$username left the chat",
            senderId = "system",
            senderName = "System",
            timestamp = System.currentTimeMillis(),
            type = Message.MessageType.SYSTEM
        )
        
        broadcastMessage(systemMessage, "system")
    }
    
    /**
     * Send user list to specific connection
     */
    private fun sendUserList(connection: ClientConnection) {
        val users = userService.getOnlineUsers()
        val userListData = UserListData(
            users = users,
            totalUsers = users.size,
            onlineUsers = users.count { it.isOnline }
        )
        
        val networkMessage = NetworkMessage(
            type = NetworkMessageType.USER_LIST,
            senderId = "server",
            data = userListData.toJson()
        )
        
        sendMessage(connection.channel, networkMessage)
    }
    
    /**
     * Send error message to client
     */
    private fun sendError(channel: Channel, code: String, message: String) {
        val errorData = ErrorData(code, message)
        val networkMessage = NetworkMessage(
            type = NetworkMessageType.ERROR,
            senderId = "server",
            data = errorData.toJson()
        )
        
        sendMessage(channel, networkMessage)
    }
    
    /**
     * Send network message to channel
     */
    private fun sendMessage(channel: Channel, message: NetworkMessage) {
        if (channel.isActive) {
            channel.writeAndFlush("${message.toJson()}\n")
        }
    }
    
    /**
     * Find connection by channel
     */
    private fun findConnectionByChannel(channel: Channel): ClientConnection? {
        return connections.values.find { it.channel == channel }
    }
    
    /**
     * Start background maintenance tasks
     */
    private fun startBackgroundTasks() {
        // Connection cleanup task
        scope.launch {
            while (isActive) {
                try {
                    cleanupInactiveConnections()
                    delay(60_000) // Run every minute
                } catch (e: Exception) {
                    logger.error(e) { "❌ Error in cleanup task" }
                }
            }
        }
        
        // Statistics update task
        scope.launch {
            while (isActive) {
                try {
                    updateStatistics()
                    delay(30_000) // Run every 30 seconds
                } catch (e: Exception) {
                    logger.error(e) { "❌ Error in statistics task" }
                }
            }
        }
    }
    
    /**
     * Clean up inactive connections
     */
    private fun cleanupInactiveConnections() {
        val now = System.currentTimeMillis()
        val timeout = config.connectionTimeout.toMillis() * 2
        
        val inactiveConnections = connections.values.filter { connection ->
            !connection.channel.isActive || (now - connection.lastActivity) > timeout
        }
        
        inactiveConnections.forEach { connection ->
            logger.info { "🧹 Cleaning up inactive connection: ${connection.id}" }
            connection.channel.close()
        }
    }
    
    /**
     * Update server statistics
     */
    private fun updateStatistics() {
        val stats = ServerStats(
            uptime = System.currentTimeMillis() - startTime,
            totalConnections = totalConnections.get(),
            currentConnections = connections.size,
            authenticatedConnections = connections.values.count { it.isAuthenticated },
            totalMessages = totalMessages.get(),
            messagesPerSecond = 0.0, // Calculate based on recent activity
            bytesTransferred = connections.values.sumOf { it.bytesReceived + it.bytesSent },
            memoryUsage = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory(),
            cpuUsage = 0.0 // Would need additional monitoring
        )
        
        metricsService.updateStats(stats)
    }
    
    /**
     * Get current server statistics
     */
    fun getStats(): ServerStats {
        return ServerStats(
            uptime = System.currentTimeMillis() - startTime,
            totalConnections = totalConnections.get(),
            currentConnections = connections.size,
            authenticatedConnections = connections.values.count { it.isAuthenticated },
            totalMessages = totalMessages.get(),
            messagesPerSecond = 0.0,
            bytesTransferred = connections.values.sumOf { it.bytesReceived + it.bytesSent },
            memoryUsage = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory(),
            cpuUsage = 0.0
        )
    }
}

/**
 * Client connection information
 */
data class ClientConnection(
    val id: String,
    val channel: Channel,
    val ipAddress: String,
    val port: Int,
    val connectedAt: Long,
    var lastActivity: Long = System.currentTimeMillis(),
    var userId: String? = null,
    var username: String? = null,
    var isAuthenticated: Boolean = false,
    var bytesReceived: Long = 0,
    var bytesSent: Long = 0,
    var messagesReceived: Long = 0,
    var messagesSent: Long = 0
)

/**
 * Extension functions for JSON serialization
 */
fun NetworkMessage.toJson(): String = kotlinx.serialization.json.Json.encodeToString(this)
fun NetworkMessage.Companion.fromJson(json: String): NetworkMessage = kotlinx.serialization.json.Json.decodeFromString(json)

fun HandshakeData.toJson(): String = kotlinx.serialization.json.Json.encodeToString(this)
fun HandshakeData.Companion.fromJson(json: String): HandshakeData = kotlinx.serialization.json.Json.decodeFromString(json)

fun HandshakeResponseData.toJson(): String = kotlinx.serialization.json.Json.encodeToString(this)
fun HandshakeResponseData.Companion.fromJson(json: String): HandshakeResponseData = kotlinx.serialization.json.Json.decodeFromString(json)

fun EncryptedMessageData.toJson(): String = kotlinx.serialization.json.Json.encodeToString(this)
fun EncryptedMessageData.Companion.fromJson(json: String): EncryptedMessageData = kotlinx.serialization.json.Json.decodeFromString(json)

fun UserListData.toJson(): String = kotlinx.serialization.json.Json.encodeToString(this)
fun ErrorData.toJson(): String = kotlinx.serialization.json.Json.encodeToString(this)