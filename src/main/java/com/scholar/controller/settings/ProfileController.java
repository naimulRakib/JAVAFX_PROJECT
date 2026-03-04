package com.scholar.controller.settings;

import com.scholar.model.Profile;
import com.scholar.service.AuthService;
import com.scholar.service.ProfileService;
import com.scholar.util.NewPopupHelper;
import com.scholar.util.PopupHelper;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Rectangle2D;
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

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;

@Component
public class ProfileController {

    @Autowired private ProfileService profileService;
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
    @FXML private TextField    tfUsername;
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
    //  MODE A — LEGACY API
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
                    if (legacyName     != null) legacyName.setText(nvl(p.getFullName()));
                    if (legacyUsername != null) legacyUsername.setText(nvl(AuthService.CURRENT_USER_NAME));
                    if (legacyEmail    != null) legacyEmail.setText(nvl(AuthService.CURRENT_USER_EMAIL));
                    populateRichUI(p);
                });
            } catch (Exception e) {
                System.err.println("❌ loadProfileSettings error: " + e.getMessage());
            }
        }, "profile-load").start();
    }

    @FXML
    public void onSaveProfile() {
        onSaveProfile(null);
    }

    public void onSaveProfile(ActionEvent event) {
        disableAndRun(event, () -> {
            String username = legacyUsername != null ? legacyUsername.getText().trim() : get(tfUsername);
            validateUsername(username);
            profileService.saveBasicInfo(
                    username,
                    legacyName  != null ? legacyName.getText().trim() : get(tfFullName),
                    get(tfStudentId), get(tfUniversity), get(tfDepartment),
                    get(tfBatchSemester),
                    legacyEmail != null ? legacyEmail.getText() : get(tfEmail),
                    get(tfPhone),
                    togglePhonePublic != null && togglePhonePublic.isSelected()
            );
        }, "Profile & Nickname updated!");
    }

    // =========================================================
    //  MODE B — FXML initialize
    // =========================================================

    @FXML
    public void initialize() {
        applyCircleClip();
        if (userNameLabel != null && AuthService.CURRENT_USER_NAME != null)
            userNameLabel.setText(AuthService.CURRENT_USER_NAME);

        if (togglePrivacy != null) {
            updatePrivacyToggleAppearance(togglePrivacy.isSelected());
            togglePrivacy.selectedProperty().addListener((obs, old, isNowSelected) ->
                    updatePrivacyToggleAppearance(isNowSelected));
        }

        loadProfileSettings();
    }

    private void updatePrivacyToggleAppearance(boolean isPrivate) {
        if (togglePrivacy == null) return;
        if (isPrivate) {
            togglePrivacy.setText("🔒 Private");
            togglePrivacy.setStyle("-fx-background-color:#ef4444;-fx-text-fill:white;-fx-font-weight:bold;-fx-background-radius:20;-fx-padding:5 16;-fx-cursor:hand;");
        } else {
            togglePrivacy.setText("🌐 Public");
            togglePrivacy.setStyle("-fx-background-color:#22c55e;-fx-text-fill:white;-fx-font-weight:bold;-fx-background-radius:20;-fx-padding:5 16;-fx-cursor:hand;");
        }
    }

    private void populateRichUI(Profile p) {
        if (p == null) return;

        // Avatar: Supabase URL takes priority; load asynchronously so UI stays responsive
        if (avatarView != null) {
            String url = p.getProfilePictureUrl();
            if (url != null && !url.isBlank()) {
                loadAvatarFromUrl(url);
            }
        }

        if (userNameLabel != null && p.getUsername() != null)
            userNameLabel.setText(p.getUsername());

        set(tfFullName,     p.getFullName());
        set(tfUsername,     p.getUsername());
        set(tfStudentId,    p.getStudentId());
        set(tfUniversity,   p.getUniversityName());
        set(tfDepartment,   p.getDepartment());
        set(tfBatchSemester,p.getBatchSemester());
        set(tfEmail,        p.getPersonalEmail());
        set(tfPhone,        p.getPhone());
        if (togglePhonePublic != null) togglePhonePublic.setSelected(p.isPhonePublic());

        set(tfCgpa,    p.getCgpa() != null ? p.getCgpa().toPlainString() : "");
        set(tfCredits, String.valueOf(p.getCompletedCredits()));
        set(tfThesis,  p.getThesisTitle());
        if (cbCgpaVisible != null) cbCgpaVisible.setSelected(p.isCgpaVisible());
        set(tfInterests, p.getAcademicInterests());
        set(tfCourses,   p.getCurrentCourses());

        set(tfProgLangs,  p.getProgLanguages());
        set(tfFrameworks, p.getFrameworksTools());
        set(tfSoftSkills, p.getSoftSkills());

        set(tfGithub,     p.getGithubUrl());
        set(tfLinkedin,   p.getLinkedinUrl());
        set(tfPortfolio,  p.getPortfolioUrl());
        set(tfCodeforces, p.getCodeforcesId());
        set(tfLeetcode,   p.getLeetcodeId());

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

    // =========================================================
    //  SAVE HANDLERS
    // =========================================================

    @FXML public void onSaveBasic(ActionEvent event) {
        disableAndRun(event, () -> {
            String username = get(tfUsername);
            validateUsername(username);
            profileService.saveBasicInfo(
                    username, get(tfFullName), get(tfStudentId), get(tfUniversity),
                    get(tfDepartment), get(tfBatchSemester), get(tfEmail), get(tfPhone),
                    togglePhonePublic != null && togglePhonePublic.isSelected()
            );
        }, "Identity & Nickname saved ✅");
    }

    @FXML public void onSaveAcademic(ActionEvent event) {
        disableAndRun(event, () -> profileService.saveAcademic(
                parseBD(tfCgpa), cbCgpaVisible != null && cbCgpaVisible.isSelected(),
                parseInt(tfCredits), get(tfThesis), get(tfInterests), get(tfCourses)
        ), "Academic info saved ✅");
    }

    @FXML public void onSaveSkills(ActionEvent event) {
        disableAndRun(event, () -> profileService.saveSkills(
                get(tfProgLangs), get(tfFrameworks), get(tfSoftSkills), "{}"
        ), "Skills saved ✅");
    }

    @FXML public void onSaveLinks(ActionEvent event) {
        disableAndRun(event, () -> profileService.saveLinks(
                get(tfGithub), get(tfLinkedin), get(tfPortfolio),
                get(tfCodeforces), get(tfLeetcode)
        ), "Links saved ✅");
    }

    @FXML public void onSaveSettings(ActionEvent event) {
        disableAndRun(event, () -> {
            Profile.Visibility vis = (togglePrivacy != null && togglePrivacy.isSelected())
                    ? Profile.Visibility.PRIVATE : Profile.Visibility.PUBLIC;

            Profile.Theme theme = Profile.Theme.DARK;
            if (themeGroup != null && themeGroup.getSelectedToggle() != null) {
                try { theme = Profile.Theme.valueOf(
                        ((String) themeGroup.getSelectedToggle().getUserData()).toUpperCase());
                } catch (Exception ignored) {}
            }

            String accent = (accentPicker != null) ? toHex(accentPicker.getValue()) : "#6C63FF";

            Profile.FontSize fs = Profile.FontSize.MEDIUM;
            if (cbFontSize != null && cbFontSize.getValue() != null)
                try { fs = Profile.FontSize.valueOf(cbFontSize.getValue().toUpperCase()); } catch (Exception ignored) {}

            Profile.Lang lang = Profile.Lang.EN;
            if (cbLanguage != null && cbLanguage.getValue() != null)
                try { lang = Profile.Lang.valueOf(cbLanguage.getValue().toUpperCase()); } catch (Exception ignored) {}

            profileService.saveSettings(vis, theme, accent, fs, lang);
        }, "Settings saved ✅");
    }

    // =========================================================
    //  AVATAR UPLOAD
    // =========================================================

    @FXML public void onUploadAvatar(ActionEvent event) {
        if (avatarView == null) return;

        Button btn = (event != null && event.getSource() instanceof Button b) ? b : null;
        if (btn != null) btn.setDisable(true);

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose Profile Photo");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg"));

        Stage stage = (Stage) avatarView.getScene().getWindow();
        File file = chooser.showOpenDialog(stage);

        if (file == null) {
            if (btn != null) btn.setDisable(false);
            return;
        }

        // Show local preview immediately for instant feedback
        Image localPreview = new Image(file.toURI().toString());
        avatarView.setImage(localPreview);
        applyCircleClip();
        NewPopupHelper.showToast(resolveOwner(), "⏳ Uploading...");

        new Thread(() -> {
            try {
                byte[] bytes     = Files.readAllBytes(file.toPath());
                String fileName  = file.getName();
                int    dotIdx    = fileName.lastIndexOf('.');
                String extension = (dotIdx >= 0) ? fileName.substring(dotIdx).toLowerCase() : ".jpg";
                String mime      = extension.equals(".png") ? "image/png" : "image/jpeg";

                // Upload to Supabase — returns the public URL
                String publicUrl = profileService.uploadProfilePictureToSupabase(bytes, mime, extension);

                Platform.runLater(() -> {
                    // Reload from the canonical URL so future loads everywhere are consistent
                    loadAvatarFromUrl(publicUrl);
                    NewPopupHelper.showToast(resolveOwner(), "✅ Profile picture updated!");
                    if (btn != null) btn.setDisable(false);
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    PopupHelper.showError(resolveOwner(), "Upload Failed", e.getMessage());
                    if (btn != null) btn.setDisable(false);
                });
            }
        }, "avatar-upload").start();
    }

    // =========================================================
    //  LOGOUT
    // =========================================================

    @FXML
    public void handleLogoutTab(javafx.event.Event event) {
        Tab tab = (Tab) event.getSource();
        if (!tab.isSelected()) return;
        Window owner = (tab.getTabPane() != null && tab.getTabPane().getScene() != null)
                ? tab.getTabPane().getScene().getWindow() : null;

        PopupHelper.showConfirm(owner, "Logout", "Are you sure you want to logout?", () -> {
            AuthService.logout();
            try {
                Stage stage = (Stage) tab.getTabPane().getScene().getWindow();
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/scholar/view/login.fxml"));
                loader.setControllerFactory(springContext::getBean);
                Parent root = loader.load();
                stage.setScene(new Scene(root, 400, 600));
                stage.centerOnScreen();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        tab.getTabPane().getSelectionModel().select(0);
    }

    // =========================================================
    //  PRIVATE HELPERS
    // =========================================================

    /**
     * Loads an avatar from a URL asynchronously (JavaFX Image background=true),
     * then applies the circle crop once fully loaded.
     */
    private void loadAvatarFromUrl(String url) {
        if (avatarView == null || url == null || url.isBlank()) return;
        // Append cache-buster so updated images are not served stale from the OS cache
        String cacheBusted = url.contains("?") ? url : url + "?t=" + System.currentTimeMillis();
        Image img = new Image(cacheBusted, true); // background loading
        avatarView.setImage(img);
        img.progressProperty().addListener((obs, oldV, newV) -> {
            if (newV.doubleValue() >= 1.0 && !img.isError()) {
                Platform.runLater(this::applyCircleClip);
            }
        });
    }

    /**
     * Applies a centre-crop viewport + circular clip to avatarView.
     * Safe to call even before an image is loaded.
     */
    private void applyCircleClip() {
        if (avatarView == null) return;
        Image img = avatarView.getImage();
        if (img != null && img.getWidth() > 0 && img.getHeight() > 0) {
            double size = Math.min(img.getWidth(), img.getHeight());
            double x    = (img.getWidth()  - size) / 2.0;
            double y    = (img.getHeight() - size) / 2.0;
            avatarView.setViewport(new Rectangle2D(x, y, size, size));
        }
        double r = Math.min(avatarView.getFitWidth(), avatarView.getFitHeight()) / 2.0;
        if (r <= 0) r = 50;
        Circle clip = new Circle(r, r, r);
        avatarView.setClip(clip);
        avatarView.setPreserveRatio(true);
    }

    /** Validates nickname format; throws RuntimeException with user-friendly message. */
    private void validateUsername(String username) {
        if (username != null && !username.matches("^[a-zA-Z0-9_]{3,20}$")) {
            throw new RuntimeException("Nickname must be 3–20 characters (letters, numbers, underscores only).");
        }
    }

    /** Runs a task on a background thread; re-enables button and shows success/error on JavaFX thread. */
    private void disableAndRun(ActionEvent event, Runnable task, String successMsg) {
        Button btn = (event != null && event.getSource() instanceof Button b) ? b : null;
        if (btn != null) btn.setDisable(true);

        new Thread(() -> {
            try {
                task.run();
                Platform.runLater(() -> {
                    PopupHelper.showInfo(resolveOwner(), "Success", successMsg);
                    if (btn != null) btn.setDisable(false);
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    PopupHelper.showError(resolveOwner(), "Error", e.getMessage());
                    if (btn != null) btn.setDisable(false);
                });
            }
        }, "profile-save").start();
    }

    private void updateBadge(int pct) {
        if (completionLabel != null) completionLabel.setText("Profile " + pct + "% complete");
        if (completionBar   != null) completionBar.setProgress(pct / 100.0);
    }

    private Window resolveOwner() {
        if (avatarView != null  && avatarView.getScene()  != null) return avatarView.getScene().getWindow();
        if (tfFullName != null  && tfFullName.getScene()  != null) return tfFullName.getScene().getWindow();
        if (legacyName != null  && legacyName.getScene()  != null) return legacyName.getScene().getWindow();
        return null;
    }

    // ── Micro-helpers ──────────────────────────────────────────────
    private static void set(TextField tf, String val) {
        if (tf != null) tf.setText(val != null ? val : "");
    }
    private static String get(TextField tf) {
        if (tf == null) return null;
        String s = tf.getText() != null ? tf.getText().trim() : "";
        return s.isEmpty() ? null : s;
    }
    private static String nvl(String s) { return s != null ? s : ""; }
    private static BigDecimal parseBD(TextField tf) {
        try { return (tf != null && tf.getText() != null && !tf.getText().isBlank())
                ? new BigDecimal(tf.getText().trim()) : null;
        } catch (NumberFormatException e) { return null; }
    }
    private static int parseInt(TextField tf) {
        try { return (tf != null && tf.getText() != null && !tf.getText().isBlank())
                ? Integer.parseInt(tf.getText().trim()) : 0;
        } catch (NumberFormatException e) { return 0; }
    }
    private static String toHex(Color c) {
        return String.format("#%02X%02X%02X",
                (int)(c.getRed()*255), (int)(c.getGreen()*255), (int)(c.getBlue()*255));
    }
}