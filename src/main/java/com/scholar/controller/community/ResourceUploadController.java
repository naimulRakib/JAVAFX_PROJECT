package com.scholar.controller.community;

import com.scholar.controller.AIController;
import com.scholar.service.AuthService;
import com.scholar.service.CourseService;
import com.scholar.util.PopupHelper;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * RESOURCE UPLOAD CONTROLLER — Upload dialog with AI summary generation
 * Path: src/main/java/com/scholar/controller/community/ResourceUploadController.java
 */
@Component
public class ResourceUploadController {

    @Autowired private CourseService courseService;
    @Autowired private AIController  aiController;

    private java.util.function.Consumer<Integer> onUploadSuccess;

    public void init(java.util.function.Consumer<Integer> onUploadSuccess) {
        this.onUploadSuccess = onUploadSuccess;
    }

    // ─────────────────────────────────────────────────────────────
    // UPLOAD DIALOG
    // ─────────────────────────────────────────────────────────────
    @FXML
    public void onUploadResourceClick(Integer currentSelectedTopicId, Window owner) {

        if (currentSelectedTopicId == null) {
            Alert err = new Alert(Alert.AlertType.ERROR,
                "❌ Please select a specific Topic from the left panel first!");
            err.initOwner(owner);
            err.showAndWait();
            return;
        }

        // ── Form fields ───────────────────────────────────────────
        TextField titleField    = darkField("");
        titleField.setPromptText("E.g., Term Final Questions 2023");
        titleField.setMaxWidth(Double.MAX_VALUE);

        TextField linkField     = darkField("https://");
        linkField.setMaxWidth(Double.MAX_VALUE);

        ComboBox<String> typeCombo = darkCombo("LINK", "PDF", "Video", "Note");
        typeCombo.setValue("LINK");
        typeCombo.setMaxWidth(Double.MAX_VALUE);

        ComboBox<String> diffCombo = darkCombo("Easy", "Medium", "Hard");
        diffCombo.setValue("Medium");
        diffCombo.setMaxWidth(Double.MAX_VALUE);

        TextField durationField = darkField("");
        durationField.setPromptText("e.g., 30 mins");
        durationField.setMaxWidth(Double.MAX_VALUE);

        TextField tagsField     = darkField("");
        tagsField.setPromptText("#Questions  #2023");
        tagsField.setMaxWidth(Double.MAX_VALUE);

        TextArea descField      = darkArea("");
        descField.setPromptText("Resource details…");
        descField.setPrefRowCount(3);
        descField.setWrapText(true);
        descField.setMaxWidth(Double.MAX_VALUE);

        // ── Form: label on top, field below ──────────────────────
        VBox formBox = new VBox(16);
        formBox.setPadding(new Insets(24));
        formBox.setStyle("-fx-background-color: #161b27;");
        formBox.getChildren().addAll(
            fieldBlock("📌  Title *",      titleField),
            fieldBlock("🔗  Drive Link *", linkField),
            fieldBlock("📁  Type",         typeCombo),
            fieldBlock("📊  Difficulty",   diffCombo),
            fieldBlock("⏱  Duration",     durationField),
            fieldBlock("🏷  Tags",         tagsField),
            fieldBlock("📝  Description",  descField)
        );

        // Wrap in dark scroll pane
        ScrollPane formScroll = new ScrollPane(formBox);
        formScroll.setFitToWidth(true);
        formScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        formScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        formScroll.setStyle(
            "-fx-background: #161b27; -fx-background-color: #161b27; -fx-border-color: transparent;");
        formScroll.skinProperty().addListener((obs, o, n) -> {
            javafx.scene.Node vp = formScroll.lookup(".viewport");
            if (vp != null) vp.setStyle("-fx-background-color: #161b27;");
        });
        VBox.setVgrow(formScroll, Priority.ALWAYS);

        // ── AI notice ─────────────────────────────────────────────
        Label aiNote = new Label("✨ An AI summary will be auto-generated on save");
        aiNote.setStyle("-fx-text-fill: #60a5fa; -fx-font-size: 11px; -fx-padding: 0 24 8 24;");

        // ── Progress indicator ────────────────────────────────────
        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setPrefSize(22, 22);
        spinner.setVisible(false);
        spinner.setStyle("-fx-progress-color: #10b981;");

        Label savingLbl = new Label("Saving with AI summary…");
        savingLbl.setVisible(false);
        savingLbl.setStyle("-fx-text-fill: #4a5a72; -fx-font-size: 12px;");

        // ── Buttons ───────────────────────────────────────────────
        Button cancelBtn = new Button("Cancel");
        cancelBtn.setStyle("-fx-background-color: #1e2736; -fx-text-fill: #94a3b8; "
            + "-fx-background-radius: 10; -fx-padding: 10 24; -fx-cursor: hand;");

        Button saveBtn = new Button("📤  Save Resource");
        saveBtn.setStyle("-fx-background-color: linear-gradient(to right, #10b981, #06b6d4); "
            + "-fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 10; "
            + "-fx-padding: 10 24; -fx-cursor: hand;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox btnRow = new HBox(12, spinner, savingLbl, spacer, cancelBtn, saveBtn);
        btnRow.setAlignment(Pos.CENTER_RIGHT);
        btnRow.setPadding(new Insets(4, 24, 20, 24));
        btnRow.setStyle("-fx-background-color: #161b27;");

        VBox root = new VBox(formScroll, aiNote, btnRow);
        root.setStyle("-fx-background-color: #161b27;");

        // ── PopupHelper stage ─────────────────────────────────────
        Stage stage = PopupHelper.create(owner,
            "📤 Upload Resource",
            root, 460, 560, 520, 620);
        stage.show();

        cancelBtn.setOnAction(e -> stage.close());

        saveBtn.setOnAction(e -> {
            String title = titleField.getText().trim();
            String link  = linkField.getText().trim();
            if (title.isEmpty() || link.isEmpty()) {
                titleField.setStyle(titleField.getStyle() + " -fx-border-color: #ef4444;");
                return;
            }

            spinner.setVisible(true);
            savingLbl.setVisible(true);
            saveBtn.setDisable(true);
            cancelBtn.setDisable(true);
            titleField.setDisable(true);
            linkField.setDisable(true);

            final String type     = typeCombo.getValue();
            final String desc     = descField.getText().trim();
            final String tags     = tagsField.getText().trim();
            final String diff     = diffCombo.getValue();
            final String duration = durationField.getText().trim();

            new Thread(() -> {
                String generatedSummary = aiController.generateResourceSummary(title, link, tags, desc);
                boolean success = courseService.addDetailedResource(
                    currentSelectedTopicId, title, link, type, desc,
                    tags, diff, duration, true, generatedSummary, AuthService.CURRENT_CHANNEL_ID);

                Platform.runLater(() -> {
                    stage.close();
                    if (success) {
                        onUploadSuccess.accept(currentSelectedTopicId);
                        Alert ok = new Alert(Alert.AlertType.INFORMATION,
                            "Resource uploaded with AI summary! ✨");
                        ok.initOwner(owner);
                        ok.show();
                    } else {
                        Alert err = new Alert(Alert.AlertType.ERROR, "Upload failed — please retry.");
                        err.initOwner(owner);
                        err.showAndWait();
                    }
                });
            }).start();
        });
    }

    // ─────────────────────────────────────────────────────────────
    // LAYOUT HELPER — label on top, control below full width
    // ─────────────────────────────────────────────────────────────
    private static VBox fieldBlock(String labelText, javafx.scene.Node ctrl) {
        Label lbl = new Label(labelText);
        lbl.setStyle("-fx-text-fill: #7b8fa8; -fx-font-size: 12px; -fx-font-weight: bold;");
        if (ctrl instanceof Region r) r.setMaxWidth(Double.MAX_VALUE);
        VBox block = new VBox(6, lbl, ctrl);
        block.setFillWidth(true);
        return block;
    }

    // ─────────────────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────────────────
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