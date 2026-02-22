package com.scholar.controller;

import com.scholar.service.CourseService;
import com.scholar.view.DraggableListView;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

import org.springframework.beans.factory.annotation.Autowired; // 🟢 নতুন
import org.springframework.stereotype.Controller; // 🟢 নতুন

@Controller
public class CommunityController {

    @FXML private ComboBox<String> courseSelector; // CSE 105, PHY 101
    @FXML private TabPane segmentTabs; // CT, Final, Basic
    
    // We create these dynamically
    private DraggableListView<String> topicList = new DraggableListView<>();
    
   @Autowired
    private CourseService courseService;

    @FXML
    public void initialize() {
        // Load Courses
        courseSelector.getItems().addAll("CSE 105", "MATH 101"); // Fetch from DB in real app
    }

    @FXML
    public void onCourseSelected() {
        String code = courseSelector.getValue();
        // 1. Fetch Segments (Auto-created by DB)
        // List<Segment> segments = courseService.getSegments(courseId);
        
        segmentTabs.getTabs().clear();
        
        // Example: Manually adding tabs to show logic
        createSegmentTab("CT 🚨");
        createSegmentTab("Term Final 📚");
        createSegmentTab("Basic Building 🛠️");
    }

    

    private void createSegmentTab(String title) {
        Tab tab = new Tab(title);
        
        VBox content = new VBox(10);
        content.setStyle("-fx-padding: 10;");
        
        // Add "Add Topic" Button
        Button addTopicBtn = new Button("+ Add New Topic");
        addTopicBtn.setOnAction(e -> {
            // Popup dialog to add topic
            topicList.getItems().add("New Topic " + System.currentTimeMillis());
        });

        // The Drag & Drop List
        topicList.getItems().add("Pointers (CT-1)");
        topicList.getItems().add("Recursion (CT-2)");
        
        content.getChildren().addAll(addTopicBtn, topicList);
        tab.setContent(content);
        
        segmentTabs.getTabs().add(tab);
    }
}