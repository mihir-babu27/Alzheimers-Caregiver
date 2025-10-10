package com.mihir.alzheimerscaregiver.data;

import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.mihir.alzheimerscaregiver.alarm.AlarmScheduler;
import com.mihir.alzheimerscaregiver.caretaker.CaretakerNotificationScheduler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Repository class for managing reminders in Firestore
 * and scheduling local alarms
 */
public class ReminderRepository {
    private static final String TAG = "ReminderRepository";
    private static final String COLLECTION_REMINDERS = "reminders";
    
    private final FirebaseFirestore db;
    private final FirebaseAuth auth;
    private final AlarmScheduler alarmScheduler;
    private final CaretakerNotificationScheduler caretakerScheduler;
    private final MutableLiveData<List<ReminderEntity>> remindersLiveData;
    
    public ReminderRepository(AlarmScheduler alarmScheduler) {
        this.db = FirebaseFirestore.getInstance();
        this.auth = FirebaseAuth.getInstance();
        this.alarmScheduler = alarmScheduler;
        // For now, caretaker scheduler is null if we don't have context access
        // This will be set separately for activities that need caretaker notifications
        this.caretakerScheduler = null;
        this.remindersLiveData = new MutableLiveData<>(new ArrayList<>());
    }
    
    /**
     * Constructor with context for caretaker notifications
     */
    public ReminderRepository(AlarmScheduler alarmScheduler, android.content.Context context) {
        this.db = FirebaseFirestore.getInstance();
        this.auth = FirebaseAuth.getInstance();
        this.alarmScheduler = alarmScheduler;
        this.caretakerScheduler = new CaretakerNotificationScheduler(context);
        this.remindersLiveData = new MutableLiveData<>(new ArrayList<>());
    }
    
    /**
     * Get the current patient ID
     */
    private String getCurrentPatientId() {
        FirebaseUser user = auth.getCurrentUser();
        if (user != null) {
            return user.getUid();
        }
        return null;
    }
    
    /**
     * Get all reminders for the current patient
     */
    public LiveData<List<ReminderEntity>> getReminders() {
        String patientId = getCurrentPatientId();
        if (patientId == null) {
            Log.w(TAG, "getReminders: No authenticated user");
            return remindersLiveData;
        }
        
        db.collection(COLLECTION_REMINDERS)
                .whereEqualTo("patientId", patientId)
                .orderBy("timeMillis", Query.Direction.ASCENDING)
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null) {
                        Log.e(TAG, "Listen failed.", e);
                        return;
                    }
                    
