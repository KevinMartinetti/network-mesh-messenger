package com.meshchat.server

import com.meshchat.server.config.ServerConfig
import com.meshchat.server.crypto.ServerCryptoManager
import com.meshchat.server.database.DatabaseManager
import com.meshchat.server.network.MeshServer
import com.meshchat.server.service.MetricsService
import com.meshchat.server.service.UserService
import com.meshchat.server.service.MessageService
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import kotlin.system.exitProcess

private val logger = KotlinLogging.logger {}

/**
 * Main entry point for MeshChat Server
 * Reliable TCP mesh server for VPS deployment
 */
suspend fun main(args: Array<String>) {
    logger.info { "🚀 Starting MeshChat Server v${ServerConfig.VERSION}" }
    
    try {
        // Initialize configuration
        val config = ServerConfig.load()
        logger.info { "📋 Configuration loaded: ${config.server.host}:${config.server.port}" }
        
        // Initialize database
        val databaseManager = DatabaseManager(config.database)
        databaseManager.initialize()
        logger.info { "🗄️ Database initialized" }
        
        // Initialize crypto manager
        val cryptoManager = ServerCryptoManager()
        logger.info { "🔐 Cryptography manager initialized" }
        
        // Initialize services
        val userService = UserService(databaseManager)
        val messageService = MessageService(databaseManager)
        val metricsService = MetricsService(config.monitoring)
        
        logger.info { "⚙️ Services initialized" }
        
        // Initialize and start mesh server
        val meshServer = MeshServer(
            config = config.server,
            cryptoManager = cryptoManager,
            userService = userService,
            messageService = messageService,
            metricsService = metricsService
        )
        
        // Setup graceful shutdown
        setupGracefulShutdown(meshServer, databaseManager, metricsService)
        
        // Start server
        logger.info { "🌐 Starting mesh server on ${config.server.host}:${config.server.port}" }
        meshServer.start()
        
        logger.info { "✅ MeshChat Server is running!" }
        logger.info { "📊 Metrics available at http://${config.monitoring.host}:${config.monitoring.port}/metrics" }
        
        // Keep server running
        awaitCancellation()
        
    } catch (e: Exception) {
        logger.error(e) { "💥 Failed to start server" }
        exitProcess(1)
    }
}

/**
 * Setup graceful shutdown handlers
 */
private fun setupGracefulShutdown(
    meshServer: MeshServer,
    databaseManager: DatabaseManager,
    metricsService: MetricsService
) {
    Runtime.getRuntime().addShutdownHook(Thread {
        logger.info { "🛑 Shutting down MeshChat Server..." }
        
        runBlocking {
            try {
                // Stop accepting new connections
                meshServer.stop()
                logger.info { "🌐 Mesh server stopped" }
                
                // Stop metrics service
                metricsService.stop()
                logger.info { "📊 Metrics service stopped" }
                
                // Close database connections
                databaseManager.close()
                logger.info { "🗄️ Database connections closed" }
                
                logger.info { "✅ Server shutdown complete" }
            } catch (e: Exception) {
                logger.error(e) { "❌ Error during shutdown" }
            }
        }
    })
}