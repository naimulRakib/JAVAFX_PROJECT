package com.scholar.controller.dashboard;

import com.scholar.service.*;
import com.scholar.model.StudyTask;
import com.scholar.service.*;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * ADMIN BROADCAST CONTROLLER — Routine/notice broadcasts + pending member approvals
 * Path: src/main/java/com/scholar/controller/dashboard/AdminBroadcastController.java
 */
@Component
public class AdminBroadcastController {

    @Autowired private AISchedulerService aiService;
    @Autowired private DataService dataService;
    @Autowired private ChannelService channelService;

    private VBox pendingListContainer;
    private List<StudyTask> allTasks;
    private Runnable onAfterBroadcast; // refreshTimeline + drawCalendar

    public void init(VBox pendingListContainer, List<StudyTask> allTasks, Runnable onAfterBroadcast) {
        this.pendingListContainer = pendingListContainer;
        this.allTasks             = allTasks;
        this.onAfterBroadcast     = onAfterBroadcast;
    }

    // ----------------------------------------------------------
    // SHOW BROADCAST DIALOG
    // ----------------------------------------------------------
    public void showAddRoutineDialog()      { showAdminBroadcastDialog(true); }
    public void showAddAnnouncementDialog() { showAdminBroadcastDialog(false); }

    private void showAdminBroadcastDialog(boolean isRoutine) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(isRoutine ? "📅 Broadcast Varsity Routine" : "📢 Broadcast Official Notice");
        dialog.setHeaderText("Paste raw text or table data. AI will automatically parse and distribute it!");

        ButtonType broadcastBtn = new ButtonType("Broadcast 🚀", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(broadcastBtn, ButtonType.CANCEL);

        TextArea inputArea = new TextArea();
        inputArea.setPromptText(isRoutine
            ? "E.g. SAT 08:00 CSE105 Room302\nSUN 11:00 MATH143..."
            : "E.g. Notice: Lab Final on 25th March 10:00 AM at Room 405...");
        inputArea.setPrefRowCount(10); inputArea.setPrefColumnCount(40); inputArea.setWrapText(true);
        inputArea.setStyle("-fx-font-family: 'Consolas'; -fx-font-size: 14px; -fx-padding: 10;");
        dialog.getDialogPane().setContent(inputArea);

        dialog.showAndWait().ifPresent(response -> {
            if (response == broadcastBtn && !inputArea.getText().trim().isEmpty()) {
                String rawText = inputArea.getText().trim();
                Alert loadingMsg = new Alert(Alert.AlertType.INFORMATION,
                    "🤖 AI is reading and organizing the data... Please wait.");
                loadingMsg.show();

                new Thread(() -> {
                    RoutineManager routineManager = new RoutineManager();
                    List<StudyTask> tempTasks;

                    if (isRoutine) {
                        tempTasks = routineManager.processVarsitySchedule(rawText);
                    } else {
                        List<StudyTask> rawNotices = aiService.parseAdminNotice(rawText, LocalDate.now().toString());
                        tempTasks = new ArrayList<>();
                        for (StudyTask t : rawNotices) {
                            tempTasks.add(new StudyTask(
                                null, t.title(), t.date(), t.startTime(),
                                t.durationMinutes(), t.roomNo(), "NOTICE",
                                t.tags(), "admin", null, null, null, null
                            ));
                        }
                    }

                    final List<StudyTask> generatedTasks = tempTasks;
                    if (!generatedTasks.isEmpty()) dataService.saveTasks(generatedTasks);

                    Platform.runLater(() -> {
                        loadingMsg.close();
                        if (generatedTasks.isEmpty()) {
                            showError("❌ AI couldn't understand the text. Please format it clearly.");
                        } else {
                            allTasks.addAll(generatedTasks);
                            if (onAfterBroadcast != null) onAfterBroadcast.run();
                            showSuccess("Successfully Broadcasted " + generatedTasks.size() + " items! ⚡");
                        }
                    });
                }).start();
            }
        });
    }

    // ----------------------------------------------------------
    // PENDING MEMBER REQUESTS (Admin Tab)
    // ----------------------------------------------------------
    @FXML
    public void loadPendingRequests() {
        if (pendingListContainer == null) return;
        pendingListContainer.getChildren().clear();
        new Thread(() -> {
            List<String[]> pendingMembers = channelService.getPendingMembers(AuthService.CURRENT_CHANNEL_ID);
            Platform.runLater(() -> {
                if (pendingMembers.isEmpty()) {
                    pendingListContainer.getChildren().add(new Label("No pending requests."));
                    return;
                }
                for (String[] student : pendingMembers) {
                    HBox row = new HBox(20);
                    row.setAlignment(Pos.CENTER_LEFT);
                    row.setStyle("-fx-background-color: white; -fx-padding: 15; -fx-background-radius: 8; "
                        + "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.1), 5, 0, 0, 0);");
                    Label emailLabel = new Label(student[1]);
                    emailLabel.setPrefWidth(300);
                    emailLabel.setFont(Font.font("System", FontWeight.BOLD, 14));
                    Button approveBtn = new Button("Approve ✅");
                    approveBtn.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-cursor: hand;");
                    approveBtn.setOnAction(e -> handleAction(student[0], "approved"));
                    Button rejectBtn = new Button("Reject ❌");
                    rejectBtn.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-cursor: hand;");
                    rejectBtn.setOnAction(e -> handleAction(student[0], "rejected"));
                    row.getChildren().addAll(emailLabel, approveBtn, rejectBtn);
                    pendingListContainer.getChildren().add(row);
                }
            });
        }).start();
    }

    private void handleAction(String studentId, String status) {
        new Thread(() -> {
            if (channelService.updateMemberStatus(studentId, AuthService.CURRENT_CHANNEL_ID, status))
                Platform.runLater(this::loadPendingRequests);
        }).start();
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