package com.scholar.service;

import com.scholar.model.StudyTask;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import io.github.cdimascio.dotenv.Dotenv;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

/**
 * AI Scheduler Service powered by Google Gemini.
 * Handles schedule generation, varsity routine parsing, and AI study guides.
 */
public class AISchedulerService {

    private static final String GEMINI_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=";
    private final String apiKey;
    private final ObjectMapper jsonMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public AISchedulerService() {
        Dotenv dotenv = Dotenv.load();
        this.apiKey = dotenv.get("GEMINI_API_KEY");
    }

   
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
            
            JSON FORMAT:
            [{"title": "Task Name", "date": "YYYY-MM-DD", "startTime": "HH:mm", "description": "Notes", "type": "Regular"}]
            """.formatted(today, userText);

        try {
            return sendAndParse(prompt);
        } catch (Exception e) {
            System.err.println("❌ AI Error: " + e.getMessage());
            return Collections.emptyList();
        }
    }

  
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
            // Reusing the Gemini request logic but returning raw text instead of JSON list
            String response = callGeminiRaw(prompt);
            return extractTextFromGemini(response);
        } catch (Exception e) {
            return "❌ AI failed to generate guide: " + e.getMessage();
        }
    }

    // ==========================================================
    // ENGINE 1: VARSITY ROUTINE (Weekly)
    // ==========================================================

    public List<StudyTask> parseVarsityRoutine(String rawText) {
        String prompt = """
            Analyze this varsity class routine and output a STRICT JSON ARRAY of events.
            
            CRITICAL RULES:
            1. Extract the subjects. Use exact keys: "title", "date", "startTime", "durationMinutes", "roomNo", "type", "tags".
            2. For "date", strictly use the FULL DAY NAME (e.g., "SUNDAY", "MONDAY").
            3. "durationMinutes" must be an integer. If merged cell (e.g. 02:00-04:00), calculate total minutes (120).
            4. If a value like room number is not provided, strictly use null (without quotes).
            5. Set "type" strictly as "ROUTINE".
            
            EXPECTED JSON FORMAT:
            [
              {
                "title": "CSE 105",
                "date": "SUNDAY",
                "startTime": "08:00 AM",
                "durationMinutes": 60,
                "roomNo": "Room 302",
                "type": "ROUTINE",
                "tags": "Class"
              }
            ]
            
            INPUT ROUTINE:
            %s
            """.formatted(rawText.replace("\"", "\\\""));

        try {
            return sendAndParse(prompt);
        } catch (Exception e) {
        
            System.err.println("❌ AI Parsing Error in Varsity Routine: " + e.getMessage());
            return java.util.Collections.emptyList();
        }
    }
    

    //Admin parse field. 
 
public String parseAdminBroadcast(String adminRawText) {
    String prompt = """
        You are a highly intelligent Academic Parser for an engineering university.
        The Admin will paste EITHER a weekly class routine (table format) OR a specific notice.
        
        YOUR TASK: Convert this into a STRICT JSON ARRAY of events.
        
