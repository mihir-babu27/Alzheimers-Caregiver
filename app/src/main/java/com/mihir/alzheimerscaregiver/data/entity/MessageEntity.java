package com.mihir.alzheimerscaregiver.data.entity;

import java.util.Date;
import java.util.List;
import java.util.ArrayList;

/**
 * MessageEntity - Represents a single message in a conversation
 * Enhanced version of ChatMessage for Firebase storage with analysis metadata
 */
public class MessageEntity {
    
    private String messageId;
    private String conversationId;
    private String text;
    private boolean isFromUser;
    private Date timestamp;
    private String speakerRole; // "patient" or "ai"
    
    // Analysis metadata
    private List<String> extractedKeywords;
    private List<String> detectedMemories;
    private double sentimentScore; // -1.0 to 1.0
    private boolean containsPersonalInformation;
    private boolean containsCognitiveMarkers;
    private int responseTime; // in milliseconds for AI responses
    
    // Firebase requires empty constructor
    public MessageEntity() {
        this.extractedKeywords = new ArrayList<>();
        this.detectedMemories = new ArrayList<>();
    }
    
    public MessageEntity(String messageId, String conversationId, String text, boolean isFromUser) {
        this();
        this.messageId = messageId;
        this.conversationId = conversationId;
        this.text = text;
        this.isFromUser = isFromUser;
        this.timestamp = new Date();
        this.speakerRole = isFromUser ? "patient" : "ai";
        this.sentimentScore = 0.0;
        this.containsPersonalInformation = false;
        this.containsCognitiveMarkers = false;
        this.responseTime = 0;
    }
    
    // Convert from ChatMessage
    public static MessageEntity fromChatMessage(String conversationId, com.mihir.alzheimerscaregiver.ChatMessage chatMessage) {
        String messageId = java.util.UUID.randomUUID().toString();
        MessageEntity entity = new MessageEntity(messageId, conversationId, chatMessage.getText(), chatMessage.isFromUser());
        entity.setTimestamp(new Date(chatMessage.getTimestamp()));
        return entity;
    }
    
    // Convert to ChatMessage for display
    public com.mihir.alzheimerscaregiver.ChatMessage toChatMessage() {
        return new com.mihir.alzheimerscaregiver.ChatMessage(
            this.text, 
            this.isFromUser, 
            this.timestamp != null ? this.timestamp.getTime() : System.currentTimeMillis()
        );
    }
    
    // Getters and Setters
    public String getMessageId() {
        return messageId;
    }
    
    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }
    
    public String getConversationId() {
        return conversationId;
    }
    
    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }
    
    public String getText() {
        return text;
    }
    
    public void setText(String text) {
        this.text = text;
    }
    
    public boolean isFromUser() {
        return isFromUser;
    }
    
    public void setFromUser(boolean fromUser) {
        isFromUser = fromUser;
    }
    
    public Date getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(Date timestamp) {
        this.timestamp = timestamp;
    }
    
    public String getSpeakerRole() {
        return speakerRole;
    }
    
    public void setSpeakerRole(String speakerRole) {
        this.speakerRole = speakerRole;
    }
    
    public List<String> getExtractedKeywords() {
        return extractedKeywords;
    }
    
    public void setExtractedKeywords(List<String> extractedKeywords) {
        this.extractedKeywords = extractedKeywords;
    }
    
    public List<String> getDetectedMemories() {
        return detectedMemories;
    }
    
    public void setDetectedMemories(List<String> detectedMemories) {
        this.detectedMemories = detectedMemories;
    }
    
    public double getSentimentScore() {
        return sentimentScore;
    }
    
    public void setSentimentScore(double sentimentScore) {
        this.sentimentScore = sentimentScore;
    }
    
    public boolean isContainsPersonalInformation() {
        return containsPersonalInformation;
    }
    
    public void setContainsPersonalInformation(boolean containsPersonalInformation) {
        this.containsPersonalInformation = containsPersonalInformation;
    }
    
    public boolean isContainsCognitiveMarkers() {
        return containsCognitiveMarkers;
    }
    
    public void setContainsCognitiveMarkers(boolean containsCognitiveMarkers) {
        this.containsCognitiveMarkers = containsCognitiveMarkers;
    }
    
    public int getResponseTime() {
        return responseTime;
    }
    
    public void setResponseTime(int responseTime) {
        this.responseTime = responseTime;
    }
    
    // Helper methods
    public void addKeyword(String keyword) {
        if (this.extractedKeywords == null) {
            this.extractedKeywords = new ArrayList<>();
        }
        if (!this.extractedKeywords.contains(keyword)) {
            this.extractedKeywords.add(keyword);
        }
    }
    
    public void addDetectedMemory(String memory) {
        if (this.detectedMemories == null) {
            this.detectedMemories = new ArrayList<>();
        }
        if (!this.detectedMemories.contains(memory)) {
            this.detectedMemories.add(memory);
        }
    }
    
    @Override
    public String toString() {
        return "MessageEntity{" +
                "messageId='" + messageId + '\'' +
                ", speakerRole='" + speakerRole + '\'' +
                ", text='" + (text != null && text.length() > 50 ? text.substring(0, 50) + "..." : text) + '\'' +
                ", timestamp=" + timestamp +
                ", sentimentScore=" + sentimentScore +
                '}';
    }
}