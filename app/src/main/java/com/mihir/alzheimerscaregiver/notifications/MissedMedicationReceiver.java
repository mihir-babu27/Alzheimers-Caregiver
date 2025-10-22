package com.mihir.alzheimerscaregiver.notifications;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import com.mihir.alzheimerscaregiver.repository.ReminderRepository;
import com.mihir.alzheimerscaregiver.data.entity.ReminderEntity;
import com.mihir.alzheimerscaregiver.utils.FCMNotificationSender;
import com.google.firebase.auth.FirebaseAuth;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Receives broadcast when it's time to check if a medication reminder was completed
 * If not completed, sends notification to caretaker via FCM
 */
public class MissedMedicationReceiver extends BroadcastReceiver {
    
    private static final String TAG = "MissedMedicationReceiver";
    
    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            String reminderId = intent.getStringExtra("reminderId");
            String reminderTitle = intent.getStringExtra("reminderTitle");
            long scheduledTime = intent.getLongExtra("scheduledTime", 0);
            String medicineNames = intent.getStringExtra("medicineNames");
            
            Log.d(TAG, "Checking missed medication: " + reminderTitle + " (ID: " + reminderId + ")");
            
            if (reminderId == null || reminderTitle == null) {
                Log.w(TAG, "Invalid reminder data for missed medication check");
                return;
            }
            
            // Check if the reminder was completed
            checkReminderCompletionAndNotify(context, reminderId, reminderTitle, scheduledTime, medicineNames);
            
        } catch (Exception e) {
            Log.e(TAG, "Error in missed medication check", e);
        }
    }
    
    /**
     * Check if reminder was completed and send notification to caretaker if not
     */
    private void checkReminderCompletionAndNotify(Context context, String reminderId, 
                                                  String reminderTitle, long scheduledTime, 
                                                  String medicineNames) {
        
        ReminderRepository repository = new ReminderRepository(context);
        
        repository.getById(reminderId, new ReminderRepository.FirebaseCallback<ReminderEntity>() {
            @Override
            public void onSuccess(ReminderEntity reminder) {
                if (reminder == null) {
                    Log.d(TAG, "Reminder not found, may have been deleted: " + reminderId);
                    return;
                }
                
                // Check if reminder was completed
                boolean isCompleted = isReminderCompleted(reminder);
                
                if (!isCompleted) {
                    Log.d(TAG, "Medication not taken, sending notification to caretaker");
                    sendMissedMedicationNotification(context, reminder, medicineNames);
                } else {
                    Log.d(TAG, "Medication was taken on time: " + reminder.title);
                }
            }
            
            @Override
            public void onError(String error) {
                Log.e(TAG, "Error retrieving reminder for missed medication check: " + error);
                // Send fallback notification if we can't verify completion
                sendMissedMedicationNotificationFallback(context, reminderId, reminderTitle, medicineNames);
            }
        });
    }
    
    /**
     * Check if reminder was completed for the scheduled time
     */
    private boolean isReminderCompleted(ReminderEntity reminder) {
        if (reminder.isRepeating) {
            // For repeating reminders, check if completed today
            return reminder.isCompletedToday();
        } else {
            // For non-repeating reminders, check permanent completion status
            return reminder.isCompleted;
        }
    }
    
    /**
     * Send missed medication notification to caretaker via FCM
     */
    private void sendMissedMedicationNotification(Context context, ReminderEntity reminder, String medicineNames) {
        try {
            // Get current patient ID
            FirebaseAuth auth = FirebaseAuth.getInstance();
            if (auth.getCurrentUser() == null) {
                Log.w(TAG, "No authenticated user, cannot send missed medication notification");
                return;
            }
            
            String patientId = auth.getCurrentUser().getUid();
            String patientName = auth.getCurrentUser().getDisplayName();
            if (patientName == null || patientName.isEmpty()) {
                patientName = "Patient";
            }
            
            // Format scheduled time for display
            String scheduledTimeStr = new SimpleDateFormat("HH:mm", Locale.getDefault())
                    .format(new Date(reminder.scheduledTimeEpochMillis));
            
            // TEMPORARY: Show local notification for testing
            String testMessage = "MISSED MEDICATION ALERT: " + patientName + " did not take " + 
                                (medicineNames != null ? medicineNames : reminder.title) + 
                                " scheduled at " + scheduledTimeStr;
            
            // Show local notification on Patient device for testing
            NotificationUtils.showReminderNotification(context, "💊 MISSED MEDICATION TEST", testMessage);
            
            // Send notification using existing FCM infrastructure
            Log.d(TAG, "🔔 Starting FCM notification process...");
            Log.d(TAG, "📋 Patient ID: " + patientId);
            Log.d(TAG, "👤 Patient Name: " + patientName);
            Log.d(TAG, "💊 Medicine: " + (medicineNames != null ? medicineNames : reminder.title));
            
            FCMNotificationSender fcmSender = new FCMNotificationSender(context);
            fcmSender.sendMissedMedicationAlert(patientId, patientName, 
                                              medicineNames != null ? medicineNames : reminder.title, 
                                              scheduledTimeStr);
            
            Log.d(TAG, "✅ FCM notification method called for: " + reminder.title);
            Log.d(TAG, "🔍 Check FCMNotificationSender logs for delivery details");
            Log.d(TAG, "LOCAL TEST NOTIFICATION: " + testMessage);
            
        } catch (Exception e) {
            Log.e(TAG, "Error sending missed medication notification", e);
        }
    }
    
    /**
     * Fallback method to send notification when we can't verify reminder details
     */
    private void sendMissedMedicationNotificationFallback(Context context, String reminderId, 
                                                         String reminderTitle, String medicineNames) {
        try {
            FirebaseAuth auth = FirebaseAuth.getInstance();
            if (auth.getCurrentUser() == null) {
                return;
            }
            
            String patientId = auth.getCurrentUser().getUid();
            String patientName = auth.getCurrentUser().getDisplayName();
            if (patientName == null || patientName.isEmpty()) {
                patientName = "Patient";
            }
            
            String title = "Missed Medication Alert";
            String message = String.format("Patient %s may have missed their medication: %s", 
                                         patientName, 
                                         medicineNames != null ? medicineNames : reminderTitle);
            
            FCMNotificationSender fcmSender = new FCMNotificationSender(context);
            fcmSender.sendGeneralNotification(patientId, title, message);
            
            Log.d(TAG, "Sent fallback missed medication notification for: " + reminderTitle);
            
        } catch (Exception e) {
            Log.e(TAG, "Error sending fallback missed medication notification", e);
        }
    }
}