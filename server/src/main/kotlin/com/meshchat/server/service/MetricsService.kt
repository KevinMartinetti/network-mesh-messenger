package com.meshchat.server.service

import com.meshchat.server.config.MonitoringSettings
import com.meshchat.server.model.ServerStats
import io.github.oshai.kotlinlogging.KotlinLogging
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import kotlinx.coroutines.*
import java.io.IOException
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicLong

private val logger = KotlinLogging.logger {}

/**
 * Metrics and monitoring service
 * Provides Prometheus metrics and health checks
 */
class MetricsService(private val config: MonitoringSettings) {
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var httpServer: SimpleHttpServer? = null
    
    // Micrometer registry
    private val meterRegistry: MeterRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    
    // Metrics
    private val connectionsTotal: Counter = Counter.builder("meshchat_connections_total")
        .description("Total number of connections")
        .register(meterRegistry)
    
    private val disconnectionsTotal: Counter = Counter.builder("meshchat_disconnections_total")
        .description("Total number of disconnections")
        .register(meterRegistry)
    
    private val messagesTotal: Counter = Counter.builder("meshchat_messages_total")
        .description("Total number of messages")
        .register(meterRegistry)
    
    private val bytesTransferred: Counter = Counter.builder("meshchat_bytes_transferred_total")
        .description("Total bytes transferred")
        .register(meterRegistry)
    
    private val messageProcessingTime: Timer = Timer.builder("meshchat_message_processing_seconds")
        .description("Message processing time")
        .register(meterRegistry)
    
    // Gauges for current state
    private val currentConnections = AtomicLong(0)
    private val authenticatedConnections = AtomicLong(0)
    private val memoryUsage = AtomicLong(0)
    private val uptime = AtomicLong(0)
    
    init {
        // Register gauges
        Gauge.builder("meshchat_current_connections")
            .description("Current number of connections")
            .register(meterRegistry) { currentConnections.get().toDouble() }
        
        Gauge.builder("meshchat_authenticated_connections")
            .description("Current number of authenticated connections")
            .register(meterRegistry) { authenticatedConnections.get().toDouble() }
        
        Gauge.builder("meshchat_memory_usage_bytes")
            .description("Current memory usage in bytes")
            .register(meterRegistry) { memoryUsage.get().toDouble() }
        
        Gauge.builder("meshchat_uptime_seconds")
            .description("Server uptime in seconds")
            .register(meterRegistry) { uptime.get().toDouble() }
        
        // Start HTTP server for metrics
        if (config.enableMetrics) {
            startHttpServer()
        }
        
        logger.info { "📊 Metrics service initialized" }
    }
    
    /**
     * Start simple HTTP server for metrics and health checks
     */
    private fun startHttpServer() {
        scope.launch {
            try {
                httpServer = SimpleHttpServer(config.host, config.port, meterRegistry, config)
                httpServer?.start()
                logger.info { "🌐 Metrics HTTP server started on ${config.host}:${config.port}" }
            } catch (e: Exception) {
                logger.error(e) { "❌ Failed to start metrics HTTP server" }
            }
        }
    }
    
    /**
     * Record new connection
     */
    fun recordConnection() {
        connectionsTotal.increment()
        currentConnections.incrementAndGet()
    }
    
    /**
     * Record disconnection
     */
    fun recordDisconnection() {
        disconnectionsTotal.increment()
        currentConnections.decrementAndGet()
    }
    
    /**
     * Record message
     */
    fun recordMessage() {
        messagesTotal.increment()
    }
    
    /**
     * Record bytes transferred
     */
    fun recordBytesTransferred(bytes: Long) {
        bytesTransferred.increment(bytes.toDouble())
    }
    
    /**
     * Record message processing time
     */
    fun recordMessageProcessingTime(durationMs: Long) {
        messageProcessingTime.record(durationMs.toDouble() / 1000.0, java.util.concurrent.TimeUnit.SECONDS)
    }
    
