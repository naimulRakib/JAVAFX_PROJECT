package com.scholar.controller.settings;

import com.scholar.model.Profile;
import com.scholar.service.AuthService;
import com.scholar.service.ProfileService;
import com.scholar.util.PopupHelper;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;

/**
 * ProfileController
 * Path: src/main/java/com/scholar/controller/settings/ProfileController.java
 *
 * ── Mode A: Legacy API used by DashboardController (unchanged) ──
 *   profileController.init(settingsName, settingsUsername, settingsEmail)
 *   profileController.loadProfileSettings()
 *   profileController.onSaveProfile()
 *   profileController.handleLogoutTab(event)
 *
 * ── Mode B: Rich FXML (profile_settings.fxml) ──
 *   All @FXML fields wired automatically when FXML is loaded.
 *   The legacy methods also populate the rich fields if present.
 */
@Component
public class ProfileController {

    @Autowired private ProfileService     profileService;
    @Autowired private ApplicationContext springContext;

    // ── Mode A: set by DashboardController.init() ─────────────
    private TextField legacyName;
    private TextField legacyUsername;
    private Label     legacyEmail;

    // ── Mode B: @FXML from profile_settings.fxml ──────────────
    @FXML private ImageView    avatarView;
    @FXML private Label        userNameLabel;
    @FXML private Label        completionLabel;
    @FXML private ProgressBar  completionBar;

    @FXML private TextField    tfFullName;
    @FXML private TextField    tfStudentId;
    @FXML private TextField    tfUniversity;
    @FXML private TextField    tfDepartment;
    @FXML private TextField    tfBatchSemester;
    @FXML private TextField    tfEmail;
    @FXML private TextField    tfPhone;
    @FXML private ToggleButton togglePhonePublic;

    @FXML private TextField    tfCgpa;
    @FXML private CheckBox     cbCgpaVisible;
    @FXML private TextField    tfCredits;
    @FXML private TextField    tfThesis;
    @FXML private TextField    tfInterests;
    @FXML private TextField    tfCourses;

    @FXML private TextField    tfProgLangs;
    @FXML private TextField    tfFrameworks;
    @FXML private TextField    tfSoftSkills;

    @FXML private TextField    tfGithub;
    @FXML private TextField    tfLinkedin;
    @FXML private TextField    tfPortfolio;
    @FXML private TextField    tfCodeforces;
    @FXML private TextField    tfLeetcode;

    @FXML private ToggleGroup      themeGroup;
    @FXML private ColorPicker      accentPicker;
    @FXML private ComboBox<String> cbFontSize;
    @FXML private ComboBox<String> cbLanguage;
    @FXML private ToggleButton     togglePrivacy;

    // =========================================================
    //  MODE A — LEGACY API  ← DashboardController calls these 3
    //  DO NOT RENAME OR REMOVE
    // =========================================================

    public void init(TextField settingsName, TextField settingsUsername, Label settingsEmail) {
        this.legacyName     = settingsName;
        this.legacyUsername = settingsUsername;
        this.legacyEmail    = settingsEmail;
    }

    public void loadProfileSettings() {
        new Thread(() -> {
            try {
                Profile p = profileService.getMyProfile();
                Platform.runLater(() -> {
                    // ── fill legacy dashboard fields ──────────
                    if (legacyName     != null) legacyName.setText(nvl(p.getFullName()));
                    if (legacyUsername != null) legacyUsername.setText(nvl(AuthService.CURRENT_USER_NAME));
                    if (legacyEmail    != null) legacyEmail.setText(nvl(AuthService.CURRENT_USER_EMAIL));
                    // ── also fill rich FXML if loaded ─────────
                    populateRichUI(p);
                });
            } catch (Exception e) {
                System.err.println("❌ loadProfileSettings error: " + e.getMessage());
            }
        }, "profile-load").start();
    }

