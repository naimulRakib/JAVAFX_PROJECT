package com.scholar.controller;

import com.scholar.model.StudyRoom;
import com.scholar.model.StudySession;
import com.scholar.service.AuthService;
import com.scholar.service.StudyRoomService;
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
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.util.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * STUDY ROOM CONTROLLER — Public & Private Rooms
 * Path: src/main/java/com/scholar/controller/StudyRoomController.java
 */
@Controller
public class StudyRoomController {

    @Autowired private StudyRoomService roomService;

    // ── FXML fields (wired from study_room.fxml) ────────────
    @FXML private VBox   publicRoomList;
    @FXML private VBox   roomContentPane;
    @FXML private Label  streakLabel;
    @FXML private Label  xpLabel;
    @FXML private VBox   leaderboardContainer;
    @FXML private VBox   historyContainer;
    @FXML private BarChart<String, Number>   weeklyChart;
    @FXML private PieChart                   topicPieChart;
    @FXML private CategoryAxis               weeklyXAxis;
    @FXML private NumberAxis                 weeklyYAxis;

    // ── Session state ────────────────────────────────────────
    private StudyRoom   currentRoom;
    private String      currentParticipantId = null;
    private Timeline    pomodoroTimer;
    private Timeline    boardRefreshTimer;
    private Timeline    chatRefreshTimer;
    private AtomicInteger secondsRemaining = new AtomicInteger(0);
    private int         totalSessionSeconds = 0;

    // ── UI references created programmatically ───────────────
    private VBox    chatBox;
    private ScrollPane chatScroll;
    private TableView<StudySession> boardTable;
    private Label   timerLabel;
    private Button  modeToggleBtn;
    private VBox    activeUsersList;

    @FXML
    public void initialize() {
        loadStreakAndXP();
        loadPublicRooms();
        loadHistory();
        loadAnalytics();
        loadLeaderboard(); // Automatically load the global leaderboard
    }

    private void loadStreakAndXP() {
        new Thread(() -> {
            int streak = roomService.getStreak(AuthService.CURRENT_USER_ID.toString());
            Platform.runLater(() -> {
                if (streakLabel != null)
                    streakLabel.setText("🔥 " + streak + " Day Streak");
            });
        }).start();
    }

    public void loadPublicRooms() {
        if (publicRoomList == null) return;
        publicRoomList.getChildren().clear();

        new Thread(() -> {
            List<StudyRoom> rooms = roomService.getPublicRooms();
            Platform.runLater(() -> {
                if (rooms.isEmpty()) {
                    Label empty = new Label("No active public rooms yet.\nCreate one to get started!");
                    empty.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 13px; -fx-text-alignment: center;");
                    empty.setWrapText(true);
                    publicRoomList.getChildren().add(empty);
                    return;
                }
                for (StudyRoom room : rooms) {
                    publicRoomList.getChildren().add(buildRoomCard(room));
                }
            });
        }).start();
    }

    private VBox buildRoomCard(StudyRoom room) {
        VBox card = new VBox(8);
        card.setPadding(new Insets(16));
        card.setStyle("""
            -fx-background-color: white;
            -fx-background-radius: 14;
            -fx-border-color: #e8eeff;
            -fx-border-radius: 14;
            -fx-cursor: hand;
            -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.07), 12, 0, 0, 4);
        """);

        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);

        Circle dot = new Circle(5, Color.web("#10b981")); 

        Label nameLabel = new Label(room.roomName());
        nameLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 15px; -fx-text-fill: #1e293b;");
        nameLabel.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(nameLabel, Priority.ALWAYS);

        Label badge = new Label("PUBLIC");
        badge.setStyle("-fx-background-color: #eff6ff; -fx-text-fill: #2563eb; -fx-background-radius: 8; -fx-padding: 3 10; -fx-font-size: 10px; -fx-font-weight: bold;");

        Label modeBadge = new Label(room.mode().equals("SILENT") ? "🔇 Silent" : "💬 Group");
        modeBadge.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748b;");

