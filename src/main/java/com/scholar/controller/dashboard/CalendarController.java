package com.scholar.controller.dashboard;

import com.scholar.model.StudyTask;
import com.scholar.service.WeatherService;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class CalendarController {

    @Autowired
    private WeatherService weatherService;

    private GridPane calendarGrid;
    private Label monthLabel;
    private List<StudyTask> allTasks;
    private LocalDate selectedDate;
    
    private java.util.function.Consumer<LocalDate> onDateClickedCallback;
    private Map<String, WeatherService.DailyWeather> weeklyWeather = new HashMap<>();
    private boolean isWeatherEnabled = false;

    public void init(GridPane calendarGrid,
                     Label monthLabel,
                     List<StudyTask> allTasks,
                     java.util.function.Supplier<LocalDate> selectedDateSupplier,
                     java.util.function.Consumer<LocalDate> onDateClicked) {
        this.calendarGrid = calendarGrid;
        this.monthLabel   = monthLabel;
        this.allTasks     = allTasks;
        this.selectedDate = selectedDateSupplier.get();
        this.onDateClickedCallback = onDateClicked;

        // 🌟 যদি লোকেশন সেভ করা থাকে, তবে অটোমেটিক ওয়েদার লোড করবে
        if (weatherService.hasSavedLocation()) {
            new Thread(() -> {
                double[] coords = weatherService.getSavedLocation();
                weeklyWeather = weatherService.fetchWeeklyForecast(coords[0], coords[1]);
                isWeatherEnabled = true;
                Platform.runLater(() -> drawCalendar(this.selectedDate));
            }).start();
        }

        drawCalendar(this.selectedDate);
    }

    public void setSelectedDate(LocalDate date) { this.selectedDate = date; }

    public void drawCalendar(LocalDate selectedDate) {
        this.selectedDate = selectedDate;
        if (calendarGrid == null || monthLabel == null) return;
        calendarGrid.getChildren().clear();

        YearMonth currentMonth = YearMonth.now();
        monthLabel.setText(currentMonth.getMonth().name() + " " + currentMonth.getYear());

        // 🌟 লোকেশন সেভ না থাকলে বাটন দেখাবে
        if (!isWeatherEnabled) {
            Button enableWeatherBtn = new Button("📍 Auto-Detect & Save Location");
            enableWeatherBtn.setStyle("-fx-background-color: #059669; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 6; -fx-cursor: hand; -fx-padding: 6 12;");
            
            enableWeatherBtn.setOnAction(e -> {
                enableWeatherBtn.setText("⏳ Saving Location & Fetching Climate...");
                enableWeatherBtn.setDisable(true);
                
                new Thread(() -> {
                    double[] coords = weatherService.detectAndSaveLocation();
                    weeklyWeather = weatherService.fetchWeeklyForecast(coords[0], coords[1]);
                    isWeatherEnabled = true;
                    Platform.runLater(() -> drawCalendar(this.selectedDate));
                }).start();
            });

            HBox btnContainer = new HBox(enableWeatherBtn);
            btnContainer.setAlignment(Pos.CENTER_RIGHT);
            btnContainer.setPadding(new Insets(0, 0, 10, 0));
            calendarGrid.add(btnContainer, 0, 0, 7, 1);
        }

        String[] daysOfWeek = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
        for (int i = 0; i < 7; i++) {
            Label dayLabel = new Label(daysOfWeek[i]);
            dayLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #7f8c8d; -fx-font-size: 14px;");
            VBox headerBox = new VBox(dayLabel);
            headerBox.setAlignment(Pos.CENTER);
            headerBox.setPadding(new Insets(10, 0, 15, 0));
            calendarGrid.add(headerBox, i, 1);
        }

        int startDay     = currentMonth.atDay(1).getDayOfWeek().getValue() % 7;
        int daysInMonth  = currentMonth.lengthOfMonth();
        int row = 2, col = startDay;

        for (int day = 1; day <= daysInMonth; day++) {
            LocalDate cellDate = currentMonth.atDay(day);
            calendarGrid.add(createDayBox(day, cellDate, selectedDate), col, row);
            col++;
            if (col > 6) { col = 0; row++; }
        }
    }

    private VBox createDayBox(int day, LocalDate date, LocalDate selectedDate) {
        VBox box = new VBox(5);
        box.setAlignment(Pos.TOP_CENTER);
        box.setPadding(new Insets(5));
        box.setPrefSize(100, 100);

        Label lbl = new Label(String.valueOf(day));
        lbl.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");
        LocalDate today  = LocalDate.now();
        String boxStyle  = "-fx-background-color: white; -fx-border-color: #ecf0f1; -fx-cursor: hand;";

        if (date.equals(today)) {
            boxStyle = "-fx-background-color: #3498db; -fx-border-color: #2980b9; -fx-background-radius: 5; -fx-border-radius: 5; -fx-cursor: hand;";
            lbl.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: white;");
        }
        if (date.equals(selectedDate)) {
            if (date.equals(today)) {
                boxStyle = "-fx-background-color: #3498db; -fx-border-color: #e74c3c; -fx-border-width: 2; -fx-background-radius: 5; -fx-border-radius: 5; -fx-cursor: hand;";
            } else {
                boxStyle = "-fx-background-color: #ebf5fb; -fx-border-color: #3498db; -fx-border-width: 2; -fx-background-radius: 5; -fx-border-radius: 5; -fx-cursor: hand;";
            }
        }
        box.setStyle(boxStyle);

        HBox topRow = new HBox();
        topRow.setAlignment(Pos.TOP_LEFT);
        boolean hasWeather = (isWeatherEnabled && weeklyWeather != null && weeklyWeather.containsKey(date.toString()));

        if (hasWeather) {
            WeatherService.DailyWeather w = weeklyWeather.get(date.toString());
            Label weatherLbl = new Label(w.emoji() + " " + w.maxTemp());
            
            if (date.equals(today)) {
                weatherLbl.setStyle("-fx-font-size: 10px; -fx-text-fill: white; -fx-background-color: rgba(255,255,255,0.25); -fx-padding: 2 4; -fx-background-radius: 3;");
            } else {
                weatherLbl.setStyle("-fx-font-size: 10px; -fx-text-fill: #7f8c8d; -fx-background-color: #f4f6f7; -fx-padding: 2 4; -fx-background-radius: 3;");
            }

            Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
            topRow.getChildren().addAll(lbl, spacer, weatherLbl);
        } else {
            topRow.getChildren().add(lbl);
            topRow.setAlignment(Pos.TOP_CENTER);
        }

        box.getChildren().add(topRow);

        List<StudyTask> tasksForThisDay = allTasks.stream()
            .filter(t -> t.date() != null && t.date().equals(date.toString()))
            .collect(Collectors.toList());

        if (!tasksForThisDay.isEmpty()) {
            Circle dot = new Circle(3, Color.web(date.equals(today) ? "#ffffff" : "#e74c3c"));
            box.getChildren().add(dot);
        }

        box.setOnMouseClicked(e -> {
            this.selectedDate = date;
            drawCalendar(this.selectedDate); 
            if (onDateClickedCallback != null) onDateClickedCallback.accept(date); 
            openClimateAndTaskPopup(date, tasksForThisDay); 
        });

        return box;
    }

    // ==========================================================
    // 🌟 ULTIMATE CLIMATE & TASK POP-UP 
    // ==========================================================
    private void openClimateAndTaskPopup(LocalDate date, List<StudyTask> tasks) {
        Stage stage = new Stage();
        stage.setTitle("📅 Details for " + date.toString());
        stage.initModality(Modality.APPLICATION_MODAL);

        VBox root = new VBox(15);
        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: #0f1117;");

        // 📅 Header
        Label dateLabel = new Label("📅 " + date.getMonth().name() + " " + date.getDayOfMonth() + ", " + date.getYear());
        dateLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #e2e8f0;");
        root.getChildren().add(dateLabel);

        // 🌍 Climate Section
        boolean hasWeather = (isWeatherEnabled && weeklyWeather != null && weeklyWeather.containsKey(date.toString()));
        if (hasWeather) {
            WeatherService.DailyWeather w = weeklyWeather.get(date.toString());
            
            VBox weatherBox = new VBox(12);
            weatherBox.setStyle("-fx-background-color: #1e2738; -fx-padding: 15; -fx-background-radius: 8; -fx-border-color: #2d3748; -fx-border-radius: 8;");

            // Top: Emoji + Condition + Temps
            HBox topInfo = new HBox(15);
            topInfo.setAlignment(Pos.CENTER_LEFT);
            Label emoji = new Label(w.emoji()); emoji.setStyle("-fx-font-size: 40px;");
            VBox temps = new VBox(3);
            Label cond = new Label(w.condition()); cond.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #3b82f6;");
            Label tLabel = new Label("🌡️ Max: " + w.maxTemp() + "  |  Min: " + w.minTemp());
            tLabel.setStyle("-fx-text-fill: #e2e8f0; -fx-font-size: 13px;");
            temps.getChildren().addAll(cond, tLabel);
            topInfo.getChildren().addAll(emoji, temps);
            weatherBox.getChildren().add(topInfo);

            // Bottom: Grid of Climate Details
            GridPane grid = new GridPane();
            grid.setHgap(20); grid.setVgap(8);
            
            grid.add(createDetailLabel("🌧️ Rain:", w.rainChance()), 0, 0);
            grid.add(createDetailLabel("🌬️ Wind:", w.windSpeed()), 1, 0);
            grid.add(createDetailLabel("☀️ UV Index:", w.uvIndex()), 0, 1);
            grid.add(createDetailLabel("🌅 Sunrise:", w.sunrise()), 1, 1);
            grid.add(createDetailLabel("🌇 Sunset:", w.sunset()), 0, 2);

            weatherBox.getChildren().add(grid);
            root.getChildren().add(weatherBox);
        }

        // 📌 Tasks Section
        Label taskTitle = new Label("📌 Planned Tasks");
        taskTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #cbd5e1; -fx-padding: 5 0 0 0;");
        root.getChildren().add(taskTitle);

        VBox tasksBox = new VBox(8);
        if (tasks.isEmpty()) {
            Label noTask = new Label("🎉 No tasks planned. Take a break!");
            noTask.setStyle("-fx-text-fill: #10b981; -fx-font-size: 13px; -fx-padding: 10; -fx-background-color: #064e3b; -fx-background-radius: 6;");
            tasksBox.getChildren().add(noTask);
        } else {
            for (StudyTask t : tasks) {
                VBox tBox = new VBox(3);
                tBox.setStyle("-fx-background-color: #161b27; -fx-padding: 12; -fx-background-radius: 6; -fx-border-color: #252d3d; -fx-border-radius: 6;");
                Label tName = new Label("🔹 " + t.title()); tName.setStyle("-fx-font-weight: bold; -fx-text-fill: #e2e8f0; -fx-font-size: 13px;");
                Label tTime = new Label("⏰ " + (t.startTime() != null ? t.startTime() : "Anytime")); tTime.setStyle("-fx-text-fill: #64748b; -fx-font-size: 11px;");
                tBox.getChildren().addAll(tName, tTime); tasksBox.getChildren().add(tBox);
            }
        }
        
        ScrollPane scroll = new ScrollPane(tasksBox);
        scroll.setFitToWidth(true); scroll.setPrefHeight(150);
        scroll.setStyle("-fx-background: #0f1117; -fx-background-color: #0f1117; -fx-border-color: transparent;");
        root.getChildren().add(scroll);

        // ❌ Close Button
        Button closeBtn = new Button("Close");
        closeBtn.setStyle("-fx-background-color: #374151; -fx-text-fill: white; -fx-cursor: hand; -fx-font-weight: bold; -fx-padding: 8 20; -fx-background-radius: 6;");
        closeBtn.setOnAction(e -> stage.close());
        HBox bottom = new HBox(closeBtn); bottom.setAlignment(Pos.CENTER_RIGHT);
        root.getChildren().add(bottom);

        Scene scene = new Scene(root, 400, 550);
        scene.setFill(Color.web("#0f1117"));
        stage.setScene(scene);
        stage.showAndWait();
    }

    // ছোট হেল্পার মেথড গ্রিডের টেক্সটের জন্য
    private HBox createDetailLabel(String iconAndTitle, String value) {
        Label title = new Label(iconAndTitle + " "); title.setStyle("-fx-text-fill: #64748b; -fx-font-size: 12px;");
        Label val = new Label(value); val.setStyle("-fx-text-fill: #e2e8f0; -fx-font-size: 12px; -fx-font-weight: bold;");
        return new HBox(title, val);
    }
}