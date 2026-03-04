package com.scholar.service;

import com.scholar.model.AIResource;
import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;

/**
 * Supabase Service — wraps Supabase Storage (file uploads) and
 * PostgREST (database REST API) calls.
 *
 * ── Setup ────────────────────────────────────────────────────────────────────
 * Add to application.properties (or application.yml):
 *
 *   supabase.url=https://xxxxxxxxxxxx.supabase.co
 *   supabase.key=<service_role key>          # use service_role for server-side; anon key for client
 *   supabase.bucket.name=ai-resources
 *
 * ── Supabase Storage bucket ──────────────────────────────────────────────────
 * Create a bucket named "ai-resources" in the Supabase Dashboard → Storage.
 * Set it to "Public" so the generated URLs are directly accessible without a
 * signed token.  If you need private access, use createSignedUrl() instead of
 * getPublicUrl().
 *
 * ── PostgREST (database) ─────────────────────────────────────────────────────
 * The Supabase project exposes every Postgres table via a REST API at:
 *   <supabase.url>/rest/v1/<table_name>
 * Authentication uses the same API key.  See AIResourceService for the DDL.
 * ─────────────────────────────────────────────────────────────────────────────
 */
@Service
public class SupabaseService {

    @Value("${supabase.url}")
    private String supabaseUrl;

    @Value("${supabase.key}")
    private String supabaseKey;

    @Value("${supabase.bucket.name:ai-resources}")
    private String bucketName;

    private static final MediaType JSON_TYPE = MediaType.parse("application/json; charset=utf-8");

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();

