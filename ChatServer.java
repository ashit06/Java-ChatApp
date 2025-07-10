import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;

public class ChatServer {
    private static final int PORT = 12345;
    private static final int MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB
    private static Set<ClientHandler> clients = Collections.synchronizedSet(new HashSet<>());
    private static Set<String> usernames = Collections.synchronizedSet(new HashSet<>());
    private static final String HISTORY_FILE = "chat_history.txt";
    private static final Map<String, String> EMOJI_MAP = createEmojiMap();

    public static void main(String[] args) {
        loadChatHistory();

        System.out.println("🚀 Enhanced Chat Server running on port " + PORT);
        System.out.println("🔒 Authentication disabled | 💾 History tracking");

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            ExecutorService pool = Executors.newCachedThreadPool();
            while (true) {
                Socket clientSocket = serverSocket.accept();
                pool.execute(new ClientHandler(clientSocket));
            }
        } catch (IOException e) {
            System.out.println("Server exception: " + e.getMessage());
        }
    }

    private static Map<String, String> createEmojiMap() {
        Map<String, String> map = new HashMap<>();
        map.put(":)", "😊");
        map.put(":(", "😞");
        map.put(":D", "😃");
        map.put(":P", "😛");
        map.put("<3", "❤️");
        map.put(":O", "😮");
        return Collections.unmodifiableMap(map);
    }

    private static void loadChatHistory() {
        try {
            Path path = Paths.get(HISTORY_FILE);
            if (!Files.exists(path)) Files.createFile(path);
        } catch (IOException e) {
            System.out.println("History file error: " + e.getMessage());
        }
    }

    private static void saveMessage(String message) {
        try (BufferedWriter writer = Files.newBufferedWriter(
                Paths.get(HISTORY_FILE), StandardCharsets.UTF_8, StandardOpenOption.APPEND)) {
            writer.write(message + "\n");
        } catch (IOException e) {
            System.out.println("Failed to save message: " + e.getMessage());
        }
    }

    private static class ClientHandler implements Runnable {
        private Socket socket;
        private PrintWriter out;
        private BufferedReader in;
        private String username;
        private long lastTypingTime = 0;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        public void run() {
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                // Ask username only
                out.println("SUBMITNAME");
                username = in.readLine();
                if (username == null) return;

                synchronized (usernames) {
                    if (usernames.contains(username)) {
                        out.println("NAME_IN_USE");
                        return;
                    }
                    usernames.add(username);
                }

                out.println("NAMEACCEPTED");
                broadcast("SERVER: " + username + " joined the chat", true);
                sendUserList();
                System.out.println("✅ " + username + " connected");

                synchronized (clients) {
                    clients.add(this);
                }

                sendHistory();

                String message;
                while ((message = in.readLine()) != null) {
                    if (message.startsWith("/typing")) {
                        broadcastTyping();
                    } 
                    else if (message.startsWith("/pm ")) {
                        handlePrivateMessage(message);
                    }
                    else if (message.startsWith("/file ")) {
                        handleFileTransfer(message);
                    }
                    else {
                        String formattedMsg = formatMessage(username + ": " + message);
                        broadcast(formattedMsg, true);
                        saveMessage(formattedMsg);
                    }
                }
            } catch (IOException e) {
                System.out.println("⚠️ " + username + " connection error: " + e.getMessage());
            } finally {
                disconnect();
            }
        }

        private String formatMessage(String message) {
            for (Map.Entry<String, String> entry : EMOJI_MAP.entrySet()) {
                message = message.replace(entry.getKey(), entry.getValue());
            }
            return message;
        }

        private void broadcast(String message, boolean includeSelf) {
            synchronized (clients) {
                for (ClientHandler client : clients) {
                    if (includeSelf || client != this) {
                        client.out.println(message);
                    }
                }
            }
        }

        private void broadcastTyping() {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastTypingTime > 2000) {
                broadcast("TYPING:" + username, false);
                lastTypingTime = currentTime;
            }
        }

        private void sendHistory() {
            try (BufferedReader reader = Files.newBufferedReader(Paths.get(HISTORY_FILE))) {
                String line;
                int count = 0;
                while ((line = reader.readLine()) != null && count++ < 50) {
                    out.println("HISTORY:" + line);
                }
            } catch (IOException e) {}
        }

        private void sendUserList() {
            StringBuilder userList = new StringBuilder("USERS:");
            synchronized (clients) {
                for (ClientHandler client : clients) {
                    userList.append(client.username).append(",");
                }
            }
            broadcast(userList.toString(), true);
        }

        private void handlePrivateMessage(String message) {
            String[] parts = message.split(" ", 3);
            if (parts.length < 3) {
                out.println("SERVER: Invalid private message format");
                return;
            }

            String recipient = parts[1];
            String pm = formatMessage("[PM] " + username + " → " + recipient + ": " + parts[2]);

            boolean sent = false;
            synchronized (clients) {
                for (ClientHandler client : clients) {
                    if (client.username.equals(recipient)) {
                        client.out.println(pm);
                        sent = true;
                    }
                }
            }

            if (sent) {
                out.println(pm);
                saveMessage(pm);
            } else {
                out.println("SERVER: User '" + recipient + "' not found");
            }
        }

        private void handleFileTransfer(String message) {
            String[] parts = message.split(" ", 3);
            if (parts.length < 3) {
                out.println("SERVER: Invalid file format");
                return;
            }

            String recipient = parts[1];
            String filename = parts[2];
            String fileInfo = "FILE:" + username + ":" + filename;

            try {
                boolean found = false;
                synchronized (clients) {
                    for (ClientHandler client : clients) {
                        if (client.username.equals(recipient)) {
                            client.out.println(fileInfo);
                            found = true;
                            break;
                        }
                    }
                }

                if (!found) {
                    out.println("SERVER: User '" + recipient + "' not found");
                    return;
                }

                DataInputStream dis = new DataInputStream(socket.getInputStream());
                long fileSize = dis.readLong();

                if (fileSize > MAX_FILE_SIZE) {
                    out.println("SERVER: File exceeds 5MB limit");
                    return;
                }

                synchronized (clients) {
                    for (ClientHandler client : clients) {
                        if (client.username.equals(recipient)) {
                            DataOutputStream dos = new DataOutputStream(client.socket.getOutputStream());
                            dos.writeLong(fileSize);

                            byte[] buffer = new byte[8192];
                            long bytesRemaining = fileSize;
                            while (bytesRemaining > 0) {
                                int bytesToRead = (int) Math.min(buffer.length, bytesRemaining);
                                int bytesRead = dis.read(buffer, 0, bytesToRead);
                                if (bytesRead == -1) break;

                                dos.write(buffer, 0, bytesRead);
                                bytesRemaining -= bytesRead;
                            }
                            break;
                        }
                    }
                }

                String notification = formatMessage("SERVER: " + username + " sent file to " + recipient + ": " + filename);
                broadcast(notification, true);
                saveMessage(notification);

            } catch (IOException e) {
                out.println("SERVER: File transfer failed: " + e.getMessage());
            }
        }

        private void disconnect() {
            try {
                socket.close();
            } catch (IOException ignored) {}

            synchronized (clients) {
                clients.remove(this);
            }
            synchronized (usernames) {
                usernames.remove(username);
            }

            broadcast("SERVER: " + username + " left the chat", true);
            sendUserList();
            System.out.println("❌ " + username + " disconnected");
        }
    }
}