package org.project_management_system.Server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.project_management_system.Adapter.LocalDateTimeAdapter;
import org.project_management_system.Database.DB;
import org.project_management_system.Models.Notification;
import org.project_management_system.Models.NotificationRecipient;

import java.net.InetSocketAddress;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class NotificationServer extends WebSocketServer {
//    private final Gson gson = new Gson();
    private final Gson gson = new GsonBuilder().registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter()).create();
    int sender_id;

    private final Map<Integer, Set<WebSocket>> userConnections = new ConcurrentHashMap<>();

    public NotificationServer(int port){
        super(new InetSocketAddress(port));
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake){
        System.out.println("Connection opened: " + conn.getRemoteSocketAddress());
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote){
        userConnections.values().forEach(set -> set.remove(conn));
        System.out.println("Connection closed: " + conn.getRemoteSocketAddress());
    }

    @Override
    public void onMessage(WebSocket conn, String message){
//        Map<String, Object> data = gson.fromJson(message, type);
        Map<String, Object> data = gson.fromJson(message, Map.class);
        String type = (String) data.get("type");
        conn.getAttachment();

        if("register".equals(type)){
            int userId = ((Double) data.get("userId")).intValue();
            sender_id = userId;
            if(userConnections.containsKey(userId)){
                String payload = gson.toJson(Map.of("type", "already_registered"));
                pushToUser(userId, payload);
            }
            userConnections.computeIfAbsent(userId, k -> new HashSet<>()).add(conn);
            getAllNotifications(userId);
            System.out.println("User " + userId + " registered for notifications.");
            System.out.println("Connections: " + userConnections.get(userId).size());
        }else if("markRead".equals(type)){
            int userId = ((Double) data.get("userId")).intValue();
            long notificationId = ((Double) data.get("notificationId")).longValue();
            markAsRead(userId, notificationId);
        }else if("push".equals(type)){
            System.out.println("new notification");
            List<Double> rawRecipients = (List<Double>) data.get("recipients");
            List<Integer> recipients = rawRecipients.stream().map(Double::intValue).toList();
            String title = (String) data.get("title");
            String msg = (String) data.get("message");
            String url = (String) data.get("url");
            String notificationType = (String) data.get("notificationType");
            int projectId = data.get("projectId") != null ? ((Double) data.get("projectId")).intValue() : 0;
            String priority = (String) data.get("priority");
            String actionLabel = (String) data.get("actionLabel");

            createNotification(sender_id, notificationType, title, msg, projectId, priority, actionLabel, url, recipients);
        }else if("fetch_unread".equals(type)){
            if(data.get("userId") != null) {
                int userId = ((Double) data.get("userId")).intValue();
                getUnreadNotifications(userId);
            }
        }else if("getAll".equals(type)){
            if(data.get("userId") != null){
                int userId = ((Double) data.get("userId")).intValue();
                getAllNotifications(userId);
            }
        }
    }

    @Override
    public void onError(WebSocket conn, Exception err){
        System.err.println("Error: " + err.getLocalizedMessage());
        err.printStackTrace();
    }

    @Override
    public void onStart(){
        System.out.println("Notification server started on port: " + getPort());
    }

    // database connection logic
    public Notification createNotification(Integer senderId, String type, String title, String message, Integer projectId, String priority, String actionLabel, String actionUrl, List<Integer> recipients) {
        Connection con = null;
        try {
            con = DB.connect();
            con.setAutoCommit(false);  // start transaction

            int notificationId;

            // 1. Insert notification
            String insertNotificationSQL =
                    "INSERT INTO notifications (sender_id, type, title, message, project_id, priority, action_label, action_url) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

            try (PreparedStatement stmt = con.prepareStatement(insertNotificationSQL, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setObject(1, senderId);
                stmt.setString(2, type);
                stmt.setString(3, title);
                stmt.setString(4, message);
                stmt.setObject(5, projectId);
                stmt.setString(6, priority);
                stmt.setString(7, actionLabel);
                stmt.setString(8, actionUrl);
                stmt.executeUpdate();

                try (ResultSet rs = stmt.getGeneratedKeys()) {
                    if (!rs.next()) throw new SQLException("No notification ID returned!");
                    notificationId = rs.getInt(1);
                }
            }

            // 2. Insert recipients
            String insertRecipientSQL =
                    "INSERT INTO notification_recipients (notification_id, recipient_id, status) VALUES (?, ?, 'UNREAD')";

            try (PreparedStatement stmt = con.prepareStatement(insertRecipientSQL)) {
                for (Integer userId : recipients) {
                    stmt.setInt(1, notificationId);
                    stmt.setInt(2, userId);
                    stmt.addBatch();
                }
                stmt.executeBatch();
            }

            // ✅ commit only if both inserts succeed
            con.commit();

            // 3. Build Notification object
            Notification notification = new Notification(
                    senderId,
                    type,
                    title,
                    message,
                    projectId,
                    priority,
                    actionLabel,
                    actionUrl
            );
            notification.setNotificationId(notificationId);
            notification.setCreatedAt(java.time.LocalDateTime.now());
            notification.setUpdatedAt(java.time.LocalDateTime.now());

            List<NotificationRecipient> recipientObjects = new ArrayList<>();
            for (Integer userId : recipients) {
                NotificationRecipient nr = new NotificationRecipient();
                nr.setNotificationId(notificationId);
                nr.setRecipientId(userId);
                nr.setStatus("UNREAD");
                nr.setReadAt(null);
                recipientObjects.add(nr);

                // Push to user
                pushToUser(userId, notification, "notification");
            }
            notification.setRecipients(recipientObjects);

            return notification;

        } catch (SQLException err) {
            System.err.println("Error creating notification: " + err.getMessage());
            try {
                con.rollback(); // ✅ rollback safely on same connection
            } catch (SQLException rollbackErr) {
                System.err.println("Rollback failed: " + rollbackErr.getMessage());
            }
        } finally {
            if (con != null) {
                try {
                    con.setAutoCommit(true); // ✅ restore default mode
                    con.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        }

        return null;
    }

    private void pushToUser(int userId, String payload){
        Set<WebSocket> connections = userConnections.get(userId);
        if(connections != null){
            System.out.println("Found " + connections.size() + " connections for user " + userId);
            for(WebSocket ws : connections){
                System.out.println("Sending to " + ws.getRemoteSocketAddress() + ": " + payload);
                ws.send(payload);
            }
        }
    }

    private void pushToUser(int userId, Notification notification, String type){
        System.out.println("Push notification to user " + userId + ": " + notification);
        String payload = gson.toJson(Map.of("type", type, "notification", notification));
        Set<WebSocket> connections = userConnections.get(userId);
        if(connections != null){
            System.out.println("Found " + connections.size() + " connections for user " + userId);
            for(WebSocket ws : connections){
                System.out.println("Sending to " + ws.getRemoteSocketAddress() + ": " + payload);
                ws.send(payload);
            }
        }
    }

    private void pushToUser(int userId, Notification notification, String type, String status){
        String payload = gson.toJson(Map.of("type", type, "status", status, "notification", notification));
        Set<WebSocket> connections = userConnections.get(userId);
        if(connections != null){
            System.out.println("Found " + connections.size() + " connections for user " + userId);
            for(WebSocket ws : connections){
                System.out.println("Sending to " + ws.getRemoteSocketAddress() + ": " + payload);
                ws.send(payload);
            }
        }
    }

    public void markAsRead(int userId, long notificationId){
        String sql = "UPDATE notification_recipients SET status = 'READ', read_at = CURRENT_TIMESTAMP WHERE recipient_id = ? AND notification_id = ?";
        try(Connection con = DB.connect(); PreparedStatement stmt = con.prepareStatement(sql)){
            stmt.setInt(1, userId);
            stmt.setLong(2, notificationId);
            stmt.executeUpdate();
            System.out.println("Notification " + notificationId + " marked as read by user " + userId);
        }catch(SQLException err){
            System.err.println("Error marking notification as read: "+ err.getLocalizedMessage());
        }
    }

    public void getUnreadNotifications(int userId){
        System.out.println("Fetching unread notifications for user " + userId);
//        List<Notification> notifications = new ArrayList<>();
        String sql = "SELECT * FROM notifications n JOIN notification_recipients un ON n.notification_id = un.notification_id WHERE un.recipient_id = ? AND un.status = 'UNREAD'";
        try(Connection con = DB.connect(); PreparedStatement stmt = con.prepareStatement(sql)){
            stmt.setInt(1, userId);
            try(ResultSet rs = stmt.executeQuery()){
                while(rs.next()){
                    // process each unread notification
                    int notificationId = rs.getInt("notification_id");
                    int senderId = rs.getInt("sender_id");
                    String type = rs.getString("type");
                    String title = rs.getString("title");
                    String message = rs.getString("message");
                    Integer projectId = rs.getInt("project_id");
                    String priority = rs.getString("priority");
                    String actionLabel = rs.getString("action_label");
                    String actionUrl = rs.getString("action_url");
                    Timestamp createdAt = rs.getTimestamp("created_at");
                    Timestamp updatedAt = rs.getTimestamp("updated_at");

                    Notification notification = new Notification(notificationId, senderId, type, title, message, projectId, priority, createdAt.toLocalDateTime(), updatedAt.toLocalDateTime(), actionLabel, actionUrl);
                    System.out.println("notification: " + notification.getTitle());
                    pushToUser(userId, notification, "unread");
                }
            }
        }catch(SQLException err){
            System.err.println("Error fetching unread notifications: "+ err.getLocalizedMessage());
        }
    }

    public void getAllNotifications(int userId){
        String sql = "SELECT n.notification_id, sender_id, type, title, message, project_id, priority, action_label, action_url, created_at, updated_at, un.status FROM notifications n JOIN notification_recipients un ON n.notification_id = un.notification_id WHERE un.recipient_id = ?";
        try(Connection con = DB.connect(); PreparedStatement stmt = con.prepareStatement(sql)){
            stmt.setInt(1, userId);
            try(ResultSet rs = stmt.executeQuery()){
                while(rs.next()){
                    // process each notification
                    int notificationId = rs.getInt("notification_id");
                    int senderId = rs.getInt("sender_id");
                    String type = rs.getString("type");
                    String title = rs.getString("title");
                    String message = rs.getString("message");
                    Integer projectId = rs.getInt("project_id");
                    String priority = rs.getString("priority");
                    String actionLabel = rs.getString("action_label");
                    String actionUrl = rs.getString("action_url");
                    Timestamp createdAt = rs.getTimestamp("created_at");
                    Timestamp updatedAt = rs.getTimestamp("updated_at");
                    String status = rs.getString("status");

                    Notification notification = new Notification(notificationId, senderId, type, title, message, projectId, priority, createdAt.toLocalDateTime(), updatedAt.toLocalDateTime(), actionLabel, actionUrl);
                    // add to list or process as needed
                    pushToUser(userId, notification,"fetch_all", status);
                }
            }
        }catch(SQLException err){
            System.err.println("Error fetching all notifications: " + err.getLocalizedMessage());
        }
    }
}
