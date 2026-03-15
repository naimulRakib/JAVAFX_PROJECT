package com.scholar.controller;

import com.scholar.service.AuthService;
import com.scholar.service.ChannelMergeService;
import com.scholar.util.PopupHelper;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * AdminMergeController — Full Channel Merge Management Panel
 *
 * Features:
 *  - Enable/disable merge availability for my channel
 *  - Set user privacy mode (PUBLIC / ANONYMOUS) in merged hub
 *  - Set merge type: PERMANENT or TEMPORARY (with custom day count)
 *  - Discover other channels that are open for merging
 *  - Send merge requests to other channels
 *  - View & accept incoming merge requests (sets hub name)
 *  - Multi-merge support: one hub can hold many channels
 *  - View all active hubs this channel is part of
 *  - Instant unmerge from any hub at any time
 *  - If hub drops to ≤1 channel after unmerge → hub auto-destroyed
 */
@Component
public class AdminMergeController {

    private static final String MERGE_TYPE_TEMPORARY = "TEMPORARY";
    private static final String MERGE_TYPE_PERMANENT = "PERMANENT";
    private static final String PRIVACY_PUBLIC = "PUBLIC";
    private static final String PRIVACY_ANONYMOUS = "ANONYMOUS";

    @Autowired
    private ChannelMergeService mergeService;

    // ── Settings Panel ────────────────────────────────────────────────
    @FXML CheckBox allowMergeCheck;
    @FXML ComboBox<String> privacyCombo;   // PUBLIC | ANONYMOUS
    @FXML ComboBox<String> mergeTypeCombo; // TEMPORARY | PERMANENT
    @FXML TextField durationField;         // days (only when TEMPORARY)
    @FXML Label settingsStatusLabel;

    // ── Discover & Request Panel ───────────────────────────────────────
    @FXML ListView<String> availableChannelsList;
    @FXML Label discoverStatusLabel;

    // ── Incoming Requests Panel ────────────────────────────────────────
    @FXML ListView<String> pendingRequestsList;
    @FXML Label requestStatusLabel;

    // ── Active Hubs Panel ─────────────────────────────────────────────
    @FXML ListView<String> activeHubsList;
    @FXML Label hubStatusLabel;

    // The channel this admin manages — set during scene load from session
    int myChannelId = -1;

    // ─────────────────────────────────────────────────────────────────
    // INIT
    // ─────────────────────────────────────────────────────────────────
    @FXML
    public void initialize() {
        // Resolve current channel id from auth session
        myChannelId = AuthService.CURRENT_CHANNEL_ID; // ensure this field exists in AuthService

        // Privacy combo
        if (privacyCombo != null) {
            privacyCombo.getItems().addAll(PRIVACY_PUBLIC, PRIVACY_ANONYMOUS);
            privacyCombo.setValue(PRIVACY_PUBLIC);
        }

        // Merge type combo — disable duration field when PERMANENT
        if (mergeTypeCombo != null) {
            mergeTypeCombo.getItems().addAll(MERGE_TYPE_TEMPORARY, MERGE_TYPE_PERMANENT);
            mergeTypeCombo.setValue(MERGE_TYPE_TEMPORARY);
            mergeTypeCombo.setOnAction(e -> {
                boolean isPermanent = MERGE_TYPE_PERMANENT.equals(mergeTypeCombo.getValue());
                if (durationField != null) {
                    durationField.setDisable(isPermanent);
                    durationField.setPromptText(isPermanent ? "N/A — Permanent" : "Days (e.g. 7)");
                    if (isPermanent) durationField.clear();
                }
            });
        }

        // NOTE: Do NOT auto-load lists here.
        // This is a Spring @Component — FXML fields are null until
        // DashboardController manually wires them via field assignment.
        // Lists are loaded by DashboardController after wiring.
    }

