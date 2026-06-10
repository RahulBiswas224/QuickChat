package server;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * ChatServer — Week 3 update
 * Added: ChatLogger integration + admin command loop (/kick /list /broadcast)
 */
public class ChatServer {

    private static final int PORT = 5000;

    private final ConcurrentHashMap<String, ClientHandler> clients = new ConcurrentHashMap<>();
    private final ChatLogger logger = new ChatLogger(); // Week 3

    public void start() {
        System.out.println("╔══════════════════════════════╗");
        System.out.println("║   QuickChat Server v2.0      ║");
        System.out.println("║   Listening on port " + PORT + "     ║");
        System.out.println("║   Type /help for admin cmds  ║");
        System.out.println("╚══════════════════════════════╝");

        // Admin command thread — reads from server's own console
        Thread adminThread = new Thread(this::adminCommandLoop);
        adminThread.setDaemon(true);
        adminThread.setName("AdminConsole");
        adminThread.start();

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("[SERVER] New connection from " + clientSocket.getInetAddress());
                ClientHandler handler = new ClientHandler(clientSocket, this);
                Thread thread = new Thread(handler);
                thread.setDaemon(true);
                thread.start();
            }
        } catch (IOException e) {
            System.err.println("[SERVER] Fatal error: " + e.getMessage());
        } finally {
            logger.close();
        }
    }

    // ── Admin commands typed directly into server console ──────────────────────
    private void adminCommandLoop() {
        BufferedReader console = new BufferedReader(new InputStreamReader(System.in));
        System.out.println("[ADMIN] Commands: /list  /kick <user>  /broadcast <msg>  /help");
        try {
            String line;
            while ((line = console.readLine()) != null) {
                line = line.trim();
                if (line.equals("/list")) {
                    System.out.println("[ADMIN] " + getOnlineUsers());

                } else if (line.startsWith("/kick ")) {
                    String target = line.substring(6).trim();
                    ClientHandler handler = clients.get(target);
                    if (handler != null) {
                        handler.sendMessage("SYSTEM:You have been kicked by the admin.");
                        handler.forceDisconnect();
                        System.out.println("[ADMIN] Kicked: " + target);
                        logger.logSystem("Admin kicked: " + target);
                    } else {
                        System.out.println("[ADMIN] User not found: " + target);
                    }

                } else if (line.startsWith("/broadcast ")) {
                    String msg = line.substring(11).trim();
                    broadcastSystemMessage("ADMIN: " + msg);
                    logger.logSystem("Admin broadcast: " + msg);

                } else if (line.equals("/help")) {
                    System.out.println("[ADMIN] /list — show online users");
                    System.out.println("[ADMIN] /kick <user> — disconnect a user");
                    System.out.println("[ADMIN] /broadcast <msg> — send server announcement");

                } else if (!line.isEmpty()) {
                    System.out.println("[ADMIN] Unknown command. Type /help");
                }
            }
        } catch (IOException e) {
            System.err.println("[ADMIN] Console error: " + e.getMessage());
        }
    }

    // ── Broadcast ──────────────────────────────────────────────────────────────
    public void broadcast(String message, String senderUsername) {
        String formatted = "[" + timestamp() + "] " + senderUsername + ": " + message;
        System.out.println(formatted);
        logger.logMessage(senderUsername, message); // Week 3: log it
        for (ClientHandler handler : clients.values()) {
            handler.sendMessage(formatted);
        }
    }

    // ── Private message ────────────────────────────────────────────────────────
    public boolean sendPrivate(String targetUsername, String message, String senderUsername) {
        ClientHandler target = clients.get(targetUsername);
        if (target == null) return false;

        String formatted = "[" + timestamp() + "] [DM from " + senderUsername + "]: " + message;
        target.sendMessage(formatted);

        ClientHandler sender = clients.get(senderUsername);
        if (sender != null) {
            sender.sendMessage("[" + timestamp() + "] [DM to " + targetUsername + "]: " + message);
        }

        logger.logDM(senderUsername, targetUsername, message); // Week 3: log DMs too
        return true;
    }

    // ── Register / remove ──────────────────────────────────────────────────────
    public boolean registerClient(String username, ClientHandler handler) {
        if (clients.containsKey(username)) return false;
        clients.put(username, handler);
        String event = username + " joined the chat! (" + clients.size() + " online)";
        broadcastSystemMessage(event);
        logger.logSystem(event);
        return true;
    }

    public void removeClient(String username) {
        clients.remove(username);
        String event = username + " left the chat. (" + clients.size() + " online)";
        broadcastSystemMessage(event);
        logger.logSystem(event);
    }

    public String getOnlineUsers() {
        if (clients.isEmpty()) return "No users online.";
        return "Online (" + clients.size() + "): " + String.join(", ", clients.keySet());
    }

    public void broadcastSystemMessage(String message) {
        String formatted = "[" + timestamp() + "] *** " + message + " ***";
        System.out.println(formatted);
        for (ClientHandler handler : clients.values()) {
            handler.sendMessage(formatted);
        }
    }

    // Expose clients map so ClientHandler can offer Tab-autocomplete usernames
    public Set<String> getOnlineUsernames() {
        return clients.keySet();
    }

    private String timestamp() {
        java.time.LocalTime now = java.time.LocalTime.now();
        return String.format("%02d:%02d", now.getHour(), now.getMinute());
    }

    public static void main(String[] args) {
        new ChatServer().start();
    }
}