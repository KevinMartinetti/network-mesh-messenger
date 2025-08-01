package com.meshchat.android.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meshchat.android.data.Message
import com.meshchat.android.data.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ChatUiState(
    val messages: List<Message> = emptyList(),
    val connectedUsers: List<User> = emptyList(),
    val currentMessage: String = "",
    val isConnected: Boolean = false,
    val currentUser: User? = null,
    val isTyping: Boolean = false,
    val showUsersList: Boolean = false,
    val networkStatus: String = "Disconnected"
)

class ChatViewModel : ViewModel() {
    
    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()
    
    fun updateCurrentMessage(message: String) {
        _uiState.value = _uiState.value.copy(currentMessage = message)
    }
    
    fun sendMessage() {
        val currentState = _uiState.value
        val message = currentState.currentMessage.trim()
        
        if (message.isNotEmpty() && currentState.currentUser != null) {
            val newMessage = Message(
                id = generateMessageId(),
                content = message,
                senderId = currentState.currentUser.id,
                senderName = currentState.currentUser.username,
                timestamp = System.currentTimeMillis(),
                type = Message.Type.TEXT,
                isFromCurrentUser = true
            )
            
            // Add message to local list
            val updatedMessages = currentState.messages + newMessage
            _uiState.value = currentState.copy(
                messages = updatedMessages,
                currentMessage = ""
            )
            
            // TODO: Send message through network
            viewModelScope.launch {
                // meshNetworkManager.sendMessage(message)
            }
        }
    }
    
    fun receiveMessage(message: Message) {
        val currentState = _uiState.value
        val messageWithUserFlag = message.copy(
            isFromCurrentUser = message.senderId == currentState.currentUser?.id
        )
        
        val updatedMessages = currentState.messages + messageWithUserFlag
        _uiState.value = currentState.copy(messages = updatedMessages)
    }
    
    fun updateConnectedUsers(users: List<User>) {
        _uiState.value = _uiState.value.copy(connectedUsers = users)
    }
    
    fun setCurrentUser(user: User) {
        _uiState.value = _uiState.value.copy(currentUser = user)
    }
    
    fun setConnectionStatus(isConnected: Boolean, status: String = "") {
        _uiState.value = _uiState.value.copy(
            isConnected = isConnected,
            networkStatus = status.ifEmpty { if (isConnected) "Connected" else "Disconnected" }
        )
    }
    
    fun toggleUsersList() {
        _uiState.value = _uiState.value.copy(
            showUsersList = !_uiState.value.showUsersList
        )
    }
    
    fun setTyping(isTyping: Boolean) {
        _uiState.value = _uiState.value.copy(isTyping = isTyping)
    }
    
    fun addSystemMessage(content: String) {
        val systemMessage = Message(
            id = generateMessageId(),
            content = content,
            senderId = "system",
            senderName = "System",
            timestamp = System.currentTimeMillis(),
            type = Message.Type.SYSTEM,
            isFromCurrentUser = false
        )
        
        val updatedMessages = _uiState.value.messages + systemMessage
        _uiState.value = _uiState.value.copy(messages = updatedMessages)
    }
    
    fun clearMessages() {
        _uiState.value = _uiState.value.copy(messages = emptyList())
    }
    
    private fun generateMessageId(): String {
        return "msg_${System.currentTimeMillis()}_${(1000..9999).random()}"
    }
    
    // Initialize with sample data for demo
    init {
        // Add some sample messages for demonstration
        val sampleUser = User(
            id = "user_demo",
            username = "DemoUser",
            publicKey = "demo_key",
            isHost = true
        )
        
        setCurrentUser(sampleUser)
        setConnectionStatus(true, "Connected to mesh network")
        
        // Add welcome message
        addSystemMessage("Welcome to MeshChat! Your connection is encrypted with AES-256.")
        
        // Add sample users
        updateConnectedUsers(
            listOf(
                sampleUser,
                User(
                    id = "user_2",
                    username = "Alice",
                    publicKey = "alice_key",
                    isHost = false
                ),
                User(
                    id = "user_3",
                    username = "Bob",
                    publicKey = "bob_key",
                    isHost = false
                )
            )
        )
    }
}