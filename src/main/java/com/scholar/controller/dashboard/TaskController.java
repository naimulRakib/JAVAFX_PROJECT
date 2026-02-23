package com.scholar.controller.dashboard;

import com.scholar.model.StudyTask;
import com.scholar.service.AuthService;
import com.scholar.service.DataService;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * TASK CONTROLLER — Timeline, Backlog, Completed, Mark Done, Delete
 * Path: src/main/java/com/scholar/controller/dashboard/TaskController.java
 *
 * Wired into DashboardController via @Autowired.
 * Call init(allTasks, timelineContainer, selectedDateSupplier) after FXML load.
 */
@Component
public class TaskController {

    @Autowired private DataService dataService;

    // Shared state — set by DashboardController after init
    private List<StudyTask> allTasks;
    private VBox timelineContainer;
    private java.util.function.Supplier<LocalDate> selectedDateSupplier;
    private java.util.function.Supplier<String> viewModeSupplier;
    private Runnable onRefreshCalendar;

    public void init(List<StudyTask> allTasks,
                     VBox timelineContainer,
                     java.util.function.Supplier<LocalDate> selectedDateSupplier,
                     java.util.function.Supplier<String> viewModeSupplier,
                     Runnable onRefreshCalendar) {
        this.allTasks = allTasks;
        this.timelineContainer = timelineContainer;
        this.selectedDateSupplier = selectedDateSupplier;
        this.viewModeSupplier = viewModeSupplier;
        this.onRefreshCalendar = onRefreshCalendar;
    }

    // ----------------------------------------------------------
    // LOAD FROM DB
    // ----------------------------------------------------------
    public void loadTasksFromDatabase(Runnable afterLoad) {
        new Thread(() -> {
            try {
                List<StudyTask> dbTasks = dataService.loadAllTasks();
                Platform.runLater(() -> {
                    allTasks.clear();
                    allTasks.addAll(dbTasks);
                    refreshTimeline();
                    if (afterLoad != null) afterLoad.run();
                });
            } catch (Exception e) {
                System.err.println("❌ Cloud Sync Failed: " + e.getMessage());
            }
        }).start();
    }

    // ----------------------------------------------------------
    // REFRESH TIMELINE
    // ----------------------------------------------------------
    public void refreshTimeline() {
        if (timelineContainer == null) return;
        timelineContainer.getChildren().clear();

        String currentViewMode = viewModeSupplier.get();
        LocalDate selectedDate = selectedDateSupplier.get();

        List<StudyTask> displayTasks = allTasks.stream()
            .filter(t -> {
                if ("BACKLOG".equals(currentViewMode))
                    return "PERSONAL".equals(t.type()) && "BACKLOG".equals(t.status());
                else if ("COMPLETED".equals(currentViewMode))
                    return "PERSONAL".equals(t.type()) && "COMPLETED".equals(t.status());
                else {
                    if (t.date() == null || !t.date().equals(selectedDate.toString())) return false;
                    if ("PERSONAL".equals(t.type()))
                        return !"COMPLETED".equals(t.status()) && !"BACKLOG".equals(t.status());
                    return true;
                }
            })
            .sorted((t1, t2) -> {
                if ("Anytime".equalsIgnoreCase(t1.startTime())) return -1;
                if ("Anytime".equalsIgnoreCase(t2.startTime())) return 1;
                try {
                    java.time.format.DateTimeFormatter f =
                        java.time.format.DateTimeFormatter.ofPattern("hh:mm a");
                    java.time.LocalTime time1 = java.time.LocalTime.parse(t1.startTime().toUpperCase(), f);
                    java.time.LocalTime time2 = java.time.LocalTime.parse(t2.startTime().toUpperCase(), f);
                    return time1.compareTo(time2);
                } catch (Exception e) { return 0; }
            })
            .collect(Collectors.toList());

        if (displayTasks.isEmpty()) {
            Label emptyLbl = new Label("No tasks found in " + currentViewMode + " view.");
            emptyLbl.setStyle("-fx-font-size: 14px; -fx-text-fill: #7f8c8d; -fx-padding: 20;");
            timelineContainer.getChildren().add(emptyLbl);
            return;
        }

        for (StudyTask task : displayTasks) {
            timelineContainer.getChildren().add(buildTaskCard(task, currentViewMode));
        }
    }

