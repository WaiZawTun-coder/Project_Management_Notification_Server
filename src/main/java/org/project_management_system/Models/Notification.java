package org.project_management_system.Models;

import java.time.LocalDateTime;
import java.util.List;

public class Notification {
    private int notificationId;
    private Integer senderId;
    private String type;          // TASK_ASSIGNED, COMMENT, DEADLINE, OTHER
    private String title;
    private String message;
    private Integer projectId;
    private String priority;      // LOW, MEDIUM, HIGH
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Optional action metadata
    private String actionLabel;
    private String actionUrl;

    // Recipients list (mapped from notification_recipients)
    private List<NotificationRecipient> recipients;

    public Notification(Integer senderId, String type, String title, String message, Integer projectId, String priority, String actionLabel, String actionUrl) {
        this.senderId = senderId;
        this.type = type;
        this.title = title;
        this.message = message;
        this.projectId = projectId;
        this.priority = priority;
        this.actionLabel = actionLabel;
        this.actionUrl = actionUrl;
    }

    public Notification(int notificationId, Integer senderId, String type, String title, String message, Integer projectId, String priority, LocalDateTime createdAt, LocalDateTime updatedAt, String actionLabel, String actionUrl) {
        this.notificationId = notificationId;
        this.senderId = senderId;
        this.type = type;
        this.title = title;
        this.message = message;
        this.projectId = projectId;
        this.priority = priority;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.actionLabel = actionLabel;
        this.actionUrl = actionUrl;
    }

    // Getters and Setters
    public int getNotificationId() {
        return notificationId;
    }

    public void setNotificationId(int notificationId) {
        this.notificationId = notificationId;
    }

    public Integer getSenderId() {
        return senderId;
    }

    public void setSenderId(Integer senderId) {
        this.senderId = senderId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Integer getProjectId() {
        return projectId;
    }

    public void setProjectId(Integer projectId) {
        this.projectId = projectId;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getActionLabel() {
        return actionLabel;
    }

    public void setActionLabel(String actionLabel) {
        this.actionLabel = actionLabel;
    }

    public String getActionUrl() {
        return actionUrl;
    }

    public void setActionUrl(String actionUrl) {
        this.actionUrl = actionUrl;
    }

    public List<NotificationRecipient> getRecipients() {
        return recipients;
    }

    public void setRecipients(List<NotificationRecipient> recipients) {
        this.recipients = recipients;
    }
}
