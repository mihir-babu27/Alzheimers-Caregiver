package com.mihir.alzheimerscaregiver.alarm;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.mihir.alzheimerscaregiver.data.ReminderRepository;

/**
 * BroadcastReceiver that reschedules all alarms after device reboot
 */
public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "BootReceiver";
    
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction() != null && 
                (intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED) ||
                 intent.getAction().equals("android.intent.action.QUICKBOOT_POWERON"))) {
            
            Log.d(TAG, "Boot completed, rescheduling alarms");
            
            // Create alarm scheduler
            AlarmScheduler alarmScheduler = new AlarmScheduler(context);
            
            // Clear alarm tracker to force rescheduling
            alarmScheduler.clearAlarmTracker();
            
            // Create repository and reschedule all alarms
            ReminderRepository repository = new ReminderRepository(alarmScheduler);
            
            // Reschedule all alarms
            repository.rescheduleAllAlarms();
            
            // Also ensure periodic sync is scheduled as a safety net
            try {
                com.mihir.alzheimerscaregiver.sync.ReminderSyncScheduler.schedulePeriodic(context.getApplicationContext());
            } catch (Exception ex) {
                Log.w(TAG, "Failed to schedule periodic sync after boot", ex);
            }
            
            Log.d(TAG, "Alarm rescheduling initiated");
        }
    }
}