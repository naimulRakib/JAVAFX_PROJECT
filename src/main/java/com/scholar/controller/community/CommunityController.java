package com.scholar.controller.community;

import com.scholar.service.CourseService;
import com.scholar.util.SPopupHelper;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * COMMUNITY CONTROLLER — TreeView with full manual hierarchy:
 *
 *   📚 University Courses
 *   └── 📘 CSE 105              (Course  — added manually)
 *       ├── 🛠 Basic Building    (fixed group)
 *       │   └── 📄 Pointers     (Topic   — added manually)
 *       ├── 🧪 Class Tests (CT)  (fixed group)
 *       │   └── 📄 CT-1 Arrays  (Topic)
 *       └── 📋 Term Final (TF)   (fixed group)
 *           └── 📄 TF 2023      (Topic)
 *
 * Selecting a Topic fires onTopicSelected so the right panel loads resources.
 *
 * Path: src/main/java/com/scholar/controller/community/CommunityController.java
 */
@SuppressWarnings("unchecked")
@Component("dashCommunityController")
public class CommunityController {

    @Autowired private CourseService courseService;

    // ── Tree-item → DB-id maps ────────────────────────────────────
    public final Map<TreeItem<String>, Integer> courseMap  = new HashMap<>();
    public final Map<TreeItem<String>, Integer> segmentMap = new HashMap<>();
    public final Map<TreeItem<String>, Integer> topicMap   = new HashMap<>();

    // ── Fixed group labels (never stored in DB) ───────────────────
    private static final String GRP_BASIC = "🛠 Basic Building";
    private static final String GRP_CT    = "🧪 Class Tests (CT)";
    private static final String GRP_TF    = "📋 Term Final (TF)";

    private TreeView<String> communityTree;
    private Label currentFolderLabel;
    private java.util.function.Consumer<Integer> onTopicSelected;
    private List<CourseService.CourseTreeRow> cachedRows = new ArrayList<>();
    private String currentQuery = "";

