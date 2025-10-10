package com.mihir.alzheimerscaregiver.data.entity;

import androidx.annotation.NonNull;

public class ReminderEntity {

    public String id;

    @NonNull
    public String title;

    public String description;

    public Long scheduledTimeEpochMillis;

    public boolean isCompleted;
    
    public boolean isRepeating;
    
    public String lastCompletedDate;

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
        }

        // Default constructor for Firebase
        public ReminderEntity() {}
        
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
}


