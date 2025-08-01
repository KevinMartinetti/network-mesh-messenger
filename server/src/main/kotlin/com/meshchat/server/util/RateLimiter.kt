package com.meshchat.server.util

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

private val logger = KotlinLogging.logger {}

/**
 * Rate limiter for protecting against DDoS and spam
 * Implements token bucket algorithm with sliding window
 */
class RateLimiter(
    private val maxRequestsPerWindow: Int = 60,
    private val windowSizeMs: Long = 60_000, // 1 minute
    private val cleanupIntervalMs: Long = 300_000 // 5 minutes
) {
    
    private val clientBuckets = ConcurrentHashMap<String, TokenBucket>()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    init {
        startCleanupTask()
        logger.info { "🛡️ Rate limiter initialized: $maxRequestsPerWindow requests per ${windowSizeMs}ms" }
    }
    
    /**
     * Check if request is allowed for client
     */
    fun isAllowed(clientId: String): Boolean {
        val bucket = clientBuckets.computeIfAbsent(clientId) {
            TokenBucket(maxRequestsPerWindow, windowSizeMs)
        }
        
        val allowed = bucket.tryConsume()
        
        if (!allowed) {
            logger.warn { "🚫 Rate limit exceeded for client: $clientId" }
        }
        
        return allowed
    }
    
    /**
     * Check if request is allowed for IP address
     */
    fun isAllowedForIp(ipAddress: String): Boolean {
        return isAllowed("ip:$ipAddress")
    }
    
    /**
     * Check if request is allowed for user
     */
    fun isAllowedForUser(userId: String): Boolean {
        return isAllowed("user:$userId")
    }
    
    /**
     * Get remaining requests for client
     */
    fun getRemainingRequests(clientId: String): Int {
        val bucket = clientBuckets[clientId] ?: return maxRequestsPerWindow
        return bucket.getAvailableTokens()
    }
    
    /**
     * Get rate limit info for client
     */
    fun getRateLimitInfo(clientId: String): RateLimitInfo {
        val bucket = clientBuckets[clientId]
        return if (bucket != null) {
            RateLimitInfo(
                limit = maxRequestsPerWindow,
                remaining = bucket.getAvailableTokens(),
                resetTime = bucket.getResetTime(),
                windowSizeMs = windowSizeMs
            )
        } else {
            RateLimitInfo(
                limit = maxRequestsPerWindow,
                remaining = maxRequestsPerWindow,
                resetTime = System.currentTimeMillis() + windowSizeMs,
                windowSizeMs = windowSizeMs
            )
        }
    }
    
    /**
     * Reset rate limit for client (admin function)
     */
    fun resetClient(clientId: String) {
        clientBuckets.remove(clientId)
        logger.info { "🔄 Rate limit reset for client: $clientId" }
    }
    
    /**
     * Block client temporarily
     */
    fun blockClient(clientId: String, durationMs: Long) {
        val bucket = clientBuckets.computeIfAbsent(clientId) {
            TokenBucket(maxRequestsPerWindow, windowSizeMs)
        }
        bucket.block(durationMs)
        logger.warn { "🔒 Client blocked for ${durationMs}ms: $clientId" }
    }
    
    /**
     * Get statistics
     */
    fun getStatistics(): RateLimiterStats {
        val totalClients = clientBuckets.size
        val blockedClients = clientBuckets.values.count { it.isBlocked() }
        val activeClients = clientBuckets.values.count { it.hasRecentActivity() }
        
        return RateLimiterStats(
            totalClients = totalClients,
            blockedClients = blockedClients,
            activeClients = activeClients,
            maxRequestsPerWindow = maxRequestsPerWindow,
            windowSizeMs = windowSizeMs
        )
    }
    
    /**
     * Start cleanup task to remove old buckets
     */
    private fun startCleanupTask() {
        scope.launch {
            while (isActive) {
                try {
                    cleanupOldBuckets()
                    delay(cleanupIntervalMs)
                } catch (e: Exception) {
                    logger.error(e) { "❌ Error in rate limiter cleanup task" }
                }
            }
        }
    }
    
    /**
     * Clean up old inactive buckets
     */
    private fun cleanupOldBuckets() {
        val now = System.currentTimeMillis()
        val cutoffTime = now - (windowSizeMs * 2) // Keep buckets for 2 windows
        
        val keysToRemove = clientBuckets.entries
            .filter { (_, bucket) -> bucket.getLastAccessTime() < cutoffTime }
            .map { it.key }
        
        keysToRemove.forEach { key ->
            clientBuckets.remove(key)
        }
        
        if (keysToRemove.isNotEmpty()) {
            logger.debug { "🧹 Cleaned up ${keysToRemove.size} old rate limit buckets" }
        }
    }
    
    /**
     * Stop rate limiter
     */
    fun stop() {
        scope.cancel()
        clientBuckets.clear()
        logger.info { "🛑 Rate limiter stopped" }
    }
}

