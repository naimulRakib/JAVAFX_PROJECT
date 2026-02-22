package com.scholar.service;

import com.scholar.model.StudyTask;
import org.springframework.beans.factory.annotation.Autowired; // 🟢 নতুন
import org.springframework.stereotype.Service; // 🟢 নতুন
import java.time.LocalDate;
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