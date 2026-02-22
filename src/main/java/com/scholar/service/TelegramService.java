package com.scholar.service;

import org.json.JSONObject;
import org.springframework.stereotype.Service; // 🟢 নতুন
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;

@Service // 🌟 ১. এটিকে একটি স্প্রিং সার্ভিস হিসেবে রেজিস্টার করা হলো
public class TelegramService {

    // ২. ফিল্ডগুলো সরাসরি সিস্টেম এনভায়রনমেন্ট থেকে ডাটা নেবে
    private final String BOT_TOKEN; 
    private final String CHAT_ID; 

    public TelegramService() {
        // 🟢 আপনার ইচ্ছা অনুযায়ী সরাসরি System.getenv() ব্যবহার করা হয়েছে
        this.BOT_TOKEN = System.getenv("TELEGRAM_BOT_TOKEN");
        this.CHAT_ID = System.getenv("TELEGRAM_CHAT_ID");

        if (BOT_TOKEN == null || BOT_TOKEN.isEmpty() || CHAT_ID == null || CHAT_ID.isEmpty()) {
            System.err.println("❌ ERROR: TELEGRAM_BOT_TOKEN or TELEGRAM_CHAT_ID is missing in the environment variables!");
        }
    }

    /**
     * Uploads a file to Telegram and returns the File ID. (Logic Unchanged)
     */
    public String uploadToCloud(File file) {
        if (BOT_TOKEN == null || CHAT_ID == null) {
            return null; 
        }

        String urlString = "https://api.telegram.org/bot" + BOT_TOKEN + "/sendDocument";
        String boundary = Long.toHexString(System.currentTimeMillis());
        String CRLF = "\r\n";

        try {
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setDoOutput(true);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

            try (OutputStream output = conn.getOutputStream();
                 PrintWriter writer = new PrintWriter(new OutputStreamWriter(output, "UTF-8"), true)) {

                // 1. Send Chat ID
                writer.append("--").append(boundary).append(CRLF);
                writer.append("Content-Disposition: form-data; name=\"chat_id\"").append(CRLF);
                writer.append(CRLF).append(CHAT_ID).append(CRLF);

                // 2. Send File
                writer.append("--").append(boundary).append(CRLF);
                writer.append("Content-Disposition: form-data; name=\"document\"; filename=\"").append(file.getName()).append("\"").append(CRLF);
                writer.append(CRLF).flush();
                Files.copy(file.toPath(), output);
                output.flush();
                writer.append(CRLF).flush();
                writer.append("--").append(boundary).append("--").append(CRLF).flush();
            }

            // 3. Get Response
            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) response.append(line);

            // 4. Extract File ID
            JSONObject json = new JSONObject(response.toString());
            return json.getJSONObject("result").getJSONObject("document").getString("file_id");

        } catch (Exception e) {
            e.printStackTrace();
            return null; 
        }
    }

    /**
     * Get direct download URL from File ID. (Logic Unchanged)
     */
    public String getFileDownloadUrl(String fileId) {
        if (BOT_TOKEN == null) return null;
        try {
            // 1. Get File Path from Telegram API
            String urlString = "https://api.telegram.org/bot" + BOT_TOKEN + "/getFile?file_id=" + fileId;
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");

            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) response.append(line);
            
            JSONObject json = new JSONObject(response.toString());
            String filePath = json.getJSONObject("result").getString("file_path");

            // 2. Construct Download URL
            return "https://api.telegram.org/file/bot" + BOT_TOKEN + "/" + filePath;

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}