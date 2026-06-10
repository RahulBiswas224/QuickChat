package client;

import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;

/**
 * ChatClientGUI — Week 4 (File Transfer Update)
 */

public class ChatClientGUI extends JFrame {

    //Network ────────────────────────────────────────────────────────────────
    private static final String HOST = "localhost";
    private static final int PORT = 5000;

    private Socket socket;
    private BufferedReader serverReader;
    private PrintWriter serverWriter;
    private volatile boolean running = true;

    //UI components ──────────────────────────────────────────────────────────
    private JTextPane chatPane;
    private StyledDocument doc;
    private JTextField inputField;
    private JButton sendButton;
    private DefaultListModel<String> userListModel;
    private JLabel statusLabel;

    //Colours ────────────────────────────────────────────────────────────────
    private static final Color COL_SYSTEM  = new Color(0x888888);
    private static final Color COL_MY_MSG  = new Color(0x0F6E56);
    private static final Color COL_OTHER   = new Color(0x534AB7);
    private static final Color COL_DM      = new Color(0xD85A30);
    private static final Color COL_TIME    = new Color(0xAAAAAA);
    private static final Color COL_BG      = new Color(0xFFFFFF);
    private static final Color COL_SIDEBAR = new Color(0xFAFAFA);

    private String myUsername = "";

    public ChatClientGUI() {
        super("QuickChat");
        buildUI();
        askForUsername();
    }

