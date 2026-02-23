package com.scholar.controller.collaboration;

import com.scholar.service.AuthService;
import com.scholar.service.CollaborationService;
import com.scholar.service.TelegramService;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.util.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * CHAT CONTROLLER — Team chat room, auto-refresh, team resource list
 * Path: src/main/java/com/scholar/controller/collaboration/ChatController.java
 */
@Component
public class ChatController {

    @Autowired private CollaborationService collaborationService;
    @Autowired private TelegramService telegramService;

    private Timeline chatTimeline;
    private List<CollaborationService.TeamResource> currentResourceData = new ArrayList<>();

    // ----------------------------------------------------------
    // SHOW CHAT ROOM (SplitPane)
    // ----------------------------------------------------------
    public void showChatRoom(CollaborationService.Post post, VBox roomContainer) {
        roomContainer.getChildren().clear();
        if (chatTimeline != null) chatTimeline.stop();

        SplitPane splitPane = new SplitPane();
        splitPane.setDividerPositions(0.7);

        // ---- Chat side ----
        VBox chatSide = new VBox(10); chatSide.setPadding(new javafx.geometry.Insets(10));
        VBox chatBox  = new VBox(10); chatBox.setPadding(new javafx.geometry.Insets(15));
        ScrollPane chatScroll = new ScrollPane(chatBox);
        chatScroll.setFitToWidth(true); VBox.setVgrow(chatScroll, Priority.ALWAYS);

        TextField msgInput = new TextField();
        msgInput.setPromptText("Type a message...");
        Button sendBtn = new Button("Send");
        sendBtn.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-cursor: hand;");
        HBox inputBox = new HBox(10, msgInput, sendBtn);
        inputBox.setAlignment(Pos.CENTER); HBox.setHgrow(msgInput, Priority.ALWAYS);
        sendBtn.setOnAction(e -> {
            if (!msgInput.getText().trim().isEmpty()) {
                collaborationService.sendMessage(post.id(), msgInput.getText());
                msgInput.clear();
                refreshChat(post.id(), chatBox, chatScroll);
            }
        });
        chatSide.getChildren().addAll(new Label("💬 " + post.title()), chatScroll, inputBox);

        // ---- Resource side ----
        VBox resSide = new VBox(10); resSide.setPadding(new javafx.geometry.Insets(10));
        ListView<String> dynamicResList = new ListView<>(); VBox.setVgrow(dynamicResList, Priority.ALWAYS);
        Button addResBtn = new Button("➕ Add Resource"); addResBtn.setMaxWidth(Double.MAX_VALUE);
        addResBtn.setStyle("-fx-background-color: #8b5cf6; -fx-text-fill: white; -fx-cursor: hand;");
        addResBtn.setOnAction(e -> onAddResourceClick(post.id(), dynamicResList));
        dynamicResList.setOnMouseClicked(e -> handleTeamResourceClick(e, dynamicResList));
        resSide.getChildren().addAll(new Label("📂 Files"), dynamicResList, addResBtn);

        splitPane.getItems().addAll(chatSide, resSide);
        roomContainer.getChildren().add(splitPane);
        refreshChat(post.id(), chatBox, chatScroll);
        loadTeamResources(post.id(), dynamicResList);
        startChatAutoRefresh(post.id(), chatBox, chatScroll, roomContainer);
    }

    // ----------------------------------------------------------
    // REFRESH CHAT MESSAGES
    // ----------------------------------------------------------
    public void refreshChat(int postId, VBox chatBox, ScrollPane scrollPane) {
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

    // ----------------------------------------------------------
    // AUTO REFRESH
    // ----------------------------------------------------------
    public void startChatAutoRefresh(int postId, VBox chatBox, ScrollPane scrollPane, VBox roomContainer) {
        if (chatTimeline != null) chatTimeline.stop();
        chatTimeline = new Timeline(new KeyFrame(Duration.seconds(3),
            e -> refreshChat(postId, chatBox, scrollPane)));
        chatTimeline.setCycleCount(Timeline.INDEFINITE);
        chatTimeline.play();
        roomContainer.sceneProperty().addListener((obs, old, nev) -> {
            if (nev == null && chatTimeline != null) chatTimeline.stop();
        });
    }

    public void stopAutoRefresh() {
        if (chatTimeline != null) chatTimeline.stop();
    }

    // ----------------------------------------------------------
    // TEAM RESOURCES
    // ----------------------------------------------------------
    public void loadTeamResources(int postId, ListView<String> listView) {
        listView.getItems().clear();
        currentResourceData = collaborationService.getTeamResources(postId);
        if (currentResourceData.isEmpty()) {
            listView.getItems().add("No shared files.");
        } else {
            for (var res : currentResourceData) {
                String icon = "FILE".equalsIgnoreCase(res.type()) ? "📄" : "🔗";
                listView.getItems().add(icon + " " + res.title() + " (" + res.addedBy() + ")");
            }
        }
    }

    private void handleTeamResourceClick(MouseEvent event, ListView<String> listView) {
        if (event.getClickCount() == 2) {
            int index = listView.getSelectionModel().getSelectedIndex();
            if (index >= 0 && index < currentResourceData.size()) {
                String url = currentResourceData.get(index).url();
                try { java.awt.Desktop.getDesktop().browse(new URI(url)); }
                catch (Exception e) { showError("Could not open link."); }
            }
        }
    }

    // ----------------------------------------------------------
    // ADD RESOURCE DIALOG
    // ----------------------------------------------------------
    public void onAddResourceClick(int postId, ListView<String> listViewToRefresh) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("📂 Add Resource"); dialog.setHeaderText("Share Link or File");

        VBox content = new VBox(10);
        TextField titleField = new TextField(); titleField.setPromptText("Title");
        TextArea descField   = new TextArea(); descField.setPromptText("Description"); descField.setPrefRowCount(2);
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
                    collaborationService.addTeamResource(postId, titleField.getText(),
                        linkField.getText(), "LINK", descField.getText(), null);
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

    // ----------------------------------------------------------
    // HELPERS
    // ----------------------------------------------------------
    private void showSuccess(String msg) {
        Platform.runLater(() -> { Alert a = new Alert(Alert.AlertType.INFORMATION); a.setContentText(msg); a.show(); });
    }
    private void showError(String msg) {
        Platform.runLater(() -> { Alert a = new Alert(Alert.AlertType.ERROR); a.setContentText(msg); a.showAndWait(); });
    }
}