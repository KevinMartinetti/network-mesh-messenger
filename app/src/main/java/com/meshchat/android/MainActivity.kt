package com.meshchat.android

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.meshchat.android.network.MeshNetworkService
import com.meshchat.android.ui.screens.ChatScreen
import com.meshchat.android.ui.screens.ConnectionScreen
import com.meshchat.android.ui.theme.MeshChatTheme
import com.meshchat.android.ui.viewmodel.ChatViewModel
import com.meshchat.android.ui.viewmodel.ConnectionViewModel

class MainActivity : ComponentActivity() {
    
    private var meshNetworkService: MeshNetworkService? = null
    private var isBound = false
    
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as MeshNetworkService.MeshNetworkBinder
            meshNetworkService = binder.getService()
            isBound = true
        }
        
        override fun onServiceDisconnected(arg0: ComponentName) {
            isBound = false
        }
    }
    
    // Permission request launcher
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Handle permission results
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        
        setContent {
            MeshChatTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MeshChatApp(
                        meshNetworkService = meshNetworkService,
                        onRequestPermissions = { permissions ->
                            permissionLauncher.launch(permissions)
                        }
                    )
                }
            }
        }
    }
    
    override fun onStart() {
        super.onStart()
        // Bind to service
        Intent(this, MeshNetworkService::class.java).also { intent ->
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }
    
    override fun onStop() {
        super.onStop()
        // Unbind from service
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
    }
}

@Composable
fun MeshChatApp(
    meshNetworkService: MeshNetworkService?,
    onRequestPermissions: (Array<String>) -> Unit
) {
    val navController = rememberNavController()
    
    NavHost(
        navController = navController,
        startDestination = "connection"
    ) {
        composable("connection") {
            val viewModel: ConnectionViewModel = viewModel()
            ConnectionScreen(
                viewModel = viewModel,
                onNavigateToChat = {
                    navController.navigate("chat") {
                        popUpTo("connection") { inclusive = true }
                    }
                },
                onRequestPermissions = onRequestPermissions,
                meshNetworkService = meshNetworkService
            )
        }
        
        composable("chat") {
            val viewModel: ChatViewModel = viewModel()
            ChatScreen(
                viewModel = viewModel,
                onNavigateBack = {
                    navController.navigate("connection") {
                        popUpTo("chat") { inclusive = true }
                    }
                },
                meshNetworkService = meshNetworkService
            )
        }
    }
}