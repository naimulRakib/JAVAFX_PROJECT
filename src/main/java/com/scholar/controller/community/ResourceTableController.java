package com.scholar.controller.community;

import com.scholar.model.ResourceRow;
import com.scholar.service.AuthService;
import com.scholar.service.CourseService;
import com.scholar.util.DriveHelper;
import com.scholar.util.PopupHelper;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * RESOURCE TABLE CONTROLLER — Card-based layout (replaces boring TableView)
 * Path: src/main/java/com/scholar/controller/community/ResourceTableController.java
 *
 * The TableView is kept in memory for API compatibility with DashboardController,
 * but is swapped out of the scene graph for a modern card ScrollPane on init.
 */
@Component
public class ResourceTableController {

    @Autowired private CourseService courseService;
    @Autowired private DriveHelper   driveHelper;

    // Kept for DashboardController.init() signature compatibility
    private TableView<ResourceRow>            resourceTable;
    private TableColumn<ResourceRow, String>  colResName, colResType, colResDiff,
                                               colResUploader, colResTags, colResVotes;
    private TableColumn<ResourceRow, Void>    colResAction;

    private ComboBox<String>   typeFilterCombo, diffFilterCombo, sortCombo;
    private Label              statusBarLabel, loadingLabel, resourceCountLabel;
    private ProgressIndicator  loadingIndicator;

    private Integer currentSelectedTopicId;
    private java.util.function.Consumer<Integer> onTopicReload;

    private DiscussionController discussionController;
    private StatisticsController statisticsController;

    private List<ResourceRow> allRows = new ArrayList<>();

    // ── Card view ─────────────────────────────────────────────────
    private VBox      cardContainer;
    private ScrollPane cardScroll;

    // ─────────────────────────────────────────────────────────────
    // INIT
    // ─────────────────────────────────────────────────────────────
    public void init(TableView<ResourceRow>           resourceTable,
                     TableColumn<ResourceRow, String> colResName,
                     TableColumn<ResourceRow, String> colResType,
                     TableColumn<ResourceRow, String> colResDiff,
                     TableColumn<ResourceRow, String> colResUploader,
                     TableColumn<ResourceRow, String> colResTags,
                     TableColumn<ResourceRow, String> colResVotes,
                     TableColumn<ResourceRow, Void>   colResAction,
                     DiscussionController             discussionController,
                     StatisticsController             statisticsController) {

        this.resourceTable        = resourceTable;
        this.colResName           = colResName;
        this.colResType           = colResType;
        this.colResDiff           = colResDiff;
        this.colResUploader       = colResUploader;
        this.colResTags           = colResTags;
        this.colResVotes          = colResVotes;
        this.colResAction         = colResAction;
        this.discussionController = discussionController;
        this.statisticsController = statisticsController;
        this.onTopicReload        = this::loadResourcesForTopic;

        injectCardView();
    }

    /**
     * Swaps the TableView out of its parent and injects a dark card ScrollPane.
     */
    private void injectCardView() {
        if (resourceTable == null) return;

        // Build card container
        cardContainer = new VBox(10);
        cardContainer.setPadding(new Insets(16));
        cardContainer.setStyle("-fx-background-color: #0d1117;");

        cardScroll = new ScrollPane(cardContainer);
        cardScroll.setFitToWidth(true);
        cardScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        cardScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        cardScroll.setStyle(
            "-fx-background: #0d1117; -fx-background-color: #0d1117; -fx-border-color: transparent;");
        cardScroll.skinProperty().addListener((obs, o, n) -> {
            javafx.scene.Node vp = cardScroll.lookup(".viewport");
            if (vp != null) vp.setStyle("-fx-background-color: #0d1117;");
        });

        // Replace TableView in its parent
        javafx.scene.Parent parent = resourceTable.getParent();
        if (parent instanceof Pane p) {
            int idx = p.getChildren().indexOf(resourceTable);
            if (idx >= 0) {
                p.getChildren().set(idx, cardScroll);
                if (parent instanceof VBox)       VBox.setVgrow(cardScroll, Priority.ALWAYS);
                if (parent instanceof HBox)       HBox.setHgrow(cardScroll, Priority.ALWAYS);
                if (parent instanceof AnchorPane ap) {
                    Double t = AnchorPane.getTopAnchor(resourceTable);
                    Double b = AnchorPane.getBottomAnchor(resourceTable);
                    Double l = AnchorPane.getLeftAnchor(resourceTable);
                    Double r = AnchorPane.getRightAnchor(resourceTable);
                    if (t != null) AnchorPane.setTopAnchor(cardScroll, t);
                    if (b != null) AnchorPane.setBottomAnchor(cardScroll, b);
                    if (l != null) AnchorPane.setLeftAnchor(cardScroll, l);
                    if (r != null) AnchorPane.setRightAnchor(cardScroll, r);
                }
            }
        }

        showEmptyState("Select a topic from the left panel to view resources.");
    }

