package com.scholar.controller;

import com.scholar.controller.ai.AITutorController;
import com.scholar.controller.collaboration.CollaborationController;
import com.scholar.controller.community.CommunityController;
import com.scholar.controller.community.DiscussionController;
import com.scholar.controller.community.ResourceTableController;
import com.scholar.controller.community.ResourceUploadController;
import com.scholar.controller.community.StatisticsController;
import com.scholar.controller.community.CommunityRagController;
import com.scholar.controller.dashboard.AdminBroadcastController;
import com.scholar.controller.dashboard.CalendarController;
import com.scholar.controller.dashboard.RoutineController;
import com.scholar.controller.dashboard.TaskController;
import com.scholar.controller.eca.ECAController;
import com.scholar.controller.settings.ProfileController;
import com.scholar.model.ResourceRow;
import com.scholar.model.StudyTask;
import com.scholar.service.*;
import com.scholar.util.PopupHelper;
import javafx.application.Platform;
import javafx.event.Event;
import org.springframework.beans.factory.annotation.Qualifier;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.web.WebView;
import javafx.stage.Window;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Controller
public class DashboardController {

    // =========================================================
    // SUB-CONTROLLERS
    // =========================================================
    @Autowired private TaskController             taskController;
    @Autowired private CalendarController         calendarController;
    @Autowired private RoutineController          routineController;
    @Autowired private AdminBroadcastController   adminBroadcastController;

    @Autowired
    @Qualifier("dashCommunityController")
    private CommunityController communityController;

    @Autowired private AdminChatToggleController  adminChatToggleController;
    @Autowired private ResourceTableController    resourceTableController;
    @Autowired private ResourceUploadController   resourceUploadController;
    @Autowired private DiscussionController       discussionController;
    @Autowired private StatisticsController       statisticsController;
    @Autowired private CommunityRagController     communityRagController;
    @Autowired private CollaborationController    collaborationController;
    @Autowired private AITutorController          aiTutorController;
    @Autowired private ECAController              ecaController;
    @Autowired private ProfileController          profileController;
    @Autowired private AdminMergeController       adminMergeController;

    // =========================================================
    // SERVICES
    // =========================================================
    @Autowired private AISchedulerService aiService;
    @Autowired private ResourceService    resourceService;
    @Autowired private DataService        dataService;

    // =========================================================
    // SHARED STATE
    // =========================================================
    private final List<StudyTask> allTasks = new ArrayList<>();
    private LocalDate selectedDate           = LocalDate.now();
    private String    currentViewMode        = "DAILY";
    private Integer   currentSelectedTopicId = null;

    // =========================================================
    // FXML INJECTIONS
    // =========================================================
    @FXML private Label    channelNameLabel;
    @FXML private TextArea inputArea;
    @FXML private Button   generateBtn;
    @FXML private GridPane routineGrid;
    @FXML private VBox     announcementList;
    @FXML private VBox     timelineContainer;
    @FXML private Button   editRoutineBtn;
    @FXML private Button   postAnnouncementBtn;
    @FXML private Button   reverseAllClassOffBtn;
    @FXML private Button   classOffBtn;
    @FXML private Button   manualNoticeBtn;
    @FXML private Button   btnDailyTasks;
    @FXML private Button   btnBacklog;
    @FXML private Button   btnCompleted;
    @FXML private VBox     adminControlsBox;

    @FXML private VBox  channelInfoBox;
    @FXML private Label channelTitleLabel;
    @FXML private Label channelDescLabel;
    @FXML private Button createChannelBtn;

    // AI Tutor
    @FXML private WebView   aiWebView;
    @FXML private TextField aiTopicInput;
    @FXML private TextField aiQuestionInput;
    @FXML private Button    aiAskBtn;
    @FXML private Button    aiTutorBtn;

    // Calendar
    @FXML private Label    monthLabel;
    @FXML private GridPane calendarGrid;

    // Resources
    @FXML private TextField resTitle;
    @FXML private TextArea  resInput;
    @FXML private VBox      resourceList;

