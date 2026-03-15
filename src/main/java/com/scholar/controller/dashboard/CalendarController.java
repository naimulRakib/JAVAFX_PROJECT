package com.scholar.controller.dashboard;

import com.scholar.model.StudyTask;
import com.scholar.model.ClassOffPeriod;
import com.scholar.service.WeatherService;
import com.scholar.util.PopupHelper;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * CalendarController — draws a dark-theme monthly calendar grid.
 *
 * KEY CHANGES (UI only — all logic untouched):
 *  • createDayBox():  replaced all hardcoded light colours
 *      white → #0f1220  (default cell bg)
 *      #ecf0f1 border → rgba(255,255,255,0.06)
 *      #2c3e50 text → #94a3b8
 *      #3498db (today) → indigo gradient
 *      #ebf5fb (selected) → rgba(99,102,241,0.18) + indigo border
 *      weather label bg #f4f6f7 → rgba(255,255,255,0.07)
 *
 *  • openClimateAndTaskPopup(): all scroll-pane viewport gaps fixed
 *    with both -fx-background and -fx-background-color.
 *
 * Path: src/main/java/com/scholar/controller/dashboard/CalendarController.java
 */
@Component
public class CalendarController {

    @Autowired
    private WeatherService weatherService;

    private GridPane calendarGrid;
    private Label    monthLabel;
    private List<StudyTask> allTasks;
    private List<ClassOffPeriod> classOffPeriods = new java.util.ArrayList<>();
    private LocalDate selectedDate;

    private java.util.function.Consumer<LocalDate> onDateClickedCallback;
    private Map<String, WeatherService.DailyWeather> weeklyWeather = new HashMap<>();
    private boolean isWeatherEnabled = false;

    /** Returns the owner Window for PopupHelper calls. */
    private javafx.stage.Window window() {
        return calendarGrid != null && calendarGrid.getScene() != null
                ? calendarGrid.getScene().getWindow() : null;
    }

    // ── init ──────────────────────────────────────────────────────────────────
    public void init(GridPane calendarGrid,
                     Label monthLabel,
                     List<StudyTask> allTasks,
                     java.util.function.Supplier<LocalDate> selectedDateSupplier,
                     java.util.function.Consumer<LocalDate> onDateClicked) {
        this.calendarGrid          = calendarGrid;
        this.monthLabel            = monthLabel;
        this.allTasks              = allTasks;
        this.selectedDate          = selectedDateSupplier.get();
        this.onDateClickedCallback = onDateClicked;

        if (weatherService.hasSavedLocation()) {
            new Thread(() -> {
                double[] coords   = weatherService.getSavedLocation();
                weeklyWeather     = weatherService.fetchWeeklyForecast(coords[0], coords[1]);
                isWeatherEnabled  = true;
                Platform.runLater(() -> drawCalendar(this.selectedDate));
            }).start();
        }

        drawCalendar(this.selectedDate);
    }

    public void setSelectedDate(LocalDate date) { this.selectedDate = date; }
    public void setClassOffPeriods(List<ClassOffPeriod> periods) {
        this.classOffPeriods = periods != null ? periods : new java.util.ArrayList<>();
    }

