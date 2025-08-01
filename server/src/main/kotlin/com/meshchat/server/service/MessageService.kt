package com.meshchat.server.service

import com.meshchat.server.database.DatabaseManager
import com.meshchat.server.database.Messages
import com.meshchat.server.model.Message
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq

private val logger = KotlinLogging.logger {}

/**
 * Service for managing messages
 */
class MessageService(private val databaseManager: DatabaseManager) {
    
    /**
     * Store a message
     */
    fun storeMessage(message: Message) {
        try {
            databaseManager.executeTransaction {
                Messages.insert {
                    it[id] = message.id
                    it[content] = message.content
                    it[senderId] = message.senderId
                    it[senderName] = message.senderName
                    it[timestamp] = message.timestamp
                    it[type] = message.type.name
                    it[roomId] = message.roomId
                    it[isEncrypted] = message.isEncrypted
                }
            }
            logger.debug { "💾 Stored message: ${message.id}" }
        } catch (e: Exception) {
            logger.error(e) { "❌ Failed to store message: ${message.id}" }
            throw e
        }
    }
    
    /**
     * Get message by ID
     */
    fun getMessageById(messageId: String): Message? {
        return try {
            databaseManager.executeTransaction {
                Messages.select { Messages.id eq messageId }
                    .singleOrNull()
                    ?.let { row ->
                        Message(
                            id = row[Messages.id],
                            content = row[Messages.content],
                            senderId = row[Messages.senderId],
                            senderName = row[Messages.senderName],
                            timestamp = row[Messages.timestamp],
                            type = Message.MessageType.valueOf(row[Messages.type]),
                            roomId = row[Messages.roomId],
                            isEncrypted = row[Messages.isEncrypted]
                        )
                    }
            }
        } catch (e: Exception) {
            logger.error(e) { "❌ Failed to get message by ID: $messageId" }
            null
        }
    }
    
    /**
     * Get recent messages (with pagination)
     */
    fun getRecentMessages(limit: Int = 50, offset: Long = 0, roomId: String? = null): List<Message> {
        return try {
            databaseManager.executeTransaction {
                val query = if (roomId != null) {
                    Messages.select { Messages.roomId eq roomId }
                } else {
                    Messages.select { Messages.roomId.isNull() }
                }
                
                query.orderBy(Messages.timestamp, SortOrder.DESC)
                    .limit(limit, offset)
                    .map { row ->
                        Message(
                            id = row[Messages.id],
                            content = row[Messages.content],
                            senderId = row[Messages.senderId],
                            senderName = row[Messages.senderName],
                            timestamp = row[Messages.timestamp],
                            type = Message.MessageType.valueOf(row[Messages.type]),
                            roomId = row[Messages.roomId],
                            isEncrypted = row[Messages.isEncrypted]
                        )
                    }
            }
        } catch (e: Exception) {
            logger.error(e) { "❌ Failed to get recent messages" }
            emptyList()
        }
    }
    
    /**
     * Get messages by sender
     */
    fun getMessagesBySender(senderId: String, limit: Int = 50, offset: Long = 0): List<Message> {
        return try {
            databaseManager.executeTransaction {
                Messages.select { Messages.senderId eq senderId }
                    .orderBy(Messages.timestamp, SortOrder.DESC)
                    .limit(limit, offset)
                    .map { row ->
                        Message(
                            id = row[Messages.id],
                            content = row[Messages.content],
                            senderId = row[Messages.senderId],
                            senderName = row[Messages.senderName],
                            timestamp = row[Messages.timestamp],
                            type = Message.MessageType.valueOf(row[Messages.type]),
                            roomId = row[Messages.roomId],
                            isEncrypted = row[Messages.isEncrypted]
                        )
                    }
            }
        } catch (e: Exception) {
            logger.error(e) { "❌ Failed to get messages by sender: $senderId" }
            emptyList()
        }
    }
    
