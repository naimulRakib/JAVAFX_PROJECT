package com.scholar.service;

import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;

@Service
public class TelegramService {

    @Value("${telegram.bot.token}")
    private String BOT_TOKEN; 

    @Value("${telegram.chat.id}")
    private String CHAT_ID; 

    @PostConstruct
    public void init() {
        if (BOT_TOKEN == null || BOT_TOKEN.isEmpty() || CHAT_ID == null || CHAT_ID.isEmpty()) {
            System.err.println("❌ ERROR: Telegram config missing in application.properties!");
        } else {
            System.out.println("✅ Telegram Service initialized successfully!");
        }
    }

    /**
     * Uploads any file (Binary/Text) to Telegram and returns the File ID.
     */
    public String uploadToCloud(File file) {
        if (BOT_TOKEN == null || CHAT_ID == null) {
            System.err.println("❌ Telegram Token or Chat ID is null.");
            return null; 
        }

        String urlString = "https://api.telegram.org/bot" + BOT_TOKEN + "/sendDocument";
        String boundary = "----WebKitFormBoundary" + System.currentTimeMillis();
        String CRLF = "\r\n";

        try {
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setDoOutput(true);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

            // 🌟 FIX: Using DataOutputStream to safely send raw binary file data
            try (OutputStream output = conn.getOutputStream();
                 DataOutputStream writer = new DataOutputStream(output)) {

                // 1. Send Chat ID (Text part)
                writer.writeBytes("--" + boundary + CRLF);
                writer.writeBytes("Content-Disposition: form-data; name=\"chat_id\"" + CRLF);
                writer.writeBytes(CRLF);
                writer.writeBytes(CHAT_ID + CRLF);

                // 2. Send File (Binary part)
                writer.writeBytes("--" + boundary + CRLF);
                writer.writeBytes("Content-Disposition: form-data; name=\"document\"; filename=\"" + file.getName() + "\"" + CRLF);
                writer.writeBytes("Content-Type: application/octet-stream" + CRLF); // Tells Telegram it's a file
                writer.writeBytes(CRLF);
                writer.flush();

                // Stream the file directly
                Files.copy(file.toPath(), output);
                output.flush();

                writer.writeBytes(CRLF);
                writer.writeBytes("--" + boundary + "--" + CRLF);
                writer.flush();
            }

            // 3. Get Response
            int responseCode = conn.getResponseCode();
            InputStream is = (responseCode >= 200 && responseCode < 300) ? conn.getInputStream() : conn.getErrorStream();
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) response.append(line);

            if (responseCode != 200) {
                System.err.println("❌ Telegram API Error: " + response.toString());
                return null;
            }

            // 4. Extract File ID
            JSONObject json = new JSONObject(response.toString());
            return json.getJSONObject("result").getJSONObject("document").getString("file_id");

        } catch (Exception e) {
            e.printStackTrace();
            return null; 
        }
    }

    /**
     * Get direct download URL from File ID.
     */
    public String getFileDownloadUrl(String fileId) {
        if (BOT_TOKEN == null) return null;
        try {
            String urlString = "https://api.telegram.org/bot" + BOT_TOKEN + "/getFile?file_id=" + fileId;
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");

            int responseCode = conn.getResponseCode();
            InputStream is = (responseCode >= 200 && responseCode < 300) ? conn.getInputStream() : conn.getErrorStream();

            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) response.append(line);
            
            if (responseCode != 200) {
                System.err.println("❌ Telegram GetFile Error: " + response.toString());
                return null;
            }

            JSONObject json = new JSONObject(response.toString());
            String filePath = json.getJSONObject("result").getString("file_path");

            return "https://api.telegram.org/file/bot" + BOT_TOKEN + "/" + filePath;

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}