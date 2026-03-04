package com.scholar.controller;

import com.scholar.model.StudyHistory;
import com.scholar.model.StudyRoom;
import com.scholar.model.StudySession;
import com.scholar.service.AuthService;
import com.scholar.service.StudyRoomService;
import com.scholar.util.PopupHelper;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.util.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * StudyRoomController — v3
 *
 * LOGIC RULES (per user spec):
 * ─────────────────────────────
 * 1. History table: stores ONLY COMPLETED sessions (not abandoned/left).
 * 2. Analytics (today/week mins): counts ALL studied minutes, including
 *    partial/abandoned sessions (so "if user leaves, count off" = we save
 *    elapsed minutes to analytics via saveAnalyticsOnly, NOT to history).
 * 3. If user leaves/abandons → NO history entry, but elapsed minutes still
 *    flow into analytics via a separate lightweight call.
 * 4. Study board: shows only COMPLETED tasks of current session users
 *    (i.e. tasks where status=COMPLETED). In-progress users show as "Studying".
 * 5. Timer paused → board status shows "⏸ Break" for that user (tracked via DB).
 * 6. After a user completes 25 min → table shows "✅ Completed (25 min)".
 *    They can then start a NEW task in the same room (re-join triggers new row).
 * 7. Leaderboard names visible, dark bluish cards with glow shadows.
 * 8. All backgrounds: #080a14 base, cards #111325, glow shadows via effect.
 *
 * Path: src/main/java/com/scholar/controller/StudyRoomController.java
 */
@Controller
public class StudyRoomController {

    @Autowired private StudyRoomService roomService;

    // ── FXML fields ──
    @FXML private VBox   publicRoomList;
    @FXML private VBox   roomContentPane;
    @FXML private Label  streakLabel;
    @FXML private Label  xpLabel;
    @FXML private VBox   leaderboardContainer;
    @FXML private VBox   historyContainer;
    @FXML private Label  analyticsTodayLabel;
    @FXML private Label  analyticsWeekLabel;
    @FXML private Label  analyticsStreakLabel;
    @FXML private Label  analyticsXpLabel;
    @FXML private BarChart<String, Number> weeklyChart;
    @FXML private PieChart                 topicPieChart;
    @FXML private CategoryAxis             weeklyXAxis;
    @FXML private NumberAxis               weeklyYAxis;
    @FXML private Button filterAllBtn;
    @FXML private Button filterWeekBtn;
    @FXML private Button filterTodayBtn;

    // ── Session state ──
    private StudyRoom    currentRoom;
    private String       currentParticipantId;
    private String       currentTopic = "";
    private String       currentTask  = "";
    private Timeline     pomodoroTimer;
    private Timeline     boardRefreshTimer;
    private Timeline     chatRefreshTimer;
    private final AtomicInteger secondsRemaining = new AtomicInteger(0);
    private int          totalSessionSeconds;
    private boolean      timerPaused = false;

    // ── Live UI handles ──
    private VBox                    chatBox;
    private ScrollPane              chatScroll;
    private TableView<StudySession> boardTable;
    private Label                   timerLabel;
    private Button                  modeToggleBtn;
    private VBox                    activeUsersList;
    private ProgressBar             timerProgressBar;

    // ── History cache ──
    private List<StudyHistory> allHistory = List.of();

    // ── Owner window for popups ──
    private javafx.stage.Window window() {
        return (roomContentPane != null && roomContentPane.getScene() != null)
                ? roomContentPane.getScene().getWindow() : null;
    }

    // ════════════════════════════════════════════
    // INIT
    // ════════════════════════════════════════════
    @FXML
    public void initialize() {
        loadStreakAndXP();
        loadPublicRooms();
        loadHistory();
        loadAnalytics();
        loadLeaderboard();
        // Apply dark style to TabPane header after scene is set
        Platform.runLater(this::applyTabPaneDarkStyle);
    }

    /**
     * Forces the TabPane header area dark via CSS lookup.
     * Called after layout so .tab-header-area is accessible.
     */
    private void applyTabPaneDarkStyle() {
        if (roomContentPane == null || roomContentPane.getScene() == null) return;
        roomContentPane.getScene().getRoot().lookupAll(".tab-pane").forEach(node -> {
            node.setStyle(node.getStyle() + ";-fx-background-color:#080a14;");
        });
        roomContentPane.getScene().getRoot().lookupAll(".tab-header-area").forEach(node ->
            node.setStyle("-fx-background-color:#0b0d1a;-fx-border-color:rgba(99,102,241,0.18);-fx-border-width:0 0 1 0;"));
        roomContentPane.getScene().getRoot().lookupAll(".tab-header-background").forEach(node ->
            node.setStyle("-fx-background-color:#0b0d1a;"));
        roomContentPane.getScene().getRoot().lookupAll(".headers-region").forEach(node ->
            node.setStyle("-fx-background-color:#0b0d1a;"));
    }

    // ════════════════════════════════════════════
    // STREAK & XP
    // ════════════════════════════════════════════
    private void loadStreakAndXP() {
        new Thread(() -> {
            String uid = AuthService.CURRENT_USER_ID.toString();
            int streak = roomService.getStreak(uid);
            int xp     = roomService.getTotalXP(uid);
            Platform.runLater(() -> {
                if (streakLabel != null) streakLabel.setText("🔥 " + streak + " Day Streak");
                if (xpLabel     != null) xpLabel.setText("⭐ " + xp + " XP");
            });
        }).start();
    }

    // ════════════════════════════════════════════
    // PUBLIC ROOMS SIDEBAR
    // ════════════════════════════════════════════
    public void loadPublicRooms() {
        if (publicRoomList == null) return;
        publicRoomList.getChildren().clear();

        Label loading = new Label("⏳  Loading rooms…");
        loading.setStyle("-fx-text-fill:#475569;-fx-font-size:12;-fx-padding:6 0;");
        publicRoomList.getChildren().add(loading);

        new Thread(() -> {
            List<StudyRoom> rooms = roomService.getPublicRooms();
            Platform.runLater(() -> {
                publicRoomList.getChildren().clear();
                if (rooms.isEmpty()) {
                    Label empty = new Label("No active rooms yet.\nCreate one to start! 🚀");
                    empty.setStyle("-fx-text-fill:#475569;-fx-font-size:12;-fx-text-alignment:center;");
                    empty.setWrapText(true);
                    publicRoomList.getChildren().add(empty);
                    return;
                }
                for (StudyRoom room : rooms) publicRoomList.getChildren().add(buildRoomCard(room));
            });
        }).start();
    }

    private VBox buildRoomCard(StudyRoom room) {
        VBox card = new VBox(8);
        card.setPadding(new Insets(13, 14, 13, 14));
        String base = "-fx-background-color:#111325;-fx-background-radius:12;" +
                      "-fx-border-color:rgba(99,102,241,0.2);-fx-border-radius:12;-fx-cursor:hand;" +
                      "-fx-effect:dropshadow(gaussian,rgba(99,102,241,0.12),10,0,0,2);";
        card.setStyle(base);

        // Header row
        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);
        Circle dot = new Circle(5);
        dot.setFill(Color.web(room.activeStatus() ? "#10b981" : "#475569"));
        Label nameLbl = new Label(room.roomName());
        nameLbl.setStyle("-fx-font-weight:bold;-fx-font-size:14;-fx-text-fill:#e2e8f0;");
        HBox.setHgrow(nameLbl, Priority.ALWAYS);
        Label modeLbl = new Label(room.mode().equals("SILENT") ? "🔇" : "💬");
        modeLbl.setStyle("-fx-font-size:13;");
        header.getChildren().addAll(dot, nameLbl, modeLbl);