    /**
     * Get messages in time range
     */
    fun getMessagesInTimeRange(
        startTime: Long,
        endTime: Long,
        roomId: String? = null,
        limit: Int = 100
    ): List<Message> {
        return try {
            databaseManager.executeTransaction {
                val baseQuery = Messages.select { 
                    Messages.timestamp.between(startTime, endTime)
                }
                
                val query = if (roomId != null) {
                    baseQuery.andWhere { Messages.roomId eq roomId }
                } else {
                    baseQuery.andWhere { Messages.roomId.isNull() }
                }
                
                query.orderBy(Messages.timestamp, SortOrder.ASC)
                    .limit(limit)
                    .map { row ->
                        Message(
                            id = row[Messages.id],
                            content = row[Messages.content],
                            senderId = row[Messages.senderId],
                            senderName = row[Messages.senderName],
                            timestamp = row[Messages.timestamp],
                            type = Message.MessageType.valueOf(row[Messages.type]),
                            roomId = row[Messages.roomId],
                            isEncrypted = row[Messages.isEncrypted]
                        )
                    }
            }
        } catch (e: Exception) {
            logger.error(e) { "❌ Failed to get messages in time range" }
            emptyList()
        }
    }
    
    /**
     * Get messages by type
     */
    fun getMessagesByType(messageType: Message.MessageType, limit: Int = 50): List<Message> {
        return try {
            databaseManager.executeTransaction {
                Messages.select { Messages.type eq messageType.name }
                    .orderBy(Messages.timestamp, SortOrder.DESC)
                    .limit(limit)
                    .map { row ->
                        Message(
                            id = row[Messages.id],
                            content = row[Messages.content],
                            senderId = row[Messages.senderId],
                            senderName = row[Messages.senderName],
                            timestamp = row[Messages.timestamp],
                            type = Message.MessageType.valueOf(row[Messages.type]),
                            roomId = row[Messages.roomId],
                            isEncrypted = row[Messages.isEncrypted]
                        )
                    }
            }
        } catch (e: Exception) {
            logger.error(e) { "❌ Failed to get messages by type: $messageType" }
            emptyList()
        }
    }
    
    /**
     * Search messages by content
     */
    fun searchMessages(searchQuery: String, limit: Int = 50, roomId: String? = null): List<Message> {
        return try {
            databaseManager.executeTransaction {
                val baseQuery = Messages.select { 
                    Messages.content.lowerCase() like "%${searchQuery.lowercase()}%"
                }
                
                val query = if (roomId != null) {
                    baseQuery.andWhere { Messages.roomId eq roomId }
                } else {
                    baseQuery.andWhere { Messages.roomId.isNull() }
                }
                
                query.orderBy(Messages.timestamp, SortOrder.DESC)
                    .limit(limit)
                    .map { row ->
                        Message(
                            id = row[Messages.id],
                            content = row[Messages.content],
                            senderId = row[Messages.senderId],
                            senderName = row[Messages.senderName],
                            timestamp = row[Messages.timestamp],
                            type = Message.MessageType.valueOf(row[Messages.type]),
                            roomId = row[Messages.roomId],
                            isEncrypted = row[Messages.isEncrypted]
                        )
                    }
            }
        } catch (e: Exception) {
            logger.error(e) { "❌ Failed to search messages: $searchQuery" }
            emptyList()
        }
    }
    
