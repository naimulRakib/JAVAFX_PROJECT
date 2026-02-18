package com.scholar;

import javafx.application.Application;
import javafx.application.HostServices;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class Main extends Application {

    public static HostServices hostServices;

    @Override
    public void start(Stage stage) throws IOException {
        hostServices = getHostServices();

        // LOAD LOGIN VIEW FIRST
        FXMLLoader fxmlLoader = new FXMLLoader(Main.class.getResource("/com/scholar/view/login.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 500, 600);
        
        stage.setTitle("Scholar Grid - Login");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}