        RULES:
        1. Extract: title, dayOfWeek (e.g., "SATURDAY"), specificDate (if it's a notice, else null), startTime, duration (in minutes), roomNo, and type ("ROUTINE" or "NOTICE").
        2. If a value is missing (like room number or duration), output exactly null (without quotes).
        3. Break down merged cells. For example, if CSE 108 Lab spans 02:00-04:00, output duration: 120.
        
        EXPECTED JSON FORMAT:
        [
          {
            "title": "CSE 107",
            "dayOfWeek": "SUNDAY",
            "specificDate": null,
            "startTime": "11:00 AM",
            "duration": 60,
            "roomNo": null,
            "type": "ROUTINE",
            "tags": "Class"
          }
        ]
        
        ADMIN INPUT:
        %s
        """.formatted(adminRawText.replace("\"", "\\\""));

    try {
        return callGeminiRaw(prompt);
    } catch (Exception e) {
        return "[]";
    }
}


    // ==========================================================
    // ENGINE 2: PERSONAL AGENT
    // ==========================================================
    public List<StudyTask> parsePersonalTask(String rawText) {
        String today = LocalDate.now().toString();
        String prompt = """
            Role: Personal Scheduling Assistant.
            Today is: %s
            USER REQUEST: "%s"
            COMMANDS: 
            - IF ADDING: Set type: "Personal".
            - IF CANCELLING: Set type: "CANCEL", title: "REMOVE", calculate date.
            """.formatted(today, rawText);

        try {
            return sendAndParse(prompt);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    // ==========================================================
    // --- PRIVATE UTILITIES ---
    // ==========================================================

    private List<StudyTask> sendAndParse(String prompt) throws Exception {
        String responseBody = callGeminiRaw(prompt);
        return extractJsonList(responseBody);
    }

    private String callGeminiRaw(String prompt) throws Exception {
        String jsonBody = "{ \"contents\": [{ \"parts\": [{ \"text\": \"%s\" }] }] }"
                .formatted(escapeJson(prompt));
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(GEMINI_URL + apiKey))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return response.body();
    }

    private String extractTextFromGemini(String rawResponse) throws Exception {
        var root = jsonMapper.readTree(rawResponse);
        if (!root.has("candidates")) return "Error: No AI Candidates";
        
        return root.path("candidates").get(0)
                   .path("content").path("parts").get(0)
                   .path("text").asText();
    }

    private List<StudyTask> extractJsonList(String rawResponse) {
        try {
            String text = extractTextFromGemini(rawResponse);
            
            // 🧹 Clean markdown blocks
            text = text.replaceAll("```json", "").replaceAll("```", "").trim();
            
            int start = text.indexOf("[");
            int end = text.lastIndexOf("]");
            
            if (start == -1 || end == -1) return Collections.emptyList();
            
            String cleanJson = text.substring(start, end + 1);
            return jsonMapper.readValue(cleanJson, new TypeReference<List<StudyTask>>() {});
            
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private String escapeJson(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
    }

 
    public String[] generateResourceMetadata(String title, String topicName) {
        try {
            String prompt = "You are an AI Librarian. I am uploading a university study resource titled '" + title + "' under the topic '" + topicName + "'. " +
                            "Generate a short, professional description (max 2 sentences) and 3 relevant tags. " +
                            "Format your response EXACTLY like this:\n" +
                            "DESC: [your description]\n" +
                            "TAGS: #tag1, #tag2, #tag3";

            // এখানে আপনার Gemini API কল করার কোড বসবে। (আমি ধরে নিচ্ছি আপনি callApi বা 비슷한 কোনো মেথড ব্যবহার করেন)
            // String response = callGeminiApi(prompt); 
            
            // ⚠️ আপাতত ডেমো রেসপন্স (API যুক্ত না থাকলে এটি কাজ করবে):
            String response = "DESC: A comprehensive study material covering key concepts and problem-solving techniques for " + title + ".\nTAGS: #" + topicName.replaceAll("\\s+", "") + ", #StudyMaterial, #Important";
            
            String desc = response.split("DESC:")[1].split("TAGS:")[0].trim();
            String tags = response.split("TAGS:")[1].trim();
            
            return new String[]{desc, tags};
        } catch (Exception e) {
            return new String[]{"A helpful resource for " + title, "#" + topicName.replaceAll("\\s+", "")};
        }
    }

    // ==========================================================
    //  ENGINE 3: UNIVERSAL NOTICE PARSER
    // ==========================================================
    public List<StudyTask> parseAdminNotice(String rawText, String todayDate) {
        String prompt = """
            You are a University Notice Broadcaster. 
            Read the raw text and output a STRICT JSON ARRAY with exactly ONE object.
            
            RULES:
            1. "title": Create a catchy short title (e.g., "🚨 Lab Final Update").
            2. "date": Extract the date (YYYY-MM-DD). If no exact date is mentioned, strictly use: %s.
            3. "startTime": Strictly use "Anytime".
            4. "durationMinutes": strictly null (without quotes).
            5. "roomNo": Extract if mentioned, else strictly null.
            6. "type": Strictly use "NOTICE".
            7. "tags": Keep the FULL exact notice message here. Format it beautifully with bullet points or emojis if it helps readability.
            
            EXPECTED JSON FORMAT:
            [
              {
                "title": "🚨 Varsity Closed Tomorrow",
                "date": "2026-02-15",
                "startTime": "Anytime",
                "durationMinutes": null,
                "roomNo": null,
                "type": "NOTICE",
                "tags": "Due to heavy rain, all classes are cancelled..."
              }
            ]
            
            INPUT NOTICE:
            %s
            """.formatted(todayDate, rawText.replace("\"", "\\\""));

        try {
            return sendAndParse(prompt);
        } catch (Exception e) {
            System.err.println("❌ AI Parsing Error in Notice: " + e.getMessage());
            return java.util.Collections.emptyList();
        }
    }
    

    
}