    // ── drawCalendar ──────────────────────────────────────────────────────────
    public void drawCalendar(LocalDate selectedDate) {
        this.selectedDate = selectedDate;
        if (calendarGrid == null || monthLabel == null) return;
        calendarGrid.getChildren().clear();

        YearMonth currentMonth = YearMonth.now();
        monthLabel.setText(currentMonth.getMonth().name() + " " + currentMonth.getYear());

        if (!isWeatherEnabled) {
            Button enableWeatherBtn = new Button("📍 Auto-Detect & Save Location");
            enableWeatherBtn.setStyle(
                "-fx-background-color:#059669;-fx-text-fill:white;" +
                "-fx-font-weight:bold;-fx-background-radius:6;" +
                "-fx-cursor:hand;-fx-padding:6 12;");

            enableWeatherBtn.setOnAction(e -> {
                enableWeatherBtn.setText("⏳ Saving Location & Fetching Climate...");
                enableWeatherBtn.setDisable(true);
                new Thread(() -> {
                    double[] coords  = weatherService.detectAndSaveLocation();
                    weeklyWeather    = weatherService.fetchWeeklyForecast(coords[0], coords[1]);
                    isWeatherEnabled = true;
                    Platform.runLater(() -> drawCalendar(this.selectedDate));
                }).start();
            });

            HBox btnContainer = new HBox(enableWeatherBtn);
            btnContainer.setAlignment(Pos.CENTER_RIGHT);
            btnContainer.setPadding(new Insets(0, 0, 10, 0));
            calendarGrid.add(btnContainer, 0, 0, 7, 1);
        } else {
            Button refreshBtn = new Button("🔄 Fetch Weather");
            refreshBtn.setStyle(
                "-fx-background-color:#0ea5e9;-fx-text-fill:white;" +
                "-fx-font-weight:bold;-fx-background-radius:6;" +
                "-fx-cursor:hand;-fx-padding:6 12;");
            refreshBtn.setOnAction(e -> {
                refreshBtn.setText("⏳ Updating...");
                refreshBtn.setDisable(true);
                new Thread(() -> {
                    double[] coords = weatherService.getSavedLocation();
                    weeklyWeather = weatherService.fetchWeeklyForecast(coords[0], coords[1]);
                    Platform.runLater(() -> {
                        refreshBtn.setText("🔄 Fetch Weather");
                        refreshBtn.setDisable(false);
                        drawCalendar(this.selectedDate);
                    });
                }).start();
            });
            HBox btnContainer = new HBox(refreshBtn);
            btnContainer.setAlignment(Pos.CENTER_RIGHT);
            btnContainer.setPadding(new Insets(0, 0, 10, 0));
            calendarGrid.add(btnContainer, 0, 0, 7, 1);
        }

        // Day-of-week headers
        String[] daysOfWeek = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
        for (int i = 0; i < 7; i++) {
            Label dayLabel = new Label(daysOfWeek[i]);
            dayLabel.setStyle(
                "-fx-font-weight:bold;-fx-text-fill:#4b5563;-fx-font-size:13px;");
            VBox headerBox = new VBox(dayLabel);
            headerBox.setAlignment(Pos.CENTER);
            headerBox.setPadding(new Insets(10, 0, 15, 0));
            calendarGrid.add(headerBox, i, 1);
        }

        int startDay    = currentMonth.atDay(1).getDayOfWeek().getValue() % 7;
        int daysInMonth = currentMonth.lengthOfMonth();
        int row = 2, col = startDay;

        for (int day = 1; day <= daysInMonth; day++) {
            LocalDate cellDate = currentMonth.atDay(day);
            calendarGrid.add(createDayBox(day, cellDate, selectedDate), col, row);
            col++;
            if (col > 6) { col = 0; row++; }
        }
    }

