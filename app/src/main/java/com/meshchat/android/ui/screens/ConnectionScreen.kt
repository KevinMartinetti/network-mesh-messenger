package com.meshchat.android.ui.screens

import android.Manifest
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.meshchat.android.R
import com.meshchat.android.network.MeshNetworkService
import com.meshchat.android.ui.theme.MeshPrimary
import com.meshchat.android.ui.theme.StatusConnecting
import com.meshchat.android.ui.viewmodel.ConnectionViewModel

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ConnectionScreen(
    viewModel: ConnectionViewModel,
    onNavigateToChat: () -> Unit,
    onRequestPermissions: (Array<String>) -> Unit,
    meshNetworkService: MeshNetworkService?
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    
    // Required permissions
    val permissionsState = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.ACCESS_WIFI_STATE
        )
    )
    
    LaunchedEffect(uiState.isConnected) {
        if (uiState.isConnected) {
            onNavigateToChat()
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // App title
        Text(
            text = "MeshChat",
            style = MaterialTheme.typography.headlineLarge,
            color = MeshPrimary,
            fontWeight = FontWeight.Bold
        )
        
        Text(
            text = "Secure Mesh Networking",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.padding(bottom = 32.dp)
        )
        
        // Connection form
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Username field
                OutlinedTextField(
                    value = uiState.username,
                    onValueChange = viewModel::updateUsername,
                    label = { Text(stringResource(R.string.enter_username)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = uiState.usernameError != null
                )
                
                if (uiState.usernameError != null) {
                    Text(
                        text = uiState.usernameError,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                
                // Connection mode tabs
                TabRow(
                    selectedTabIndex = if (uiState.isServerMode) 0 else 1,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Tab(
                        selected = uiState.isServerMode,
                        onClick = { viewModel.setServerMode(true) },
                        text = { Text(stringResource(R.string.create_network)) }
                    )
                    Tab(
                        selected = !uiState.isServerMode,
                        onClick = { viewModel.setServerMode(false) },
                        text = { Text(stringResource(R.string.connect_to_network)) }
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                if (uiState.isServerMode) {
                    // Server mode - port selection
                    OutlinedTextField(
                        value = uiState.port,
                        onValueChange = viewModel::updatePort,
                        label = { Text(stringResource(R.string.enter_port)) },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        isError = uiState.portError != null
                    )
                    
                    if (uiState.portError != null) {
                        Text(
                            text = uiState.portError,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                } else {
                    // Client mode - server address and port
                    OutlinedTextField(
                        value = uiState.serverAddress,
                        onValueChange = viewModel::updateServerAddress,
                        label = { Text(stringResource(R.string.enter_server_address)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        isError = uiState.serverAddressError != null
                    )
                    
                    if (uiState.serverAddressError != null) {
                        Text(
                            text = uiState.serverAddressError,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    
                    OutlinedTextField(
                        value = uiState.port,
                        onValueChange = viewModel::updatePort,
                        label = { Text(stringResource(R.string.enter_port)) },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        isError = uiState.portError != null
                    )
                    
                    if (uiState.portError != null) {
                        Text(
                            text = uiState.portError,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                
                // Connection status
                if (uiState.isConnecting) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = StatusConnecting
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.connecting),
                            color = StatusConnecting,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                
                if (uiState.errorMessage != null) {
                    Text(
                        text = uiState.errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                
                // Connect button
                Button(
                    onClick = {
                        if (permissionsState.allPermissionsGranted) {
                            if (uiState.isServerMode) {
                                startServer(context, viewModel, meshNetworkService)
                            } else {
                                connectToServer(context, viewModel, meshNetworkService)
                            }
                        } else {
                            onRequestPermissions(
                                arrayOf(
                                    Manifest.permission.INTERNET,
                                    Manifest.permission.ACCESS_NETWORK_STATE,
                                    Manifest.permission.ACCESS_WIFI_STATE
                                )
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isConnecting && viewModel.isFormValid()
                ) {
                    Text(
                        text = if (uiState.isServerMode) {
                            "Create Network"
                        } else {
                            stringResource(R.string.connect)
                        },
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
        }
        
        // Security info
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "🔒 Security Features",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = "• AES-256-GCM encryption\n• RSA-4096 key exchange\n• Digital signatures\n• Perfect forward secrecy",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
            }
        }
    }
}

private fun startServer(
    context: android.content.Context,
    viewModel: ConnectionViewModel,
    meshNetworkService: MeshNetworkService?
) {
    viewModel.setConnecting(true)
    
    val intent = Intent(context, MeshNetworkService::class.java).apply {
        action = MeshNetworkService.ACTION_START_SERVER
        putExtra(MeshNetworkService.EXTRA_USERNAME, viewModel.uiState.value.username)
        putExtra(MeshNetworkService.EXTRA_PORT, viewModel.uiState.value.port.toIntOrNull() ?: 8080)
    }
    
    context.startForegroundService(intent)
    
    // Simulate connection delay
    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
        kotlinx.coroutines.delay(2000)
        viewModel.setConnected(true)
    }
}

private fun connectToServer(
    context: android.content.Context,
    viewModel: ConnectionViewModel,
    meshNetworkService: MeshNetworkService?
) {
    viewModel.setConnecting(true)
    
    val intent = Intent(context, MeshNetworkService::class.java).apply {
        action = MeshNetworkService.ACTION_CONNECT_TO_SERVER
        putExtra(MeshNetworkService.EXTRA_USERNAME, viewModel.uiState.value.username)
        putExtra(MeshNetworkService.EXTRA_HOST, viewModel.uiState.value.serverAddress)
        putExtra(MeshNetworkService.EXTRA_PORT, viewModel.uiState.value.port.toIntOrNull() ?: 8080)
    }
    
    context.startForegroundService(intent)
    
    // Simulate connection delay
    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
        kotlinx.coroutines.delay(3000)
        viewModel.setConnected(true)
    }
}