    /**
     * Update server statistics
     */
    fun updateStats(stats: ServerStats) {
        currentConnections.set(stats.currentConnections.toLong())
        authenticatedConnections.set(stats.authenticatedConnections.toLong())
        memoryUsage.set(stats.memoryUsage)
        uptime.set(stats.uptime / 1000) // Convert to seconds
    }
    
    /**
     * Get Prometheus metrics
     */
    fun getMetrics(): String {
        return if (meterRegistry is PrometheusMeterRegistry) {
            meterRegistry.scrape()
        } else {
            "# Metrics not available"
        }
    }
    
    /**
     * Check server health
     */
    fun getHealthStatus(): HealthStatus {
        val runtime = Runtime.getRuntime()
        val totalMemory = runtime.totalMemory()
        val freeMemory = runtime.freeMemory()
        val usedMemory = totalMemory - freeMemory
        val memoryUsagePercent = (usedMemory.toDouble() / totalMemory.toDouble()) * 100
        
        val isHealthy = memoryUsagePercent < 90.0 && currentConnections.get() >= 0
        
        return HealthStatus(
            status = if (isHealthy) "UP" else "DOWN",
            timestamp = System.currentTimeMillis(),
            uptime = uptime.get(),
            memoryUsed = usedMemory,
            memoryTotal = totalMemory,
            memoryUsagePercent = memoryUsagePercent,
            currentConnections = currentConnections.get(),
            authenticatedConnections = authenticatedConnections.get(),
            totalMessages = messagesTotal.count().toLong(),
            totalConnections = connectionsTotal.count().toLong()
        )
    }
    
    /**
     * Stop metrics service
     */
    fun stop() {
        try {
            httpServer?.stop()
            scope.cancel()
            logger.info { "📊 Metrics service stopped" }
        } catch (e: Exception) {
            logger.error(e) { "❌ Error stopping metrics service" }
        }
    }
}

/**
 * Health status data class
 */
data class HealthStatus(
    val status: String,
    val timestamp: Long,
    val uptime: Long,
    val memoryUsed: Long,
    val memoryTotal: Long,
    val memoryUsagePercent: Double,
    val currentConnections: Long,
    val authenticatedConnections: Long,
    val totalMessages: Long,
    val totalConnections: Long
)

/**
 * Simple HTTP server for metrics and health checks
 */
