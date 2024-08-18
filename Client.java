import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.util.*;

public class Client {
    private JFrame frame;
    private JTextArea chatArea;
    private JTextField inputField;
    private JButton sendButton, createGroupButton, addToGroupButton, removeFromGroupButton;
    private JList<String> userList, groupList;
    private DefaultListModel<String> userListModel, groupListModel;
    private Socket clientSocket;
    private PrintWriter out;
    private BufferedReader in;
    private String clientName;
    private Set<String> ownedGroups = new HashSet<>();

    public Client(String host, int port) throws IOException {
        // Thiết lập kết nối đến server
        clientSocket = new Socket(host, port);
        out = new PrintWriter(clientSocket.getOutputStream(), true);
        in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

        // Giao diện GUI
        frame = new JFrame("Chat Client");
        chatArea = new JTextArea();
        chatArea.setEditable(false);
        inputField = new JTextField();
        sendButton = new JButton("Send");
        createGroupButton = new JButton("Create Group");
        addToGroupButton = new JButton("Add to Group");
        removeFromGroupButton = new JButton("Remove from Group");

        userListModel = new DefaultListModel<>();
        userList = new JList<>(userListModel);

        groupListModel = new DefaultListModel<>();
        groupList = new JList<>(groupListModel);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JScrollPane(userList), new JScrollPane(chatArea));
        splitPane.setDividerLocation(150);

        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(inputField, BorderLayout.CENTER);
        bottomPanel.add(sendButton, BorderLayout.EAST);

        JPanel groupPanel = new JPanel(new BorderLayout());
        groupPanel.add(new JScrollPane(groupList), BorderLayout.CENTER);

        JPanel groupButtonsPanel = new JPanel();
        groupButtonsPanel.setLayout(new BoxLayout(groupButtonsPanel, BoxLayout.Y_AXIS));
        groupButtonsPanel.add(createGroupButton);
        groupButtonsPanel.add(addToGroupButton);
        groupButtonsPanel.add(removeFromGroupButton);
        groupPanel.add(groupButtonsPanel, BorderLayout.SOUTH);

        JSplitPane mainSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, splitPane, groupPanel);
        mainSplitPane.setDividerLocation(400);

        frame.setLayout(new BorderLayout());
        frame.add(mainSplitPane, BorderLayout.CENTER);
        frame.add(bottomPanel, BorderLayout.SOUTH);

        frame.setSize(600, 400);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);

        // Xử lý sự kiện khi nhấn nút "Send"
        sendButton.addActionListener(e -> sendMessage());

        // Xử lý sự kiện khi nhấn Enter trong JTextField
        inputField.addActionListener(e -> sendMessage());

        // Xử lý sự kiện khi nhấn nút "Create Group"
        createGroupButton.addActionListener(e -> createGroup());

        // Xử lý sự kiện khi nhấn nút "Add to Group"
        addToGroupButton.addActionListener(e -> addToGroup());

        // Xử lý sự kiện khi nhấn nút "Remove from Group"
        removeFromGroupButton.addActionListener(e -> removeFromGroup());

        // Bắt đầu client
        start();
    }

    private void sendMessage() {
        String message = inputField.getText();
        if (!message.isEmpty()) {
            String selectedGroup = groupList.getSelectedValue();
            if (selectedGroup != null) {
                out.println("/group " + selectedGroup + " " + message);
            } else {
                out.println(message);
            }
            inputField.setText("");
        }
    }

    private void createGroup() {
        // Yêu cầu server tạo nhóm, nhưng không thêm nhóm vào danh sách ngay
        String groupName = JOptionPane.showInputDialog(frame, "Enter group name:");
        if (groupName != null && !groupName.trim().isEmpty()) {
            out.println("/create " + groupName);
            ownedGroups.add(groupName);  // Đánh dấu client là chủ nhóm
        }
    }

    private void addToGroup() {
        String selectedGroup = groupList.getSelectedValue();
        String selectedUser = userList.getSelectedValue();

        if (selectedGroup != null && selectedUser != null) {
            out.println("/add " + selectedGroup + " " + selectedUser);
        } else {
            JOptionPane.showMessageDialog(frame, "Please select both a group and a user.");
        }
    }

    private void removeFromGroup() {
        String selectedGroup = groupList.getSelectedValue();
        String selectedUser = userList.getSelectedValue();

        if (selectedGroup != null && selectedUser != null) {
            out.println("/remove " + selectedGroup + " " + selectedUser);
        } else {
            JOptionPane.showMessageDialog(frame, "Please select both a group and a user.");
        }
    }

    public void start() throws IOException {
        // Nhận yêu cầu nhập tên từ server
        String welcomeMessage = in.readLine();
        chatArea.append(welcomeMessage + "\n");

        clientName = JOptionPane.showInputDialog(frame, "Enter your name:");
        if (clientName == null || clientName.trim().isEmpty()) {
            return; // Nếu không có tên hợp lệ, thoát khỏi client
        }
        out.println(clientName);

        // Thread để nhận tin nhắn từ server
        new Thread(() -> {
            try {
                String response;
                while ((response = in.readLine()) != null) {
                    if (response.startsWith("/users")) {
                        // Cập nhật danh sách người dùng
                        updateUserList(response.substring(7).split(" "));
                    } else if (response.startsWith("/groupadded")) {
                        // Nhận phản hồi từ server để thêm nhóm vào danh sách nhóm
                        String groupName = response.split(" ")[1];
                        groupListModel.addElement(groupName);
                    } else if (response.startsWith("/groupremoved")) {
                        // Xóa nhóm khỏi danh sách nhóm
                        String groupName = response.split(" ")[1];
                        groupListModel.removeElement(groupName);
                    } else {
                        displayMessage(response);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void displayMessage(String message) {
        // Giả sử định dạng tin nhắn từ server là: "[group] sender: message"
        // Tách các thành phần của tin nhắn
        int colonIndex = message.indexOf(": ");
        if (colonIndex != -1) {
            String sender = message.substring(0, colonIndex);
            String content = message.substring(colonIndex + 2);

            // Kiểm tra nếu sender là chính người dùng thì hiển thị là "Tôi"
            if (sender.equals(clientName)) {
                chatArea.append("Tôi: " + content + "\n");
            } else {
                chatArea.append(sender + ": " + content + "\n");
            }
        } else {
            chatArea.append(message + "\n");
        }
    }

    private void updateUserList(String[] users) {
        userListModel.clear();
        for (String user : users) {
            if (user.equals(clientName)) {
                userListModel.addElement("Tôi");
            } else {
                userListModel.addElement(user);
            }
        }
    }

    public static void main(String[] args) {
        try {
            Client client = new Client("localhost", 1234);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
