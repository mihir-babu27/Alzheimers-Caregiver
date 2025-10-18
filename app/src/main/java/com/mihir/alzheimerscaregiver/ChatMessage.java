package com.mihir.alzheimerscaregiver;

/**
 * ChatMessage - Represents a single message in the chat conversation
 */
public class ChatMessage {
    private String text;
    private boolean isFromUser;
    private long timestamp;
    
    public ChatMessage(String text, boolean isFromUser, long timestamp) {
        this.text = text;
        this.isFromUser = isFromUser;
        this.timestamp = timestamp;
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
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}