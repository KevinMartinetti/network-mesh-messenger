package com.meshchat.android.network

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.meshchat.android.MainActivity
import com.meshchat.android.R
import com.meshchat.android.crypto.CryptoManager
import kotlinx.coroutines.*

/**
 * Foreground service for maintaining mesh network connections
 */
class MeshNetworkService : Service() {
    
    companion object {
        private const val TAG = "MeshNetworkService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "mesh_network_channel"
        
        const val ACTION_START_SERVER = "START_SERVER"
        const val ACTION_CONNECT_TO_SERVER = "CONNECT_TO_SERVER"
        const val ACTION_STOP_NETWORK = "STOP_NETWORK"
        
        const val EXTRA_USERNAME = "username"
        const val EXTRA_HOST = "host"
        const val EXTRA_PORT = "port"
    }
    
    private val binder = MeshNetworkBinder()
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    private lateinit var cryptoManager: CryptoManager
    private lateinit var meshNetworkManager: MeshNetworkManager
    
    private var isInitialized = false
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        
        createNotificationChannel()
        
        // Initialize crypto and network managers
        cryptoManager = CryptoManager(this)
        meshNetworkManager = MeshNetworkManager(cryptoManager)
        
        isInitialized = true
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service started with action: ${intent?.action}")
        
        if (!isInitialized) {
            Log.e(TAG, "Service not initialized")
            return START_NOT_STICKY
        }
        
        when (intent?.action) {
            ACTION_START_SERVER -> {
                val username = intent.getStringExtra(EXTRA_USERNAME) ?: "Unknown"
                val port = intent.getIntExtra(EXTRA_PORT, 8080)
                startServer(username, port)
            }
            ACTION_CONNECT_TO_SERVER -> {
                val username = intent.getStringExtra(EXTRA_USERNAME) ?: "Unknown"
                val host = intent.getStringExtra(EXTRA_HOST) ?: "localhost"
                val port = intent.getIntExtra(EXTRA_PORT, 8080)
                connectToServer(username, host, port)
            }
            ACTION_STOP_NETWORK -> {
                stopNetwork()
            }
        }
        
        startForeground(NOTIFICATION_ID, createNotification())
        
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder = binder
    
    override fun onDestroy() {
        Log.d(TAG, "Service destroyed")
        
        serviceScope.launch {
            meshNetworkManager.stop()
        }
        
        serviceScope.cancel()
        super.onDestroy()
    }
    
    /**
     * Start mesh network as server
     */
    private fun startServer(username: String, port: Int) {
        serviceScope.launch {
            try {
                val result = meshNetworkManager.startAsServer(port, username)
                if (result.isSuccess) {
                    Log.d(TAG, "Server started successfully on port $port")
                    updateNotification("Server running on port $port")
                } else {
                    Log.e(TAG, "Failed to start server: ${result.exceptionOrNull()}")
                    stopSelf()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error starting server", e)
                stopSelf()
            }
        }
    }
    
    /**
     * Connect to existing mesh network
     */
    private fun connectToServer(username: String, host: String, port: Int) {
        serviceScope.launch {
            try {
                val result = meshNetworkManager.connectToServer(host, port, username)
                if (result.isSuccess) {
                    Log.d(TAG, "Connected to server at $host:$port")
                    updateNotification("Connected to $host:$port")
                } else {
                    Log.e(TAG, "Failed to connect to server: ${result.exceptionOrNull()}")
                    stopSelf()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error connecting to server", e)
                stopSelf()
            }
        }
    }
    
    /**
     * Stop mesh network
     */
    private fun stopNetwork() {
        serviceScope.launch {
            try {
                meshNetworkManager.stop()
                Log.d(TAG, "Network stopped")
                stopSelf()
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping network", e)
                stopSelf()
            }
        }
    }
    
    /**
     * Create notification channel for Android 8.0+
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_description)
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    /**
     * Create notification for foreground service
     */
    private fun createNotification(text: String = getString(R.string.notification_text)): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }
    
    /**
     * Update notification text
     */
    private fun updateNotification(text: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, createNotification(text))
    }
    
    /**
     * Binder for service communication
     */
    inner class MeshNetworkBinder : Binder() {
        fun getService(): MeshNetworkService = this@MeshNetworkService
        fun getMeshNetworkManager(): MeshNetworkManager = meshNetworkManager
    }
}