package com.scholar.controller;

import com.scholar.model.*;
import com.scholar.service.AuthService;
import com.scholar.service.ChatSettingsService;
import com.scholar.service.SocialZoneService;
import com.scholar.util.NewPopupHelper;
import com.scholar.util.PopupHelper;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Controller;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Controller
public class SocialZoneController {

    // ── Chat
    @FXML private VBox       chatContainer;
    @FXML private ScrollPane chatScroll;
    @FXML private TextField  messageInput;
    @FXML private Label      chatHeaderLabel;

    // ── Audio
    @FXML private Button joinAudioBtn, leaveAudioBtn, muteAudioBtn;
    @FXML private Label  activeVoiceUsersLabel;

    // ── Layout panels
    @FXML private BorderPane chatMainContainer;
    @FXML private BorderPane dailyThreadsContainer;
    @FXML private VBox       messageRequestsContainer;
    @FXML private BorderPane notificationsContainer;

    // ── Threads
    @FXML private VBox  threadsFeedBox;
    @FXML private Label threadsCategoryLabel;
    @FXML private Label threadsSubtitleLabel;

    // ── Sidebar dynamic areas
    @FXML private VBox requestsContainer;
    @FXML private VBox contactsContainer;
    @FXML private VBox notificationsListContainer;

    // ── Badges
    @FXML private Label requestBadge;
    @FXML private Label notificationBadge;
    @FXML private Label currentUserLabel;

    @Autowired private com.scholar.service.AudioCallService audioCallService;
    @Autowired private SocialZoneService socialService;
    @Autowired private ChatSettingsService chatSettingsService;
    @Autowired private ApplicationContext springContext;

    // ── State
    private String currentChatMode           = "GROUP";
    private String currentPrivateContactId   = null;
    private String currentPrivateContactName = null;
    private String currentThreadCategory     = "HOME";

    // ── Last seen message id for smart refresh
    private String lastSeenMessageId = null;

