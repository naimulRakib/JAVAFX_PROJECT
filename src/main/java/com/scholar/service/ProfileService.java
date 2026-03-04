package com.scholar.service;

import com.scholar.model.Profile;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.*;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.sql.Connection;
/**
 * ProfileService — plain JDBC, PostgreSQL + Supabase Storage.
 * Uses AuthService.CURRENT_USER_ID (UUID static field).
 * Path: src/main/java/com/scholar/service/ProfileService.java
 */
@Service
public class ProfileService {

    @Value("${supabase.url}")
    private String supabaseUrl;

    @Value("${supabase.key}")
    private String supabaseKey;

    @Autowired
    private DataSource dataSource;

    // ── Shared OkHttpClient (thread-safe, reuse connections) ──────────────────
    private static final OkHttpClient HTTP_CLIENT = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();

    private Connection connect() throws SQLException {
        return dataSource.getConnection();
    }

    // ─────────────────────────────────────────────────────────
    //  READ: My Profile (JOIN users → profiles)
    // ─────────────────────────────────────────────────────────

    public Profile getMyProfile() {
        UUID uid = requireUid();
        String sql = """
                SELECT p.*, u.username, u.full_name AS u_full_name
                FROM users u
                LEFT JOIN profiles p ON u.id = p.user_id
                WHERE u.id = ?
                """;
        try (Connection con = connect(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setObject(1, uid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        } catch (SQLException e) {
            System.err.println("❌ getMyProfile error: " + e.getMessage());
        }
        Profile blank = new Profile();
        blank.setUserId(uid);
        return blank;
    }

    /** Returns the full profile for any user; respects privacy for non-admin/non-self callers. */
    public Profile getUserProfile(UUID targetUserId) {
        String sql = """
                SELECT p.*, u.username, u.full_name AS u_full_name
                FROM users u
                LEFT JOIN profiles p ON u.id = p.user_id
                WHERE u.id = ?
                """;
        try (Connection con = connect(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setObject(1, targetUserId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Profile p = mapRow(rs);
                    boolean isAdmin = "admin".equals(AuthService.CURRENT_USER_ROLE);
                    boolean isMe    = targetUserId.equals(AuthService.CURRENT_USER_ID);

                    if (p.getProfileVisibility() == Profile.Visibility.PRIVATE && !isAdmin && !isMe) {
                        Profile masked = new Profile();
                        masked.setUsername(p.getUsername());
                        masked.setProfilePictureUrl(p.getProfilePictureUrl());
                        masked.setFullName("Anonymous Scholar");
                        masked.setProfileVisibility(Profile.Visibility.PRIVATE);
                        return masked;
                    }
                    return p;
                }
            }
        } catch (SQLException e) {
            System.err.println("❌ getUserProfile error: " + e.getMessage());
        }
        return null;
    }

    /** Returns null if PRIVATE or not found. */
    public Profile getPublicProfile(UUID userId) {
        String sql = "SELECT p.*, u.username, u.full_name AS u_full_name " +
                     "FROM users u LEFT JOIN profiles p ON u.id = p.user_id " +
                     "WHERE u.id = ? AND (p.profile_visibility = 'PUBLIC' OR p.profile_visibility IS NULL)";
        try (Connection con = connect(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setObject(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        } catch (SQLException e) {
            System.err.println("❌ getPublicProfile error: " + e.getMessage());
        }
        return null;
    }

    // ─────────────────────────────────────────────────────────
    //  SAVE — Basic Info  (atomic: users + profiles)
    // ─────────────────────────────────────────────────────────

    public void saveBasicInfo(String username, String fullName, String studentId,
                              String university, String department,
                              String batchSemester, String personalEmail,
                              String phone, boolean phonePublic) {
        UUID uid = requireUid();
        String updateUsers   = "UPDATE users SET username = ?, full_name = ? WHERE id = ?";
        String upsertProfile = """
                INSERT INTO profiles
                  (user_id, full_name, student_id, university_name, department,
                   batch_semester, personal_email, phone, phone_public)
                VALUES (?,?,?,?,?,?,?,?,?)
                ON CONFLICT (user_id) DO UPDATE SET
                  full_name      = EXCLUDED.full_name,
                  student_id     = EXCLUDED.student_id,
                  university_name= EXCLUDED.university_name,
                  department     = EXCLUDED.department,
                  batch_semester = EXCLUDED.batch_semester,
                  personal_email = EXCLUDED.personal_email,
                  phone          = EXCLUDED.phone,
                  phone_public   = EXCLUDED.phone_public,
                  updated_at     = NOW()
                """;
        try (Connection con = connect()) {
            con.setAutoCommit(false);
            try (PreparedStatement psUser = con.prepareStatement(updateUsers);
                 PreparedStatement psProf = con.prepareStatement(upsertProfile)) {

                psUser.setString(1, (username != null && !username.isBlank()) ? username : null);
                psUser.setString(2, fullName);
                psUser.setObject(3, uid);
                psUser.executeUpdate();

                psProf.setObject(1, uid);
                psProf.setString(2, fullName);
                psProf.setString(3, studentId);
                psProf.setString(4, university);
                psProf.setString(5, department);
                psProf.setString(6, batchSemester);
                psProf.setString(7, personalEmail);
                psProf.setString(8, phone);
                psProf.setBoolean(9, phonePublic);
                psProf.executeUpdate();

                con.commit();
                AuthService.CURRENT_USER_NAME = username;
                System.out.println("✅ Basic info saved for: " + uid);
            } catch (SQLException ex) {
                con.rollback();
                if (ex.getMessage() != null &&
                        (ex.getMessage().contains("unique_username") || ex.getMessage().contains("users_username_key"))) {
                    throw new RuntimeException("That username is already taken. Please choose another nickname.");
                }
                throw ex;
            } finally {
                con.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    // ─────────────────────────────────────────────────────────
    //  SAVE — Profile Picture → Supabase Storage (PUT upsert)
    // ─────────────────────────────────────────────────────────

    /**
     * Uploads image bytes to Supabase Storage bucket "profile_pic" using PUT (upsert).
     * Saves the resulting public URL to the profiles table.
     *
     * @return the public URL of the uploaded image
     */
    public String uploadProfilePictureToSupabase(byte[] imageBytes, String mimeType, String extension) throws Exception {
        UUID uid = requireUid();

        // 🌟 FIX: Extract the base URL safely
        String baseUrl = supabaseUrl;
        if (baseUrl.contains("/rest/v1")) {
            baseUrl = baseUrl.substring(0, baseUrl.indexOf("/rest/v1"));
        }

        // Use a stable, deterministic filename per user so re-uploads overwrite the old file.
        String fileName  = uid.toString() + extension.toLowerCase();
        
        // 🌟 FIX: Use the clean baseUrl for Storage API
        String uploadUrl = baseUrl + "/storage/v1/object/profile_pic/" + fileName;

        RequestBody body = RequestBody.create(imageBytes, MediaType.parse(mimeType));
        Request request = new Request.Builder()
                .url(uploadUrl)
                // PUT with x-upsert:true → creates or replaces, no duplicate key errors
                .put(body)
                .addHeader("Authorization", "Bearer " + supabaseKey)
                .addHeader("apikey",         supabaseKey)
                .addHeader("Content-Type",   mimeType)
                .addHeader("x-upsert",       "true")
                .build();

        try (Response response = HTTP_CLIENT.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "no response body";
                throw new IOException("Supabase upload failed [" + response.code() + "]: " + errorBody);
            }
        }

        // 🌟 FIX: Public URL with clean baseUrl
        String publicUrl = baseUrl + "/storage/v1/object/public/profile_pic/" + fileName;

        // Persist URL to DB
        String sql = """
                INSERT INTO profiles (user_id, profile_picture_url)
                VALUES (?, ?)
                ON CONFLICT (user_id) DO UPDATE SET
                  profile_picture_url = EXCLUDED.profile_picture_url,
                  updated_at          = NOW()
                """;
        try (Connection con = connect(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setObject(1, uid);
            ps.setString(2, publicUrl);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("DB update failed after upload: " + e.getMessage(), e);
        }

        System.out.println("✅ Avatar uploaded for: " + uid + " → " + publicUrl);
        return publicUrl;
    }

    

    // ─────────────────────────────────────────────────────────
    //  SAVE — Academic Info
    // ─────────────────────────────────────────────────────────

    public void saveAcademic(BigDecimal cgpa, boolean cgpaVisible,
                             int credits, String thesisTitle,
                             String academicInterests, String currentCourses) {
        UUID uid = requireUid();
        String sql = """
                INSERT INTO profiles
                  (user_id, cgpa, cgpa_visible, completed_credits, thesis_title,
                   academic_interests, current_courses)
                VALUES (?,?,?,?,?,?,?)
                ON CONFLICT (user_id) DO UPDATE SET
                  cgpa               = EXCLUDED.cgpa,
                  cgpa_visible       = EXCLUDED.cgpa_visible,
                  completed_credits  = EXCLUDED.completed_credits,
                  thesis_title       = EXCLUDED.thesis_title,
                  academic_interests = EXCLUDED.academic_interests,
                  current_courses    = EXCLUDED.current_courses,
                  updated_at         = NOW()
                """;
        try (Connection con = connect(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setObject(1, uid);
            if (cgpa != null) ps.setBigDecimal(2, cgpa);
            else              ps.setNull      (2, Types.DECIMAL);
            ps.setBoolean(3, cgpaVisible);
            ps.setInt    (4, credits);
            ps.setString (5, thesisTitle);
            ps.setString (6, academicInterests);
            ps.setString (7, currentCourses);
            ps.executeUpdate();
            System.out.println("✅ Academic info saved for: " + uid);
        } catch (SQLException e) {
            System.err.println("❌ saveAcademic error: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    // ─────────────────────────────────────────────────────────
    //  SAVE — Skills
    // ─────────────────────────────────────────────────────────

    public void saveSkills(String progLanguages, String frameworksTools,
                           String softSkills, String skillLevelsJson) {
        UUID uid = requireUid();
        String sql = """
                INSERT INTO profiles
                  (user_id, prog_languages, frameworks_tools, soft_skills, skill_levels_json)
                VALUES (?,?,?,?,?)
                ON CONFLICT (user_id) DO UPDATE SET
                  prog_languages    = EXCLUDED.prog_languages,
                  frameworks_tools  = EXCLUDED.frameworks_tools,
                  soft_skills       = EXCLUDED.soft_skills,
                  skill_levels_json = EXCLUDED.skill_levels_json,
                  updated_at        = NOW()
                """;
        try (Connection con = connect(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setObject(1, uid);
            ps.setString(2, progLanguages);
            ps.setString(3, frameworksTools);
            ps.setString(4, softSkills);
            ps.setString(5, skillLevelsJson);
            ps.executeUpdate();
            System.out.println("✅ Skills saved for: " + uid);
        } catch (SQLException e) {
            System.err.println("❌ saveSkills error: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    // ─────────────────────────────────────────────────────────
    //  SAVE — Achievements
    // ─────────────────────────────────────────────────────────

    public void saveAchievements(String achievementsJson) {
        UUID uid = requireUid();
        String sql = """
                INSERT INTO profiles (user_id, achievements_json)
                VALUES (?,?)
                ON CONFLICT (user_id) DO UPDATE SET
                  achievements_json = EXCLUDED.achievements_json,
                  updated_at        = NOW()
                """;
        try (Connection con = connect(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setObject(1, uid);
            ps.setString(2, achievementsJson);
            ps.executeUpdate();
            System.out.println("✅ Achievements saved for: " + uid);
        } catch (SQLException e) {
            System.err.println("❌ saveAchievements error: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    // ─────────────────────────────────────────────────────────
    //  SAVE — Social Links
    // ─────────────────────────────────────────────────────────

    public void saveLinks(String github, String linkedin,
                          String portfolio, String codeforces, String leetcode) {
        UUID uid = requireUid();
        String sql = """
                INSERT INTO profiles
                  (user_id, github_url, linkedin_url, portfolio_url, codeforces_id, leetcode_id)
                VALUES (?,?,?,?,?,?)
                ON CONFLICT (user_id) DO UPDATE SET
                  github_url    = EXCLUDED.github_url,
                  linkedin_url  = EXCLUDED.linkedin_url,
                  portfolio_url = EXCLUDED.portfolio_url,
                  codeforces_id = EXCLUDED.codeforces_id,
                  leetcode_id   = EXCLUDED.leetcode_id,
                  updated_at    = NOW()
                """;
        try (Connection con = connect(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setObject(1, uid);
            ps.setString(2, github);
            ps.setString(3, linkedin);
            ps.setString(4, portfolio);
            ps.setString(5, codeforces);
            ps.setString(6, leetcode);
            ps.executeUpdate();
            System.out.println("✅ Links saved for: " + uid);
        } catch (SQLException e) {
            System.err.println("❌ saveLinks error: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    // ─────────────────────────────────────────────────────────
    //  SAVE — Settings
    // ─────────────────────────────────────────────────────────

    public void saveSettings(Profile.Visibility visibility, Profile.Theme theme,
                             String accentColor, Profile.FontSize fontSize,
                             Profile.Lang language) {
        UUID uid = requireUid();
        String sql = """
                INSERT INTO profiles
                  (user_id, profile_visibility, theme, accent_color, font_size, language)
                VALUES (?,?,?,?,?,?)
                ON CONFLICT (user_id) DO UPDATE SET
                  profile_visibility = EXCLUDED.profile_visibility,
                  theme              = EXCLUDED.theme,
                  accent_color       = EXCLUDED.accent_color,
                  font_size          = EXCLUDED.font_size,
                  language           = EXCLUDED.language,
                  updated_at         = NOW()
                """;
        try (Connection con = connect(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setObject(1, uid);
            ps.setString(2, visibility != null ? visibility.name() : "PUBLIC");
            ps.setString(3, theme      != null ? theme.name()      : "DARK");
            ps.setString(4, accentColor);
            ps.setString(5, fontSize   != null ? fontSize.name()   : "MEDIUM");
            ps.setString(6, language   != null ? language.name()   : "EN");
            ps.executeUpdate();
            System.out.println("✅ Settings saved for: " + uid);
        } catch (SQLException e) {
            System.err.println("❌ saveSettings error: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    // ─────────────────────────────────────────────────────────
    //  ROW MAPPER
    // ─────────────────────────────────────────────────────────

    private Profile mapRow(ResultSet rs) throws SQLException {
        Profile p = new Profile();

        // UUID — works with both native UUID and string columns
        Object rawUid = rs.getObject("user_id");
        if (rawUid instanceof UUID u) p.setUserId(u);
        else if (rawUid != null)      p.setUserId(UUID.fromString(rawUid.toString()));

        // Username comes from the users table JOIN
        safeSet(rs, "username",  v -> p.setUsername(v));

        // Profile picture URL (Supabase) — primary avatar source
        safeSet(rs, "profile_picture_url", v -> p.setProfilePictureUrl(v));

        // full_name: prefer profiles.full_name, fall back to users.full_name
        String profileName = safeGet(rs, "full_name");
        String usersName   = safeGet(rs, "u_full_name");
        p.setFullName(profileName != null ? profileName : usersName);

        // A. Basic
        p.setStudentId     (rs.getString ("student_id"));
        p.setUniversityName(rs.getString ("university_name"));
        p.setDepartment    (rs.getString ("department"));
        p.setBatchSemester (rs.getString ("batch_semester"));
        p.setPersonalEmail (rs.getString ("personal_email"));
        p.setPhone         (rs.getString ("phone"));
        p.setPhonePublic   (rs.getBoolean("phone_public"));

        // B. Academic
        BigDecimal cgpa = rs.getBigDecimal("cgpa");
        p.setCgpa            (rs.wasNull() ? null : cgpa);
        p.setCgpaVisible     (rs.getBoolean("cgpa_visible"));
        p.setCompletedCredits(rs.getInt    ("completed_credits"));
        p.setThesisTitle     (rs.getString ("thesis_title"));
        p.setAcademicInterests(rs.getString("academic_interests"));
        p.setCurrentCourses  (rs.getString ("current_courses"));

        // C. Skills
        p.setProgLanguages  (rs.getString("prog_languages"));
        p.setFrameworksTools(rs.getString("frameworks_tools"));
        p.setSoftSkills     (rs.getString("soft_skills"));
        p.setSkillLevelsJson(rs.getString("skill_levels_json"));

        // D. Achievements
        p.setAchievementsJson(rs.getString("achievements_json"));

        // E. Links
        p.setGithubUrl   (rs.getString("github_url"));
        p.setLinkedinUrl (rs.getString("linkedin_url"));
        p.setPortfolioUrl(rs.getString("portfolio_url"));
        p.setCodeforcesId(rs.getString("codeforces_id"));
        p.setLeetcodeId  (rs.getString("leetcode_id"));

        // Settings
        String vis = rs.getString("profile_visibility");
        p.setProfileVisibility(vis != null ? Profile.Visibility.valueOf(vis) : Profile.Visibility.PUBLIC);
        String thm = rs.getString("theme");
        p.setTheme(thm != null ? Profile.Theme.valueOf(thm) : Profile.Theme.DARK);
        p.setAccentColor(rs.getString("accent_color"));
        String fs = rs.getString("font_size");
        p.setFontSize(fs != null ? Profile.FontSize.valueOf(fs) : Profile.FontSize.MEDIUM);
        String lang = rs.getString("language");
        p.setLanguage(lang != null ? Profile.Lang.valueOf(lang) : Profile.Lang.EN);

        // Timestamps
        Timestamp ca = rs.getTimestamp("created_at");
        if (ca != null) p.setCreatedAt(ca.toLocalDateTime());
        Timestamp ua = rs.getTimestamp("updated_at");
        if (ua != null) p.setUpdatedAt(ua.toLocalDateTime());

        return p;
    }

    // ─────────────────────────────────────────────────────────
    //  HELPERS
    // ─────────────────────────────────────────────────────────

    /** Silently reads a nullable string column — returns null if column missing from result set. */
    private String safeGet(ResultSet rs, String col) {
        try { return rs.getString(col); } catch (SQLException ignored) { return null; }
    }

    /** Silently sets a nullable string column via a consumer. */
    private void safeSet(ResultSet rs, String col, java.util.function.Consumer<String> setter) {
        try {
            String v = rs.getString(col);
            if (v != null) setter.accept(v);
        } catch (SQLException ignored) {}
    }

    // ─────────────────────────────────────────────────────────
    //  GUARD
    // ─────────────────────────────────────────────────────────

    private UUID requireUid() {
        UUID uid = AuthService.CURRENT_USER_ID;
        if (uid == null) throw new IllegalStateException("❌ No user logged in.");
        return uid;
    }
}