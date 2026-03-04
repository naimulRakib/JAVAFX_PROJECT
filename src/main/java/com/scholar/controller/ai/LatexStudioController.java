package com.scholar.controller.ai;

import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.canvas.draw.SolidLine;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.LineSeparator;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.properties.HorizontalAlignment;
import com.itextpdf.layout.properties.TextAlignment;
import com.scholar.service.AIResourceService;
import com.scholar.service.AuthService;
import com.scholar.service.TelegramService;
import com.scholar.model.AIResource;
import com.scholar.util.PopupHelper;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.*;
import javafx.scene.image.WritableImage;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import javafx.util.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LatexStudioController
 * ══════════════════════════════════════════════════════════════════════
 *
 * Full pipeline (zero browser):
 *   1. User pastes AI-generated text (with LaTeX) into the TextArea
 *   2. Live KaTeX preview updates (350 ms debounce)
 *   3. User fills: Resource Name, Subject, Difficulty, Description, Tags
 *   4. "Export PDF & Upload to Telegram":
 *        a. Build PDF (iText — math → WebView snapshot → PNG, text → Paragraph)
 *        b. Upload PDF via TelegramService → get file_id + download URL
 *        c. Save AIResource row via AIResourceService (name, description,
 *           tags, subject, difficulty, telegram_file_id, telegram_download_url)
 *        d. Refresh history list
 *   5. History panel shows cards: name | date | subject | ⬇ download link
 *   6. Search filters the history list client-side (instant)
 *
 * No external fonts — iText uses built-in Helvetica for text.
 * Non-ASCII text in PDF → placeholder "[non-Latin — see preview]".
 *
 * Path: src/main/java/com/scholar/controller/ai/LatexStudioController.java
 */
@Controller
public class LatexStudioController {

    // ── Spring services ───────────────────────────────────────────────────────
    @Autowired private TelegramService    telegramService;
    @Autowired private AIResourceService  resourceService;

    // ── FXML ─────────────────────────────────────────────────────────────────
    @FXML private TextArea         inputArea;
    @FXML private WebView          previewWebView;
    @FXML private ComboBox<String> fontSizeCombo;
    @FXML private Label            statusLabel;
    @FXML private Button           pasteBtn, clearBtn, copyImgBtn;
    @FXML private Button           uploadBtn, pdfOnlyBtn, clearFormBtn, refreshHistoryBtn;
    @FXML private TextField        resourceNameField, tagsField, historySearchField;
    @FXML private ComboBox<String> subjectCombo, difficultyCombo;
    @FXML private TextArea         descriptionArea;
    @FXML private ProgressBar      uploadProgress;
    @FXML private VBox             historyContainer;

    // ── State ─────────────────────────────────────────────────────────────────
    private WebEngine  engine;
    private boolean    katexReady = false;
    private List<AIResource> allHistory = new ArrayList<>();

