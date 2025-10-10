package com.mihir.alzheimerscaregiver.caretaker;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.mihir.alzheimerscaregiver.data.entity.ReminderEntity;

/**
 * BroadcastReceiver that checks if reminders were completed and creates caretaker alerts if not
 * This is triggered by delayed alarms scheduled by CaretakerNotificationScheduler
 */
public class CaretakerNotificationReceiver extends BroadcastReceiver {
    private static final String TAG = "CaretakerNotificationReceiver";
    private static final String COLLECTION_REMINDERS = "reminders";
    
    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "Checking reminder completion for caretaker notification");
        
        try {
            // Extract reminder information from intent
            String reminderId = intent.getStringExtra("reminderId");
            String reminderTitle = intent.getStringExtra("reminderTitle");
            String reminderType = intent.getStringExtra("reminderType");
            long reminderScheduledTime = intent.getLongExtra("reminderScheduledTime", 0);
            int delayMinutes = intent.getIntExtra("delayMinutes", 0);
            String patientId = intent.getStringExtra("patientId");
            
            if (reminderId == null || patientId == null) {
                Log.e(TAG, "Missing required data in intent");
                return;
            }
            
            // Check if the reminder was completed
            checkReminderCompletion(context, reminderId, reminderTitle, reminderType, 
                                  reminderScheduledTime, delayMinutes, patientId);
            
        } catch (Exception e) {
            Log.e(TAG, "Error in caretaker notification receiver", e);
        }
    }
    
    /**
     * Check if the reminder was completed and create caretaker alert if not
     */
    private void checkReminderCompletion(Context context, String reminderId, String reminderTitle,
                                       String reminderType, long reminderScheduledTime, 
                                       int delayMinutes, String patientId) {
        
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        
        db.collection(COLLECTION_REMINDERS)
                .document(reminderId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        ReminderEntity reminder = documentSnapshot.toObject(ReminderEntity.class);
                        if (reminder != null) {
                            // Check if reminder is completed
                            boolean isCompleted = reminder.isRepeating ? 
                                isCompletedToday(reminder) : reminder.isCompleted;
                            
                            if (!isCompleted) {
                                // Reminder is not completed - create caretaker alert
                                Log.d(TAG, "Creating caretaker alert for incomplete reminder: " + reminderTitle);
                                
                                CaretakerNotificationScheduler scheduler = new CaretakerNotificationScheduler(context);
                                scheduler.createIncompleteReminderAlert(
                                    reminderId, reminderTitle, reminderType, 
                                    reminderScheduledTime, delayMinutes
                                );
                            } else {
                                Log.d(TAG, "Reminder was completed, no alert needed: " + reminderTitle);
                            }
                        }
                    } else {
                        Log.w(TAG, "Reminder no longer exists: " + reminderId);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to check reminder completion: " + reminderId, e);
                });
    }
    
    /**
     * Check if a repeating reminder has been completed today
     */
    private boolean isCompletedToday(ReminderEntity reminder) {
        if (reminder.lastCompletedDate == null) {
            return false;
        }
        
        // Check if lastCompletedDate is today
        String today = java.time.LocalDate.now().toString();
        return today.equals(reminder.lastCompletedDate);
    }
}