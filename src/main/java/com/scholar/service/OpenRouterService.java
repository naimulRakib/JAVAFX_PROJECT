package com.scholar.service;

import okhttp3.*;
import org.json.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

@Service
public class OpenRouterService {

    private static final Logger LOG = Logger.getLogger(OpenRouterService.class.getName());

    @Value("${openrouter.api.key}")
    private String apiKey;

    @Value("${openrouter.api.url:https://openrouter.ai/api/v1/chat/completions}")
    private String apiUrl;

    @Value("${openrouter.model:meta-llama/llama-4-maverick:free}")
    private String model;

    private final OkHttpClient http = new OkHttpClient.Builder()
        .callTimeout(60, TimeUnit.SECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build();

    public String chat(String systemPrompt, String userMessage) {
        try {
            JSONObject body = new JSONObject();
            body.put("model", model);
            body.put("max_tokens", 1500);

            JSONArray messages = new JSONArray();
            messages.put(new JSONObject().put("role", "system").put("content", systemPrompt));
            messages.put(new JSONObject().put("role", "user").put("content", userMessage));
            body.put("messages", messages);

            Request request = new Request.Builder()
                .url(apiUrl)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .header("HTTP-Referer", "https://scholargrid.app")
                .header("X-Title", "ScholarGrid")
                .post(RequestBody.create(body.toString(), MediaType.get("application/json")))
                .build();

            try (Response response = http.newCall(request).execute()) {
                String responseBody = response.body().string();

                // ── Debug: print raw response so you can see errors ──
                System.out.println("📡 OpenRouter [" + response.code() + "]: " + responseBody);

                JSONObject json = new JSONObject(responseBody);

                // ── Handle error field (model unavailable, quota, etc.) ──
                if (json.has("error")) {
                    String errMsg = json.getJSONObject("error")
                                       .optString("message", "Unknown error");
                    LOG.log(Level.WARNING, "OpenRouter error: " + errMsg);
                    return errorHtml("AI service error: " + errMsg);
                }

                // ── Handle non-200 HTTP codes ──
                if (!response.isSuccessful()) {
                    return errorHtml("AI service returned HTTP " + response.code()
                        + ". Check your OpenRouter API key and model name.");
                }

                // ── Handle missing choices (malformed response) ──
                if (!json.has("choices") || json.getJSONArray("choices").isEmpty()) {
                    LOG.warning("No choices in response: " + responseBody);
                    return errorHtml("AI returned an empty response. Try again.");
                }

                String markdown = json
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content");

                return markdownToHtml(markdown);
            }

        } catch (Exception e) {
            LOG.log(Level.SEVERE, "OpenRouter chat failed", e);
            return errorHtml("Connection error: " + e.getMessage());
        }
    }

    public String markdownToHtml(String markdown) {
        com.vladsch.flexmark.util.data.MutableDataSet options =
            new com.vladsch.flexmark.util.data.MutableDataSet();
        com.vladsch.flexmark.parser.Parser parser =
            com.vladsch.flexmark.parser.Parser.builder(options).build();
        com.vladsch.flexmark.html.HtmlRenderer renderer =
            com.vladsch.flexmark.html.HtmlRenderer.builder(options).build();
        return renderer.render(parser.parse(markdown));
    }

    private String errorHtml(String message) {
        return "<div style='color:#ef4444;padding:12px;background:#fff0f0;"
             + "border-radius:8px;border-left:3px solid #ef4444;'>"
             + "❌ " + message + "</div>";
    }
}