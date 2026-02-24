package com.scholar.controller.collaboration;

import com.scholar.service.AuthService;
import com.scholar.service.CollaborationService;
import com.scholar.util.PopupHelper;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component("dashCollaborationController")
public class CollaborationController {

    @Autowired private CollaborationService collaborationService;
    @Autowired private TeamWorkspaceController teamWorkspaceController;

    @FXML private VBox channelList;
    @FXML private VBox teamList;
    @FXML private VBox roomContainer;
    @FXML private VBox channelInfoBox;
    @FXML private Label channelTitleLabel;
    @FXML private Label channelDescLabel;
    @FXML private Button createChannelBtn;

    private int currentChannelId = -1;
    private String currentChannelName = "";

    // ── Evaluated at runtime, not class-load time ──────────────────────────
    private boolean isAdmin() {
        return "admin".equals(AuthService.CURRENT_USER_ROLE);
    }

    // Shared background executor for async DB calls
    private final ExecutorService executor =
            Executors.newCachedThreadPool(r -> { Thread t = new Thread(r); t.setDaemon(true); return t; });

    // ─────────────────────────────────────────────────────────────────────────
    // INIT
    // ─────────────────────────────────────────────────────────────────────────

    public void init(VBox channelList, VBox teamList, VBox roomContainer,
                     VBox channelInfoBox, Label channelTitleLabel,
                     Label channelDescLabel, Button createChannelBtn) {
        this.channelList       = channelList;
        this.teamList          = teamList;
        this.roomContainer     = roomContainer;
        this.channelInfoBox    = channelInfoBox;
        this.channelTitleLabel = channelTitleLabel;
        this.channelDescLabel  = channelDescLabel;
        this.createChannelBtn  = createChannelBtn;

        // ── Admin button visibility — evaluated NOW after login ──────────────
        applyAdminVisibility();

        loadChannelsAsync();
    }

