package server;

import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.time.format.*;

/**
 * ChatLogger — Week 3
 *
 * Writes every chat message to a daily log file:
 *   logs/chat_2025-06-10.log
 *
 * Key I/O concepts used:
 *   - BufferedWriter for efficient file writes (not one syscall per message)
 *   - FileWriter in append mode (true) so restarts don't wipe history
 *   - Automatic daily file rotation via LocalDate in filename
 *   - synchronized to stay thread-safe (multiple ClientHandler threads call log())
 */

public class ChatLogger {

    private static final String LOG_DIR = "logs";
    private BufferedWriter writer;
    private String currentDate;

    private static final DateTimeFormatter DATE_FMT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FMT =
        DateTimeFormatter.ofPattern("HH:mm:ss");

    public ChatLogger() {
        try {
            Files.createDirectories(Paths.get(LOG_DIR));
            openTodaysFile();
        } catch (IOException e) {
            System.err.println("[LOGGER] Could not create log directory: " + e.getMessage());
        }
    }

    //Called by ChatServer for every broadcast and system event ──────────────
    public synchronized void log(String type, String username, String message) {
        try {
            rotatIfNeeded(); // New day → new file

            String line = String.format("[%s] [%s] %s: %s",
                LocalTime.now().format(TIME_FMT),
                type,
                username,
                message
            );

            writer.write(line);
            writer.newLine();
            writer.flush(); // Flush so log is readable even if server crashes

        } catch (IOException e) {
            System.err.println("[LOGGER] Write error: " + e.getMessage());
        }
    }

    // Convenience overloads
    public void logMessage(String username, String message) {
        log("MSG", username, message);
    }

    public void logSystem(String event) {
        log("SYS", "SERVER", event);
    }

    public void logDM(String from, String to, String message) {
        log("DM", from + " -> " + to, message);
    }

    //Opens (or reopens) today's log file ────────────────────────────────────
    private void openTodaysFile() throws IOException {
        if (writer != null) {
            try { writer.close(); } catch (IOException ignored) {}
        }

        currentDate = LocalDate.now().format(DATE_FMT);
        String filename = LOG_DIR + "/chat_" + currentDate + ".log";

        // append=true: don't erase existing log on server restart
        writer = new BufferedWriter(new FileWriter(filename, true));
        logSystem("=== Session started ===");
        System.out.println("[LOGGER] Logging to: " + filename);
    }

    //Auto-rotate when day changes ───────────────────────────────────────────
    private void rotatIfNeeded() throws IOException {
        String today = LocalDate.now().format(DATE_FMT);
        if (!today.equals(currentDate)) {
            logSystem("=== Session ended (day rollover) ===");
            openTodaysFile();
        }
    }

    public void close() {
        try {
            if (writer != null) {
                logSystem("=== Session ended ===");
                writer.close();
            }
        } catch (IOException ignored) {}
    }
}
