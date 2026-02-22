package com.scholar.controller;

import com.scholar.repository.UserRepository;
import com.scholar.service.AuthService;
import com.scholar.service.ChannelService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
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
    @FXML private TextField emailField;
    @FXML private PasswordField passwordField;

    // --- Sign Up Fields (Common) ---
    @FXML private TextField regNameField;
    @FXML private TextField regEmailField;
    @FXML private PasswordField regPassField;

    // --- Sign Up Fields (Student Specific) ---
    @FXML private TextField channelUuidField; // Classroom UUID
    @FXML private VBox studentFields;         // Container for student-only UI

    // --- Sign Up Fields (Admin Specific) ---
    @FXML private TextField newChannelNameField; // "BUET CSE-24"
    @FXML private TextField newChannelCodeField; // "CSE24"
    @FXML private VBox adminFields;           // Container for admin-only UI

    // --- Controls ---
    @FXML private Label statusLabel;
    @FXML private Button loginBtn;
    @FXML private ToggleButton studentToggle, adminToggle;

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
    }

    // ==========================================================
    // 1. LOGIN (Universal Entry)
    // ==========================================================
    @FXML
    public void onLoginClick() {
        String email = emailField.getText().trim();
        String pass = passwordField.getText();

        if (email.isEmpty() || pass.isEmpty()) {
            setMsg("❌ Email and Password required", "#e74c3c");
            return;
        }

        setMsg("⏳ Authenticating...", "#f1c40f");
        loginBtn.setDisable(true);

        new Thread(() -> {
            boolean success = authService.login(email, pass);
            Platform.runLater(() -> {
                loginBtn.setDisable(false);
                if (success) {
                    setMsg("✅ Welcome back!", "#2ecc71");
                    handlePostLoginRouting(); // Automatic routing based on role/channel
                } else {
                    setMsg("❌ Invalid credentials", "#e74c3c");
                }
            });
        }).start();
    }

    // ==========================================================
    // 2. ADMIN SIGNUP (Create Admin + Create Channel)
    // ==========================================================
    @FXML
    public void onAdminSignupClick() {
        String name = regNameField.getText().trim();
        String email = regEmailField.getText().trim();
        String pass = regPassField.getText();
        String cName = newChannelNameField.getText().trim();
        String cCode = newChannelCodeField.getText().trim();

        if (name.isEmpty() || email.isEmpty() || pass.isEmpty() || cName.isEmpty() || cCode.isEmpty()) {
            setMsg("❌ All Admin fields are required", "#e74c3c");
            return;
        }

        new Thread(() -> {
            String result = channelService.registerAsAdmin(name, email, pass, cName, cCode);
            Platform.runLater(() -> {
                if ("SUCCESS".equals(result)) {
                    setMsg("✅ Channel Created! Log in to enter.", "#2ecc71");
                } else {
                    setMsg("❌ Error: " + result, "#e74c3c");
                }
            });
        }).start();
    }

    // ==========================================================
    // 3. STUDENT SIGNUP (Create User + Join via UUID)
    // ==========================================================
    @FXML
    public void onStudentSignupClick() {
        String name = regNameField.getText().trim();
        String email = regEmailField.getText().trim();
        String pass = regPassField.getText();
        String uuid = channelUuidField.getText().trim();

        if (name.isEmpty() || email.isEmpty() || pass.isEmpty() || uuid.isEmpty()) {
            setMsg("❌ All Student fields & UUID required", "#e74c3c");
            return;
        }

        new Thread(() -> {
            String result = channelService.registerAsStudent(name, email, pass, uuid);
            Platform.runLater(() -> {
                if ("SUCCESS".equals(result)) {
                    setMsg("✅ Registered! Wait for CR approval.", "#2ecc71");
                } else if ("INVALID_CODE".equals(result)) {
                    setMsg("❌ Classroom UUID not found.", "#e74c3c");
                } else {
                    setMsg("❌ Error: " + result, "#e74c3c");
                }
            });
        }).start();
    }

    // ==========================================================
    // 🚦 ROUTER: Decides Dashboard vs Lobby
    // ==========================================================
    private void handlePostLoginRouting() {
        if (AuthService.CURRENT_CHANNEL_ID != -1) {
            if ("student".equals(AuthService.CURRENT_USER_ROLE) && 
                "pending".equals(AuthService.CURRENT_USER_STATUS)) { 
                 setMsg("⚠️ Account is Pending CR Approval", "#f39c12");
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
            
            // 🌟 অত্যন্ত সেনসিটিভ লাইন: এটি পরবর্তী পেজের কন্ট্রোলারকেও স্প্রিং-এর আন্ডারে রাখবে
            loader.setControllerFactory(springContext::getBean);

            Parent root = loader.load();
            Stage stage = (Stage) loginBtn.getScene().getWindow();
            stage.setScene(new Scene(root, 1200, 800));
            stage.setTitle(title);
            stage.centerOnScreen();
        } catch (IOException e) {
            e.printStackTrace();
            setMsg("❌ UI Load Error", "#e74c3c");
        }
    }

    private void setMsg(String m, String color) {
        statusLabel.setText(m);
        statusLabel.setStyle("-fx-text-fill: " + color + ";");
    }
}