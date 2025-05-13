## Network-Mesh-Messenger

NetworkMesh Messenger is a terminal-based chat application written in Java.
It’s a great project for learning the functionality of client-server communication, socket programming, and multithreading.

### This Program Demonstrates
- How a server accepts multiple client connections
- How to use threads to handle real-time communication
- How clients can send messages to each other through the server

### 📁 Project Structure
Located in: src/com/networkmesh/messenger/
- NodeHost.java – Server logic
- NodeHandler.java – Handles each client on the server
- ClientNode.java – Client interface – Server logic

### How to Use

1. Start the ServerRun the NodeHost class. This will launch the server and begin listening for client connections.

2. Connect ClientsRun the ClientNode class for each participant who wants to join the chat.
- Enter a username to receive a session ID.
- Receive a welcome message and basic usage instructions.
- Start chatting with other connected clients in real time.

3. Exiting the ChatClients can type /exit to leave the chat. A confirmation prompt will appear before the client is disconnected.
