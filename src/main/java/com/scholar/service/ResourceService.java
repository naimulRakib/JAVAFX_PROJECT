package com.scholar.service;


import org.springframework.stereotype.Service;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

@Service
public class ResourceService {

    public record Resource(int id, String title, String link, String type, String content) {}

    public boolean addResource(String title, String link, String type, String content) {
        String sql = "INSERT INTO resources (title, link, type, content) VALUES (?, ?, ?, ?)";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, title);
            pstmt.setString(2, link);
            pstmt.setString(3, type);
            pstmt.setString(4, content);

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

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                list.add(new Resource(
                    rs.getInt("id"),
                    rs.getString("title"),
                    rs.getString("link"),
                    rs.getString("type"),
                    rs.getString("content")
                ));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }
}