    /** Load the saved settings for this channel and populate UI */
    private void loadCurrentSettings() {
        if (myChannelId < 0) return;
        runAsync(() -> {
            String[] settings = mergeService.getSettings(myChannelId); // returns [allow_merge, privacy_mode]
            Platform.runLater(() -> {
                if (settings != null) {
                    if (allowMergeCheck != null) allowMergeCheck.setSelected("true".equals(settings[0]));
                    if (privacyCombo   != null) privacyCombo.setValue(settings[1]);
                }
            });
        });
    }

    // ─────────────────────────────────────────────────────────────────
    // SECTION 1 — MERGE SETTINGS
    // ─────────────────────────────────────────────────────────────────

    /**
     * Save merge availability + privacy mode for this channel.
     * Called by "Save Settings" button in admin dashboard.
     */
    @FXML
    public void onSaveSettings() {
        if (myChannelId < 0) { showSettingsStatus("❌ Channel not loaded.", true); return; }

        boolean allow  = allowMergeCheck != null && allowMergeCheck.isSelected();
        String privacy = privacyCombo != null ? privacyCombo.getValue() : PRIVACY_PUBLIC;

        setSettingsUILocked(true);
        runAsync(() -> {
            boolean ok = mergeService.updateSettings(myChannelId, allow, privacy);
            Platform.runLater(() -> {
                setSettingsUILocked(false);
                if (ok) {
                    showSettingsStatus("✅ Settings saved! Merge: " + (allow ? "ON" : "OFF")
                            + " | Privacy: " + privacy, false);
                } else {
                    PopupHelper.showError(getWin(), "Failed to save settings. Please try again.");
                }
            });
        });
    }

    // ─────────────────────────────────────────────────────────────────
    // SECTION 2 — DISCOVER CHANNELS & SEND REQUEST
    // ─────────────────────────────────────────────────────────────────

    /**
     * Load channels that have allow_merge = true (excluding mine).
     */
    @FXML
    public void onLoadChannels() {
        if (myChannelId < 0 || availableChannelsList == null) return;
        availableChannelsList.getItems().clear();
        showDiscoverStatus("🔄 Loading available channels...", false);

        runAsync(() -> {
            List<String[]> list = mergeService.getAvailableChannels(myChannelId);
            Platform.runLater(() -> {
                if (availableChannelsList == null) return;
                availableChannelsList.getItems().clear();
                if (list.isEmpty()) {
                    showDiscoverStatus("No channels are currently open for merging.", false);
                } else {
                    for (String[] ch : list) {
                        availableChannelsList.getItems().add(ch[0] + " | " + ch[1]);
                    }
                    showDiscoverStatus("✅ " + list.size() + " channel(s) found.", false);
                }
            });
        });
    }

    /**
     * Send a merge request to the selected channel.
     * Merge type and duration come from the UI controls.
     */
    @FXML
    public void onSendRequest() {
        if (availableChannelsList == null) return;
        String sel = availableChannelsList.getSelectionModel().getSelectedItem();
        if (sel == null) {
            showDiscoverStatus("⚠️ Please select a channel first.", true);
            return;
        }

        int receiverId;
        try {
            receiverId = Integer.parseInt(sel.split(" \\| ")[0].trim());
        } catch (NumberFormatException ex) {
            showDiscoverStatus("⚠️ Could not parse channel ID.", true);
            return;
        }

        String type = mergeTypeCombo != null ? mergeTypeCombo.getValue() : MERGE_TYPE_TEMPORARY;
        int days = 0;

        if (MERGE_TYPE_TEMPORARY.equals(type)) {
            if (durationField == null || durationField.getText().isBlank()) {
                showDiscoverStatus("⚠️ Enter number of days for temporary merge.", true);
                return;
            }
            try {
                days = Integer.parseInt(durationField.getText().trim());
                if (days <= 0) throw new NumberFormatException();
            } catch (NumberFormatException ex) {
                showDiscoverStatus("⚠️ Duration must be a positive number.", true);
                return;
            }
        }

        final int finalDays     = days;
        final int finalReceiver = receiverId;
        final String finalType  = type;

        PopupHelper.showConfirm(getWin(),
                "Send Merge Request",
                "Send a " + type + (MERGE_TYPE_TEMPORARY.equals(type) ? " (" + days + " days)" : "") +
                " merge request to channel ID " + receiverId + "?",
                () -> {
                    runAsync(() -> {
                        boolean ok = mergeService.sendMergeRequest(myChannelId, finalReceiver, finalType, finalDays);
                        Platform.runLater(() -> {
                            if (ok) {
                                showDiscoverStatus("✅ Merge request sent!", false);
                                PopupHelper.showInfo(getWin(), "Request Sent",
                                        "Your merge request has been sent. Wait for the other admin to accept.");
                            } else {
                                showDiscoverStatus("❌ Failed to send request.", true);
                                PopupHelper.showError(getWin(), "Request Failed. You may have already sent one to this channel.");
                            }
                        });
                    });
                });
    }

