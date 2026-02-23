package com.scholar.model;

import com.scholar.service.CourseService;

/**
 * TABLE MODEL for Community Resource Table
 * Path: src/main/java/com/scholar/model/ResourceRow.java
 */
public class ResourceRow {

    private final String name, type, diff, uploader, tags, votes;
    private final CourseService.Resource rawResource;

    public ResourceRow(String name, String type, String diff, String uploader,
                       String tags, String votes, CourseService.Resource rawResource) {
        this.name = name;
        this.type = type;
        this.diff = diff;
        this.uploader = uploader;
        this.tags = tags;
        this.votes = votes;
        this.rawResource = rawResource;
    }

    public String getName()                      { return name; }
    public String getType()                      { return type; }
    public String getDiff()                      { return diff; }
    public String getUploader()                  { return uploader; }
    public String getTags()                      { return tags; }
    public String getVotes()                     { return votes; }
    public CourseService.Resource getRawResource() { return rawResource; }
}