    public void initExtras(ComboBox<String>   typeFilterCombo,
                           ComboBox<String>   diffFilterCombo,
                           ComboBox<String>   sortCombo,
                           Label              statusBarLabel,
                           Label              loadingLabel,
                           ProgressIndicator  loadingIndicator,
                           Label              resourceCountLabel) {

        this.typeFilterCombo    = typeFilterCombo;
        this.diffFilterCombo    = diffFilterCombo;
        this.sortCombo          = sortCombo;
        this.statusBarLabel     = statusBarLabel;
        this.loadingLabel       = loadingLabel;
        this.loadingIndicator   = loadingIndicator;
        this.resourceCountLabel = resourceCountLabel;

        if (typeFilterCombo != null) {
            typeFilterCombo.getItems().addAll("All", "LINK", "PDF", "Video", "Note");
            typeFilterCombo.setValue("All");
            typeFilterCombo.setOnAction(e -> reapplyFilters());
        }
        if (diffFilterCombo != null) {
            diffFilterCombo.getItems().addAll("All", "Easy", "Medium", "Hard");
            diffFilterCombo.setValue("All");
            diffFilterCombo.setOnAction(e -> reapplyFilters());
        }
        if (sortCombo != null) {
            sortCombo.getItems().addAll("Default", "Most Upvotes", "Least Upvotes", "Name A-Z");
            sortCombo.setValue("Default");
            sortCombo.setOnAction(e -> reapplyFilters());
        }
    }

    public void setCurrentTopicId(Integer topicId) { this.currentSelectedTopicId = topicId; }
    public Integer getCurrentTopicId()              { return currentSelectedTopicId; }

    // ─────────────────────────────────────────────────────────────
    // CARD RENDERING
    // ─────────────────────────────────────────────────────────────
    private void renderCards(List<ResourceRow> rows) {
        if (cardContainer == null) return;
        cardContainer.getChildren().clear();

        if (rows.isEmpty()) {
            showEmptyState("No resources found. Upload the first one! 🚀");
            return;
        }

        for (ResourceRow row : rows)
            cardContainer.getChildren().add(buildCard(row));
    }

