package com.scholar.controller.ai;

import com.scholar.model.AIResource;
import com.scholar.service.AIResourceService;
import com.scholar.service.AuthService;
import com.scholar.service.SupabaseService;
import com.scholar.util.PdfGenerator;
import com.scholar.util.PopupHelper;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.layout.*;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Window;
import javafx.util.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * AIResourceControllerEnhanced
 *
 * Changes in this version:
 *  • No RichTextEditor dependency — plain TextArea injected into editorContainer
 *  • uploadFile() uses the correct 2-arg SupabaseService signature
 *  • colActions wired: View / Edit / Delete
 *  • Edit → PATCH, New → INSERT
 *  • injectClipboardBridge() called on every WebView page load so Cmd+C /
 *    Ctrl+C in the Gemini/Claude browser correctly copies to the FX clipboard
 *  • "Extract from AI" also reads the FX system clipboard as fallback
 */
@Controller
public class AIResourceControllerEnhanced {

    @Autowired private AIResourceService resourceService;
    @Autowired private SupabaseService    supabaseService;

    // Browser
    @FXML private WebView     aiWebView;
    @FXML private TextField   aiUrlField;
    @FXML private Button      aiGoBtn, aiBackBtn, aiForwardBtn, aiRefreshBtn, aiHomeBtn;
    @FXML private ProgressBar aiLoadingBar;

    // Creator form
    @FXML private TextField        resourceTitleField;
    @FXML private ComboBox<String> resourceSubjectCombo, resourceDifficultyCombo;
    @FXML private TextField        resourceTagsField;
    @FXML private TextArea         resourceDescriptionArea;
    @FXML private VBox             editorContainer;
    @FXML private Label            resourceDateLabel, resourceStatusLabel, statsLabel;
    @FXML private CheckBox         publishCheckBox;
    @FXML private Button           saveResourceBtn, generatePdfBtn, uploadToCloudBtn,
                                   clearFormBtn, copyFromAIBtn;

    // Library
    @FXML private TableView<AIResource>           resourceTable;
    @FXML private TableColumn<AIResource, String> colTitle, colSubject, colDifficulty,
                                                   colCreated, colStatus;
    @FXML private TableColumn<AIResource, Void>   colActions;
    @FXML private ComboBox<String>                filterSubjectCombo, filterDifficultyCombo;
    @FXML private TextField                       searchField;

    // State
    private TextArea   contentEditor;
    private WebEngine  webEngine;
    private AIResource currentResource;
    private File       currentPdfFile;
    private Timeline   clockTimeline;

