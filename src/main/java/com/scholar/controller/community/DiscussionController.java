package com.scholar.controller.community;

import com.scholar.service.AuthService;
import com.scholar.service.CourseService;
import com.scholar.util.PopupHelper;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * DISCUSSION CONTROLLER — Q&A panel for community resources
 * Path: src/main/java/com/scholar/controller/community/DiscussionController.java
 *
 * Now opens via PopupHelper so it centres correctly on every monitor.
 * Internal logic (addComment, loadQnA) is unchanged.
 */
@Component
public class DiscussionController {

    @Autowired private CourseService courseService;

    // ─────────────────────────────────────────────────────────────
    // SHOW DISCUSSION PANEL  — owner param added for PopupHelper
    // ─────────────────────────────────────────────────────────────
    public void showDiscussionPanel(CourseService.Resource res, Window owner) {

        // ── Layout root ───────────────────────────────────────────
        VBox layout = new VBox(14);
        layout.setPadding(new Insets(18));
        layout.setStyle("-fx-background-color: #0f1117;");

        // ── Toggle ────────────────────────────────────────────────
        ToggleButton myQuestionsToggle = new ToggleButton("🔍 My Questions Only");
        myQuestionsToggle.setStyle(toggleOff());

        VBox qnaContainer = new VBox(14);
        ScrollPane scroll = new ScrollPane(qnaContainer);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background: #0f1117; -fx-border-color: transparent;");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        // ── Input row ─────────────────────────────────────────────
        TextField input = new TextField();
        input.setPromptText("Ask a question or share a doubt…");
        input.setStyle("-fx-background-color: #1a2236; -fx-text-fill: #e2e8f0; "
            + "-fx-prompt-text-fill: #4a5a72; -fx-background-radius: 20; "
            + "-fx-border-color: #2c3a52; -fx-border-radius: 20; -fx-padding: 10 16;");
        HBox.setHgrow(input, Priority.ALWAYS);

        Button sendBtn = new Button("Ask 🚀");
        sendBtn.setStyle("-fx-background-color: #3b82f6; -fx-text-fill: white; "
            + "-fx-background-radius: 20; -fx-font-weight: bold; -fx-cursor: hand; -fx-padding: 9 18;");

        sendBtn.setOnAction(e -> {
            if (input.getText().trim().isEmpty()) return;
            new Thread(() -> {
                if (courseService.addComment(res.id(), input.getText().trim(), null))
                    Platform.runLater(() -> {
                        input.clear();
                        loadQnA(res.id(), qnaContainer, myQuestionsToggle.isSelected());
                    });
            }).start();
        });

        // Enter key submits
        input.setOnAction(e -> sendBtn.fire());

        myQuestionsToggle.setOnAction(e -> {
            myQuestionsToggle.setStyle(myQuestionsToggle.isSelected() ? toggleOn() : toggleOff());
            loadQnA(res.id(), qnaContainer, myQuestionsToggle.isSelected());
        });

        HBox topBar   = new HBox(myQuestionsToggle);
        topBar.setAlignment(Pos.CENTER_RIGHT);
        HBox inputBox = new HBox(10, input, sendBtn);
        inputBox.setAlignment(Pos.CENTER);

        layout.getChildren().addAll(topBar, scroll, inputBox);

        // ── PopupHelper stage ─────────────────────────────────────
        Stage stage = PopupHelper.create(owner,
            "💬 Q&A — " + res.title(),
            layout,
            480, 540, 580, 640);
        stage.show();

        loadQnA(res.id(), qnaContainer, false);
    }

