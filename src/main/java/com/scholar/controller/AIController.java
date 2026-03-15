
 //private String GEMINI_API_KEY = "AIzaSyCsgRERKdg0pDtapy5nuYJeFfiotr5V6MM"; 
package com.scholar.controller;

import com.scholar.service.AuthService;
import com.scholar.service.DatabaseConnection;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired; // 🟢 নতুন
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller; // 🟢 নতুন
import javax.sql.DataSource; // 🟢


@Controller

public class AIController {
    @Autowired
    private DataSource dataSource;

    @Value("${gemini.api.key:}")
    private String GEMINI_API_KEY;

    @Value("${gemini.model:gemini-3.1-pro-flash-lite-preview}")
    private String GEMINI_MODEL;

    public void setApiKey(String key) {
        this.GEMINI_API_KEY = key;
    }

    // ==========================================================
    // 🤖 1. AUTO-SUMMARIZER (Called during resource upload)
    // ==========================================================
    public String generateResourceSummary(String title, String link, String tags, String description) {
        if (GEMINI_API_KEY == null || GEMINI_API_KEY.equals("YOUR_GEMINI_API_KEY_HERE")) {
            return "AI Summary is unavailable because API Key is missing.";
        }

        String prompt = "You are a helpful AI assistant for BUET students. " +
                "Write a short, engaging 2-sentence summary for this study resource based on the following details:\\n" +
                "Title: " + title + "\\n" +
                "Link: " + link + "\\n" +
                "Tags: " + tags + "\\n" +
                "Description: " + description + "\\n" +
                "Make the summary useful so students know exactly what they will learn from it. Do not use special characters that break JSON.";

        try {
            // Escape special characters for JSON
            String cleanPrompt = prompt.replace("\"", "\\\"").replace("\n", " ");
            String jsonBody = "{\"contents\": [{\"parts\": [{\"text\": \"" + cleanPrompt + "\"}]}]}";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://generativelanguage.googleapis.com/v1beta/models/" + GEMINI_MODEL
                        + ":generateContent?key=" + GEMINI_API_KEY))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            // Extract text from JSON response using Regex
            Matcher m = Pattern.compile("\"text\"\\s*:\\s*\"([^\"]+)\"").matcher(response.body());
            if (m.find()) {
                return m.group(1).replace("\\n", "\n").replace("\\\"", "\"");
            }
        } catch (Exception e) {
            System.err.println("❌ AI Summary Failed: " + e.getMessage());
        }
        return "A useful study resource regarding " + title;
    }

    // ==========================================================
    // 💬 2. SYSTEMATIC AI TUTOR (Ranked & Categorized)
    // ==========================================================
    public String askSmartAITutor(String userQuery, String searchKeyword) {
        // 1. API Key Check
        if (GEMINI_API_KEY == null || GEMINI_API_KEY.equals("YOUR_GEMINI_API_KEY_HERE")) {
            return wrapWithModernUI("<p style='color:red;'>⚠️ Error: API Key is missing! Please connect your AI Key first.</p>");
        }

        // 2. Fetch Systematic Context from Database
        String dbContext = fetchSystematicContext(searchKeyword);

        if (dbContext.trim().isEmpty()) {
            return wrapWithModernUI("<p>No resources found in ScholarGrid regarding '<b>" + searchKeyword + "</b>'. Please try a different keyword.</p>");
        }

        // 3. Prompt Engineering
       String systemPrompt = """
    You are 'ScholarGrid AI', a professional academic assistant. Use the provided ranked resources and student discussions to help the user.
    
    --- CONTEXT ---
    %s
    
    YOUR STRICT INSTRUCTIONS:
    1. ROLE: Act as a helpful tutor. If no resources are found in the context, answer the query generally but state that no specific files were found in the database.
    2. HIERARCHY: For each resource, strictly display the path as: <span class='path-badge'>COURSE > SEGMENT > TOPIC</span>.
    3. SUMMARY: Directly use the 'ai_summary' field. Do not make up information.
    4. SENTIMENT & FEEDBACK: 
       - If 'Community Feedback' contains keywords like 'problem', 'error', or 'hard', add: <div class='warning'>⚠️ Note: Students reported issues with this resource.</div>
       - If upvotes > 5 or feedback is very positive, add: <div class='student-choice'>⭐ Community Choice</div>
    5. OUTPUT FORMAT:
       - Every resource must be inside <div class='resource-card'>.
       - Resource title should be in <h3>.
       - Use <ul> for details like Upvotes and Difficulty.
       - Links MUST use this exact class: <a class='btn-open' href='LINK'>View Resource</a>
    6. TONE: Professional, clean, and academic. Do NOT use Markdown (like **bold**); use <b>bold</b> instead.
    """.formatted(dbContext.replace("\n", "\\n").replace("\"", "\\\""));

        try {
            // 4. Construct JSON Body
            String safeUserQuery = userQuery.replace("\"", "\\\"").replace("\n", " ");
            String fullPrompt = systemPrompt + " USER QUESTION: " + safeUserQuery;
            String jsonBody = "{\"contents\": [{\"parts\": [{\"text\": \"" + fullPrompt + "\"}]}]}";

            // 5. Send Request
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://generativelanguage.googleapis.com/v1beta/models/" + GEMINI_MODEL
                        + ":generateContent?key=" + GEMINI_API_KEY))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            // Debugging
            System.out.println("🤖 AI Response Body: " + response.body());

            if (response.statusCode() != 200) {
                return wrapWithModernUI("<p style='color:red;'>⚠️ API Error (" + response.statusCode() + "): " + response.body() + "</p>");
            }

            // 6. Extract and Clean Response
            Matcher m = Pattern.compile("\"text\"\\s*:\\s*\"([^\"]+)\"").matcher(response.body());
            StringBuilder fullContent = new StringBuilder();
            
            while (m.find()) {
                String text = m.group(1);
                // Clean Unicode and HTML escape characters
                String cleanPart = text
                        .replace("\\u003c", "<")
                        .replace("\\u003e", ">")
                        .replace("\\n", "<br>")
                        .replace("\\\"", "\"")
                        .replace("\\*", "") // Remove Markdown bold
                        .replace("\\u0026", "&");
                fullContent.append(cleanPart);
            }

            if (fullContent.length() > 0) {
                return wrapWithModernUI(fullContent.toString());
            } else {
                return wrapWithModernUI("<p>⚠️ Parsing Error: AI responded but data could not be read.</p>");
            }

        } catch (Exception e) {
            System.err.println("❌ AI Error: " + e.getMessage());
            return wrapWithModernUI("<p style='color:red;'>⚠️ Connection error with ScholarGrid AI.</p>");
        }
    }

    
