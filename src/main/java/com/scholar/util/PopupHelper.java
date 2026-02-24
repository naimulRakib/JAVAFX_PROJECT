package com.scholar.util;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.TextAlignment;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;


/**
 * PopupHelper — creates modal stages that:
 *   • always open centred over the owner window
 *   • appear on the correct screen (fixes macOS multi-monitor black screen)
 *   • never spawn at (0,0) top-left corner
 *   • apply the dark theme stylesheet automatically
 *
 * Also provides static helpers:
 *   • showInfo(owner, title, message)
 *   • showError(owner, title, message)
 *   • showConfirm(owner, title, message, onConfirm)
 *
 * These replace ALL usages of JavaFX Alert throughout the app, fixing the
 * black-screen repaint bug that occurs when an Alert is shown without an
 * owner window while the main window is maximised.
 *
 * Path: src/main/java/com/scholar/util/PopupHelper.java
 */
public final class PopupHelper {

    private PopupHelper() {}

    private static final String DARK_CSS = "/com/scholar/view/css/scholar-dark-theme.css";

    // ══════════════════════════════════════════════════════════════════════════
    // CORE FACTORY
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Creates a ready-to-show modal Stage with dark background.
     * Call popup.show() after attaching any button handlers.
     *
     * @param owner  Any node's window:  someNode.getScene().getWindow()
     * @param title  Window title bar text
     * @param root   Root layout (VBox, BorderPane, ScrollPane…)
     * @param minW   Minimum width
     * @param minH   Minimum height
     * @param prefW  Initial width
     * @param prefH  Initial height
     */
    public static Stage create(Window owner,
                               String title,
                               Region root,
                               double minW, double minH,
                               double prefW, double prefH) {

        root.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        Scene scene = new Scene(root, prefW, prefH);
        scene.setFill(Color.web("#0f1117"));

        // Apply dark theme
        var cssUrl = PopupHelper.class.getResource(DARK_CSS);
        if (cssUrl != null) scene.getStylesheets().add(cssUrl.toExternalForm());

        Stage stage = new Stage();
        stage.setTitle(title);
        stage.setScene(scene);
        stage.setMinWidth(minW);
        stage.setMinHeight(minH);

        if (owner != null) stage.initOwner(owner);
        stage.initModality(Modality.WINDOW_MODAL);

        // Centre over owner after layout pass
        stage.setOnShowing(e -> centreOver(stage, owner));

        return stage;
    }

    /** Re-centres a stage over its owner. Safe to call at any time. */
    public static void centreOver(Stage stage, Window owner) {
        if (owner == null) return;
        Platform.runLater(() -> {
            double cx = owner.getX() + owner.getWidth()  / 2.0;
            double cy = owner.getY() + owner.getHeight() / 2.0;
            stage.setX(cx - stage.getWidth()  / 2.0);
            stage.setY(cy - stage.getHeight() / 2.0);
        });
    }

    // ══════════════════════════════════════════════════════════════════════════
    // STATIC MESSAGE HELPERS
    // These replace every Alert.INFORMATION / Alert.ERROR / Alert.CONFIRMATION
    // in the app, fixing the black-screen bug on window maximise.
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Show a dark-themed info popup, centred over owner.
     * Non-blocking — control returns immediately to the caller.
     * Safe to call from any thread (dispatches to FX thread automatically).
     */
    public static void showInfo(Window owner, String title, String message) {
        runOnFx(() -> buildMessagePopup(owner, "ℹ️", "#60a5fa", title, message, null));
    }

    /**
     * Convenience overload: uses "Notice" as the title.
     */
    public static void showInfo(Window owner, String message) {
        showInfo(owner, "Notice", message);
    }

    /**
     * Show a dark-themed error popup, centred over owner.
     * Non-blocking. Safe to call from any thread.
     */
    public static void showError(Window owner, String title, String message) {
        runOnFx(() -> buildMessagePopup(owner, "❌", "#f87171", title, message, null));
    }

    /**
     * Convenience overload: uses "Error" as the title.
     */
    public static void showError(Window owner, String message) {
        showError(owner, "Error", message);
    }

