package com.scholar.model;

public class ResourceItem {
    private String name;
    private String date;
    private String fileId; // টেলিগ্রাম 
    private String downloadUrl;

    public ResourceItem(String name, String date, String fileId) {
        this.name = name;
        this.date = date;
        this.fileId = fileId;
    }

    public ResourceItem(String name, String date, String fileId, String downloadUrl) {
        this.name = name;
        this.date = date;
        this.fileId = fileId;
        this.downloadUrl = downloadUrl;
    }

    public String getName() { return name; }
    public String getDate() { return date; }
    public String getFileId() { return fileId; }
    public String getDownloadUrl() { return downloadUrl; }
}
