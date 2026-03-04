package com.scholar.controller.collaboration;

import com.scholar.service.AuthService;
import com.scholar.service.CollaborationService;
import com.scholar.service.ProfileService;
import com.scholar.service.TelegramService;
import com.scholar.util.NewPopupHelper;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.shape.Circle;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component("teamWorkspaceController")
public class TeamWorkspaceController {

    @Autowired private CollaborationService collaborationService;
    @Autowired private ProfileService profileService; // 🌟 ADDED Profile Service
    @Autowired private TelegramService telegramService; // 🌟 ADDED Telegram Service

    private ScheduledExecutorService chatPoller;
    private final ExecutorService executor =
            Executors.newCachedThreadPool(r -> { Thread t = new Thread(r); t.setDaemon(true); return t; });

    // ══════════════════════════════════════════════════════════════════════════
    // ENTRY POINT
    // ══════════════════════════════════════════════════════════════════════════

    public void loadRoomView(CollaborationService.Post post, VBox roomContainer) {
        stopChatPoller();
        executor.submit(() -> {
            String  myStatus   = collaborationService.getMyStatus(post.id());
            boolean isOwner    = "OWNER".equals(myStatus);
            boolean isAdmin    = "admin".equals(AuthService.CURRENT_USER_ROLE);
            boolean isApproved = isOwner || "APPROVED".equals(myStatus);

            Platform.runLater(() -> buildRoomUI(post, myStatus, isOwner, isAdmin, isApproved, roomContainer));
        });
    }

