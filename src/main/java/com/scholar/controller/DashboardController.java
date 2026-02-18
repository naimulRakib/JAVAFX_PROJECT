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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import java.io.IOException;
import java.net.URI;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

//Download resource
import javafx.stage.FileChooser;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import javafx.scene.Scene;
import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.time.Duration;



public class DashboardController {

    // ==========================================================
    // SERVICES & STATE VARIABLES
    // ==========================================================
    private final AISchedulerService aiService = new AISchedulerService();
    private final ResourceService resourceService = new ResourceService();
    private final CourseService courseService = new CourseService();
    private final DataService dataService = new DataService();
    private final CollaborationService collaborationService = new CollaborationService();
    private final ECAService ecaService = new ECAService();
    private final ChannelService channelService = new ChannelService();

    // যেহেতু একই প্যাকেজে আছে, তাই "controller." লেখার দরকার নেই
private final AIController aiController = new AIController();


    private final List<StudyTask> allTasks = new ArrayList<>();
    private LocalDate selectedDate = LocalDate.now();
    private VBox lastUsedResourceContainer;

    // TreeView Tracking Maps
    private final java.util.Map<TreeItem<String>, Integer> courseMap = new java.util.HashMap<>();
    private final java.util.Map<TreeItem<String>, Integer> segmentMap = new java.util.HashMap<>();
    private final java.util.Map<TreeItem<String>, Integer> topicMap = new java.util.HashMap<>();
    private Integer currentSelectedTopicId = null;

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

    // AI Tutor Tab Elements
    @FXML private javafx.scene.web.WebView aiWebView;
    @FXML private javafx.scene.control.TextField aiTopicInput;
    @FXML private javafx.scene.control.TextField aiQuestionInput;
    @FXML private javafx.scene.control.Button aiAskBtn;

// complete button backlog button
// 🌟 FXML Buttons for Task Views (SceneBuilder থেকে লিঙ্ক করে নেবেন)
    @FXML private Button btnDailyTasks;
    @FXML private Button btnBacklog;
    @FXML private Button btnCompleted;

    // 🌟 বর্তমান ভিউ ট্র্যাক করার জন্য একটি ভেরিয়েবল
    private String currentViewMode = "DAILY"; // 3 modes: DAILY, BACKLOG, COMPLETED



     @FXML
    private javafx.scene.control.Button aiTutorBtn;

    // --- Tab 2: Calendar ---
    @FXML private Label monthLabel;
    @FXML private GridPane calendarGrid;

    // --- Tab 3: Resources ---
    @FXML private TextField resTitle;
    @FXML private TextArea resInput;
    @FXML private VBox resourceList;

    // --- Tab 4: Community (Modern UI) ---
    @FXML private TreeView<String> communityTree;
    @FXML private Label currentFolderLabel;
    
    @FXML private TableView<ResourceRow> resourceTable; 
    @FXML private TableColumn<ResourceRow, String> colResName;
    @FXML private TableColumn<ResourceRow, String> colResType;
    @FXML private TableColumn<ResourceRow, String> colResDiff; // Difficulty Column
    @FXML private TableColumn<ResourceRow, String> colResUploader;
    @FXML private TableColumn<ResourceRow, String> colResTags;
    @FXML private TableColumn<ResourceRow, String> colResVotes;
    @FXML private TableColumn<ResourceRow, Void> colResAction; 

    // --- Tab 5: Collaboration ---
    @FXML private VBox channelList;
    @FXML private VBox teamList;
    @FXML private VBox roomContainer;

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


    // ==========================================================
    // 🚀 INITIALIZATION
    // ==========================================================
   @FXML
    public void initialize() {
        System.out.println("🚀 ScholarGrid Dashboard Initializing...");

        //routine butons
        new Thread(() -> {
            dataService.autoMoveToBacklog(); 
        }).start();

        if (btnDailyTasks != null) btnDailyTasks.setOnAction(e -> { currentViewMode = "DAILY"; refreshTimeline(); });
        if (btnBacklog != null) btnBacklog.setOnAction(e -> { currentViewMode = "BACKLOG"; refreshTimeline(); });
        if (btnCompleted != null) btnCompleted.setOnAction(e -> { currentViewMode = "COMPLETED"; refreshTimeline(); });


        
       
        if (aiTutorBtn != null) {
            aiTutorBtn.setOnAction(e -> showAITutorPanel()); 
        }

        // 1. Multiverse (Channel) Setup
        if (AuthService.CURRENT_CHANNEL_ID != -1) {
            if (channelNameLabel != null) {
                channelNameLabel.setText(AuthService.CURRENT_CHANNEL_NAME);
            }
            loadClassRoutine();
            loadClassAnnouncements();
        }

        // 2. Admin Security Check
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

        // 3. Setup Community Tree & Table
        if (communityTree != null) {
            setupResourceTable();
            onRefreshCommunity();
        }

        // 4. Load Modules
        drawCalendar();
        loadResources();
        loadChannels();
        loadQuestionBankTab();
        loadProfileSettings();

        // 5. Load Personal Tasks from Cloud
        loadTasksFromDatabase();
       

        // ==========================================================
        // 🤖 6. SETUP AI TUTOR TAB LOGIC (NEWLY ADDED)
        // ==========================================================
        if (aiWebView != null && aiAskBtn != null) {
            
            // ১. ওয়েলকাম মেসেজ সেট করা
            String welcomeHtml = "<html><body style='font-family: Arial, sans-serif; color: #2c3e50; text-align: center; margin-top: 15%;'>" +
                "<h2 style='color: #8e44ad;'>👋 Hello! I am your ScholarGrid AI.</h2>" +
                "<p>Ask me what to study, and I will find the <b>best resources</b> from our database for you!</p>" +
                "</body></html>";
            aiWebView.getEngine().loadContent(welcomeHtml);

    



       aiAskBtn.setOnAction(e -> {
    // ১. ইউজারের কাঁচা ইনপুট নেওয়া
    final String rawQuery = aiQuestionInput.getText().trim(); 
    if (rawQuery.isEmpty()) return;

    // ২. ভেরিয়েবল ডিক্লেয়ার করা (যাতে লাল দাগ না আসে)
    String extractedKeyword; 

    // ৩. Regex ছাঁকনি দিয়ে কোর্স কোড (e.g., CSE 105) বের করা
    java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?i)([a-z]{2,3}\\s*\\d{3})").matcher(rawQuery);
    
    if (m.find()) {
        extractedKeyword = m.group(1); // ব্র্যাকেটের ভেতরের টুকু (CSE 105) নিল
    } else {
        // কোর্স কোড না পেলে বাক্য ছোট করা
        extractedKeyword = rawQuery.toLowerCase()
                                   .replace("i need", "")
                                   .replace("resources", "")
                                   .trim();
    }

    // ৪. UI আপডেট: ইউজারকে জানানো কী সার্চ হচ্ছে
    aiWebView.getEngine().loadContent("<html><body style='font-family:sans-serif; text-align:center; padding-top:20%;'>" +
            "🤖 Searching ScholarGrid for: <b>" + extractedKeyword + "</b>...<br>Ranking by Community Votes...</body></html>");
    aiAskBtn.setDisable(true);

    // ৫. ব্যাকগ্রাউন্ড থ্রেড ব্যবহার করা (যাতে অ্যাপ হ্যাং না করে)
    new Thread(() -> {
        try {
            // 🌟 AIController কল করা (সম্পূর্ণ প্রশ্ন এবং ফিল্টার কি-ওয়ার্ড সহ)
            // এটি এখন systematic ভাবে র‍্যাঙ্ক করা ডাটা আনবে
            String aiHtmlResponse = aiController.askSmartAITutor(rawQuery, extractedKeyword);

            Platform.runLater(() -> {
                aiWebView.getEngine().loadContent(aiHtmlResponse);
                aiAskBtn.setDisable(false);
                aiQuestionInput.clear();
            });
        } catch (Exception ex) {
            Platform.runLater(() -> {
                aiWebView.getEngine().loadContent("<p style='color:red;'>Error: " + ex.getMessage() + "</p>");
                aiAskBtn.setDisable(false);
            });
        }
    }).start();
});



          
         aiWebView.getEngine().locationProperty().addListener((obs, oldLoc, newLoc) -> {
    if (newLoc != null && !newLoc.isEmpty() && newLoc.startsWith("http")) {
        // ইউজারের জন্য ক্লিনিং মেসেজ
        Platform.runLater(() -> {
            aiWebView.getEngine().loadContent("<h3>🌍 Opening Resource in Browser...</h3>");
        });
        
        try {
            // পিসির ডিফল্ট ব্রাউজারে লিংকটি ওপেন করবে
            java.awt.Desktop.getDesktop().browse(new java.net.URI(newLoc));
        } catch (Exception ex) { 
            ex.printStackTrace(); 
        }
    }
});
        }
    }


    private void loadQuestionBankTab() {
        if (questionBankTab == null) return;
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/scholar/view/question_bank.fxml"));
            Node questionBankContent = loader.load();
            questionBankTab.setContent(questionBankContent);
        } catch (IOException e) {
            System.err.println("❌ Failed to load Question Bank Tab");
        }
    }




    
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
                    data.add(new String[]{rs.getString("day_name"), rs.getString("course_code"), rs.getString("time_slot"), rs.getString("room_no")});
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
                    card.setStyle("-fx-background-color: white; -fx-padding: 10; -fx-background-radius: 5; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.1), 5, 0, 0, 0);");
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
                pstmt.setString(1, day); pstmt.setString(2, course); pstmt.setString(3, time); pstmt.setString(4, room); pstmt.setInt(5, AuthService.CURRENT_CHANNEL_ID);
                pstmt.executeUpdate();
                Platform.runLater(this::loadClassRoutine);
            } catch (SQLException e) { e.printStackTrace(); }
        }).start();
    }

