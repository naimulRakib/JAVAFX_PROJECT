package com.scholar.controller.settings;

import com.scholar.service.AuthService;
import com.scholar.service.CourseService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * PROFILE CONTROLLER — Settings, profile save, logout
 * Path: src/main/java/com/scholar/controller/settings/ProfileController.java
 */
@Component
public class ProfileController {

    @Autowired private CourseService courseService;
    @Autowired private ApplicationContext springContext;

    private TextField settingsName;
    private TextField settingsUsername;
    private Label settingsEmail;

    public void init(TextField settingsName, TextField settingsUsername, Label settingsEmail) {
        this.settingsName     = settingsName;
        this.settingsUsername = settingsUsername;
        this.settingsEmail    = settingsEmail;
    }

    // ----------------------------------------------------------
    // LOAD PROFILE FROM DB
    // ----------------------------------------------------------
    @FXML
    public void loadProfileSettings() {
        if (settingsName == null) return;
        new Thread(() -> {
            var profile = courseService.getMyProfile();
            Platform.runLater(() -> {
                settingsName.setText(profile.fullName());
                settingsUsername.setText(profile.username());
                settingsEmail.setText(profile.email());
            });
        }).start();
    }

    // ----------------------------------------------------------
    // SAVE PROFILE
    // ----------------------------------------------------------
    @FXML
    public void onSaveProfile() {
        new Thread(() -> {
            if (courseService.updateProfile(settingsName.getText(), settingsUsername.getText()))
                Platform.runLater(() -> showSuccess("Profile Updated! ✅"));
        }).start();
    }

    // ----------------------------------------------------------
    // LOGOUT (Tab selection event)
    // ----------------------------------------------------------
    @FXML
    public void handleLogoutTab(javafx.event.Event event) {
        Tab tab = (Tab) event.getSource();
        if (tab.isSelected()) {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Logout?");
            if (confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
                AuthService.logout();
                try {
                    Stage stage = (Stage) tab.getTabPane().getScene().getWindow();
                    FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/scholar/view/login.fxml"));
                    loader.setControllerFactory(springContext::getBean);
                    Parent root = (Parent) loader.load();
                    stage.setScene(new Scene(root, 400, 600));
                    stage.centerOnScreen();
                } catch (IOException e) { e.printStackTrace(); }
            } else {
                tab.getTabPane().getSelectionModel().select(0);
            }
        }
    }

    // ----------------------------------------------------------
    // HELPER
    // ----------------------------------------------------------
    private void showSuccess(String msg) {
        Platform.runLater(() -> { Alert a = new Alert(Alert.AlertType.INFORMATION); a.setContentText(msg); a.show(); });
    }
}