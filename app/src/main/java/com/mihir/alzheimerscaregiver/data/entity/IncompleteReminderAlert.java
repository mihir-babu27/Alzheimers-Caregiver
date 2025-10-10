package com.mihir.alzheimerscaregiver.data.entity;

import java.util.HashMap;
import java.util.Map;

/**
 * Entity to track incomplete reminders for caretaker notifications
 * This is used to alert caretakers when patients don't complete their medication/task reminders
 */
public class IncompleteReminderAlert {
    public String id;                    // Alert ID
    public String reminderId;            // Original reminder ID
    public String patientId;             // Patient who missed the reminder
    public String reminderTitle;         // Title of the missed reminder
    public String reminderType;          // "medication", "task", etc.
    public long reminderScheduledTime;   // When the reminder was supposed to be completed
    public long alertCreatedTime;        // When this alert was created
    public int delayMinutes;             // How many minutes after scheduled time this alert was created
    public boolean isResolved;           // Whether the reminder was eventually completed or dismissed
    public long resolvedTime;            // When the alert was resolved (if resolved)
    public String status;                // "pending", "resolved", "dismissed"
    
    // Default constructor for Firestore
    public IncompleteReminderAlert() {}
    
    public IncompleteReminderAlert(String reminderId, String patientId, String reminderTitle, 
                                  String reminderType, long reminderScheduledTime, int delayMinutes) {
        this.id = generateAlertId(reminderId, delayMinutes);
        this.reminderId = reminderId;
        this.patientId = patientId;
        this.reminderTitle = reminderTitle;
        this.reminderType = reminderType;
        this.reminderScheduledTime = reminderScheduledTime;
        this.alertCreatedTime = System.currentTimeMillis();
        this.delayMinutes = delayMinutes;
        this.isResolved = false;
        this.resolvedTime = 0;
        this.status = "pending";
    }
    
    /**
     * Generate a unique alert ID based on reminder ID and delay
     */
    private String generateAlertId(String reminderId, int delayMinutes) {
        return reminderId + "_alert_" + delayMinutes + "min";
    }
    
    /**
     * Mark this alert as resolved (reminder was completed)
     */
    public void markResolved() {
        this.isResolved = true;
        this.resolvedTime = System.currentTimeMillis();
        this.status = "resolved";
    }
    
    /**
     * Mark this alert as dismissed by caretaker
     */
    public void markDismissed() {
        this.isResolved = true;
        this.resolvedTime = System.currentTimeMillis();
        this.status = "dismissed";
    }
    
    /**
     * Get human-readable delay description
     */
    public String getDelayDescription() {
        if (delayMinutes < 60) {
            return delayMinutes + " minutes late";
        } else {
            int hours = delayMinutes / 60;
            int remainingMinutes = delayMinutes % 60;
            if (remainingMinutes == 0) {
                return hours + " hour" + (hours > 1 ? "s" : "") + " late";
            } else {
                return hours + "h " + remainingMinutes + "m late";
            }
        }
    }
    
    /**
     * Convert to Firestore map
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("id", id);
        map.put("reminderId", reminderId);
        map.put("patientId", patientId);
        map.put("reminderTitle", reminderTitle);
        map.put("reminderType", reminderType);
        map.put("reminderScheduledTime", reminderScheduledTime);
        map.put("alertCreatedTime", alertCreatedTime);
        map.put("delayMinutes", delayMinutes);
        map.put("isResolved", isResolved);
        map.put("resolvedTime", resolvedTime);
        map.put("status", status);
        return map;
    }
    
    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getReminderId() { return reminderId; }
    public void setReminderId(String reminderId) { this.reminderId = reminderId; }
    
    public String getPatientId() { return patientId; }
    public void setPatientId(String patientId) { this.patientId = patientId; }
    
    public String getReminderTitle() { return reminderTitle; }
    public void setReminderTitle(String reminderTitle) { this.reminderTitle = reminderTitle; }
    
    public String getReminderType() { return reminderType; }
    public void setReminderType(String reminderType) { this.reminderType = reminderType; }
    
    public long getReminderScheduledTime() { return reminderScheduledTime; }
    public void setReminderScheduledTime(long reminderScheduledTime) { this.reminderScheduledTime = reminderScheduledTime; }
    
    public long getAlertCreatedTime() { return alertCreatedTime; }
    public void setAlertCreatedTime(long alertCreatedTime) { this.alertCreatedTime = alertCreatedTime; }
    
    public int getDelayMinutes() { return delayMinutes; }
    public void setDelayMinutes(int delayMinutes) { this.delayMinutes = delayMinutes; }
    
    public boolean isResolved() { return isResolved; }
    public void setResolved(boolean resolved) { isResolved = resolved; }
    
    public long getResolvedTime() { return resolvedTime; }
    public void setResolvedTime(long resolvedTime) { this.resolvedTime = resolvedTime; }
    
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}