    private void buildRoomUI(CollaborationService.Post post, String myStatus,
                              boolean isOwner, boolean isAdmin,
                              boolean isApproved, VBox roomContainer) {
        roomContainer.getChildren().clear();
        roomContainer.setStyle("-fx-background-color:#0f1117; -fx-background-radius:16; -fx-border-color:#2d3150; -fx-border-radius:16;");

        roomContainer.getChildren().add(buildHeader(post, myStatus, isOwner, isAdmin, roomContainer));

        Separator sep = new Separator();
        sep.setStyle("-fx-background-color:#2d3150;");
        roomContainer.getChildren().add(sep);

        if (!isApproved) {
            roomContainer.getChildren().add(buildGuestPanel(post, myStatus, roomContainer));
            return;
        }

        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.setStyle("-fx-background-color:transparent;");
        VBox.setVgrow(tabs, Priority.ALWAYS);
        tabs.getTabs().addAll(
            buildChatTab(post),
            buildMembersTab(post, isOwner, isAdmin),
            buildResourcesTab(post),
            buildPlansTab(post)
        );
        roomContainer.getChildren().add(tabs);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // HEADER & GUEST PANEL
    // ══════════════════════════════════════════════════════════════════════════

    private HBox buildHeader(CollaborationService.Post post, String myStatus, boolean isOwner, boolean isAdmin, VBox roomContainer) {
        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setStyle("-fx-padding:14 20; -fx-background-color:#13151f; -fx-background-radius:16 16 0 0;");

        Label title = new Label("🏠 " + post.title());
        title.setStyle("-fx-font-size:17px; -fx-font-weight:bold; -fx-text-fill:#e2e8f0;");

        Label statusBadge = makeBadge(post.status(), "OPEN".equals(post.status()) ? "#34d399" : "#fbbf24",
                "OPEN".equals(post.status()) ? "rgba(52,211,153,0.15)" : "rgba(251,191,36,0.15)");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        header.getChildren().addAll(title, statusBadge, spacer);

        if ("APPROVED".equals(myStatus) && !isOwner) {
            Button leaveBtn = new Button("🚪 Leave");
            leaveBtn.setStyle(dangerBtn());
            leaveBtn.setOnAction(e -> {
                leaveBtn.setDisable(true); // Anti-spam
                NewPopupHelper.showConfirm(getWindow(roomContainer), "Leave Team", "Are you sure you want to leave?",
                    () -> executor.submit(() -> {
                        collaborationService.leaveTeam(post.id());
                        Platform.runLater(() -> loadRoomView(post, roomContainer));
                    }));
            });
            header.getChildren().add(leaveBtn);
        }

        if (isOwner || isAdmin) {
            Button deleteBtn = new Button("🗑 Delete Team");
            deleteBtn.setStyle(dangerBtn());
            deleteBtn.setOnAction(e -> {
                deleteBtn.setDisable(true); // Anti-spam
                NewPopupHelper.showConfirm(getWindow(roomContainer), "Delete Team", "Delete team and ALL its data?",
                    () -> executor.submit(() -> {
                        collaborationService.deletePost(post.id());
                        Platform.runLater(() -> {
                            roomContainer.getChildren().clear();
                            showPlaceholder(roomContainer);
                        });
                    }));
            });
            header.getChildren().add(deleteBtn);
        }
        return header;
    }

    private VBox buildGuestPanel(CollaborationService.Post post, String myStatus, VBox roomContainer) {
        VBox panel = new VBox(16);
        panel.setAlignment(Pos.CENTER);
        panel.setPadding(new Insets(40));
        VBox.setVgrow(panel, Priority.ALWAYS);

        if ("PENDING".equals(myStatus)) {
            Label lbl = new Label("⏳ Your application is pending approval.");
            lbl.setStyle("-fx-font-size:15px; -fx-text-fill:#fbbf24;");
            panel.getChildren().add(lbl);
            return panel;
        }

        Label hint = new Label("You're not a member of this team yet.");
        hint.setStyle("-fx-text-fill:#94a3b8; -fx-font-size:14px;");

        Button applyBtn = new Button("📩 Apply to Join");
        applyBtn.setStyle("-fx-background-color:linear-gradient(to right,#6366f1,#8b5cf6); -fx-text-fill:white; -fx-font-weight:bold; -fx-background-radius:10; -fx-padding:10 24; -fx-cursor:hand; -fx-font-size:14px;");
        applyBtn.setOnAction(e -> showApplyPopup(getWindow(roomContainer), post, roomContainer));

        panel.getChildren().addAll(hint, applyBtn);
        return panel;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // CHAT TAB
    // ══════════════════════════════════════════════════════════════════════════

    private Tab buildChatTab(CollaborationService.Post post) {
        Tab tab = new Tab("💬 Chat");
        VBox root = new VBox(10);
        root.setStyle("-fx-background-color:#0f1117; -fx-padding:12;");
        VBox.setVgrow(root, Priority.ALWAYS);

        ScrollPane scroll = new ScrollPane();
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background:#0f1117; -fx-background-color:#0f1117; -fx-border-color:transparent;");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        VBox msgBox = new VBox(8);
        msgBox.setPadding(new Insets(10));
        scroll.setContent(msgBox);

        HBox inputRow = new HBox(10);
        inputRow.setAlignment(Pos.CENTER);

        TextField msgField = new TextField();
        msgField.setPromptText("Type a message…");
        msgField.setStyle("-fx-background-color:#1e2235; -fx-text-fill:#e2e8f0; -fx-prompt-text-fill:#64748b; -fx-border-color:#2d3150; -fx-border-radius:24; -fx-background-radius:24; -fx-padding:10 16;");
        HBox.setHgrow(msgField, Priority.ALWAYS);

        Button sendBtn = new Button("Send ➤");
        sendBtn.setStyle("-fx-background-color:linear-gradient(to right,#6366f1,#8b5cf6); -fx-text-fill:white; -fx-font-weight:bold; -fx-background-radius:24; -fx-padding:10 20; -fx-cursor:hand;");

        Runnable doSend = () -> {
            String txt = msgField.getText().trim();
            if (txt.isEmpty()) return;
            msgField.clear();
            sendBtn.setDisable(true); // Anti-spam
            executor.submit(() -> {
                collaborationService.sendMessage(post.id(), txt);
                Platform.runLater(() -> sendBtn.setDisable(false));
                fetchAndRenderMessages(post.id(), msgBox, scroll);
            });
        };
        sendBtn.setOnAction(e -> doSend.run());
        msgField.setOnAction(e -> doSend.run());

        inputRow.getChildren().addAll(msgField, sendBtn);
        root.getChildren().addAll(scroll, inputRow);

        fetchAndRenderMessages(post.id(), msgBox, scroll);
        startChatPoller(post.id(), msgBox, scroll);
        tab.setContent(root);
        return tab;
    }

    private void fetchAndRenderMessages(int postId, VBox msgBox, ScrollPane scroll) {
        executor.submit(() -> {
            List<CollaborationService.Message> msgs = collaborationService.getMessages(postId);
            Platform.runLater(() -> {
                msgBox.getChildren().clear();
                for (var m : msgs) {
                    VBox bubble = new VBox(2);
                    Label sender = new Label(m.sender());
                    sender.setStyle("-fx-font-size:10px; -fx-text-fill:#64748b; -fx-font-weight:bold;");
                    Label content = new Label(m.content());
                    content.setWrapText(true);
                    content.setStyle("-fx-font-size:13px; -fx-text-fill:#e2e8f0; -fx-background-color:#23263a; -fx-background-radius:10; -fx-padding:8 12;");
                    Label time = new Label(m.time() != null ? m.time().substring(0, Math.min(16, m.time().length())) : "");
                    time.setStyle("-fx-font-size:10px; -fx-text-fill:#475569;");
                    bubble.getChildren().addAll(sender, content, time);
                    bubble.setMaxWidth(380);
                    msgBox.getChildren().add(bubble);
                }
                scroll.setVvalue(1.0);
            });
        });
    }

    private void startChatPoller(int postId, VBox msgBox, ScrollPane scroll) {
        stopChatPoller();
        chatPoller = Executors.newSingleThreadScheduledExecutor(r -> { Thread t = new Thread(r); t.setDaemon(true); return t; });
        chatPoller.scheduleAtFixedRate(() -> fetchAndRenderMessages(postId, msgBox, scroll), 3, 3, TimeUnit.SECONDS);
    }
    public void stopChatPoller() { if (chatPoller != null && !chatPoller.isShutdown()) chatPoller.shutdownNow(); }

    // ══════════════════════════════════════════════════════════════════════════
    // TAB: MEMBERS & PROFILES (User Avatars Integration)
    // ══════════════════════════════════════════════════════════════════════════

    private Tab buildMembersTab(CollaborationService.Post post, boolean isOwner, boolean isAdmin) {
        Tab tab = new Tab("👥 Members");
        VBox root = new VBox(12);
        root.setStyle("-fx-background-color:#0f1117; -fx-padding:16;");
        loadMembersAsync(root, post, isOwner, isAdmin);
        tab.setContent(scrollWrap(root));
        return tab;
    }

    private void loadMembersAsync(VBox root, CollaborationService.Post post, boolean isOwner, boolean isAdmin) {
        root.getChildren().clear();
        Label loading = new Label("Loading members…");
        loading.setStyle("-fx-text-fill:#64748b; -fx-font-size:13px; -fx-font-style:italic;");
        root.getChildren().add(loading);

        executor.submit(() -> {
            List<CollaborationService.Application> apps = (isOwner || isAdmin) ? collaborationService.getApplicationsForPost(post.id()) : List.of();
            List<CollaborationService.TeamMember> members = collaborationService.getTeamMembers(post.id());
            Platform.runLater(() -> renderMembers(root, post, isOwner, isAdmin, apps, members));
        });
    }

    private void renderMembers(VBox root, CollaborationService.Post post, boolean isOwner, boolean isAdmin,
                                List<CollaborationService.Application> apps, List<CollaborationService.TeamMember> members) {
        root.getChildren().clear();

        if (isOwner || isAdmin) {
            Label h = new Label("📋 Pending Applications");
            h.setStyle("-fx-font-size:13px; -fx-font-weight:bold; -fx-text-fill:#fbbf24;");
            root.getChildren().add(h);

            for (var app : apps) {
                if (!"PENDING".equals(app.status())) continue;
                HBox row = new HBox(12);
                row.setAlignment(Pos.CENTER_LEFT);
                row.setStyle("-fx-background-color:#1e2235; -fx-background-radius:10; -fx-padding:10 14; -fx-border-color:#2d3150; -fx-border-radius:10;");

                // 🌟 Avatar integration for Applications
                Node avatar = createAvatar(app.profilePictureUrl(), app.username(), 36);
                avatar.setStyle("-fx-cursor:hand;");
                avatar.setOnMouseClicked(e -> showUserProfileDialog(app.userId(), getWindow(root)));

                VBox info = new VBox(3);
                Label name = new Label(app.username());
                name.setStyle("-fx-font-weight:bold; -fx-text-fill:#e2e8f0;");
                info.getChildren().add(name);
                app.answers().forEach(qa -> {
                    Label ql = new Label("↳ " + qa);
                    ql.setStyle("-fx-text-fill:#94a3b8; -fx-font-size:11px;");
                    info.getChildren().add(ql);
                });
                HBox.setHgrow(info, Priority.ALWAYS);

                Button approveBtn = new Button("✅"); approveBtn.setStyle(successBtn());
                approveBtn.setOnAction(e -> {
                    approveBtn.setDisable(true); // Anti-spam
                    executor.submit(() -> {
                        collaborationService.approveMember(post.id(), app.userId());
                        Platform.runLater(() -> loadMembersAsync(root, post, isOwner, isAdmin));
                    });
                });

                Button rejectBtn = new Button("❌"); rejectBtn.setStyle(dangerBtn());
                rejectBtn.setOnAction(e -> {
                    rejectBtn.setDisable(true); // Anti-spam
                    executor.submit(() -> {
                        collaborationService.rejectMember(post.id(), app.userId());
                        Platform.runLater(() -> loadMembersAsync(root, post, isOwner, isAdmin));
                    });
                });

                row.getChildren().addAll(avatar, info, approveBtn, rejectBtn);
                root.getChildren().add(row);
            }
        }

        Label mh = new Label("👥 Current Members");
        mh.setStyle("-fx-font-size:13px; -fx-font-weight:bold; -fx-text-fill:#818cf8;");
        root.getChildren().add(mh);

        for (var m : members) {
            if ("PENDING".equals(m.status())) continue;
            HBox row = new HBox(12);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setStyle("-fx-background-color:#1a1d27; -fx-background-radius:10; -fx-padding:10 14; -fx-border-color:#2d3150; -fx-border-radius:10;");

            // 🌟 Avatar integration for Active Members
            Node avatar = createAvatar(m.profilePictureUrl(), m.username(), 32);
            avatar.setStyle("-fx-cursor:hand;");
            avatar.setOnMouseClicked(e -> showUserProfileDialog(m.userId(), getWindow(root)));

            Label name = new Label(m.username());
            name.setStyle("-fx-font-weight:bold; -fx-text-fill:#e2e8f0;");
            Label badge = "OWNER".equals(m.role()) ? makeBadge("OWNER", "#60a5fa", "rgba(96,165,250,0.15)") : makeBadge("MEMBER", "#34d399", "rgba(52,211,153,0.15)");
            Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
            row.getChildren().addAll(avatar, name, badge, sp);

            boolean canKick = (isOwner || isAdmin) && !"OWNER".equals(m.role()) && !m.userId().equals(AuthService.CURRENT_USER_ID.toString());
            if (canKick) {
                Button kickBtn = new Button("🥾 Kick");
                kickBtn.setStyle(dangerBtn());
                kickBtn.setOnAction(e -> {
                    kickBtn.setDisable(true); // Anti-spam
                    NewPopupHelper.showConfirm(getWindowFromNode(row), "Kick Member", "Remove " + m.username() + "?",
                        () -> executor.submit(() -> {
                            collaborationService.kickMember(post.id(), m.userId());
                            Platform.runLater(() -> loadMembersAsync(root, post, isOwner, isAdmin));
                        }));
                });
                row.getChildren().add(kickBtn);
            }
            root.getChildren().add(row);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // TAB: RESOURCES (TELEGRAM INTEGRATION)
    // ══════════════════════════════════════════════════════════════════════════

    private Tab buildResourcesTab(CollaborationService.Post post) {
        Tab tab = new Tab("📎 Resources");
        VBox root = new VBox(12);
        root.setStyle("-fx-background-color:#0f1117; -fx-padding:16;");

        Button addBtn = new Button("➕ Add Resource");
        addBtn.setStyle(primaryBtn());
        addBtn.setOnAction(e -> showAddResourcePopup(getWindowFromNode(root), post, root));
        HBox top = new HBox(addBtn); top.setAlignment(Pos.CENTER_RIGHT);
        root.getChildren().add(top);

        loadResourcesAsync(post.id(), root);
        tab.setContent(scrollWrap(root));
        return tab;
    }

    private void loadResourcesAsync(int postId, VBox root) {
        while (root.getChildren().size() > 1) root.getChildren().remove(1);
        Label loading = new Label("Loading resources…");
        loading.setStyle("-fx-text-fill:#64748b; -fx-font-size:13px; -fx-font-style:italic; -fx-padding:10 0;");
        root.getChildren().add(loading);

        executor.submit(() -> {
            List<CollaborationService.TeamResource> list = collaborationService.getTeamResources(postId);
            Platform.runLater(() -> renderResources(list, postId, root));
        });
    }

    private void renderResources(List<CollaborationService.TeamResource> list, int postId, VBox root) {
        while (root.getChildren().size() > 1) root.getChildren().remove(1);
        if (list.isEmpty()) {
            Label none = new Label("No resources yet.");
            none.setStyle("-fx-text-fill:#64748b; -fx-font-size:13px; -fx-font-style:italic; -fx-padding:20 0;");
            root.getChildren().add(none);
            return;
        }
        for (var r : list) {
            HBox row = new HBox(12);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setStyle("-fx-background-color:#1e2235; -fx-background-radius:10; -fx-padding:10 14; -fx-border-color:#2d3150; -fx-border-radius:10;");
            
            VBox info = new VBox(3);
            String icon = "FILE".equals(r.type()) ? "📄 " : "🔗 ";
            Label t = new Label(icon + r.title());
            t.setStyle("-fx-font-weight:bold; -fx-text-fill:#818cf8;");
            Label b = new Label("Added by " + r.addedBy());
            b.setStyle("-fx-text-fill:#475569; -fx-font-size:11px;");
            info.getChildren().addAll(t, b);
            HBox.setHgrow(info, Priority.ALWAYS);

           Button openBtn = new Button("FILE".equals(r.type()) ? "⬇ Download" : "↗ Open");
            openBtn.setStyle("-fx-background-color:#2563eb; -fx-text-fill:white; -fx-background-radius:6; -fx-cursor:hand; -fx-padding:6 12; -fx-font-weight:bold;");
            
            // 🌟 FIX: Bulletproof URL Opener
            openBtn.setOnAction(e -> {
                String urlString = r.url();
                try {
                    // ১. স্পেস থাকলে সেটিকে ব্রাউজারের রিডেবল ফরমেটে (%20) কনভার্ট করা
                    urlString = urlString.replace(" ", "%20");
                    java.net.URI uri = new java.net.URI(urlString);

                    if (java.awt.Desktop.isDesktopSupported() && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.BROWSE)) {
                        java.awt.Desktop.getDesktop().browse(uri);
                    } else {
                        // ২. Fallback: যদি Desktop সাপোর্ট না করে তবে OS কমান্ড দিয়ে ব্রাউজার ওপেন করবে
                        String os = System.getProperty("os.name").toLowerCase();
                        if (os.contains("mac")) {
                            Runtime.getRuntime().exec("open " + urlString);
                        } else if (os.contains("win")) {
                            Runtime.getRuntime().exec("rundll32 url.dll,FileProtocolHandler " + urlString);
                        } else {
                            Runtime.getRuntime().exec("xdg-open " + urlString);
                        }
                    }
                } catch (Exception ex) {
                    ex.printStackTrace(); // টার্মিনালে আসল এররটি প্রিন্ট করবে
                    Platform.runLater(() -> NewPopupHelper.showError(getWindow(root), "Error", "Could not open link:\n" + ex.getMessage()));
                }
            });

            row.getChildren().addAll(info, openBtn);
            root.getChildren().add(row);
        }
    }

    // 🌟 FULL TELEGRAM INTEGRATION FOR FILE UPLOAD
    private void showAddResourcePopup(Window ownerWindow, CollaborationService.Post post, VBox resourcesRoot) {
        VBox content = new VBox(12);
        content.setPadding(new Insets(22));
        content.setStyle("-fx-background-color:#13151f;");

        Label heading = new Label("📎 Add Resource");
        heading.setStyle("-fx-font-size:15px; -fx-font-weight:bold; -fx-text-fill:#e2e8f0;");
        
        ComboBox<String> typeBox = new ComboBox<>();
        typeBox.getItems().addAll("LINK", "FILE");
        typeBox.setValue("LINK");
        typeBox.setStyle("-fx-background-color:#1e2235; -fx-text-fill:white; -fx-border-color:#2d3150; -fx-border-radius:6;");

        TextField titleF = darkField("Title (e.g. Design Doc)");
        TextField urlF   = darkField("Paste URL here...");
        TextArea  descF  = darkArea("Brief description (optional)", 2);

        Button fileBtn = new Button("Choose File 📁");
        fileBtn.setStyle(ghostBtn());
        Label fileNameLbl = new Label("No file selected");
        fileNameLbl.setStyle("-fx-text-fill:#64748b; -fx-font-size:11px;");
        HBox fileBox = new HBox(10, fileBtn, fileNameLbl);
        fileBox.setAlignment(Pos.CENTER_LEFT);
        fileBox.setVisible(false); fileBox.setManaged(false);

        typeBox.setOnAction(e -> {
            boolean isFile = "FILE".equals(typeBox.getValue());
            urlF.setVisible(!isFile); urlF.setManaged(!isFile);
            fileBox.setVisible(isFile); fileBox.setManaged(isFile);
        });

        final File[] selectedFile = {null};
        fileBtn.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            File f = fc.showOpenDialog(ownerWindow);
            if (f != null) {
                selectedFile[0] = f;
                fileNameLbl.setText(f.getName());
            }
        });

        HBox btnRow = new HBox(10);
        btnRow.setAlignment(Pos.CENTER_RIGHT);
        Button cancelBtn = new Button("Cancel"); cancelBtn.setStyle(ghostBtn());
        Button saveBtn   = new Button("💾 Save"); saveBtn.setStyle(primaryBtn());
        btnRow.getChildren().addAll(cancelBtn, saveBtn);

        content.getChildren().addAll(heading, fieldLabel("Type:"), typeBox, fieldLabel("Title:"), titleF, fieldLabel("Source:"), urlF, fileBox, fieldLabel("Description:"), descF, btnRow);

        Stage popup = NewPopupHelper.create(ownerWindow, "Add Resource", content, 380, 480, 480, 520);

        cancelBtn.setOnAction(e -> popup.close());
        saveBtn.setOnAction(e -> {
            if (titleF.getText().isBlank()) {
                NewPopupHelper.showToast(ownerWindow, "⚠️ Title is required!");
                return;
            }
            boolean isFile = "FILE".equals(typeBox.getValue());
            if (isFile && selectedFile[0] == null) {
                NewPopupHelper.showToast(ownerWindow, "⚠️ Please select a file!");
                return;
            }
            if (!isFile && urlF.getText().isBlank()) {
                NewPopupHelper.showToast(ownerWindow, "⚠️ URL is required!");
                return;
            }

            saveBtn.setDisable(true); // Anti-spam
            saveBtn.setText("Processing...");

            new Thread(() -> {
                boolean ok = false;
                if (isFile) {
                    Platform.runLater(() -> NewPopupHelper.showToast(ownerWindow, "⏳ Uploading file to Telegram..."));
                    String fileId = telegramService.uploadToCloud(selectedFile[0]);
                    if (fileId != null) {
                        String url = telegramService.getFileDownloadUrl(fileId);
                        ok = collaborationService.addTeamResource(post.id(), titleF.getText(), url, "FILE", descF.getText(), fileId);
                    }
                } else {
                    ok = collaborationService.addTeamResource(post.id(), titleF.getText(), urlF.getText(), "LINK", descF.getText(), null);
                }

                final boolean finalOk = ok;
                Platform.runLater(() -> {
                    if (finalOk) {
                        popup.close();
                        NewPopupHelper.showToast(ownerWindow, "✅ Resource added successfully!");
                        loadResourcesAsync(post.id(), resourcesRoot);
                    } else {
                        saveBtn.setDisable(false);
                        saveBtn.setText("💾 Save");
                        NewPopupHelper.showError(ownerWindow, "Error", "Failed to add resource.");
                    }
                });
            }).start();
        });
        popup.show();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // TAB: PLANS (Unchanged Logic, added Anti-Spam)
    // ══════════════════════════════════════════════════════════════════════════

    private Tab buildPlansTab(CollaborationService.Post post) {
        Tab tab = new Tab("📋 Plans");
        TabPane inner = new TabPane();
        inner.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        inner.setStyle("-fx-background-color:#0f1117;");
        inner.getTabs().addAll(buildActivePlansTab(post), buildPlanHistoryTab(post));
        tab.setContent(inner);
        return tab;
    }

    private Tab buildActivePlansTab(CollaborationService.Post post) {
        Tab tab = new Tab("🚀 Active Plans");
        VBox root = new VBox(14);
        root.setStyle("-fx-background-color:#0f1117; -fx-padding:16;");

        Button newPlanBtn = new Button("➕ New Plan");
        newPlanBtn.setStyle(primaryBtn());
        newPlanBtn.setOnAction(e -> showCreatePlanPopup(getWindowFromNode(root), post, root));
        HBox top = new HBox(newPlanBtn); top.setAlignment(Pos.CENTER_RIGHT);
        root.getChildren().add(top);

        loadActivePlansAsync(post, root);
        tab.setContent(scrollWrap(root));
        return tab;
    }

    private void loadActivePlansAsync(CollaborationService.Post post, VBox root) {
        while (root.getChildren().size() > 1) root.getChildren().remove(1);
        executor.submit(() -> {
            List<CollaborationService.Plan> plans = collaborationService.getPlans(post.id());
            Platform.runLater(() -> {
                while (root.getChildren().size() > 1) root.getChildren().remove(1);
                boolean hasActive = false;
                for (var plan : plans) {
                    if ("COMPLETED".equals(plan.status())) continue;
                    hasActive = true;
                    root.getChildren().add(buildPlanCard(plan, post, root));
                }
                if (!hasActive) {
                    Label none = new Label("No active plans. Create one!");
                    none.setStyle("-fx-text-fill:#64748b; -fx-font-size:13px; -fx-font-style:italic; -fx-padding:20 0;");
                    root.getChildren().add(none);
                }
            });
        });
    }

    private VBox buildPlanCard(CollaborationService.Plan plan, CollaborationService.Post post, VBox plansRoot) {
        VBox card = new VBox(10);
        card.setStyle("-fx-background-color:#1a1d27; -fx-border-color:#2d3150; -fx-border-radius:12; -fx-background-radius:12; -fx-padding:14;");

        HBox titleRow = new HBox(10); titleRow.setAlignment(Pos.CENTER_LEFT);
        Label planTitle = new Label("📋 " + plan.title());
        planTitle.setStyle("-fx-font-weight:bold; -fx-font-size:15px; -fx-text-fill:#e2e8f0;");
        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
        Button addStepBtn = new Button("+ Step");
        addStepBtn.setStyle("-fx-background-color:rgba(99,102,241,0.15); -fx-text-fill:#818cf8; -fx-font-weight:bold; -fx-background-radius:6; -fx-cursor:hand; -fx-padding:5 10;");
        titleRow.getChildren().addAll(planTitle, sp, addStepBtn);

        VBox stepsBox = new VBox(6);
        loadStepsAsync(plan, stepsBox, post, plansRoot);
        addStepBtn.setOnAction(e -> showAddStepPopup(getWindowFromNode(card), plan, stepsBox, post, plansRoot));

        card.getChildren().addAll(titleRow, stepsBox);
        return card;
    }

    private void loadStepsAsync(CollaborationService.Plan plan, VBox stepsBox, CollaborationService.Post post, VBox plansRoot) {
        stepsBox.getChildren().clear();
        executor.submit(() -> {
            List<CollaborationService.PlanStep> steps = collaborationService.getSteps(plan.id());
            Platform.runLater(() -> renderSteps(steps, plan, stepsBox, post, plansRoot));
        });
    }

    private void renderSteps(List<CollaborationService.PlanStep> steps, CollaborationService.Plan plan, VBox stepsBox, CollaborationService.Post post, VBox plansRoot) {
        stepsBox.getChildren().clear();
        if (steps.isEmpty()) {
            Label none = new Label("No steps yet.");
            none.setStyle("-fx-text-fill:#64748b; -fx-font-size:12px; -fx-font-style:italic;");
            stepsBox.getChildren().add(none);
            return;
        }
        for (var step : steps) {
            boolean isDone = "DONE".equals(step.status());
            HBox row = new HBox(10); row.setAlignment(Pos.CENTER_LEFT);
            row.setStyle(isDone ? "-fx-background-color:rgba(52,211,153,0.08); -fx-background-radius:8; -fx-padding:8 12;" : "-fx-background-color:#23263a; -fx-background-radius:8; -fx-padding:8 12;");
            Label desc = new Label((isDone ? "✅ " : "⬜ ") + step.description());
            desc.setStyle("-fx-text-fill:" + (isDone ? "#34d399" : "#e2e8f0") + "; -fx-font-size:13px;" + (isDone ? " -fx-strikethrough:true;" : ""));
            HBox.setHgrow(desc, Priority.ALWAYS);
            row.getChildren().add(desc);

            if (!isDone) {
                Button doneBtn = new Button("✔"); doneBtn.setStyle(successBtn());
                doneBtn.setOnAction(e -> {
                    doneBtn.setDisable(true); // Anti-spam
                    executor.submit(() -> {
                        collaborationService.completeStep(step.id(), plan.id());
                        Platform.runLater(() -> loadActivePlansAsync(post, plansRoot));
                    });
                });
                row.getChildren().add(doneBtn);
            }
            stepsBox.getChildren().add(row);
        }
    }

    private Tab buildPlanHistoryTab(CollaborationService.Post post) {
        Tab tab = new Tab("📜 History");
        VBox root = new VBox(14); root.setStyle("-fx-background-color:#0f1117; -fx-padding:16;");
        executor.submit(() -> {
            List<CollaborationService.PlanHistory> history = collaborationService.getPlanHistory(post.id());
            Platform.runLater(() -> {
                root.getChildren().clear();
                if (history.isEmpty()) {
                    Label none = new Label("No completed plans yet.");
                    none.setStyle("-fx-text-fill:#64748b; -fx-font-style:italic;");
                    root.getChildren().add(none);
                } else {
                    for (var h : history) {
                        VBox card = new VBox(8);
                        card.setStyle("-fx-background-color:#12201a; -fx-border-color:#34d399; -fx-border-radius:12; -fx-background-radius:12; -fx-padding:14;");
                        Label pl = new Label("✅ " + h.planTitle());
                        pl.setStyle("-fx-font-weight:bold; -fx-font-size:15px; -fx-text-fill:#34d399;");
                        card.getChildren().add(pl);
                        root.getChildren().add(card);
                    }
                }
            });
        });
        tab.setContent(scrollWrap(root));
        return tab;
    }

    private void showApplyPopup(Window ownerWindow, CollaborationService.Post post, VBox roomContainer) {
        executor.submit(() -> {
            List<CollaborationService.Requirement> reqs = collaborationService.getRequirements(post.id());
            Platform.runLater(() -> {
                VBox content = new VBox(12); content.setPadding(new Insets(22)); content.setStyle("-fx-background-color:#13151f;");
                Label heading = new Label("📩 Apply — " + post.title());
                heading.setStyle("-fx-font-size:15px; -fx-font-weight:bold; -fx-text-fill:#e2e8f0;");
                content.getChildren().add(heading);

                List<TextField> answerFields = new ArrayList<>();
                for (var req : reqs) {
                    content.getChildren().add(fieldLabel(req.question()));
                    TextField tf = darkField("Your answer…");
                    answerFields.add(tf);
                    content.getChildren().add(tf);
                }

                HBox btnRow = new HBox(10); btnRow.setAlignment(Pos.CENTER_RIGHT);
                Button cancelBtn = new Button("Cancel"); cancelBtn.setStyle(ghostBtn());
                Button applyBtn  = new Button("📩 Apply"); applyBtn.setStyle(primaryBtn());
                btnRow.getChildren().addAll(cancelBtn, applyBtn);
                content.getChildren().add(btnRow);

                Stage popup = NewPopupHelper.create(ownerWindow, "Apply", content, 360, 210, 460, 200 + (reqs.size() * 72.0));
                cancelBtn.setOnAction(e -> popup.close());
                applyBtn.setOnAction(e -> {
                    applyBtn.setDisable(true); // Anti-spam
                    List<Integer> ids = reqs.stream().map(CollaborationService.Requirement::id).toList();
                    List<String> answers = answerFields.stream().map(TextField::getText).toList();
                    executor.submit(() -> {
                        boolean ok = collaborationService.applyToTeamWithAnswers(post.id(), ids, answers);
                        Platform.runLater(() -> {
                            popup.close();
                            if (ok) NewPopupHelper.showToast(ownerWindow, "✅ Request sent!");
                            else NewPopupHelper.showError(ownerWindow, "Error", "Already applied!");
                            loadRoomView(post, roomContainer);
                        });
                    });
                });
                popup.show();
            });
        });
    }

    private void showCreatePlanPopup(Window ownerWindow, CollaborationService.Post post, VBox plansRoot) {
        VBox content = new VBox(12); content.setPadding(new Insets(22)); content.setStyle("-fx-background-color:#13151f;");
        Label heading = new Label("📋 New Plan"); heading.setStyle("-fx-font-size:15px; -fx-font-weight:bold; -fx-text-fill:#e2e8f0;");
        TextField titleF = darkField("E.g. Sprint 1");
        
        HBox btnRow = new HBox(10); btnRow.setAlignment(Pos.CENTER_RIGHT);
        Button cancelBtn = new Button("Cancel"); cancelBtn.setStyle(ghostBtn());
        Button createBtn = new Button("➕ Create"); createBtn.setStyle(primaryBtn());
        btnRow.getChildren().addAll(cancelBtn, createBtn);
        content.getChildren().addAll(heading, fieldLabel("Plan Title:"), titleF, btnRow);

        Stage popup = NewPopupHelper.create(ownerWindow, "Create Plan", content, 340, 200, 440, 230);
        cancelBtn.setOnAction(e -> popup.close());
        createBtn.setOnAction(e -> {
            createBtn.setDisable(true); // Anti-spam
            executor.submit(() -> {
                collaborationService.createPlan(post.id(), titleF.getText().trim());
                Platform.runLater(() -> { popup.close(); loadActivePlansAsync(post, plansRoot); });
            });
        });
        popup.show();
    }

    private void showAddStepPopup(Window ownerWindow, CollaborationService.Plan plan, VBox stepsBox, CollaborationService.Post post, VBox plansRoot) {
        VBox content = new VBox(12); content.setPadding(new Insets(22)); content.setStyle("-fx-background-color:#13151f;");
        TextField stepF = darkField("E.g. Set up database");
        HBox btnRow = new HBox(10); btnRow.setAlignment(Pos.CENTER_RIGHT);
        Button cancelBtn = new Button("Cancel"); cancelBtn.setStyle(ghostBtn());
        Button addBtn = new Button("➕ Add"); addBtn.setStyle(primaryBtn());
        btnRow.getChildren().addAll(cancelBtn, addBtn);
        content.getChildren().addAll(new Label("➕ Add Step"), stepF, btnRow);

        Stage popup = NewPopupHelper.create(ownerWindow, "Add Step", content, 340, 210, 460, 250);
        cancelBtn.setOnAction(e -> popup.close());
        addBtn.setOnAction(e -> {
            addBtn.setDisable(true); // Anti-spam
            executor.submit(() -> {
                collaborationService.addStep(plan.id(), stepF.getText().trim());
                Platform.runLater(() -> { popup.close(); loadStepsAsync(plan, stepsBox, post, plansRoot); });
            });
        });
        popup.show();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // HELPERS & UI COMPONENTS
    // ══════════════════════════════════════════════════════════════════════════

    private void showPlaceholder(VBox roomContainer) {
        roomContainer.setStyle("-fx-background-color:#0f1117; -fx-background-radius:16; -fx-border-color:#2d3150; -fx-border-radius:16;");
        VBox ph = new VBox(10); ph.setAlignment(Pos.CENTER); VBox.setVgrow(ph, Priority.ALWAYS);
        Label l1 = new Label("💬 Team Workspace"); l1.setStyle("-fx-font-size:20px; -fx-font-weight:bold; -fx-text-fill:#e2e8f0;");
        Label l2 = new Label("Select a team to start collaborating."); l2.setStyle("-fx-text-fill:#64748b; -fx-font-size:14px;");
        ph.getChildren().addAll(l1, l2); roomContainer.getChildren().add(ph);
    }

    // 🌟 HELPER TO SHOW USER PROFILE ON AVATAR CLICK
    private void showUserProfileDialog(String userId, Window ownerWindow) {
        new Thread(() -> {
            com.scholar.model.Profile p = profileService.getUserProfile(java.util.UUID.fromString(userId));
            Platform.runLater(() -> {
                if (p == null) {
                    NewPopupHelper.showError(ownerWindow, "Error", "Profile is private or hidden.");
                    return;
                }
                VBox box = new VBox(15); box.setAlignment(Pos.CENTER); box.setPadding(new Insets(30));
                box.setStyle("-fx-background-color: #161b27; -fx-border-color: #334155; -fx-border-width: 1; -fx-background-radius: 12;");
                Node avatar = createAvatar(p.getProfilePictureUrl(), p.getUsername() != null ? p.getUsername() : p.getFullName(), 80);
                Label nameLbl = new Label(p.getFullName()); nameLbl.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: white;");
                Label userLbl = new Label("@" + (p.getUsername() != null ? p.getUsername() : "student")); userLbl.setStyle("-fx-text-fill: #94a3b8;");
                Label eduLbl = new Label("🎓 " + (p.getDepartment() != null ? p.getDepartment() : "N/A") + " at " + (p.getUniversityName() != null ? p.getUniversityName() : "N/A")); eduLbl.setStyle("-fx-text-fill: #cbd5e1;");
                Button closeBtn = new Button("Close"); closeBtn.setStyle(ghostBtn());
                box.getChildren().addAll(avatar, nameLbl, userLbl, eduLbl, closeBtn);
                Stage stage = NewPopupHelper.create(ownerWindow, "User Profile", box, 350, 400, 400, 450);
                closeBtn.setOnAction(e -> stage.close());
                stage.showAndWait();
            });
        }).start();
    }

    // 🌟 FAST & PERFECT CIRCULAR AVATARS
    private Node createAvatar(String imageUrl, String fallbackName, double size) {
        if (imageUrl != null && !imageUrl.isEmpty()) {
            Image img = new Image(imageUrl, true);
            ImageView imgView = new ImageView(img);
            imgView.setFitWidth(size); imgView.setFitHeight(size); imgView.setPreserveRatio(true);
            img.progressProperty().addListener((obs, oldV, newV) -> {
                if (newV.doubleValue() == 1.0 && !img.isError()) {
                    Platform.runLater(() -> {
                        double minSide = Math.min(img.getWidth(), img.getHeight());
                        double x = (img.getWidth() - minSide) / 2.0; double y = (img.getHeight() - minSide) / 2.0;
                        imgView.setViewport(new javafx.geometry.Rectangle2D(x, y, minSide, minSide));
                        imgView.setClip(new Circle(size/2, size/2, size/2));
                    });
                }
            });
            return imgView;
        } else {
            String initial = fallbackName != null && !fallbackName.isEmpty() ? String.valueOf(fallbackName.charAt(0)).toUpperCase() : "?";
            Label lbl = new Label(initial);
            lbl.setStyle("-fx-background-color: #2563eb; -fx-text-fill: white; -fx-font-weight: bold; -fx-alignment: center; -fx-background-radius: 50; -fx-min-width: " + size + "; -fx-min-height: " + size + "; -fx-font-size: " + (size/2.2) + "px;");
            return lbl;
        }
    }

    private Window getWindow(VBox box)       { return getWindowFromNode(box); }
    private Window getWindowFromNode(Node n) { try { if (n.getScene() != null) return n.getScene().getWindow(); } catch (Exception ignored) {} return null; }
    private ScrollPane scrollWrap(VBox root) { ScrollPane sp = new ScrollPane(root); sp.setFitToWidth(true); sp.setStyle("-fx-background:#0f1117; -fx-background-color:#0f1117; -fx-border-color:transparent;"); return sp; }
    private Label makeBadge(String text, String color, String bg) { Label l = new Label(text); l.setStyle("-fx-text-fill:" + color + "; -fx-background-color:" + bg + "; -fx-font-size:11px; -fx-font-weight:bold; -fx-padding:3 8; -fx-background-radius:6;"); return l; }
    private TextField darkField(String prompt) { TextField tf = new TextField(); tf.setPromptText(prompt); tf.setStyle("-fx-background-color:#1e2235; -fx-text-fill:#e2e8f0; -fx-prompt-text-fill:#64748b; -fx-border-color:#2d3150; -fx-border-radius:8; -fx-background-radius:8; -fx-padding:9;"); return tf; }
    private TextArea darkArea(String prompt, int rows) { TextArea ta = new TextArea(); ta.setPromptText(prompt); ta.setPrefRowCount(rows); ta.setWrapText(true); ta.setStyle("-fx-background-color:#1e2235; -fx-text-fill:#e2e8f0; -fx-prompt-text-fill:#64748b; -fx-border-color:#2d3150; -fx-border-radius:8; -fx-background-radius:8; -fx-padding:8;"); return ta; }
    private Label fieldLabel(String text) { Label l = new Label(text); l.setStyle("-fx-text-fill:#94a3b8; -fx-font-size:12px; -fx-font-weight:bold;"); return l; }
    private String primaryBtn() { return "-fx-background-color:linear-gradient(to right,#6366f1,#8b5cf6); -fx-text-fill:white; -fx-font-weight:bold; -fx-background-radius:8; -fx-padding:8 20; -fx-cursor:hand;"; }
    private String dangerBtn() { return "-fx-text-fill:#f87171; -fx-background-color:rgba(248,113,113,0.12); -fx-font-weight:bold; -fx-background-radius:8; -fx-border-color:rgba(248,113,113,0.3); -fx-border-radius:8; -fx-cursor:hand; -fx-padding:7 14;"; }
    private String successBtn() { return "-fx-text-fill:#34d399; -fx-background-color:rgba(52,211,153,0.12); -fx-font-weight:bold; -fx-background-radius:8; -fx-border-color:rgba(52,211,153,0.3); -fx-border-radius:8; -fx-cursor:hand; -fx-padding:7 14;"; }
    private String ghostBtn() { return "-fx-background-color:#23263a; -fx-text-fill:#94a3b8; -fx-background-radius:8; -fx-border-color:#2d3150; -fx-border-radius:8; -fx-padding:8 20; -fx-cursor:hand;"; }
}