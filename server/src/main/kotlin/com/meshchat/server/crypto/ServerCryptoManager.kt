package com.meshchat.server.crypto

import io.github.oshai.kotlinlogging.KotlinLogging
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.*
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

private val logger = KotlinLogging.logger {}

/**
 * Server-side cryptography manager compatible with Android client
 * Handles AES-256-GCM encryption and RSA-4096 key exchange
 */
class ServerCryptoManager {
    
    companion object {
        private const val RSA_KEY_SIZE = 4096
        private const val AES_KEY_SIZE = 256
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 16
        
        private const val RSA_TRANSFORMATION = "RSA/ECB/OAEPWITHSHA-256ANDMGF1PADDING"
        private const val AES_TRANSFORMATION = "AES/GCM/NoPadding"
        
        init {
            // Add BouncyCastle provider for enhanced cryptography
            if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
                Security.addProvider(BouncyCastleProvider())
            }
        }
    }
    
    private val serverKeyPair: KeyPair
    private val sessionKeys = ConcurrentHashMap<String, SecretKey>()
    private val clientPublicKeys = ConcurrentHashMap<String, PublicKey>()
    
    init {
        serverKeyPair = generateKeyPair()
        logger.info { "🔐 Server RSA-$RSA_KEY_SIZE key pair generated" }
    }
    
    /**
     * Generate RSA-4096 key pair
     */
    private fun generateKeyPair(): KeyPair {
        val keyGenerator = KeyPairGenerator.getInstance("RSA", BouncyCastleProvider.PROVIDER_NAME)
        keyGenerator.initialize(RSA_KEY_SIZE, SecureRandom())
        return keyGenerator.generateKeyPair()
    }
    
    /**
     * Get server's public key as Base64 string
     */
    fun getServerPublicKey(): String {
        return Base64.getEncoder().encodeToString(serverKeyPair.public.encoded)
    }
    
    /**
     * Store client's public key
     */
    fun storeClientPublicKey(clientId: String, publicKeyString: String) {
        try {
            val publicKeyBytes = Base64.getDecoder().decode(publicKeyString)
            val keyFactory = KeyFactory.getInstance("RSA")
            val publicKey = keyFactory.generatePublic(X509EncodedKeySpec(publicKeyBytes))
            
            clientPublicKeys[clientId] = publicKey
            logger.debug { "🔑 Stored public key for client: $clientId" }
        } catch (e: Exception) {
            logger.error(e) { "❌ Failed to store public key for client: $clientId" }
            throw CryptoException("Invalid public key format", e)
        }
    }
    
    /**
     * Generate AES-256 session key for client
     */
    fun generateSessionKey(clientId: String): SecretKey {
        val keyGenerator = KeyGenerator.getInstance("AES")
        keyGenerator.init(AES_KEY_SIZE)
        val sessionKey = keyGenerator.generateKey()
        
        sessionKeys[clientId] = sessionKey
        logger.debug { "🔐 Generated session key for client: $clientId" }
        
        return sessionKey
    }
    
    /**
     * Encrypt session key with client's RSA public key
     */
    fun encryptSessionKey(sessionKey: SecretKey, clientId: String): String {
        val clientPublicKey = clientPublicKeys[clientId]
            ?: throw CryptoException("Client public key not found: $clientId")
        
        try {
            val cipher = Cipher.getInstance(RSA_TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, clientPublicKey)
            
            val encryptedKey = cipher.doFinal(sessionKey.encoded)
            return Base64.getEncoder().encodeToString(encryptedKey)
        } catch (e: Exception) {
            logger.error(e) { "❌ Failed to encrypt session key for client: $clientId" }
            throw CryptoException("Failed to encrypt session key", e)
        }
    }
    
    /**
     * Decrypt session key with server's RSA private key
     */
    fun decryptSessionKey(encryptedSessionKey: String): SecretKey {
        try {
            val encryptedKeyBytes = Base64.getDecoder().decode(encryptedSessionKey)
            
            val cipher = Cipher.getInstance(RSA_TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, serverKeyPair.private)
            
            val decryptedKeyBytes = cipher.doFinal(encryptedKeyBytes)
            return SecretKeySpec(decryptedKeyBytes, "AES")
        } catch (e: Exception) {
            logger.error(e) { "❌ Failed to decrypt session key" }
            throw CryptoException("Failed to decrypt session key", e)
        }
    }
    
    /**
     * Encrypt message with AES-256-GCM
     */
    fun encryptMessage(message: String, clientId: String): EncryptedMessage {
        val sessionKey = sessionKeys[clientId]
            ?: throw CryptoException("Session key not found for client: $clientId")
        
        return encryptMessage(message, sessionKey)
    }
    
    /**
     * Encrypt message with specific session key
     */
    fun encryptMessage(message: String, sessionKey: SecretKey): EncryptedMessage {
        try {
            val cipher = Cipher.getInstance(AES_TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, sessionKey)
            
            val iv = cipher.iv
            val encryptedData = cipher.doFinal(message.toByteArray(Charsets.UTF_8))
            
            return EncryptedMessage(
                data = Base64.getEncoder().encodeToString(encryptedData),
                iv = Base64.getEncoder().encodeToString(iv)
            )
        } catch (e: Exception) {
            logger.error(e) { "❌ Failed to encrypt message" }
            throw CryptoException("Failed to encrypt message", e)
        }
    }
    
    /**
     * Decrypt message with AES-256-GCM
     */
    fun decryptMessage(encryptedMessage: EncryptedMessage, clientId: String): String {
        val sessionKey = sessionKeys[clientId]
            ?: throw CryptoException("Session key not found for client: $clientId")
        
        return decryptMessage(encryptedMessage, sessionKey)
    }
    
    /**
     * Decrypt message with specific session key
     */
    fun decryptMessage(encryptedMessage: EncryptedMessage, sessionKey: SecretKey): String {
        try {
            val cipher = Cipher.getInstance(AES_TRANSFORMATION)
            val iv = Base64.getDecoder().decode(encryptedMessage.iv)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH * 8, iv)
            
            cipher.init(Cipher.DECRYPT_MODE, sessionKey, spec)
            
            val encryptedData = Base64.getDecoder().decode(encryptedMessage.data)
            val decryptedData = cipher.doFinal(encryptedData)
            
            return String(decryptedData, Charsets.UTF_8)
        } catch (e: Exception) {
            logger.error(e) { "❌ Failed to decrypt message" }
            throw CryptoException("Failed to decrypt message", e)
        }
    }
    
    /**
     * Sign message with server's RSA private key
     */
    fun signMessage(message: String): String {
        try {
            val signature = Signature.getInstance("SHA256withRSA")
            signature.initSign(serverKeyPair.private)
            signature.update(message.toByteArray(Charsets.UTF_8))
            
            val signatureBytes = signature.sign()
            return Base64.getEncoder().encodeToString(signatureBytes)
        } catch (e: Exception) {
            logger.error(e) { "❌ Failed to sign message" }
            throw CryptoException("Failed to sign message", e)
        }
    }
    
    /**
     * Verify message signature with client's public key
     */
    fun verifySignature(message: String, signatureString: String, clientId: String): Boolean {
        val clientPublicKey = clientPublicKeys[clientId] ?: return false
        
        return try {
            val signature = Signature.getInstance("SHA256withRSA")
            signature.initVerify(clientPublicKey)
            signature.update(message.toByteArray(Charsets.UTF_8))
            
            val signatureBytes = Base64.getDecoder().decode(signatureString)
            signature.verify(signatureBytes)
        } catch (e: Exception) {
            logger.error(e) { "❌ Failed to verify signature for client: $clientId" }
            false
        }
    }
    
    /**
     * Get session key for client
     */
    fun getSessionKey(clientId: String): SecretKey? {
        return sessionKeys[clientId]
    }
    
    /**
     * Remove client's cryptographic data
     */
    fun removeClientData(clientId: String) {
        sessionKeys.remove(clientId)
        clientPublicKeys.remove(clientId)
        logger.debug { "🗑️ Removed crypto data for client: $clientId" }
    }
    
    /**
     * Generate secure random bytes
     */
    fun generateRandomBytes(length: Int): ByteArray {
        val bytes = ByteArray(length)
        SecureRandom().nextBytes(bytes)
        return bytes
    }
    
    /**
     * Get active session count
     */
    fun getActiveSessionCount(): Int = sessionKeys.size
    
    /**
     * Clear all session data (for maintenance)
     */
    fun clearAllSessions() {
        sessionKeys.clear()
        clientPublicKeys.clear()
        logger.info { "🧹 Cleared all session data" }
    }
}

/**
 * Encrypted message data class
 */
data class EncryptedMessage(
    val data: String,
    val iv: String
)

/**
 * Cryptography exception
 */
class CryptoException(message: String, cause: Throwable? = null) : Exception(message, cause)