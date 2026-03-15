package com.scholar.controller;

import com.scholar.model.AIResource;
import com.scholar.model.Question;
import com.scholar.service.AIResourceService;
import com.scholar.service.AuthService;
import com.scholar.service.QuestionBankService;
import com.scholar.service.TelegramService;
import com.scholar.util.PopupHelper;

// যদি আপনার CourseService এবং AuthService তৈরি করা থাকে, তবে নিচের লাইনগুলো আনকমেন্ট করে নেবেন
// import com.scholar.service.CourseService;
// import com.scholar.service.AuthService;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.FileChooser;

import com.scholar.model.ResourceItem;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.awt.Desktop; // ব্রাউজারে লিংক ওপেন করার জন্য
import java.net.URI;
import org.springframework.beans.factory.annotation.Autowired; // 🟢 নতুন
import org.springframework.stereotype.Controller;

import java.io.File;
import java.util.List;
import org.json.JSONObject;
@Controller
public class QuestionBankController {

    // --- UI উপাদান (FXML) ---
    @FXML private ListView<String> fileListView; 
    @FXML private TextArea logArea;              
    @FXML private Button processBtn;             
    @FXML private Button downloadBtn;            
    @FXML private Label statusLabel;

    @FXML private TextField resourceNameField;
    @FXML private TableView<ResourceItem> resourceTable;
    @FXML private TableColumn<ResourceItem, String> nameCol;
    @FXML private TableColumn<ResourceItem, String> dateCol;
    @FXML private TableColumn<ResourceItem, Void> actionCol;


    // --- সার্ভিস ---
    @Autowired private QuestionBankService qbService;
    @Autowired private TelegramService telegramService;
    @Autowired private AuthService authService;
    @Autowired private AIResourceService aiResourceService;
    
    // private final CourseService courseService = new CourseService(); // ডাটাবেসের জন্য এটি আনকমেন্ট করবেন

