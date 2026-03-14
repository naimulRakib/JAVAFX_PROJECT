package com.scholar.util;

import javafx.animation.FadeTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Popup;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import javafx.util.Duration;

/**
 * SPopupHelper — drop-in replacement for raw Alert / TextInputDialog usage.
 *
 * Path: src/main/java/com/scholar/util/SPopupHelper.java
 *
 * Usage examples:
 *   SPopupHelper.showToast(window(), "Saved ✅");
 *   SPopupHelper.showError(window(), "Oops", "Something went wrong.");
 *   SPopupHelper.showInfo(window(), "Done", "Upload complete.");
 *   SPopupHelper.showConfirm(window(), "Delete?", "Are you sure?", () -> doDelete());
 *   SPopupHelper.showInput(window(), "Add Course", "Enter code:", "CSE 105", code -> save(code));
 *   Stage s = SPopupHelper.create(window(), "Title", myNode, 400, 300, 480, 360);
 */
public class SPopupHelper {

    // ────────────────────────────────────────────────────────────────
    // 1.  TOAST  (auto-hiding bottom notification)
    // ────────────────────────────────────────────────────────────────
    public static void showToast(Window owner, String message) {
        Platform.runLater(() -> {
            if (owner == null) return;

            Label toast = new Label(message);
            toast.setStyle(
                "-fx-background-color: #10b981; -fx-text-fill: white;" +
                "-fx-padding: 12 24; -fx-background-radius: 25;" +
                "-fx-font-size: 14px; -fx-font-weight: bold;" +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.4), 12, 0, 0, 4);");

            Popup popup = new Popup();
            popup.getContent().add(toast);
            popup.setAutoHide(true);
            popup.show(owner);

            // applyCss so width/height are ready before positioning
            toast.applyCss();
            toast.layout();
            popup.setX(owner.getX() + (owner.getWidth()  - toast.getWidth())  / 2.0);
            popup.setY(owner.getY() +  owner.getHeight() - toast.getHeight()  - 80);

            FadeTransition fade = new FadeTransition(Duration.millis(500), toast);
            fade.setDelay(Duration.seconds(2));
            fade.setFromValue(1.0);
            fade.setToValue(0.0);
            fade.setOnFinished(e -> popup.hide());
            fade.play();
        });
    }

    // ────────────────────────────────────────────────────────────────
    // 2.  INFO ALERT
    // ────────────────────────────────────────────────────────────────
    public static void showInfo(Window owner, String title, String message) {
        Platform.runLater(() -> {
            VBox root = buildAlertRoot(title, message, "#3b82f6");
            Stage stage = create(owner, title, root, 350, 180, 400, 220);

            Button ok = btn("  OK  ", "#3b82f6");
            ok.setOnAction(e -> stage.close());
            root.getChildren().add(rightAligned(ok));

            stage.showAndWait();
        });
    }

    // ────────────────────────────────────────────────────────────────
    // 3.  ERROR ALERT
    // ────────────────────────────────────────────────────────────────
    public static void showError(Window owner, String title, String message) {
        Platform.runLater(() -> {
            VBox root = buildAlertRoot(title, message, "#ef4444");
            Stage stage = create(owner, title, root, 350, 180, 400, 220);

            Button ok = btn("Dismiss", "#ef4444");
            ok.setOnAction(e -> stage.close());
            root.getChildren().add(rightAligned(ok));

            stage.showAndWait();
        });
    }

    // ────────────────────────────────────────────────────────────────
    // 4.  CONFIRM DIALOG  (Yes / No)
    // ────────────────────────────────────────────────────────────────
    public static void showConfirm(Window owner, String title, String message, Runnable onConfirm) {
        Platform.runLater(() -> {
            VBox root = buildAlertRoot(title, message, "#f59e0b");
            Stage stage = create(owner, title, root, 350, 180, 420, 230);

            Button cancel  = btn("Cancel",  "#374151");
            Button confirm = btn("Confirm", "#f59e0b");
            cancel.setOnAction(e -> stage.close());
            confirm.setOnAction(e -> { stage.close(); if (onConfirm != null) onConfirm.run(); });

            HBox row = new HBox(10, cancel, confirm);
            row.setAlignment(Pos.CENTER_RIGHT);
            row.setPadding(new Insets(0, 20, 20, 20));
            root.getChildren().add(row);

            stage.showAndWait();
        });
    }

    // ────────────────────────────────────────────────────────────────
    // 5.  TEXT INPUT DIALOG  (replaces TextInputDialog)
    // ────────────────────────────────────────────────────────────────
    public static void showInput(Window owner, String title, String prompt,
                                  String placeholder,
                                  java.util.function.Consumer<String> onResult) {
        Platform.runLater(() -> {
            Label titleLbl = new Label(title);
            titleLbl.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #e2e8f0;");

            Label promptLbl = new Label(prompt);
            promptLbl.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 13px;");
            promptLbl.setWrapText(true);

            TextField field = new TextField();
            field.setPromptText(placeholder);
            field.setStyle(
                "-fx-background-color: #1a2236; -fx-text-fill: #e2e8f0;" +
                "-fx-border-color: #3b82f6; -fx-border-radius: 8;" +
                "-fx-background-radius: 8; -fx-padding: 9 12; -fx-font-size: 13px;");
            field.setMaxWidth(Double.MAX_VALUE);

            Button cancel = btn("Cancel", "#374151");
            Button ok     = btn("Add",    "#3b82f6");
            cancel.setMinWidth(80);
            ok.setMinWidth(80);

            VBox root = new VBox(14, titleLbl, promptLbl, field);
            root.setPadding(new Insets(22, 22, 14, 22));
            root.setStyle("-fx-background-color: #161b27;");

            Stage stage = create(owner, title, root, 360, 200, 420, 250);

            Runnable submit = () -> {
                String val = field.getText().trim();
                if (val.isEmpty()) {
                    field.setStyle(field.getStyle() + " -fx-border-color: #ef4444;");
                    return;
                }
                stage.close();
                onResult.accept(val);
            };

            cancel.setOnAction(e -> stage.close());
            ok.setOnAction(e -> submit.run());
            field.setOnAction(e -> submit.run());   // Enter key submits

            HBox btnRow = new HBox(10, cancel, ok);
            btnRow.setAlignment(Pos.CENTER_RIGHT);
            btnRow.setPadding(new Insets(6, 0, 6, 0));
            root.getChildren().add(btnRow);

            stage.showAndWait();
        });
    }

    // ────────────────────────────────────────────────────────────────
    // 6.  CUSTOM MODAL WINDOW CREATOR
    // ────────────────────────────────────────────────────────────────
    public static Stage create(Window owner, String title, Node content,
                                double minW, double minH, double prefW, double prefH) {
        Stage stage = new Stage();
        if (owner != null) stage.initOwner(owner);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.initStyle(StageStyle.DECORATED);
        stage.setTitle(title);

        VBox container = new VBox(content);
        container.setStyle("-fx-background-color: #161b27; -fx-border-color: #2d3748; -fx-border-width: 1;");
        VBox.setVgrow(content, Priority.ALWAYS);

        Scene scene = new Scene(container, prefW, prefH);
        stage.setScene(scene);
        stage.setMinWidth(minW);
        stage.setMinHeight(minH);

        // Centre on owner window
        if (owner != null) {
            stage.setOnShown(e -> {
                stage.setX(owner.getX() + (owner.getWidth()  - stage.getWidth())  / 2.0);
                stage.setY(owner.getY() + (owner.getHeight() - stage.getHeight()) / 2.0);
            });
        }

        return stage;
    }

    // ── Private helpers ──────────────────────────────────────────────

    private static VBox buildAlertRoot(String titleText, String messageText, String accentColor) {
        VBox root = new VBox(12);
        root.setPadding(new Insets(22, 22, 10, 22));
        root.setStyle(
            "-fx-background-color: #161b27;" +
            "-fx-border-color: " + accentColor + ";" +
            "-fx-border-width: 0 0 0 4;");

        Label title = new Label(titleText);
        title.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #e2e8f0;");

        Label msg = new Label(messageText);
        msg.setWrapText(true);
        msg.setStyle("-fx-font-size: 13px; -fx-text-fill: #94a3b8;");

        root.getChildren().addAll(title, msg);
        return root;
    }

    private static Button btn(String text, String color) {
        Button b = new Button(text);
        b.setStyle(
            "-fx-background-color: " + color + "; -fx-text-fill: white;" +
            "-fx-font-weight: bold; -fx-padding: 8 18;" +
            "-fx-background-radius: 8; -fx-cursor: hand;");
        return b;
    }

    private static HBox rightAligned(Button b) {
        HBox row = new HBox(b);
        row.setAlignment(Pos.CENTER_RIGHT);
        row.setPadding(new Insets(0, 20, 20, 20));
        return row;
    }
}
