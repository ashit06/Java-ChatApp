# 💬 Java Multi-User Chat App (Swing GUI)

A multi-user real-time chat application built using Java Sockets and Swing GUI. It supports live messaging, private messaging, typing indicators, and file sharing—all in a clean graphical interface.

---

## 🔧 Features Implemented

- 👥 Multiple users can connect and chat in real time
- 💬 Public chat with auto-scrolling chat panel
- 🙋 Unique username required (no password needed)
- ✉️ Private messages via `/pm username message`
- 📁 File sharing (up to 5MB)
- ✍️ Typing indicator shows when someone is typing
- 📜 Recent chat history (last 50 messages) shown to new users
- 😊 Emoji support for `:)`, `:(`, `:D`, `:P`, `<3`, `:O`
- 🪟 Built with Java Swing (no external libraries)

---

## 🛠️ How to Setup & Run
```bash
1. Clone the Repository
git clone https://github.com/your-username/java-chat-app.git
cd java-chat-app
Replace with your actual GitHub repository URL
2. Compile the Java Files
javac ChatServer.java ChatClient.java
3. Run the Server
java ChatServer
Starts the server on localhost:12345
Creates chat_history.txt for message history
4. Start One or More Clients
In a new terminal window or tab:

java ChatClient
You'll be asked for a username
Start chatting with others!
📝 How to Use

Public message: Type your message and hit Enter or press Send
Private message:
Type /pm username Hello
OR double-click a user in the right panel
Send file: Click 📁 and choose recipient (or leave blank for all)
Typing: Typing updates appear live in the status bar
🗂 Project Structure

├── ChatClient.java     # GUI Client
├── ChatServer.java     # Server code
├── chat_history.txt    # Auto-created history log
└── README.md           # You're reading it
✅ Requirements

Java JDK 8 or higher
Works on Windows, macOS, and Linux
👤 Author

Arpit Agrahari