    @FXML
    public void onSaveProfile() {
        new Thread(() -> {
            try {
                profileService.saveBasicInfo(
                        legacyName != null ? legacyName.getText().trim() : get(tfFullName),
                        get(tfStudentId),
                        get(tfUniversity),
                        get(tfDepartment),
                        get(tfBatchSemester),
                        legacyEmail != null ? legacyEmail.getText() : get(tfEmail),
                        get(tfPhone),
                        togglePhonePublic != null && togglePhonePublic.isSelected()
                );
                Platform.runLater(() -> PopupHelper.showInfo(resolveOwner(), "Saved", "Profile updated!"));
            } catch (Exception e) {
                Platform.runLater(() -> PopupHelper.showError(resolveOwner(), "Error", e.getMessage()));
            }
        }, "profile-save-legacy").start();
    }

    // =========================================================
    //  MODE B — RICH FXML initialize
    // =========================================================

    @FXML
    public void initialize() {
        applyCircleClip();
        if (userNameLabel != null && AuthService.CURRENT_USER_NAME != null)
            userNameLabel.setText(AuthService.CURRENT_USER_NAME);

        // ── Wire privacy toggle label + style on every click ──
        if (togglePrivacy != null) {
            updatePrivacyToggleAppearance(togglePrivacy.isSelected());
            togglePrivacy.selectedProperty().addListener((obs, wasSelected, isNowSelected) ->
                updatePrivacyToggleAppearance(isNowSelected));
        }

        loadProfileSettings();
    }

    /**
     * Updates the togglePrivacy button text and inline style to reflect
     * the current selected state.
     *
     *   selected = true  → Private (red)
     *   selected = false → Public  (green)
     */
    private void updatePrivacyToggleAppearance(boolean isPrivate) {
        if (togglePrivacy == null) return;
        if (isPrivate) {
            togglePrivacy.setText("🔒 Private");
            togglePrivacy.setStyle(
                "-fx-background-color: #ef4444; -fx-text-fill: white; " +
                "-fx-font-weight: bold; -fx-background-radius: 20; " +
                "-fx-border-radius: 20; -fx-padding: 5 16; -fx-cursor: hand;");
        } else {
            togglePrivacy.setText("🌐 Public");
            togglePrivacy.setStyle(
                "-fx-background-color: #22c55e; -fx-text-fill: white; " +
                "-fx-font-weight: bold; -fx-background-radius: 20; " +
                "-fx-border-radius: 20; -fx-padding: 5 16; -fx-cursor: hand;");
        }
    }

    private void populateRichUI(Profile p) {
        if (p == null) return;
        if (avatarView != null && p.getProfilePicture() != null && p.getProfilePicture().length > 0) {
            avatarView.setImage(new Image(new ByteArrayInputStream(p.getProfilePicture())));
            applyCircleClip(); // re-apply clip after image is set
        }

        set(tfFullName, p.getFullName()); set(tfStudentId, p.getStudentId());
        set(tfUniversity, p.getUniversityName()); set(tfDepartment, p.getDepartment());
        set(tfBatchSemester, p.getBatchSemester()); set(tfEmail, p.getPersonalEmail());
        set(tfPhone, p.getPhone());
        if (togglePhonePublic != null) togglePhonePublic.setSelected(p.isPhonePublic());

        set(tfCgpa, p.getCgpa() != null ? p.getCgpa().toPlainString() : "");
        set(tfCredits, String.valueOf(p.getCompletedCredits()));
        set(tfThesis, p.getThesisTitle());
        if (cbCgpaVisible != null) cbCgpaVisible.setSelected(p.isCgpaVisible());
        set(tfInterests, p.getAcademicInterests());
        set(tfCourses, p.getCurrentCourses());

        set(tfProgLangs, p.getProgLanguages()); set(tfFrameworks, p.getFrameworksTools());
        set(tfSoftSkills, p.getSoftSkills());

        set(tfGithub, p.getGithubUrl()); set(tfLinkedin, p.getLinkedinUrl());
        set(tfPortfolio, p.getPortfolioUrl()); set(tfCodeforces, p.getCodeforcesId());
        set(tfLeetcode, p.getLeetcodeId());

        // Sync privacy toggle state + appearance
        if (togglePrivacy != null) {
            boolean isPrivate = p.getProfileVisibility() == Profile.Visibility.PRIVATE;
            togglePrivacy.setSelected(isPrivate);
            updatePrivacyToggleAppearance(isPrivate);
        }

        if (accentPicker != null && p.getAccentColor() != null) {
            try { accentPicker.setValue(Color.web(p.getAccentColor())); } catch (Exception ignored) {}
        }
        updateBadge(p.completionPercent());
    }

