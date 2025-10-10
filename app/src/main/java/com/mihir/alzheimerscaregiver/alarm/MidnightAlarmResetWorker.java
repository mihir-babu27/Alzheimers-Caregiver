package com.mihir.alzheimerscaregiver.alarm;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.mihir.alzheimerscaregiver.data.ReminderRepository;
import com.mihir.alzheimerscaregiver.repository.TaskRepository;

/**
 * WorkManager worker that runs daily at midnight to reset and reinitialize alarms
 * This ensures all alarms stay properly scheduled and handles timezone changes
 * Also resets daily completion status for both repeating reminders and tasks
 */
public class MidnightAlarmResetWorker extends Worker {
    private static final String TAG = "MidnightAlarmResetWorker";
    
    public MidnightAlarmResetWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }
    
    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "Starting midnight alarm reset job");
        
        try {
            Context context = getApplicationContext();
            
            // Create alarm scheduler
            AlarmScheduler alarmScheduler = new AlarmScheduler(context);
            
            // Clear the current alarm tracker to force rescheduling
            alarmScheduler.clearAlarmTracker();
            Log.d(TAG, "Cleared alarm tracker");
            
            // Create repository and reschedule all alarms
            ReminderRepository repository = new ReminderRepository(alarmScheduler);
            
            // This will trigger a refresh of all reminders from Firestore
            // and reschedule any that need rescheduling
            repository.rescheduleAllAlarms();
            Log.d(TAG, "Initiated alarm rescheduling from Firestore");
            
            // Also reschedule any repeating alarms that might have been missed
            rescheduleRepeatingAlarms(alarmScheduler);
            
            // Reset daily completion status for repeating reminders
            resetDailyCompletionStatus(repository);
            
            // Reset daily completion status for repeating tasks
            resetTasksDailyCompletionStatus();
            
            Log.d(TAG, "Midnight alarm reset job completed successfully");
            return Result.success();
            
        } catch (Exception e) {
            Log.e(TAG, "Error in midnight alarm reset job", e);
            return Result.retry();
        }
    }
    
    /**
     * Reschedule any repeating alarms that might need updating
     */
    private void rescheduleRepeatingAlarms(AlarmScheduler scheduler) {
        try {
            // This method would iterate through stored repeating alarms
            // and ensure they're scheduled for the correct next occurrence
            // For now, we rely on the Firestore sync to handle this
            Log.d(TAG, "Checking repeating alarms for rescheduling");
            
            // The AlarmScheduler's SharedPreferences contain repeating alarm info
            // Future enhancement: iterate through these and reschedule as needed
            
        } catch (Exception e) {
            Log.e(TAG, "Error rescheduling repeating alarms", e);
        }
    }
    
    /**
     * Reset daily completion status for repeating reminders
     * This ensures repeating reminders show as unchecked after midnight
     */
    private void resetDailyCompletionStatus(ReminderRepository repository) {
        try {
            Log.d(TAG, "Resetting daily completion status for repeating reminders");
            repository.resetDailyCompletionStatus();
        } catch (Exception e) {
            Log.e(TAG, "Error resetting daily completion status", e);
        }
    }
    
    /**
     * Reset daily completion status for repeating tasks and reschedule their alarms
     * This ensures repeating tasks show as unchecked after midnight and notifications fire
     */
    private void resetTasksDailyCompletionStatus() {
        try {
            Context context = getApplicationContext();
            Log.d(TAG, "Resetting daily completion status and rescheduling alarms for repeating tasks");
            
            TaskRepository taskRepository = new TaskRepository(context);
            
            // Reset completion status
            taskRepository.resetDailyCompletionStatus();
            
            // Reschedule task alarms for the new day
            taskRepository.rescheduleAllTaskAlarms(context);
            
        } catch (Exception e) {
            Log.e(TAG, "Error resetting daily completion status and rescheduling task alarms", e);
        }
    }
}