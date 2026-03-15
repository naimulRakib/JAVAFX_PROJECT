package com.scholar.controller;

import com.scholar.model.AIResource;
import com.scholar.service.AIOrchestrator;
import com.scholar.service.AIResourceService;
import com.scholar.service.AuthService;
import com.scholar.service.TelegramService;
import com.scholar.util.PopupHelper;
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

    @Autowired
    private AIResourceService aiResourceService;

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
            String downloadUrl = telegramService.getFileDownloadUrl(fileId);

            AIResource resource = new AIResource();
            resource.setTitle(file.getName());
            resource.setDescription("Smart Upload Resource");
            resource.setResourceType("SMART_UPLOAD");
            resource.setTelegramFileId(fileId);
            resource.setTelegramDownloadUrl(downloadUrl);
            resource.setTags(tags.toString());
            if (AuthService.CURRENT_USER_ID != null) {
                resource.setCreatedByUserId(AuthService.CURRENT_USER_ID);
            }
            resource.setPublished(true);
            boolean saved = aiResourceService != null && aiResourceService.saveResource(resource);

            // 3. Update UI
            Platform.runLater(() -> {
                statusLabel.setText("✅ Done! File ID: " + fileId);
                aiOutputArea.setText(tags.toString(4)); // Pretty print JSON
                uploadBtn.setDisable(false);

                if (saved) {
                    PopupHelper.showInfo(statusLabel.getScene().getWindow(), "Saved",
                        "File metadata saved to database.");
                } else {
                    PopupHelper.showError(statusLabel.getScene().getWindow(), "Save Failed",
                        "Could not save metadata to database.");
                }
            });

        }).start();
    }
}
