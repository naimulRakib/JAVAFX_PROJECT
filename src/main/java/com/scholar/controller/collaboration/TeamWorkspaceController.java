package com.scholar.controller.collaboration;

import com.scholar.service.CollaborationService;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * TEAM WORKSPACE CONTROLLER — Owner dashboard, member approval, join requirements
 * Path: src/main/java/com/scholar/controller/collaboration/TeamWorkspaceController.java
 */
@Component
public class TeamWorkspaceController {

    @Autowired private CollaborationService collaborationService;
    @Autowired private ChatController chatController;

    // ----------------------------------------------------------
    // LOAD ROOM VIEW (route by user status)
    // ----------------------------------------------------------
    public void loadRoomView(CollaborationService.Post post, VBox roomContainer) {
        roomContainer.getChildren().clear();
        String myStatus = collaborationService.getMyStatus(post.id());
        switch (myStatus) {
            case "OWNER"    -> showOwnerDashboard(post, roomContainer);
            case "APPROVED" -> chatController.showChatRoom(post, roomContainer);
            case "PENDING"  -> {
                VBox pendingBox = new VBox(15); pendingBox.setAlignment(Pos.CENTER);
                Label status = new Label("⏳ Application Sent");
                status.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #e67e22;");
                pendingBox.getChildren().addAll(status, new Label("Waiting for owner approval."));
                roomContainer.getChildren().add(pendingBox);
            }
            default -> showRequirementZone(post, roomContainer);
        }
    }

    // ----------------------------------------------------------
    // REQUIREMENT ZONE (apply to join)
    // ----------------------------------------------------------
    public void showRequirementZone(CollaborationService.Post post, VBox roomContainer) {
        VBox reqBox = new VBox(15);
        reqBox.setStyle("-fx-padding: 30; -fx-background-color: white; "
            + "-fx-background-radius: 10; -fx-border-color: #e2e8f0;");
        reqBox.getChildren().addAll(
            new Label("📝 Join Team: " + post.title()),
            new Label("Please answer the following questions:"));

        List<CollaborationService.Requirement> questions = collaborationService.getRequirements(post.id());
        List<TextField> answers = new ArrayList<>();
        for (var q : questions) {
            Label qLbl = new Label("• " + q.question());
            qLbl.setStyle("-fx-font-weight: bold;");
            TextField ansField = new TextField(); ansField.setPromptText("Your answer...");
            answers.add(ansField);
            reqBox.getChildren().addAll(qLbl, ansField);
        }

        Button applyBtn = new Button("Submit Application 🚀");
        applyBtn.setStyle("-fx-background-color: #10b981; -fx-text-fill: white; "
            + "-fx-font-weight: bold; -fx-cursor: hand;");
        applyBtn.setOnAction(e -> {
            List<String> answerTexts = answers.stream().map(TextField::getText).toList();
            if (answerTexts.stream().anyMatch(String::isEmpty) && !questions.isEmpty()) {
                showError("Please answer all questions."); return;
            }
            if (collaborationService.applyToTeamWithAnswers(post.id(),
                    questions.stream().map(CollaborationService.Requirement::id).toList(), answerTexts)) {
                showSuccess("Application Sent!");
                loadRoomView(post, roomContainer);
            } else showError("Failed to apply.");
        });
        reqBox.getChildren().add(applyBtn);
        roomContainer.getChildren().add(reqBox);
    }

    // ----------------------------------------------------------
    // OWNER DASHBOARD
    // ----------------------------------------------------------
    public void showOwnerDashboard(CollaborationService.Post post, VBox roomContainer) {
        roomContainer.getChildren().clear();
        VBox mainBox = new VBox(20); mainBox.setPadding(new Insets(20));

        HBox header = new HBox(15); header.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("👑 Management Dashboard: " + post.title());
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 20px; -fx-text-fill: #1e293b;");
        Button gotoChatBtn = new Button("Go to Team Workspace 💬");
        gotoChatBtn.setStyle("-fx-background-color: #3b82f6; -fx-text-fill: white; "
            + "-fx-font-weight: bold; -fx-cursor: hand;");
        gotoChatBtn.setOnAction(e -> chatController.showChatRoom(post, roomContainer));
        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        header.getChildren().addAll(title, spacer, gotoChatBtn);

        VBox appsBox = new VBox(10);
        ScrollPane scroll = new ScrollPane(appsBox);
        scroll.setFitToWidth(true); VBox.setVgrow(scroll, Priority.ALWAYS);

        List<CollaborationService.Application> apps = collaborationService.getApplicationsForPost(post.id());
        if (apps.isEmpty()) appsBox.getChildren().add(new Label("No pending applications."));
        for (var app : apps) {
            VBox appCard = new VBox(10);
            appCard.setStyle("-fx-border-color: #e2e8f0; -fx-padding: 15; -fx-background-color: #f8fafc;");
            Label applicantName = new Label("👤 Applicant: " + app.username());
            applicantName.setStyle("-fx-font-weight: bold; -fx-text-fill: #0f172a;");
            VBox qaBox = new VBox(5);
            for (String ans : app.answers()) {
                Label a = new Label("• " + ans); a.setWrapText(true); qaBox.getChildren().add(a);
            }
            HBox actions = new HBox(10);
            if (app.status().equals("PENDING")) {
                Button approveBtn = new Button("Approve ✅");
                approveBtn.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-cursor: hand;");
                approveBtn.setOnAction(e -> {
                    if (collaborationService.approveMember(post.id(), app.userId())) {
                        showSuccess(app.username() + " approved!");
                        showOwnerDashboard(post, roomContainer);
                    }
                });
                actions.getChildren().add(approveBtn);
            } else {
                actions.getChildren().add(new Label("Status: " + app.status()));
            }
            appCard.getChildren().addAll(applicantName, new Label("Responses:"), qaBox, new Separator(), actions);
            appsBox.getChildren().add(appCard);
        }
        mainBox.getChildren().addAll(header, new Label("Pending Applications"), scroll);
        roomContainer.getChildren().add(mainBox);
    }

    // ----------------------------------------------------------
    // HELPERS
    // ----------------------------------------------------------
    private void showSuccess(String msg) {
        Platform.runLater(() -> { Alert a = new Alert(Alert.AlertType.INFORMATION); a.setContentText(msg); a.show(); });
    }
    private void showError(String msg) {
        Platform.runLater(() -> { Alert a = new Alert(Alert.AlertType.ERROR); a.setContentText(msg); a.showAndWait(); });
    }
}