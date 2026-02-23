package com.scholar.controller;

import com.scholar.service.AuthService;
import com.scholar.service.ChatSettingsService;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * AdminChatToggleController
 *
 * Renders the "Public Chat Control" card inside the Admin tab and
 * handles enable / disable logic. Wire this into AdminBroadcastController
 * or directly into DashboardController.
 *
 * Path: src/main/java/com/scholar/controller/dashboard/AdminChatToggleController.java
 */
@Component
public class AdminChatToggleController {

    @Autowired
    private ChatSettingsService chatSettingsService;

    // The VBox inside the Admin tab where this card will be injected
    private VBox adminTabContainer;

    // Live UI references so we can update them after toggle
    private Label  statusValueLabel;
    private Button toggleBtn;

    // ----------------------------------------------------------
    // INIT — call this from DashboardController.initialize()
    // ----------------------------------------------------------

    /**
     * @param adminTabContainer  The VBox (fx:id="adminControlsBox") inside the Admin tab.
     *                           The card is appended to whatever is already there.
     */
    public void init(VBox adminTabContainer) {
        // Only admins should ever reach this, but guard anyway
        if (!"admin".equals(AuthService.CURRENT_USER_ROLE)) return;

        this.adminTabContainer = adminTabContainer;
        Platform.runLater(this::buildChatToggleCard);
    }

    // ----------------------------------------------------------
    // BUILD CARD
    // ----------------------------------------------------------

    private void buildChatToggleCard() {
        boolean currentlyEnabled = chatSettingsService.isPublicChatEnabled();

        // ── Outer card ──────────────────────────────────────────
        VBox card = new VBox(14);
        card.setStyle(
            "-fx-background-color: white;" +
            "-fx-background-radius: 12;" +
            "-fx-border-color: #e0e0e0;" +
            "-fx-border-radius: 12;" +
            "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.08), 8, 0, 0, 2);"
        );
        card.setPadding(new Insets(20));
        card.setMaxWidth(560);

        // ── Title row ───────────────────────────────────────────
        HBox titleRow = new HBox(10);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        Label icon = new Label("💬");
        icon.setFont(Font.font(22));

        VBox titleText = new VBox(2);
        Label title = new Label("Public Group Chat");
        title.setFont(Font.font("System", FontWeight.BOLD, 15));
        title.setStyle("-fx-text-fill: #1a1a2e;");

        Label subtitle = new Label("Control whether students can send messages in the global group chat.");
        subtitle.setStyle("-fx-text-fill: #6b7280; -fx-font-size: 12px;");
        subtitle.setWrapText(true);

        titleText.getChildren().addAll(title, subtitle);
        titleRow.getChildren().addAll(icon, titleText);

        // ── Divider ─────────────────────────────────────────────
        Separator sep = new Separator();
        sep.setStyle("-fx-background-color: #f0f0f0;");

        // ── Status row ──────────────────────────────────────────
        HBox statusRow = new HBox(12);
        statusRow.setAlignment(Pos.CENTER_LEFT);

        Label statusKey = new Label("Current Status:");
        statusKey.setFont(Font.font("System", FontWeight.BOLD, 13));
        statusKey.setStyle("-fx-text-fill: #374151;");

        statusValueLabel = new Label();
        refreshStatusLabel(currentlyEnabled);

        statusRow.getChildren().addAll(statusKey, statusValueLabel);

        // ── Info box ────────────────────────────────────────────
        Label infoBox = new Label(
            "ℹ️  When disabled, students will see a notice that public chat is currently turned off " +
            "and the message input will be locked. Admin messages are not affected."
        );
        infoBox.setWrapText(true);
        infoBox.setStyle(
            "-fx-background-color: #eff6ff;" +
            "-fx-background-radius: 8;" +
            "-fx-border-color: #bfdbfe;" +
            "-fx-border-radius: 8;" +
            "-fx-padding: 10 12;" +
            "-fx-text-fill: #1d4ed8;" +
            "-fx-font-size: 12px;"
        );

        // ── Action button ────────────────────────────────────────
        toggleBtn = new Button();
        refreshToggleButton(currentlyEnabled);
        toggleBtn.setMaxWidth(Double.MAX_VALUE);
        toggleBtn.setFont(Font.font("System", FontWeight.BOLD, 13));
        toggleBtn.setCursor(javafx.scene.Cursor.HAND);
        toggleBtn.setOnAction(e -> handleToggle());

        card.getChildren().addAll(titleRow, sep, statusRow, infoBox, toggleBtn);

        // ── Insert into admin tab ────────────────────────────────
        // Add a section header above if the container is empty
        if (adminTabContainer.getChildren().isEmpty()) {
            Label sectionHeader = new Label("⚙️  Chat Controls");
            sectionHeader.setFont(Font.font("System", FontWeight.BOLD, 14));
            sectionHeader.setStyle("-fx-text-fill: #374151; -fx-padding: 0 0 6 0;");
            adminTabContainer.getChildren().add(sectionHeader);
        }
        adminTabContainer.getChildren().add(card);
    }

