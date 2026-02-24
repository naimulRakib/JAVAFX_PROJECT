package com.scholar.controller;

import com.scholar.model.Doubt;
import com.scholar.model.DoubtAnswer;
import com.scholar.service.AuthService;
import com.scholar.service.ConsultancyService;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import java.util.List;

@Controller
public class ConsultancyController {

    @FXML private VBox doubtsListContainer, doubtDetailBox, answersContainer, replyBox;
    @FXML private Label detailSubject, detailTopic, detailTitle, detailDesc, detailStudent;
    @FXML private TextArea answerInput;

    @Autowired private ConsultancyService consultancyService;
    private Doubt currentSelectedDoubt = null;

    public void initialize() {
        loadDoubts();
    }

    // ==========================================
    // 📚 LOAD DOUBTS (WITH ADMIN DELETE & ANONYMOUS)
    // ==========================================
    private void loadDoubts() {
        new Thread(() -> {
            List<Doubt> doubts = consultancyService.getAllOpenDoubts();
            boolean isAdmin = "admin".equals(AuthService.CURRENT_USER_ROLE);

            Platform.runLater(() -> {
                doubtsListContainer.getChildren().clear();
                for (Doubt d : doubts) {
                    VBox card = new VBox(5);
                    card.setStyle(
                        "-fx-background-color: #13151f; " +
                        "-fx-padding: 14; " +
                        "-fx-background-radius: 10; " +
                        "-fx-border-color: #1e2540; " +
                        "-fx-border-radius: 10; " +
                        "-fx-cursor: hand; " +
                        "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.45), 8, 0, 0, 2);"
                    );

                    HBox header = new HBox(10);

                    boolean isResolved = d.status().equals("RESOLVED");
                    Label status = new Label(isResolved ? "✅ Solved" : "🔥 Open");
                    status.setStyle(
                        isResolved
                        ? "-fx-text-fill: #86efac; -fx-font-weight: bold; -fx-font-size: 12px;"
                        : "-fx-text-fill: #fb923c; -fx-font-weight: bold; -fx-font-size: 12px;"
                    );

                    Region spacer = new Region();
                    HBox.setHgrow(spacer, Priority.ALWAYS);
                    header.getChildren().addAll(status, spacer);

                    // 🛑 Admin Delete Button
                    if (isAdmin) {
                        Button delBtn = new Button("🗑️");
                        delBtn.setStyle(
                            "-fx-background-color: transparent; " +
                            "-fx-cursor: hand; " +
                            "-fx-text-fill: #ef4444; " +
                            "-fx-font-size: 14px;"
                        );
                        delBtn.setOnAction(e -> {
                            e.consume(); // Prevent click from opening details
                            new Thread(() -> {
                                if (consultancyService.deleteDoubt(d.id()))
                                    Platform.runLater(this::loadDoubts);
                            }).start();
                        });
                        header.getChildren().add(delBtn);
                    }

                    Label title = new Label(d.title());
                    title.setStyle(
                        "-fx-font-weight: bold; " +
                        "-fx-font-size: 14px; " +
                        "-fx-text-fill: #e2e8f0;"
                    );

                    // 🕵️ Anonymous Setup
                    String displayName = d.isAnonymous() ? "🕵️ Anonymous" : "👤 " + d.studentName();
                    Label author = new Label(displayName + " • 📚 " + d.subject());
                    author.setStyle(
                        "-fx-text-fill: #475569; " +
                        "-fx-font-size: 11px;"
                    );

                    card.getChildren().addAll(header, title, author);
                    card.setOnMouseClicked(e -> showDoubtDetails(d));

                    doubtsListContainer.getChildren().add(card);
                }
            });
        }).start();
    }

    // ==========================================
    // 📖 SHOW DETAILS & ANSWERS
    // ==========================================
    private void showDoubtDetails(Doubt d) {
        currentSelectedDoubt = d;
        doubtDetailBox.setVisible(true);
        replyBox.setVisible(!d.status().equals("RESOLVED"));

        detailSubject.setText(d.subject());
        detailTopic.setText(d.topic());
        detailTitle.setText(d.title());
        detailDesc.setText(d.description());

        String displayName = d.isAnonymous() ? "🕵️ Anonymous Student" : "👤 " + d.studentName();
        detailStudent.setText("Asked by: " + displayName + " | Privacy: " + d.privacy());

        loadAnswers(d.id());
    }

