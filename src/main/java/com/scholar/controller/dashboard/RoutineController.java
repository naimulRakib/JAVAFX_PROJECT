package com.scholar.controller.dashboard;

import com.scholar.service.DatabaseConnection;
import com.scholar.service.AuthService;
import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * ROUTINE CONTROLLER — Class schedule & announcements from DB
 * Path: src/main/java/com/scholar/controller/dashboard/RoutineController.java
 */
@Component
public class RoutineController {

    private GridPane routineGrid;
    private VBox announcementList;

    public void init(GridPane routineGrid, VBox announcementList) {
        this.routineGrid     = routineGrid;
        this.announcementList = announcementList;
    }

    // ----------------------------------------------------------
    // LOAD CLASS ROUTINE
    // ----------------------------------------------------------
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
                        routineGrid.add(new Label(r[0]), 0, row);
                        routineGrid.add(new Label(r[1]), 1, row);
                        routineGrid.add(new Label(r[2]), 2, row);
                        routineGrid.add(new Label(r[3]), 3, row);
                        row++;
                    }
                });
            } catch (SQLException e) { e.printStackTrace(); }
        }).start();
    }

    // ----------------------------------------------------------
    // LOAD ANNOUNCEMENTS
    // ----------------------------------------------------------
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
                    card.setStyle("-fx-background-color: white; -fx-padding: 10; -fx-background-radius: 5; "
                        + "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.1), 5, 0, 0, 0);");
                    Label t = new Label(rs.getString("title"));
                    t.setStyle("-fx-font-weight: bold;");
                    Label c = new Label(rs.getString("content"));
                    c.setWrapText(true);
                    card.getChildren().addAll(t, c);
                    cards.add(card);
                }
                Platform.runLater(() -> announcementList.getChildren().addAll(cards));
            } catch (SQLException e) { e.printStackTrace(); }
        }).start();
    }

    // ----------------------------------------------------------
    // SAVE ROUTINE ROW
    // ----------------------------------------------------------
    public void saveRoutineToDB(String day, String course, String time, String room) {
        String sql = "INSERT INTO routines (day_name, course_code, time_slot, room_no, channel_id) "
                   + "VALUES (?, ?, ?, ?, ?)";
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
}