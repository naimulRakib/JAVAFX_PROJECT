package com.scholar.service;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import io.github.cdimascio.dotenv.Dotenv;

public class ResourceService {


    private static final String DB_URL = Dotenv.load().get("DB_URL");
    // ✅ UPDATED RECORD: Now includes 'content'
    public record Resource(int id, String title, String link, String type, String content) {}

    // ✅ UPDATED METHOD: Now accepts 4 arguments (including 'content')
    public boolean addResource(String title, String link, String type, String content) {
        // We insert 4 values now
        String sql = "INSERT INTO resources (title, link, type, content) VALUES (?, ?, ?, ?)";
        
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, title);
            pstmt.setString(2, link);
            pstmt.setString(3, type);
            pstmt.setString(4, content); // Save the AI Note
            
            pstmt.executeUpdate();
            return true;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public List<Resource> getAllResources() {
        List<Resource> list = new ArrayList<>();
        String sql = "SELECT * FROM resources ORDER BY created_at DESC";
        
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            
            while (rs.next()) {
                list.add(new Resource(
                    rs.getInt("id"),
                    rs.getString("title"),
                    rs.getString("link"),
                    rs.getString("type"),
                    rs.getString("content") // Read the AI Note
                ));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }
}