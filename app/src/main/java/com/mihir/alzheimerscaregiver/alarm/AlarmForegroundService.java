package com.mihir.alzheimerscaregiver.alarm;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import android.content.pm.ServiceInfo;

/**
 * Foreground service that holds a non-dismissible alarm notification with a full-screen intent.
 * This prevents users from swiping the alarm notification away while the alarm is active.
 */
public class AlarmForegroundService extends Service {

    public static final String EXTRA_REMINDER_ID = "reminder_id";
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_MESSAGE = "message";
    public static final String EXTRA_TYPE = "type";
    public static final String EXTRA_NOTIFICATION_ID = "notification_id";

    private static final String CHANNEL_ID = "alarm_fgs_channel_v1";

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            stopSelf();
            return START_NOT_STICKY;
        }

        String reminderId = intent.getStringExtra(EXTRA_REMINDER_ID);
        String title = intent.getStringExtra(EXTRA_TITLE);
        String message = intent.getStringExtra(EXTRA_MESSAGE);
        String type = intent.getStringExtra(EXTRA_TYPE);
        int notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, (int) System.currentTimeMillis());

        if (title == null) title = "Reminder";
        if (message == null) message = "You have a reminder";

        // Ensure channel exists with proper alarm semantics
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Alarm Foreground",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Foreground service for active alarms");
            channel.enableVibration(true);
            channel.setBypassDnd(true);
            channel.setLockscreenVisibility(NotificationCompat.VISIBILITY_PUBLIC);
            Uri alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (alarmSound == null) {
                alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            }
            AudioAttributes aa = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            channel.setSound(alarmSound, aa);
            nm.createNotificationChannel(channel);
        }

        // Full-screen intent to the AlarmActivity
        Intent activityIntent = new Intent(this, AlarmActivity.class);
        activityIntent.putExtra(EXTRA_REMINDER_ID, reminderId);
        activityIntent.putExtra(EXTRA_TITLE, title);
        activityIntent.putExtra(EXTRA_MESSAGE, message);
        activityIntent.putExtra(EXTRA_TYPE, type);
        activityIntent.putExtra(EXTRA_NOTIFICATION_ID, notificationId);
        activityIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent fullScreenPendingIntent = PendingIntent.getActivity(
                this,
                notificationId,
                activityIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Uri alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        if (alarmSound == null) {
            alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setAutoCancel(false)
                .setSound(alarmSound)
                .setVibrate(new long[]{0, 500, 500, 500, 500})
                .setFullScreenIntent(fullScreenPendingIntent, true)
                .setContentIntent(fullScreenPendingIntent);

        Notification notification = builder.build();
        notification.flags |= Notification.FLAG_NO_CLEAR;
        notification.flags |= Notification.FLAG_ONGOING_EVENT;

        // Start as a foreground service so the notification cannot be swiped away
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Use the correct mediaPlayback type constant
            int fgServiceType = ForegroundType.mediaPlayback();
            startForeground(notificationId, notification, fgServiceType);
        } else {
            startForeground(notificationId, notification);
        }

        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        stopForeground(true);
        super.onDestroy();
    }

    // Simple wrapper to avoid direct compile-time dependency on newer constants here
    private static class ForegroundType {
        static int mediaPlayback() {
            if (Build.VERSION.SDK_INT >= 29) {
                return ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK; // Correct constant on API 29+
            }
            return 0x00000002; // Correct numeric value for MEDIA_PLAYBACK on older compile contexts
        }
    }
}