class SimpleHttpServer(
    private val host: String,
    private val port: Int,
    private val meterRegistry: MeterRegistry,
    private val config: MonitoringSettings
) {
    private var serverChannel: ServerSocketChannel? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    fun start() {
        try {
            serverChannel = ServerSocketChannel.open()
            serverChannel?.bind(InetSocketAddress(host, port))
            serverChannel?.configureBlocking(false)
            
            scope.launch {
                while (isActive) {
                    try {
                        val clientChannel = serverChannel?.accept()
                        if (clientChannel != null) {
                            launch {
                                handleClient(clientChannel)
                            }
                        } else {
                            delay(10) // Small delay when no connections
                        }
                    } catch (e: Exception) {
                        if (isActive) {
                            logger.error(e) { "Error accepting connection" }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to start HTTP server" }
            throw e
        }
    }
    
    fun stop() {
        try {
            scope.cancel()
            serverChannel?.close()
        } catch (e: Exception) {
            logger.error(e) { "Error stopping HTTP server" }
        }
    }
    
    private suspend fun handleClient(clientChannel: SocketChannel) {
        try {
            clientChannel.configureBlocking(false)
            
            val buffer = ByteBuffer.allocate(1024)
            val requestBuilder = StringBuilder()
            
            // Read request
            while (true) {
                val bytesRead = clientChannel.read(buffer)
                if (bytesRead > 0) {
                    buffer.flip()
                    requestBuilder.append(StandardCharsets.UTF_8.decode(buffer))
                    buffer.clear()
                    
                    // Check if we have complete request
                    if (requestBuilder.contains("\r\n\r\n")) {
                        break
                    }
                } else if (bytesRead == 0) {
                    delay(1)
                } else {
                    break
                }
            }
            
            val request = requestBuilder.toString()
            val response = processRequest(request)
            
            // Send response
            val responseBuffer = ByteBuffer.wrap(response.toByteArray(StandardCharsets.UTF_8))
            while (responseBuffer.hasRemaining()) {
                clientChannel.write(responseBuffer)
            }
            
        } catch (e: Exception) {
            logger.error(e) { "Error handling HTTP client" }
        } finally {
            try {
                clientChannel.close()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }
    
    private fun processRequest(request: String): String {
        val lines = request.split("\r\n")
        if (lines.isEmpty()) {
            return createHttpResponse(400, "Bad Request", "Invalid request")
        }
        
        val requestLine = lines[0]
        val parts = requestLine.split(" ")
        if (parts.size < 2) {
            return createHttpResponse(400, "Bad Request", "Invalid request line")
        }
        
        val method = parts[0]
        val path = parts[1]
        
        return when {
            method == "GET" && path == config.metricsPath -> {
                val metrics = if (meterRegistry is PrometheusMeterRegistry) {
                    meterRegistry.scrape()
                } else {
                    "# Metrics not available"
                }
                createHttpResponse(200, "OK", metrics, "text/plain; version=0.0.4; charset=utf-8")
            }
            
            method == "GET" && path == config.healthCheckPath -> {
                val healthStatus = getHealthStatus()
                val healthJson = """
                    {
                        "status": "${healthStatus.status}",
                        "timestamp": ${healthStatus.timestamp},
                        "uptime": ${healthStatus.uptime},
                        "memory": {
                            "used": ${healthStatus.memoryUsed},
                            "total": ${healthStatus.memoryTotal},
                            "usagePercent": ${healthStatus.memoryUsagePercent}
                        },
                        "connections": {
                            "current": ${healthStatus.currentConnections},
                            "authenticated": ${healthStatus.authenticatedConnections},
                            "total": ${healthStatus.totalConnections}
                        },
                        "messages": {
                            "total": ${healthStatus.totalMessages}
                        }
                    }
                """.trimIndent()
                createHttpResponse(200, "OK", healthJson, "application/json")
            }
            
            method == "GET" && path == "/" -> {
                val indexHtml = """
                    <!DOCTYPE html>
                    <html>
                    <head><title>MeshChat Server</title></head>
                    <body>
                        <h1>MeshChat Server</h1>
                        <ul>
                            <li><a href="${config.metricsPath}">Metrics</a></li>
                            <li><a href="${config.healthCheckPath}">Health Check</a></li>
                        </ul>
                    </body>
                    </html>
                """.trimIndent()
                createHttpResponse(200, "OK", indexHtml, "text/html")
            }
            
            else -> {
                createHttpResponse(404, "Not Found", "Path not found: $path")
            }
        }
    }
    
    private fun createHttpResponse(
        statusCode: Int,
        statusText: String,
        body: String,
        contentType: String = "text/plain"
    ): String {
        return """
            HTTP/1.1 $statusCode $statusText
            Content-Type: $contentType
            Content-Length: ${body.toByteArray(StandardCharsets.UTF_8).size}
            Connection: close
            
            $body
        """.trimIndent()
    }
    
    private fun getHealthStatus(): HealthStatus {
        val runtime = Runtime.getRuntime()
        val totalMemory = runtime.totalMemory()
        val freeMemory = runtime.freeMemory()
        val usedMemory = totalMemory - freeMemory
        val memoryUsagePercent = (usedMemory.toDouble() / totalMemory.toDouble()) * 100
        
        return HealthStatus(
            status = "UP",
            timestamp = System.currentTimeMillis(),
            uptime = 0, // Will be updated by MetricsService
            memoryUsed = usedMemory,
            memoryTotal = totalMemory,
            memoryUsagePercent = memoryUsagePercent,
            currentConnections = 0,
            authenticatedConnections = 0,
            totalMessages = 0,
            totalConnections = 0
        )
    }
}