package com.scholar.util;

import javafx.animation.FadeTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Popup;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import javafx.util.Duration;

public class NewPopupHelper { // 🌟 ক্লাসের নাম পরিবর্তন করা হয়েছে

    // ────────────────────────────────────────────────────────
    // 1. TOAST NOTIFICATION (Auto-hiding popup)
    // ────────────────────────────────────────────────────────
    public static void showToast(Window owner, String message) {
        Platform.runLater(() -> {
            if (owner == null) return;

            Label toastLabel = new Label(message);
            toastLabel.setStyle(
                "-fx-background-color: #10b981; -fx-text-fill: white; " +
                "-fx-padding: 12 24; -fx-background-radius: 25; " +
                "-fx-font-size: 14px; -fx-font-weight: bold; " +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.4), 12, 0, 0, 4);"
            );

            Popup popup = new Popup();
            popup.getContent().add(toastLabel);
            popup.setAutoHide(true);
            popup.show(owner);

            // Center horizontally, position slightly above the bottom
            popup.setX(owner.getX() + (owner.getWidth() - toastLabel.getWidth()) / 2.0);
            popup.setY(owner.getY() + owner.getHeight() - toastLabel.getHeight() - 80);

            FadeTransition fade = new FadeTransition(Duration.millis(500), toastLabel);
            fade.setDelay(Duration.seconds(2));
            fade.setFromValue(1.0);
            fade.setToValue(0.0);
            fade.setOnFinished(e -> popup.hide());
            fade.play();
        });
    }

    // ────────────────────────────────────────────────────────
    // 2. INFO ALERT
    // ────────────────────────────────────────────────────────
    public static void showInfo(Window owner, String title, String message) {
        Platform.runLater(() -> {
            VBox root = createBaseAlert(title, message, "#3b82f6"); // Blue accent
            Stage stage = create(owner, title, root, 350, 200, 400, 250);
            
            Button okBtn = createButton("OK", "#3b82f6");
            okBtn.setOnAction(e -> stage.close());
            root.getChildren().add(okBtn);
            
            stage.showAndWait();
        });
    }

    // ────────────────────────────────────────────────────────
    // 3. ERROR ALERT
    // ────────────────────────────────────────────────────────
    public static void showError(Window owner, String title, String message) {
        Platform.runLater(() -> {
            VBox root = createBaseAlert(title, message, "#ef4444"); // Red accent
            Stage stage = create(owner, title, root, 350, 200, 400, 250);
            
            Button okBtn = createButton("Dismiss", "#ef4444");
            okBtn.setOnAction(e -> stage.close());
            root.getChildren().add(okBtn);
            
            stage.showAndWait();
        });
    }

    // ────────────────────────────────────────────────────────
    // 4. CONFIRMATION ALERT (Yes/No)
    // ────────────────────────────────────────────────────────
    public static void showConfirm(Window owner, String title, String message, Runnable onConfirm) {
        Platform.runLater(() -> {
            VBox root = createBaseAlert(title, message, "#f59e0b"); // Warning orange accent
            Stage stage = create(owner, title, root, 350, 200, 400, 250);
            
            HBox buttons = new HBox(15);
            buttons.setAlignment(Pos.CENTER_RIGHT);
            
            Button cancelBtn = createButton("Cancel", "#374151");
            cancelBtn.setOnAction(e -> stage.close());
            
            Button confirmBtn = createButton("Confirm", "#f59e0b");
            confirmBtn.setOnAction(e -> {
                stage.close();
                if (onConfirm != null) onConfirm.run();
            });
            
            buttons.getChildren().addAll(cancelBtn, confirmBtn);
            root.getChildren().add(buttons);
            
            stage.showAndWait();
        });
    }

// ────────────────────────────────────────────────────────
    // 5. CUSTOM MODAL WINDOW CREATOR
    // ────────────────────────────────────────────────────────
    public static Stage create(Window owner, String title, Node content, double minW, double minH, double prefW, double prefH) {
        Stage stage = new Stage();
        stage.initOwner(owner);
        stage.initModality(Modality.APPLICATION_MODAL);
        // 🌟 FIX: DECORATED forces OS to render a proper drop shadow on Mac/Windows
        stage.initStyle(StageStyle.DECORATED); 
        stage.setTitle(title);

        VBox container = new VBox(content);
        // 🌟 FIX: Slightly lighter background than the main app, with a distinct border
        container.setStyle("-fx-background-color: #161b27; -fx-border-color: #2d3748; -fx-border-width: 1;");
        VBox.setVgrow(content, javafx.scene.layout.Priority.ALWAYS);

        Scene scene = new Scene(container, prefW, prefH);
        stage.setScene(scene);
        stage.setMinWidth(minW);
        stage.setMinHeight(minH);
        
        return stage;
    }

    // ── Internal Helpers ────────────────────────────────────

    private static VBox createBaseAlert(String titleText, String messageText, String accentColor) {
        VBox root = new VBox(15);
        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: #161b27; -fx-border-color: " + accentColor + "-fx-border-width: 0 0 0 4;");
        
        Label title = new Label(titleText);
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #e2e8f0;");
        
        Label msg = new Label(messageText);
        msg.setWrapText(true);
        msg.setStyle("-fx-font-size: 13px; -fx-text-fill: #94a3b8;");
        
        root.getChildren().addAll(title, msg);
        return root;
    }

    private static Button createButton(String text, String color) {
        Button btn = new Button(text);
        btn.setStyle(
            "-fx-background-color: " + color + "; -fx-text-fill: white; " +
            "-fx-font-weight: bold; -fx-padding: 8 16; -fx-background-radius: 6; -fx-cursor: hand;"
        );
        return btn;
    }
}