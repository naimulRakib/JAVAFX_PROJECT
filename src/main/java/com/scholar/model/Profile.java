package com.scholar.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Profile model — plain Java getters/setters, no Lombok.
 * user_id is UUID (matches AuthService.CURRENT_USER_ID).
 * Path: src/main/java/com/scholar/model/Profile.java
 */
public class Profile {

    // ── Enums ─────────────────────────────────────────────
    public enum Visibility { PUBLIC, PRIVATE }
    public enum Theme      { LIGHT, DARK, SYSTEM }
    public enum FontSize   { SMALL, MEDIUM, LARGE }
    public enum Lang       { EN, BN }

    // ── Fields ────────────────────────────────────────────
    private Long id;
    private UUID userId;   // ← UUID, matches your users.id column

    // A. Basic Identity
    private String  fullName;
    private String  studentId;
    private String  universityName;
    private String  department;
    private String  batchSemester;
    private String  personalEmail;
    private String  phone;
    private boolean phonePublic       = false;
    private byte[]  profilePicture;
    private String  profilePictureType;

    // B. Academic
    private BigDecimal cgpa;
    private boolean    cgpaVisible     = true;
    private int        completedCredits = 0;
    private String     thesisTitle;
    private String     academicInterests; // comma-separated
    private String     currentCourses;    // comma-separated

    // C. Skills (comma-separated)
    private String progLanguages;
    private String frameworksTools;
    private String softSkills;
    private String skillLevelsJson;    // {"Java":"Advanced","Python":"Intermediate"}

    // D. Achievements
    private String achievementsJson;   // JSON array string

    // E. Social Links
    private String githubUrl;
    private String linkedinUrl;
    private String portfolioUrl;
    private String codeforcesId;
    private String leetcodeId;

    // Settings
    private Visibility profileVisibility = Visibility.PUBLIC;
    private Theme      theme             = Theme.DARK;
    private String     accentColor       = "#6C63FF";
    private FontSize   fontSize          = FontSize.MEDIUM;
    private Lang       language          = Lang.EN;

    // Timestamps
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // ── Constructor ───────────────────────────────────────
    public Profile() {}

    // ── Computed ──────────────────────────────────────────
    /** Returns 0–100 completion percentage for the badge. */
    public int completionPercent() {
        int score = 0, total = 10;
        if (notBlank(fullName))         score++;
        if (notBlank(studentId))        score++;
        if (notBlank(universityName))   score++;
        if (notBlank(department))       score++;
        if (profilePicture != null && profilePicture.length > 0) score++;
        if (cgpa != null)               score++;
        if (notBlank(progLanguages))    score++;
        if (notBlank(githubUrl))        score++;
        if (notBlank(achievementsJson) && !achievementsJson.equals("[]")) score++;
        if (notBlank(thesisTitle))      score++;
        return (score * 100) / total;
    }

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    // ── Getters & Setters ─────────────────────────────────

    public Long getId()                    { return id; }
    public void setId(Long id)             { this.id = id; }

    public UUID getUserId()                { return userId; }
    public void setUserId(UUID userId)     { this.userId = userId; }

    public String getFullName()            { return fullName; }
    public void setFullName(String v)      { this.fullName = v; }

    public String getStudentId()           { return studentId; }
    public void setStudentId(String v)     { this.studentId = v; }

    public String getUniversityName()             { return universityName; }
    public void setUniversityName(String v)       { this.universityName = v; }

    public String getDepartment()          { return department; }
    public void setDepartment(String v)    { this.department = v; }

    public String getBatchSemester()              { return batchSemester; }
    public void setBatchSemester(String v)        { this.batchSemester = v; }

    public String getPersonalEmail()              { return personalEmail; }
    public void setPersonalEmail(String v)        { this.personalEmail = v; }

    public String getPhone()               { return phone; }
    public void setPhone(String v)         { this.phone = v; }

    public boolean isPhonePublic()         { return phonePublic; }
    public void setPhonePublic(boolean v)  { this.phonePublic = v; }

    public byte[] getProfilePicture()             { return profilePicture; }
    public void setProfilePicture(byte[] v)       { this.profilePicture = v; }

    public String getProfilePictureType()         { return profilePictureType; }
    public void setProfilePictureType(String v)   { this.profilePictureType = v; }

    public BigDecimal getCgpa()            { return cgpa; }
    public void setCgpa(BigDecimal v)      { this.cgpa = v; }

    public boolean isCgpaVisible()         { return cgpaVisible; }
    public void setCgpaVisible(boolean v)  { this.cgpaVisible = v; }

    public int getCompletedCredits()              { return completedCredits; }
    public void setCompletedCredits(int v)        { this.completedCredits = v; }

    public String getThesisTitle()         { return thesisTitle; }
    public void setThesisTitle(String v)   { this.thesisTitle = v; }

    public String getAcademicInterests()          { return academicInterests; }
    public void setAcademicInterests(String v)    { this.academicInterests = v; }

    public String getCurrentCourses()             { return currentCourses; }
    public void setCurrentCourses(String v)       { this.currentCourses = v; }

    public String getProgLanguages()              { return progLanguages; }
    public void setProgLanguages(String v)        { this.progLanguages = v; }

    public String getFrameworksTools()            { return frameworksTools; }
    public void setFrameworksTools(String v)      { this.frameworksTools = v; }

    public String getSoftSkills()          { return softSkills; }
    public void setSoftSkills(String v)    { this.softSkills = v; }

    public String getSkillLevelsJson()            { return skillLevelsJson; }
    public void setSkillLevelsJson(String v)      { this.skillLevelsJson = v; }

    public String getAchievementsJson()           { return achievementsJson; }
    public void setAchievementsJson(String v)     { this.achievementsJson = v; }

    public String getGithubUrl()           { return githubUrl; }
    public void setGithubUrl(String v)     { this.githubUrl = v; }

    public String getLinkedinUrl()         { return linkedinUrl; }
    public void setLinkedinUrl(String v)   { this.linkedinUrl = v; }

    public String getPortfolioUrl()        { return portfolioUrl; }
    public void setPortfolioUrl(String v)  { this.portfolioUrl = v; }

    public String getCodeforcesId()        { return codeforcesId; }
    public void setCodeforcesId(String v)  { this.codeforcesId = v; }

    public String getLeetcodeId()          { return leetcodeId; }
    public void setLeetcodeId(String v)    { this.leetcodeId = v; }

    public Visibility getProfileVisibility()          { return profileVisibility; }
    public void setProfileVisibility(Visibility v)    { this.profileVisibility = v; }

    public Theme getTheme()                { return theme; }
    public void setTheme(Theme v)          { this.theme = v; }

    public String getAccentColor()         { return accentColor; }
    public void setAccentColor(String v)   { this.accentColor = v; }

    public FontSize getFontSize()          { return fontSize; }
    public void setFontSize(FontSize v)    { this.fontSize = v; }

    public Lang getLanguage()              { return language; }
    public void setLanguage(Lang v)        { this.language = v; }

    public LocalDateTime getCreatedAt()           { return createdAt; }
    public void setCreatedAt(LocalDateTime v)     { this.createdAt = v; }

    public LocalDateTime getUpdatedAt()           { return updatedAt; }
    public void setUpdatedAt(LocalDateTime v)     { this.updatedAt = v; }
}