    private final PauseTransition debounce  = new PauseTransition(Duration.millis(350));
    private final ExecutorService bgPool    = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "latex-bg"); t.setDaemon(true); return t;
    });

    private static final DateTimeFormatter DISPLAY_FMT =
            DateTimeFormatter.ofPattern("dd MMM yyyy  HH:mm");

    // ═════════════════════════════════════════════════════════════════════════
    //  INIT
    // ═════════════════════════════════════════════════════════════════════════

    @FXML
    public void initialize() {
        setupFontCombo();
        setupWebView();
        setupInputArea();
        setupFormCombos();
        setupHistorySearch();
        loadHistory();
    }

    private void setupFontCombo() {
        if (fontSizeCombo == null) return;
        fontSizeCombo.getItems().addAll("13","15","17","19","22","26","30");
        fontSizeCombo.setValue("17");
        fontSizeCombo.setOnAction(e -> render());
    }

    private void setupWebView() {
        engine = previewWebView.getEngine();
        engine.setJavaScriptEnabled(true);
        engine.getLoadWorker().stateProperty().addListener((obs, old, state) -> {
            if (state == Worker.State.SUCCEEDED) {
                katexReady = true;
                setStatus("✅ KaTeX ready");
            }
            if (state == Worker.State.FAILED)
                setStatus("⚠ Offline — KaTeX CDN unreachable");
        });
        engine.loadContent(buildPage(List.of(), fontSize()));
    }

    private void setupInputArea() {
        debounce.setOnFinished(e -> render());
        inputArea.textProperty().addListener((obs, o, n) -> {
            setStatus("…");
            debounce.playFromStart();
        });
    }

    private void setupFormCombos() {
        if (subjectCombo != null) {
            subjectCombo.getItems().addAll(
                "Mathematics","Physics","Chemistry","Biology",
                "Computer Science","English","History","General");
            subjectCombo.setValue("General");
        }
        if (difficultyCombo != null) {
            difficultyCombo.getItems().addAll("EASY","MEDIUM","HARD");
            difficultyCombo.setValue("MEDIUM");
        }
    }

    private void setupHistorySearch() {
        if (historySearchField == null) return;
        PauseTransition sd = new PauseTransition(Duration.millis(200));
        historySearchField.textProperty().addListener((obs, o, n) -> {
            sd.setOnFinished(e -> filterHistory(n));
            sd.playFromStart();
        });
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  LATEX PARSER
    //  Segments input text into TEXT / MATH_DISPLAY / MATH_INLINE.
    //  Handles: $$...$$ \[...\]  →  DISPLAY
    //           $...$   \(...\)  →  INLINE
    // ═════════════════════════════════════════════════════════════════════════

    enum SegType { TEXT, MATH_DISPLAY, MATH_INLINE }
    record Seg(SegType type, String content) {}

    static List<Seg> parse(String raw) {
        List<Seg> segs = new ArrayList<>();
        if (raw == null || raw.isBlank()) return segs;

        // Longer/greedier delimiters first to avoid partial matches
        Pattern p = Pattern.compile(
                "\\$\\$([\\s\\S]+?)\\$\\$"         // $$...$$   → DISPLAY
              + "|\\\\\\[([\\s\\S]+?)\\\\\\]"      // \[...\]   → DISPLAY
              + "|\\$([^$\n]+?)\\$"                // $...$     → INLINE
              + "|\\\\\\(([\\s\\S]+?)\\\\\\)",     // \(...\)   → INLINE
              Pattern.DOTALL);

        Matcher m = p.matcher(raw);
        int last = 0;
        while (m.find()) {
            if (m.start() > last) {
                String txt = raw.substring(last, m.start());
                if (!txt.isBlank()) segs.add(new Seg(SegType.TEXT, txt));
            }
            String g1=m.group(1), g2=m.group(2), g3=m.group(3), g4=m.group(4);
            if      (g1 != null) segs.add(new Seg(SegType.MATH_DISPLAY, g1.trim()));
            else if (g2 != null) segs.add(new Seg(SegType.MATH_DISPLAY, g2.trim()));
            else if (g3 != null) segs.add(new Seg(SegType.MATH_INLINE,  g3.trim()));
            else if (g4 != null) segs.add(new Seg(SegType.MATH_INLINE,  g4.trim()));
            last = m.end();
        }
        if (last < raw.length()) {
            String tail = raw.substring(last);
            if (!tail.isBlank()) segs.add(new Seg(SegType.TEXT, tail));
        }
        return segs;
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  RENDER  (live preview)
    // ═════════════════════════════════════════════════════════════════════════

    private void render() {
        String raw = inputArea.getText();
        if (raw == null || raw.isBlank()) {
            engine.loadContent(buildPage(List.of(), fontSize()));
            setStatus("Paste content above ↑");
            return;
        }
        List<Seg> segs = parse(raw);
        engine.loadContent(buildPage(segs, fontSize()));
        setStatus("✅ Rendered — " + segs.size() + " segments");
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  PAGE BUILDER
    //  LaTeX passed via JS array window._LATEX[] — JSON encoding preserves
    //  backslashes perfectly. No data-* attributes used.
    // ═════════════════════════════════════════════════════════════════════════

    static String buildPage(List<Seg> segs, int fontSize) {
        StringBuilder html    = new StringBuilder();
        StringBuilder jsArr   = new StringBuilder("window._LATEX=[\n");
        StringBuilder jsCalls = new StringBuilder();
        int mi = 0;

        for (Seg seg : segs) {
            switch (seg.type()) {
                case TEXT -> {
                    for (String line : seg.content().split("\n", -1)) {
                        if (line.isBlank())
                            html.append("<div class='sp'></div>\n");
                        else
                            html.append("<p class='t'>").append(htmlEsc(line)).append("</p>\n");
                    }
                }
                case MATH_DISPLAY -> {
                    String id = "m" + mi;
                    html.append("<div id='").append(id).append("' class='md'></div>\n");
                    jsArr.append(jsonStr(seg.content())).append(",\n");
                    jsCalls.append(renderCall(id, mi, true));
                    mi++;
                }
                case MATH_INLINE -> {
                    String id = "m" + mi;
                    html.append("<div id='").append(id).append("' class='mi'></div>\n");
                    jsArr.append(jsonStr(seg.content())).append(",\n");
                    jsCalls.append(renderCall(id, mi, false));
                    mi++;
                }
            }
        }
        jsArr.append("];\n");

        return """
<!DOCTYPE html><html lang="en"><head>
<meta charset="UTF-8">
<link rel="stylesheet"
  href="https://cdnjs.cloudflare.com/ajax/libs/KaTeX/0.16.9/katex.min.css"
  crossorigin="anonymous" referrerpolicy="no-referrer"/>
<script src="https://cdnjs.cloudflare.com/ajax/libs/KaTeX/0.16.9/katex.min.js"
  crossorigin="anonymous" referrerpolicy="no-referrer"></script>
<style>
*{box-sizing:border-box;margin:0;padding:0}
html{background:#fff}
body{font-family:Georgia,'Times New Roman',serif;font-size:%dpx;
     line-height:1.85;color:#1a1a2e;padding:28px 38px;max-width:860px;margin:0 auto}
p.t{margin:5px 0;text-align:justify;word-break:break-word}
div.sp{height:10px}
div.md{text-align:center;margin:20px 0;padding:16px 22px;
       background:#f8f9ff;border-left:3px solid #6366f1;
       border-radius:4px;overflow-x:auto}
div.mi{display:inline-block;vertical-align:middle;margin:0 3px}
.katex-display{margin:0!important}
.katex-error{color:#dc2626;font-family:monospace;font-size:12px;
             background:#fef2f2;padding:4px 8px;border-radius:4px}
</style></head><body>
%s
<script>
%s
(function(){
  if(typeof katex==='undefined')return;
%s
})();
</script></body></html>
""".formatted(fontSize, html, jsArr, jsCalls);
    }

    private static String renderCall(String id, int idx, boolean display) {
        return """
  try{
    var e%d=document.getElementById('%s');
    if(e%d) katex.render(window._LATEX[%d],e%d,{displayMode:%b,throwOnError:false,strict:false});
  }catch(x%d){
    var e%d=document.getElementById('%s');
    if(e%d) e%d.innerHTML='<span class="katex-error">⚠ '+x%d.message+'</span>';
  }
""".formatted(idx,id,idx,idx,idx,display, idx,idx,id,idx,idx,idx);
    }

    /** JSON-encode a Java string — backslashes arrive intact in JS. */
    static String jsonStr(String s) {
        if (s == null) return "\"\"";
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"'  -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default   -> { if (c < 0x20) sb.append(String.format("\\u%04x",(int)c)); else sb.append(c); }
            }
        }
        return sb.append("\"").toString();
    }

    static String htmlEsc(String s) {
        if (s == null) return "";
        return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  FXML ACTIONS
    // ═════════════════════════════════════════════════════════════════════════

    @FXML
    private void pasteAndRender() {
        Clipboard cb = Clipboard.getSystemClipboard();
        if (cb.hasString() && !cb.getString().isBlank()) {
            inputArea.setText(cb.getString());
            // text listener triggers debounce → render() automatically
        } else {
            PopupHelper.showInfo(getOwner(), "Clipboard Empty",
                    "Copy text from an AI (Ctrl+C or Cmd+C), then click Paste & Render.");
        }
    }

    @FXML
    private void clearAll() {
        inputArea.clear();
        engine.loadContent(buildPage(List.of(), fontSize()));
        setStatus("Cleared");
    }

    @FXML
    private void clearForm() {
        resourceNameField.clear();
        descriptionArea.clear();
        tagsField.clear();
        subjectCombo.setValue("General");
        difficultyCombo.setValue("MEDIUM");
        uploadProgress.setVisible(false);
        uploadProgress.setProgress(0);
    }

    @FXML
    private void copyAsImage() {
        setStatus("📸 Capturing…");
        SnapshotParameters sp = new SnapshotParameters();
        sp.setFill(Color.WHITE);
        WritableImage wi = previewWebView.snapshot(sp, null);
        ClipboardContent cc = new ClipboardContent();
        cc.putImage(wi);
        Clipboard.getSystemClipboard().setContent(cc);
        setStatus("✅ Preview copied to clipboard!");
    }

    // ─── Save PDF only (no upload) ───────────────────────────────────────────
    @FXML
    private void savePdfOnly() {
        String raw = inputArea.getText().trim();
        if (raw.isBlank()) {
            PopupHelper.showInfo(getOwner(), "Empty", "Paste content first."); return;
        }
        FileChooser fc = new FileChooser();
        fc.setTitle("Save PDF");
        fc.setInitialFileName("latex_" + System.currentTimeMillis() + ".pdf");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF","*.pdf"));
        File dest = fc.showSaveDialog(getOwner());
        if (dest == null) return;

        setStatus("📄 Building PDF…");
        setAllButtons(false);
        List<Seg> segs = parse(raw);

        bgPool.submit(() -> {
            try {
                File tmp = buildPdf(segs, nvl(resourceNameField.getText(), "LaTeX Document"));
                Files.copy(tmp.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
                Platform.runLater(() -> {
                    setAllButtons(true);
                    setStatus("✅ PDF saved");
                    PopupHelper.showInfo(getOwner(), "PDF Saved ✅", dest.getAbsolutePath());
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    setAllButtons(true);
                    setStatus("❌ " + ex.getMessage());
                    PopupHelper.showError(getOwner(), "PDF Error", ex.getMessage());
                });
            }
        });
    }

    // ─── Main action: Export PDF → Telegram → Save history ──────────────────
    @FXML
    private void exportAndUpload() {
        String raw  = inputArea.getText().trim();
        String name = resourceNameField.getText().trim();

        if (raw.isBlank()) {
            PopupHelper.showInfo(getOwner(), "Nothing to Export", "Paste content first."); return;
        }
        if (name.isBlank()) {
            resourceNameField.setStyle(resourceNameField.getStyle()
                + "-fx-border-color:#ef4444;");
            PopupHelper.showInfo(getOwner(), "Name Required", "Enter a resource name."); return;
        }
        // Reset border if it was red
        resetFieldStyle(resourceNameField);

        setAllButtons(false);
        uploadProgress.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
        uploadProgress.setVisible(true);
        setStatus("📄 Building PDF…");

        List<Seg> segs = parse(raw);
        String subject    = subjectCombo.getValue();
        String difficulty = difficultyCombo.getValue();
        String desc       = descriptionArea.getText().trim();
        String tags       = tagsField.getText().trim();
        String rawContent = raw;

        bgPool.submit(() -> {
            try {
                // ── Step 1: Build PDF ─────────────────────────────────────
                Platform.runLater(() -> setStatus("📄 Building PDF…"));
                File pdfFile = buildPdf(segs, name);

                // ── Step 2: Upload to Telegram ────────────────────────────
                Platform.runLater(() -> setStatus("📡 Uploading to Telegram…"));
                String fileId = telegramService.uploadToCloud(pdfFile);
                if (fileId == null) {
                    Platform.runLater(() -> {
                        setAllButtons(true);
                        uploadProgress.setVisible(false);
                        setStatus("❌ Telegram upload failed");
                        PopupHelper.showError(getOwner(), "Upload Failed",
                            "Could not upload to Telegram.\n" +
                            "Check BOT_TOKEN and CHAT_ID in application.properties.");
                    });
                    return;
                }

                // ── Step 3: Get download URL ──────────────────────────────
                Platform.runLater(() -> setStatus("🔗 Getting download link…"));
                String downloadUrl = telegramService.getFileDownloadUrl(fileId);

                // ── Step 4: Save resource record ──────────────────────────
                AIResource res = new AIResource();
                res.setTitle(name);
                res.setDescription(desc.isBlank() ? null : desc);
                res.setTags(tags.isBlank() ? null : tags);
                res.setSubject(subject);
                res.setDifficultyLevel(difficulty);
                res.setTelegramFileId(fileId);
                res.setTelegramDownloadUrl(downloadUrl);
                res.setContentMarkdown(rawContent);
                res.setResourceType("LATEX_PDF");
                res.setContentFormat("latex");
                res.setHasLatex(true);
                res.setPublished(true);
                res.setPdfPath(pdfFile.getAbsolutePath());
                if (AuthService.CURRENT_USER_ID != null)
                    res.setCreatedByUserId(AuthService.CURRENT_USER_ID);

                boolean saved = resourceService.saveResource(res);

                // ── Step 5: UI update ─────────────────────────────────────
                final String finalUrl = downloadUrl;
                Platform.runLater(() -> {
                    setAllButtons(true);
                    uploadProgress.setVisible(false);
                    uploadProgress.setProgress(0);

                    if (saved) {
                        setStatus("✅ Uploaded & saved — " + name);
                        loadHistory();
                        clearForm();
                        showSuccessCard(name, finalUrl);
                    } else {
                        // Uploaded OK but DB save failed — still show link
                        setStatus("⚠ Uploaded but record not saved");
                        PopupHelper.showInfo(getOwner(), "Partial Success",
                            "PDF uploaded to Telegram ✅\n" +
                            "But resource record could not be saved to DB.\n\n" +
                            "Download URL:\n" + (finalUrl != null ? finalUrl : "(unavailable)"));
                    }
                });

            } catch (Exception ex) {
                ex.printStackTrace();
                Platform.runLater(() -> {
                    setAllButtons(true);
                    uploadProgress.setVisible(false);
                    setStatus("❌ Error: " + ex.getMessage());
                    PopupHelper.showError(getOwner(), "Export Error", ex.getMessage());
                });
            }
        });
    }

    @FXML
    private void refreshHistory() { loadHistory(); }

    // ═════════════════════════════════════════════════════════════════════════
    //  PDF BUILDER  (iText — no HtmlConverter, no external fonts)
    //  Text  → Paragraph (Helvetica built-in)
    //  Math  → off-screen WebView snapshot → PNG → Image
    // ═════════════════════════════════════════════════════════════════════════

    private File buildPdf(List<Seg> segs, String title) throws Exception {
        Path dir = Path.of(System.getProperty("java.io.tmpdir"), "scholar-latex");
        Files.createDirectories(dir);
        File out = dir.resolve("latex_" + System.currentTimeMillis() + ".pdf").toFile();

        try (PdfWriter   pw  = new PdfWriter(out);
             PdfDocument pd  = new PdfDocument(pw);
             Document    doc = new Document(pd, PageSize.A4)) {

            doc.setMargins(54, 52, 54, 52);

            // ── Header ───────────────────────────────────────────────────
            doc.add(new Paragraph(title)
                    .setFontSize(17).setBold()
                    .setFontColor(new DeviceRgb(0x4f, 0x46, 0xe5))
                    .setTextAlignment(TextAlignment.CENTER));
            doc.add(new Paragraph(
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMMM dd, yyyy  HH:mm")))
                    .setFontSize(9).setFontColor(ColorConstants.GRAY)
                    .setTextAlignment(TextAlignment.CENTER).setMarginBottom(4));
            doc.add(new LineSeparator(new SolidLine(0.5f)).setMarginBottom(14));

            // ── Body ─────────────────────────────────────────────────────
            for (Seg seg : segs) {
                switch (seg.type()) {
                    case TEXT -> {
                        String safe = stripNonAscii(seg.content());
                        if (!safe.isBlank())
                            doc.add(new Paragraph(safe)
                                    .setFontSize(11)
                                    .setTextAlignment(TextAlignment.JUSTIFIED)
                                    .setMarginBottom(5));
                    }
                    case MATH_DISPLAY -> {
                        byte[] png = mathToPng(seg.content(), true, fontSize());
                        addMathImage(doc, png, seg.content(), true);
                    }
                    case MATH_INLINE -> {
                        byte[] png = mathToPng(seg.content(), false, fontSize());
                        addMathImage(doc, png, seg.content(), false);
                    }
                }
            }
        }
        return out;
    }

    private void addMathImage(Document doc, byte[] png, String fallback, boolean display)
            throws Exception {
        if (png != null && png.length > 0) {
            Image img = new Image(ImageDataFactory.create(png));
            float maxW = PageSize.A4.getWidth() - 104;
            if (img.getImageWidth() > maxW) img.scaleToFit(maxW, 9999);
            img.setHorizontalAlignment(display ? HorizontalAlignment.CENTER : HorizontalAlignment.LEFT);
            img.setMarginTop(8).setMarginBottom(8);
            doc.add(img);
        } else {
            // Fallback: raw LaTeX as monospace grey text
            doc.add(new Paragraph("[ " + stripNonAscii(fallback) + " ]")
                    .setFontSize(10).setFontColor(ColorConstants.GRAY).setMarginBottom(4));
        }
    }

    /**
     * Renders one LaTeX expression to PNG bytes via an off-screen WebView.
     * Must be called from a background thread; schedules FX work internally.
     */
    private byte[] mathToPng(String latex, boolean display, int fontSize) {
        CountDownLatch latch  = new CountDownLatch(1);
        byte[][]       result = {null};

        Platform.runLater(() -> {
            WebView   wv  = new WebView();
            WebEngine eng = wv.getEngine();
            wv.setPrefSize(780, 240);

            eng.getLoadWorker().stateProperty().addListener((obs, old, state) -> {
                if (state == Worker.State.SUCCEEDED) {
                    // Two FX pulses so KaTeX fully updates the DOM
                    Platform.runLater(() -> Platform.runLater(() -> {
                        try {
                            SnapshotParameters sp = new SnapshotParameters();
                            sp.setFill(Color.WHITE);
                            WritableImage wi = wv.snapshot(sp, null);
                            result[0] = toPngBytes(wi);
                        } catch (Exception e) {
                            e.printStackTrace();
                        } finally {
                            latch.countDown();
                        }
                    }));
                } else if (state == Worker.State.FAILED || state == Worker.State.CANCELLED) {
                    latch.countDown();
                }
            });

            List<Seg> single = List.of(new Seg(
                    display ? SegType.MATH_DISPLAY : SegType.MATH_INLINE, latex));
            eng.loadContent(buildPage(single, fontSize));
        });

        try { latch.await(10, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
        return result[0];
    }

    private static byte[] toPngBytes(WritableImage wi) throws IOException {
        BufferedImage bi = SwingFXUtils.fromFXImage(wi, null);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(bi, "PNG", baos);
        return baos.toByteArray();
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  HISTORY
    // ═════════════════════════════════════════════════════════════════════════

    private void loadHistory() {
        bgPool.submit(() -> {
            try {
                // Fetch LATEX_PDF resources from DB, ordered newest first
                List<AIResource> list = resourceService.getAllResources(null, null, null);
                // Keep only ones that have a telegram URL (our uploads)
                List<AIResource> filtered = list.stream()
                    .filter(r -> r.getTelegramFileId() != null && !r.getTelegramFileId().isBlank())
                    .toList();
                allHistory = new ArrayList<>(filtered);
                Platform.runLater(() -> renderHistory(allHistory));
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void filterHistory(String query) {
        if (query == null || query.isBlank()) {
            renderHistory(allHistory);
            return;
        }
        String q = query.toLowerCase();
        List<AIResource> filtered = allHistory.stream()
            .filter(r -> (r.getTitle()   != null && r.getTitle().toLowerCase().contains(q))
                      || (r.getSubject() != null && r.getSubject().toLowerCase().contains(q))
                      || (r.getTags()    != null && r.getTags().toLowerCase().contains(q)))
            .toList();
        renderHistory(filtered);
    }

    private void renderHistory(List<AIResource> list) {
        historyContainer.getChildren().clear();

        if (list.isEmpty()) {
            VBox empty = new VBox(10);
            empty.setAlignment(Pos.CENTER);
            empty.setPadding(new Insets(30));
            Label icon = new Label("📭"); icon.setStyle("-fx-font-size:34;");
            Label msg  = new Label("No uploads yet.");
            msg.setStyle("-fx-text-fill:#475569;-fx-font-size:13;");
            Label sub  = new Label("Export a PDF & upload to see it here.");
            sub.setStyle("-fx-text-fill:#334155;-fx-font-size:11;");
            empty.getChildren().addAll(icon, msg, sub);
            historyContainer.getChildren().add(empty);
            return;
        }

        for (AIResource r : list) {
            historyContainer.getChildren().add(buildHistoryCard(r));
        }
    }

    private VBox buildHistoryCard(AIResource r) {
        VBox card = new VBox(8);
        card.setPadding(new Insets(12, 14, 12, 14));
        card.setStyle("-fx-background-color:#111325;-fx-background-radius:12;" +
            "-fx-border-color:rgba(99,102,241,0.2);-fx-border-radius:12;" +
            "-fx-effect:dropshadow(gaussian,rgba(99,102,241,0.1),10,0,0,2);");

        // Top row: name + subject badge
        HBox top = new HBox(8);
        top.setAlignment(Pos.CENTER_LEFT);
        Label nameL = new Label(r.getTitle() != null ? r.getTitle() : "Untitled");
        nameL.setStyle("-fx-font-weight:bold;-fx-font-size:13;-fx-text-fill:#e2e8f0;");
        nameL.setWrapText(true);
        HBox.setHgrow(nameL, Priority.ALWAYS);

        if (r.getSubject() != null && !r.getSubject().isBlank()) {
            Label subj = new Label(r.getSubject());
            subj.setStyle("-fx-background-color:rgba(99,102,241,0.15);-fx-text-fill:#818cf8;" +
                "-fx-background-radius:20;-fx-padding:2 10;-fx-font-size:10;-fx-font-weight:bold;");
            top.getChildren().addAll(nameL, subj);
        } else {
            top.getChildren().add(nameL);
        }

        // Description (optional)
        if (r.getDescription() != null && !r.getDescription().isBlank()) {
            Label desc = new Label(r.getDescription());
            desc.setStyle("-fx-text-fill:#64748b;-fx-font-size:11;");
            desc.setWrapText(true);
            card.getChildren().add(desc);
        }

        // Meta row: difficulty + date + tags
        HBox meta = new HBox(8);
        meta.setAlignment(Pos.CENTER_LEFT);

        if (r.getDifficultyLevel() != null) {
            String col = switch (r.getDifficultyLevel()) {
                case "EASY" -> "#34d399"; case "HARD" -> "#f87171"; default -> "#fbbf24";
            };
            Label diff = new Label(r.getDifficultyLevel());
            diff.setStyle("-fx-text-fill:" + col + ";-fx-font-size:10;-fx-font-weight:bold;" +
                "-fx-background-color:rgba(0,0,0,0.2);-fx-background-radius:8;-fx-padding:2 8;");
            meta.getChildren().add(diff);
        }

        String dateStr = r.getCreatedAt() != null
            ? r.getCreatedAt().format(DISPLAY_FMT) : "—";
        Label dateLbl = new Label("📅 " + dateStr);
        dateLbl.setStyle("-fx-text-fill:#475569;-fx-font-size:10;");
        meta.getChildren().add(dateLbl);

        if (r.getTags() != null && !r.getTags().isBlank()) {
            Label tagsLbl = new Label("🏷 " + r.getTags());
            tagsLbl.setStyle("-fx-text-fill:#334155;-fx-font-size:10;");
            tagsLbl.setWrapText(true);
            meta.getChildren().add(tagsLbl);
        }

        // Download button
        String url = r.getTelegramDownloadUrl();
        HBox btnRow = new HBox(8);
        btnRow.setAlignment(Pos.CENTER_LEFT);

        if (url != null && !url.isBlank()) {
            Button dlBtn = new Button("⬇ Download PDF");
            dlBtn.setStyle("-fx-background-color:rgba(16,185,129,0.12);-fx-text-fill:#34d399;" +
                "-fx-font-weight:bold;-fx-background-radius:8;" +
                "-fx-border-color:rgba(16,185,129,0.3);-fx-border-radius:8;" +
                "-fx-cursor:hand;-fx-padding:6 14;-fx-font-size:12;");
            dlBtn.setOnAction(e -> openInBrowser(url));

            Button copyLinkBtn = new Button("📋 Copy Link");
            copyLinkBtn.setStyle("-fx-background-color:rgba(99,102,241,0.1);-fx-text-fill:#818cf8;" +
                "-fx-font-size:11;-fx-background-radius:8;" +
                "-fx-border-color:rgba(99,102,241,0.2);-fx-border-radius:8;" +
                "-fx-cursor:hand;-fx-padding:6 12;");
            copyLinkBtn.setOnAction(e -> {
                ClipboardContent cc = new ClipboardContent();
                cc.putString(url);
                Clipboard.getSystemClipboard().setContent(cc);
                setStatus("✅ Link copied!");
            });
            btnRow.getChildren().addAll(dlBtn, copyLinkBtn);
        } else {
            Label noLink = new Label("⚠ No download link");
            noLink.setStyle("-fx-text-fill:#475569;-fx-font-size:11;");
            btnRow.getChildren().add(noLink);
        }

        // Delete button
        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
        Button delBtn = new Button("🗑");
        delBtn.setStyle("-fx-background-color:rgba(239,68,68,0.08);-fx-text-fill:#f87171;" +
            "-fx-background-radius:6;-fx-border-color:rgba(239,68,68,0.2);-fx-border-radius:6;" +
            "-fx-cursor:hand;-fx-padding:4 10;-fx-font-size:11;");
        delBtn.setOnAction(e -> confirmDelete(r));
        btnRow.getChildren().addAll(sp, delBtn);

        card.getChildren().addAll(top, meta, btnRow);
        return card;
    }

    private void showSuccessCard(String name, String downloadUrl) {
        String msg = "✅ \"" + name + "\" uploaded!\n\n" +
            (downloadUrl != null ? "Download:\n" + downloadUrl : "Upload complete.");
        PopupHelper.showInfo(getOwner(), "Upload Successful 🎉", msg);
    }

    private void confirmDelete(AIResource r) {
        PopupHelper.showConfirm(getOwner(), "Delete Resource",
            "Delete \"" + r.getTitle() + "\"?\nThis removes it from the database only.",
            () -> bgPool.submit(() -> {
                boolean ok = resourceService.deleteResource(r.getId());
                Platform.runLater(() -> {
                    if (ok) { loadHistory(); setStatus("🗑 Deleted: " + r.getTitle()); }
                    else PopupHelper.showError(getOwner(), "Delete Failed",
                            "Could not delete — check DB connection.");
                });
            }));
    }

    private void openInBrowser(String url) {
        try {
            // Use Desktop API — works cross-platform
            java.awt.Desktop.getDesktop().browse(java.net.URI.create(url));
        } catch (Exception e) {
            // Fallback: copy to clipboard
            ClipboardContent cc = new ClipboardContent();
            cc.putString(url);
            Clipboard.getSystemClipboard().setContent(cc);
            setStatus("📋 Link copied (browser open failed)");
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  HELPERS
    // ═════════════════════════════════════════════════════════════════════════

    private int fontSize() {
        try { return Integer.parseInt(fontSizeCombo.getValue()); }
        catch (Exception e) { return 17; }
    }

    private void setStatus(String t) {
        if (statusLabel != null) statusLabel.setText(t);
    }

    private void setAllButtons(boolean enabled) {
        if (uploadBtn    != null) uploadBtn.setDisable(!enabled);
        if (pdfOnlyBtn   != null) pdfOnlyBtn.setDisable(!enabled);
        if (pasteBtn     != null) pasteBtn.setDisable(!enabled);
        if (clearBtn     != null) clearBtn.setDisable(!enabled);
        if (clearFormBtn != null) clearFormBtn.setDisable(!enabled);
    }

    private void resetFieldStyle(TextField f) {
        f.setStyle("-fx-background-color:#111325;-fx-text-fill:#e2e8f0;" +
            "-fx-prompt-text-fill:#334155;-fx-background-radius:8;" +
            "-fx-border-color:rgba(99,102,241,0.22);-fx-border-radius:8;" +
            "-fx-padding:9 12;-fx-font-size:12;");
    }

    private Window getOwner() {
        if (previewWebView != null && previewWebView.getScene() != null)
            return previewWebView.getScene().getWindow();
        if (historyContainer != null && historyContainer.getScene() != null)
            return historyContainer.getScene().getWindow();
        return null;
    }

    private static String nvl(String s, String def) {
        return (s == null || s.isBlank()) ? def : s;
    }

    static String stripNonAscii(String s) {
        if (s == null) return "";
        return s.replaceAll("[^\\x00-\\x7F]+", "[non-Latin text — see preview]");
    }
}