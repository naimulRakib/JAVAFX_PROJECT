package com.scholar.controller;

import com.scholar.model.StudyTask;
import com.scholar.service.*;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.chart.PieChart;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import javafx.stage.FileChooser;
import javafx.scene.web.WebView;
import javafx.scene.input.MouseEvent;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.util.Duration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Controller
public class DashboardController {

    // ==========================================================
    // SERVICES & STATE VARIABLES (Spring @Autowired)
    // ==========================================================
    @Autowired private AISchedulerService aiService;
    @Autowired private ResourceService resourceService;
    @Autowired private CourseService courseService;
    @Autowired private DataService dataService;
    @Autowired private CollaborationService collaborationService;
    @Autowired private ECAService ecaService;
    @Autowired private ChannelService channelService;
    @Autowired private AIController aiController;
    @Autowired private TelegramService telegramService;
    @Autowired
    private org.springframework.context.ApplicationContext springContext; // 🌟 স্প্রিং কন্টেক্সট যোগ করা হলো

    private final List<StudyTask> allTasks = new ArrayList<>();
    private LocalDate selectedDate = LocalDate.now();
    private VBox lastUsedResourceContainer;

    // TreeView Tracking Maps
    private final java.util.Map<TreeItem<String>, Integer> courseMap = new java.util.HashMap<>();
    private final java.util.Map<TreeItem<String>, Integer> segmentMap = new java.util.HashMap<>();
    private final java.util.Map<TreeItem<String>, Integer> topicMap = new java.util.HashMap<>();
    private Integer currentSelectedTopicId = null;

    // Collaboration State
    private CollaborationService.Post currentActivePost;
    private List<CollaborationService.TeamResource> currentResourceData = new ArrayList<>();
    private Timeline chatTimeline;

    // View Mode: DAILY | BACKLOG | COMPLETED
    private String currentViewMode = "DAILY";

    // ==========================================================
    // UI ELEMENTS (Mapped to FXML)
    // ==========================================================
    @FXML private Label channelNameLabel;

    // --- Tab 1: Dashboard ---
    @FXML private TextArea inputArea;
    @FXML private Button generateBtn;
    @FXML private GridPane routineGrid;
    @FXML private VBox announcementList;
    @FXML private VBox timelineContainer;
    @FXML private Button editRoutineBtn;
    @FXML private Button postAnnouncementBtn;
    @FXML private Button btnDailyTasks;
    @FXML private Button btnBacklog;
    @FXML private Button btnCompleted;

    // --- AI Tutor Tab ---
    @FXML private WebView aiWebView;
    @FXML private TextField aiTopicInput;
    @FXML private TextField aiQuestionInput;
    @FXML private Button aiAskBtn;
    @FXML private Button aiTutorBtn;

    // --- Tab 2: Calendar ---
    @FXML private Label monthLabel;
    @FXML private GridPane calendarGrid;

    // --- Tab 3: Resources ---
    @FXML private TextField resTitle;
    @FXML private TextArea resInput;
    @FXML private VBox resourceList;

    // --- Tab 4: Community ---
    @FXML private TreeView<String> communityTree;
    @FXML private Label currentFolderLabel;
    @FXML private TableView<ResourceRow> resourceTable;
    @FXML private TableColumn<ResourceRow, String> colResName;
    @FXML private TableColumn<ResourceRow, String> colResType;
    @FXML private TableColumn<ResourceRow, String> colResDiff;
    @FXML private TableColumn<ResourceRow, String> colResUploader;
    @FXML private TableColumn<ResourceRow, String> colResTags;
    @FXML private TableColumn<ResourceRow, String> colResVotes;
    @FXML private TableColumn<ResourceRow, Void> colResAction;

    // --- Tab 5: Collaboration ---
    @FXML private VBox channelList;
    @FXML private VBox teamList;
    @FXML private VBox roomContainer;
    @FXML private ListView<String> teamResourceList;

    // --- Tab 6: ECA Tracker ---
    @FXML private VBox ecaContainer;

    // --- Tab 7: Settings ---
    @FXML private TextField settingsName;
    @FXML private TextField settingsUsername;
    @FXML private Label settingsEmail;

    // --- Tab 8: Admin Panel ---
    @FXML private Tab adminTab;
    @FXML private VBox pendingListContainer;

    // --- Tab 9: AI Question Bank ---
    @FXML private Tab questionBankTab;

    // --- Community AI ---
    @FXML private TextArea communityAiInput;
    @FXML private Button communityAiBtn;
    @FXML private WebView communityAiWebView;

    // ==========================================================
    // INITIALIZATION
    // ==========================================================
    @FXML
    public void initialize() {
        System.out.println("🚀 ScholarGrid Dashboard Initializing...");

        // Auto-move overdue tasks to backlog in background
        new Thread(() -> dataService.autoMoveToBacklog()).start();

        // Task view mode buttons
        if (btnDailyTasks != null) btnDailyTasks.setOnAction(e -> { currentViewMode = "DAILY"; refreshTimeline(); });
        if (btnBacklog != null)    btnBacklog.setOnAction(e -> { currentViewMode = "BACKLOG"; refreshTimeline(); });
        if (btnCompleted != null)  btnCompleted.setOnAction(e -> { currentViewMode = "COMPLETED"; refreshTimeline(); });

        // AI Tutor floating panel button
        if (aiTutorBtn != null) aiTutorBtn.setOnAction(e -> showAITutorPanel());

        // Channel (Multiverse) setup
        if (AuthService.CURRENT_CHANNEL_ID != -1) {
            if (channelNameLabel != null) channelNameLabel.setText(AuthService.CURRENT_CHANNEL_NAME);
            loadClassRoutine();
            loadClassAnnouncements();
        }

        // Admin security check
        if (adminTab != null) {
            if (!"admin".equals(AuthService.CURRENT_USER_ROLE)) {
                adminTab.setDisable(true);
                adminTab.setText("🔒 Admin Only");
                if (editRoutineBtn != null) editRoutineBtn.setVisible(false);
                if (postAnnouncementBtn != null) postAnnouncementBtn.setVisible(false);
            } else {
                if (editRoutineBtn != null) editRoutineBtn.setOnAction(e -> showAddRoutineDialog());
                if (postAnnouncementBtn != null) postAnnouncementBtn.setOnAction(e -> showAddAnnouncementDialog());
            }
        }

        // Community tree & resource table
        if (communityTree != null) {
            setupResourceTable();
            onRefreshCommunity();
        }

        // Load all modules
        drawCalendar();
        loadResources();
        loadChannels();
        loadTeamsForChannel(1, "Hackathons");
        loadQuestionBankTab();
        loadProfileSettings();
        loadTasksFromDatabase();

        // AI Tutor Tab inline setup
        if (aiAskBtn == null) {
            System.err.println("❌ ERROR: 'aiAskBtn' is NULL! Check FXML link.");
        }
        if (aiWebView != null && aiAskBtn != null) {
            aiWebView.getEngine().loadContent("""
                <html><body style='font-family: Arial; text-align: center; margin-top: 10%;
                color: #2c3e50; background-color: #f4f6f7;'>
                <h2>👋 ScholarGrid AI Ready!</h2>
                <p>Type a topic or question to start.</p>
                </body></html>
                """);
            aiAskBtn.setOnAction(e -> {
                String topic = (aiTopicInput != null) ? aiTopicInput.getText().trim() : "";
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
    }

    // ==========================================================
    // QUESTION BANK TAB
    // ==========================================================
    private void loadQuestionBankTab() {
        if (questionBankTab == null) return;
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/scholar/view/question_bank.fxml"));
            Node content = loader.load();
            questionBankTab.setContent(content);
        } catch (IOException e) {
            System.err.println("❌ Failed to load Question Bank Tab");
        }
    }

    // ==========================================================
    // DATABASE TASK LOADING
    // ==========================================================
    private void loadTasksFromDatabase() {
        new Thread(() -> {
            try {
                List<StudyTask> dbTasks = dataService.loadAllTasks();
                Platform.runLater(() -> {
                    allTasks.clear();
                    allTasks.addAll(dbTasks);
                    refreshTimeline();
                    drawCalendar();
                });
            } catch (Exception e) {
                System.err.println("❌ Cloud Sync Failed: " + e.getMessage());
            }
        }).start();
    }

    // ==========================================================
    // 🌍 1. MULTIVERSE & ADMIN LOGIC
    // ==========================================================
    private void loadClassRoutine() {
        if (routineGrid == null) return;
        routineGrid.getChildren().clear();
        String sql = "SELECT * FROM routines WHERE channel_id = ? ORDER BY time_slot ASC";
        new Thread(() -> {
            try (Connection conn = DatabaseConnection.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, AuthService.CURRENT_CHANNEL_ID);
                ResultSet rs = pstmt.executeQuery();
                List<String[]> data = new ArrayList<>();
                while (rs.next()) {
                    data.add(new String[]{
                        rs.getString("day_name"), rs.getString("course_code"),
                        rs.getString("time_slot"), rs.getString("room_no")
                    });
                }
                Platform.runLater(() -> {
                    int row = 1;
                    for (String[] r : data) {
                        routineGrid.add(new Label(r[0]), 0, row);
                        routineGrid.add(new Label(r[1]), 1, row);
                        routineGrid.add(new Label(r[2]), 2, row);
                        routineGrid.add(new Label(r[3]), 3, row);
                        row++;
                    }
                });
            } catch (SQLException e) { e.printStackTrace(); }
        }).start();
    }

    private void loadClassAnnouncements() {
        if (announcementList == null) return;
        announcementList.getChildren().clear();
        String sql = "SELECT * FROM announcements WHERE channel_id = ? ORDER BY created_at DESC";
        new Thread(() -> {
            try (Connection conn = DatabaseConnection.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, AuthService.CURRENT_CHANNEL_ID);
                ResultSet rs = pstmt.executeQuery();
                List<VBox> cards = new ArrayList<>();
                while (rs.next()) {
                    VBox card = new VBox(5);
                    card.setStyle("-fx-background-color: white; -fx-padding: 10; -fx-background-radius: 5; "
                        + "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.1), 5, 0, 0, 0);");
                    Label t = new Label(rs.getString("title")); t.setStyle("-fx-font-weight: bold;");
                    Label c = new Label(rs.getString("content")); c.setWrapText(true);
                    card.getChildren().addAll(t, c);
                    cards.add(card);
                }
                Platform.runLater(() -> announcementList.getChildren().addAll(cards));
            } catch (SQLException e) { e.printStackTrace(); }
        }).start();
    }

