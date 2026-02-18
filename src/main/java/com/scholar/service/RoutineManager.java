package com.scholar.service;

import com.scholar.model.StudyTask;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class RoutineManager {

    private final AISchedulerService aiService = new AISchedulerService();

    // ==========================================================
    // 🏛️ 1. VARSITY ROUTINE (Admin Task - Null Status)
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
                        null, null // 🌟 ফিক্স: ROUTINE এর জন্য Status এবং Importance null থাকবে
                    ));
                }
            }
        }
        return expandedTasks;
    }

    // ==========================================================
    // 👤 2. PERSONAL TASKS (Date Verification & Importance)
    // ==========================================================
    public List<StudyTask> processPersonalRequest(String rawText) {
        List<StudyTask> tasks = aiService.parsePersonalTask(rawText);
        List<StudyTask> finalTasks = new ArrayList<>();

        for (StudyTask t : tasks) {
            String type = "CANCEL".equalsIgnoreCase(t.type()) ? "CANCEL" : "PERSONAL";
            String importance = t.importance() != null ? t.importance() : "Medium"; 
            
            // 🌟 ফিক্স: যদি এআই তারিখ না দেয়, তবে আজকের তারিখ অটোমেটিক বসে যাবে!
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