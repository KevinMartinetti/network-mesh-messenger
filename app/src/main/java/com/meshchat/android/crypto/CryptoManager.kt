package com.meshchat.android.crypto

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.*
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * CryptoManager handles all encryption/decryption operations
 * Uses AES-256-GCM for symmetric encryption and RSA-4096 for key exchange
 */
class CryptoManager(private val context: Context) {
    
    companion object {
        private const val RSA_KEY_SIZE = 4096
        private const val AES_KEY_SIZE = 256
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 16
        
        private const val RSA_TRANSFORMATION = "RSA/ECB/OAEPWITHSHA-256ANDMGF1PADDING"
        private const val AES_TRANSFORMATION = "AES/GCM/NoPadding"
        
        private const val PREFS_NAME = "encryption_keys"
        private const val PRIVATE_KEY_ALIAS = "rsa_private_key"
        private const val PUBLIC_KEY_ALIAS = "rsa_public_key"
        
        init {
            // Add BouncyCastle provider for enhanced cryptography
            if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
                Security.addProvider(BouncyCastleProvider())
            }
        }
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private var keyPair: KeyPair? = null
    
    init {
        initializeKeyPair()
    }
    
    /**
     * Initialize or load existing RSA key pair
     */
    private fun initializeKeyPair() {
        keyPair = loadKeyPair() ?: generateKeyPair()
    }
    
    /**
     * Generate new RSA-4096 key pair
     */
    private fun generateKeyPair(): KeyPair {
        val keyGenerator = KeyPairGenerator.getInstance("RSA", BouncyCastleProvider.PROVIDER_NAME)
        keyGenerator.initialize(RSA_KEY_SIZE, SecureRandom())
        val newKeyPair = keyGenerator.generateKeyPair()
        
        saveKeyPair(newKeyPair)
        return newKeyPair
    }
    
    /**
     * Save key pair to secure storage
     */
    private fun saveKeyPair(keyPair: KeyPair) {
        val privateKeyBytes = keyPair.private.encoded
        val publicKeyBytes = keyPair.public.encoded
        
        prefs.edit()
            .putString(PRIVATE_KEY_ALIAS, Base64.encodeToString(privateKeyBytes, Base64.DEFAULT))
            .putString(PUBLIC_KEY_ALIAS, Base64.encodeToString(publicKeyBytes, Base64.DEFAULT))
            .apply()
    }
    
