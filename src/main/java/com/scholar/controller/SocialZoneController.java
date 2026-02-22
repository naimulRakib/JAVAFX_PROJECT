package com.scholar.controller;

import com.scholar.model.ChatMessage;
import com.scholar.model.ChatRequest;
import com.scholar.model.PrivateContact;
import com.scholar.service.AuthService;
import com.scholar.service.SocialZoneService;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

import java.util.ArrayList;
import java.util.Optional;
import java.util.List;

@Controller
public class SocialZoneController {

    // ── Chat
    @FXML private VBox chatContainer;
    @FXML private ScrollPane chatScroll;
    @FXML private TextField messageInput;
    @FXML private Label chatHeaderLabel;

    // ── Audio
    @FXML private Button joinAudioBtn, leaveAudioBtn, muteAudioBtn;
    @FXML private Label activeVoiceUsersLabel;

    // ── Layout panels
    @FXML private BorderPane chatMainContainer;
    @FXML private BorderPane dailyThreadsContainer;
    @FXML private VBox messageRequestsContainer;
    @FXML private BorderPane notificationsContainer;

    // ── Threads
    @FXML private VBox threadsFeedBox;
    @FXML private Label threadsCategoryLabel;
    @FXML private Label threadsSubtitleLabel;

    // ── Sidebar dynamic areas
    @FXML private VBox requestsContainer;
    @FXML private VBox contactsContainer;
    @FXML private VBox notificationsListContainer;

    // ── Badges
    @FXML private Label requestBadge;
    @FXML private Label notificationBadge;

    // ── Labels
    @FXML private Label currentUserLabel;

    @Autowired private com.scholar.service.AudioCallService audioCallService;
    @Autowired private SocialZoneService socialService;

    // State
    private String currentChatMode = "GROUP";
    private String currentPrivateContactId = null;
    private String currentPrivateContactName = null;
    private String currentThreadCategory = "HOME";

    // ─────────────────────────────────────────────
    //  INIT
    // ─────────────────────────────────────────────

    public void initialize() {
        if (currentUserLabel != null && AuthService.CURRENT_USER_NAME != null) {
            currentUserLabel.setText(AuthService.CURRENT_USER_NAME);
        }
        startAutoRefresh();
        refreshSidebar();
        loadMessages();
    }

    private void startAutoRefresh() {
        Timeline refresh = new Timeline(new KeyFrame(Duration.seconds(2), e -> {
            loadMessages();
            refreshSidebar();
        }));
        refresh.setCycleCount(Timeline.INDEFINITE);
        refresh.play();
    }

    // ─────────────────────────────────────────────
    //  CHAT – SEND & LOAD
    // ─────────────────────────────────────────────

    @FXML
    public void onSendMessage(ActionEvent event) {
        String text = messageInput.getText().trim();
        if (text.isEmpty()) return;
        new Thread(() -> {
            boolean success = false;
            if (currentChatMode.equals("GROUP")) {
                success = socialService.sendMessage(text);
            } else if (currentChatMode.equals("PRIVATE") && currentPrivateContactId != null) {
                success = socialService.sendPrivateMessage(currentPrivateContactId, text);
            }
            if (success) {
                Platform.runLater(() -> {
                    messageInput.clear();
                    loadMessages();
                });
            }
        }).start();
    }

    private void loadMessages() {
        new Thread(() -> {
            List<ChatMessage> messages;
            if (currentChatMode.equals("GROUP")) {
                messages = socialService.getRecentMessages();
            } else {
                if (currentPrivateContactId == null) return;
                messages = socialService.getPrivateMessages(currentPrivateContactId);
            }
            Platform.runLater(() -> {
                chatContainer.getChildren().clear();
                for (var msg : messages) {
                    chatContainer.getChildren().add(createMessageBubble(msg));
                }
                chatScroll.setVvalue(1.0);
            });
        }).start();
    }

    // ─────────────────────────────────────────────
    //  NAVIGATION – VIEWS
    // ─────────────────────────────────────────────

    private void hideAllPanels() {
        setVisible(chatMainContainer, false);
        setVisible(dailyThreadsContainer, false);
        setVisible(messageRequestsContainer, false);
        setVisible(notificationsContainer, false);
    }

    private void setVisible(Node node, boolean v) {
        if (node != null) { node.setVisible(v); node.setManaged(v); }
    }

    @FXML public void showGroupChatView(ActionEvent event) {
        hideAllPanels();
        setVisible(chatMainContainer, true);
        currentChatMode = "GROUP";
        currentPrivateContactId = null;
        chatHeaderLabel.setText("🌍 Global Group Chat");
        loadMessages();
    }

    @FXML public void showDailyThreadsView(ActionEvent event) {
        hideAllPanels();
        setVisible(dailyThreadsContainer, true);
        showThreadCategory("HOME", "🌟 Daily Threads", "Share your thoughts with the community");
    }

    @FXML public void showMessageRequestsView(ActionEvent event) {
        hideAllPanels();
        setVisible(messageRequestsContainer, true);
    }

    @FXML public void showNotificationsView(ActionEvent event) {
        hideAllPanels();
        setVisible(notificationsContainer, true);
        loadNotifications();
        // Clear badge on open
        if (notificationBadge != null) {
            notificationBadge.setVisible(false);
            notificationBadge.setManaged(false);
        }
    }

    // ─────────────────────────────────────────────
    //  THREAD CATEGORY NAVIGATION
    // ─────────────────────────────────────────────

