package com.scholar.controller.ai;

import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.LineSeparator;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.kernel.pdf.canvas.draw.SolidLine;
import com.itextpdf.layout.properties.HorizontalAlignment;
import com.itextpdf.layout.properties.TextAlignment;
import com.scholar.util.PopupHelper;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXML;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.*;
import javafx.scene.image.WritableImage;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Window;
import javafx.util.Duration;
import org.springframework.stereotype.Controller;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LaTeXRendererController — fixed root cause of broken rendering.
 *
 * ROOT CAUSE OF PREVIOUS BUG:
 *   LaTeX strings were stored in HTML data-* attributes.
 *   attrEscape() turned \ → \\ so the attribute value contained double-backslashes.
 *   When KaTeX read getAttribute('data-latex') it got "\\int" instead of "\int"
 *   and treated it as literal text → "int", "frac", etc. with no formatting.
 *
 * FIX:
 *   LaTeX strings are NEVER put in HTML attributes.
 *   Instead, segsToHtml() builds:
 *     • <div id="math-0" class="math-display"></div>   ← empty placeholder
 *     • <div id="math-1" class="math-inline"></div>
 *   Then buildFullPage() embeds a JS array WINDOW._LATEX = ["...", "..."]
 *   where each string is JSON-encoded (JSON.stringify handles all escaping correctly).
 *   renderAll() reads window._LATEX[i] and calls katex.render() — backslashes intact.
 *
 * PDF:
 *   iText direct API — no HtmlConverter, no external fonts, no font-embed errors.
 *   Math segments → off-screen WebView snapshot → PNG → iText Image.
 *   Text segments → iText Paragraph with built-in Helvetica.
 */
@Controller
public class LaTeXRendererController {

    @FXML private TextArea         inputArea;
    @FXML private WebView          previewWebView;
    @FXML private Button           exportPdfBtn;
    @FXML private Button           copyImageBtn;
    @FXML private Button           clearBtn;
    @FXML private Label            statusLabel;
    @FXML private ComboBox<String> fontSizeCombo;
    @FXML private VBox             rootVBox;

    private WebEngine previewEngine;
    private boolean   katexReady = false;

    private final PauseTransition debounce = new PauseTransition(Duration.millis(350));

    // ─── Segment model ────────────────────────────────────────────────────────
    enum SegType { TEXT, MATH_DISPLAY, MATH_INLINE }
    record Seg(SegType type, String content) {}

    // ═════════════════════════════════════════════════════════════════════════
    //  INIT
    // ═════════════════════════════════════════════════════════════════════════

    @FXML
    public void initialize() {
        setupWebView();
        setupInput();
        setupControls();
    }

    private void setupWebView() {
        previewEngine = previewWebView.getEngine();
        previewEngine.setJavaScriptEnabled(true);
        previewEngine.getLoadWorker().stateProperty().addListener((obs, old, state) -> {
            if (state == Worker.State.SUCCEEDED) {
                katexReady = true;
                setStatus("✅ KaTeX ready");
            }
            if (state == Worker.State.FAILED) setStatus("⚠️ Offline — KaTeX CDN unreachable");
        });
        // Load blank host page to warm up KaTeX
        previewEngine.loadContent(buildPage(List.of(), fontSize()));
    }

    private void setupInput() {
        debounce.setOnFinished(e -> render());
        inputArea.textProperty().addListener((obs, o, n) -> {
            setStatus("…");
            debounce.playFromStart();
        });
    }