    /**
     * Load existing key pair from storage
     */
    private fun loadKeyPair(): KeyPair? {
        val privateKeyString = prefs.getString(PRIVATE_KEY_ALIAS, null) ?: return null
        val publicKeyString = prefs.getString(PUBLIC_KEY_ALIAS, null) ?: return null
        
        return try {
            val privateKeyBytes = Base64.decode(privateKeyString, Base64.DEFAULT)
            val publicKeyBytes = Base64.decode(publicKeyString, Base64.DEFAULT)
            
            val keyFactory = KeyFactory.getInstance("RSA")
            val privateKey = keyFactory.generatePrivate(PKCS8EncodedKeySpec(privateKeyBytes))
            val publicKey = keyFactory.generatePublic(X509EncodedKeySpec(publicKeyBytes))
            
            KeyPair(publicKey, privateKey)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Get public key for sharing with other users
     */
    fun getPublicKeyString(): String {
        val publicKeyBytes = keyPair?.public?.encoded ?: throw IllegalStateException("Key pair not initialized")
        return Base64.encodeToString(publicKeyBytes, Base64.DEFAULT)
    }
    
    /**
     * Generate AES-256 session key
     */
    fun generateSessionKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance("AES")
        keyGenerator.init(AES_KEY_SIZE)
        return keyGenerator.generateKey()
    }
    
    /**
     * Encrypt session key with recipient's RSA public key
     */
    fun encryptSessionKey(sessionKey: SecretKey, recipientPublicKey: String): String {
        val publicKeyBytes = Base64.decode(recipientPublicKey, Base64.DEFAULT)
        val keyFactory = KeyFactory.getInstance("RSA")
        val publicKey = keyFactory.generatePublic(X509EncodedKeySpec(publicKeyBytes))
        
        val cipher = Cipher.getInstance(RSA_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, publicKey)
        
        val encryptedKey = cipher.doFinal(sessionKey.encoded)
        return Base64.encodeToString(encryptedKey, Base64.DEFAULT)
    }
    
    /**
     * Decrypt session key with our RSA private key
     */
    fun decryptSessionKey(encryptedSessionKey: String): SecretKey {
        val privateKey = keyPair?.private ?: throw IllegalStateException("Private key not available")
        val encryptedKeyBytes = Base64.decode(encryptedSessionKey, Base64.DEFAULT)
        
        val cipher = Cipher.getInstance(RSA_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, privateKey)
        
        val decryptedKeyBytes = cipher.doFinal(encryptedKeyBytes)
        return SecretKeySpec(decryptedKeyBytes, "AES")
    }
    
    /**
     * Encrypt message with AES-256-GCM
     */
    fun encryptMessage(message: String, sessionKey: SecretKey): EncryptedMessage {
        val cipher = Cipher.getInstance(AES_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, sessionKey)
        
        val iv = cipher.iv
        val encryptedData = cipher.doFinal(message.toByteArray(Charsets.UTF_8))
        
        return EncryptedMessage(
            data = Base64.encodeToString(encryptedData, Base64.DEFAULT),
            iv = Base64.encodeToString(iv, Base64.DEFAULT)
        )
    }
    
    /**
     * Decrypt message with AES-256-GCM
     */
    fun decryptMessage(encryptedMessage: EncryptedMessage, sessionKey: SecretKey): String {
        val cipher = Cipher.getInstance(AES_TRANSFORMATION)
        val iv = Base64.decode(encryptedMessage.iv, Base64.DEFAULT)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH * 8, iv)
        
        cipher.init(Cipher.DECRYPT_MODE, sessionKey, spec)
        
        val encryptedData = Base64.decode(encryptedMessage.data, Base64.DEFAULT)
        val decryptedData = cipher.doFinal(encryptedData)
        
        return String(decryptedData, Charsets.UTF_8)
    }
    
    /**
     * Sign message with RSA private key for authentication
     */
    fun signMessage(message: String): String {
        val privateKey = keyPair?.private ?: throw IllegalStateException("Private key not available")
        val signature = Signature.getInstance("SHA256withRSA")
        signature.initSign(privateKey)
        signature.update(message.toByteArray(Charsets.UTF_8))
        
        val signatureBytes = signature.sign()
        return Base64.encodeToString(signatureBytes, Base64.DEFAULT)
    }
    
    /**
     * Verify message signature with sender's public key
     */
    fun verifySignature(message: String, signatureString: String, senderPublicKey: String): Boolean {
        return try {
            val publicKeyBytes = Base64.decode(senderPublicKey, Base64.DEFAULT)
            val keyFactory = KeyFactory.getInstance("RSA")
            val publicKey = keyFactory.generatePublic(X509EncodedKeySpec(publicKeyBytes))
            
            val signature = Signature.getInstance("SHA256withRSA")
            signature.initVerify(publicKey)
            signature.update(message.toByteArray(Charsets.UTF_8))
            
            val signatureBytes = Base64.decode(signatureString, Base64.DEFAULT)
            signature.verify(signatureBytes)
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Generate secure random bytes for various purposes
     */
    fun generateRandomBytes(length: Int): ByteArray {
        val bytes = ByteArray(length)
        SecureRandom().nextBytes(bytes)
        return bytes
    }
    
    /**
     * Clear all stored keys (for logout/reset)
     */
    fun clearKeys() {
        prefs.edit().clear().apply()
        keyPair = null
    }
}

/**
 * Data class for encrypted message with IV
 */
data class EncryptedMessage(
    val data: String,
    val iv: String
)