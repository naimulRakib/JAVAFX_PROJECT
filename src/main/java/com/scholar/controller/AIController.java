package com.scholar.controller;


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



import com.scholar.service.*;





// 🌟 আপনার প্রজেক্টের ডাটাবেস ক্লাসের লোকেশন অনুযায়ী এটি ইমপোর্ট করে নিন
// import your.package.name.DatabaseConnection; 

public class AIController {

    // ইউজারের নিজস্ব API Key (BYOK) অথবা আপনার ডিফল্ট Key
    // ⚠️ এখানে আপনার Google AI Studio থেকে পাওয়া Gemini API Key টি বসান
    private String GEMINI_API_KEY = "AIzaSyCsgRERKdg0pDtapy5nuYJeFfiotr5V6MM"; 

    public void setApiKey(String key) {
        this.GEMINI_API_KEY = key;
    }

    // ==========================================================
    // 🤖 1. AUTO-SUMMARIZER (রিসোর্স আপলোডের সময় কল করবেন)
    // ==========================================================
    public String generateResourceSummary(String title, String link, String tags, String description) {
        if (GEMINI_API_KEY == null || GEMINI_API_KEY.equals("YOUR_GEMINI_API_KEY")) {
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
            // JSON-এর জন্য স্পেশাল ক্যারেক্টার ক্লিন করা (যাতে API ক্র্যাশ না করে)
            String cleanPrompt = prompt.replace("\"", "\\\"").replace("\n", " ");
            String jsonBody = "{\"contents\": [{\"parts\": [{\"text\": \"" + cleanPrompt + "\"}]}]}";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=" + GEMINI_API_KEY))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(15))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

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
    // 🧠 2. FETCH CONTEXT (ডাটাবেস থেকে টপ রিসোর্স খোঁজা)
    // ==========================================================
    private String fetchComprehensiveContext(String keyword) {
        StringBuilder context = new StringBuilder();
        
        // 🌟 ম্যাজিকাল SQL: রিসোর্সের ডাটা এবং ওই রিসোর্সের সব কমেন্ট একসাথে আনা (STRING_AGG ব্যবহার করে)
        String sql = """
            SELECT r.*, 
                   (SELECT STRING_AGG('User Says: ' || content, ' | ') 
                    FROM comments WHERE resource_id = r.id) as discussion
            FROM resources r
            WHERE r.title ILIKE ? OR r.tags ILIKE ? OR r.description ILIKE ?
            ORDER BY r.upvotes DESC LIMIT 5
        """;

        try (java.sql.Connection conn = com.scholar.service.DatabaseConnection.getConnection();
             java.sql.PreparedStatement pstmt = conn.prepareStatement(sql)) {

            String searchParam = "%" + keyword.trim() + "%";
            pstmt.setString(1, searchParam);
            pstmt.setString(2, searchParam);
            pstmt.setString(3, searchParam);

            java.sql.ResultSet rs = pstmt.executeQuery();
            int count = 1;

            while (rs.next()) {
                context.append("Resource ").append(count++).append(":\n")
                       .append("- Title: ").append(rs.getString("title")).append("\n")
                       .append("- Link: ").append(rs.getString("link")).append("\n")
                       .append("- AI Summary: ").append(rs.getString("ai_summary")).append("\n")
                       .append("- Discussion (Community Voice): ").append(rs.getString("discussion") != null ? rs.getString("discussion") : "No discussion yet.").append("\n")
                       .append("- Upvotes: ").append(rs.getInt("upvotes")).append("\n\n");
            }
        } catch (Exception e) {
            System.err.println("❌ Error fetching AI context: " + e.getMessage());
        }
        return context.toString();
    }

    // ==========================================================
    // 💬 3. SYSTEMATIC AI TUTOR (RANKED & CATEGORIZED)
    // ==========================================================
    public String askSmartAITutor(String userQuery, String searchKeyword) {
    // ১. এপিআই কী চেক
    if (GEMINI_API_KEY == null || GEMINI_API_KEY.equals("YOUR_GEMINI_API_KEY")) {
        return "<p style='color:red;'>⚠️ Error: API Key is missing! Please connect your AI Key first.</p>";
    }

    // ২. ডাটাবেস থেকে সিস্টেমেটিক কন্টেন্ট আনা (এখানে আপনার ai_summary এবং discussion ডাটা থাকে)
    String dbContext = fetchSystematicContext(searchKeyword);

    if (dbContext.trim().isEmpty()) {
        return "<p>ScholarGrid-এ '<b>" + searchKeyword + "</b>' সংক্রান্ত কোনো রিসোর্স (CT/Final/Basic) পাওয়া যায়নি। অন্য কী-ওয়ার্ড ট্রাই করুন!</p>";
    }

    // ৩. প্রম্পট ইঞ্জিনিয়ারিং (AI Summary এবং Discussion ডাটাকে গুরুত্ব দেওয়া)
    String systemPrompt = "You are 'ScholarGrid AI'. I am providing you with ranked resources and actual student discussions. " +
            "--- CONTEXT ---\\n" + dbContext.replace("\n", "\\n") + "\\n" +
            "YOUR STRICT INSTRUCTIONS:\\n" +
            "1. List resources by Course > Segment > Topic.\\n" +
            "2. SUMMARY: Use the UNIQUE 'ai_summary' from context. Do NOT generalize.\\n" +
            "3. SENTIMENT: Check the 'Community Feedback'. If a student says 'problem' (like ID 23), warn the user. If they say 'better', highlight it as 'Student Choice'.\\n" +
            "4. RANKING: Use this formula for internal priority: " +
            "Score = (Upvotes * 1.5) + (CommentsCount * 0.5)\\n" +
            "5. OUTPUT: Clean HTML with <h3>, <ul>, and clickable buttons.";

   try {
    // ১. JSON বডি তৈরি (সঠিক এস্কেপিং সহ)
    String safeUserQuery = userQuery.replace("\"", "\\\"").replace("\n", " ");
    String jsonBody = "{\"contents\": [{\"parts\": [{\"text\": \"" + systemPrompt + " USER QUESTION: " + safeUserQuery + "\"}]}]}";

    // ২. জেমিনি ১.৫ ফ্ল্যাশ ব্যবহার (স্ট্যাবিলিটি নিশ্চিত করতে)
    java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
            .uri(java.net.URI.create("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=" + GEMINI_API_KEY))
            .header("Content-Type", "application/json")
            .timeout(java.time.Duration.ofSeconds(25))
            .POST(java.net.http.HttpRequest.BodyPublishers.ofString(jsonBody))
            .build();

    java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
    java.net.http.HttpResponse<String> response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());

    // ৩. ডিবাগিং
    System.out.println("🤖 AI Response Body: " + response.body());

    if (response.statusCode() != 200) {
        return wrapWithModernUI("<p style='color:red;'>⚠️ API Error (" + response.statusCode() + "): Quota exceeded or invalid key.</p>");
    }

    // ৪. উন্নত Regex এবং Unicode Fix (\u003c সমাধান)
    java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"text\"\\s*:\\s*\"([^\"]+)\"").matcher(response.body());
    
    StringBuilder fullContent = new StringBuilder();
    while (m.find()) {
        String text = m.group(1);
        
        // 🌟 ইউনিকোড এবং এইচটিএমএল ক্যারেক্টার ক্লিন করা
        String cleanPart = text
                .replace("\\u003c", "<")
                .replace("\\u003e", ">")
                .replace("\\n", "<br>")
                .replace("\\\"", "\"")
                .replace("\\*", "") // Markdown বোল্ড সাইন রিমুভ
                .replace("\\u0026", "&");
        
        fullContent.append(cleanPart);
    }

    if (fullContent.length() > 0) {
        // 🌟 ৫. আপনার আধুনিক HTML/CSS টেমপ্লেট দিয়ে র‍্যাপ করা
        return wrapWithModernUI(fullContent.toString());
    } else {
        return wrapWithModernUI("<p>⚠️ Parsing Error: এআই উত্তর দিয়েছে কিন্তু ডাটা পড়া যাচ্ছে না।</p>");
    }

} catch (Exception e) {
    System.err.println("❌ AI Error: " + e.getMessage());
    return wrapWithModernUI("<p style='color:red;'>⚠️ Connection error with ScholarGrid AI.</p>");
}}