    /**
     * Delete message by ID
     */
    fun deleteMessage(messageId: String): Boolean {
        return try {
            databaseManager.executeTransaction {
                val deletedRows = Messages.deleteWhere { Messages.id eq messageId }
                if (deletedRows > 0) {
                    logger.info { "🗑️ Deleted message: $messageId" }
                    true
                } else {
                    false
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "❌ Failed to delete message: $messageId" }
            false
        }
    }
    
    /**
     * Delete messages by sender
     */
    fun deleteMessagesBySender(senderId: String): Int {
        return try {
            databaseManager.executeTransaction {
                val deletedRows = Messages.deleteWhere { Messages.senderId eq senderId }
                if (deletedRows > 0) {
                    logger.info { "🗑️ Deleted $deletedRows messages from sender: $senderId" }
                }
                deletedRows
            }
        } catch (e: Exception) {
            logger.error(e) { "❌ Failed to delete messages by sender: $senderId" }
            0
        }
    }
    
    /**
     * Delete old messages (older than specified time)
     */
    fun deleteOldMessages(olderThanMs: Long): Int {
        return try {
            val cutoffTime = System.currentTimeMillis() - olderThanMs
            
            databaseManager.executeTransaction {
                val deletedRows = Messages.deleteWhere { Messages.timestamp less cutoffTime }
                if (deletedRows > 0) {
                    logger.info { "🧹 Deleted $deletedRows old messages" }
                }
                deletedRows
            }
        } catch (e: Exception) {
            logger.error(e) { "❌ Failed to delete old messages" }
            0
        }
    }
    
    /**
     * Get message count
     */
    fun getMessageCount(roomId: String? = null): Long {
        return try {
            databaseManager.executeTransaction {
                if (roomId != null) {
                    Messages.select { Messages.roomId eq roomId }.count()
                } else {
                    Messages.select { Messages.roomId.isNull() }.count()
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "❌ Failed to get message count" }
            0
        }
    }
    
    /**
     * Get message count by sender
     */
    fun getMessageCountBySender(senderId: String): Long {
        return try {
            databaseManager.executeTransaction {
                Messages.select { Messages.senderId eq senderId }.count()
            }
        } catch (e: Exception) {
            logger.error(e) { "❌ Failed to get message count by sender: $senderId" }
            0
        }
    }
    
    /**
     * Get message count by type
     */
    fun getMessageCountByType(messageType: Message.MessageType): Long {
        return try {
            databaseManager.executeTransaction {
                Messages.select { Messages.type eq messageType.name }.count()
            }
        } catch (e: Exception) {
            logger.error(e) { "❌ Failed to get message count by type: $messageType" }
            0
        }
    }
    
    /**
     * Get message statistics
     */
    fun getMessageStatistics(): MessageStatistics {
        return try {
            databaseManager.executeTransaction {
                val totalMessages = Messages.selectAll().count()
                val textMessages = Messages.select { Messages.type eq Message.MessageType.TEXT.name }.count()
                val systemMessages = Messages.select { Messages.type eq Message.MessageType.SYSTEM.name }.count()
                val fileMessages = Messages.select { Messages.type eq Message.MessageType.FILE.name }.count()
                val imageMessages = Messages.select { Messages.type eq Message.MessageType.IMAGE.name }.count()
                
                val oldestMessage = Messages.selectAll()
                    .orderBy(Messages.timestamp, SortOrder.ASC)
                    .limit(1)
                    .singleOrNull()
                    ?.get(Messages.timestamp)
                
                val newestMessage = Messages.selectAll()
                    .orderBy(Messages.timestamp, SortOrder.DESC)
                    .limit(1)
                    .singleOrNull()
                    ?.get(Messages.timestamp)
                
                MessageStatistics(
                    totalMessages = totalMessages,
                    textMessages = textMessages,
                    systemMessages = systemMessages,
                    fileMessages = fileMessages,
                    imageMessages = imageMessages,
                    oldestMessageTime = oldestMessage,
                    newestMessageTime = newestMessage
                )
            }
        } catch (e: Exception) {
            logger.error(e) { "❌ Failed to get message statistics" }
            MessageStatistics()
        }
    }
}

/**
 * Message statistics data class
 */
data class MessageStatistics(
    val totalMessages: Long = 0,
    val textMessages: Long = 0,
    val systemMessages: Long = 0,
    val fileMessages: Long = 0,
    val imageMessages: Long = 0,
    val oldestMessageTime: Long? = null,
    val newestMessageTime: Long? = null
)