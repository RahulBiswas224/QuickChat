package server;

import java.io.*;
import java.net.*;

/**
 * ClientHandler — Week 1
 *
 * Each connected client gets one of these running on its own thread.
 * Responsibilities:
 *   1. Ask the client for a username
 *   2. Read incoming messages in a loop
 *   3. Route messages (broadcast or private via /msg)
 *   4. Handle disconnect cleanly
 */
public class ClientHandler implements Runnable {

    private final Socket socket;
    private final ChatServer server;

    private BufferedReader reader;
    private PrintWriter writer;
    private String username;

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
                    writer.println("SYSTEM:Commands: /users  /msg <user> <text>  /quit");
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
                    // Format: /msg <username> <message>
                    handlePrivateMessage(line);

                } else {
                    // Regular broadcast
                    server.broadcast(line, username);
                }
            }

        } catch (IOException e) {
            System.out.println("[SERVER] Connection lost: " + (username != null ? username : "unknown"));
        } finally {
            cleanup();
        }
    }

    private void handlePrivateMessage(String line) {
        // /msg username rest of message
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
            if (username != null) server.removeClient(username);
            if (reader != null) reader.close();
            if (writer != null) writer.close();
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException e) {
            System.err.println("[SERVER] Cleanup error: " + e.getMessage());
        }
    }
}