    // ── Rich save handlers (wired from profile_settings.fxml) ──

    @FXML public void onSaveBasic() {
        runAsync(() -> profileService.saveBasicInfo(
                get(tfFullName), get(tfStudentId), get(tfUniversity), get(tfDepartment),
                get(tfBatchSemester), get(tfEmail), get(tfPhone),
                togglePhonePublic != null && togglePhonePublic.isSelected()
        ), "Identity saved ✅");
    }

    @FXML public void onSaveAcademic() {
        runAsync(() -> profileService.saveAcademic(
                parseBD(tfCgpa),
                cbCgpaVisible != null && cbCgpaVisible.isSelected(),
                parseInt(tfCredits), get(tfThesis), get(tfInterests), get(tfCourses)
        ), "Academic info saved ✅");
    }

    @FXML public void onSaveSkills() {
        runAsync(() -> profileService.saveSkills(
                get(tfProgLangs), get(tfFrameworks), get(tfSoftSkills), "{}"
        ), "Skills saved ✅");
    }

    @FXML public void onSaveLinks() {
        runAsync(() -> profileService.saveLinks(
                get(tfGithub), get(tfLinkedin), get(tfPortfolio),
                get(tfCodeforces), get(tfLeetcode)
        ), "Links saved ✅");
    }

    @FXML public void onSaveSettings() {
        runAsync(() -> {
            Profile.Visibility vis = (togglePrivacy != null && togglePrivacy.isSelected())
                    ? Profile.Visibility.PRIVATE : Profile.Visibility.PUBLIC;
            Profile.Theme theme = Profile.Theme.DARK;
            if (themeGroup != null && themeGroup.getSelectedToggle() != null)
                try { theme = Profile.Theme.valueOf(
                        ((String) themeGroup.getSelectedToggle().getUserData()).toUpperCase());
                } catch (Exception ignored) {}
            String accent = "#6C63FF";
            if (accentPicker != null) accent = toHex(accentPicker.getValue());
            Profile.FontSize fs = Profile.FontSize.MEDIUM;
            if (cbFontSize != null && cbFontSize.getValue() != null)
                try { fs = Profile.FontSize.valueOf(cbFontSize.getValue().toUpperCase()); }
                catch (Exception ignored) {}
            Profile.Lang lang = Profile.Lang.EN;
            if (cbLanguage != null && cbLanguage.getValue() != null)
                try { lang = Profile.Lang.valueOf(cbLanguage.getValue().toUpperCase()); }
                catch (Exception ignored) {}
            profileService.saveSettings(vis, theme, accent, fs, lang);
        }, "Settings saved ✅");
    }

