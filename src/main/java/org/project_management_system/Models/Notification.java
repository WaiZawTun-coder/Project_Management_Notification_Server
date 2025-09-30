package org.project_management_system.Models;

import java.sql.Timestamp;

public class Notification {
    public long id;
    public String title;
    public String message;
    public String url;
    public boolean isRead;      // read status for THIS user
    public Timestamp readAt;    // when read
    public Timestamp createdAt;

    public Notification(){}

    public Notification(String title, String message, String url){
        this.title = title;
        this.message = message;
        this.url = url;
    }
}
