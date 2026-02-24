package com.scholar.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * ChannelMergeService — Full backend logic for channel merging system.
 *
 * Capabilities:
 *  - Save / load per-channel merge settings (allow_merge, privacy_mode)
 *  - Discover channels available for merging
 *  - Send / reject / accept merge requests (TEMPORARY or PERMANENT)
 *  - Multi-merge: one hub can hold N channels simultaneously
 *  - Instant unmerge: any admin leaves at any time → hub auto-destroys if ≤1 channel left
 *  - User copy respects ANONYMOUS flag per channel setting
 *
 * SQL Prerequisites (already applied):
 *   ALTER TABLE channels ADD COLUMN IF NOT EXISTS is_merged_hub BOOLEAN DEFAULT false;
 *   ALTER TABLE channel_members ADD COLUMN IF NOT EXISTS source_channel_id INT;
 *   ALTER TABLE channel_members ADD COLUMN IF NOT EXISTS is_anonymous BOOLEAN DEFAULT false;
 *   CREATE TABLE IF NOT EXISTS channel_settings (...);
 *   CREATE TABLE IF NOT EXISTS merge_requests (...);
 *   CREATE TABLE IF NOT EXISTS merged_hubs (...);
 */
@Service
public class ChannelMergeService {

    @Autowired
    private DataSource dataSource;

    // ═══════════════════════════════════════════════════════════════
    // 1.  SETTINGS — save & load
    // ═══════════════════════════════════════════════════════════════

