package org.project_management_system.Server;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.project_management_system.Database.DB;
import org.project_management_system.Models.Notification;

import java.lang.reflect.Type;
import java.net.InetSocketAddress;
import java.sql.*;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class NotificationServer extends WebSocketServer {
    private final Gson gson = new Gson();
    String message = "{\"type\":\"value\", \"number\": 123}";
    Type type = new TypeToken<Map<String, Object>>() {}.getType();

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
            userConnections.computeIfAbsent(userId, k -> new HashSet<>()).add(conn);
            System.out.println("User " + userId + " registered for notifications.");
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
            createNotification(recipients, title, msg, url);
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
    public Notification createNotification(List<Integer> recipients, String title, String message, String url){
        try(Connection con = DB.connect()){
            con.setAutoCommit(false);

            long notificationId;
            String insertNotificationSQL = "INSERT INTO notifications (title, message, url) VALUES (?, ?, ?)";

            try(PreparedStatement stmt = con.prepareStatement(insertNotificationSQL, Statement.RETURN_GENERATED_KEYS)){
                stmt.setString(1, title);
                stmt.setString(2, message);
                stmt.setString(3, url);
                stmt.executeUpdate();

                try(ResultSet rs = stmt.getGeneratedKeys()){
                    if(!rs.next()) throw new SQLException("No notification ID returned!");
                    notificationId = rs.getLong(1);
                }
            }

            String insertUserNotificationSQL = "INSERT INTO user_notifications (notification_id, user_id) VALUES (?, ?)";
            try(PreparedStatement stmt = con.prepareStatement(insertUserNotificationSQL)){
                for(Integer userId: recipients){
                    System.out.println("userId: " + userId);
                    stmt.setLong(1, notificationId);
                    stmt.setInt(2, userId);
                    stmt.addBatch();
                }

                stmt.executeBatch();
            }

            con.commit();

            Notification notification = new Notification();
            notification.id = notificationId;
            notification.title = title;
            notification.message = message;
            notification.url = url;
            notification.isRead = false;
            notification.createdAt = new Timestamp(System.currentTimeMillis());

            for(Integer userId: recipients){
                pushToUser(userId, notification);
            }

            return notification;
        }catch(SQLException err){
            System.err.println("Error creating notification: " + err.getLocalizedMessage());
        }

        return null;
    }

    private void pushToUser(int userId, Notification notification){
        String payload = gson.toJson(Map.of("type", "notification", "notification", notification));
        Set<WebSocket> connections = userConnections.get(userId);
        if(connections != null){
            for(WebSocket ws : connections){
                ws.send(payload);
            }
        }
    }

    public void markAsRead(int userId, long notificationId){
        String sql = "UPDATE user_notifications SET is_read = 1, read_at = CURRENT_TIMESTAMP WHERE user_id = ? AND notification_id = ?";
        try(Connection con = DB.connect(); PreparedStatement stmt = con.prepareStatement(sql)){
            stmt.setInt(1, userId);
            stmt.setLong(2, notificationId);
            stmt.executeUpdate();
            System.out.println("Notification " + notificationId + " marked as read by user " + userId);
        }catch(SQLException err){
            System.err.println("Error marking notification as read: "+ err.getLocalizedMessage());
        }
    }
}
