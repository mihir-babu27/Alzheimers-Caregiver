package com.mihir.alzheimerscaregiver.notifications;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import android.content.SharedPreferences;

import com.mihir.alzheimerscaregiver.EnhancedMmseQuizActivity;

public class MmseReminderReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Intent openIntent = new Intent(context, EnhancedMmseQuizActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        
        // Add patient ID for AI personalization
        SharedPreferences prefs = context.getSharedPreferences("alzheimers_caregiver", Context.MODE_PRIVATE);
        String patientId = prefs.getString("patient_id", null);
        if (patientId != null) {
            openIntent.putExtra("patient_id", patientId);
        }
        PendingIntent contentIntent = PendingIntent.getActivity(
                context,
                2001,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationUtils.showReminderNotificationWithIntent(
                context,
                "MMSE Reminder",
                "It’s time for the monthly MMSE test.",
                contentIntent
        );

        // Schedule next month's reminder upon firing
        MmseReminderScheduler.scheduleNextMonth(context);
    }
}


