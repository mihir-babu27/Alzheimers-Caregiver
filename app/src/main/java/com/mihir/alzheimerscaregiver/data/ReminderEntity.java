package com.mihir.alzheimerscaregiver.data;

import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.Exclude;
import com.google.firebase.firestore.ServerTimestamp;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Entity representing a medication or task reminder
 * This model works with Firestore and supports local alarm scheduling
 */
public class ReminderEntity {
    @DocumentId
    private String id; // Firestore document ID
    private String title;
    private String message;
    private long timeMillis; // Scheduled time in milliseconds
    private String type; // e.g., "medication", "task"
    private String patientId; // ID of the patient this reminder is for
    private boolean isCompleted;
    
    @ServerTimestamp
    private Date createdAt;
    
    @Exclude
    private boolean needsAlarmUpdate; // Local flag to track if alarm needs updating

    // Required empty constructor for Firestore
    public ReminderEntity() {
    }
    
    public ReminderEntity(String title, String message, long timeMillis, String type, String patientId) {
        this.title = title;
        this.message = message;
        this.timeMillis = timeMillis;
        this.type = type;
        this.patientId = patientId;
        this.isCompleted = false;
        this.needsAlarmUpdate = true;
    }
    
    // Convert to HashMap for Firestore
    public Map<String, Object> toMap() {
        HashMap<String, Object> result = new HashMap<>();
        result.put("title", title);
        result.put("message", message);
        result.put("timeMillis", timeMillis);
        result.put("type", type);
        result.put("patientId", patientId);
        result.put("isCompleted", isCompleted);
        // createdAt is handled automatically by Firestore with @ServerTimestamp
        return result;
    }
    
    // Getters and setters
    
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public long getTimeMillis() {
        return timeMillis;
    }

    public void setTimeMillis(long timeMillis) {
        this.timeMillis = timeMillis;
        this.needsAlarmUpdate = true;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getPatientId() {
        return patientId;
    }

    public void setPatientId(String patientId) {
        this.patientId = patientId;
    }

    public boolean isCompleted() {
        return isCompleted;
    }

    public void setCompleted(boolean completed) {
        isCompleted = completed;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    @Exclude
    public boolean needsAlarmUpdate() {
        return needsAlarmUpdate;
    }

    @Exclude
    public void setNeedsAlarmUpdate(boolean needsAlarmUpdate) {
        this.needsAlarmUpdate = needsAlarmUpdate;
    }
}
