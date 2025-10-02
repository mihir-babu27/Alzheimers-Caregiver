package com.mihir.alzheimerscaregiver.alarm;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.mihir.alzheimerscaregiver.MainActivity;
import com.mihir.alzheimerscaregiver.R;
import android.media.AudioAttributes;

/**
 * BroadcastReceiver that shows a notification when an alarm triggers
 */
public class AlarmReceiver extends BroadcastReceiver {
    private static final String TAG = "AlarmReceiver";
    private static final String CHANNEL_ID = "reminder_notifications";
    private static final String CHANNEL_NAME = "Reminders";
    private static final String EXTRA_REMINDER_ID = "reminder_id";
    private static final String EXTRA_TITLE = "title";
    private static final String EXTRA_MESSAGE = "message";
    private static final String EXTRA_TYPE = "type";
    
    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "Alarm triggered!");
        
        try {
            // Get reminder details from intent
            String reminderId = intent.getStringExtra(EXTRA_REMINDER_ID);
            String title = intent.getStringExtra(EXTRA_TITLE);
            String message = intent.getStringExtra(EXTRA_MESSAGE);
            String type = intent.getStringExtra(EXTRA_TYPE);
            
            // Default values if null
            if (title == null) title = "Reminder";
            if (message == null) message = "You have a reminder";
            
            Log.d(TAG, "Alarm for: " + title);
            
            // Start a foreground service that posts a non-dismissible full-screen alarm notification
            startAlarmService(context, reminderId, title, message, type);
            
        } catch (Exception e) {
            Log.e(TAG, "Error processing alarm: " + e.getMessage(), e);
        }
    }
    
    /**
     * Shows a full-screen alarm notification that behaves exactly like a clock app
     */
    private void startAlarmService(Context context, String reminderId, String title, String message, String type) {
        try {
            int notificationId = reminderId != null ? reminderId.hashCode() : (int) System.currentTimeMillis();
            Intent serviceIntent = new Intent(context, AlarmForegroundService.class);
            serviceIntent.putExtra(AlarmForegroundService.EXTRA_REMINDER_ID, reminderId);
            serviceIntent.putExtra(AlarmForegroundService.EXTRA_TITLE, title);
            serviceIntent.putExtra(AlarmForegroundService.EXTRA_MESSAGE, message);
            serviceIntent.putExtra(AlarmForegroundService.EXTRA_TYPE, type);
            serviceIntent.putExtra(AlarmForegroundService.EXTRA_NOTIFICATION_ID, notificationId);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }
            Log.d(TAG, "Started AlarmForegroundService for non-dismissible alarm");
            
        } catch (Exception e) {
            Log.e(TAG, "Error showing full-screen alarm", e);
        }
    }
    
}