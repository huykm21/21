import java.io.*;
import java.net.*;
import java.util.*;

public class Server {
    private ServerSocket serverSocket;
    private Map<String, ClientHandler> clients = new HashMap<>();
    private Map<String, Set<ClientHandler>> groups = new HashMap<>();
    private Map<String, ClientHandler> groupOwners = new HashMap<>();  // Map nhóm với người tạo nhóm

    public Server(int port) throws IOException {
        serverSocket = new ServerSocket(port);
    }

    public void start() throws IOException {
        System.out.println("Server started...");
        while (true) {
            Socket clientSocket = serverSocket.accept();
            ClientHandler clientHandler = new ClientHandler(clientSocket, this);
            clientHandler.start();
        }
    }

    public synchronized void registerClient(String name, ClientHandler clientHandler) {
        clients.put(name, clientHandler);
        broadcastUserList();
    }

    public synchronized void unregisterClient(String name) {
        clients.remove(name);
        broadcastUserList();
    }

    public synchronized void broadcastUserList() {
        StringBuilder userList = new StringBuilder("/users ");
        for (String user : clients.keySet()) {
            userList.append(user).append(" ");
        }
        broadcast(userList.toString().trim());
    }

    public synchronized void broadcast(String message) {
        for (ClientHandler client : clients.values()) {
            client.sendMessage(message);
        }
    }

    public synchronized void createGroup(String groupName, ClientHandler creator) {
        if (!groups.containsKey(groupName)) {
            groups.put(groupName, new HashSet<>(Collections.singletonList(creator)));
            groupOwners.put(groupName, creator);  // Lưu trữ người tạo nhóm
            creator.addGroup(groupName);
            creator.sendMessage("Group '" + groupName + "' created.");
        } else {
            creator.sendMessage("Group '" + groupName + "' already exists.");
        }
    }

    public synchronized void addClientToGroup(String groupName, String clientName, ClientHandler requester) {
        Set<ClientHandler> group = groups.get(groupName);
        ClientHandler clientToAdd = clients.get(clientName);

        if (group != null && clientToAdd != null && groupOwners.get(groupName) == requester) {
            group.add(clientToAdd);
            requester.sendMessage(clientName + " has been added to group '" + groupName + "'.");
            clientToAdd.addGroup(groupName);
            clientToAdd.sendMessage("You have been added to group '" + groupName + "' by " + requester.getClientName() + ".");
        } else if (groupOwners.get(groupName) != requester) {
            requester.sendMessage("Only the group owner can add members.");
        } else {
            requester.sendMessage("Group or client does not exist.");
        }
    }

    public synchronized void removeClientFromGroup(String groupName, String clientName, ClientHandler requester) {
        Set<ClientHandler> group = groups.get(groupName);
        ClientHandler clientToRemove = clients.get(clientName);

        if (group != null && clientToRemove != null && groupOwners.get(groupName) == requester) {
            if (group.remove(clientToRemove)) {
                requester.sendMessage(clientName + " has been removed from group '" + groupName + "'.");
                clientToRemove.removeGroup(groupName);
                clientToRemove.sendMessage("You have been removed from group '" + groupName + "' by " + requester.getClientName() + ".");
            } else {
                requester.sendMessage(clientName + " is not in the group.");
            }
        } else if (groupOwners.get(groupName) != requester) {
            requester.sendMessage("Only the group owner can remove members.");
        } else {
            requester.sendMessage("Group or client does not exist.");
        }
    }

    public synchronized void broadcastToGroup(String groupName, String message, ClientHandler sender) {
        Set<ClientHandler> group = groups.get(groupName);

        if (group != null) {
            for (ClientHandler client : group) {
                client.sendMessage("[" + groupName + "] " + sender.getClientName() + ": " + message);
            }
        } else {
            sender.sendMessage("Group '" + groupName + "' does not exist.");
        }
    }

    public static void main(String[] args) throws IOException {
        Server server = new Server(1234);
        server.start();
    }
}

class ClientHandler extends Thread {
    private Socket clientSocket;
    private PrintWriter out;
    private BufferedReader in;
    private String clientName;
    private Server server;
    private Set<String> myGroups = new HashSet<>();

    public ClientHandler(Socket socket, Server server) {
        this.clientSocket = socket;
        this.server = server;
    }

    public String getClientName() {
        return clientName;
    }

    public void addGroup(String groupName) {
        myGroups.add(groupName);
        sendMessage("/groupadded " + groupName);
    }

    public void removeGroup(String groupName) {
        myGroups.remove(groupName);
        sendMessage("/groupremoved " + groupName);
    }

    public boolean isInGroup(String groupName) {
        return myGroups.contains(groupName);
    }

    public void sendMessage(String message) {
        out.println(message);
    }

    public void run() {
        try {
            out = new PrintWriter(clientSocket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

            out.println("Please enter your name: ");
            clientName = in.readLine();
            if (clientName == null || clientName.trim().isEmpty()) {
                return; // Nếu không có tên hợp lệ, thoát luồng
            }
            server.registerClient(clientName, this);
            System.out.println(clientName + " has joined the server.");

            String message;
            while ((message = in.readLine()) != null) {
                String[] tokens = message.split(" ", 2);
                String command = tokens[0];

                if (command.equalsIgnoreCase("/create")) {
                    String groupName = tokens[1];
                    server.createGroup(groupName, this);
                } else if (command.equalsIgnoreCase("/add")) {
                    String[] params = tokens[1].split(" ");
                    String groupName = params[0];
                    String userName = params[1];
                    server.addClientToGroup(groupName, userName, this);
                } else if (command.equalsIgnoreCase("/remove")) {
                    String[] params = tokens[1].split(" ");
                    String groupName = params[0];
                    String userName = params[1];
                    server.removeClientFromGroup(groupName, userName, this);
                } else if (command.startsWith("/group")) {
                    String[] params = tokens[1].split(" ", 2);
                    String groupName = params[0];
                    String groupMessage = params[1];
                    if (isInGroup(groupName)) {
                        server.broadcastToGroup(groupName, groupMessage, this);
                    } else {
                        sendMessage("You are not part of this group.");
                    }
                } else {
                    System.out.println(clientName + ": " + message);
                }

                if (message.equalsIgnoreCase("exit")) {
                    break;
                }
            }

            server.unregisterClient(clientName);
            in.close();
            out.close();
            clientSocket.close();
            System.out.println(clientName + " has left the chat.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
