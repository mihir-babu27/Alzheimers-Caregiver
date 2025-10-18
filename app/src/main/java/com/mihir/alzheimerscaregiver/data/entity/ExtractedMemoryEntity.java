package com.mihir.alzheimerscaregiver.data.entity;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * ExtractedMemoryEntity - Represents key memories and experiences extracted from conversations
 * These will be used for personalized story generation and reminiscence therapy
 */
public class ExtractedMemoryEntity {
    
    private String memoryId;
    private String patientId;
    private String conversationId;
    private String messageId; // Source message that contained this memory
    
    // Memory content
    private String memoryText;
    private String memoryType; // "personal", "historical", "family", "work", "hobby", etc.
    private String emotionalContext; // "happy", "sad", "nostalgic", "proud", etc.
    private int timeReferenced; // Approximate year or decade referenced
    private String location; // Place mentioned in the memory
    
    // People involved
    private List<String> peopleInvolved;
    private String relationship; // "spouse", "child", "friend", "colleague", etc.
    
    // Memory quality and reliability
    private double confidenceScore; // 0.0 to 1.0 - how clear/detailed the memory is
    private boolean isRecurringTheme; // If this memory/topic comes up often
    private int mentionCount; // How many times this memory has been referenced
    
    // Therapeutic value
    private double therapeuticValue; // 0.0 to 1.0 - potential for story generation
    private boolean usedInStory; // Whether this memory has been used in story generation
    private Date lastUsedInStory;
    
    // Metadata
    private Date extractedDate;
    private Date lastMentioned;
    private List<String> keywords;
    private String category; // For organizing memories
    
    // Firebase requires empty constructor
    public ExtractedMemoryEntity() {
        this.peopleInvolved = new ArrayList<>();
        this.keywords = new ArrayList<>();
    }
    
    public ExtractedMemoryEntity(String memoryId, String patientId, String conversationId, String memoryText) {
        this();
        this.memoryId = memoryId;
        this.patientId = patientId;
        this.conversationId = conversationId;
        this.memoryText = memoryText;
        this.extractedDate = new Date();
        this.lastMentioned = new Date();
        this.confidenceScore = 0.5; // Default medium confidence
        this.therapeuticValue = 0.5; // Default medium therapeutic value
        this.mentionCount = 1;
        this.isRecurringTheme = false;
        this.usedInStory = false;
    }
    
    // Getters and Setters
    public String getMemoryId() {
        return memoryId;
    }
    
    public void setMemoryId(String memoryId) {
        this.memoryId = memoryId;
    }
    
    public String getPatientId() {
        return patientId;
    }
    
    public void setPatientId(String patientId) {
        this.patientId = patientId;
    }
    
    public String getConversationId() {
        return conversationId;
    }
    
    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }
    
    public String getMessageId() {
        return messageId;
    }
    
    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }
    
    public String getMemoryText() {
        return memoryText;
    }
    
    public void setMemoryText(String memoryText) {
        this.memoryText = memoryText;
    }
    
    public String getMemoryType() {
        return memoryType;
    }
    
    public void setMemoryType(String memoryType) {
        this.memoryType = memoryType;
    }
    
    public String getEmotionalContext() {
        return emotionalContext;
    }
    
    public void setEmotionalContext(String emotionalContext) {
        this.emotionalContext = emotionalContext;
    }
    
    public int getTimeReferenced() {
        return timeReferenced;
    }
    
    public void setTimeReferenced(int timeReferenced) {
        this.timeReferenced = timeReferenced;
    }
    
    public String getLocation() {
        return location;
    }
    
    public void setLocation(String location) {
        this.location = location;
    }
    
    public List<String> getPeopleInvolved() {
        return peopleInvolved;
    }
    
    public void setPeopleInvolved(List<String> peopleInvolved) {
        this.peopleInvolved = peopleInvolved;
    }
    
    public String getRelationship() {
        return relationship;
    }
    
    public void setRelationship(String relationship) {
        this.relationship = relationship;
    }
    
    public double getConfidenceScore() {
        return confidenceScore;
    }
    
    public void setConfidenceScore(double confidenceScore) {
        this.confidenceScore = confidenceScore;
    }
    
    public boolean isRecurringTheme() {
        return isRecurringTheme;
    }
    
    public void setRecurringTheme(boolean recurringTheme) {
        isRecurringTheme = recurringTheme;
    }
    
    public int getMentionCount() {
        return mentionCount;
    }
    
    public void setMentionCount(int mentionCount) {
        this.mentionCount = mentionCount;
    }
    
    public double getTherapeuticValue() {
        return therapeuticValue;
    }
    
    public void setTherapeuticValue(double therapeuticValue) {
        this.therapeuticValue = therapeuticValue;
    }
    
    public boolean isUsedInStory() {
        return usedInStory;
    }
    
    public void setUsedInStory(boolean usedInStory) {
        this.usedInStory = usedInStory;
    }
    
    public Date getLastUsedInStory() {
        return lastUsedInStory;
    }
    
    public void setLastUsedInStory(Date lastUsedInStory) {
        this.lastUsedInStory = lastUsedInStory;
    }
    
    public Date getExtractedDate() {
        return extractedDate;
    }
    
    public void setExtractedDate(Date extractedDate) {
        this.extractedDate = extractedDate;
    }
    
    public Date getLastMentioned() {
        return lastMentioned;
    }
    
    public void setLastMentioned(Date lastMentioned) {
        this.lastMentioned = lastMentioned;
    }
    
    public List<String> getKeywords() {
        return keywords;
    }
    
    public void setKeywords(List<String> keywords) {
        this.keywords = keywords;
    }
    
    public String getCategory() {
        return category;
    }
    
    public void setCategory(String category) {
        this.category = category;
    }
    
    // Helper methods
    public void addPersonInvolved(String person) {
        if (this.peopleInvolved == null) {
            this.peopleInvolved = new ArrayList<>();
        }
        if (!this.peopleInvolved.contains(person)) {
            this.peopleInvolved.add(person);
        }
    }
    
    public void addKeyword(String keyword) {
        if (this.keywords == null) {
            this.keywords = new ArrayList<>();
        }
        if (!this.keywords.contains(keyword)) {
            this.keywords.add(keyword);
        }
    }
    
    public void incrementMentionCount() {
        this.mentionCount++;
        this.lastMentioned = new Date();
        
        // Mark as recurring theme if mentioned multiple times
        if (this.mentionCount >= 3) {
            this.isRecurringTheme = true;
        }
    }
    
    public void markUsedInStory() {
        this.usedInStory = true;
        this.lastUsedInStory = new Date();
    }
    
    @Override
    public String toString() {
        return "ExtractedMemoryEntity{" +
                "memoryId='" + memoryId + '\'' +
                ", memoryType='" + memoryType + '\'' +
                ", emotionalContext='" + emotionalContext + '\'' +
                ", confidenceScore=" + confidenceScore +
                ", therapeuticValue=" + therapeuticValue +
                ", mentionCount=" + mentionCount +
                ", memoryText='" + (memoryText != null && memoryText.length() > 50 ? 
                    memoryText.substring(0, 50) + "..." : memoryText) + '\'' +
                '}';
    }
}