    // ── createDayBox — fully dark ─────────────────────────────────────────────
    /**
     * Builds a single calendar cell.
     *
     * Dark colour guide:
     *   default cell bg  →  #0f1220   (dark navy)
     *   default border   →  rgba(255,255,255,0.06)
     *   default day text →  #94a3b8
     *
     *   today            →  bg: linear-gradient(#4f46e5,#6366f1)
     *                        border: #6366f1
     *                        day text: white
     *
     *   selected (not today) → bg: rgba(99,102,241,0.14)
     *                           border: #6366f1, width 2
     *                           day text: #c7d2fe
     *
     *   today + selected →  border: #f87171 (red accent) on top of today gradient
     *
     *   task dot         →  #6366f1 (always indigo, visible on dark bg)
     *   weather label bg →  rgba(255,255,255,0.07) normal | rgba(255,255,255,0.18) today
     */
    private VBox createDayBox(int day, LocalDate date, LocalDate selectedDate) {
        VBox box = new VBox(4);
        box.setAlignment(Pos.TOP_CENTER);
        box.setPadding(new Insets(6));
        box.setPrefSize(100, 100);

        LocalDate today   = LocalDate.now();
        boolean   isToday    = date.equals(today);
        boolean   isSelected = date.equals(selectedDate);

        // ── Day number label ──────────────────────────────────
        Label lbl = new Label(String.valueOf(day));

        // ── Determine box style ───────────────────────────────
        String boxStyle;
        String lblStyle;

        ClassOffPeriod offForDay = classOffForDate(date);
        boolean isClassOff = offForDay != null;

        if (isClassOff) {
            boxStyle =
                "-fx-background-color:linear-gradient(to bottom right,#064e3b,#10b981);" +
                "-fx-border-color:#34d399;-fx-border-width:1;" +
                "-fx-background-radius:8;-fx-border-radius:8;-fx-cursor:hand;" +
                "-fx-effect:dropshadow(gaussian,rgba(16,185,129,0.35),10,0,0,2);";
            lblStyle = "-fx-font-size:14px;-fx-font-weight:bold;-fx-text-fill:#ecfdf5;";

        } else if (isToday && isSelected) {
            // Today AND selected — indigo bg, red border accent
            boxStyle =
                "-fx-background-color:linear-gradient(to bottom right,#4338ca,#6366f1);" +
                "-fx-border-color:#f87171;-fx-border-width:2;" +
                "-fx-background-radius:8;-fx-border-radius:8;-fx-cursor:hand;" +
                "-fx-effect:dropshadow(gaussian,rgba(99,102,241,0.55),10,0,0,2);";
            lblStyle = "-fx-font-size:14px;-fx-font-weight:bold;-fx-text-fill:white;";

        } else if (isToday) {
            // Today only — indigo gradient
            boxStyle =
                "-fx-background-color:linear-gradient(to bottom right,#4338ca,#6366f1);" +
                "-fx-border-color:#6366f1;-fx-border-width:1;" +
                "-fx-background-radius:8;-fx-border-radius:8;-fx-cursor:hand;" +
                "-fx-effect:dropshadow(gaussian,rgba(99,102,241,0.45),10,0,0,2);";
            lblStyle = "-fx-font-size:14px;-fx-font-weight:bold;-fx-text-fill:white;";

        } else if (isSelected) {
            // Selected only — subtle indigo tint
            boxStyle =
                "-fx-background-color:rgba(99,102,241,0.14);" +
                "-fx-border-color:#6366f1;-fx-border-width:2;" +
                "-fx-background-radius:8;-fx-border-radius:8;-fx-cursor:hand;";
            lblStyle = "-fx-font-size:14px;-fx-font-weight:bold;-fx-text-fill:#c7d2fe;";

        } else {
            // Default dark cell
            boxStyle =
                "-fx-background-color:#0f1220;" +
                "-fx-border-color:rgba(255,255,255,0.06);-fx-border-width:1;" +
                "-fx-background-radius:8;-fx-border-radius:8;-fx-cursor:hand;";
            lblStyle = "-fx-font-size:14px;-fx-font-weight:bold;-fx-text-fill:#94a3b8;";
        }

        box.setStyle(boxStyle);
        lbl.setStyle(lblStyle);

        // ── Top row: day number [spacer] [weather] ────────────
        HBox topRow = new HBox();
        topRow.setAlignment(Pos.TOP_LEFT);

        boolean hasWeather = isWeatherEnabled
                && weeklyWeather != null
                && weeklyWeather.containsKey(date.toString());

        if (hasWeather) {
            WeatherService.DailyWeather w = weeklyWeather.get(date.toString());
            Label weatherLbl = new Label(w.emoji() + " " + w.maxTemp());

            String wBg  = isToday ? "rgba(255,255,255,0.18)" : "rgba(255,255,255,0.07)";
            String wFg  = isToday ? "white"                  : "#64748b";
            weatherLbl.setStyle(
                "-fx-font-size:10px;-fx-text-fill:" + wFg + ";" +
                "-fx-background-color:" + wBg + ";" +
                "-fx-padding:2 4;-fx-background-radius:3;");

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            topRow.getChildren().addAll(lbl, spacer, weatherLbl);
        } else {
            topRow.getChildren().add(lbl);
            topRow.setAlignment(Pos.TOP_CENTER);
        }

        box.getChildren().add(topRow);

        if (isClassOff) {
            Label badge = new Label("OFF");
            badge.setStyle(
                "-fx-font-size:9px;-fx-font-weight:bold;" +
                "-fx-text-fill:#ecfdf5;-fx-background-color:rgba(6,95,70,0.9);" +
                "-fx-padding:1 4;-fx-background-radius:4;");
            box.getChildren().add(badge);
        }

        // ── Task dot ──────────────────────────────────────────
        List<StudyTask> tasksForThisDay = allTasks.stream()
            .filter(t -> t.date() != null && t.date().equals(date.toString()))
            .filter(t -> !(isClassOff && "ROUTINE".equals(t.type())))
            .collect(Collectors.toList());

        if (!tasksForThisDay.isEmpty()) {
            // Always visible — green if class off
            Circle dot = new Circle(3, Color.web(isClassOff ? "#22c55e" : "#6366f1"));
            box.getChildren().add(dot);
        }

        // ── Click handler — LOGIC UNCHANGED ──────────────────
        box.setOnMouseClicked(e -> {
            this.selectedDate = date;
            drawCalendar(this.selectedDate);
            if (onDateClickedCallback != null) onDateClickedCallback.accept(date);
            openClimateAndTaskPopup(date, tasksForThisDay);
        });

        return box;
    }

