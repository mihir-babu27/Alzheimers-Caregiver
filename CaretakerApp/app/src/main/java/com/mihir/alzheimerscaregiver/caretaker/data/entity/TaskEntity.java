package com.mihir.alzheimerscaregiver.caretaker.data.entity;

public class TaskEntity {

    public String id;

    public String name;

    public String description;

    public boolean isCompleted;

    public String category; // e.g., Morning, Exercise, Cognitive, etc.

    public Long scheduledTimeEpochMillis; // nullable if unscheduled

    public boolean isRecurring;

    public String recurrenceRule; // e.g., DAILY, WEEKLY:MON,WED,FRI
    
    public boolean isRepeating; // true for daily repeating tasks
    
    public String lastCompletedDate; // tracks completion for repeating tasks (YYYY-MM-DD format)
    
    // Enhanced day-based scheduling fields (like traditional clock apps)
    public boolean repeatOnSunday;
    public boolean repeatOnMonday;
    public boolean repeatOnTuesday;
    public boolean repeatOnWednesday;
    public boolean repeatOnThursday;
    public boolean repeatOnFriday;
    public boolean repeatOnSaturday;
    
    // Alarm/notification settings
    public boolean enableAlarm; // whether to set alarms/notifications
    public boolean enableCaretakerNotification; // whether to notify caretakers if missed

    public TaskEntity(String name,
                      String description,
                      boolean isCompleted,
                      String category,
                      Long scheduledTimeEpochMillis,
                      boolean isRecurring,
                      String recurrenceRule) {
        this.name = name;
        this.description = description;
        this.isCompleted = isCompleted;
        this.category = category;
        this.scheduledTimeEpochMillis = scheduledTimeEpochMillis;
        this.isRecurring = isRecurring;
        this.recurrenceRule = recurrenceRule;
        this.isRepeating = false;
        this.lastCompletedDate = null;
        
        // Initialize new fields with defaults
        this.repeatOnSunday = false;
        this.repeatOnMonday = false;
        this.repeatOnTuesday = false;
        this.repeatOnWednesday = false;
        this.repeatOnThursday = false;
        this.repeatOnFriday = false;
        this.repeatOnSaturday = false;
        this.enableAlarm = true;
        this.enableCaretakerNotification = true;
    }
    
    public TaskEntity(String name,
                      String description,
                      boolean isCompleted,
                      String category,
                      Long scheduledTimeEpochMillis,
                      boolean isRecurring,
                      String recurrenceRule,
                      boolean isRepeating) {
        this.name = name;
        this.description = description;
        this.isCompleted = isCompleted;
        this.category = category;
        this.scheduledTimeEpochMillis = scheduledTimeEpochMillis;
        this.isRecurring = isRecurring;
        this.recurrenceRule = recurrenceRule;
        this.isRepeating = isRepeating;
        this.lastCompletedDate = null;
        
        // Initialize new fields with defaults
        this.repeatOnSunday = false;
        this.repeatOnMonday = false;
        this.repeatOnTuesday = false;
        this.repeatOnWednesday = false;
        this.repeatOnThursday = false;
        this.repeatOnFriday = false;
        this.repeatOnSaturday = false;
        this.enableAlarm = true;
        this.enableCaretakerNotification = true;
    }

    // Default constructor for Firebase
    public TaskEntity() {}
    
    /**
     * Check if this task was completed today (using same logic as ReminderEntity)
     */
    public boolean isCompletedToday() {
        if (lastCompletedDate == null) {
            return false;
        }
        
        // Get today's date in YYYY-MM-DD format (same as ReminderEntity)
        java.text.SimpleDateFormat dateFormat = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault());
        String today = dateFormat.format(new java.util.Date());
        
