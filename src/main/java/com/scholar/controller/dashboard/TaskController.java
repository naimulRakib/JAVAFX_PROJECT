package com.scholar.controller.dashboard;

import com.scholar.model.StudyTask;
import com.scholar.service.AuthService;
import com.scholar.service.DataService;
import com.scholar.util.PopupHelper;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * TASK CONTROLLER — Timeline, Backlog, Completed, Mark Done, Delete
 * Path: src/main/java/com/scholar/controller/dashboard/TaskController.java
 */
@Component
public class TaskController {

    @Autowired private DataService dataService;

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
            emptyLbl.setStyle(
                "-fx-font-size: 14px; -fx-text-fill: #475569; -fx-padding: 20;");
            timelineContainer.getChildren().add(emptyLbl);
            return;
        }

        for (StudyTask task : displayTasks) {
            timelineContainer.getChildren().add(buildTaskCard(task, currentViewMode));
        }
    }

    // ----------------------------------------------------------
    // BUILD CARD — dark theme
    // ----------------------------------------------------------
    private VBox buildTaskCard(StudyTask task, String currentViewMode) {
        VBox card = new VBox(5);
        card.setStyle(
            "-fx-background-radius: 10; " +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.45), 8, 0, 0, 2); " +
            "-fx-cursor: hand; " +
            "-fx-background-color: #13151f; " +
            "-fx-border-color: #1e2540; " +
            "-fx-border-radius: 10;");
        card.setOnMouseClicked(e -> {
            if (e.getTarget() instanceof Button || e.getTarget() instanceof javafx.scene.text.Text) return;
            Platform.runLater(() -> showTaskDetailsDialog(task));
        });

        if ("NOTICE".equals(task.type())) {
            card.setStyle(
                "-fx-background-color: #1a0a0a; -fx-border-color: #7f1d1d; " +
                "-fx-border-left-width: 5; -fx-border-width: 0 0 0 5; " +
                "-fx-border-radius: 0 10 10 0; -fx-background-radius: 10; " +
                "-fx-padding: 15; " +
                "-fx-effect: dropshadow(gaussian, rgba(239,68,68,0.15), 10, 0, 0, 2); " +
                "-fx-cursor: hand;");
            Label noticeTitle = new Label(task.title());
            noticeTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 16px; -fx-text-fill: #fca5a5;");
            Label noticeContent = new Label(task.tags());
            noticeContent.setWrapText(true);
            noticeContent.setStyle(
                "-fx-font-size: 14px; -fx-text-fill: #94a3b8; -fx-padding: 5 0 0 0;");
            card.getChildren().addAll(noticeTitle, noticeContent);
        } else {
            card.setPadding(new Insets(12));
            String accentColor;
            String cardBg;
            if ("BACKLOG".equals(currentViewMode))        { accentColor = "#ef4444"; cardBg = "#130a0a"; }
            else if ("COMPLETED".equals(currentViewMode)) { accentColor = "#22c55e"; cardBg = "#0a1a10"; }
            else if ("ROUTINE".equals(task.type()))       { accentColor = "#3b82f6"; cardBg = "#0a1020"; }
            else                                          { accentColor = "#8b5cf6"; cardBg = "#100a1a"; }

            card.setStyle(
                "-fx-background-color: " + cardBg + "; " +
                "-fx-border-color: " + accentColor + " transparent transparent transparent; " +
                "-fx-border-width: 0 0 0 4; -fx-border-radius: 0 10 10 0; " +
                "-fx-background-radius: 10; " +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.45), 8, 0, 0, 2); " +
                "-fx-cursor: hand;");

            HBox header = new HBox(10);
            Label timeLbl = new Label("🕒 " + task.startTime()
                + ("BACKLOG".equals(currentViewMode) ? " (" + task.date() + ")" : ""));
            timeLbl.setStyle(
                "-fx-font-weight: bold; -fx-text-fill: #64748b; -fx-font-size: 12px;");
            Label titleLbl = new Label(task.title());
            titleLbl.setStyle(
                "-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: #e2e8f0;");
            header.getChildren().addAll(timeLbl, titleLbl);
            card.getChildren().add(header);

            if (task.roomNo() != null && !task.roomNo().isEmpty() && !task.roomNo().equals("null")) {
                Label roomLbl = new Label("📍 Room: " + task.roomNo());
                roomLbl.setStyle("-fx-text-fill: #475569; -fx-font-size: 12px;");
                card.getChildren().add(roomLbl);
            }
        }

        HBox actionBox = new HBox(10);
        actionBox.setAlignment(Pos.CENTER_RIGHT);
        boolean isAdmin   = "admin".equals(AuthService.CURRENT_USER_ROLE);
        boolean isCreator = task.creatorRole() != null
            && task.creatorRole().equals(AuthService.CURRENT_USER_ROLE);

        String actionBtnBase =
            "-fx-background-radius: 15; -fx-cursor: hand; " +
            "-fx-font-size: 11px; -fx-font-weight: bold; -fx-padding: 5 14;";

        if ("PERSONAL".equals(task.type())) {
            if ("DAILY".equals(currentViewMode)) {
                Button completeBtn = new Button("✅ Done");
                completeBtn.setStyle(actionBtnBase +
                    "-fx-background-color: #166534; -fx-text-fill: #86efac; " +
                    "-fx-border-color: #14532d; -fx-border-radius: 15;");
                completeBtn.setOnAction(e -> {
                    e.consume();
                    markTaskStatus(task, "COMPLETED", task.date(), task.startTime());
                });
                actionBox.getChildren().add(completeBtn);
            } else if ("BACKLOG".equals(currentViewMode)) {
                Button completeBtn = new Button("✅ Done Now");
                completeBtn.setStyle(actionBtnBase +
                    "-fx-background-color: #166534; -fx-text-fill: #86efac; " +
                    "-fx-border-color: #14532d; -fx-border-radius: 15;");
                completeBtn.setOnAction(e -> {
                    e.consume();
                    markTaskStatus(task, "COMPLETED", task.date(), task.startTime());
                });
                Button rescheduleBtn = new Button("📅 Reschedule");
                rescheduleBtn.setStyle(actionBtnBase +
                    "-fx-background-color: #1e3a5f; -fx-text-fill: #93c5fd; " +
                    "-fx-border-color: #1e40af; -fx-border-radius: 15;");
                rescheduleBtn.setOnAction(e -> { e.consume(); showRescheduleDialog(task); });
                actionBox.getChildren().addAll(completeBtn, rescheduleBtn);
            }
        }

        if (isAdmin || (isCreator && !"ROUTINE".equals(task.type()) && !"NOTICE".equals(task.type()))) {
            Button delBtn = new Button("🗑️");
            delBtn.setStyle(
                "-fx-background-color: transparent; -fx-cursor: hand; " +
                "-fx-text-fill: #ef4444; -fx-font-size: 14px;");
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
        PopupHelper.showConfirm(
            resolveOwner(),
            "Delete Task",
            "Are you sure you want to delete '" + task.title() + "'?",
            () -> {
                allTasks.remove(task);
                refreshTimeline();
                if (onRefreshCalendar != null) onRefreshCalendar.run();
                new Thread(() -> dataService.deleteTask(task.id(), task.type(), task.creatorRole())).start();
            }
        );
    }

    // ----------------------------------------------------------
    // TASK DETAILS — PopupHelper dark stage
    // ----------------------------------------------------------
    public void showTaskDetailsDialog(StudyTask task) {
        boolean isCT       = task.title() != null && task.title().toUpperCase().contains("CT");
        boolean isPersonal = "PERSONAL".equals(task.type());
        boolean isAdmin    = "admin".equals(AuthService.CURRENT_USER_ROLE);
        boolean isCreator  = task.creatorRole() != null
            && task.creatorRole().equals(AuthService.CURRENT_USER_ROLE);
        boolean canEdit    = isAdmin
            || (isCreator && !"ROUTINE".equals(task.type()) && !"NOTICE".equals(task.type()));

        // ── Editable fields ────────────────────────────────────
        TextField titleField = new TextField(task.title());
        titleField.setEditable(canEdit);

        TextField roomField = new TextField(
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

        // Apply dark field styles
        String fieldStyle =
            "-fx-background-color: #1e2235; -fx-text-fill: #e2e8f0; " +
            "-fx-border-color: #2d3150; -fx-border-radius: 8; " +
            "-fx-background-radius: 8; -fx-padding: 7 10; -fx-font-size: 13px;";
        String areaStyle =
            "-fx-background-color: #1e2235; -fx-text-fill: #e2e8f0; " +
            "-fx-border-color: #2d3150; -fx-border-radius: 8; " +
            "-fx-background-radius: 8; -fx-padding: 7 10; -fx-font-size: 13px; " +
            "-fx-control-inner-background: #1e2235;";
        for (TextField tf : new TextField[]{titleField, roomField, ctCourseField})
            tf.setStyle(fieldStyle);
        for (TextArea ta : new TextArea[]{descArea, ctSyllabusArea})
            ta.setStyle(areaStyle);
        importanceBox.setStyle(
            "-fx-background-color: #1e2235; -fx-border-color: #2d3150; " +
            "-fx-border-radius: 8; -fx-background-radius: 8;");

        // ── Header banner ──────────────────────────────────────
        String headerGradient = isCT
            ? "linear-gradient(to right, #1a0a00, #7c2d12)"   // CT → amber-red
            : isPersonal
                ? "linear-gradient(to right, #0a0a1a, #1e1b4b)" // personal → indigo
                : "linear-gradient(to right, #0a1020, #0c1a3a)"; // routine → blue

        String headerBadge = isCT ? "🚨  Class Test" : isPersonal ? "👤  Personal Task" : "📝  Task Details";
        String headerBadgeColor = isCT ? "#fbbf24" : isPersonal ? "#a5b4fc" : "#93c5fd";
        String popupTitle  = isCT ? task.title() + " — CT Details"
            : isPersonal ? task.title() : task.title();

        VBox header = new VBox(4);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setStyle("-fx-background-color: " + headerGradient + "; -fx-padding: 18 24 16 24;");
        Label badgeLbl = new Label(headerBadge);
        badgeLbl.setStyle(
            "-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: " + headerBadgeColor + ";");
        Label taskTitleLbl = new Label(task.title());
        taskTitleLbl.setWrapText(true);
        taskTitleLbl.setMaxWidth(420);
        taskTitleLbl.setStyle(
            "-fx-font-size: 17px; -fx-font-weight: bold; -fx-text-fill: white;");
        header.getChildren().addAll(badgeLbl, taskTitleLbl);

        // ── Form body ──────────────────────────────────────────
        GridPane grid = new GridPane();
        grid.setHgap(14); grid.setVgap(12);
        grid.setPadding(new Insets(20, 24, 4, 24));
        grid.setStyle("-fx-background-color: #0d0f1a;");

        if (isCT) {
            grid.add(mkLabel("📌  CT Title"),        0, 0); grid.add(titleField,    1, 0);
            grid.add(mkLabel("📘  CT Course"),       0, 1); grid.add(ctCourseField, 1, 1);
            grid.add(mkLabel("📍  CT Room"),         0, 2); grid.add(roomField,     1, 2);
            grid.add(mkLabel("🕒  CT Time"),         0, 3); grid.add(mkValue(task.startTime()), 1, 3);
            grid.add(mkLabel("📚  Syllabus / Notes"),0, 4); grid.add(ctSyllabusArea,1, 4);
        } else if (isPersonal) {
            grid.add(mkLabel("📌  Title"),      0, 0); grid.add(titleField, 1, 0);
            grid.add(mkLabel("🕒  Time"),       0, 1);
            grid.add(mkValue(task.startTime() + "  (" + task.date() + ")"), 1, 1);
            grid.add(mkLabel("🔥  Importance"), 0, 2); grid.add(importanceBox, 1, 2);
            grid.add(mkLabel("📝  Description"),0, 3); grid.add(descArea, 1, 3);
        } else {
            grid.add(mkLabel("📌  Title"),      0, 0); grid.add(titleField, 1, 0);
            grid.add(mkLabel("🕒  Time slot"),  0, 1); grid.add(mkValue(task.startTime()), 1, 1);
            grid.add(mkLabel("📍  Room No"),    0, 2); grid.add(roomField, 1, 2);
            grid.add(mkLabel("📚  Description"),0, 3); grid.add(descArea, 1, 3);
        }
        GridPane.setHgrow(grid.getChildren().get(1), Priority.ALWAYS);

        // ── Button row ─────────────────────────────────────────
        HBox btnRow = new HBox(12);
        btnRow.setAlignment(Pos.CENTER_RIGHT);
        btnRow.setStyle("-fx-padding: 16 24 20 24; -fx-background-color: #0d0f1a;");

        Button closeBtn = new Button("Close");
        closeBtn.setStyle(
            "-fx-background-color: #1e2235; -fx-text-fill: #94a3b8; " +
            "-fx-font-weight: bold; -fx-background-radius: 8; " +
            "-fx-border-color: #2d3150; -fx-border-radius: 8; " +
            "-fx-padding: 8 22; -fx-cursor: hand; -fx-font-size: 13px;");

        VBox root = new VBox(0);
        root.setStyle("-fx-background-color: #0d0f1a;");
        root.getChildren().addAll(header, grid, btnRow);

        Stage popup = PopupHelper.create(
            resolveOwner(), popupTitle, root,
            460, 360, 520, canEdit ? 480 : 420);

        closeBtn.setOnAction(e -> popup.close());
        btnRow.getChildren().add(closeBtn);

        if (canEdit) {
            Button saveBtn = new Button("💾  Save to Supabase");
            saveBtn.setStyle(
                "-fx-background-color: linear-gradient(to right, #4f46e5, #7c3aed); " +
                "-fx-text-fill: white; -fx-font-weight: bold; " +
                "-fx-background-radius: 8; -fx-padding: 8 22; " +
                "-fx-cursor: hand; -fx-font-size: 13px; " +
                "-fx-effect: dropshadow(gaussian, rgba(99,102,241,0.4), 10, 0, 0, 3);");
            saveBtn.setOnAction(e -> {
                String newTitle      = titleField.getText().trim();
                String newRoom       = roomField.getText().trim();
                String newDesc       = descArea.getText().trim();
                String newCtCourse   = isCT ? ctCourseField.getText().trim() : task.ctCourse();
                String newCtSyllabus = isCT ? ctSyllabusArea.getText().trim() : task.ctSyllabus();
                String newImportance = isPersonal ? importanceBox.getValue() : task.importance();
                popup.close();
                new Thread(() -> {
                    boolean isUpdated = dataService.updateTaskDetails(
                        task.id(), newTitle, newRoom, newDesc,
                        newCtCourse, newCtSyllabus, newImportance);
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
            });
            btnRow.getChildren().add(0, saveBtn);
        }

        popup.show();
    }

    // ----------------------------------------------------------
    // RESCHEDULE — PopupHelper dark stage
    // ----------------------------------------------------------
    public void showRescheduleDialog(StudyTask task) {
        // ── Header ─────────────────────────────────────────────
        VBox header = new VBox(4);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setStyle(
            "-fx-background-color: linear-gradient(to right, #0a1020, #1e3a5f); " +
            "-fx-padding: 18 24 16 24;");
        Label badge = new Label("📅  Reschedule Task");
        badge.setStyle(
            "-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #93c5fd;");
        Label taskTitle = new Label(task.title());
        taskTitle.setWrapText(true);
        taskTitle.setMaxWidth(380);
        taskTitle.setStyle(
            "-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: white;");
        header.getChildren().addAll(badge, taskTitle);

        // ── Inputs ─────────────────────────────────────────────
        DatePicker newDatePicker = new DatePicker(LocalDate.now());
        newDatePicker.setPromptText("Select New Date");
        newDatePicker.setStyle(
            "-fx-background-color: #1e2235; -fx-border-color: #2d3150; " +
            "-fx-border-radius: 8; -fx-background-radius: 8;");

        TextField newTimeField = new TextField(task.startTime());
        newTimeField.setPromptText("e.g., 10:00 AM");
        newTimeField.setStyle(
            "-fx-background-color: #1e2235; -fx-text-fill: #e2e8f0; " +
            "-fx-border-color: #2d3150; -fx-border-radius: 8; " +
            "-fx-background-radius: 8; -fx-padding: 7 10; -fx-font-size: 13px;");

        GridPane grid = new GridPane();
        grid.setHgap(14); grid.setVgap(14);
        grid.setPadding(new Insets(22, 24, 8, 24));
        grid.setStyle("-fx-background-color: #0d0f1a;");
        grid.add(mkLabel("📅  New Date"), 0, 0); grid.add(newDatePicker, 1, 0);
        grid.add(mkLabel("🕒  New Time"), 0, 1); grid.add(newTimeField,  1, 1);
        GridPane.setHgrow(newDatePicker, Priority.ALWAYS);
        GridPane.setHgrow(newTimeField,  Priority.ALWAYS);

        // ── Buttons ────────────────────────────────────────────
        HBox btnRow = new HBox(12);
        btnRow.setAlignment(Pos.CENTER_RIGHT);
        btnRow.setStyle("-fx-padding: 16 24 20 24; -fx-background-color: #0d0f1a;");

        Button cancelBtn = new Button("Cancel");
        cancelBtn.setStyle(
            "-fx-background-color: #1e2235; -fx-text-fill: #94a3b8; " +
            "-fx-font-weight: bold; -fx-background-radius: 8; " +
            "-fx-border-color: #2d3150; -fx-border-radius: 8; " +
            "-fx-padding: 8 22; -fx-cursor: hand; -fx-font-size: 13px;");

        Button saveBtn = new Button("📅  Reschedule");
        saveBtn.setStyle(
            "-fx-background-color: linear-gradient(to right, #1d4ed8, #2563eb); " +
            "-fx-text-fill: white; -fx-font-weight: bold; " +
            "-fx-background-radius: 8; -fx-padding: 8 22; " +
            "-fx-cursor: hand; -fx-font-size: 13px; " +
            "-fx-effect: dropshadow(gaussian, rgba(37,99,235,0.4), 10, 0, 0, 3);");

        btnRow.getChildren().addAll(cancelBtn, saveBtn);

        VBox root = new VBox(0);
        root.setStyle("-fx-background-color: #0d0f1a;");
        root.getChildren().addAll(header, grid, btnRow);

        Stage popup = PopupHelper.create(
            resolveOwner(), "Reschedule — " + task.title(),
            root, 380, 260, 460, 290);

        cancelBtn.setOnAction(e -> popup.close());
        saveBtn.setOnAction(e -> {
            if (newDatePicker.getValue() == null) return;
            String newDate = newDatePicker.getValue().toString();
            String newTime = newTimeField.getText().trim();
            popup.close();
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
        });

        popup.show();
    }

    // ----------------------------------------------------------
    // MANUAL TASK ENTRY — PopupHelper dark stage
    // ----------------------------------------------------------
    @FXML
    public void showManualTaskEntryDialog() {
        // ── Header ─────────────────────────────────────────────
        VBox header = new VBox(4);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setStyle(
            "-fx-background-color: linear-gradient(to right, #0a0a1a, #1e1b4b); " +
            "-fx-padding: 18 24 16 24;");
        Label badge = new Label("👤  Personal Task");
        badge.setStyle(
            "-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #a5b4fc;");
        Label subTitle = new Label("Create a new personal study task or event");
        subTitle.setStyle(
            "-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: white;");
        header.getChildren().addAll(badge, subTitle);

        // ── Fields ─────────────────────────────────────────────
        String fieldStyle =
            "-fx-background-color: #1e2235; -fx-text-fill: #e2e8f0; " +
            "-fx-border-color: #2d3150; -fx-border-radius: 8; " +
            "-fx-background-radius: 8; -fx-padding: 7 10; -fx-font-size: 13px;";
        String areaStyle =
            "-fx-background-color: #1e2235; -fx-text-fill: #e2e8f0; " +
            "-fx-border-color: #2d3150; -fx-border-radius: 8; " +
            "-fx-background-radius: 8; -fx-padding: 7 10; -fx-font-size: 13px; " +
            "-fx-control-inner-background: #1e2235;";

        TextField titleField = new TextField();
        titleField.setPromptText("Task Title");
        titleField.setStyle(fieldStyle);

        DatePicker datePicker = new DatePicker(LocalDate.now());
        datePicker.setStyle(
            "-fx-background-color: #1e2235; -fx-border-color: #2d3150; " +
            "-fx-border-radius: 8; -fx-background-radius: 8;");

        TextField timeField = new TextField();
        timeField.setPromptText("09:00 AM");
        timeField.setStyle(fieldStyle);

        ComboBox<String> importanceBox = new ComboBox<>();
        importanceBox.getItems().addAll("High", "Medium", "Low");
        importanceBox.setValue("Medium");
        importanceBox.setStyle(
            "-fx-background-color: #1e2235; -fx-border-color: #2d3150; " +
            "-fx-border-radius: 8; -fx-background-radius: 8;");

        TextArea descArea = new TextArea();
        descArea.setPromptText("Notes...");
        descArea.setPrefRowCount(3);
        descArea.setStyle(areaStyle);

        GridPane grid = new GridPane();
        grid.setHgap(14); grid.setVgap(12);
        grid.setPadding(new Insets(20, 24, 4, 24));
        grid.setStyle("-fx-background-color: #0d0f1a;");
        grid.add(mkLabel("📌  Title"),      0, 0); grid.add(titleField,    1, 0);
        grid.add(mkLabel("📅  Date"),       0, 1); grid.add(datePicker,    1, 1);
        grid.add(mkLabel("🕒  Time"),       0, 2); grid.add(timeField,     1, 2);
        grid.add(mkLabel("🔥  Importance"), 0, 3); grid.add(importanceBox, 1, 3);
        grid.add(mkLabel("📝  Notes"),      0, 4); grid.add(descArea,      1, 4);
        GridPane.setHgrow(titleField,    Priority.ALWAYS);
        GridPane.setHgrow(datePicker,    Priority.ALWAYS);
        GridPane.setHgrow(timeField,     Priority.ALWAYS);
        GridPane.setHgrow(importanceBox, Priority.ALWAYS);
        GridPane.setHgrow(descArea,      Priority.ALWAYS);

        // ── Buttons ────────────────────────────────────────────
        HBox btnRow = new HBox(12);
        btnRow.setAlignment(Pos.CENTER_RIGHT);
        btnRow.setStyle("-fx-padding: 16 24 20 24; -fx-background-color: #0d0f1a;");

        Button cancelBtn = new Button("Cancel");
        cancelBtn.setStyle(
            "-fx-background-color: #1e2235; -fx-text-fill: #94a3b8; " +
            "-fx-font-weight: bold; -fx-background-radius: 8; " +
            "-fx-border-color: #2d3150; -fx-border-radius: 8; " +
            "-fx-padding: 8 22; -fx-cursor: hand; -fx-font-size: 13px;");

        Button addBtn = new Button("🚀  Add Task");
        addBtn.setStyle(
            "-fx-background-color: linear-gradient(to right, #4f46e5, #7c3aed); " +
            "-fx-text-fill: white; -fx-font-weight: bold; " +
            "-fx-background-radius: 8; -fx-padding: 8 22; " +
            "-fx-cursor: hand; -fx-font-size: 13px; " +
            "-fx-effect: dropshadow(gaussian, rgba(99,102,241,0.4), 10, 0, 0, 3);");

        btnRow.getChildren().addAll(cancelBtn, addBtn);

        VBox root = new VBox(0);
        root.setStyle("-fx-background-color: #0d0f1a;");
        root.getChildren().addAll(header, grid, btnRow);

        Stage popup = PopupHelper.create(
            resolveOwner(), "Add Personal Task",
            root, 440, 420, 500, 480);

        cancelBtn.setOnAction(e -> popup.close());
        addBtn.setOnAction(e -> {
            if (titleField.getText().trim().isEmpty()) return;
            StudyTask newTask = new StudyTask(
                null, titleField.getText().trim(), datePicker.getValue().toString(),
                timeField.getText().trim().isEmpty() ? "Anytime" : timeField.getText().trim(),
                60, null, "PERSONAL", descArea.getText().trim(),
                AuthService.CURRENT_USER_ROLE, null, null, "PENDING", importanceBox.getValue());
            popup.close();
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
        });

        popup.show();
    }

    // ----------------------------------------------------------
    // SHARED POPUP BUILDER HELPERS
    // ----------------------------------------------------------

    /** Dark-styled label for form field keys. */
    private Label mkLabel(String text) {
        Label lbl = new Label(text);
        lbl.setMinWidth(110);
        lbl.setStyle(
            "-fx-text-fill: #64748b; " +
            "-fx-font-size: 12px; " +
            "-fx-font-weight: bold;");
        return lbl;
    }

    /** Dark read-only value label used for non-editable rows. */
    private Label mkValue(String text) {
        Label lbl = new Label(text);
        lbl.setWrapText(true);
        lbl.setStyle(
            "-fx-text-fill: #e2e8f0; " +
            "-fx-font-size: 13px;");
        return lbl;
    }

    // ----------------------------------------------------------
    // HELPERS
    // ----------------------------------------------------------

    private void showSuccess(String msg) {
        PopupHelper.showInfo(resolveOwner(), "Success", msg);
    }

    private Window resolveOwner() {
        if (timelineContainer != null && timelineContainer.getScene() != null)
            return timelineContainer.getScene().getWindow();
        return null;
    }
}