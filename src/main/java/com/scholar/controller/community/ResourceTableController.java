package com.scholar.controller.community;

import com.scholar.model.ResourceRow;
import com.scholar.service.AuthService;
import com.scholar.service.CourseService;
import com.scholar.util.DriveHelper;
import com.scholar.util.SPopupHelper;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class ResourceTableController {

    @Autowired private CourseService courseService;
    @Autowired private DriveHelper   driveHelper;

    private TableView<ResourceRow>           resourceTable;
    private TableColumn<ResourceRow, String> colResName, colResType, colResDiff,
                                              colResUploader, colResTags, colResVotes;
    private TableColumn<ResourceRow, Void>   colResAction;

    private ComboBox<String>  typeFilterCombo, diffFilterCombo, sortCombo;
    private Label             statusBarLabel, loadingLabel, resourceCountLabel;
    private ProgressIndicator loadingIndicator;

    private Integer currentSelectedTopicId;
    private java.util.function.Consumer<Integer> onTopicReload;

    private DiscussionController discussionController;
    private StatisticsController statisticsController;

    private List<ResourceRow> allRows = new ArrayList<>();
    private VBox       cardContainer;
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
        
        setupTableViewFallback(); 
        injectCardView();
    }

    // 🌟 THE ULTIMATE FALLBACK: Bulletproof TableView Mapping
    private void setupTableViewFallback() {
        if (resourceTable == null) return;
        if (colResName != null) colResName.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getName()));
        if (colResType != null) colResType.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getType()));
        if (colResDiff != null) colResDiff.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getDiff()));
        if (colResUploader != null) colResUploader.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getUploader()));
        if (colResTags != null) colResTags.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getTags()));
        if (colResVotes != null) colResVotes.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getVotes()));
        
        if (colResAction != null) {
            colResAction.setCellFactory(col -> new TableCell<>() {
                private final Button btn = new Button("👁 Preview");
                {
                    btn.setStyle("-fx-background-color: #2c3e50; -fx-text-fill: white; -fx-cursor: hand;");
                    btn.setOnAction(e -> {
                        ResourceRow row = getTableView().getItems().get(getIndex());
                        driveHelper.showInAppPreview(row.getRawResource().link(), row.getRawResource().title());
                    });
                }
                @Override
                protected void updateItem(Void item, boolean empty) {
                    super.updateItem(item, empty);
                    setGraphic(empty ? null : btn);
                }
            });
        }
    }

    private void injectCardView() {
        if (resourceTable == null) return;

        cardContainer = new VBox(10);
        cardContainer.setPadding(new Insets(16));
        cardContainer.setStyle("-fx-background-color: #0d1117;");

        cardScroll = new ScrollPane(cardContainer);
        cardScroll.setFitToWidth(true);
        cardScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        cardScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        cardScroll.setMaxWidth(Double.MAX_VALUE);
        cardScroll.setMaxHeight(Double.MAX_VALUE);
        
        cardScroll.setStyle(
            "-fx-background: #0d1117; -fx-background-color: #0d1117; -fx-border-color: transparent;");
        cardScroll.skinProperty().addListener((obs, o, n) -> {
            javafx.scene.Node vp = cardScroll.lookup(".viewport");
            if (vp != null) vp.setStyle("-fx-background-color: #0d1117;");
        });

        if (!tryInjectIntoParent()) {
            resourceTable.parentProperty().addListener((obs, oldP, newP) -> {
                if (newP != null) tryInjectIntoParent();
            });
        }
        showEmptyState("Select a topic from the left panel to view resources.");
    }

    // 🌟 BULLETPROOF INJECTION: Wait for Layout Engine & Disable Table instead of Removing
    private boolean tryInjectIntoParent() {
        if (resourceTable == null || cardScroll == null) return false;
        javafx.scene.Parent parent = resourceTable.getParent();
        if (parent == null) return false;

        Platform.runLater(() -> {
            try {
                if (parent instanceof VBox vBox) {
                    if (!vBox.getChildren().contains(cardScroll)) {
                        int idx = vBox.getChildren().indexOf(resourceTable);
                        if (idx >= 0) {
                            // Hide old table but keep it in the scene
                            resourceTable.setVisible(false);
                            resourceTable.setManaged(false);
                            
                            // Insert CardScroll directly in place
                            vBox.getChildren().add(idx, cardScroll);
                            VBox.setVgrow(cardScroll, Priority.ALWAYS);
                            System.out.println("✅ CardScroll successfully added to VBox and Expanded!");
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("Injection error: " + e.getMessage());
            }
        });
        return true;
    }

    public void initExtras(ComboBox<String>  typeFilterCombo, ComboBox<String>  diffFilterCombo,
                           ComboBox<String>  sortCombo, Label statusBarLabel, Label loadingLabel,
                           ProgressIndicator loadingIndicator, Label resourceCountLabel) {

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
    // LOAD RESOURCES
    // ─────────────────────────────────────────────────────────────
    public void loadResourcesForTopic(Integer topicId) {
        if (topicId == null) {
            allRows.clear();
            showEmptyState("Select a topic from the left panel to view resources.");
            return;
        }

        this.currentSelectedTopicId = topicId;
        showLoadingState();

        new Thread(() -> {
            try {
                List<CourseService.Resource> resources = courseService.getResources(topicId);

                Platform.runLater(() -> {
                    List<ResourceRow> rows = new ArrayList<>();
                    for (var res : resources) {
                        String uploader = res.creatorName() != null ? res.creatorName() : "Student";
                        String type     = res.type()        != null ? res.type().trim() : "LINK";
                        String diff     = res.difficulty()  != null ? res.difficulty()  : "Medium";
                        String tags     = res.tags()        != null ? res.tags()        : "General";
                        String votes    = "Up: " + res.upvotes() + " | Down: " + res.downvotes();
                        rows.add(new ResourceRow(res.title(), type, diff, uploader, tags, votes, res));
                    }
                    allRows = rows;
                    setLoading(false);
                    
                    if (rows.isEmpty()) {
                        showEmptyState("No resources yet for this topic.\nBe the first to upload! 🚀");
                        if (resourceTable != null) resourceTable.getItems().clear();
                    } else {
                        applyFiltersAndSort(rows);
                    }
                    updateCountBadge();
                });

            } catch (Exception ex) {
                ex.printStackTrace();
                Platform.runLater(() -> {
                    setLoading(false);
                    showEmptyState("Failed to load resources — check console for details.");
                });
            }
        }).start();
    }

    // ─────────────────────────────────────────────────────────────
    // CARD RENDERING
    // ─────────────────────────────────────────────────────────────
    private void renderCards(List<ResourceRow> rows) {
        // Fallback: Populate standard TableView as well
        if (resourceTable != null) {
            resourceTable.getItems().setAll(rows);
        }

        if (cardContainer == null) return;
        cardContainer.getChildren().clear();
        if (rows.isEmpty()) {
            showEmptyState("No resources match the current filters.");
            return;
        }
        for (ResourceRow row : rows)
            cardContainer.getChildren().add(buildCard(row));
    }

    private VBox buildCard(ResourceRow row) {
        CourseService.Resource res = row.getRawResource();

        VBox card = new VBox(10);
        card.setPadding(new Insets(16, 20, 16, 20));
        card.setStyle(cardStyle(false));
        card.setOnMouseEntered(e -> card.setStyle(cardStyle(true)));
        card.setOnMouseExited(e  -> card.setStyle(cardStyle(false)));

        Label typePill    = typePill(row.getType());
        Label diffBadge   = diffBadge(row.getDiff());
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

        Label nameLbl = new Label(row.getName());
        nameLbl.setStyle("-fx-text-fill: #e2e8f0; -fx-font-size: 15px; -fx-font-weight: bold;");
        nameLbl.setWrapText(true);

        Separator sep = new Separator();
        sep.setStyle("-fx-opacity: 0.12;");

        boolean completed = res.isCompleted();
        boolean voted     = res.hasUserVoted();

        Button previewBtn  = cardBtn("👁  Preview",  "#2c3e50");
        Button downloadBtn = cardBtn("⬇  Download", "#16a085");
        Button upBtn       = cardBtn("👍  Upvote",   "#27ae60");
        Button downBtn     = cardBtn("👎  Downvote", "#e74c3c");
        Button doneBtn     = cardBtn(completed ? "✅  Edit Note" : "✅  Mark Done",
                                     completed ? "#16a085"       : "#2980b9");
        Button statsBtn    = cardBtn("📊  Stats",   "#8e44ad");
        Button discussBtn  = cardBtn("💬  Discuss", "#34495e");

        upBtn.setDisable(voted);
        downBtn.setDisable(voted);

        previewBtn.setOnAction(e  -> driveHelper.showInAppPreview(res.link(), res.title()));
        downloadBtn.setOnAction(e -> driveHelper.directDownloadFile(res.link(), res.title()));
        upBtn.setOnAction(e       -> processVote(row, 1));
        downBtn.setOnAction(e     -> processVote(row, -1));
        doneBtn.setOnAction(e     -> statisticsController.showResourceCompletionDialog(
                                        row, currentSelectedTopicId, onTopicReload, resolveWindow()));
        statsBtn.setOnAction(e    -> statisticsController.showStatisticsDialog(res, resolveWindow()));
        discussBtn.setOnAction(e  -> discussionController.showDiscussionPanel(res, resolveWindow()));

        HBox actionRow = new HBox(6, previewBtn, downloadBtn, upBtn, downBtn,
                                     doneBtn, statsBtn, discussBtn);
        actionRow.setAlignment(Pos.CENTER_LEFT);

        boolean canManage = "admin".equals(AuthService.CURRENT_USER_ROLE)
            || (res.creatorId() != null
                && String.valueOf(res.creatorId()).equals(
                   String.valueOf(AuthService.CURRENT_USER_ID)));
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
    // STATES
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

    private void showLoadingState() {
        if (cardContainer == null) return;
        cardContainer.getChildren().clear();
        ProgressIndicator pi = new ProgressIndicator();
        pi.setPrefSize(36, 36);
        pi.setStyle("-fx-progress-color: #3b82f6;");
        Label lbl = new Label("Loading resources…");
        lbl.setStyle("-fx-text-fill: #4a5a72; -fx-font-size: 13px;");
        VBox box = new VBox(12, pi, lbl);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(60));
        cardContainer.getChildren().add(box);
        if (loadingIndicator != null) loadingIndicator.setVisible(true);
        if (loadingLabel     != null) loadingLabel.setVisible(true);
        if (statusBarLabel   != null) statusBarLabel.setText("Loading...");
    }

    private void setLoading(boolean on) {
        if (loadingIndicator != null) loadingIndicator.setVisible(on);
        if (loadingLabel     != null) loadingLabel.setVisible(on);
        if (statusBarLabel   != null) statusBarLabel.setText(on ? "Loading..." : "Ready");
    }

    // ─────────────────────────────────────────────────────────────
    // DELETE
    // ─────────────────────────────────────────────────────────────
    private void showDeleteConfirm(ResourceRow row, Window owner) {
        SPopupHelper.showConfirm(owner,
            "🗑 Delete Resource",
            "Delete \"" + row.getName() + "\"?\nThis action cannot be undone.",
            () -> new Thread(() -> {
                if (courseService.deleteResource(row.getRawResource().id()))
                    Platform.runLater(() -> loadResourcesForTopic(currentSelectedTopicId));
                else
                    SPopupHelper.showError(owner, "Delete Failed",
                        "Could not delete the resource. Please retry.");
            }).start());
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
                else SPopupHelper.showError(resolveWindow(), "Vote Failed",
                    "Could not record your vote. Please retry.");
            });
        }).start();
    }

    // ─────────────────────────────────────────────────────────────
    // EDIT RESOURCE
    // ─────────────────────────────────────────────────────────────
    private void showEditResourceDialog(CourseService.Resource res) {
        TextField        titleField    = darkField(res.title());
        TextField        linkField     = darkField(res.link() != null ? res.link() : "");
        ComboBox<String> typeCombo     = darkCombo("LINK", "PDF", "Video", "Note");
        typeCombo.setValue(res.type()       != null ? res.type()       : "LINK");
        ComboBox<String> diffCombo     = darkCombo("Easy", "Medium", "Hard");
        diffCombo.setValue(res.difficulty() != null ? res.difficulty() : "Medium");
        TextField        durationField = darkField(res.duration()    != null ? res.duration()    : "");
        TextField        tagsField     = darkField(res.tags()        != null ? res.tags()        : "");
        TextArea         descField     = darkArea(res.description()  != null ? res.description() : "");
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

        Stage stage = SPopupHelper.create(resolveWindow(),
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
                Platform.runLater(() -> {
                    if (ok) loadResourcesForTopic(currentSelectedTopicId);
                    else SPopupHelper.showError(resolveWindow(), "Update Failed",
                        "Could not update the resource. Please retry.");
                });
            }).start();
        });
    }

    // ─────────────────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────────────────
    private void updateCountBadge() {
        if (resourceCountLabel != null)
            resourceCountLabel.setText(allRows.size() + " resource(s)");
    }

    private Window resolveWindow() {
        return cardScroll != null && cardScroll.getScene() != null
            ? cardScroll.getScene().getWindow() : null;
    }

    private static VBox fieldBlock(String labelText, javafx.scene.Node ctrl) {
        Label lbl = new Label(labelText);
        lbl.setStyle("-fx-text-fill: #7b8fa8; -fx-font-size: 12px; -fx-font-weight: bold;");
        if (ctrl instanceof Region r) r.setMaxWidth(Double.MAX_VALUE);
        VBox block = new VBox(6, lbl, ctrl);
        block.setFillWidth(true);
        return block;
    }

    private static String cardStyle(boolean hovered) {
        return "-fx-background-color: " + (hovered ? "#1a2236" : "#161b27") + "; "
            + "-fx-background-radius: 12; "
            + "-fx-border-color: " + (hovered ? "#3b82f6" : "#1e2736") + "; "
            + "-fx-border-width: 1; -fx-border-radius: 12; "
            + "-fx-effect: dropshadow(three-pass-box, "
            + (hovered ? "rgba(59,130,246,0.2)" : "rgba(0,0,0,0.3)") + ", 10, 0, 0, 2);";
    }

    private static Label typePill(String type) {
        if (type == null) type = "LINK";
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
        l.setStyle(color + " -fx-background-radius: 20; -fx-padding: 3 10; "
            + "-fx-font-size: 11px; -fx-font-weight: bold;");
        return l;
    }

    private static Label diffBadge(String diff) {
        if (diff == null) diff = "Medium";
        String style = switch (diff) {
            case "Easy" -> "-fx-background-color: #14532d; -fx-text-fill: #4ade80;";
            case "Hard" -> "-fx-background-color: #7f1d1d; -fx-text-fill: #f87171;";
            default     -> "-fx-background-color: #713f12; -fx-text-fill: #fbbf24;";
        };
        Label l = new Label(diff);
        l.setStyle(style + " -fx-background-radius: 20; -fx-padding: 3 10; "
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