        // Stats row
        HBox stats = new HBox(8);
        stats.setAlignment(Pos.CENTER_LEFT);
        Label typeBadge = new Label("PUBLIC");
        typeBadge.setStyle("-fx-background-color:rgba(99,102,241,0.15);-fx-text-fill:#818cf8;" +
            "-fx-background-radius:6;-fx-padding:2 8;-fx-font-size:10;-fx-font-weight:bold;");
        Label usersLbl = new Label("👥 " + room.activeUsersCount() + " studying");
        usersLbl.setStyle("-fx-text-fill:#64748b;-fx-font-size:11;");
        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
        Label creatorLbl = new Label("by " + room.creatorName());
        creatorLbl.setStyle("-fx-text-fill:#334155;-fx-font-size:10;");
        stats.getChildren().addAll(typeBadge, usersLbl, sp, creatorLbl);

        // Join button
        Button joinBtn = new Button("Join →");
        joinBtn.setMaxWidth(Double.MAX_VALUE);
        joinBtn.setStyle("-fx-background-color:rgba(99,102,241,0.18);-fx-text-fill:#818cf8;" +
            "-fx-font-weight:bold;-fx-background-radius:8;-fx-cursor:hand;-fx-padding:7 0;" +
            "-fx-font-size:12;-fx-border-color:rgba(99,102,241,0.3);-fx-border-radius:8;");
        joinBtn.setOnAction(e -> showJoinDialog(room));

        card.setOnMouseEntered(e -> card.setStyle(
            "-fx-background-color:#1a1e3a;-fx-background-radius:12;" +
            "-fx-border-color:rgba(99,102,241,0.45);-fx-border-radius:12;-fx-cursor:hand;" +
            "-fx-effect:dropshadow(gaussian,rgba(99,102,241,0.28),14,0,0,4);"));
        card.setOnMouseExited(e -> card.setStyle(base));