    private void loadAnswers(String doubtId) {
        new Thread(() -> {
            List<DoubtAnswer> answers = consultancyService.getAnswers(doubtId);
            Platform.runLater(() -> {
                answersContainer.getChildren().clear();
                if (answers.isEmpty()) {
                    Label noAnswerLbl = new Label("No answers yet. Be the first to help! 💡");
                    noAnswerLbl.setStyle(
                        "-fx-text-fill: #475569; " +
                        "-fx-font-size: 13px; " +
                        "-fx-padding: 12 0;"
                    );
                    answersContainer.getChildren().add(noAnswerLbl);
                    return;
                }

                for (DoubtAnswer a : answers) {
                    VBox ansCard = new VBox(8);
                    boolean isBest = a.isBestAnswer();
                    ansCard.setStyle(
                        "-fx-background-color: " + (isBest ? "#0a1a10" : "#13151f") + "; " +
                        "-fx-padding: 15; " +
                        "-fx-background-radius: 10; " +
                        "-fx-border-color: " + (isBest ? "#16a34a" : "#1e2540") + "; " +
                        "-fx-border-width: " + (isBest ? "2" : "1") + "; " +
                        "-fx-border-radius: 10; " +
                        "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.45), 8, 0, 0, 2);"
                    );

                    HBox header = new HBox(10);
                    Label name = new Label("🎓 Mentor/Senior: " + a.mentorName());
                    name.setStyle(
                        "-fx-font-weight: bold; " +
                        "-fx-text-fill: #c7d2fe; " +
                        "-fx-font-size: 13px;"
                    );
                    Region spacer = new Region();
                    HBox.setHgrow(spacer, Priority.ALWAYS);
                    header.getChildren().addAll(name, spacer);

                    if (isBest) {
                        Label bestBadge = new Label("🌟 Best Answer");
                        bestBadge.setStyle(
                            "-fx-background-color: #854d0e; " +
                            "-fx-text-fill: #fde68a; " +
                            "-fx-padding: 3 10; " +
                            "-fx-background-radius: 12; " +
                            "-fx-font-weight: bold; " +
                            "-fx-font-size: 11px;"
                        );
                        header.getChildren().add(bestBadge);
                    } else if (currentSelectedDoubt != null
                            && currentSelectedDoubt.studentId()
                               .equals(String.valueOf(AuthService.CURRENT_USER_ID))) {
                        Button markBestBtn = new Button("Mark as Best ✅");
                        markBestBtn.setStyle(
                            "-fx-background-color: #166534; " +
                            "-fx-text-fill: #86efac; " +
                            "-fx-cursor: hand; " +
                            "-fx-font-size: 11px; " +
                            "-fx-background-radius: 8; " +
                            "-fx-padding: 4 12;"
                        );
                        markBestBtn.setOnAction(e -> markAsBest(a.id()));
                        header.getChildren().add(markBestBtn);
                    }

                    Label content = new Label(a.content());
                    content.setWrapText(true);
                    content.setStyle(
                        "-fx-text-fill: #94a3b8; " +
                        "-fx-font-size: 13px;"
                    );

                    ansCard.getChildren().addAll(header, content);
                    answersContainer.getChildren().add(ansCard);
                }
            });
        }).start();
    }

    // ==========================================
    // ✍️ POST ANSWER & BEST ANSWER
    // ==========================================
    @FXML
    public void onSubmitAnswer(ActionEvent event) {
        if (currentSelectedDoubt == null || answerInput.getText().trim().isEmpty()) return;
        new Thread(() -> {
            if (consultancyService.submitAnswer(currentSelectedDoubt.id(), answerInput.getText().trim())) {
                Platform.runLater(() -> {
                    answerInput.clear();
                    loadAnswers(currentSelectedDoubt.id());
                });
            }
        }).start();
    }

    private void markAsBest(String answerId) {
        new Thread(() -> {
            if (consultancyService.markBestAnswer(currentSelectedDoubt.id(), answerId)) {
                Platform.runLater(() -> {
                    loadDoubts();
                    showDoubtDetails(currentSelectedDoubt);
                });
            }
        }).start();
    }

    // ==========================================
    // 🙋‍♂️ ASK A NEW DOUBT DIALOG
    // ==========================================
    @FXML
    public void onAskDoubt(ActionEvent event) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("🙋‍♂️ Ask a Doubt");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10); grid.setVgap(10); grid.setPadding(new Insets(20));

        ComboBox<String> subBox = new ComboBox<>();
        subBox.getItems().addAll("Math", "Java", "Database", "Physics", "Other");
        subBox.setValue("Java");
        TextField topicField = new TextField(); topicField.setPromptText("e.g. Object Oriented Programming");
        TextField titleField = new TextField(); titleField.setPromptText("Question Title");
        TextArea descArea = new TextArea();
        descArea.setPromptText("Describe your problem in detail..."); descArea.setPrefRowCount(4);
        CheckBox anonymousCheck = new CheckBox("Ask Anonymously 🕵️");
        anonymousCheck.setStyle(
            "-fx-font-weight: bold; " +
            "-fx-text-fill: #fb923c;"
        );

        grid.add(new Label("Subject:"), 0, 0);     grid.add(subBox, 1, 0);
        grid.add(new Label("Topic:"), 0, 1);       grid.add(topicField, 1, 1);
        grid.add(new Label("Title:"), 0, 2);       grid.add(titleField, 1, 2);
        grid.add(new Label("Description:"), 0, 3); grid.add(descArea, 1, 3);
        grid.add(anonymousCheck, 1, 4);

        dialog.getDialogPane().setContent(grid);

        dialog.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK && !titleField.getText().isEmpty()) {
                new Thread(() -> {
                    if (consultancyService.submitDoubt(
                            titleField.getText(), descArea.getText(),
                            subBox.getValue(), topicField.getText(),
                            "PUBLIC", anonymousCheck.isSelected())) {
                        Platform.runLater(this::loadDoubts);
                    }
                }).start();
            }
        });
    }
}