package com.scholar.util;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * PopupHelper — creates modal stages that:
 *   • always open centred over the owner window
 *   • appear on the correct screen (fixes macOS multi-monitor black screen)
 *   • never spawn at (0,0) top-left corner
 *
 * Path: src/main/java/com/scholar/util/PopupHelper.java
 */
public final class PopupHelper {

    private PopupHelper() {}

    /**
     * @param owner  Any node's window:  someNode.getScene().getWindow()
     * @param title  Window title bar text
     * @param root   Root layout (VBox, BorderPane, ScrollPane…)
     * @param minW   Minimum width  — prevents content clipping on resize
     * @param minH   Minimum height
     * @param prefW  Initial width
     * @param prefH  Initial height
     */
    public static Stage create(Window owner,
                               String title,
                               Region root,
                               double minW, double minH,
                               double prefW, double prefH) {

        // Let root fill the full stage when user resizes
        root.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        Scene scene = new Scene(root, prefW, prefH);
        scene.setFill(Color.web("#0f1117"));   // dark background, no white flash
 scene.getStylesheets().add(
        PopupHelper.class.getResource(
            "/com/scholar/view/css/scholar-dark-theme.css")
            .toExternalForm()
    );
    
        Stage stage = new Stage();
        stage.setTitle(title);
        stage.setScene(scene);
        stage.setMinWidth(minW);
        stage.setMinHeight(minH);

        // ── Critical fix: attach to owner window ──────────────────
        // Without this JavaFX picks the primary display at (0,0)
        if (owner != null) {
            stage.initOwner(owner);
        }
        stage.initModality(Modality.WINDOW_MODAL);

        // ── Centre over owner after layout ────────────────────────
        stage.setOnShowing(e -> centreOver(stage, owner));

        return stage;
    }

    /** Re-centres a stage over its owner. Safe to call any time. */
    public static void centreOver(Stage stage, Window owner) {
        if (owner == null) return;
        // Run after the layout pass so stage.getWidth() is valid
        Platform.runLater(() -> {
            double cx = owner.getX() + owner.getWidth()  / 2.0;
            double cy = owner.getY() + owner.getHeight() / 2.0;
            stage.setX(cx - stage.getWidth()  / 2.0);
            stage.setY(cy - stage.getHeight() / 2.0);
        });
    }
}