    private List<File> selectedFiles;
    private List<Question> extractedQuestions;
    private static final String RESOURCE_TYPE_QUESTION_BANK = "QUESTION_BANK";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    @FXML
    public void initialize() {
        downloadBtn.setVisible(false); // শুরুতে ডাউনলোড বাটন লুকানো থাকবে


        downloadBtn.setVisible(false);
        resourceNameField.setVisible(false);

        // টেবিলের কলাম সেটআপ
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));
        dateCol.setCellValueFactory(new PropertyValueFactory<>("date"));
        
        // অ্যাকশন কলামে "Download" ও "Preview" বাটন যুক্ত করা
        setupActionColumn();

        loadQuestionBankResources();
    }

    // ১. ফাইল সিলেক্ট করা
    @FXML
    public void onSelectFilesClick() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select CT Question Papers (PDF)");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF Files", "*.pdf"));
        
        selectedFiles = chooser.showOpenMultipleDialog(processBtn.getScene().getWindow());

        if (selectedFiles != null && !selectedFiles.isEmpty()) {
            fileListView.getItems().clear();
            for (File f : selectedFiles) {
                fileListView.getItems().add("📄 " + f.getName());
            }
            log("✅ Selected " + selectedFiles.size() + " files. Ready to process.");
            processBtn.setDisable(false);
        }
    }

    // ২. AI দিয়ে প্রসেস করা 
    @FXML
    public void onProcessClick() {
        if (selectedFiles == null || selectedFiles.isEmpty()) return;

        processBtn.setDisable(true);
        log("🚀 Starting AI Analysis... Please wait.");

        new Thread(() -> {
            try {
                Platform.runLater(() -> log("📖 Reading PDF contents..."));
                String rawText = qbService.readPdfs(selectedFiles);

                Platform.runLater(() -> log("🤖 AI is extracting questions..."));
                extractedQuestions = qbService.analyzeQuestions(rawText);

                Platform.runLater(() -> {
                    log("✨ Success! Found " + extractedQuestions.size() + " unique questions.");
                    log("📝 Topics found: " + extractedQuestions.stream().map(Question::topic).distinct().toList());
                    downloadBtn.setVisible(true); 
                    processBtn.setDisable(false);
                });

            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> {
                    log("❌ Error: " + e.getMessage());
                    processBtn.setDisable(false);
                });
            }
        }).start();
    }

    // ৩. নতুন পিডিএফ জেনারেট, আপলোড এবং ডাটাবেসে সেভ করা
    @FXML
    public void onDownloadPdfClick() {
        if (extractedQuestions == null || extractedQuestions.isEmpty()) return;

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save Question Bank");
        chooser.setInitialFileName("ScholarGrid_Master_QB.pdf");
        File dest = chooser.showSaveDialog(downloadBtn.getScene().getWindow());

        if (dest != null) {
            // পিডিএফ তৈরি করা
            String result = qbService.createPdfDocument(extractedQuestions, dest.getAbsolutePath());
            log(result);

            // টেলিগ্রামে আপলোড এবং ডাটাবেস এন্ট্রি (Background Thread)
            new Thread(() -> {
                Platform.runLater(() -> log("☁️ Uploading to Telegram Cloud..."));
                String fileId = telegramService.uploadToCloud(dest);

                if (fileId != null) {
                    Platform.runLater(() -> {
                        log("✅ Uploaded to Cloud! File ID: " + fileId);
                        log("💾 Saving link to Database...");

                        String resName = resourceNameField.getText().isEmpty() ? "Master Question Bank" : resourceNameField.getText();
                        String today = LocalDate.now().format(DATE_FMT);
                        String downloadUrl = telegramService.getFileDownloadUrl(fileId);

                        JSONObject tagJson = new JSONObject();
                        List<String> topics = extractedQuestions.stream()
                            .map(Question::topic)
                            .filter(t -> t != null && !t.isBlank())
                            .distinct()
                            .toList();
                        tagJson.put("topics", topics);

                        AIResource res = new AIResource();
                        res.setTitle(resName);
                        res.setDescription("AI Generated Question Bank");
                        res.setResourceType(RESOURCE_TYPE_QUESTION_BANK);
                        res.setTelegramFileId(fileId);
                        res.setTelegramDownloadUrl(downloadUrl);
                        res.setTags(tagJson.toString());
                        if (AuthService.CURRENT_USER_ID != null) {
                            res.setCreatedByUserId(AuthService.CURRENT_USER_ID);
                        }
                        res.setPublished(true);

                        boolean ok = aiResourceService != null && aiResourceService.saveResource(res);
                        if (ok) {
                            resourceTable.getItems().add(new ResourceItem(resName, today, fileId, downloadUrl));
                            resourceNameField.clear();
                            showSuccess("Resource saved successfully!");
                        } else {
                            showError("Failed to save resource to database.");
                        }

        
                    });
                    Platform.runLater(() -> {
                        log("🎉 Process Complete!");
                        showSuccess("Question Bank PDF has been generated and uploaded to Telegram!");
                    });

                } else {
                    Platform.runLater(() -> log("❌ Telegram Upload Failed."));
                }
            }).start();
        }
    }

    // --- হেল্পার মেথডস ---
    
    private void log(String msg) {
        logArea.appendText(msg + "\n");
    }

    private void showSuccess(String message) {
        PopupHelper.showInfo(resolveOwner(), "Success", message);
    }

    private void showError(String message) {
        PopupHelper.showError(resolveOwner(), "Error", message);
    }


    private void setupActionColumn() {
        actionCol.setCellFactory(param -> new TableCell<>() {
            private final Button previewBtn = new Button("👁 Preview");
            private final Button downloadBtn = new Button("⬇ Download");
            private final HBox pane = new HBox(5, previewBtn, downloadBtn);

            {
                previewBtn.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-cursor: hand;");
                downloadBtn.setStyle("-fx-background-color: #2ecc71; -fx-text-fill: white; -fx-cursor: hand;");

                // প্রিভিউ বাটনের কাজ (ব্রাউজারে ওপেন হবে)
                previewBtn.setOnAction(event -> {
                    ResourceItem item = getTableView().getItems().get(getIndex());
                    String url = resolveDownloadUrl(item);
                    if (url != null) openInBrowser(url);
                    else showError("No download link available for this item.");
                    // নোট: টেলিগ্রাম প্রাইভেট ফাইলের লিংক এভাবেই কাজ করে যদি ইউজার চ্যানেলে জয়েন থাকে।
                });

                // ডাউনলোড বাটনের কাজ
                downloadBtn.setOnAction(event -> {
                    ResourceItem item = getTableView().getItems().get(getIndex());
                    String url = resolveDownloadUrl(item);
                    if (url != null) {
                        openInBrowser(url);
                        showSuccess("Downloading: " + item.getName());
                    } else {
                        showError("No download link available for this item.");
                    }
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : pane);
            }
        });
    }

    // ব্রাউজারে লিংক ওপেন করার হেল্পার মেথড
    private void openInBrowser(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void loadQuestionBankResources() {
        if (resourceTable == null || aiResourceService == null) return;
        new Thread(() -> {
            List<AIResource> list = aiResourceService.getResourcesByType(RESOURCE_TYPE_QUESTION_BANK);
            Platform.runLater(() -> {
                resourceTable.getItems().clear();
                for (AIResource r : list) {
                    String date = r.getCreatedAt() != null
                        ? r.getCreatedAt().toLocalDate().format(DATE_FMT)
                        : "";
                    String fileId = r.getTelegramFileId() != null ? r.getTelegramFileId() : r.getSupabaseFileId();
                    String url = r.getPrimaryDownloadUrl();
                    resourceTable.getItems().add(new ResourceItem(r.getTitle(), date, fileId, url));
                }
            });
        }).start();
    }

    private String resolveDownloadUrl(ResourceItem item) {
        if (item == null) return null;
        if (item.getDownloadUrl() != null && !item.getDownloadUrl().isBlank()) return item.getDownloadUrl();
        if (item.getFileId() != null && !item.getFileId().isBlank()) {
            return "https://t.me/c/YOUR_CHANNEL_ID_HERE/" + item.getFileId();
        }
        return null;
    }

    private javafx.stage.Window resolveOwner() {
        if (processBtn != null && processBtn.getScene() != null) return processBtn.getScene().getWindow();
        if (resourceTable != null && resourceTable.getScene() != null) return resourceTable.getScene().getWindow();
        return null;
    }


}
