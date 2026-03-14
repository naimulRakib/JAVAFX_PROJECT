package com.scholar.controller.community;

import com.scholar.model.ResourceRow;
import com.scholar.service.CourseService;
import com.scholar.util.SPopupHelper;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

/**
 * STATISTICS CONTROLLER — Resource stats popup + Mark-as-done dialog.
 * All raw Alert calls replaced with SPopupHelper.
 *
 * Path: src/main/java/com/scholar/controller/community/StatisticsController.java
 */
@Component
public class StatisticsController {

    @Autowired private CourseService courseService;

    private final Set<String> activeDialogs = new HashSet<>();

    // ─────────────────────────────────────────────────────────────
    // STATISTICS POPUP
    // ─────────────────────────────────────────────────────────────
    public void showStatisticsDialog(CourseService.Resource res, Window owner) {

        String lockKey = "STATS_" + res.id();
        if (!activeDialogs.add(lockKey)) return;

        Label spinnerLbl = new Label("⏳ Fetching community data…");
        spinnerLbl.setStyle("-fx-text-fill: #7b8fa8; -fx-font-size: 13px;");
        VBox layout = new VBox(16, spinnerLbl);
        layout.setPadding(new Insets(24));
        layout.setStyle("-fx-background-color: #0f1117;");

        Stage stage = SPopupHelper.create(owner,
            "📊 Statistics — " + res.title(),
            layout, 460, 500, 520, 580);

        stage.setOnHidden(e -> activeDialogs.remove(lockKey));
        stage.show();

        new Thread(() -> {
            try {
                CourseService.ResourceStats stats = courseService.getResourceStatistics(res.id());
                Platform.runLater(() -> {
                    layout.getChildren().clear();

                    HBox votesBox = new HBox(16);
                    votesBox.setAlignment(Pos.CENTER_LEFT);
                    votesBox.getChildren().addAll(
                        badge("👍 " + stats.totalUpvotes()   + " upvotes",   "#14532d", "#4ade80"),
                        badge("👎 " + stats.totalDownvotes() + " downvotes", "#7f1d1d", "#f87171")
                    );

                    HBox diffBox = new HBox(12);
                    diffBox.setStyle("-fx-background-color: #161b27; -fx-padding: 14; -fx-background-radius: 10;");
                    diffBox.setAlignment(Pos.CENTER_LEFT);
                    diffBox.getChildren().addAll(
                        badge("🟢 Easy: "   + stats.easyCount(),   "#14532d", "#4ade80"),
                        badge("🟡 Medium: " + stats.mediumCount(), "#713f12", "#fbbf24"),
                        badge("🔴 Hard: "   + stats.hardCount(),   "#7f1d1d", "#f87171")
                    );

                    Label reviewTitle = new Label("📝  Student Notes & Experiences");
                    reviewTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #e2e8f0;");

                    VBox notesContainer = new VBox(10);
                    notesContainer.setStyle("-fx-background-color: #0f1117;");
                    notesContainer.setPadding(new Insets(4, 0, 4, 0));

                    if (stats.userLogs().isEmpty()) {
                        notesContainer.getChildren().add(emptyLabel("No notes yet — be the first! 🚀"));
                    } else {
                        for (CourseService.CompletionLog log : stats.userLogs()) {
                            VBox card = new VBox(8);
                            card.setStyle("-fx-background-color: #161b27; -fx-padding: 14; "
                                + "-fx-background-radius: 10; -fx-border-color: #1e2736; -fx-border-width: 1;");

                            HBox header = new HBox(10);
                            header.setAlignment(Pos.CENTER_LEFT);

                            Label nameLbl = new Label("👤 " + log.username());
                            nameLbl.setStyle("-fx-font-weight: bold; -fx-text-fill: #60a5fa;");

                            Label diffLbl = new Label("Rated: " + log.difficulty());
                            diffLbl.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 11px;");

                            Label timeLbl = new Label("⏱️ " + log.timeMins() + " min");
                            timeLbl.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 11px;");

                            Region sp = new Region();
                            HBox.setHgrow(sp, Priority.ALWAYS);

                            Label dateLbl = new Label(log.date());
                            dateLbl.setStyle("-fx-text-fill: #4a5a72; -fx-font-size: 11px;");

                            header.getChildren().addAll(nameLbl, diffLbl, timeLbl, sp, dateLbl);

                            Separator sep = new Separator();
                            sep.setStyle("-fx-opacity: 0.15;");

                            Label noteLbl = new Label(
                                (log.note() == null || log.note().isEmpty())
                                    ? "No additional notes." : log.note());
                            noteLbl.setWrapText(true);
                            noteLbl.setStyle("-fx-text-fill: #cbd5e1; -fx-font-size: 12px;");

                            card.getChildren().addAll(header, sep, noteLbl);
                            notesContainer.getChildren().add(card);
                        }
                    }

                    ScrollPane scroll = new ScrollPane(notesContainer);
                    scroll.setFitToWidth(true);
                    scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
                    scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
                    scroll.setStyle(
                        "-fx-background: #0f1117; -fx-background-color: #0f1117; -fx-border-color: transparent;");
                    scroll.skinProperty().addListener((obs, o, n) -> {
                        javafx.scene.Node vp = scroll.lookup(".viewport");
                        if (vp != null) vp.setStyle("-fx-background-color: #0f1117;");
                    });
                    VBox.setVgrow(scroll, Priority.ALWAYS);

                    layout.getChildren().addAll(votesBox, diffBox, reviewTitle, scroll);
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    activeDialogs.remove(lockKey);
                    SPopupHelper.showError(owner, "Error", "Could not load statistics: " + ex.getMessage());
                });
            }
        }).start();
    }

