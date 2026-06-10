package client;

import java.io.*;
import java.net.*;

/**
 * ChatClient — Week 4 Update (CLI version with Auto-Reconnect)
 *
 * Two threads keep things non-blocking:
 * • Main thread  → reads keyboard input and sends to server
 * • Listener thread → reads messages from server and prints them
 */

public class ChatClient {

    private static final String HOST = "localhost";
    private static final int PORT = 5000;

    private Socket socket;
    private BufferedReader serverReader;
    private volatile PrintWriter serverWriter; // volatile ensures the main thread instantly sees updates on reconnect
    private volatile boolean running = true;
    private boolean isReconnecting = false;

    public void connect() {
        System.out.println("Connecting to QuickChat server at " + HOST + ":" + PORT + "...");

        try {
            establishConnection();
            System.out.println("Connected!\n");

            //Main thread: keyboard → server ─────────────────────────────────
            readFromKeyboard();

        } catch (IOException e) {
            System.err.println("Could not establish initial connection: " + e.getMessage());
        } finally {
            cleanup();
        }
    }

    private void establishConnection() throws IOException {
        socket = new Socket(HOST, PORT);
        serverReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        serverWriter = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);

        //Listener thread: server → console ──────────────────────────────
        Thread listenerThread = new Thread(this::listenFromServer);
        listenerThread.setDaemon(true);
        listenerThread.setName("ServerListener");
        listenerThread.start();
    }

    //Runs on listener thread ─────────────────────────────────────────────────
    private void listenFromServer() {
        try {
            String line;
            while (running && (line = serverReader.readLine()) != null) {
                if (line.startsWith("SYSTEM:")) {
                    System.out.println("  >>> " + line.substring(7));
                } else {
                    System.out.println(line);
                }
            }
        } catch (IOException e) {
            if (running && !isReconnecting) {
                System.out.println("\n[SYSTEM] Lost connection to server. Attempting to reconnect...");
                handleReconnect();
            }
        }
    }

    //Reconnection Loop with Exponential Backoff ─────────────────────────────
    private void handleReconnect() {
        isReconnecting = true;
        
        Thread reconnectThread = new Thread(() -> {
            int delay = 1000; // Start at 1 second
            int maxDelay = 16000; // Cap at 16 seconds

            while (running) {
                try {
                  
                    // Close old streams cleanly
                    if (serverReader != null) try { serverReader.close(); } catch (IOException ignored) {}
                    if (serverWriter != null) serverWriter.close(); // No try-catch needed for PrintWriter!
                    if (socket != null) try { socket.close(); } catch (IOException ignored) {}

                    System.out.println("[SYSTEM] Retrying connection...");
                    establishConnection();
                    
                    System.out.println("[SYSTEM] Reconnected successfully! You may resume typing.");
                    isReconnecting = false;
                    break; // Successfully connected, break out of retry loop

                } catch (IOException e) {
                    System.out.println("[SYSTEM] Connection failed. Retrying in " + (delay / 1000) + " seconds...");
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    delay = Math.min(delay * 2, maxDelay); // Double the backoff delay
                }
            }
        });
        
        reconnectThread.setName("CliReconnectWorker");
        reconnectThread.start();
    }

    //Runs on main thread ─────────────────────────────────────────────────────
    private void readFromKeyboard() {
        BufferedReader keyboard = new BufferedReader(new InputStreamReader(System.in));
        try {
            String input;
            while (running && (input = keyboard.readLine()) != null) {
                if (input.equalsIgnoreCase("/quit")) {
                    if (serverWriter != null) serverWriter.println("/quit");
                    running = false;
                    break;
                }
                
                // If we are currently reconnecting, block inputs or warn user
                if (isReconnecting) {
                    System.out.println("  [!] Cannot send message. Still offline...");
                    continue;
                }

                if (serverWriter != null) {
                    serverWriter.println(input);
                }
            }
        } catch (IOException e) {
            System.err.println("Keyboard read error: " + e.getMessage());
        }
    }

    private void cleanup() {
        running = false;
        try {
            if (serverReader != null) serverReader.close();
            if (serverWriter != null) serverWriter.close();
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException e) {
            // Ignore cleanup errors
        }
        System.out.println("Goodbye!");
    }

    public static void main(String[] args) {
        new ChatClient().connect();
    }
}