package com.networkmesh.messenger;

import java.io.*;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * NodeHandler manages communication with a single connected client.
 * It runs on its own thread, reads messages from the client, and broadcasts them to all other clients.
 */
public class NodeHandler implements Runnable {

    
    public static List<NodeHandler> activeNodes = new CopyOnWriteArrayList<>(); // List of active clients in session. 

    private Socket socket;                 // Socket for communicating with this client.
    private BufferedReader input;          // Read messages from the client.
    private BufferedWriter output;         // Send messages to the client.
    private String username;               // Client's username. 
    private int sessionID;                 // Unique session ID assigned by the server.

    /**
     * Creates a new NodeHandler for a client and adds it to the list of active nodes.
     * @param socket the client's socket used for communication.
     * @param host reference to the server, used to assign a session ID.
     */
    public NodeHandler(Socket socket, NodeHost host) {
        try {
            this.socket = socket;
            this.output = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
            this.input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            this.username = input.readLine();                  // First line is expected to be the username.
            this.sessionID = host.generateSessionID();         // Generate a unique session ID.
            activeNodes.add(this);                             // Add this handler to the active list.

            broadcast("[System] " + username + " just joined the chat. ID: " + sessionID, true);

            output.write("Welcome to the chat " + username + "! Your session ID is: " + sessionID);
            output.newLine();
            output.flush();
        } catch (IOException e) {
            terminateConnection();
        }
    }

    /**
     * The run method listens for messages from the client and broadcasts them on its own thread. 
     */
    @Override
    public void run() {
        String message;
        try {
            while ((message = input.readLine()) != null) {
                if (message.trim().equalsIgnoreCase("/exit")) {
                    output.write("Goodbye!");
                    output.newLine();
                    output.flush();
                    break;
                }
                broadcast("[" + username + "] " + message, true);
            }
        } catch (IOException e) {
        } finally {
            terminateConnection(); // In error: close the connection with the client. 
        }
    }

    /**
     * Sends a message to all connected clients.
     * @param msg the message to send.
     * @param skipSelf if true, the message is not sent back to the sender.
     */
    private void broadcast(String msg, boolean skipSelf) {
        for (NodeHandler node : activeNodes) {
            if (skipSelf && node == this) continue;
            try {
                node.output.write(msg);
                node.output.newLine();
                node.output.flush();
            } catch (IOException e) {
                node.terminateConnection(); // In error: close connetion with the client. 
            }
        }
    }

    /**
     * Closes the connection with the client and removes this handler from the active list.
     */
    private void terminateConnection() {
        activeNodes.remove(this);
        broadcast("[System] " + username + " left the chat.", false);
        try {
            if (input != null) input.close();
            if (output != null) output.close();
            if (socket != null) socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
