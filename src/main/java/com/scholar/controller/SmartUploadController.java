package com.scholar.controller;

import com.scholar.service.AIOrchestrator;
import com.scholar.service.TelegramService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import org.json.JSONObject;
import java.io.File;
import org.springframework.beans.factory.annotation.Autowired; // 🟢 নতুন
import org.springframework.stereotype.Controller;

public class SmartUploadController {

    @FXML private Label statusLabel;
    @FXML private TextArea aiOutputArea; // To show the tags
    @FXML private Button uploadBtn;

  @Autowired 
    private TelegramService telegramService;

    @Autowired 
    private AIOrchestrator aiBrain;

    @FXML
    public void onSmartUploadClick() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select CT Question / Note");
        File file = fileChooser.showOpenDialog(uploadBtn.getScene().getWindow());

        if (file != null) {
            runSmartUpload(file);
        }
    }

    private void runSmartUpload(File file) {
        uploadBtn.setDisable(true);
        statusLabel.setText("⏳ Uploading to Telegram Cloud...");

        new Thread(() -> {
            // 1. Upload to Telegram
            String fileId = telegramService.uploadToCloud(file);
            
            if (fileId == null) {
                Platform.runLater(() -> {
                    statusLabel.setText("❌ Upload Failed!");
                    uploadBtn.setDisable(false);
                });
                return;
            }

            Platform.runLater(() -> statusLabel.setText("🤖 AI Analyzing..."));

            // 2. AI Analysis 
            // (In a real app, you'd extract text from the PDF here. 
            // For now, we send the filename as a hint to the AI)
            JSONObject tags = aiBrain.autoTagResource("File name: " + file.getName());

            // 3. Update UI
            Platform.runLater(() -> {
                statusLabel.setText("✅ Done! File ID: " + fileId);
                aiOutputArea.setText(tags.toString(4)); // Pretty print JSON
                uploadBtn.setDisable(false);
                
                // TODO: Save 'fileId' and 'tags' to Supabase here
            });

        }).start();
    }
}