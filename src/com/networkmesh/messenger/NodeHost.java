package com.networkmesh.messenger;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * NodeHost is the main server class. It listens for incoming client connections
 * and creates a new thread for each client using the NodeHandler class.
 */
public class NodeHost {

    
    private ServerSocket serverSocket;              // ServerSocket used to accept client connections
    private static int sessionCounter = 1000;       // Counter used to assign unique session IDs to each client

    /**
     * Constructor that sets the server socket to use for accepting connections. 
     */
    public NodeHost(ServerSocket serverSocket) {
        this.serverSocket = serverSocket;
    }

    /**
     * Generates a unique session ID for each connected client.
     */
    public int generateSessionID() {
        return sessionCounter++;
    }

    /**
     * Starts the server, accepts client connections, and handles each one in a new thread.
     */
    public void launch() {
        System.out.println("[Server] Listening for incoming client nodes...");
        try {
            while (!serverSocket.isClosed()) {
                Socket socket = serverSocket.accept(); // Accept new client connection
                System.out.println("[Server] Node connected: " + socket.getInetAddress());

                // Create and start a handler thread for this client
                NodeHandler handler = new NodeHandler(socket, this);
                new Thread(handler).start();
            }
        } catch (IOException e) {
            System.out.println("[Server] IOException: " + e.getMessage());
        }
    }

    /**
     * Closes the server socket to shut down the server.
     */
    public void ServerShutdown() {
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Main method that creates a NodeHost server on port 8080 and starts it.
     */
    public static void main(String[] args) throws IOException {
        ServerSocket socket = new ServerSocket(8080);
        NodeHost server = new NodeHost(socket);
        server.launch();
    }
}