    @FXML public void onUploadAvatar() {
        if (avatarView == null) return;
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose Profile Photo");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg"));
        Stage stage = (Stage) avatarView.getScene().getWindow();
        File file = chooser.showOpenDialog(stage);
        if (file == null) return;
        new Thread(() -> {
            try {
                byte[] bytes = Files.readAllBytes(file.toPath());
                String mime  = file.getName().toLowerCase().endsWith(".png") ? "image/png" : "image/jpeg";
                profileService.saveProfilePicture(bytes, mime);
                Image img = new Image(new ByteArrayInputStream(bytes));
                Platform.runLater(() -> {
                    avatarView.setImage(img);
                    applyCircleClip(); // re-apply after every image change
                });
            } catch (Exception e) {
                Platform.runLater(() -> PopupHelper.showError(resolveOwner(), "Upload Failed",
                        "Upload failed: " + e.getMessage()));
            }
        }, "avatar-upload").start();
    }

    @FXML
    public void handleLogoutTab(javafx.event.Event event) {
        Tab tab = (Tab) event.getSource();
        if (!tab.isSelected()) return;
        Window owner = tab.getTabPane() != null && tab.getTabPane().getScene() != null
                ? tab.getTabPane().getScene().getWindow()
                : null;
        PopupHelper.showConfirm(owner, "Logout", "Are you sure you want to logout?", () -> {
            AuthService.logout();
            try {
                Stage stage = (Stage) tab.getTabPane().getScene().getWindow();
                FXMLLoader loader = new FXMLLoader(
                        getClass().getResource("/com/scholar/view/login.fxml"));
                loader.setControllerFactory(springContext::getBean);
                Parent root = loader.load();
                stage.setScene(new Scene(root, 400, 600));
                stage.centerOnScreen();
            } catch (IOException e) { e.printStackTrace(); }
        });
        // If user cancels, revert tab selection back to tab 0.
        // PopupHelper.showConfirm is non-blocking so we wire the cancel
        // side-effect by always selecting tab 0 right away —
        // if they confirm, the stage scene replaces before it matters.
        tab.getTabPane().getSelectionModel().select(0);
    }

    // =========================================================
    //  HELPERS
    // =========================================================

    /**
     * Applies a circular clip to avatarView so the image is always
     * rendered as a circle regardless of source aspect ratio.
     * Safe to call multiple times — replaces any existing clip.
     */
    private void applyCircleClip() {
        if (avatarView == null) return;
        double r = Math.min(avatarView.getFitWidth(), avatarView.getFitHeight()) / 2.0;
        if (r <= 0) r = 50; // fallback if fitWidth/Height not set yet
        Circle clip = new Circle(r, r, r);
        avatarView.setClip(clip);
        avatarView.setPreserveRatio(false); // must be false so image fills the circle
    }

    private void updateBadge(int pct) {
        if (completionLabel != null) completionLabel.setText("Profile " + pct + "% complete");
        if (completionBar   != null) completionBar.setProgress(pct / 100.0);
    }

    private void runAsync(Runnable task, String successMsg) {
        new Thread(() -> {
            try {
                task.run();
                Platform.runLater(() -> PopupHelper.showInfo(resolveOwner(), "Success", successMsg));
            } catch (Exception e) {
                Platform.runLater(() -> PopupHelper.showError(resolveOwner(), "Error", e.getMessage()));
            }
        }, "profile-save").start();
    }

    /** Resolves the best available Window for PopupHelper. */
    private Window resolveOwner() {
        if (avatarView != null && avatarView.getScene() != null)
            return avatarView.getScene().getWindow();
        if (tfFullName != null && tfFullName.getScene() != null)
            return tfFullName.getScene().getWindow();
        if (legacyName != null && legacyName.getScene() != null)
            return legacyName.getScene().getWindow();
        return null;
    }

    private static void set(TextField tf, String val) {
        if (tf != null) tf.setText(val != null ? val : "");
    }
    private static String get(TextField tf) {
        if (tf == null) return null;
        String s = tf.getText() != null ? tf.getText().trim() : "";
        return s.isEmpty() ? null : s;
    }
    private static String nvl(String s)           { return s != null ? s : ""; }
    private static BigDecimal parseBD(TextField tf) {
        try { return (tf != null && !tf.getText().isBlank()) ? new BigDecimal(tf.getText().trim()) : null; }
        catch (NumberFormatException e) { return null; }
    }
    private static int parseInt(TextField tf) {
        try { return (tf != null && !tf.getText().isBlank()) ? Integer.parseInt(tf.getText().trim()) : 0; }
        catch (NumberFormatException e) { return 0; }
    }
    private static String toHex(Color c) {
        return String.format("#%02X%02X%02X",
                (int)(c.getRed()*255), (int)(c.getGreen()*255), (int)(c.getBlue()*255));
    }
}