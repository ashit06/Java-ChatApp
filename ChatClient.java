
import javax.swing.*;
import javax.swing.text.*;
import javax.swing.text.html.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import javax.swing.Timer;

public class ChatClient extends JFrame {
    private JTextPane chatPane;
    private JTextField inputField;
    private JLabel statusBar;
    private JList<String> userList;
    private DefaultListModel<String> userListModel;
    private PrintWriter out;
    private String username;
    private Timer typingTimer;
    private Timer clearStatusTimer;
    private HTMLEditorKit htmlKit;
    private HTMLDocument htmlDoc;
    private Socket socket;

    public ChatClient() {
        createGUI();
        connectToServer();
    }

    private void createGUI() {
        setTitle("Chat Client");
        setSize(800, 600);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        chatPane = new JTextPane();
        chatPane.setEditable(false);
        chatPane.setContentType("text/html");
        htmlKit = new HTMLEditorKit();
        htmlDoc = new HTMLDocument();
        chatPane.setEditorKit(htmlKit);
        chatPane.setDocument(htmlDoc);

        JScrollPane chatScroll = new JScrollPane(chatPane);
        chatScroll.getVerticalScrollBar().addAdjustmentListener(e -> {
            if (!e.getValueIsAdjusting()) {
                e.getAdjustable().setValue(e.getAdjustable().getMaximum());
            }
        });
        add(chatScroll, BorderLayout.CENTER);

        JPanel userPanel = new JPanel(new BorderLayout());
        userPanel.setPreferredSize(new Dimension(150, 0));
        userPanel.setBorder(BorderFactory.createTitledBorder("Online Users"));

        userListModel = new DefaultListModel<>();
        userList = new JList<>(userListModel);
        userList.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    String user = userList.getSelectedValue();
                    if (user != null && !user.equals(username)) {
                        inputField.setText("/pm " + user + " ");
                        inputField.requestFocus();
                    }
                }
            }
        });

        userPanel.add(new JScrollPane(userList), BorderLayout.CENTER);
        add(userPanel, BorderLayout.EAST);

        JPanel inputPanel = new JPanel(new BorderLayout());

        inputField = new JTextField();
        inputField.addKeyListener(new KeyAdapter() {
            public void keyPressed(KeyEvent e) {
                if (out != null) {
                    out.println("/typing");
                }
            }
        });
        inputField.addActionListener(e -> sendMessage());

        JButton sendButton = new JButton("Send");
        sendButton.addActionListener(e -> sendMessage());

        JButton fileButton = new JButton("📁");
        fileButton.addActionListener(e -> sendFile());

        JPanel buttonPanel = new JPanel(new GridLayout(1, 2));
        buttonPanel.add(fileButton);
        buttonPanel.add(sendButton);

        inputPanel.add(inputField, BorderLayout.CENTER);
        inputPanel.add(buttonPanel, BorderLayout.EAST);

        statusBar = new JLabel(" ");
        statusBar.setBorder(BorderFactory.createEtchedBorder());

        typingTimer = new Timer(2000, e -> {
            if (out != null) out.println("/typing");
        });
        typingTimer.setRepeats(true);

        clearStatusTimer = new Timer(3000, e -> statusBar.setText(" "));
        clearStatusTimer.setRepeats(false);

        add(inputPanel, BorderLayout.SOUTH);
        add(statusBar, BorderLayout.NORTH);
    }

    private void connectToServer() {
        try {
            socket = new Socket("localhost", 12345);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

            while (true) {
                String serverCommand = in.readLine();
                if (serverCommand == null) break;

                if (serverCommand.equals("SUBMITNAME")) {
                    username = JOptionPane.showInputDialog("Enter username:");
                    if (username == null) System.exit(0);
                    out.println(username);
                } else if (serverCommand.equals("NAMEACCEPTED")) {
                    break;
                } else if (serverCommand.equals("NAME_IN_USE")) {
                    JOptionPane.showMessageDialog(this, "Username already taken. Try another.");
                    System.exit(1);
                }
            }

            new Thread(() -> {
                try {
                    String message;
                    while ((message = in.readLine()) != null) {
                        processServerMessage(message);
                    }
                } catch (IOException e) {
                    SwingUtilities.invokeLater(() ->
                        appendToChat("SYSTEM: Connection lost", Color.RED));
                } finally {
                    try {
                        socket.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }).start();

        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Cannot connect to server: " + e.getMessage());
            System.exit(1);
        }
    }

    private void sendMessage() {
        String message = inputField.getText().trim();
        if (!message.isEmpty()) {
            out.println(message);
            inputField.setText("");
        }
    }

    private void sendFile() {
        JOptionPane.showMessageDialog(this, "File sharing not implemented in this demo.");
    }

    private void appendToChat(String message, Color color) {
        SwingUtilities.invokeLater(() -> {
            try {
                String colorHex = String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
                String htmlMsg = "<div style='color:" + colorHex + "'>" + message + "</div>";
                htmlKit.insertHTML(htmlDoc, htmlDoc.getLength(), htmlMsg, 0, 0, null);
                chatPane.setCaretPosition(htmlDoc.getLength());
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void processServerMessage(String message) {
        if (message.startsWith("HISTORY:")) {
            appendToChat(message.substring(8), Color.GRAY);
        } else if (message.startsWith("TYPING:")) {
            String typingUser = message.substring(7);
            if (!typingUser.equals(username)) {
                SwingUtilities.invokeLater(() -> {
                    statusBar.setText(typingUser + " is typing...");
                    clearStatusTimer.start();
                });
            }
        } else if (message.startsWith("USERS:")) {
            SwingUtilities.invokeLater(() -> {
                userListModel.clear();
                String[] users = message.substring(6).split(",");
                for (String user : users) {
                    if (!user.isEmpty()) {
                        userListModel.addElement(user);
                    }
                }
            });
        } else {
            appendToChat(message, Color.BLACK);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            ChatClient client = new ChatClient();
            client.setVisible(true);
        });
    }
}