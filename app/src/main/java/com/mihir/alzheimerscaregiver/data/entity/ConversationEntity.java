package com.mihir.alzheimerscaregiver.data.entity;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * ConversationEntity - Represents a complete conversation session
 * Used for Firebase Firestore storage and caregiver review
 */
public class ConversationEntity {
    
    private String conversationId;
    private String patientId;
    private Date startTime;
    private Date endTime;
    private int messageCount;
    private String conversationSummary;
    private List<String> extractedMemories;
    private List<String> keyTopics;
    private double emotionalTone; // -1.0 (negative) to 1.0 (positive)
    private int engagementLevel; // 1-5 scale
    private boolean containsCognitiveAssessment;
    private List<MessageEntity> messages;
    
    // Firebase requires empty constructor
    public ConversationEntity() {
        this.messages = new ArrayList<>();
        this.extractedMemories = new ArrayList<>();
        this.keyTopics = new ArrayList<>();
    }
    
    public ConversationEntity(String conversationId, String patientId) {
        this();
        this.conversationId = conversationId;
        this.patientId = patientId;
        this.startTime = new Date();
        this.messageCount = 0;
        this.emotionalTone = 0.0;
        this.engagementLevel = 3;
        this.containsCognitiveAssessment = false;
    }
    
    // Getters and Setters
    public String getConversationId() {
        return conversationId;
    }
    
    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }
    
    public String getPatientId() {
        return patientId;
    }
    
    public void setPatientId(String patientId) {
        this.patientId = patientId;
    }
    
    public Date getStartTime() {
        return startTime;
    }
    
    public void setStartTime(Date startTime) {
        this.startTime = startTime;
    }
    
    public Date getEndTime() {
        return endTime;
    }
    
    public void setEndTime(Date endTime) {
        this.endTime = endTime;
    }
    
    public int getMessageCount() {
        return messageCount;
    }
    
    public void setMessageCount(int messageCount) {
        this.messageCount = messageCount;
    }
    
    public String getConversationSummary() {
        return conversationSummary;
    }
    
    public void setConversationSummary(String conversationSummary) {
        this.conversationSummary = conversationSummary;
    }
    
    public List<String> getExtractedMemories() {
        return extractedMemories;
    }
    
    public void setExtractedMemories(List<String> extractedMemories) {
        this.extractedMemories = extractedMemories;
    }
    
    public List<String> getKeyTopics() {
        return keyTopics;
    }
    
    public void setKeyTopics(List<String> keyTopics) {
        this.keyTopics = keyTopics;
    }
    
    public double getEmotionalTone() {
        return emotionalTone;
    }
    
    public void setEmotionalTone(double emotionalTone) {
        this.emotionalTone = emotionalTone;
    }
    
    public int getEngagementLevel() {
        return engagementLevel;
    }
    
    public void setEngagementLevel(int engagementLevel) {
        this.engagementLevel = engagementLevel;
    }
    
    public boolean isContainsCognitiveAssessment() {
        return containsCognitiveAssessment;
    }
    
    public void setContainsCognitiveAssessment(boolean containsCognitiveAssessment) {
        this.containsCognitiveAssessment = containsCognitiveAssessment;
    }
    
    public List<MessageEntity> getMessages() {
        return messages;
    }
    
    public void setMessages(List<MessageEntity> messages) {
        this.messages = messages;
    }
    
    // Helper methods
    public void addMessage(MessageEntity message) {
        if (this.messages == null) {
            this.messages = new ArrayList<>();
        }
        this.messages.add(message);
        this.messageCount = this.messages.size();
    }
    
    public void addExtractedMemory(String memory) {
        if (this.extractedMemories == null) {
            this.extractedMemories = new ArrayList<>();
        }
        if (!this.extractedMemories.contains(memory)) {
            this.extractedMemories.add(memory);
        }
    }
    
    public void addKeyTopic(String topic) {
        if (this.keyTopics == null) {
            this.keyTopics = new ArrayList<>();
        }
        if (!this.keyTopics.contains(topic)) {
            this.keyTopics.add(topic);
        }
    }
    
    public long getDurationMinutes() {
        if (startTime != null && endTime != null) {
            return (endTime.getTime() - startTime.getTime()) / (1000 * 60);
        }
        return 0;
    }
    
    @Override
    public String toString() {
        return "ConversationEntity{" +
                "conversationId='" + conversationId + '\'' +
                ", patientId='" + patientId + '\'' +
                ", messageCount=" + messageCount +
                ", duration=" + getDurationMinutes() + " minutes" +
                ", extractedMemories=" + (extractedMemories != null ? extractedMemories.size() : 0) +
                ", emotionalTone=" + emotionalTone +
                '}';
    }
}