    // ----------------------------------------------------------
    // BUILD CARD
    // ----------------------------------------------------------
    private VBox buildTaskCard(StudyTask task, String currentViewMode) {
        VBox card = new VBox(5);
        card.setStyle("-fx-background-radius: 8; "
            + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 5, 0, 0, 1); -fx-cursor: hand;");
        card.setOnMouseClicked(e -> {
            if (e.getTarget() instanceof Button || e.getTarget() instanceof javafx.scene.text.Text) return;
            Platform.runLater(() -> showTaskDetailsDialog(task));
        });

        if ("NOTICE".equals(task.type())) {
            card.setStyle(card.getStyle()
                + "-fx-background-color: #fdf2f2; -fx-border-left-color: #e74c3c; "
                + "-fx-border-left-width: 5; -fx-padding: 15;");
            Label noticeTitle = new Label(task.title());
            noticeTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 16px; -fx-text-fill: #c0392b;");
            Label noticeContent = new Label(task.tags());
            noticeContent.setWrapText(true);
            noticeContent.setStyle("-fx-font-size: 14px; -fx-text-fill: #34495e; -fx-padding: 5 0 0 0;");
            card.getChildren().addAll(noticeTitle, noticeContent);
        } else {
            card.setPadding(new Insets(10));
            card.setStyle(card.getStyle() + "-fx-background-color: white;");

            if ("BACKLOG".equals(currentViewMode))
                card.setStyle(card.getStyle()
                    + "-fx-border-left-color: #e74c3c; -fx-border-left-width: 4; -fx-background-color: #fff5f5;");
            else if ("COMPLETED".equals(currentViewMode))
                card.setStyle(card.getStyle()
                    + "-fx-border-left-color: #2ecc71; -fx-border-left-width: 4; -fx-background-color: #f4fdf8;");
            else if ("ROUTINE".equals(task.type()))
                card.setStyle(card.getStyle() + "-fx-border-left-color: #3498db; -fx-border-left-width: 4;");
            else
                card.setStyle(card.getStyle() + "-fx-border-left-color: #9b59b6; -fx-border-left-width: 4;");

            HBox header = new HBox(10);
            Label timeLbl = new Label("🕒 " + task.startTime()
                + ("BACKLOG".equals(currentViewMode) ? " (" + task.date() + ")" : ""));
            timeLbl.setStyle("-fx-font-weight: bold; -fx-text-fill: #7f8c8d;");
            Label titleLbl = new Label(task.title());
            titleLbl.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
            header.getChildren().addAll(timeLbl, titleLbl);
            card.getChildren().add(header);

            if (task.roomNo() != null && !task.roomNo().isEmpty() && !task.roomNo().equals("null")) {
                Label roomLbl = new Label("📍 Room: " + task.roomNo());
                roomLbl.setStyle("-fx-text-fill: #34495e; -fx-font-size: 12px;");
                card.getChildren().add(roomLbl);
            }
        }

        HBox actionBox = new HBox(10);
        actionBox.setAlignment(Pos.CENTER_RIGHT);
        boolean isAdmin   = "admin".equals(AuthService.CURRENT_USER_ROLE);
        boolean isCreator = task.creatorRole() != null
            && task.creatorRole().equals(AuthService.CURRENT_USER_ROLE);

        if ("PERSONAL".equals(task.type())) {
            if ("DAILY".equals(currentViewMode)) {
                Button completeBtn = new Button("✅ Done");
                completeBtn.setStyle("-fx-background-color: #2ecc71; -fx-text-fill: white; "
                    + "-fx-background-radius: 15; -fx-cursor: hand; -fx-font-size: 11px;");
                completeBtn.setOnAction(e -> {
                    e.consume();
                    markTaskStatus(task, "COMPLETED", task.date(), task.startTime());
                });
                actionBox.getChildren().add(completeBtn);
            } else if ("BACKLOG".equals(currentViewMode)) {
                Button completeBtn = new Button("✅ Done Now");
                completeBtn.setStyle("-fx-background-color: #2ecc71; -fx-text-fill: white; "
                    + "-fx-background-radius: 15; -fx-cursor: hand; -fx-font-size: 11px;");
                completeBtn.setOnAction(e -> {
                    e.consume();
                    markTaskStatus(task, "COMPLETED", task.date(), task.startTime());
                });
                Button rescheduleBtn = new Button("📅 Reschedule");
                rescheduleBtn.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; "
                    + "-fx-background-radius: 15; -fx-cursor: hand; -fx-font-size: 11px;");
                rescheduleBtn.setOnAction(e -> { e.consume(); showRescheduleDialog(task); });
                actionBox.getChildren().addAll(completeBtn, rescheduleBtn);
            }
        }

