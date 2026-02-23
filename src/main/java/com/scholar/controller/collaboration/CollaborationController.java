package com.scholar.controller.collaboration;

import com.scholar.service.AuthService;
import com.scholar.service.CollaborationService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

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

    public void init(VBox channelList, VBox teamList, VBox roomContainer, VBox channelInfoBox, Label channelTitleLabel, Label channelDescLabel, Button createChannelBtn) {
        this.channelList       = channelList;
        this.teamList          = teamList;
        this.roomContainer     = roomContainer;
        this.channelInfoBox    = channelInfoBox;
        this.channelTitleLabel = channelTitleLabel;
        this.channelDescLabel  = channelDescLabel;
        this.createChannelBtn  = createChannelBtn;

        if (createChannelBtn != null) {
            createChannelBtn.setVisible("admin".equals(AuthService.CURRENT_USER_ROLE));
            createChannelBtn.setManaged("admin".equals(AuthService.CURRENT_USER_ROLE));
        }

        loadChannels();
    }

    public void loadChannels() {
        if (channelList == null) return;
        channelList.getChildren().clear();
        List<CollaborationService.Channel> channels = collaborationService.getAllChannels();
        
        for (var c : channels) {
            Button btn = new Button("# " + c.title());
            btn.setMaxWidth(Double.MAX_VALUE); 
            btn.setAlignment(Pos.CENTER_LEFT);
            btn.setStyle("-fx-background-color: transparent; -fx-text-fill: #94a3b8; -fx-font-size: 14px; -fx-font-weight: bold; -fx-cursor: hand; -fx-padding: 10 15; -fx-background-radius: 8;");
            
            btn.setOnMouseEntered(e -> {
                if (currentChannelId != c.id()) {
                    btn.setStyle("-fx-background-color: rgba(124,131,253,0.1); -fx-text-fill: #c7d2fe; -fx-font-size: 14px; -fx-font-weight: bold; -fx-cursor: hand; -fx-padding: 10 15; -fx-background-radius: 8;");
                }
            });
            btn.setOnMouseExited(e -> {
                if (currentChannelId != c.id()) {
                    btn.setStyle("-fx-background-color: transparent; -fx-text-fill: #94a3b8; -fx-font-size: 14px; -fx-font-weight: bold; -fx-cursor: hand; -fx-padding: 10 15; -fx-background-radius: 8;");
                }
            });

            btn.setOnAction(e -> {
                currentChannelId = c.id();
                currentChannelName = c.title();
                
                channelList.getChildren().forEach(node -> 
                    node.setStyle("-fx-background-color: transparent; -fx-text-fill: #94a3b8; -fx-font-size: 14px; -fx-font-weight: bold; -fx-cursor: hand; -fx-padding: 10 15; -fx-background-radius: 8;"));
                
                btn.setStyle("-fx-background-color: #3b82f6; -fx-text-fill: white; -fx-font-size: 14px; -fx-font-weight: bold; -fx-cursor: hand; -fx-padding: 10 15; -fx-background-radius: 8;");
                
                showChannelInfo(c);
                loadTeamsForChannel(c.id(), c.title());
            });
            channelList.getChildren().add(btn);
        }
    }

    private void showChannelInfo(CollaborationService.Channel channel) {
        if (channelInfoBox != null && channelTitleLabel != null && channelDescLabel != null) {
            channelTitleLabel.setText("📌 " + channel.title());
            channelDescLabel.setText(channel.desc() != null && !channel.desc().isEmpty() ? channel.desc() : "No information provided for this channel.");
            channelInfoBox.setVisible(true);
            channelInfoBox.setManaged(true);
        }
    }

    public void loadTeamsForChannel(int channelId, String channelName) {
        if (teamList == null) return;
        teamList.getChildren().clear();
        List<CollaborationService.Post> posts = collaborationService.getPostsForChannel(channelId);

        if (posts.isEmpty()) {
            Label noTeamLabel = new Label("No teams yet in #" + channelName + ". Be the first to create one!");
            noTeamLabel.setStyle("-fx-text-fill: #94a3b8; -fx-padding: 20; -fx-font-size: 14px; -fx-font-style: italic;");
            teamList.getChildren().add(noTeamLabel);
        }
        
        for (CollaborationService.Post post : posts) {
            VBox card = new VBox(10);
            card.setStyle("-fx-background-color: white; -fx-padding: 18; -fx-background-radius: 12; "
                + "-fx-border-color: #e2e8f0; -fx-border-radius: 12; "
                + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.04), 8, 0, 0, 3);");
            
            card.setOnMouseEntered(e -> card.setStyle(card.getStyle().replace("-fx-background-color: white", "-fx-background-color: #f8fafc").replace("rgba(0,0,0,0.04)", "rgba(59,130,246,0.15)")));
            card.setOnMouseExited(e -> card.setStyle(card.getStyle().replace("-fx-background-color: #f8fafc", "-fx-background-color: white").replace("rgba(59,130,246,0.15)", "rgba(0,0,0,0.04)")));

            Label title = new Label("🚀 " + post.title());
            title.setStyle("-fx-font-weight: bold; -fx-font-size: 16px; -fx-text-fill: #1e293b;");
            
            Label desc = new Label(post.desc() == null || post.desc().isEmpty() ? "No description provided." : post.desc());
            desc.setWrapText(true); 
            desc.setStyle("-fx-text-fill: #64748b; -fx-font-size: 13px;");
            
            HBox metaRow = new HBox(15);
            metaRow.setAlignment(Pos.CENTER_LEFT);
            Label members = new Label("👥 Max Members: " + post.maxMembers());
            members.setStyle("-fx-font-size: 12px; -fx-text-fill: #059669; -fx-font-weight: bold; -fx-background-color: #d1fae5; -fx-padding: 4 8; -fx-background-radius: 6;");
            
            Label statusBadge = new Label(post.status());
            statusBadge.setStyle("-fx-font-size: 12px; -fx-text-fill: #d97706; -fx-font-weight: bold; -fx-background-color: #fef3c7; -fx-padding: 4 8; -fx-background-radius: 6;");
            
            metaRow.getChildren().addAll(members, statusBadge);

            Button viewBtn = new Button("View Workspace →");
            viewBtn.setMaxWidth(Double.MAX_VALUE);
            viewBtn.setStyle("-fx-background-color: linear-gradient(to right, #6366f1, #8b5cf6); -fx-text-fill: white; "
                + "-fx-cursor: hand; -fx-font-weight: bold; -fx-background-radius: 8; -fx-padding: 8;");
            viewBtn.setOnAction(e -> teamWorkspaceController.loadRoomView(post, roomContainer));
            
            card.getChildren().addAll(title, desc, metaRow, viewBtn);
            teamList.getChildren().add(card);
        }
    }

    @FXML
    public void onCreatePost() {
        if (currentChannelId == -1) {
            showError("Please select a channel from the left first!");
            return;
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("🚀 Create Team");
        dialog.setHeaderText("Create a new team in #" + currentChannelName);
        
        VBox content = new VBox(12);
        content.setPadding(new Insets(10));
        
        TextField titleField = new TextField(); titleField.setPromptText("E.g. Java Hackathon Team");
        titleField.setStyle("-fx-padding: 8; -fx-background-radius: 6; -fx-border-color: #cbd5e1; -fx-border-radius: 6;");
        
        TextArea descField = new TextArea(); descField.setPromptText("Describe your team's goal..."); 
        descField.setPrefRowCount(3); descField.setWrapText(true);
        descField.setStyle("-fx-padding: 5; -fx-background-radius: 6; -fx-border-color: #cbd5e1; -fx-border-radius: 6;");
        
        Spinner<Integer> spinner = new Spinner<>(2, 20, 4);
        
        VBox qBox = new VBox(8); 
        List<TextField> qFields = new ArrayList<>();
        Button addQ = new Button("+ Add Join Question");
        addQ.setStyle("-fx-background-color: #f1f5f9; -fx-text-fill: #475569; -fx-cursor: hand; -fx-font-weight: bold; -fx-border-color: #cbd5e1; -fx-border-radius: 6; -fx-background-radius: 6;");
        addQ.setOnAction(e -> {
            TextField tf = new TextField(); tf.setPromptText("E.g. What is your tech stack?");
            tf.setStyle("-fx-padding: 8; -fx-background-radius: 6; -fx-border-color: #cbd5e1; -fx-border-radius: 6;");
            qFields.add(tf); qBox.getChildren().add(tf);
        });
        
        content.getChildren().addAll(
            new Label("Team Name:"), titleField,
            new Label("Description:"), descField,
            new Label("Max Members:"), spinner,
            new Label("Questions for Applicants (Optional):"), qBox, addQ);
            
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.showAndWait().ifPresent(type -> {
            if (type == ButtonType.OK) {
                String title = titleField.getText().trim();
                String desc = descField.getText().trim();

                if (title.isEmpty() || desc.isEmpty()) {
                    showError("Team Name and Description cannot be empty!");
                    return;
                }

                List<String> qs = qFields.stream().map(TextField::getText).filter(s -> !s.trim().isEmpty()).toList();
                int maxMem = spinner.getValue();
                
                new Thread(() -> {
                    boolean success = collaborationService.createPostWithRequirements(
                        currentChannelId, title, desc, maxMem, qs);
                    
                    Platform.runLater(() -> {
                        if (success) {
                            showSuccess("Team Created Successfully! 🚀");
                            loadTeamsForChannel(currentChannelId, currentChannelName);
                        } else {
                            showError("Failed to create team. Database error occurred.");
                        }
                    });
                }).start();
            }
        });
    }

    @FXML
    public void onCreateChannel() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("📁 Create New Channel");
        dialog.setHeaderText("Create a global event or topic channel");

        VBox content = new VBox(12);
        content.setPadding(new Insets(10));
        
        TextField nameField = new TextField(); 
        nameField.setPromptText("E.g. Codeforces Grind");
        nameField.setStyle("-fx-padding: 8; -fx-background-radius: 6; -fx-border-color: #cbd5e1; -fx-border-radius: 6;");
        
        TextArea descField = new TextArea(); 
        descField.setPromptText("Provide information about this channel. Users will see this when they click it.");
        descField.setPrefRowCount(4); 
        descField.setWrapText(true);
        descField.setStyle("-fx-padding: 5; -fx-background-radius: 6; -fx-border-color: #cbd5e1; -fx-border-radius: 6;");

        content.getChildren().addAll(new Label("Channel Name:"), nameField, new Label("Channel Information / Message:"), descField);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.showAndWait().ifPresent(type -> {
            if (type == ButtonType.OK && !nameField.getText().trim().isEmpty()) {
                
                // 🌟 FIX: Calls Service, NO RAW SQL here!
                boolean success = collaborationService.createChannel(
                    nameField.getText().trim(), 
                    descField.getText().trim()
                );
                
                if (success) {
                    showSuccess("Channel Created Successfully! 🎉");
                    loadChannels(); 
                } else {
                    showError("Failed to create channel.");
                }
            }
        });
    }

    private void showSuccess(String msg) {
        Platform.runLater(() -> { Alert a = new Alert(Alert.AlertType.INFORMATION); a.setContentText(msg); a.show(); });
    }
    private void showError(String msg) {
        Platform.runLater(() -> { Alert a = new Alert(Alert.AlertType.ERROR); a.setContentText(msg); a.showAndWait(); });
    }
}