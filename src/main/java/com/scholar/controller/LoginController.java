package com.scholar.controller;

import com.scholar.repository.UserRepository;
import com.scholar.service.AuthService;
import com.scholar.service.ChannelService;
import com.scholar.util.PopupHelper;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Controller;

@Controller
public class LoginController {

    // 🌟 Spring Boot-এর কনটেক্সট (পরবর্তী পেজ লোড করার জন্য দরকার)
    @Autowired
    private ApplicationContext springContext;

    @Autowired
    private UserRepository userRepository;

    // 🟢 Service Injection (Spring এখন এগুলোকে হ্যান্ডেল করবে)
    @Autowired
    private AuthService authService;

    @Autowired
    private ChannelService channelService;

    // --- Login Fields ---
    @FXML private TextField     emailField;
    @FXML private PasswordField passwordField;

    // --- Sign Up Fields (Common) ---
    @FXML private TextField     regNameField;
    @FXML private TextField     regEmailField;
    @FXML private PasswordField regPassField;

    // --- Sign Up Fields (Student Specific) ---
    @FXML private TextField channelUuidField; // Classroom UUID
    @FXML private VBox      studentFields;    // Container for student-only UI

    // --- Sign Up Fields (Admin Specific) ---
    @FXML private TextField newChannelNameField; // "BUET CSE-24"
    @FXML private TextField newChannelCodeField; // "CSE24"
    @FXML private VBox      adminFields;         // Container for admin-only UI

    // --- Controls ---
    @FXML private Label        statusLabel;
    @FXML private Button       loginBtn;
    @FXML private ToggleButton studentToggle;
    @FXML private ToggleButton adminToggle;

    @FXML
    public void initialize() {
        // Clear-cut UI logic: Toggle which fields are visible during Sign Up
        if (studentToggle != null && adminToggle != null) {
            studentToggle.setOnAction(e -> {
                studentFields.setVisible(true);
                adminFields.setVisible(false);
            });
            adminToggle.setOnAction(e -> {
                studentFields.setVisible(false);
                adminFields.setVisible(true);
            });
        }
        // Start with no status message shown
        hideStatus();
    }

    // ==========================================================
    // 1. LOGIN (Universal Entry)
    // ==========================================================
    @FXML
    public void onLoginClick() {
        String email = emailField.getText().trim();
        String pass  = passwordField.getText();

        if (email.isEmpty() || pass.isEmpty()) {
            setStatus("⚠️  Email and Password required", StatusType.WARN);
            return;
        }

        setStatus("⏳  Authenticating…", StatusType.INFO);
        loginBtn.setDisable(true);

        new Thread(() -> {
            boolean success = authService.login(email, pass);
            Platform.runLater(() -> {
                loginBtn.setDisable(false);
                if (success) {
                    setStatus("✅  Welcome back!", StatusType.SUCCESS);
                    handlePostLoginRouting(); // Automatic routing based on role/channel
                } else {
                    setStatus("❌  Invalid credentials", StatusType.ERROR);
                }
            });
        }).start();
    }

    // ==========================================================
    // 2. ADMIN SIGNUP (Create Admin + Create Channel)
    // ==========================================================
    @FXML
    public void onAdminSignupClick() {
        String name  = regNameField.getText().trim();
        String email = regEmailField.getText().trim();
        String pass  = regPassField.getText();
        String cName = newChannelNameField.getText().trim();
        String cCode = newChannelCodeField.getText().trim();

        if (name.isEmpty() || email.isEmpty() || pass.isEmpty()
                || cName.isEmpty() || cCode.isEmpty()) {
            setStatus("⚠️  All Admin fields are required", StatusType.WARN);
            return;
        }

        new Thread(() -> {
            String result = channelService.registerAsAdmin(name, email, pass, cName, cCode);
            Platform.runLater(() -> {
                if ("SUCCESS".equals(result)) {
                    setStatus("✅  Channel Created! Log in to enter.", StatusType.SUCCESS);
                } else {
                    setStatus("❌  Error: " + result, StatusType.ERROR);
                }
            });
        }).start();
    }

    // ==========================================================
    // 3. STUDENT SIGNUP (Create User + Join via UUID)
    // ==========================================================
    @FXML
    public void onStudentSignupClick() {
        String name  = regNameField.getText().trim();
        String email = regEmailField.getText().trim();
        String pass  = regPassField.getText();
        String uuid  = channelUuidField.getText().trim();

        if (name.isEmpty() || email.isEmpty() || pass.isEmpty() || uuid.isEmpty()) {
            setStatus("⚠️  All Student fields & UUID required", StatusType.WARN);
            return;
        }

        new Thread(() -> {
            String result = channelService.registerAsStudent(name, email, pass, uuid);
            Platform.runLater(() -> {
                if ("SUCCESS".equals(result)) {
                    setStatus("✅  Registered! Wait for CR approval.", StatusType.SUCCESS);
                } else if ("INVALID_CODE".equals(result)) {
                    setStatus("❌  Classroom UUID not found.", StatusType.ERROR);
                } else {
                    setStatus("❌  Error: " + result, StatusType.ERROR);
                }
            });
        }).start();
    }

    // ==========================================================
    // 🚦 ROUTER: Decides Dashboard vs Lobby
    // ==========================================================
    private void handlePostLoginRouting() {
        if (AuthService.CURRENT_CHANNEL_ID != -1) {
            if ("student".equals(AuthService.CURRENT_USER_ROLE)
                    && "pending".equals(AuthService.CURRENT_USER_STATUS)) {
                setStatus("⚠️  Account is Pending CR Approval", StatusType.WARN);
            } else {
                loadScene("/com/scholar/view/dashboard.fxml", "Scholar Grid - Dashboard");
            }
        } else {
            loadScene("/com/scholar/view/lobby.fxml", "Scholar Grid - Lobby");
        }
    }

    private void loadScene(String fxmlPath, String title) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            // 🌟 Controller factory must be set BEFORE load()
            loader.setControllerFactory(springContext::getBean);
            Parent root = loader.load();
            Stage stage = (Stage) loginBtn.getScene().getWindow();
            stage.setScene(new Scene(root, 1200, 800));
            stage.setTitle(title);
            stage.centerOnScreen();
        } catch (IOException e) {
            e.printStackTrace();
            PopupHelper.showError(resolveOwner(), "UI Load Error",
                    "Failed to load screen: " + e.getMessage());
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
        if (loginBtn != null && loginBtn.getScene() != null)
            return loginBtn.getScene().getWindow();
        if (emailField != null && emailField.getScene() != null)
            return emailField.getScene().getWindow();
        return null;
    }
}