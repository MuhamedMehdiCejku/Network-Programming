package sb.server;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime; 
import java.time.format.DateTimeFormatter; 
import java.util.ArrayList; 
import java.util.Collections; 
import java.util.List; 
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SyncBridgeServer {

    private static final int PORT = 5050;
    private static final Map<String, ClientHandler> clients = new ConcurrentHashMap<>();
    private static final List<String> chatHistory = Collections.synchronizedList(new ArrayList<>());
    private static final DateTimeFormatter DT_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static void main(String[] args) {
        System.out.println("SyncBridge Server running on port " + PORT);
        chatHistory.add("[system] Server started at " + LocalDateTime.now().format(DT_FORMAT));
        try (ServerSocket server = new ServerSocket(PORT)) {
            while (true) {
                Socket sock = server.accept();
                new ClientHandler(sock).start();
            }
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
        }
    }

    // Used for regular chat messages (does NOT send back to sender)
    static void broadcast(String msg, String fromNick) {
        chatHistory.add(msg); 
        for (ClientHandler c : clients.values()) {
            if (!c.nick.equals(fromNick)) c.send(msg);
        }
        System.out.println(msg);
    }
    
    // Used for system messages/read receipts (sends to ALL clients, including sender)
    static void broadcastAll(String msg) {
        chatHistory.add(msg);
        for (ClientHandler c : clients.values()) {
            c.send(msg);
        }
        System.out.println(msg);
    }

    private static class ClientHandler extends Thread {
        private final Socket socket;
        private BufferedReader in;
        private PrintWriter out;
        String nick = "???";

        ClientHandler(Socket socket) {
            this.socket = socket;
        }

        void send(String msg) {
            try {
                out.println(msg);
            } catch (Exception e) {
                disconnect();
            }
        }

        @Override
        public void run() {
            try {
                in = new BufferedReader(new InputStreamReader(
                        socket.getInputStream(), StandardCharsets.UTF_8));
                out = new PrintWriter(new OutputStreamWriter(
                        socket.getOutputStream(), StandardCharsets.UTF_8), true);

                String first = in.readLine();
                if (first == null || !first.startsWith("NICK:")) {
                    socket.close();
                    return;
                }
                nick = first.substring(5).trim();
                if (nick.isEmpty() || clients.containsKey(nick)) {
                    out.println("[system] Nickname not allowed.");
                    socket.close();
                    return;
                }

                clients.put(nick, this);
                out.println("[system] Welcome, " + nick + "!");
                
                // Send Chat History on Connect
                for (String historyMsg : chatHistory) {
                    out.println(historyMsg);
                }
                
                broadcast("[system] " + nick + " has connected successfully.", nick);

                String line;
                while ((line = in.readLine()) != null) {
                    if (line.equalsIgnoreCase("QUIT")) break;

                    if (line.startsWith("MSG:")) {
                        String txt = line.substring(4).trim();
                        if (!txt.isEmpty())
                            broadcast(nick + ": " + txt, nick);

                    } else if (line.equals("TYPING_ON")) {
                        broadcast("[typing] " + nick + " is typing...", nick);

                    } else if (line.equals("TYPING_OFF")) {
                        broadcast("[typing] " + nick + " has stopped typing.", nick);

                    } else if (line.equals("READ")) {
                        // Send "READ" status to all clients
                        broadcastAll("[read] " + nick + " has read the messages.");
                        
                    } else if (line.startsWith("SAVE_CHAT:")) {
                        // Use broadcastAll for system messages to ensure the sender also sees the confirmation
                        String filename = line.substring("SAVE_CHAT:".length()).trim();
                        broadcastAll("[system] " + nick + " saved the chat locally as: " + filename);
                    }
                }

            } catch (IOException e) {
                System.out.println(nick + " disconnected.");
            } finally {
                disconnect();
            }
        }

        private void disconnect() {
            if (clients.remove(nick) != null) {
                broadcast("[system] " + nick + " was kicked out of server.", nick);
            }
            try { socket.close(); } catch (IOException ignored) {}
        }
    }
}