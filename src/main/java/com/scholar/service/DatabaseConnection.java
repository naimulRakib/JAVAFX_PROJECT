package com.scholar.service;

import io.github.cdimascio.dotenv.Dotenv;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseConnection {

    private static final String DB_URL;
    private static final String DB_USER;
    private static final String DB_PASSWORD;
    
    // 🚀 SPEED FIX: Keep one connection open!
    private static Connection instance = null;

    static {
        try {
            Dotenv dotenv = Dotenv.load();
            // Ensure you use port 5432 and sslmode=require in your .env!
            DB_URL = dotenv.get("DB_URL");
            DB_USER = dotenv.get("DB_USER");
            DB_PASSWORD = dotenv.get("DB_PASSWORD");
        } catch (Exception e) {
            System.err.println("❌ ERROR: Could not load .env file.");
            throw new RuntimeException(e);
        }
    }

    public static synchronized Connection getConnection() {
        try {
            // Re-use connection if valid (FASTER)
            if (instance != null && !instance.isClosed()) {
                return instance;
            }
            
            // Otherwise, reconnect
            // System.out.println("🔄 Establishing new DB connection...");
            instance = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
            return instance;
            
        } catch (SQLException e) {
            System.err.println("❌ DB CONNECTION FAILED: " + e.getMessage());
            return null; // Handle null in Service!
        }
    }
}