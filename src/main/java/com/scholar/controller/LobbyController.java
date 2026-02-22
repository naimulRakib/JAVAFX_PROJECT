package com.scholar.controller;

import com.scholar.service.AuthService;
import com.scholar.service.ChannelService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.springframework.beans.factory.annotation.Autowired; // 🟢 নতুন
import org.springframework.context.ApplicationContext; // 🟢 নতুন
import org.springframework.stereotype.Controller; // 🟢 নতুন

import java.io.IOException;


@Controller
public class LobbyController {

    @FXML private TextField joinCodeField;
    @FXML private TextField createNameField;
    @FXML private TextField createCodeField;
    @FXML private Label statusLabel;
    
  @Autowired
    private ApplicationContext springContext;

    // 🌟 ৩. 'new' কি-ওয়ার্ড সরিয়ে @Autowired করা হলো
    @Autowired
    private ChannelService channelService;

    @Autowired
    private AuthService authService;


    // ==========================================
    // OPTION A: JOIN A CHANNEL (Student)
    // ==========================================
    @FXML
    public void onJoinClick() {
        String code = joinCodeField.getText().trim();
        if (code.isEmpty()) {
            setMsg("❌ Enter a Channel Code!", "red");
            return;
        }

        new Thread(() -> {
            String result = channelService.joinChannel(code);
            Platform.runLater(() -> {
                if ("SUCCESS".equals(result)) {
                    setMsg("✅ Request Sent! Wait for Admin approval.", "green");
                } else if ("INVALID_CODE".equals(result)) {
                    setMsg("❌ Channel not found.", "red");
                } else if ("ALREADY_JOINED".equals(result)) {
                    setMsg("⚠️ You already sent a request.", "orange");
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
            setMsg("❌ Name and Code required!", "red");
            return;
        }

        new Thread(() -> {
            boolean success = channelService.createChannel(name, code);
            Platform.runLater(() -> {
                if (success) {
                    setMsg("✅ Channel Created! Entering Dashboard...", "green");
                    
                    // ✅ এখন আর লাল দাগ থাকবে না
                    authService.refreshSession(); 
                    openDashboard();
                } else {
                    setMsg("❌ Code already taken or DB Error.", "red");
                }
            });
        }).start();
    }

    // ==========================================
    // NAVIGATION
    // ==========================================
    private void openDashboard() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/scholar/view/dashboard.fxml"));
            Parent root = loader.load();
            loader.setControllerFactory(springContext::getBean);
            Stage stage = (Stage) statusLabel.getScene().getWindow();
            
            // ড্যাশবোর্ডের স্ট্যান্ডার্ড সাইজ দিন
            stage.setScene(new Scene(root, 1200, 800));
            stage.setTitle("Scholar Grid - Dashboard");
            stage.centerOnScreen();
        } catch (IOException e) { 
            e.printStackTrace();
            setMsg("❌ UI Load Error!", "red");
        }
    }

    private void setMsg(String msg, String color) {
        statusLabel.setText(msg);
        statusLabel.setStyle("-fx-text-fill: " + color + ";");
    }
}