    /**
     * Save (upsert) merge settings for a channel.
     * @param channelId    the admin's channel
     * @param allowMerge   whether this channel appears to others as available
     * @param privacy      "PUBLIC" or "ANONYMOUS"
     */
    public boolean updateSettings(int channelId, boolean allowMerge, String privacy) {
        String sql = """
            INSERT INTO channel_settings (channel_id, allow_merge, privacy_mode)
            VALUES (?, ?, ?)
            ON CONFLICT (channel_id)
            DO UPDATE SET allow_merge = EXCLUDED.allow_merge,
                          privacy_mode = EXCLUDED.privacy_mode
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pst = conn.prepareStatement(sql)) {
            pst.setInt(1, channelId);
            pst.setBoolean(2, allowMerge);
            pst.setString(3, privacy);
            return pst.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Load current merge settings for a channel.
     * @return String[2] = { "true"/"false", "PUBLIC"/"ANONYMOUS" }, or null if not set
     */
    public String[] getSettings(int channelId) {
        String sql = "SELECT allow_merge, privacy_mode FROM channel_settings WHERE channel_id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pst = conn.prepareStatement(sql)) {
            pst.setInt(1, channelId);
            ResultSet rs = pst.executeQuery();
            if (rs.next()) {
                return new String[]{
                    String.valueOf(rs.getBoolean("allow_merge")),
                    rs.getString("privacy_mode")
                };
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    // ═══════════════════════════════════════════════════════════════
    // 2.  DISCOVERY — which channels are open for merging?
    // ═══════════════════════════════════════════════════════════════

    /**
     * Get all channels that have allow_merge = true (excluding mine).
     * @return List of String[2]: { channelId, channelName }
     */
    public List<String[]> getAvailableChannels(int myChannelId) {
        List<String[]> list = new ArrayList<>();
        String sql = """
            SELECT c.id, c.name
            FROM channels c
            JOIN channel_settings cs ON c.id = cs.channel_id
            WHERE cs.allow_merge = true
              AND c.id != ?
              AND c.is_merged_hub = false
            ORDER BY c.name
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pst = conn.prepareStatement(sql)) {
            pst.setInt(1, myChannelId);
            ResultSet rs = pst.executeQuery();
            while (rs.next()) {
                list.add(new String[]{ rs.getString("id"), rs.getString("name") });
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    // ═══════════════════════════════════════════════════════════════
    // 3.  MERGE REQUESTS — send / list / reject
    // ═══════════════════════════════════════════════════════════════

    /**
     * Send a merge request from my channel to another channel.
     * @param senderId   my channel id
     * @param receiverId target channel id
     * @param type       "TEMPORARY" or "PERMANENT"
     * @param days       duration in days (ignored when PERMANENT)
     */
    public boolean sendMergeRequest(int senderId, int receiverId, String type, int days) {
        // Prevent duplicate pending requests
        String checkSql = """
            SELECT id FROM merge_requests
            WHERE sender_channel_id = ? AND receiver_channel_id = ? AND status = 'PENDING'
            """;
        try (Connection conn = dataSource.getConnection()) {
            try (PreparedStatement chk = conn.prepareStatement(checkSql)) {
                chk.setInt(1, senderId);
                chk.setInt(2, receiverId);
                ResultSet rs = chk.executeQuery();
                if (rs.next()) return false; // already sent
            }

            String sql = """
                INSERT INTO merge_requests
                    (sender_channel_id, receiver_channel_id, merge_type, duration_days)
                VALUES (?, ?, ?, ?)
                """;
            try (PreparedStatement pst = conn.prepareStatement(sql)) {
                pst.setInt(1, senderId);
                pst.setInt(2, receiverId);
                pst.setString(3, type);
                if ("TEMPORARY".equals(type) && days > 0) pst.setInt(4, days);
                else pst.setNull(4, Types.INTEGER);
                return pst.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Get all pending merge requests sent TO myChannelId.
     * @return List of String[4]: { reqId, senderChannelName, mergeType, durationDays }
     */
    public List<String[]> getPendingRequests(int myChannelId) {
        List<String[]> reqs = new ArrayList<>();
        String sql = """
            SELECT r.id, c.name, r.merge_type, r.duration_days
            FROM merge_requests r
            JOIN channels c ON r.sender_channel_id = c.id
            WHERE r.receiver_channel_id = ? AND r.status = 'PENDING'
            ORDER BY r.created_at DESC
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pst = conn.prepareStatement(sql)) {
            pst.setInt(1, myChannelId);
            ResultSet rs = pst.executeQuery();
            while (rs.next()) {
                reqs.add(new String[]{
                    rs.getString("id"),
                    rs.getString("name"),
                    rs.getString("merge_type"),
                    rs.getString("duration_days") // may be null for PERMANENT
                });
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return reqs;
    }

    /**
     * Reject a pending merge request (marks it as REJECTED).
     */
    public boolean rejectMergeRequest(int reqId) {
        String sql = "UPDATE merge_requests SET status = 'REJECTED' WHERE id = ? AND status = 'PENDING'";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pst = conn.prepareStatement(sql)) {
            pst.setInt(1, reqId);
            return pst.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 4.  ACCEPT MERGE — creates hub, adds both channels, copies users
    // ═══════════════════════════════════════════════════════════════

    /**
     * Accept an incoming merge request.
     *
     * Flow:
     *   1. Load the request (sender + receiver + type + days).
     *   2. If receiver is NOT already a hub → create a brand-new hub channel.
     *      Add receiver's users into it first.
     *   3. If receiver IS already a hub → reuse it.
     *   4. Add sender's users into the hub.
     *   5. Both original admins get admin role in the hub.
     *   6. Mark request as ACCEPTED.
     *
     * @param reqId       merge_requests.id
     * @param newHubName  desired name for the new merged hub (used only if hub is new)
     * @param acceptingAdminId  UUID of the admin who clicked Accept
     * @return "SUCCESS" or an error message
     */
    public String acceptMergeRequest(int reqId, String newHubName, UUID acceptingAdminId) {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // ── Step 1: Load request ─────────────────────────────────
                int senderId = -1, receiverId = -1, durationDays = 0;
                String mergeType = "";

                try (PreparedStatement pst = conn.prepareStatement(
                        "SELECT * FROM merge_requests WHERE id = ? AND status = 'PENDING'")) {
                    pst.setInt(1, reqId);
                    ResultSet rs = pst.executeQuery();
                    if (rs.next()) {
                        senderId    = rs.getInt("sender_channel_id");
                        receiverId  = rs.getInt("receiver_channel_id");
                        mergeType   = rs.getString("merge_type");
                        durationDays = rs.getInt("duration_days"); // 0 if null/PERMANENT
                    } else {
                        conn.rollback();
                        return "REQUEST_NOT_FOUND";
                    }
                }

                // ── Step 2: Decide if we create a new hub or reuse existing ─
                boolean isReceiverAlreadyHub = false;
                try (PreparedStatement pst = conn.prepareStatement(
                        "SELECT is_merged_hub FROM channels WHERE id = ?")) {
                    pst.setInt(1, receiverId);
                    ResultSet rs = pst.executeQuery();
                    if (rs.next()) isReceiverAlreadyHub = rs.getBoolean(1);
                }

                int targetHubId;

                if (!isReceiverAlreadyHub) {
                    // Create a new hub channel
                    targetHubId = createHubChannel(conn, newHubName, acceptingAdminId);
                    if (targetHubId < 0) { conn.rollback(); return "FAILED_TO_CREATE_HUB"; }

                    // Add receiver (the accepting side) into the new hub
                    addChannelToHub(conn, targetHubId, receiverId, mergeType, durationDays);

                    // Make the accepting admin an admin of the hub too
                    promoteAdminToHub(conn, targetHubId, receiverId);
                } else {
                    // Receiver is already a hub → add sender directly
                    targetHubId = receiverId;
                }

                // ── Step 3: Add sender channel into the hub ──────────────
                addChannelToHub(conn, targetHubId, senderId, mergeType, durationDays);

                // Make sender's admin an admin in the hub
                promoteAdminToHub(conn, targetHubId, senderId);

                // ── Step 4: Mark request as accepted ─────────────────────
                try (PreparedStatement pst = conn.prepareStatement(
                        "UPDATE merge_requests SET status = 'ACCEPTED' WHERE id = ?")) {
                    pst.setInt(1, reqId);
                    pst.executeUpdate();
                }

                conn.commit();
                return "SUCCESS";

            } catch (SQLException e) {
                conn.rollback();
                return "DB_ERROR: " + e.getMessage();
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            return "CONNECTION_ERROR: " + e.getMessage();
        }
    }

    /** Create a new merged hub channel. Returns new hub's channel id, or -1 on failure. */
    private int createHubChannel(Connection conn, String hubName, UUID createdBy) throws SQLException {
        String sql = """
            INSERT INTO channels (name, unique_code, created_by, is_merged_hub)
            VALUES (?, ?, ?::uuid, true)
            RETURNING id
            """;
        try (PreparedStatement pst = conn.prepareStatement(sql)) {
            pst.setString(1, hubName);
            pst.setString(2, "HUB-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase());
            pst.setString(3, createdBy.toString());
            ResultSet rs = pst.executeQuery();
            if (rs.next()) return rs.getInt(1);
        }
        return -1;
    }

    /**
     * Copy all approved users from originalChannelId into hubId,
     * tagging them with source_channel_id and respecting the channel's privacy setting.
     * Also records the merge in merged_hubs table.
     */
    private void addChannelToHub(Connection conn, int hubId, int originalChannelId,
                                  String mergeType, int durationDays) throws SQLException {
        // ── Track in merged_hubs ────────────────────────────────────
        Timestamp expiresAt = null;
        if ("TEMPORARY".equals(mergeType) && durationDays > 0) {
            expiresAt = new Timestamp(System.currentTimeMillis() + ((long) durationDays * 86_400_000L));
        }

        String trackSql = """
            INSERT INTO merged_hubs (hub_channel_id, original_channel_id, expires_at)
            VALUES (?, ?, ?)
            ON CONFLICT DO NOTHING
            """;
        try (PreparedStatement pst = conn.prepareStatement(trackSql)) {
            pst.setInt(1, hubId);
            pst.setInt(2, originalChannelId);
            if (expiresAt != null) pst.setTimestamp(3, expiresAt);
            else pst.setNull(3, Types.TIMESTAMP);
            pst.executeUpdate();
        }

        // ── Resolve privacy setting ──────────────────────────────────
        boolean isAnonymous = false;
        String privSql = "SELECT privacy_mode FROM channel_settings WHERE channel_id = ?";
        try (PreparedStatement pst = conn.prepareStatement(privSql)) {
            pst.setInt(1, originalChannelId);
            ResultSet rs = pst.executeQuery();
            if (rs.next() && "ANONYMOUS".equals(rs.getString("privacy_mode"))) {
                isAnonymous = true;
            }
        }

        // ── Copy approved users into hub ─────────────────────────────
        // Skip if already copied (re-accept guard)
        String copySql = """
            INSERT INTO channel_members (user_id, channel_id, role, status, is_anonymous, source_channel_id)
            SELECT cm.user_id, ?, cm.role, 'approved', ?, ?
            FROM channel_members cm
            WHERE cm.channel_id = ? AND cm.status = 'approved'
            ON CONFLICT (user_id, channel_id) DO NOTHING
            """;
        try (PreparedStatement pst = conn.prepareStatement(copySql)) {
            pst.setInt(1, hubId);
            pst.setBoolean(2, isAnonymous);
            pst.setInt(3, originalChannelId);
            pst.setInt(4, originalChannelId);
            pst.executeUpdate();
        }
    }

    /**
     * Find the admin of originalChannelId and give them admin role in hubId.
     * This implements "both admins become admin of the merged hub."
     */
    private void promoteAdminToHub(Connection conn, int hubId, int originalChannelId) throws SQLException {
        String sql = """
            INSERT INTO channel_members (user_id, channel_id, role, status, source_channel_id)
            SELECT cm.user_id, ?, 'admin', 'approved', ?
            FROM channel_members cm
            WHERE cm.channel_id = ? AND cm.role = 'admin' AND cm.status = 'approved'
            ON CONFLICT (user_id, channel_id) DO UPDATE SET role = 'admin'
            """;
        try (PreparedStatement pst = conn.prepareStatement(sql)) {
            pst.setInt(1, hubId);
            pst.setInt(2, originalChannelId);
            pst.setInt(3, originalChannelId);
            pst.executeUpdate();
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 5.  ACTIVE HUBS — list & instant unmerge
    // ═══════════════════════════════════════════════════════════════

    /**
     * Get all hubs that originalChannelId is currently a member of.
     * @return List of String[3]: { hubChannelId, hubName, expiresAt (nullable) }
     */
    public List<String[]> getMyActiveHubs(int originalChannelId) {
        List<String[]> hubs = new ArrayList<>();
        String sql = """
            SELECT mh.hub_channel_id, c.name,
                   TO_CHAR(mh.expires_at, 'YYYY-MM-DD HH24:MI') AS expires_str
            FROM merged_hubs mh
            JOIN channels c ON mh.hub_channel_id = c.id
            WHERE mh.original_channel_id = ?
            ORDER BY mh.joined_at DESC
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pst = conn.prepareStatement(sql)) {
            pst.setInt(1, originalChannelId);
            ResultSet rs = pst.executeQuery();
            while (rs.next()) {
                hubs.add(new String[]{
                    rs.getString("hub_channel_id"),
                    rs.getString("name"),
                    rs.getString("expires_str") // null = PERMANENT
                });
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return hubs;
    }

    /**
     * Instantly remove myChannelId from a merged hub.
     *
     * Steps:
     *   1. Delete all users from the hub whose source_channel_id = myChannelId.
     *   2. Delete the tracking row in merged_hubs.
     *   3. Count remaining channels in the hub.
     *   4. If ≤ 1 remain → destroy the hub entirely (CASCADE removes members).
     *
     * @param hubId            the merged hub's channel id
     * @param originalChannelId the channel leaving the hub
     */
    public boolean instantUnmerge(int hubId, int originalChannelId) {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // ── Remove this channel's users from hub ─────────────
                try (PreparedStatement pst = conn.prepareStatement(
                        "DELETE FROM channel_members WHERE channel_id = ? AND source_channel_id = ?")) {
                    pst.setInt(1, hubId);
                    pst.setInt(2, originalChannelId);
                    pst.executeUpdate();
                }

                // ── Remove tracking row ──────────────────────────────
                try (PreparedStatement pst = conn.prepareStatement(
                        "DELETE FROM merged_hubs WHERE hub_channel_id = ? AND original_channel_id = ?")) {
                    pst.setInt(1, hubId);
                    pst.setInt(2, originalChannelId);
                    pst.executeUpdate();
                }

                // ── Check remaining participants ─────────────────────
                int remaining = 0;
                try (PreparedStatement pst = conn.prepareStatement(
                        "SELECT COUNT(*) FROM merged_hubs WHERE hub_channel_id = ?")) {
                    pst.setInt(1, hubId);
                    ResultSet rs = pst.executeQuery();
                    if (rs.next()) remaining = rs.getInt(1);
                }

                // ── Auto-destroy hub if only 0 or 1 channel left ─────
                if (remaining <= 1) {
                    // Delete remaining tracking rows first
                    try (PreparedStatement pst = conn.prepareStatement(
                            "DELETE FROM merged_hubs WHERE hub_channel_id = ?")) {
                        pst.setInt(1, hubId);
                        pst.executeUpdate();
                    }
                    // Delete all hub members
                    try (PreparedStatement pst = conn.prepareStatement(
                            "DELETE FROM channel_members WHERE channel_id = ?")) {
                        pst.setInt(1, hubId);
                        pst.executeUpdate();
                    }
                    // Delete the hub channel itself
                    try (PreparedStatement pst = conn.prepareStatement(
                            "DELETE FROM channels WHERE id = ? AND is_merged_hub = true")) {
                        pst.setInt(1, hubId);
                        pst.executeUpdate();
                    }
                }

                conn.commit();
                return true;
            } catch (SQLException e) {
                e.printStackTrace();
                conn.rollback();
                return false;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
}