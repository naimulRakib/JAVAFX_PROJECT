package com.scholar.controller;

import com.scholar.service.AuthService;
import com.scholar.service.ChannelService;
import com.scholar.util.PopupHelper;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import javafx.stage.Window;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Controller;

import java.io.IOException;

@Controller
public class LobbyController {

    @FXML private TextField joinCodeField;
    @FXML private TextField createNameField;
    @FXML private TextField createCodeField;
    @FXML private Label     statusLabel;

    @Autowired
    private ApplicationContext springContext;

    @Autowired
    private ChannelService channelService;

    @Autowired
    private AuthService authService;

    @FXML
    public void initialize() {
        // Start with no status message shown
        hideStatus();
    }

    // ==========================================
    // OPTION A: JOIN A CHANNEL (Student)
    // ==========================================
    @FXML
    public void onJoinClick() {
        String code = joinCodeField.getText().trim();
        if (code.isEmpty()) {
            setStatus("⚠️  Enter a Channel Code!", StatusType.WARN);
            return;
        }

        new Thread(() -> {
            String result = channelService.joinChannel(code);
            Platform.runLater(() -> {
                if ("SUCCESS".equals(result)) {
                    setStatus("✅  Request Sent! Wait for Admin approval.", StatusType.SUCCESS);
                } else if ("INVALID_CODE".equals(result)) {
                    setStatus("❌  Channel not found.", StatusType.ERROR);
                } else if ("ALREADY_JOINED".equals(result)) {
                    setStatus("⚠️  You already sent a request.", StatusType.WARN);
                }
            });
        }).start();
    }

    // ==========================================
    // OPTION B: CREATE A CHANNEL (Admin/CR)
    // ==========================================
    @FXML
    public void onCreateClick() {
        String name = createNameField.getText().trim();
        String code = createCodeField.getText().trim();

        if (name.isEmpty() || code.isEmpty()) {
            setStatus("⚠️  Name and Code required!", StatusType.WARN);
            return;
        }

        new Thread(() -> {
            boolean success = channelService.createChannel(name, code);
            Platform.runLater(() -> {
                if (success) {
                    setStatus("✅  Channel Created! Entering Dashboard…", StatusType.SUCCESS);
                    authService.refreshSession();
                    openDashboard();
                } else {
                    setStatus("❌  Code already taken or DB Error.", StatusType.ERROR);
                }
            });
        }).start();
    }

    // ==========================================
    // NAVIGATION
    // ==========================================
    private void openDashboard() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/scholar/view/dashboard.fxml"));
            // 🌟 Controller factory MUST be set before load()
            loader.setControllerFactory(springContext::getBean);
            Parent root = loader.load();
            Stage stage = (Stage) statusLabel.getScene().getWindow();
            stage.setScene(new Scene(root, 1200, 800));
            stage.setTitle("Scholar Grid - Dashboard");
            stage.centerOnScreen();
        } catch (IOException e) {
            e.printStackTrace();
            PopupHelper.showError(resolveOwner(), "UI Load Error",
                    "Failed to load dashboard: " + e.getMessage());
        }
    }

    // ==========================================================
    // STATUS HELPERS
    // ==========================================================

    /** Status types map to CSS modifier classes in auth-dark.css */
    private enum StatusType { ERROR, SUCCESS, WARN, INFO }

    /**
     * Sets the statusLabel text and applies the correct CSS class pair
     * so auth-dark.css drives all colour / background / border styling.
     */
    private void setStatus(String message, StatusType type) {
        if (statusLabel == null) return;
        statusLabel.setText(message);
        String modifier = switch (type) {
            case ERROR   -> "auth-status--error";
            case SUCCESS -> "auth-status--success";
            case WARN    -> "auth-status--warn";
            case INFO    -> "auth-status--info";
        };
        statusLabel.getStyleClass().setAll("auth-status", modifier);
    }

    /** Clears the status label so it takes up no visible space. */
    private void hideStatus() {
        if (statusLabel == null) return;
        statusLabel.setText("");
        statusLabel.getStyleClass().setAll("auth-status", "auth-status--hidden");
    }

    /** Resolves the owner Window for PopupHelper hard-error dialogs. */
    private Window resolveOwner() {
        if (statusLabel != null && statusLabel.getScene() != null)
            return statusLabel.getScene().getWindow();
        if (joinCodeField != null && joinCodeField.getScene() != null)
            return joinCodeField.getScene().getWindow();
        return null;
    }
}