    /**
     * Builds a single resource card:
     *
     * ┌──────────────────────────────────────────────────────────┐
     * │  [🔗 LINK]  [Medium]   by admin        🏷 good  👍1 👎0  │
     * │  Resource Name — large bold title                        │
     * │  ────────────────────────────────────────────────────── │
     * │  [👁 Preview] [⬇ Download] [👍 Up] [👎 Down]            │
     * │  [✅ Done]   [📊 Stats]   [💬 Discuss]  [✏️Edit][🗑Del] │
     * └──────────────────────────────────────────────────────────┘
     */
    private VBox buildCard(ResourceRow row) {
        CourseService.Resource res = row.getRawResource();

        // ── Card shell ────────────────────────────────────────────
        VBox card = new VBox(10);
        card.setPadding(new Insets(16, 20, 16, 20));
        card.setStyle(cardStyle(false));
        card.setOnMouseEntered(e -> card.setStyle(cardStyle(true)));
        card.setOnMouseExited(e  -> card.setStyle(cardStyle(false)));

        // ── Row 1: type pill | diff badge | uploader | tags | votes
        Label typePill  = typePill(row.getType());
        Label diffBadge = diffBadge(row.getDiff());

        Label uploaderLbl = new Label("by " + row.getUploader());
        uploaderLbl.setStyle("-fx-text-fill: #4a5a72; -fx-font-size: 11px;");

        Label tagsLbl = new Label("🏷 " + row.getTags());
        tagsLbl.setStyle("-fx-text-fill: #4a5a72; -fx-font-size: 11px;");

        Label votesLbl = new Label("👍 " + res.upvotes() + "   👎 " + res.downvotes());
        votesLbl.setStyle("-fx-text-fill: #60a5fa; -fx-font-size: 11px; -fx-font-weight: bold;");

        Region sp1 = new Region();
        HBox.setHgrow(sp1, Priority.ALWAYS);

        HBox metaRow = new HBox(8, typePill, diffBadge, uploaderLbl, sp1, tagsLbl, votesLbl);
        metaRow.setAlignment(Pos.CENTER_LEFT);

        // ── Row 2: resource title ─────────────────────────────────
        Label nameLbl = new Label(row.getName());
        nameLbl.setStyle(
            "-fx-text-fill: #e2e8f0; -fx-font-size: 15px; -fx-font-weight: bold;");
        nameLbl.setWrapText(true);

        // ── Divider ───────────────────────────────────────────────
        Separator sep = new Separator();
        sep.setStyle("-fx-opacity: 0.12;");

        // ── Row 3: action buttons ─────────────────────────────────
        boolean voted     = res.hasUserVoted();
        boolean completed = res.isCompleted();

        Button previewBtn  = cardBtn("👁  Preview",  "#2c3e50");
        Button downloadBtn = cardBtn("⬇  Download", "#16a085");
        Button upBtn       = cardBtn("👍  Upvote",   "#27ae60");
        Button downBtn     = cardBtn("👎  Downvote", "#e74c3c");
        Button doneBtn     = cardBtn(
            completed ? "✅  Edit Note"   : "✅  Mark Done",
            completed ? "#16a085"         : "#2980b9");
        Button statsBtn    = cardBtn("📊  Stats",    "#8e44ad");
        Button discussBtn  = cardBtn("💬  Discuss",  "#34495e");

        upBtn.setDisable(voted);
        downBtn.setDisable(voted);

        previewBtn.setOnAction(e  -> driveHelper.showInAppPreview(res.link(), res.title()));
        downloadBtn.setOnAction(e -> driveHelper.directDownloadFile(res.link(), res.title()));
        upBtn.setOnAction(e   -> processVote(row, 1));
        downBtn.setOnAction(e -> processVote(row, -1));
        
        // Fix: Simply use resolveWindow() directly for the Window argument
        doneBtn.setOnAction(e ->
            statisticsController.showResourceCompletionDialog(
                row, currentSelectedTopicId, onTopicReload, resolveWindow()));
                
        statsBtn.setOnAction(e ->
            statisticsController.showStatisticsDialog(res, resolveWindow()));
        discussBtn.setOnAction(e ->
            discussionController.showDiscussionPanel(res, resolveWindow()));

        HBox actionRow = new HBox(6, previewBtn, downloadBtn, upBtn, downBtn,
                                     doneBtn, statsBtn, discussBtn);
        actionRow.setAlignment(Pos.CENTER_LEFT);

        // Admin / owner buttons
        boolean canManage = "admin".equals(AuthService.CURRENT_USER_ROLE)
            || (res.creatorId() != null && res.creatorId().equals(AuthService.CURRENT_USER_ID));
        if (canManage) {
            Button editBtn = cardBtn("✏️  Edit",   "#f39c12");
            Button delBtn  = cardBtn("🗑  Delete", "#c0392b");
            editBtn.setOnAction(e -> showEditResourceDialog(res));
            delBtn.setOnAction(e  -> showDeleteConfirm(row, resolveWindow()));
            actionRow.getChildren().addAll(editBtn, delBtn);
        }

        card.getChildren().addAll(metaRow, nameLbl, sep, actionRow);
        return card;
    }

    // ─────────────────────────────────────────────────────────────
    // EMPTY STATE
    // ─────────────────────────────────────────────────────────────
    private void showEmptyState(String message) {
        if (cardContainer == null) return;
        cardContainer.getChildren().clear();
        Label icon = new Label("📂");
        icon.setStyle("-fx-font-size: 42px;");
        Label lbl = new Label(message);
        lbl.setStyle("-fx-text-fill: #4a5a72; -fx-font-size: 13px;");
        lbl.setWrapText(true);
        VBox empty = new VBox(12, icon, lbl);
        empty.setAlignment(Pos.CENTER);
        empty.setPadding(new Insets(60));
        cardContainer.getChildren().add(empty);
    }

