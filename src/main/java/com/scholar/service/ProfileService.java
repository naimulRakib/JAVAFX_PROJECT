package com.scholar.service;

import com.scholar.model.Profile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.*;
import java.util.UUID;

/**
 * ProfileService — plain JDBC, PostgreSQL compatible.
 * Uses AuthService.CURRENT_USER_ID (UUID static field).
 * Path: src/main/java/com/scholar/service/ProfileService.java
 */
@Service
public class ProfileService {

    @Autowired
    private DataSource dataSource;

    private Connection connect() throws SQLException {
        return dataSource.getConnection();
    }

    // ─────────────────────────────────────────────────────────
    //  READ
    // ─────────────────────────────────────────────────────────

    public Profile getMyProfile() {
        UUID uid = AuthService.CURRENT_USER_ID;
        if (uid == null) throw new IllegalStateException("No user logged in.");

        String sql = "SELECT * FROM profiles WHERE user_id = ?";
        try (Connection con = connect();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setObject(1, uid);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return mapRow(rs);
        } catch (SQLException e) {
            System.err.println("❌ getMyProfile error: " + e.getMessage());
        }

        // No row yet — return blank profile
        Profile blank = new Profile();
        blank.setUserId(uid);
        return blank;
    }

    /** Returns null if PRIVATE or not found. */
    public Profile getPublicProfile(UUID userId) {
        String sql = "SELECT * FROM profiles WHERE user_id = ? AND profile_visibility = 'PUBLIC'";
        try (Connection con = connect();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setObject(1, userId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return mapRow(rs);
        } catch (SQLException e) {
            System.err.println("❌ getPublicProfile error: " + e.getMessage());
        }
        return null;
    }

    // ─────────────────────────────────────────────────────────
    //  SAVE — Basic Identity
    // ─────────────────────────────────────────────────────────

    public void saveBasicInfo(String fullName, String studentId,
                              String university, String department,
                              String batchSemester, String personalEmail,
                              String phone, boolean phonePublic) {
        UUID uid = requireUid();
        // PostgreSQL upsert syntax
        String sql = """
            INSERT INTO profiles
              (user_id, full_name, student_id, university_name, department,
               batch_semester, personal_email, phone, phone_public)
            VALUES (?,?,?,?,?,?,?,?,?)
            ON CONFLICT (user_id) DO UPDATE SET
              full_name       = EXCLUDED.full_name,
              student_id      = EXCLUDED.student_id,
              university_name = EXCLUDED.university_name,
              department      = EXCLUDED.department,
              batch_semester  = EXCLUDED.batch_semester,
              personal_email  = EXCLUDED.personal_email,
              phone           = EXCLUDED.phone,
              phone_public    = EXCLUDED.phone_public
            """;
        try (Connection con = connect();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setObject(1, uid);
            ps.setString (2, fullName);
            ps.setString (3, studentId);
            ps.setString (4, university);
            ps.setString (5, department);
            ps.setString (6, batchSemester);
            ps.setString (7, personalEmail);
            ps.setString (8, phone);
            ps.setBoolean(9, phonePublic);
            ps.executeUpdate();
            System.out.println("✅ Basic info saved for: " + uid);
        } catch (SQLException e) {
            System.err.println("❌ saveBasicInfo error: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    // ─────────────────────────────────────────────────────────
    //  SAVE — Profile Picture  (PostgreSQL uses BYTEA, not BLOB)
    // ─────────────────────────────────────────────────────────

    public void saveProfilePicture(byte[] imageBytes, String mimeType) {
        UUID uid = requireUid();
        String sql = """
            INSERT INTO profiles (user_id, profile_picture, profile_picture_type)
            VALUES (?,?,?)
            ON CONFLICT (user_id) DO UPDATE SET
              profile_picture      = EXCLUDED.profile_picture,
              profile_picture_type = EXCLUDED.profile_picture_type
            """;
        try (Connection con = connect();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setObject(1, uid);
            ps.setBytes (2, imageBytes);        // BYTEA — just setBytes, no Blob needed
            ps.setString(3, mimeType);
            ps.executeUpdate();
            System.out.println("✅ Picture saved for: " + uid);
        } catch (SQLException e) {
            System.err.println("❌ saveProfilePicture error: " + e.getMessage());
            throw new RuntimeException(e);
        }
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
              current_courses    = EXCLUDED.current_courses
            """;
        try (Connection con = connect();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setObject(1, uid);
            if (cgpa != null) ps.setBigDecimal(2, cgpa);
            else              ps.setNull(2, Types.DECIMAL);
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
              skill_levels_json = EXCLUDED.skill_levels_json
            """;
        try (Connection con = connect();
             PreparedStatement ps = con.prepareStatement(sql)) {
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
              achievements_json = EXCLUDED.achievements_json
            """;
        try (Connection con = connect();
             PreparedStatement ps = con.prepareStatement(sql)) {
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
              leetcode_id   = EXCLUDED.leetcode_id
            """;
        try (Connection con = connect();
             PreparedStatement ps = con.prepareStatement(sql)) {
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
              language           = EXCLUDED.language
            """;
        try (Connection con = connect();
             PreparedStatement ps = con.prepareStatement(sql)) {
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

        // Native PostgreSQL UUID — JDBC returns UUID object directly via getObject()
        Object rawUid = rs.getObject("user_id");
        if (rawUid instanceof UUID) p.setUserId((UUID) rawUid);
        else if (rawUid != null) p.setUserId(UUID.fromString(rawUid.toString()));

        // A. Basic
        p.setFullName      (rs.getString ("full_name"));
        p.setStudentId     (rs.getString ("student_id"));
        p.setUniversityName(rs.getString ("university_name"));
        p.setDepartment    (rs.getString ("department"));
        p.setBatchSemester (rs.getString ("batch_semester"));
        p.setPersonalEmail (rs.getString ("personal_email"));
        p.setPhone         (rs.getString ("phone"));
        p.setPhonePublic   (rs.getBoolean("phone_public"));

        // Avatar — PostgreSQL BYTEA → getBytes()
        byte[] pic = rs.getBytes("profile_picture");
        if (pic != null && pic.length > 0) {
            p.setProfilePicture    (pic);
            p.setProfilePictureType(rs.getString("profile_picture_type"));
        }

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
    //  GUARD
    // ─────────────────────────────────────────────────────────

    private UUID requireUid() {
        UUID uid = AuthService.CURRENT_USER_ID;
        if (uid == null) throw new IllegalStateException("❌ No user logged in.");
        return uid;
    }
}