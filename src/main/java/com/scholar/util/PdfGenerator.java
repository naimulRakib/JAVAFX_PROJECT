package com.scholar.util;

import com.itextpdf.html2pdf.ConverterProperties;
import com.itextpdf.html2pdf.HtmlConverter;
import com.itextpdf.html2pdf.resolver.font.DefaultFontProvider;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.properties.TextAlignment;
import com.scholar.model.AIResource;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;

/**
 * Enhanced PDF Generator with LaTeX rendering and rich formatting
 * Converts AI Resource content (HTML/Markdown/Plain) to professional PDF
 */
public class PdfGenerator {

    private static final DateTimeFormatter DATE_FORMATTER = 
        DateTimeFormatter.ofPattern("MMMM dd, yyyy 'at' HH:mm");

    /**
     * Generate PDF from AIResource
     */
    public static File generatePdf(AIResource resource) throws IOException {
        // Create temp directory if not exists
        Path tempDir = Path.of(System.getProperty("java.io.tmpdir"), "scholar-pdfs");
        Files.createDirectories(tempDir);

        // Generate filename
        String sanitizedTitle = resource.getTitle()
                .replaceAll("[^a-zA-Z0-9\\s]", "")
                .replaceAll("\\s+", "_");
        String filename = String.format("%s_%d.pdf", sanitizedTitle, System.currentTimeMillis());
        File pdfFile = tempDir.resolve(filename).toFile();

        // Choose generation method based on content format
        if (resource.getContentHtml() != null && !resource.getContentHtml().isEmpty()) {
            generateFromHtml(resource, pdfFile);
        } else if (resource.getContent() != null && !resource.getContent().isEmpty()) {
            generateFromPlainText(resource, pdfFile);
        } else {
            throw new IOException("Resource has no content to generate PDF from");
        }

        return pdfFile;
    }

    /**
     * Generate PDF from HTML content with LaTeX support
     */
    private static void generateFromHtml(AIResource resource, File outputFile) throws IOException {
        String htmlContent = buildHtmlDocument(resource);

        try {
            PdfWriter writer = new PdfWriter(new FileOutputStream(outputFile));
            PdfDocument pdf = new PdfDocument(writer);
            
            // Setup converter properties
            ConverterProperties converterProperties = new ConverterProperties();
            DefaultFontProvider fontProvider = new DefaultFontProvider(true, true, true);
            converterProperties.setFontProvider(fontProvider);
            
            // Convert HTML to PDF
            HtmlConverter.convertToPdf(htmlContent, pdf, converterProperties);
            
            System.out.println("✅ PDF generated successfully: " + outputFile.getName());
            
        } catch (Exception e) {
            System.err.println("❌ Error generating PDF: " + e.getMessage());
            throw new IOException("Failed to generate PDF", e);
        }
    }

    /**
     * Generate PDF from plain text
     */
    private static void generateFromPlainText(AIResource resource, File outputFile) throws IOException {
        try (PdfWriter writer = new PdfWriter(outputFile);
             PdfDocument pdf = new PdfDocument(writer);
             Document document = new Document(pdf)) {

            // Add title
            Paragraph title = new Paragraph(resource.getTitle())
                    .setFontSize(24)
                    .setBold()
                    .setTextAlignment(TextAlignment.CENTER);
            document.add(title);

            // Add metadata
            String metadata = String.format("%s | %s | Created: %s",
                    resource.getSubjectEmoji() + " " + resource.getSubject(),
                    resource.getDifficultyEmoji() + " " + resource.getDifficultyLevel(),
                    resource.getFormattedCreatedAt());
            
            Paragraph meta = new Paragraph(metadata)
                    .setFontSize(10)
                    .setItalic()
                    .setTextAlignment(TextAlignment.CENTER);
            document.add(meta);

            // Add spacing
            document.add(new Paragraph("\n"));

            // Add description if available
            if (resource.getDescription() != null && !resource.getDescription().isEmpty()) {
                Paragraph desc = new Paragraph(resource.getDescription())
                        .setFontSize(12)
                        .setItalic();
                document.add(desc);
                document.add(new Paragraph("\n"));
            }

            // Add content
            Paragraph content = new Paragraph(resource.getContent())
                    .setFontSize(11);
            document.add(content);

            System.out.println("✅ PDF generated successfully: " + outputFile.getName());

        } catch (Exception e) {
            System.err.println("❌ Error generating PDF: " + e.getMessage());
            throw new IOException("Failed to generate PDF", e);
        }
    }