    // ─────────────────────────────────────────────────────────────────
    // SECTION 3 — INCOMING REQUESTS (ACCEPT / REJECT)
    // ─────────────────────────────────────────────────────────────────

    /** Refresh the list of pending incoming merge requests. */
    @FXML
    public void onLoadRequests() {
        if (myChannelId < 0 || pendingRequestsList == null) return;
        pendingRequestsList.getItems().clear();
        showRequestStatus("🔄 Loading requests...", false);

        runAsync(() -> {
            List<String[]> list = mergeService.getPendingRequests(myChannelId);
            Platform.runLater(() -> {
                if (pendingRequestsList == null) return;
                pendingRequestsList.getItems().clear();
                if (list.isEmpty()) {
                    showRequestStatus("No pending merge requests.", false);
                } else {
                    for (String[] r : list) {
                        String days  = r[3] != null && !"0".equals(r[3]) ? " (" + r[3] + " days)" : "";
                        String label = "REQ:" + r[0] + " | From: " + r[1] + " | " + r[2] + days;
                        pendingRequestsList.getItems().add(label);
                    }
                    showRequestStatus("📬 " + list.size() + " pending request(s).", false);
                }
            });
        });
    }

    /**
     * Accept selected merge request.
     * Opens a popup to name the new merged hub.
     * Both channel admins become admins of the merged hub.
     */
    @FXML
    public void onAcceptRequest() {
        if (pendingRequestsList == null) return;
        String sel = pendingRequestsList.getSelectionModel().getSelectedItem();
        if (sel == null) {
            showRequestStatus("⚠️ Select a request first.", true);
            return;
        }

        int reqId;
        try {
            reqId = Integer.parseInt(sel.split("REQ:")[1].split(" \\|")[0].trim());
        } catch (Exception ex) {
            showRequestStatus("⚠️ Could not parse request ID.", true);
            return;
        }

        // --- Popup to enter merged hub name ---
        VBox content = new VBox(12);
        content.setPadding(new Insets(20));
        content.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label("Name the Merged Hub");
        title.setFont(Font.font("System", FontWeight.BOLD, 14));

        Label info = new Label("Both channel admins will become admins of this hub.\nUsers will be added based on each channel's privacy setting.");
        info.setWrapText(true);
        info.setStyle("-fx-text-fill: #555;");

        TextField nameField = new TextField();
        nameField.setPromptText("e.g. BUET-BRAC CSE Merge Hub");
        nameField.setPrefWidth(300);

        HBox btnRow = new HBox(10);
        btnRow.setAlignment(Pos.CENTER_RIGHT);
        Button cancelBtn = new Button("Cancel");
        Button acceptBtn = new Button("✅ Accept & Merge");
        acceptBtn.setStyle("-fx-background-color: #2e7d32; -fx-text-fill: white; -fx-font-weight: bold;");
        btnRow.getChildren().addAll(cancelBtn, acceptBtn);

        content.getChildren().addAll(title, info, new Label("Hub Name:"), nameField, btnRow);

        Stage popup = PopupHelper.create(getWin(), "Accept Merge Request", content, 400, 220, 400, 220);

        cancelBtn.setOnAction(e -> popup.close());

        final int finalReqId = reqId;
        acceptBtn.setOnAction(e -> {
            String hubName = nameField.getText().trim();
            if (hubName.isEmpty()) {
                PopupHelper.showError(popup, "Hub name cannot be empty.");
                return;
            }
            popup.close();

            runAsync(() -> {
                String result = mergeService.acceptMergeRequest(finalReqId, hubName,
                        (UUID) AuthService.CURRENT_USER_ID);
                Platform.runLater(() -> {
                    if ("SUCCESS".equals(result)) {
                        showRequestStatus("✅ Channels merged into \"" + hubName + "\"!", false);
                        PopupHelper.showInfo(getWin(), "Merge Successful",
                                "Both channels are now merged into hub: " + hubName +
                                "\nAll users have been added based on privacy settings.");
                        onLoadRequests();
                        onLoadActiveMerges();
                    } else {
                        showRequestStatus("❌ Merge failed: " + result, true);
                        PopupHelper.showError(getWin(), "Merge Failed: " + result);
                    }
                });
            });
        });

        popup.show();
        nameField.requestFocus();
    }