    // ----------------------------------------------------------
    // TOGGLE ACTION
    // ----------------------------------------------------------

    private void handleToggle() {
        toggleBtn.setDisable(true);
        toggleBtn.setText("⏳  Updating...");

        boolean currentlyEnabled = chatSettingsService.isPublicChatEnabled();
        boolean newValue = !currentlyEnabled;

        new Thread(() -> {
            boolean success = chatSettingsService.setPublicChatEnabled(newValue);
            Platform.runLater(() -> {
                if (success) {
                    refreshStatusLabel(newValue);
                    refreshToggleButton(newValue);
                    showNotification(newValue
                        ? "✅ Public chat has been ENABLED. Students can now send messages."
                        : "🔒 Public chat has been DISABLED. Students cannot send messages.");
                } else {
                    showError("❌ Failed to update chat setting. Please try again.");
                    refreshToggleButton(currentlyEnabled); // revert UI
                }
                toggleBtn.setDisable(false);
            });
        }).start();
    }

    // ----------------------------------------------------------
    // UI REFRESH HELPERS
    // ----------------------------------------------------------

    private void refreshStatusLabel(boolean enabled) {
        if (statusValueLabel == null) return;
        if (enabled) {
            statusValueLabel.setText("🟢  ENABLED  —  Students can chat publicly");
            statusValueLabel.setStyle(
                "-fx-text-fill: #15803d;" +
                "-fx-font-weight: bold;" +
                "-fx-background-color: #dcfce7;" +
                "-fx-background-radius: 20;" +
                "-fx-padding: 3 10;" +
                "-fx-font-size: 12px;"
            );
        } else {
            statusValueLabel.setText("🔴  DISABLED  —  Public chat is locked");
            statusValueLabel.setStyle(
                "-fx-text-fill: #b91c1c;" +
                "-fx-font-weight: bold;" +
                "-fx-background-color: #fee2e2;" +
                "-fx-background-radius: 20;" +
                "-fx-padding: 3 10;" +
                "-fx-font-size: 12px;"
            );
        }
    }

    private void refreshToggleButton(boolean currentlyEnabled) {
        if (toggleBtn == null) return;
        if (currentlyEnabled) {
            // Next action = DISABLE
            toggleBtn.setText("🔒  Disable Public Chat");
            toggleBtn.setStyle(
                "-fx-background-color: #dc2626;" +
                "-fx-text-fill: white;" +
                "-fx-background-radius: 8;" +
                "-fx-padding: 11 0;"
            );
        } else {
            // Next action = ENABLE
            toggleBtn.setText("🟢  Enable Public Chat");
            toggleBtn.setStyle(
                "-fx-background-color: #16a34a;" +
                "-fx-text-fill: white;" +
                "-fx-background-radius: 8;" +
                "-fx-padding: 11 0;"
            );
        }
    }

    // ----------------------------------------------------------
    // NOTIFICATION HELPERS
    // ----------------------------------------------------------

    private void showNotification(String msg) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Chat Settings Updated");
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.show();
    }

    private void showError(String msg) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Update Failed");
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.showAndWait();
    }
}