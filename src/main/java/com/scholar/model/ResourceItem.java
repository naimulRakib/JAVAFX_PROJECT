package com.scholar.model;

public class ResourceItem {
    private String name;
    private String date;
    private String fileId; // টেলিগ্রাম 

    public ResourceItem(String name, String date, String fileId) {
        this.name = name;
        this.date = date;
        this.fileId = fileId;
    }

    public String getName() { return name; }
    public String getDate() { return date; }
    public String getFileId() { return fileId; }
}