    /**
     * Reject a pending merge request (deletes it from DB).
     */
    @FXML
    public void onRejectRequest() {
        String sel = pendingRequestsList.getSelectionModel().getSelectedItem();
        if (sel == null) {
            showRequestStatus("⚠️ Select a request first.", true);
            return;
        }

        int reqId;
        try {
            reqId = Integer.parseInt(sel.split("REQ:")[1].split(" \\|")[0].trim());
        } catch (Exception ex) {
            showRequestStatus("⚠️ Could not parse request ID.", true);
            return;
        }

        final int finalReqId = reqId;
        PopupHelper.showConfirm(getWin(), "Reject Request",
                "Reject this merge request? The sender channel will be notified.",
                () -> runAsync(() -> {
                    boolean ok = mergeService.rejectMergeRequest(finalReqId);
                    Platform.runLater(() -> {
                        if (ok) {
                            showRequestStatus("Request rejected.", false);
                            onLoadRequests();
                        } else {
                            showRequestStatus("❌ Failed to reject request.", true);
                        }
                    });
                }));
    }

    // ─────────────────────────────────────────────────────────────────
    // SECTION 4 — ACTIVE MERGED HUBS & INSTANT UNMERGE
    // ─────────────────────────────────────────────────────────────────

    /**
     * Load all active hubs that my channel is currently part of.
     * A channel can be part of multiple hubs simultaneously.
     */
    @FXML
    public void onLoadActiveMerges() {
        if (myChannelId < 0 || activeHubsList == null) return;
        activeHubsList.getItems().clear();
        showHubStatus("🔄 Loading active merges...", false);

        runAsync(() -> {
            List<String[]> list = mergeService.getMyActiveHubs(myChannelId);
            Platform.runLater(() -> {
                if (activeHubsList == null) return;
                activeHubsList.getItems().clear();
                if (list.isEmpty()) {
                    showHubStatus("Not currently merged with any channel.", false);
                } else {
                    for (String[] h : list) {
                        String expiry = (h[2] != null && !h[2].isBlank()) ? " | Expires: " + h[2] : " | PERMANENT";
                        activeHubsList.getItems().add("HUB:" + h[0] + " | " + h[1] + expiry);
                    }
                    showHubStatus("🔗 " + list.size() + " active merge(s).", false);
                }
            });
        });
    }