    private final ExecutorService bg = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r); t.setDaemon(true); return t;
    });

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm");

    // ═════════════════════════════════════════════════════════════════════════
    //  INIT
    // ═════════════════════════════════════════════════════════════════════════

    @FXML
    public void initialize() {
        setupContentEditor();
        setupAIWebView();
        setupResourceForm();
        setupResourceTable();
        setupFilterControls();
        loadResourceLibrary();
        updateStats();
        startClock();
    }

    private void setupContentEditor() {
        contentEditor = new TextArea();
        contentEditor.setPromptText(
                "Paste or type content here…\n" +
                "• Supports plain text, Markdown, and LaTeX ($…$ or \\[…\\])\n" +
                "• Select text in the AI browser → click 'Extract from AI'");
        contentEditor.setWrapText(true);
        contentEditor.setStyle(
                "-fx-control-inner-background:#0d0f1a;" +
                "-fx-text-fill:#e2e8f0;" +
                "-fx-font-family:'JetBrains Mono','Fira Code','Consolas',monospace;" +
                "-fx-font-size:12px;" +
                "-fx-focus-color:#6366f1;" +
                "-fx-faint-focus-color:transparent;");
        VBox.setVgrow(contentEditor, Priority.ALWAYS);
        contentEditor.textProperty().addListener((obs, o, n) -> syncBadge(n));
        if (editorContainer != null)
            editorContainer.getChildren().setAll(contentEditor);
    }

    private void syncBadge(String text) {
        if (resourceStatusLabel == null || text == null) return;
        boolean latex = text.contains("$") || text.contains("\\[") || text.contains("\\begin");
        boolean code  = text.contains("```");
        String badge  = (latex ? " 📐 LaTeX" : "") + (code ? " 💻 Code" : "");
        resourceStatusLabel.setText(badge.isBlank() ? "Ready" : "Ready |" + badge);
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  AI BROWSER  +  CLIPBOARD FIX
    // ═════════════════════════════════════════════════════════════════════════

    private void setupAIWebView() {
        if (aiWebView == null) return;
        webEngine = aiWebView.getEngine();
        webEngine.getHistory().setMaxSize(30);
        webEngine.setJavaScriptEnabled(true);
        webEngine.setUserAgent(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36");
        webEngine.load("https://claude.ai");

        if (aiUrlField   != null) aiUrlField.setOnAction(e -> navigateTo(aiUrlField.getText()));
        if (aiGoBtn      != null) aiGoBtn.setOnAction(e -> navigateTo(aiUrlField.getText()));
        if (aiBackBtn    != null) aiBackBtn.setOnAction(e -> js("history.back()"));
        if (aiForwardBtn != null) aiForwardBtn.setOnAction(e -> js("history.forward()"));
        if (aiRefreshBtn != null) aiRefreshBtn.setOnAction(e -> webEngine.reload());
        if (aiHomeBtn    != null) aiHomeBtn.setOnAction(e -> webEngine.load("https://claude.ai"));

        webEngine.locationProperty().addListener((obs, o, loc) -> {
            if (aiUrlField != null) aiUrlField.setText(loc);
        });

        webEngine.getLoadWorker().stateProperty().addListener((obs, old, state) -> {
            if (aiLoadingBar != null) aiLoadingBar.setVisible(state == Worker.State.RUNNING);
            if (state == Worker.State.SUCCEEDED) {
                // ← KEY FIX: inject clipboard bridge on EVERY page load
                LaTeXRendererController.injectClipboardBridge(webEngine);
            }
        });
    }

    private void navigateTo(String url) {
        if (url == null || url.isBlank()) return;
        if (!url.startsWith("http")) url = "https://" + url;
        webEngine.load(url);
    }

    private void js(String script) {
        try { webEngine.executeScript(script); } catch (Exception ignored) {}
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  RESOURCE FORM
    // ═════════════════════════════════════════════════════════════════════════

    private void setupResourceForm() {
        if (resourceSubjectCombo != null) {
            resourceSubjectCombo.getItems().addAll(
                    "Mathematics","Physics","Chemistry","Biology",
                    "Computer Science","English","History","General");
            resourceSubjectCombo.setValue("General");
        }
        if (resourceDifficultyCombo != null) {
            resourceDifficultyCombo.getItems().addAll("EASY","MEDIUM","HARD");
            resourceDifficultyCombo.setValue("MEDIUM");
        }
        if (copyFromAIBtn    != null) copyFromAIBtn.setOnAction(e -> extractFromAI());
        if (saveResourceBtn  != null) saveResourceBtn.setOnAction(e -> saveResource());
        if (generatePdfBtn   != null) generatePdfBtn.setOnAction(e -> generatePdf());
        if (uploadToCloudBtn != null) uploadToCloudBtn.setOnAction(e -> uploadToCloud());
        if (clearFormBtn     != null) clearFormBtn.setOnAction(e -> clearForm());
    }

    private void extractFromAI() {
        // 1. Try JS text selection from the WebView
        try {
            Object result = webEngine.executeScript("window.scholarCopy()");
            if (result != null && !result.toString().isBlank()) {
                contentEditor.setText(result.toString());
                setStatus("✅ Extracted from AI!");
                return;
            }
        } catch (Exception ignored) {}

        // 2. Fall back to JavaFX system clipboard (works after real Cmd+C)
        Clipboard cb = Clipboard.getSystemClipboard();
        if (cb.hasString() && !cb.getString().isBlank()) {
            contentEditor.setText(cb.getString());
            setStatus("📋 Pasted from clipboard");
        } else {
            PopupHelper.showInfo(getOwner(), "Nothing to extract",
                    "Select text in the browser, press Cmd+C, then click this button.");
        }
    }

    // ── Save ─────────────────────────────────────────────────────────────────

    private void saveResource() {
        String title   = resourceTitleField != null ? resourceTitleField.getText().trim() : "";
        String content = contentEditor      != null ? contentEditor.getText().trim()      : "";
        if (title.isEmpty())   { PopupHelper.showInfo(getOwner(), "Validation", "Title required.");   return; }
        if (content.isEmpty()) { PopupHelper.showInfo(getOwner(), "Validation", "Content required."); return; }

        setStatus("💾 Saving…");
        AIResource res = buildResource(title, content);

        bg.submit(() -> {
            boolean ok;
            if (currentResource != null && currentResource.getId() > 0) {
                res.setId(currentResource.getId());
                res.setSupabaseFileUrl(currentResource.getSupabaseFileUrl());
                res.setSupabaseFileId(currentResource.getSupabaseFileId());
                ok = resourceService.updateResource(res);
            } else {
                ok = resourceService.saveResource(res);
            }
            Platform.runLater(() -> {
                if (ok) {
                    currentResource = res;
                    loadResourceLibrary(); updateStats();
                    setStatus("✅ Saved — id: " + res.getId());
                    PopupHelper.showInfo(getOwner(), "Saved ✅", "\"" + res.getTitle() + "\" saved.");
                } else {
                    setStatus("❌ Save failed");
                    PopupHelper.showError(getOwner(), "Save Failed",
                            "Could not save to Supabase.\nCheck RLS policies and credentials.");
                }
            });
        });
    }

    private AIResource buildResource(String title, String content) {
        AIResource r = new AIResource();
        r.setTitle(title);
        r.setContent(content);
        r.setContentMarkdown(content);
        r.setContentFormat("markdown");
        r.setContentHtml("<pre style='white-space:pre-wrap;'>"
                + content.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;")
                + "</pre>");
        r.setResourceType("AI_LESSON");
        if (resourceDescriptionArea != null) r.setDescription(resourceDescriptionArea.getText().trim());
        r.setSubject(nvl(resourceSubjectCombo != null ? resourceSubjectCombo.getValue() : null, "General"));
        r.setDifficultyLevel(nvl(resourceDifficultyCombo != null ? resourceDifficultyCombo.getValue() : null, "MEDIUM"));
        r.setTags(resourceTagsField != null ? resourceTagsField.getText().trim() : "");
        r.setPublished(publishCheckBox != null && publishCheckBox.isSelected());
        r.setHasLatex(content.contains("$") || content.contains("\\[") || content.contains("\\begin"));
        r.setHasCodeBlocks(content.contains("```"));
        r.setCreatedByUserId(AuthService.CURRENT_USER_ID != null
                ? (UUID) AuthService.CURRENT_USER_ID : UUID.randomUUID());
        return r;
    }

    // ── Generate PDF ──────────────────────────────────────────────────────────

    private void generatePdf() {
        if (currentResource == null) {
            PopupHelper.showInfo(getOwner(), "Not Saved", "Save the resource first."); return;
        }
        setStatus("📄 Generating PDF…");
        bg.submit(() -> {
            try {
                File pdf = PdfGenerator.generatePdf(currentResource);
                Platform.runLater(() -> {
                    currentPdfFile = pdf;
                    setStatus("✅ PDF ready");
                    PopupHelper.showInfo(getOwner(), "PDF Generated ✅",
                            "Saved to:\n" + pdf.getAbsolutePath());
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    setStatus("❌ PDF error");
                    PopupHelper.showError(getOwner(), "PDF Error", e.getMessage());
                });
            }
        });
    }

    // ── Upload (2-arg uploadFile matching actual SupabaseService) ─────────────

    private void uploadToCloud() {
        if (currentResource == null) { PopupHelper.showInfo(getOwner(), "Not Saved", "Save first."); return; }
        if (currentPdfFile  == null) { PopupHelper.showInfo(getOwner(), "No PDF", "Generate PDF first."); return; }
        setStatus("☁️ Uploading…");

        String userId = AuthService.CURRENT_USER_ID != null
                ? AuthService.CURRENT_USER_ID.toString() : "anon";
        String remoteName = userId + "_" + System.currentTimeMillis() + "_" + currentPdfFile.getName();

        bg.submit(() -> {
            String url = supabaseService.uploadFile(currentPdfFile, remoteName);
            Platform.runLater(() -> {
                if (url != null && !url.isBlank()) {
                    currentResource.setSupabaseFileUrl(url);
                    currentResource.setSupabaseFileId(remoteName);
                    bg.submit(() -> {
                        resourceService.updateResource(currentResource);
                        Platform.runLater(() -> {
                            loadResourceLibrary();
                            setStatus("✅ Uploaded");
                            PopupHelper.showInfo(getOwner(), "Uploaded ✅", "URL:\n" + url);
                        });
                    });
                } else {
                    setStatus("❌ Upload failed");
                    PopupHelper.showError(getOwner(), "Upload Failed",
                            "Check bucket 'ai-resources' is Public and service_role key is set.");
                }
            });
        });
    }

    private void clearForm() {
        if (resourceTitleField      != null) resourceTitleField.clear();
        if (resourceTagsField       != null) resourceTagsField.clear();
        if (resourceDescriptionArea != null) resourceDescriptionArea.clear();
        if (contentEditor           != null) contentEditor.clear();
        if (publishCheckBox         != null) publishCheckBox.setSelected(false);
        if (resourceSubjectCombo    != null) resourceSubjectCombo.setValue("General");
        if (resourceDifficultyCombo != null) resourceDifficultyCombo.setValue("MEDIUM");
        currentResource = null; currentPdfFile = null;
        setStatus("Ready");
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  TABLE
    // ═════════════════════════════════════════════════════════════════════════

    private void setupResourceTable() {
        if (resourceTable == null) return;
        colTitle     .setCellValueFactory(d -> sp(d.getValue().getTitle()));
        colSubject   .setCellValueFactory(d -> sp(nvl(d.getValue().getSubject(), "—")));
        colDifficulty.setCellValueFactory(d -> sp(nvl(d.getValue().getDifficultyLevel(), "—")));
        colCreated   .setCellValueFactory(d -> sp(d.getValue().getFormattedCreatedAt()));
        colStatus    .setCellValueFactory(d -> sp(d.getValue().isPublished() ? "📢 Published" : "📝 Draft"));

        if (colActions != null) {
            colActions.setCellFactory(col -> new TableCell<>() {
                private final Button vBtn = mkBtn("👁","#6366f1","View");
                private final Button eBtn = mkBtn("✏️","#0ea5e9","Edit");
                private final Button dBtn = mkBtn("🗑","#ef4444","Delete");
                private final HBox   box  = new HBox(4, vBtn, eBtn, dBtn);
                { box.setPadding(new Insets(2));
                  vBtn.setOnAction(e -> { if (!isEmpty()) showDetail(item()); });
                  eBtn.setOnAction(e -> { if (!isEmpty()) loadIntoForm(item()); });
                  dBtn.setOnAction(e -> { if (!isEmpty()) confirmDelete(item()); }); }
                private AIResource item() { return getTableView().getItems().get(getIndex()); }
                @Override protected void updateItem(Void v, boolean empty) {
                    super.updateItem(v, empty); setGraphic(empty ? null : box);
                }
            });
        }
        resourceTable.setRowFactory(tv -> {
            TableRow<AIResource> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) loadIntoForm(row.getItem());
            });
            return row;
        });
    }

    private Button mkBtn(String emoji, String color, String tip) {
        Button b = new Button(emoji);
        b.setTooltip(new Tooltip(tip));
        b.setStyle("-fx-background-color:"+color+"22;-fx-text-fill:"+color+
                   ";-fx-border-color:"+color+"55;-fx-border-radius:5;-fx-background-radius:5;" +
                   "-fx-padding:3 7;-fx-font-size:12px;-fx-cursor:hand;");
        return b;
    }

    private void showDetail(AIResource r) {
        PopupHelper.showInfo(getOwner(), "📄 " + r.getTitle(),
                "Subject: "    + nvl(r.getSubject(), "—")         + "\n" +
                "Difficulty: " + nvl(r.getDifficultyLevel(), "—") + "\n" +
                "Created: "    + r.getFormattedCreatedAt()         + "\n" +
                "Views: "      + r.getViewCount()                  +
                (r.getSupabaseFileUrl() != null ? "\n\n☁️ " + r.getSupabaseFileUrl() : ""));
    }

    private void loadIntoForm(AIResource r) {
        currentResource = r;
        if (resourceTitleField      != null) resourceTitleField.setText(nvl(r.getTitle(), ""));
        if (resourceTagsField       != null) resourceTagsField .setText(nvl(r.getTags(), ""));
        if (resourceDescriptionArea != null) resourceDescriptionArea.setText(nvl(r.getDescription(), ""));
        if (resourceSubjectCombo    != null) resourceSubjectCombo.setValue(nvl(r.getSubject(), "General"));
        if (resourceDifficultyCombo != null) resourceDifficultyCombo.setValue(nvl(r.getDifficultyLevel(), "MEDIUM"));
        if (publishCheckBox         != null) publishCheckBox.setSelected(r.isPublished());
        String body = r.getContentMarkdown() != null ? r.getContentMarkdown()
                    : r.getContent()         != null ? r.getContent() : "";
        if (contentEditor != null) contentEditor.setText(body);
        setStatus("✏️ Editing: " + r.getTitle());
    }

    private void confirmDelete(AIResource r) {
        PopupHelper.showConfirm(getOwner(), "Delete", "Delete \"" + r.getTitle() + "\"?",
                () -> bg.submit(() -> {
                    boolean ok = resourceService.deleteResource(r.getId());
                    Platform.runLater(() -> {
                        if (ok) { if (currentResource != null && currentResource.getId() == r.getId()) clearForm();
                            loadResourceLibrary(); updateStats(); setStatus("🗑 Deleted");
                        } else PopupHelper.showError(getOwner(), "Delete Failed", "Could not delete.");
                    });
                }));
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  FILTERS / SEARCH / STATS / CLOCK
    // ═════════════════════════════════════════════════════════════════════════

    private void setupFilterControls() {
        if (filterSubjectCombo != null) {
            filterSubjectCombo.getItems().addAll("All","Mathematics","Physics","Computer Science","Biology","Chemistry","English","History","General");
            filterSubjectCombo.setValue("All");
            filterSubjectCombo.setOnAction(e -> loadResourceLibrary());
        }
        if (filterDifficultyCombo != null) {
            filterDifficultyCombo.getItems().addAll("All","EASY","MEDIUM","HARD");
            filterDifficultyCombo.setValue("All");
            filterDifficultyCombo.setOnAction(e -> loadResourceLibrary());
        }
        if (searchField != null) {
            PauseTransition d = new PauseTransition(Duration.millis(350));
            searchField.textProperty().addListener((obs, o, n) -> {
                d.setOnFinished(ev -> loadResourceLibrary()); d.playFromStart();
            });
        }
    }

    private void loadResourceLibrary() {
        String s = filterSubjectCombo    != null ? filterSubjectCombo   .getValue() : "All";
        String d = filterDifficultyCombo != null ? filterDifficultyCombo.getValue() : "All";
        String q = searchField           != null ? searchField          .getText()  : "";
        bg.submit(() -> {
            List<AIResource> list = resourceService.getAllResources(s, d, q);
            Platform.runLater(() -> { if (resourceTable != null) resourceTable.getItems().setAll(list); });
        });
    }

    private void updateStats() {
        if (statsLabel == null) return;
        bg.submit(() -> {
            AIResourceService.ResourceStats st = resourceService.getStatistics();
            Platform.runLater(() -> statsLabel.setText(
                    "📚 " + st.totalResources + " Resources  |  👁 " + st.totalViews + " Views"));
        });
    }

    private void startClock() {
        if (clockTimeline != null) clockTimeline.stop();
        clockTimeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            if (resourceDateLabel != null)
                resourceDateLabel.setText(LocalDateTime.now().format(DATE_FMT));
        }));
        clockTimeline.setCycleCount(Timeline.INDEFINITE);
        clockTimeline.play();
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  HELPERS
    // ═════════════════════════════════════════════════════════════════════════

    private void setStatus(String t) { if (resourceStatusLabel != null) resourceStatusLabel.setText(t); }

    private Window getOwner() {
        if (aiWebView     != null && aiWebView    .getScene() != null) return aiWebView    .getScene().getWindow();
        if (resourceTable != null && resourceTable.getScene() != null) return resourceTable.getScene().getWindow();
        return null;
    }

    private static String nvl(String s, String fb) { return (s == null || s.isBlank()) ? fb : s; }

    private static javafx.beans.property.SimpleStringProperty sp(String s) {
        return new javafx.beans.property.SimpleStringProperty(s);
    }
}