package org.project_management_system.Database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DB {
    private static final String JDBC_URL = "jdbc:mysql://localhost:3306/project_management_system";
    private static final String DB_USER = "project_management_user";
    private static final String DB_PASSWORD = "project_management_password";

    static Connection con = null;

    public static Connection connect(){
        try{
            Class.forName("com.mysql.cj.jdbc.Driver");
            con = DriverManager.getConnection(JDBC_URL, DB_USER, DB_PASSWORD);
            System.out.println("Success: Connected to database successful.");
        }catch(ClassNotFoundException | SQLException err){
            System.err.println("Error: " + err.getLocalizedMessage());
        }

        return con;
    }

    public static void disconnect(){
        try{
            if(con != null && !con.isClosed()){
                con.close();
                System.out.println("Success: Disconnected from database successful.");
            }
        }catch(SQLException err){
            System.err.println("Error: unable to disconnect from database.");
        }
    }
}
