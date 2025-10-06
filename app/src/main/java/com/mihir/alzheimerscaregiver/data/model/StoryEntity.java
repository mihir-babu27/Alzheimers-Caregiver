package com.mihir.alzheimerscaregiver.data.model;

import java.util.Date;

/**
 * Data model for storing generated stories in Firebase Firestore
 */
public class StoryEntity {
    private String storyId;
    private String patientId;
    private String generatedStory;
    private Date timestamp;
    private String language; // Language preference used for generation
    private String theme; // Story theme used (community, work, family, etc.)
    
    // Required empty constructor for Firebase
    public StoryEntity() {}
    
    public StoryEntity(String storyId, String patientId, String generatedStory, 
                      Date timestamp, String language, String theme) {
        this.storyId = storyId;
        this.patientId = patientId;
        this.generatedStory = generatedStory;
        this.timestamp = timestamp;
        this.language = language;
        this.theme = theme;
    }
    
    // Getters and Setters
    public String getStoryId() { return storyId; }
    public void setStoryId(String storyId) { this.storyId = storyId; }
    
    public String getPatientId() { return patientId; }
    public void setPatientId(String patientId) { this.patientId = patientId; }
    
    public String getGeneratedStory() { return generatedStory; }
    public void setGeneratedStory(String generatedStory) { this.generatedStory = generatedStory; }
    
    public Date getTimestamp() { return timestamp; }
    public void setTimestamp(Date timestamp) { this.timestamp = timestamp; }
    
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    
    public String getTheme() { return theme; }
    public void setTheme(String theme) { this.theme = theme; }
}