    // ─────────────────────────────────────────────────────────────
    // DELETE CONFIRM
    // ─────────────────────────────────────────────────────────────
    private void showDeleteConfirm(ResourceRow row, Window owner) {
        Label icon    = new Label("🗑️");
        icon.setStyle("-fx-font-size: 32px;");
        Label msg     = new Label("Delete this resource?");
        msg.setStyle("-fx-text-fill: #e2e8f0; -fx-font-size: 15px; -fx-font-weight: bold;");
        Label sub     = new Label(row.getName());
        sub.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 12px;");
        sub.setWrapText(true);

        Button cancelBtn  = actionBtn("Cancel", "#1e2736", "#94a3b8");
        Button confirmBtn = actionBtn("Delete", "#c0392b", "white");
        confirmBtn.setStyle(confirmBtn.getStyle() + " -fx-font-weight: bold;");

        Separator sep = new Separator();
        sep.setStyle("-fx-opacity: 0.2;");
        HBox btnRow = new HBox(12, cancelBtn, confirmBtn);
        btnRow.setAlignment(Pos.CENTER_RIGHT);

        VBox root = new VBox(16, icon, msg, sub, sep, btnRow);
        root.setPadding(new Insets(28));
        root.setAlignment(Pos.CENTER_LEFT);
        root.setStyle("-fx-background-color: #161b27;");

        Stage stage = PopupHelper.create(owner, "Confirm Delete", root, 340, 220, 380, 240);
        stage.show();
        cancelBtn.setOnAction(e -> stage.close());
        confirmBtn.setOnAction(e -> {
            stage.close();
            new Thread(() -> {
                if (courseService.deleteResource(row.getRawResource().id()))
                    Platform.runLater(() -> loadResourcesForTopic(currentSelectedTopicId));
            }).start();
        });
    }

    // ─────────────────────────────────────────────────────────────
    // LOAD RESOURCES
    // ─────────────────────────────────────────────────────────────
    public void loadResourcesForTopic(Integer topicId) {
        if (topicId == null) {
            allRows.clear();
            showEmptyState("Select a topic from the left panel to view resources.");
            return;
        }
        this.currentSelectedTopicId = topicId;
        setLoading(true);

        new Thread(() -> {
            List<CourseService.Resource> resources = courseService.getResources(topicId);
            Platform.runLater(() -> {
                List<ResourceRow> rows = new ArrayList<>();
                for (var res : resources) {
                    String uploader = res.creatorName() != null ? res.creatorName() : "Student";
                    String type     = res.type()        != null ? res.type()        : "LINK";
                    String diff     = res.difficulty()  != null ? res.difficulty()  : "Medium";
                    String tags     = res.tags()        != null ? res.tags()        : "General";
                    String votes    = "Up: " + res.upvotes() + " | Down: " + res.downvotes();
                    rows.add(new ResourceRow(res.title(), type, diff, uploader, tags, votes, res));
                }
                allRows = rows;
                applyFiltersAndSort(rows);
                setLoading(false);
                updateCountBadge();
            });
        }).start();
    }

    // ─────────────────────────────────────────────────────────────
    // FILTER / SORT / SEARCH
    // ─────────────────────────────────────────────────────────────
    private void reapplyFilters() { if (!allRows.isEmpty()) applyFiltersAndSort(allRows); }

    private void applyFiltersAndSort(List<ResourceRow> source) {
        String typeF = typeFilterCombo != null ? typeFilterCombo.getValue() : "All";
        String diffF = diffFilterCombo != null ? diffFilterCombo.getValue() : "All";
        String sortV = sortCombo       != null ? sortCombo.getValue()       : "Default";

        List<ResourceRow> filtered = new ArrayList<>(source);
        if (typeF != null && !typeF.equals("All"))
            filtered.removeIf(r -> !r.getType().equalsIgnoreCase(typeF));
        if (diffF != null && !diffF.equals("All"))
            filtered.removeIf(r -> !r.getDiff().equalsIgnoreCase(diffF));

        if (sortV != null) {
            if ("Most Upvotes".equals(sortV))
                filtered.sort((a, b) -> upvotes(b) - upvotes(a));
            else if ("Least Upvotes".equals(sortV))
                filtered.sort((a, b) -> upvotes(a) - upvotes(b));
            else if ("Name A-Z".equals(sortV))
                filtered.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        }

        renderCards(filtered);
        updateCountBadge();
    }