        header.getChildren().addAll(dot, nameLabel, modeBadge, badge);

        Button joinBtn = new Button("Join Room →");
        joinBtn.setMaxWidth(Double.MAX_VALUE);
        joinBtn.setStyle("""
            -fx-background-color: linear-gradient(to right, #4f46e5, #7c83fd);
            -fx-text-fill: white; -fx-font-weight: bold;
            -fx-background-radius: 10; -fx-cursor: hand; -fx-padding: 9 0;
            -fx-font-size: 13px;
        """);
        joinBtn.setOnAction(e -> showJoinDialog(room));

        card.setOnMouseEntered(e -> card.setStyle(card.getStyle().replace("-fx-background-color: white", "-fx-background-color: #f8faff")));
        card.setOnMouseExited(e -> card.setStyle(card.getStyle().replace("-fx-background-color: #f8faff", "-fx-background-color: white")));

        card.getChildren().addAll(header, joinBtn);
        return card;
    }

    // ══════════════════════════════════════════════════════════
    // PRIVATE ROOM LAUNCHER
    // ══════════════════════════════════════════════════════════
    @FXML
    public void openMyPrivateRoom() {
        String userId = AuthService.CURRENT_USER_ID.toString();
        new Thread(() -> {
            StudyRoom myRoom = roomService.getMyPrivateRoom(userId);
            if (myRoom == null) {
                // If the user doesn't have a private room yet, create one for them instantly
                String id = roomService.createRoom("My Focus Space", "PRIVATE", userId, "SILENT");
                myRoom = roomService.getMyPrivateRoom(userId);
            }
            StudyRoom finalRoom = myRoom;
            Platform.runLater(() -> {
                if (finalRoom != null) showJoinDialog(finalRoom);
                else showError("Could not launch your private room.");
            });
        }).start();
    }

    private void showJoinDialog(StudyRoom room) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Join: " + room.roomName());
        dialog.setHeaderText("Set your study session details");

        ButtonType joinBtn = new ButtonType("Enter Room 🚀", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(joinBtn, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(15); grid.setVgap(15); grid.setPadding(new Insets(20));

        TextField topicField = new TextField();
        topicField.setPromptText("What are you studying? e.g. Calculus");
        topicField.setStyle("-fx-padding: 10; -fx-background-radius: 8; -fx-border-color: #e2e8f0; -fx-border-radius: 8;");

        TextArea taskField = new TextArea();
        taskField.setPromptText("Describe your task goal for this session...");
        taskField.setPrefRowCount(3); taskField.setWrapText(true);
        taskField.setStyle("-fx-background-radius: 8; -fx-border-color: #e2e8f0; -fx-border-radius: 8;");

        Spinner<Integer> timerSpinner = new Spinner<>(5, 120, 25, 5);
        timerSpinner.setEditable(true);
        timerSpinner.setStyle("-fx-background-radius: 8;");

        grid.add(new Label("📚 Study Topic:"), 0, 0);  grid.add(topicField, 1, 0);
        grid.add(new Label("🎯 Task Goal:"), 0, 1);    grid.add(taskField, 1, 1);
        grid.add(new Label("⏱ Timer (mins):"), 0, 2); grid.add(timerSpinner, 1, 2);
        dialog.getDialogPane().setContent(grid);

        dialog.showAndWait().ifPresent(response -> {
            if (response == joinBtn && !topicField.getText().trim().isEmpty()) {
                enterRoom(room, topicField.getText().trim(),
                    taskField.getText().trim(), timerSpinner.getValue());
            }
        });
    }

    private void enterRoom(StudyRoom room, String topic, String task, int minutes) {
        stopAllTimers();
        this.currentRoom = room;
        totalSessionSeconds = minutes * 60;
        secondsRemaining.set(totalSessionSeconds);

        String pid = roomService.joinRoom(room.id(), AuthService.CURRENT_USER_ID.toString(),
            AuthService.CURRENT_USER_NAME, topic, task, minutes);
        currentParticipantId = pid;

        if ("PUBLIC".equals(room.type()))
            new Thread(() -> roomService.addXP(AuthService.CURRENT_USER_ID.toString(), 5)).start();

        Platform.runLater(() -> buildRoomUI(room, topic, task, minutes));
    }

    private void buildRoomUI(StudyRoom room, String topic, String task, int minutes) {
        if (roomContentPane == null) return;
        roomContentPane.getChildren().clear();

        HBox topBar = new HBox(14);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setPadding(new Insets(14, 20, 14, 20));
        topBar.setStyle("""
            -fx-background-color: linear-gradient(to right, #1a1a2e, #16213e);
            -fx-background-radius: 14 14 0 0;
        """);

        Label roomNameLbl = new Label("📖 " + room.roomName() + ("PRIVATE".equals(room.type()) ? " (Private)" : ""));
        roomNameLbl.setStyle("-fx-font-weight: bold; -fx-font-size: 18px; -fx-text-fill: white;");

        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);

        modeToggleBtn = new Button(room.mode().equals("SILENT") ? "🔇 Silent" : "💬 Group");
        modeToggleBtn.setStyle("""
            -fx-background-color: rgba(124,131,253,0.2);
            -fx-text-fill: #7c83fd;
            -fx-border-color: rgba(124,131,253,0.4);
            -fx-border-radius: 20; -fx-background-radius: 20;
            -fx-cursor: hand; -fx-padding: 6 16;
        """);
        modeToggleBtn.setOnAction(e -> toggleRoomMode(room));

        Button leaveBtn = new Button("Leave ✗");
        leaveBtn.setStyle("""
            -fx-background-color: rgba(239,68,68,0.2);
            -fx-text-fill: #ef4444;
            -fx-border-color: rgba(239,68,68,0.4);
            -fx-border-radius: 20; -fx-background-radius: 20;
            -fx-cursor: hand; -fx-padding: 6 16;
        """);
        leaveBtn.setOnAction(e -> leaveCurrentRoom());

        topBar.getChildren().addAll(roomNameLbl, spacer, modeToggleBtn, leaveBtn);

        SplitPane split = new SplitPane();
        split.setDividerPositions(0.22, 0.60);
        VBox.setVgrow(split, Priority.ALWAYS);

        VBox leftPane = buildActiveUsersPane();
        VBox centerPane = buildCenterPane(topic, task, minutes, room);
        VBox rightPane = buildChatPane(room);

        split.getItems().addAll(leftPane, centerPane, rightPane);
        roomContentPane.getChildren().addAll(topBar, split);

        startPomodoroTimer(minutes);
        startBoardRefresh(room.id());
        if ("GROUP".equals(room.mode()) && "PUBLIC".equals(room.type())) {
            startChatRefresh(room.id());
        }
    }

    private VBox buildActiveUsersPane() {
        VBox pane = new VBox(12);
        pane.setPadding(new Insets(16));
        pane.setStyle("-fx-background-color: #f8faff;");

        Label title = new Label("👥 Active Users");
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: #1e293b;");

        activeUsersList = new VBox(8);
        ScrollPane scroll = new ScrollPane(activeUsersList);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent; -fx-border-color: transparent;");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        pane.getChildren().addAll(title, scroll);
        return pane;
    }

    private VBox buildCenterPane(String topic, String task, int minutes, StudyRoom room) {
        VBox pane = new VBox(16);
        pane.setPadding(new Insets(20));
        pane.setStyle("-fx-background-color: white;");

        VBox timerCard = new VBox(8);
        timerCard.setAlignment(Pos.CENTER);
        timerCard.setPadding(new Insets(24));
        timerCard.setStyle("""
            -fx-background-color: linear-gradient(to bottom right, #f0f4ff, #fff0f6);
            -fx-background-radius: 16;
            -fx-border-color: #e8eeff; -fx-border-radius: 16;
            -fx-effect: dropshadow(gaussian, rgba(79,70,229,0.12), 16, 0, 0, 5);
        """);

        Label topicLbl = new Label("📚 " + topic);
        topicLbl.setStyle("-fx-font-weight: bold; -fx-font-size: 15px; -fx-text-fill: #4f46e5;");

        timerLabel = new Label(formatTime(minutes * 60));
        timerLabel.setStyle("-fx-font-size: 58px; -fx-font-weight: bold; -fx-text-fill: #1e293b;");
        timerLabel.setFont(Font.font("System", FontWeight.BOLD, 58));

        Label taskLbl = new Label("🎯 " + (task.isEmpty() ? "No specific task set" : task));
        taskLbl.setWrapText(true);
        taskLbl.setStyle("-fx-font-size: 13px; -fx-text-fill: #64748b; -fx-text-alignment: center;");

        HBox timerControls = new HBox(10);
        timerControls.setAlignment(Pos.CENTER);

        Button pauseBtn = new Button("⏸ Pause");
        pauseBtn.setStyle(btnStyle("#fef3c7", "#d97706", "#fde68a"));
        pauseBtn.setOnAction(e -> {
            if (pomodoroTimer != null) {
                if (pomodoroTimer.getStatus() == Timeline.Status.RUNNING) {
                    pomodoroTimer.pause(); pauseBtn.setText("▶ Resume");
                } else {
                    pomodoroTimer.play(); pauseBtn.setText("⏸ Pause");
                }
            }
        });

        Button finishBtn = new Button("✅ Finish Early");
        finishBtn.setStyle(btnStyle("#f0fdf4", "#16a34a", "#bbf7d0"));
        finishBtn.setOnAction(e -> finishSession(true));

        timerControls.getChildren().addAll(pauseBtn, finishBtn);
        timerCard.getChildren().addAll(topicLbl, timerLabel, taskLbl, timerControls);

        Label boardTitle = new Label("📊 Live Study Board");
        boardTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: #1e293b;");

        boardTable = buildStudyBoardTable();
        VBox.setVgrow(boardTable, Priority.ALWAYS);

        pane.getChildren().addAll(timerCard, boardTitle, boardTable);
        return pane;
    }

    @SuppressWarnings("unchecked")
    private TableView<StudySession> buildStudyBoardTable() {
        TableView<StudySession> table = new TableView<>();
        table.setStyle("-fx-border-color: #e8eeff; -fx-border-radius: 10; -fx-background-radius: 10;");

        TableColumn<StudySession, String> colUser   = new TableColumn<>("👤 User");
        TableColumn<StudySession, String> colTopic  = new TableColumn<>("📚 Topic");
        TableColumn<StudySession, String> colTask   = new TableColumn<>("🎯 Task");
        TableColumn<StudySession, String> colTime   = new TableColumn<>("⏱ Timer");
        TableColumn<StudySession, String> colStatus = new TableColumn<>("Status");

        colUser.setCellValueFactory(d  -> new javafx.beans.property.SimpleStringProperty(d.getValue().userName()));
        colTopic.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().topic()));
        colTask.setCellValueFactory(d  -> new javafx.beans.property.SimpleStringProperty(d.getValue().taskDescription()));
        colTime.setCellValueFactory(d  -> new javafx.beans.property.SimpleStringProperty(d.getValue().timerDuration() + " min"));
        colStatus.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().completionStatus()));

        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setStyle(""); return; }
                setText(switch (item) {
                    case "COMPLETED" -> "✅ Done";
                    case "ABANDONED" -> "❌ Left";
                    default          -> "🟢 Studying";
                });
                setStyle(switch (item) {
                    case "COMPLETED" -> "-fx-text-fill: #16a34a; -fx-font-weight: bold;";
                    case "ABANDONED" -> "-fx-text-fill: #ef4444; -fx-font-weight: bold;";
                    default          -> "-fx-text-fill: #2563eb; -fx-font-weight: bold;";
                });
            }
        });

        colUser.setPrefWidth(110); colTopic.setPrefWidth(160);
        colTask.setPrefWidth(200); colTime.setPrefWidth(80); colStatus.setPrefWidth(100);
        table.getColumns().addAll(colUser, colTopic, colTask, colTime, colStatus);
        return table;
    }

    private VBox buildChatPane(StudyRoom room) {
        VBox pane = new VBox(10);
        pane.setPadding(new Insets(16));
        pane.setStyle("-fx-background-color: #f8faff;");

        Label title = new Label("💬 Room Chat");
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: #1e293b;");

        chatBox = new VBox(8);
        chatScroll = new ScrollPane(chatBox);
        chatScroll.setFitToWidth(true);
        chatScroll.setStyle("-fx-background-color: transparent; -fx-border-color: transparent;");
        VBox.setVgrow(chatScroll, Priority.ALWAYS);

        TextField msgInput = new TextField();
        msgInput.setPromptText("Type a message...");
        msgInput.setStyle("-fx-background-radius: 20; -fx-padding: 9 14; -fx-border-color: #e2e8f0; -fx-border-radius: 20;");
        HBox.setHgrow(msgInput, Priority.ALWAYS);

        boolean isSilent = "SILENT".equals(room.mode()) || "PRIVATE".equals(room.type());
        msgInput.setDisable(isSilent);
        msgInput.setPromptText(isSilent ? "🔇 Silent mode / Private room" : "Type a message...");

        Button sendBtn = new Button("Send");
        sendBtn.setDisable(isSilent);
        sendBtn.setStyle(btnStyle("#eef2ff", "#4f46e5", "#c7d2fe"));
        sendBtn.setOnAction(e -> {
            String msg = msgInput.getText().trim();
            if (!msg.isEmpty()) {
                new Thread(() -> roomService.sendChatMessage(room.id(),
                    AuthService.CURRENT_USER_ID.toString(), AuthService.CURRENT_USER_NAME, msg)).start();
                msgInput.clear();
            }
        });
        msgInput.setOnAction(e -> sendBtn.fire());

        HBox inputRow = new HBox(8, msgInput, sendBtn);
        inputRow.setAlignment(Pos.CENTER);

        pane.getChildren().addAll(title, chatScroll, inputRow);
        return pane;
    }

    private void startPomodoroTimer(int minutes) {
        secondsRemaining.set(minutes * 60);
        pomodoroTimer = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            int remaining = secondsRemaining.decrementAndGet();
            Platform.runLater(() -> {
                if (timerLabel != null) timerLabel.setText(formatTime(remaining));
            });
            if (remaining <= 0) {
                pomodoroTimer.stop();
                Platform.runLater(() -> finishSession(false));
            }
        }));
        pomodoroTimer.setCycleCount(Timeline.INDEFINITE);
        pomodoroTimer.play();
    }

    private String formatTime(int totalSeconds) {
        int m = totalSeconds / 60;
        int s = totalSeconds % 60;
        return String.format("%02d:%02d", m, s);
    }

    private void finishSession(boolean early) {
        stopAllTimers();
        int elapsed = (totalSessionSeconds - secondsRemaining.get()) / 60;
        int xpEarned = elapsed * 2 + 20; 

        if (currentParticipantId != null && currentRoom != null) {
            new Thread(() -> {
                roomService.completeSession(currentParticipantId, elapsed);
                roomService.saveHistory(AuthService.CURRENT_USER_ID.toString(), currentRoom.id(),
                    "", "", totalSessionSeconds / 60, elapsed, xpEarned);
                roomService.addXP(AuthService.CURRENT_USER_ID.toString(), xpEarned);
                roomService.updateStreak(AuthService.CURRENT_USER_ID.toString());
                Platform.runLater(() -> {
                    loadStreakAndXP();
                    showCompletionDialog(elapsed, xpEarned);
                    showRoomSelector();
                });
            }).start();
        }
        currentParticipantId = null; 
    }

    private void leaveCurrentRoom() {
        if (currentRoom == null) return;
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
            "Leave the room? Your progress will be marked as abandoned.", ButtonType.YES, ButtonType.NO);
        confirm.showAndWait().ifPresent(r -> {
            if (r == ButtonType.YES) {
                stopAllTimers();
                if (currentParticipantId != null) { 
                    new Thread(() -> roomService.leaveRoom(
                        currentRoom.id(), AuthService.CURRENT_USER_ID.toString())).start();
                }
                currentParticipantId = null;
                currentRoom = null;
                showRoomSelector();
            }
        });
    }

    private void showCompletionDialog(int elapsed, int xp) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("🎉 Session Complete!");
        alert.setHeaderText("Amazing work! You studied for " + elapsed + " minutes.");
        alert.setContentText("✅ Completed\n🏆 +" + xp + " XP earned\n🔥 Streak updated!");
        alert.show();
    }

    private void showRoomSelector() {
        if (roomContentPane == null) return;
        roomContentPane.getChildren().clear();
        VBox placeholder = new VBox(16);
        placeholder.setAlignment(Pos.CENTER);
        placeholder.setPadding(new Insets(60));
        Label lbl = new Label("Select or create a study room to begin 📖");
        lbl.setStyle("-fx-font-size: 16px; -fx-text-fill: #94a3b8;");
        placeholder.getChildren().add(lbl);
        roomContentPane.getChildren().add(placeholder);
        loadPublicRooms();
    }

    private void toggleRoomMode(StudyRoom room) {
        if (!room.createdBy().equals(AuthService.CURRENT_USER_ID.toString())
            && !"admin".equals(AuthService.CURRENT_USER_ROLE)) {
            showError("Only the room creator can toggle mode.");
            return;
        }
        String newMode = "GROUP".equals(room.mode()) ? "SILENT" : "GROUP";
        new Thread(() -> {
            roomService.toggleMode(room.id(), newMode);
            Platform.runLater(() -> {
                if (modeToggleBtn != null)
                    modeToggleBtn.setText("SILENT".equals(newMode) ? "🔇 Silent" : "💬 Group");
                showSuccess("Mode switched to " + newMode);
            });
        }).start();
    }

    // ══════════════════════════════════════════════════════════
    // CREATE ROOM DIALOG
    // ══════════════════════════════════════════════════════════
    @FXML
    public void showCreateRoomDialog() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("🏠 Create Study Room");
        dialog.setHeaderText("Set up your study room");

        ButtonType createBtn = new ButtonType("Create Room ✨", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(createBtn, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(15); grid.setVgrow(grid, Priority.ALWAYS);
        grid.setVgap(15); grid.setPadding(new Insets(20));

        TextField nameField = new TextField(); nameField.setPromptText("Room name (e.g. Code Sprint 🚀)");
        ComboBox<String> typeBox = new ComboBox<>();
        typeBox.getItems().addAll("PUBLIC", "PRIVATE"); 
        typeBox.setValue("PUBLIC");
        
        ComboBox<String> modeBox = new ComboBox<>();
        modeBox.getItems().addAll("GROUP", "SILENT"); modeBox.setValue("GROUP");

        grid.add(new Label("Room Name:"), 0, 0); grid.add(nameField, 1, 0);
        grid.add(new Label("Type:"),      0, 1); grid.add(typeBox, 1, 1);
        grid.add(new Label("Mode:"),      0, 2); grid.add(modeBox, 1, 2);
        dialog.getDialogPane().setContent(grid);

        dialog.showAndWait().ifPresent(response -> {
            if (response == createBtn && !nameField.getText().trim().isEmpty()) {
                new Thread(() -> {
                    String id = roomService.createRoom(
                        nameField.getText().trim(), typeBox.getValue(),
                        AuthService.CURRENT_USER_ID.toString(), modeBox.getValue());
                    Platform.runLater(() -> {
                        if (id != null) { loadPublicRooms(); showSuccess("Room created! 🎉"); }
                        else showError("Failed to create room.");
                    });
                }).start();
            }
        });
    }

    private void startBoardRefresh(String roomId) {
        boardRefreshTimer = new Timeline(new KeyFrame(Duration.seconds(4), e -> {
            new Thread(() -> {
                List<StudySession> sessions = roomService.getAllSessions(roomId);
                Platform.runLater(() -> {
                    if (boardTable != null)
                        boardTable.getItems().setAll(sessions);
                    if (activeUsersList != null) {
                        activeUsersList.getChildren().clear();
                        for (StudySession s : sessions) {
                            if ("STUDYING".equals(s.completionStatus())) {
                                HBox row = new HBox(8);
                                row.setAlignment(Pos.CENTER_LEFT);
                                Circle dot = new Circle(5, Color.web("#10b981"));
                                Label name = new Label(s.userName());
                                name.setStyle("-fx-font-size: 13px; -fx-text-fill: #1e293b;");
                                Label topic = new Label("• " + s.topic());
                                topic.setStyle("-fx-font-size: 11px; -fx-text-fill: #94a3b8;");
                                topic.setMaxWidth(120); topic.setWrapText(true);
                                VBox info = new VBox(1, name, topic);
                                row.getChildren().addAll(dot, info);
                                activeUsersList.getChildren().add(row);
                            }
                        }
                    }
                });
            }).start();
        }));
        boardRefreshTimer.setCycleCount(Timeline.INDEFINITE);
        boardRefreshTimer.play();
    }

    private void startChatRefresh(String roomId) {
        chatRefreshTimer = new Timeline(new KeyFrame(Duration.seconds(3), e -> {
            new Thread(() -> {
                List<StudyRoomService.ChatMessage> msgs = roomService.getChatMessages(roomId);
                Platform.runLater(() -> {
                    if (chatBox == null) return;
                    chatBox.getChildren().clear();
                    for (var msg : msgs) {
                        boolean isMe = msg.sender().equals(AuthService.CURRENT_USER_NAME);
                        HBox bubble = new HBox();
                        bubble.setAlignment(isMe ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
                        Label lbl = new Label(msg.sender() + ": " + msg.content());
                        lbl.setWrapText(true); lbl.setMaxWidth(200);
                        lbl.setStyle(isMe
                            ? "-fx-background-color: #eef2ff; -fx-text-fill: #4f46e5; -fx-padding: 8 12; -fx-background-radius: 12 12 2 12;"
                            : "-fx-background-color: #f1f5f9; -fx-text-fill: #334155; -fx-padding: 8 12; -fx-background-radius: 12 12 12 2;");
                        bubble.getChildren().add(lbl);
                        chatBox.getChildren().add(bubble);
                    }
                    if (chatScroll != null) chatScroll.setVvalue(1.0);
                });
            }).start();
        }));
        chatRefreshTimer.setCycleCount(Timeline.INDEFINITE);
        chatRefreshTimer.play();
    }

    private void stopAllTimers() {
        if (pomodoroTimer    != null) pomodoroTimer.stop();
        if (boardRefreshTimer != null) boardRefreshTimer.stop();
        if (chatRefreshTimer  != null) chatRefreshTimer.stop();
    }

    private void loadAnalytics() {
        new Thread(() -> {
            Map<String, Integer> weekly = roomService.getWeeklyStats(AuthService.CURRENT_USER_ID.toString());
            Map<String, Integer> topics = roomService.getTopicStats(AuthService.CURRENT_USER_ID.toString());
            Platform.runLater(() -> {
                if (weeklyChart != null) {
                    weeklyChart.getData().clear();
                    XYChart.Series<String, Number> series = new XYChart.Series<>();
                    series.setName("Study Minutes");
                    weekly.forEach((day, mins) -> series.getData().add(
                        new XYChart.Data<>(day, mins)));
                    weeklyChart.getData().add(series);
                }
                if (topicPieChart != null) {
                    topicPieChart.getData().clear();
                    topics.forEach((topic, mins) ->
                        topicPieChart.getData().add(new PieChart.Data(topic, mins)));
                }
            });
        }).start();
    }

    private void loadHistory() {
        if (historyContainer == null) return;
        new Thread(() -> {
            var history = roomService.getHistory(AuthService.CURRENT_USER_ID.toString());
            Platform.runLater(() -> {
                historyContainer.getChildren().clear();
                for (var h : history) {
                    HBox row = new HBox(14);
                    row.setPadding(new Insets(10, 14, 10, 14));
                    row.setStyle("-fx-background-color: white; -fx-background-radius: 10; " +
                        "-fx-border-color: #e8eeff; -fx-border-radius: 10;");
                    Label dateLbl = new Label("📅 " + h.date());
                    dateLbl.setStyle("-fx-text-fill: #64748b; -fx-min-width: 90;");
                    Label topicLbl = new Label("📚 " + h.topic());
                    topicLbl.setStyle("-fx-font-weight: bold; -fx-max-width: 200;");
                    topicLbl.setWrapText(true);
                    Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
                    Label timeLbl = new Label("⏱ " + h.completedTime() + "/" + h.plannedTime() + " min");
                    timeLbl.setStyle("-fx-text-fill: #16a34a; -fx-font-weight: bold;");
                    row.getChildren().addAll(dateLbl, topicLbl, sp, timeLbl);
                    historyContainer.getChildren().add(row);
                }
                if (history.isEmpty()) {
                    historyContainer.getChildren().add(
                        new Label("No study history yet. Start your first session! 🚀"));
                }
            });
        }).start();
    }

    private void loadLeaderboard() {
        if (leaderboardContainer == null) return;
        new Thread(() -> {
            List<String[]> board = roomService.getGlobalLeaderboard();
            Platform.runLater(() -> {
                leaderboardContainer.getChildren().clear();
                int rank = 1;
                for (String[] row : board) {
                    HBox box = new HBox(15);
                    box.setAlignment(Pos.CENTER_LEFT);
                    box.setPadding(new Insets(12));
                    box.setStyle("-fx-background-color: white; -fx-background-radius: 8; -fx-border-color: #e8eeff; -fx-border-radius: 8;");
                    
                    Label rLbl = new Label("#" + rank);
                    rLbl.setStyle("-fx-font-weight: bold; -fx-font-size: 16px; -fx-text-fill: " + (rank <= 3 ? "#d97706" : "#64748b") + ";");
                    
                    Label nLbl = new Label("👤 " + row[0]);
                    nLbl.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
                    
                    Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
                    
                    Label sLbl = new Label("🔥 " + row[2] + " Streak");
                    sLbl.setStyle("-fx-text-fill: #ef4444; -fx-font-weight: bold; -fx-font-size: 12px;");
                    
                    Label xLbl = new Label("⭐ " + row[1] + " XP");
                    xLbl.setStyle("-fx-text-fill: #8b5cf6; -fx-font-weight: bold; -fx-font-size: 12px;");

                    box.getChildren().addAll(rLbl, nLbl, sp, sLbl, xLbl);
                    leaderboardContainer.getChildren().add(box);
                    rank++;
                }
                if (board.isEmpty()) {
                    leaderboardContainer.getChildren().add(new Label("Leaderboard is empty right now."));
                }
            });
        }).start();
    }

    private String btnStyle(String bg, String text, String border) {
        return "-fx-background-color: " + bg + "; -fx-text-fill: " + text + "; " +
               "-fx-border-color: " + border + "; -fx-border-radius: 10; " +
               "-fx-background-radius: 10; -fx-cursor: hand; -fx-padding: 7 16;";
    }

    private void showSuccess(String msg) {
        Platform.runLater(() -> {
            Alert a = new Alert(Alert.AlertType.INFORMATION); a.setContentText(msg); a.show();
        });
    }
    private void showError(String msg) {
        Platform.runLater(() -> {
            Alert a = new Alert(Alert.AlertType.ERROR); a.setContentText(msg); a.showAndWait();
        });
    }
}