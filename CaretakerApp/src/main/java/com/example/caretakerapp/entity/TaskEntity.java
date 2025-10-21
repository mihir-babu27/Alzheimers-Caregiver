package com.example.caretakerapp.entity;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class TaskEntity {
    private String taskId;
    private String taskName;
    private String taskDescription;
    private String category;
    private String scheduledTime;
    private boolean isRepeating;
    
    // Day-based repeat pattern
    private boolean repeatOnSunday;
    private boolean repeatOnMonday;
    private boolean repeatOnTuesday;
    private boolean repeatOnWednesday;
    private boolean repeatOnThursday;
    private boolean repeatOnFriday;
    private boolean repeatOnSaturday;
    
    // Completion tracking
    private String lastCompletedDate;
    private boolean isCompleted;
    
    // Alarm and notification settings
    private boolean enableAlarm;
    private boolean enableCaretakerNotification;
    
    // Timestamps
    private long createdAt;
    private long updatedAt;

    // Default constructor for Firebase
    public TaskEntity() {
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
        this.enableAlarm = true;
        this.enableCaretakerNotification = true;
    }

    // Constructor
    public TaskEntity(String taskName, String taskDescription, String category, String scheduledTime) {
        this();
        this.taskName = taskName;
        this.taskDescription = taskDescription;
        this.category = category;
        this.scheduledTime = scheduledTime;
    }

    // Check if task is completed for today
    public boolean isCompletedToday() {
        if (lastCompletedDate == null) {
            return false;
        }
        
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        String today = dateFormat.format(new Date());
        return today.equals(lastCompletedDate);
    }

    // Check if task is scheduled for today
    public boolean isScheduledForToday() {
        if (!isRepeating) {
            return true; // One-time tasks are always "scheduled"
        }

        Calendar calendar = Calendar.getInstance();
        int dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK);

        switch (dayOfWeek) {
            case Calendar.SUNDAY:
                return repeatOnSunday;
            case Calendar.MONDAY:
                return repeatOnMonday;
            case Calendar.TUESDAY:
                return repeatOnTuesday;
            case Calendar.WEDNESDAY:
                return repeatOnWednesday;
            case Calendar.THURSDAY:
                return repeatOnThursday;
            case Calendar.FRIDAY:
                return repeatOnFriday;
            case Calendar.SATURDAY:
                return repeatOnSaturday;
            default:
                return false;
        }
    }

    // Get repeat pattern as readable string
    public String getRepeatPatternString() {
        if (!isRepeating) {
            return "One-time";
        }

        StringBuilder pattern = new StringBuilder();
        if (repeatOnSunday) pattern.append("Sun ");
        if (repeatOnMonday) pattern.append("Mon ");
        if (repeatOnTuesday) pattern.append("Tue ");
        if (repeatOnWednesday) pattern.append("Wed ");
        if (repeatOnThursday) pattern.append("Thu ");
        if (repeatOnFriday) pattern.append("Fri ");
        if (repeatOnSaturday) pattern.append("Sat ");

        if (pattern.length() == 0) {
            return "No days selected";
        }

        return pattern.toString().trim();
    }

    // Check if all days are selected (daily)
    public boolean isDailyRepeat() {
        return repeatOnSunday && repeatOnMonday && repeatOnTuesday && 
               repeatOnWednesday && repeatOnThursday && repeatOnFriday && repeatOnSaturday;
    }

    // Check if weekdays only are selected
    public boolean isWeekdaysRepeat() {
        return repeatOnMonday && repeatOnTuesday && repeatOnWednesday && 
               repeatOnThursday && repeatOnFriday && 
               !repeatOnSunday && !repeatOnSaturday;
    }

    // Check if weekends only are selected
    public boolean isWeekendsRepeat() {
        return repeatOnSunday && repeatOnSaturday && 
               !repeatOnMonday && !repeatOnTuesday && !repeatOnWednesday && 
               !repeatOnThursday && !repeatOnFriday;
    }

    // Set daily repeat pattern
    public void setDailyRepeat() {
        repeatOnSunday = true;
        repeatOnMonday = true;
        repeatOnTuesday = true;
        repeatOnWednesday = true;
        repeatOnThursday = true;
        repeatOnFriday = true;
        repeatOnSaturday = true;
    }

    // Set weekdays repeat pattern
    public void setWeekdaysRepeat() {
        repeatOnSunday = false;
        repeatOnMonday = true;
        repeatOnTuesday = true;
        repeatOnWednesday = true;
        repeatOnThursday = true;
        repeatOnFriday = true;
        repeatOnSaturday = false;
    }

    // Set weekends repeat pattern
    public void setWeekendsRepeat() {
        repeatOnSunday = true;
        repeatOnMonday = false;
        repeatOnTuesday = false;
        repeatOnWednesday = false;
        repeatOnThursday = false;
        repeatOnFriday = false;
        repeatOnSaturday = true;
    }

    // Clear all repeat days
    public void clearAllRepeatDays() {
        repeatOnSunday = false;
        repeatOnMonday = false;
        repeatOnTuesday = false;
        repeatOnWednesday = false;
        repeatOnThursday = false;
        repeatOnFriday = false;
        repeatOnSaturday = false;
    }

    // Getters and Setters
    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getTaskName() {
        return taskName;
    }

    public void setTaskName(String taskName) {
        this.taskName = taskName;
    }

    public String getTaskDescription() {
        return taskDescription;
    }

    public void setTaskDescription(String taskDescription) {
        this.taskDescription = taskDescription;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getScheduledTime() {
        return scheduledTime;
    }

    public void setScheduledTime(String scheduledTime) {
        this.scheduledTime = scheduledTime;
    }

    public boolean isRepeating() {
        return isRepeating;
    }

    public void setRepeating(boolean repeating) {
        isRepeating = repeating;
    }

    public boolean isRepeatOnSunday() {
        return repeatOnSunday;
    }

    public void setRepeatOnSunday(boolean repeatOnSunday) {
        this.repeatOnSunday = repeatOnSunday;
    }

    public boolean isRepeatOnMonday() {
        return repeatOnMonday;
    }

    public void setRepeatOnMonday(boolean repeatOnMonday) {
        this.repeatOnMonday = repeatOnMonday;
    }

    public boolean isRepeatOnTuesday() {
        return repeatOnTuesday;
    }

    public void setRepeatOnTuesday(boolean repeatOnTuesday) {
        this.repeatOnTuesday = repeatOnTuesday;
    }

    public boolean isRepeatOnWednesday() {
        return repeatOnWednesday;
    }

    public void setRepeatOnWednesday(boolean repeatOnWednesday) {
        this.repeatOnWednesday = repeatOnWednesday;
    }

    public boolean isRepeatOnThursday() {
        return repeatOnThursday;
    }

    public void setRepeatOnThursday(boolean repeatOnThursday) {
        this.repeatOnThursday = repeatOnThursday;
    }

    public boolean isRepeatOnFriday() {
        return repeatOnFriday;
    }

    public void setRepeatOnFriday(boolean repeatOnFriday) {
        this.repeatOnFriday = repeatOnFriday;
    }

    public boolean isRepeatOnSaturday() {
        return repeatOnSaturday;
    }

    public void setRepeatOnSaturday(boolean repeatOnSaturday) {
        this.repeatOnSaturday = repeatOnSaturday;
    }

    public String getLastCompletedDate() {
        return lastCompletedDate;
    }

    public void setLastCompletedDate(String lastCompletedDate) {
        this.lastCompletedDate = lastCompletedDate;
    }

    public boolean isCompleted() {
        return isCompleted;
    }

    public void setCompleted(boolean completed) {
        isCompleted = completed;
    }

    public boolean isEnableAlarm() {
        return enableAlarm;
    }

    public void setEnableAlarm(boolean enableAlarm) {
        this.enableAlarm = enableAlarm;
    }

    public boolean isEnableCaretakerNotification() {
        return enableCaretakerNotification;
    }

    public void setEnableCaretakerNotification(boolean enableCaretakerNotification) {
        this.enableCaretakerNotification = enableCaretakerNotification;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public String toString() {
        return "TaskEntity{" +
                "taskId='" + taskId + '\'' +
                ", taskName='" + taskName + '\'' +
                ", category='" + category + '\'' +
                ", scheduledTime='" + scheduledTime + '\'' +
                ", isRepeating=" + isRepeating +
                ", repeatPattern='" + getRepeatPatternString() + '\'' +
                '}';
    }
}