    // ─────────────────────────────────────────────────────────────
    // MARK AS DONE / EDIT NOTE
    // ─────────────────────────────────────────────────────────────
    public void showResourceCompletionDialog(ResourceRow row,
                                              Integer currentSelectedTopicId,
                                              Consumer<Integer> onReload,
                                              Window owner) {

        String lockKey = "DONE_" + row.getRawResource().id();
        if (!activeDialogs.add(lockKey)) return;

        new Thread(() -> {
            try {
                CourseService.UserProgress existing = courseService.getUserProgress(row.getRawResource().id());
                Platform.runLater(() -> {

                    boolean isEdit = existing.isCompleted();

                    TextField durationField = darkField(
                        existing.timeMins() > 0 ? String.valueOf(existing.timeMins()) : "");
                    durationField.setPromptText("Time in minutes (e.g. 45)");
                    durationField.setMaxWidth(Double.MAX_VALUE);

                    ComboBox<String> diffCombo = darkCombo("Easy", "Medium", "Hard");
                    diffCombo.setValue(existing.difficulty() != null ? existing.difficulty() : "Medium");
                    diffCombo.setMaxWidth(Double.MAX_VALUE);

                    TextArea noteArea = darkArea(existing.userNote() != null ? existing.userNote() : "");
                    noteArea.setPromptText("Your notes, hints, or review for future students…");
                    noteArea.setPrefRowCount(4);
                    noteArea.setWrapText(true);
                    noteArea.setMaxWidth(Double.MAX_VALUE);

                    VBox formBox = new VBox(18);
                    formBox.setPadding(new Insets(24));
                    formBox.setStyle("-fx-background-color: #161b27;");
                    formBox.getChildren().addAll(
                        fieldBlock("⏱  Time taken (mins)", durationField),
                        fieldBlock("📊  Rate difficulty",   diffCombo),
                        fieldBlock("📝  Your notes",         noteArea)
                    );

                    Button cancelBtn = new Button("Cancel");
                    cancelBtn.setStyle("-fx-background-color: #1e2736; -fx-text-fill: #94a3b8; "
                        + "-fx-background-radius: 10; -fx-padding: 10 24; -fx-cursor: hand;");

                    Button saveBtn = new Button(isEdit ? "✅  Update Details" : "🎉  Save Experience");
                    saveBtn.setStyle("-fx-background-color: linear-gradient(to right, #10b981, #06b6d4); "
                        + "-fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 10; "
                        + "-fx-padding: 10 24; -fx-cursor: hand;");

                    HBox btnRow = new HBox(12, cancelBtn, saveBtn);
                    btnRow.setAlignment(Pos.CENTER_RIGHT);
                    btnRow.setPadding(new Insets(4, 24, 20, 24));
                    btnRow.setStyle("-fx-background-color: #161b27;");

                    VBox root = new VBox(formBox, btnRow);
                    root.setStyle("-fx-background-color: #161b27;");

                    Stage stage = SPopupHelper.create(owner,
                        isEdit ? "✏️ Edit Your Note — " + row.getName()
                               : "🎉 Completed — " + row.getName(),
                        root, 420, 440, 480, 500);

                    stage.setOnHidden(ev -> activeDialogs.remove(lockKey));
                    stage.show();

                    cancelBtn.setOnAction(e -> stage.close());

                    saveBtn.setOnAction(e -> {
                        int mins = durationField.getText().matches("\\d+")
                            ? Integer.parseInt(durationField.getText()) : 0;
                        stage.close();
                        new Thread(() -> {
                            if (courseService.markResourceDone(row.getRawResource().id(),
                                    diffCombo.getValue(), mins, noteArea.getText().trim())) {
                                Platform.runLater(() -> {
                                    SPopupHelper.showToast(owner, "Progress & notes saved ✅");
                                    if (currentSelectedTopicId != null)
                                        onReload.accept(currentSelectedTopicId);
                                });
                            } else {
                                SPopupHelper.showError(owner, "Save Failed",
                                    "Could not save your progress. Please retry.");
                            }
                        }).start();
                    });
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    activeDialogs.remove(lockKey);
                    SPopupHelper.showError(owner, "Error", "Could not load your progress: " + ex.getMessage());
                });
            }
        }).start();
    }