    // Community
    @FXML private TreeView<String>             communityTree;
    @FXML private TextField                    communitySearchField;
    @FXML private Label                        currentFolderLabel;
    @FXML private TableView<ResourceRow>       resourceTable;
    @FXML private TableColumn<ResourceRow, String> colResName;
    @FXML private TableColumn<ResourceRow, String> colResType;
    @FXML private TableColumn<ResourceRow, String> colResDiff;
    @FXML private TableColumn<ResourceRow, String> colResUploader;
    @FXML private TableColumn<ResourceRow, String> colResTags;
    @FXML private TableColumn<ResourceRow, String> colResVotes;
    @FXML private TableColumn<ResourceRow, Void>   colResAction;
    @FXML private WebView   ragChatWebView;

    @FXML private ComboBox<String>   typeFilterCombo;
    @FXML private ComboBox<String>   diffFilterCombo;
    @FXML private ComboBox<String>   sortCombo;
    @FXML private TextField          globalSearchField;
    @FXML private Button             clearFiltersBtn;
    @FXML private Button             uploadResourceBtn;
    @FXML private Label              statusBarLabel;
    @FXML private Label              loadingLabel;
    @FXML private ProgressIndicator  loadingIndicator;
    @FXML private Label              resourceCountLabel;
    @FXML private Label              breadcrumbCourse;
    @FXML private Label              breadcrumbSegment;

    // Collaboration
    @FXML private VBox channelList;
    @FXML private VBox teamList;
    @FXML private VBox roomContainer;

    // ECA
    @FXML private VBox ecaContainer;

    // Settings
    @FXML private TextField settingsName;
    @FXML private TextField settingsUsername;
    @FXML private Label     settingsEmail;

    // Admin
    @FXML private Tab adminTab;
    @FXML private VBox pendingListContainer;
    @FXML private Tab questionBankTab;

    // Merge tab
    @FXML public void onSaveSettings()      { adminMergeController.onSaveSettings(); }
    @FXML public void onLoadChannels()      { adminMergeController.onLoadChannels(); }
    @FXML public void onSendRequest()       { adminMergeController.onSendRequest(); }
    @FXML public void onLoadRequests()      { adminMergeController.onLoadRequests(); }
    @FXML public void onAcceptRequest()     { adminMergeController.onAcceptRequest(); }
    @FXML public void onRejectRequest()     { adminMergeController.onRejectRequest(); }
    @FXML public void onLoadActiveMerges()  { adminMergeController.onLoadActiveMerges(); }
    @FXML public void onInstantUnmerge()    { adminMergeController.onInstantUnmerge(); }

    @FXML private CheckBox         allowMergeCheck;
    @FXML private ComboBox<String> privacyCombo;
    @FXML private ComboBox<String> mergeTypeCombo;
    @FXML private TextField        durationField;
    @FXML private ListView<String> availableChannelsList;
    @FXML private ListView<String> pendingRequestsList;
    @FXML private ListView<String> activeHubsList;
    @FXML private Label            settingsStatusLabel;
    @FXML private Label            discoverStatusLabel;
    @FXML private Label            requestStatusLabel;
    @FXML private Label            hubStatusLabel;

