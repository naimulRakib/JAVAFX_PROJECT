package com.scholar.controller.dashboard;

import com.scholar.service.DatabaseConnection;
import com.scholar.service.AuthService;
import com.scholar.util.PopupHelper;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * ROUTINE CONTROLLER — Class schedule & announcements from DB
 *
 * CHANGES (UI only — all logic untouched):
 *  • loadClassAnnouncements(): replaced the fragile setOnMouseEntered/Exited
 *    style-string .replace() approach with two static style constants
 *    (CARD_NORMAL / CARD_HOVER). String-replace on inline styles is error-prone
 *    if the style string gets reordered or reformatted by any tool. Using two
 *    pre-built constants is O(1) assignment and never breaks.
 *
 * Path: src/main/java/com/scholar/controller/dashboard/RoutineController.java
 */
@Component
public class RoutineController {

    private GridPane routineGrid;
    private VBox     announcementList;

    // ── Announcement card styles (static — no runtime string building) ────────
    //  Normal: dark #0f1020 bg, subtle dark border
    //  Hover:  slightly lighter bg + indigo left-border accent
    private static final String CARD_NORMAL =
        "-fx-background-color:#0f1020;" +
        "-fx-padding:14;" +
        "-fx-background-radius:10;" +
        "-fx-border-color:#1e2540;" +
        "-fx-border-width:0 0 0 3;" +
        "-fx-border-radius:10;" +
        "-fx-cursor:hand;" +
        "-fx-effect:dropshadow(gaussian,rgba(0,0,0,0.45),8,0,0,2);";

    private static final String CARD_HOVER =
        "-fx-background-color:#16182e;" +
        "-fx-padding:14;" +
        "-fx-background-radius:10;" +
        "-fx-border-color:#4f46e5;" +
        "-fx-border-width:0 0 0 3;" +
        "-fx-border-radius:10;" +
        "-fx-cursor:hand;" +
        "-fx-effect:dropshadow(gaussian,rgba(99,102,241,0.25),10,0,0,2);";

    public void init(GridPane routineGrid, VBox announcementList) {
        this.routineGrid      = routineGrid;
        this.announcementList = announcementList;
    }