// ==========================================================
    // 📢 ADMIN BROADCAST SYSTEM (AI POWERED)
    // ==========================================================

    private void showAddRoutineDialog() {
        showAdminBroadcastDialog(true); // true মানে এটি Weekly Routine
    }

    private void showAddAnnouncementDialog() {
        showAdminBroadcastDialog(false); // false মানে এটি Specific Notice
    }

    // 🌟 ইউনিভার্সাল ডায়ালগ বক্স যা রুটিন এবং নোটিশ দুটোই হ্যান্ডেল করবে
    private void showAdminBroadcastDialog(boolean isRoutine) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(isRoutine ? "📅 Broadcast Varsity Routine" : "📢 Broadcast Official Notice");
        dialog.setHeaderText("Paste raw text or table data. AI will automatically parse and distribute it to all students!");

        ButtonType broadcastBtn = new ButtonType("Broadcast 🚀", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(broadcastBtn, ButtonType.CANCEL);

        // 🌟 একটি মাত্র বিশাল টেক্সট এরিয়া
        TextArea inputArea = new TextArea();
        inputArea.setPromptText(isRoutine ? 
            "E.g. SAT 08:00 CSE105 Room302\nSUN 11:00 MATH143..." : 
            "E.g. Notice: Lab Final on 25th March 10:00 AM at Room 405...");
        inputArea.setPrefRowCount(10);
        inputArea.setPrefColumnCount(40);
        inputArea.setWrapText(true);
        inputArea.setStyle("-fx-font-family: 'Consolas'; -fx-font-size: 14px; -fx-padding: 10;");

        dialog.getDialogPane().setContent(inputArea);

        dialog.showAndWait().ifPresent(response -> {
            if (response == broadcastBtn && !inputArea.getText().trim().isEmpty()) {
                String rawText = inputArea.getText().trim();
                
                // UI আপডেট (লোডিং দেখানো)
                Alert loadingMsg = new Alert(Alert.AlertType.INFORMATION, "🤖 AI is reading and organizing the data... Please wait.");
                loadingMsg.show();

           new Thread(() -> {
                    RoutineManager routineManager = new RoutineManager(); 
                    List<StudyTask> tempTasks; // 🌟 টেম্পোরারি ভেরিয়েবল নিলাম
                    
                    if (isRoutine) {
                        // 📅 রুটিনের ক্ষেত্রে ১২০ দিনের জেনারেটর কল হবে
                        tempTasks = routineManager.processVarsitySchedule(rawText);
                    } else {
                        // 📢 নোটিশের ক্ষেত্রে ইউনিভার্সাল AI পার্সার কল হবে
                        List<StudyTask> rawNotices = aiService.parseAdminNotice(rawText, LocalDate.now().toString());
                        tempTasks = new ArrayList<>();
                        
                       for(StudyTask t : rawNotices) {
                            // 🌟 ফিক্স: এখন মোট ১৩টি প্যারামিটার পাস করা হচ্ছে (Status এবং Importance সহ)
                            tempTasks.add(new StudyTask(
                                null,                 // 1. id
                                t.title(),            // 2. title
                                t.date(),             // 3. date
                                t.startTime(),        // 4. startTime ("Anytime")
                                t.durationMinutes(),  // 5. durationMinutes
                                t.roomNo(),           // 6. roomNo
                                "NOTICE",             // 7. type 
                                t.tags(),             // 8. tags (পুরো নোটিশের মেসেজ)
                                "admin",              // 9. creatorRole
                                null,                 // 10. ctCourse (নোটিশের জন্য null)
                                null,                 // 11. ctSyllabus (নোটিশের জন্য null)
                                null,                 // 12. 🌟 NEW: status (নোটিশের জন্য null)
                                null                  // 13. 🌟 NEW: importance (নোটিশের জন্য null)
                            ));
                        }
                    }

                
                    final List<StudyTask> generatedTasks = tempTasks;

                
                    if (!generatedTasks.isEmpty()) {
                        dataService.saveTasks(generatedTasks);
                    }

                    Platform.runLater(() -> {
                        loadingMsg.close();
                        if (generatedTasks.isEmpty()) {
                            showError("❌ AI couldn't understand the text. Please format it clearly.");
                        } else {
                            
                            allTasks.addAll(generatedTasks);
                            refreshTimeline();
                            drawCalendar();
                            showSuccess("Successfully Broadcasted " + generatedTasks.size() + " items to all students! ⚡");
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
                    row.setStyle("-fx-background-color: white; -fx-padding: 15; -fx-background-radius: 8; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.1), 5, 0, 0, 0);");
                    Label emailLabel = new Label(student[1]); emailLabel.setPrefWidth(300); emailLabel.setFont(Font.font("System", FontWeight.BOLD, 14));
                    Button approveBtn = new Button("Approve ✅"); approveBtn.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-cursor: hand;");
                    approveBtn.setOnAction(e -> handleAction(student[0], "approved"));
                    Button rejectBtn = new Button("Reject ❌"); rejectBtn.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-cursor: hand;");
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
                // 🌟 ১. আপনার আগের কাজ করা AI মেথডটাই কল করছি! (যেটি আগে সফলভাবে ডেটা দিচ্ছিল)
                List<StudyTask> aiGeneratedTasks = aiService.generateSchedule(userText);
                
                // যদি AI বুঝতে না পেরে ফাঁকা ডেটা দেয়, তবে এখানেই আটকে দেবে
                if (aiGeneratedTasks == null || aiGeneratedTasks.isEmpty()) {
                    Platform.runLater(() -> {
                        showError("AI Error ❌: AI couldn't understand the input. Please write clearly!");
                        generateBtn.setText("Generate Routine ⚡"); 
                        generateBtn.setDisable(false);
                    });
                    return;
                }

                // 🌟 ২. AI-এর ডেটাকে জোর করে (Forcefully) "PERSONAL" টাস্কে রূপান্তর করা
                List<StudyTask> finalPersonalTasks = new java.util.ArrayList<>();
                String todayDate = java.time.LocalDate.now().toString();

                for (StudyTask t : aiGeneratedTasks) {
                    // তারিখ না থাকলে আজকের তারিখ বসবে
                    String safeDate = (t.date() != null && t.date().matches("\\d{4}-\\d{2}-\\d{2}")) ? t.date() : todayDate;
                    
                    finalPersonalTasks.add(new StudyTask(
                        null, t.title(), safeDate, t.startTime(), 
                        t.durationMinutes() != null ? t.durationMinutes() : 60, // ডিফল্ট ১ ঘণ্টা
                        t.roomNo(), "PERSONAL", t.tags(), "student", 
                        t.ctCourse(), t.ctSyllabus(), "PENDING", "Medium" // 🌟 Status & Importance
                    ));
                }

                // 🌟 ৩. ডাটাবেসে সেভ করা (১৩ প্যারামিটারের আপডেটেড মেথড দিয়ে)
                boolean isSaved = dataService.saveTasks(finalPersonalTasks);
                
                Platform.runLater(() -> {
                    if (isSaved) {
                        loadTasksFromDatabase(); // 🌟 ডাটাবেস থেকে ফ্রেশ ডেটা লোড করে টাইমলাইন আপডেট
                        showSuccess("Personal Task Added to Timeline! 🎉");
                    } else {
                        showError("Database Error ❌: Failed to save task. Please check Supabase table.");
                    }
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
        
        // ==========================================================
        // 🌟 নতুন সংযোজন: বারের নামগুলো (Day Headers) যোগ করা হলো
        // ==========================================================
        String[] daysOfWeek = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
        for (int i = 0; i < 7; i++) {
            Label dayLabel = new Label(daysOfWeek[i]);
            // সুন্দর স্টাইল: গাঢ় ছাই রঙের বোল্ড টেক্সট
            dayLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #7f8c8d; -fx-font-size: 15px;");
            
            VBox headerBox = new VBox(dayLabel);
            headerBox.setAlignment(Pos.CENTER);
            headerBox.setPadding(new Insets(10, 0, 15, 0)); // নিচে একটু গ্যাপ রাখা হলো
            
            calendarGrid.add(headerBox, i, 0); // Row 0 তে বারের নামগুলো বসবে
        }
        
        // ==========================================================

        int startDay = currentMonth.atDay(1).getDayOfWeek().getValue() % 7;
        int daysInMonth = currentMonth.lengthOfMonth();
        
        // 🌟 ফিক্স: তারিখগুলো এখন Row 1 থেকে শুরু হবে (যেহেতু Row 0 তে বারের নাম আছে)
        int row = 1, col = startDay;
        
        for (int day = 1; day <= daysInMonth; day++) {
            LocalDate cellDate = currentMonth.atDay(day);
            VBox box = createDayBox(day, cellDate); 
            
            calendarGrid.add(box, col, row);
            
            col++; 
            if (col > 6) { 
                col = 0; 
                row++; 
            }
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

        // 🎨 ১. ডিফল্ট স্টাইল (সাদা ব্যাকগ্রাউন্ড)
        String boxStyle = "-fx-background-color: white; -fx-border-color: #ecf0f1; -fx-cursor: hand;";

        // 🌟 ২. আজকের দিনের স্টাইল (নীল ব্যাকগ্রাউন্ড, সাদা টেক্সট)
        if (date.equals(today)) {
            boxStyle = "-fx-background-color: #3498db; -fx-border-color: #2980b9; -fx-background-radius: 5; -fx-border-radius: 5; -fx-cursor: hand;";
            lbl.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: white;");
        }

        // 🎯 ৩. সিলেক্ট করা দিনের স্টাইল (গাঢ় বর্ডার)
        if (date.equals(selectedDate)) {
            if (date.equals(today)) {
                boxStyle = "-fx-background-color: #3498db; -fx-border-color: #e74c3c; -fx-border-width: 2; -fx-background-radius: 5; -fx-border-radius: 5; -fx-cursor: hand;";
            } else {
                boxStyle = "-fx-background-color: #ebf5fb; -fx-border-color: #3498db; -fx-border-width: 2; -fx-background-radius: 5; -fx-border-radius: 5; -fx-cursor: hand;";
            }
        }

        box.setStyle(boxStyle);
        box.getChildren().add(lbl);

        // 🔴 ৪. টাস্ক ইন্ডিকেটর এবং 🌟 Tooltip ম্যাজিক
        // প্রথমে ওই দিনের সব টাস্ক ফিল্টার করে বের করে নিচ্ছি
        List<StudyTask> tasksForThisDay = allTasks.stream()
            .filter(t -> t.date() != null && t.date().equals(date.toString()))
            .collect(java.util.stream.Collectors.toList());

        if (!tasksForThisDay.isEmpty()) {
            // ডট অ্যাড করা
            Circle dot = new Circle(3, Color.web(date.equals(today) ? "#ffffff" : "#e74c3c"));
            box.getChildren().add(dot);

            // 🌟 Tooltip তৈরি করা (Hover করলে টাস্কের নাম দেখাবে)
            StringBuilder tooltipText = new StringBuilder("📌 Tasks:\n");
            for (StudyTask t : tasksForThisDay) {
                tooltipText.append("• ").append(t.title()).append(" (").append(t.startTime()).append(")\n");
            }
            
            javafx.scene.control.Tooltip tooltip = new javafx.scene.control.Tooltip(tooltipText.toString().trim());
            // Tooltip এর ডিজাইন একদম প্রিমিয়াম ডার্ক থিমের মতো করা হলো
            tooltip.setStyle("-fx-font-size: 12px; -fx-background-color: rgba(44, 62, 80, 0.9); -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 8px; -fx-background-radius: 5px;");
            
            // Tooltip টি এই দিনের কার্ডের (VBox) সাথে যুক্ত করে দেওয়া হলো
            javafx.scene.control.Tooltip.install(box, tooltip);
        }

        // 🖱️ ৫. ক্লিক ইভেন্ট
        box.setOnMouseClicked(e -> { 
            selectedDate = date; 
            refreshTimeline(); 
            drawCalendar(); 
        });
        
        return box;
    }



   // DashboardController.java এর refreshTimeline মেথড আপডেট করুন
private void refreshTimeline() {
        if (timelineContainer == null) return;
        timelineContainer.getChildren().clear();
        
        // 🌟 ১. বর্তমান View Mode অনুযায়ী টাস্ক ফিল্টার করা
        List<StudyTask> displayTasks = allTasks.stream()
            .filter(t -> {
                if ("BACKLOG".equals(currentViewMode)) {
                    // 🔴 ব্যাকলগ ভিউ: শুধু ব্যাকলগ টাস্ক দেখাবে (যেকোনো তারিখের)
                    return "PERSONAL".equals(t.type()) && "BACKLOG".equals(t.status());
                } else if ("COMPLETED".equals(currentViewMode)) {
                    // 🟢 কমপ্লিটেড ভিউ: শুধু কমপ্লিটেড টাস্ক দেখাবে
                    return "PERSONAL".equals(t.type()) && "COMPLETED".equals(t.status());
                } else {
                    // 🔵 ডেইলি ভিউ (ডিফল্ট): আজকের টাস্ক দেখাবে, কিন্তু কমপ্লিট/ব্যাকলগগুলো হাইড করবে
                    if (t.date() == null || !t.date().equals(selectedDate.toString())) return false;
                    if ("PERSONAL".equals(t.type())) {
                        return !"COMPLETED".equals(t.status()) && !"BACKLOG".equals(t.status());
                    }
                    return true; // রুটিন বা নোটিশ দেখাবে
                }
            })
            .sorted((t1, t2) -> {
                if ("Anytime".equalsIgnoreCase(t1.startTime())) return -1;
                if ("Anytime".equalsIgnoreCase(t2.startTime())) return 1;
                try {
                    java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("hh:mm a");
                    java.time.LocalTime time1 = java.time.LocalTime.parse(t1.startTime().toUpperCase(), formatter);
                    java.time.LocalTime time2 = java.time.LocalTime.parse(t2.startTime().toUpperCase(), formatter);
                    return time1.compareTo(time2);
                } catch (Exception e) { return 0; }
            })
            .collect(java.util.stream.Collectors.toList());

        // যদি কোনো টাস্ক না থাকে
        if (displayTasks.isEmpty()) {
            Label emptyLbl = new Label("No tasks found in " + currentViewMode + " view.");
            emptyLbl.setStyle("-fx-font-size: 14px; -fx-text-fill: #7f8c8d; -fx-padding: 20;");
            timelineContainer.getChildren().add(emptyLbl);
            return;
        }

        // 🌟 ২. কার্ড রেন্ডারিং (আগের মতোই)
        for (StudyTask task : displayTasks) {
            VBox card = new VBox(5);
            card.setStyle("-fx-background-radius: 8; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 5, 0, 0, 1); -fx-cursor: hand;");

            card.setOnMouseClicked(e -> {
                if (e.getTarget() instanceof Button || e.getTarget() instanceof javafx.scene.text.Text) return; 
                Platform.runLater(() -> showTaskDetailsDialog(task));
            });

            // 🌟 স্পেশাল নোটিশ UI
            if ("NOTICE".equals(task.type())) {
                card.setStyle(card.getStyle() + "-fx-background-color: #fdf2f2; -fx-border-left-color: #e74c3c; -fx-border-left-width: 5; -fx-padding: 15;");
                Label noticeTitle = new Label(task.title()); noticeTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 16px; -fx-text-fill: #c0392b;");
                Label noticeContent = new Label(task.tags()); noticeContent.setWrapText(true); noticeContent.setStyle("-fx-font-size: 14px; -fx-text-fill: #34495e; -fx-padding: 5 0 0 0;");
                card.getChildren().addAll(noticeTitle, noticeContent);
            } 
            // 🌟 রেগুলার/পার্সোনাল/ব্যাকলগ/কমপ্লিটেড UI
            else {
                card.setPadding(new Insets(10));
                card.setStyle(card.getStyle() + "-fx-background-color: white;");
                
                // ভিউ অনুযায়ী কার্ডের রঙ পরিবর্তন
                if ("BACKLOG".equals(currentViewMode)) card.setStyle(card.getStyle() + "-fx-border-left-color: #e74c3c; -fx-border-left-width: 4; -fx-background-color: #fff5f5;");
                else if ("COMPLETED".equals(currentViewMode)) card.setStyle(card.getStyle() + "-fx-border-left-color: #2ecc71; -fx-border-left-width: 4; -fx-background-color: #f4fdf8;");
                else if ("ROUTINE".equals(task.type())) card.setStyle(card.getStyle() + "-fx-border-left-color: #3498db; -fx-border-left-width: 4;");
                else card.setStyle(card.getStyle() + "-fx-border-left-color: #9b59b6; -fx-border-left-width: 4;");

                HBox header = new HBox(10);
                Label timeLbl = new Label("🕒 " + task.startTime() + ("BACKLOG".equals(currentViewMode) ? " (" + task.date() + ")" : ""));
                timeLbl.setStyle("-fx-font-weight: bold; -fx-text-fill: #7f8c8d;");
                Label titleLbl = new Label(task.title());
                titleLbl.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
                header.getChildren().addAll(timeLbl, titleLbl);
                card.getChildren().add(header);

                if (task.roomNo() != null && !task.roomNo().isEmpty() && !task.roomNo().equals("null")) {
                    Label roomLbl = new Label("📍 Room: " + task.roomNo()); roomLbl.setStyle("-fx-text-fill: #34495e; -fx-font-size: 12px;"); card.getChildren().add(roomLbl);
                }
            }

            // 🌟 ৩. সিকিউরিটি এবং ডায়নামিক বাটন
            HBox actionBox = new HBox(10); actionBox.setAlignment(Pos.CENTER_RIGHT);
            boolean isAdmin = "admin".equals(AuthService.CURRENT_USER_ROLE);
            boolean isCreator = task.creatorRole() != null && task.creatorRole().equals(AuthService.CURRENT_USER_ROLE);

            if ("PERSONAL".equals(task.type())) {
                if ("DAILY".equals(currentViewMode)) {
                    // ✅ Daily View তে Done বাটন
                    Button completeBtn = new Button("✅ Done");
                    completeBtn.setStyle("-fx-background-color: #2ecc71; -fx-text-fill: white; -fx-background-radius: 15; -fx-cursor: hand; -fx-font-size: 11px;");
                    completeBtn.setOnAction(e -> { e.consume(); markTaskStatus(task, "COMPLETED", task.date(), task.startTime()); });
                    actionBox.getChildren().add(completeBtn);
                } else if ("BACKLOG".equals(currentViewMode)) {
                    // 📅 Backlog View তে Reschedule এবং Done বাটন
                    Button completeBtn = new Button("✅ Done Now");
                    completeBtn.setStyle("-fx-background-color: #2ecc71; -fx-text-fill: white; -fx-background-radius: 15; -fx-cursor: hand; -fx-font-size: 11px;");
                    completeBtn.setOnAction(e -> { e.consume(); markTaskStatus(task, "COMPLETED", task.date(), task.startTime()); });
                    
                    Button rescheduleBtn = new Button("📅 Reschedule");
                    rescheduleBtn.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-background-radius: 15; -fx-cursor: hand; -fx-font-size: 11px;");
                    rescheduleBtn.setOnAction(e -> { e.consume(); showRescheduleDialog(task); });
                    
                    actionBox.getChildren().addAll(completeBtn, rescheduleBtn);
                }
            }

            // ডিলিট বাটন (সব ভিউতেই থাকবে যদি ক্রিয়েটর হয়)
            if (isAdmin || (isCreator && !"ROUTINE".equals(task.type()) && !"NOTICE".equals(task.type()))) {
                Button delBtn = new Button("🗑️"); delBtn.setStyle("-fx-background-color: transparent; -fx-cursor: hand;");
                delBtn.setOnAction(e -> deleteTimelineTask(task)); actionBox.getChildren().add(delBtn);
            }

            if (!actionBox.getChildren().isEmpty()) card.getChildren().add(actionBox);
            timelineContainer.getChildren().add(card);
        }
    }

    // 🌟 Helper Method for cleaner code
    private void markTaskStatus(StudyTask task, String status, String date, String time) {
        new Thread(() -> {
            if (dataService.updateTaskStatus(task.id(), status, date, time)) {
                Platform.runLater(() -> {
                    // আপডেট করে লিস্ট রিলোড করা
                    allTasks.remove(task);
                    allTasks.add(new StudyTask(task.id(), task.title(), date, time, task.durationMinutes(), task.roomNo(), task.type(), task.tags(), task.creatorRole(), task.ctCourse(), task.ctSyllabus(), status, task.importance()));
                    refreshTimeline();
                    showSuccess(status.equals("COMPLETED") ? "Task Completed! 🎉" : "Task Rescheduled! 📅");
                });
            }
        }).start();
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

    // 🎓 COMMUNITY TABLE & ACTIONS LOGIC
    // ==========================================================
// ==========================================================
    // 🎓 COMMUNITY TABLE & ACTIONS LOGIC (FIXED)
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
            private final Button previewBtn = new Button("👁️");  // 🌟 NEW
            private final Button downloadBtn = new Button("⬇️"); // 🌟 NEW
            private final Button upBtn = new Button("👍");
            private final Button downBtn = new Button("👎");
            private final Button doneBtn = new Button("✅");
            private final Button statsBtn = new Button("📊");
            private final Button discussBtn = new Button("💬"); // 🌟 NEW: Discussion Button
            private final Button editBtn = new Button("✏️");
            private final Button delBtn = new Button("🗑️");
            private final HBox pane = new HBox(5, upBtn, downBtn, doneBtn, statsBtn, discussBtn);

            {
                String btnStyle = "-fx-cursor: hand; -fx-padding: 4 6; -fx-background-radius: 5; -fx-text-fill: white; -fx-font-size: 11px; -fx-font-weight: bold;";
                upBtn.setStyle(btnStyle + "-fx-background-color: #27ae60;");
                downBtn.setStyle(btnStyle + "-fx-background-color: #e74c3c;");
                doneBtn.setStyle(btnStyle + "-fx-background-color: #2980b9;");
                statsBtn.setStyle(btnStyle + "-fx-background-color: #8e44ad;");
                discussBtn.setStyle(btnStyle + "-fx-background-color: #34495e;"); // 🌟 NEW: Style
                editBtn.setStyle(btnStyle + "-fx-background-color: #f39c12;");
                delBtn.setStyle(btnStyle + "-fx-background-color: #c0392b;");

                previewBtn.setTooltip(new Tooltip("In-App Preview"));
                downloadBtn.setTooltip(new Tooltip("Direct Download"));

                upBtn.setTooltip(new Tooltip("Upvote")); 
                downBtn.setTooltip(new Tooltip("Downvote"));
                doneBtn.setTooltip(new Tooltip("Mark as Done / Edit Note")); 
                statsBtn.setTooltip(new Tooltip("Statistics & Reviews"));
                discussBtn.setTooltip(new Tooltip("Open Discussion")); // 🌟 NEW: Tooltip
                editBtn.setTooltip(new Tooltip("Edit Resource"));

                // 🟢 ACTIONS
                upBtn.setOnAction(e -> processVote(getTableView().getItems().get(getIndex()), 1));
                downBtn.setOnAction(e -> processVote(getTableView().getItems().get(getIndex()), -1));




                // 🌟 NEW ACTIONS:
                previewBtn.setOnAction(e -> {
                    CourseService.Resource res = getTableView().getItems().get(getIndex()).getRawResource();
                    showInAppPreview(res.link(), res.title());
                });

                downloadBtn.setOnAction(e -> {
                    CourseService.Resource res = getTableView().getItems().get(getIndex()).getRawResource();
                    directDownloadFile(res.link(), res.title());
                });


               // এই লাইনটি খুঁজে বের করে নিচেরটি দিয়ে রিপ্লেস করুন:
doneBtn.setOnAction(e -> showResourceCompletionDialog(getTableView().getItems().get(getIndex())));
                statsBtn.setOnAction(e -> showStatisticsDialog(getTableView().getItems().get(getIndex()).getRawResource()));
                discussBtn.setOnAction(e -> showDiscussionPanel(getTableView().getItems().get(getIndex()).getRawResource())); // 🌟 NEW: Action
                editBtn.setOnAction(e -> showEditResourceDialog(getTableView().getItems().get(getIndex()).getRawResource()));

                delBtn.setOnAction(e -> {
                    ResourceRow row = getTableView().getItems().get(getIndex());
                    Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Delete this resource?", ButtonType.YES, ButtonType.NO);
                    confirm.showAndWait().ifPresent(res -> {
                        if(res == ButtonType.YES) new Thread(() -> { 
                            if(courseService.deleteResource(row.getRawResource().id())) 
                                Platform.runLater(() -> loadResourcesForTopic(currentSelectedTopicId)); 
                        }).start();
                    });
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) setGraphic(null); 
                else {
                    ResourceRow row = getTableView().getItems().get(getIndex());
                    pane.getChildren().clear(); 
                    // 🌟 FIX: Added discussBtn to the default view
                pane.getChildren().addAll(previewBtn, downloadBtn, upBtn, downBtn, doneBtn, statsBtn, discussBtn);

                    // 🌟 Show Edit & Delete ONLY if user is Creator or Admin
                    if ("admin".equals(AuthService.CURRENT_USER_ROLE) || (row.getRawResource().creatorId() != null && row.getRawResource().creatorId().equals(AuthService.CURRENT_USER_ID))) {
                        pane.getChildren().addAll(editBtn, delBtn);
                    }
                    
                    // 🌟 FIX: Done State Logic (Edit Option Enabled)
                    if (row.getRawResource().isCompleted()) { 
                        doneBtn.setText("✅ Edit Note"); // এখন দেখাবে Edit Note
                        doneBtn.setDisable(false); // বাটন ক্লিক করা যাবে
                        doneBtn.setStyle("-fx-background-color: #16a085; -fx-text-fill: white; -fx-padding: 4 6; -fx-background-radius: 5; -fx-font-size: 11px; -fx-font-weight: bold;");
                    } else {
                        doneBtn.setText("✅ Done"); 
                        doneBtn.setDisable(false);
                        doneBtn.setStyle("-fx-background-color: #2980b9; -fx-text-fill: white; -fx-padding: 4 6; -fx-background-radius: 5; -fx-font-size: 11px; -fx-font-weight: bold;");
                    }
                    
                    // 🌟 FIX: Vote State Logic
                    if (row.getRawResource().hasUserVoted()) {
                        upBtn.setDisable(true);
                        downBtn.setDisable(true);
                    } else {
                        upBtn.setDisable(false);
                        downBtn.setDisable(false);
                    }

                    setGraphic(pane); setAlignment(javafx.geometry.Pos.CENTER);
                }
            }
        });
        
        communityTree.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                if (currentFolderLabel != null) currentFolderLabel.setText(newVal.getValue());
                if (topicMap.containsKey(newVal)) {
                    currentSelectedTopicId = topicMap.get(newVal);
                    loadResourcesForTopic(currentSelectedTopicId);
                } else { currentSelectedTopicId = null; resourceTable.getItems().clear(); }
            }
        });
    }




    // 🌟 ১. ভোট প্রসেস করার মেথড (এটি লাল দাগ দূর করবে)
    private void processVote(ResourceRow row, int voteType) {
        new Thread(() -> {
            // CourseService এর submitVote মেথড কল করা হচ্ছে
            boolean success = courseService.submitVote(row.getRawResource().id(), voteType);
            
            Platform.runLater(() -> {
                if (success) {
                    // ভোট সফল হলে টেবিল রিফ্রেশ করা যাতে লাইভ কাউন্ট দেখা যায়
                    loadResourcesForTopic(currentSelectedTopicId);
                } else {
                    System.err.println("❌ Vote failed to save in Supabase.");
                }
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
                            TreeItem<String> ctGroup = new TreeItem<>("🚨 Class Tests (CT)");
                            TreeItem<String> basicsGroup = new TreeItem<>("🛠️ Basic Building");
                            TreeItem<String> finalGroup = new TreeItem<>("📚 Term Final");
                            TreeItem<String> othersGroup = new TreeItem<>("📁 Others");

                            for (var seg : segments) {
                                String segName = seg.name();
                                TreeItem<String> segNode = new TreeItem<>("📂 " + segName);
                                segmentMap.put(segNode, seg.id());

                                if (segName.toUpperCase().contains("CT")) {
                                    ctGroup.getChildren().add(segNode);
                                } else if (segName.toLowerCase().contains("basic")) {
                                    basicsGroup.getChildren().add(segNode);
                                } else if (segName.toLowerCase().contains("final")) {
                                    finalGroup.getChildren().add(segNode);
                                } else {
                                    othersGroup.getChildren().add(segNode);
                                }

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
                            if (!ctGroup.getChildren().isEmpty()) courseNode.getChildren().add(ctGroup);
                            if (!finalGroup.getChildren().isEmpty()) courseNode.getChildren().add(finalGroup);
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
                    if (courseService.addCourse(code, "Community Resource")) Platform.runLater(this::onRefreshCommunity);
                }).start();
            });
        } 
        else if (segmentMap.containsKey(selected)) {
            TextInputDialog dialog = new TextInputDialog();
            dialog.setTitle("New Topic"); dialog.setHeaderText("Add Topic in " + selected.getValue());
            dialog.showAndWait().ifPresent(name -> {
                new Thread(() -> {
                    if (courseService.addTopic(segmentMap.get(selected), name.trim(), "General")) Platform.runLater(this::onRefreshCommunity);
                }).start();
            });
        } else {
            showError("❌ Please select 'University Courses' to add a Course, or select a sub-folder like 'CT-1' to add a Topic.");
        }
    }






   


    //editing 
    // ==========================================================
    // ✏️ EDIT RESOURCE DIALOG
    // ==========================================================
    private void showEditResourceDialog(CourseService.Resource res) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("✏️ Edit Resource");
        ButtonType saveBtn = new ButtonType("Update Resource", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveBtn, ButtonType.CANCEL);

        GridPane grid = new GridPane(); grid.setHgap(10); grid.setVgap(15); grid.setPadding(new Insets(20));

        TextField titleField = new TextField(res.title()); 
        TextField linkField = new TextField(res.link());
        
        ComboBox<String> typeCombo = new ComboBox<>(); 
        typeCombo.getItems().addAll("LINK", "PDF", "Video", "Note"); 
        typeCombo.setValue(res.type());
        
        ComboBox<String> diffCombo = new ComboBox<>(); 
        diffCombo.getItems().addAll("Easy", "Medium", "Hard"); 
        diffCombo.setValue(res.difficulty());
        
        TextField durationField = new TextField(res.duration()); 
        TextField tagsField = new TextField(res.tags());
      TextArea descField = new TextArea(res.description() != null ? res.description() : "");
        descField.setPrefRowCount(3);

        grid.add(new Label("Title * :"), 0, 0);      grid.add(titleField, 1, 0);
        grid.add(new Label("Drive Link * :"), 0, 1); grid.add(linkField, 1, 1);
        grid.add(new Label("Type :"), 0, 2);         grid.add(typeCombo, 1, 2);
        grid.add(new Label("Difficulty :"), 0, 3);   grid.add(diffCombo, 1, 3);
        grid.add(new Label("Duration :"), 0, 4);     grid.add(durationField, 1, 4);
        grid.add(new Label("Tags :"), 0, 5);         grid.add(tagsField, 1, 5);
        grid.add(new Label("Description :"), 0, 6);  grid.add(descField, 1, 6);
        dialog.getDialogPane().setContent(grid);

        dialog.showAndWait().ifPresent(response -> {
            if (response == saveBtn && !titleField.getText().isEmpty() && !linkField.getText().isEmpty()) {
                new Thread(() -> {
                    boolean success = courseService.updateResource(
                        res.id(), titleField.getText().trim(), linkField.getText().trim(), 
                        typeCombo.getValue(), descField.getText().trim(), tagsField.getText().trim(), 
                        diffCombo.getValue(), durationField.getText().trim()
                    );
                    Platform.runLater(() -> {
                        if (success) { 
                            loadResourcesForTopic(currentSelectedTopicId); 
                            Alert a = new Alert(Alert.AlertType.INFORMATION, "Resource Updated Successfully!"); a.show();
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
                    String type = res.type() != null ? res.type() : "LINK";
                    String diff = res.difficulty() != null ? res.difficulty() : "Medium";
                    String tags = res.tags() != null ? res.tags() : "General";
                    
                    // 📊 Upvote এবং Downvote দুটোই দেখাবে
                    String votes = "👍 " + res.upvotes() + " | 👎 " + res.downvotes();
                    
                    rows.add(new ResourceRow(res.title(), type, diff, uploader, tags, votes, res));
                }
                resourceTable.getItems().setAll(rows);
            });
        }).start();
    }
    

    // 🌟 Manual Upload Form (NO AI) 🌟
    @FXML
    public void onUploadResourceClick() {
        if (currentSelectedTopicId == null) {
            showError("❌ Please select a specific Topic from the left panel first!"); return;
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("📤 Upload Resource");
        ButtonType saveBtn = new ButtonType("Save Resource", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveBtn, ButtonType.CANCEL);

        GridPane grid = new GridPane(); 
        grid.setHgap(10); grid.setVgap(15); grid.setPadding(new Insets(20));

        TextField titleField = new TextField(); titleField.setPromptText("E.g., Term Final Questions 2023");
        TextField linkField = new TextField("https://");
        
        ComboBox<String> typeCombo = new ComboBox<>();
        typeCombo.getItems().addAll("LINK", "PDF", "Video", "Note"); typeCombo.setValue("LINK");
        
        ComboBox<String> diffCombo = new ComboBox<>();
        diffCombo.getItems().addAll("Easy", "Medium", "Hard"); diffCombo.setValue("Medium");

        TextField durationField = new TextField(); durationField.setPromptText("e.g., 30 mins");
        TextField tagsField = new TextField(); tagsField.setPromptText("e.g., #Questions");
        TextArea descField = new TextArea(); descField.setPromptText("Resource details..."); descField.setPrefRowCount(3);

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
        // UI-তে একটি মেসেজ দিন যে AI কাজ করছে
        System.out.println("🤖 AI is working on the summary...");

        new Thread(() -> {
            // ১. আগে Gemini থেকে সামারি তৈরি করে আনা
            String generatedSummary = aiController.generateResourceSummary(
                titleField.getText(), linkField.getText(), tagsField.getText(), descField.getText()
            );

            // ২. এবার সামারিটিসহ ডাটাবেসে পাঠানো
            boolean success = courseService.addDetailedResource(
                currentSelectedTopicId, 
                titleField.getText().trim(), 
                linkField.getText().trim(), 
                typeCombo.getValue(), 
                descField.getText().trim(), 
                tagsField.getText().trim(), 
                diffCombo.getValue(), 
                durationField.getText().trim(), 
                true,
                generatedSummary ,
                AuthService.CURRENT_CHANNEL_ID // 👈 নতুন প্যারামিটার যোগ করা হলো
            );

            Platform.runLater(() -> {
                if (success) {
                    loadResourcesForTopic(currentSelectedTopicId);
                    showSuccess("Resource Uploaded with AI Summary! ✨");
                }
            });
        }).start();
    }
});
    }

    // 💬 5. ADVANCED DISCUSSION & Q&A PANEL
    // ==========================================================
    private void showDiscussionPanel(CourseService.Resource res) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("💬 Q&A: " + res.title());
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        VBox layout = new VBox(15); 
        layout.setPadding(new Insets(15)); 
        layout.setPrefSize(550, 600);
        layout.setStyle("-fx-background-color: #f4f6f7;");

        // 🌟 "My Questions" Toggle Button
        ToggleButton myQuestionsToggle = new ToggleButton("🔍 Show My Questions Only");
        myQuestionsToggle.setStyle("-fx-background-color: #ecf0f1; -fx-text-fill: #2c3e50; -fx-font-weight: bold; -fx-cursor: hand; -fx-background-radius: 20; -fx-padding: 5 15;");

        VBox qnaContainer = new VBox(15);
        ScrollPane scroll = new ScrollPane(qnaContainer);
        scroll.setFitToWidth(true); 
        scroll.setStyle("-fx-background: #f4f6f7; -fx-border-color: transparent;");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        // 🌟 New Question Input
        TextField input = new TextField(); 
        input.setPromptText("Ask a question or share a doubt...");
        input.setStyle("-fx-background-radius: 20; -fx-padding: 10;"); 
        HBox.setHgrow(input, Priority.ALWAYS);
        
        Button sendBtn = new Button("Ask 🚀");
        sendBtn.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-background-radius: 20; -fx-font-weight: bold; -fx-cursor: hand;");
        
        sendBtn.setOnAction(e -> {
            if(input.getText().trim().isEmpty()) return;
            new Thread(() -> {
                if(courseService.addComment(res.id(), input.getText().trim(), null)) {
                    Platform.runLater(() -> { 
                        input.clear(); 
                        loadQnA(res.id(), qnaContainer, myQuestionsToggle.isSelected()); 
                    });
                }
            }).start();
        });

        // Toggle Action
        myQuestionsToggle.setOnAction(e -> {
            if (myQuestionsToggle.isSelected()) {
                myQuestionsToggle.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand; -fx-background-radius: 20; -fx-padding: 5 15;");
            } else {
                myQuestionsToggle.setStyle("-fx-background-color: #ecf0f1; -fx-text-fill: #2c3e50; -fx-font-weight: bold; -fx-cursor: hand; -fx-background-radius: 20; -fx-padding: 5 15;");
            }
            loadQnA(res.id(), qnaContainer, myQuestionsToggle.isSelected());
        });

        HBox topBar = new HBox(myQuestionsToggle); topBar.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);
        HBox inputBox = new HBox(10, input, sendBtn); inputBox.setAlignment(javafx.geometry.Pos.CENTER);
        
        layout.getChildren().addAll(topBar, scroll, inputBox);
        dialog.getDialogPane().setContent(layout);
        
        // Initial Load
        loadQnA(res.id(), qnaContainer, false);
        dialog.showAndWait();
    }

    // 🌟 Load Threads (Questions + Answers)
    private void loadQnA(int resId, VBox container, boolean showOnlyMine) {
        new Thread(() -> {
            List<CourseService.Comment> allComments = courseService.getComments(resId);
            Platform.runLater(() -> {
                container.getChildren().clear();
                
                // Separate Questions (parentId == 0) and Answers
                List<CourseService.Comment> questions = new ArrayList<>();
                List<CourseService.Comment> answers = new ArrayList<>();
                for (var c : allComments) {
                    if (c.parentId() == 0) questions.add(c);
                    else answers.add(c);
                }

                if (questions.isEmpty()) {
                    container.getChildren().add(new Label("No questions yet. Be the first to ask! 💡"));
                    return;
                }

                boolean foundAny = false;

                for (var q : questions) {
                    // Filter "My Questions" logic
                   String currentUserIdStr = String.valueOf(AuthService.CURRENT_USER_ID);
if (showOnlyMine && q.userId() != null && !String.valueOf(q.userId()).equals(currentUserIdStr)) continue;
                    
                    foundAny = true;

                    // 1. Build Question Card
                    VBox qCard = new VBox(8);
                    qCard.setStyle("-fx-background-color: white; -fx-padding: 15; -fx-background-radius: 10; -fx-border-color: #bdc3c7; -fx-border-width: 1 1 1 5; -fx-border-color: #3498db; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.05), 5, 0, 0, 2);");
                    
                    Label qUser = new Label("❓ " + q.userName() + " asked:");
                    qUser.setStyle("-fx-font-weight: bold; -fx-text-fill: #2c3e50;");
                    Label qContent = new Label(q.content());
                    qContent.setWrapText(true); qContent.setStyle("-fx-font-size: 14px; -fx-text-fill: #34495e;");
                    
                    // Reply Box (Hidden by default)
                    HBox replyInputBox = new HBox(10);
                    replyInputBox.setVisible(false); replyInputBox.setManaged(false);
                    TextField replyInput = new TextField(); replyInput.setPromptText("Write an answer..."); HBox.setHgrow(replyInput, Priority.ALWAYS);
                    Button submitReplyBtn = new Button("Reply"); submitReplyBtn.setStyle("-fx-background-color: #2ecc71; -fx-text-fill: white; -fx-cursor: hand;");
                    replyInputBox.getChildren().addAll(replyInput, submitReplyBtn);

                    Button showReplyBtn = new Button("↳ Answer this");
                    showReplyBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #3498db; -fx-cursor: hand; -fx-padding: 0;");
                    showReplyBtn.setOnAction(e -> {
                        replyInputBox.setVisible(!replyInputBox.isVisible());
                        replyInputBox.setManaged(!replyInputBox.isManaged());
                    });

                    submitReplyBtn.setOnAction(e -> {
                        if(replyInput.getText().trim().isEmpty()) return;
                        new Thread(() -> {
                            if(courseService.addComment(resId, replyInput.getText().trim(), q.id())) {
                                Platform.runLater(() -> loadQnA(resId, container, showOnlyMine)); // Reload UI
                            }
                        }).start();
                    });

                    qCard.getChildren().addAll(qUser, qContent, showReplyBtn, replyInputBox);

                    // 2. Build Answers (Replies) under this Question
                    VBox answersBox = new VBox(8);
                    answersBox.setPadding(new Insets(10, 0, 0, 30)); // Indent answers
                    for (var a : answers) {
                        if (a.parentId() == q.id()) {
                            VBox aCard = new VBox(5);
                            aCard.setStyle("-fx-background-color: #ecf0f1; -fx-padding: 10; -fx-background-radius: 8;");
                            Label aUser = new Label("💡 " + a.userName() + " replied:");
                            aUser.setStyle("-fx-font-weight: bold; -fx-text-fill: #27ae60; -fx-font-size: 11px;");
                            Label aContent = new Label(a.content());
                            aContent.setWrapText(true);
                            aCard.getChildren().addAll(aUser, aContent);
                            answersBox.getChildren().add(aCard);
                        }
                    }

                    container.getChildren().addAll(qCard, answersBox);
                }

                if (showOnlyMine && !foundAny) {
                    container.getChildren().add(new Label("You haven't asked any questions yet! 🔍"));
                }
            });
        }).start();
    }





    // ==========================================================
    // 🗃️ UPDATED TABLE MODEL (Matches Supabase schema completely)
    // ==========================================================
    public static class ResourceRow {
        private final String name;
        private final String type;
        private final String diff;
        private final String uploader;
        private final String tags;
        private final String votes;
        private final CourseService.Resource rawResource;

        public ResourceRow(String name, String type, String diff, String uploader, String tags, String votes, CourseService.Resource rawResource) {
            this.name = name; 
            this.type = type; 
            this.diff = diff; 
            this.uploader = uploader; 
            this.tags = tags; 
            this.votes = votes; 
            this.rawResource = rawResource;
        }

        public String getName() { return name; }
        public String getType() { return type; }
        public String getDiff() { return diff; }
        public String getUploader() { return uploader; }
        public String getTags() { return tags; }
        public String getVotes() { return votes; }
        public CourseService.Resource getRawResource() { return rawResource; }
    }

    // ==========================================================
    // 🤝 5. COLLABORATION & TEAMS
    // ==========================================================
    private void loadChannels() {
        if(channelList == null) return;
        channelList.getChildren().clear();
        String[] channels = {"Hackathons 🏆", "Research 🔬", "Study Partners 📚", "Project Help 🆘"};
        for (String c : channels) {
            Button btn = new Button("# " + c);
            btn.setMaxWidth(Double.MAX_VALUE); btn.setAlignment(Pos.CENTER_LEFT);
            btn.setStyle("-fx-background-color: transparent; -fx-text-fill: white; -fx-cursor: hand;");
            btn.setOnAction(e -> loadTeamsForChannel(c)); channelList.getChildren().add(btn);
        }
    }

    private void loadTeamsForChannel(String channelName) {
        teamList.getChildren().clear();
        Label title = new Label("Teams in " + channelName); title.setStyle("-fx-font-weight: bold; -fx-font-size: 16px;");
        teamList.getChildren().add(title);
        VBox card = new VBox(5); card.setStyle("-fx-background-color: white; -fx-padding: 10;");
        card.getChildren().addAll(new Label("🚀 Space Apps Team"), new Button("View Team"));
        card.setOnMouseClicked(e -> loadRoomView(1)); teamList.getChildren().add(card);
    }

    private void loadRoomView(int postId) {
        String myStatus = collaborationService.getMyStatus(postId);
        roomContainer.getChildren().clear();
        if (myStatus.equals("APPROVED")) showChatRoom(postId);
        else {
            Button applyBtn = new Button("Apply to Join Team");
            applyBtn.setOnAction(e -> { if (collaborationService.applyToTeam(postId)) showSuccess("Application Sent!"); });
            roomContainer.getChildren().add(applyBtn);
        }
    }

    private void showChatRoom(int postId) {
        VBox chatBox = new VBox(10); TextArea msgInput = new TextArea(); msgInput.setPrefRowCount(2);
        Button sendBtn = new Button("Send 🚀");
        sendBtn.setOnAction(e -> { collaborationService.sendMessage(postId, msgInput.getText()); msgInput.clear(); refreshChat(postId, chatBox); });
        refreshChat(postId, chatBox); roomContainer.getChildren().addAll(chatBox, msgInput, sendBtn);
    }

    private void refreshChat(int postId, VBox chatBox) {
        new Thread(() -> {
            List<CollaborationService.Message> messages = collaborationService.getMessages(postId);
            Platform.runLater(() -> {
                chatBox.getChildren().clear();
                for (var msg : messages) chatBox.getChildren().add(new Label(msg.sender() + ": " + msg.content()));
            });
        }).start();
    }

    @FXML public void onCreateChannel() { showSuccess("Feature coming soon!"); }
    @FXML public void onCreatePost() {
        TextInputDialog dialog = new TextInputDialog(); dialog.setHeaderText("Create Post Title:");
        dialog.showAndWait().ifPresent(title -> { if(collaborationService.createPost(1, title)) showSuccess("Post Created!"); });
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
        saveBtn.setOnAction(e -> {
            new Thread(() -> { if(ecaService.linkAccounts(lcField.getText(), dpField.getText())) Platform.runLater(() -> loadECATracker(container)); }).start();
        });
        box.getChildren().addAll(lcField, dpField, saveBtn); container.getChildren().add(box);
    }

    private void showECADashboard(VBox container, String lcUser, String dpUser) {
        HBox cards = new HBox(20);
        VBox lcCard = new VBox(10); Label lcStat = new Label("Loading..."); lcCard.getChildren().addAll(new Label("LeetCode"), lcStat);
        VBox dpCard = new VBox(10); Label dpStat = new Label("Loading..."); dpCard.getChildren().addAll(new Label("Devpost"), dpStat);
        PieChart topicChart = new PieChart(); topicChart.setPrefSize(300, 200);
        cards.getChildren().addAll(lcCard, dpCard, topicChart); container.getChildren().addAll(new Label("Activity Dashboard"), cards);

        new Thread(() -> {
            int[] lcStats = ecaService.fetchLeetCodeStats(lcUser);
            int dpProjects = ecaService.fetchDevpostProjectCount(dpUser);
            var topicStats = ecaService.fetchTopicStats(lcUser);
            Platform.runLater(() -> {
                if (lcStats != null) lcStat.setText("Solved: " + lcStats[0]);
                if (dpProjects != -1) dpStat.setText("Projects: " + dpProjects);
                if (topicStats != null) {
                    topicChart.getData().clear();
                    topicStats.forEach((tag, count) -> { if (count > 0) topicChart.getData().add(new PieChart.Data(tag, count)); });
                }
            });
        }).start();
    }

    // ==========================================================
    // ⚙️ 7. PROFILE & GLOBAL ACTIONS
    // ==========================================================
    @FXML
    public void loadProfileSettings() {
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

    @FXML
    public void onSaveProfile() {
        new Thread(() -> {
            if (courseService.updateProfile(settingsName.getText(), settingsUsername.getText())) {
                Platform.runLater(() -> showSuccess("Profile Updated!"));
            }
        }).start();
    }

    @FXML
    private void handleLogoutTab(Event event) {
        Tab tab = (Tab) event.getSource();
        if (tab.isSelected()) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Logout"); alert.setHeaderText("Are you sure you want to logout?");
            if (alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
                AuthService.logout();
                try {
                    FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/scholar/view/login.fxml"));
                    Stage stage = (Stage) tab.getTabPane().getScene().getWindow();
                    stage.setScene(new Scene(loader.load(), 400, 600)); stage.centerOnScreen();
                } catch (IOException e) { e.printStackTrace(); }
            } else tab.getTabPane().getSelectionModel().select(0);
        }
    }

    private void showSuccess(String msg) { Alert a = new Alert(Alert.AlertType.INFORMATION); a.setContentText(msg); a.show(); }
    private void showError(String msg) { Alert a = new Alert(Alert.AlertType.ERROR); a.setContentText(msg); a.showAndWait(); }



    //Quora Style 
    // ==========================================================
    // 📊 2. QUORA-STYLE STATISTICS POPUP
    // ==========================================================
    private void showStatisticsDialog(CourseService.Resource res) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("📊 Community Statistics");
        dialog.setHeaderText("Insights for: " + res.title());
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        VBox layout = new VBox(15); 
        layout.setPadding(new Insets(20)); 
        layout.setPrefSize(450, 500);
        layout.setStyle("-fx-background-color: #f4f6f7;");

        Label loadingLbl = new Label("Fetching community data from Supabase...");
        layout.getChildren().add(loadingLbl);
        dialog.getDialogPane().setContent(layout);

        new Thread(() -> {
            // ডাটাবেস থেকে স্ট্যাটিস্টিক্স আনা হচ্ছে
            CourseService.ResourceStats stats = courseService.getResourceStatistics(res.id());
            
            Platform.runLater(() -> {
                layout.getChildren().clear();

                // 1. Voting Stats
                HBox votesBox = new HBox(30);
                Label upLbl = new Label("👍 Upvotes: " + stats.totalUpvotes());
                upLbl.setStyle("-fx-font-weight: bold; -fx-text-fill: #27ae60; -fx-font-size: 16px;");
                Label downLbl = new Label("👎 Downvotes: " + stats.totalDownvotes());
                downLbl.setStyle("-fx-font-weight: bold; -fx-text-fill: #c0392b; -fx-font-size: 16px;");
                votesBox.getChildren().addAll(upLbl, downLbl);

                // 2. Difficulty Distribution
                HBox diffBox = new HBox(15);
                diffBox.setStyle("-fx-background-color: white; -fx-padding: 15; -fx-background-radius: 8; -fx-border-color: #bdc3c7;");
                diffBox.getChildren().addAll(
                    new Label("🟢 Easy: " + stats.easyCount()),
                    new Label("🟡 Medium: " + stats.mediumCount()),
                    new Label("🔴 Hard: " + stats.hardCount())
                );

                // 3. User Notes & Reviews (Quora Style Feed)
                Label reviewTitle = new Label("Student Notes & Experiences:");
                reviewTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 15px;");
                
                VBox notesContainer = new VBox(12);
                if (stats.userLogs().isEmpty()) {
                    notesContainer.getChildren().add(new Label("No notes yet. Be the first to complete it! 🚀"));
                } else {
                    for (CourseService.CompletionLog log : stats.userLogs()) {
                        VBox card = new VBox(8);
                        card.setStyle("-fx-background-color: white; -fx-padding: 15; -fx-background-radius: 10; -fx-border-color: #ecf0f1;");
                        
                        HBox header = new HBox(15);
                        Label nameLbl = new Label("👤 " + log.username());
                        nameLbl.setStyle("-fx-font-weight: bold; -fx-text-fill: #2980b9;");
                        Label diffLbl = new Label("Rated: " + log.difficulty());
                        Label timeLbl = new Label("⏱️ " + log.timeMins() + " mins");
                        Label dateLbl = new Label(log.date());
                        dateLbl.setStyle("-fx-text-fill: #95a5a6; -fx-font-size: 11px;");
                        
                        header.getChildren().addAll(nameLbl, diffLbl, timeLbl, new Region(), dateLbl);
                        HBox.setHgrow(header.getChildren().get(3), Priority.ALWAYS); // Push date to right
                        
                        Label noteLbl = new Label(log.note() == null || log.note().isEmpty() ? "No additional notes provided." : log.note());
                        noteLbl.setWrapText(true);
                        noteLbl.setStyle("-fx-text-fill: #34495e; -fx-font-size: 13px;");
                        
                        card.getChildren().addAll(header, new Separator(), noteLbl);
                        notesContainer.getChildren().add(card);
                    }
                }
                
                ScrollPane scroll = new ScrollPane(notesContainer);
                scroll.setFitToWidth(true); scroll.setStyle("-fx-background-color: transparent; -fx-border-color: transparent;");
                VBox.setVgrow(scroll, Priority.ALWAYS);

                layout.getChildren().addAll(votesBox, diffBox, reviewTitle, scroll);
            });
        }).start();

        dialog.showAndWait();
    }


    // ==========================================================
    // 📝 1. MARK AS DONE / EDIT NOTE DIALOG
    // ==========================================================
    private void showResourceCompletionDialog(ResourceRow row) {
        // প্রথমে ব্যাকগ্রাউন্ড থ্রেডে আগের ডাটা চেক করা হচ্ছে
        new Thread(() -> {
            CourseService.UserProgress existingProgress = courseService.getUserProgress(row.getRawResource().id());
            
            Platform.runLater(() -> {
                Dialog<ButtonType> dialog = new Dialog<>();
                // যদি আগে থেকে ডান করা থাকে, তবে টাইটেল হবে "Edit", না হলে "Completed"
                dialog.setTitle(existingProgress.isCompleted() ? "✏️ Edit Your Note" : "🎉 Resource Completed!");
                dialog.setHeaderText("Log your experience for: " + row.getName());

                ButtonType saveBtn = new ButtonType(existingProgress.isCompleted() ? "Update Details" : "Save Experience", ButtonBar.ButtonData.OK_DONE);
                dialog.getDialogPane().getButtonTypes().addAll(saveBtn, ButtonType.CANCEL);

                GridPane grid = new GridPane(); 
                grid.setHgap(10); grid.setVgap(15); grid.setPadding(new Insets(20));

                // আগের ডাটা দিয়ে ফিল্ডগুলো পূরণ করা হচ্ছে (Pre-fill)
                TextField durationField = new TextField(existingProgress.timeMins() > 0 ? String.valueOf(existingProgress.timeMins()) : ""); 
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
                        String note = noteArea.getText().trim();
                        new Thread(() -> {
                            // এটি ডাটাবেসে সেভ করবে। যদি আগে থেকে থাকে তবে ওভাররাইট (Update) করবে।
                            if (courseService.markResourceDone(row.getRawResource().id(), diffCombo.getValue(), mins, note)) {
                                Platform.runLater(() -> {
                                    Alert a = new Alert(Alert.AlertType.INFORMATION, "Your notes and progress are updated! ✅");
                                    a.show();
                                    if (currentSelectedTopicId != null) loadResourcesForTopic(currentSelectedTopicId); 
                                });
                            }
                        }).start();
                    }
                });
            });
        }).start();
    }


    
    // ==========================================================
    // 🌍 GOOGLE DRIVE PREVIEW & DOWNLOAD SYSTEM
    // ==========================================================

    // ১. ড্রাইভ লিংক থেকে ID বের করার হ্যাক (Regex)
    private String extractDriveId(String url) {
        if (url == null || !url.contains("drive.google.com")) return null;
        Matcher m = Pattern.compile("/d/([a-zA-Z0-9-_]+)").matcher(url);
        if (m.find()) return m.group(1);
        m = Pattern.compile("id=([a-zA-Z0-9-_]+)").matcher(url);
        if (m.find()) return m.group(1);
        return null;
    }

    // ২. সফটওয়্যারের ভেতরেই ফাইল প্রিভিউ করার মেথড
    private void showInAppPreview(String url, String title) {
        try {
            Stage previewStage = new Stage();
            previewStage.setTitle("👁️ Preview: " + title);
            
            WebView webView = new WebView();
            webView.getEngine().load(url); // গুগলের প্রিভিউ ভিউয়ার লোড হবে
            
            Scene scene = new Scene(webView, 900, 650);
            previewStage.setScene(scene);
            previewStage.show();
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Preview Error", "Could not load preview. Please ensure JavaFX Web module is installed.");
        }
    }

    // ৩. এক ক্লিকে ডিরেক্ট ডেস্কটপে ডাউনলোড করার মেথড
    // ==========================================================
    // 🌍 UNIVERSAL DOWNLOADER (DRIVE + YOUTUBE + TELEGRAM)
    // ==========================================================

    // ১. স্মার্ট রাউটার (লিংক চেক করে সঠিক জায়গায় পাঠাবে)
    private void directDownloadFile(String url, String title) {
        if (url == null || url.isEmpty()) return;

        if (url.contains("drive.google.com")) {
            // 🌟 ড্রাইভের ফাইল আগের মতোই সরাসরি ডেস্কটপে সেভ হবে
            String fileId = extractDriveId(url);
            if (fileId != null) {
                String directUrl = "https://drive.google.com/uc?export=download&id=" + fileId;
                startFileDownloadProcess(directUrl, title, ".pdf"); 
            }
        } else if (url.contains("youtube.com") || url.contains("youtu.be") || url.contains("t.me") || url.contains("telegram.me")) {
            // 🌟 ইউটিউব ও টেলিগ্রামের জন্য Smart Redirect (কখনো ব্লক হবে না)
            openExternalDownloader(url);
        } else {
            showAlert(Alert.AlertType.WARNING, "Unsupported Link", "Direct download is supported for Google Drive, YouTube, and Telegram links.");
        }
    }


   // 🌟 ১০০% কার্যকরী মেথড: লিংক কপি করে সেফ ডাউনলোডার ওপেন করা
    private void openExternalDownloader(String mediaUrl) {
        try {
            // ১. অটোমেটিকভাবে ভিডিওর লিংক ইউজারের ক্লিপবোর্ডে কপি করে দেওয়া
            javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
            javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
            content.putString(mediaUrl);
            clipboard.setContent(content);

            // ২. ইউজারকে সুন্দর একটি পপআপ মেসেজ দেওয়া
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Link Copied! ✂️");
            alert.setHeaderText("Opening Secure Downloader...");
            alert.setContentText("The media link has been copied to your clipboard!\n\nWe are opening a fast & secure downloader. Just paste the link there to get your MP4 instantly.");
            alert.showAndWait();

            // ৩. পিসির ডিফল্ট ব্রাউজার ওপেন করা (Cobalt এর অফিসিয়াল সেফ ওয়েবসাইট)
            // আপনি চাইলে এখানে আপনার পছন্দের কোনো টেলিগ্রাম বটের লিংকও দিতে পারেন (যেমন: https://t.me/utubebot)
            String safeDownloaderUrl = "https://cobalt.tools"; 
            
            if (java.awt.Desktop.isDesktopSupported() && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.BROWSE)) {
                java.awt.Desktop.getDesktop().browse(new URI(safeDownloaderUrl));
            } else {
                showAlert(Alert.AlertType.WARNING, "Browser Error", "Could not open browser automatically. Please go to: " + safeDownloaderUrl);
            }

        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "System Error", "Could not open downloader: " + e.getMessage());
        }
    }

    // ৩. সেভ ডায়লগ এবং আসল ডাউনলোড প্রসেস (শুধুমাত্র Google Drive এর জন্য)
    private void startFileDownloadProcess(String directDownloadUrl, String title, String extension) {
        javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
        fileChooser.setTitle("Save File");
        fileChooser.setInitialFileName(title.replaceAll("[^a-zA-Z0-9.-]", "_") + extension);
        fileChooser.setInitialDirectory(new File(System.getProperty("user.home"), "Desktop"));
        
        File destFile = fileChooser.showSaveDialog(null);
        
        if (destFile != null) {
            Alert dlAlert = new Alert(Alert.AlertType.INFORMATION, "Downloading " + extension.toUpperCase() + "... Please wait.");
            dlAlert.show();

            new Thread(() -> {
                try (InputStream in = new URL(directDownloadUrl).openStream()) {
                    java.nio.file.Files.copy(in, destFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    javafx.application.Platform.runLater(() -> {
                        dlAlert.close();
                        showAlert(Alert.AlertType.INFORMATION, "Success 🎉", "Saved perfectly to: " + destFile.getName());
                    });
                } catch (Exception e) {
                    javafx.application.Platform.runLater(() -> {
                        dlAlert.close();
                        showAlert(Alert.AlertType.ERROR, "Download Failed", "Error saving file: " + e.getMessage());
                    });
                }
            }).start();
        }
    }

// ==========================================================
    // 🛠️ HELPER METHOD FOR SHOWING ALERTS
    // ==========================================================
    private void showAlert(Alert.AlertType type, String title, String content) {
        // Platform.runLater ব্যবহার করা হয়েছে যাতে ব্যাকগ্রাউন্ড থ্রেড থেকে কল করলেও অ্যাপ ক্র্যাশ না করে
        javafx.application.Platform.runLater(() -> {
            javafx.scene.control.Alert alert = new javafx.scene.control.Alert(type);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(content);
            alert.showAndWait();
        });
    }


    // ==========================================================
    // 🤖 SMART AI TUTOR PANEL (CHAT UI WITH WEBVIEW)
    // ==========================================================
    private void showAITutorPanel() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("🤖 ScholarGrid AI Tutor");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        VBox layout = new VBox(15);
        layout.setPadding(new javafx.geometry.Insets(15));
        layout.setPrefSize(650, 550);
        layout.setStyle("-fx-background-color: #f4f6f7;");

        // ১. AI-এর উত্তর দেখানোর জন্য WebView (মিনি ব্রাউজার)
        javafx.scene.web.WebView webView = new javafx.scene.web.WebView();
        javafx.scene.layout.VBox.setVgrow(webView, javafx.scene.layout.Priority.ALWAYS);
        
        // ওয়েলকাম মেসেজ
        String welcomeHtml = "<html><body style='font-family: Arial, sans-serif; color: #2c3e50; text-align: center; margin-top: 15%;'>" +
            "<h2 style='color: #8e44ad;'>👋 Hello! I am your ScholarGrid AI.</h2>" +
            "<p>Ask me what to study, and I will find the <b>best resources</b> from our database for you!</p>" +
            "</body></html>";
        webView.getEngine().loadContent(welcomeHtml);

        // ২. সার্চ টপিক এবং প্রশ্ন ইনপুট
        javafx.scene.control.TextField topicInput = new javafx.scene.control.TextField();
        topicInput.setPromptText("Topic (e.g., Recursion)");
        topicInput.setPrefWidth(150);
        topicInput.setStyle("-fx-padding: 10; -fx-background-radius: 10; -fx-border-radius: 10; -fx-border-color: #bdc3c7;");

        javafx.scene.control.TextField questionInput = new javafx.scene.control.TextField();
        questionInput.setPromptText("Ask your question here...");
        javafx.scene.layout.HBox.setHgrow(questionInput, javafx.scene.layout.Priority.ALWAYS);
        questionInput.setStyle("-fx-padding: 10; -fx-background-radius: 10; -fx-border-radius: 10; -fx-border-color: #bdc3c7;");

        javafx.scene.control.Button askBtn = new javafx.scene.control.Button("Ask AI ✨");
        askBtn.setStyle("-fx-background-color: #8e44ad; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 10; -fx-cursor: hand; -fx-padding: 10 20;");

        // ৩. Ask বাটনের অ্যাকশন (AI কে কল করা)
        askBtn.setOnAction(e -> {
            String topic = topicInput.getText().trim();
            String question = questionInput.getText().trim();

            if (topic.isEmpty() || question.isEmpty()) {
                showAlert(Alert.AlertType.WARNING, "Missing Info", "Please enter both a Topic and your Question!");
                return;
            }

            // লোডিং এনিমেশন দেখানো
            webView.getEngine().loadContent("<html><body style='font-family: Arial; text-align: center; margin-top: 20%; color: #7f8c8d;'>" +
                    "<h3>🤖 AI is analyzing the database... Please wait... ⏳</h3></body></html>");
            askBtn.setDisable(true);

            // ব্যাকগ্রাউন্ড থ্রেডে AI-কে কল করা (যাতে অ্যাপ হ্যাং না করে)
            new Thread(() -> {
                // 🌟 AIController থেকে ম্যাজিকাল মেথড কল করা হচ্ছে!
                String aiHtmlResponse = aiController.askSmartAITutor(question, topic);

                // UI আপডেট করার জন্য Platform.runLater
                javafx.application.Platform.runLater(() -> {
                    // উত্তরটিকে সুন্দর HTML মোড়কে সাজানো
                    String finalHtml = "<html><body style='font-family: Arial, sans-serif; padding: 15px; line-height: 1.6; color: #34495e;'>" +
                            aiHtmlResponse + "</body></html>";
                    
                    webView.getEngine().loadContent(finalHtml);
                    askBtn.setDisable(false);
                    questionInput.clear(); // প্রশ্ন বক্স খালি করা
                });
            }).start();
        });

        // 🌟 ৪. WebView-এর ভেতরের লিংকে ক্লিক করলে যেন পিসির মেইন ব্রাউজারে ওপেন হয়
        webView.getEngine().locationProperty().addListener((obs, oldLoc, newLoc) -> {
            if (newLoc != null && !newLoc.isEmpty() && newLoc.startsWith("http")) {
                javafx.application.Platform.runLater(() -> {
                    webView.getEngine().loadContent("<html><body style='font-family: Arial; text-align: center; margin-top: 20%; color: #27ae60;'>" +
                        "<h3>🌍 Opening link in your default browser...</h3><p>Return here to continue chatting!</p></body></html>");
                });
                try {
                    java.awt.Desktop.getDesktop().browse(new java.net.URI(newLoc));
                } catch (Exception ex) { ex.printStackTrace(); }
            }
        });

        javafx.scene.layout.HBox inputBox = new javafx.scene.layout.HBox(10, topicInput, questionInput, askBtn);
        inputBox.setAlignment(javafx.geometry.Pos.CENTER);

        layout.getChildren().addAll(webView, inputBox);
        dialog.getDialogPane().setContent(layout);
        
        dialog.showAndWait();
    }


 // task deletation helper
 // ==========================================================
