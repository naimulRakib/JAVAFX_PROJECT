package com.scholar.util;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * UTILITY: Google Drive Preview, Download, External Links
 * Path: src/main/java/com/scholar/util/DriveHelper.java
 */
@Component
public class DriveHelper {

    public String extractDriveId(String url) {
        if (url == null || !url.contains("drive.google.com")) return null;
        Matcher m = Pattern.compile("/d/([a-zA-Z0-9-_]+)").matcher(url);
        if (m.find()) return m.group(1);
        m = Pattern.compile("id=([a-zA-Z0-9-_]+)").matcher(url);
        return m.find() ? m.group(1) : null;
    }

    public void showInAppPreview(String url, String title) {
        try {
            Stage previewStage = new Stage();
            previewStage.setTitle("👁️ Preview: " + title);
            WebView webView = new WebView();
            webView.getEngine().load(url);
            previewStage.setScene(new Scene(webView, 900, 650));
            previewStage.show();
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Preview Error", "Could not load preview.");
        }
    }

    public void directDownloadFile(String url, String title) {
        if (url == null || url.isEmpty()) return;
        if (url.contains("drive.google.com")) {
            String fileId = extractDriveId(url);
            if (fileId != null)
                startFileDownloadProcess(
                    "https://drive.google.com/uc?export=download&id=" + fileId, title, ".pdf");
        } else if (url.contains("youtube.com") || url.contains("youtu.be")
                || url.contains("t.me") || url.contains("telegram.me")) {
            openExternalDownloader(url);
        } else {
            showAlert(Alert.AlertType.WARNING, "Unsupported Link",
                "Direct download supports Google Drive, YouTube, and Telegram links.");
        }
    }

    public void openExternalDownloader(String mediaUrl) {
        try {
            javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
            javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
            content.putString(mediaUrl);
            clipboard.setContent(content);
            new Alert(Alert.AlertType.INFORMATION, "Link copied! Opening secure downloader...").showAndWait();
            if (java.awt.Desktop.isDesktopSupported())
                java.awt.Desktop.getDesktop().browse(new URI("https://cobalt.tools"));
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "System Error", "Could not open downloader: " + e.getMessage());
        }
    }

    public void startFileDownloadProcess(String directDownloadUrl, String title, String extension) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save File");
        fileChooser.setInitialFileName(title.replaceAll("[^a-zA-Z0-9.-]", "_") + extension);
        fileChooser.setInitialDirectory(new File(System.getProperty("user.home"), "Desktop"));
        File destFile = fileChooser.showSaveDialog(null);
        if (destFile != null) {
            Alert dlAlert = new Alert(Alert.AlertType.INFORMATION, "Downloading... Please wait.");
            dlAlert.show();
            new Thread(() -> {
                try (InputStream in = new URL(directDownloadUrl).openStream()) {
                    Files.copy(in, destFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    Platform.runLater(() -> {
                        dlAlert.close();
                        showAlert(Alert.AlertType.INFORMATION, "Success 🎉", "Saved to: " + destFile.getName());
                    });
                } catch (Exception e) {
                    Platform.runLater(() -> {
                        dlAlert.close();
                        showAlert(Alert.AlertType.ERROR, "Download Failed", e.getMessage());
                    });
                }
            }).start();
        }
    }

    public void showAlert(Alert.AlertType type, String title, String content) {
        Platform.runLater(() -> {
            Alert alert = new Alert(type);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(content);
            alert.showAndWait();
        });
    }
}