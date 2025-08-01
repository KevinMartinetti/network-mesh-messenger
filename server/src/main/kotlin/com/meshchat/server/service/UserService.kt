package com.meshchat.server.service

import com.meshchat.server.database.DatabaseManager
import com.meshchat.server.database.Users
import com.meshchat.server.model.User
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq

private val logger = KotlinLogging.logger {}

/**
 * Service for managing users
 */
class UserService(private val databaseManager: DatabaseManager) {
    
    /**
     * Add or update user
     */
    fun addUser(user: User) {
        try {
            databaseManager.executeTransaction {
                val existingUser = Users.select { Users.id eq user.id }.singleOrNull()
                
                if (existingUser != null) {
                    // Update existing user
                    Users.update({ Users.id eq user.id }) {
                        it[username] = user.username
                        it[publicKey] = user.publicKey
                        it[isHost] = user.isHost
                        it[isOnline] = user.isOnline
                        it[lastSeen] = user.lastSeen
                        it[connectionId] = user.connectionId
                        it[ipAddress] = user.ipAddress
                        it[updatedAt] = System.currentTimeMillis()
                    }
                    logger.debug { "👤 Updated user: ${user.username}" }
                } else {
                    // Insert new user
                    Users.insert {
                        it[id] = user.id
                        it[username] = user.username
                        it[publicKey] = user.publicKey
                        it[isHost] = user.isHost
                        it[isOnline] = user.isOnline
                        it[lastSeen] = user.lastSeen
                        it[connectionId] = user.connectionId
                        it[ipAddress] = user.ipAddress
                    }
                    logger.info { "👤 Added new user: ${user.username}" }
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "❌ Failed to add/update user: ${user.username}" }
            throw e
        }
    }
    
    /**
     * Get user by ID
     */
    fun getUserById(userId: String): User? {
        return try {
            databaseManager.executeTransaction {
                Users.select { Users.id eq userId }
                    .singleOrNull()
                    ?.let { row ->
                        User(
                            id = row[Users.id],
                            username = row[Users.username],
                            publicKey = row[Users.publicKey],
                            isHost = row[Users.isHost],
                            isOnline = row[Users.isOnline],
                            lastSeen = row[Users.lastSeen],
                            connectionId = row[Users.connectionId],
                            ipAddress = row[Users.ipAddress]
                        )
                    }
            }
        } catch (e: Exception) {
            logger.error(e) { "❌ Failed to get user by ID: $userId" }
            null
        }
    }
    
    /**
     * Get user by username
     */
    fun getUserByUsername(username: String): User? {
        return try {
            databaseManager.executeTransaction {
                Users.select { Users.username eq username }
                    .singleOrNull()
                    ?.let { row ->
                        User(
                            id = row[Users.id],
                            username = row[Users.username],
                            publicKey = row[Users.publicKey],
                            isHost = row[Users.isHost],
                            isOnline = row[Users.isOnline],
                            lastSeen = row[Users.lastSeen],
                            connectionId = row[Users.connectionId],
                            ipAddress = row[Users.ipAddress]
                        )
                    }
            }
        } catch (e: Exception) {
            logger.error(e) { "❌ Failed to get user by username: $username" }
            null
        }
    }
    
    /**
     * Get all online users
     */
    fun getOnlineUsers(): List<User> {
        return try {
            databaseManager.executeTransaction {
                Users.select { Users.isOnline eq true }
                    .orderBy(Users.username)
                    .map { row ->
                        User(
                            id = row[Users.id],
                            username = row[Users.username],
                            publicKey = row[Users.publicKey],
                            isHost = row[Users.isHost],
                            isOnline = row[Users.isOnline],
                            lastSeen = row[Users.lastSeen],
                            connectionId = row[Users.connectionId],
                            ipAddress = row[Users.ipAddress]
                        )
                    }
            }
        } catch (e: Exception) {
            logger.error(e) { "❌ Failed to get online users" }
            emptyList()
        }
    }
    
    /**
     * Get all users (with pagination)
     */
    fun getAllUsers(limit: Int = 100, offset: Long = 0): List<User> {
        return try {
            databaseManager.executeTransaction {
                Users.selectAll()
                    .orderBy(Users.createdAt, SortOrder.DESC)
                    .limit(limit, offset)
                    .map { row ->
                        User(
                            id = row[Users.id],
                            username = row[Users.username],
                            publicKey = row[Users.publicKey],
                            isHost = row[Users.isHost],
                            isOnline = row[Users.isOnline],
                            lastSeen = row[Users.lastSeen],
                            connectionId = row[Users.connectionId],
                            ipAddress = row[Users.ipAddress]
                        )
                    }
            }
        } catch (e: Exception) {
            logger.error(e) { "❌ Failed to get all users" }
            emptyList()
        }
    }
    
    /**
     * Set user online status
     */
    fun setUserOnline(userId: String, isOnline: Boolean = true) {
        try {
            databaseManager.executeTransaction {
                Users.update({ Users.id eq userId }) {
                    it[Users.isOnline] = isOnline
                    it[lastSeen] = System.currentTimeMillis()
                    it[updatedAt] = System.currentTimeMillis()
                }
            }
            logger.debug { "🔄 Set user $userId online status: $isOnline" }
        } catch (e: Exception) {
            logger.error(e) { "❌ Failed to set user online status: $userId" }
        }
    }
    
    /**
     * Set user offline
     */
    fun setUserOffline(userId: String) {
        setUserOnline(userId, false)
    }
    
    /**
     * Update user connection info
     */
    fun updateUserConnection(userId: String, connectionId: String?, ipAddress: String?) {
        try {
            databaseManager.executeTransaction {
                Users.update({ Users.id eq userId }) {
                    it[Users.connectionId] = connectionId
                    it[Users.ipAddress] = ipAddress
                    it[lastSeen] = System.currentTimeMillis()
                    it[updatedAt] = System.currentTimeMillis()
                }
            }
            logger.debug { "🔄 Updated connection info for user: $userId" }
        } catch (e: Exception) {
            logger.error(e) { "❌ Failed to update user connection: $userId" }
        }
    }
    
    /**
     * Remove user
     */
    fun removeUser(userId: String) {
        try {
            databaseManager.executeTransaction {
                val deletedRows = Users.deleteWhere { Users.id eq userId }
                if (deletedRows > 0) {
                    logger.info { "🗑️ Removed user: $userId" }
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "❌ Failed to remove user: $userId" }
        }
    }
    
    /**
     * Check if username exists
     */
    fun usernameExists(username: String): Boolean {
        return try {
            databaseManager.executeTransaction {
                Users.select { Users.username eq username }.count() > 0
            }
        } catch (e: Exception) {
            logger.error(e) { "❌ Failed to check username existence: $username" }
            false
        }
    }
    
    /**
     * Get user count
     */
    fun getUserCount(): Long {
        return try {
            databaseManager.executeTransaction {
                Users.selectAll().count()
            }
        } catch (e: Exception) {
            logger.error(e) { "❌ Failed to get user count" }
            0
        }
    }
    
    /**
     * Get online user count
     */
    fun getOnlineUserCount(): Long {
        return try {
            databaseManager.executeTransaction {
                Users.select { Users.isOnline eq true }.count()
            }
        } catch (e: Exception) {
            logger.error(e) { "❌ Failed to get online user count" }
            0
        }
    }
    
    /**
     * Clean up inactive users (offline for more than specified time)
     */
    fun cleanupInactiveUsers(inactiveThresholdMs: Long) {
        try {
            val cutoffTime = System.currentTimeMillis() - inactiveThresholdMs
            
            databaseManager.executeTransaction {
                val deletedRows = Users.deleteWhere { 
                    (Users.isOnline eq false) and (Users.lastSeen less cutoffTime)
                }
                
                if (deletedRows > 0) {
                    logger.info { "🧹 Cleaned up $deletedRows inactive users" }
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "❌ Failed to cleanup inactive users" }
        }
    }
    
    /**
     * Update user last seen timestamp
     */
    fun updateLastSeen(userId: String) {
        try {
            databaseManager.executeTransaction {
                Users.update({ Users.id eq userId }) {
                    it[lastSeen] = System.currentTimeMillis()
                    it[updatedAt] = System.currentTimeMillis()
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "❌ Failed to update last seen for user: $userId" }
        }
    }
    
    /**
     * Get users by connection status
     */
    fun getUsersByConnectionStatus(hasConnection: Boolean): List<User> {
        return try {
            databaseManager.executeTransaction {
                val query = if (hasConnection) {
                    Users.select { Users.connectionId.isNotNull() }
                } else {
                    Users.select { Users.connectionId.isNull() }
                }
                
                query.map { row ->
                    User(
                        id = row[Users.id],
                        username = row[Users.username],
                        publicKey = row[Users.publicKey],
                        isHost = row[Users.isHost],
                        isOnline = row[Users.isOnline],
                        lastSeen = row[Users.lastSeen],
                        connectionId = row[Users.connectionId],
                        ipAddress = row[Users.ipAddress]
                    )
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "❌ Failed to get users by connection status" }
            emptyList()
        }
    }
}