    // =========================================================
    // INITIALIZE
    // =========================================================
    @FXML
    public void initialize() {
        System.out.println("ScholarGrid Dashboard Initializing...");

        new Thread(() -> dataService.autoMoveToBacklog()).start();

        // Tasks
        taskController.init(
            allTasks, timelineContainer,
            () -> selectedDate,
            () -> currentViewMode,
            () -> calendarController.drawCalendar(selectedDate),
            periods -> {
                calendarController.setClassOffPeriods(periods);
                calendarController.drawCalendar(selectedDate);
            });

        if (btnDailyTasks != null) btnDailyTasks.setOnAction(e -> { currentViewMode = "DAILY";     taskController.refreshTimeline(); });
        if (btnBacklog    != null) btnBacklog.setOnAction(e    -> { currentViewMode = "BACKLOG";    taskController.refreshTimeline(); });
        if (btnCompleted  != null) btnCompleted.setOnAction(e  -> { currentViewMode = "COMPLETED";  taskController.refreshTimeline(); });

        // Calendar
        calendarController.init(calendarGrid, monthLabel, allTasks, () -> selectedDate, clicked -> {
            selectedDate = clicked;
            taskController.refreshTimeline();
            calendarController.drawCalendar(selectedDate);
        });
        calendarController.drawCalendar(selectedDate);

        adminMergeController.myChannelId = AuthService.CURRENT_CHANNEL_ID;

        // Routine
        routineController.init(routineGrid, announcementList);

        // Admin broadcast
        adminBroadcastController.init(pendingListContainer, allTasks, () ->
            taskController.loadTasksFromDatabase(() -> calendarController.drawCalendar(selectedDate))
        );

        // Admin merge
        adminMergeController.allowMergeCheck       = allowMergeCheck;
        adminMergeController.privacyCombo          = privacyCombo;
        adminMergeController.mergeTypeCombo        = mergeTypeCombo;
        adminMergeController.durationField         = durationField;
        adminMergeController.availableChannelsList = availableChannelsList;
        adminMergeController.pendingRequestsList   = pendingRequestsList;
        adminMergeController.activeHubsList        = activeHubsList;
        adminMergeController.settingsStatusLabel   = settingsStatusLabel;
        adminMergeController.discoverStatusLabel   = discoverStatusLabel;
        adminMergeController.requestStatusLabel    = requestStatusLabel;
        adminMergeController.hubStatusLabel        = hubStatusLabel;

        adminMergeController.initAndLoad();

        // Channel setup
        if (AuthService.CURRENT_CHANNEL_ID != -1) {
            if (channelNameLabel != null) channelNameLabel.setText(AuthService.CURRENT_CHANNEL_NAME);
            routineController.loadClassRoutine();
            routineController.loadClassAnnouncements();
        }

        // Admin tab security
        if (adminTab != null) {
            if (!"admin".equals(AuthService.CURRENT_USER_ROLE)) {
                adminTab.setDisable(true);
                adminTab.setText("Admin Only");
                if (editRoutineBtn      != null) editRoutineBtn.setVisible(false);
                if (postAnnouncementBtn != null) postAnnouncementBtn.setVisible(false);
                if (classOffBtn         != null) classOffBtn.setVisible(false);
                if (manualNoticeBtn     != null) manualNoticeBtn.setVisible(false);
                if (reverseAllClassOffBtn != null) reverseAllClassOffBtn.setVisible(false);
            } else {
                if (editRoutineBtn      != null) editRoutineBtn.setOnAction(e -> adminBroadcastController.showAddRoutineDialog());
                if (postAnnouncementBtn != null) postAnnouncementBtn.setOnAction(e -> adminBroadcastController.showAddAnnouncementDialog());
            }
        }
        if ("admin".equals(AuthService.CURRENT_USER_ROLE) && adminControlsBox != null) {
            adminChatToggleController.init(adminControlsBox);
        }

        // ── Community ─────────────────────────────────────────
        if (communityTree != null && communityController != null) {

            communityController.init(communityTree, currentFolderLabel, topicId -> {
                currentSelectedTopicId = topicId;
                resourceTableController.setCurrentTopicId(topicId);
                resourceTableController.loadResourcesForTopic(topicId);
                updateBreadcrumb(topicId);
            });

            if (resourceTable != null) {
                resourceTableController.init(
                    resourceTable, colResName, colResType, colResDiff,
                    colResUploader, colResTags, colResVotes, colResAction,
                    discussionController, statisticsController);

                resourceTableController.initExtras(
                    typeFilterCombo, diffFilterCombo, sortCombo,
                    statusBarLabel, loadingLabel, loadingIndicator, resourceCountLabel);
            }

            resourceUploadController.init(resourceTableController::loadResourcesForTopic);
            if (uploadResourceBtn != null) {
                uploadResourceBtn.setOnAction(e -> triggerUpload());
            }

            if (globalSearchField != null) {
                globalSearchField.textProperty().addListener((obs, old, text) -> {
                    if (text == null || text.isBlank()) resourceTableController.clearSearch();
                    else                               resourceTableController.filterByText(text.trim().toLowerCase());
                });
            }

            if (clearFiltersBtn != null) {
                clearFiltersBtn.setOnAction(e -> {
                    if (typeFilterCombo   != null) typeFilterCombo.setValue("All");
                    if (diffFilterCombo   != null) diffFilterCombo.setValue("All");
                    if (sortCombo         != null) sortCombo.setValue("Default");
                    if (globalSearchField != null) globalSearchField.clear();
                });
            }

            if (communitySearchField != null) {
                communitySearchField.textProperty().addListener((obs, old, text) -> {
                    if (communityController != null) communityController.applySearch(text);
                });
            }

            communityController.onRefreshCommunity();
        }

        // ── Community RAG Chat ───────────────────────────────
        if (ragChatWebView != null && communityRagController != null) {
            String uid = AuthService.CURRENT_USER_ID != null
                ? AuthService.CURRENT_USER_ID.toString()
                : null;
            communityRagController.init(ragChatWebView, uid);
        }

        // ── Collaboration ─────────────────────────────────────
        collaborationController.init(channelList, teamList, roomContainer, channelInfoBox, channelTitleLabel, channelDescLabel, createChannelBtn);
        collaborationController.loadChannels();
        collaborationController.loadTeamsForChannel(1, "Hackathons");

        // ── AI Tutor — RAG INTEGRATION ────────────────────────
        // Pass the logged-in user's UUID so RAG can personalize responses
        if (AuthService.CURRENT_USER_ID != null) {
            aiTutorController.setCurrentUserId(AuthService.CURRENT_USER_ID.toString());
        }
        Platform.runLater(() -> {
            WebView resolvedWebView = resolveAiWebView();
            if (resolvedWebView == null) {
                System.err.println("❌ DashboardController: aiWebView not injected or not found in scene.");
                return;
            }
            aiTutorController.initInlineTab(resolvedWebView, aiTopicInput, aiQuestionInput, aiAskBtn);
        });
        if (aiTutorBtn != null) aiTutorBtn.setOnAction(e -> aiTutorController.showAITutorPanel());

        // ── ECA ───────────────────────────────────────────────
        ecaController.init(ecaContainer);

        // ── Profile ───────────────────────────────────────────
        profileController.init(settingsName, settingsUsername, settingsEmail);
        profileController.loadProfileSettings();

        // ── Question Bank ─────────────────────────────────────
        loadQuestionBankTab();

        // ── Tasks last ────────────────────────────────────────
        taskController.loadTasksFromDatabase(() -> calendarController.drawCalendar(selectedDate));
        loadResources();
    }

