package com.mihir.alzheimerscaregiver.caretaker.utils;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.mihir.alzheimerscaregiver.caretaker.R;
import com.mihir.alzheimerscaregiver.caretaker.GeofenceManagementActivity;

/**
 * Helper class for managing notifications in CaretakerApp
 */
public class NotificationHelper {
    
    public static final String GEOFENCE_CHANNEL_ID = "geofence_alerts";
    public static final String GENERAL_CHANNEL_ID = "general_notifications";
    
    private static final String GEOFENCE_CHANNEL_NAME = "Geofence Alerts";
    private static final String GENERAL_CHANNEL_NAME = "General Notifications";
    
    private Context context;
    private NotificationManagerCompat notificationManager;
    
    public NotificationHelper(Context context) {
        this.context = context;
        this.notificationManager = NotificationManagerCompat.from(context);
        createNotificationChannels();
    }
    
    /**
     * Create notification channels for different types of notifications
     */
    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Geofence alerts channel (High priority)
            NotificationChannel geofenceChannel = new NotificationChannel(
                GEOFENCE_CHANNEL_ID,
                GEOFENCE_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            );
            geofenceChannel.setDescription("Critical alerts for patient geofence transitions");
            geofenceChannel.enableLights(true);
            geofenceChannel.enableVibration(true);
            
            // General notifications channel (Default priority)
            NotificationChannel generalChannel = new NotificationChannel(
                GENERAL_CHANNEL_ID,
                GENERAL_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            );
            generalChannel.setDescription("General app notifications");
            
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            manager.createNotificationChannel(geofenceChannel);
            manager.createNotificationChannel(generalChannel);
        }
    }
    
    /**
     * Show geofence alert notification with enhanced styling
     */
    public void showGeofenceAlert(String patientName, String geofenceName, 
                                 String transitionType, String severity, 
                                 String alertId, String patientId) {
        
        String title = "Patient Location Alert";
        String message;
        
        if ("EXIT".equals(transitionType)) {
            message = patientName + " has left the " + geofenceName + " safe zone";
        } else if ("ENTER".equals(transitionType)) {
            message = patientName + " has entered the " + geofenceName + " safe zone";
        } else {
            message = patientName + " - " + transitionType + " " + geofenceName;
        }
        
        // Create intent to open GeofenceManagementActivity
        Intent intent = new Intent(context, GeofenceManagementActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra("alert_id", alertId);
        intent.putExtra("patient_id", patientId);
        intent.putExtra("action", "view_alert");
        
        PendingIntent pendingIntent = PendingIntent.getActivity(
            context,
            alertId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        // Determine notification style based on severity
        int priority = "high".equals(severity) ? 
            NotificationCompat.PRIORITY_HIGH : NotificationCompat.PRIORITY_DEFAULT;
        
        // Enhanced notification title based on severity
        String enhancedTitle;
        if ("high".equals(severity)) {
            enhancedTitle = "🚨 URGENT: Patient Safety Alert";
        } else if (message.contains("left")) {
            enhancedTitle = "📍 Patient Location Update";
        } else {
            enhancedTitle = "✅ Safe Zone Activity";
        }
        
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, GEOFENCE_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_location) // Custom location icon
                .setContentTitle(enhancedTitle)
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle()
                    .bigText(message)
                    .setBigContentTitle(enhancedTitle))
                .setPriority(priority)
                .setAutoCancel(true)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setContentIntent(pendingIntent)
                .setGroup("patient_alerts_" + patientId) // Group notifications by patient
                .setColor(context.getResources().getColor(
                    "high".equals(severity) ? R.color.notification_urgent : R.color.notification_safe, null))
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE) // Privacy for sensitive health data
                .setCategory(NotificationCompat.CATEGORY_ALARM); // High importance category
        
        // Add action buttons for high severity alerts
        if ("high".equals(severity)) {
            // "View Location" action
            Intent viewLocationIntent = new Intent(context, GeofenceManagementActivity.class);
            viewLocationIntent.putExtra("action", "view_location");
            viewLocationIntent.putExtra("patient_id", patientId);
            viewLocationIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            
            PendingIntent viewLocationPendingIntent = PendingIntent.getActivity(
                context,
                (alertId + "_location").hashCode(),
                viewLocationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            
            builder.addAction(
                R.drawable.ic_location,
                "View Location", 
                viewLocationPendingIntent
            );
            
            // "Acknowledge" action
            Intent acknowledgeIntent = new Intent(context, GeofenceManagementActivity.class);
            acknowledgeIntent.putExtra("action", "acknowledge_alert");
            acknowledgeIntent.putExtra("alert_id", alertId);
            acknowledgeIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            
            PendingIntent acknowledgePendingIntent = PendingIntent.getActivity(
                context,
                (alertId + "_ack").hashCode(),
                acknowledgeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            
            builder.addAction(
                R.drawable.ic_close_white,
                "Acknowledge",
                acknowledgePendingIntent
            );
        }
        
        // Show notification with unique ID based on alert
        int notificationId = alertId.hashCode();
        notificationManager.notify(notificationId, builder.build());
    }
    
    /**
     * Show general notification
     */
    public void showGeneralNotification(String title, String message, String action) {
        Intent intent = new Intent(context, GeofenceManagementActivity.class);
        if (action != null) {
            intent.putExtra("action", action);
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        
        PendingIntent pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, GENERAL_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_warning)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);
        
        notificationManager.notify(1, builder.build());
    }
    
    /**
     * Cancel notification by ID
     */
    public void cancelNotification(int notificationId) {
        notificationManager.cancel(notificationId);
    }
    
    /**
     * Cancel all notifications
     */
    public void cancelAllNotifications() {
        notificationManager.cancelAll();
    }
    
    /**
     * 💊 Show missed medication alert notification from Patient app
     */
    public void showMissedMedicationAlert(String patientName, String medicationName, String scheduledTime) {
        createNotificationChannels();
        
        // Create intent to open main activity when notification is tapped
        Intent intent = new Intent(context, com.mihir.alzheimerscaregiver.caretaker.MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        
        PendingIntent pendingIntent = PendingIntent.getActivity(
            context, 
            0, 
            intent, 
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        // Build the notification
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, GEOFENCE_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert) // Use generic alert icon
                .setContentTitle("💊 Medication Reminder Missed")
                .setContentText(patientName + " has not taken " + medicationName + " scheduled at " + scheduledTime)
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(patientName + " has not taken their " + medicationName + 
                                " medication scheduled at " + scheduledTime + 
                                ". Please check on them to ensure they take their medication."))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setColor(context.getResources().getColor(android.R.color.holo_orange_dark, null)) // Orange for medication alerts
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC);
        
        // Add action buttons for quick response
        Intent callIntent = new Intent(Intent.ACTION_DIAL);
        PendingIntent callPendingIntent = PendingIntent.getActivity(
            context, 
            1, 
            callIntent, 
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        builder.addAction(android.R.drawable.ic_menu_call, "Call Patient", callPendingIntent);
        
        // Generate unique notification ID based on medication and time
        int notificationId = (patientName + medicationName + scheduledTime).hashCode();
        
        // Show the notification
        notificationManager.notify(notificationId, builder.build());
        
        // Log for debugging
        android.util.Log.d("NotificationHelper", "📱 Missed medication notification displayed:");
        android.util.Log.d("NotificationHelper", "👤 Patient: " + patientName);
        android.util.Log.d("NotificationHelper", "💊 Medication: " + medicationName);
        android.util.Log.d("NotificationHelper", "⏰ Time: " + scheduledTime);
        android.util.Log.d("NotificationHelper", "🔔 Notification ID: " + notificationId);
    }
}