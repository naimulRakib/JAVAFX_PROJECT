package com.scholar.service;

import okhttp3.*;
import org.json.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class EmbeddingService {

    @Value("${openrouter.api.key}")
    private String apiKey;

    // Using OpenAI-compatible embeddings via OpenRouter
    private static final String EMBED_URL = "https://openrouter.ai/api/v1/embeddings";
    private static final String EMBED_MODEL = "openai/text-embedding-3-small"; // 1536 dims, cheap

    private final OkHttpClient http = new OkHttpClient();

    public float[] embed(String text) {
        try {
            JSONObject body = new JSONObject();
            body.put("model", EMBED_MODEL);
            body.put("input", text.substring(0, Math.min(text.length(), 8000)));

            Request request = new Request.Builder()
                .url(EMBED_URL)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .post(RequestBody.create(body.toString(), MediaType.get("application/json")))
                .build();

            try (Response response = http.newCall(request).execute()) {
                JSONObject json = new JSONObject(response.body().string());
                JSONArray embArray = json
                    .getJSONArray("data")
                    .getJSONObject(0)
                    .getJSONArray("embedding");

                float[] vector = new float[embArray.length()];
                for (int i = 0; i < embArray.length(); i++) {
                    vector[i] = (float) embArray.getDouble(i);
                }
                return vector;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return new float[1536]; // zero vector fallback
        }
    }

    /** Convert float[] to Postgres vector string: '[0.1,0.2,...]' */
    public String toVectorString(float[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            sb.append(vector[i]);
            if (i < vector.length - 1) sb.append(",");
        }
        sb.append("]");
        return sb.toString();
    }
}