    private void loadQuestionBankTab() {
        if (questionBankTab == null) return;
        try {
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/com/scholar/view/question_bank.fxml"));
            Node content = loader.load();
            questionBankTab.setContent(content);
        } catch (IOException e) {
            System.err.println("Failed to load Question Bank Tab");
        }
    }

    @FXML
    public void onGenerateClick() {
        String userText = inputArea.getText().trim();
        if (userText.isEmpty()) return;
        generateBtn.setText("Thinking..."); generateBtn.setDisable(true);

        new Thread(() -> {
            try {
                List<StudyTask> aiTasks = aiService.generateSchedule(userText);
                if (aiTasks == null || aiTasks.isEmpty()) {
                    Platform.runLater(() -> {
                        showError("AI couldn't understand the input. Please write more clearly!");
                        generateBtn.setText("Generate Routine"); generateBtn.setDisable(false);
                    });
                    return;
                }
                List<StudyTask> finalTasks = new ArrayList<>();
                String today = LocalDate.now().toString();
                for (StudyTask t : aiTasks) {
                    String date = (t.date() != null && t.date().matches("\\d{4}-\\d{2}-\\d{2}")) ? t.date() : today;
                    finalTasks.add(new StudyTask(
                        null, t.title(), date, t.startTime(),
                        t.durationMinutes() != null ? t.durationMinutes() : 60,
                        t.roomNo(), "PERSONAL", t.tags(), "student",
                        t.ctCourse(), t.ctSyllabus(), "PENDING", "Medium"));
                }
                boolean saved = dataService.saveTasks(finalTasks);
                Platform.runLater(() -> {
                    if (saved) {
                        taskController.loadTasksFromDatabase(() -> calendarController.drawCalendar(selectedDate));
                        showSuccess("Personal Task Added to Timeline!");
                    } else showError("Database Error: Failed to save task.");
                    generateBtn.setText("Generate Routine"); generateBtn.setDisable(false);
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    showError("System Error: " + e.getMessage());
                    generateBtn.setText("Generate Routine"); generateBtn.setDisable(false);
                });
            }
        }).start();
    }

    @FXML
    public void showManualTaskEntryDialog() { taskController.showManualTaskEntryDialog(); }

    // =========================================================
    // COMMUNITY — FXML @FXML action delegates
    // =========================================================
    @FXML public void onRefreshCommunity() {
        if (communityController != null) communityController.onRefreshCommunity();
    }

    @FXML public void onAddNewFolder() {
        if (communityController != null) communityController.onAddCourse();
    }

    @FXML
    public void onUploadResourceClick() { triggerUpload(); }

    private void triggerUpload() {
        Window owner = resolveOwner();
        resourceUploadController.onUploadResourceClick(currentSelectedTopicId, owner);
    }