        if (isAdmin || (isCreator && !"ROUTINE".equals(task.type()) && !"NOTICE".equals(task.type()))) {
            Button delBtn = new Button("🗑️");
            delBtn.setStyle("-fx-background-color: transparent; -fx-cursor: hand;");
            delBtn.setOnAction(e -> deleteTimelineTask(task));
            actionBox.getChildren().add(delBtn);
        }

        if (!actionBox.getChildren().isEmpty()) card.getChildren().add(actionBox);
        return card;
    }

    // ----------------------------------------------------------
    // MARK STATUS
    // ----------------------------------------------------------
    public void markTaskStatus(StudyTask task, String status, String date, String time) {
        new Thread(() -> {
            if (dataService.updateTaskStatus(task.id(), status, date, time)) {
                Platform.runLater(() -> {
                    allTasks.remove(task);
                    allTasks.add(new StudyTask(task.id(), task.title(), date, time,
                        task.durationMinutes(), task.roomNo(), task.type(), task.tags(),
                        task.creatorRole(), task.ctCourse(), task.ctSyllabus(), status, task.importance()));
                    refreshTimeline();
                    showSuccess(status.equals("COMPLETED") ? "Task Completed! 🎉" : "Task Rescheduled! 📅");
                });
            }
        }).start();
    }

    // ----------------------------------------------------------
    // DELETE TASK
    // ----------------------------------------------------------
    public void deleteTimelineTask(StudyTask task) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
            "Are you sure you want to delete '" + task.title() + "'?", ButtonType.YES, ButtonType.NO);
        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.YES) {
                allTasks.remove(task);
                refreshTimeline();
                if (onRefreshCalendar != null) onRefreshCalendar.run();
                new Thread(() -> dataService.deleteTask(task.id(), task.type(), task.creatorRole())).start();
            }
        });
    }

    // ----------------------------------------------------------
    // TASK DETAILS DIALOG
    // ----------------------------------------------------------
    public void showTaskDetailsDialog(StudyTask task) {
        boolean isCT       = task.title() != null && task.title().toUpperCase().contains("CT");
        boolean isPersonal = "PERSONAL".equals(task.type());

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(isCT ? "🚨 CT Details Interface"
            : (isPersonal ? "👤 Personal Task" : "📝 Task Details"));
        dialog.setHeaderText(isCT ? "Manage Class Test Information"
            : (isPersonal ? "Edit your personal task" : "Course Information"));

        boolean isAdmin   = "admin".equals(AuthService.CURRENT_USER_ROLE);
        boolean isCreator = task.creatorRole() != null
            && task.creatorRole().equals(AuthService.CURRENT_USER_ROLE);
        boolean canEdit   = isAdmin
            || (isCreator && !"ROUTINE".equals(task.type()) && !"NOTICE".equals(task.type()));

        ButtonType saveBtn = new ButtonType("Save to Supabase 💾", ButtonBar.ButtonData.OK_DONE);
        if (canEdit) dialog.getDialogPane().getButtonTypes().add(saveBtn);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        GridPane grid = new GridPane();
        grid.setHgap(15); grid.setVgap(15); grid.setPadding(new Insets(20));

        TextField titleField    = new TextField(task.title()); titleField.setEditable(canEdit);
        TextField roomField     = new TextField(
            task.roomNo() == null || task.roomNo().equals("null") ? "" : task.roomNo());
        roomField.setEditable(canEdit);
        TextArea descArea = new TextArea(
            task.tags() == null || task.tags().equals("null") ? "" : task.tags());
        descArea.setEditable(canEdit); descArea.setPrefRowCount(3); descArea.setWrapText(true);

        TextField ctCourseField = new TextField(
            task.ctCourse() == null || task.ctCourse().equals("null") ? "" : task.ctCourse());
        ctCourseField.setEditable(canEdit); ctCourseField.setPromptText("e.g., CSE 105");

        TextArea ctSyllabusArea = new TextArea(
            task.ctSyllabus() == null || task.ctSyllabus().equals("null") ? "" : task.ctSyllabus());
        ctSyllabusArea.setEditable(canEdit);
        ctSyllabusArea.setPromptText("Enter Exam Syllabus / Notes here...");
        ctSyllabusArea.setPrefRowCount(4); ctSyllabusArea.setWrapText(true);

        ComboBox<String> importanceBox = new ComboBox<>();
        importanceBox.getItems().addAll("High", "Medium", "Low");
        importanceBox.setValue(task.importance() != null ? task.importance() : "Medium");
        importanceBox.setDisable(!canEdit);

        if (isCT) {
            grid.add(new Label("📌 CT Title:"), 0, 0);          grid.add(titleField, 1, 0);
            grid.add(new Label("📘 CT Course:"), 0, 1);         grid.add(ctCourseField, 1, 1);
            grid.add(new Label("📍 CT Room:"), 0, 2);           grid.add(roomField, 1, 2);
            grid.add(new Label("🕒 CT Time:"), 0, 3);           grid.add(new Label(task.startTime()), 1, 3);
            grid.add(new Label("📚 CT Syllabus/Notes:"), 0, 4); grid.add(ctSyllabusArea, 1, 4);
        } else if (isPersonal) {
            grid.add(new Label("📌 Title:"), 0, 0);       grid.add(titleField, 1, 0);
            grid.add(new Label("🕒 Time:"), 0, 1);
            grid.add(new Label(task.startTime() + " (" + task.date() + ")"), 1, 1);
            grid.add(new Label("🔥 Importance:"), 0, 2);  grid.add(importanceBox, 1, 2);
            grid.add(new Label("📝 Description:"), 0, 3); grid.add(descArea, 1, 3);
        } else {
            grid.add(new Label("📌 Title:"), 0, 0);       grid.add(titleField, 1, 0);
            grid.add(new Label("🕒 Time slot:"), 0, 1);   grid.add(new Label(task.startTime()), 1, 1);
            grid.add(new Label("📍 Room No:"), 0, 2);     grid.add(roomField, 1, 2);
            grid.add(new Label("📚 Description:"), 0, 3); grid.add(descArea, 1, 3);
        }

        dialog.getDialogPane().setContent(grid);
        dialog.showAndWait().ifPresent(response -> {
            if (response == saveBtn) {
                String newTitle      = titleField.getText().trim();
                String newRoom       = roomField.getText().trim();
                String newDesc       = descArea.getText().trim();
                String newCtCourse   = isCT ? ctCourseField.getText().trim() : task.ctCourse();
                String newCtSyllabus = isCT ? ctSyllabusArea.getText().trim() : task.ctSyllabus();
                String newImportance = isPersonal ? importanceBox.getValue() : task.importance();

                new Thread(() -> {
                    boolean isUpdated = dataService.updateTaskDetails(
                        task.id(), newTitle, newRoom, newDesc, newCtCourse, newCtSyllabus, newImportance);
                    if (isUpdated) {
                        Platform.runLater(() -> {
                            for (int i = 0; i < allTasks.size(); i++) {
                                if (allTasks.get(i).id().equals(task.id())) {
                                    allTasks.set(i, new StudyTask(
                                        task.id(), newTitle, task.date(), task.startTime(),
                                        task.durationMinutes(), newRoom, task.type(), newDesc,
                                        task.creatorRole(), newCtCourse, newCtSyllabus,
                                        task.status(), newImportance));
                                    break;
                                }
                            }
                            refreshTimeline();
                            showSuccess("Task Details Saved to Supabase Successfully! 🚀");
                        });
                    }
                }).start();
            }
        });
    }

    // ----------------------------------------------------------
    // RESCHEDULE DIALOG
    // ----------------------------------------------------------
    public void showRescheduleDialog(StudyTask task) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("📅 Reschedule Task");
        dialog.setHeaderText("Reschedule '" + task.title() + "' to a new date and time.");

        ButtonType saveBtn = new ButtonType("Reschedule 🚀", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveBtn, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(15); grid.setVgap(15); grid.setPadding(new Insets(20));

        DatePicker newDatePicker = new DatePicker(LocalDate.now());
        newDatePicker.setPromptText("Select New Date");
        TextField newTimeField = new TextField(task.startTime());
        newTimeField.setPromptText("e.g., 10:00 AM");

        grid.add(new Label("📅 New Date:"), 0, 0); grid.add(newDatePicker, 1, 0);
        grid.add(new Label("🕒 New Time:"), 0, 1); grid.add(newTimeField, 1, 1);
        dialog.getDialogPane().setContent(grid);

        dialog.showAndWait().ifPresent(response -> {
            if (response == saveBtn && newDatePicker.getValue() != null) {
                String newDate = newDatePicker.getValue().toString();
                String newTime = newTimeField.getText().trim();
                new Thread(() -> {
                    boolean isUpdated = dataService.updateTaskStatus(task.id(), "PENDING", newDate, newTime);
                    if (isUpdated) {
                        Platform.runLater(() -> {
                            for (int i = 0; i < allTasks.size(); i++) {
                                if (allTasks.get(i).id().equals(task.id())) {
                                    allTasks.set(i, new StudyTask(
                                        task.id(), task.title(), newDate, newTime,
                                        task.durationMinutes(), task.roomNo(), task.type(), task.tags(),
                                        task.creatorRole(), task.ctCourse(), task.ctSyllabus(),
                                        "PENDING", task.importance()));
                                    break;
                                }
                            }
                            refreshTimeline();
                            showSuccess("Task Rescheduled to " + newDate + "! 🚀");
                        });
                    }
                }).start();
            }
        });
    }

    // ----------------------------------------------------------
    // MANUAL TASK ENTRY DIALOG
    // ----------------------------------------------------------
    @FXML
    public void showManualTaskEntryDialog() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("👤 Add Personal Task");
        dialog.setHeaderText("Create a new personal study task or event.");

        ButtonType addBtn = new ButtonType("Add Task 🚀", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(addBtn, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(15); grid.setVgap(15); grid.setPadding(new Insets(20));

        TextField titleField  = new TextField(); titleField.setPromptText("Task Title");
        DatePicker datePicker = new DatePicker(LocalDate.now());
        TextField timeField   = new TextField(); timeField.setPromptText("09:00 AM");
        ComboBox<String> importanceBox = new ComboBox<>();
        importanceBox.getItems().addAll("High", "Medium", "Low"); importanceBox.setValue("Medium");
        TextArea descArea = new TextArea(); descArea.setPromptText("Notes..."); descArea.setPrefRowCount(3);

        grid.add(new Label("📌 Title:"), 0, 0);      grid.add(titleField, 1, 0);
        grid.add(new Label("📅 Date:"), 0, 1);       grid.add(datePicker, 1, 1);
        grid.add(new Label("🕒 Time:"), 0, 2);       grid.add(timeField, 1, 2);
        grid.add(new Label("🔥 Importance:"), 0, 3); grid.add(importanceBox, 1, 3);
        grid.add(new Label("📝 Notes:"), 0, 4);      grid.add(descArea, 1, 4);
        dialog.getDialogPane().setContent(grid);

        dialog.showAndWait().ifPresent(response -> {
            if (response == addBtn && !titleField.getText().trim().isEmpty()) {
                StudyTask newTask = new StudyTask(
                    null, titleField.getText().trim(), datePicker.getValue().toString(),
                    timeField.getText().trim().isEmpty() ? "Anytime" : timeField.getText().trim(),
                    60, null, "PERSONAL", descArea.getText().trim(),
                    AuthService.CURRENT_USER_ROLE, null, null, "PENDING", importanceBox.getValue());

                new Thread(() -> {
                    java.util.List<StudyTask> listToSave = new java.util.ArrayList<>();
                    listToSave.add(newTask);
                    if (dataService.saveTasks(listToSave)) {
                        Platform.runLater(() -> {
                            loadTasksFromDatabase(null);
                            showSuccess("Personal Task Added! 🎉");
                        });
                    }
                }).start();
            }
        });
    }

    // ----------------------------------------------------------
    // HELPERS
    // ----------------------------------------------------------
    private void showSuccess(String msg) {
        Platform.runLater(() -> {
            Alert a = new Alert(Alert.AlertType.INFORMATION);
            a.setContentText(msg); a.show();
        });
    }
}