        return today.equals(lastCompletedDate);
    }
    
    /**
     * Get the effective completion status for this task
     * For repeating tasks, returns whether completed today
     * For non-repeating tasks, returns the standard completion status
     */
    public boolean getEffectiveCompletionStatus() {
        if (isRepeating) {
            return isCompletedToday();
        } else {
            return isCompleted;
        }
    }
    
    // Getter methods
    public String getTaskId() { 
        return id; 
    }
    
    public String getTaskName() { 
        return name; 
    }
    
    public boolean isRepeating() { 
        return isRepeating; 
    }
    
    /**
     * Mark this task as completed for today (for repeating tasks)
     */
    public void markCompletedForToday() {
        if (isRepeating) {
            this.lastCompletedDate = java.time.LocalDate.now().toString();
            this.isCompleted = true; // Keep overall status as completed for compatibility
        } else {
            this.isCompleted = true;
        }
    }
    
    /**
     * Reset daily completion status for repeating tasks
     */
    public void resetDailyCompletion() {
        if (isRepeating) {
            // Don't change lastCompletedDate, just let isCompletedToday() return false for new day
            // The task becomes "available" again for the new day
        }
    }
    
    /**
     * Get the last completed date string
     */
    public String getLastCompletedDate() {
        return lastCompletedDate;
    }
    
    /**
     * Mark this task as completed for today (using same logic as ReminderEntity)
     */
    public void markCompletedToday() {
        java.text.SimpleDateFormat dateFormat = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault());
        this.lastCompletedDate = dateFormat.format(new java.util.Date());
        
        // For non-repeating tasks, also mark as permanently completed
        if (!isRepeating) {
            this.isCompleted = true;
        }
    }
    
    /**
     * Check if this task should repeat on the given day of week
     * @param dayOfWeek Calendar.SUNDAY (1) through Calendar.SATURDAY (7)
     * @return true if task should repeat on this day
     */
    public boolean shouldRepeatOnDay(int dayOfWeek) {
        switch (dayOfWeek) {
            case java.util.Calendar.SUNDAY: return repeatOnSunday;
            case java.util.Calendar.MONDAY: return repeatOnMonday;
            case java.util.Calendar.TUESDAY: return repeatOnTuesday;
            case java.util.Calendar.WEDNESDAY: return repeatOnWednesday;
            case java.util.Calendar.THURSDAY: return repeatOnThursday;
            case java.util.Calendar.FRIDAY: return repeatOnFriday;
            case java.util.Calendar.SATURDAY: return repeatOnSaturday;
            default: return false;
        }
    }
    
    /**
     * Set all weekdays (Monday-Friday) to repeat
     */
    public void setWeekdaysRepeat(boolean repeat) {
        repeatOnMonday = repeat;
        repeatOnTuesday = repeat;
        repeatOnWednesday = repeat;
        repeatOnThursday = repeat;
        repeatOnFriday = repeat;
    }
    
    /**
     * Set weekends (Saturday-Sunday) to repeat
     */
    public void setWeekendsRepeat(boolean repeat) {
        repeatOnSaturday = repeat;
        repeatOnSunday = repeat;
    }
    
    /**
     * Set all days to repeat (daily)
     */
    public void setDailyRepeat(boolean repeat) {
        repeatOnSunday = repeat;
        repeatOnMonday = repeat;
        repeatOnTuesday = repeat;
        repeatOnWednesday = repeat;
        repeatOnThursday = repeat;
        repeatOnFriday = repeat;
        repeatOnSaturday = repeat;
    }
    
    /**
     * Get a human-readable description of repeat days
     */
    public String getRepeatDaysDescription() {
        if (!isRepeating) return "One-time";
        
        boolean[] days = {repeatOnSunday, repeatOnMonday, repeatOnTuesday, repeatOnWednesday, 
                         repeatOnThursday, repeatOnFriday, repeatOnSaturday};
        String[] dayNames = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
        
        // Check for common patterns
        if (repeatOnMonday && repeatOnTuesday && repeatOnWednesday && repeatOnThursday && repeatOnFriday 
            && !repeatOnSaturday && !repeatOnSunday) {
            return "Weekdays";
        }
        
        if (repeatOnSaturday && repeatOnSunday && !repeatOnMonday && !repeatOnTuesday 
            && !repeatOnWednesday && !repeatOnThursday && !repeatOnFriday) {
            return "Weekends";
        }
        
        boolean allDays = true;
        for (boolean day : days) {
            if (!day) {
                allDays = false;
                break;
            }
        }
        if (allDays) return "Daily";
        
        // Build custom description
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < days.length; i++) {
            if (days[i]) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(dayNames[i]);
            }
        }
        
        return sb.length() > 0 ? sb.toString() : "Never";
    }
}