    @FXML public void showThreadsHome(ActionEvent event) {
        hideAllPanels(); setVisible(dailyThreadsContainer, true);
        showThreadCategory("HOME", "🏠 Home Feed", "Latest posts from your community");
    }
    @FXML public void showPublicThreads(ActionEvent event) {
        hideAllPanels(); setVisible(dailyThreadsContainer, true);
        showThreadCategory("PUBLIC", "🌐 Public Threads", "Posts visible to everyone");
    }
    @FXML public void showThreadsAcademics(ActionEvent event) {
        hideAllPanels(); setVisible(dailyThreadsContainer, true);
        showThreadCategory("ACADEMICS", "📚 Academics", "Lecture notes, study tips & academic help");
    }
    @FXML public void showThreadsEvents(ActionEvent event) {
        hideAllPanels(); setVisible(dailyThreadsContainer, true);
        showThreadCategory("EVENTS", "🎉 Events", "Upcoming campus events & announcements");
    }
    @FXML public void showThreadsPlacements(ActionEvent event) {
        hideAllPanels(); setVisible(dailyThreadsContainer, true);
        showThreadCategory("PLACEMENTS", "💼 Placements", "Jobs, internships & career opportunities");
    }
    @FXML public void showThreadsIssues(ActionEvent event) {
        hideAllPanels(); setVisible(dailyThreadsContainer, true);
        showThreadCategory("ISSUES", "🚨 Issues", "Report problems & campus concerns");
    }
    @FXML public void showMyThreads(ActionEvent event) {
        hideAllPanels(); setVisible(dailyThreadsContainer, true);
        showThreadCategory("MY_THREADS", "👤 My Threads", "All your posts in one place");
    }
    @FXML public void showSavedThreads(ActionEvent event) {
        hideAllPanels(); setVisible(dailyThreadsContainer, true);
        showThreadCategory("SAVED", "🔖 Saved Threads", "Bookmarked posts");
    }

    private void showThreadCategory(String category, String title, String subtitle) {
        currentThreadCategory = category;
        if (threadsCategoryLabel != null) threadsCategoryLabel.setText(title);
        if (threadsSubtitleLabel != null) threadsSubtitleLabel.setText(subtitle);
        loadThreadsForCategory(category);
    }

    private void loadThreadsForCategory(String category) {
        if (threadsFeedBox == null) return;
        threadsFeedBox.getChildren().clear();
        threadsFeedBox.getChildren().add(createLoadingLabel());

        new Thread(() -> {
            List<com.scholar.model.DailyThread> threads = socialService.getThreadsByCategory(category);
            Platform.runLater(() -> {
                threadsFeedBox.getChildren().clear();
                if (threads == null || threads.isEmpty()) {
                    threadsFeedBox.getChildren().add(createEmptyState(category));
                } else {
                    for (com.scholar.model.DailyThread thread : threads) {
                        threadsFeedBox.getChildren().add(createThreadCard(thread));
                    }
                }
            });
        }).start();
    }

    private Label createLoadingLabel() {
        Label l = new Label("⏳ Loading...");
        l.setStyle("-fx-text-fill: #475569; -fx-font-size: 14px;");
        return l;
    }

    private VBox createEmptyState(String category) {
        VBox box = new VBox(10);
        box.setAlignment(Pos.CENTER);
        box.setStyle("-fx-padding: 60 0;");
        Label icon = new Label("📭");
        icon.setStyle("-fx-font-size: 48px;");
        Label msg = new Label("No posts in " + category + " yet.");
        msg.setStyle("-fx-text-fill: #475569; -fx-font-size: 14px;");
        Label sub = new Label("Be the first to share something!");
        sub.setStyle("-fx-text-fill: #334155; -fx-font-size: 12px;");
        box.getChildren().addAll(icon, msg, sub);
        return box;
    }