                    if (snapshots != null) {
                        List<ReminderEntity> reminders = new ArrayList<>();
                        for (QueryDocumentSnapshot doc : snapshots) {
                            ReminderEntity reminder = doc.toObject(ReminderEntity.class);
                            
                            // Check if we need to schedule an alarm for this reminder
                            if (reminder.needsAlarmUpdate() || !alarmScheduler.isAlarmScheduled(reminder.getId())) {
                                // Schedule alarm with repeating flag
                                alarmScheduler.scheduleAlarm(reminder, reminder.isRepeating());
                                reminder.setNeedsAlarmUpdate(false);
                            }
                            
                            reminders.add(reminder);
                        }
                        remindersLiveData.setValue(reminders);
                    }
                });
        
        return remindersLiveData;
    }
    
    /**
     * Add a new reminder
     */
    public Task<DocumentReference> addReminder(ReminderEntity reminder) {
        String patientId = getCurrentPatientId();
        if (patientId == null) {
            Log.w(TAG, "addReminder: No authenticated user");
            return null;
        }
        
        reminder.setPatientId(patientId);
        
        return db.collection(COLLECTION_REMINDERS)
                .add(reminder.toMap())
                .addOnSuccessListener(documentReference -> {
                    reminder.setId(documentReference.getId());
                    alarmScheduler.scheduleAlarm(reminder, reminder.isRepeating());
                    
                    // Caretaker notifications are handled by repository/ReminderRepository instead
                    
                    Log.d(TAG, "Reminder added with ID: " + documentReference.getId());
                })
                .addOnFailureListener(e -> Log.e(TAG, "Error adding reminder", e));
    }
    
    /**
     * Update an existing reminder
     */
    public Task<Void> updateReminder(ReminderEntity reminder) {
        if (reminder.getId() == null) {
            Log.e(TAG, "updateReminder: Reminder ID is null");
            return null;
        }
        
        // Cancel existing alarm before updating
        alarmScheduler.cancelAlarm(reminder.getId());
        
        // Update in Firestore
        return db.collection(COLLECTION_REMINDERS)
                .document(reminder.getId())
                .update(reminder.toMap())
                .addOnSuccessListener(aVoid -> {
                    // Schedule new alarm with updated time
                    alarmScheduler.scheduleAlarm(reminder, reminder.isRepeating());
                    Log.d(TAG, "Reminder updated: " + reminder.getId());
                })
                .addOnFailureListener(e -> Log.e(TAG, "Error updating reminder", e));
    }
    
    /**
     * Delete a reminder
     */
    public Task<Void> deleteReminder(String reminderId) {
        // Cancel alarm for this reminder
        alarmScheduler.cancelAlarm(reminderId);
        
        // Delete from Firestore
        return db.collection(COLLECTION_REMINDERS)
                .document(reminderId)
                .delete()
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Reminder deleted: " + reminderId))
                .addOnFailureListener(e -> Log.e(TAG, "Error deleting reminder", e));
    }
    
    /**
     * Mark a reminder as completed
     */
    public Task<Void> completeReminder(String reminderId) {
        return db.collection(COLLECTION_REMINDERS)
                .document(reminderId)
                .get()
                .continueWithTask(task -> {
                    if (!task.isSuccessful() || !task.getResult().exists()) {
                        throw new RuntimeException("Reminder not found");
                    }
                    
                    ReminderEntity reminder = task.getResult().toObject(ReminderEntity.class);
                    if (reminder == null) {
                        throw new RuntimeException("Failed to parse reminder");
                    }
                    
                    // Mark as completed for today
                    reminder.markCompletedToday();
                    
                    // Update the reminder in Firestore
                    Map<String, Object> updates = new HashMap<>();
                    updates.put("lastCompletedDate", reminder.getLastCompletedDate());
                    
                    // Only mark as permanently completed if it's not a repeating reminder
                    if (!reminder.isRepeating()) {
                        updates.put("isCompleted", true);
                        // Cancel alarm for non-repeating reminders
                        alarmScheduler.cancelAlarm(reminderId);
                        Log.d(TAG, "Non-repeating reminder marked complete: " + reminderId);
                    } else {
                        Log.d(TAG, "Repeating reminder marked complete for today: " + reminderId);
                    }
                    
                    // Cancel/resolve caretaker notifications when reminder is completed
                    if (caretakerScheduler != null) {
                        caretakerScheduler.resolveIncompleteReminderAlert(reminderId);
                    }
                    
                    return db.collection(COLLECTION_REMINDERS)
                            .document(reminderId)
                            .update(updates);
                })
                .addOnFailureListener(e -> Log.e(TAG, "Error marking reminder complete", e));
    }
    
    /**
     * Get all reminders for rescheduling on device boot
     */
    public void rescheduleAllAlarms() {
        String patientId = getCurrentPatientId();
        if (patientId == null) {
            Log.w(TAG, "rescheduleAllAlarms: No authenticated user");
            return;
        }
        
        db.collection(COLLECTION_REMINDERS)
                .whereEqualTo("patientId", patientId)
                .whereEqualTo("isCompleted", false)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        ReminderEntity reminder = doc.toObject(ReminderEntity.class);
                        if (reminder.getTimeMillis() > System.currentTimeMillis() || reminder.isRepeating()) {
                            // Reschedule future reminders or repeating reminders (even if past due)
                            alarmScheduler.scheduleAlarm(reminder, reminder.isRepeating());
                            Log.d(TAG, "Rescheduled alarm for reminder: " + reminder.getId());
                        }
                    }
                })
                .addOnFailureListener(e -> Log.e(TAG, "Error fetching reminders for rescheduling", e));
    }
    
    /**
     * Reset daily completion status for all repeating reminders
     * This is called at midnight to make repeating reminders appear unchecked for the new day
     */
    public void resetDailyCompletionStatus() {
        String patientId = getCurrentPatientId();
        if (patientId == null) {
            Log.w(TAG, "resetDailyCompletionStatus: No authenticated user");
            return;
        }
        
        Log.d(TAG, "Starting daily completion status reset");
        
        db.collection(COLLECTION_REMINDERS)
                .whereEqualTo("patientId", patientId)
                .whereEqualTo("isRepeating", true)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        ReminderEntity reminder = doc.toObject(ReminderEntity.class);
                        if (reminder != null && reminder.isCompletedToday()) {
                            // Reset the lastCompletedDate to make it appear unchecked
                            Map<String, Object> updates = new HashMap<>();
                            updates.put("lastCompletedDate", null);
                            
                            db.collection(COLLECTION_REMINDERS)
                                    .document(doc.getId())
                                    .update(updates)
                                    .addOnSuccessListener(aVoid -> 
                                        Log.d(TAG, "Reset completion status for reminder: " + doc.getId()))
                                    .addOnFailureListener(e -> 
                                        Log.e(TAG, "Error resetting completion status for " + doc.getId(), e));
                        }
                    }
                    Log.d(TAG, "Daily completion status reset completed");
                })
                .addOnFailureListener(e -> Log.e(TAG, "Error fetching repeating reminders for reset", e));
    }
}