    // ─────────────────────────────────────────────────────────────
    // INIT
    // ─────────────────────────────────────────────────────────────
    public void init(TreeView<String> communityTree,
                     Label currentFolderLabel,
                     java.util.function.Consumer<Integer> onTopicSelected) {
        this.communityTree      = communityTree;
        this.currentFolderLabel = currentFolderLabel;
        this.onTopicSelected    = onTopicSelected;

        // Style the tree
        communityTree.setStyle(
            "-fx-background-color: #0d1117; -fx-border-color: transparent;");
        communityTree.getStyleClass().add("dark-tree");

        // Apply custom cell factory for right-click context menus
        communityTree.setCellFactory(tv -> buildCell());

        // Selection listener — only topics fire the resource panel
        communityTree.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null) return;
            if (currentFolderLabel != null) currentFolderLabel.setText(stripEmoji(newVal.getValue()));
            if (topicMap.containsKey(newVal)) onTopicSelected.accept(topicMap.get(newVal));
            // Selecting a course or group clears the resource panel
            else                             onTopicSelected.accept(null);
        });
    }

    // ─────────────────────────────────────────────────────────────
    // REFRESH  — rebuild tree from DB
    // ─────────────────────────────────────────────────────────────
    @FXML
    public void onRefreshCommunity() {
        if (communityTree == null) return;

        // ── Single background thread → single Platform.runLater ──────
        // This eliminates the previous race condition where topicMap was
        // still empty when the user clicked a node that was already visible.
        new Thread(() -> {
            List<CourseService.CourseTreeRow> rows = courseService.loadCourseTree();
            Platform.runLater(() -> {
                cachedRows = rows != null ? rows : new ArrayList<>();
                rebuildTree(cachedRows, currentQuery);
            });
        }).start();
    }

    public void applySearch(String query) {
        currentQuery = query == null ? "" : query.trim().toLowerCase();
        if (Platform.isFxApplicationThread()) rebuildTree(cachedRows, currentQuery);
        else Platform.runLater(() -> rebuildTree(cachedRows, currentQuery));
    }

    private void rebuildTree(List<CourseService.CourseTreeRow> rows, String query) {
        if (communityTree == null) return;
        courseMap.clear();
        segmentMap.clear();
        topicMap.clear();

        boolean hasQuery = query != null && !query.isBlank();
        String q = hasQuery ? query.toLowerCase() : "";

        Set<Integer> matchedCourses = new HashSet<>();
        Set<Integer> matchedSegments = new HashSet<>();
        Set<Integer> matchedTopics = new HashSet<>();
        Set<Integer> directCourseMatches = new HashSet<>();
        Set<Integer> directSegmentMatches = new HashSet<>();

        if (hasQuery && rows != null) {
            for (CourseService.CourseTreeRow row : rows) {
                boolean courseMatch = contains(row.courseCode(), q);
                boolean segmentMatch = contains(row.segmentName(), q);
                boolean topicMatch = row.topicId() != -1 && contains(row.topicTitle(), q);

                if (courseMatch) directCourseMatches.add(row.courseId());
                if (segmentMatch) directSegmentMatches.add(row.segmentId());
                if (topicMatch) matchedTopics.add(row.topicId());

                if (courseMatch || segmentMatch || topicMatch) matchedCourses.add(row.courseId());
                if (segmentMatch || topicMatch) matchedSegments.add(row.segmentId());
            }
        }

        TreeItem<String> root = new TreeItem<>("📚 University Courses");
        root.setExpanded(true);

        Map<Integer, TreeItem<String>> courseNodes = new java.util.LinkedHashMap<>();
        Map<Integer, TreeItem<String>> segNodes = new java.util.HashMap<>();
        Map<Integer, TreeItem<String>> groupBasic = new java.util.HashMap<>();
        Map<Integer, TreeItem<String>> groupCT = new java.util.HashMap<>();
        Map<Integer, TreeItem<String>> groupTF = new java.util.HashMap<>();

        if (rows != null) {
            for (CourseService.CourseTreeRow row : rows) {
                boolean includeCourse = !hasQuery || matchedCourses.contains(row.courseId());
                if (!includeCourse) continue;

                boolean includeSegment = !hasQuery
                    || directCourseMatches.contains(row.courseId())
                    || directSegmentMatches.contains(row.segmentId())
                    || matchedSegments.contains(row.segmentId());

                boolean includeTopic = row.topicId() != -1 && (!hasQuery
                    || directCourseMatches.contains(row.courseId())
                    || directSegmentMatches.contains(row.segmentId())
                    || matchedTopics.contains(row.topicId()));

                // ── Course node ───────────────────────────────────
                TreeItem<String> courseNode = courseNodes.computeIfAbsent(row.courseId(), id -> {
                    TreeItem<String> cn = new TreeItem<>("📘 " + row.courseCode());
                    cn.setExpanded(hasQuery);
                    courseMap.put(cn, id);

                    TreeItem<String> basic = new TreeItem<>(GRP_BASIC);
                    TreeItem<String> ct = new TreeItem<>(GRP_CT);
                    TreeItem<String> tf = new TreeItem<>(GRP_TF);
                    basic.setExpanded(hasQuery);
                    ct.setExpanded(hasQuery);
                    tf.setExpanded(hasQuery);
                    cn.getChildren().addAll(basic, ct, tf);

                    segmentMap.put(basic, id);
                    segmentMap.put(ct, id);
                    segmentMap.put(tf, id);

                    groupBasic.put(id, basic);
                    groupCT.put(id, ct);
                    groupTF.put(id, tf);

                    root.getChildren().add(cn);
                    return cn;
                });

                if (!includeSegment && !includeTopic) continue;

                TreeItem<String> segNode = segNodes.computeIfAbsent(row.segmentId(), id -> {
                    TreeItem<String> sn = new TreeItem<>("📂 " + row.segmentName());
                    sn.setExpanded(hasQuery);
                    segmentMap.put(sn, id);

                    String lname = row.segmentName().toLowerCase();
                    TreeItem<String> targetGroup =
                        (lname.contains("ct") || lname.contains("class test"))
                            ? groupCT.get(row.courseId())
                        : lname.contains("final")
                            ? groupTF.get(row.courseId())
                            : groupBasic.get(row.courseId());

                    if (targetGroup != null) targetGroup.getChildren().add(sn);
                    return sn;
                });

                if (includeTopic) {
                    TreeItem<String> topicNode = new TreeItem<>("📄 " + row.topicTitle());
                    topicMap.put(topicNode, row.topicId());
                    segNode.getChildren().add(topicNode);
                }
            }
        }

        communityTree.setRoot(root);
    }

    // ─────────────────────────────────────────────────────────────
    // ADD COURSE  (root-level)
    // ─────────────────────────────────────────────────────────────
    @FXML
    public void onAddCourse() {
        SPopupHelper.showInput(window(),
            "➕ Add Course",
            "Enter the course code (e.g. CSE 105, MATH 201)",
            "Course code…",
            code -> new Thread(() -> {
                if (courseService.addCourse(code.toUpperCase().trim(), "Community Resource"))
                    Platform.runLater(this::onRefreshCommunity);
                else
                    SPopupHelper.showError(window(), "Duplicate Course",
                        "A course with that code already exists.");
            }).start());
    }

    // ─────────────────────────────────────────────────────────────
    // ADD TOPIC  (under a group node — Basic / CT / TF)
    // ─────────────────────────────────────────────────────────────
    @FXML
    public void onAddTopic() {
        TreeItem<String> selected = communityTree.getSelectionModel().getSelectedItem();

        // Must select a group (Basic Building, CT, TF) or a segment node
        if (selected == null) {
            SPopupHelper.showError(window(), "Nothing Selected",
                "Select a group (🛠 Basic Building, 🧪 Class Tests, or 📋 Term Final) first.");
            return;
        }

        // Walk up to find the parent segment node we should place the topic under
        TreeItem<String> segNode = resolveSegmentNode(selected);
        if (segNode == null || !segmentMap.containsKey(segNode)) {
            SPopupHelper.showError(window(), "Invalid Selection",
                "Please select a group folder (Basic Building, CT, or TF) or a segment inside it.");
            return;
        }

        String groupLabel = stripEmoji(selected.getValue());
        int    segId      = segmentMap.get(segNode);

        SPopupHelper.showInput(window(),
            "📄 Add Topic",
            "Adding topic under \"" + groupLabel + "\"",
            "Topic title…",
            name -> new Thread(() -> {
                if (courseService.addTopic(segId, name.trim(), "General"))
                    Platform.runLater(this::onRefreshCommunity);
                else
                    SPopupHelper.showError(window(), "Failed",
                        "Could not add topic. Please try again.");
            }).start());
    }

    // ─────────────────────────────────────────────────────────────
    // ADD SEGMENT  (manual sub-folder inside a course)
    // ─────────────────────────────────────────────────────────────
    @FXML
    public void onAddSegment() {
        TreeItem<String> selected = communityTree.getSelectionModel().getSelectedItem();
        TreeItem<String> courseNode = resolveCourseNode(selected);

        if (courseNode == null || !courseMap.containsKey(courseNode)) {
            SPopupHelper.showError(window(), "Invalid Selection",
                "Select a course (📘) or one of its groups to add a custom segment.");
            return;
        }

        int    courseId    = courseMap.get(courseNode);
        String courseLabel = stripEmoji(courseNode.getValue());

        SPopupHelper.showInput(window(),
            "📂 Add Segment",
            "Adding segment inside \"" + courseLabel + "\"",
            "Segment name (e.g. Lab Work, Quiz)…",
            name -> new Thread(() -> {
                if (courseService.addSegment(courseId, name.trim()))
                    Platform.runLater(this::onRefreshCommunity);
                else
                    SPopupHelper.showError(window(), "Failed",
                        "Could not add segment. Please try again.");
            }).start());
    }

    // ─────────────────────────────────────────────────────────────
    // DELETE — course, segment, or topic
    // ─────────────────────────────────────────────────────────────
    public void onDeleteSelected() {
        TreeItem<String> selected = communityTree.getSelectionModel().getSelectedItem();
        if (selected == null || selected == communityTree.getRoot()) return;

        String label = stripEmoji(selected.getValue());

        if (topicMap.containsKey(selected)) {
            SPopupHelper.showConfirm(window(), "🗑 Delete Topic",
                "Delete topic \"" + label + "\" and all its resources?",
                () -> new Thread(() -> {
                    if (courseService.deleteTopic(topicMap.get(selected)))
                        Platform.runLater(this::onRefreshCommunity);
                }).start());
        } else if (segmentMap.containsKey(selected)) {
            SPopupHelper.showConfirm(window(), "🗑 Delete Segment",
                "Delete segment \"" + label + "\" and all its topics?",
                () -> new Thread(() -> {
                    if (courseService.deleteSegment(segmentMap.get(selected)))
                        Platform.runLater(this::onRefreshCommunity);
                }).start());
        } else if (courseMap.containsKey(selected)) {
            SPopupHelper.showConfirm(window(), "🗑 Delete Course",
                "Delete course \"" + label + "\" and ALL its data?",
                () -> new Thread(() -> {
                    if (courseService.deleteCourse(courseMap.get(selected)))
                        Platform.runLater(this::onRefreshCommunity);
                }).start());
        } else {
            SPopupHelper.showError(window(), "Cannot Delete",
                "The fixed group folders (Basic Building / CT / TF) cannot be deleted.");
        }
    }

    // ─────────────────────────────────────────────────────────────
    // CONTEXT MENU  — right-click on tree nodes
    // ─────────────────────────────────────────────────────────────
    private TreeCell<String> buildCell() {
        TreeCell<String> cell = new TreeCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setContextMenu(null);
                    setStyle("-fx-background-color: transparent;");
                } else {
                    setText(item);
                    setStyle("-fx-text-fill: #e2e8f0; -fx-font-size: 13px; "
                        + "-fx-background-color: transparent;");
                    setContextMenu(buildContextMenu(getTreeItem()));
                }
            }
        };
        return cell;
    }

    private ContextMenu buildContextMenu(TreeItem<String> item) {
        if (item == null) return null;
        ContextMenu cm = new ContextMenu();

        boolean isRoot   = item == communityTree.getRoot();
        boolean isCourse = courseMap.containsKey(item);
        boolean isGroup  = isFixedGroup(item);
        boolean isSeg    = segmentMap.containsKey(item) && !isGroup;
        boolean isTopic  = topicMap.containsKey(item);

        if (isRoot || isCourse) {
            MenuItem addCourse = new MenuItem("➕  Add Course");
            addCourse.setOnAction(e -> onAddCourse());
            cm.getItems().add(addCourse);
        }
        if (isCourse || isGroup || isSeg) {
            MenuItem addTopic = new MenuItem("📄  Add Topic here");
            addTopic.setOnAction(e -> {
                communityTree.getSelectionModel().select(item);
                onAddTopic();
            });
            MenuItem addSeg = new MenuItem("📂  Add Custom Segment");
            addSeg.setOnAction(e -> {
                communityTree.getSelectionModel().select(item);
                onAddSegment();
            });
            cm.getItems().addAll(addTopic, addSeg);
        }
        if (isCourse || isSeg || isTopic) {
            MenuItem delete = new MenuItem("🗑  Delete");
            delete.setOnAction(e -> {
                communityTree.getSelectionModel().select(item);
                onDeleteSelected();
            });
            cm.getItems().add(delete);
        }
        if (cm.getItems().isEmpty()) return null;
        return cm;
    }

    // ─────────────────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────────────────

    /** Walk up to find the nearest ancestor (or self) that is a segmentMap key (excluding fixed groups). */
    private TreeItem<String> resolveSegmentNode(TreeItem<String> item) {
        while (item != null) {
            if (segmentMap.containsKey(item) && !isFixedGroup(item)) return item;
            // If the item IS a fixed group, try its first child segment if any
            if (isFixedGroup(item)) {
                if (!item.getChildren().isEmpty()) return item.getChildren().get(0);
                // No child yet — we must create a default segment for this group
                return item;   // caller will re-check via segmentMap
            }
            item = item.getParent();
        }
        return null;
    }

    /** Walk up to find the nearest ancestor (or self) that is a courseMap key. */
    private TreeItem<String> resolveCourseNode(TreeItem<String> item) {
        while (item != null) {
            if (courseMap.containsKey(item)) return item;
            item = item.getParent();
        }
        return null;
    }

    private boolean isFixedGroup(TreeItem<String> item) {
        if (item == null) return false;
        String v = item.getValue();
        return GRP_BASIC.equals(v) || GRP_CT.equals(v) || GRP_TF.equals(v);
    }

    private static boolean contains(String value, String query) {
        return value != null && value.toLowerCase().contains(query);
    }

    private static String stripEmoji(String s) {
        if (s == null) return "";
        return s.replaceAll("[\\p{So}\\p{Cn}\\p{Sm}]", "").trim();
    }

    private Window window() {
        return communityTree != null && communityTree.getScene() != null
            ? communityTree.getScene().getWindow() : null;
    }
}
