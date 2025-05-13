package com.networkmesh.messenger;

import java.io.*;
import java.net.Socket;
import java.util.Scanner;

/**
 * ClientNode represents the client that connects to the server.
 * The client can send messages and listen for messages from the server.
 */
public class ClientNode {

    private Socket socket;              // Socket for connecting to the server.
    private BufferedReader input;       // Read messages from the server.
    private BufferedWriter output;      // Send messages to the server.
    private String username;            // Client's username.

    /**
    * Constructor that sets up the socket and I/O streams for communication.
    * @param socket the connection to the server.
    * @param username client's username.
    */
    public ClientNode(Socket socket, String username) {
        try {
            this.socket = socket;
            this.output = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
            this.input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            this.username = username;
        } catch (IOException e) {
            closeAll();
        }
    }

   
    /**
    * Sends the username and messages to the server.
    * Listens for input from the user until they confirm exiting with '/exit'.
    */
    public void send() {
        try {
            output.write(username);
            output.newLine();
            output.flush();

            Scanner scanner = new Scanner(System.in);

            while (socket.isConnected()) {
                String msg = scanner.nextLine();
                if (msg.trim().equalsIgnoreCase("/exit")) {
                    System.out.print("It seems that you are trying to leave this chat. ");
                    System.out.print("Please type 'yes' to confirm or 'no' to stay: ");
                    try {
                        String confirmation = scanner.nextLine().trim().toLowerCase();
                        if (confirmation.equals("yes")) {
                            output.write("/exit");
                            output.newLine();
                            output.flush();
                            break;
                        } else if (confirmation.equals("no")) {
                            System.out.println("Exit cancelled. You are still connected.");
                            continue;
                        } else {
                            System.out.println("Invalid input. Staying in chat by default.");
                            continue;
                        }
                    } catch (Exception e) {
                        System.out.println("Something went wrong. Staying in chat by default.");
                        continue;
                    }
                }

                if (!msg.trim().isEmpty()) {
                    output.write(msg);
                    output.newLine();
                    output.flush();
                }
            }

            closeAll();
        } catch (IOException e) {
            closeAll();
        }
    }


    /**
     * Starts a thread to continuously listen for incoming messages from the server.
     * Prints them to the console as they arrive.
     */
    public void receive() {
        new Thread(() -> {
            String incoming;
            boolean firstMessage = true; // Track if it's the welcome message
            try {
                while ((incoming = input.readLine()) != null) {
                    if (firstMessage) {
                        System.out.println(incoming);
                        System.out.println("Type your message and press Enter.");
                        System.out.println("To exit the chat, type '/exit'.");
                        firstMessage = false;
                    } else {
                        System.out.println(incoming);
                    }
                }
            } catch (IOException e) {
                closeAll();
            }
        }).start();
    }
    

    /**
     * Closes the socket and I/O streams to end the connection.
     */
    private void closeAll() {
        try {
            if (input != null) input.close();
            if (output != null) output.close();
            if (socket != null) socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Main method to start the client.
     * Asks the user for a username, connects to the server, and starts communication.
     */
    public static void main(String[] args) throws IOException {
        Scanner scanner = new Scanner(System.in);
        System.out.print("\nEnter your username: ");
        String name = scanner.nextLine();
        Socket socket = new Socket("localhost", 8080);
        ClientNode client = new ClientNode(socket, name);
        client.receive();
        client.send();
    }
}