// 🗑️ TIMELINE TASK DELETION LOGIC
// ==========================================================
private void deleteTimelineTask(StudyTask task) {
    // ১. ইউজারকে কনফার্মেশন জিজ্ঞেস করা
    Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, 
        "Are you sure you want to delete '" + task.title() + "'?", 
        ButtonType.YES, ButtonType.NO);
    
    confirm.showAndWait().ifPresent(response -> {
        if (response == ButtonType.YES) {
            
            // ২. UI থেকে সাথে সাথে রিমুভ করা (যাতে অ্যাপ ফাস্ট মনে হয়)
            allTasks.remove(task);
            refreshTimeline(); 
            drawCalendar();

            // ৩. ব্যাকগ্রাউন্ড থ্রেডে ডাটাবেস থেকে ডিলিট করা (Security Check সহ)
            new Thread(() -> {
                dataService.deleteTask(task.id(), task.type(), task.creatorRole());
            }).start();
        }
    });
}

// ==========================================================
    // 📝 DETAILED TASK DIALOG (VIEW & EDIT)
    // ==========================================================
   // ==========================================================
    // 📝 DETAILED TASK DIALOG (CT INTERFACE & REGULAR)
    // ==========================================================
   private void showTaskDetailsDialog(StudyTask task) {
        // 🌟 লজিক: টাইটেলে "CT" লেখা থাকলেই স্পেশাল ইন্টারফেস ওপেন হবে!
        boolean isCT = task.title() != null && task.title().toUpperCase().contains("CT");
        boolean isPersonal = "PERSONAL".equals(task.type()); // 🌟 NEW: পার্সোনাল টাস্ক চেক

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(isCT ? "🚨 CT Details Interface" : (isPersonal ? "👤 Personal Task" : "📝 Task Details"));
        dialog.setHeaderText(isCT ? "Manage Class Test Information" : (isPersonal ? "Edit your personal task" : "Course Information"));

        boolean isAdmin = "admin".equals(AuthService.CURRENT_USER_ROLE);
        boolean isCreator = task.creatorRole() != null && task.creatorRole().equals(AuthService.CURRENT_USER_ROLE);
        boolean canEdit = isAdmin || (isCreator && !"ROUTINE".equals(task.type()) && !"NOTICE".equals(task.type()));

        ButtonType saveBtn = new ButtonType("Save to Supabase 💾", ButtonBar.ButtonData.OK_DONE);
        if (canEdit) dialog.getDialogPane().getButtonTypes().add(saveBtn);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        GridPane grid = new GridPane();
        grid.setHgap(15); grid.setVgap(15); grid.setPadding(new Insets(20));

        // সব ফিল্ড ডিক্লেয়ার করা
        TextField titleField = new TextField(task.title()); titleField.setEditable(canEdit);
        TextField roomField = new TextField(task.roomNo() == null || task.roomNo().equals("null") ? "" : task.roomNo()); roomField.setEditable(canEdit);
        TextArea descArea = new TextArea(task.tags() == null || task.tags().equals("null") ? "" : task.tags()); descArea.setEditable(canEdit); descArea.setPrefRowCount(3); descArea.setWrapText(true);
        
        // 🌟 CT স্পেসিফিক ফিল্ড
        TextField ctCourseField = new TextField(task.ctCourse() == null || task.ctCourse().equals("null") ? "" : task.ctCourse()); 
        ctCourseField.setEditable(canEdit); ctCourseField.setPromptText("e.g., CSE 105");
        
        TextArea ctSyllabusArea = new TextArea(task.ctSyllabus() == null || task.ctSyllabus().equals("null") ? "" : task.ctSyllabus()); 
        ctSyllabusArea.setEditable(canEdit); ctSyllabusArea.setPromptText("Enter Exam Syllabus / Notes here..."); 
        ctSyllabusArea.setPrefRowCount(4); ctSyllabusArea.setWrapText(true);

        // 🌟 NEW: Personal Task স্পেসিফিক ফিল্ড (Importance Dropdown)
        javafx.scene.control.ComboBox<String> importanceBox = new javafx.scene.control.ComboBox<>();
        importanceBox.getItems().addAll("High", "Medium", "Low");
        importanceBox.setValue(task.importance() != null ? task.importance() : "Medium");
        importanceBox.setDisable(!canEdit);

        // 🌟 UI সাজানো (CT, Personal এবং Routine এর জন্য আলাদা লেআউট)
        if (isCT) {
            grid.add(new Label("📌 CT Title:"), 0, 0); grid.add(titleField, 1, 0);
            grid.add(new Label("📘 CT Course:"), 0, 1); grid.add(ctCourseField, 1, 1);
            grid.add(new Label("📍 CT Room:"), 0, 2); grid.add(roomField, 1, 2);
            grid.add(new Label("🕒 CT Time:"), 0, 3); grid.add(new Label(task.startTime()), 1, 3);
            grid.add(new Label("📚 CT Syllabus/Notes:"), 0, 4); grid.add(ctSyllabusArea, 1, 4);
        } else if (isPersonal) {
            // 🌟 পার্সোনাল টাস্কের লেআউট
            grid.add(new Label("📌 Title:"), 0, 0); grid.add(titleField, 1, 0);
            grid.add(new Label("🕒 Time:"), 0, 1); grid.add(new Label(task.startTime() + " (" + task.date() + ")"), 1, 1);
            grid.add(new Label("🔥 Importance:"), 0, 2); grid.add(importanceBox, 1, 2); // ড্রপডাউন
            grid.add(new Label("📝 Description:"), 0, 3); grid.add(descArea, 1, 3);
        } else {
            // সাধারণ রুটিনের লেআউট
            grid.add(new Label("📌 Title:"), 0, 0); grid.add(titleField, 1, 0);
            grid.add(new Label("🕒 Time slot:"), 0, 1); grid.add(new Label(task.startTime()), 1, 1);
            grid.add(new Label("📍 Room No:"), 0, 2); grid.add(roomField, 1, 2);
            grid.add(new Label("📚 Description:"), 0, 3); grid.add(descArea, 1, 3);
        }

        dialog.getDialogPane().setContent(grid);

        dialog.showAndWait().ifPresent(response -> {
            if (response == saveBtn) {
                // UI থেকে ডেটা নেওয়া
                String newTitle = titleField.getText().trim();
                String newRoom = roomField.getText().trim();
                String newDesc = isCT ? descArea.getText().trim() : descArea.getText().trim(); // সাধারণ ডেসক্রিপশন
                String newCtCourse = isCT ? ctCourseField.getText().trim() : task.ctCourse();
                String newCtSyllabus = isCT ? ctSyllabusArea.getText().trim() : task.ctSyllabus();
                String newImportance = isPersonal ? importanceBox.getValue() : task.importance(); // 🌟 নতুন Importance

                new Thread(() -> {
                    // 🌟 Supabase-এ সেভ করা (৭ প্যারামিটার পাঠানো হচ্ছে)
                    boolean isUpdated = dataService.updateTaskDetails(task.id(), newTitle, newRoom, newDesc, newCtCourse, newCtSyllabus, newImportance);
                    
                    if (isUpdated) {
                        Platform.runLater(() -> {
                            // অ্যাপের লোকাল মেমোরি আপডেট করা
                           // লোকাল লিস্ট আপডেট (১৩ টি প্যারামিটার)
                            for (int i = 0; i < allTasks.size(); i++) {
                                if (allTasks.get(i).id().equals(task.id())) {
                                    allTasks.set(i, new StudyTask(
                                        task.id(), newTitle, task.date(), task.startTime(), 
                                        task.durationMinutes(), newRoom, task.type(), newDesc, 
                                        task.creatorRole(), newCtCourse, newCtSyllabus, 
                                        task.status(), newImportance // 🌟 ফিক্স: task.importance() এর বদলে newImportance যোগ করা হলো
                                    ));
                                    break;
                                }
                            }
                            refreshTimeline(); // UI রিফ্রেশ করা
                            showSuccess("Task Details Saved to Supabase Successfully! 🚀");
                        });
                    }
                }).start();
            }
        });
    }



    //controlling backlogs

    // ==========================================================
    // 📅 RESCHEDULE BACKLOG TASK DIALOG
    // ==========================================================
    private void showRescheduleDialog(StudyTask task) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("📅 Reschedule Task");
        dialog.setHeaderText("Reschedule '" + task.title() + "' to a new date and time.");

        ButtonType saveBtn = new ButtonType("Reschedule 🚀", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveBtn, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(15); grid.setVgap(15); grid.setPadding(new Insets(20));

        // 🌟 নতুন ডেট এবং টাইম ইনপুট নেওয়ার ফিল্ড
        DatePicker newDatePicker = new DatePicker(LocalDate.now()); // ডিফল্টভাবে আজকের ডেট সিলেক্ট করা থাকবে
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
                    // 🌟 ডাটাবেসে PENDING স্ট্যাটাস এবং নতুন ডেট-টাইম দিয়ে আপডেট করা
                    boolean isUpdated = dataService.updateTaskStatus(task.id(), "PENDING", newDate, newTime);
                    
                    if (isUpdated) {
                        Platform.runLater(() -> {
                            // লোকাল লিস্ট আপডেট করা
                            for (int i = 0; i < allTasks.size(); i++) {
                                if (allTasks.get(i).id().equals(task.id())) {
                                    allTasks.set(i, new StudyTask(
                                        task.id(), task.title(), newDate, newTime, 
                                        task.durationMinutes(), task.roomNo(), task.type(), task.tags(), 
                                        task.creatorRole(), task.ctCourse(), task.ctSyllabus(), 
                                        "PENDING", task.importance() // স্ট্যাটাস আবার PENDING হয়ে গেল
                                    ));
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
        javafx.scene.control.ComboBox<String> importanceBox = new javafx.scene.control.ComboBox<>();
        importanceBox.getItems().addAll("High", "Medium", "Low");
        importanceBox.setValue("Medium");
        TextArea descArea = new TextArea(); descArea.setPromptText("Notes..."); descArea.setPrefRowCount(3);

        grid.add(new Label("📌 Title:"), 0, 0); grid.add(titleField, 1, 0);
        grid.add(new Label("📅 Date:"), 0, 1); grid.add(datePicker, 1, 1);
        grid.add(new Label("🕒 Time:"), 0, 2); grid.add(timeField, 1, 2);
        grid.add(new Label("🔥 Importance:"), 0, 3); grid.add(importanceBox, 1, 3);
        grid.add(new Label("📝 Notes:"), 0, 4); grid.add(descArea, 1, 4);

        dialog.getDialogPane().setContent(grid);

        dialog.showAndWait().ifPresent(response -> {
            if (response == addBtn && !titleField.getText().trim().isEmpty()) {
                // 🌟 ১৩ প্যারামিটারের মডেল! (Id null, Type: PERSONAL, Status: PENDING)
                StudyTask newTask = new StudyTask(
                    null, titleField.getText().trim(), datePicker.getValue().toString(), timeField.getText().trim(),
                    60, null, "PERSONAL", descArea.getText().trim(), AuthService.CURRENT_USER_ROLE, 
                    null, null, "PENDING", importanceBox.getValue()
                );

              new Thread(() -> {
                    List<StudyTask> listToSave = new java.util.ArrayList<>();
                    listToSave.add(newTask);
                    
                    // 🌟 ফিক্স: if কন্ডিশন সরিয়ে সরাসরি সেভ মেথড কল করা হলো
                    dataService.saveTasks(listToSave);
                    
                    Platform.runLater(() -> {
                        loadTasksFromDatabase(); // ডাটাবেস থেকে আবার ফ্রেশ লোড করে টাইমলাইন আপডেট
                        showSuccess("Personal Task Added! 🎉");
                    });
                }).start();
            }
        });
    }


}