    // ── openClimateAndTaskPopup ───────────────────────────────────────────────
    // LOGIC UNCHANGED — only ScrollPane viewport fix applied to remove white bg
    private void openClimateAndTaskPopup(LocalDate date, List<StudyTask> tasks) {
        VBox root = new VBox(15);
        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color:#0f1117;");

        // Header
        Label dateLabel = new Label("📅 " + date.getMonth().name()
                + " " + date.getDayOfMonth() + ", " + date.getYear());
        dateLabel.setStyle(
            "-fx-font-size:18px;-fx-font-weight:bold;-fx-text-fill:#e2e8f0;");
        root.getChildren().add(dateLabel);

        ClassOffPeriod offForDay = classOffForDate(date);
        if (offForDay != null) {
            Label offBanner = new Label("✅ Varsity Off — Classes Cancelled");
            offBanner.setStyle(
                "-fx-background-color:#064e3b;-fx-text-fill:#d1fae5;" +
                "-fx-padding:6 10;-fx-background-radius:6;-fx-font-weight:bold;");
            root.getChildren().add(offBanner);
        }

        // Current weather (accurate)
        WeatherService.CurrentWeather current = weatherService.getCurrentWeather();
        if (current != null) {
            VBox currentBox = new VBox(8);
            currentBox.setStyle(
                "-fx-background-color:#0f172a;-fx-padding:12;" +
                "-fx-background-radius:8;-fx-border-color:#1e293b;-fx-border-radius:8;");
            Label curTitle = new Label("🌤️ Current Weather");
            curTitle.setStyle("-fx-text-fill:#93c5fd;-fx-font-weight:bold;");
            Label curMain = new Label(current.emoji() + " " + current.condition());
            curMain.setStyle("-fx-text-fill:#e2e8f0;-fx-font-size:14px;-fx-font-weight:bold;");
            Label curTemp = new Label("🌡️ Temp: " + current.temperature());
            curTemp.setStyle("-fx-text-fill:#e2e8f0;");
            Label curHum = new Label("💧 Humidity: " + current.humidity());
            curHum.setStyle("-fx-text-fill:#e2e8f0;");
            Label curPrecip = new Label("🌧️ Precipitation: " + current.precipitationProb());
            curPrecip.setStyle("-fx-text-fill:#e2e8f0;");
            Label curWind = new Label("🌬️ Wind: " + current.windSpeed());
            curWind.setStyle("-fx-text-fill:#e2e8f0;");
            Label curRain = new Label("🌧️ " + current.rainDetails());
            curRain.setStyle("-fx-text-fill:#94a3b8;");
            currentBox.getChildren().addAll(curTitle, curMain, curTemp, curHum, curPrecip, curWind, curRain);
            root.getChildren().add(currentBox);
        }

        // Climate section
        boolean hasWeather = isWeatherEnabled
                && weeklyWeather != null
                && weeklyWeather.containsKey(date.toString());
        if (hasWeather) {
            WeatherService.DailyWeather w = weeklyWeather.get(date.toString());

            VBox weatherBox = new VBox(12);
            weatherBox.setStyle(
                "-fx-background-color:#1e2738;-fx-padding:15;" +
                "-fx-background-radius:8;-fx-border-color:#2d3748;-fx-border-radius:8;");

            HBox topInfo = new HBox(15);
            topInfo.setAlignment(Pos.CENTER_LEFT);
            Label emoji = new Label(w.emoji());
            emoji.setStyle("-fx-font-size:40px;");
            VBox temps = new VBox(3);
            Label cond = new Label(w.condition());
            cond.setStyle(
                "-fx-font-size:16px;-fx-font-weight:bold;-fx-text-fill:#3b82f6;");
            Label tLabel = new Label("🌡️ Max: " + w.maxTemp() + "  |  Min: " + w.minTemp());
            tLabel.setStyle("-fx-text-fill:#e2e8f0;-fx-font-size:13px;");
            temps.getChildren().addAll(cond, tLabel);
            topInfo.getChildren().addAll(emoji, temps);
            weatherBox.getChildren().add(topInfo);

            GridPane grid = new GridPane();
            grid.setHgap(20); grid.setVgap(8);
            grid.add(createDetailLabel("🌧️ Rain:",     w.rainChance()), 0, 0);
            grid.add(createDetailLabel("🌬️ Wind:",     w.windSpeed()),  1, 0);
            grid.add(createDetailLabel("☀️ UV Index:", w.uvIndex()),    0, 1);
            grid.add(createDetailLabel("🌅 Sunrise:",  w.sunrise()),    1, 1);
            grid.add(createDetailLabel("🌇 Sunset:",   w.sunset()),     0, 2);
            weatherBox.getChildren().add(grid);
            root.getChildren().add(weatherBox);
        }

        // Tasks section
        Label taskTitle = new Label("📌 Planned Tasks");
        taskTitle.setStyle(
            "-fx-font-size:14px;-fx-font-weight:bold;" +
            "-fx-text-fill:#cbd5e1;-fx-padding:5 0 0 0;");
        root.getChildren().add(taskTitle);

        VBox tasksBox = new VBox(8);
        if (tasks.isEmpty()) {
            Label noTask = new Label("🎉 No tasks planned. Take a break!");
            noTask.setStyle(
                "-fx-text-fill:#10b981;-fx-font-size:13px;-fx-padding:10;" +
                "-fx-background-color:#064e3b;-fx-background-radius:6;");
            tasksBox.getChildren().add(noTask);
        } else {
            for (StudyTask t : tasks) {
                VBox tBox = new VBox(3);
                tBox.setStyle(
                    "-fx-background-color:#161b27;-fx-padding:12;" +
                    "-fx-background-radius:6;-fx-border-color:#252d3d;-fx-border-radius:6;");
                Label tName = new Label("🔹 " + t.title());
                tName.setStyle(
                    "-fx-font-weight:bold;-fx-text-fill:#e2e8f0;-fx-font-size:13px;");
                Label tTime = new Label(
                    "⏰ " + (t.startTime() != null ? t.startTime() : "Anytime"));
                tTime.setStyle("-fx-text-fill:#64748b;-fx-font-size:11px;");
                tBox.getChildren().addAll(tName, tTime);
                tasksBox.getChildren().add(tBox);
            }
        }

        // ScrollPane — VIEWPORT FIX: both -fx-background and -fx-background-color
        ScrollPane scroll = new ScrollPane(tasksBox);
        scroll.setFitToWidth(true);
        scroll.setPrefHeight(150);
        scroll.setStyle(
            "-fx-background:#0f1117;" +
            "-fx-background-color:#0f1117;" +
            "-fx-border-color:transparent;");
        root.getChildren().add(scroll);

        // Close button
        Button closeBtn = new Button("Close");
        closeBtn.setStyle(
            "-fx-background-color:#374151;-fx-text-fill:white;" +
            "-fx-cursor:hand;-fx-font-weight:bold;" +
            "-fx-padding:8 20;-fx-background-radius:6;");
        HBox bottom = new HBox(closeBtn);
        bottom.setAlignment(Pos.CENTER_RIGHT);
        root.getChildren().add(bottom);

        Stage popup = PopupHelper.create(
            window(),
            "📅 Details for " + date.toString(),
            root,
            340, 280,
            400, 550
        );
        closeBtn.setOnAction(e -> popup.close());
        popup.show();
    }

    // ── helpers ───────────────────────────────────────────────────────────────
    private HBox createDetailLabel(String iconAndTitle, String value) {
        Label title = new Label(iconAndTitle + " ");
        title.setStyle("-fx-text-fill:#64748b;-fx-font-size:12px;");
        Label val = new Label(value);
        val.setStyle("-fx-text-fill:#e2e8f0;-fx-font-size:12px;-fx-font-weight:bold;");
        return new HBox(title, val);
    }

    private ClassOffPeriod classOffForDate(LocalDate date) {
        if (date == null || classOffPeriods == null) return null;
        for (ClassOffPeriod p : classOffPeriods) {
            if (p.startDate() == null || p.endDate() == null) continue;
            if (!date.isBefore(p.startDate()) && !date.isAfter(p.endDate())) return p;
        }
        return null;
    }
}
