package com.mihir.alzheimerscaregiver.mmse;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.mihir.alzheimerscaregiver.EnhancedMmseQuizActivity;
import com.mihir.alzheimerscaregiver.notifications.NotificationUtils;

import java.util.Calendar;
import java.util.List;

public class MmseScheduleManager {
    private static final String TAG = "MmseScheduleManager";
    private static final String MMSE_SCHEDULE_COLLECTION = "mmse_schedule";
    private static final int REQUEST_CODE_BASE = 3000;

    public static void scheduleAll(Context context, String patientId) {
        Log.d(TAG, "scheduleAll: Checking for pending MMSE schedules for patientId=" + patientId);
        FirebaseFirestore.getInstance()
                .collection("patients").document(patientId)
                .collection(MMSE_SCHEDULE_COLLECTION)
                .whereEqualTo("status", "pending")
                .get()
                .addOnSuccessListener(query -> {
                    if (query != null && !query.isEmpty()) {
                        for (DocumentSnapshot doc : query.getDocuments()) {
                            Timestamp ts = doc.getTimestamp("datetime");
                            String scheduleId = doc.getId();
                            Log.d(TAG, "Found schedule: id=" + scheduleId + ", datetime=" + (ts != null ? ts.toDate() : "null"));
                            if (ts != null) {
                                scheduleAlarm(context, ts.toDate().getTime(), scheduleId);
                            }
                        }
                    } else {
                        Log.d(TAG, "No pending MMSE schedules found for patientId=" + patientId);
                    }
                })
                .addOnFailureListener(e -> Log.e(TAG, "Failed to fetch MMSE schedules", e));
    }

    public static void scheduleAlarm(Context context, long triggerAtMillis, String scheduleId) {
    Log.d(TAG, "scheduleAlarm: Scheduling alarm for scheduleId=" + scheduleId + ", triggerAtMillis=" + triggerAtMillis + " (" + new java.util.Date(triggerAtMillis) + ")");
    AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
    if (alarmManager == null) {
        Log.e(TAG, "scheduleAlarm: AlarmManager is null!");
        return;
    }
    Intent intent = new Intent(context, MmseScheduleReceiver.class);
    intent.putExtra("scheduleId", scheduleId);
    PendingIntent pendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE_BASE + scheduleId.hashCode(),
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
    );
    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
    Log.d(TAG, "scheduleAlarm: Alarm set for scheduleId=" + scheduleId);
    }

    public static class MmseScheduleReceiver extends BroadcastReceiver {
        @Override
    public void onReceive(Context context, Intent intent) {
        String scheduleId = intent.getStringExtra("scheduleId");
        Log.d(TAG, "MmseScheduleReceiver.onReceive: Received alarm for scheduleId=" + scheduleId);
        // Show notification to take MMSE test - use Enhanced MMSE with AI personalization
        Intent openIntent = new Intent(context, EnhancedMmseQuizActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        openIntent.putExtra("scheduleId", scheduleId);
        
        // Add patient ID from shared preferences for AI personalization
        SharedPreferences prefs = context.getSharedPreferences("alzheimers_caregiver", Context.MODE_PRIVATE);
        String patientId = prefs.getString("patient_id", null);
        if (patientId != null) {
            openIntent.putExtra("patient_id", patientId);
        }
        PendingIntent contentIntent = PendingIntent.getActivity(
            context,
            REQUEST_CODE_BASE + (scheduleId != null ? scheduleId.hashCode() : 0),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        NotificationUtils.showReminderNotificationWithIntent(
            context,
            "MMSE Test Scheduled",
            "It's time to take your MMSE test.",
            contentIntent
        );
        Log.d(TAG, "MmseScheduleReceiver.onReceive: Notification sent for scheduleId=" + scheduleId);
    }
    }

    public static void markCompleted(String patientId, String scheduleId) {
        FirebaseFirestore.getInstance()
                .collection("patients").document(patientId)
                .collection(MMSE_SCHEDULE_COLLECTION).document(scheduleId)
                .update("status", "completed");
    }
}
