package com.scholar.service;

import com.scholar.model.Question;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itextpdf.text.*;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.Loader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service; // 🟢 নতুন

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service // 🌟 ১. এটিকে একটি স্প্রিং সার্ভিস হিসেবে রেজিস্টার করা হলো
public class QuestionBankService {

    private static final String GEMINI_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=";
    private final ObjectMapper jsonMapper = new ObjectMapper();

    @Value("${gemini.api.key:}")
    private String geminiApiKey;

    /**
     * Reads PDFs using Apache PDFBox. (Logic Unchanged)
     */
    public String readPdfs(List<File> files) {
        StringBuilder rawText = new StringBuilder();
        for (File file : files) {
            try (PDDocument doc = Loader.loadPDF(file)) { 
                PDFTextStripper stripper = new PDFTextStripper();
                rawText.append("--- SOURCE: ").append(file.getName()).append(" ---\n");
                rawText.append(stripper.getText(doc)).append("\n\n");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return rawText.toString();
    }

    /**
     * Main AI Analysis via Gemini API. (Logic Unchanged)
     */
    public List<Question> analyzeQuestions(String rawPdfText) throws Exception {
        String apiKey = resolveApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new Exception("GEMINI_API_KEY is missing. Set GEMINI_API_KEY env var or gemini.api.key in application.properties.");
        }
        String prompt = """
            You are a JSON Converter. Extract exam questions from the text.
            Merge duplicates. Rate importance (1-5).
            Return STRICT JSON Array ONLY. No markdown.
            Example:
            [{"topic": "BST", "text": "Explain deletion", "years": "2021", "frequency": 1, "stars": 5}]
            TEXT:
            %s
            """.formatted(rawPdfText);

        String jsonBody = "{ \"contents\": [{ \"parts\": [{ \"text\": \"%s\" }] }] }"
                .formatted(escapeJson(prompt));

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(GEMINI_URL + apiKey))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        String aiResponseText = extractTextFromGemini(response.body());
        
        aiResponseText = aiResponseText.replace("```json", "").replace("```", "").trim();
        int start = aiResponseText.indexOf("[");
        int end = aiResponseText.lastIndexOf("]");
        
        if (start == -1 || end == -1) {
            throw new Exception("AI returned invalid format. RAW RESPONSE:\n" + aiResponseText);
        }
        
        String cleanJson = aiResponseText.substring(start, end + 1);
        return jsonMapper.readValue(cleanJson, new TypeReference<List<Question>>() {});
    }

    private String resolveApiKey() {
        String envKey = System.getenv("GEMINI_API_KEY");
        if (envKey != null && !envKey.isBlank()) return envKey;
        return geminiApiKey;
    }

    /**
     * Creates Output PDF using iText. (Logic Unchanged)
     */
    public String createPdfDocument(List<Question> questions, String destPath) {
        if (questions.isEmpty()) return "❌ No questions found. PDF not created.";

        try {
            Document document = new Document();
            PdfWriter.getInstance(document, new FileOutputStream(destPath));
            document.open();

            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 24, BaseColor.BLACK);
            Font topicFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, BaseColor.DARK_GRAY);
            Font normalFont = FontFactory.getFont(FontFactory.HELVETICA, 12, BaseColor.BLACK);
            Font starFont = FontFactory.getFont(FontFactory.HELVETICA, 12, new BaseColor(255, 140, 0));

            Paragraph title = new Paragraph("Question Bank", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(20);
            document.add(title);

            Map<String, List<Question>> byTopic = questions.stream()
                    .collect(Collectors.groupingBy(Question::topic));

            for (var entry : byTopic.entrySet()) {
                Paragraph topicHeader = new Paragraph("Topic: " + entry.getKey(), topicFont);
                topicHeader.setSpacingBefore(15);
                topicHeader.setSpacingAfter(10);
                document.add(topicHeader);

                PdfPTable table = new PdfPTable(3);
                table.setWidthPercentage(100);
                table.setWidths(new float[]{1.5f, 6f, 2.5f});

                table.addCell(new PdfPCell(new Phrase("Stars", FontFactory.getFont(FontFactory.HELVETICA, 10))));
                table.addCell(new PdfPCell(new Phrase("Question", FontFactory.getFont(FontFactory.HELVETICA, 10))));
                table.addCell(new PdfPCell(new Phrase("Years", FontFactory.getFont(FontFactory.HELVETICA, 10))));

                for (Question q : entry.getValue()) {
                    String starStr = "★".repeat(Math.max(0, q.stars()));
                    table.addCell(new Phrase(starStr, starFont));
                    table.addCell(new Phrase(q.text(), normalFont));
                    table.addCell(new Phrase(q.years() + "\n(" + q.frequency() + "x)", normalFont));
                }
                document.add(table);
            }

            document.close();
            return "✅ Saved PDF to: " + destPath;

        } catch (Exception e) {
            e.printStackTrace();
            return "❌ Failed to create PDF: " + e.getMessage();
        }
    }

    private String extractTextFromGemini(String rawResponse) {
        try {
            var root = jsonMapper.readTree(rawResponse);
            if (!root.has("candidates")) return "";
            return root.path("candidates").get(0)
                       .path("content").path("parts").get(0)
                       .path("text").asText();
        } catch (Exception e) {
            return "";
        }
    }

    private String escapeJson(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
    }
}
