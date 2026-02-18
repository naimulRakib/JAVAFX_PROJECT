package com.scholar;

import io.github.cdimascio.dotenv.Dotenv;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class DatabaseTester {
    public static void main(String[] args) {
        System.out.println("⏳ Testing Connection to Supabase...");

        try {
            Dotenv dotenv = Dotenv.load();
            String url = dotenv.get("DB_URL");
            String user = dotenv.get("DB_USER");
            String pass = dotenv.get("DB_PASSWORD");

            System.out.println("URL: " + url);
            // Don't print password!

            try (Connection conn = DriverManager.getConnection(url, user, pass)) {
                System.out.println("✅ Connection Established!");

                // Try to Insert a Fake User
                String sql = "INSERT INTO users (email, password) VALUES (?, ?)";
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setString(1, "test_user_" + System.currentTimeMillis() + "@test.com");
                    pstmt.setString(2, "password123");
                    
                    int rows = pstmt.executeUpdate();
                    if (rows > 0) {
                        System.out.println("✅ INSERT SUCCESS! Check your 'users' table in Table Editor.");
                    } else {
                        System.err.println("❌ Insert ran but no rows affected.");
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("❌ SQL ERROR: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("❌ CONFIG ERROR: " + e.getMessage());
        }
    }
}