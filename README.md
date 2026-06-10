# QuickChat

A multi-threaded LAN chat application built in Java, covering core concepts from a Java programming course — sockets, threads, Swing GUI, and file I/O.

---

## What's built

### Architecture

```
src/
├── server/
│   ├── ChatServer.java       — TCP server, one thread per client, admin console
│   ├── ClientHandler.java    — handles one connected client on its own thread
│   └── ChatLogger.java       — writes all messages to daily rotating log files
└── client/
    ├── ChatClient.java       — original CLI client (Week 1)
    └── ChatClientGUI.java    — Swing desktop client (Week 2+)
```

### Features
- Drag & connect — any number of clients on the same network
- Real-time broadcast messaging to all connected users
- Private DMs via `/msg <username> <text>`
- Live online users sidebar, updates as people join/leave
- Tab autocomplete for usernames after `/msg`
- Color-coded messages (your messages in green, others in purple, DMs in orange)
- Daily rotating chat logs saved to `logs/chat_YYYY-MM-DD.log`
- Server admin commands: `/kick`, `/list`, `/broadcast`

---

## How to run

### Prerequisites
- Java 17 or higher
- Git Bash (Windows) or any terminal (Mac/Linux)

### Start the server
```bash
./run.sh server
```

### Connect clients (open new terminals for each)
```bash
./run.sh gui        # Swing GUI client (recommended)
./run.sh client     # CLI client (Week 1 style)
```

### Windows (without Git Bash)
```cmd
mkdir out
javac -d out src/server/ChatServer.java src/server/ClientHandler.java src/server/ChatLogger.java src/client/ChatClient.java src/client/ChatClientGUI.java
java -cp out server.ChatServer       # server
java -cp out client.ChatClientGUI    # GUI client
```

### In-chat commands
| Command | Description |
|---|---|
| `/users` | List everyone online |
| `/msg <user> <text>` | Send a private DM |
| `/quit` | Disconnect cleanly |
| Tab | Autocomplete username after `/msg` |

### Server admin commands (type in server terminal)
| Command | Description |
|---|---|
| `/list` | Show all online users |
| `/kick <user>` | Force-disconnect a user |
| `/broadcast <text>` | Send a server announcement to all |
| `/help` | Show all admin commands |

---

## Course concepts covered

| Week | Topic | Where it appears |
|---|---|---|
| Week 3 | Input/Output | `ChatLogger` — `BufferedWriter`, file append, daily rotation |
| Week 7 | Multithreading | One `Thread` per client, `ConcurrentHashMap`, `synchronized` log writes |
| Week 9 | Swing / AWT | `ChatClientGUI` — `JFrame`, `JTextPane`, `StyledDocument`, `JList` |
| Week 10 | Networking | `ServerSocket`, `Socket`, TCP client-server architecture |

---

## Next steps

The project is structured in weeks. Here's what comes after Week 3:

### Week 4 — File transfer (ties in Download Manager concept)
Add the ability to send files between users directly through the chat. This means a second socket connection just for the file bytes, a progress bar in the GUI, and the recipient choosing where to save it. Covers: `FileInputStream`, `FileOutputStream`, transfer in chunks, `JProgressBar`.

### Week 5 — Persistent chat history viewer
A separate window inside the GUI that lets you browse old logs. Load any `logs/chat_YYYY-MM-DD.log` from a date picker, render it with the same color styling as the live chat, and add a search bar to filter by username or keyword. Covers: `Files.readAllLines()`, `JFileChooser`, `JTabbedPane`.

### Week 6 — Connection resilience
Right now if the server goes down, the client just dies. Add auto-reconnect: the client detects the disconnect, shows a "reconnecting..." overlay, and retries with exponential backoff (1s, 2s, 4s...). Covers: exception handling patterns, retry loops, `SwingWorker` for background tasks.

### Week 7 — Rooms / channels
Add chat rooms like `#general` and `#random`. Users can `/join <room>` and `/leave <room>`. Messages only go to people in the same room. The server needs a `ConcurrentHashMap<String, Set<ClientHandler>>` for room membership. Covers: deeper concurrent data structures, more complex routing logic.

### Week 8 — Simple encryption
Encrypt messages between client and server using a shared key so they can't be read by packet sniffers on the network. Java's `javax.crypto` package has `AES` built in. Covers: `Cipher`, `SecretKeySpec`, Base64 encoding, the concept of symmetric encryption.

### Stretch goal — go beyond LAN
Right now this only works on localhost or a local network. To let friends connect from anywhere, you'd need to deploy the server to a cloud VM (free tier on Oracle Cloud or Fly.io), open the port in the firewall, and have clients connect to the public IP instead of `localhost`. No code changes needed — just deployment.

---

## Project log

| Week | What was built |
|---|---|
| Week 1 | TCP server + CLI client, multithreading, broadcast, private messages |
| Week 2 | Swing GUI client with styled chat pane and online users sidebar |
| Week 3 | Chat logger with daily rotation, Tab autocomplete, server admin commands |
| Week 4 (Current) | P2P data-plane file transfer, isolated channel routing, and exponential backoff auto-reconnect loops |