    private void saveRoutineToDB(String day, String course, String time, String room) {
        String sql = "INSERT INTO routines (day_name, course_code, time_slot, room_no, channel_id) VALUES (?, ?, ?, ?, ?)";
        new Thread(() -> {
            try (Connection conn = DatabaseConnection.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, day); pstmt.setString(2, course);
                pstmt.setString(3, time); pstmt.setString(4, room);
                pstmt.setInt(5, AuthService.CURRENT_CHANNEL_ID);
                pstmt.executeUpdate();
                Platform.runLater(this::loadClassRoutine);
            } catch (SQLException e) { e.printStackTrace(); }
        }).start();
    }

    // ==========================================================
    // 📢 ADMIN BROADCAST SYSTEM (AI POWERED)
    // ==========================================================
    private void showAddRoutineDialog()      { showAdminBroadcastDialog(true); }
    private void showAddAnnouncementDialog() { showAdminBroadcastDialog(false); }

    private void showAdminBroadcastDialog(boolean isRoutine) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(isRoutine ? "📅 Broadcast Varsity Routine" : "📢 Broadcast Official Notice");
        dialog.setHeaderText("Paste raw text or table data. AI will automatically parse and distribute it to all students!");

        ButtonType broadcastBtn = new ButtonType("Broadcast 🚀", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(broadcastBtn, ButtonType.CANCEL);

        TextArea inputArea = new TextArea();
        inputArea.setPromptText(isRoutine
            ? "E.g. SAT 08:00 CSE105 Room302\nSUN 11:00 MATH143..."
            : "E.g. Notice: Lab Final on 25th March 10:00 AM at Room 405...");
        inputArea.setPrefRowCount(10); inputArea.setPrefColumnCount(40);
        inputArea.setWrapText(true);
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
                            refreshTimeline(); drawCalendar();
                            showSuccess("Successfully Broadcasted " + generatedTasks.size() + " items! ⚡");
                        }
                    });
                }).start();
            }
        });
    }

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
            if (channelService.updateMemberStatus(studentId, AuthService.CURRENT_CHANNEL_ID, status)) {
                Platform.runLater(this::loadPendingRequests);
            }
        }).start();
    }

    // ==========================================================
    // 📅 2. ROUTINE & CALENDAR LOGIC (Personal)
    // ==========================================================
    @FXML
    public void onGenerateClick() {
        String userText = inputArea.getText().trim();
        if (userText.isEmpty()) return;
        generateBtn.setText("Thinking... 🧠");
        generateBtn.setDisable(true);

        new Thread(() -> {
            try {
                List<StudyTask> aiGeneratedTasks = aiService.generateSchedule(userText);
                if (aiGeneratedTasks == null || aiGeneratedTasks.isEmpty()) {
                    Platform.runLater(() -> {
                        showError("AI Error ❌: AI couldn't understand the input. Please write clearly!");
                        generateBtn.setText("Generate Routine ⚡");
                        generateBtn.setDisable(false);
                    });
                    return;
                }

                List<StudyTask> finalPersonalTasks = new ArrayList<>();
                String todayDate = LocalDate.now().toString();
                for (StudyTask t : aiGeneratedTasks) {
                    String safeDate = (t.date() != null && t.date().matches("\\d{4}-\\d{2}-\\d{2}"))
                        ? t.date() : todayDate;
                    finalPersonalTasks.add(new StudyTask(
                        null, t.title(), safeDate, t.startTime(),
                        t.durationMinutes() != null ? t.durationMinutes() : 60,
                        t.roomNo(), "PERSONAL", t.tags(), "student",
                        t.ctCourse(), t.ctSyllabus(), "PENDING", "Medium"
                    ));
                }

                boolean isSaved = dataService.saveTasks(finalPersonalTasks);
                Platform.runLater(() -> {
                    if (isSaved) { loadTasksFromDatabase(); showSuccess("Personal Task Added to Timeline! 🎉"); }
                    else showError("Database Error ❌: Failed to save task. Please check Supabase table.");
                    generateBtn.setText("Generate Routine ⚡");
                    generateBtn.setDisable(false);
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    showError("System Error ❌: " + e.getMessage());
                    generateBtn.setText("Generate Routine ⚡");
                    generateBtn.setDisable(false);
                });
            }
        }).start();
    }

    private void drawCalendar() {
        if (calendarGrid == null || monthLabel == null) return;
        calendarGrid.getChildren().clear();
        YearMonth currentMonth = YearMonth.now();
        monthLabel.setText(currentMonth.getMonth().name() + " " + currentMonth.getYear());

        String[] daysOfWeek = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
        for (int i = 0; i < 7; i++) {
            Label dayLabel = new Label(daysOfWeek[i]);
            dayLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #7f8c8d; -fx-font-size: 15px;");
            VBox headerBox = new VBox(dayLabel);
            headerBox.setAlignment(Pos.CENTER);
            headerBox.setPadding(new Insets(10, 0, 15, 0));
            calendarGrid.add(headerBox, i, 0);
        }

        int startDay = currentMonth.atDay(1).getDayOfWeek().getValue() % 7;
        int daysInMonth = currentMonth.lengthOfMonth();
        int row = 1, col = startDay;

        for (int day = 1; day <= daysInMonth; day++) {
            LocalDate cellDate = currentMonth.atDay(day);
            calendarGrid.add(createDayBox(day, cellDate), col, row);
            col++;
            if (col > 6) { col = 0; row++; }
        }
    }

    private VBox createDayBox(int day, LocalDate date) {
        VBox box = new VBox(5);
        box.setAlignment(Pos.TOP_CENTER);
        box.setPadding(new Insets(5));
        box.setPrefSize(100, 100);

        Label lbl = new Label(String.valueOf(day));
        lbl.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");
        LocalDate today = LocalDate.now();
        String boxStyle = "-fx-background-color: white; -fx-border-color: #ecf0f1; -fx-cursor: hand;";

        if (date.equals(today)) {
            boxStyle = "-fx-background-color: #3498db; -fx-border-color: #2980b9; "
                + "-fx-background-radius: 5; -fx-border-radius: 5; -fx-cursor: hand;";
            lbl.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: white;");
        }
        if (date.equals(selectedDate)) {
            if (date.equals(today)) {
                boxStyle = "-fx-background-color: #3498db; -fx-border-color: #e74c3c; "
                    + "-fx-border-width: 2; -fx-background-radius: 5; -fx-border-radius: 5; -fx-cursor: hand;";
            } else {
                boxStyle = "-fx-background-color: #ebf5fb; -fx-border-color: #3498db; "
                    + "-fx-border-width: 2; -fx-background-radius: 5; -fx-border-radius: 5; -fx-cursor: hand;";
            }
        }

        box.setStyle(boxStyle);
        box.getChildren().add(lbl);

        List<StudyTask> tasksForThisDay = allTasks.stream()
            .filter(t -> t.date() != null && t.date().equals(date.toString()))
            .collect(Collectors.toList());

        if (!tasksForThisDay.isEmpty()) {
            Circle dot = new Circle(3, Color.web(date.equals(today) ? "#ffffff" : "#e74c3c"));
            box.getChildren().add(dot);
            StringBuilder tooltipText = new StringBuilder("📌 Tasks:\n");
            for (StudyTask t : tasksForThisDay) {
                tooltipText.append("• ").append(t.title()).append(" (").append(t.startTime()).append(")\n");
            }
            Tooltip tooltip = new Tooltip(tooltipText.toString().trim());
            tooltip.setStyle("-fx-font-size: 12px; -fx-background-color: rgba(44,62,80,0.9); "
                + "-fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 8px; -fx-background-radius: 5px;");
            Tooltip.install(box, tooltip);
        }

        box.setOnMouseClicked(e -> { selectedDate = date; refreshTimeline(); drawCalendar(); });
        return box;
    }

    // ==========================================================
    // TIMELINE REFRESH
    // ==========================================================
    private void refreshTimeline() {
        if (timelineContainer == null) return;
        timelineContainer.getChildren().clear();

        List<StudyTask> displayTasks = allTasks.stream()
            .filter(t -> {
                if ("BACKLOG".equals(currentViewMode)) {
                    return "PERSONAL".equals(t.type()) && "BACKLOG".equals(t.status());
                } else if ("COMPLETED".equals(currentViewMode)) {
                    return "PERSONAL".equals(t.type()) && "COMPLETED".equals(t.status());
                } else {
                    if (t.date() == null || !t.date().equals(selectedDate.toString())) return false;
                    if ("PERSONAL".equals(t.type())) {
                        return !"COMPLETED".equals(t.status()) && !"BACKLOG".equals(t.status());
                    }
                    return true;
                }
            })
            .sorted((t1, t2) -> {
                if ("Anytime".equalsIgnoreCase(t1.startTime())) return -1;
                if ("Anytime".equalsIgnoreCase(t2.startTime())) return 1;
                try {
                    java.time.format.DateTimeFormatter f = java.time.format.DateTimeFormatter.ofPattern("hh:mm a");
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
            boolean isAdmin = "admin".equals(AuthService.CURRENT_USER_ROLE);
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
            timelineContainer.getChildren().add(card);
        }
    }

    private void markTaskStatus(StudyTask task, String status, String date, String time) {
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

    // ==========================================================
    // 🗑️ TASK DELETION
    // ==========================================================
    private void deleteTimelineTask(StudyTask task) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
            "Are you sure you want to delete '" + task.title() + "'?", ButtonType.YES, ButtonType.NO);
        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.YES) {
                allTasks.remove(task);
                refreshTimeline();
                drawCalendar();
                new Thread(() -> dataService.deleteTask(task.id(), task.type(), task.creatorRole())).start();
            }
        });
    }

    // ==========================================================
    // 📝 TASK DETAILS DIALOG (CT + PERSONAL + ROUTINE)
    // ==========================================================
    private void showTaskDetailsDialog(StudyTask task) {
        boolean isCT = task.title() != null && task.title().toUpperCase().contains("CT");
        boolean isPersonal = "PERSONAL".equals(task.type());

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(isCT ? "🚨 CT Details Interface"
            : (isPersonal ? "👤 Personal Task" : "📝 Task Details"));
        dialog.setHeaderText(isCT ? "Manage Class Test Information"
            : (isPersonal ? "Edit your personal task" : "Course Information"));

        boolean isAdmin = "admin".equals(AuthService.CURRENT_USER_ROLE);
        boolean isCreator = task.creatorRole() != null
            && task.creatorRole().equals(AuthService.CURRENT_USER_ROLE);
        boolean canEdit = isAdmin
            || (isCreator && !"ROUTINE".equals(task.type()) && !"NOTICE".equals(task.type()));

        ButtonType saveBtn = new ButtonType("Save to Supabase 💾", ButtonBar.ButtonData.OK_DONE);
        if (canEdit) dialog.getDialogPane().getButtonTypes().add(saveBtn);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        GridPane grid = new GridPane();
        grid.setHgap(15); grid.setVgap(15); grid.setPadding(new Insets(20));

        TextField titleField = new TextField(task.title()); titleField.setEditable(canEdit);
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

        if (isCT) {
            grid.add(new Label("📌 CT Title:"), 0, 0);        grid.add(titleField, 1, 0);
            grid.add(new Label("📘 CT Course:"), 0, 1);       grid.add(ctCourseField, 1, 1);
            grid.add(new Label("📍 CT Room:"), 0, 2);         grid.add(roomField, 1, 2);
            grid.add(new Label("🕒 CT Time:"), 0, 3);         grid.add(new Label(task.startTime()), 1, 3);
            grid.add(new Label("📚 CT Syllabus/Notes:"), 0, 4); grid.add(ctSyllabusArea, 1, 4);
        } else if (isPersonal) {
            grid.add(new Label("📌 Title:"), 0, 0);       grid.add(titleField, 1, 0);
            grid.add(new Label("🕒 Time:"), 0, 1);
            grid.add(new Label(task.startTime() + " (" + task.date() + ")"), 1, 1);
            grid.add(new Label("🔥 Importance:"), 0, 2);  grid.add(importanceBox, 1, 2);
            grid.add(new Label("📝 Description:"), 0, 3); grid.add(descArea, 1, 3);
        } else {
            grid.add(new Label("📌 Title:"), 0, 0);      grid.add(titleField, 1, 0);
            grid.add(new Label("🕒 Time slot:"), 0, 1);  grid.add(new Label(task.startTime()), 1, 1);
            grid.add(new Label("📍 Room No:"), 0, 2);    grid.add(roomField, 1, 2);
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

    // ==========================================================
    // 📅 RESCHEDULE BACKLOG TASK
    // ==========================================================
    private void showRescheduleDialog(StudyTask task) {
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

    // ==========================================================
    // ➕ MANUAL PERSONAL TASK CREATOR
    // ==========================================================
    @FXML
    public void showManualTaskEntryDialog() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("👤 Add Personal Task");
        dialog.setHeaderText("Create a new personal study task or event.");

        ButtonType addBtn = new ButtonType("Add Task 🚀", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(addBtn, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(15); grid.setVgap(15); grid.setPadding(new Insets(20));

        TextField titleField = new TextField(); titleField.setPromptText("Task Title (e.g. Do Math 101)");
        DatePicker datePicker = new DatePicker(LocalDate.now());
        TextField timeField = new TextField(); timeField.setPromptText("09:00 AM");
        ComboBox<String> importanceBox = new ComboBox<>();
        importanceBox.getItems().addAll("High", "Medium", "Low"); importanceBox.setValue("Medium");
        TextArea descArea = new TextArea(); descArea.setPromptText("Notes...");
        descArea.setPrefRowCount(3);

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
                    List<StudyTask> listToSave = new ArrayList<>();
                    listToSave.add(newTask);
                    if (dataService.saveTasks(listToSave)) {
                        Platform.runLater(() -> { loadTasksFromDatabase(); showSuccess("Personal Task Added! 🎉"); });
                    }
                }).start();
            }
        });
    }

    // ==========================================================
    // 🧠 3. RESOURCE ENGINE (AI STUDY GUIDES)
    // ==========================================================
    @FXML
    public void onGenerateResource() {
        String topic = resTitle.getText().trim(); String content = resInput.getText().trim();
        if (topic.isEmpty() || content.isEmpty()) return;
        resInput.setText("🤖 AI Analyzing content...");
        new Thread(() -> {
            String guide = aiService.generateStudyGuide(topic, content);
            resourceService.addResource(topic, "", "AI Guide", guide);
            Platform.runLater(() -> { resInput.clear(); resTitle.clear(); loadResources(); });
        }).start();
    }

    @FXML
    public void loadResources() {
        if (resourceList == null) return;
        resourceList.getChildren().clear();
        new Thread(() -> {
            List<ResourceService.Resource> items = resourceService.getAllResources();
            Platform.runLater(() -> {
                for (var item : items) {
                    Button b = new Button("🎓 " + item.title());
                    b.setMaxWidth(Double.MAX_VALUE);
                    b.setStyle("-fx-alignment: center-left; -fx-background-color: white; -fx-border-color: #bdc3c7;");
                    b.setOnAction(e -> showFullNote(item));
                    resourceList.getChildren().add(b);
                }
            });
        }).start();
    }

    private void showFullNote(ResourceService.Resource res) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setHeaderText(res.title()); alert.setContentText(res.content()); alert.show();
    }

    // ==========================================================
    // 🎓 COMMUNITY TABLE & ACTIONS LOGIC
    // ==========================================================
    public void setupResourceTable() {
        if (colResName == null) return;
        colResName.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("name"));
        colResType.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("type"));
        colResDiff.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("diff"));
        colResUploader.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("uploader"));
        colResTags.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("tags"));
        colResVotes.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("votes"));

        colResAction.setCellFactory(param -> new TableCell<ResourceRow, Void>() {
            private final Button previewBtn  = new Button("👁️");
            private final Button downloadBtn = new Button("⬇️");
            private final Button upBtn       = new Button("👍");
            private final Button downBtn     = new Button("👎");
            private final Button doneBtn     = new Button("✅");
            private final Button statsBtn    = new Button("📊");
            private final Button discussBtn  = new Button("💬");
            private final Button editBtn     = new Button("✏️");
            private final Button delBtn      = new Button("🗑️");
            private final HBox pane = new HBox(5);

            {
                String btnStyle = "-fx-cursor: hand; -fx-padding: 4 6; -fx-background-radius: 5; "
                    + "-fx-text-fill: white; -fx-font-size: 11px; -fx-font-weight: bold;";
                previewBtn.setStyle(btnStyle + "-fx-background-color: #2c3e50;");
                downloadBtn.setStyle(btnStyle + "-fx-background-color: #16a085;");
                upBtn.setStyle(btnStyle + "-fx-background-color: #27ae60;");
                downBtn.setStyle(btnStyle + "-fx-background-color: #e74c3c;");
                doneBtn.setStyle(btnStyle + "-fx-background-color: #2980b9;");
                statsBtn.setStyle(btnStyle + "-fx-background-color: #8e44ad;");
                discussBtn.setStyle(btnStyle + "-fx-background-color: #34495e;");
                editBtn.setStyle(btnStyle + "-fx-background-color: #f39c12;");
                delBtn.setStyle(btnStyle + "-fx-background-color: #c0392b;");

                previewBtn.setTooltip(new Tooltip("In-App Preview"));
                downloadBtn.setTooltip(new Tooltip("Direct Download"));
                upBtn.setTooltip(new Tooltip("Upvote"));
                downBtn.setTooltip(new Tooltip("Downvote"));
                doneBtn.setTooltip(new Tooltip("Mark as Done / Edit Note"));
                statsBtn.setTooltip(new Tooltip("Statistics & Reviews"));
                discussBtn.setTooltip(new Tooltip("Open Discussion"));
                editBtn.setTooltip(new Tooltip("Edit Resource"));
                delBtn.setTooltip(new Tooltip("Delete Resource"));

                upBtn.setOnAction(e -> processVote(getTableView().getItems().get(getIndex()), 1));
                downBtn.setOnAction(e -> processVote(getTableView().getItems().get(getIndex()), -1));

                previewBtn.setOnAction(e -> {
                    CourseService.Resource res = getTableView().getItems().get(getIndex()).getRawResource();
                    showInAppPreview(res.link(), res.title());
                });
                downloadBtn.setOnAction(e -> {
                    CourseService.Resource res = getTableView().getItems().get(getIndex()).getRawResource();
                    directDownloadFile(res.link(), res.title());
                });
                doneBtn.setOnAction(e -> showResourceCompletionDialog(getTableView().getItems().get(getIndex())));
                statsBtn.setOnAction(e -> showStatisticsDialog(getTableView().getItems().get(getIndex()).getRawResource()));
                discussBtn.setOnAction(e -> showDiscussionPanel(getTableView().getItems().get(getIndex()).getRawResource()));
                editBtn.setOnAction(e -> showEditResourceDialog(getTableView().getItems().get(getIndex()).getRawResource()));
                delBtn.setOnAction(e -> {
                    ResourceRow row = getTableView().getItems().get(getIndex());
                    Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Delete this resource?", ButtonType.YES, ButtonType.NO);
                    confirm.showAndWait().ifPresent(res -> {
                        if (res == ButtonType.YES) new Thread(() -> {
                            if (courseService.deleteResource(row.getRawResource().id()))
                                Platform.runLater(() -> loadResourcesForTopic(currentSelectedTopicId));
                        }).start();
                    });
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) { setGraphic(null); return; }

                ResourceRow row = getTableView().getItems().get(getIndex());
                pane.getChildren().clear();
                pane.getChildren().addAll(previewBtn, downloadBtn, upBtn, downBtn, doneBtn, statsBtn, discussBtn);

                boolean canManage = "admin".equals(AuthService.CURRENT_USER_ROLE)
                    || (row.getRawResource().creatorId() != null
                        && row.getRawResource().creatorId().equals(AuthService.CURRENT_USER_ID));
                if (canManage) pane.getChildren().addAll(editBtn, delBtn);

                // Done state
                if (row.getRawResource().isCompleted()) {
                    doneBtn.setText("✅ Edit Note");
                    doneBtn.setStyle("-fx-background-color: #16a085; -fx-text-fill: white; "
                        + "-fx-padding: 4 6; -fx-background-radius: 5; -fx-font-size: 11px; -fx-font-weight: bold;");
                } else {
                    doneBtn.setText("✅ Done");
                    doneBtn.setStyle("-fx-background-color: #2980b9; -fx-text-fill: white; "
                        + "-fx-padding: 4 6; -fx-background-radius: 5; -fx-font-size: 11px; -fx-font-weight: bold;");
                }

                // Vote state
                if (row.getRawResource().hasUserVoted()) {
                    upBtn.setDisable(true); downBtn.setDisable(true);
                } else {
                    upBtn.setDisable(false); downBtn.setDisable(false);
                }

                setGraphic(pane); setAlignment(Pos.CENTER);
            }
        });

        communityTree.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                if (currentFolderLabel != null) currentFolderLabel.setText(newVal.getValue());
                if (topicMap.containsKey(newVal)) {
                    currentSelectedTopicId = topicMap.get(newVal);
                    loadResourcesForTopic(currentSelectedTopicId);
                } else {
                    currentSelectedTopicId = null;
                    resourceTable.getItems().clear();
                }
            }
        });
    }

    private void processVote(ResourceRow row, int voteType) {
        new Thread(() -> {
            boolean success = courseService.submitVote(row.getRawResource().id(), voteType);
            Platform.runLater(() -> {
                if (success) loadResourcesForTopic(currentSelectedTopicId);
                else System.err.println("❌ Vote failed.");
            });
        }).start();
    }

    @FXML
    public void onRefreshCommunity() {
        if (communityTree == null) return;
        TreeItem<String> root = new TreeItem<>("📚 University Courses");
        root.setExpanded(true);
        courseMap.clear(); segmentMap.clear(); topicMap.clear();

        new Thread(() -> {
            List<String> courses = courseService.getAllCourseCodes();
            Platform.runLater(() -> {
                communityTree.setRoot(root);
                for (String code : courses) {
                    TreeItem<String> courseNode = new TreeItem<>("📘 " + code);
                    int courseId = courseService.getCourseId(code);
                    courseMap.put(courseNode, courseId);
                    root.getChildren().add(courseNode);

                    new Thread(() -> {
                        List<CourseService.Segment> segments = courseService.getSegments(courseId);
                        Platform.runLater(() -> {
                            TreeItem<String> ctGroup      = new TreeItem<>(" Class Tests (CT)");
                            TreeItem<String> basicsGroup  = new TreeItem<>("Basic Building");
                            TreeItem<String> finalGroup   = new TreeItem<>("Term Final");
                            TreeItem<String> othersGroup  = new TreeItem<>("📁 Others");

                            for (var seg : segments) {
                                String segName = seg.name();
                                TreeItem<String> segNode = new TreeItem<>("📂 " + segName);
                                segmentMap.put(segNode, seg.id());

                                if (segName.toUpperCase().contains("CT")) ctGroup.getChildren().add(segNode);
                                else if (segName.toLowerCase().contains("basic")) basicsGroup.getChildren().add(segNode);
                                else if (segName.toLowerCase().contains("final")) finalGroup.getChildren().add(segNode);
                                else othersGroup.getChildren().add(segNode);

                                new Thread(() -> {
                                    List<CourseService.Topic> topics = courseService.getTopics(seg.id());
                                    Platform.runLater(() -> {
                                        for (var topic : topics) {
                                            TreeItem<String> topicNode = new TreeItem<>("📄 " + topic.title());
                                            topicMap.put(topicNode, topic.id());
                                            segNode.getChildren().add(topicNode);
                                        }
                                    });
                                }).start();
                            }

                            if (!basicsGroup.getChildren().isEmpty()) courseNode.getChildren().add(basicsGroup);
                            if (!ctGroup.getChildren().isEmpty())     courseNode.getChildren().add(ctGroup);
                            if (!finalGroup.getChildren().isEmpty())  courseNode.getChildren().add(finalGroup);
                            if (!othersGroup.getChildren().isEmpty()) courseNode.getChildren().add(othersGroup);
                        });
                    }).start();
                }
            });
        }).start();
    }

    @FXML
    public void onAddNewFolder() {
        TreeItem<String> selected = communityTree.getSelectionModel().getSelectedItem();
        if (selected == null || selected == communityTree.getRoot()) {
            TextInputDialog dialog = new TextInputDialog();
            dialog.setTitle("New Course"); dialog.setHeaderText("Create a new Course");
            dialog.showAndWait().ifPresent(code -> {
                new Thread(() -> {
                    if (courseService.addCourse(code, "Community Resource"))
                        Platform.runLater(this::onRefreshCommunity);
                }).start();
            });
        } else if (segmentMap.containsKey(selected)) {
            TextInputDialog dialog = new TextInputDialog();
            dialog.setTitle("New Topic"); dialog.setHeaderText("Add Topic in " + selected.getValue());
            dialog.showAndWait().ifPresent(name -> {
                new Thread(() -> {
                    if (courseService.addTopic(segmentMap.get(selected), name.trim(), "General"))
                        Platform.runLater(this::onRefreshCommunity);
                }).start();
            });
        } else {
            showError("❌ Please select 'University Courses' to add a Course, or a sub-folder to add a Topic.");
        }
    }

    // ==========================================================
    // ✏️ EDIT RESOURCE DIALOG
    // ==========================================================
    private void showEditResourceDialog(CourseService.Resource res) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("✏️ Edit Resource");
        ButtonType saveBtn = new ButtonType("Update Resource", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveBtn, ButtonType.CANCEL);

        GridPane grid = new GridPane(); grid.setHgap(10); grid.setVgap(15); grid.setPadding(new Insets(20));
        TextField titleField    = new TextField(res.title());
        TextField linkField     = new TextField(res.link());
        ComboBox<String> typeCombo = new ComboBox<>();
        typeCombo.getItems().addAll("LINK", "PDF", "Video", "Note");
        typeCombo.setValue(res.type() != null ? res.type() : "LINK");
        ComboBox<String> diffCombo = new ComboBox<>();
        diffCombo.getItems().addAll("Easy", "Medium", "Hard");
        diffCombo.setValue(res.difficulty() != null ? res.difficulty() : "Medium");
        TextField durationField = new TextField(res.duration() != null ? res.duration() : "");
        TextField tagsField     = new TextField(res.tags() != null ? res.tags() : "");
        TextArea descField      = new TextArea(res.description() != null ? res.description() : "");
        descField.setPrefRowCount(3);

        grid.add(new Label("Title * :"), 0, 0);       grid.add(titleField, 1, 0);
        grid.add(new Label("Drive Link * :"), 0, 1);  grid.add(linkField, 1, 1);
        grid.add(new Label("Type :"), 0, 2);          grid.add(typeCombo, 1, 2);
        grid.add(new Label("Difficulty :"), 0, 3);    grid.add(diffCombo, 1, 3);
        grid.add(new Label("Duration :"), 0, 4);      grid.add(durationField, 1, 4);
        grid.add(new Label("Tags :"), 0, 5);          grid.add(tagsField, 1, 5);
        grid.add(new Label("Description :"), 0, 6);   grid.add(descField, 1, 6);
        dialog.getDialogPane().setContent(grid);

        dialog.showAndWait().ifPresent(response -> {
            if (response == saveBtn && !titleField.getText().isEmpty() && !linkField.getText().isEmpty()) {
                new Thread(() -> {
                    boolean success = courseService.updateResource(
                        res.id(), titleField.getText().trim(), linkField.getText().trim(),
                        typeCombo.getValue(), descField.getText().trim(),
                        tagsField.getText().trim(), diffCombo.getValue(), durationField.getText().trim());
                    Platform.runLater(() -> {
                        if (success) {
                            loadResourcesForTopic(currentSelectedTopicId);
                            new Alert(Alert.AlertType.INFORMATION, "Resource Updated Successfully!").show();
                        }
                    });
                }).start();
            }
        });
    }

    private void loadResourcesForTopic(int topicId) {
        new Thread(() -> {
            List<CourseService.Resource> resources = courseService.getResources(topicId);
            Platform.runLater(() -> {
                List<ResourceRow> rows = new ArrayList<>();
                for (var res : resources) {
                    String uploader = res.creatorName() != null ? res.creatorName() : "Student";
                    String type     = res.type() != null ? res.type() : "LINK";
                    String diff     = res.difficulty() != null ? res.difficulty() : "Medium";
                    String tags     = res.tags() != null ? res.tags() : "General";
                    String votes    = "👍 " + res.upvotes() + " | 👎 " + res.downvotes();
                    rows.add(new ResourceRow(res.title(), type, diff, uploader, tags, votes, res));
                }
                resourceTable.getItems().setAll(rows);
            });
        }).start();
    }

    // ==========================================================
    // 📤 UPLOAD RESOURCE (Manual + AI Summary)
    // ==========================================================
    @FXML
    public void onUploadResourceClick() {
        if (currentSelectedTopicId == null) {
            showError("❌ Please select a specific Topic from the left panel first!"); return;
        }
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("📤 Upload Resource");
        ButtonType saveBtn = new ButtonType("Save Resource", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveBtn, ButtonType.CANCEL);

        GridPane grid = new GridPane(); grid.setHgap(10); grid.setVgap(15); grid.setPadding(new Insets(20));
        TextField titleField    = new TextField(); titleField.setPromptText("E.g., Term Final Questions 2023");
        TextField linkField     = new TextField("https://");
        ComboBox<String> typeCombo = new ComboBox<>();
        typeCombo.getItems().addAll("LINK", "PDF", "Video", "Note"); typeCombo.setValue("LINK");
        ComboBox<String> diffCombo = new ComboBox<>();
        diffCombo.getItems().addAll("Easy", "Medium", "Hard"); diffCombo.setValue("Medium");
        TextField durationField = new TextField(); durationField.setPromptText("e.g., 30 mins");
        TextField tagsField     = new TextField(); tagsField.setPromptText("e.g., #Questions");
        TextArea descField      = new TextArea(); descField.setPromptText("Resource details..."); descField.setPrefRowCount(3);

        grid.add(new Label("Title * :"), 0, 0);      grid.add(titleField, 1, 0);
        grid.add(new Label("Drive Link * :"), 0, 1); grid.add(linkField, 1, 1);
        grid.add(new Label("Type :"), 0, 2);         grid.add(typeCombo, 1, 2);
        grid.add(new Label("Difficulty :"), 0, 3);   grid.add(diffCombo, 1, 3);
        grid.add(new Label("Duration :"), 0, 4);     grid.add(durationField, 1, 4);
        grid.add(new Label("Tags :"), 0, 5);         grid.add(tagsField, 1, 5);
        grid.add(new Label("Description :"), 0, 6);  grid.add(descField, 1, 6);
        dialog.getDialogPane().setContent(grid);

        dialog.showAndWait().ifPresent(response -> {
            if (response == saveBtn) {
                new Thread(() -> {
                    String generatedSummary = aiController.generateResourceSummary(
                        titleField.getText(), linkField.getText(), tagsField.getText(), descField.getText());
                    boolean success = courseService.addDetailedResource(
                        currentSelectedTopicId, titleField.getText().trim(), linkField.getText().trim(),
                        typeCombo.getValue(), descField.getText().trim(), tagsField.getText().trim(),
                        diffCombo.getValue(), durationField.getText().trim(), true,
                        generatedSummary, AuthService.CURRENT_CHANNEL_ID);
                    Platform.runLater(() -> {
                        if (success) { loadResourcesForTopic(currentSelectedTopicId); showSuccess("Resource Uploaded with AI Summary! ✨"); }
                    });
                }).start();
            }
        });
    }

    // ==========================================================
    // 💬 DISCUSSION & Q&A PANEL
    // ==========================================================
    private void showDiscussionPanel(CourseService.Resource res) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("💬 Q&A: " + res.title());
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        VBox layout = new VBox(15);
        layout.setPadding(new Insets(15)); layout.setPrefSize(550, 600);
        layout.setStyle("-fx-background-color: #f4f6f7;");

        ToggleButton myQuestionsToggle = new ToggleButton("🔍 Show My Questions Only");
        myQuestionsToggle.setStyle("-fx-background-color: #ecf0f1; -fx-text-fill: #2c3e50; "
            + "-fx-font-weight: bold; -fx-cursor: hand; -fx-background-radius: 20; -fx-padding: 5 15;");

        VBox qnaContainer = new VBox(15);
        ScrollPane scroll = new ScrollPane(qnaContainer);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background: #f4f6f7; -fx-border-color: transparent;");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        TextField input = new TextField();
        input.setPromptText("Ask a question or share a doubt...");
        input.setStyle("-fx-background-radius: 20; -fx-padding: 10;");
        HBox.setHgrow(input, Priority.ALWAYS);
        Button sendBtn = new Button("Ask 🚀");
        sendBtn.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-background-radius: 20; "
            + "-fx-font-weight: bold; -fx-cursor: hand;");
        sendBtn.setOnAction(e -> {
            if (input.getText().trim().isEmpty()) return;
            new Thread(() -> {
                if (courseService.addComment(res.id(), input.getText().trim(), null)) {
                    Platform.runLater(() -> {
                        input.clear(); loadQnA(res.id(), qnaContainer, myQuestionsToggle.isSelected());
                    });
                }
            }).start();
        });

        myQuestionsToggle.setOnAction(e -> {
            if (myQuestionsToggle.isSelected())
                myQuestionsToggle.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; "
                    + "-fx-font-weight: bold; -fx-cursor: hand; -fx-background-radius: 20; -fx-padding: 5 15;");
            else
                myQuestionsToggle.setStyle("-fx-background-color: #ecf0f1; -fx-text-fill: #2c3e50; "
                    + "-fx-font-weight: bold; -fx-cursor: hand; -fx-background-radius: 20; -fx-padding: 5 15;");
            loadQnA(res.id(), qnaContainer, myQuestionsToggle.isSelected());
        });

        HBox topBar  = new HBox(myQuestionsToggle); topBar.setAlignment(Pos.CENTER_RIGHT);
        HBox inputBox = new HBox(10, input, sendBtn); inputBox.setAlignment(Pos.CENTER);
        layout.getChildren().addAll(topBar, scroll, inputBox);
        dialog.getDialogPane().setContent(layout);
        loadQnA(res.id(), qnaContainer, false);
        dialog.showAndWait();
    }

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
                    container.getChildren().add(new Label("No questions yet. Be the first to ask! 💡")); return;
                }
                boolean foundAny = false;
                for (var q : questions) {
                    String currentUserIdStr = String.valueOf(AuthService.CURRENT_USER_ID);
                    if (showOnlyMine && q.userId() != null
                        && !String.valueOf(q.userId()).equals(currentUserIdStr)) continue;
                    foundAny = true;

                    VBox qCard = new VBox(8);
                    qCard.setStyle("-fx-background-color: white; -fx-padding: 15; "
                        + "-fx-background-radius: 10; -fx-border-color: #3498db; "
                        + "-fx-border-width: 1 1 1 5; "
                        + "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.05), 5, 0, 0, 2);");

                    Label qUser = new Label("❓ " + q.userName() + " asked:");
                    qUser.setStyle("-fx-font-weight: bold; -fx-text-fill: #2c3e50;");
                    Label qContent = new Label(q.content());
                    qContent.setWrapText(true); qContent.setStyle("-fx-font-size: 14px; -fx-text-fill: #34495e;");

                    HBox replyInputBox = new HBox(10);
                    replyInputBox.setVisible(false); replyInputBox.setManaged(false);
                    TextField replyInput = new TextField(); replyInput.setPromptText("Write an answer...");
                    HBox.setHgrow(replyInput, Priority.ALWAYS);
                    Button submitReplyBtn = new Button("Reply");
                    submitReplyBtn.setStyle("-fx-background-color: #2ecc71; -fx-text-fill: white; -fx-cursor: hand;");
                    replyInputBox.getChildren().addAll(replyInput, submitReplyBtn);

                    Button showReplyBtn = new Button("↳ Answer this");
                    showReplyBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #3498db; -fx-cursor: hand;");
                    showReplyBtn.setOnAction(e -> {
                        replyInputBox.setVisible(!replyInputBox.isVisible());
                        replyInputBox.setManaged(!replyInputBox.isManaged());
                    });
                    submitReplyBtn.setOnAction(e -> {
                        if (replyInput.getText().trim().isEmpty()) return;
                        new Thread(() -> {
                            if (courseService.addComment(resId, replyInput.getText().trim(), q.id()))
                                Platform.runLater(() -> loadQnA(resId, container, showOnlyMine));
                        }).start();
                    });

                    qCard.getChildren().addAll(qUser, qContent, showReplyBtn, replyInputBox);

                    VBox answersBox = new VBox(8);
                    answersBox.setPadding(new Insets(10, 0, 0, 30));
                    for (var a : answers) {
                        if (a.parentId() == q.id()) {
                            VBox aCard = new VBox(5);
                            aCard.setStyle("-fx-background-color: #ecf0f1; -fx-padding: 10; -fx-background-radius: 8;");
                            Label aUser = new Label("💡 " + a.userName() + " replied:");
                            aUser.setStyle("-fx-font-weight: bold; -fx-text-fill: #27ae60; -fx-font-size: 11px;");
                            Label aContent = new Label(a.content()); aContent.setWrapText(true);
                            aCard.getChildren().addAll(aUser, aContent);
                            answersBox.getChildren().add(aCard);
                        }
                    }
                    container.getChildren().addAll(qCard, answersBox);
                }
                if (showOnlyMine && !foundAny)
                    container.getChildren().add(new Label("You haven't asked any questions yet! 🔍"));
            });
        }).start();
    }

    // ==========================================================
    // 📊 STATISTICS POPUP (Quora Style)
    // ==========================================================
    private void showStatisticsDialog(CourseService.Resource res) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("📊 Community Statistics");
        dialog.setHeaderText("Insights for: " + res.title());
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        VBox layout = new VBox(15); layout.setPadding(new Insets(20));
        layout.setPrefSize(450, 500); layout.setStyle("-fx-background-color: #f4f6f7;");
        layout.getChildren().add(new Label("Fetching community data..."));
        dialog.getDialogPane().setContent(layout);

        new Thread(() -> {
            CourseService.ResourceStats stats = courseService.getResourceStatistics(res.id());
            Platform.runLater(() -> {
                layout.getChildren().clear();

                HBox votesBox = new HBox(30);
                Label upLbl   = new Label("👍 Upvotes: " + stats.totalUpvotes());
                upLbl.setStyle("-fx-font-weight: bold; -fx-text-fill: #27ae60; -fx-font-size: 16px;");
                Label downLbl = new Label("👎 Downvotes: " + stats.totalDownvotes());
                downLbl.setStyle("-fx-font-weight: bold; -fx-text-fill: #c0392b; -fx-font-size: 16px;");
                votesBox.getChildren().addAll(upLbl, downLbl);

                HBox diffBox = new HBox(15);
                diffBox.setStyle("-fx-background-color: white; -fx-padding: 15; "
                    + "-fx-background-radius: 8; -fx-border-color: #bdc3c7;");
                diffBox.getChildren().addAll(
                    new Label("🟢 Easy: " + stats.easyCount()),
                    new Label("🟡 Medium: " + stats.mediumCount()),
                    new Label("🔴 Hard: " + stats.hardCount()));

                Label reviewTitle = new Label("Student Notes & Experiences:");
                reviewTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 15px;");
                VBox notesContainer = new VBox(12);

                if (stats.userLogs().isEmpty()) {
                    notesContainer.getChildren().add(new Label("No notes yet. Be the first! 🚀"));
                } else {
                    for (CourseService.CompletionLog log : stats.userLogs()) {
                        VBox card = new VBox(8);
                        card.setStyle("-fx-background-color: white; -fx-padding: 15; "
                            + "-fx-background-radius: 10; -fx-border-color: #ecf0f1;");
                        HBox header = new HBox(15);
                        Label nameLbl = new Label("👤 " + log.username());
                        nameLbl.setStyle("-fx-font-weight: bold; -fx-text-fill: #2980b9;");
                        Label dateLbl = new Label(log.date());
                        dateLbl.setStyle("-fx-text-fill: #95a5a6; -fx-font-size: 11px;");
                        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
                        header.getChildren().addAll(nameLbl,
                            new Label("Rated: " + log.difficulty()),
                            new Label("⏱️ " + log.timeMins() + " mins"), spacer, dateLbl);
                        Label noteLbl = new Label(log.note() == null || log.note().isEmpty()
                            ? "No additional notes." : log.note());
                        noteLbl.setWrapText(true);
                        noteLbl.setStyle("-fx-text-fill: #34495e; -fx-font-size: 13px;");
                        card.getChildren().addAll(header, new Separator(), noteLbl);
                        notesContainer.getChildren().add(card);
                    }
                }

                ScrollPane scroll = new ScrollPane(notesContainer);
                scroll.setFitToWidth(true);
                scroll.setStyle("-fx-background-color: transparent; -fx-border-color: transparent;");
                VBox.setVgrow(scroll, Priority.ALWAYS);
                layout.getChildren().addAll(votesBox, diffBox, reviewTitle, scroll);
            });
        }).start();
        dialog.showAndWait();
    }

    // ==========================================================
    // 📝 MARK AS DONE / EDIT NOTE
    // ==========================================================
    private void showResourceCompletionDialog(ResourceRow row) {
        new Thread(() -> {
            CourseService.UserProgress existingProgress = courseService.getUserProgress(row.getRawResource().id());
            Platform.runLater(() -> {
                Dialog<ButtonType> dialog = new Dialog<>();
                dialog.setTitle(existingProgress.isCompleted() ? "✏️ Edit Your Note" : "🎉 Resource Completed!");
                dialog.setHeaderText("Log your experience for: " + row.getName());
                ButtonType saveBtn = new ButtonType(
                    existingProgress.isCompleted() ? "Update Details" : "Save Experience",
                    ButtonBar.ButtonData.OK_DONE);
                dialog.getDialogPane().getButtonTypes().addAll(saveBtn, ButtonType.CANCEL);

                GridPane grid = new GridPane(); grid.setHgap(10); grid.setVgap(15); grid.setPadding(new Insets(20));
                TextField durationField = new TextField(
                    existingProgress.timeMins() > 0 ? String.valueOf(existingProgress.timeMins()) : "");
                durationField.setPromptText("Time in minutes (e.g. 45)");
                ComboBox<String> diffCombo = new ComboBox<>();
                diffCombo.getItems().addAll("Easy", "Medium", "Hard");
                diffCombo.setValue(existingProgress.difficulty() != null ? existingProgress.difficulty() : "Medium");
                TextArea noteArea = new TextArea(existingProgress.userNote() != null ? existingProgress.userNote() : "");
                noteArea.setPromptText("Write your notes, hints, or review for future students...");
                noteArea.setPrefRowCount(3); noteArea.setWrapText(true);

                grid.add(new Label("Time Taken (mins):"), 0, 0); grid.add(durationField, 1, 0);
                grid.add(new Label("Rate Difficulty:"), 0, 1);   grid.add(diffCombo, 1, 1);
                grid.add(new Label("Your Notes:"), 0, 2);        grid.add(noteArea, 1, 2);
                dialog.getDialogPane().setContent(grid);

                dialog.showAndWait().ifPresent(response -> {
                    if (response == saveBtn) {
                        int mins = durationField.getText().matches("\\d+") ? Integer.parseInt(durationField.getText()) : 0;
                        new Thread(() -> {
                            if (courseService.markResourceDone(row.getRawResource().id(),
                                    diffCombo.getValue(), mins, noteArea.getText().trim())) {
                                Platform.runLater(() -> {
                                    new Alert(Alert.AlertType.INFORMATION, "Your notes and progress updated! ✅").show();
                                    if (currentSelectedTopicId != null)
                                        loadResourcesForTopic(currentSelectedTopicId);
                                });
                            }
                        }).start();
                    }
                });
            });
        }).start();
    }

    // ==========================================================
    // 🌍 GOOGLE DRIVE PREVIEW & DOWNLOAD
    // ==========================================================
    private String extractDriveId(String url) {
        if (url == null || !url.contains("drive.google.com")) return null;
        Matcher m = Pattern.compile("/d/([a-zA-Z0-9-_]+)").matcher(url);
        if (m.find()) return m.group(1);
        m = Pattern.compile("id=([a-zA-Z0-9-_]+)").matcher(url);
        return m.find() ? m.group(1) : null;
    }

    private void showInAppPreview(String url, String title) {
        try {
            Stage previewStage = new Stage();
            previewStage.setTitle("👁️ Preview: " + title);
            WebView webView = new WebView(); webView.getEngine().load(url);
            previewStage.setScene(new Scene(webView, 900, 650));
            previewStage.show();
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Preview Error", "Could not load preview.");
        }
    }

    private void directDownloadFile(String url, String title) {
        if (url == null || url.isEmpty()) return;
        if (url.contains("drive.google.com")) {
            String fileId = extractDriveId(url);
            if (fileId != null)
                startFileDownloadProcess("https://drive.google.com/uc?export=download&id=" + fileId, title, ".pdf");
        } else if (url.contains("youtube.com") || url.contains("youtu.be")
            || url.contains("t.me") || url.contains("telegram.me")) {
            openExternalDownloader(url);
        } else {
            showAlert(Alert.AlertType.WARNING, "Unsupported Link", "Direct download supports Google Drive, YouTube, and Telegram links.");
        }
    }

    private void openExternalDownloader(String mediaUrl) {
        try {
            javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
            javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
            content.putString(mediaUrl); clipboard.setContent(content);
            new Alert(Alert.AlertType.INFORMATION,
                "Link copied! Opening secure downloader...").showAndWait();
            if (java.awt.Desktop.isDesktopSupported())
                java.awt.Desktop.getDesktop().browse(new URI("https://cobalt.tools"));
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "System Error", "Could not open downloader: " + e.getMessage());
        }
    }

    private void startFileDownloadProcess(String directDownloadUrl, String title, String extension) {
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

    // ==========================================================
    // 🤖 AI TUTOR FLOATING PANEL
    // ==========================================================
    private void showAITutorPanel() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("🤖 ScholarGrid AI Tutor");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        VBox layout = new VBox(15); layout.setPadding(new Insets(15));
        layout.setPrefSize(650, 550); layout.setStyle("-fx-background-color: #f4f6f7;");

        WebView webView = new WebView(); VBox.setVgrow(webView, Priority.ALWAYS);
        webView.getEngine().loadContent(
            "<html><body style='font-family: Arial; text-align: center; margin-top: 15%; color: #2c3e50;'>"
            + "<h2 style='color: #8e44ad;'>👋 Hello! I am your ScholarGrid AI.</h2>"
            + "<p>Ask me what to study!</p></body></html>");

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
                showAlert(Alert.AlertType.WARNING, "Missing Info", "Please enter both Topic and Question!"); return;
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
                    askBtn.setDisable(false); questionInput.clear();
                });
            }).start();
        });

        // Open links in default browser
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

    // ==========================================================
    // 🤝 5. COLLABORATION & TEAMS
    // ==========================================================
    @FXML public void onCreateChannel() { showSuccess("Feature coming soon! Default channels are available."); }

    private void loadChannels() {
        if (channelList == null) return;
        channelList.getChildren().clear();
        List<CollaborationService.Channel> channels = collaborationService.getAllChannels();
        for (var c : channels) {
            Button btn = new Button("# " + c.title());
            btn.setMaxWidth(Double.MAX_VALUE); btn.setAlignment(Pos.CENTER_LEFT);
            btn.setStyle("-fx-background-color: transparent; -fx-text-fill: white; -fx-cursor: hand; -fx-padding: 8 15;");
            btn.setOnMouseEntered(e -> btn.setStyle("-fx-background-color: #334155; -fx-text-fill: white; -fx-cursor: hand; -fx-padding: 8 15;"));
            btn.setOnMouseExited(e -> btn.setStyle("-fx-background-color: transparent; -fx-text-fill: white; -fx-cursor: hand; -fx-padding: 8 15;"));
            btn.setOnAction(e -> loadTeamsForChannel(c.id(), c.title()));
            channelList.getChildren().add(btn);
        }
    }

    private void loadTeamsForChannel(int channelId, String channelName) {
        if (teamList == null) return;
        teamList.getChildren().clear();
        List<CollaborationService.Post> posts = collaborationService.getPostsForChannel(channelId);
        if (posts.isEmpty()) {
            Label noTeamLabel = new Label("No teams yet in #" + channelName);
            noTeamLabel.setStyle("-fx-text-fill: #94a3b8; -fx-padding: 10;");
            teamList.getChildren().add(noTeamLabel);
        }
        for (CollaborationService.Post post : posts) {
            VBox card = new VBox(8);
            card.setStyle("-fx-background-color: white; -fx-padding: 15; -fx-background-radius: 10; "
                + "-fx-border-color: #e2e8f0; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.05), 5, 0, 0, 2);");
            Label title   = new Label("🚀 " + post.title());
            title.setStyle("-fx-font-weight: bold; -fx-font-size: 15px; -fx-text-fill: #1e293b;");
            Label desc    = new Label(post.desc() == null || post.desc().isEmpty() ? "No description." : post.desc());
            desc.setWrapText(true); desc.setStyle("-fx-text-fill: #64748b;");
            Label members = new Label("👥 Max Members: " + post.maxMembers());
            members.setStyle("-fx-font-size: 12px; -fx-text-fill: #8b5cf6; -fx-font-weight: bold;");
            Button viewBtn = new Button("View Workspace");
            viewBtn.setMaxWidth(Double.MAX_VALUE);
            viewBtn.setStyle("-fx-background-color: #8b5cf6; -fx-text-fill: white; -fx-cursor: hand; -fx-font-weight: bold;");
            viewBtn.setOnAction(e -> loadRoomView(post));
            card.getChildren().addAll(title, desc, members, viewBtn);
            teamList.getChildren().add(card);
        }
    }

    @FXML
    public void onAddTeamResourceClick() {
        if (currentActivePost == null) { showError("Please select a team workspace first."); return; }
        onAddResourceClick(currentActivePost.id(), teamResourceList);
    }

    private void loadRoomView(CollaborationService.Post post) {
        this.currentActivePost = post;
        roomContainer.getChildren().clear();
        if (chatTimeline != null) chatTimeline.stop();
        String myStatus = collaborationService.getMyStatus(post.id());
        switch (myStatus) {
            case "OWNER"    -> showOwnerDashboard(post);
            case "APPROVED" -> showChatRoom(post);
            case "PENDING"  -> {
                VBox pendingBox = new VBox(15); pendingBox.setAlignment(Pos.CENTER);
                Label status = new Label("⏳ Application Sent");
                status.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #e67e22;");
                pendingBox.getChildren().addAll(status, new Label("Waiting for owner approval."));
                roomContainer.getChildren().add(pendingBox);
            }
            default -> showRequirementZone(post);
        }
    }

    private void showRequirementZone(CollaborationService.Post post) {
        VBox reqBox = new VBox(15);
        reqBox.setStyle("-fx-padding: 30; -fx-background-color: white; -fx-background-radius: 10; -fx-border-color: #e2e8f0;");
        reqBox.getChildren().addAll(new Label("📝 Join Team: " + post.title()),
            new Label("Please answer the following questions:"));
        List<CollaborationService.Requirement> questions = collaborationService.getRequirements(post.id());
        List<TextField> answers = new ArrayList<>();
        for (var q : questions) {
            Label qLbl = new Label("• " + q.question()); qLbl.setStyle("-fx-font-weight: bold;");
            TextField ansField = new TextField(); ansField.setPromptText("Your answer...");
            answers.add(ansField); reqBox.getChildren().addAll(qLbl, ansField);
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
                showSuccess("Application Sent!"); loadRoomView(post);
            } else showError("Failed to apply.");
        });
        reqBox.getChildren().add(applyBtn);
        roomContainer.getChildren().add(reqBox);
    }

    private void showOwnerDashboard(CollaborationService.Post post) {
        roomContainer.getChildren().clear();
        VBox mainBox = new VBox(20); mainBox.setPadding(new Insets(20));
        HBox header = new HBox(15); header.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("👑 Management Dashboard: " + post.title());
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 20px; -fx-text-fill: #1e293b;");
        Button gotoChatBtn = new Button("Go to Team Workspace 💬");
        gotoChatBtn.setStyle("-fx-background-color: #3b82f6; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand;");
        gotoChatBtn.setOnAction(e -> showChatRoom(post));
        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        header.getChildren().addAll(title, spacer, gotoChatBtn);

        VBox appsBox = new VBox(10); ScrollPane scroll = new ScrollPane(appsBox);
        scroll.setFitToWidth(true); VBox.setVgrow(scroll, Priority.ALWAYS);

        List<CollaborationService.Application> apps = collaborationService.getApplicationsForPost(post.id());
        if (apps.isEmpty()) appsBox.getChildren().add(new Label("No pending applications."));
        for (var app : apps) {
            VBox appCard = new VBox(10);
            appCard.setStyle("-fx-border-color: #e2e8f0; -fx-padding: 15; -fx-background-color: #f8fafc;");
            Label applicantName = new Label("👤 Applicant: " + app.username());
            applicantName.setStyle("-fx-font-weight: bold; -fx-text-fill: #0f172a;");
            VBox qaBox = new VBox(5);
            for (String ans : app.answers()) { Label a = new Label("• " + ans); a.setWrapText(true); qaBox.getChildren().add(a); }
            HBox actions = new HBox(10);
            if (app.status().equals("PENDING")) {
                Button approveBtn = new Button("Approve ✅");
                approveBtn.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-cursor: hand;");
                approveBtn.setOnAction(e -> {
                    if (collaborationService.approveMember(post.id(), app.userId())) {
                        showSuccess(app.username() + " approved!"); showOwnerDashboard(post);
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

    // ==========================================================
    // 💬 CHAT ROOM
    // ==========================================================
    private void showChatRoom(CollaborationService.Post post) {
        roomContainer.getChildren().clear();
        SplitPane splitPane = new SplitPane(); splitPane.setDividerPositions(0.7);

        VBox chatSide = new VBox(10); chatSide.setPadding(new Insets(10));
        VBox chatBox = new VBox(10); chatBox.setPadding(new Insets(15));
        ScrollPane chatScroll = new ScrollPane(chatBox); chatScroll.setFitToWidth(true);
        VBox.setVgrow(chatScroll, Priority.ALWAYS);
        TextField msgInput = new TextField(); Button sendBtn = new Button("Send");
        HBox inputBox = new HBox(10, msgInput, sendBtn); inputBox.setAlignment(Pos.CENTER);
        HBox.setHgrow(msgInput, Priority.ALWAYS);
        sendBtn.setOnAction(e -> {
            if (!msgInput.getText().trim().isEmpty()) {
                collaborationService.sendMessage(post.id(), msgInput.getText());
                msgInput.clear(); refreshChat(post.id(), chatBox, chatScroll);
            }
        });
        chatSide.getChildren().addAll(new Label("💬 " + post.title()), chatScroll, inputBox);

        VBox resSide = new VBox(10); resSide.setPadding(new Insets(10));
        ListView<String> dynamicResList = new ListView<>(); VBox.setVgrow(dynamicResList, Priority.ALWAYS);
        Button addResBtn = new Button("➕ Add Resource"); addResBtn.setMaxWidth(Double.MAX_VALUE);
        addResBtn.setOnAction(e -> onAddResourceClick(post.id(), dynamicResList));
        dynamicResList.setOnMouseClicked(e -> handleTeamResourceClick(e, dynamicResList));
        resSide.getChildren().addAll(new Label("📂 Files"), dynamicResList, addResBtn);

        splitPane.getItems().addAll(chatSide, resSide);
        roomContainer.getChildren().add(splitPane);
        refreshChat(post.id(), chatBox, chatScroll);
        loadTeamResources(post.id(), dynamicResList);
        startChatAutoRefresh(post.id(), chatBox, chatScroll);
    }

    private void handleTeamResourceClick(MouseEvent event, ListView<String> listView) {
        if (event.getClickCount() == 2) {
            int index = listView.getSelectionModel().getSelectedIndex();
            if (index >= 0 && index < currentResourceData.size()) {
                String url = currentResourceData.get(index).url();
                try { java.awt.Desktop.getDesktop().browse(new java.net.URI(url)); }
                catch (Exception e) { showError("Could not open link."); }
            }
        }
    }

    private void refreshChat(int postId, VBox chatBox, ScrollPane scrollPane) {
        new Thread(() -> {
            List<CollaborationService.Message> msgs = collaborationService.getMessages(postId);
            Platform.runLater(() -> {
                chatBox.getChildren().clear();
                for (var msg : msgs) {
                    Label label = new Label(msg.sender() + ": " + msg.content());
                    label.setWrapText(true); label.setMaxWidth(300);
                    HBox bubble = new HBox(label);
                    if (msg.sender().equals(AuthService.CURRENT_USER_NAME)) {
                        bubble.setAlignment(Pos.CENTER_RIGHT);
                        label.setStyle("-fx-background-color: #dbeafe; -fx-padding: 8; -fx-background-radius: 5;");
                    } else {
                        bubble.setAlignment(Pos.CENTER_LEFT);
                        label.setStyle("-fx-background-color: #e2e8f0; -fx-padding: 8; -fx-background-radius: 5;");
                    }
                    chatBox.getChildren().add(bubble);
                }
                chatBox.heightProperty().addListener(o -> scrollPane.setVvalue(1.0));
            });
        }).start();
    }

    private void startChatAutoRefresh(int postId, VBox chatBox, ScrollPane scrollPane) {
        if (chatTimeline != null) chatTimeline.stop();
        chatTimeline = new Timeline(new KeyFrame(Duration.seconds(3),
            e -> refreshChat(postId, chatBox, scrollPane)));
        chatTimeline.setCycleCount(Timeline.INDEFINITE); chatTimeline.play();
        roomContainer.sceneProperty().addListener((obs, old, nev) -> {
            if (nev == null && chatTimeline != null) chatTimeline.stop();
        });
    }

    private void loadTeamResources(int postId, ListView<String> listView) {
        listView.getItems().clear();
        currentResourceData = collaborationService.getTeamResources(postId);
        if (currentResourceData.isEmpty()) listView.getItems().add("No shared files.");
        else for (var res : currentResourceData) {
            String icon = "FILE".equalsIgnoreCase(res.type()) ? "📄" : "🔗";
            listView.getItems().add(icon + " " + res.title() + " (" + res.addedBy() + ")");
        }
    }

    private void onAddResourceClick(int postId, ListView<String> listViewToRefresh) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("📂 Add Resource");
        dialog.setHeaderText("Share Link or File");
        VBox content = new VBox(10);
        TextField titleField = new TextField(); titleField.setPromptText("Title");
        TextArea descField = new TextArea(); descField.setPromptText("Description"); descField.setPrefRowCount(2);
        ComboBox<String> typeBox = new ComboBox<>();
        typeBox.getItems().addAll("LINK", "FILE"); typeBox.setValue("LINK");
        TextField linkField = new TextField(); linkField.setPromptText("Paste URL...");
        HBox fileBox = new HBox(10);
        Button uploadBtn = new Button("Select File 📁"); Label fileLabel = new Label("No file");
        fileBox.getChildren().addAll(uploadBtn, fileLabel); fileBox.setVisible(false);
        final File[] selectedFile = {null};
        uploadBtn.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            File f = fc.showOpenDialog(null);
            if (f != null) { selectedFile[0] = f; fileLabel.setText(f.getName()); }
        });
        typeBox.setOnAction(e -> {
            boolean isFile = typeBox.getValue().equals("FILE");
            linkField.setVisible(!isFile); fileBox.setVisible(isFile);
        });
        content.getChildren().addAll(new Label("Type:"), typeBox, new Label("Title:"),
            titleField, descField, linkField, fileBox);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.showAndWait().ifPresent(type -> {
            if (type == ButtonType.OK) {
                if (typeBox.getValue().equals("LINK")) {
                    collaborationService.addTeamResource(postId, titleField.getText(), linkField.getText(),
                        "LINK", descField.getText(), null);
                    loadTeamResources(postId, listViewToRefresh);
                    showSuccess("Link Added!");
                } else if (selectedFile[0] != null) {
                    showSuccess("Uploading...");
                    new Thread(() -> {
                        String fileId = telegramService.uploadToCloud(selectedFile[0]);
                        if (fileId != null) {
                            String url = telegramService.getFileDownloadUrl(fileId);
                            Platform.runLater(() -> {
                                collaborationService.addTeamResource(postId, titleField.getText(),
                                    url, "FILE", descField.getText(), fileId);
                                loadTeamResources(postId, listViewToRefresh);
                                showSuccess("File Uploaded!");
                            });
                        } else Platform.runLater(() -> showError("Upload Failed"));
                    }).start();
                }
            }
        });
    }

    @FXML
    public void onCreatePost() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("🚀 Create Team");
        dialog.setHeaderText("Create a new collaboration team");
        VBox content = new VBox(10);
        TextField titleField = new TextField(); titleField.setPromptText("Team Name");
        TextArea descField = new TextArea(); descField.setPromptText("Description"); descField.setPrefRowCount(3);
        Spinner<Integer> spinner = new Spinner<>(2, 10, 4);
        VBox qBox = new VBox(5); List<TextField> qFields = new ArrayList<>();
        Button addQ = new Button("+ Add Question");
        addQ.setOnAction(e -> {
            TextField tf = new TextField(); tf.setPromptText("Question..."); qFields.add(tf); qBox.getChildren().add(tf);
        });
        content.getChildren().addAll(new Label("Name:"), titleField, new Label("Desc:"), descField,
            new Label("Members:"), spinner, new Label("Entry Questions:"), qBox, addQ);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.showAndWait().ifPresent(type -> {
            if (type == ButtonType.OK && !titleField.getText().isEmpty() && !descField.getText().isEmpty()) {
                List<String> qs = qFields.stream().map(TextField::getText).filter(s -> !s.isEmpty()).toList();
                if (collaborationService.createPostWithRequirements(1, titleField.getText(),
                        descField.getText(), spinner.getValue(), qs)) {
                    showSuccess("Team Created!");
                    loadTeamsForChannel(1, "Hackathons");
                }
            }
        });
    }

    // ==========================================================
    // 🏆 6. ECA TRACKER
    // ==========================================================
    @FXML public void handleECATrackerSelect(Event event) {
        Tab tab = (Tab) event.getSource();
        if (tab.isSelected() && ecaContainer != null) loadECATracker(ecaContainer);
    }

    @FXML public void forceRefreshECA() { loadECATracker(ecaContainer); }

    private void loadECATracker(VBox container) {
        container.getChildren().clear(); container.getChildren().add(new Label("Syncing Profiles..."));
        new Thread(() -> {
            String[] accounts = ecaService.getLinkedAccounts();
            Platform.runLater(() -> {
                container.getChildren().clear();
                if (accounts == null || accounts[0] == null) showECASetupForm(container);
                else showECADashboard(container, accounts[0], accounts[1]);
            });
        }).start();
    }

    private void showECASetupForm(VBox container) {
        VBox box = new VBox(15);
        TextField lcField = new TextField(); lcField.setPromptText("LeetCode Username");
        TextField dpField = new TextField(); dpField.setPromptText("Devpost Username");
        Button saveBtn = new Button("Save & Sync");
        saveBtn.setOnAction(e -> new Thread(() -> {
            if (ecaService.linkAccounts(lcField.getText(), dpField.getText()))
                Platform.runLater(() -> loadECATracker(container));
        }).start());
        box.getChildren().addAll(lcField, dpField, saveBtn);
        container.getChildren().add(box);
    }

    private void showECADashboard(VBox container, String lcUser, String dpUser) {
        HBox cards = new HBox(20);
        VBox lcCard = new VBox(10); Label lcStat = new Label("Loading...");
        lcCard.getChildren().addAll(new Label("LeetCode"), lcStat);
        VBox dpCard = new VBox(10); Label dpStat = new Label("Loading...");
        dpCard.getChildren().addAll(new Label("Devpost"), dpStat);
        PieChart topicChart = new PieChart(); topicChart.setPrefSize(300, 200);
        cards.getChildren().addAll(lcCard, dpCard, topicChart);
        container.getChildren().addAll(new Label("Activity Dashboard"), cards);

        new Thread(() -> {
            int[] lcStats = ecaService.fetchLeetCodeStats(lcUser);
            int dpProjects = ecaService.fetchDevpostProjectCount(dpUser);
            var topicStats = ecaService.fetchTopicStats(lcUser);
            Platform.runLater(() -> {
                if (lcStats != null) lcStat.setText("Solved: " + lcStats[0]);
                if (dpProjects != -1) dpStat.setText("Projects: " + dpProjects);
                if (topicStats != null) {
                    topicChart.getData().clear();
                    topicStats.forEach((tag, count) -> {
                        if (count > 0) topicChart.getData().add(new PieChart.Data(tag, count));
                    });
                }
            });
        }).start();
    }

    // ==========================================================
    // ⚙️ 7. PROFILE & GLOBAL ACTIONS
    // ==========================================================
    @FXML public void loadProfileSettings() {
        if (settingsName == null) return;
        new Thread(() -> {
            var profile = courseService.getMyProfile();
            Platform.runLater(() -> {
                settingsName.setText(profile.fullName());
                settingsUsername.setText(profile.username());
                settingsEmail.setText(profile.email());
            });
        }).start();
    }

    @FXML public void onSaveProfile() {
        new Thread(() -> {
            if (courseService.updateProfile(settingsName.getText(), settingsUsername.getText()))
                Platform.runLater(() -> showSuccess("Profile Updated!"));
        }).start();
    }

    
   @FXML
    private void handleLogoutTab(Event event) {
        Tab tab = (Tab) event.getSource();
        if (tab.isSelected()) {
            if (new Alert(Alert.AlertType.CONFIRMATION, "Logout?").showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
                AuthService.logout();
                try {
                    Stage stage = (Stage) tab.getTabPane().getScene().getWindow();
                    
                    // 🌟 ফিক্স: Spring Boot-কে দায়িত্ব দেওয়া হলো LoginController তৈরি করার
                    FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/scholar/view/login.fxml"));
                    loader.setControllerFactory(springContext::getBean); // 👈 এই লাইনটিই আসল ম্যাজিক!
                   Parent root = (Parent) loader.load();
                    
                    stage.setScene(new Scene(root, 400, 600));
                    stage.centerOnScreen();
                } catch (IOException e) { e.printStackTrace(); }
            } else tab.getTabPane().getSelectionModel().select(0);
        }
    }
    // ==========================================================
    // HELPER METHODS
    // ==========================================================
    private void showSuccess(String msg) {
        Platform.runLater(() -> { Alert a = new Alert(Alert.AlertType.INFORMATION); a.setContentText(msg); a.show(); });
    }
    private void showError(String msg) {
        Platform.runLater(() -> { Alert a = new Alert(Alert.AlertType.ERROR); a.setContentText(msg); a.showAndWait(); });
    }
    private void showAlert(Alert.AlertType type, String title, String content) {
        Platform.runLater(() -> {
            Alert alert = new Alert(type);
            alert.setTitle(title); alert.setHeaderText(null); alert.setContentText(content);
            alert.showAndWait();
        });
    }

    // ==========================================================
    // 🗃️ TABLE MODEL (ResourceRow)
    // ==========================================================
    public static class ResourceRow {
        private final String name, type, diff, uploader, tags, votes;
        private final CourseService.Resource rawResource;

        public ResourceRow(String name, String type, String diff, String uploader,
                           String tags, String votes, CourseService.Resource rawResource) {
            this.name = name; this.type = type; this.diff = diff;
            this.uploader = uploader; this.tags = tags; this.votes = votes;
            this.rawResource = rawResource;
        }

        public String getName()     { return name; }
        public String getType()     { return type; }
        public String getDiff()     { return diff; }
        public String getUploader() { return uploader; }
        public String getTags()     { return tags; }
        public String getVotes()    { return votes; }
        public CourseService.Resource getRawResource() { return rawResource; }
    }
}