package com.scholar.controller.ai;

import com.scholar.controller.AIController;
import com.scholar.service.AISchedulerService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.web.WebView;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * AI TUTOR CONTROLLER — Floating AI tutor panel + inline tab AI
 * Path: src/main/java/com/scholar/controller/ai/AITutorController.java
 */
@Component
public class AITutorController {

    @Autowired private AIController aiController;
    @Autowired private AISchedulerService aiService;

    // Inline tab elements (wired by DashboardController after FXML init)
    private WebView aiWebView;
    private TextField aiTopicInput;
    private TextField aiQuestionInput;
    private Button aiAskBtn;

    public void initInlineTab(WebView aiWebView,
                               TextField aiTopicInput,
                               TextField aiQuestionInput,
                               Button aiAskBtn) {
        this.aiWebView       = aiWebView;
        this.aiTopicInput    = aiTopicInput;
        this.aiQuestionInput = aiQuestionInput;
        this.aiAskBtn        = aiAskBtn;

        if (aiWebView == null || aiAskBtn == null) {
            System.err.println("❌ AITutorController: aiWebView or aiAskBtn is NULL!");
            return;
        }

        aiWebView.getEngine().loadContent("""
            <html><body style='font-family: Arial; text-align: center; margin-top: 10%;
            color: #2c3e50; background-color: #f4f6f7;'>
            <h2>👋 ScholarGrid AI Ready!</h2>
            <p>Type a topic or question to start.</p>
            </body></html>
            """);

        aiAskBtn.setOnAction(e -> {
            String topic    = (aiTopicInput != null) ? aiTopicInput.getText().trim() : "";
            String question = aiQuestionInput.getText().trim();
            if (topic.isEmpty() && question.isEmpty()) return;
            aiWebView.getEngine().loadContent("<h3>🤖 AI Thinking...</h3>");
            aiAskBtn.setDisable(true);
            new Thread(() -> {
                try {
                    String response = aiController.askSmartAITutor(question, topic + " " + question);
                    Platform.runLater(() -> {
                        aiWebView.getEngine().loadContent(response);
                        aiAskBtn.setDisable(false);
                    });
                } catch (Exception ex) { ex.printStackTrace(); }
            }).start();
        });
    }

    // ----------------------------------------------------------
    // FLOATING PANEL
    // ----------------------------------------------------------
    @FXML
    public void showAITutorPanel() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("🤖 ScholarGrid AI Tutor");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        VBox layout = new VBox(15); layout.setPadding(new Insets(15));
        layout.setPrefSize(650, 550); layout.setStyle("-fx-background-color: #f4f6f7;");

        WebView webView = new WebView(); VBox.setVgrow(webView, Priority.ALWAYS);
        webView.getEngine().loadContent("""
            <html><body style='font-family: Arial; text-align: center; margin-top: 15%; color: #2c3e50;'>
            <h2 style='color: #8e44ad;'>👋 Hello! I am your ScholarGrid AI.</h2>
            <p>Ask me what to study!</p></body></html>
            """);

        TextField topicInput = new TextField(); topicInput.setPromptText("Topic (e.g., Recursion)");
        topicInput.setPrefWidth(150); topicInput.setStyle("-fx-padding: 10; -fx-background-radius: 10;");
        TextField questionInput = new TextField(); questionInput.setPromptText("Ask your question here...");
        HBox.setHgrow(questionInput, Priority.ALWAYS);
        questionInput.setStyle("-fx-padding: 10; -fx-background-radius: 10;");

        Button askBtn = new Button("Ask AI ✨");
        askBtn.setStyle("-fx-background-color: #8e44ad; -fx-text-fill: white; -fx-font-weight: bold; "
            + "-fx-background-radius: 10; -fx-cursor: hand; -fx-padding: 10 20;");
        askBtn.setOnAction(e -> {
            String topic    = topicInput.getText().trim();
            String question = questionInput.getText().trim();
            if (topic.isEmpty() || question.isEmpty()) {
                new Alert(Alert.AlertType.WARNING, "Please enter both Topic and Question!").show();
                return;
            }
            webView.getEngine().loadContent(
                "<html><body style='font-family: Arial; text-align: center; margin-top: 20%;'>"
                + "<h3>🤖 AI analyzing... ⏳</h3></body></html>");
            askBtn.setDisable(true);
            new Thread(() -> {
                String aiHtmlResponse = aiController.askSmartAITutor(question, topic);
                Platform.runLater(() -> {
                    webView.getEngine().loadContent(
                        "<html><body style='font-family: Arial; padding: 15px; line-height: 1.6;'>"
                        + aiHtmlResponse + "</body></html>");
                    askBtn.setDisable(false);
                    questionInput.clear();
                });
            }).start();
        });

        // Open links in system browser
        webView.getEngine().locationProperty().addListener((obs, oldLoc, newLoc) -> {
            if (newLoc != null && !newLoc.isEmpty() && newLoc.startsWith("http")) {
                Platform.runLater(() -> webView.getEngine().loadContent(
                    "<html><body style='font-family: Arial; text-align: center; margin-top: 20%;'>"
                    + "<h3>🌍 Opening in browser...</h3></body></html>"));
                try { java.awt.Desktop.getDesktop().browse(new URI(newLoc)); }
                catch (Exception ex) { ex.printStackTrace(); }
            }
        });

        HBox inputBox = new HBox(10, topicInput, questionInput, askBtn);
        inputBox.setAlignment(Pos.CENTER);
        layout.getChildren().addAll(webView, inputBox);
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