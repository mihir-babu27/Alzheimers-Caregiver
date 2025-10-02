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

import java.util.ArrayList;
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
    private final MutableLiveData<List<ReminderEntity>> remindersLiveData;
    
    public ReminderRepository(AlarmScheduler alarmScheduler) {
        this.db = FirebaseFirestore.getInstance();
        this.auth = FirebaseAuth.getInstance();
        this.alarmScheduler = alarmScheduler;
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
                                alarmScheduler.scheduleAlarm(reminder);
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
                    alarmScheduler.scheduleAlarm(reminder);
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
                    alarmScheduler.scheduleAlarm(reminder);
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
                .update("isCompleted", true)
                .addOnSuccessListener(aVoid -> {
                    // Optionally cancel alarm if reminder is marked as completed
                    alarmScheduler.cancelAlarm(reminderId);
                    Log.d(TAG, "Reminder marked complete: " + reminderId);
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
                        if (reminder.getTimeMillis() > System.currentTimeMillis()) {
                            // Only reschedule future reminders
                            alarmScheduler.scheduleAlarm(reminder);
                            Log.d(TAG, "Rescheduled alarm for reminder: " + reminder.getId());
                        }
                    }
                })
                .addOnFailureListener(e -> Log.e(TAG, "Error fetching reminders for rescheduling", e));
    }
}