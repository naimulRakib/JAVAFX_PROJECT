package com.scholar.service;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

@Service
public class GeminiService {

    private static final Logger LOG = Logger.getLogger(GeminiService.class.getName());

    @Value("${gemini.api.key:}")
    private String apiKey;

    @Value("${gemini.model:gemini-3.1-pro-flash-lite-preview}")
    private String model;

    @Value("${gemini.api.url:https://generativelanguage.googleapis.com/v1beta/models/}")
    private String apiUrl;

    private final OkHttpClient http = new OkHttpClient.Builder()
        .callTimeout(60, TimeUnit.SECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build();

    public String chat(String systemPrompt, String userMessage) {
        if (apiKey == null || apiKey.isBlank()) {
            return errorHtml("Gemini API key is missing. Set gemini.api.key in application.properties.");
        }

        try {
            String fullPrompt = systemPrompt + "\n\nUSER QUESTION:\n" + userMessage;

            JSONObject body = new JSONObject();
            JSONArray contents = new JSONArray();
            JSONObject content = new JSONObject();
            JSONArray parts = new JSONArray();
            parts.put(new JSONObject().put("text", fullPrompt));
            content.put("parts", parts);
            contents.put(content);
            body.put("contents", contents);

            JSONObject gen = new JSONObject();
            gen.put("temperature", 0.2);
            gen.put("maxOutputTokens", 2000);
            body.put("generationConfig", gen);

            String url = apiUrl + model + ":generateContent?key=" + apiKey;
            Request request = new Request.Builder()
                .url(url)
                .header("Content-Type", "application/json")
                .post(RequestBody.create(body.toString(), MediaType.get("application/json")))
                .build();

            try (Response response = http.newCall(request).execute()) {
                String responseBody = response.body() != null ? response.body().string() : "";
                System.out.println("📡 Gemini [" + response.code() + "]: " + responseBody);

                if (!response.isSuccessful()) {
                    return errorHtml("Gemini returned HTTP " + response.code()
                        + ". Check model name and API key.");
                }

                JSONObject json = new JSONObject(responseBody);
                if (json.has("error")) {
                    String errMsg = json.getJSONObject("error")
                        .optString("message", "Unknown error");
                    return errorHtml("Gemini error: " + errMsg);
                }

                if (!json.has("candidates") || json.getJSONArray("candidates").isEmpty()) {
                    return errorHtml("Gemini returned an empty response. Try again.");
                }

                JSONObject candidate = json.getJSONArray("candidates").getJSONObject(0);
                JSONObject contentObj = candidate.optJSONObject("content");
                if (contentObj == null) return errorHtml("Gemini response missing content.");

                JSONArray outParts = contentObj.optJSONArray("parts");
                if (outParts == null || outParts.isEmpty()) {
                    return errorHtml("Gemini response missing text parts.");
                }

                StringBuilder combined = new StringBuilder();
                for (int i = 0; i < outParts.length(); i++) {
                    String text = outParts.getJSONObject(i).optString("text", "");
                    combined.append(text);
                }
                return combined.toString();
            }
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Gemini chat failed", e);
            return errorHtml("Connection error: " + e.getMessage());
        }
    }

    private String errorHtml(String message) {
        return "<div style='color:#ef4444;padding:12px;background:#fff0f0;"
             + "border-radius:8px;border-left:3px solid #ef4444;'>"
             + "❌ " + message + "</div>";
    }
}
