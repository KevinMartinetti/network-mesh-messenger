package com.meshchat.server.config

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import java.time.Duration

/**
 * Server configuration with environment-specific settings
 */
data class ServerConfig(
    val server: ServerSettings,
    val database: DatabaseSettings,
    val security: SecuritySettings,
    val monitoring: MonitoringSettings
) {
    companion object {
        const val VERSION = "1.0.0"
        
        fun load(): ServerConfig {
            val config = ConfigFactory.load()
            return ServerConfig(
                server = ServerSettings.from(config.getConfig("server")),
                database = DatabaseSettings.from(config.getConfig("database")),
                security = SecuritySettings.from(config.getConfig("security")),
                monitoring = MonitoringSettings.from(config.getConfig("monitoring"))
            )
        }
    }
}

data class ServerSettings(
    val host: String,
    val port: Int,
    val maxConnections: Int,
    val connectionTimeout: Duration,
    val heartbeatInterval: Duration,
    val bufferSize: Int,
    val workerThreads: Int
) {
    companion object {
        fun from(config: Config) = ServerSettings(
            host = config.getString("host"),
            port = config.getInt("port"),
            maxConnections = config.getInt("maxConnections"),
            connectionTimeout = config.getDuration("connectionTimeout"),
            heartbeatInterval = config.getDuration("heartbeatInterval"),
            bufferSize = config.getInt("bufferSize"),
            workerThreads = config.getInt("workerThreads")
        )
    }
}

data class DatabaseSettings(
    val driver: String,
    val url: String,
    val username: String,
    val password: String,
    val maxPoolSize: Int,
    val connectionTimeout: Duration,
    val maxLifetime: Duration
) {
    companion object {
        fun from(config: Config) = DatabaseSettings(
            driver = config.getString("driver"),
            url = config.getString("url"),
            username = config.getString("username"),
            password = config.getString("password"),
            maxPoolSize = config.getInt("maxPoolSize"),
            connectionTimeout = config.getDuration("connectionTimeout"),
            maxLifetime = config.getDuration("maxLifetime")
        )
    }
}

data class SecuritySettings(
    val rsaKeySize: Int,
    val aesKeySize: Int,
    val sessionTimeout: Duration,
    val maxFailedAttempts: Int,
    val rateLimitPerMinute: Int,
    val enableTls: Boolean,
    val keystorePath: String?,
    val keystorePassword: String?
) {
    companion object {
        fun from(config: Config) = SecuritySettings(
            rsaKeySize = config.getInt("rsaKeySize"),
            aesKeySize = config.getInt("aesKeySize"),
            sessionTimeout = config.getDuration("sessionTimeout"),
            maxFailedAttempts = config.getInt("maxFailedAttempts"),
            rateLimitPerMinute = config.getInt("rateLimitPerMinute"),
            enableTls = config.getBoolean("enableTls"),
            keystorePath = if (config.hasPath("keystorePath")) config.getString("keystorePath") else null,
            keystorePassword = if (config.hasPath("keystorePassword")) config.getString("keystorePassword") else null
        )
    }
}

data class MonitoringSettings(
    val host: String,
    val port: Int,
    val enableMetrics: Boolean,
    val metricsPath: String,
    val healthCheckPath: String,
    val logLevel: String
) {
    companion object {
        fun from(config: Config) = MonitoringSettings(
            host = config.getString("host"),
            port = config.getInt("port"),
            enableMetrics = config.getBoolean("enableMetrics"),
            metricsPath = config.getString("metricsPath"),
            healthCheckPath = config.getString("healthCheckPath"),
            logLevel = config.getString("logLevel")
        )
    }
}