package com.mihir.alzheimerscaregiver.caretaker;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.mihir.alzheimerscaregiver.data.entity.IncompleteReminderAlert;
import com.mihir.alzheimerscaregiver.data.entity.ReminderEntity;

import java.util.HashMap;
import java.util.Map;

/**
 * Service to monitor reminders and create alerts for caretakers when patients don't complete them
 * This creates delayed alarms that check if reminders were completed and notify caretakers if not
 */
public class CaretakerNotificationScheduler {
    private static final String TAG = "CaretakerNotificationScheduler";
    private static final String COLLECTION_INCOMPLETE_ALERTS = "incomplete_reminder_alerts";
    
    // Configurable delay times (in minutes)
    public static final int DEFAULT_DELAY_MINUTES = 15;
    public static final int SECONDARY_DELAY_MINUTES = 60;
    public static final int FINAL_DELAY_MINUTES = 180; // 3 hours
    
    private final Context context;
    private final FirebaseFirestore db;
    private final FirebaseAuth auth;
    private final AlarmManager alarmManager;
    
    public CaretakerNotificationScheduler(Context context) {
        this.context = context;
        this.db = FirebaseFirestore.getInstance();
        this.auth = FirebaseAuth.getInstance();
        this.alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
    }
    
    /**
     * Schedule caretaker notification checks for a reminder
     * This will create delayed alarms to check if the reminder was completed
     */
    public void scheduleCaretakerNotifications(ReminderEntity reminder) {
        if (reminder == null || reminder.id == null) {
            Log.e(TAG, "Cannot schedule caretaker notifications: invalid reminder");
            return;
        }
        
        long reminderTime = reminder.scheduledTimeEpochMillis;
        long currentTime = System.currentTimeMillis();
        
        // Only schedule for future reminders or current reminders
        if (reminderTime > currentTime - (5 * 60 * 1000)) { // Allow 5 minute buffer for current reminders
            scheduleDelayedCheck(reminder, DEFAULT_DELAY_MINUTES);
            scheduleDelayedCheck(reminder, SECONDARY_DELAY_MINUTES);
            scheduleDelayedCheck(reminder, FINAL_DELAY_MINUTES);
            
            Log.d(TAG, "Scheduled caretaker notification checks for reminder: " + reminder.title);
        }
    }
    
    /**
     * Schedule a delayed check to see if reminder was completed
     */
    private void scheduleDelayedCheck(ReminderEntity reminder, int delayMinutes) {
        long reminderTime = reminder.scheduledTimeEpochMillis;
        long checkTime = reminderTime + (delayMinutes * 60 * 1000);
        
        // Don't schedule checks for past times
        if (checkTime <= System.currentTimeMillis()) {
            return;
        }
        
        Intent intent = new Intent(context, CaretakerNotificationReceiver.class);
        intent.putExtra("reminderId", reminder.id);
        intent.putExtra("reminderTitle", reminder.title);
        intent.putExtra("reminderType", "medication"); // Default type since ReminderEntity doesn't have type field
        intent.putExtra("reminderScheduledTime", reminderTime);
        intent.putExtra("delayMinutes", delayMinutes);
        intent.putExtra("patientId", getCurrentPatientId());
        
        // Use reminder ID + delay as unique identifier
        int requestCode = (reminder.id + "_" + delayMinutes).hashCode();
        
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, checkTime, pendingIntent);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, checkTime, pendingIntent);
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, checkTime, pendingIntent);
            }
            
            Log.d(TAG, "Scheduled caretaker check for " + delayMinutes + " minutes after " + reminder.title);
        } catch (Exception e) {
            Log.e(TAG, "Failed to schedule caretaker notification check", e);
        }
    }
    
    /**
     * Cancel all caretaker notification checks for a reminder (e.g., when completed)
     */
    public void cancelCaretakerNotifications(String reminderId) {
        if (reminderId == null) return;
        
        int[] delays = {DEFAULT_DELAY_MINUTES, SECONDARY_DELAY_MINUTES, FINAL_DELAY_MINUTES};
        
        for (int delay : delays) {
            Intent intent = new Intent(context, CaretakerNotificationReceiver.class);
            int requestCode = (reminderId + "_" + delay).hashCode();
            
            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            
            alarmManager.cancel(pendingIntent);
        }
        
        Log.d(TAG, "Cancelled caretaker notification checks for reminder: " + reminderId);
    }
    
    /**
     * Create an incomplete reminder alert in Firestore for caretakers to see
     */
    public void createIncompleteReminderAlert(String reminderId, String reminderTitle, String reminderType,
                                            long reminderScheduledTime, int delayMinutes) {
        String patientId = getCurrentPatientId();
        if (patientId == null) {
            Log.e(TAG, "Cannot create alert: no patient ID");
            return;
        }
        
        IncompleteReminderAlert alert = new IncompleteReminderAlert(
            reminderId, patientId, reminderTitle, reminderType, reminderScheduledTime, delayMinutes
        );
        
        db.collection(COLLECTION_INCOMPLETE_ALERTS)
                .document(alert.getId())
                .set(alert.toMap())
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Created incomplete reminder alert: " + alert.getId());
                    // Optionally send push notification to caretaker app here
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to create incomplete reminder alert", e);
                });
    }
    
    /**
     * Resolve an incomplete reminder alert (when reminder is eventually completed)
     */
    public void resolveIncompleteReminderAlert(String reminderId) {
        // Cancel any pending caretaker notification checks
        cancelCaretakerNotifications(reminderId);
        
        // Update any existing alerts to resolved status
        int[] delays = {DEFAULT_DELAY_MINUTES, SECONDARY_DELAY_MINUTES, FINAL_DELAY_MINUTES};
        
        for (int delay : delays) {
            String alertId = reminderId + "_alert_" + delay + "min";
            
            Map<String, Object> updates = new HashMap<>();
            updates.put("isResolved", true);
            updates.put("resolvedTime", System.currentTimeMillis());
            updates.put("status", "resolved");
            
            db.collection(COLLECTION_INCOMPLETE_ALERTS)
                    .document(alertId)
                    .update(updates)
                    .addOnSuccessListener(aVoid -> 
                        Log.d(TAG, "Resolved incomplete reminder alert: " + alertId))
                    .addOnFailureListener(e -> 
                        Log.w(TAG, "Failed to resolve alert (may not exist): " + alertId));
        }
    }
    
    /**
     * Get current patient ID from Firebase Auth
     */
    private String getCurrentPatientId() {
        FirebaseUser user = auth.getCurrentUser();
        return user != null ? user.getUid() : null;
    }
}