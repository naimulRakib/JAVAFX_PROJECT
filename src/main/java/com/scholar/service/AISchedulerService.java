package com.scholar.service;

import com.scholar.model.StudyTask;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * AI Scheduler Service — powered by Groq (OpenAI-compatible API).
 *
 * MIGRATION: Gemini → Groq
 * ─────────────────────────
 * • API key is read from application.properties
 * • Endpoint: https://api.groq.com/openai/v1/chat/completions
 * • Model is configurable via groq.model (default: llama-3.1-70b-versatile)
 */
@Service
public class AISchedulerService {

    // ── Groq config ───────────────────────────────────────────────
    private static final String GROQ_URL = "https://api.groq.com/openai/v1/chat/completions";

    @Value("${groq.api.key:}")
    private String groqApiKey;

    @Value("${groq.model:llama-3.1-70b-versatile}")
    private String groqModel;

    private final ObjectMapper jsonMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    // ══════════════════════════════════════════════════════════════
    // ENGINE: SCHEDULE GENERATOR
    // ══════════════════════════════════════════════════════════════

    /**
     * Parses generic user study requests into structured tasks.
     * Maps to the 'onGenerateClick' action in DashboardController.
     */
    public List<StudyTask> generateSchedule(String userText) {
        String today = LocalDate.now().toString();
        String prompt = """
            You are a Scholar Assistant for a CSE student.
            Analyze the following study request and output a JSON ARRAY of tasks.
            Current Date: %s

            REQUEST: "%s"

            RULES:
            1. Extract the Title, specific Date (YYYY-MM-DD), and Start Time (HH:mm).
            2. Assign "type" as "Regular".
            3. If no time is specified, use "Flexible".

            OUTPUT ONLY a valid JSON array, no explanation, no markdown:
            [{"title": "Task Name", "date": "YYYY-MM-DD", "startTime": "HH:mm", "description": "Notes", "type": "Regular"}]
            """.formatted(today, userText);

        try {
            return sendAndParse(prompt);
        } catch (Exception e) {
            System.err.println("❌ Groq Error (generateSchedule): " + e.getMessage());
            return Collections.emptyList();
        }
    }

    // ══════════════════════════════════════════════════════════════
    // ENGINE: STUDY GUIDE GENERATOR
    // ══════════════════════════════════════════════════════════════

    /**
     * Generates an AI Study Guide for the Resource Engine.
     * Maps to the 'onGenerateResource' action in DashboardController.
     */
    public String generateStudyGuide(String topic, String rawContent) {
        String prompt = """
            Create a comprehensive AI Study Guide for the topic: %s.
            Use the following raw content or syllabus as a base:
            %s

            FORMAT:
            1. Executive Summary
            2. Key Concepts & Formulas
            3. Potential Exam Questions (Short & Long)
            4. Quick Revision Tips
            """.formatted(topic, rawContent);

        try {
            return callGroqRaw(prompt);
        } catch (Exception e) {
            return "❌ AI failed to generate guide: " + e.getMessage();
        }
    }

    // ══════════════════════════════════════════════════════════════
    // ENGINE 1: VARSITY ROUTINE (Weekly)
    // ══════════════════════════════════════════════════════════════

