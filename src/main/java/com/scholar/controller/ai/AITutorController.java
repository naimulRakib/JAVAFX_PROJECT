package com.scholar.controller.ai;

import com.scholar.controller.AIController;
import com.scholar.service.AISchedulerService;
import com.scholar.service.RAGService;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.web.WebView;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * AI TUTOR CONTROLLER — Floating AI tutor panel + inline tab AI
 * Path: src/main/java/com/scholar/controller/ai/AITutorController.java
 */
@Component
public class AITutorController {

    @Autowired private AIController aiController;
    @Autowired private AISchedulerService aiService;
    @Autowired private RAGService ragService;
    @Autowired private JdbcTemplate jdbc;

    // Inline tab elements (wired by DashboardController after FXML init)
    private WebView aiWebView;
    private TextField aiTopicInput;
    private TextField aiQuestionInput;
    private Button aiAskBtn;

    // Current logged-in user ID (set from DashboardController)
    private String currentUserId = null;

    /** Called by DashboardController to pass the logged-in user's UUID */
    public void setCurrentUserId(String userId) {
        this.currentUserId = userId;
    }

    // ----------------------------------------------------------
    // INLINE TAB INIT
    // ----------------------------------------------------------
    public void initInlineTab(WebView aiWebView,
                               TextField aiTopicInput,
                               TextField aiQuestionInput,
                               Button aiAskBtn) {
        this.aiWebView       = aiWebView;
        this.aiTopicInput    = aiTopicInput;
        this.aiQuestionInput = aiQuestionInput;
        this.aiAskBtn        = aiAskBtn;

        if (aiWebView == null) {
            System.err.println("❌ AITutorController: aiWebView is NULL!");
            return;
        }

        // Load the RAG chat HTML file
        URL htmlUrl = getClass().getResource("/static/ai_chat.html");
        if (htmlUrl == null) {
            System.err.println("❌ ai_chat.html not found in /static/! Falling back to inline.");
            aiWebView.getEngine().loadContent("""
                <html><body style='font-family: Arial; text-align: center; margin-top: 10%;
                color: #2c3e50; background-color: #f4f6f7;'>
                <h2>👋 ScholarGrid AI Ready!</h2>
                <p>ai_chat.html not found. Place it in src/main/resources/static/</p>
                </body></html>
                """);
            return;
        }

        aiWebView.getEngine().load(htmlUrl.toExternalForm());

        // After HTML fully loads, inject userId + courses into the WebView JS
        aiWebView.getEngine().getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == Worker.State.SUCCEEDED) {
                injectUserContext(aiWebView);
            }
        });

        // Hide the old JavaFX input bar if it exists (aiAskBtn etc.)
        // The new HTML has its own input bar built-in
        if (aiAskBtn != null) aiAskBtn.setVisible(false);
        if (aiTopicInput != null) aiTopicInput.setVisible(false);
        if (aiQuestionInput != null) aiQuestionInput.setVisible(false);
    }

    /**
     * Injects userId and course list into the WebView JavaScript context
     */
    private void injectUserContext(WebView webView) {
        // Inject user ID
        if (currentUserId != null && !currentUserId.isBlank()) {
            webView.getEngine().executeScript(
                "if(typeof setUserId === 'function') setUserId('" + currentUserId + "');"
            );
        }

        // Fetch courses from DB and inject as JSON
        try {
            List<Map<String, Object>> courses = jdbc.queryForList(
                "SELECT code, title FROM courses ORDER BY code"
            );

            StringBuilder json = new StringBuilder("[");
            for (int i = 0; i < courses.size(); i++) {
                Map<String, Object> c = courses.get(i);
                json.append("{\"code\":\"")
                    .append(escape(String.valueOf(c.get("code"))))
                    .append("\",\"title\":\"")
                    .append(escape(String.valueOf(c.get("title"))))
                    .append("\"}");
                if (i < courses.size() - 1) json.append(",");
            }
            json.append("]");

            String script = "if(typeof setCourses === 'function') setCourses('" 
                          + json.toString().replace("'", "\\'") + "');";
            webView.getEngine().executeScript(script);

        } catch (Exception e) {
            System.err.println("❌ Failed to load courses for AI WebView: " + e.getMessage());
        }
    }

    /** Escape special chars for JS string injection */
    private String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("'", "\\'")
                .replace("\n", " ")
                .replace("\r", "");
    }

    // ----------------------------------------------------------
    // FLOATING PANEL (updated to also use ai_chat.html)
    // ----------------------------------------------------------
    @FXML
    public void showAITutorPanel() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("🤖 ScholarGrid AI Tutor");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        VBox layout = new VBox();
        layout.setPadding(new Insets(0));
        layout.setPrefSize(750, 620);

        WebView webView = new WebView();
        VBox.setVgrow(webView, Priority.ALWAYS);

        // Load the same RAG chat HTML
        URL htmlUrl = getClass().getResource("/static/ai_chat.html");
        if (htmlUrl != null) {
            webView.getEngine().load(htmlUrl.toExternalForm());
            webView.getEngine().getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
                if (newState == Worker.State.SUCCEEDED) {
                    injectUserContext(webView);
                }
            });
        } else {
            // Fallback
            webView.getEngine().loadContent("""
                <html><body style='font-family: Arial; text-align: center; margin-top: 15%; color: #2c3e50;'>
                <h2 style='color: #8e44ad;'>⚠️ ai_chat.html not found</h2>
                <p>Place it in src/main/resources/static/</p>
                </body></html>
                """);
        }

        // Open external links in system browser
        webView.getEngine().locationProperty().addListener((obs, oldLoc, newLoc) -> {
            if (newLoc != null && !newLoc.isEmpty() && newLoc.startsWith("http")
                    && !newLoc.contains("localhost")) {
                Platform.runLater(() -> webView.getEngine().loadContent(
                    "<html><body style='font-family: Arial; text-align: center; margin-top: 20%;'>"
                    + "<h3>🌍 Opening in browser...</h3></body></html>"));
                try { java.awt.Desktop.getDesktop().browse(new URI(newLoc)); }
                catch (Exception ex) { ex.printStackTrace(); }
            }
        });

        layout.getChildren().add(webView);
        dialog.getDialogPane().setContent(layout);
        dialog.showAndWait();
    }

    // ----------------------------------------------------------
    // AI ROUTINE GENERATION (called by DashboardController)
    // ----------------------------------------------------------
    public List<com.scholar.model.StudyTask> generateSchedule(String userText) {
        try {
            return aiService.generateSchedule(userText);
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }
}