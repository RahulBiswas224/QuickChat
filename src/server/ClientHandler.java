package server;

import java.io.*;
import java.net.*;

/**
 * ClientHandler — Week 4 update (Rooms / Channels integration)
 *
 * Each connected client gets one of these running on its own thread.
 * Tracks user state regarding room boundaries and intercepts channel commands.
 */
public class ClientHandler implements Runnable {

    private final Socket socket;
    private final ChatServer server;

    private BufferedReader reader;
    private PrintWriter writer;
    private String username;
    
    // ── NEW: Room Tracking State ──────────────────────────────────────────────
    private String currentRoom = "#general";

    public ClientHandler(Socket socket, ChatServer server) {
        this.socket = socket;
        this.server = server;
    }

    @Override
    public void run() {
        try {
            // Set up I/O streams
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);

            // ── Step 1: username handshake ──────────────────────────────────────
            writer.println("SYSTEM:Welcome to QuickChat! Enter your username:");

            while (true) {
                String name = reader.readLine();
                if (name == null || name.isBlank()) {
                    writer.println("SYSTEM:Username cannot be empty. Try again:");
                    continue;
                }
                name = name.trim();

                if (server.registerClient(name, this)) {
                    username = name;
                    writer.println("SYSTEM:Username accepted. You are now connected as " + username);
                    writer.println("SYSTEM:Commands: /join <room>  /users  /msg <user> <text>  /quit");
                    
                    // Auto-join default room (#general) on connection
                    server.joinRoom(currentRoom, this);
                    server.broadcastToRoom(currentRoom, username + " joined the chat!", "SYSTEM");
                    break;
                } else {
                    writer.println("SYSTEM:Username '" + name + "' is already taken. Try another:");
                }
            }

            // ── Step 2: main message loop ───────────────────────────────────────
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                if (line.equals("/quit")) {
                    break;

                } else if (line.equals("/users")) {
                    sendMessage("SYSTEM:" + server.getOnlineUsers());

                } else if (line.startsWith("/msg ")) {
                    handlePrivateMessage(line);

                } else if (line.startsWith("/join ")) {
                    // Handle changing rooms/channels
                    handleJoinRoom(line);

                } else {
                    // Regular message: Route to room instead of global broadcast
                    server.broadcastToRoom(currentRoom, line, username);
                }
            }

        } catch (IOException e) {
            System.out.println("[SERVER] Connection lost: " + (username != null ? username : "unknown"));
        } finally {
            cleanup();
        }
    }

    private void handlePrivateMessage(String line) {
        String[] parts = line.split(" ", 3);
        if (parts.length < 3) {
            sendMessage("SYSTEM:Usage: /msg <username> <message>");
            return;
        }
        String target = parts[1];
        String message = parts[2];

        if (target.equals(username)) {
            sendMessage("SYSTEM:You can't message yourself!");
            return;
        }

        boolean sent = server.sendPrivate(target, message, username);
        if (!sent) {
            sendMessage("SYSTEM:User '" + target + "' not found or offline.");
        }
    }

    // ── NEW: Room Switching Handler ──────────────────────────────────────────
    private void handleJoinRoom(String line) {
        String newRoom = line.substring(6).trim();
        if (newRoom.isEmpty()) {
            sendMessage("SYSTEM:Usage: /join <room_name>");
            return;
        }
        
        // Enforce '#' channel notation prefix
        if (!newRoom.startsWith("#")) {
            newRoom = "#" + newRoom;
        }

        if (newRoom.equals(currentRoom)) {
            sendMessage("SYSTEM:You are already in " + currentRoom);
            return;
        }

        // 1. Leave the old room
        server.leaveRoom(currentRoom, this);
        server.broadcastToRoom(currentRoom, username + " left the room.", "SYSTEM");

        // 2. Switch context and join the new room
        String oldRoom = currentRoom;
        currentRoom = newRoom;
        server.joinRoom(currentRoom, this);
        
        // 3. Notify remaining occupants and confirm to sender
        server.broadcastToRoom(currentRoom, username + " joined the room.", "SYSTEM");
        sendMessage("SYSTEM:You successfully switched from " + oldRoom + " to " + currentRoom);
    }

    // ── Thread-safe send to THIS client ────────────────────────────────────────
    public void sendMessage(String message) {
        if (writer != null) {
            writer.println(message);
        }
    }

    // ── Called by admin /kick command ──────────────────────────────────────────
    public void forceDisconnect() {
        cleanup();
    }

    private void cleanup() {
        try {
            if (username != null) {
                server.removeClient(username);
                server.leaveRoom(currentRoom, this); // Ensure client unbinds from room arrays
            }
            if (reader != null) reader.close();
            if (writer != null) writer.close();
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException e) {
            System.err.println("[SERVER] Cleanup error: " + e.getMessage());
        }
    }
}