package com.scholar.service;

import io.github.cdimascio.dotenv.Dotenv; 
import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;

public class TelegramService {

 
    private static final Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();

   
    private static final String BOT_TOKEN = dotenv.get("TELEGRAM_BOT_TOKEN"); 
    private static final String CHAT_ID = dotenv.get("TELEGRAM_CHAT_ID"); 

    public TelegramService() {
       
        if (BOT_TOKEN == null || BOT_TOKEN.isEmpty() || CHAT_ID == null || CHAT_ID.isEmpty()) {
            System.err.println("❌ ERROR: TELEGRAM_BOT_TOKEN or TELEGRAM_CHAT_ID is missing in the .env file!");
        }
    }

    /**
     * Uploads a file to Telegram and returns the File ID.
     */
    public String uploadToCloud(File file) {
        if (BOT_TOKEN == null || CHAT_ID == null) {
            return null; // টোকেন না থাকলে আপলোড বাতিল হবে
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
            return null; // Upload failed
        }
    }



    /**
     * Get direct download URL from File ID
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