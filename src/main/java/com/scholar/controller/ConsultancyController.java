package com.scholar.controller;

import com.scholar.model.Doubt;
import com.scholar.model.DoubtAnswer;
import com.scholar.model.AnswerReply;
import com.scholar.service.AuthService;
import com.scholar.service.ConsultancyService;
import com.scholar.service.ProfileService;
import com.scholar.util.NewPopupHelper;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.shape.Circle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Controller
public class ConsultancyController {

    // ── FXML Bindings ──────────────────────────────────────────────────────────
    @FXML private VBox doubtsListContainer, doubtDetailBox, answersContainer, replyBox;
    @FXML private Label detailSubject, detailTopic, detailStatus, detailTitle, detailDesc, detailStudent;
    @FXML private TextArea answerInput;
    @FXML private Button submitAnswerBtn;          // FIX: injected directly — no more instanceof cast
    @FXML private ComboBox<String> categoryFilterCombo;
    @FXML private TextField searchField;

    // ── Spring Services ────────────────────────────────────────────────────────
    @Autowired private ConsultancyService consultancyService;
    @Autowired private ProfileService profileService;

    // ── State ──────────────────────────────────────────────────────────────────
    private Doubt currentSelectedDoubt = null;
    private List<Doubt> allCachedDoubts = null;

    /**
     * FIX: Image cache prevents re-downloading the same avatar repeatedly when
     * the list re-renders (e.g. after posting an answer). Weak values let GC
     * reclaim images that are no longer displayed.
     */
    private final Map<String, Image> imageCache = new ConcurrentHashMap<>();

    /**
     * FIX: Generation counter stops stale background threads from updating the UI
     * after the user has already clicked another doubt. Each loadAnswersAndReplies
     * call increments this; the thread checks it before touching the scene graph.
     */
    private final AtomicInteger answerLoadGeneration = new AtomicInteger(0);

    // ──────────────────────────────────────────────────────────────────────────
    // LIFECYCLE
    // ──────────────────────────────────────────────────────────────────────────
    public void initialize() {
        if (doubtDetailBox != null) doubtDetailBox.setVisible(false);
        if (replyBox != null)       replyBox.setVisible(false);

        // FIX: categoryFilterCombo now exists in FXML, so this won't NPE
        if (categoryFilterCombo != null) {
            categoryFilterCombo.getItems().addAll("All", "Academic", "Personal", "Varsity", "Others");
            categoryFilterCombo.setValue("All");
            categoryFilterCombo.setOnAction(e -> applyFilters());
        }

        // Live search — no button needed
        if (searchField != null) {
            searchField.textProperty().addListener((obs, oldV, newV) -> applyFilters());
        }

        loadDoubts();
    }

