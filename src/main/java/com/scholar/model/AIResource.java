package com.scholar.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Model class for AI-generated learning resources.
 * Supports rich text (HTML), Markdown, plain text, LaTeX, and code blocks.
 */
public class AIResource {

    private int id;
    private String title;
    private String description;

    // Content in multiple formats
    private String content;           // Plain text
    private String contentHtml;       // HTML
    private String contentMarkdown;   // Markdown

    // Supabase cloud storage
    private String supabaseFileUrl;   // Public download URL
    private String supabaseFileId;    // Filename used as storage key

    // Legacy Telegram fields (keep for backward compat)
    private String telegramFileId;
    private String telegramDownloadUrl;

    private String pdfPath;           // Local temp path after PDF generation

    // Metadata
    private String resourceType;
    private String subject;
    private String difficultyLevel;
    private String tags;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private UUID createdByUserId;
    private boolean isPublished;
    private int viewCount;
    private int downloadCount;

    // Rich-text feature flags
    private boolean hasLatex;
    private boolean hasCodeBlocks;
    private String contentFormat;  // "html" | "markdown" | "plain"

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm");

    // ── Constructors ─────────────────────────────────────────────────────────

    public AIResource() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.resourceType = "AI_LESSON";
        this.difficultyLevel = "MEDIUM";
        this.isPublished = false;
        this.viewCount = 0;
        this.downloadCount = 0;
        this.contentFormat = "html";
        this.hasLatex = false;
        this.hasCodeBlocks = false;
    }

    public AIResource(String title, String content, String subject) {
        this();
        this.title = title;
        this.content = content;
        this.subject = subject;
    }

    // ── Getters / Setters ────────────────────────────────────────────────────

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getContentHtml() { return contentHtml; }
    public void setContentHtml(String contentHtml) { this.contentHtml = contentHtml; }

    public String getContentMarkdown() { return contentMarkdown; }
    public void setContentMarkdown(String contentMarkdown) { this.contentMarkdown = contentMarkdown; }

    public String getSupabaseFileUrl() { return supabaseFileUrl; }
    public void setSupabaseFileUrl(String supabaseFileUrl) { this.supabaseFileUrl = supabaseFileUrl; }

    public String getSupabaseFileId() { return supabaseFileId; }
    public void setSupabaseFileId(String supabaseFileId) { this.supabaseFileId = supabaseFileId; }

    public String getTelegramFileId() { return telegramFileId; }
    public void setTelegramFileId(String telegramFileId) { this.telegramFileId = telegramFileId; }

    public String getTelegramDownloadUrl() { return telegramDownloadUrl; }
    public void setTelegramDownloadUrl(String telegramDownloadUrl) { this.telegramDownloadUrl = telegramDownloadUrl; }

    public String getPdfPath() { return pdfPath; }
    public void setPdfPath(String pdfPath) { this.pdfPath = pdfPath; }

    public String getResourceType() { return resourceType; }
    public void setResourceType(String resourceType) { this.resourceType = resourceType; }

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    public String getDifficultyLevel() { return difficultyLevel; }
    public void setDifficultyLevel(String difficultyLevel) { this.difficultyLevel = difficultyLevel; }

    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public UUID getCreatedByUserId() { return createdByUserId; }
    public void setCreatedByUserId(UUID createdByUserId) { this.createdByUserId = createdByUserId; }

    public boolean isPublished() { return isPublished; }
    public void setPublished(boolean published) { isPublished = published; }

    public int getViewCount() { return viewCount; }
    public void setViewCount(int viewCount) { this.viewCount = viewCount; }

    public int getDownloadCount() { return downloadCount; }
    public void setDownloadCount(int downloadCount) { this.downloadCount = downloadCount; }

    public boolean isHasLatex() { return hasLatex; }
    public void setHasLatex(boolean hasLatex) { this.hasLatex = hasLatex; }

    public boolean isHasCodeBlocks() { return hasCodeBlocks; }
    public void setHasCodeBlocks(boolean hasCodeBlocks) { this.hasCodeBlocks = hasCodeBlocks; }

    public String getContentFormat() { return contentFormat; }
    public void setContentFormat(String contentFormat) { this.contentFormat = contentFormat; }

    // ── Utility ──────────────────────────────────────────────────────────────

    public String getFormattedCreatedAt() {
        return createdAt != null ? createdAt.format(FORMATTER) : "";
    }

    public String getFormattedUpdatedAt() {
        return updatedAt != null ? updatedAt.format(FORMATTER) : "";
    }

    public String getShortDescription() {
        if (description == null || description.length() <= 100) return description;
        return description.substring(0, 97) + "...";
    }

    /** Returns true if there is any cloud download URL available. */
    public boolean hasDownloadLink() {
        return (supabaseFileUrl != null && !supabaseFileUrl.isBlank()) ||
               (telegramDownloadUrl != null && !telegramDownloadUrl.isBlank());
    }

    /** Prefers Supabase URL; falls back to Telegram. */
    public String getPrimaryDownloadUrl() {
        if (supabaseFileUrl != null && !supabaseFileUrl.isBlank()) return supabaseFileUrl;
        return telegramDownloadUrl;
    }

    public String getDifficultyEmoji() {
        if (difficultyLevel == null) return "⚪";
        return switch (difficultyLevel.toUpperCase()) {
            case "EASY"   -> "🟢";
            case "MEDIUM" -> "🟡";
            case "HARD"   -> "🔴";
            default       -> "⚪";
        };
    }

    public String getSubjectEmoji() {
        if (subject == null) return "📚";
        return switch (subject.toLowerCase()) {
            case "mathematics"       -> "🔢";
            case "physics"           -> "⚛️";
            case "chemistry"         -> "🧪";
            case "biology"           -> "🧬";
            case "computer science"  -> "💻";
            case "english"           -> "📖";
            case "history"           -> "📜";
            default                  -> "📚";
        };
    }

    /** Auto-detect LaTeX markers in any available content field. */
    public void detectLatex() {
        String check = bestContentForDetection();
        if (check != null) {
            this.hasLatex = check.contains("$") ||
                            check.contains("\\[") ||
                            check.contains("\\(");
        }
    }

    /** Auto-detect code-block markers in any available content field. */
    public void detectCodeBlocks() {
        String check = bestContentForDetection();
        if (check != null) {
            this.hasCodeBlocks = check.contains("```") ||
                                 check.contains("<code>") ||
                                 check.contains("<pre>");
        }
    }

    /** Returns the richest non-null content string for feature detection. */
    private String bestContentForDetection() {
        if (contentHtml     != null && !contentHtml.isBlank())     return contentHtml;
        if (contentMarkdown != null && !contentMarkdown.isBlank()) return contentMarkdown;
        return content;
    }

    @Override
    public String toString() {
        return "AIResource{id=" + id +
               ", title='" + title + '\'' +
               ", subject='" + subject + '\'' +
               ", difficulty='" + difficultyLevel + '\'' +
               ", published=" + isPublished +
               ", format='" + contentFormat + '\'' +
               ", hasLatex=" + hasLatex +
               ", hasCode=" + hasCodeBlocks + '}';
    }
}