    // ── loadClassRoutine ──────────────────────────────────────────────────────
    public void loadClassRoutine() {
        if (routineGrid == null) return;
        routineGrid.getChildren().clear();
        String sql = "SELECT * FROM routines WHERE channel_id = ? ORDER BY time_slot ASC";
        new Thread(() -> {
            try (Connection conn = DatabaseConnection.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, AuthService.CURRENT_CHANNEL_ID);
                ResultSet rs = pstmt.executeQuery();
                List<String[]> data = new ArrayList<>();
                while (rs.next()) {
                    data.add(new String[]{
                        rs.getString("day_name"),
                        rs.getString("course_code"),
                        rs.getString("time_slot"),
                        rs.getString("room_no")
                    });
                }
                Platform.runLater(() -> {
                    int row = 1;
                    for (String[] r : data) {
                        Label dayLbl    = new Label(r[0]);
                        Label courseLbl = new Label(r[1]);
                        Label timeLbl   = new Label(r[2]);
                        Label roomLbl   = new Label(r[3]);

                        String cellStyle =
                            "-fx-text-fill:#cbd5e1;" +
                            "-fx-font-size:13px;" +
                            "-fx-padding:4 8;";
                        dayLbl.setStyle(cellStyle);
                        courseLbl.setStyle(
                            cellStyle + "-fx-font-weight:bold;-fx-text-fill:#c7d2fe;");
                        timeLbl.setStyle(
                            cellStyle + "-fx-text-fill:#94a3b8;");
                        roomLbl.setStyle(
                            cellStyle + "-fx-text-fill:#64748b;");

                        // Pointer cursor on every cell
                        for (Label lbl : new Label[]{dayLbl, courseLbl, timeLbl, roomLbl}) {
                            lbl.setStyle(lbl.getStyle() + "-fx-cursor:hand;");
                        }

                        // Click any cell → show routine detail popup (LOGIC UNCHANGED)
                        final String[] rowData = r;
                        for (Label lbl : new Label[]{dayLbl, courseLbl, timeLbl, roomLbl}) {
                            lbl.setOnMouseClicked(e ->
                                showRoutineDetailPopup(
                                    rowData[0], rowData[1], rowData[2], rowData[3]));
                        }

                        routineGrid.add(dayLbl,    0, row);
                        routineGrid.add(courseLbl, 1, row);
                        routineGrid.add(timeLbl,   2, row);
                        routineGrid.add(roomLbl,   3, row);
                        row++;
                    }
                });
            } catch (SQLException e) { e.printStackTrace(); }
        }).start();
    }

    // ── loadClassAnnouncements ────────────────────────────────────────────────
    /**
     * HOVER FIX: the old code called
     *   card.setStyle( card.getStyle().replace(A, B).replace(C, D) )
     * which breaks silently if the style string is ever reformatted.
     *
     * New approach: two immutable constants (CARD_NORMAL / CARD_HOVER).
     * setOnMouseEntered → card.setStyle(CARD_HOVER)
     * setOnMouseExited  → card.setStyle(CARD_NORMAL)
     * Clean, O(1), impossible to corrupt.
     */
    public void loadClassAnnouncements() {
        if (announcementList == null) return;
        announcementList.getChildren().clear();
        String sql = "SELECT * FROM announcements WHERE channel_id = ? ORDER BY created_at DESC";
        new Thread(() -> {
            try (Connection conn = DatabaseConnection.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, AuthService.CURRENT_CHANNEL_ID);
                ResultSet rs = pstmt.executeQuery();
                List<VBox> cards = new ArrayList<>();
                while (rs.next()) {
                    VBox card = new VBox(5);
                    card.setStyle(CARD_NORMAL);

                    // Stable hover: assign the constant directly, no string surgery
                    card.setOnMouseEntered(e -> card.setStyle(CARD_HOVER));
                    card.setOnMouseExited( e -> card.setStyle(CARD_NORMAL));

                    Label t = new Label(rs.getString("title"));
                    t.setStyle(
                        "-fx-font-weight:bold;" +
                        "-fx-font-size:14px;" +
                        "-fx-text-fill:#c7d2fe;");

                    Label c = new Label(rs.getString("content"));
                    c.setWrapText(true);
                    c.setStyle(
                        "-fx-text-fill:#94a3b8;" +
                        "-fx-font-size:13px;" +
                        "-fx-padding:4 0 0 0;");

                    card.getChildren().addAll(t, c);

                    // Click card → show announcement detail popup (LOGIC UNCHANGED)
                    final String cardTitle   = rs.getString("title");
                    final String cardContent = rs.getString("content");
                    card.setOnMouseClicked(e ->
                        showAnnouncementDetailPopup(cardTitle, cardContent));

                    cards.add(card);
                }
                Platform.runLater(() -> announcementList.getChildren().addAll(cards));
            } catch (SQLException e) { e.printStackTrace(); }
        }).start();
    }

    // ── saveRoutineToDB ───────────────────────────────────────────────────────
    public void saveRoutineToDB(String day, String course, String time, String room) {
        String sql = "INSERT INTO routines (day_name, course_code, time_slot, room_no, channel_id) " +
                     "VALUES (?, ?, ?, ?, ?)";
        new Thread(() -> {
            try (Connection conn = DatabaseConnection.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, day);
                pstmt.setString(2, course);
                pstmt.setString(3, time);
                pstmt.setString(4, room);
                pstmt.setInt(5, AuthService.CURRENT_CHANNEL_ID);
                pstmt.executeUpdate();
                Platform.runLater(this::loadClassRoutine);
            } catch (SQLException e) { e.printStackTrace(); }
        }).start();
    }

    // ── showRoutineDetailPopup ────────────────────────────────────────────────
    private void showRoutineDetailPopup(String day, String course, String time, String room) {
        VBox content = new VBox(0);
        content.setStyle("-fx-background-color:#0d0f1a;");

        VBox header = new VBox(4);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setStyle(
            "-fx-background-color:linear-gradient(to right,#1e1b4b,#312e81);" +
            "-fx-padding:20 24 18 24;");
        Label badge = new Label("📅  Class Schedule");
        badge.setStyle(
            "-fx-font-size:11px;-fx-text-fill:#a5b4fc;-fx-font-weight:bold;");
        Label courseLbl = new Label(course);
        courseLbl.setStyle(
            "-fx-font-size:20px;-fx-font-weight:bold;" +
            "-fx-text-fill:white;-fx-wrap-text:true;");
        header.getChildren().addAll(badge, courseLbl);

        VBox body = new VBox(0);
        body.setStyle("-fx-padding:20 24 24 24;-fx-background-color:#0d0f1a;");

        body.getChildren().add(buildDetailRow("📆  Day",  day));
        body.getChildren().add(buildSep());
        body.getChildren().add(buildDetailRow("🕒  Time", time));
        body.getChildren().add(buildSep());
        body.getChildren().add(buildDetailRow("📍  Room",
            (room == null || room.isBlank()) ? "—" : room));

        content.getChildren().addAll(header, body);

        javafx.stage.Stage popup = PopupHelper.create(
            resolveOwner(), course,
            content,
            320, 240, 400, 290
        );
        popup.show();
    }

    // ── showAnnouncementDetailPopup ───────────────────────────────────────────
    private void showAnnouncementDetailPopup(String title, String fullContent) {
        VBox content = new VBox(0);
        content.setStyle("-fx-background-color:#0d0f1a;");

        VBox header = new VBox(4);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setStyle(
            "-fx-background-color:linear-gradient(to right,#1a0e0e,#4c0519);" +
            "-fx-padding:20 24 18 24;");
        Label badge = new Label("📢  Announcement");
        badge.setStyle(
            "-fx-font-size:11px;-fx-text-fill:#fca5a5;-fx-font-weight:bold;");
        Label titleLbl = new Label(title);
        titleLbl.setStyle(
            "-fx-font-size:17px;-fx-font-weight:bold;" +
            "-fx-text-fill:white;-fx-wrap-text:true;");
        titleLbl.setMaxWidth(360);
        titleLbl.setWrapText(true);
        header.getChildren().addAll(badge, titleLbl);

        VBox body = new VBox(12);
        body.setStyle("-fx-padding:18 24 24 24;-fx-background-color:#0d0f1a;");

        Region sepLine = buildSep();
        Label bodyLbl = new Label(fullContent);
        bodyLbl.setWrapText(true);
        bodyLbl.setMaxWidth(360);
        bodyLbl.setStyle(
            "-fx-text-fill:#cbd5e1;" +
            "-fx-font-size:13px;" +
            "-fx-line-spacing:3;");

        body.getChildren().addAll(sepLine, bodyLbl);
        content.getChildren().addAll(header, body);

        double estimatedH = 210 + Math.min(320, (fullContent.length() / 80.0) * 22);

        javafx.stage.Stage popup = PopupHelper.create(
            resolveOwner(), title,
            content,
            340, 210, 440, estimatedH
        );
        popup.show();
    }

    // ── shared popup helpers ──────────────────────────────────────────────────
    private HBox buildDetailRow(String fieldLabel, String value) {
        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setStyle("-fx-padding:12 0;");

        Label key = new Label(fieldLabel);
        key.setMinWidth(90);
        key.setStyle(
            "-fx-text-fill:#475569;" +
            "-fx-font-size:12px;" +
            "-fx-font-weight:bold;");

        Label val = new Label(value);
        val.setWrapText(true);
        val.setStyle(
            "-fx-text-fill:#e2e8f0;" +
            "-fx-font-size:13px;");

        row.getChildren().addAll(key, val);
        return row;
    }

    private Region buildSep() {
        Region line = new Region();
        line.setPrefHeight(1);
        line.setMaxHeight(1);
        line.setStyle("-fx-background-color:#1e2540;");
        return line;
    }

    private Window resolveOwner() {
        if (routineGrid != null && routineGrid.getScene() != null)
            return routineGrid.getScene().getWindow();
        if (announcementList != null && announcementList.getScene() != null)
            return announcementList.getScene().getWindow();
        return null;
    }
}