/**
 * Token bucket implementation for rate limiting
 */
class TokenBucket(
    private val maxTokens: Int,
    private val windowSizeMs: Long
) {
    private val tokens = AtomicInteger(maxTokens)
    private val lastRefillTime = AtomicLong(System.currentTimeMillis())
    private val lastAccessTime = AtomicLong(System.currentTimeMillis())
    private val blockedUntil = AtomicLong(0)
    
    /**
     * Try to consume a token
     */
    fun tryConsume(): Boolean {
        lastAccessTime.set(System.currentTimeMillis())
        
        // Check if blocked
        if (isBlocked()) {
            return false
        }
        
        refillTokens()
        
        val currentTokens = tokens.get()
        if (currentTokens > 0) {
            return tokens.compareAndSet(currentTokens, currentTokens - 1)
        }
        
        return false
    }
    
    /**
     * Refill tokens based on elapsed time
     */
    private fun refillTokens() {
        val now = System.currentTimeMillis()
        val lastRefill = lastRefillTime.get()
        val timePassed = now - lastRefill
        
        if (timePassed >= windowSizeMs) {
            // Full window passed, refill completely
            tokens.set(maxTokens)
            lastRefillTime.set(now)
        }
    }
    
    /**
     * Get available tokens
     */
    fun getAvailableTokens(): Int {
        if (isBlocked()) return 0
        refillTokens()
        return tokens.get()
    }
    
    /**
     * Get reset time
     */
    fun getResetTime(): Long {
        return lastRefillTime.get() + windowSizeMs
    }
    
    /**
     * Get last access time
     */
    fun getLastAccessTime(): Long {
        return lastAccessTime.get()
    }
    
    /**
     * Check if bucket is blocked
     */
    fun isBlocked(): Boolean {
        val blockedTime = blockedUntil.get()
        if (blockedTime > 0 && System.currentTimeMillis() < blockedTime) {
            return true
        }
        
        // Clear block if expired
        if (blockedTime > 0) {
            blockedUntil.compareAndSet(blockedTime, 0)
        }
        
        return false
    }
    
    /**
     * Block bucket for specified duration
     */
    fun block(durationMs: Long) {
        val blockUntil = System.currentTimeMillis() + durationMs
        blockedUntil.set(blockUntil)
        tokens.set(0) // Clear all tokens
    }
    
    /**
     * Check if bucket has recent activity
     */
    fun hasRecentActivity(): Boolean {
        val now = System.currentTimeMillis()
        return (now - lastAccessTime.get()) < windowSizeMs
    }
}

/**
 * Rate limit information
 */
data class RateLimitInfo(
    val limit: Int,
    val remaining: Int,
    val resetTime: Long,
    val windowSizeMs: Long
) {
    fun getResetTimeSeconds(): Long = resetTime / 1000
    fun getRetryAfterSeconds(): Long = maxOf(0, (resetTime - System.currentTimeMillis()) / 1000)
}

/**
 * Rate limiter statistics
 */
data class RateLimiterStats(
    val totalClients: Int,
    val blockedClients: Int,
    val activeClients: Int,
    val maxRequestsPerWindow: Int,
    val windowSizeMs: Long
)