    public void filterByText(String lower) {
        List<ResourceRow> filtered = allRows.stream()
            .filter(r -> r.getName().toLowerCase().contains(lower)
                      || r.getTags().toLowerCase().contains(lower)
                      || r.getUploader().toLowerCase().contains(lower))
            .collect(Collectors.toList());
        renderCards(filtered);
        updateCountBadge();
    }

    public void clearSearch() { applyFiltersAndSort(allRows); }

    private int upvotes(ResourceRow r) {
        try { return r.getRawResource().upvotes(); } catch (Exception e) { return 0; }
    }

    // ─────────────────────────────────────────────────────────────
    // VOTE
    // ─────────────────────────────────────────────────────────────
    private void processVote(ResourceRow row, int voteType) {
        new Thread(() -> {
            boolean success = courseService.submitVote(row.getRawResource().id(), voteType);
            Platform.runLater(() -> {
                if (success) loadResourcesForTopic(currentSelectedTopicId);
                else System.err.println("Vote failed.");
            });
        }).start();
    }

    // ─────────────────────────────────────────────────────────────
    // EDIT RESOURCE
    // ─────────────────────────────────────────────────────────────
    private void showEditResourceDialog(CourseService.Resource res) {
        TextField        titleField    = darkField(res.title());
        TextField        linkField     = darkField(res.link());
        ComboBox<String> typeCombo     = darkCombo("LINK", "PDF", "Video", "Note");
        typeCombo.setValue(res.type() != null ? res.type() : "LINK");
        ComboBox<String> diffCombo     = darkCombo("Easy", "Medium", "Hard");
        diffCombo.setValue(res.difficulty() != null ? res.difficulty() : "Medium");
        TextField        durationField = darkField(res.duration()   != null ? res.duration()   : "");
        TextField        tagsField     = darkField(res.tags()       != null ? res.tags()       : "");
        TextArea         descField     = darkArea(res.description() != null ? res.description(): "");
        descField.setPrefRowCount(3);

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

        Button cancelBtn = actionBtn("Cancel",          "#1e2736", "#94a3b8");
        Button saveBtn   = actionBtn("Update Resource", "#10b981", "white");
        saveBtn.setStyle(saveBtn.getStyle() + " -fx-font-weight: bold;");

        HBox btnRow = new HBox(12, cancelBtn, saveBtn);
        btnRow.setAlignment(Pos.CENTER_RIGHT);
        btnRow.setPadding(new Insets(12, 24, 20, 24));
        btnRow.setStyle("-fx-background-color: #161b27;");

        VBox root = new VBox(formScroll, btnRow);
        root.setStyle("-fx-background-color: #161b27;");

        Stage stage = PopupHelper.create(resolveWindow(),
            "✏️ Edit Resource — " + res.title(), root, 440, 540, 500, 600);
        stage.show();

        cancelBtn.setOnAction(e -> stage.close());
        saveBtn.setOnAction(e -> {
            if (titleField.getText().isBlank() || linkField.getText().isBlank()) return;
            stage.close();
            new Thread(() -> {
                boolean ok = courseService.updateResource(
                    res.id(), titleField.getText().trim(), linkField.getText().trim(),
                    typeCombo.getValue(), descField.getText().trim(),
                    tagsField.getText().trim(), diffCombo.getValue(), durationField.getText().trim());
                Platform.runLater(() -> { if (ok) loadResourcesForTopic(currentSelectedTopicId); });
            }).start();
        });
    }

    // ─────────────────────────────────────────────────────────────
    // STATUS / LOADING
    // ─────────────────────────────────────────────────────────────
    private void setLoading(boolean on) {
        if (loadingIndicator != null) loadingIndicator.setVisible(on);
        if (loadingLabel     != null) loadingLabel.setVisible(on);
        if (statusBarLabel   != null) statusBarLabel.setText(on ? "Loading..." : "Ready");
        if (cardContainer != null && on) {
            cardContainer.getChildren().clear();
            Label lbl = new Label("⏳  Loading resources…");
            lbl.setStyle("-fx-text-fill: #4a5a72; -fx-font-size: 13px;");
            VBox loading = new VBox(lbl);
            loading.setAlignment(Pos.CENTER);
            loading.setPadding(new Insets(60));
            cardContainer.getChildren().add(loading);
        }
    }

