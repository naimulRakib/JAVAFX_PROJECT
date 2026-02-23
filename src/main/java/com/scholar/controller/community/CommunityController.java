package com.scholar.controller.community;

import com.scholar.service.CourseService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * COMMUNITY CONTROLLER — TreeView, folder/course management
 * Path: src/main/java/com/scholar/controller/community/CommunityController.java
 */
@Component("dashCommunityController")
public class CommunityController {

    @Autowired private CourseService courseService;

    // TreeView tracking maps — shared with ResourceTableController
    public final Map<TreeItem<String>, Integer> courseMap  = new HashMap<>();
    public final Map<TreeItem<String>, Integer> segmentMap = new HashMap<>();
    public final Map<TreeItem<String>, Integer> topicMap   = new HashMap<>();

    private TreeView<String> communityTree;
    private Label currentFolderLabel;
    private java.util.function.Consumer<Integer> onTopicSelected; // → loadResourcesForTopic

    public void init(TreeView<String> communityTree,
                     Label currentFolderLabel,
                     java.util.function.Consumer<Integer> onTopicSelected) {
        this.communityTree     = communityTree;
        this.currentFolderLabel = currentFolderLabel;
        this.onTopicSelected   = onTopicSelected;

        communityTree.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                if (currentFolderLabel != null) currentFolderLabel.setText(newVal.getValue());
                if (topicMap.containsKey(newVal)) {
                    onTopicSelected.accept(topicMap.get(newVal));
                } else {
                    onTopicSelected.accept(null);
                }
            }
        });
    }

    // ----------------------------------------------------------
    // REFRESH COMMUNITY TREE
    // ----------------------------------------------------------
    @FXML
    public void onRefreshCommunity() {
        if (communityTree == null) return;
        TreeItem<String> root = new TreeItem<>("📚 University Courses");
        root.setExpanded(true);
        courseMap.clear(); segmentMap.clear(); topicMap.clear();

        new Thread(() -> {
            List<String> courses = courseService.getAllCourseCodes();
            Platform.runLater(() -> {
                communityTree.setRoot(root);
                for (String code : courses) {
                    TreeItem<String> courseNode = new TreeItem<>("📘 " + code);
                    int courseId = courseService.getCourseId(code);
                    courseMap.put(courseNode, courseId);
                    root.getChildren().add(courseNode);

                    new Thread(() -> {
                        List<CourseService.Segment> segments = courseService.getSegments(courseId);
                        Platform.runLater(() -> {
                            TreeItem<String> ctGroup     = new TreeItem<>(" Class Tests (CT)");
                            TreeItem<String> basicsGroup = new TreeItem<>("Basic Building");
                            TreeItem<String> finalGroup  = new TreeItem<>("Term Final");
                            TreeItem<String> othersGroup = new TreeItem<>("📁 Others");

                            for (var seg : segments) {
                                String segName = seg.name();
                                TreeItem<String> segNode = new TreeItem<>("📂 " + segName);
                                segmentMap.put(segNode, seg.id());

                                if (segName.toUpperCase().contains("CT"))           ctGroup.getChildren().add(segNode);
                                else if (segName.toLowerCase().contains("basic"))   basicsGroup.getChildren().add(segNode);
                                else if (segName.toLowerCase().contains("final"))   finalGroup.getChildren().add(segNode);
                                else                                                othersGroup.getChildren().add(segNode);

                                new Thread(() -> {
                                    List<CourseService.Topic> topics = courseService.getTopics(seg.id());
                                    Platform.runLater(() -> {
                                        for (var topic : topics) {
                                            TreeItem<String> topicNode = new TreeItem<>("📄 " + topic.title());
                                            topicMap.put(topicNode, topic.id());
                                            segNode.getChildren().add(topicNode);
                                        }
                                    });
                                }).start();
                            }

                            if (!basicsGroup.getChildren().isEmpty()) courseNode.getChildren().add(basicsGroup);
                            if (!ctGroup.getChildren().isEmpty())     courseNode.getChildren().add(ctGroup);
                            if (!finalGroup.getChildren().isEmpty())  courseNode.getChildren().add(finalGroup);
                            if (!othersGroup.getChildren().isEmpty()) courseNode.getChildren().add(othersGroup);
                        });
                    }).start();
                }
            });
        }).start();
    }

    // ----------------------------------------------------------
    // ADD FOLDER / TOPIC
    // ----------------------------------------------------------
    @FXML
    public void onAddNewFolder() {
        TreeItem<String> selected = communityTree.getSelectionModel().getSelectedItem();
        if (selected == null || selected == communityTree.getRoot()) {
            TextInputDialog dialog = new TextInputDialog();
            dialog.setTitle("New Course"); dialog.setHeaderText("Create a new Course");
            dialog.showAndWait().ifPresent(code -> new Thread(() -> {
                if (courseService.addCourse(code, "Community Resource"))
                    Platform.runLater(this::onRefreshCommunity);
            }).start());
        } else if (segmentMap.containsKey(selected)) {
            TextInputDialog dialog = new TextInputDialog();
            dialog.setTitle("New Topic"); dialog.setHeaderText("Add Topic in " + selected.getValue());
            dialog.showAndWait().ifPresent(name -> new Thread(() -> {
                if (courseService.addTopic(segmentMap.get(selected), name.trim(), "General"))
                    Platform.runLater(this::onRefreshCommunity);
            }).start());
        } else {
            showError("❌ Please select 'University Courses' to add a Course, or a sub-folder to add a Topic.");
        }
    }

    private void showError(String msg) {
        Platform.runLater(() -> { Alert a = new Alert(Alert.AlertType.ERROR); a.setContentText(msg); a.showAndWait(); });
    }
}