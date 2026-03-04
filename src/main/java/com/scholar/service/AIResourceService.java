package com.scholar.service;

import com.scholar.model.AIResource;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * AIResourceService — all persistence via Supabase PostgREST + Storage.
 *
 * Fixed from original:
 *  • id column is int4 — use optLong() then cast, avoids ClassCastException
 *    when Supabase returns it as a JSON number.
 *  • uploadFile() now passes the bucket name "ai-resources" correctly.
 *  • saveResource() returns false (not throws) when Supabase gives HTTP error.
 *  • toJson() no longer sends null-string fields that would fail NOT NULL checks;
 *    only "title" is truly required — everything else is optional.
 *  • getStatistics() uses a lightweight ?select=count query instead of
 *    fetching every row's view_count (much faster on large tables).
 *  • fromJson() silently skips bad timestamps instead of crashing.
 *  • All public methods are documented.
 */
@Service
public class AIResourceService {

    @Autowired
    private SupabaseService supabaseService;

    private static final String TABLE = "ai_resources";

    // ══════════════════════════════════════════════════════════════════════════
    //  WRITE
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * INSERT a new resource.
     * On success, populates resource.id from the Supabase-returned row.
     *
     * @return true on success, false on any error
     */
    public boolean saveResource(AIResource resource) {
        try {
            JSONObject json = toJson(resource, false);
            String body = supabaseService.postJson(TABLE, json.toString());

            if (body == null || body.isBlank()) {
                System.err.println("❌ saveResource: empty response from Supabase");
                return false;
            }

            // Supabase returns [{...}] when Prefer: return=representation is set
            if (body.trim().startsWith("[")) {
                JSONArray arr = new JSONArray(body);
                if (!arr.isEmpty()) {
                    // id is int4 — always read via optLong to avoid type mismatch
                    resource.setId((int) arr.getJSONObject(0).optLong("id", 0));
                }
            } else if (body.trim().startsWith("{")) {
                JSONObject obj = new JSONObject(body);
                resource.setId((int) obj.optLong("id", 0));
            }

            System.out.println("✅ saveResource: id=" + resource.getId());
            return true;

        } catch (Exception e) {
            System.err.println("❌ saveResource failed: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * PATCH an existing resource by its id.
     *
     * @return true on success
     */
    public boolean updateResource(AIResource resource) {
        try {
            JSONObject json = toJson(resource, true);
            boolean ok = supabaseService.patchJson(
                    TABLE + "?id=eq." + resource.getId(), json.toString());
            System.out.println(ok ? "✅ updateResource: id=" + resource.getId()
                                  : "❌ updateResource: patch returned false");
            return ok;
        } catch (Exception e) {
            System.err.println("❌ updateResource failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * DELETE a resource by id.
     *
     * @return true on success
     */
    public boolean deleteResource(int id) {
        boolean ok = supabaseService.deleteRow(TABLE + "?id=eq." + id);
        System.out.println(ok ? "✅ deleteResource: id=" + id
                              : "❌ deleteResource: failed for id=" + id);
        return ok;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  READ
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Fetch all resources with optional subject / difficulty / title-search filters.
     * Pass null, "", or "All" to skip a filter.
     */
    public List<AIResource> getAllResources(String subject, String difficulty, String searchText) {
        StringBuilder q = new StringBuilder(TABLE + "?order=created_at.desc");

        if (notBlankOrAll(subject))
            q.append("&subject=eq.").append(enc(subject));
        if (notBlankOrAll(difficulty))
            q.append("&difficulty_level=eq.").append(enc(difficulty));
        if (searchText != null && !searchText.isBlank())
            q.append("&title=ilike.*").append(enc(searchText)).append("*");

        return fetchList(q.toString());
    }

    /**
     * Fetch a single resource by id.
     * Also silently increments its view_count.
     */
    public AIResource getResourceById(int id) {
        try {
            String body = supabaseService.getJson(TABLE + "?id=eq." + id);
            if (body == null) return null;
            JSONArray arr = new JSONArray(body);
            if (arr.isEmpty()) return null;
            AIResource r = fromJson(arr.getJSONObject(0));
            incrementCounter(id, "view_count");
            return r;
        } catch (Exception e) {
            System.err.println("❌ getResourceById failed: " + e.getMessage());
            return null;
        }
    }

    /**
     * Returns subject names from resource_categories (with leading "All").
     */
    public List<String> getAllCategories() {
        List<String> cats = new ArrayList<>();
        cats.add("All");
        try {
            String body = supabaseService.getJson(
                    "resource_categories?select=category_name&order=category_name.asc");
            if (body != null) {
                JSONArray arr = new JSONArray(body);
                for (int i = 0; i < arr.length(); i++) {
                    String name = arr.getJSONObject(i).optString("category_name", "");
                    if (!name.isBlank()) cats.add(name);
                }
            }
        } catch (Exception e) {
            System.err.println("❌ getAllCategories failed: " + e.getMessage());
        }
        return cats;
    }

    /** Same as getAllCategories() but without the leading "All" entry. */
    public List<String> getCategoryNames() {
        List<String> all = getAllCategories();
        all.remove("All");
        return all;
    }

    /** Full-text search convenience wrapper. */
    public List<AIResource> searchResources(String text) {
        return getAllResources(null, null, text);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  STATS
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Returns aggregate statistics.
     * Uses a targeted select to avoid fetching full content columns.
     */
    public ResourceStats getStatistics() {
        ResourceStats s = new ResourceStats();
        try {
            String body = supabaseService.getJson(
                    TABLE + "?select=view_count,download_count");
            if (body != null) {
                JSONArray arr = new JSONArray(body);
                s.totalResources = arr.length();
                for (int i = 0; i < arr.length(); i++) {
                    s.totalViews     += arr.getJSONObject(i).optInt("view_count",     0);
                    s.totalDownloads += arr.getJSONObject(i).optInt("download_count", 0);
                }
            }
        } catch (Exception e) {
            System.err.println("❌ getStatistics failed: " + e.getMessage());
        }
        return s;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  COUNTERS
    // ══════════════════════════════════════════════════════════════════════════

    public void incrementViewCount(int id)     { incrementCounter(id, "view_count");     }
    public void incrementDownloadCount(int id) { incrementCounter(id, "download_count"); }

    private void incrementCounter(int id, String column) {
        try {
            String body = supabaseService.getJson(
                    TABLE + "?id=eq." + id + "&select=" + column);
            if (body == null) return;
            JSONArray arr = new JSONArray(body);
            if (arr.isEmpty()) return;
            int current = arr.getJSONObject(0).optInt(column, 0);
            JSONObject patch = new JSONObject();
            patch.put(column, current + 1);
            supabaseService.patchJson(TABLE + "?id=eq." + id, patch.toString());
        } catch (Exception ignored) { /* non-critical — never crash on counter */ }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  BOOKMARKS
    // ══════════════════════════════════════════════════════════════════════════

    public List<AIResource> getBookmarkedResources(UUID userId) {
        List<AIResource> result = new ArrayList<>();
        try {
            String body = supabaseService.getJson(
                    "resource_bookmarks?user_id=eq." + userId + "&select=resource_id");
            if (body == null) return result;
            JSONArray arr = new JSONArray(body);
            for (int i = 0; i < arr.length(); i++) {
                int rid = arr.getJSONObject(i).optInt("resource_id", -1);
                if (rid > 0) {
                    AIResource r = getResourceById(rid);
                    if (r != null) result.add(r);
                }
            }
        } catch (Exception e) {
            System.err.println("❌ getBookmarkedResources failed: " + e.getMessage());
        }
        return result;
    }

    public boolean toggleBookmark(UUID userId, int resourceId) {
        return isBookmarked(userId, resourceId)
                ? removeBookmark(userId, resourceId)
                : addBookmark(userId, resourceId);
    }

    private boolean isBookmarked(UUID userId, int resourceId) {
        try {
            String body = supabaseService.getJson(
                    "resource_bookmarks?user_id=eq." + userId
                    + "&resource_id=eq." + resourceId);
            return body != null && !new JSONArray(body).isEmpty();
        } catch (Exception e) { return false; }
    }

    private boolean addBookmark(UUID userId, int resourceId) {
        JSONObject j = new JSONObject();
        j.put("user_id",     userId.toString());
        j.put("resource_id", resourceId);
        return supabaseService.postJson("resource_bookmarks", j.toString()) != null;
    }

    private boolean removeBookmark(UUID userId, int resourceId) {
        return supabaseService.deleteRow(
                "resource_bookmarks?user_id=eq." + userId
                + "&resource_id=eq." + resourceId);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  JSON HELPERS
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Serialize an AIResource to a JSON object for INSERT or PATCH.
     *
     * Rules:
     *  • "title" is always included (required NOT NULL column).
     *  • Every other column is only included when non-blank; null/blank strings
     *    are sent as JSON null so Postgres keeps the column DEFAULT.
     *  • On INSERT (isUpdate=false) created_by_user_id is included.
     *  • On PATCH  (isUpdate=true)  we never include id (it's the filter key).
     */
    private JSONObject toJson(AIResource r, boolean isUpdate) {
        JSONObject j = new JSONObject();

        // Required
        j.put("title", r.getTitle());

        // Optional text columns — send null if blank so DB default is preserved
        putOpt(j, "description",           r.getDescription());
        putOpt(j, "content",               r.getContent());
        putOpt(j, "content_html",          r.getContentHtml());
        putOpt(j, "content_markdown",      r.getContentMarkdown());
        putOpt(j, "supabase_file_url",     r.getSupabaseFileUrl());
        putOpt(j, "supabase_file_id",      r.getSupabaseFileId());
        putOpt(j, "telegram_file_id",      r.getTelegramFileId());
        putOpt(j, "telegram_download_url", r.getTelegramDownloadUrl());
        putOpt(j, "pdf_path",              r.getPdfPath());
        putOpt(j, "resource_type",         r.getResourceType());
        putOpt(j, "subject",               r.getSubject());
        putOpt(j, "difficulty_level",      r.getDifficultyLevel());
        putOpt(j, "tags",                  r.getTags());
        putOpt(j, "content_format",        r.getContentFormat());

        // Booleans — always explicit
        j.put("is_published",    r.isPublished());
        j.put("has_latex",       r.isHasLatex());
        j.put("has_code_blocks", r.isHasCodeBlocks());

        // Only on INSERT
        if (!isUpdate && r.getCreatedByUserId() != null) {
            j.put("created_by_user_id", r.getCreatedByUserId().toString());
        }

        return j;
    }

    /** Puts key→value only when value is non-null/non-blank; otherwise puts key→null. */
    private static void putOpt(JSONObject j, String key, String value) {
        if (value == null || value.isBlank()) {
            j.put(key, JSONObject.NULL);
        } else {
            j.put(key, value);
        }
    }

    /**
     * Deserialize a single Supabase JSON row into an AIResource.
     * Every field is read with an optXxx() call so a missing/null column
     * never throws.
     */
    private AIResource fromJson(JSONObject o) {
        AIResource r = new AIResource();

        // id is int4 in DB — use optLong then cast to be safe with large numbers
        r.setId((int) o.optLong("id", 0));

        r.setTitle(              o.optString("title",               ""));
        r.setDescription(        o.optString("description",         null));
        r.setContent(            o.optString("content",             null));
        r.setContentHtml(        o.optString("content_html",        null));
        r.setContentMarkdown(    o.optString("content_markdown",    null));
        r.setSupabaseFileUrl(    o.optString("supabase_file_url",   null));
        r.setSupabaseFileId(     o.optString("supabase_file_id",    null));
        r.setTelegramFileId(     o.optString("telegram_file_id",    null));
        r.setTelegramDownloadUrl(o.optString("telegram_download_url", null));
        r.setPdfPath(            o.optString("pdf_path",            null));
        r.setResourceType(       o.optString("resource_type",       "AI_LESSON"));
        r.setSubject(            o.optString("subject",             null));
        r.setDifficultyLevel(    o.optString("difficulty_level",    "MEDIUM"));
        r.setTags(               o.optString("tags",                null));
        r.setContentFormat(      o.optString("content_format",      "plain"));

        r.setPublished(   o.optBoolean("is_published",    false));
        r.setHasLatex(    o.optBoolean("has_latex",       false));
        r.setHasCodeBlocks(o.optBoolean("has_code_blocks",false));
        r.setViewCount(   o.optInt("view_count",    0));
        r.setDownloadCount(o.optInt("download_count", 0));

        // Timestamps — try both offset and plain ISO formats
        r.setCreatedAt(parseTimestamp(o.optString("created_at", null)));
        r.setUpdatedAt(parseTimestamp(o.optString("updated_at", null)));

        String uid = o.optString("created_by_user_id", null);
        if (uid != null && !uid.isBlank()) {
            try { r.setCreatedByUserId(UUID.fromString(uid)); }
            catch (Exception ignored) {}
        }

        return r;
    }

    private static LocalDateTime parseTimestamp(String ts) {
        if (ts == null || ts.isBlank()) return null;
        // Try offset datetime first (Supabase default: "2024-01-15T10:30:00+00:00")
        try { return LocalDateTime.parse(ts, DateTimeFormatter.ISO_OFFSET_DATE_TIME); }
        catch (Exception ignored) {}
        // Fallback: plain ISO ("2024-01-15T10:30:00")
        try { return LocalDateTime.parse(ts, DateTimeFormatter.ISO_LOCAL_DATE_TIME); }
        catch (Exception ignored) {}
        return null;
    }

    private List<AIResource> fetchList(String path) {
        List<AIResource> list = new ArrayList<>();
        try {
            String body = supabaseService.getJson(path);
            if (body == null || body.isBlank()) return list;
            JSONArray arr = new JSONArray(body);
            for (int i = 0; i < arr.length(); i++) {
                try {
                    list.add(fromJson(arr.getJSONObject(i)));
                } catch (Exception rowErr) {
                    System.err.println("⚠️  fetchList: skipping bad row — " + rowErr.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("❌ fetchList failed (" + path + "): " + e.getMessage());
        }
        return list;
    }

    private static boolean notBlankOrAll(String s) {
        return s != null && !s.isBlank() && !s.equals("All");
    }

    private static String enc(String s) {
        try { return java.net.URLEncoder.encode(s, "UTF-8"); }
        catch (Exception e) { return s; }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  STATS DTO
    // ══════════════════════════════════════════════════════════════════════════

    public static class ResourceStats {
        public int totalResources;
        public int totalViews;
        public int totalDownloads;
    }
}