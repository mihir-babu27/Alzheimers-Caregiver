package com.mihir.alzheimerscaregiver.data.entity;

import androidx.annotation.NonNull;
import java.util.List;
import java.util.ArrayList;

public class ReminderEntity {

    public String id;

    @NonNull
    public String title;

    public String description;

    public Long scheduledTimeEpochMillis;

    public boolean isCompleted;
    
    public boolean isRepeating;
    
    public String lastCompletedDate;
    
    // New fields for multiple medicines and images
    public List<String> medicineNames; // List of medicine names
    public List<String> imageUrls; // List of image URLs (Firebase Storage or local paths)

        public ReminderEntity(@NonNull String title,
                                                  String description,
                                                  Long scheduledTimeEpochMillis,
                                                  boolean isCompleted) {
                this.title = title;
                this.description = description;
                this.scheduledTimeEpochMillis = scheduledTimeEpochMillis;
                this.isCompleted = isCompleted;
                this.isRepeating = false;
                this.lastCompletedDate = null;
                this.medicineNames = new ArrayList<>();
                this.imageUrls = new ArrayList<>();
        }
        
        public ReminderEntity(@NonNull String title,
                                                  String description,
                                                  Long scheduledTimeEpochMillis,
                                                  boolean isCompleted,
                                                  boolean isRepeating) {
                this.title = title;
                this.description = description;
                this.scheduledTimeEpochMillis = scheduledTimeEpochMillis;
                this.isCompleted = isCompleted;
                this.isRepeating = isRepeating;
                this.lastCompletedDate = null;
                this.medicineNames = new ArrayList<>();
                this.imageUrls = new ArrayList<>();
        }

        // Default constructor for Firebase
        public ReminderEntity() {
            this.medicineNames = new ArrayList<>();
            this.imageUrls = new ArrayList<>();
        }
        
        // Getter methods for compatibility with main ReminderEntity
        public String getTitle() {
            return title;
        }
        
        public long getTimeMillis() {
            return scheduledTimeEpochMillis != null ? scheduledTimeEpochMillis : 0;
        }
        
        public boolean isRepeating() {
            return isRepeating;
        }
        
        /**
         * Check if this reminder was completed today
         */
        public boolean isCompletedToday() {
            if (lastCompletedDate == null) {
                return false;
            }
            
            // Get today's date in YYYY-MM-DD format
            java.text.SimpleDateFormat dateFormat = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault());
            String today = dateFormat.format(new java.util.Date());
            
            return today.equals(lastCompletedDate);
        }

        /**
         * Mark this reminder as completed for today
         */
        public void markCompletedToday() {
            java.text.SimpleDateFormat dateFormat = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault());
            this.lastCompletedDate = dateFormat.format(new java.util.Date());
            
            // For non-repeating reminders, also mark as permanently completed
            if (!isRepeating) {
                this.isCompleted = true;
            }
        }
        
        /**
         * Get formatted medicine names as a single string
         */
        public String getMedicineNamesString() {
            if (medicineNames == null || medicineNames.isEmpty()) {
                return title; // Fallback to title for backward compatibility
            }
            return String.join(", ", medicineNames);
        }
        
        /**
         * Add a medicine name to the list
         */
        public void addMedicineName(String medicineName) {
            if (medicineNames == null) {
                medicineNames = new ArrayList<>();
            }
            if (medicineName != null && !medicineName.trim().isEmpty()) {
                medicineNames.add(medicineName.trim());
            }
        }
        
        /**
         * Add an image URL to the list
         */
        public void addImageUrl(String imageUrl) {
            if (imageUrls == null) {
                imageUrls = new ArrayList<>();
            }
            if (imageUrl != null && !imageUrl.trim().isEmpty()) {
                imageUrls.add(imageUrl.trim());
            }
        }
        
        /**
         * Check if this reminder has any images
         */
        public boolean hasImages() {
            return imageUrls != null && !imageUrls.isEmpty();
        }
        
        /**
         * Get the first image URL (for display purposes)
         */
        public String getFirstImageUrl() {
            if (hasImages()) {   
                return imageUrls.get(0);
            }
            return null;
        }
}


