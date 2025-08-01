package com.meshchat.android.ui.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ConnectionUiState(
    val username: String = "",
    val serverAddress: String = "192.168.1.100",
    val port: String = "8080",
    val isServerMode: Boolean = true,
    val isConnecting: Boolean = false,
    val isConnected: Boolean = false,
    val errorMessage: String? = null,
    val usernameError: String? = null,
    val serverAddressError: String? = null,
    val portError: String? = null
)

class ConnectionViewModel : ViewModel() {
    
    private val _uiState = MutableStateFlow(ConnectionUiState())
    val uiState: StateFlow<ConnectionUiState> = _uiState.asStateFlow()
    
    fun updateUsername(username: String) {
        _uiState.value = _uiState.value.copy(
            username = username,
            usernameError = null
        )
    }
    
    fun updateServerAddress(address: String) {
        _uiState.value = _uiState.value.copy(
            serverAddress = address,
            serverAddressError = null
        )
    }
    
    fun updatePort(port: String) {
        _uiState.value = _uiState.value.copy(
            port = port,
            portError = null
        )
    }
    
    fun setServerMode(isServer: Boolean) {
        _uiState.value = _uiState.value.copy(
            isServerMode = isServer,
            errorMessage = null
        )
    }
    
    fun setConnecting(connecting: Boolean) {
        _uiState.value = _uiState.value.copy(
            isConnecting = connecting,
            errorMessage = if (connecting) null else _uiState.value.errorMessage
        )
    }
    
    fun setConnected(connected: Boolean) {
        _uiState.value = _uiState.value.copy(
            isConnected = connected,
            isConnecting = false,
            errorMessage = null
        )
    }
    
    fun setError(error: String) {
        _uiState.value = _uiState.value.copy(
            errorMessage = error,
            isConnecting = false
        )
    }
    
    fun isFormValid(): Boolean {
        val state = _uiState.value
        
        // Validate username
        if (state.username.isBlank()) {
            _uiState.value = state.copy(usernameError = "Username cannot be empty")
            return false
        }
        
        if (state.username.length < 2) {
            _uiState.value = state.copy(usernameError = "Username must be at least 2 characters")
            return false
        }
        
        // Validate port
        val portNum = state.port.toIntOrNull()
        if (portNum == null || portNum < 1024 || portNum > 65535) {
            _uiState.value = state.copy(portError = "Port must be between 1024 and 65535")
            return false
        }
        
        // Validate server address (only for client mode)
        if (!state.isServerMode) {
            if (state.serverAddress.isBlank()) {
                _uiState.value = state.copy(serverAddressError = "Server address cannot be empty")
                return false
            }
            
            // Basic IP address validation
            if (!isValidIPAddress(state.serverAddress) && !isValidHostname(state.serverAddress)) {
                _uiState.value = state.copy(serverAddressError = "Invalid server address")
                return false
            }
        }
        
        return true
    }
    
    private fun isValidIPAddress(ip: String): Boolean {
        val parts = ip.split(".")
        if (parts.size != 4) return false
        
        return parts.all { part ->
            val num = part.toIntOrNull()
            num != null && num in 0..255
        }
    }
    
    private fun isValidHostname(hostname: String): Boolean {
        if (hostname.length > 253) return false
        if (hostname.isEmpty()) return false
        
        val regex = "^[a-zA-Z0-9]([a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9])?(\\.[a-zA-Z0-9]([a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9])?)*$".toRegex()
        return regex.matches(hostname)
    }
}