    // =========================================================
    // COLLABORATION — delegates
    // =========================================================
    @FXML
    public void onCreatePost(javafx.event.ActionEvent event) {
        if (collaborationController != null) {
            collaborationController.onCreatePost(event);
        }
    }
    @FXML public void onCreateChannel() { collaborationController.onCreateChannel(); }
    @FXML public void onAddTeamResourceClick() {
        showError("Please select a team workspace first and use the 'Add Resource' button inside it.");
    }

    @FXML public void loadPendingRequests()  { adminBroadcastController.loadPendingRequests(); }
    @FXML public void onBroadcastRoutine()   { adminBroadcastController.showAddRoutineDialog(); }
    @FXML public void onBroadcastNotice()    { adminBroadcastController.showAddAnnouncementDialog(); }
    @FXML public void onReverseAllClassOff() { routineController.reverseAllClassOffPeriods(); }
    @FXML public void onClassOffPrompt()     { adminBroadcastController.showClassOffDialog(); }
    @FXML public void onManualNotice()       { routineController.showManualAnnouncementDialog(); }

    @FXML public void handleECATrackerSelect(Event event) { ecaController.handleECATrackerSelect(event); }
    @FXML public void forceRefreshECA()                   { ecaController.forceRefreshECA(); }

    @FXML public void loadProfileSettings()        { profileController.loadProfileSettings(); }
    @FXML public void onSaveProfile()              { profileController.onSaveProfile(); }
    @FXML public void handleLogoutTab(Event event) { profileController.handleLogoutTab(event); }

    @FXML
    public void onGenerateResource() {
        String topic   = resTitle.getText().trim();
        String content = resInput.getText().trim();
        if (topic.isEmpty() || content.isEmpty()) return;
        resInput.setText("AI Analyzing content...");
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
                    Button b = new Button(item.title());
                    b.setMaxWidth(Double.MAX_VALUE);
                    b.setStyle("-fx-alignment: center-left; -fx-background-color: white; -fx-border-color: #bdc3c7;");
                    b.setOnAction(e -> PopupHelper.showInfo(resolveOwner(), item.title(), item.content()));
                    resourceList.getChildren().add(b);
                }
            });
        }).start();
    }

    // =========================================================
    // BREADCRUMB
    // =========================================================
    private void updateBreadcrumb(Integer topicId) {
        if (breadcrumbCourse == null && breadcrumbSegment == null) return;
        if (topicId == null) {
            if (breadcrumbCourse  != null) breadcrumbCourse.setText("—");
            if (breadcrumbSegment != null) breadcrumbSegment.setText("—");
            return;
        }

        if (communityController != null && communityController.topicMap != null) {
            communityController.topicMap.forEach((node, id) -> {
                if (id.equals(topicId) && node.getParent() != null) {
                    String seg = node.getParent().getValue().replace("  ", "").replace("📂 ", "");
                    if (breadcrumbSegment != null) breadcrumbSegment.setText(seg);

                    if (node.getParent().getParent() != null
                        && node.getParent().getParent().getParent() != null) {
                        String course = node.getParent().getParent().getParent()
                            .getValue().replace("📘 ", "");
                        if (breadcrumbCourse != null) breadcrumbCourse.setText(course);
                    }
                }
            });
        }
    }

    private Window resolveOwner() {
        if (resourceTable  != null && resourceTable.getScene()  != null) return resourceTable.getScene().getWindow();
        if (communityTree  != null && communityTree.getScene()  != null) return communityTree.getScene().getWindow();
        if (generateBtn    != null && generateBtn.getScene()    != null) return generateBtn.getScene().getWindow();
        return null;
    }

    private void showSuccess(String msg) {
        Platform.runLater(() -> PopupHelper.showInfo(resolveOwner(), "Success", msg));
    }

    private void showError(String msg) {
        Platform.runLater(() -> PopupHelper.showError(resolveOwner(), "Error", msg));
    }

    private WebView resolveAiWebView() {
        if (aiWebView != null) return aiWebView;
        Node anchor = aiTutorBtn != null ? aiTutorBtn
                    : (generateBtn != null ? generateBtn : null);
        if (anchor == null || anchor.getScene() == null) return null;
        Node found = anchor.getScene().lookup("#aiWebView");
        return (found instanceof WebView) ? (WebView) found : null;
    }
}
