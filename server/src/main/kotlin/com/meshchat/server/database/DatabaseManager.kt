package com.meshchat.server.database

import com.meshchat.server.config.DatabaseSettings
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.java.JavaInstantColumnType
import java.time.Instant

private val logger = KotlinLogging.logger {}

/**
 * Database manager using Exposed ORM
 * Handles database connections and schema management
 */
class DatabaseManager(private val config: DatabaseSettings) {
    
    private var database: Database? = null
    
    /**
     * Initialize database connection and create tables
     */
    fun initialize() {
        try {
            // Connect to database
            database = Database.connect(
                url = config.url,
                driver = config.driver,
                user = config.username,
                password = config.password
            )
            
            logger.info { "🗄️ Connected to database: ${config.url}" }
            
            // Create tables
            transaction {
                SchemaUtils.create(Users, Messages, Rooms, UserRooms)
                logger.info { "📋 Database schema initialized" }
            }
            
        } catch (e: Exception) {
            logger.error(e) { "❌ Failed to initialize database" }
            throw e
        }
    }
    
    /**
     * Close database connections
     */
    fun close() {
        logger.info { "🔒 Closing database connections" }
        // Exposed doesn't provide explicit close method for Database
        // Connection pooling is handled automatically
    }
    
    /**
     * Execute transaction with proper error handling
     */
    fun <T> executeTransaction(block: Transaction.() -> T): T {
        return transaction(database) {
            block()
        }
    }
    
    /**
     * Check database health
     */
    fun isHealthy(): Boolean {
        return try {
            transaction {
                exec("SELECT 1")
            }
            true
        } catch (e: Exception) {
            logger.error(e) { "❌ Database health check failed" }
            false
        }
    }
}

/**
 * Users table definition
 */
object Users : Table("users") {
    val id = varchar("id", 64).primaryKey()
    val username = varchar("username", 100)
    val publicKey = text("public_key")
    val isHost = bool("is_host").default(false)
    val isOnline = bool("is_online").default(true)
    val lastSeen = long("last_seen")
    val connectionId = varchar("connection_id", 64).nullable()
    val ipAddress = varchar("ip_address", 45).nullable()
    val createdAt = long("created_at").default(System.currentTimeMillis())
    val updatedAt = long("updated_at").default(System.currentTimeMillis())
    
    init {
        index(false, username)
        index(false, isOnline)
        index(false, lastSeen)
    }
}

/**
 * Messages table definition
 */
object Messages : Table("messages") {
    val id = varchar("id", 64).primaryKey()
    val content = text("content")
    val senderId = varchar("sender_id", 64)
    val senderName = varchar("sender_name", 100)
    val timestamp = long("timestamp")
    val type = varchar("type", 20).default("TEXT")
    val roomId = varchar("room_id", 64).nullable()
    val isEncrypted = bool("is_encrypted").default(true)
    val createdAt = long("created_at").default(System.currentTimeMillis())
    
    init {
        index(false, senderId)
        index(false, timestamp)
        index(false, roomId)
        index(false, type)
    }
}

/**
 * Rooms table definition (for group chats)
 */
object Rooms : Table("rooms") {
    val id = varchar("id", 64).primaryKey()
    val name = varchar("name", 200)
    val description = text("description").nullable()
    val createdBy = varchar("created_by", 64)
    val createdAt = long("created_at")
    val isPublic = bool("is_public").default(true)
    val maxUsers = integer("max_users").default(100)
    val currentUsers = integer("current_users").default(0)
    
    init {
        index(false, createdBy)
        index(false, isPublic)
        index(false, createdAt)
    }
}

/**
 * User-Room relationship table
 */
object UserRooms : Table("user_rooms") {
    val userId = varchar("user_id", 64)
    val roomId = varchar("room_id", 64)
    val joinedAt = long("joined_at").default(System.currentTimeMillis())
    val role = varchar("role", 20).default("MEMBER") // ADMIN, MODERATOR, MEMBER
    val isActive = bool("is_active").default(true)
    
    override val primaryKey = PrimaryKey(userId, roomId)
    
    init {
        index(false, userId)
        index(false, roomId)
        index(false, joinedAt)
    }
}