package com.meshchat.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meshchat.android.data.Message
import com.meshchat.android.data.User
import com.meshchat.android.network.MeshNetworkService
import com.meshchat.android.ui.theme.*
import com.meshchat.android.ui.viewmodel.ChatViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onNavigateBack: () -> Unit,
    meshNetworkService: MeshNetworkService?
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    
    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            scope.launch {
                listState.animateScrollToItem(uiState.messages.size - 1)
            }
        }
    }
    
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Top app bar
        TopAppBar(
            title = {
                Column {
                    Text(
                        text = "MeshChat",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = uiState.networkStatus,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (uiState.isConnected) StatusOnline else StatusOffline
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
            },
            actions = {
                // Connection status indicator
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(if (uiState.isConnected) StatusOnline else StatusOffline)
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                // Users list button
                IconButton(onClick = { viewModel.toggleUsersList() }) {
                    Badge(
                        content = { Text("${uiState.connectedUsers.size}") }
                    ) {
                        Icon(Icons.Default.People, contentDescription = "Users")
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        )
        
        // Main content
        Box(modifier = Modifier.weight(1f)) {
            // Messages list
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.messages) { message ->
                    MessageItem(
                        message = message,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            
            // Users list overlay
            if (uiState.showUsersList) {
                Card(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .widthIn(max = 280.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Connected Users (${uiState.connectedUsers.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        
                        uiState.connectedUsers.forEach { user ->
                            UserItem(
                                user = user,
                                isCurrentUser = user.id == uiState.currentUser?.id,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                        
                        TextButton(
                            onClick = { viewModel.toggleUsersList() },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("Close")
                        }
                    }
                }
            }
        }
        
        // Message input
        MessageInput(
            currentMessage = uiState.currentMessage,
            onMessageChange = viewModel::updateCurrentMessage,
            onSendMessage = viewModel::sendMessage,
            enabled = uiState.isConnected,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun MessageItem(
    message: Message,
    modifier: Modifier = Modifier
) {
    val isFromCurrentUser = message.isFromCurrentUser
    val isSystem = message.type == Message.Type.SYSTEM
    
    Row(
        modifier = modifier,
        horizontalArrangement = if (isFromCurrentUser && !isSystem) {
            Arrangement.End
        } else if (isSystem) {
            Arrangement.Center
        } else {
            Arrangement.Start
        }
    ) {
        if (isSystem) {
            // System message
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                ),
                modifier = Modifier.padding(horizontal = 32.dp)
            ) {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(8.dp)
                )
            }
        } else {
            // Regular message
            Column(
                horizontalAlignment = if (isFromCurrentUser) Alignment.End else Alignment.Start,
                modifier = Modifier.widthIn(max = 280.dp)
            ) {
                if (!isFromCurrentUser) {
                    Text(
                        text = message.senderName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.padding(start = 12.dp, bottom = 2.dp)
                    )
                }
                
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (isFromCurrentUser) {
                            MessageSent
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    ),
                    shape = RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isFromCurrentUser) 16.dp else 4.dp,
                        bottomEnd = if (isFromCurrentUser) 4.dp else 16.dp
                    )
                ) {
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isFromCurrentUser) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp)
                    )
                }
                
                Text(
                    text = message.getFormattedTime(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.padding(
                        start = if (isFromCurrentUser) 0.dp else 12.dp,
                        end = if (isFromCurrentUser) 12.dp else 0.dp,
                        top = 2.dp
                    )
                )
            }
        }
    }
}

@Composable
fun UserItem(
    user: User,
    isCurrentUser: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // User avatar
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(
                    if (isCurrentUser) MeshPrimary else MaterialTheme.colorScheme.primary
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = user.getInitials(),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = user.username,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isCurrentUser) FontWeight.Bold else FontWeight.Normal
                )
                
                if (isCurrentUser) {
                    Text(
                        text = " (You)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                
                if (user.isHost) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        Icons.Default.Star,
                        contentDescription = "Host",
                        modifier = Modifier.size(16.dp),
                        tint = MeshAccent
                    )
                }
            }
            
            Text(
                text = user.getStatus(),
                style = MaterialTheme.typography.bodySmall,
                color = if (user.isOnline) StatusOnline else StatusOffline
            )
        }
        
        // Status indicator
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (user.isOnline) StatusOnline else StatusOffline)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageInput(
    currentMessage: String,
    onMessageChange: (String) -> Unit,
    onSendMessage: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.Bottom
        ) {
            OutlinedTextField(
                value = currentMessage,
                onValueChange = onMessageChange,
                placeholder = { Text("Type a message...") },
                modifier = Modifier.weight(1f),
                enabled = enabled,
                maxLines = 4,
                shape = RoundedCornerShape(24.dp)
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            FloatingActionButton(
                onClick = onSendMessage,
                modifier = Modifier.size(48.dp),
                containerColor = if (currentMessage.isNotBlank() && enabled) {
                    MeshPrimary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            ) {
                Icon(
                    Icons.Default.Send,
                    contentDescription = "Send",
                    tint = if (currentMessage.isNotBlank() && enabled) {
                        Color.White
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}