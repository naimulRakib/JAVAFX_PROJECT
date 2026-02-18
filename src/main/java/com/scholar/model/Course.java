package com.scholar.model;

import java.util.Arrays;

public class Course {
    private String name;
    private double credit;
    private double ct1, ct2, ct3, ct4;
    private double attendance;

    public Course(String name, double credit, double ct1, double ct2, double ct3, double ct4, double attendance) {
        this.name = name;
        this.credit = credit;
        this.ct1 = ct1; this.ct2 = ct2; this.ct3 = ct3; this.ct4 = ct4;
        this.attendance = attendance;
    }

  
    public double getBest3CT() {
        double[] marks = {ct1, ct2, ct3, ct4};
        Arrays.sort(marks); 
        // Sum highest 3 (indices 1, 2, 3)
        return marks[1] + marks[2] + marks[3];
    }

    public double getCurrentTotal() {
        return getBest3CT() + attendance; // Max 90 (60 CT + 30 Att)
    }

    public String getTargetMessage() {
        // Target: GPA 4.00 = 80% = 240 marks out of 300
        double target = 240.0; 
        double current = getCurrentTotal();
        double needed = target - current;

        if (needed > 210) return "Impossible (Need " + needed + ")";
        if (needed <= 0) return "Done! (Already 4.0)";
        
        return String.format("Need %.1f / 210", needed);
    }

    // Getters for UI Table
    public String getName() { return name; }
    public double getCredit() { return credit; }
    public String getCtBreakdown() { return String.format("%.0f, %.0f, %.0f, %.0f", ct1, ct2, ct3, ct4); }
    public String getFormattedTotal() { return String.format("%.1f / 90", getCurrentTotal()); }
    public String getPrediction() { return getTargetMessage(); }
}