    // ── Shared thread pool — daemon threads die cleanly on app close
    private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r);
        t.setDaemon(true);
        return t;
    });

    // ─────────────────────────────────────────────────────────
    //  INIT
    // ─────────────────────────────────────────────────────────

    @FXML
    public void initialize() {
        if (currentUserLabel != null && AuthService.CURRENT_USER_NAME != null)
            currentUserLabel.setText(AuthService.CURRENT_USER_NAME);

        loadMessages();
        refreshSidebar();
        startAutoRefresh();
    }

    private void startAutoRefresh() {
        Timeline refresh = new Timeline(new KeyFrame(Duration.seconds(3), e -> {
            loadMessages();
            refreshSidebar();
        }));
        refresh.setCycleCount(Timeline.INDEFINITE);
        refresh.play();
    }

    private Window getOwner() {
        if (chatMainContainer != null && chatMainContainer.getScene() != null)
            return chatMainContainer.getScene().getWindow();
        return null;
    }

    // ─────────────────────────────────────────────────────────
    //  NAVIGATION
    // ─────────────────────────────────────────────────────────

    private void hideAllPanels() {
        setVisible(chatMainContainer,        false);
        setVisible(dailyThreadsContainer,    false);
        setVisible(messageRequestsContainer, false);
        setVisible(notificationsContainer,   false);
    }

    private void setVisible(Node node, boolean v) {
        if (node != null) { node.setVisible(v); node.setManaged(v); }
    }

    @FXML public void showGroupChatView(ActionEvent event) {
        hideAllPanels(); setVisible(chatMainContainer, true);
        currentChatMode = "GROUP"; currentPrivateContactId = null;
        if (chatHeaderLabel != null) chatHeaderLabel.setText("🌍 Global Group Chat");
        loadMessages();
        applyChatInputState();
    }

    @FXML public void showDailyThreadsView(ActionEvent event) {
        hideAllPanels(); setVisible(dailyThreadsContainer, true);
        showThreadCategory("HOME", "🌟 Daily Threads", "Share your thoughts with the community");
    }

    @FXML public void showMessageRequestsView(ActionEvent event) {
        hideAllPanels(); setVisible(messageRequestsContainer, true);
    }

    @FXML public void showNotificationsView(ActionEvent event) {
        hideAllPanels(); setVisible(notificationsContainer, true);
        loadNotifications();
        setVisible(notificationBadge, false);
    }

    // ─────────────────────────────────────────────────────────
    //  THREAD CATEGORY NAV
    // ─────────────────────────────────────────────────────────

    @FXML public void showThreadsHome      (ActionEvent e) { gotoThreads("HOME",       "🏠 Home Feed",        "Latest posts from your community"); }
    @FXML public void showPublicThreads    (ActionEvent e) { gotoThreads("PUBLIC",     "🌐 Public Threads",   "Posts visible to everyone"); }
    @FXML public void showThreadsAcademics (ActionEvent e) { gotoThreads("ACADEMICS",  "📚 Academics",        "Lecture notes, study tips & academic help"); }
    @FXML public void showThreadsEvents    (ActionEvent e) { gotoThreads("EVENTS",     "🎉 Events",           "Upcoming campus events & announcements"); }
    @FXML public void showThreadsPlacements(ActionEvent e) { gotoThreads("PLACEMENTS", "💼 Placements",       "Jobs, internships & career opportunities"); }
    @FXML public void showThreadsIssues   (ActionEvent e) { gotoThreads("ISSUES",     "🚨 Issues",           "Report problems & campus concerns"); }
    @FXML public void showMyThreads        (ActionEvent e) { gotoThreads("MY_THREADS", "👤 My Threads",       "All your posts in one place"); }
    @FXML public void showSavedThreads     (ActionEvent e) { gotoThreads("SAVED",      "🔖 Saved Threads",    "Bookmarked posts"); }

    private void gotoThreads(String category, String title, String subtitle) {
        hideAllPanels(); setVisible(dailyThreadsContainer, true);
        showThreadCategory(category, title, subtitle);
    }

    private void showThreadCategory(String category, String title, String subtitle) {
        currentThreadCategory = category;
        if (threadsCategoryLabel != null) threadsCategoryLabel.setText(title);
        if (threadsSubtitleLabel != null) threadsSubtitleLabel.setText(subtitle);
        loadThreadsForCategory(category);
    }

    // ─────────────────────────────────────────────────────────
    //  CHAT — SEND & LOAD
    // ─────────────────────────────────────────────────────────

    @FXML
    public void onSendMessage(ActionEvent event) {
        String text = messageInput.getText().trim();
        if (text.isEmpty()) return;

        if ("GROUP".equals(currentChatMode)
                && !chatSettingsService.isPublicChatEnabled()
                && !"admin".equals(AuthService.CURRENT_USER_ROLE)) {
            NewPopupHelper.showToast(getOwner(), "🔒 Public chat is currently disabled by admin.");
            return;
        }

        messageInput.clear();
        executor.submit(() -> {
            boolean ok = "GROUP".equals(currentChatMode)
                    ? socialService.sendMessage(text)
                    : (currentPrivateContactId != null && socialService.sendPrivateMessage(currentPrivateContactId, text));
            if (ok) Platform.runLater(this::loadMessages);
            else    Platform.runLater(() -> messageInput.setText(text)); // restore on failure
        });
    }

    private void loadMessages() {
        executor.submit(() -> {
            List<ChatMessage> messages = "GROUP".equals(currentChatMode)
                    ? socialService.getRecentMessages()
                    : (currentPrivateContactId != null
                        ? socialService.getPrivateMessages(currentPrivateContactId)
                        : List.of());

            Platform.runLater(() -> {
                if (messages.isEmpty()) return;
                String newestId = messages.get(messages.size() - 1).id();
                // Only redraw if something new arrived
                if (newestId.equals(lastSeenMessageId) &&
                        chatContainer.getChildren().size() == messages.size()) return;
                lastSeenMessageId = newestId;
                chatContainer.getChildren().clear();
                for (ChatMessage msg : messages)
                    chatContainer.getChildren().add(createMessageBubble(msg));
                scrollToBottom();
            });
        });
    }

    private void scrollToBottom() {
        Platform.runLater(() -> chatScroll.setVvalue(1.0));
    }

    private void applyChatInputState() {
        if (messageInput == null || !"GROUP".equals(currentChatMode)) return;
        boolean enabled = chatSettingsService.isPublicChatEnabled()
                || "admin".equals(AuthService.CURRENT_USER_ROLE);
        messageInput.setDisable(!enabled);
        if (enabled) {
            messageInput.setPromptText("Type a message…");
            messageInput.setStyle("-fx-background-color:#1e2738;-fx-text-fill:#e2e8f0;" +
                    "-fx-prompt-text-fill:#475569;-fx-background-radius:22;-fx-padding:11 18;" +
                    "-fx-border-color:#2d3748;-fx-border-radius:22;-fx-font-size:13px;");
        } else {
            messageInput.setPromptText("🔒 Public chat disabled by admin");
            messageInput.setStyle("-fx-background-color:#1a1a1a;-fx-text-fill:#6b7280;" +
                    "-fx-prompt-text-fill:#6b7280;-fx-background-radius:22;-fx-padding:11 18;" +
                    "-fx-border-color:#374151;-fx-border-radius:22;-fx-font-size:13px;-fx-opacity:0.6;");
        }
    }

    // ─────────────────────────────────────────────────────────
    //  MESSAGE BUBBLE  (with clickable sender → profile popup)
    // ─────────────────────────────────────────────────────────

    private HBox createMessageBubble(ChatMessage msg) {
        VBox bubble = new VBox(3);
        bubble.setPadding(new Insets(9, 14, 9, 14));
        bubble.setMaxWidth(380);

        Label textLbl = new Label(msg.content());
        textLbl.setWrapText(true);
        textLbl.setStyle("-fx-font-size:13px;");

        HBox container = new HBox(8);
        container.setPadding(new Insets(3, 12, 3, 12));
        container.setMaxWidth(Double.MAX_VALUE);

        if (msg.isMe()) {
            // My messages — no avatar needed, right-align
            bubble.setStyle("-fx-background-color:#2563eb;-fx-background-radius:16 16 3 16;");
            textLbl.setStyle("-fx-text-fill:white;-fx-font-size:13px;");
            bubble.getChildren().add(textLbl);
            container.setAlignment(Pos.CENTER_RIGHT);
            container.getChildren().add(bubble);
        } else {
            // Others — avatar on left, clickable for profile
            Node avatarNode = buildAvatar(msg.senderName(), msg.avatarUrl(), 34);
            avatarNode.setStyle(avatarNode.getStyle() + "-fx-cursor:hand;");
            avatarNode.setOnMouseClicked(e -> showProfilePopup(msg.senderId(), msg.senderName()));

            Label nameLbl = new Label(msg.senderName());
            nameLbl.setStyle("-fx-font-size:10px;-fx-font-weight:bold;-fx-text-fill:#93c5fd;-fx-cursor:hand;-fx-padding:0 0 2 0;");
            nameLbl.setOnMouseClicked(e -> showProfilePopup(msg.senderId(), msg.senderName()));

            bubble.setStyle("-fx-background-color:#1e2738;-fx-background-radius:16 16 16 3;");
            textLbl.setStyle("-fx-text-fill:#e2e8f0;-fx-font-size:13px;");
            bubble.getChildren().addAll(nameLbl, textLbl);

            container.setAlignment(Pos.CENTER_LEFT);
            container.getChildren().addAll(avatarNode, bubble);
        }

        return container;
    }

    // ─────────────────────────────────────────────────────────
    //  THREAD FEED
    // ─────────────────────────────────────────────────────────

    private void loadThreadsForCategory(String category) {
        if (threadsFeedBox == null) return;
        threadsFeedBox.getChildren().setAll(createLoadingLabel());

        executor.submit(() -> {
            List<DailyThread> threads = socialService.getThreadsByCategory(category);
            Platform.runLater(() -> {
                threadsFeedBox.getChildren().clear();
                if (threads == null || threads.isEmpty())
                    threadsFeedBox.getChildren().add(createEmptyState(category));
                else
                    threads.forEach(t -> threadsFeedBox.getChildren().add(createThreadCard(t)));
            });
        });
    }

    private VBox createThreadCard(DailyThread thread) {
        VBox card = new VBox(10);
        card.setStyle("-fx-background-color:#161b27;-fx-padding:16;-fx-background-radius:12;" +
                      "-fx-border-color:#252d3d;-fx-border-radius:12;-fx-max-width:680;");

        // ── Header: clickable avatar + author name ─────────────
        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);

        Node avatar = buildAvatar(thread.authorName(), thread.authorAvatar(), 38);
        avatar.setStyle(avatar.getStyle() + "-fx-cursor:hand;");
        avatar.setOnMouseClicked(e -> showProfilePopup(thread.userId(), thread.authorName()));

        VBox authorInfo = new VBox(2);
        Label authorName = new Label(thread.authorName() != null ? thread.authorName() : "Unknown");
        authorName.setStyle("-fx-font-weight:bold;-fx-text-fill:#e2e8f0;-fx-font-size:13px;-fx-cursor:hand;");
        authorName.setOnMouseClicked(e -> showProfilePopup(thread.userId(), thread.authorName()));
        Label timeLabel = new Label(thread.createdAt() != null ? thread.createdAt() : "");
        timeLabel.setStyle("-fx-text-fill:#475569;-fx-font-size:10px;");
        authorInfo.getChildren().addAll(authorName, timeLabel);

        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        Label catBadge = new Label(getCategoryEmoji(thread.category()) + " " + thread.category());
        catBadge.setStyle("-fx-background-color:#1e3a5f;-fx-text-fill:#93c5fd;-fx-font-size:10px;" +
                          "-fx-padding:3 8;-fx-background-radius:10;");

        header.getChildren().addAll(avatar, authorInfo, spacer, catBadge);
        card.getChildren().add(header);

        // ── Content ────────────────────────────────────────────
        if (notBlank(thread.contentText())) {
            Label content = new Label(thread.contentText());
            content.setWrapText(true);
            content.setStyle("-fx-text-fill:#cbd5e1;-fx-font-size:13px;-fx-line-spacing:3;");
            card.getChildren().add(content);
        }

        if (notBlank(thread.photoUrl())) {
            // Try to show photo inline; fall back to a clickable link label
            try {
                ImageView imgView = new ImageView(new Image(thread.photoUrl(), 600, 0, true, true, true));
                imgView.setFitWidth(600); imgView.setPreserveRatio(true);
                imgView.setStyle("-fx-background-radius:8;-fx-cursor:hand;");
                imgView.setOnMouseClicked(e -> openUrl(thread.photoUrl()));
                card.getChildren().add(imgView);
            } catch (Exception ex) {
                card.getChildren().add(makeLink("🖼️ " + thread.photoUrl(), thread.photoUrl()));
            }
        }

        if (notBlank(thread.mediaUrl())) {
            card.getChildren().add(makeLink("🔗 " + thread.mediaUrl(), thread.mediaUrl()));
        }

        // ── Footer: reactions ──────────────────────────────────
        HBox footer = new HBox(12);
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.setStyle("-fx-padding:10 0 0 0;-fx-border-color:#252d3d;-fx-border-width:1 0 0 0;");

        String activeLike    = "-fx-background-color:rgba(16,185,129,0.15);-fx-text-fill:#34d399;-fx-background-radius:6;-fx-cursor:hand;-fx-padding:5 10;-fx-font-weight:bold;";
        String normalBtn     = "-fx-background-color:transparent;-fx-text-fill:#64748b;-fx-cursor:hand;-fx-padding:5 10;-fx-font-weight:bold;";
        String activeDislike = "-fx-background-color:rgba(239,68,68,0.15);-fx-text-fill:#f87171;-fx-background-radius:6;-fx-cursor:hand;-fx-padding:5 10;-fx-font-weight:bold;";
        String activeSave    = "-fx-background-color:transparent;-fx-text-fill:#93c5fd;-fx-cursor:hand;-fx-padding:5 10;-fx-font-weight:bold;";

        Button likeBtn = new Button("👍 " + thread.likeCount());
        likeBtn.setStyle(thread.likedByMe() ? activeLike : normalBtn);
        likeBtn.setOnAction(e -> { likeBtn.setDisable(true); executor.submit(() -> { socialService.likeThread(thread.id()); Platform.runLater(() -> loadThreadsForCategory(currentThreadCategory)); }); });

        Button dislikeBtn = new Button("👎 " + thread.dislikeCount());
        dislikeBtn.setStyle(thread.dislikedByMe() ? activeDislike : normalBtn);
        dislikeBtn.setOnAction(e -> { dislikeBtn.setDisable(true); executor.submit(() -> { socialService.dislikeThread(thread.id()); Platform.runLater(() -> loadThreadsForCategory(currentThreadCategory)); }); });

        Button saveBtn = new Button(thread.savedByMe() ? "🔖 Saved" : "🔖 Save");
        saveBtn.setStyle(thread.savedByMe() ? activeSave : normalBtn);
        saveBtn.setOnAction(e -> { saveBtn.setDisable(true); executor.submit(() -> { socialService.saveThread(thread.id()); Platform.runLater(() -> loadThreadsForCategory(currentThreadCategory)); }); });

        Region footerSpacer = new Region(); HBox.setHgrow(footerSpacer, Priority.ALWAYS);
        Button repostBtn = new Button("🔗 Repost");
        repostBtn.setStyle("-fx-background-color:rgba(59,130,246,0.15);-fx-text-fill:#60a5fa;-fx-background-radius:6;-fx-cursor:hand;-fx-padding:5 12;-fx-font-size:11px;-fx-font-weight:bold;");
        repostBtn.setOnAction(e -> openCreateThreadDialog(thread));

        footer.getChildren().addAll(likeBtn, dislikeBtn, saveBtn, footerSpacer, repostBtn);
        card.getChildren().add(footer);

        return card;
    }

    // ─────────────────────────────────────────────────────────
    //  PROFILE POPUP  ← THE KEY FEATURE
    // ─────────────────────────────────────────────────────────

    /**
     * Shows a rich profile card popup when a user's avatar or name is clicked.
     * Loads profile data on a background thread; shows a loading state first.
     */
    private void showProfilePopup(String userId, String displayName) {
        if (userId == null) return;
        Window owner = getOwner();

        // ── Popup layout ───────────────────────────────────────
        VBox root = new VBox(0);
        root.setStyle("-fx-background-color:#0f1117;-fx-background-radius:16;");
        root.setMinWidth(360); root.setMaxWidth(420);

        // Loading skeleton
        VBox loading = new VBox(12);
        loading.setAlignment(Pos.CENTER);
        loading.setPadding(new Insets(50));
        loading.getChildren().add(makeLabel("⏳ Loading profile…", "#64748b", 13, false));
        root.getChildren().add(loading);

        Stage stage = NewPopupHelper.create(owner,
                "👤 " + (displayName != null ? displayName : "Profile"),
                root, 360, 200, 420, 600);

        // Load profile on background thread
        executor.submit(() -> {
            com.scholar.model.Profile profile = socialService.getProfileForUser(userId);
            Platform.runLater(() -> {
                root.getChildren().clear();
                if (profile == null) {
                    VBox err = new VBox(10); err.setAlignment(Pos.CENTER); err.setPadding(new Insets(40));
                    err.getChildren().add(makeLabel("😶 Profile not available", "#64748b", 14, false));
                    root.getChildren().add(err);
                    return;
                }
                root.getChildren().add(buildProfileCard(profile, userId, stage));
            });
        });

        stage.show();
    }

    private VBox buildProfileCard(com.scholar.model.Profile profile, String userId, Stage stage) {
        VBox card = new VBox(0);
        card.setStyle("-fx-background-color:#0f1117;");

        // ── Banner + Avatar ─────────────────────────────────────
        StackPane banner = new StackPane();
        banner.setPrefHeight(80);
        banner.setStyle("-fx-background-color:linear-gradient(to right, #1e3a5f, #2563eb);");

        Node avatarNode = buildAvatar(profile.getFullName(), profile.getProfilePictureUrl(), 72);
        StackPane.setAlignment(avatarNode, Pos.BOTTOM_LEFT);
        StackPane.setMargin(avatarNode, new Insets(0, 0, -36, 20));
        banner.getChildren().add(avatarNode);
        card.getChildren().add(banner);

        // ── Info section ───────────────────────────────────────
        VBox info = new VBox(6);
        info.setPadding(new Insets(44, 20, 16, 20)); // top padding accounts for avatar overhang
        info.setStyle("-fx-background-color:#0f1117;");

        String displayName = notBlank(profile.getFullName()) ? profile.getFullName()
                : (notBlank(profile.getUsername()) ? profile.getUsername() : "Unknown Scholar");
        Label nameLabel = makeLabel(displayName, "#e2e8f0", 18, true);
        Label usernameLabel = notBlank(profile.getUsername())
                ? makeLabel("@" + profile.getUsername(), "#64748b", 12, false)
                : new Label();

        info.getChildren().addAll(nameLabel, usernameLabel);

        // Uni / dept row
        if (notBlank(profile.getUniversityName()) || notBlank(profile.getDepartment())) {
            HBox uniRow = new HBox(6);
            uniRow.setAlignment(Pos.CENTER_LEFT);
            if (notBlank(profile.getUniversityName()))
                uniRow.getChildren().add(makeChip("🏛️ " + profile.getUniversityName(), "#1e3a5f", "#93c5fd"));
            if (notBlank(profile.getDepartment()))
                uniRow.getChildren().add(makeChip("📚 " + profile.getDepartment(), "#1e3a5f", "#93c5fd"));
            info.getChildren().add(uniRow);
        }

        // CGPA
        if (profile.getCgpa() != null && profile.isCgpaVisible()) {
            info.getChildren().add(makeLabel("🎓 CGPA: " + profile.getCgpa().toPlainString(), "#a3e635", 12, false));
        }

        // Skills
        if (notBlank(profile.getProgLanguages())) {
            info.getChildren().add(makeSectionHeader("💻 Skills"));
            info.getChildren().add(buildChipRow(profile.getProgLanguages().split(",")));
        }

        // Social links
        boolean hasLinks = notBlank(profile.getGithubUrl()) || notBlank(profile.getLinkedinUrl())
                || notBlank(profile.getPortfolioUrl());
        if (hasLinks) {
            info.getChildren().add(makeSectionHeader("🔗 Links"));
            HBox links = new HBox(8); links.setAlignment(Pos.CENTER_LEFT);
            if (notBlank(profile.getGithubUrl()))    links.getChildren().add(makeLinkButton("GitHub",    profile.getGithubUrl()));
            if (notBlank(profile.getLinkedinUrl()))  links.getChildren().add(makeLinkButton("LinkedIn",  profile.getLinkedinUrl()));
            if (notBlank(profile.getPortfolioUrl())) links.getChildren().add(makeLinkButton("Portfolio", profile.getPortfolioUrl()));
            info.getChildren().add(links);
        }

        // ── Action buttons ─────────────────────────────────────
        boolean isMe = userId.equals(AuthService.CURRENT_USER_ID != null
                ? AuthService.CURRENT_USER_ID.toString() : "");

        if (!isMe) {
            Separator sep = new Separator();
            sep.setStyle("-fx-opacity:0.2;");
            info.getChildren().add(sep);

            HBox actions = new HBox(10); actions.setAlignment(Pos.CENTER_LEFT);

            // Connect button — fetch status async to avoid blocking UI
            Button connectBtn = new Button("⏳");
            connectBtn.setStyle("-fx-background-color:#1e2738;-fx-text-fill:#94a3b8;-fx-background-radius:20;-fx-padding:7 16;-fx-font-weight:bold;-fx-cursor:hand;");
            connectBtn.setDisable(true);
            executor.submit(() -> {
                String status = socialService.getConnectionStatus(userId);
                Platform.runLater(() -> applyConnectionStyle(connectBtn, status, userId));
            });
            actions.getChildren().add(connectBtn);

            Button msgBtn = new Button("💬 Message");
            msgBtn.setStyle("-fx-background-color:#1e2738;-fx-text-fill:#94a3b8;-fx-background-radius:20;-fx-padding:7 16;-fx-font-weight:bold;-fx-cursor:hand;");
            msgBtn.setOnAction(e -> {
                stage.close();
                // If already connected, open private chat directly
                executor.submit(() -> {
                    String status = socialService.getConnectionStatus(userId);
                    Platform.runLater(() -> {
                        if ("ACCEPTED".equalsIgnoreCase(status)) {
                            String name = notBlank(profile.getUsername()) ? profile.getUsername()
                                    : (notBlank(profile.getFullName()) ? profile.getFullName() : "User");
                            switchToPrivateChat(new PrivateContact(userId, name, profile.getProfilePictureUrl()));
                        } else {
                            NewPopupHelper.showToast(getOwner(), "⚠️ Connect with this user first to message them.");
                        }
                    });
                });
            });
            actions.getChildren().add(msgBtn);
            info.getChildren().add(actions);
        }

        card.getChildren().add(info);

        // Scroll for tall content
        ScrollPane scroll = new ScrollPane(card);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background:#0f1117;-fx-background-color:#0f1117;-fx-border-color:transparent;");

        VBox wrapper = new VBox(scroll);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        return wrapper;
    }

    private void applyConnectionStyle(Button btn, String status, String userId) {
        switch (status == null ? "NONE" : status.toUpperCase()) {
            case "ACCEPTED" -> {
                btn.setText("✅ Connected");
                btn.setStyle("-fx-background-color:#064e3b;-fx-text-fill:#34d399;-fx-background-radius:20;-fx-padding:7 16;-fx-font-weight:bold;");
                btn.setDisable(true);
            }
            case "PENDING" -> {
                btn.setText("⏳ Request Sent");
                btn.setStyle("-fx-background-color:#451a03;-fx-text-fill:#fb923c;-fx-background-radius:20;-fx-padding:7 16;-fx-font-weight:bold;");
                btn.setDisable(true);
            }
            default -> {
                btn.setText("➕ Connect");
                btn.setStyle("-fx-background-color:#2563eb;-fx-text-fill:white;-fx-background-radius:20;-fx-padding:7 16;-fx-font-weight:bold;-fx-cursor:hand;");
                btn.setDisable(false);
                btn.setOnAction(e -> {
                    btn.setDisable(true); btn.setText("Sending…");
                    executor.submit(() -> {
                        boolean ok = socialService.sendChatRequestById(userId);
                        Platform.runLater(() -> {
                            if (ok) applyConnectionStyle(btn, "PENDING", userId);
                            else { btn.setText("Error"); btn.setDisable(false); }
                        });
                    });
                });
            }
        }
    }

    // ─────────────────────────────────────────────────────────
    //  SIDEBAR — REQUESTS + CONTACTS
    // ─────────────────────────────────────────────────────────

    private void refreshSidebar() {
        executor.submit(() -> {
            List<ChatRequest>    reqs     = socialService.getPendingRequests();
            List<PrivateContact> contacts = socialService.getAcceptedContacts();
            int                  unread   = socialService.getUnreadNotificationCount();

            Platform.runLater(() -> {
                // Request badge
                if (requestBadge != null) {
                    boolean show = !reqs.isEmpty();
                    requestBadge.setText(String.valueOf(reqs.size()));
                    requestBadge.setVisible(show); requestBadge.setManaged(show);
                }

                // Requests panel
                if (requestsContainer != null) {
                    requestsContainer.getChildren().clear();
                    if (reqs.isEmpty()) {
                        requestsContainer.getChildren().add(emptyState("📭", "No pending requests"));
                    } else {
                        reqs.forEach(r -> requestsContainer.getChildren().add(createRequestCard(r)));
                    }
                }

                // Contacts sidebar
                if (contactsContainer != null) {
                    contactsContainer.getChildren().clear();
                    if (contacts.isEmpty()) {
                        Label l = new Label("  No conversations yet");
                        l.setStyle("-fx-text-fill:#334155;-fx-font-size:11px;-fx-padding:4 10;");
                        contactsContainer.getChildren().add(l);
                    } else {
                        contacts.forEach(c -> contactsContainer.getChildren().add(createContactButton(c)));
                    }
                }

                // Notification badge
                if (notificationBadge != null) {
                    if (unread > 0) {
                        notificationBadge.setText(unread > 99 ? "99+" : String.valueOf(unread));
                        notificationBadge.setVisible(true); notificationBadge.setManaged(true);
                    } else {
                        notificationBadge.setVisible(false); notificationBadge.setManaged(false);
                    }
                }
            });
        });
    }

    private HBox createRequestCard(ChatRequest req) {
        HBox card = new HBox(12);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setStyle("-fx-background-color:#161b27;-fx-padding:14 18;-fx-background-radius:12;" +
                      "-fx-border-color:#2563eb;-fx-border-radius:12;-fx-border-width:1 1 1 3;-fx-max-width:580;");

        Node avatar = buildAvatar(req.senderName(), req.avatarUrl(), 42);
        avatar.setStyle(avatar.getStyle() + "-fx-cursor:hand;");
        avatar.setOnMouseClicked(e -> showProfilePopup(req.senderId(), req.senderName()));

        VBox infoBox = new VBox(3); HBox.setHgrow(infoBox, Priority.ALWAYS);
        Label name = makeLabel(req.senderName(), "#e2e8f0", 14, true);
        name.setOnMouseClicked(e -> showProfilePopup(req.senderId(), req.senderName()));
        name.setStyle(name.getStyle() + "-fx-cursor:hand;");
        Label sub  = makeLabel("wants to connect with you", "#64748b", 11, false);
        infoBox.getChildren().addAll(name, sub);

        Button accept = new Button("✅ Accept");
        accept.setStyle("-fx-background-color:#059669;-fx-text-fill:white;-fx-font-weight:bold;" +
                        "-fx-background-radius:16;-fx-padding:7 14;-fx-cursor:hand;");

        Button decline = new Button("✗ Decline");
        decline.setStyle("-fx-background-color:#374151;-fx-text-fill:#94a3b8;-fx-background-radius:16;-fx-padding:7 10;-fx-cursor:hand;");

        accept.setOnAction(e -> {
            accept.setDisable(true); decline.setDisable(true); accept.setText("✓");
            executor.submit(() -> { if (socialService.acceptRequest(req.id())) Platform.runLater(this::refreshSidebar); });
        });
        decline.setOnAction(e -> {
            accept.setDisable(true); decline.setDisable(true);
            executor.submit(() -> { if (socialService.deleteChatRequest(req.id())) Platform.runLater(this::refreshSidebar); });
        });

        HBox actions = new HBox(8, accept, decline); actions.setAlignment(Pos.CENTER);
        card.getChildren().addAll(avatar, infoBox, actions);
        return card;
    }

    private Button createContactButton(PrivateContact contact) {
        boolean isActive = "PRIVATE".equals(currentChatMode) && contact.userId().equals(currentPrivateContactId);

        HBox inner = new HBox(10); inner.setAlignment(Pos.CENTER_LEFT);
        Node avatar = buildAvatar(contact.userName(), contact.avatarUrl(), 28);

        Label name = new Label(contact.userName());
        name.setStyle("-fx-text-fill:" + (isActive ? "#93c5fd" : "#94a3b8") + ";-fx-font-size:13px;" +
                      (isActive ? "-fx-font-weight:bold;" : ""));

        inner.getChildren().addAll(avatar, name);

        Button btn = new Button();
        btn.setGraphic(inner);
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setPadding(new Insets(8, 12, 8, 12));
        btn.setStyle(isActive
                ? "-fx-background-color:#1e3a5f;-fx-background-radius:8;"
                : "-fx-background-color:transparent;-fx-background-radius:8;");
        btn.setOnAction(e -> switchToPrivateChat(contact));
        return btn;
    }

    private void switchToPrivateChat(PrivateContact contact) {
        currentChatMode = "PRIVATE";
        currentPrivateContactId   = contact.userId();
        currentPrivateContactName = contact.userName();
        lastSeenMessageId = null;
        hideAllPanels(); setVisible(chatMainContainer, true);
        if (chatHeaderLabel != null) chatHeaderLabel.setText("💬 " + currentPrivateContactName);
        refreshSidebar();
        loadMessages();
    }

    @FXML
    public void switchToGroupChat(ActionEvent event) {
        currentChatMode = "GROUP"; currentPrivateContactId = null; lastSeenMessageId = null;
        hideAllPanels(); setVisible(chatMainContainer, true);
        if (chatHeaderLabel != null) chatHeaderLabel.setText("🌍 Global Group Chat");
        loadMessages();
    }

    // ─────────────────────────────────────────────────────────
    //  SEND NEW CHAT REQUEST DIALOG  (channel member list)
    // ─────────────────────────────────────────────────────────

    @FXML
    public void onSendNewRequest(ActionEvent event) {
        Window owner = getOwner();
        VBox vbox = new VBox(10); vbox.setPadding(new Insets(15));
        vbox.setStyle("-fx-background-color:#0f1117;");

        ScrollPane scroll = new ScrollPane(vbox); scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background:#0f1117;-fx-background-color:#0f1117;-fx-border-color:transparent;");

        vbox.getChildren().addAll(
                makeLabel("👥 Channel Members", "#e2e8f0", 16, true),
                makeLabel("Send a message request to connect", "#64748b", 12, false),
                makeLabel("⏳ Fetching members…", "#64748b", 13, false));

        Stage stage = NewPopupHelper.create(owner, "👥 Channel Members", scroll, 380, 450, 420, 520);

        executor.submit(() -> {
            List<PrivateContact> members = socialService.getChannelMembers(AuthService.CURRENT_CHANNEL_ID);
            // Batch fetch all statuses in ONE query
            List<String> ids = members.stream().map(PrivateContact::userId).toList();
            Map<String, String> statuses = socialService.getBatchConnectionStatuses(ids);

            Platform.runLater(() -> {
                vbox.getChildren().clear();
                vbox.getChildren().addAll(
                        makeLabel("👥 Channel Members", "#e2e8f0", 16, true),
                        makeLabel("Send a message request to connect", "#64748b", 12, false));

                if (members.isEmpty()) {
                    vbox.getChildren().add(makeLabel("No other members found.", "#64748b", 13, false));
                    return;
                }

                for (PrivateContact member : members) {
                    HBox row = new HBox(12); row.setAlignment(Pos.CENTER_LEFT);
                    row.setStyle("-fx-background-color:#161b27;-fx-padding:12 14;" +
                                 "-fx-background-radius:10;-fx-border-color:#2d3748;-fx-border-radius:10;");

                    Node av = buildAvatar(member.userName(), member.avatarUrl(), 38);
                    av.setStyle(av.getStyle() + "-fx-cursor:hand;");
                    av.setOnMouseClicked(e -> showProfilePopup(member.userId(), member.userName()));

                    VBox nameBox = new VBox(2); HBox.setHgrow(nameBox, Priority.ALWAYS);
                    Label nameLbl = makeLabel(member.userName(), "#e2e8f0", 13, true);
                    nameLbl.setOnMouseClicked(e -> showProfilePopup(member.userId(), member.userName()));
                    nameLbl.setStyle(nameLbl.getStyle() + "-fx-cursor:hand;");
                    nameBox.getChildren().addAll(nameLbl, makeLabel("Channel member", "#475569", 10, false));

                    Button reqBtn = new Button();
                    String status = statuses.getOrDefault(member.userId(), "NONE");
                    applyConnectionStyle(reqBtn, status, member.userId());

                    row.getChildren().addAll(av, nameBox, reqBtn);
                    vbox.getChildren().add(row);
                }
            });
        });

        stage.showAndWait();
    }

    // ─────────────────────────────────────────────────────────
    //  NOTIFICATIONS
    // ─────────────────────────────────────────────────────────

    private void loadNotifications() {
        if (notificationsListContainer == null) return;
        notificationsListContainer.getChildren().setAll(createLoadingLabel());

        executor.submit(() -> {
            List<AppNotification> notifs = socialService.getNotifications();
            Platform.runLater(() -> {
                notificationsListContainer.getChildren().clear();
                if (notifs == null || notifs.isEmpty()) {
                    Label l = new Label("🎉 You're all caught up!");
                    l.setStyle("-fx-text-fill:#475569;-fx-font-size:14px;-fx-padding:40 0;");
                    notificationsListContainer.getChildren().add(l);
                } else {
                    notifs.forEach(n -> notificationsListContainer.getChildren().add(createNotificationCard(n)));
                }
            });
        });
    }

    private HBox createNotificationCard(AppNotification n) {
        HBox card = new HBox(14); card.setAlignment(Pos.CENTER_LEFT);
        String bg = n.isRead() ? "#161b27" : "#1a2236";
        String border = n.isRead() ? "#252d3d" : "#2563eb";
        String bw = n.isRead() ? "1" : "1 1 1 3";
        card.setStyle("-fx-background-color:" + bg + ";-fx-padding:14 18;-fx-background-radius:10;" +
                      "-fx-border-color:" + border + ";-fx-border-radius:10;-fx-border-width:" + bw + ";-fx-max-width:680;");

        Label icon = new Label(getNotifIcon(n.type()));
        icon.setStyle("-fx-font-size:22px;-fx-min-width:36;-fx-alignment:CENTER;");

        VBox content = new VBox(4); HBox.setHgrow(content, Priority.ALWAYS);
        Label title = makeLabel(n.title(), n.isRead() ? "#94a3b8" : "#e2e8f0", 13, true);
        Label body  = new Label(n.body()); body.setWrapText(true);
        body.setStyle("-fx-text-fill:#64748b;-fx-font-size:12px;");
        content.getChildren().addAll(title, body);

        VBox right = new VBox(6); right.setAlignment(Pos.TOP_RIGHT);
        right.getChildren().add(makeLabel(n.timeAgo(), "#475569", 10, false));
        if (!n.isRead()) {
            Label dot = new Label("●"); dot.setStyle("-fx-text-fill:#3b82f6;-fx-font-size:8px;");
            right.getChildren().add(dot);
        }

        card.getChildren().addAll(icon, content, right);
        card.setOnMouseClicked(e -> executor.submit(() -> {
            socialService.markNotificationRead(n.id());
            Platform.runLater(() -> card.setStyle(card.getStyle()
                    .replace(bg, "#161b27").replace(border, "#252d3d").replace(bw, "1")));
        }));
        return card;
    }

    private String getNotifIcon(String type) {
        if (type == null) return "🔔";
        return switch (type.toUpperCase()) {
            case "LIKE"     -> "👍";
            case "COMMENT"  -> "💬";
            case "REQUEST"  -> "📩";
            case "ACCEPTED" -> "✅";
            case "POLL"     -> "📊";
            case "MENTION"  -> "📌";
            default         -> "🔔";
        };
    }

    @FXML
    public void onMarkAllNotificationsRead(ActionEvent event) {
        executor.submit(() -> {
            socialService.markAllNotificationsRead();
            Platform.runLater(this::loadNotifications);
        });
    }

    // ─────────────────────────────────────────────────────────
    //  AUDIO CALLS
    // ─────────────────────────────────────────────────────────

    @FXML public void onJoinAudioCall(ActionEvent event) {
        String roomId = getActiveRoomId();
        String name   = AuthService.CURRENT_USER_NAME != null ? AuthService.CURRENT_USER_NAME : "Student";
        executor.submit(() -> {
            audioCallService.joinVoiceChannel(roomId, name,
                    users -> Platform.runLater(() -> activeVoiceUsersLabel.setText("🎙️ Active in Voice: " + users)));
            Platform.runLater(() -> {
                setVisible(joinAudioBtn, false); setVisible(leaveAudioBtn, true);
                setVisible(muteAudioBtn, true);  setVisible(activeVoiceUsersLabel, true);
                muteAudioBtn.setText("🎤 Mute");
            });
        });
    }

    @FXML public void onLeaveAudioCall(ActionEvent event) {
        audioCallService.leaveVoiceChannel();
        setVisible(leaveAudioBtn, false); setVisible(muteAudioBtn, false);
        setVisible(activeVoiceUsersLabel, false); setVisible(joinAudioBtn, true);
    }

    @FXML public void onToggleMute(ActionEvent event) {
        audioCallService.toggleMute();
        boolean muted = audioCallService.isMuted();
        muteAudioBtn.setText(muted ? "🔇 Unmuted" : "🎤 Mute");
        muteAudioBtn.setStyle(muted
                ? "-fx-background-color:#4b5563;-fx-text-fill:white;-fx-font-weight:bold;-fx-background-radius:20;-fx-padding:7 16;"
                : "-fx-background-color:#d97706;-fx-text-fill:white;-fx-font-weight:bold;-fx-background-radius:20;-fx-padding:7 16;");
    }

    private String getActiveRoomId() {
        if ("PRIVATE".equals(currentChatMode) && currentPrivateContactId != null) {
            String myId = AuthService.CURRENT_USER_ID.toString();
            return myId.compareTo(currentPrivateContactId) < 0
                    ? "private_" + myId + "_" + currentPrivateContactId
                    : "private_" + currentPrivateContactId + "_" + myId;
        }
        return "channel_voice_" + AuthService.CURRENT_CHANNEL_ID;
    }

    // ─────────────────────────────────────────────────────────
    //  POLLS
    // ─────────────────────────────────────────────────────────

    @FXML
    public void onOpenPolls(ActionEvent event) {
        if (!"GROUP".equals(currentChatMode)) {
            NewPopupHelper.showToast(getOwner(), "⚠️ Polls are only available in Group Channels!");
            return;
        }
        boolean isAdmin = "admin".equals(AuthService.CURRENT_USER_ROLE);
        Window  owner   = getOwner();

        VBox container = new VBox(15);
        container.setPadding(new Insets(20));
        container.setStyle("-fx-background-color:#0f1117;");

        if (isAdmin) {
            Button createBtn = new Button("➕ Create New Poll");
            createBtn.setMaxWidth(Double.MAX_VALUE);
            createBtn.setStyle("-fx-background-color:#2563eb;-fx-text-fill:white;-fx-font-weight:bold;" +
                               "-fx-cursor:hand;-fx-padding:10;-fx-background-radius:8;");
            container.getChildren().add(createBtn);

            Stage[] stageRef = new Stage[1];
            createBtn.setOnAction(e -> {
                if (stageRef[0] != null) stageRef[0].close();
                Platform.runLater(() -> openCreatePollDialog(owner));
            });
            VBox pollsList = buildPollsList(true, owner);
            ScrollPane scroll = new ScrollPane(pollsList); scroll.setFitToWidth(true);
            scroll.setStyle("-fx-background:#0f1117;-fx-background-color:#0f1117;-fx-border-color:transparent;");
            VBox.setVgrow(scroll, Priority.ALWAYS);
            container.getChildren().add(scroll);

            Stage stage = PopupHelper.create(owner, "📊 Channel Polls", container, 420, 500, 500, 600);
            stageRef[0] = stage;
            stage.showAndWait();
        } else {
            VBox pollsList = buildPollsList(false, owner);
            ScrollPane scroll = new ScrollPane(pollsList); scroll.setFitToWidth(true);
            scroll.setStyle("-fx-background:#0f1117;-fx-background-color:#0f1117;-fx-border-color:transparent;");
            VBox.setVgrow(scroll, Priority.ALWAYS);
            container.getChildren().add(scroll);
            PopupHelper.create(owner, "📊 Channel Polls", container, 420, 500, 500, 600).showAndWait();
        }
    }

    private VBox buildPollsList(boolean isAdmin, Window owner) {
        VBox list = new VBox(15); list.setStyle("-fx-background-color:#0f1117;");
        Label loading = makeLabel("⏳ Loading polls…", "#64748b", 13, false);
        list.getChildren().add(loading);

        executor.submit(() -> {
            List<com.scholar.model.Poll> polls = socialService.getChannelPolls();
            Platform.runLater(() -> {
                list.getChildren().clear();
                if (polls == null || polls.isEmpty())
                    list.getChildren().add(makeLabel("📭 No active polls right now.", "#64748b", 14, false));
                else
                    polls.forEach(p -> list.getChildren().add(createPollCard(p, isAdmin, list)));
            });
        });
        return list;
    }

    private VBox createPollCard(com.scholar.model.Poll p, boolean isAdmin, VBox pollsList) {
        VBox card = new VBox(12);
        card.setStyle("-fx-padding:16;-fx-background-color:#161b27;-fx-background-radius:12;" +
                      "-fx-border-color:#252d3d;-fx-border-radius:12;");

        HBox header = new HBox(10); header.setAlignment(Pos.CENTER_LEFT);
        Label qLabel = new Label("❓ " + p.question());
        qLabel.setStyle("-fx-font-weight:bold;-fx-font-size:15px;-fx-text-fill:#e2e8f0;");
        qLabel.setWrapText(true); HBox.setHgrow(qLabel, Priority.ALWAYS);
        header.getChildren().add(qLabel);

        if (isAdmin) {
            Button del = new Button("🗑️");
            del.setStyle("-fx-background-color:transparent;-fx-text-fill:#ef4444;-fx-cursor:hand;");
            del.setOnAction(e -> executor.submit(() -> {
                if (socialService.deletePoll(p.id()))
                    Platform.runLater(() -> { NewPopupHelper.showToast(getOwner(), "🗑️ Poll deleted"); refreshPollsList(pollsList, true); });
            }));
            header.getChildren().add(del);
        }
        card.getChildren().add(header);
        card.getChildren().add(makeLabel("Total votes: " + p.totalVotes(), "#64748b", 11, false));

        for (com.scholar.model.PollOption opt : p.options()) {
            VBox optBox = new VBox(5);

            Button voteBtn = new Button(opt.text() + (opt.votedByMe() ? " ✅" : ""));
            voteBtn.setMaxWidth(Double.MAX_VALUE);
            voteBtn.setStyle(opt.votedByMe()
                    ? "-fx-background-color:#064e3b;-fx-text-fill:#34d399;-fx-border-color:#059669;-fx-border-radius:8;-fx-background-radius:8;-fx-font-weight:bold;-fx-alignment:CENTER_LEFT;-fx-cursor:hand;-fx-padding:8 12;"
                    : "-fx-background-color:#1e2738;-fx-text-fill:#94a3b8;-fx-border-color:#2d3748;-fx-border-radius:8;-fx-background-radius:8;-fx-alignment:CENTER_LEFT;-fx-cursor:hand;-fx-padding:8 12;");
            voteBtn.setOnAction(e -> {
                voteBtn.setDisable(true);
                executor.submit(() -> {
                    boolean ok = socialService.castVote(p.id(), opt.id());
                    Platform.runLater(() -> {
                        if (ok) { NewPopupHelper.showToast(getOwner(), "✅ Vote Updated!"); refreshPollsList(pollsList, isAdmin); }
                        else { voteBtn.setDisable(false); NewPopupHelper.showToast(getOwner(), "❌ Failed to vote."); }
                    });
                });
            });

            double pct = p.totalVotes() == 0 ? 0.0 : (double) opt.voteCount() / p.totalVotes();
            ProgressBar pb = new ProgressBar(pct); pb.setMaxWidth(Double.MAX_VALUE); pb.setPrefHeight(6);
            pb.setStyle(opt.votedByMe() ? "-fx-accent:#10b981;" : "-fx-accent:#3b82f6;");
            Label stats = makeLabel(opt.voteCount() + " votes (" + Math.round(pct * 100) + "%)", "#64748b", 10, false);

            optBox.getChildren().addAll(voteBtn, pb, stats);
            card.getChildren().add(optBox);
        }

        if (isAdmin) {
            TextField input = new TextField(); input.setPromptText("Add new option…");
            input.setStyle("-fx-background-color:#1e2738;-fx-text-fill:#e2e8f0;-fx-border-color:#2d3748;-fx-border-radius:6;-fx-background-radius:6;-fx-padding:8;");
            HBox.setHgrow(input, Priority.ALWAYS);

            Button addBtn = new Button("Add");
            addBtn.setStyle("-fx-background-color:#3b82f6;-fx-text-fill:white;-fx-cursor:hand;-fx-background-radius:6;-fx-font-weight:bold;");
            addBtn.setOnAction(e -> {
                String text = input.getText().trim();
                if (text.isEmpty()) return;
                addBtn.setDisable(true);
                executor.submit(() -> {
                    boolean ok = socialService.addPollOption(p.id(), text);
                    Platform.runLater(() -> {
                        addBtn.setDisable(false);
                        if (ok) { NewPopupHelper.showToast(getOwner(), "➕ Option added!"); refreshPollsList(pollsList, true); }
                    });
                });
            });
            input.setOnAction(e -> addBtn.fire());

            HBox addRow = new HBox(8, input, addBtn);
            addRow.setPadding(new Insets(10, 0, 0, 0));
            card.getChildren().add(addRow);
        }
        return card;
    }

    private void refreshPollsList(VBox pollsList, boolean isAdmin) {
        executor.submit(() -> {
            List<com.scholar.model.Poll> polls = socialService.getChannelPolls();
            Platform.runLater(() -> {
                pollsList.getChildren().clear();
                if (polls != null)
                    polls.forEach(p -> pollsList.getChildren().add(createPollCard(p, isAdmin, pollsList)));
            });
        });
    }

    private void openCreatePollDialog(Window owner) {
        String fieldStyle = "-fx-background-color:#1e2738;-fx-text-fill:#e2e8f0;" +
                            "-fx-border-color:#2d3748;-fx-background-radius:6;-fx-padding:10;";

        VBox box = new VBox(15); box.setPadding(new Insets(20));
        box.setStyle("-fx-background-color:#0f1117;");

        TextField qField = new TextField(); qField.setPromptText("Enter your question…");
        qField.setStyle(fieldStyle); qField.setMaxWidth(Double.MAX_VALUE);

        VBox optionsBox = new VBox(10);
        TextField opt1 = new TextField(); opt1.setPromptText("Option 1"); opt1.setStyle(fieldStyle); opt1.setMaxWidth(Double.MAX_VALUE);
        TextField opt2 = new TextField(); opt2.setPromptText("Option 2"); opt2.setStyle(fieldStyle); opt2.setMaxWidth(Double.MAX_VALUE);
        optionsBox.getChildren().addAll(opt1, opt2);

        Button addMoreBtn = new Button("➕ Add Another Option");
        addMoreBtn.setStyle("-fx-background-color:transparent;-fx-text-fill:#3b82f6;-fx-cursor:hand;-fx-font-weight:bold;");

        ScrollPane scroll = new ScrollPane(box); scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background:#0f1117;-fx-background-color:#0f1117;-fx-border-color:transparent;");

        Stage stage = PopupHelper.create(owner, "📊 Create New Poll", scroll, 400, 380, 480, 460);

        addMoreBtn.setOnAction(e -> {
            TextField nf = new TextField(); nf.setPromptText("Option " + (optionsBox.getChildren().size() + 1));
            nf.setStyle(fieldStyle); nf.setMaxWidth(Double.MAX_VALUE);
            optionsBox.getChildren().add(nf);
        });

        Button cancel = new Button("Cancel");
        cancel.setStyle("-fx-background-color:#374151;-fx-text-fill:white;-fx-cursor:hand;-fx-padding:8 18;-fx-background-radius:6;-fx-font-weight:bold;");
        cancel.setOnAction(e -> { stage.close(); Platform.runLater(() -> onOpenPolls(null)); });

        Button create = new Button("🚀 Create Poll");
        create.setStyle("-fx-background-color:#2563eb;-fx-text-fill:white;-fx-font-weight:bold;-fx-cursor:hand;-fx-padding:8 18;-fx-background-radius:6;");
        create.setOnAction(e -> {
            if (qField.getText().isBlank()) { NewPopupHelper.showToast(owner, "⚠️ Question cannot be empty!"); return; }
            List<String> opts = new ArrayList<>();
            for (Node n : optionsBox.getChildren())
                if (n instanceof TextField tf && !tf.getText().isBlank()) opts.add(tf.getText().trim());
            if (opts.size() < 2) { NewPopupHelper.showToast(owner, "⚠️ Provide at least 2 options!"); return; }
            create.setDisable(true);
            stage.close();
            executor.submit(() -> {
                boolean ok = socialService.createPoll(qField.getText().trim(), opts);
                Platform.runLater(() -> {
                    if (ok) { NewPopupHelper.showToast(owner, "✅ Poll Created!"); onOpenPolls(null); }
                    else NewPopupHelper.showToast(owner, "❌ Failed to create poll.");
                });
            });
        });

        HBox actions = new HBox(12, cancel, create); actions.setAlignment(Pos.CENTER_RIGHT); actions.setPadding(new Insets(15, 0, 0, 0));
        box.getChildren().addAll(
                makeLabel("❓ Question:", "#94a3b8", 13, true), qField,
                makeLabel("📋 Options:", "#94a3b8", 13, true), optionsBox,
                addMoreBtn, actions);
        stage.showAndWait();
    }

    // ─────────────────────────────────────────────────────────
    //  CREATE THREAD DIALOG
    // ─────────────────────────────────────────────────────────

    @FXML
    public void onOpenCreateThread(ActionEvent event) { openCreateThreadDialog(null); }

    private void openCreateThreadDialog(DailyThread linkedThread) {
        VBox box = new VBox(16); box.setPadding(new Insets(24));
        box.setStyle("-fx-background-color:#161b27;-fx-border-color:#334155;-fx-border-width:1;");

        ComboBox<String> catBox = new ComboBox<>();
        catBox.getItems().addAll("PUBLIC", "ACADEMICS", "EVENTS", "PLACEMENTS", "ISSUES");
        catBox.setValue("HOME".equals(currentThreadCategory) ? "PUBLIC" : currentThreadCategory);
        catBox.setMaxWidth(Double.MAX_VALUE);
        catBox.setStyle("-fx-background-color:#1e2738;-fx-text-fill:#e2e8f0;-fx-border-color:#334155;-fx-border-radius:6;-fx-background-radius:6;");

        TextArea contentArea = new TextArea();
        contentArea.setPromptText("Type your thoughts…");
        contentArea.setWrapText(true); contentArea.setPrefRowCount(6);
        contentArea.setStyle("-fx-control-inner-background:#1e2738;-fx-background-color:#1e2738;" +
                "-fx-text-fill:#e2e8f0;-fx-prompt-text-fill:#64748b;-fx-border-color:#334155;" +
                "-fx-border-radius:6;-fx-background-radius:6;-fx-font-size:14px;");

        if (linkedThread != null) {
            String quote = "🗣️ @" + linkedThread.authorName() + " said:\n" +
                           "> \"" + linkedThread.contentText() + "\"\n────────────────────\n\n";
            contentArea.setText(quote);
            Platform.runLater(() -> { contentArea.requestFocus(); contentArea.positionCaret(contentArea.getText().length()); });
        }

        String fieldStyle = "-fx-background-color:#1e2738;-fx-text-fill:#e2e8f0;-fx-prompt-text-fill:#64748b;" +
                            "-fx-border-color:#334155;-fx-border-radius:6;-fx-background-radius:6;-fx-padding:10;";
        TextField mediaField = new TextField(); mediaField.setPromptText("YouTube / Spotify URL (Optional)"); mediaField.setStyle(fieldStyle);
        TextField photoField = new TextField(); photoField.setPromptText("Image link (Imgur, etc.) (Optional)"); photoField.setStyle(fieldStyle);

        Button cancelBtn = new Button("Cancel");
        cancelBtn.setStyle("-fx-background-color:transparent;-fx-text-fill:#94a3b8;-fx-cursor:hand;-fx-padding:8 16;-fx-border-color:#475569;-fx-border-radius:6;");
        Button postBtn = new Button("🚀 Post Thread");
        postBtn.setStyle("-fx-background-color:#3b82f6;-fx-text-fill:white;-fx-font-weight:bold;-fx-cursor:hand;-fx-padding:8 20;-fx-background-radius:6;");

        HBox actionBox = new HBox(12, cancelBtn, postBtn); actionBox.setAlignment(Pos.CENTER_RIGHT); actionBox.setPadding(new Insets(10, 0, 0, 0));
        box.getChildren().addAll(
                makeLabel("📂 Category:", "#e2e8f0", 13, true), catBox,
                makeLabel(linkedThread == null ? "📝 What's on your mind?" : "🔗 Add your thoughts:", "#e2e8f0", 13, true),
                contentArea, mediaField, photoField, actionBox);

        Stage stage = NewPopupHelper.create(getOwner(),
                linkedThread == null ? "✏️ Create Thread" : "🔗 Repost Thread",
                box, 450, 500, 480, 520);

        cancelBtn.setOnAction(e -> stage.close());
        postBtn.setOnAction(e -> {
            String content  = contentArea.getText().trim();
            String media    = mediaField.getText().trim();
            String photo    = photoField.getText().trim();
            String category = catBox.getValue();
            if (content.isEmpty() && media.isEmpty() && photo.isEmpty()) {
                NewPopupHelper.showToast(getOwner(), "⚠️ Post cannot be completely empty!"); return;
            }
            postBtn.setDisable(true); postBtn.setText("Posting…");
            executor.submit(() -> {
                String status = socialService.createDailyThread(content, media, photo, category);
                Platform.runLater(() -> {
                    stage.close();
                    switch (status) {
                        case "SUCCESS"       -> { NewPopupHelper.showToast(getOwner(), "✅ Thread Posted!"); loadThreadsForCategory(currentThreadCategory); }
                        case "ALREADY_POSTED"-> NewPopupHelper.showError(getOwner(), "Limit Reached", "You've already posted today. Come back tomorrow!");
                        default              -> { postBtn.setDisable(false); postBtn.setText("🚀 Post Thread"); NewPopupHelper.showToast(getOwner(), "❌ Failed to post."); }
                    }
                });
            });
        });

        stage.showAndWait();
    }

    // ─────────────────────────────────────────────────────────
    //  UI FACTORY HELPERS
    // ─────────────────────────────────────────────────────────

    /**
     * Builds a circular avatar.
     * If avatarUrl is provided, shows a real photo; otherwise shows initials.
     */
    private Node buildAvatar(String name, String avatarUrl, double size) {
        if (notBlank(avatarUrl)) {
            try {
                ImageView iv = new ImageView(new Image(avatarUrl, size, size, true, true, true));
                iv.setFitWidth(size); iv.setFitHeight(size); iv.setPreserveRatio(false);
                iv.setClip(new Circle(size / 2, size / 2, size / 2));
                return iv;
            } catch (Exception ignored) { /* fall through to initials */ }
        }
        // Initials fallback
        String initial = (name != null && !name.isEmpty())
                ? String.valueOf(name.charAt(0)).toUpperCase() : "?";
        Label label = new Label(initial);
        String color = pickAvatarColor(name);
        label.setStyle("-fx-background-color:" + color + ";-fx-text-fill:white;-fx-font-weight:bold;" +
                       "-fx-background-radius:" + (size / 2) + ";-fx-min-width:" + size + ";-fx-min-height:" + size +
                       ";-fx-alignment:CENTER;-fx-font-size:" + (int)(size * 0.4) + "px;");
        return label;
    }

    /** Returns a deterministic color for a user based on their name. */
    private String pickAvatarColor(String name) {
        String[] palette = {"#2563eb","#7c3aed","#059669","#d97706","#dc2626","#0891b2","#be185d"};
        int idx = name == null ? 0 : Math.abs(name.hashCode()) % palette.length;
        return palette[idx];
    }

    private Label makeLabel(String text, String color, int size, boolean bold) {
        Label l = new Label(text);
        l.setStyle("-fx-text-fill:" + color + ";-fx-font-size:" + size + "px;" + (bold ? "-fx-font-weight:bold;" : ""));
        return l;
    }

    private Label makeSectionHeader(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-text-fill:#475569;-fx-font-size:11px;-fx-font-weight:bold;-fx-padding:8 0 2 0;");
        return l;
    }

    private Label makeChip(String text, String bg, String fg) {
        Label l = new Label(text);
        l.setStyle("-fx-background-color:" + bg + ";-fx-text-fill:" + fg + ";-fx-font-size:10px;" +
                   "-fx-padding:3 8;-fx-background-radius:10;");
        return l;
    }

    private HBox buildChipRow(String[] skills) {
        HBox row = new HBox(6); row.setAlignment(Pos.CENTER_LEFT);
        row.setStyle("-fx-flex-wrap:wrap;");
        for (String s : skills) {
            String trim = s.trim();
            if (!trim.isEmpty()) row.getChildren().add(makeChip(trim, "#1e2738", "#94a3b8"));
        }
        return row;
    }

    private Button makeLinkButton(String text, String url) {
        Button btn = new Button(text);
        btn.setStyle("-fx-background-color:#1e2738;-fx-text-fill:#60a5fa;-fx-cursor:hand;" +
                     "-fx-background-radius:16;-fx-padding:5 12;-fx-font-size:11px;");
        btn.setOnAction(e -> openUrl(url));
        return btn;
    }

    private Label makeLink(String label, String url) {
        Label l = new Label(label);
        l.setStyle("-fx-text-fill:#3b82f6;-fx-font-size:11px;-fx-cursor:hand;");
        l.setOnMouseClicked(e -> openUrl(url));
        return l;
    }

    private Label createLoadingLabel() {
        return makeLabel("⏳ Loading…", "#475569", 14, false);
    }

    private VBox createEmptyState(String category) {
        VBox box = new VBox(10); box.setAlignment(Pos.CENTER);
        box.setStyle("-fx-padding:60 0;");
        Label icon = new Label("📭"); icon.setStyle("-fx-font-size:48px;");
        box.getChildren().addAll(icon,
                makeLabel("No posts in " + category + " yet.", "#475569", 14, false),
                makeLabel("Be the first to share something!", "#334155", 12, false));
        return box;
    }

    private VBox emptyState(String emoji, String msg) {
        VBox b = new VBox(8); b.setAlignment(Pos.CENTER); b.setStyle("-fx-padding:60 0;");
        Label ico = new Label(emoji); ico.setStyle("-fx-font-size:40px;");
        b.getChildren().addAll(ico, makeLabel(msg, "#475569", 14, false));
        return b;
    }

    private String getCategoryEmoji(String category) {
        if (category == null) return "📝";
        return switch (category.toUpperCase()) {
            case "ACADEMICS"  -> "📚";
            case "EVENTS"     -> "🎉";
            case "PLACEMENTS" -> "💼";
            case "ISSUES"     -> "🚨";
            case "PUBLIC"     -> "🌐";
            default           -> "🌟";
        };
    }

    private void openUrl(String url) {
        if (url == null || url.isBlank()) return;
        try { java.awt.Desktop.getDesktop().browse(new java.net.URI(url)); }
        catch (Exception e) { NewPopupHelper.showToast(getOwner(), "Cannot open URL: " + url); }
    }

    private static boolean notBlank(String s) { return s != null && !s.isBlank(); }

    // ─────────────────────────────────────────────────────────
    //  DEAD CODE REMOVED / COMPAT STUBS
    // ─────────────────────────────────────────────────────────

    private void styleDialog(Dialog<?> d) {
        d.getDialogPane().setStyle("-fx-background-color:#161b27;-fx-border-color:#252d3d;");
    }

    private void loadDailyThreads() { /* kept for backward compat */ }
}