    private VBox createThreadCard(com.scholar.model.DailyThread thread) {
        VBox card = new VBox(10);
        card.setStyle("-fx-background-color: #161b27; -fx-padding: 16; -fx-background-radius: 12; " +
                      "-fx-border-color: #252d3d; -fx-border-radius: 12; -fx-max-width: 680;");

        // Header
        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);
        Label avatar = new Label(thread.authorName() != null && !thread.authorName().isEmpty()
                ? String.valueOf(thread.authorName().charAt(0)).toUpperCase() : "?");
        avatar.setStyle("-fx-background-color: #2563eb; -fx-text-fill: white; -fx-font-weight: bold; " +
                        "-fx-background-radius: 18; -fx-min-width: 36; -fx-min-height: 36; -fx-alignment: CENTER;");
        VBox authorInfo = new VBox(2);
        Label authorName = new Label(thread.authorName());
        authorName.setStyle("-fx-font-weight: bold; -fx-text-fill: #e2e8f0; -fx-font-size: 13px;");
        Label timeLabel = new Label(thread.createdAt() != null ? thread.createdAt() : "");
        timeLabel.setStyle("-fx-text-fill: #475569; -fx-font-size: 10px;");
        authorInfo.getChildren().addAll(authorName, timeLabel);

        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);

        // Category badge
        Label catBadge = new Label(getCategoryEmoji(thread.category()) + " " + thread.category());
        catBadge.setStyle("-fx-background-color: #1e3a5f; -fx-text-fill: #93c5fd; -fx-font-size: 10px; " +
                          "-fx-padding: 3 8; -fx-background-radius: 10;");

        header.getChildren().addAll(avatar, authorInfo, spacer, catBadge);
        card.getChildren().add(header);

        // Content
        if (thread.contentText() != null && !thread.contentText().isEmpty()) {
            Label content = new Label(thread.contentText());
            content.setWrapText(true);
            content.setStyle("-fx-text-fill: #cbd5e1; -fx-font-size: 13px; -fx-line-spacing: 3;");
            card.getChildren().add(content);
        }

        // Photo URL display
        if (thread.photoUrl() != null && !thread.photoUrl().isEmpty()) {
            Label photoLink = new Label("🖼️ " + thread.photoUrl());
            photoLink.setStyle("-fx-text-fill: #3b82f6; -fx-font-size: 11px; -fx-cursor: hand;");
            card.getChildren().add(photoLink);
        }

        // Media URL
        if (thread.mediaUrl() != null && !thread.mediaUrl().isEmpty()) {
            Label mediaLink = new Label("🔗 " + thread.mediaUrl());
            mediaLink.setStyle("-fx-text-fill: #3b82f6; -fx-font-size: 11px; -fx-cursor: hand;");
            card.getChildren().add(mediaLink);
        }

        // Footer actions
        HBox footer = new HBox(12);
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.setStyle("-fx-padding: 8 0 0 0; -fx-border-color: #252d3d; -fx-border-width: 1 0 0 0;");

        Button likeBtn = new Button("👍 " + thread.likeCount());
        likeBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #64748b; -fx-cursor: hand; -fx-font-size: 12px;");
        likeBtn.setOnAction(e -> {
            new Thread(() -> {
                socialService.likeThread(thread.id());
                Platform.runLater(() -> loadThreadsForCategory(currentThreadCategory));
            }).start();
        });

        Button saveBtn = new Button(thread.savedByMe() ? "🔖 Saved" : "🔖 Save");
        saveBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #64748b; -fx-cursor: hand; -fx-font-size: 12px;");
        saveBtn.setOnAction(e -> {
            new Thread(() -> {
                socialService.saveThread(thread.id());
                Platform.runLater(() -> loadThreadsForCategory(currentThreadCategory));
            }).start();
        });

        footer.getChildren().addAll(likeBtn, saveBtn);
        card.getChildren().add(footer);

        return card;
    }

    private String getCategoryEmoji(String category) {
        if (category == null) return "📝";
        return switch (category.toUpperCase()) {
            case "ACADEMICS" -> "📚";
            case "EVENTS" -> "🎉";
            case "PLACEMENTS" -> "💼";
            case "ISSUES" -> "🚨";
            case "PUBLIC" -> "🌐";
            default -> "🌟";
        };
    }

    // ─────────────────────────────────────────────
    //  NOTIFICATIONS
    // ─────────────────────────────────────────────

    private void loadNotifications() {
        if (notificationsListContainer == null) return;
        notificationsListContainer.getChildren().clear();
        notificationsListContainer.getChildren().add(createLoadingLabel());

        new Thread(() -> {
            List<com.scholar.model.AppNotification> notifs = socialService.getNotifications();
            Platform.runLater(() -> {
                notificationsListContainer.getChildren().clear();
                if (notifs == null || notifs.isEmpty()) {
                    Label empty = new Label("🎉 You're all caught up!");
                    empty.setStyle("-fx-text-fill: #475569; -fx-font-size: 14px; -fx-padding: 40 0;");
                    notificationsListContainer.getChildren().add(empty);
                } else {
                    for (com.scholar.model.AppNotification n : notifs) {
                        notificationsListContainer.getChildren().add(createNotificationCard(n));
                    }
                }
            });
        }).start();
    }

    private HBox createNotificationCard(com.scholar.model.AppNotification n) {
        HBox card = new HBox(14);
        card.setAlignment(Pos.CENTER_LEFT);
        String bg = n.isRead() ? "#161b27" : "#1a2236";
        String border = n.isRead() ? "#252d3d" : "#2563eb";
        card.setStyle("-fx-background-color: " + bg + "; -fx-padding: 14 18; -fx-background-radius: 10; " +
                      "-fx-border-color: " + border + "; -fx-border-radius: 10; -fx-border-width: " +
                      (n.isRead() ? "1" : "1 1 1 3") + "; -fx-max-width: 680;");

        // Icon
        Label icon = new Label(getNotifIcon(n.type()));
        icon.setStyle("-fx-font-size: 22px; -fx-min-width: 36; -fx-alignment: CENTER;");

        // Content
        VBox content = new VBox(4);
        HBox.setHgrow(content, Priority.ALWAYS);
        Label title = new Label(n.title());
        title.setStyle("-fx-font-weight: bold; -fx-text-fill: " + (n.isRead() ? "#94a3b8" : "#e2e8f0") + "; -fx-font-size: 13px;");
        Label body = new Label(n.body());
        body.setWrapText(true);
        body.setStyle("-fx-text-fill: #64748b; -fx-font-size: 12px;");
        content.getChildren().addAll(title, body);

        // Time + unread dot
        VBox right = new VBox(6);
        right.setAlignment(Pos.TOP_RIGHT);
        Label time = new Label(n.timeAgo());
        time.setStyle("-fx-text-fill: #475569; -fx-font-size: 10px;");
        right.getChildren().add(time);
        if (!n.isRead()) {
            Label dot = new Label("●");
            dot.setStyle("-fx-text-fill: #3b82f6; -fx-font-size: 8px;");
            right.getChildren().add(dot);
        }

        card.getChildren().addAll(icon, content, right);
        card.setOnMouseClicked(e -> {
            new Thread(() -> socialService.markNotificationRead(n.id())).start();
            card.setStyle(card.getStyle().replace(bg, "#161b27").replace(border, "#252d3d")
                    .replace("1 1 1 3", "1"));
        });
        return card;
    }

    private String getNotifIcon(String type) {
        if (type == null) return "🔔";
        return switch (type.toUpperCase()) {
            case "LIKE" -> "👍";
            case "COMMENT" -> "💬";
            case "REQUEST" -> "📩";
            case "ACCEPTED" -> "✅";
            case "POLL" -> "📊";
            case "MENTION" -> "📌";
            default -> "🔔";
        };
    }

    @FXML
    public void onMarkAllNotificationsRead(ActionEvent event) {
        new Thread(() -> {
            socialService.markAllNotificationsRead();
            Platform.runLater(this::loadNotifications);
        }).start();
    }

    // ─────────────────────────────────────────────
    //  SIDEBAR – REQUESTS + CONTACTS
    // ─────────────────────────────────────────────

    private void refreshSidebar() {
        new Thread(() -> {
            List<ChatRequest> reqs = socialService.getPendingRequests();
            List<PrivateContact> contacts = socialService.getAcceptedContacts();

            Platform.runLater(() -> {
                // Update request badge in left sidebar
                if (requestBadge != null) {
                    if (reqs.isEmpty()) {
                        requestBadge.setVisible(false); requestBadge.setManaged(false);
                    } else {
                        requestBadge.setText(String.valueOf(reqs.size()));
                        requestBadge.setVisible(true); requestBadge.setManaged(true);
                    }
                }

                // Fill requests panel (shown in messageRequestsContainer)
                if (requestsContainer != null) {
                    requestsContainer.getChildren().clear();
                    if (reqs.isEmpty()) {
                        VBox empty = new VBox(8);
                        empty.setAlignment(Pos.CENTER);
                        empty.setStyle("-fx-padding: 60 0;");
                        Label l1 = new Label("📭"); l1.setStyle("-fx-font-size: 40px;");
                        Label l2 = new Label("No pending requests");
                        l2.setStyle("-fx-text-fill: #475569; -fx-font-size: 14px;");
                        empty.getChildren().addAll(l1, l2);
                        requestsContainer.getChildren().add(empty);
                    } else {
                        for (ChatRequest req : reqs) {
                            requestsContainer.getChildren().add(createRequestCard(req));
                        }
                    }
                }

                // Fill contacts in sidebar
                if (contactsContainer != null) {
                    contactsContainer.getChildren().clear();
                    if (contacts.isEmpty()) {
                        Label l = new Label("  No conversations yet");
                        l.setStyle("-fx-text-fill: #334155; -fx-font-size: 11px; -fx-padding: 4 10;");
                        contactsContainer.getChildren().add(l);
                    } else {
                        for (PrivateContact contact : contacts) {
                            contactsContainer.getChildren().add(createContactButton(contact));
                        }
                    }
                }

                // Notification badge (poll from service)
                updateNotificationBadge();
            });
        }).start();
    }

    private HBox createRequestCard(ChatRequest req) {
        HBox card = new HBox(12);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setStyle("-fx-background-color: #161b27; -fx-padding: 14 18; -fx-background-radius: 12; " +
                      "-fx-border-color: #2563eb; -fx-border-radius: 12; -fx-border-width: 1 1 1 3; -fx-max-width: 580;");

        // Avatar
        String initial = req.senderName() != null && !req.senderName().isEmpty()
                ? String.valueOf(req.senderName().charAt(0)).toUpperCase() : "?";
        Label avatar = new Label(initial);
        avatar.setStyle("-fx-background-color: #7c3aed; -fx-text-fill: white; -fx-font-weight: bold; " +
                        "-fx-background-radius: 20; -fx-min-width: 40; -fx-min-height: 40; -fx-alignment: CENTER;");

        VBox info = new VBox(3); HBox.setHgrow(info, Priority.ALWAYS);
        Label name = new Label(req.senderName());
        name.setStyle("-fx-font-weight: bold; -fx-text-fill: #e2e8f0; -fx-font-size: 14px;");
        Label subtitle = new Label("wants to connect with you");
        subtitle.setStyle("-fx-text-fill: #64748b; -fx-font-size: 11px;");
        info.getChildren().addAll(name, subtitle);

        HBox actions = new HBox(8);
        actions.setAlignment(Pos.CENTER);
        Button accept = new Button("✅ Accept");
        accept.setStyle("-fx-background-color: #059669; -fx-text-fill: white; -fx-font-weight: bold; " +
                        "-fx-background-radius: 16; -fx-padding: 7 14; -fx-cursor: hand;");
        Button decline = new Button("✗");
        decline.setStyle("-fx-background-color: #374151; -fx-text-fill: #94a3b8; -fx-background-radius: 16; -fx-padding: 7 10; -fx-cursor: hand;");

        accept.setOnAction(e -> {
            accept.setText("✓"); accept.setDisable(true);
            new Thread(() -> {
                if (socialService.acceptRequest(req.id())) Platform.runLater(this::refreshSidebar);
            }).start();
        });
        // Decline – just refresh (can add service call)
        decline.setOnAction(e -> refreshSidebar());

        actions.getChildren().addAll(accept, decline);
        card.getChildren().addAll(avatar, info, actions);
        return card;
    }

    private Button createContactButton(PrivateContact contact) {
        HBox inner = new HBox(10);
        inner.setAlignment(Pos.CENTER_LEFT);
        String initial = contact.userName() != null && !contact.userName().isEmpty()
                ? String.valueOf(contact.userName().charAt(0)).toUpperCase() : "?";
        Label avatar = new Label(initial);

        boolean isActive = currentChatMode.equals("PRIVATE") && contact.userId().equals(currentPrivateContactId);
        avatar.setStyle("-fx-background-color: " + (isActive ? "#2563eb" : "#1e2738") + "; -fx-text-fill: " +
                        (isActive ? "white" : "#94a3b8") + "; -fx-font-weight: bold; -fx-background-radius: 14; " +
                        "-fx-min-width: 28; -fx-min-height: 28; -fx-alignment: CENTER; -fx-font-size: 12px;");
        Label name = new Label(contact.userName());
        name.setStyle("-fx-text-fill: " + (isActive ? "#93c5fd" : "#94a3b8") + "; -fx-font-size: 13px;" +
                      (isActive ? " -fx-font-weight: bold;" : ""));
        inner.getChildren().addAll(avatar, name);

        Button btn = new Button();
        btn.setGraphic(inner);
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setPadding(new Insets(8, 12, 8, 12));
        String activeBg = "-fx-background-color: #1e3a5f; -fx-background-radius: 8;";
        String normalBg = "-fx-background-color: transparent; -fx-background-radius: 8;";
        btn.setStyle(isActive ? activeBg : normalBg);
        btn.setOnAction(e -> switchToPrivateChat(contact));
        return btn;
    }

    private void updateNotificationBadge() {
        new Thread(() -> {
            int count = socialService.getUnreadNotificationCount();
            Platform.runLater(() -> {
                if (notificationBadge == null) return;
                if (count > 0) {
                    notificationBadge.setText(count > 99 ? "99+" : String.valueOf(count));
                    notificationBadge.setVisible(true); notificationBadge.setManaged(true);
                } else {
                    notificationBadge.setVisible(false); notificationBadge.setManaged(false);
                }
            });
        }).start();
    }

    // ─────────────────────────────────────────────
    //  PRIVATE CHAT SWITCHING
    // ─────────────────────────────────────────────

    @FXML
    public void switchToGroupChat(ActionEvent event) {
        currentChatMode = "GROUP";
        currentPrivateContactId = null;
        hideAllPanels();
        setVisible(chatMainContainer, true);
        chatHeaderLabel.setText("🌍 Global Group Chat");
        loadMessages();
    }

    private void switchToPrivateChat(PrivateContact contact) {
        currentChatMode = "PRIVATE";
        currentPrivateContactId = contact.userId();
        currentPrivateContactName = contact.userName();
        hideAllPanels();
        setVisible(chatMainContainer, true);
        chatHeaderLabel.setText("💬 " + currentPrivateContactName);
        refreshSidebar();
        loadMessages();
    }

    // ─────────────────────────────────────────────
    //  NEW CONTACT REQUEST DIALOG
    // ─────────────────────────────────────────────

    @FXML
    public void onSendNewRequest(ActionEvent event) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("👥 Channel Members");
        dialog.setHeaderText("Send a message request to connect");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        styleDialog(dialog);

        VBox vbox = new VBox(10);
        vbox.setPadding(new Insets(15));
        vbox.setPrefSize(380, 440);
        vbox.setStyle("-fx-background-color: #0f1117;");
        ScrollPane scroll = new ScrollPane(vbox);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background: #0f1117; -fx-background-color: #0f1117; -fx-border-color: transparent;");
        dialog.getDialogPane().setContent(scroll);
        dialog.getDialogPane().setStyle("-fx-background-color: #0f1117;");

        Label loading = new Label("⏳ Fetching members...");
        loading.setStyle("-fx-text-fill: #64748b; -fx-font-size: 13px;");
        vbox.getChildren().add(loading);

        new Thread(() -> {
            List<PrivateContact> members = socialService.getChannelMembers(AuthService.CURRENT_CHANNEL_ID);
            Platform.runLater(() -> {
                vbox.getChildren().clear();
                if (members.isEmpty()) {
                    Label l = new Label("No other members found.");
                    l.setStyle("-fx-text-fill: #64748b;");
                    vbox.getChildren().add(l);
                    return;
                }
                for (PrivateContact member : members) {
                    HBox row = new HBox(12);
                    row.setAlignment(Pos.CENTER_LEFT);
                    row.setStyle("-fx-background-color: #161b27; -fx-padding: 12 14; -fx-background-radius: 10; -fx-border-color: #252d3d; -fx-border-radius: 10;");

                    String init = member.userName() != null && !member.userName().isEmpty()
                            ? String.valueOf(member.userName().charAt(0)).toUpperCase() : "?";
                    Label av = new Label(init);
                    av.setStyle("-fx-background-color: #2563eb; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 18; -fx-min-width: 36; -fx-min-height: 36; -fx-alignment: CENTER;");

                    VBox nameBox = new VBox(2); HBox.setHgrow(nameBox, Priority.ALWAYS);
                    Label name = new Label(member.userName());
                    name.setStyle("-fx-font-weight: bold; -fx-text-fill: #e2e8f0; -fx-font-size: 13px;");
                    Label sub = new Label("Channel member");
                    sub.setStyle("-fx-text-fill: #475569; -fx-font-size: 10px;");
                    nameBox.getChildren().addAll(name, sub);

                    Button reqBtn = new Button("Connect ➕");
                    reqBtn.setStyle("-fx-background-color: #2563eb; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 16; -fx-padding: 7 14; -fx-cursor: hand;");
                    reqBtn.setOnAction(e -> {
                        reqBtn.setText("Sending...");
                        reqBtn.setDisable(true);
                        new Thread(() -> {
                            boolean success = socialService.sendChatRequestById(member.userId());
                            Platform.runLater(() -> {
                                if (success) {
                                    reqBtn.setText("Sent ✅");
                                    reqBtn.setStyle("-fx-background-color: #059669; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 16; -fx-padding: 7 14;");
                                } else {
                                    reqBtn.setText("Already Sent");
                                    reqBtn.setStyle("-fx-background-color: #374151; -fx-text-fill: #94a3b8; -fx-background-radius: 16; -fx-padding: 7 14;");
                                }
                            });
                        }).start();
                    });

                    row.getChildren().addAll(av, nameBox, reqBtn);
                    vbox.getChildren().add(row);
                }
            });
        }).start();

        dialog.showAndWait();
    }

    // ─────────────────────────────────────────────
    //  MESSAGE BUBBLE
    // ─────────────────────────────────────────────

    private HBox createMessageBubble(ChatMessage msg) {
        VBox bubble = new VBox(3);
        bubble.setPadding(new Insets(9, 14, 9, 14));
        bubble.setMaxWidth(360);

        Label nameLbl = new Label(msg.senderName());
        nameLbl.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: #93c5fd;");

        Label textLbl = new Label(msg.content());
        textLbl.setWrapText(true);
        textLbl.setStyle("-fx-font-size: 13px;");

        bubble.getChildren().addAll(nameLbl, textLbl);
        HBox container = new HBox(bubble);
        container.setPadding(new Insets(4, 12, 4, 12));
        container.setMaxWidth(Double.MAX_VALUE);

        if (msg.isMe()) {
            container.setAlignment(Pos.CENTER_RIGHT);
            bubble.setStyle("-fx-background-color: #2563eb; -fx-background-radius: 16 16 3 16;");
            textLbl.setStyle("-fx-text-fill: white; -fx-font-size: 13px;");
            nameLbl.setVisible(false); nameLbl.setManaged(false);
        } else {
            container.setAlignment(Pos.CENTER_LEFT);
            bubble.setStyle("-fx-background-color: #1e2738; -fx-background-radius: 16 16 16 3;");
            textLbl.setStyle("-fx-text-fill: #e2e8f0; -fx-font-size: 13px;");
        }
        return container;
    }

    // ─────────────────────────────────────────────
    //  AUDIO CALLS
    // ─────────────────────────────────────────────

    @FXML
    public void onJoinAudioCall(ActionEvent event) {
        String roomId = getActiveRoomId();
        String myName = AuthService.CURRENT_USER_NAME != null ? AuthService.CURRENT_USER_NAME : "Student";
        new Thread(() -> {
            audioCallService.joinVoiceChannel(roomId, myName, (activeUsers) -> {
                Platform.runLater(() -> activeVoiceUsersLabel.setText("🎙️ Active in Voice: " + activeUsers));
            });
            Platform.runLater(() -> {
                setVisible(joinAudioBtn, false); setVisible(leaveAudioBtn, true); setVisible(muteAudioBtn, true);
                setVisible(activeVoiceUsersLabel, true);
                muteAudioBtn.setText("🎤 Mute");
            });
        }).start();
    }

    @FXML
    public void onLeaveAudioCall(ActionEvent event) {
        audioCallService.leaveVoiceChannel();
        Platform.runLater(() -> {
            setVisible(leaveAudioBtn, false); setVisible(muteAudioBtn, false); setVisible(activeVoiceUsersLabel, false);
            setVisible(joinAudioBtn, true);
        });
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

    @FXML
    public void onToggleMute(ActionEvent event) {
        audioCallService.toggleMute();
        boolean isMuted = audioCallService.isMuted();
        Platform.runLater(() -> {
            if (isMuted) {
                muteAudioBtn.setText("🔇 Unmuted");
                muteAudioBtn.setStyle("-fx-background-color: #4b5563; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 20; -fx-padding: 7 16;");
            } else {
                muteAudioBtn.setText("🎤 Mute");
                muteAudioBtn.setStyle("-fx-background-color: #d97706; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 20; -fx-padding: 7 16;");
            }
        });
    }

    // ─────────────────────────────────────────────
    //  POLLING SYSTEM
    // ─────────────────────────────────────────────

  @FXML
    public void onOpenPolls(ActionEvent event) {
        if (currentChatMode == null || !currentChatMode.equals("GROUP")) {
            showToast("⚠️ Polls are only available in Group Channels!");
            return;
        }

        boolean isAdmin = true; // টেস্ট করার জন্য আপাতত true

        javafx.stage.Stage stage = new javafx.stage.Stage();
        stage.setTitle("📊 Channel Polls");
        stage.initModality(javafx.stage.Modality.APPLICATION_MODAL);

        VBox container = new VBox(15);
        container.setPadding(new Insets(20));
        container.setStyle("-fx-background-color: #0f1117;");

        if (isAdmin) {
            Button createBtn = new Button("➕ Create New Poll");
            createBtn.setMaxWidth(Double.MAX_VALUE);
            createBtn.setStyle("-fx-background-color: #2563eb; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand; -fx-padding: 10; -fx-background-radius: 8;");
            
            createBtn.setOnAction(e -> {
                stage.close(); // প্রথম উইন্ডোটি ক্লোজ করা হলো
                // 🌟 MAC FIX 1: সাথে সাথে ওপেন না করে, runLater দিয়ে ম্যাককে একটু সময় দেওয়া হলো
                Platform.runLater(() -> openCreatePollDialog());
            });
            container.getChildren().add(createBtn);
        }

        VBox pollsList = new VBox(15);
        pollsList.setStyle("-fx-background-color: #0f1117;");
        
        ScrollPane scroll = new ScrollPane(pollsList);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background: #0f1117; -fx-background-color: #0f1117; -fx-border-color: transparent;");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        container.getChildren().add(scroll);

        // 🌟 MAC FIX 2: Scene কে ফোর্স করে ডার্ক কালার করে দেওয়া, যাতে সাদা স্ক্রিন আসতে না পারে!
        javafx.scene.Scene scene = new javafx.scene.Scene(container, 480, 600);
        scene.setFill(javafx.scene.paint.Color.web("#0f1117")); 
        stage.setScene(scene);

        new Thread(() -> {
            try {
                List<com.scholar.model.Poll> polls = socialService.getChannelPolls();
                Platform.runLater(() -> {
                    pollsList.getChildren().clear();
                    if (polls == null || polls.isEmpty()) {
                        Label empty = new Label("📭 No active polls right now.");
                        empty.setStyle("-fx-text-fill: #64748b; -fx-font-size: 14px;");
                        pollsList.getChildren().add(empty);
                    } else {
                        for (com.scholar.model.Poll p : polls) {
                            pollsList.getChildren().add(createPollCard(p, isAdmin, pollsList));
                        }
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();

        stage.showAndWait();
    }





    private VBox createPollCard(com.scholar.model.Poll p, boolean isAdmin, VBox pollsList) {
        VBox card = new VBox(12);
        // ডার্ক থিমের পোল কার্ড
        card.setStyle("-fx-padding: 16; -fx-background-color: #161b27; -fx-background-radius: 12; -fx-border-color: #252d3d; -fx-border-radius: 12;");

        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);
        Label qLabel = new Label("❓ " + p.question());
        qLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 15px; -fx-text-fill: #e2e8f0;");
        qLabel.setWrapText(true);
        HBox.setHgrow(qLabel, Priority.ALWAYS);
        header.getChildren().add(qLabel);

        if (isAdmin) {
            Button delBtn = new Button("🗑️");
            delBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #ef4444; -fx-cursor: hand;");
            delBtn.setOnAction(e -> new Thread(() -> {
                if (socialService.deletePoll(p.id())) {
                    Platform.runLater(() -> {
                        showToast("🗑️ Poll Deleted");
                        refreshPollsList(pollsList, isAdmin);
                    });
                }
            }).start());
            header.getChildren().add(delBtn);
        }
        card.getChildren().add(header);

        Label totalLabel = new Label("Total votes: " + p.totalVotes());
        totalLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748b;");
        card.getChildren().add(totalLabel);

        for (com.scholar.model.PollOption opt : p.options()) {
            VBox optBox = new VBox(5);

            Button voteBtn = new Button(opt.text() + (opt.votedByMe() ? " ✅" : ""));
            voteBtn.setMaxWidth(Double.MAX_VALUE);
            
            // ডার্ক থিমের ভোটিং বাটন
            voteBtn.setStyle(opt.votedByMe()
                ? "-fx-background-color: #064e3b; -fx-text-fill: #34d399; -fx-border-color: #059669; -fx-border-radius: 8; -fx-background-radius: 8; -fx-font-weight: bold; -fx-alignment: CENTER_LEFT; -fx-cursor: hand; -fx-padding: 8 12;"
                : "-fx-background-color: #1e2738; -fx-text-fill: #94a3b8; -fx-border-color: #2d3748; -fx-border-radius: 8; -fx-background-radius: 8; -fx-alignment: CENTER_LEFT; -fx-cursor: hand; -fx-padding: 8 12;");

            voteBtn.setOnAction(e -> {
                voteBtn.setDisable(true);
                new Thread(() -> {
                    boolean ok = socialService.castVote(p.id(), opt.id());
                    Platform.runLater(() -> {
                        if (ok) {
                            showToast("✅ Vote Updated!");
                            refreshPollsList(pollsList, isAdmin);
                        } else {
                            voteBtn.setDisable(false);
                            showToast("❌ Failed to vote.");
                        }
                    });
                }).start();
            });

            double pct = p.totalVotes() == 0 ? 0 : (double) opt.voteCount() / p.totalVotes();
            ProgressBar pb = new ProgressBar(pct);
            pb.setMaxWidth(Double.MAX_VALUE); pb.setPrefHeight(6);
            pb.setStyle(opt.votedByMe() ? "-fx-accent: #10b981;" : "-fx-accent: #3b82f6;"); 

            Label stats = new Label(opt.voteCount() + " votes (" + Math.round(pct * 100) + "%)");
            stats.setStyle("-fx-font-size: 10px; -fx-text-fill: #64748b;");

            optBox.getChildren().addAll(voteBtn, pb, stats);
            card.getChildren().add(optBox);
        }

        if (isAdmin) {
            HBox addBox = new HBox(8); addBox.setPadding(new Insets(10, 0, 0, 0));
            TextField input = new TextField(); input.setPromptText("Add new option...");
            input.setStyle("-fx-background-color: #1e2738; -fx-text-fill: #e2e8f0; -fx-border-color: #2d3748; -fx-border-radius: 6; -fx-background-radius: 6; -fx-padding: 8;");
            HBox.setHgrow(input, Priority.ALWAYS);
            
            Button addBtn = new Button("Add");
            addBtn.setStyle("-fx-background-color: #3b82f6; -fx-text-fill: white; -fx-cursor: hand; -fx-background-radius: 6; -fx-font-weight: bold;");
            addBtn.setOnAction(e -> {
                String text = input.getText().trim();
                if (!text.isEmpty()) {
                    addBtn.setDisable(true);
                    new Thread(() -> {
                        boolean ok = socialService.addPollOption(p.id(), text);
                        Platform.runLater(() -> {
                            addBtn.setDisable(false);
                            if (ok) { showToast("➕ Option added!"); refreshPollsList(pollsList, isAdmin); }
                        });
                    }).start();
                }
            });
            input.setOnAction(e -> addBtn.fire());
            addBox.getChildren().addAll(input, addBtn);
            card.getChildren().add(addBox);
        }
        return card;
    }




    private void showToast(String message) {
        Platform.runLater(() -> {
            // ১. মূল উইন্ডো (Stage) খুঁজে বের করা
            javafx.stage.Window window = chatMainContainer.getScene().getWindow();
            if (window == null) return;

            // ২. টোস্টের ডিজাইন (স্লীক এবং রাউন্ডেড)
            Label toastLabel = new Label(message);
            toastLabel.setStyle(
                "-fx-background-color: #10b981; " +  // সুন্দর সবুজ রঙ
                "-fx-text-fill: white; " +
                "-fx-padding: 12 24; " +
                "-fx-background-radius: 25; " +
                "-fx-font-size: 14px; " +
                "-fx-font-weight: bold; " +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 10, 0, 0, 4);" // ভাসমান ইফেক্ট
            );

            // ৩. পপ-আপ তৈরি করা
            javafx.stage.Popup popup = new javafx.stage.Popup();
            popup.getContent().add(toastLabel);
            popup.setAutoHide(true);
            
            // স্ক্রিনে শো করানো (যাতে width ক্যালকুলেট করতে পারে)
            popup.show(window);

            // ৪. পজিশন সেট করা (স্ক্রিনের একদম নিচে মাঝখানে)
            popup.setX(window.getX() + (window.getWidth() / 2) - (toastLabel.getWidth() / 2));
            popup.setY(window.getY() + window.getHeight() - 100);

            // ৫. ফেড-আউট (Fade-out) অ্যানিমেশন (২ সেকেন্ড পর ধীরে ধীরে মিলিয়ে যাবে)
            javafx.animation.FadeTransition fade = new javafx.animation.FadeTransition(javafx.util.Duration.millis(500), toastLabel);
            fade.setDelay(javafx.util.Duration.seconds(2)); // ২ সেকেন্ড স্ক্রিনে থাকবে
            fade.setFromValue(1.0);
            fade.setToValue(0.0);
            fade.setOnFinished(e -> popup.hide()); // অ্যানিমেশন শেষ হলে পপ-আপ মুছে যাবে
            fade.play();
        });
    }



    private void refreshPollsList(VBox pollsList, boolean isAdmin) {
        new Thread(() -> {
            try {
                List<com.scholar.model.Poll> polls = socialService.getChannelPolls();
                Platform.runLater(() -> {
                    pollsList.getChildren().clear();
                    if (polls != null) {
                        for (com.scholar.model.Poll p : polls) {
                            pollsList.getChildren().add(createPollCard(p, isAdmin, pollsList));
                        }
                    }
                });
            } catch (Exception e) { e.printStackTrace(); }
        }).start();
    }

    // ─────────────────────────────────────────────
    //  CREATE POLL DIALOG (DYNAMIC & DARK THEME)
    // ─────────────────────────────────────────────

  private void openCreatePollDialog() {
        javafx.stage.Stage stage = new javafx.stage.Stage();
        stage.setTitle("📊 Create New Poll");
        stage.initModality(javafx.stage.Modality.APPLICATION_MODAL);

        VBox box = new VBox(15);
        box.setPadding(new Insets(20));
        box.setStyle("-fx-background-color: #0f1117;"); 

        Label qLabel = new Label("❓ Question:");
        qLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #94a3b8;");
        TextField qField = new TextField(); qField.setPromptText("Enter your question...");
        qField.setStyle("-fx-background-color: #1e2738; -fx-text-fill: #e2e8f0; -fx-border-color: #2d3748; -fx-background-radius: 6; -fx-padding: 10;");

        Label optLabel = new Label("📋 Options:");
        optLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #94a3b8;");
        VBox optionsBox = new VBox(10);

        String fieldStyle = "-fx-background-color: #1e2738; -fx-text-fill: #e2e8f0; -fx-border-color: #2d3748; -fx-background-radius: 6; -fx-padding: 10;";
        TextField opt1 = new TextField(); opt1.setPromptText("Option 1"); opt1.setStyle(fieldStyle);
        TextField opt2 = new TextField(); opt2.setPromptText("Option 2"); opt2.setStyle(fieldStyle);
        optionsBox.getChildren().addAll(opt1, opt2);

        Button addMoreBtn = new Button("➕ Add Another Option");
        addMoreBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #3b82f6; -fx-cursor: hand; -fx-font-weight: bold;");
        addMoreBtn.setOnAction(e -> {
            TextField newOpt = new TextField(); newOpt.setPromptText("Option " + (optionsBox.getChildren().size() + 1));
            newOpt.setStyle(fieldStyle);
            optionsBox.getChildren().add(newOpt);
            stage.sizeToScene(); 
        });

        HBox actionBox = new HBox(12); actionBox.setAlignment(Pos.CENTER_RIGHT); actionBox.setPadding(new Insets(15, 0, 0, 0));
        
        Button cancelBtn = new Button("Cancel");
        cancelBtn.setStyle("-fx-background-color: #374151; -fx-text-fill: white; -fx-cursor: hand; -fx-padding: 8 18; -fx-background-radius: 6; -fx-font-weight: bold;");
        cancelBtn.setOnAction(e -> {
            stage.close();
            // ক্যানসেল করলে আবার আগের পোল লিস্টে ফিরিয়ে নিয়ে যাওয়া
            Platform.runLater(() -> onOpenPolls(null));
        });

        Button createBtn = new Button("🚀 Create Poll");
        createBtn.setStyle("-fx-background-color: #2563eb; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand; -fx-padding: 8 18; -fx-background-radius: 6;");
        createBtn.setOnAction(e -> {
            if (qField.getText().trim().isEmpty()) { showToast("⚠️ Question cannot be empty!"); return; }
            List<String> finalOptions = new ArrayList<>();
            for (javafx.scene.Node node : optionsBox.getChildren()) {
                if (node instanceof TextField && !((TextField) node).getText().trim().isEmpty()) {
                    finalOptions.add(((TextField) node).getText().trim());
                }
            }
            if (finalOptions.size() >= 2) {
                stage.close();
                new Thread(() -> {
                    boolean success = socialService.createPoll(qField.getText().trim(), finalOptions);
                    Platform.runLater(() -> {
                        if (success) { 
                            showToast("✅ Poll Created!"); 
                            // 🌟 MAC FIX 1: পোল ক্রিয়েট হওয়ার পর runLater দিয়ে পোল লিস্ট ওপেন করা
                            Platform.runLater(() -> onOpenPolls(null)); 
                        } 
                        else showToast("❌ Failed to create poll.");
                    });
                }).start();
            } else showToast("⚠️ Provide at least 2 options!");
        });

        actionBox.getChildren().addAll(cancelBtn, createBtn);
        box.getChildren().addAll(qLabel, qField, optLabel, optionsBox, addMoreBtn, actionBox);

        // 🌟 MAC FIX 2: Scene কে ফোর্স করে ডার্ক কালার দেওয়া হলো
        javafx.scene.Scene scene = new javafx.scene.Scene(box, 450, 450);
        scene.setFill(javafx.scene.paint.Color.web("#0f1117")); 
        stage.setScene(scene);
        
        stage.showAndWait();
    }

    



    
    // ─────────────────────────────────────────────
    //  CREATE THREAD DIALOG (original logic preserved)
    // ─────────────────────────────────────────────

    @FXML
    public void onOpenCreateThread(ActionEvent event) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("✏️ Create Your Daily Thread");
        dialog.setHeaderText("Share something with the community (1 Post / Day)");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        styleDialog(dialog);

        VBox box = new VBox(14); box.setPadding(new Insets(20)); box.setPrefWidth(420);
        box.setStyle("-fx-background-color: #0f1117;");

        // Category selector
        Label catLbl = new Label("📂 Category:");
        catLbl.setStyle("-fx-font-weight: bold; -fx-text-fill: #94a3b8;");
        ComboBox<String> categoryBox = new ComboBox<>();
        categoryBox.getItems().addAll("PUBLIC", "ACADEMICS", "EVENTS", "PLACEMENTS", "ISSUES");
        categoryBox.setValue(currentThreadCategory.equals("HOME") ? "PUBLIC" : currentThreadCategory);
        categoryBox.setMaxWidth(Double.MAX_VALUE);
        categoryBox.setStyle("-fx-background-color: #1e2738; -fx-text-fill: #e2e8f0; -fx-border-color: #2d3748;");

        Label contentLbl = new Label("📝 What's on your mind?");
        contentLbl.setStyle("-fx-font-weight: bold; -fx-text-fill: #94a3b8;");
        TextArea contentArea = new TextArea();
        contentArea.setPromptText("Type your thoughts...");
        contentArea.setWrapText(true); contentArea.setPrefRowCount(4);
        contentArea.setStyle("-fx-background-color: #1e2738; -fx-text-fill: #e2e8f0; -fx-border-color: #2d3748; -fx-background-radius: 6;");

        Label mediaLbl = new Label("🔗 Media Link (Optional):");
        mediaLbl.setStyle("-fx-font-weight: bold; -fx-text-fill: #94a3b8;");
        TextField mediaUrlField = new TextField();
        mediaUrlField.setPromptText("YouTube / Spotify URL");
        mediaUrlField.setStyle("-fx-background-color: #1e2738; -fx-text-fill: #e2e8f0; -fx-border-color: #2d3748; -fx-background-radius: 6; -fx-padding: 9;");

        Label photoLbl = new Label("🖼️ Photo URL (Optional):");
        photoLbl.setStyle("-fx-font-weight: bold; -fx-text-fill: #94a3b8;");
        TextField photoUrlField = new TextField();
        photoUrlField.setPromptText("Image link (Imgur, etc.)");
        photoUrlField.setStyle("-fx-background-color: #1e2738; -fx-text-fill: #e2e8f0; -fx-border-color: #2d3748; -fx-background-radius: 6; -fx-padding: 9;");

        box.getChildren().addAll(catLbl, categoryBox, contentLbl, contentArea, mediaLbl, mediaUrlField, photoLbl, photoUrlField);
        dialog.getDialogPane().setContent(box);
        dialog.getDialogPane().setStyle("-fx-background-color: #0f1117;");

        dialog.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                String content = contentArea.getText().trim();
                String mediaUrl = mediaUrlField.getText().trim();
                String photoUrl = photoUrlField.getText().trim();
                String category = categoryBox.getValue();

                if (content.isEmpty() && mediaUrl.isEmpty() && photoUrl.isEmpty()) {
                    showToast("⚠️ Post cannot be completely empty!"); return;
                }

                new Thread(() -> {
                    String status = socialService.createDailyThread(content, mediaUrl, photoUrl, category);
                    Platform.runLater(() -> {
                        if ("SUCCESS".equals(status)) {
                            showToast("✅ Thread Posted!");
                            loadThreadsForCategory(currentThreadCategory);
                        } else if ("ALREADY_POSTED".equals(status)) {
                            Alert a = new Alert(Alert.AlertType.WARNING);
                            a.setTitle("Limit Reached");
                            a.setHeaderText("One Post Per Day!");
                            a.setContentText("You've already posted today. Come back tomorrow!");
                            a.show();
                        } else {
                            showToast("❌ Failed to post. Connection error.");
                        }
                    });
                }).start();
            }
        });
    }

    // ─────────────────────────────────────────────
    //  HELPER
    // ─────────────────────────────────────────────

    private void styleDialog(Dialog<?> dialog) {
        dialog.getDialogPane().setStyle("-fx-background-color: #161b27; -fx-border-color: #252d3d;");
    }

    private void loadDailyThreads() {
        // Kept for compatibility
    }
}