    //Build the Swing UI ─────────────────────────────────────────────────────
    private void buildUI() {
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) { disconnect(); }
        });
        setSize(720, 500);
        setMinimumSize(new Dimension(500, 380));
        setLocationRelativeTo(null);

        //Chat pane (non-editable, styled) ───────────────────────────────────
        chatPane = new JTextPane();
        chatPane.setEditable(false);
        chatPane.setBackground(COL_BG);
        chatPane.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        doc = chatPane.getStyledDocument();

        JScrollPane chatScroll = new JScrollPane(chatPane);
        chatScroll.setBorder(BorderFactory.createEmptyBorder());

        //Online users sidebar ───────────────────────────────────────────────
        userListModel = new DefaultListModel<>();
        JList<String> userList = new JList<>(userListModel);
        userList.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        userList.setBackground(COL_SIDEBAR);
        userList.setFixedCellHeight(28);
        userList.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

        JLabel onlineLabel = new JLabel("  Online");
        onlineLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        onlineLabel.setForeground(Color.GRAY);
        onlineLabel.setBorder(BorderFactory.createEmptyBorder(8, 8, 4, 8));

        JPanel sidebar = new JPanel(new BorderLayout());
        sidebar.setBackground(COL_SIDEBAR);
        sidebar.setPreferredSize(new Dimension(160, 0));
        sidebar.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, new Color(0xDDDDDD)));
        sidebar.add(onlineLabel, BorderLayout.NORTH);
        sidebar.add(new JScrollPane(userList), BorderLayout.CENTER);

        //Input row ──────────────────────────────────────────────────────────
        inputField = new JTextField();
        inputField.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        inputField.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(0xDDDDDD)),
            BorderFactory.createEmptyBorder(8, 12, 8, 12)
        ));
        inputField.setEnabled(false);
        inputField.addActionListener(e -> sendMessage());
        inputField.setToolTipText("Type a message · /msg <user> for DM · Tab to autocomplete username");

        inputField.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyPressed(java.awt.event.KeyEvent e) {
                if (e.getKeyCode() == java.awt.event.KeyEvent.VK_TAB) {
                    e.consume();
                    autocompleteUsername();
                }
            }
        });

        sendButton = new JButton("Send");
        sendButton.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        sendButton.setBackground(new Color(0x534AB7));
        sendButton.setForeground(Color.WHITE);
        sendButton.setFocusPainted(false);
        sendButton.setBorder(BorderFactory.createEmptyBorder(8, 20, 8, 20));
        sendButton.setEnabled(false);
        sendButton.addActionListener(e -> sendMessage());

        // File Attachment Button
        JButton fileButton = new JButton("📎");
        fileButton.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        fileButton.setFocusPainted(false);
        fileButton.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        fileButton.addActionListener(e -> initiateFileTransfer());

        JPanel inputPanel = new JPanel(new BorderLayout());
        inputPanel.add(fileButton, BorderLayout.WEST);
        inputPanel.add(inputField, BorderLayout.CENTER);
        inputPanel.add(sendButton, BorderLayout.EAST);

        //Status bar ─────────────────────────────────────────────────────────
        statusLabel = new JLabel("  Not connected");
        statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        statusLabel.setForeground(Color.GRAY);
        statusLabel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

        //Main layout ────────────────────────────────────────────────────────
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, chatScroll, sidebar);
        splitPane.setResizeWeight(1.0);
        splitPane.setDividerSize(1);
        splitPane.setBorder(BorderFactory.createEmptyBorder());

        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(inputPanel, BorderLayout.CENTER);
        bottomPanel.add(statusLabel, BorderLayout.SOUTH);

        setLayout(new BorderLayout());
        add(splitPane, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);
    }

    //File Transfer Logic (Control Plane) ────────────────────────────────────
    private void initiateFileTransfer() {
        JFileChooser fileChooser = new JFileChooser();
        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File fileToSend = fileChooser.getSelectedFile();
            
            String targetUser = JOptionPane.showInputDialog(this, "Send file to which user?");
            if (targetUser != null && !targetUser.isBlank()) {
                new Thread(() -> hostFileAndNotify(fileToSend, targetUser)).start();
            }
        }
    }

    private void hostFileAndNotify(File file, String targetUser) {
        try (ServerSocket fileServer = new ServerSocket(0)) { 
            int port = fileServer.getLocalPort();
            
            // Notify target via main server
            serverWriter.println("/msg " + targetUser + " [SYSTEM_FILE_OFFER] " + file.getName() + " " + port);
            SwingUtilities.invokeLater(() -> appendSystem("Waiting for " + targetUser + " to accept " + file.getName() + "..."));

            // Wait for target to connect and stream bytes
            try (Socket targetSocket = fileServer.accept();
                 FileInputStream fis = new FileInputStream(file);
                 OutputStream os = targetSocket.getOutputStream()) {
                 
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    os.write(buffer, 0, bytesRead);
                }
                SwingUtilities.invokeLater(() -> appendSystem("File " + file.getName() + " sent successfully!"));
            }
        } catch (IOException ex) {
            SwingUtilities.invokeLater(() -> appendSystem("File transfer failed: " + ex.getMessage()));
        }
    }

    private void downloadFile(String filename, int port) {
        SwingUtilities.invokeLater(() -> appendSystem("Receiving file: " + filename + "..."));
        
        File outDir = new File("downloads");
        outDir.mkdir(); 
        File outFile = new File(outDir, filename);

        try (Socket downloadSocket = new Socket(HOST, port);
             InputStream is = downloadSocket.getInputStream();
             FileOutputStream fos = new FileOutputStream(outFile)) {

            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                fos.write(buffer, 0, bytesRead);
            }
            SwingUtilities.invokeLater(() -> appendSystem("Downloaded " + filename + " to the downloads folder!"));

        } catch (IOException e) {
            SwingUtilities.invokeLater(() -> appendSystem("Failed to download file: " + e.getMessage()));
        }
    }

    //Username dialog ────────────────────────────────────────────────────────
    private void askForUsername() {
        String name = JOptionPane.showInputDialog(
            this, "Enter your username:", "QuickChat", JOptionPane.PLAIN_MESSAGE
        );
        if (name == null || name.isBlank()) {
            System.exit(0);
        }
        myUsername = name.trim();
        setTitle("QuickChat — " + myUsername);
        connectToServer();
    }

    //Connect to server ──────────────────────────────────────────────────────
    private void connectToServer() {
        try {
            socket = new Socket(HOST, PORT);
            serverReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            serverWriter = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);

            statusLabel.setText("  Connected to " + HOST + ":" + PORT);

            Thread listener = new Thread(this::listenFromServer);
            listener.setDaemon(true);
            listener.setName("ServerListener");
            listener.start();

            inputField.setEnabled(true);
            sendButton.setEnabled(true);
            inputField.requestFocus();

        } catch (ConnectException e) {
            JOptionPane.showMessageDialog(this,
                "Could not connect — is the server running on port " + PORT + "?",
                "Connection failed", JOptionPane.ERROR_MESSAGE);
            System.exit(0);
        } catch (IOException e) {
            appendSystem("Connection error: " + e.getMessage());
        }
    }

    //Listener thread ────────────────────────────────────────────────────────
    private void listenFromServer() {
        try {
            String line;
            while (running && (line = serverReader.readLine()) != null) {
                final String msg = line;
                SwingUtilities.invokeLater(() -> processIncoming(msg));
            }
        } catch (IOException e) {
            if (running) {
                SwingUtilities.invokeLater(() -> appendSystem("Disconnected from server."));
            }
        }
    }

    //Route incoming message ─────────────────────────────────────────────────
    private void processIncoming(String msg) {
        // Intercept file offers
        if (msg.contains("[SYSTEM_FILE_OFFER]")) {
            String[] parts = msg.split(" ");
            String filename = parts[parts.length - 2];
            int port = Integer.parseInt(parts[parts.length - 1]);
            
            new Thread(() -> downloadFile(filename, port)).start();
            return;
        }

        if (msg.startsWith("SYSTEM:")) {
            String content = msg.substring(7);
            if (content.startsWith("Online (")) {
                updateUserList(content);
            } else if (content.contains("joined the chat")) {
                String user = content.split(" ")[0];
                if (!userListModel.contains(user)) userListModel.addElement(user);
                appendSystem(content);
            } else if (content.contains("left the chat")) {
                String user = content.split(" ")[0];
                userListModel.removeElement(user);
                appendSystem(content);
            } else {
                appendSystem(content);
            }
        } else if (msg.contains("[DM")) {
            appendDM(msg);
        } else {
            appendChat(msg);
        }
    }

    private void updateUserList(String content) {
        userListModel.clear();
        if (content.contains(": ")) {
            String[] users = content.split(": ")[1].split(", ");
            for (String u : users) {
                String clean = u.trim();
                if (clean.equals(myUsername)) clean = clean + " (you)";
                userListModel.addElement(clean);
            }
        }
    }

    //Text Formatting ────────────────────────────────────────────────────────
    private void appendChat(String line) {
        try {
            String timeStr = "";
            String rest = line;
            if (line.startsWith("[")) {
                int close = line.indexOf(']');
                if (close > 0) {
                    timeStr = line.substring(0, close + 1) + " ";
                    rest = line.substring(close + 2);
                }
            }

            String username = "";
            String message = rest;
            int colon = rest.indexOf(": ");
            if (colon > 0) {
                username = rest.substring(0, colon);
                message = rest.substring(colon + 2);
            }

            boolean isMe = username.equals(myUsername);
            Color nameColor = isMe ? COL_MY_MSG : COL_OTHER;

            appendStyled(timeStr, COL_TIME, false);
            appendStyled(username + ": ", nameColor, true);
            appendStyled(message + "\n", null, false);

        } catch (Exception e) {
            appendStyled(line + "\n", null, false);
        }
        scrollToBottom();
    }

    private void appendSystem(String msg) {
        appendStyled("  *** " + msg + " ***\n", COL_SYSTEM, false);
        scrollToBottom();
    }

    private void appendDM(String line) {
        appendStyled(line + "\n", COL_DM, false);
        scrollToBottom();
    }

    private void appendStyled(String text, Color color, boolean bold) {
        SimpleAttributeSet attrs = new SimpleAttributeSet();
        if (color != null) StyleConstants.setForeground(attrs, color);
        StyleConstants.setBold(attrs, bold);
        StyleConstants.setFontFamily(attrs, "Segoe UI");
        StyleConstants.setFontSize(attrs, 14);
        try {
            doc.insertString(doc.getLength(), text, attrs);
        } catch (BadLocationException ignored) {}
    }

    private void scrollToBottom() {
        chatPane.setCaretPosition(doc.getLength());
    }

    private void autocompleteUsername() {
        String text = inputField.getText();
        if (!text.startsWith("/msg ")) return;

        String partial = text.substring(5);
        if (partial.contains(" ")) return;

        java.util.List<String> matches = new java.util.ArrayList<>();
        for (int i = 0; i < userListModel.size(); i++) {
            String entry = userListModel.get(i).replace(" (you)", "");
            if (entry.toLowerCase().startsWith(partial.toLowerCase()) && !entry.equals(myUsername)) {
                matches.add(entry);
            }
        }

        if (matches.size() == 1) {
            inputField.setText("/msg " + matches.get(0) + " ");
            inputField.setCaretPosition(inputField.getText().length());
        } else if (matches.size() > 1) {
            appendSystem("Tab options: " + String.join(", ", matches));
        }
    }

    private void sendMessage() {
        String text = inputField.getText().trim();
        if (text.isEmpty() || serverWriter == null) return;
        serverWriter.println(text);
        inputField.setText("");
    }

    private void disconnect() {
        running = false;
        if (serverWriter != null) serverWriter.println("/quit");
        try {
            if (socket != null) socket.close();
        } catch (IOException ignored) {}
        System.exit(0);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            ChatClientGUI gui = new ChatClientGUI();
            gui.setVisible(true);
        });
    }
}