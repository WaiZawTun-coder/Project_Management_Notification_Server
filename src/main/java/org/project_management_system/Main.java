package org.project_management_system;

import org.project_management_system.Server.NotificationServer;

import java.util.Arrays;

public class Main {
    public static void main(String[] args) throws InterruptedException {
        NotificationServer server = new NotificationServer(8080);
        server.start();

        System.out.println("Server running on ws://localhost:8080");

        Thread.sleep(5000);
//        server.createNotification(Arrays.asList(1, 2), "Welcome!", "This is a test notification.", null);
    }
}