    private void updateCountBadge() {
        if (resourceCountLabel != null)
            resourceCountLabel.setText(allRows.size() + " resource(s)");
    }

    private Window resolveWindow() {
        return cardScroll != null && cardScroll.getScene() != null
            ? cardScroll.getScene().getWindow() : null;
    }

    // ─────────────────────────────────────────────────────────────
    // LAYOUT HELPER
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
    // STYLE HELPERS
    // ─────────────────────────────────────────────────────────────
    private static String cardStyle(boolean hovered) {
        return "-fx-background-color: " + (hovered ? "#1a2236" : "#161b27") + "; "
            + "-fx-background-radius: 12; "
            + "-fx-border-color: "      + (hovered ? "#3b82f6" : "#1e2736") + "; "
            + "-fx-border-width: 1; "
            + "-fx-border-radius: 12; "
            + "-fx-effect: dropshadow(three-pass-box, "
            + (hovered ? "rgba(59,130,246,0.2)" : "rgba(0,0,0,0.3)")
            + ", 10, 0, 0, 2);";
    }

    private static Label typePill(String type) {
        String emoji = switch (type) {
            case "PDF"   -> "📄";
            case "Video" -> "🎬";
            case "Note"  -> "📝";
            default      -> "🔗";
        };
        String color = switch (type) {
            case "PDF"   -> "-fx-background-color: #1e3a5f; -fx-text-fill: #7dd3fc;";
            case "Video" -> "-fx-background-color: #3b1f5e; -fx-text-fill: #c4b5fd;";
            case "Note"  -> "-fx-background-color: #1f3b2e; -fx-text-fill: #6ee7b7;";
            default      -> "-fx-background-color: #1e2f50; -fx-text-fill: #93c5fd;";
        };
        Label l = new Label(emoji + "  " + type);
        l.setStyle(color
            + " -fx-background-radius: 20; -fx-padding: 3 10; "
            + "-fx-font-size: 11px; -fx-font-weight: bold;");
        return l;
    }

    private static Label diffBadge(String diff) {
        String style = switch (diff) {
            case "Easy" -> "-fx-background-color: #14532d; -fx-text-fill: #4ade80;";
            case "Hard" -> "-fx-background-color: #7f1d1d; -fx-text-fill: #f87171;";
            default     -> "-fx-background-color: #713f12; -fx-text-fill: #fbbf24;";
        };
        Label l = new Label(diff);
        l.setStyle(style
            + " -fx-background-radius: 20; -fx-padding: 3 10; "
            + "-fx-font-size: 11px; -fx-font-weight: bold;");
        return l;
    }

    private static Button cardBtn(String label, String color) {
        Button b = new Button(label);
        b.setMinWidth(Region.USE_PREF_SIZE);
        b.setMinHeight(30);
        b.setStyle("-fx-background-color: " + color + "; -fx-text-fill: white; "
            + "-fx-background-radius: 8; -fx-padding: 6 14; "
            + "-fx-cursor: hand; -fx-font-size: 11px; -fx-font-weight: bold;");
        return b;
    }

    private static Button actionBtn(String text, String bg, String fg) {
        Button b = new Button(text);
        b.setStyle("-fx-background-color: " + bg + "; -fx-text-fill: " + fg + "; "
            + "-fx-background-radius: 10; -fx-padding: 10 24; -fx-cursor: hand;");
        return b;
    }

    private static TextField darkField(String val) {
        TextField f = new TextField(val);
        f.setStyle("-fx-background-color: #1a2236; -fx-text-fill: #e2e8f0; "
            + "-fx-border-color: #2c3a52; -fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 8 12;");
        f.setMaxWidth(Double.MAX_VALUE);
        return f;
    }

    private static TextArea darkArea(String val) {
        TextArea a = new TextArea(val);
        a.setStyle("-fx-background-color: #1a2236; -fx-text-fill: #e2e8f0; "
            + "-fx-border-color: #2c3a52; -fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 8 12;");
        a.setWrapText(true);
        a.setMaxWidth(Double.MAX_VALUE);
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