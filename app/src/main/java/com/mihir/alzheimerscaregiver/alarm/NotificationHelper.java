package com.mihir.alzheimerscaregiver.alarm;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.mihir.alzheimerscaregiver.MainActivity;
import com.mihir.alzheimerscaregiver.R;
import com.mihir.alzheimerscaregiver.RemindersActivity;

/**
 * Helper class for creating and showing notifications from alarms
 */
public class NotificationHelper {

    private static final String TAG = "NotificationHelper";
    public static final String CHANNEL_ID = "reminders_alarm_channel";
    
    private final Context context;

    public NotificationHelper(Context context) {
        this.context = context;
        createNotificationChannel();
    }

    private void createNotificationChannel() {
        // Create the notification channel for API 26+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Reminder Alarms",
                    NotificationManager.IMPORTANCE_HIGH
            );
            
            channel.setDescription("Notifications for scheduled reminders and medications");
            channel.enableVibration(true);
            channel.enableLights(true);
            
            // Register the channel with the system
            NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
                Log.d(TAG, "Notification channel created");
            }
        }
    }

    public void showNotification(String reminderId, String title, String message) {
        // Check notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) 
                    != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "Notification permission not granted");
                return;
            }
        }
        
        // Create intent for when the notification is tapped
        Intent intent = new Intent(context, RemindersActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Build the notification
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_brain)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setVibrate(new long[]{0, 500, 200, 500});
                
        // Get notification ID from reminderId to ensure each reminder has its own notification
        int notificationId = reminderId.hashCode();
        
        try {
            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
            notificationManager.notify(notificationId, builder.build());
            Log.d(TAG, "Notification shown for reminder: " + title);
        } catch (SecurityException e) {
            Log.e(TAG, "Security exception when showing notification", e);
        }
    }
}