    /**
     * Show a dark-themed confirmation popup centred over owner.
     * onConfirm runs on the FX thread when the user clicks Confirm.
     * Non-blocking for the caller.
     */
    public static void showConfirm(Window owner, String title,
                                   String message, Runnable onConfirm) {
        runOnFx(() -> buildConfirmPopup(owner, title, message, onConfirm));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // INTERNAL BUILDERS
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Builds an info/error popup with a single "OK" button.
     * If onClose is non-null it runs after the popup closes.
     */
    private static void buildMessagePopup(Window owner, String emoji,
                                          String accentColor, String title,
                                          String message, Runnable onClose) {
        VBox content = new VBox(18);
        content.setAlignment(Pos.CENTER);
        content.setPadding(new Insets(30, 32, 26, 32));
        content.setStyle("-fx-background-color:#13151f;");

        Label iconLbl = new Label(emoji);
        iconLbl.setStyle("-fx-font-size:36px;");

        Label titleLbl = new Label(title);
        titleLbl.setStyle("-fx-font-size:15px; -fx-font-weight:bold; "
                + "-fx-text-fill:" + accentColor + ";");

        Label msgLbl = new Label(message);
        msgLbl.setStyle("-fx-text-fill:#cbd5e1; -fx-font-size:13px;");
        msgLbl.setWrapText(true);
        msgLbl.setTextAlignment(TextAlignment.CENTER);
        msgLbl.setMaxWidth(320);

        Button okBtn = new Button("OK");
        okBtn.setStyle("-fx-background-color:" + accentColor.replace(")", ",0.15)").replace("#", "rgba(")
                // fallback if replace fails — just use accent directly
                + "; -fx-background-color:rgba(99,102,241,0.2); "
                + "-fx-text-fill:white; -fx-font-weight:bold; "
                + "-fx-background-radius:8; -fx-padding:9 32; -fx-cursor:hand; -fx-font-size:13px;");
        // Simpler, always-correct style:
        okBtn.setStyle("-fx-background-color:#23263a; -fx-text-fill:#e2e8f0; "
                + "-fx-font-weight:bold; -fx-background-radius:8; "
                + "-fx-border-color:#3d4268; -fx-border-radius:8; "
                + "-fx-padding:9 32; -fx-cursor:hand; -fx-font-size:13px;");

        content.getChildren().addAll(iconLbl, titleLbl, msgLbl, okBtn);

        Stage popup = create(owner, title, content, 300, 200, 380, 250);

        okBtn.setOnAction(e -> {
            popup.close();
            if (onClose != null) Platform.runLater(onClose);
        });

        popup.show();
    }

    /**
     * Builds a confirmation popup with Cancel / Confirm buttons.
     */
    private static void buildConfirmPopup(Window owner, String title,
                                          String message, Runnable onConfirm) {
        VBox content = new VBox(18);
        content.setAlignment(Pos.CENTER);
        content.setPadding(new Insets(30, 32, 26, 32));
        content.setStyle("-fx-background-color:#13151f;");

        Label iconLbl = new Label("⚠️");
        iconLbl.setStyle("-fx-font-size:36px;");

        Label titleLbl = new Label(title);
        titleLbl.setStyle("-fx-font-size:15px; -fx-font-weight:bold; -fx-text-fill:#fbbf24;");

        Label msgLbl = new Label(message);
        msgLbl.setStyle("-fx-text-fill:#cbd5e1; -fx-font-size:13px;");
        msgLbl.setWrapText(true);
        msgLbl.setTextAlignment(TextAlignment.CENTER);
        msgLbl.setMaxWidth(320);

        HBox btnRow = new HBox(14);
        btnRow.setAlignment(Pos.CENTER);

        Button cancelBtn = new Button("Cancel");
        cancelBtn.setStyle("-fx-background-color:#1e2235; -fx-text-fill:#94a3b8; "
                + "-fx-background-radius:8; -fx-border-color:#2d3150; -fx-border-radius:8; "
                + "-fx-padding:9 24; -fx-cursor:hand; -fx-font-size:13px;");

        Button confirmBtn = new Button("Confirm");
        confirmBtn.setStyle("-fx-background-color:#dc2626; -fx-text-fill:white; "
                + "-fx-font-weight:bold; -fx-background-radius:8; "
                + "-fx-padding:9 24; -fx-cursor:hand; -fx-font-size:13px;");

        btnRow.getChildren().addAll(cancelBtn, confirmBtn);
        content.getChildren().addAll(iconLbl, titleLbl, msgLbl, btnRow);

        Stage popup = create(owner, title, content, 320, 220, 420, 260);

        cancelBtn.setOnAction(e -> popup.close());
        confirmBtn.setOnAction(e -> {
            popup.close();
            Platform.runLater(onConfirm);
        });

        popup.show();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // THREAD SAFETY
    // ══════════════════════════════════════════════════════════════════════════

    /** Runs r on the FX thread, or immediately if already on it. */
    private static void runOnFx(Runnable r) {
        if (Platform.isFxApplicationThread()) r.run();
        else Platform.runLater(r);
    }
}