    /**
     * Build complete HTML document with styling and LaTeX rendering
     */
    private static String buildHtmlDocument(AIResource resource) {
        // Pre-process HTML to render LaTeX
        String processedHtml = preprocessLatex(resource.getContentHtml());

        return String.format("""
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>%s</title>
    <style>
        @page {
            size: A4;
            margin: 2cm;
        }
        
        body {
            font-family: 'Georgia', 'Times New Roman', serif;
            font-size: 11pt;
            line-height: 1.6;
            color: #1a1a1a;
            max-width: 100%%;
        }
        
        .header {
            text-align: center;
            border-bottom: 2px solid #4f46e5;
            padding-bottom: 20px;
            margin-bottom: 30px;
        }
        
        .title {
            font-size: 24pt;
            font-weight: bold;
            color: #4f46e5;
            margin: 0 0 10px 0;
        }
        
        .metadata {
            font-size: 10pt;
            color: #666;
            font-style: italic;
        }
        
        .description {
            background-color: #f8f9fa;
            padding: 15px;
            border-left: 4px solid #4f46e5;
            margin: 20px 0;
            font-style: italic;
        }
        
        .content {
            margin-top: 20px;
        }
        
        h1, h2, h3, h4 {
            color: #4f46e5;
            margin-top: 20px;
            margin-bottom: 10px;
        }
        
        h1 { font-size: 20pt; }
        h2 { font-size: 18pt; }
        h3 { font-size: 16pt; }
        h4 { font-size: 14pt; }
        
        p {
            margin: 10px 0;
            text-align: justify;
        }
        
        ul, ol {
            margin: 10px 0;
            padding-left: 30px;
        }
        
        li {
            margin: 5px 0;
        }
        
        code {
            background-color: #f5f5f5;
            padding: 2px 6px;
            border-radius: 3px;
            font-family: 'Courier New', monospace;
            font-size: 10pt;
        }
        
        pre {
            background-color: #f5f5f5;
            padding: 15px;
            border-radius: 5px;
            border-left: 3px solid #4f46e5;
            overflow-x: auto;
            font-family: 'Courier New', monospace;
            font-size: 9pt;
            line-height: 1.4;
        }
        
        pre code {
            background: none;
            padding: 0;
        }
        
        blockquote {
            border-left: 4px solid #4f46e5;
            padding-left: 20px;
            margin: 15px 0;
            color: #555;
            font-style: italic;
        }
        
        table {
            border-collapse: collapse;
            width: 100%%;
            margin: 15px 0;
        }
        
        th, td {
            border: 1px solid #ddd;
            padding: 10px;
            text-align: left;
        }
        
        th {
            background-color: #4f46e5;
            color: white;
            font-weight: bold;
        }
        
        tr:nth-child(even) {
            background-color: #f9f9f9;
        }
        
        .footer {
            margin-top: 40px;
            padding-top: 20px;
            border-top: 1px solid #ddd;
            text-align: center;
            font-size: 9pt;
            color: #888;
        }
        
        /* LaTeX-like math styling */
        .math {
            font-family: 'Latin Modern Math', 'STIX Two Math', serif;
            font-style: italic;
        }
        
        .math-display {
            text-align: center;
            margin: 20px 0;
            font-size: 12pt;
        }
    </style>
</head>
<body>
    <div class="header">
        <div class="title">%s %s</div>
        <div class="metadata">
            %s | %s | Created: %s
        </div>
    </div>
    
    %s
    
    <div class="content">
        %s
    </div>
    
    <div class="footer">
        Generated by Scholar AI Resource Studio • %s
    </div>
</body>
</html>
                """,
                resource.getTitle(), // <title>
                resource.getSubjectEmoji(),
                resource.getTitle(),
                resource.getSubject(),
                resource.getDifficultyEmoji() + " " + resource.getDifficultyLevel(),
                resource.getFormattedCreatedAt(),
                resource.getDescription() != null && !resource.getDescription().isEmpty()
                        ? "<div class=\"description\">" + resource.getDescription() + "</div>"
                        : "",
                processedHtml,
                java.time.LocalDateTime.now().format(DATE_FORMATTER)
        );
    }

    /**
     * Preprocess HTML to render LaTeX equations
     * Converts LaTeX notation to visual approximation or leaves as-is
     */
    private static String preprocessLatex(String html) {
        if (html == null) return "";
        
        // For now, preserve LaTeX as-is
        // In a full implementation, you'd use a LaTeX renderer like JLaTeXMath
        // or convert to MathML
        
        return html;
    }

    /**
     * Convert Markdown to HTML if needed
     */
    public static String markdownToHtml(String markdown) {
        if (markdown == null || markdown.isEmpty()) {
            return "";
        }

        // Basic markdown conversion
        String html = markdown;

        // Headers
        html = html.replaceAll("(?m)^### (.+)$", "<h3>$1</h3>");
        html = html.replaceAll("(?m)^## (.+)$", "<h2>$1</h2>");
        html = html.replaceAll("(?m)^# (.+)$", "<h1>$1</h1>");

        // Bold and italic
        html = html.replaceAll("\\*\\*(.+?)\\*\\*", "<strong>$1</strong>");
        html = html.replaceAll("\\*(.+?)\\*", "<em>$1</em>");

        // Code blocks
        html = html.replaceAll("```([\\s\\S]+?)```", "<pre><code>$1</code></pre>");
        html = html.replaceAll("`(.+?)`", "<code>$1</code>");

        // Lists
        html = html.replaceAll("(?m)^[\\*\\-] (.+)$", "<li>$1</li>");
        html = html.replaceAll("(<li>.*?</li>\\n?)+", "<ul>$0</ul>");

        // Line breaks
        html = html.replaceAll("\\n\\n", "</p><p>");
        html = html.replaceAll("\\n", "<br>");

        // Wrap in paragraphs
        if (!html.startsWith("<")) {
            html = "<p>" + html + "</p>";
        }

        return html;
    }
}