    public List<StudyTask> parseVarsityRoutine(String rawText) {
        String prompt = """
            Analyze this varsity class routine and output a STRICT JSON ARRAY of events.

            CRITICAL RULES:
            1. Extract the subjects. Use exact keys: "title", "date", "startTime", "durationMinutes", "roomNo", "type", "tags".
            2. For "date", strictly use the FULL DAY NAME (e.g., "SUNDAY", "MONDAY").
            3. "durationMinutes" must be an integer. If merged cell (e.g. 02:00-04:00), calculate total minutes (120).
            4. If a value like room number is not provided, strictly use null (without quotes).
            5. Set "type" strictly as "ROUTINE".
            6. OUTPUT ONLY the JSON array. No explanation, no markdown, no backticks.

            EXPECTED JSON FORMAT:
            [{"title": "CSE 105", "date": "SUNDAY", "startTime": "08:00 AM", "durationMinutes": 60, "roomNo": "Room 302", "type": "ROUTINE", "tags": "Class"}]

            INPUT ROUTINE:
            %s
            """.formatted(rawText.replace("\"", "\\\""));

        try {
            return sendAndParse(prompt);
        } catch (Exception e) {
            System.err.println("❌ Groq Parsing Error in Varsity Routine: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    // ══════════════════════════════════════════════════════════════
    // ADMIN BROADCAST PARSER
    // ══════════════════════════════════════════════════════════════

    public String parseAdminBroadcast(String adminRawText) {
        String prompt = """
            You are a highly intelligent Academic Parser for an engineering university.
            The Admin will paste EITHER a weekly class routine (table format) OR a specific notice.

            YOUR TASK: Convert this into a STRICT JSON ARRAY of events.

            RULES:
            1. Extract: title, dayOfWeek (e.g., "SATURDAY"), specificDate (if it's a notice, else null), startTime, duration (in minutes), roomNo, and type ("ROUTINE" or "NOTICE").
            2. If a value is missing (like room number or duration), output exactly null (without quotes).
            3. Break down merged cells. For example, if CSE 108 Lab spans 02:00-04:00, output duration: 120.
            4. OUTPUT ONLY the JSON array. No explanation, no markdown, no backticks.

            EXPECTED JSON FORMAT:
            [{"title": "CSE 107", "dayOfWeek": "SUNDAY", "specificDate": null, "startTime": "11:00 AM", "duration": 60, "roomNo": null, "type": "ROUTINE", "tags": "Class"}]

            ADMIN INPUT:
            %s
            """.formatted(adminRawText.replace("\"", "\\\""));

        try {
            // Returns raw JSON string — same as Gemini version
            return callGroqRaw(prompt);
        } catch (Exception e) {
            return "[]";
        }
    }

    // ══════════════════════════════════════════════════════════════
    // ENGINE 2: PERSONAL AGENT
    // ══════════════════════════════════════════════════════════════

    public List<StudyTask> parsePersonalTask(String rawText) {
        String today = LocalDate.now().toString();
        String prompt = """
            Role: Personal Scheduling Assistant.
            Today is: %s
            USER REQUEST: "%s"
            COMMANDS:
            - IF ADDING: Set type: "Personal".
            - IF CANCELLING: Set type: "CANCEL", title: "REMOVE", calculate date.
            OUTPUT ONLY a valid JSON array, no explanation, no markdown:
            [{"title": "...", "date": "YYYY-MM-DD", "startTime": "HH:mm", "type": "Personal"}]
            """.formatted(today, rawText);

        try {
            return sendAndParse(prompt);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    // ══════════════════════════════════════════════════════════════
    // RESOURCE METADATA GENERATOR
    // ══════════════════════════════════════════════════════════════

    public String[] generateResourceMetadata(String title, String topicName) {
        try {
            String prompt = """
                You are an AI Librarian. I am uploading a university study resource titled '%s' under the topic '%s'.
                Generate a short, professional description (max 2 sentences) and 3 relevant tags.
                Format your response EXACTLY like this (no extra text):
                DESC: [your description]
                TAGS: #tag1, #tag2, #tag3
                """.formatted(title, topicName);

            String response = callGroqRaw(prompt);

            // Parse DESC and TAGS lines
            String desc = "A helpful resource for " + title;
            String tags = "#" + topicName.replaceAll("\\s+", "");

            if (response.contains("DESC:") && response.contains("TAGS:")) {
                desc = response.split("DESC:")[1].split("TAGS:")[0].trim();
                tags = response.split("TAGS:")[1].trim();
            }

            return new String[]{desc, tags};
        } catch (Exception e) {
            return new String[]{"A helpful resource for " + title, "#" + topicName.replaceAll("\\s+", "")};
        }
    }

    // ══════════════════════════════════════════════════════════════
    // ENGINE 3: UNIVERSAL NOTICE PARSER
    // ══════════════════════════════════════════════════════════════

    public List<StudyTask> parseAdminNotice(String rawText, String todayDate) {
        String prompt = """
            You are a University Notice Broadcaster.
            Read the raw text and output a STRICT JSON ARRAY with exactly ONE object.

            RULES:
            1. "title": Create a catchy short title (e.g., "🚨 Lab Final Update").
            2. "date": Extract the date (YYYY-MM-DD). If no exact date is mentioned, use: %s.
            3. "startTime": Strictly use "Anytime".
            4. "durationMinutes": strictly null (without quotes).
            5. "roomNo": Extract if mentioned, else strictly null.
            6. "type": Strictly use "NOTICE".
            7. "tags": Keep the FULL exact notice message here.
            8. OUTPUT ONLY the JSON array. No explanation, no markdown, no backticks.

            EXPECTED FORMAT:
            [{"title": "🚨 Varsity Closed Tomorrow", "date": "2026-02-15", "startTime": "Anytime", "durationMinutes": null, "roomNo": null, "type": "NOTICE", "tags": "Due to heavy rain, all classes are cancelled..."}]

            INPUT NOTICE:
            %s
            """.formatted(todayDate, rawText.replace("\"", "\\\""));

        try {
            return sendAndParse(prompt);
        } catch (Exception e) {
            System.err.println("❌ Groq Parsing Error in Notice: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    // ══════════════════════════════════════════════════════════════
    // PRIVATE: GROQ HTTP CALL
    // ══════════════════════════════════════════════════════════════

    private String callGroqRaw(String prompt) throws Exception {
        if (groqApiKey == null || groqApiKey.isBlank()) {
            throw new IllegalStateException("groq.api.key is missing. Set it in application.properties.");
        }

        String jsonBody = "{\"model\":\"%s\",\"messages\":[{\"role\":\"user\",\"content\":\"%s\"}],\"temperature\":0.2}".formatted(
            escapeJson(groqModel),
            escapeJson(prompt)
        );

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(GROQ_URL))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + groqApiKey)
            .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Groq returned HTTP " + response.statusCode() + " — " + response.body());
        }

        return extractTextFromGroq(response.body());
    }

    private String extractTextFromGroq(String rawResponse) throws Exception {
        var root = jsonMapper.readTree(rawResponse);
        if (!root.has("choices")) return "Error: No AI Choices";

        return root.path("choices").get(0)
                   .path("message").path("content").asText();
    }

    // ══════════════════════════════════════════════════════════════
    // PRIVATE: PARSE + SHARED UTILITIES
    // ══════════════════════════════════════════════════════════════

    private List<StudyTask> sendAndParse(String prompt) throws Exception {
        String responseText = callGroqRaw(prompt);
        return extractJsonList(responseText);
    }

    private List<StudyTask> extractJsonList(String text) {
        try {
            if (text == null || text.isBlank()) return Collections.emptyList();

            // Clean markdown fences if model adds them
            text = text.replaceAll("```json", "").replaceAll("```", "").trim();

            int start = text.indexOf("[");
            int end   = text.lastIndexOf("]");

            if (start == -1 || end == -1 || end <= start) return Collections.emptyList();

            String cleanJson = text.substring(start, end + 1);
            return jsonMapper.readValue(cleanJson, new TypeReference<List<StudyTask>>() {});
        } catch (Exception e) {
            System.err.println("⚠️ JSON parse failed: " + e.getMessage());
            System.err.println("   Raw response: " + text);
            return Collections.emptyList();
        }
    }

    private String escapeJson(String text) {
        if (text == null) return "";
        return text
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "")
            .replace("\t", "\\t");
    }
}
