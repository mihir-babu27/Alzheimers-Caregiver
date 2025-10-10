package com.mihir.alzheimerscaregiver.data;

import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.Exclude;
import com.google.firebase.firestore.ServerTimestamp;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

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
    private boolean isRepeating; // Whether this reminder repeats daily
    private String lastCompletedDate; // Date when last completed (YYYY-MM-DD format)
    
    // New fields for multiple medicines and images
    public List<String> medicineNames; // List of medicine names
    public List<String> imageUrls; // List of image URLs (Firebase Storage or local paths)
    
    @ServerTimestamp
    private Date createdAt;
    
    @Exclude
    private boolean needsAlarmUpdate; // Local flag to track if alarm needs updating

    // Required empty constructor for Firestore
    public ReminderEntity() {
        this.medicineNames = new ArrayList<>();
        this.imageUrls = new ArrayList<>();
    }
    
    public ReminderEntity(String title, String message, long timeMillis, String type, String patientId) {
        this.title = title;
        this.message = message;
        this.timeMillis = timeMillis;
        this.type = type;
        this.patientId = patientId;
        this.isCompleted = false;
        this.isRepeating = false;
        this.lastCompletedDate = null;
        this.needsAlarmUpdate = true;
        this.medicineNames = new ArrayList<>();
        this.imageUrls = new ArrayList<>();
    }
    
    public ReminderEntity(String title, String message, long timeMillis, String type, String patientId, boolean isRepeating) {
        this.title = title;
        this.message = message;
        this.timeMillis = timeMillis;
        this.type = type;
        this.patientId = patientId;
        this.isCompleted = false;
        this.isRepeating = isRepeating;
        this.lastCompletedDate = null;
        this.needsAlarmUpdate = true;
        this.medicineNames = new ArrayList<>();
        this.imageUrls = new ArrayList<>();
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
        result.put("isRepeating", isRepeating);
        result.put("lastCompletedDate", lastCompletedDate);
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

    public boolean isRepeating() {
        return isRepeating;
    }

    public void setRepeating(boolean repeating) {
        isRepeating = repeating;
    }

    public String getLastCompletedDate() {
        return lastCompletedDate;
    }

    public void setLastCompletedDate(String lastCompletedDate) {
        this.lastCompletedDate = lastCompletedDate;
    }

    @Exclude
    public boolean needsAlarmUpdate() {
        return needsAlarmUpdate;
    }

    @Exclude
    public void setNeedsAlarmUpdate(boolean needsAlarmUpdate) {
        this.needsAlarmUpdate = needsAlarmUpdate;
    }

    /**
     * Check if this reminder was completed today
     */
    @Exclude
    public boolean isCompletedToday() {
        if (lastCompletedDate == null) {
            return false;
        }
        
        // Get today's date in YYYY-MM-DD format
        java.text.SimpleDateFormat dateFormat = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault());
        String today = dateFormat.format(new Date());
        
        return today.equals(lastCompletedDate);
    }

    /**
     * Mark this reminder as completed for today
     */
    @Exclude
    public void markCompletedToday() {
        java.text.SimpleDateFormat dateFormat = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault());
        this.lastCompletedDate = dateFormat.format(new Date());
        
        // For non-repeating reminders, also mark as permanently completed
        if (!isRepeating) {
            this.isCompleted = true;
        }
    }
    
    // Helper methods for medicine names and images
    @Exclude
    public String getMedicineNamesString() {
        if (medicineNames == null || medicineNames.isEmpty()) {
            return "";
        }
        return String.join(", ", medicineNames);
    }
    
    @Exclude
    public void addMedicineName(String medicineName) {
        if (medicineNames == null) {
            medicineNames = new ArrayList<>();
        }
        if (!medicineNames.contains(medicineName)) {
            medicineNames.add(medicineName);
        }
    }
    
    @Exclude
    public void addImageUrl(String imageUrl) {
        if (imageUrls == null) {
            imageUrls = new ArrayList<>();
        }
        if (!imageUrls.contains(imageUrl)) {
            imageUrls.add(imageUrl);
        }
    }
    
    @Exclude
    public boolean hasImages() {
        return imageUrls != null && !imageUrls.isEmpty();
    }
    
    @Exclude
    public String getFirstImageUrl() {
        if (hasImages()) {
            return imageUrls.get(0);
        }
        return null;
    }
}