    private void setupControls() {
        if (fontSizeCombo != null) {
            fontSizeCombo.getItems().addAll("13","15","17","19","22","26","30","36");
            fontSizeCombo.setValue("17");
            fontSizeCombo.setOnAction(e -> render());
        }
        if (exportPdfBtn != null) exportPdfBtn.setOnAction(e -> exportPdf());
        if (copyImageBtn != null) copyImageBtn.setOnAction(e -> copyAsImage());
        if (clearBtn     != null) clearBtn    .setOnAction(e -> clearAll());
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  PARSER
    //  Splits mixed text + LaTeX into ordered segments.
    //  Handles: $$...$$ \[...\]  →  MATH_DISPLAY
    //           $...$   \(...\)  →  MATH_INLINE
    //           everything else  →  TEXT
    // ═════════════════════════════════════════════════════════════════════════

    static List<Seg> parse(String raw) {
        List<Seg> segs = new ArrayList<>();
        if (raw == null || raw.isBlank()) return segs;

        // Order matters: longer/greedier patterns first
        Pattern p = Pattern.compile(
                "\\$\\$([\\s\\S]+?)\\$\\$"        // group 1: $$...$$
                + "|\\\\\\[([\\s\\S]+?)\\\\\\]"   // group 2: \[...\]
                + "|\\$([^$\n]+?)\\$"             // group 3: $...$
                + "|\\\\\\(([\\s\\S]+?)\\\\\\)",  // group 4: \(...\)
                Pattern.DOTALL);

        Matcher m = p.matcher(raw);
        int last = 0;

        while (m.find()) {
            if (m.start() > last) {
                String txt = raw.substring(last, m.start());
                if (!txt.isBlank()) segs.add(new Seg(SegType.TEXT, txt));
            }
            String g1 = m.group(1), g2 = m.group(2),
                   g3 = m.group(3), g4 = m.group(4);
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
    //  RENDER
    // ═════════════════════════════════════════════════════════════════════════

    private void render() {
        String raw = inputArea.getText();
        if (raw == null || raw.isBlank()) {
            previewEngine.loadContent(buildPage(List.of(), fontSize()));
            setStatus("Paste content above ↑");
            return;
        }
        List<Seg> segs = parse(raw);
        previewEngine.loadContent(buildPage(segs, fontSize()));
        setStatus("✅ Rendered (" + segs.size() + " segments)");
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  PAGE BUILDER  ← THE ACTUAL FIX
    //
    //  LaTeX strings are passed via a JavaScript array window._LATEX[].
    //  jsonString() uses JSON rules (double-quote delimited, \\ for backslash,
    //  \n for newline) which perfectly preserves LaTeX backslashes.
    //  KaTeX reads window._LATEX[i] — backslashes arrive 100% intact.
    //
    //  HTML placeholder divs/spans have NO data-* attributes carrying LaTeX.
    // ═════════════════════════════════════════════════════════════════════════

    static String buildPage(List<Seg> segs, int fontSize) {
        StringBuilder placeholders = new StringBuilder();
        StringBuilder jsArray      = new StringBuilder("window._LATEX = [\n");
        StringBuilder jsRenderCalls = new StringBuilder();

        int mathIdx = 0;
        for (int i = 0; i < segs.size(); i++) {
            Seg seg = segs.get(i);
            switch (seg.type()) {
                case TEXT -> {
                    String[] lines = seg.content().split("\n", -1);
                    for (String line : lines) {
                        if (line.isBlank())
                            placeholders.append("<div class='spacer'></div>\n");
                        else
                            placeholders.append("<p class='text-seg'>")
                                        .append(htmlEsc(line))
                                        .append("</p>\n");
                    }
                }
                case MATH_DISPLAY -> {
                    String id = "math-" + mathIdx;
                    placeholders.append("<div id='").append(id)
                                .append("' class='math-display-block'></div>\n");
                    jsArray.append("  ").append(jsonString(seg.content())).append(",\n");
                    jsRenderCalls.append(renderCall(id, mathIdx, true));
                    mathIdx++;
                }
                case MATH_INLINE -> {
                    String id = "math-" + mathIdx;
                    // wrap in a div so it still breaks normally in flow
                    placeholders.append("<div id='").append(id)
                                .append("' class='math-inline-wrap'></div>\n");
                    jsArray.append("  ").append(jsonString(seg.content())).append(",\n");
                    jsRenderCalls.append(renderCall(id, mathIdx, false));
                    mathIdx++;
                }
            }
        }

        jsArray.append("];\n");

        return """
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <link rel="stylesheet"
        href="https://cdnjs.cloudflare.com/ajax/libs/KaTeX/0.16.9/katex.min.css"
        crossorigin="anonymous" referrerpolicy="no-referrer"/>
  <script src="https://cdnjs.cloudflare.com/ajax/libs/KaTeX/0.16.9/katex.min.js"
          crossorigin="anonymous" referrerpolicy="no-referrer"></script>
  <style>
    * { box-sizing:border-box; margin:0; padding:0; }
    html { background:#fff; }
    body {
      font-family: Georgia,'Times New Roman',serif;
      font-size: %dpx;
      line-height: 1.8;
      color: #1a1a2e;
      padding: 28px 36px;
      max-width: 860px;
      margin: 0 auto;
    }
    p.text-seg  { margin:6px 0; text-align:justify; word-break:break-word; }
    div.spacer  { height:10px; }
    div.math-display-block {
      text-align:center; margin:20px 0; padding:16px 20px;
      background:#f8f9ff; border-left:3px solid #6366f1; border-radius:4px;
      overflow-x:auto;
    }
    div.math-inline-wrap { display:inline-block; vertical-align:middle; margin:0 4px; }
    .katex-display { margin:0 !important; }
    .katex-error {
      color:#dc2626; font-family:monospace; font-size:12px;
      background:#fef2f2; padding:4px 8px; border-radius:4px;
    }
  </style>
</head>
<body>
%s
<script>
%s
(function() {
  if (typeof katex === 'undefined') { return; }
%s
})();
</script>
</body>
</html>
""".formatted(fontSize, placeholders.toString(), jsArray.toString(), jsRenderCalls.toString());
    }

    /** Builds a JS snippet to render one math block. */
    private static String renderCall(String elemId, int arrayIdx, boolean display) {
        return """
  try {
    var _el%d = document.getElementById('%s');
    if (_el%d) {
      katex.render(window._LATEX[%d], _el%d, {
        displayMode: %b, throwOnError: false, strict: false, trust: true
      });
    }
  } catch(_e%d) {
    var _el%d = document.getElementById('%s');
    if (_el%d) _el%d.innerHTML =
      '<span class="katex-error">⚠ ' + _e%d.message + '</span>';
  }
""".formatted(arrayIdx, elemId, arrayIdx, arrayIdx, arrayIdx, display,
              arrayIdx, arrayIdx, elemId, arrayIdx, arrayIdx, arrayIdx);
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  JSON STRING ENCODING
    //  Wraps a Java string in JSON double-quote delimiters with proper escaping.
    //  This is the safe channel for passing LaTeX to JavaScript:
    //    \int  →  stored as "\\int" in Java String
    //           →  written as "\\int" in JSON  (the JSON spec requires \\)
    //           →  JS runtime evaluates to \int  ✓
    // ═════════════════════════════════════════════════════════════════════════

    static String jsonString(String s) {
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
                default   -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        sb.append("\"");
        return sb.toString();
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  PASTE & RENDER  (FXML button: onAction="#pasteAndRender")
    // ═════════════════════════════════════════════════════════════════════════

    @FXML
    private void pasteAndRender() {
        Clipboard cb = Clipboard.getSystemClipboard();
        if (cb.hasString() && !cb.getString().isBlank()) {
            inputArea.setText(cb.getString());
            // listener triggers debounce → render() automatically
        } else {
            PopupHelper.showInfo(getOwner(), "Clipboard Empty",
                    "Copy text from the AI browser (Cmd+C), then click this.");
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  COPY AS IMAGE
    // ═════════════════════════════════════════════════════════════════════════

    private void copyAsImage() {
        setStatus("📸 Capturing…");
        SnapshotParameters sp = new SnapshotParameters();
        sp.setFill(Color.WHITE);
        WritableImage img = previewWebView.snapshot(sp, null);
        ClipboardContent cc = new ClipboardContent();
        cc.putImage(img);
        Clipboard.getSystemClipboard().setContent(cc);
        setStatus("✅ Copied to clipboard!");
        PopupHelper.showInfo(getOwner(), "Copied ✅",
                "Preview image copied.\nPaste into Word, Docs, Notion, etc.");
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  PDF EXPORT
    //  Uses iText direct API only — NO HtmlConverter, NO external fonts.
    //  Math segments → off-screen WebView → PNG → Image in PDF.
    //  Text segments → Paragraph with built-in Helvetica (zero embed issues).
    // ═════════════════════════════════════════════════════════════════════════

    private void exportPdf() {
        String raw = inputArea.getText().trim();
        if (raw.isBlank()) {
            PopupHelper.showInfo(getOwner(), "Empty", "Paste content first."); return;
        }
        setStatus("📄 Building PDF…");
        exportPdfBtn.setDisable(true);

        List<Seg> segs = parse(raw);
        Thread t = new Thread(() -> {
            try {
                File pdf = buildPdf(segs);
                Platform.runLater(() -> {
                    exportPdfBtn.setDisable(false);
                    setStatus("✅ PDF saved");
                    PopupHelper.showInfo(getOwner(), "PDF Exported ✅",
                            "Saved to:\n" + pdf.getAbsolutePath());
                });
            } catch (Exception ex) {
                ex.printStackTrace();
                Platform.runLater(() -> {
                    exportPdfBtn.setDisable(false);
                    setStatus("❌ PDF error: " + ex.getMessage());
                    PopupHelper.showError(getOwner(), "PDF Error", ex.getMessage());
                });
            }
        });
        t.setDaemon(true);
        t.start();
    }

    private File buildPdf(List<Seg> segs) throws Exception {
        Path dir = Path.of(System.getProperty("java.io.tmpdir"), "scholar-pdfs");
        Files.createDirectories(dir);
        File out = dir.resolve("latex_" + System.currentTimeMillis() + ".pdf").toFile();

        try (PdfWriter writer = new PdfWriter(out);
             PdfDocument pdfDoc = new PdfDocument(writer);
             Document doc = new Document(pdfDoc, PageSize.A4)) {

            doc.setMargins(54, 50, 54, 50);

            // Header
            doc.add(new Paragraph("Scholar AI — LaTeX Document")
                    .setFontSize(16).setBold()
                    .setFontColor(new DeviceRgb(0x4f,0x46,0xe5))
                    .setTextAlignment(TextAlignment.CENTER));
            doc.add(new Paragraph(LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("MMMM dd, yyyy  HH:mm")))
                    .setFontSize(9).setFontColor(ColorConstants.GRAY)
                    .setTextAlignment(TextAlignment.CENTER).setMarginBottom(4));
            doc.add(new LineSeparator(new SolidLine(0.5f))
                    .setMarginTop(4).setMarginBottom(14));

            for (Seg seg : segs) {
                switch (seg.type()) {

                    case TEXT -> {
                        // Strip non-ASCII to avoid any font-embed issue in PDF
                        String safe = stripNonAscii(seg.content());
                        if (!safe.isBlank())
                            doc.add(new Paragraph(safe).setFontSize(11)
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
            float maxW = PageSize.A4.getWidth() - 100;
            if (img.getImageWidth() > maxW) img.scaleToFit(maxW, 9999);
            img.setHorizontalAlignment(display
                    ? HorizontalAlignment.CENTER : HorizontalAlignment.LEFT);
            img.setMarginTop(8).setMarginBottom(8);
            doc.add(img);
        } else {
            // Fallback: raw LaTeX as monospace text
            doc.add(new Paragraph("[ " + fallback + " ]")
                    .setFontSize(10).setFontColor(ColorConstants.GRAY).setMarginBottom(4));
        }
    }

    /**
     * Renders a single math expression to PNG by loading a minimal KaTeX page
     * in an off-screen WebView and snapshotting it.
     * Called from background thread; marshals to FX thread internally.
     */
    private byte[] mathToPng(String latex, boolean display, int fontSize) {
        CountDownLatch latch  = new CountDownLatch(1);
        byte[][]       result = {null};

        Platform.runLater(() -> {
            WebView  wv  = new WebView();
            WebEngine eng = wv.getEngine();
            wv.setPrefSize(780, 240);

            eng.getLoadWorker().stateProperty().addListener((obs, old, state) -> {
                if (state == Worker.State.SUCCEEDED) {
                    // One extra FX pulse so KaTeX finishes DOM updates
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

            // Build a single-equation page using the same safe JS-array approach
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
    //  CLIPBOARD BRIDGE  (injected into the AI browser WebView)
    //  Called by AIResourceControllerEnhanced on every page load.
    // ═════════════════════════════════════════════════════════════════════════

    public static void injectClipboardBridge(WebEngine engine) {
        if (engine == null) return;
        try {
            engine.executeScript("""
                (function(){
                  if (window._scholarBridge) return;
                  window._scholarBridge = true;
                  window.scholarCopy = function(){ return window.getSelection().toString(); };
                  document.addEventListener('keydown', function(e) {
                    var mac = /Mac/i.test(navigator.platform);
                    if ((mac ? e.metaKey : e.ctrlKey) && e.key === 'c') {
                      var sel = window.getSelection().toString();
                      if (!sel) return;
                      var ta = document.createElement('textarea');
                      ta.value = sel;
                      ta.style.cssText = 'position:fixed;opacity:0;top:0;left:0';
                      document.body.appendChild(ta);
                      ta.focus(); ta.select();
                      document.execCommand('copy');
                      document.body.removeChild(ta);
                    }
                  }, true);
                })();
            """);
        } catch (Exception ignored) {}
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  CLEAR
    // ═════════════════════════════════════════════════════════════════════════

    private void clearAll() {
        inputArea.clear();
        previewEngine.loadContent(buildPage(List.of(), fontSize()));
        setStatus("Cleared");
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  HELPERS
    // ═════════════════════════════════════════════════════════════════════════

    private int fontSize() {
        try { return Integer.parseInt(fontSizeCombo.getValue()); }
        catch (Exception e) { return 17; }
    }

    private void setStatus(String t) { if (statusLabel != null) statusLabel.setText(t); }

    private Window getOwner() {
        return (previewWebView != null && previewWebView.getScene() != null)
                ? previewWebView.getScene().getWindow() : null;
    }

    /** HTML-escape text content (not for attributes, not for JS). */
    static String htmlEsc(String s) {
        if (s == null) return "";
        return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");
    }

    /**
     * Replace runs of non-ASCII characters with a bracketed placeholder.
     * Prevents iText from trying to find/embed fonts for Bengali etc.
     * The WebView preview still shows the original text correctly.
     */
    static String stripNonAscii(String s) {
        if (s == null) return "";
        return s.replaceAll("[^\\x00-\\x7F]+", "[non-Latin text — see preview]");
    }
}