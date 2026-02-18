package com.scholar.service;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import io.github.cdimascio.dotenv.Dotenv;
import org.json.JSONObject;

public class AIOrchestrator {

    private static final Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
    private static final String GOOGLE_API_KEY = dotenv.get("GOOGLE_API_KEY"); 

    // 1. THE LIBRARIAN: Gemini 2.5 Flash
    // Optimized for speed and high volume (reading PDFs)
    private final ChatLanguageModel librarian;

    // 2. THE TUTOR: Gemini 2.5 Pro
    // Optimized for deep reasoning (answering "Why?" questions)
    private final ChatLanguageModel tutor;

    public AIOrchestrator() {
        if (GOOGLE_API_KEY == null) {
            System.err.println("❌ ERROR: GOOGLE_API_KEY not found in .env!");
        }

        // Initialize Gemini 2.5 Flash
        this.librarian = GoogleAiGeminiChatModel.builder()
                .apiKey(GOOGLE_API_KEY)
                .modelName("gemini-2.5-flash") 
                .temperature(0.1) // Low creativity for accurate OCR/Tagging
                .build();

        // Initialize Gemini 2.5 Pro
        this.tutor = GoogleAiGeminiChatModel.builder()
                .apiKey(GOOGLE_API_KEY)
                .modelName("gemini-2.5-pro") 
                .temperature(0.7) // Higher creativity for natural chat
                .build();
    }

    /**
     * Feature: Smart Ingest (Uses Gemini 2.5 Flash)
     */
    public JSONObject autoTagResource(String textInfo) {
        String prompt = """
            You are a rigorous academic librarian. Analyze this text:
            "%s"
            
            Return a JSON object with:
            - 'course': Course Code (e.g. CSE 201)
            - 'topic': Main Topic (e.g. DP, MST)
            - 'type': 'CT-Question', 'Term-Final', or 'Class-Note'
            - 'summary': A 1-sentence summary.
            
            Return ONLY JSON.
            """.formatted(textInfo);

        try {
            String response = librarian.generate(prompt);
            response = response.replace("```json", "").replace("```", "").trim();
            return new JSONObject(response);
        } catch (Exception e) {
            return new JSONObject().put("error", "Analysis Failed");
        }
    }

    /**
     * Feature: Viva/Tutor Chat (Uses Gemini 2.5 Pro)
     */
    public String askTutor(String question) {
        return tutor.generate("You are a friendly CSE Professor. Briefly explain: " + question);
    }
}