    /** Shows/hides the create-channel button based on current role. */
    private void applyAdminVisibility() {
        if (createChannelBtn == null) return;
        boolean admin = isAdmin();
        createChannelBtn.setVisible(admin);
        createChannelBtn.setManaged(admin);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CHANNELS — async load
    // ─────────────────────────────────────────────────────────────────────────

    public void loadChannelsAsync() {
        if (channelList == null) return;
        // Show a spinner while loading
        showChannelListLoading(true);

        executor.submit(() -> {
            List<CollaborationService.Channel> channels = collaborationService.getAllChannels();
            Platform.runLater(() -> {
                showChannelListLoading(false);
                renderChannels(channels);
            });
        });
    }

    /** Kept for callers that need synchronous reload after create/delete. */
    public void loadChannels() {
        loadChannelsAsync();
    }

    private void showChannelListLoading(boolean loading) {
        if (channelList == null) return;
        if (loading) {
            channelList.getChildren().clear();
            Label lbl = new Label("Loading…");
            lbl.setStyle("-fx-text-fill:#64748b; -fx-font-size:12px; -fx-font-style:italic; -fx-padding:8 14;");
            channelList.getChildren().add(lbl);
        }
    }

    private void renderChannels(List<CollaborationService.Channel> channels) {
        channelList.getChildren().clear();

        if (channels.isEmpty()) {
            Label empty = new Label("No channels yet.");
            empty.setStyle("-fx-text-fill:#64748b; -fx-font-size:12px; -fx-font-style:italic; -fx-padding:8 14;");
            channelList.getChildren().add(empty);
            return;
        }

        for (var c : channels) {
            HBox row = new HBox(4);
            row.setAlignment(Pos.CENTER_LEFT);

            Button btn = new Button("# " + c.title());
            btn.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(btn, Priority.ALWAYS);
            btn.setAlignment(Pos.CENTER_LEFT);
            btn.setStyle(channelBtnStyle(false));
            btn.setOnMouseEntered(e -> { if (currentChannelId != c.id()) btn.setStyle(channelBtnHover()); });
            btn.setOnMouseExited(e  -> { if (currentChannelId != c.id()) btn.setStyle(channelBtnStyle(false)); });
            btn.setOnAction(e -> selectChannel(c, btn));
            row.getChildren().add(btn);

            // Admin: delete channel button
            if (isAdmin()) {
                Button del = new Button("✕");
                del.setStyle("-fx-background-color:transparent; -fx-text-fill:#f87171; "
                        + "-fx-cursor:hand; -fx-font-size:12px; -fx-padding:4 6;");
                del.setOnMouseEntered(e -> del.setStyle(
                        "-fx-background-color:rgba(248,113,113,0.15); -fx-text-fill:#f87171; "
                        + "-fx-background-radius:6; -fx-cursor:hand; -fx-padding:4 6;"));
                del.setOnMouseExited(e -> del.setStyle(
                        "-fx-background-color:transparent; -fx-text-fill:#f87171; "
                        + "-fx-cursor:hand; -fx-font-size:12px; -fx-padding:4 6;"));
                del.setOnAction(e ->
                    PopupHelper.showConfirm(getWindow(),
                        "Delete Channel",
                        "Delete \"" + c.title() + "\" and ALL its teams,\nmessages and resources?",
                        () -> {
                            executor.submit(() -> {
                                collaborationService.deleteChannel(c.id());
                                Platform.runLater(() -> {
                                    if (currentChannelId == c.id()) {
                                        currentChannelId = -1;
                                        currentChannelName = "";
                                        if (channelInfoBox != null) {
                                            channelInfoBox.setVisible(false);
                                            channelInfoBox.setManaged(false);
                                        }
                                        teamList.getChildren().clear();
                                        roomContainer.getChildren().clear();
                                    }
                                    loadChannelsAsync();
                                });
                            });
                        }
                    )
                );
                row.getChildren().add(del);
            }

            channelList.getChildren().add(row);
        }
    }

    private void selectChannel(CollaborationService.Channel c, Button btn) {
        currentChannelId   = c.id();
        currentChannelName = c.title();

        // Reset all channel button styles
        channelList.getChildren().forEach(node -> {
            if (node instanceof HBox hb)
                hb.getChildren().forEach(child -> {
                    if (child instanceof Button b && b != btn)
                        b.setStyle(channelBtnStyle(false));
                });
        });
        btn.setStyle(channelBtnStyle(true));

        showChannelInfo(c);
        loadTeamsForChannelAsync(c.id(), c.title());
    }

    private void showChannelInfo(CollaborationService.Channel channel) {
        if (channelInfoBox == null) return;
        channelTitleLabel.setText("📌 " + channel.title());
        channelDescLabel.setText(channel.desc() != null && !channel.desc().isEmpty()
                ? channel.desc() : "No information provided for this channel.");
        channelInfoBox.setVisible(true);
        channelInfoBox.setManaged(true);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TEAMS — async load
    // ─────────────────────────────────────────────────────────────────────────

    public void loadTeamsForChannelAsync(int channelId, String channelName) {
        if (teamList == null) return;
        teamList.getChildren().clear();

        // Loading indicator
        Label loading = new Label("Loading teams…");
        loading.setStyle("-fx-text-fill:#64748b; -fx-font-size:13px; -fx-font-style:italic; -fx-padding:20;");
        teamList.getChildren().add(loading);

        executor.submit(() -> {
            List<CollaborationService.Post> posts = collaborationService.getPostsForChannel(channelId);
            Platform.runLater(() -> renderTeams(posts, channelId, channelName));
        });
    }

    public void loadTeamsForChannel(int channelId, String channelName) {
        loadTeamsForChannelAsync(channelId, channelName);
    }

    private void renderTeams(List<CollaborationService.Post> posts,
                              int channelId, String channelName) {
        teamList.getChildren().clear();

        if (posts.isEmpty()) {
            Label lbl = new Label("No teams yet in #" + channelName + ". Be the first!");
            lbl.setStyle("-fx-text-fill:#64748b; -fx-padding:20; -fx-font-size:14px; -fx-font-style:italic;");
            teamList.getChildren().add(lbl);
            return;
        }

        for (var post : posts) {
            VBox card = new VBox(10);
            card.setStyle(teamCardStyle(false));
            card.setOnMouseEntered(e -> card.setStyle(teamCardStyle(true)));
            card.setOnMouseExited(e  -> card.setStyle(teamCardStyle(false)));

            Label title = new Label("🚀 " + post.title());
            title.setStyle("-fx-font-weight:bold; -fx-font-size:16px; -fx-text-fill:#e2e8f0;");

            Label desc = new Label(post.desc() == null || post.desc().isEmpty()
                    ? "No description." : post.desc());
            desc.setWrapText(true);
            desc.setStyle("-fx-text-fill:#94a3b8; -fx-font-size:13px;");

            HBox meta = new HBox(10);
            meta.setAlignment(Pos.CENTER_LEFT);
            meta.getChildren().addAll(
                badge("👥 Max: " + post.maxMembers(), "#34d399", "rgba(52,211,153,0.15)"),
                badge(post.status(),
                    "OPEN".equals(post.status()) ? "#34d399" : "#fbbf24",
                    "OPEN".equals(post.status()) ? "rgba(52,211,153,0.15)" : "rgba(251,191,36,0.15)")
            );

            Button viewBtn = new Button("View Workspace →");
            viewBtn.setMaxWidth(Double.MAX_VALUE);
            viewBtn.setStyle("-fx-background-color:linear-gradient(to right,#6366f1,#8b5cf6); "
                    + "-fx-text-fill:white; -fx-cursor:hand; -fx-font-weight:bold; "
                    + "-fx-background-radius:8; -fx-padding:8;");
            viewBtn.setOnAction(e -> teamWorkspaceController.loadRoomView(post, roomContainer));

            card.getChildren().addAll(title, desc, meta, viewBtn);
            teamList.getChildren().add(card);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CREATE TEAM
    // ─────────────────────────────────────────────────────────────────────────

    @FXML
    public void onCreatePost() {
        if (currentChannelId == -1) {
            PopupHelper.showError(getWindow(), "No Channel Selected",
                    "Please select a channel from the left first.");
            return;
        }

        // Check one-team-per-channel asynchronously
        executor.submit(() -> {
            boolean alreadyIn = collaborationService.isUserInAnyTeamUnderChannel(currentChannelId);
            Platform.runLater(() -> {
                if (alreadyIn) {
                    PopupHelper.showError(getWindow(), "Already in a Team",
                            "You are already in a team under #" + currentChannelName
                            + ".\nYou can only join one team per channel.");
                } else {
                    showCreateTeamPopup();
                }
            });
        });
    }

    private void showCreateTeamPopup() {
        VBox content = new VBox(12);
        content.setPadding(new Insets(20));
        content.setStyle("-fx-background-color:#13151f;");

        Label heading = new Label("🚀 Create Team in #" + currentChannelName);
        heading.setStyle("-fx-font-size:15px; -fx-font-weight:bold; -fx-text-fill:#e2e8f0;");
        heading.setWrapText(true);

        TextField titleField = darkField("E.g. Java Hackathon Team");
        TextArea  descField  = darkArea("Describe your team's goal…", 3);
        Spinner<Integer> spinner = new Spinner<>(2, 20, 4);
        spinner.setStyle("-fx-background-color:#1e2235; -fx-border-color:#2d3150; -fx-border-radius:8;");
        spinner.setEditable(true);

        VBox qBox = new VBox(8);
        List<TextField> qFields = new ArrayList<>();
        Button addQ = new Button("+ Add Join Question");
        addQ.setStyle("-fx-background-color:#23263a; -fx-text-fill:#818cf8; -fx-cursor:hand; "
                + "-fx-font-weight:bold; -fx-border-color:#2d3150; -fx-border-radius:8; "
                + "-fx-background-radius:8; -fx-padding:8 14;");
        addQ.setOnAction(e -> {
            TextField tf = darkField("E.g. What is your tech stack?");
            qFields.add(tf);
            qBox.getChildren().add(tf);
        });

        HBox btnRow = new HBox(10);
        btnRow.setAlignment(Pos.CENTER_RIGHT);
        Button cancelBtn = new Button("Cancel");  cancelBtn.setStyle(ghostBtn());
        Button createBtn = new Button("🚀 Create"); createBtn.setStyle(primaryBtn());
        btnRow.getChildren().addAll(cancelBtn, createBtn);

        content.getChildren().addAll(
            heading,
            fieldLabel("Team Name:"),   titleField,
            fieldLabel("Description:"), descField,
            fieldLabel("Max Members:"), spinner,
            fieldLabel("Join Questions (Optional):"), qBox, addQ,
            btnRow
        );

        Stage popup = PopupHelper.create(getWindow(), "Create Team",
                content, 400, 480, 500, 560);

        cancelBtn.setOnAction(e -> popup.close());
        createBtn.setOnAction(e -> {
            String t = titleField.getText().trim();
            String d = descField.getText().trim();
            if (t.isEmpty() || d.isEmpty()) {
                PopupHelper.showError(popup, "Missing Fields",
                        "Team name and description cannot be empty.");
                return;
            }
            List<String> qs = qFields.stream()
                    .map(TextField::getText).filter(s -> !s.isBlank()).toList();
            int maxMem = spinner.getValue();
            popup.close();

            executor.submit(() -> {
                boolean ok = collaborationService.createPostWithRequirements(
                        currentChannelId, t, d, maxMem, qs);
                Platform.runLater(() -> {
                    if (ok) {
                        PopupHelper.showInfo(getWindow(), "Team Created",
                                "Your team was created successfully! 🚀");
                        loadTeamsForChannelAsync(currentChannelId, currentChannelName);
                    } else {
                        PopupHelper.showError(getWindow(), "Failed",
                                "Could not create team. Please try again.");
                    }
                });
            });
        });

        popup.show();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CREATE CHANNEL (Admin only)
    // ─────────────────────────────────────────────────────────────────────────

    @FXML
    public void onCreateChannel() {
        // Double-guard: only admins should reach this, but just in case
        if (!isAdmin()) {
            PopupHelper.showError(getWindow(), "Access Denied",
                    "Only admins can create channels.");
            return;
        }

        VBox content = new VBox(12);
        content.setPadding(new Insets(20));
        content.setStyle("-fx-background-color:#13151f;");

        Label heading = new Label("📁 New Channel");
        heading.setStyle("-fx-font-size:15px; -fx-font-weight:bold; -fx-text-fill:#e2e8f0;");

        TextField nameField = darkField("E.g. Codeforces Grind");
        TextArea  descField = darkArea("Describe this channel…", 4);

        HBox btnRow = new HBox(10);
        btnRow.setAlignment(Pos.CENTER_RIGHT);
        Button cancelBtn = new Button("Cancel");    cancelBtn.setStyle(ghostBtn());
        Button createBtn = new Button("➕ Create"); createBtn.setStyle(primaryBtn());
        btnRow.getChildren().addAll(cancelBtn, createBtn);

        content.getChildren().addAll(
            heading,
            fieldLabel("Channel Name:"),   nameField,
            fieldLabel("Description:"),    descField,
            btnRow
        );

        Stage popup = PopupHelper.create(getWindow(), "Create Channel",
                content, 380, 320, 460, 360);

        cancelBtn.setOnAction(e -> popup.close());
        createBtn.setOnAction(e -> {
            if (nameField.getText().isBlank()) {
                PopupHelper.showError(popup, "Missing Name", "Channel name cannot be empty.");
                return;
            }
            String name = nameField.getText().trim();
            String desc = descField.getText().trim();
            popup.close();

            executor.submit(() -> {
                boolean ok = collaborationService.createChannel(name, desc);
                Platform.runLater(() -> {
                    if (ok) {
                        PopupHelper.showInfo(getWindow(), "Channel Created",
                                "Channel \"" + name + "\" created successfully! 🎉");
                        loadChannelsAsync();
                    } else {
                        PopupHelper.showError(getWindow(), "Failed",
                                "Could not create channel. Please try again.");
                    }
                });
            });
        });

        popup.show();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // WINDOW HELPER
    // ─────────────────────────────────────────────────────────────────────────

    private Window getWindow() {
        try {
            if (roomContainer != null && roomContainer.getScene() != null)
                return roomContainer.getScene().getWindow();
            if (channelList != null && channelList.getScene() != null)
                return channelList.getScene().getWindow();
        } catch (Exception ignored) {}
        return null;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // STYLE HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    private String channelBtnStyle(boolean active) {
        return active
            ? "-fx-background-color:#4f46e5; -fx-text-fill:white; -fx-font-size:14px; "
              + "-fx-font-weight:bold; -fx-cursor:hand; -fx-padding:10 15; -fx-background-radius:8;"
            : "-fx-background-color:transparent; -fx-text-fill:#94a3b8; -fx-font-size:14px; "
              + "-fx-font-weight:bold; -fx-cursor:hand; -fx-padding:10 15; -fx-background-radius:8;";
    }

    private String channelBtnHover() {
        return "-fx-background-color:rgba(99,102,241,0.15); -fx-text-fill:#c7d2fe; "
                + "-fx-font-size:14px; -fx-font-weight:bold; -fx-cursor:hand; "
                + "-fx-padding:10 15; -fx-background-radius:8;";
    }

    private String teamCardStyle(boolean hover) {
        return hover
            ? "-fx-background-color:#1e2235; -fx-padding:18; -fx-background-radius:14; "
              + "-fx-border-color:#6366f1; -fx-border-radius:14; "
              + "-fx-effect:dropshadow(gaussian,rgba(99,102,241,0.2),10,0,0,3);"
            : "-fx-background-color:#1a1d27; -fx-padding:18; -fx-background-radius:14; "
              + "-fx-border-color:#2d3150; -fx-border-radius:14; "
              + "-fx-effect:dropshadow(gaussian,rgba(0,0,0,0.25),8,0,0,3);";
    }

    private Label badge(String text, String color, String bg) {
        Label l = new Label(text);
        l.setStyle("-fx-font-size:12px; -fx-text-fill:" + color + "; -fx-font-weight:bold; "
                + "-fx-background-color:" + bg + "; -fx-padding:4 8; -fx-background-radius:6;");
        return l;
    }

    private TextField darkField(String prompt) {
        TextField tf = new TextField();
        tf.setPromptText(prompt);
        tf.setStyle("-fx-background-color:#1e2235; -fx-text-fill:#e2e8f0; "
                + "-fx-prompt-text-fill:#64748b; -fx-border-color:#2d3150; "
                + "-fx-border-radius:8; -fx-background-radius:8; -fx-padding:9;");
        return tf;
    }

    private TextArea darkArea(String prompt, int rows) {
        TextArea ta = new TextArea();
        ta.setPromptText(prompt);
        ta.setPrefRowCount(rows);
        ta.setWrapText(true);
        ta.setStyle("-fx-background-color:#1e2235; -fx-text-fill:#e2e8f0; "
                + "-fx-prompt-text-fill:#64748b; -fx-border-color:#2d3150; "
                + "-fx-border-radius:8; -fx-background-radius:8; -fx-padding:8;");
        return ta;
    }

    private Label fieldLabel(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-text-fill:#94a3b8; -fx-font-size:12px; -fx-font-weight:bold;");
        return l;
    }

    private String primaryBtn() {
        return "-fx-background-color:linear-gradient(to right,#6366f1,#8b5cf6); "
                + "-fx-text-fill:white; -fx-font-weight:bold; "
                + "-fx-background-radius:8; -fx-padding:8 20; -fx-cursor:hand;";
    }

    private String ghostBtn() {
        return "-fx-background-color:#23263a; -fx-text-fill:#94a3b8; "
                + "-fx-background-radius:8; -fx-border-color:#2d3150; -fx-border-radius:8; "
                + "-fx-padding:8 20; -fx-cursor:hand;";
    }
}