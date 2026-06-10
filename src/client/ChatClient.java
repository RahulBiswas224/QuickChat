package client;

import java.io.*;
import java.net.*;

/**
 * ChatClient — Week 1 (CLI version)
 *
 * Two threads keep things non-blocking:
 *   • Main thread  → reads keyboard input and sends to server
 *   • Listener thread → reads messages from server and prints them
 *
 * In Week 2 you'll replace the keyboard/print parts with a Swing GUI,
 * but the socket logic stays exactly the same.
 */
public class ChatClient {

    private static final String HOST = "localhost";
    private static final int PORT = 5000;

    private Socket socket;
    private BufferedReader serverReader;
    private PrintWriter serverWriter;
    private volatile boolean running = true;

    public void connect() {
        System.out.println("Connecting to QuickChat server at " + HOST + ":" + PORT + "...");

        try {
            socket = new Socket(HOST, PORT);
            serverReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            serverWriter = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);

            System.out.println("Connected!\n");

            // ── Listener thread: server → console ──────────────────────────────
            Thread listenerThread = new Thread(this::listenFromServer);
            listenerThread.setDaemon(true);
            listenerThread.setName("ServerListener");
            listenerThread.start();

            // ── Main thread: keyboard → server ─────────────────────────────────
            readFromKeyboard();

        } catch (ConnectException e) {
            System.err.println("Could not connect — is the server running on port " + PORT + "?");
        } catch (IOException e) {
            System.err.println("Connection error: " + e.getMessage());
        } finally {
            cleanup();
        }
    }

    // ── Runs on listener thread ─────────────────────────────────────────────────
    private void listenFromServer() {
        try {
            String message;
            while (running && (message = serverReader.readLine()) != null) {
                // SYSTEM: prefix = server notification, strip it and style differently
                if (message.startsWith("SYSTEM:")) {
                    System.out.println("  >>> " + message.substring(7));
                } else {
                    System.out.println(message);
                }
            }
        } catch (IOException e) {
            if (running) {
                System.out.println("\nDisconnected from server.");
            }
        }
        running = false;
    }

    // ── Runs on main thread ─────────────────────────────────────────────────────
    private void readFromKeyboard() {
        BufferedReader keyboard = new BufferedReader(new InputStreamReader(System.in));
        try {
            String input;
            while (running && (input = keyboard.readLine()) != null) {
                if (input.equalsIgnoreCase("/quit")) {
                    serverWriter.println("/quit");
                    running = false;
                    break;
                }
                serverWriter.println(input);
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