    // ─────────────────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────────────────
    private static VBox fieldBlock(String labelText, javafx.scene.Node ctrl) {
        Label lbl = new Label(labelText);
        lbl.setStyle("-fx-text-fill: #7b8fa8; -fx-font-size: 12px; -fx-font-weight: bold;");
        if (ctrl instanceof Region r) r.setMaxWidth(Double.MAX_VALUE);
        VBox block = new VBox(6, lbl, ctrl);
        block.setFillWidth(true);
        return block;
    }

    private static Label badge(String text, String bg, String fg) {
        Label l = new Label(text);
        l.setStyle("-fx-background-color: " + bg + "; -fx-text-fill: " + fg + "; "
            + "-fx-font-weight: bold; -fx-font-size: 12px; -fx-padding: 4 12; "
            + "-fx-background-radius: 12;");
        return l;
    }

    private static Label emptyLabel(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-text-fill: #4a5a72; -fx-font-size: 13px;");
        return l;
    }

    private static TextField darkField(String val) {
        TextField f = new TextField(val);
        f.setStyle("-fx-background-color: #1a2236; -fx-text-fill: #e2e8f0; "
            + "-fx-border-color: #2c3a52; -fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 8 12;");
        return f;
    }

    private static TextArea darkArea(String val) {
        TextArea a = new TextArea(val);
        a.setStyle("-fx-background-color: #1a2236; -fx-text-fill: #e2e8f0; "
            + "-fx-border-color: #2c3a52; -fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 8 12;");
        return a;
    }

    private static ComboBox<String> darkCombo(String... items) {
        ComboBox<String> c = new ComboBox<>();
        c.getItems().addAll(items);
        c.setMaxWidth(Double.MAX_VALUE);
        c.setStyle("-fx-background-color: #1a2236; -fx-text-fill: #e2e8f0; "
            + "-fx-border-color: #2c3a52; -fx-border-radius: 8; -fx-background-radius: 8;");
        return c;
    }
}