private String fetchSystematicContext(String keyword) {
    StringBuilder context = new StringBuilder();
    
    // 🌟 ১. ফ্লেক্সিবল সার্চ প্যাটার্ন তৈরি
    // এটি "cse 105 ct1 dsa" কে "%cse%105%ct%1%dsa%" তে রূপান্তর করবে।
    String cleanKeyword = keyword.trim().toLowerCase()
                                 .replace("-", " ") 
                                 .replace("0", "") // CT-01 এবং CT 1 এর পার্থক্য দূর করতে
                                 .replace("  ", " ");
    
    String[] parts = cleanKeyword.split("\\s+");
    StringBuilder patternBuilder = new StringBuilder("%");
    for (String part : parts) {
        if (!part.isEmpty()) patternBuilder.append(part).append("%");
    }
    String searchPattern = patternBuilder.toString();

    // 🌟 ২. ফ্লেক্সিবল SQL (সব ডিটেইলস চেক করবে)
    String sql = """
        SELECT r.*, 
               c.code AS course_name, s.name AS segment_name, t.title AS topic_name,
               (SELECT STRING_AGG('- Note: ' || user_note || ' (Rated: ' || difficulty_rating || ')', E'\\n') 
                FROM user_progress 
                WHERE resource_id = r.id AND user_note IS NOT NULL AND user_note != '') as sentiment,
               
               (SELECT STRING_AGG('- Student Doubt: ' || content, E'\\n') 
                FROM comments 
                WHERE resource_id = r.id AND content IS NOT NULL) as discussions,

               (SELECT COUNT(*) FROM user_progress WHERE resource_id = r.id AND difficulty_rating = 'Easy') as easy_count,
               (SELECT COUNT(*) FROM user_progress WHERE resource_id = r.id AND difficulty_rating = 'Medium') as med_count,
               (SELECT COUNT(*) FROM user_progress WHERE resource_id = r.id AND difficulty_rating = 'Hard') as hard_count
        FROM resources r
        JOIN topics t ON r.topic_id = t.id
        JOIN segments s ON t.segment_id = s.id
        JOIN courses c ON s.course_id = c.id
        WHERE (LOWER(c.code) || ' ' || LOWER(s.name) || ' ' || LOWER(t.title) || ' ' || LOWER(r.title) || ' ' || LOWER(r.tags)) LIKE ?
        AND r.channel_id = ? 
        ORDER BY r.upvotes DESC LIMIT 5; 
    """;

    try (Connection conn = dataSource.getConnection();
         PreparedStatement pstmt = conn.prepareStatement(sql)) {
        
        pstmt.setString(1, searchPattern);
        pstmt.setInt(2, AuthService.CURRENT_CHANNEL_ID); 

        ResultSet rs = pstmt.executeQuery();
        while (rs.next()) {
            context.append("=== RESOURCE ENTRY ===\n")
                   .append("📁 Hierarchy: ").append(rs.getString("course_name")).append(" > ")
                   .append(rs.getString("segment_name")).append(" > ").append(rs.getString("topic_name")).append("\n")
                   
                   .append("📄 Title: ").append(rs.getString("title")).append(" (").append(rs.getString("type")).append(")\n")
                   .append("🏷️ Tags: ").append(rs.getString("tags")).append("\n")
                   .append("📝 Description: ").append(rs.getString("description")).append("\n")
                   
                   .append("📊 Stats: Upvotes(").append(rs.getInt("upvotes")).append("), ")
                   .append("Easy(").append(rs.getInt("easy_count")).append("), ")
                   .append("Med(").append(rs.getInt("med_count")).append("), ")
                   .append("Hard(").append(rs.getInt("hard_count")).append(")\n")
                   
                   .append("🤖 AI Summary: ").append(rs.getString("ai_summary")).append("\n")
                   
                   // স্টুডেন্ট ফিডব্যাক (যেমন: "not so good") এবং ডাউটস (যেমন: "i have doubts") অন্তর্ভুক্ত করা হয়েছে
                   .append("💬 STUDENT SENTIMENT (Progress Notes):\n")
                   .append(rs.getString("sentiment") != null ? rs.getString("sentiment") : "No sentiment data yet.").append("\n")
                   
                   .append("❓ COMMUNITY DOUBTS (Discussion):\n")
                   .append(rs.getString("discussions") != null ? rs.getString("discussions") : "No active doubts.").append("\n")
                   
                   .append("🔗 Link: ").append(rs.getString("link")).append("\n\n");
        }
    } catch (SQLException e) { 
        e.printStackTrace(); 
        context.append("Error: ").append(e.getMessage());
    }
    return context.toString();
}


    // ==========================================================
    // 🎨 4. MODERN UI WRAPPER
    // ==========================================================
   private String wrapWithModernUI(String aiResponse) {
    return """
        <!DOCTYPE html>
        <html>
        <head>
            <style>
                :root {
                    --bg-color: #f7f7f8;
                    --user-msg-bg: #ffffff;
                    --ai-msg-bg: #f7f7f8;
                    --text-color: #374151;
                    --accent-color: #10a37f; /* ChatGPT Green */
                }
                body { 
                    font-family: 'Sentry', 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; 
                    background-color: var(--bg-color); 
                    margin: 0; padding: 0; color: var(--text-color);
                }
                .chat-container { display: flex; flex-direction: column; width: 100%; }
                
                /* চ্যাট বাবল স্টাইল */
                .message {
                    padding: 25px 15%;
                    display: flex;
                    gap: 20px;
                    line-height: 1.6;
                    border-bottom: 1px solid rgba(0,0,0,0.05);
                }
                .ai-message { background-color: var(--ai-msg-bg); }
                .user-message { background-color: var(--user-msg-bg); }
                
                .avatar {
                    width: 35px; height: 35px; border-radius: 4px;
                    display: flex; align-items: center; justify-content: center;
                    font-weight: bold; font-size: 12px; flex-shrink: 0;
                }
                .ai-avatar { background-color: var(--accent-color); color: white; }
                .user-avatar { background-color: #5436da; color: white; }

                .content { flex-grow: 1; font-size: 15px; }
                
                /* রিসোর্স কার্ড স্টাইল */
                .resource-card {
                    background: white;
                    border: 1px solid #e5e7eb;
                    border-radius: 12px;
                    padding: 16px;
                    margin: 15px 0;
                    box-shadow: 0 2px 6px rgba(0,0,0,0.02);
                }
                .path-badge {
                    font-size: 11px; color: var(--accent-color);
                    background: #e6f6f2; padding: 4px 10px;
                    border-radius: 20px; font-weight: 600;
                }
                .btn-open {
                    display: inline-block; background-color: var(--accent-color);
                    color: white; padding: 8px 18px; text-decoration: none;
                    border-radius: 6px; font-size: 14px; font-weight: 500;
                    margin-top: 12px; transition: background 0.2s;
                }
                .btn-open:hover { background-color: #1a7f64; }
                
                h3 { margin-top: 0; color: #111827; }
                ul { padding-left: 20px; }
                li { margin-bottom: 8px; }
            </style>
        </head>
        <body>
            <div class="chat-container">
                <div class="message ai-message">
                    <div class="avatar ai-avatar">SG</div>
                    <div class="content">
                        """ + aiResponse + """
                    </div>
                </div>
            </div>
        </body>
        </html>
        """;
}
}