        card.getChildren().addAll(header, stats, joinBtn);
        return card;
    }

    // ════════════════════════════════════════════
    // PRIVATE ROOM
    // ════════════════════════════════════════════
    @FXML
    public void openMyPrivateRoom() {
        String userId = AuthService.CURRENT_USER_ID.toString();
        new Thread(() -> {
            StudyRoom myRoom = roomService.getMyPrivateRoom(userId);
            if (myRoom == null) {
                roomService.createRoom("My Focus Space", "PRIVATE", userId, "SILENT");
                myRoom = roomService.getMyPrivateRoom(userId);
            }
            StudyRoom finalRoom = myRoom;
            Platform.runLater(() -> {
                if (finalRoom != null) showJoinDialog(finalRoom);
                else PopupHelper.showError(window(), "Could not launch your private room. Please try again.");
            });
        }).start();
    }

    // ════════════════════════════════════════════
    // JOIN DIALOG
    // ════════════════════════════════════════════
    private void showJoinDialog(StudyRoom room) {
        VBox content = new VBox(14);
        content.setPadding(new Insets(28, 32, 24, 32));
        content.setStyle("-fx-background-color:#0d0f1e;");

        Label titleLbl = new Label("🚀 Join: " + room.roomName());
        titleLbl.setStyle("-fx-font-size:16;-fx-font-weight:bold;-fx-text-fill:#e2e8f0;");

        Label subLbl = new Label("Set your focus details for this session");
        subLbl.setStyle("-fx-text-fill:#475569;-fx-font-size:12;");

        Label topicHdr = new Label("📚 Study Topic");
        topicHdr.setStyle("-fx-text-fill:#94a3b8;-fx-font-size:12;-fx-font-weight:bold;");
        TextField topicField = new TextField();
        topicField.setPromptText("e.g. Calculus, Java, History…");
        topicField.setStyle(inputStyle());

        Label taskHdr = new Label("🎯 Session Goal");
        taskHdr.setStyle("-fx-text-fill:#94a3b8;-fx-font-size:12;-fx-font-weight:bold;");
        TextArea taskField = new TextArea();
        taskField.setPromptText("What do you want to accomplish?");
        taskField.setPrefRowCount(2);
        taskField.setWrapText(true);
        taskField.setStyle(inputStyle());

        Label timerHdr = new Label("⏱ Timer Duration (minutes)");
        timerHdr.setStyle("-fx-text-fill:#94a3b8;-fx-font-size:12;-fx-font-weight:bold;");
        Spinner<Integer> timerSpinner = new Spinner<>(5, 120, 25, 5);
        timerSpinner.setEditable(true);
        timerSpinner.setMaxWidth(Double.MAX_VALUE);
        timerSpinner.setStyle("-fx-background-color:#1a1e35;-fx-background-radius:8;");

        Label topicError = new Label("⚠ Please enter a study topic");
        topicError.setStyle("-fx-text-fill:#f87171;-fx-font-size:11;");
        topicError.setVisible(false);

        HBox btnRow = new HBox(12);
        btnRow.setAlignment(Pos.CENTER_RIGHT);
        Button cancelBtn = new Button("Cancel");
        cancelBtn.setStyle(btnSecondary());
        Button enterBtn = new Button("Enter Room 🚀");
        enterBtn.setStyle(btnPrimary("#6366f1"));
        btnRow.getChildren().addAll(cancelBtn, enterBtn);

        content.getChildren().addAll(titleLbl, subLbl, topicHdr, topicField,
            taskHdr, taskField, timerHdr, timerSpinner, topicError, btnRow);

        javafx.stage.Stage popup = PopupHelper.create(window(), "Join Study Room", content, 380, 400, 460, 455);

        cancelBtn.setOnAction(e -> popup.close());
        enterBtn.setOnAction(e -> {
            String topic = topicField.getText().trim();
            if (topic.isEmpty()) {
                topicError.setVisible(true);
                topicField.setStyle(inputStyle() + "-fx-border-color:#ef4444;");
                return;
            }
            popup.close();
            enterRoom(room, topic, taskField.getText().trim(), timerSpinner.getValue());
        });
        popup.show();
    }

    // ════════════════════════════════════════════
    // ENTER ROOM
    // ════════════════════════════════════════════
    private void enterRoom(StudyRoom room, String topic, String task, int minutes) {
        stopAllTimers();
        this.currentRoom          = room;
        this.currentTopic         = topic;
        this.currentTask          = task;
        this.totalSessionSeconds  = minutes * 60;
        this.timerPaused          = false;
        secondsRemaining.set(totalSessionSeconds);

        new Thread(() -> {
            String pid = roomService.joinRoom(
                room.id(), AuthService.CURRENT_USER_ID.toString(),
                AuthService.CURRENT_USER_NAME, topic, task, minutes);
            currentParticipantId = pid;
            if ("PUBLIC".equals(room.type())) {
                roomService.addXP(AuthService.CURRENT_USER_ID.toString(), 5);
            }
            Platform.runLater(() -> buildRoomUI(room, topic, task, minutes));
        }).start();
    }

    // ════════════════════════════════════════════
    // ROOM UI
    // ════════════════════════════════════════════
    private void buildRoomUI(StudyRoom room, String topic, String task, int minutes) {
        if (roomContentPane == null) return;
        roomContentPane.getChildren().clear();

        // Top bar
        HBox topBar = new HBox(12);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setPadding(new Insets(11, 18, 11, 18));
        topBar.setStyle("-fx-background-color:#0b0d1a;" +
            "-fx-border-color:rgba(99,102,241,0.2);-fx-border-width:0 0 1 0;");

        Label roomNameLbl = new Label("📖 " + room.roomName() +
            ("PRIVATE".equals(room.type()) ? " (Private)" : ""));
        roomNameLbl.setStyle("-fx-font-weight:bold;-fx-font-size:16;-fx-text-fill:#e2e8f0;");

        Label modeChip = new Label("SILENT".equals(room.mode()) ? "🔇 Silent" : "💬 Group");
        modeChip.setStyle("-fx-background-color:rgba(99,102,241,0.1);-fx-text-fill:#818cf8;" +
            "-fx-background-radius:20;-fx-padding:4 12;-fx-font-size:11;-fx-font-weight:bold;");

        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);

        modeToggleBtn = new Button("SILENT".equals(room.mode()) ? "🔇 Silent" : "💬 Group");
        modeToggleBtn.setStyle(btnGhost("#818cf8", "rgba(99,102,241,0.3)"));
        modeToggleBtn.setOnAction(e -> toggleRoomMode(room));

        Button leaveBtn = new Button("Leave ✗");
        leaveBtn.setStyle(btnGhost("#f87171", "rgba(239,68,68,0.3)"));
        leaveBtn.setOnAction(e -> leaveCurrentRoom());

        topBar.getChildren().addAll(roomNameLbl, modeChip, spacer, modeToggleBtn, leaveBtn);

        // 3-column split
        SplitPane split = new SplitPane();
        split.setDividerPositions(0.19, 0.60);
        split.setStyle("-fx-background-color:#080a14;-fx-border-color:transparent;");
        VBox.setVgrow(split, Priority.ALWAYS);

        split.getItems().addAll(
            buildActiveUsersPane(),
            buildCenterPane(topic, task, minutes),
            buildChatPane(room)
        );

        roomContentPane.getChildren().addAll(topBar, split);

        startPomodoroTimer(minutes);
        startBoardRefresh(room.id());
        if ("GROUP".equals(room.mode()) && "PUBLIC".equals(room.type())) {
            startChatRefresh(room.id());
        }
    }

    // ── Active Users pane ──
    private VBox buildActiveUsersPane() {
        VBox pane = new VBox(10);
        pane.setPadding(new Insets(16));
        pane.setStyle("-fx-background-color:#0b0d1a;");

        Label title = new Label("👥 In This Room");
        title.setStyle("-fx-font-weight:bold;-fx-font-size:12;-fx-text-fill:#64748b;" +
            "-fx-padding:0 0 4 0;");

        activeUsersList = new VBox(8);
        activeUsersList.setStyle("-fx-background-color:transparent;");

        ScrollPane scroll = new ScrollPane(activeUsersList);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background:#0b0d1a;-fx-background-color:#0b0d1a;-fx-border-color:transparent;");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        pane.getChildren().addAll(title, scroll);
        return pane;
    }

    // ── Center pane: timer + board ──
    private VBox buildCenterPane(String topic, String task, int minutes) {
        VBox pane = new VBox(14);
        pane.setPadding(new Insets(16, 18, 16, 18));
        pane.setStyle("-fx-background-color:#080a14;");

        // Timer card
        VBox timerCard = new VBox(10);
        timerCard.setAlignment(Pos.CENTER);
        timerCard.setPadding(new Insets(20, 28, 20, 28));
        timerCard.setStyle("-fx-background-color:#111325;-fx-background-radius:16;" +
            "-fx-border-color:rgba(99,102,241,0.28);-fx-border-radius:16;" +
            "-fx-effect:dropshadow(gaussian,rgba(99,102,241,0.2),18,0,0,4);");

        Label topicLbl = new Label("📚 " + topic);
        topicLbl.setStyle("-fx-font-weight:bold;-fx-font-size:14;-fx-text-fill:#818cf8;");

        timerLabel = new Label(formatTime(minutes * 60));
        timerLabel.setStyle("-fx-font-size:58;-fx-font-weight:bold;-fx-text-fill:#e2e8f0;" +
            "-fx-font-family:'Courier New';");

        timerProgressBar = new ProgressBar(1.0);
        timerProgressBar.setMaxWidth(Double.MAX_VALUE);
        timerProgressBar.setStyle("-fx-accent:#6366f1;-fx-pref-height:6;" +
            "-fx-background-color:#1e2235;-fx-background-radius:4;");

        String taskText = (task == null || task.isBlank()) ? "No specific goal set" : task;
        Label taskLbl = new Label("🎯 " + taskText);
        taskLbl.setWrapText(true);
        taskLbl.setMaxWidth(380);
        taskLbl.setStyle("-fx-font-size:12;-fx-text-fill:#64748b;-fx-text-alignment:center;");

        // Controls
        HBox controls = new HBox(10);
        controls.setAlignment(Pos.CENTER);

        Button pauseBtn = new Button("⏸ Pause");
        pauseBtn.setStyle(btnGhost("#fbbf24", "rgba(251,191,36,0.3)"));
        pauseBtn.setOnAction(e -> handlePauseResume(pauseBtn));

        Button finishBtn = new Button("✅ Finish Session");
        finishBtn.setStyle(btnGhost("#34d399", "rgba(16,185,129,0.3)"));
        finishBtn.setOnAction(e -> finishSession(true));

        controls.getChildren().addAll(pauseBtn, finishBtn);
        timerCard.getChildren().addAll(topicLbl, timerLabel, timerProgressBar, taskLbl, controls);

        // Study board — shows only COMPLETED tasks in this room
        HBox boardHeader = new HBox(8);
        boardHeader.setAlignment(Pos.CENTER_LEFT);
        Label boardTitle = new Label("📊 Completed Tasks This Session");
        boardTitle.setStyle("-fx-font-weight:bold;-fx-font-size:12;-fx-text-fill:#64748b;");
        Label boardSub = new Label("(live updates every 4s)");
        boardSub.setStyle("-fx-text-fill:#334155;-fx-font-size:10;");
        boardHeader.getChildren().addAll(boardTitle, boardSub);

        boardTable = buildStudyBoardTable();
        VBox.setVgrow(boardTable, Priority.ALWAYS);

        pane.getChildren().addAll(timerCard, boardHeader, boardTable);
        return pane;
    }

    private void handlePauseResume(Button pauseBtn) {
        if (pomodoroTimer == null) return;
        if (pomodoroTimer.getStatus() == Timeline.Status.RUNNING) {
            pomodoroTimer.pause();
            timerPaused = true;
            pauseBtn.setText("▶ Resume");
            pauseBtn.setStyle(btnGhost("#34d399", "rgba(16,185,129,0.3)"));
            // Mark user as ON_BREAK in DB
            if (currentParticipantId != null) {
                new Thread(() -> roomService.setBreak(currentParticipantId, true)).start();
            }
        } else {
            pomodoroTimer.play();
            timerPaused = false;
            pauseBtn.setText("⏸ Pause");
            pauseBtn.setStyle(btnGhost("#fbbf24", "rgba(251,191,36,0.3)"));
            if (currentParticipantId != null) {
                new Thread(() -> roomService.setBreak(currentParticipantId, false)).start();
            }
        }
    }

    // ── Study board: completed tasks only ──
    @SuppressWarnings("unchecked")
    private TableView<StudySession> buildStudyBoardTable() {
        TableView<StudySession> table = new TableView<>();
        table.setStyle("-fx-background-color:#111325;-fx-background-radius:12;" +
            "-fx-border-color:rgba(99,102,241,0.2);-fx-border-radius:12;" +
            "-fx-table-cell-border-color:rgba(99,102,241,0.07);");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPlaceholder(darkPlaceholder("No completed tasks yet in this room"));

        TableColumn<StudySession, String> colUser = new TableColumn<>("👤 Student");
        colUser.setCellValueFactory(d -> prop(d.getValue().userName()));
        colUser.setCellFactory(c -> darkCell("#e2e8f0", true));

        TableColumn<StudySession, String> colTopic = new TableColumn<>("📚 Topic");
        colTopic.setCellValueFactory(d -> prop(d.getValue().topic()));
        colTopic.setCellFactory(c -> darkCell("#818cf8", false));

        TableColumn<StudySession, String> colTask = new TableColumn<>("🎯 Goal");
        colTask.setCellValueFactory(d -> {
            String t = d.getValue().taskDescription();
            return prop(t == null || t.isBlank() ? "—" : t);
        });
        colTask.setCellFactory(c -> darkCell("#94a3b8", false));

        TableColumn<StudySession, String> colTime = new TableColumn<>("✅ Completed");
        colTime.setCellValueFactory(d ->
            prop(d.getValue().timerDuration() + " min"));
        colTime.setCellFactory(c -> darkCell("#34d399", true));

        TableColumn<StudySession, String> colStarted = new TableColumn<>("🕐 At");
        colStarted.setCellValueFactory(d -> {
            String t = d.getValue().startTime();
            if (t != null && t.length() > 16) t = t.substring(11, 16);
            return prop(t != null ? t : "—");
        });
        colStarted.setCellFactory(c -> darkCell("#64748b", false));

        // Status badge — supports STUDYING/ON_BREAK/COMPLETED
        TableColumn<StudySession, String> colStatus = new TableColumn<>("Status");
        colStatus.setCellValueFactory(d -> prop(d.getValue().completionStatus()));
        colStatus.setCellFactory(c -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setGraphic(null); setText(null); return; }
                String text, color, bg;
                switch (item) {
                    case "COMPLETED" -> { text = "✅ Done";      color = "#34d399"; bg = "rgba(16,185,129,0.1)"; }
                    case "ON_BREAK"  -> { text = "⏸ Break";     color = "#fbbf24"; bg = "rgba(251,191,36,0.1)"; }
                    case "ABANDONED" -> { text = "❌ Left";      color = "#f87171"; bg = "rgba(239,68,68,0.1)"; }
                    default          -> { text = "🟢 Studying";  color = "#60a5fa"; bg = "rgba(96,165,250,0.1)"; }
                }
                Label badge = new Label(text);
                badge.setStyle("-fx-text-fill:" + color + ";-fx-background-color:" + bg + ";" +
                    "-fx-background-radius:20;-fx-padding:3 10;-fx-font-size:11;-fx-font-weight:bold;");
                setGraphic(badge);
                setText(null);
                setStyle("-fx-background-color:transparent;-fx-alignment:CENTER_LEFT;");
            }
        });

        table.getColumns().addAll(colUser, colTopic, colTask, colTime, colStarted, colStatus);
        return table;
    }

    // ── Chat pane ──
    private VBox buildChatPane(StudyRoom room) {
        VBox pane = new VBox(0);
        pane.setStyle("-fx-background-color:#0b0d1a;");

        HBox header = new HBox();
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(13, 16, 11, 16));
        header.setStyle("-fx-background-color:#0b0d1a;" +
            "-fx-border-color:rgba(99,102,241,0.15);-fx-border-width:0 0 1 0;");
        Label titleLbl = new Label("💬 Room Chat");
        titleLbl.setStyle("-fx-font-weight:bold;-fx-font-size:13;-fx-text-fill:#64748b;");
        header.getChildren().add(titleLbl);

        chatBox = new VBox(6);
        chatBox.setPadding(new Insets(10, 10, 6, 10));
        chatBox.setStyle("-fx-background-color:#0b0d1a;");
        chatScroll = new ScrollPane(chatBox);
        chatScroll.setFitToWidth(true);
        chatScroll.setStyle("-fx-background:#0b0d1a;-fx-background-color:#0b0d1a;-fx-border-color:transparent;");
        VBox.setVgrow(chatScroll, Priority.ALWAYS);

        boolean isSilent = "SILENT".equals(room.mode()) || "PRIVATE".equals(room.type());
        if (isSilent) {
            VBox msg = new VBox(8);
            msg.setAlignment(Pos.CENTER);
            msg.setPadding(new Insets(40, 16, 16, 16));
            Label icon = new Label("🔇"); icon.setStyle("-fx-font-size:34;");
            Label lbl  = new Label("Silent Mode");
            lbl.setStyle("-fx-text-fill:#475569;-fx-font-weight:bold;-fx-font-size:13;");
            Label sub  = new Label("Chat is disabled in silent and private rooms");
            sub.setStyle("-fx-text-fill:#2d3145;-fx-font-size:11;");
            sub.setWrapText(true);
            sub.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
            msg.getChildren().addAll(icon, lbl, sub);
            chatBox.getChildren().add(msg);
        }

        // Input row
        HBox inputRow = new HBox(8);
        inputRow.setAlignment(Pos.CENTER);
        inputRow.setPadding(new Insets(9, 12, 11, 12));
        inputRow.setStyle("-fx-background-color:#0b0d1a;" +
            "-fx-border-color:rgba(99,102,241,0.15);-fx-border-width:1 0 0 0;");
        TextField msgInput = new TextField();
        msgInput.setPromptText(isSilent ? "Chat disabled" : "Say something…");
        msgInput.setDisable(isSilent);
        msgInput.setStyle("-fx-background-color:#1a1e35;-fx-text-fill:#e2e8f0;" +
            "-fx-prompt-text-fill:#334155;-fx-background-radius:20;" +
            "-fx-border-color:rgba(99,102,241,0.2);-fx-border-radius:20;-fx-padding:9 14;");
        HBox.setHgrow(msgInput, Priority.ALWAYS);
        Button sendBtn = new Button("Send");
        sendBtn.setDisable(isSilent);
        sendBtn.setStyle("-fx-background-color:#6366f1;-fx-text-fill:white;" +
            "-fx-font-weight:bold;-fx-background-radius:20;-fx-cursor:hand;" +
            "-fx-padding:9 16;-fx-font-size:12;");
        sendBtn.setOnAction(e -> {
            String msg = msgInput.getText().trim();
            if (!msg.isEmpty()) {
                new Thread(() -> roomService.sendChatMessage(
                    room.id(), AuthService.CURRENT_USER_ID.toString(),
                    AuthService.CURRENT_USER_NAME, msg)).start();
                msgInput.clear();
            }
        });
        msgInput.setOnAction(e -> sendBtn.fire());
        inputRow.getChildren().addAll(msgInput, sendBtn);

        pane.getChildren().addAll(header, chatScroll, inputRow);
        return pane;
    }

    // ════════════════════════════════════════════
    // TIMER
    // ════════════════════════════════════════════
    private void startPomodoroTimer(int minutes) {
        secondsRemaining.set(minutes * 60);
        pomodoroTimer = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            int rem = secondsRemaining.decrementAndGet();
            Platform.runLater(() -> {
                if (timerLabel != null) timerLabel.setText(formatTime(rem));
                if (timerProgressBar != null && totalSessionSeconds > 0) {
                    double ratio = (double) rem / totalSessionSeconds;
                    timerProgressBar.setProgress(ratio);
                    String col = ratio > 0.5 ? "#6366f1" : ratio > 0.2 ? "#fbbf24" : "#ef4444";
                    timerProgressBar.setStyle("-fx-accent:" + col + ";-fx-pref-height:6;");
                    if (rem <= 60 && timerLabel != null) {
                        timerLabel.setStyle("-fx-font-size:58;-fx-font-weight:bold;" +
                            "-fx-text-fill:#ef4444;-fx-font-family:'Courier New';");
                    }
                }
            });
            if (rem <= 0) {
                pomodoroTimer.stop();
                Platform.runLater(() -> finishSession(false));
            }
        }));
        pomodoroTimer.setCycleCount(Timeline.INDEFINITE);
        pomodoroTimer.play();
    }

    private String formatTime(int secs) {
        if (secs < 0) secs = 0;
        return String.format("%02d:%02d", secs / 60, secs % 60);
    }

    // ════════════════════════════════════════════
    // BOARD REFRESH — shows COMPLETED + STUDYING
    // (board displays COMPLETED prominently, STUDYING in real-time)
    // ════════════════════════════════════════════
    private void startBoardRefresh(String roomId) {
        boardRefreshTimer = new Timeline(new KeyFrame(Duration.seconds(4), e -> {
            new Thread(() -> {
                // All sessions (completed + studying + break), no abandoned
                List<StudySession> sessions = roomService.getBoardSessions(roomId);
                Platform.runLater(() -> {
                    if (boardTable != null) boardTable.getItems().setAll(sessions);
                    if (activeUsersList != null) refreshActiveUsers(sessions);
                });
            }).start();
        }));
        boardRefreshTimer.setCycleCount(Timeline.INDEFINITE);
        boardRefreshTimer.play();
    }

    private void refreshActiveUsers(List<StudySession> sessions) {
        activeUsersList.getChildren().clear();
        for (StudySession s : sessions) {
            if ("ABANDONED".equals(s.completionStatus())) continue;

            HBox row = new HBox(8);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(9, 12, 9, 12));
            row.setStyle("-fx-background-color:#111325;-fx-background-radius:10;" +
                "-fx-border-color:rgba(99,102,241,0.12);-fx-border-radius:10;" +
                "-fx-effect:dropshadow(gaussian,rgba(99,102,241,0.08),8,0,0,1);");

            String dotColor = switch (s.completionStatus()) {
                case "COMPLETED" -> "#34d399";
                case "ON_BREAK"  -> "#fbbf24";
                default          -> "#10b981";
            };
            Circle dot = new Circle(5, Color.web(dotColor));

            VBox info = new VBox(2);
            Label nameLbl = new Label(s.userName());
            nameLbl.setStyle("-fx-font-size:12;-fx-font-weight:bold;-fx-text-fill:#e2e8f0;");
            Label topicLbl = new Label("📚 " + s.topic());
            topicLbl.setStyle("-fx-font-size:10;-fx-text-fill:#818cf8;");
            topicLbl.setMaxWidth(120);

            String statusText = switch (s.completionStatus()) {
                case "COMPLETED" -> "✅ Done";
                case "ON_BREAK"  -> "⏸ Break";
                default          -> "🟢 Studying";
            };
            Label statusLbl = new Label(statusText);
            statusLbl.setStyle("-fx-font-size:9;-fx-text-fill:#475569;");
            info.getChildren().addAll(nameLbl, topicLbl, statusLbl);

            row.getChildren().addAll(dot, info);
            activeUsersList.getChildren().add(row);
        }
        if (activeUsersList.getChildren().isEmpty()) {
            Label empty = new Label("No one here yet");
            empty.setStyle("-fx-text-fill:#2d3145;-fx-font-size:12;-fx-padding:6 0;");
            activeUsersList.getChildren().add(empty);
        }
    }

    // ════════════════════════════════════════════
    // CHAT REFRESH
    // ════════════════════════════════════════════
    private void startChatRefresh(String roomId) {
        chatRefreshTimer = new Timeline(new KeyFrame(Duration.seconds(3), e -> {
            new Thread(() -> {
                var msgs = roomService.getChatMessages(roomId);
                Platform.runLater(() -> renderChat(msgs));
            }).start();
        }));
        chatRefreshTimer.setCycleCount(Timeline.INDEFINITE);
        chatRefreshTimer.play();
    }

    private void renderChat(List<StudyRoomService.ChatMessage> msgs) {
        if (chatBox == null) return;
        chatBox.getChildren().clear();
        for (StudyRoomService.ChatMessage msg : msgs) {
            boolean isMe = msg.sender().equals(AuthService.CURRENT_USER_NAME);

            VBox bubble = new VBox(2);
            bubble.setMaxWidth(210);
            bubble.setPadding(new Insets(8, 12, 8, 12));

            if (!isMe) {
                Label sender = new Label(msg.sender());
                sender.setStyle("-fx-font-size:10;-fx-font-weight:bold;-fx-text-fill:#818cf8;");
                bubble.getChildren().add(sender);
            }

            Label text = new Label(msg.content());
            text.setWrapText(true);
            text.setMaxWidth(190);
            text.setStyle("-fx-text-fill:" + (isMe ? "#e2e8f0" : "#cbd5e1") + ";-fx-font-size:13;");

            String time = msg.time();
            if (time != null && time.length() > 16) time = time.substring(11, 16);
            Label timeLbl = new Label(time != null ? time : "");
            timeLbl.setStyle("-fx-font-size:9;-fx-text-fill:#334155;");

            bubble.getChildren().addAll(text, timeLbl);

            String radii = isMe ? "12 12 2 12" : "12 12 12 2";
            bubble.setStyle((isMe
                ? "-fx-background-color:#1e2b50;-fx-border-color:rgba(99,102,241,0.28);"
                : "-fx-background-color:#161a2e;-fx-border-color:rgba(99,102,241,0.1);") +
                "-fx-background-radius:" + radii + ";-fx-border-radius:" + radii + ";");

            HBox wrapper = new HBox();
            wrapper.setAlignment(isMe ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
            wrapper.getChildren().add(bubble);
            chatBox.getChildren().add(wrapper);
        }
        if (chatScroll != null) chatScroll.setVvalue(1.0);
    }

    // ════════════════════════════════════════════
    // FINISH SESSION
    // Rule: save to history ONLY if completed (not abandoned).
    // Analytics always get elapsed minutes (even abandoned).
    // ════════════════════════════════════════════
    private void finishSession(boolean early) {
        stopAllTimers();
        int elapsedSecs = totalSessionSeconds - secondsRemaining.get();
        int elapsedMins = Math.max(1, elapsedSecs / 60);
        int xpEarned    = elapsedMins * 2 + 20;

        if (currentParticipantId == null || currentRoom == null) {
            showRoomSelector();
            return;
        }

        final String    pid   = currentParticipantId;
        final StudyRoom room  = currentRoom;
        final String    topic = currentTopic;
        final String    task  = currentTask;
        final int       planned = totalSessionSeconds / 60;
        currentParticipantId = null;
        currentRoom = null;

        new Thread(() -> {
            // 1. Mark session COMPLETED in participants table
            roomService.completeSession(pid, elapsedMins);

            // 2. Save to HISTORY (completed sessions only, not abandoned)
            roomService.saveHistory(
                AuthService.CURRENT_USER_ID.toString(), room.id(),
                topic, task, planned, elapsedMins, xpEarned);

            // 3. Give XP + update streak
            roomService.addXP(AuthService.CURRENT_USER_ID.toString(), xpEarned);
            roomService.updateStreak(AuthService.CURRENT_USER_ID.toString());

            Platform.runLater(() -> {
                loadStreakAndXP();
                loadHistory();
                loadAnalytics();
                PopupHelper.showInfo(
                    window(),
                    "🎉 Session Complete!",
                    "You studied for " + elapsedMins + " min on \"" + topic + "\".\n" +
                    "+" + xpEarned + " XP earned  •  Streak updated 🔥\n\n" +
                    "You can start a new task in the same room anytime.");
                showRoomSelector();
            });
        }).start();
    }

    // ════════════════════════════════════════════
    // LEAVE ROOM (abandoned — NO history saved)
    // Analytics still count elapsed minutes.
    // ════════════════════════════════════════════
    private void leaveCurrentRoom() {
        if (currentRoom == null) return;
        PopupHelper.showConfirm(
            window(),
            "Leave Room?",
            "Leave the room? This session won't be saved to your history.\n" +
            "Elapsed minutes will still count toward your analytics.",
            () -> {
                stopAllTimers();
                int elapsedSecs = totalSessionSeconds - secondsRemaining.get();
                int elapsedMins = Math.max(0, elapsedSecs / 60);

                final String    pid  = currentParticipantId;
                final StudyRoom room = currentRoom;
                currentParticipantId = null;
                currentRoom = null;

                new Thread(() -> {
                    if (pid != null) {
                        roomService.leaveRoom(room.id(), AuthService.CURRENT_USER_ID.toString());
                    }
                    // Save elapsed minutes to analytics-only (no history entry)
                    if (elapsedMins > 0) {
                        roomService.saveAnalyticsOnly(
                            AuthService.CURRENT_USER_ID.toString(),
                            elapsedMins);
                    }
                    Platform.runLater(() -> {
                        loadAnalytics();
                        showRoomSelector();
                    });
                }).start();
            }
        );
    }

    private void showRoomSelector() {
        if (roomContentPane == null) return;
        roomContentPane.getChildren().clear();
        VBox placeholder = new VBox(18);
        placeholder.setAlignment(Pos.CENTER);
        placeholder.setStyle("-fx-background-color:#080a14;");
        VBox.setVgrow(placeholder, Priority.ALWAYS);
        Label icon = new Label("📖"); icon.setStyle("-fx-font-size:50;");
        Label lbl  = new Label("Select a room to start studying");
        lbl.setStyle("-fx-font-size:17;-fx-text-fill:#475569;");
        Label sub  = new Label("Pick a public room or enter your private space from the sidebar.");
        sub.setStyle("-fx-font-size:12;-fx-text-fill:#2d3145;");
        placeholder.getChildren().addAll(icon, lbl, sub);
        roomContentPane.getChildren().add(placeholder);
        loadPublicRooms();
    }

    // ════════════════════════════════════════════
    // TOGGLE ROOM MODE
    // ════════════════════════════════════════════
    private void toggleRoomMode(StudyRoom room) {
        if (!room.createdBy().equals(AuthService.CURRENT_USER_ID.toString())
                && !"admin".equals(AuthService.CURRENT_USER_ROLE)) {
            PopupHelper.showError(window(), "Only the room creator can change the mode.");
            return;
        }
        String newMode = "GROUP".equals(room.mode()) ? "SILENT" : "GROUP";
        new Thread(() -> {
            roomService.toggleMode(room.id(), newMode);
            Platform.runLater(() -> {
                if (modeToggleBtn != null)
                    modeToggleBtn.setText("SILENT".equals(newMode) ? "🔇 Silent" : "💬 Group");
                PopupHelper.showInfo(window(), "Mode switched to " + newMode);
            });
        }).start();
    }

    // ════════════════════════════════════════════
    // CREATE ROOM DIALOG
    // ════════════════════════════════════════════
    @FXML
    public void showCreateRoomDialog() {
        VBox content = new VBox(14);
        content.setPadding(new Insets(28, 32, 24, 32));
        content.setStyle("-fx-background-color:#0d0f1e;");

        Label titleLbl = new Label("🏠 Create Study Room");
        titleLbl.setStyle("-fx-font-size:16;-fx-font-weight:bold;-fx-text-fill:#e2e8f0;");

        Label nameHdr = new Label("Room Name");
        nameHdr.setStyle("-fx-text-fill:#94a3b8;-fx-font-size:12;-fx-font-weight:bold;");
        TextField nameField = new TextField();
        nameField.setPromptText("e.g. Code Sprint 🚀, Math Group…");
        nameField.setStyle(inputStyle());

        Label typeHdr = new Label("Room Type");
        typeHdr.setStyle("-fx-text-fill:#94a3b8;-fx-font-size:12;-fx-font-weight:bold;");
        ComboBox<String> typeBox = new ComboBox<>();
        typeBox.getItems().addAll("PUBLIC", "PRIVATE");
        typeBox.setValue("PUBLIC");
        typeBox.setMaxWidth(Double.MAX_VALUE);

        Label modeHdr = new Label("Study Mode");
        modeHdr.setStyle("-fx-text-fill:#94a3b8;-fx-font-size:12;-fx-font-weight:bold;");
        ComboBox<String> modeBox = new ComboBox<>();
        modeBox.getItems().addAll("GROUP", "SILENT");
        modeBox.setValue("GROUP");
        modeBox.setMaxWidth(Double.MAX_VALUE);

        Label nameError = new Label("⚠ Room name is required");
        nameError.setStyle("-fx-text-fill:#f87171;-fx-font-size:11;");
        nameError.setVisible(false);

        HBox btnRow = new HBox(12);
        btnRow.setAlignment(Pos.CENTER_RIGHT);
        Button cancelBtn = new Button("Cancel");
        cancelBtn.setStyle(btnSecondary());
        Button createBtn = new Button("Create Room ✨");
        createBtn.setStyle(btnPrimary("#10b981"));
        btnRow.getChildren().addAll(cancelBtn, createBtn);

        content.getChildren().addAll(titleLbl, nameHdr, nameField,
            typeHdr, typeBox, modeHdr, modeBox, nameError, btnRow);

        javafx.stage.Stage popup = PopupHelper.create(window(), "Create Study Room", content, 360, 340, 440, 380);

        cancelBtn.setOnAction(e -> popup.close());
        createBtn.setOnAction(e -> {
            if (nameField.getText().trim().isEmpty()) {
                nameError.setVisible(true);
                nameField.setStyle(inputStyle() + "-fx-border-color:#ef4444;");
                return;
            }
            popup.close();
            new Thread(() -> {
                String id = roomService.createRoom(nameField.getText().trim(),
                    typeBox.getValue(), AuthService.CURRENT_USER_ID.toString(), modeBox.getValue());
                Platform.runLater(() -> {
                    if (id != null) {
                        loadPublicRooms();
                        PopupHelper.showInfo(window(), "Room Created! 🎉", "Your room is now live.");
                    } else {
                        PopupHelper.showError(window(), "Failed to create room. Please try again.");
                    }
                });
            }).start();
        });
        popup.show();
    }

    // ════════════════════════════════════════════
    // ANALYTICS
    // ════════════════════════════════════════════
    private void loadAnalytics() {
        new Thread(() -> {
            String uid      = AuthService.CURRENT_USER_ID.toString();
            var weekly      = roomService.getWeeklyStats(uid);
            var topics      = roomService.getTopicStats(uid);
            int todayMins   = roomService.getTodayStudyMinutes(uid);
            int weekMins    = weekly.values().stream().mapToInt(Integer::intValue).sum();
            int streak      = roomService.getStreak(uid);
            int totalXp     = roomService.getTotalXP(uid);

            Platform.runLater(() -> {
                if (analyticsTodayLabel  != null) analyticsTodayLabel.setText(todayMins + " min");
                if (analyticsWeekLabel   != null) analyticsWeekLabel.setText(weekMins + " min");
                if (analyticsStreakLabel  != null) analyticsStreakLabel.setText(streak + " days");
                if (analyticsXpLabel     != null) analyticsXpLabel.setText(totalXp + " XP");

                if (weeklyChart != null) {
                    weeklyChart.getData().clear();
                    weeklyChart.setStyle("-fx-background-color:transparent;");
                    XYChart.Series<String, Number> series = new XYChart.Series<>();
                    series.setName("Minutes");
                    weekly.forEach((day, mins) -> series.getData().add(new XYChart.Data<>(day, mins)));
                    weeklyChart.getData().add(series);
                }

                if (topicPieChart != null) {
                    topicPieChart.getData().clear();
                    topicPieChart.setStyle("-fx-background-color:transparent;");
                    if (topics.isEmpty()) {
                        topicPieChart.getData().add(new PieChart.Data("No data yet", 1));
                    } else {
                        topics.forEach((t, m) ->
                            topicPieChart.getData().add(new PieChart.Data(t + " (" + m + "m)", m)));
                    }
                }
            });
        }).start();
    }

    // ════════════════════════════════════════════
    // HISTORY — completed sessions only
    // ════════════════════════════════════════════
    private void loadHistory() {
        if (historyContainer == null) return;
        new Thread(() -> {
            allHistory = roomService.getHistory(AuthService.CURRENT_USER_ID.toString());
            Platform.runLater(() -> renderHistory(allHistory));
        }).start();
    }

    @FXML public void filterHistoryAll()   { renderHistory(allHistory); setFilter("all"); }
    @FXML public void filterHistoryWeek()  {
        String cutoff = java.time.LocalDate.now().minusDays(7).toString();
        renderHistory(allHistory.stream()
            .filter(h -> h.date() != null && h.date().compareTo(cutoff) >= 0).toList());
        setFilter("week");
    }
    @FXML public void filterHistoryToday() {
        String today = java.time.LocalDate.now().toString();
        renderHistory(allHistory.stream()
            .filter(h -> h.date() != null && h.date().startsWith(today)).toList());
        setFilter("today");
    }

    private void setFilter(String f) {
        String on  = "-fx-background-color:#6366f1;-fx-text-fill:white;-fx-background-radius:20;-fx-padding:6 18;-fx-cursor:hand;-fx-font-size:12;-fx-font-weight:bold;";
        String off = "-fx-background-color:rgba(255,255,255,0.04);-fx-text-fill:#64748b;-fx-border-color:rgba(255,255,255,0.09);-fx-border-radius:20;-fx-background-radius:20;-fx-padding:6 18;-fx-cursor:hand;-fx-font-size:12;";
        if (filterAllBtn   != null) filterAllBtn.setStyle("all".equals(f)   ? on : off);
        if (filterWeekBtn  != null) filterWeekBtn.setStyle("week".equals(f) ? on : off);
        if (filterTodayBtn != null) filterTodayBtn.setStyle("today".equals(f) ? on : off);
    }

    private void renderHistory(List<StudyHistory> list) {
        if (historyContainer == null) return;
        historyContainer.getChildren().clear();

        if (list.isEmpty()) {
            VBox empty = new VBox(10);
            empty.setAlignment(Pos.CENTER);
            empty.setPadding(new Insets(40));
            Label icon = new Label("📭"); icon.setStyle("-fx-font-size:38;");
            Label msg  = new Label("No completed sessions found.");
            msg.setStyle("-fx-text-fill:#475569;-fx-font-size:13;");
            Label sub  = new Label("Finish a full session to see it here.");
            sub.setStyle("-fx-text-fill:#334155;-fx-font-size:11;");
            empty.getChildren().addAll(icon, msg, sub);
            historyContainer.getChildren().add(empty);
            return;
        }

        for (StudyHistory h : list) {
            int planned   = Math.max(1, h.plannedTime());
            int completed = h.completedTime();
            double ratio  = Math.min(1.0, (double) completed / planned);
            String rColor = ratio >= 0.8 ? "#34d399" : ratio >= 0.5 ? "#fbbf24" : "#f87171";

            VBox card = new VBox(10);
            card.setPadding(new Insets(14, 16, 14, 16));
            card.setStyle("-fx-background-color:#111325;-fx-background-radius:12;" +
                "-fx-border-color:rgba(99,102,241,0.18);-fx-border-radius:12;" +
                "-fx-effect:dropshadow(gaussian,rgba(99,102,241,0.1),10,0,0,2);");

            // Top: date + XP badge
            HBox topRow = new HBox(10);
            topRow.setAlignment(Pos.CENTER_LEFT);
            Label dateLbl = new Label("📅 " + fmtDate(h.date()));
            dateLbl.setStyle("-fx-text-fill:#64748b;-fx-font-size:11;");
            Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
            Label xpLbl = new Label("+" + h.earnedXp() + " XP");
            xpLbl.setStyle("-fx-text-fill:#c084fc;-fx-font-weight:bold;-fx-font-size:12;" +
                "-fx-background-color:rgba(124,58,237,0.12);-fx-background-radius:20;-fx-padding:2 10;");
            topRow.getChildren().addAll(dateLbl, sp, xpLbl);

            // Topic
            Label topicLbl = new Label("📚 " + nvl(h.topic(), "—"));
            topicLbl.setStyle("-fx-font-weight:bold;-fx-font-size:14;-fx-text-fill:#818cf8;");

            card.getChildren().addAll(topRow, topicLbl);

            // Task (optional)
            if (h.task() != null && !h.task().isBlank()) {
                Label taskLbl = new Label("🎯 " + h.task());
                taskLbl.setStyle("-fx-text-fill:#64748b;-fx-font-size:12;");
                taskLbl.setWrapText(true);
                card.getChildren().add(taskLbl);
            }

            // Time stats
            HBox statsRow = new HBox(16);
            statsRow.setAlignment(Pos.CENTER_LEFT);
            Label timeLbl = new Label("✅ " + completed + " / " + planned + " min");
            timeLbl.setStyle("-fx-font-weight:bold;-fx-font-size:13;-fx-text-fill:" + rColor + ";");
            Label pctLbl  = new Label(Math.round(ratio * 100) + "% complete");
            pctLbl.setStyle("-fx-text-fill:#475569;-fx-font-size:11;");
            statsRow.getChildren().addAll(timeLbl, pctLbl);

            ProgressBar pb = new ProgressBar(ratio);
            pb.setMaxWidth(Double.MAX_VALUE);
            pb.setStyle("-fx-accent:" + rColor + ";-fx-pref-height:5;");

            card.getChildren().addAll(statsRow, pb);
            historyContainer.getChildren().add(card);
        }
    }

    private String fmtDate(String raw) {
        if (raw == null) return "—";
        return raw.length() > 10 ? raw.substring(0, 10) : raw;
    }
    private String nvl(String s, String def) { return (s == null || s.isBlank()) ? def : s; }

    // ════════════════════════════════════════════
    // LEADERBOARD — dark bluish cards, visible names
    // ════════════════════════════════════════════
    private void loadLeaderboard() {
        if (leaderboardContainer == null) return;
        new Thread(() -> {
            var board = roomService.getGlobalLeaderboard();
            Platform.runLater(() -> {
                leaderboardContainer.getChildren().clear();
                if (board.isEmpty()) {
                    Label e = new Label("Leaderboard is empty — be the first! 🏆");
                    e.setStyle("-fx-text-fill:#475569;-fx-font-size:13;-fx-padding:20 0;");
                    leaderboardContainer.getChildren().add(e);
                    return;
                }
                int rank = 1;
                for (String[] row : board) {
                    leaderboardContainer.getChildren().add(buildLeaderRow(rank, row));
                    rank++;
                }
            });
        }).start();
    }

    private HBox buildLeaderRow(int rank, String[] row) {
        HBox box = new HBox(14);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setPadding(new Insets(13, 18, 13, 18));

        String cardBg, border, glow;
        switch (rank) {
            case 1 -> { cardBg="#171d12"; border="rgba(251,191,36,0.3)";  glow="rgba(251,191,36,0.18)"; }
            case 2 -> { cardBg="#141822"; border="rgba(148,163,184,0.25)"; glow="rgba(148,163,184,0.1)"; }
            case 3 -> { cardBg="#1a1410"; border="rgba(234,88,12,0.25)";  glow="rgba(234,88,12,0.1)"; }
            default-> { cardBg="#111325"; border="rgba(99,102,241,0.15)";  glow="rgba(99,102,241,0.08)"; }
        }
        box.setStyle("-fx-background-color:" + cardBg + ";-fx-background-radius:12;" +
            "-fx-border-color:" + border + ";-fx-border-radius:12;" +
            "-fx-effect:dropshadow(gaussian," + glow + ",12,0,0,2);");

        // Rank badge
        Label rankLbl = new Label(rank <= 3
            ? (rank == 1 ? "🥇" : rank == 2 ? "🥈" : "🥉")
            : "#" + rank);
        rankLbl.setStyle("-fx-font-weight:bold;-fx-font-size:" + (rank <= 3 ? "24" : "15") +
            ";-fx-text-fill:" + (rank == 1 ? "#fbbf24" : rank == 2 ? "#94a3b8"
                              : rank == 3 ? "#ea580c" : "#475569") + ";");
        rankLbl.setMinWidth(38);

        // Avatar circle with initials
        String name = row[0] != null ? row[0] : "?";
        Circle avatar = new Circle(18, Color.web(rankColor(rank)));
        Label initials = new Label(name.substring(0, Math.min(2, name.length())).toUpperCase());
        initials.setStyle("-fx-text-fill:white;-fx-font-weight:bold;-fx-font-size:11;");
        StackPane avatarPane = new StackPane(avatar, initials);

        // NAME — must be clearly visible white/light
        Label nameLbl = new Label(name);
        nameLbl.setStyle("-fx-font-weight:bold;-fx-font-size:14;-fx-text-fill:#e2e8f0;");
        HBox.setHgrow(nameLbl, Priority.ALWAYS);

        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);

        // Streak badge
        Label streakB = new Label("🔥 " + row[2] + " days");
        streakB.setStyle("-fx-background-color:rgba(239,68,68,0.1);-fx-text-fill:#f87171;" +
            "-fx-background-radius:20;-fx-padding:4 12;-fx-font-size:12;-fx-font-weight:bold;");

        // XP badge
        Label xpB = new Label("⭐ " + row[1] + " XP");
        xpB.setStyle("-fx-background-color:rgba(124,58,237,0.12);-fx-text-fill:#c084fc;" +
            "-fx-background-radius:20;-fx-padding:4 12;-fx-font-size:12;-fx-font-weight:bold;");

        box.getChildren().addAll(rankLbl, avatarPane, nameLbl, sp, streakB, xpB);
        return box;
    }

    private String rankColor(int r) {
        String[] c = {"#f59e0b","#94a3b8","#ea580c","#6366f1","#10b981",
                       "#06b6d4","#ec4899","#8b5cf6","#f97316","#14b8a6"};
        return c[Math.min(r - 1, c.length - 1)];
    }

    // ════════════════════════════════════════════
    // STOP TIMERS
    // ════════════════════════════════════════════
    private void stopAllTimers() {
        if (pomodoroTimer     != null) { pomodoroTimer.stop();     pomodoroTimer = null; }
        if (boardRefreshTimer != null) { boardRefreshTimer.stop(); boardRefreshTimer = null; }
        if (chatRefreshTimer  != null) { chatRefreshTimer.stop();  chatRefreshTimer = null; }
    }

    // ════════════════════════════════════════════
    // STYLE HELPERS
    // ════════════════════════════════════════════
    private String inputStyle() {
        return "-fx-background-color:#1a1e35;-fx-text-fill:#e2e8f0;" +
               "-fx-prompt-text-fill:#334155;-fx-background-radius:8;" +
               "-fx-border-color:rgba(99,102,241,0.22);-fx-border-radius:8;-fx-padding:9 12;";
    }
    private String btnPrimary(String color) {
        return "-fx-background-color:" + color + ";-fx-text-fill:white;-fx-font-weight:bold;" +
               "-fx-background-radius:8;-fx-cursor:hand;-fx-padding:9 22;-fx-font-size:13;";
    }
    private String btnSecondary() {
        return "-fx-background-color:#1e2235;-fx-text-fill:#94a3b8;" +
               "-fx-background-radius:8;-fx-border-color:rgba(99,102,241,0.18);" +
               "-fx-border-radius:8;-fx-cursor:hand;-fx-padding:9 22;-fx-font-size:13;";
    }
    private String btnGhost(String text, String border) {
        return "-fx-background-color:transparent;-fx-text-fill:" + text + ";" +
               "-fx-border-color:" + border + ";-fx-border-radius:20;-fx-background-radius:20;" +
               "-fx-cursor:hand;-fx-padding:6 16;-fx-font-size:12;";
    }

    // ════════════════════════════════════════════
    // TABLE CELL HELPERS
    // ════════════════════════════════════════════
    private <T> javafx.beans.property.SimpleStringProperty prop(String s) {
        return new javafx.beans.property.SimpleStringProperty(s != null ? s : "");
    }
    private <T> TableCell<T, String> darkCell(String color, boolean bold) {
        return new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setStyle("-fx-background-color:transparent;"); return; }
                setText(item);
                setStyle("-fx-text-fill:" + color + ";-fx-background-color:transparent;" +
                    (bold ? "-fx-font-weight:bold;" : "") + "-fx-font-size:12;");
            }
        };
    }
    private Label darkPlaceholder(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-text-fill:#334155;-fx-font-size:12;");
        return l;
    }
}