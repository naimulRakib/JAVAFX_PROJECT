package com.scholar.controller.eca;

import com.scholar.service.ECAService;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.fxml.FXML;
import javafx.scene.chart.PieChart;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * ECA TRACKER CONTROLLER — LeetCode + Devpost dashboard
 * Path: src/main/java/com/scholar/controller/eca/ECAController.java
 */
@Component
public class ECAController {

    @Autowired private ECAService ecaService;

    private VBox ecaContainer;

    public void init(VBox ecaContainer) {
        this.ecaContainer = ecaContainer;
    }

    // ----------------------------------------------------------
    // TAB SELECT EVENT (wired via FXML onSelectionChanged)
    // ----------------------------------------------------------
    @FXML
    public void handleECATrackerSelect(Event event) {
        Tab tab = (Tab) event.getSource();
        if (tab.isSelected() && ecaContainer != null) loadECATracker(ecaContainer);
    }

    @FXML
    public void forceRefreshECA() {
        if (ecaContainer != null) loadECATracker(ecaContainer);
    }

    // ----------------------------------------------------------
    // LOAD ECA TRACKER
    // ----------------------------------------------------------
    public void loadECATracker(VBox container) {
        container.getChildren().clear();
        container.getChildren().add(new Label("Syncing Profiles..."));
        new Thread(() -> {
            String[] accounts = ecaService.getLinkedAccounts();
            Platform.runLater(() -> {
                container.getChildren().clear();
                if (accounts == null || accounts[0] == null)
                    showECASetupForm(container);
                else
                    showECADashboard(container, accounts[0], accounts[1]);
            });
        }).start();
    }

    // ----------------------------------------------------------
    // SETUP FORM (first time)
    // ----------------------------------------------------------
    private void showECASetupForm(VBox container) {
        VBox box = new VBox(15);
        box.setStyle("-fx-padding: 20; -fx-background-color: white; -fx-background-radius: 10;");
        Label title = new Label("🔗 Link Your Accounts");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
        TextField lcField = new TextField(); lcField.setPromptText("LeetCode Username");
        TextField dpField = new TextField(); dpField.setPromptText("Devpost Username");
        Button saveBtn = new Button("Save & Sync 🚀");
        saveBtn.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; "
            + "-fx-cursor: hand; -fx-font-weight: bold; -fx-padding: 8 20; -fx-background-radius: 8;");
        saveBtn.setOnAction(e -> new Thread(() -> {
            if (ecaService.linkAccounts(lcField.getText(), dpField.getText()))
                Platform.runLater(() -> loadECATracker(container));
        }).start());
        box.getChildren().addAll(title, lcField, dpField, saveBtn);
        container.getChildren().add(box);
    }

    // ----------------------------------------------------------
    // ECA DASHBOARD
    // ----------------------------------------------------------
    private void showECADashboard(VBox container, String lcUser, String dpUser) {
        HBox cards = new HBox(20);
        cards.setStyle("-fx-padding: 10;");

        VBox lcCard  = new VBox(10); Label lcStat  = new Label("Loading...");
        lcCard.setStyle("-fx-background-color: white; -fx-padding: 20; -fx-background-radius: 10;");
        Label lcTitle = new Label("🧩 LeetCode");
        lcTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 16px;");
        lcCard.getChildren().addAll(lcTitle, lcStat);

        VBox dpCard  = new VBox(10); Label dpStat  = new Label("Loading...");
        dpCard.setStyle("-fx-background-color: white; -fx-padding: 20; -fx-background-radius: 10;");
        Label dpTitle = new Label("🏆 Devpost");
        dpTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 16px;");
        dpCard.getChildren().addAll(dpTitle, dpStat);

        PieChart topicChart = new PieChart(); topicChart.setPrefSize(300, 200);
        topicChart.setTitle("Topic Breakdown");

        cards.getChildren().addAll(lcCard, dpCard, topicChart);
        Label header = new Label("📊 Activity Dashboard");
        header.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-padding: 10 0 5 0;");
        container.getChildren().addAll(header, cards);

        new Thread(() -> {
            int[]   lcStats    = ecaService.fetchLeetCodeStats(lcUser);
            int     dpProjects = ecaService.fetchDevpostProjectCount(dpUser);
            var     topicStats = ecaService.fetchTopicStats(lcUser);
            Platform.runLater(() -> {
                if (lcStats != null) lcStat.setText("Solved: " + lcStats[0]);
                if (dpProjects != -1) dpStat.setText("Projects: " + dpProjects);
                if (topicStats != null) {
                    topicChart.getData().clear();
                    topicStats.forEach((tag, count) -> {
                        if (count > 0) topicChart.getData().add(new PieChart.Data(tag, count));
                    });
                }
            });
        }).start();
    }
}