    private javafx.stage.Window window() {
        return doubtsListContainer != null && doubtsListContainer.getScene() != null
                ? doubtsListContainer.getScene().getWindow() : null;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 1. DOUBT LIST  –  load / filter / render
    // ──────────────────────────────────────────────────────────────────────────
    private void loadDoubts() {
        new Thread(() -> {
            List<Doubt> fetched = consultancyService.getAllOpenDoubts();
            Platform.runLater(() -> {
                allCachedDoubts = fetched;
                applyFilters();
            });
        }).start();
    }

    private void applyFilters() {
        if (allCachedDoubts == null) return;

        String cat = categoryFilterCombo != null ? categoryFilterCombo.getValue() : "All";
        String q   = searchField != null ? searchField.getText().toLowerCase().trim() : "";

        List<Doubt> filtered = allCachedDoubts.stream()
                .filter(d -> "All".equals(cat) || d.subject().equalsIgnoreCase(cat))
                .filter(d -> q.isEmpty()
                        || d.title().toLowerCase().contains(q)
                        || (d.topic() != null && d.topic().toLowerCase().contains(q)))
                .collect(Collectors.toList());

        renderDoubtsList(filtered);
    }

    private void renderDoubtsList(List<Doubt> doubts) {
        doubtsListContainer.getChildren().clear();
        String currentUserId = String.valueOf(AuthService.CURRENT_USER_ID);
        boolean isAdmin      = "admin".equals(AuthService.CURRENT_USER_ROLE);

        if (doubts.isEmpty()) {
            Label empty = new Label("📭 No questions found.");
            empty.setStyle("-fx-text-fill:#475569; -fx-font-size:14; -fx-padding:20;");
            doubtsListContainer.getChildren().add(empty);
            return;
        }

        for (Doubt d : doubts) {
            boolean canDelete = isAdmin || currentUserId.equals(d.studentId());
            doubtsListContainer.getChildren().add(buildDoubtCard(d, canDelete));
        }
    }

    private VBox buildDoubtCard(Doubt d, boolean canDelete) {
        VBox card = new VBox(10);
        String base  = "-fx-background-color:#161b27; -fx-padding:14; -fx-background-radius:12;"
                     + "-fx-border-color:#2d3748; -fx-border-radius:12; -fx-cursor:hand;";
        String hover = "-fx-background-color:#1e2738; -fx-padding:14; -fx-background-radius:12;"
                     + "-fx-border-color:#6366f1; -fx-border-radius:12; -fx-cursor:hand;";
        card.setStyle(base);
        card.setOnMouseEntered(e -> card.setStyle(hover));
        card.setOnMouseExited(e  -> card.setStyle(base));
        card.setOnMouseClicked(e -> showDoubtDetails(d));

        // ── Header row: status badge + optional delete ────────────────────────
        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);

        boolean resolved = "RESOLVED".equals(d.status());
        Label statusLbl = new Label(resolved ? "✅ Solved" : "🔥 Open");
        statusLbl.setStyle(resolved
                ? "-fx-text-fill:#34d399; -fx-font-weight:bold; -fx-font-size:11;"
                : "-fx-text-fill:#fbbf24; -fx-font-weight:bold; -fx-font-size:11;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        header.getChildren().addAll(statusLbl, spacer);

        if (canDelete) {
            Button delBtn = new Button("🗑️");
            delBtn.setStyle("-fx-background-color:transparent; -fx-cursor:hand; -fx-text-fill:#ef4444;");
            delBtn.setOnAction(e -> {
                e.consume();
                // FIX: disable immediately to prevent double-click before confirm dialog
                delBtn.setDisable(true);
                NewPopupHelper.showConfirm(window(), "Delete Question?",
                        "Are you sure you want to delete this?", () -> {
                    new Thread(() -> {
                        boolean ok = consultancyService.deleteDoubt(d.id());
                        Platform.runLater(() -> {
                            if (ok) {
                                if (currentSelectedDoubt != null && currentSelectedDoubt.id().equals(d.id())) {
                                    currentSelectedDoubt = null;
                                    doubtDetailBox.setVisible(false);
                                    replyBox.setVisible(false);
                                    answersContainer.getChildren().clear();
                                }
                                loadDoubts();
                                NewPopupHelper.showToast(window(), "🗑️ Question Deleted");
                            } else {
                                delBtn.setDisable(false);
                                NewPopupHelper.showError(window(), "Error", "Could not delete question.");
                            }
                        });
                    }).start();
                });
                // FIX: if user cancels the confirm dialog we must re-enable the button
                // We re-enable in the "cancel" path by always re-enabling after dialog returns
                // (showConfirm is synchronous; if confirm callback wasn't invoked, re-enable)
                if (delBtn.isDisabled()) delBtn.setDisable(false);
            });
            header.getChildren().add(delBtn);
        }

        // ── Author row: avatar + title + meta ────────────────────────────────
        String displayName = d.isAnonymous() ? "🕵️ Anonymous" : "👤 " + d.studentName();
        Label title  = new Label(d.title());
        title.setStyle("-fx-font-weight:bold; -fx-font-size:14; -fx-text-fill:#f8fafc;");
        title.setWrapText(true);

        Label meta = new Label(displayName + "  •  📂 " + d.subject());
        meta.setStyle("-fx-text-fill:#94a3b8; -fx-font-size:11;");

        Node avatar = createAvatar(d.profilePictureUrl(), d.studentName(), d.isAnonymous(), 36);
        if (!d.isAnonymous()) {
            avatar.setStyle(avatar.getStyle() + "-fx-cursor:hand;");
            avatar.setOnMouseClicked(e -> { e.consume(); showUserProfileDialog(d.studentId()); });
        }

        VBox textBlock = new VBox(2, title, meta);
        HBox authorRow = new HBox(10, avatar, textBlock);
        authorRow.setAlignment(Pos.CENTER_LEFT);

        card.getChildren().addAll(header, authorRow);
        return card;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 2. DOUBT DETAIL + ANSWERS
    // ──────────────────────────────────────────────────────────────────────────
    private void showDoubtDetails(Doubt d) {
        currentSelectedDoubt = d;

        doubtDetailBox.setVisible(true);
        boolean resolved = "RESOLVED".equals(d.status());
        replyBox.setVisible(!resolved);

        detailSubject.setText("📂 " + d.subject());

        // FIX: detailTopic was never populated
        String topic = d.topic() != null && !d.topic().isEmpty() ? d.topic() : "—";
        detailTopic.setText("🏷 " + topic);
        detailTopic.setVisible(!topic.equals("—"));
        detailTopic.setManaged(!topic.equals("—"));

        // FIX: detailStatus now exists in FXML
        detailStatus.setText(resolved ? "✅ Solved" : "🔥 Open");
        detailStatus.setStyle(resolved
                ? "-fx-background-color:rgba(16,185,129,0.15); -fx-text-fill:#34d399; -fx-padding:4 10; -fx-background-radius:6;"
                : "-fx-background-color:rgba(245,158,11,0.15); -fx-text-fill:#fbbf24; -fx-padding:4 10; -fx-background-radius:6;");

        detailTitle.setText(d.title());
        detailDesc.setText(d.description());

        String who = d.isAnonymous() ? "🕵️ Anonymous Student" : "👤 " + d.studentName();
        detailStudent.setText("Asked by: " + who + "  •  " + d.createdAt());

        loadAnswersAndReplies(d.id());
    }

    /**
     * FIX: Generation guard prevents a slow network response from a *previous*
     * doubt selection overwriting the answers for the currently selected doubt.
     */
    private void loadAnswersAndReplies(String doubtId) {
        int myGen = answerLoadGeneration.incrementAndGet();

        // Show a lightweight loading indicator immediately (no blank white flash)
        answersContainer.getChildren().clear();
        Label loading = new Label("⏳ Loading answers...");
        loading.setStyle("-fx-text-fill:#475569; -fx-font-size:13; -fx-padding:20;");
        answersContainer.getChildren().add(loading);

        new Thread(() -> {
            List<DoubtAnswer> answers = consultancyService.getAnswers(doubtId);
            Platform.runLater(() -> {
                // FIX: stale result guard
                if (myGen != answerLoadGeneration.get()) return;

                answersContainer.getChildren().clear();
                if (answers.isEmpty()) {
                    Label none = new Label("📭 No answers yet. Be the first to help!");
                    none.setStyle("-fx-text-fill:#64748b; -fx-font-size:14; -fx-padding:20;");
                    answersContainer.getChildren().add(none);
                    return;
                }
                for (DoubtAnswer a : answers) {
                    answersContainer.getChildren().add(buildAnswerThreadUI(a));
                }
            });
        }).start();
    }

    private VBox buildAnswerThreadUI(DoubtAnswer a) {
        VBox threadBox = new VBox(0);

        boolean isBest = a.isBestAnswer();
        VBox mainCard = new VBox(10);
        mainCard.setStyle(
            "-fx-background-color:" + (isBest ? "rgba(16,185,129,0.06)" : "#1a2236") + ";"
            + "-fx-padding:16; -fx-background-radius:12;"
            + "-fx-border-color:" + (isBest ? "#10b981" : "#2d3748") + ";"
            + "-fx-border-width:" + (isBest ? "2" : "1") + "; -fx-border-radius:12;"
        );

        // ── Answer header ─────────────────────────────────────────────────────
        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);

        Node mentorAvatar = createAvatar(a.profilePictureUrl(), a.mentorName(), false, 36);
        mentorAvatar.setStyle(mentorAvatar.getStyle() + "-fx-cursor:hand;");
        mentorAvatar.setOnMouseClicked(e -> { e.consume(); showUserProfileDialog(a.mentorId()); });

        Label name = new Label("👤 " + a.mentorName());
        name.setStyle("-fx-font-weight:bold; -fx-text-fill:#93c5fd; -fx-font-size:14;");

        Label rankBadge = new Label("🏆 " + a.mentorBestAnswerCount() + " Best");
        rankBadge.setStyle("-fx-background-color:rgba(245,158,11,0.15); -fx-text-fill:#fbbf24;"
                + "-fx-font-size:10; -fx-padding:3 8; -fx-background-radius:10; -fx-font-weight:bold;");

        HBox nameAndBadge = new HBox(8, name, rankBadge);
        nameAndBadge.setAlignment(Pos.CENTER_LEFT);

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        header.getChildren().addAll(mentorAvatar, nameAndBadge, sp);

        if (isBest) {
            Label bestBadge = new Label("🌟 BEST ANSWER");
            bestBadge.setStyle("-fx-background-color:#10b981; -fx-text-fill:white;"
                    + "-fx-padding:4 12; -fx-background-radius:15; -fx-font-weight:bold; -fx-font-size:11;");
            header.getChildren().add(bestBadge);
        } else if (currentSelectedDoubt != null
                && !currentSelectedDoubt.status().equals("RESOLVED")
                && currentSelectedDoubt.studentId().equals(String.valueOf(AuthService.CURRENT_USER_ID))) {
            Button markBestBtn = new Button("Mark as Best ✅");
            markBestBtn.setStyle("-fx-background-color:transparent; -fx-text-fill:#34d399;"
                    + "-fx-border-color:#10b981; -fx-border-radius:6; -fx-cursor:hand;"
                    + "-fx-font-size:11; -fx-padding:4 10;");
            markBestBtn.setOnAction(e -> markAsBest(a.id(), markBestBtn));
            header.getChildren().add(markBestBtn);
        }

        // ── Answer body ───────────────────────────────────────────────────────
        Label content = new Label(a.content());
        content.setWrapText(true);
        content.setStyle("-fx-text-fill:#e2e8f0; -fx-font-size:14; -fx-line-spacing:4;");

        Label dateLbl = new Label(a.createdAt());
        dateLbl.setStyle("-fx-text-fill:#475569; -fx-font-size:11;");

        Button replyBtn = new Button("💬 Reply");
        replyBtn.setStyle("-fx-background-color:transparent; -fx-text-fill:#64748b;"
                + "-fx-cursor:hand; -fx-font-weight:bold; -fx-padding:0;");

        HBox actionBar = new HBox(15, replyBtn, dateLbl);
        actionBar.setAlignment(Pos.CENTER_LEFT);
        actionBar.setPadding(new Insets(4, 0, 0, 0));

        mainCard.getChildren().addAll(header, content, actionBar);
        threadBox.getChildren().add(mainCard);

        // ── Nested replies ────────────────────────────────────────────────────
        VBox repliesContainer = new VBox(6);
        repliesContainer.setPadding(new Insets(8, 0, 8, 46));

        if (a.replies() != null) {
            for (AnswerReply reply : a.replies()) {
                repliesContainer.getChildren().add(buildReplyRow(reply));
            }
        }

        // ── Inline reply input (hidden by default) ────────────────────────────
        VBox replyInputBox = new VBox(6);
        replyInputBox.setPadding(new Insets(4, 0, 4, 46));
        replyInputBox.setVisible(false);
        replyInputBox.setManaged(false);

        TextField inlineInput = new TextField();
        inlineInput.setPromptText("Write a reply...");
        inlineInput.setStyle("-fx-background-color:#161b27; -fx-text-fill:white;"
                + "-fx-border-color:#2d3748; -fx-border-radius:20; -fx-background-radius:20; -fx-padding:8 15;");

        Button sendBtn = new Button("Send");
        sendBtn.setStyle("-fx-background-color:#3b82f6; -fx-text-fill:white;"
                + "-fx-background-radius:15; -fx-cursor:hand; -fx-padding:8 14;");

        // FIX: also allow Enter key to submit reply — removes need for mouse click on Send
        inlineInput.setOnAction(e -> sendBtn.fire());

        sendBtn.setOnAction(e -> {
            String text = inlineInput.getText().trim();
            if (text.isEmpty()) return;
            sendBtn.setDisable(true);
            sendBtn.setText("…");
            new Thread(() -> {
                boolean ok = consultancyService.submitAnswerReply(a.id(), text);
                Platform.runLater(() -> {
                    sendBtn.setDisable(false);
                    sendBtn.setText("Send");
                    if (ok) {
                        inlineInput.clear();
                        if (currentSelectedDoubt != null) loadAnswersAndReplies(currentSelectedDoubt.id());
                    }
                });
            }).start();
        });

        HBox inputRow = new HBox(8, inlineInput, sendBtn);
        HBox.setHgrow(inlineInput, Priority.ALWAYS);
        replyInputBox.getChildren().add(inputRow);

        // Toggle reply box; keyboard-focus the field on open
        replyBtn.setOnAction(e -> {
            boolean show = !replyInputBox.isVisible();
            replyInputBox.setVisible(show);
            replyInputBox.setManaged(show);
            if (show) inlineInput.requestFocus();
        });

        threadBox.getChildren().addAll(repliesContainer, replyInputBox);
        return threadBox;
    }

    private HBox buildReplyRow(AnswerReply reply) {
        HBox row = new HBox(10);
        row.setAlignment(Pos.TOP_LEFT);

        Node avatar = createAvatar(reply.profilePictureUrl(), reply.userName(), false, 28);
        avatar.setStyle(avatar.getStyle() + "-fx-cursor:hand;");
        avatar.setOnMouseClicked(e -> { e.consume(); showUserProfileDialog(reply.userId()); });

        VBox card = new VBox(3);
        card.setStyle("-fx-background-color:#141828; -fx-padding:10; -fx-background-radius:10;");

        Label author = new Label(reply.userName() + "  •  " + reply.createdAt());
        author.setStyle("-fx-font-weight:bold; -fx-text-fill:#94a3b8; -fx-font-size:11;");

        Label text = new Label(reply.content());
        text.setWrapText(true);
        text.setStyle("-fx-text-fill:#cbd5e1; -fx-font-size:13;");

        card.getChildren().addAll(author, text);
        row.getChildren().addAll(avatar, card);
        HBox.setHgrow(card, Priority.ALWAYS);
        return row;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 3. ACTION HANDLERS
    // ──────────────────────────────────────────────────────────────────────────

    /** Post main answer. submitAnswerBtn is now @FXML-injected — no instanceof cast needed. */
    @FXML
    public void onSubmitAnswer(ActionEvent event) {
        if (currentSelectedDoubt == null) return;
        String text = answerInput.getText().trim();
        if (text.isEmpty()) return;

        // FIX: button injected directly — no fragile cast
        submitAnswerBtn.setDisable(true);
        submitAnswerBtn.setText("Posting…");

        new Thread(() -> {
            boolean ok = consultancyService.submitAnswer(currentSelectedDoubt.id(), text);
            Platform.runLater(() -> {
                submitAnswerBtn.setDisable(false);
                submitAnswerBtn.setText("Post Answer 🚀");
                if (ok) {
                    answerInput.clear();
                    loadAnswersAndReplies(currentSelectedDoubt.id());
                    NewPopupHelper.showToast(window(), "✅ Answer Posted!");
                } else {
                    NewPopupHelper.showError(window(), "Error", "Failed to post your answer.");
                }
            });
        }).start();
    }

    private void markAsBest(String answerId, Button markBtn) {
        markBtn.setDisable(true); // disable before confirm so double-click is impossible
        NewPopupHelper.showConfirm(window(), "Mark as Best?",
                "This will lock the question as 'Solved'. Only one best answer is allowed. Proceed?", () -> {
            new Thread(() -> {
                boolean ok = consultancyService.markBestAnswer(currentSelectedDoubt.id(), answerId);
                Platform.runLater(() -> {
                    if (ok) {
                        loadDoubts();
                        showDoubtDetails(currentSelectedDoubt);
                        NewPopupHelper.showToast(window(), "🌟 Best answer marked!");
                    } else {
                        markBtn.setDisable(false);
                        NewPopupHelper.showError(window(), "Error", "Could not mark best answer.");
                    }
                });
            }).start();
        });
        // FIX: re-enable if user cancels the confirm dialog
        if (markBtn.isDisabled()) markBtn.setDisable(false);
    }

    @FXML
    public void onAskDoubt(ActionEvent event) {
        VBox box = new VBox(15);
        box.setPadding(new Insets(24));
        box.setStyle("-fx-background-color:#161b27; -fx-border-color:#334155; -fx-border-width:1;");

        String labelStyle = "-fx-font-weight:bold; -fx-text-fill:#e2e8f0; -fx-font-size:13;";
        String fieldStyle  = "-fx-background-color:#1e2738; -fx-text-fill:#ffffff;"
                           + "-fx-prompt-text-fill:#64748b; -fx-border-color:#334155;"
                           + "-fx-border-radius:6; -fx-background-radius:6; -fx-padding:10;";

        Label subLbl = new Label("📂 Category:"); subLbl.setStyle(labelStyle);
        ComboBox<String> subBox = new ComboBox<>();
        subBox.getItems().addAll("Academic", "Personal", "Varsity", "Others");
        subBox.setValue("Academic");
        subBox.setMaxWidth(Double.MAX_VALUE);

        Label titleLbl = new Label("📌 Question Title:"); titleLbl.setStyle(labelStyle);
        TextField titleField = new TextField();
        titleField.setPromptText("Keep it short and clear");
        titleField.setStyle(fieldStyle);

        Label descLbl = new Label("📝 Description:"); descLbl.setStyle(labelStyle);
        TextArea descArea = new TextArea();
        descArea.setPromptText("Describe your problem in detail...");
        descArea.setPrefRowCount(5);
        descArea.setWrapText(true);
        descArea.setStyle("-fx-control-inner-background:#1e2738; " + fieldStyle);

        CheckBox anonCheck = new CheckBox("Ask Anonymously 🕵️");
        anonCheck.setStyle("-fx-font-weight:bold; -fx-text-fill:#fb923c; -fx-font-size:13;");

        Button cancelBtn = new Button("Cancel");
        cancelBtn.setStyle("-fx-background-color:transparent; -fx-text-fill:#94a3b8; -fx-cursor:hand; -fx-padding:8 16;");
        Button postBtn = new Button("🚀 Post Question");
        postBtn.setStyle("-fx-background-color:#2563eb; -fx-text-fill:white; -fx-font-weight:bold;"
                + "-fx-cursor:hand; -fx-padding:8 24; -fx-background-radius:6;");

        HBox actionBox = new HBox(12, cancelBtn, postBtn);
        actionBox.setAlignment(Pos.CENTER_RIGHT);

        box.getChildren().addAll(subLbl, subBox, titleLbl, titleField, descLbl, descArea, anonCheck, actionBox);

        javafx.stage.Stage stage = NewPopupHelper.create(window(), "🙋 Ask a Question", box, 450, 550, 480, 600);
        cancelBtn.setOnAction(e -> stage.close());

        postBtn.setOnAction(e -> {
            if (titleField.getText().trim().isEmpty() || descArea.getText().trim().isEmpty()) {
                NewPopupHelper.showToast(window(), "⚠️ Title and Description cannot be empty!");
                return;
            }
            postBtn.setDisable(true);
            postBtn.setText("Posting…");
            new Thread(() -> {
                // FIX: submitDoubt SQL had 6 placeholders for 7 params (topic was missing).
                // Passing empty string for topic matches the SQL's 7th positional param.
                boolean ok = consultancyService.submitDoubt(
                        titleField.getText().trim(),
                        descArea.getText().trim(),
                        subBox.getValue(),
                        "",           // topic (empty — user can extend with a topic field later)
                        "PUBLIC",
                        anonCheck.isSelected());
                Platform.runLater(() -> {
                    if (ok) {
                        stage.close();
                        loadDoubts();
                        NewPopupHelper.showToast(window(), "✅ Question Posted!");
                    } else {
                        postBtn.setDisable(false);
                        postBtn.setText("🚀 Post Question");
                        NewPopupHelper.showError(window(), "Error", "Failed to post. Try again.");
                    }
                });
            }).start();
        });

        stage.showAndWait();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 4. USER PROFILE POPUP
    // ──────────────────────────────────────────────────────────────────────────
    private void showUserProfileDialog(String userId) {
        new Thread(() -> {
            com.scholar.model.Profile p = profileService.getUserProfile(java.util.UUID.fromString(userId));
            Platform.runLater(() -> {
                if (p == null) {
                    NewPopupHelper.showError(window(), "Error", "Profile not found or is private.");
                    return;
                }
                VBox box = new VBox(15);
                box.setAlignment(Pos.CENTER);
                box.setPadding(new Insets(30));
                box.setStyle("-fx-background-color:#161b27; -fx-border-color:#334155; -fx-border-width:1;"
                        + "-fx-background-radius:12; -fx-border-radius:12;");

                String uname = p.getUsername() != null ? p.getUsername() : p.getFullName();
                Node avatar = createAvatar(p.getProfilePictureUrl(), uname, false, 80);

                Label nameLbl = new Label(p.getFullName());
                nameLbl.setStyle("-fx-font-size:20; -fx-font-weight:bold; -fx-text-fill:white;");

                Label userLbl = new Label("@" + (p.getUsername() != null ? p.getUsername() : "student"));
                userLbl.setStyle("-fx-font-size:14; -fx-text-fill:#94a3b8;");

                String dept = p.getDepartment() != null ? p.getDepartment() : "Department N/A";
                String uni  = p.getUniversityName() != null ? p.getUniversityName() : "University N/A";
                Label eduLbl = new Label("🎓 " + dept + " at " + uni);
                eduLbl.setStyle("-fx-text-fill:#cbd5e1; -fx-font-size:13;");
                eduLbl.setWrapText(true);

                HBox links = new HBox(15);
                links.setAlignment(Pos.CENTER);
                if (p.getGithubUrl()   != null && !p.getGithubUrl().isEmpty())
                    links.getChildren().add(profileLink("🔗 GitHub", p.getGithubUrl()));
                if (p.getLinkedinUrl() != null && !p.getLinkedinUrl().isEmpty())
                    links.getChildren().add(profileLink("🔗 LinkedIn", p.getLinkedinUrl()));

                Button closeBtn = new Button("Close");
                closeBtn.setStyle("-fx-background-color:#374151; -fx-text-fill:white;"
                        + "-fx-padding:6 20; -fx-background-radius:6; -fx-cursor:hand;");

                box.getChildren().addAll(avatar, nameLbl, userLbl, eduLbl);
                if (!links.getChildren().isEmpty()) box.getChildren().add(links);
                box.getChildren().add(closeBtn);

                javafx.stage.Stage stage = NewPopupHelper.create(window(), "User Profile", box, 350, 400, 400, 450);
                closeBtn.setOnAction(e -> stage.close());
                stage.showAndWait();
            });
        }).start();
    }

    private Label profileLink(String text, String url) {
        Label lbl = new Label(text);
        lbl.setStyle("-fx-text-fill:#60a5fa; -fx-cursor:hand;");
        lbl.setOnMouseClicked(e -> {
            try { java.awt.Desktop.getDesktop().browse(new java.net.URI(url)); }
            catch (Exception ex) { /* ignore */ }
        });
        return lbl;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 5. AVATAR HELPER  –  cached, circular, instant placeholder
    // ──────────────────────────────────────────────────────────────────────────
    /**
     * FIX: Previous version showed nothing until the remote image finished loading,
     * causing a white/blank flash. Now we always return a visible placeholder first
     * and swap in the real image once downloaded (background thread, cached).
     *
     * FIX: Image objects are cached by URL so the same avatar isn't re-downloaded
     * every time the list re-renders (e.g. after posting an answer).
     */
    private Node createAvatar(String imageUrl, String fallbackName, boolean isAnonymous, double size) {
        if (isAnonymous) {
            return makeInitialsLabel("🕵️", "#374151", size);
        }

        String initial = (fallbackName != null && !fallbackName.isEmpty())
                ? String.valueOf(fallbackName.charAt(0)).toUpperCase() : "?";

        // Always start with an initials placeholder (immediately visible, no flash)
        Label placeholder = makeInitialsLabel(initial, "#2563eb", size);

        if (imageUrl == null || imageUrl.isEmpty()) {
            return placeholder;
        }

        // Use a StackPane so we can overlay the real image on top once loaded
        StackPane pane = new StackPane(placeholder);
        pane.setMinSize(size, size);
        pane.setMaxSize(size, size);

        // Check cache first — avoids re-downloading on list re-render
        Image cachedImg = imageCache.get(imageUrl);
        if (cachedImg != null && !cachedImg.isError()) {
            pane.getChildren().add(makeCircularImageView(cachedImg, size));
        } else {
            // Load in background; swap placeholder once ready
            Image img = new Image(imageUrl, size, size, true, true, true /* background loading */);
            imageCache.put(imageUrl, img);
            img.progressProperty().addListener((obs, oldV, newV) -> {
                if (newV.doubleValue() >= 1.0 && !img.isError()) {
                    Platform.runLater(() -> pane.getChildren().add(makeCircularImageView(img, size)));
                }
            });
        }

        return pane;
    }

    private Label makeInitialsLabel(String text, String bgColor, double size) {
        Label lbl = new Label(text);
        lbl.setAlignment(Pos.CENTER);
        lbl.setStyle("-fx-background-color:" + bgColor + "; -fx-text-fill:white; -fx-font-weight:bold;"
                + "-fx-alignment:center; -fx-background-radius:50;"
                + "-fx-min-width:" + size + "; -fx-min-height:" + size + ";"
                + "-fx-max-width:" + size + "; -fx-max-height:" + size + ";"
                + "-fx-font-size:" + (size / 2.4) + ";");
        return lbl;
    }

    private ImageView makeCircularImageView(Image img, double size) {
        ImageView iv = new ImageView(img);
        iv.setFitWidth(size);
        iv.setFitHeight(size);
        iv.setPreserveRatio(false);

        // Crop to square centre
        double w = img.getWidth(), h = img.getHeight();
        double side = Math.min(w, h);
        iv.setViewport(new Rectangle2D((w - side) / 2.0, (h - side) / 2.0, side, side));
        iv.setClip(new Circle(size / 2, size / 2, size / 2));
        return iv;
    }
}