package com.scholar.controller;

import com.scholar.api.GameExtension;
import com.scholar.service.AuthService;
import com.scholar.util.NewPopupHelper;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.springframework.stereotype.Component;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

@Component
public class GameZoneController {

    @FXML private VBox installedGamesList;
    @FXML private StackPane gamePlayContainer;
    @FXML private Label activeGameNameLbl;
    @FXML private Button stopGameBtn;

    private List<GameExtension> loadedGames = new ArrayList<>();
    private GameExtension currentGame = null;

    private final Path GAMES_DIR = Paths.get(System.getProperty("user.home"), ".scholargrid", "games");

    @FXML
    public void initialize() {
        // 🌟 FIX: লগআউট/লগইন করার পর মেমোরি ক্লিয়ার করে দেওয়া হচ্ছে 
        // যাতে গেমগুলো স্কিপ না করে নতুন করে স্ক্রিনে ড্র হয়।
        loadedGames.clear();
        currentGame = null;
        
        if (installedGamesList != null) {
            installedGamesList.getChildren().clear();
        }
        if (gamePlayContainer != null) {
            gamePlayContainer.getChildren().clear();
        }
        if (activeGameNameLbl != null) {
            activeGameNameLbl.setText("No Game Selected");
        }
        if (stopGameBtn != null) {
            stopGameBtn.setVisible(false);
        }

        // 🌟 Background Thread-এ গেম ফোল্ডার চেক করা হচ্ছে
        new Thread(() -> {
            try {
                if (!Files.exists(GAMES_DIR)) {
                    Files.createDirectories(GAMES_DIR);
                }
                loadSavedGamesAuto();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void loadSavedGamesAuto() {
        File folder = GAMES_DIR.toFile();
        File[] listOfFiles = folder.listFiles((dir, name) -> name.endsWith(".jar"));
        
        if (listOfFiles != null) {
            for (File file : listOfFiles) {
                Platform.runLater(() -> loadGameFromJar(file, false)); 
            }
        }
    }

    @FXML
    public void onUploadGameClick(ActionEvent event) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Upload Game Extension");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Java ARchive (*.jar)", "*.jar"));
        
        File jarFile = fileChooser.showOpenDialog(installedGamesList.getScene().getWindow());
        if (jarFile != null) {
            try {
                Path targetPath = GAMES_DIR.resolve(jarFile.getName());
                Files.copy(jarFile.toPath(), targetPath, StandardCopyOption.REPLACE_EXISTING);
                loadGameFromJar(targetPath.toFile(), true);
            } catch (Exception e) {
                e.printStackTrace();
                NewPopupHelper.showError(installedGamesList.getScene().getWindow(), "Error", "Failed to save game securely.");
            }
        }
    }

    private void loadGameFromJar(File jarFile, boolean showToast) {
        try {
            URLClassLoader childLoader = new URLClassLoader(
                    new URL[]{jarFile.toURI().toURL()},
                    this.getClass().getClassLoader()
            );

            ServiceLoader<GameExtension> serviceLoader = ServiceLoader.load(GameExtension.class, childLoader);
            
            boolean found = false;
            for (GameExtension game : serviceLoader) {
                boolean alreadyExists = loadedGames.stream().anyMatch(g -> g.getGameName().equals(game.getGameName()));
                if (!alreadyExists) {
                    loadedGames.add(game);
                    
                    // 🌟 গেমের পাশাপাশি .jar ফাইলটির রেফারেন্সও পাঠানো হচ্ছে
                    addGameToSidebar(game, jarFile, childLoader);
                    found = true;
                }
            }

            if (found && showToast) {
                NewPopupHelper.showToast(installedGamesList.getScene().getWindow(), "✅ Game Installed Successfully!");
            } else if (!found && showToast) {
                NewPopupHelper.showError(installedGamesList.getScene().getWindow(), "Invalid Extension", 
                        "This .jar file does not contain a valid ScholarGrid GameExtension.");
                Files.deleteIfExists(jarFile.toPath());
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // 🌟 ফাইলে ডিলিট করার অপশন যুক্ত করা হলো
    private void addGameToSidebar(GameExtension game, File jarFile, URLClassLoader classLoader) {
        VBox card = new VBox(8);
        card.setStyle("-fx-background-color:#1e2738; -fx-padding:12; -fx-background-radius:8; -fx-border-color:#334155; -fx-border-radius:8;");
        
        Label name = new Label(game.getGameName() + " (v" + game.getVersion() + ")");
        name.setStyle("-fx-font-weight:bold; -fx-text-fill:#e2e8f0; -fx-font-size:14px;");
        
        Label dev = new Label("By " + game.getDeveloperName());
        dev.setStyle("-fx-text-fill:#94a3b8; -fx-font-size:11px;");
        
        HBox bottom = new HBox(10);
        bottom.setAlignment(Pos.CENTER_RIGHT);

        // 🌟 শুধুমাত্র অ্যাডমিনদের জন্য আনইনস্টল বাটন
        boolean isAdmin = "admin".equals(AuthService.CURRENT_USER_ROLE);
        
        if (isAdmin) {
            Button uninstallBtn = new Button("🗑️ Uninstall");
            uninstallBtn.setStyle("-fx-background-color:rgba(239,68,68,0.15); -fx-text-fill:#f87171; -fx-font-weight:bold; -fx-background-radius:6; -fx-padding:6 12; -fx-cursor:hand; -fx-font-size:11px;");
            
            uninstallBtn.setOnAction(e -> {
                NewPopupHelper.showConfirm(installedGamesList.getScene().getWindow(), "Uninstall Game", 
                    "Are you sure you want to completely remove '" + game.getGameName() + "'?", 
                    () -> {
                        try {
                            // ১. যদি গেমটি রানিং থাকে, তবে আগে স্টপ করা
                            if (currentGame != null && currentGame.getGameName().equals(game.getGameName())) {
                                onStopGameClick(null);
                            }
                            
                            // ২. UI থেকে সরিয়ে ফেলা
                            installedGamesList.getChildren().remove(card);
                            loadedGames.remove(game);
                            
                            // ৩. ClassLoader ক্লোজ করা (যাতে উইন্ডোজ ফাইলটিকে লক করে না রাখে)
                            classLoader.close();
                            
                            // ৪. হার্ডডিস্ক থেকে .jar ফাইলটি ডিলিট করে দেওয়া
                            if (jarFile.exists()) {
                                Files.delete(jarFile.toPath());
                            }
                            
                            NewPopupHelper.showToast(installedGamesList.getScene().getWindow(), "🗑️ Game Uninstalled!");
                            
                        } catch (Exception ex) {
                            ex.printStackTrace();
                            // যদি উইন্ডোজের কারণে ফাইল লক থাকে, তবে অ্যাপ বন্ধ হওয়ার সময় ডিলিট হবে
                            jarFile.deleteOnExit(); 
                            NewPopupHelper.showToast(installedGamesList.getScene().getWindow(), "⚠️ Game removed from UI. File will be deleted on exit.");
                        }
                    }
                );
            });
            
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            bottom.getChildren().addAll(uninstallBtn, spacer);
        }

        Button playBtn = new Button("▶ Play");
        playBtn.setStyle("-fx-background-color:#2563eb; -fx-text-fill:white; -fx-font-weight:bold; -fx-background-radius:6; -fx-padding:6 16; -fx-cursor:hand;");
        playBtn.setOnAction(e -> launchGame(game));

        bottom.getChildren().add(playBtn);
        card.getChildren().addAll(name, dev, bottom);
        
        // Hover effects
        card.setOnMouseEntered(e -> card.setStyle("-fx-background-color:#232d42; -fx-padding:12; -fx-background-radius:8; -fx-border-color:#6366f1; -fx-border-radius:8;"));
        card.setOnMouseExited(e -> card.setStyle("-fx-background-color:#1e2738; -fx-padding:12; -fx-background-radius:8; -fx-border-color:#334155; -fx-border-radius:8;"));
        
        installedGamesList.getChildren().add(card);
    }

    private void launchGame(GameExtension game) {
        if (currentGame != null) {
            currentGame.stopGame();
        }

        currentGame = game;
        activeGameNameLbl.setText("🕹️ " + game.getGameName());
        stopGameBtn.setVisible(true);
        
        gamePlayContainer.getChildren().clear();
        Node gameUI = game.getGameUI();
        
        if (gameUI instanceof javafx.scene.layout.Region) {
            ((javafx.scene.layout.Region) gameUI).setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        }
        
        gamePlayContainer.getChildren().add(gameUI);
        Platform.runLater(() -> game.startGame());
    }

    @FXML
    public void onStopGameClick(ActionEvent event) {
        if (currentGame != null) {
            currentGame.stopGame();
            currentGame = null;
        }
        
        activeGameNameLbl.setText("No Game Selected");
        stopGameBtn.setVisible(false);
        gamePlayContainer.getChildren().clear();
        
        VBox placeholder = new VBox(10);
        placeholder.setAlignment(Pos.CENTER);
        Label icon = new Label("🕹️"); icon.setStyle("-fx-font-size:40px;");
        Label text = new Label("Game Stopped."); text.setStyle("-fx-text-fill:#64748b; -fx-font-size:14px;");
        placeholder.getChildren().addAll(icon, text);
        
        gamePlayContainer.getChildren().add(placeholder);
    }
}