    /**
     * Instantly remove my channel from the selected merged hub.
     * - All my users are removed from the hub.
     * - If hub drops to ≤1 channel → hub is auto-destroyed.
     */
    @FXML
    public void onInstantUnmerge() {
        if (activeHubsList == null) return;
        String sel = activeHubsList.getSelectionModel().getSelectedItem();
        if (sel == null) {
            showHubStatus("⚠️ Select a hub to leave.", true);
            return;
        }

        int hubId;
        try {
            hubId = Integer.parseInt(sel.split("HUB:")[1].split(" \\|")[0].trim());
        } catch (Exception ex) {
            showHubStatus("⚠️ Could not parse Hub ID.", true);
            return;
        }

        final int finalHubId = hubId;
        String hubName = sel.contains("|") ? sel.split("\\|")[1].trim() : "this hub";

        PopupHelper.showConfirm(getWin(),
                "Leave Merged Hub",
                "Are you sure you want to leave \"" + hubName + "\"?\n\n" +
                "• All your users will be instantly removed from the hub.\n" +
                "• Your channel returns to normal immediately.\n" +
                "• If no other channels remain in the hub, it will be destroyed.",
                () -> runAsync(() -> {
                    boolean ok = mergeService.instantUnmerge(finalHubId, myChannelId);
                    Platform.runLater(() -> {
                        if (ok) {
                            showHubStatus("✅ Successfully left the merged hub.", false);
                            PopupHelper.showInfo(getWin(), "Unmerged",
                                    "Your channel has been separated. All your users are back to your original channel only.");
                            onLoadActiveMerges();
                        } else {
                            showHubStatus("❌ Failed to unmerge.", true);
                            PopupHelper.showError(getWin(), "Unmerge failed. Please try again.");
                        }
                    });
                }));
    }

    // ─────────────────────────────────────────────────────────────────
    // CALLED BY DashboardController AFTER field wiring is complete
    // ─────────────────────────────────────────────────────────────────

    /**
     * Call this from DashboardController after manually assigning all
     * FXML fields (allowMergeCheck, activeHubsList, etc.).
     * This replaces the auto-load that was in initialize().
     */
    public void initAndLoad() {
        myChannelId = AuthService.CURRENT_CHANNEL_ID;
        loadCurrentSettings();
        onLoadChannels();
        onLoadRequests();
        onLoadActiveMerges();
    }

    // ─────────────────────────────────────────────────────────────────
    // HELPER — UI FEEDBACK LABELS
    // ─────────────────────────────────────────────────────────────────

    private void showSettingsStatus(String msg, boolean isError) {
        if (settingsStatusLabel != null) {
            settingsStatusLabel.setText(msg);
            settingsStatusLabel.setStyle(isError ? "-fx-text-fill: red;" : "-fx-text-fill: #2e7d32;");
        }
    }

    private void showDiscoverStatus(String msg, boolean isError) {
        if (discoverStatusLabel != null) {
            discoverStatusLabel.setText(msg);
            discoverStatusLabel.setStyle(isError ? "-fx-text-fill: red;" : "-fx-text-fill: #555;");
        }
    }

    private void showRequestStatus(String msg, boolean isError) {
        if (requestStatusLabel != null) {
            requestStatusLabel.setText(msg);
            requestStatusLabel.setStyle(isError ? "-fx-text-fill: red;" : "-fx-text-fill: #555;");
        }
    }

    private void showHubStatus(String msg, boolean isError) {
        if (hubStatusLabel != null) {
            hubStatusLabel.setText(msg);
            hubStatusLabel.setStyle(isError ? "-fx-text-fill: red;" : "-fx-text-fill: #555;");
        }
    }

    private void setSettingsUILocked(boolean locked) {
        if (allowMergeCheck != null) allowMergeCheck.setDisable(locked);
        if (privacyCombo    != null) privacyCombo.setDisable(locked);
    }

    private void runAsync(Runnable work) {
        Thread thread = new Thread(work);
        thread.setDaemon(true);
        thread.start();
    }

    private Window getWin() {
        // Safe window resolver — tries multiple FXML nodes
        if (allowMergeCheck    != null && allowMergeCheck.getScene()    != null) return allowMergeCheck.getScene().getWindow();
        if (availableChannelsList != null && availableChannelsList.getScene() != null) return availableChannelsList.getScene().getWindow();
        if (pendingRequestsList   != null && pendingRequestsList.getScene()   != null) return pendingRequestsList.getScene().getWindow();
        if (activeHubsList        != null && activeHubsList.getScene()        != null) return activeHubsList.getScene().getWindow();
        return null;
    }
}
