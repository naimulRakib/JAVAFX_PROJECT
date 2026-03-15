package com.scholar.controller.dashboard;

import com.scholar.service.*;
import com.scholar.model.StudyTask;
import com.scholar.util.PopupHelper;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Window;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import javafx.stage.*;

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
    @Autowired private RoutineManager routineManager;

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
    public void showClassOffDialog()        { showClassOffOnlyDialog(); }

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

                // Show non-blocking dark loading popup instead of Alert
                Stage loadingPopup = PopupHelper.create(
                    resolveOwner(), "Processing",
                    buildLoadingPane(),
                    300, 150, 360, 160
                );
                loadingPopup.show();

                new Thread(() -> {
                    List<StudyTask> tempTasks;
                    boolean classOffApplied = false;
                    boolean classOffReversed = false;

                    if (isRoutine) {
                        RoutineManager.ClassOffCommand cmd = RoutineManager.parseClassOffCommand(rawText);
                        if (cmd != null) {
                            if (cmd.reverse()) {
                                for (RoutineManager.DateRange r : cmd.ranges()) {
                                    dataService.reverseClassOffByRange(r.startDate(), r.endDate());
                                }
                                classOffReversed = true;
                            } else {
                                for (RoutineManager.DateRange r : cmd.ranges()) {
                                    classOffApplied |= dataService.addClassOffPeriod(
                                        r.startDate(), r.endDate(), cmd.reason());
                                }
                            }
                            tempTasks = new ArrayList<>();
                        } else {
                            tempTasks = routineManager.processVarsitySchedule(rawText);
                        }
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

                    final boolean classOffAppliedFinal = classOffApplied;
                    final boolean classOffReversedFinal = classOffReversed;
                    Platform.runLater(() -> {
                        loadingPopup.close();
                        if (classOffReversedFinal) {
                            if (onAfterBroadcast != null) onAfterBroadcast.run();
                            showSuccess("↩ Class Off Reversed");
                        } else if (classOffAppliedFinal) {
                            if (onAfterBroadcast != null) onAfterBroadcast.run();
                            showSuccess("✅ Class Off Applied (" + rawText + ")");
                        } else if (generatedTasks.isEmpty()) {
                            showError("AI couldn't understand the text. Please format it clearly.");
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

    private void showClassOffOnlyDialog() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("🏫 Class Off (Date/Range)");
        dialog.setHeaderText("Write dates in day/month/year (e.g., 13/3/2026) or month names.\n" +
                             "Examples: 13 Mar 2026, 17 Mar, 18 Mar 2026, 13/3/2026 to 25/3/2026\n" +
                             "To reverse: add 'reverse' (e.g., reverse 13/3/2026 to 25/3/2026)");

        ButtonType applyBtn = new ButtonType("Apply ✅", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(applyBtn, ButtonType.CANCEL);

        TextArea inputArea = new TextArea();
        inputArea.setPromptText("e.g. 13/3/2026 to 25/3/2026 varsity off\nor: reverse 13 Mar 2026");
        inputArea.setPrefRowCount(6);
        inputArea.setWrapText(true);
        dialog.getDialogPane().setContent(inputArea);

        dialog.showAndWait().ifPresent(response -> {
            if (response == applyBtn && !inputArea.getText().trim().isEmpty()) {
                String rawText = inputArea.getText().trim();

                Stage loadingPopup = PopupHelper.create(
                    resolveOwner(), "Processing",
                    buildLoadingPane(),
                    300, 150, 360, 160
                );
                loadingPopup.show();

                new Thread(() -> {
                    RoutineManager.ClassOffCommand cmd = RoutineManager.parseClassOffCommand(rawText);
                    boolean applied = false;
                    boolean reversed = false;
                    if (cmd != null) {
                        if (cmd.reverse()) {
                            for (RoutineManager.DateRange r : cmd.ranges()) {
                                dataService.reverseClassOffByRange(r.startDate(), r.endDate());
                            }
                            reversed = true;
                        } else {
                            for (RoutineManager.DateRange r : cmd.ranges()) {
                                applied |= dataService.addClassOffPeriod(
                                    r.startDate(), r.endDate(), cmd.reason());
                            }
                        }
                    }

                    final boolean appliedFinal = applied;
                    final boolean reversedFinal = reversed;
                    Platform.runLater(() -> {
                        loadingPopup.close();
                        if (cmd == null) {
                            showError("Couldn't parse the dates. Please use day/month/year.");
                        } else if (reversedFinal) {
                            if (onAfterBroadcast != null) onAfterBroadcast.run();
                            showSuccess("↩ Class Off Reversed");
                        } else if (appliedFinal) {
                            if (onAfterBroadcast != null) onAfterBroadcast.run();
                            showSuccess("✅ Class Off Applied");
                        } else {
                            showError("No changes were applied.");
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
                    Label emptyLbl = new Label("No pending requests.");
                    emptyLbl.setStyle("-fx-text-fill: #64748b; -fx-font-size: 13px;");
                    pendingListContainer.getChildren().add(emptyLbl);
                    return;
                }
                for (String[] student : pendingMembers) {
                    HBox row = new HBox(20);
                    row.setAlignment(Pos.CENTER_LEFT);
                    row.setStyle(
                        "-fx-background-color: #13151f; " +
                        "-fx-padding: 15; " +
                        "-fx-background-radius: 10; " +
                        "-fx-border-color: #1e2540; " +
                        "-fx-border-radius: 10; " +
                        "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.4), 8, 0, 0, 2);"
                    );

                    Label emailLabel = new Label(student[1]);
                    emailLabel.setPrefWidth(300);
                    emailLabel.setFont(Font.font("System", FontWeight.BOLD, 14));
                    emailLabel.setStyle("-fx-text-fill: #e2e8f0;");

                    Button approveBtn = new Button("Approve ✅");
                    approveBtn.setStyle(
                        "-fx-background-color: #166534; " +
                        "-fx-text-fill: #86efac; " +
                        "-fx-font-weight: bold; " +
                        "-fx-background-radius: 8; " +
                        "-fx-border-color: #14532d; " +
                        "-fx-border-radius: 8; " +
                        "-fx-padding: 6 16; " +
                        "-fx-cursor: hand;"
                    );
                    approveBtn.setOnAction(e -> handleAction(student[0], "approved"));

                    Button rejectBtn = new Button("Reject ❌");
                    rejectBtn.setStyle(
                        "-fx-background-color: #7f1d1d; " +
                        "-fx-text-fill: #fca5a5; " +
                        "-fx-font-weight: bold; " +
                        "-fx-background-radius: 8; " +
                        "-fx-border-color: #991b1b; " +
                        "-fx-border-radius: 8; " +
                        "-fx-padding: 6 16; " +
                        "-fx-cursor: hand;"
                    );
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

    /** Builds the VBox content shown inside the loading popup. */
    private javafx.scene.layout.VBox buildLoadingPane() {
        javafx.scene.layout.VBox pane = new javafx.scene.layout.VBox(14);
        pane.setAlignment(javafx.geometry.Pos.CENTER);
        pane.setPadding(new javafx.geometry.Insets(28, 32, 28, 32));
        pane.setStyle("-fx-background-color: #13151f;");

        javafx.scene.control.ProgressIndicator spinner = new javafx.scene.control.ProgressIndicator();
        spinner.setPrefSize(38, 38);
        spinner.setStyle("-fx-accent: #6366f1;");

        Label lbl = new Label("🤖  AI is reading and organizing the data…");
        lbl.setStyle(
            "-fx-text-fill: #94a3b8; " +
            "-fx-font-size: 13px; " +
            "-fx-wrap-text: true;"
        );
        lbl.setWrapText(true);
        lbl.setMaxWidth(280);
        lbl.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);

        pane.getChildren().addAll(spinner, lbl);
        return pane;
    }

    private void showSuccess(String msg) {
        PopupHelper.showInfo(resolveOwner(), "Broadcast Complete", msg);
    }

    private void showError(String msg) {
        PopupHelper.showError(resolveOwner(), "Broadcast Failed", msg);
    }

    /** Resolves the best available Window for PopupHelper. */
    private Window resolveOwner() {
        if (pendingListContainer != null && pendingListContainer.getScene() != null)
            return pendingListContainer.getScene().getWindow();
        return null;
    }
}
