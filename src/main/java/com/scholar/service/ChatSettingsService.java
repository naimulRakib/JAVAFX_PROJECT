package com.scholar.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * ChatSettingsService
 * Manages platform-wide chat settings stored in the `chat_settings` table.
 *
 * Path: src/main/java/com/scholar/service/ChatSettingsService.java
 */
@Service
public class ChatSettingsService {

    private static final String KEY_PUBLIC_CHAT = "public_chat_enabled";

    @Autowired
    private DataService dataService;

    // ----------------------------------------------------------
    // READ
    // ----------------------------------------------------------

    /**
     * Returns true if public group chat is currently enabled.
     * Defaults to true if the setting row is missing.
     */
    public boolean isPublicChatEnabled() {
        String sql = "SELECT value FROM chat_settings WHERE key = ?";
        try (Connection conn = dataService.connect();
             PreparedStatement p = conn.prepareStatement(sql)) {
            p.setString(1, KEY_PUBLIC_CHAT);
            ResultSet rs = p.executeQuery();
            if (rs.next()) {
                return "true".equalsIgnoreCase(rs.getString("value"));
            }
        } catch (SQLException e) {
            System.err.println("❌ ChatSettingsService.isPublicChatEnabled: " + e.getMessage());
        }
        return true; // safe default — chat is ON
    }

    // ----------------------------------------------------------
    // WRITE
    // ----------------------------------------------------------

    /**
     * Enables or disables public group chat platform-wide.
     *
     * @param enabled true to enable, false to disable
     * @return true if the update succeeded
     */
    public boolean setPublicChatEnabled(boolean enabled) {
        String sql = """
            INSERT INTO chat_settings (key, value, updated_at)
            VALUES (?, ?, NOW())
            ON CONFLICT (key) DO UPDATE
                SET value      = EXCLUDED.value,
                    updated_at = NOW()
            """;
        try (Connection conn = dataService.connect();
             PreparedStatement p = conn.prepareStatement(sql)) {
            p.setString(1, KEY_PUBLIC_CHAT);
            p.setString(2, enabled ? "true" : "false");
            boolean ok = p.executeUpdate() > 0;
            if (ok) {
                System.out.println("✅ Public chat " + (enabled ? "ENABLED" : "DISABLED") + " by admin.");
            }
            return ok;
        } catch (SQLException e) {
            System.err.println("❌ ChatSettingsService.setPublicChatEnabled: " + e.getMessage());
            return false;
        }
    }
}