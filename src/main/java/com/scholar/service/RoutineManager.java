package com.scholar.service;

import com.scholar.model.StudyTask;
import org.springframework.beans.factory.annotation.Autowired; // 🟢 নতুন
import org.springframework.stereotype.Service; // 🟢 নতুন
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.ArrayList;
import java.util.List;

@Service // 🌟 ১. ক্লাসটিকে স্প্রিং সার্ভিস হিসেবে রেজিস্টার করা হলো
public class RoutineManager {

    // 🌟 ২. 'new' কি-ওয়ার্ড সরিয়ে @Autowired করা হলো। 
    // এখন স্প্রিং নিজে থেকেই AISchedulerService ইনজেক্ট করে দেবে।
    @Autowired
    private AISchedulerService aiService;

    // ==========================================================
    // 🏛️ 1. VARSITY ROUTINE (Admin Task - Logic Unchanged)
    // ==========================================================
    public List<StudyTask> processVarsitySchedule(String rawText) {
        List<StudyTask> weeklyRules = aiService.parseVarsityRoutine(rawText);
        List<StudyTask> expandedTasks = new ArrayList<>();
        LocalDate today = LocalDate.now();
        
        for (int i = 0; i < 120; i++) {
            LocalDate date = today.plusDays(i);
            String dayOfWeek = date.getDayOfWeek().name(); 
            for (StudyTask rule : weeklyRules) {
                if (rule.date() != null && rule.date().equalsIgnoreCase(dayOfWeek)) {
                    expandedTasks.add(new StudyTask(
                        null, rule.title(), date.toString(), rule.startTime(), 
                        rule.durationMinutes(), rule.roomNo(), "ROUTINE", 
                        rule.tags(), "admin", null, null, 
                        null, null // 🌟 লজিক অপরিবর্তিত: ROUTINE এর জন্য Status এবং Importance null
                    ));
                }
            }
        }
        return expandedTasks;
    }

    // ==========================================================
    // 📴 3. CLASS OFF PARSER (Admin Routine Broadcast)
    // ==========================================================
    public static record ClassOffCommand(boolean reverse, List<DateRange> ranges, String reason) {}
    public static record DateRange(LocalDate startDate, LocalDate endDate) {}

    public static ClassOffCommand parseClassOffCommand(String rawText) {
        if (rawText == null || rawText.isBlank()) return null;
        String lower = rawText.toLowerCase(Locale.ROOT);
        boolean isReverse = lower.contains("reverse") || lower.contains("undo") || lower.contains("restore");

        if (!isReverse && !lower.contains("off")) return null;

        List<LocalDate> dates = extractDates(rawText);
        if (dates.isEmpty()) return null;

        List<DateRange> ranges = new ArrayList<>();
        if (lower.contains(" to ") && dates.size() >= 2) {
            LocalDate start = dates.get(0);
            LocalDate end   = dates.get(1);
            if (end.isBefore(start)) {
                LocalDate tmp = start;
                start = end;
                end = tmp;
            }
            ranges.add(new DateRange(start, end));
        } else {
            for (LocalDate d : dates) ranges.add(new DateRange(d, d));
        }

        return new ClassOffCommand(isReverse, ranges, rawText.trim());
    }

    private static List<LocalDate> extractDates(String rawText) {
        java.util.LinkedHashSet<LocalDate> dates = new java.util.LinkedHashSet<>();

        Pattern num = Pattern.compile("(\\d{1,2}[/-]\\d{1,2}[/-]\\d{4})");
        Matcher m1 = num.matcher(rawText);
        while (m1.find()) {
            LocalDate d = tryParseDate(m1.group(1));
            if (d != null) dates.add(d);
        }

        Pattern word = Pattern.compile("(\\d{1,2})\\s*([A-Za-z]{3,9})(?:\\s*(\\d{4}))?");
        Matcher m2 = word.matcher(rawText);
        while (m2.find()) {
            LocalDate d = tryParseWordDate(m2.group(1), m2.group(2), m2.group(3));
            if (d != null) dates.add(d);
        }

        return new ArrayList<>(dates);
    }

    private static LocalDate tryParseDate(String s) {
        if (s == null) return null;
        DateTimeFormatter[] formats = new DateTimeFormatter[] {
            DateTimeFormatter.ofPattern("d/M/uuuu"),
            DateTimeFormatter.ofPattern("d-M-uuuu"),
            DateTimeFormatter.ISO_LOCAL_DATE
        };
        for (DateTimeFormatter f : formats) {
            try { return LocalDate.parse(s, f); }
            catch (DateTimeParseException ignored) {}
        }
        return null;
    }

    private static LocalDate tryParseWordDate(String day, String monthWord, String year) {
        if (day == null || monthWord == null) return null;
        String mon = monthWord.toLowerCase(Locale.ROOT);
        if (mon.length() > 3) mon = mon.substring(0, 3);
        String y = (year != null && !year.isBlank())
            ? year
            : String.valueOf(LocalDate.now().getYear());
        String normalized = day + " " + mon + " " + y;
        try {
            DateTimeFormatter fmt = new java.time.format.DateTimeFormatterBuilder()
                .parseCaseInsensitive()
                .appendPattern("d MMM uuuu")
                .toFormatter(Locale.ENGLISH);
            return LocalDate.parse(normalized, fmt);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    // ==========================================================
    // 👤 2. PERSONAL TASKS (Logic Unchanged)
    // ==========================================================
    public List<StudyTask> processPersonalRequest(String rawText) {
        List<StudyTask> tasks = aiService.parsePersonalTask(rawText);
        List<StudyTask> finalTasks = new ArrayList<>();

        for (StudyTask t : tasks) {
            String type = "CANCEL".equalsIgnoreCase(t.type()) ? "CANCEL" : "PERSONAL";
            String importance = t.importance() != null ? t.importance() : "Medium"; 
            
            // 🌟 তারিখ ভেরিফিকেশন লজিক অপরিবর্তিত
            String safeDate = (t.date() != null && !t.date().isEmpty() && !t.date().equals("null")) 
                              ? t.date() : LocalDate.now().toString();
            
            finalTasks.add(new StudyTask(
                null, t.title(), safeDate, t.startTime(), t.durationMinutes(), 
                t.roomNo(), type, t.tags(), "student", null, null, 
                "PENDING", importance // 🌟 Status & Importance
            ));
        }
        return finalTasks;
    }
}