    // ─────────────────────────────────────────────────────────────
    // LOAD Q&A — logic unchanged
    // ─────────────────────────────────────────────────────────────
    private void loadQnA(int resId, VBox container, boolean showOnlyMine) {
        new Thread(() -> {
            List<CourseService.Comment> allComments = courseService.getComments(resId);
            Platform.runLater(() -> {
                container.getChildren().clear();

                List<CourseService.Comment> questions = new ArrayList<>();
                List<CourseService.Comment> answers   = new ArrayList<>();
                for (var c : allComments) {
                    if (c.parentId() == 0) questions.add(c); else answers.add(c);
                }

                if (questions.isEmpty()) {
                    container.getChildren().add(emptyLabel("No questions yet — be the first to ask! 💡"));
                    return;
                }

                boolean foundAny = false;
                for (var q : questions) {
                    String currentUserIdStr = String.valueOf(AuthService.CURRENT_USER_ID);
                    if (showOnlyMine && q.userId() != null
                        && !String.valueOf(q.userId()).equals(currentUserIdStr)) continue;
                    foundAny = true;

                    // ── Question card ─────────────────────────────
                    VBox qCard = new VBox(8);
                    qCard.setStyle("-fx-background-color: #161b27; -fx-padding: 14; "
                        + "-fx-background-radius: 10; -fx-border-color: #3b82f6; "
                        + "-fx-border-width: 1 1 1 4; "
                        + "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.25), 6, 0, 0, 2);");

                    Label qUser = new Label("❓ " + q.userName() + " asked:");
                    qUser.setStyle("-fx-font-weight: bold; -fx-text-fill: #93c5fd;");
                    Label qContent = new Label(q.content());
                    qContent.setWrapText(true);
                    qContent.setStyle("-fx-font-size: 13px; -fx-text-fill: #e2e8f0;");

                    // ── Reply input (hidden until toggled) ────────
                    HBox replyInputBox = new HBox(10);
                    replyInputBox.setVisible(false);
                    replyInputBox.setManaged(false);
                    TextField replyInput = new TextField();
                    replyInput.setPromptText("Write an answer…");
                    replyInput.setStyle("-fx-background-color: #1a2236; -fx-text-fill: #e2e8f0; "
                        + "-fx-background-radius: 8; -fx-border-color: #2c3a52; "
                        + "-fx-border-radius: 8; -fx-padding: 7 12;");
                    HBox.setHgrow(replyInput, Priority.ALWAYS);

                    Button submitReplyBtn = new Button("Reply ➤");
                    submitReplyBtn.setStyle("-fx-background-color: #22c55e; -fx-text-fill: white; "
                        + "-fx-cursor: hand; -fx-background-radius: 8; -fx-padding: 7 14;");
                    replyInputBox.getChildren().addAll(replyInput, submitReplyBtn);

                    Button showReplyBtn = new Button("↳ Answer this");
                    showReplyBtn.setStyle("-fx-background-color: transparent; "
                        + "-fx-text-fill: #60a5fa; -fx-cursor: hand; -fx-font-size: 11px;");
                    showReplyBtn.setOnAction(e -> {
                        boolean vis = !replyInputBox.isVisible();
                        replyInputBox.setVisible(vis);
                        replyInputBox.setManaged(vis);
                    });

                    submitReplyBtn.setOnAction(e -> {
                        if (replyInput.getText().trim().isEmpty()) return;
                        new Thread(() -> {
                            if (courseService.addComment(resId, replyInput.getText().trim(), q.id()))
                                Platform.runLater(() -> loadQnA(resId, container, showOnlyMine));
                        }).start();
                    });

                    // Enter key in reply field
                    replyInput.setOnAction(e -> submitReplyBtn.fire());

                    qCard.getChildren().addAll(qUser, qContent, showReplyBtn, replyInputBox);

                    // ── Answers ───────────────────────────────────
                    VBox answersBox = new VBox(8);
                    answersBox.setPadding(new Insets(6, 0, 0, 28));
                    for (var a : answers) {
                        if (a.parentId() == q.id()) {
                            VBox aCard = new VBox(4);
                            aCard.setStyle("-fx-background-color: #1a2236; -fx-padding: 10; "
                                + "-fx-background-radius: 8;");
                            Label aUser = new Label("💡 " + a.userName() + " replied:");
                            aUser.setStyle("-fx-font-weight: bold; -fx-text-fill: #4ade80; -fx-font-size: 11px;");
                            Label aContent = new Label(a.content());
                            aContent.setWrapText(true);
                            aContent.setStyle("-fx-text-fill: #cbd5e1;");
                            aCard.getChildren().addAll(aUser, aContent);
                            answersBox.getChildren().add(aCard);
                        }
                    }
                    container.getChildren().addAll(qCard, answersBox);
                }

                if (showOnlyMine && !foundAny)
                    container.getChildren().add(emptyLabel("You haven't asked any questions yet 🔍"));
            });
        }).start();
    }

    // ─────────────────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────────────────
    private static Label emptyLabel(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-text-fill: #4a5a72; -fx-font-size: 13px;");
        return l;
    }

    private static String toggleOff() {
        return "-fx-background-color: #1e2736; -fx-text-fill: #7b8fa8; "
            + "-fx-font-weight: bold; -fx-cursor: hand; "
            + "-fx-background-radius: 20; -fx-padding: 5 15;";
    }

    private static String toggleOn() {
        return "-fx-background-color: #3b82f6; -fx-text-fill: white; "
            + "-fx-font-weight: bold; -fx-cursor: hand; "
            + "-fx-background-radius: 20; -fx-padding: 5 15;";
    }
}