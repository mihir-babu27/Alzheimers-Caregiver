package com.mihir.alzheimerscaregiver.data.entity;

import java.util.Date;
import java.util.List;

/**
 * MemoryQuestionEntity - Represents memory-based MMSE questions generated during conversations
 * These questions are created proactively when memories are extracted and stored for later use
 */
public class MemoryQuestionEntity {
    
    private String questionId;
    private String patientId;
    private String memoryText;           // Original memory that inspired the question
    private String question;             // The actual question text
    private String correctAnswer;        // Expected answer
    private List<String> alternativeAnswers; // Acceptable alternative answers
    private String difficulty;           // "easy", "medium", "hard"
    private Date createdDate;           // When question was generated
    private Date lastUsedDate;          // When question was last presented
    private int timesUsed;              // How many times this question has been used
    private boolean isActive;           // Whether question is still valid/active
    private String conversationId;      // Link to conversation where memory was extracted
    
    // Default constructor for Firebase
    public MemoryQuestionEntity() {
    }
    
    public MemoryQuestionEntity(String questionId, String patientId, String memoryText, 
                              String question, String correctAnswer, String difficulty) {
        this.questionId = questionId;
        this.patientId = patientId;
        this.memoryText = memoryText;
        this.question = question;
        this.correctAnswer = correctAnswer;
        this.difficulty = difficulty;
        this.createdDate = new Date();
        this.timesUsed = 0;
        this.isActive = true;
    }
    
    // Getters and Setters
    public String getQuestionId() {
        return questionId;
    }
    
    public void setQuestionId(String questionId) {
        this.questionId = questionId;
    }
    
    public String getPatientId() {
        return patientId;
    }
    
    public void setPatientId(String patientId) {
        this.patientId = patientId;
    }
    
    public String getMemoryText() {
        return memoryText;
    }
    
    public void setMemoryText(String memoryText) {
        this.memoryText = memoryText;
    }
    
    public String getQuestion() {
        return question;
    }
    
    public void setQuestion(String question) {
        this.question = question;
    }
    
    public String getCorrectAnswer() {
        return correctAnswer;
    }
    
    public void setCorrectAnswer(String correctAnswer) {
        this.correctAnswer = correctAnswer;
    }
    
    public List<String> getAlternativeAnswers() {
        return alternativeAnswers;
    }
    
    public void setAlternativeAnswers(List<String> alternativeAnswers) {
        this.alternativeAnswers = alternativeAnswers;
    }
    
    public String getDifficulty() {
        return difficulty;
    }
    
    public void setDifficulty(String difficulty) {
        this.difficulty = difficulty;
    }
    
    public Date getCreatedDate() {
        return createdDate;
    }
    
    public void setCreatedDate(Date createdDate) {
        this.createdDate = createdDate;
    }
    
    public Date getLastUsedDate() {
        return lastUsedDate;
    }
    
    public void setLastUsedDate(Date lastUsedDate) {
        this.lastUsedDate = lastUsedDate;
    }
    
    public int getTimesUsed() {
        return timesUsed;
    }
    
    public void setTimesUsed(int timesUsed) {
        this.timesUsed = timesUsed;
    }
    
    public boolean isActive() {
        return isActive;
    }
    
    public void setActive(boolean active) {
        isActive = active;
    }
    
    public String getConversationId() {
        return conversationId;
    }
    
    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }
    
    /**
     * Mark this question as used
     */
    public void markAsUsed() {
        this.timesUsed++;
        this.lastUsedDate = new Date();
    }
    
    /**
     * Check if question should be refreshed (used too many times or too old)
     */
    public boolean shouldRefresh() {
        // Refresh if used more than 3 times or older than 30 days
        long thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000);
        return timesUsed >= 3 || (createdDate != null && createdDate.getTime() < thirtyDaysAgo);
    }
}