    // ══════════════════════════════════════════════════════════════════════════
    // STORAGE  (file upload / delete / URL)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Upload a file to Supabase Storage.
     *
     * @param file     Local file to upload
     * @param fileName Path inside the bucket, e.g. "resource_42_1700000000000.pdf"
     * @return public URL of the uploaded file, or null on failure
     */
    public String uploadFile(File file, String fileName) {
        try {
            byte[]  bytes       = Files.readAllBytes(file.toPath());
            String  contentType = Files.probeContentType(file.toPath());
            if (contentType == null) contentType = "application/octet-stream";

            String uploadUrl = supabaseUrl + "/storage/v1/object/" + bucketName + "/" + fileName;

            Request request = new Request.Builder()
                    .url(uploadUrl)
                    .post(RequestBody.create(bytes, MediaType.parse(contentType)))
                    .addHeader("Authorization", "Bearer " + supabaseKey)
                    .addHeader("Content-Type", contentType)
                    .addHeader("x-upsert", "true")   // overwrite if same name exists
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    System.out.println("✅ Uploaded to Supabase Storage: " + fileName);
                    return getPublicUrl(fileName);
                } else {
                    String err = response.body() != null ? response.body().string() : "no body";
                    System.err.println("❌ Upload failed [" + response.code() + "]: " + err);
                    return null;
                }
            }
        } catch (IOException e) {
            System.err.println("❌ uploadFile exception: " + e.getMessage());
            return null;
        }
    }

    /**
     * Returns the public URL for a file stored in the bucket.
     * Only works if the bucket is configured as "Public" in Supabase Dashboard.
     */
    public String getPublicUrl(String fileName) {
        return supabaseUrl + "/storage/v1/object/public/" + bucketName + "/" + fileName;
    }

    /**
     * Delete a file from the bucket.
     *
     * @param fileName Path inside the bucket (the value stored as supabase_file_id)
     */
    public boolean deleteFile(String fileName) {
        String deleteUrl = supabaseUrl + "/storage/v1/object/" + bucketName + "/" + fileName;
        Request request = new Request.Builder()
                .url(deleteUrl)
                .delete()
                .addHeader("Authorization", "Bearer " + supabaseKey)
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful()) {
                System.out.println("✅ Deleted from Supabase Storage: " + fileName);
                return true;
            } else {
                System.err.println("❌ Delete failed [" + response.code() + "]");
                return false;
            }
        } catch (IOException e) {
            System.err.println("❌ deleteFile exception: " + e.getMessage());
            return false;
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // POSTGREST  (database REST API — used by AIResourceService)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * HTTP GET on a PostgREST path.
     *
     * @param queryPath e.g. "ai_resources?order=created_at.desc"
     * @return raw JSON string (array), or null on failure
     */
    public String getJson(String queryPath) {
        String url = supabaseUrl + "/rest/v1/" + queryPath;
        Request request = new Request.Builder()
                .url(url)
                .get()
                .addHeader("Authorization", "Bearer " + supabaseKey)
                .addHeader("apikey", supabaseKey)
                .addHeader("Accept", "application/json")
                .build();
        return executeForBody(request, "GET " + queryPath);
    }

    /**
     * HTTP POST (INSERT) on a PostgREST path.
     *
     * @param table    Table name (no leading slash), e.g. "ai_resources"
     * @param jsonBody Serialized JSON object string
     * @return response body (representation), or null on failure
     */
    public String postJson(String table, String jsonBody) {
        String url = supabaseUrl + "/rest/v1/" + table;
        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(jsonBody, JSON_TYPE))
                .addHeader("Authorization", "Bearer " + supabaseKey)
                .addHeader("apikey", supabaseKey)
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "return=representation")  // returns inserted row as JSON
                .build();
        return executeForBody(request, "POST " + table);
    }

    /**
     * HTTP PATCH (UPDATE) on a PostgREST path.
     *
     * @param queryPath e.g. "ai_resources?id=eq.42"
     * @param jsonBody  Fields to update as JSON object string
     * @return true on success
     */
    public boolean patchJson(String queryPath, String jsonBody) {
        String url = supabaseUrl + "/rest/v1/" + queryPath;
        Request request = new Request.Builder()
                .url(url)
                .patch(RequestBody.create(jsonBody, JSON_TYPE))
                .addHeader("Authorization", "Bearer " + supabaseKey)
                .addHeader("apikey", supabaseKey)
                .addHeader("Content-Type", "application/json")
                .build();
        return executeSuccessCheck(request, "PATCH " + queryPath);
    }

    /**
     * HTTP DELETE on a PostgREST path.
     *
     * @param queryPath e.g. "ai_resources?id=eq.42"
     * @return true on success
     */
    public boolean deleteRow(String queryPath) {
        String url = supabaseUrl + "/rest/v1/" + queryPath;
        Request request = new Request.Builder()
                .url(url)
                .delete()
                .addHeader("Authorization", "Bearer " + supabaseKey)
                .addHeader("apikey", supabaseKey)
                .build();
        return executeSuccessCheck(request, "DELETE " + queryPath);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // METADATA HELPERS  (kept for callers that use saveResourceMetadata directly)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * @deprecated Prefer AIResourceService.saveResource() which uses this class internally.
     */
    @Deprecated
    public boolean saveResourceMetadata(AIResource resource) {
        // Delegate to AIResourceService logic inlined here for legacy callers.
        JSONObject json = buildResourceJson(resource, false);
        String result = postJson("ai_resources", json.toString());
        if (result != null) {
            try {
                JSONArray arr = new JSONArray(result);
                if (!arr.isEmpty()) resource.setId(arr.getJSONObject(0).getInt("id"));
            } catch (Exception ignored) {}
            return true;
        }
        return false;
    }

    /**
     * @deprecated Prefer AIResourceService.updateResource().
     */
    @Deprecated
    public boolean updateResourceMetadata(AIResource resource) {
        JSONObject json = buildResourceJson(resource, true);
        return patchJson("ai_resources?id=eq." + resource.getId(), json.toString());
    }

    // ── Connection test ───────────────────────────────────────────────────────

    /** Performs a lightweight GET to verify connectivity and API key validity. */
    public boolean testConnection() {
        String body = getJson("ai_resources?limit=1");
        if (body != null) {
            System.out.println("✅ Supabase connection OK");
            return true;
        }
        System.err.println("❌ Supabase connection failed");
        return false;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private String executeForBody(Request request, String label) {
        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful()) {
                return response.body() != null ? response.body().string() : "[]";
            } else {
                String err = response.body() != null ? response.body().string() : "no body";
                System.err.println("❌ " + label + " [" + response.code() + "]: " + err);
                return null;
            }
        } catch (IOException e) {
            System.err.println("❌ " + label + " exception: " + e.getMessage());
            return null;
        }
    }

    private boolean executeSuccessCheck(Request request, String label) {
        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful()) return true;
            String err = response.body() != null ? response.body().string() : "no body";
            System.err.println("❌ " + label + " [" + response.code() + "]: " + err);
            return false;
        } catch (IOException e) {
            System.err.println("❌ " + label + " exception: " + e.getMessage());
            return false;
        }
    }

    /** Shared JSON builder for the two deprecated metadata methods. */
    private JSONObject buildResourceJson(AIResource r, boolean forUpdate) {
        JSONObject json = new JSONObject();
        json.put("title",                 r.getTitle());
        json.put("description",           orNull(r.getDescription()));
        json.put("content",               orNull(r.getContent()));
        json.put("content_html",          orNull(r.getContentHtml()));
        json.put("content_markdown",      orNull(r.getContentMarkdown()));
        json.put("supabase_file_url",     orNull(r.getSupabaseFileUrl()));
        json.put("supabase_file_id",      orNull(r.getSupabaseFileId()));
        json.put("resource_type",         r.getResourceType());
        json.put("subject",               orNull(r.getSubject()));
        json.put("difficulty_level",      r.getDifficultyLevel());
        json.put("tags",                  orNull(r.getTags()));
        json.put("is_published",          r.isPublished());
        json.put("has_latex",             r.isHasLatex());
        json.put("has_code_blocks",       r.isHasCodeBlocks());
        json.put("content_format",        r.getContentFormat());
        if (!forUpdate && r.getCreatedByUserId() != null) {
            json.put("created_by_user_id", r.getCreatedByUserId().toString());
        }
        return json;
    }

    private Object orNull(String s) {
        return (s == null || s.isBlank()) ? JSONObject.NULL : s;
    }
}