    // ==========================================================
    // 🧠 2. SYSTEMATIC FETCH: GET RANKED RESOURCES + FEEDBACK
    // ==========================================================
   private String fetchSystematicContext(String keyword) {
    StringBuilder context = new StringBuilder();
    String searchParam = "%" + keyword.trim() + "%";

    // 🌟 JOIN Logic: Course > Segment > Topic এবং র‍্যাঙ্কিং
    String sql = """
        SELECT r.title, r.link, r.type, r.difficulty, r.upvotes, r.ai_summary,
               c.code AS course, s.name AS segment, t.title AS topic,
               (SELECT STRING_AGG('User Note: ' || content, ' | ') FROM comments WHERE resource_id = r.id) as discussion
        FROM resources r
        JOIN topics t ON r.topic_id = t.id
        JOIN segments s ON t.segment_id = s.id
        JOIN courses c ON s.course_id = c.id
        WHERE (c.code ILIKE ? OR r.title ILIKE ? OR r.tags ILIKE ?)
        AND r.channel_id = ? 
        ORDER BY r.upvotes DESC LIMIT 5; 
    """;

    try (Connection conn = DatabaseConnection.getConnection();
         PreparedStatement pstmt = conn.prepareStatement(sql)) {
        
        pstmt.setString(1, searchParam);
        pstmt.setString(2, searchParam);
        pstmt.setString(3, searchParam);
        pstmt.setInt(4, AuthService.CURRENT_CHANNEL_ID); 

        ResultSet rs = pstmt.executeQuery();
        while (rs.next()) {
            context.append("--- DATA ENTRY ---\n")
                   .append("Hierarchy: ").append(rs.getString("course")).append(" > ")
                   .append(rs.getString("segment")).append(" > ").append(rs.getString("topic")).append("\n")
                   .append("Resource: ").append(rs.getString("title")).append("\n")
                   .append("Discussion Data: ").append(rs.getString("discussion") != null ? rs.getString("discussion") : "No comments.").append("\n")
                   .append("AI Summary: ").append(rs.getString("ai_summary")).append("\n")
                   .append("Link: ").append(rs.getString("link")).append("\n\n");
        }
    } catch (SQLException e) { e.printStackTrace(); }
    return context.toString();
}

private String wrapWithModernUI(String aiResponse) {
    // আপনার দেওয়া স্টাইল এবং স্ট্রাকচার এখানে থাকবে
    return "<!DOCTYPE html><html><head><style>" +
        "body { font-family: 'Segoe UI', sans-serif; background-color: #f4f7f6; margin: 0; padding: 15px; }" +
        ".chat-container { max-width: 100%; margin: auto; }" +
        ".ai-message { background: white; border-radius: 12px; padding: 15px; box-shadow: 0 4px 10px rgba(0,0,0,0.05); border-left: 5px solid #8e44ad; line-height: 1.6; }" +
        ".resource-card { border: 1px solid #eee; border-radius: 8px; padding: 12px; margin-top: 12px; background: #fff; border-left: 3px solid #3498db; }" +
        ".rank-badge { background: #f1c40f; color: black; padding: 2px 6px; border-radius: 4px; font-size: 10px; font-weight: bold; text-transform: uppercase; }" +
        ".warning { color: #e74c3c; font-weight: bold; background: #fff5f5; padding: 8px; border-radius: 4px; margin-top: 8px; font-size: 13px; border: 1px solid #ffe3e3; }" +
        ".btn-read { display: inline-block; background: #8e44ad; color: white; padding: 8px 16px; text-decoration: none; border-radius: 4px; margin-top: 8px; font-size: 13px; font-weight: bold; }" +
        "</style></head><body>" +
        "<div class='chat-container'><div class='ai-message'>" + aiResponse + "</div></div>" +
        "</body></html>";
}


}