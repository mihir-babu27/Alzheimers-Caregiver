package com.mihir.alzheimerscaregiver.caretaker.services;

import android.util.Log;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.mihir.alzheimerscaregiver.caretaker.managers.FCMTokenManager;
import com.mihir.alzheimerscaregiver.caretaker.utils.NotificationHelper;

import java.util.Map;

/**
 * Firebase Cloud Messaging Service for CaretakerApp
 * Handles incoming push notifications for geofence alerts
 */
public class CaretakerMessagingService extends FirebaseMessagingService {
    
    private static final String TAG = "CaretakerMessagingService";
    private NotificationHelper notificationHelper;
    private FCMTokenManager fcmTokenManager;
    
    @Override
    public void onCreate() {
        super.onCreate();
        notificationHelper = new NotificationHelper(this);
        fcmTokenManager = new FCMTokenManager(this);
        Log.d(TAG, "CaretakerMessagingService created");
    }
    
    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);
        
        Log.d(TAG, "FCM message received from: " + remoteMessage.getFrom());
        
        // Check if message contains data payload
        if (remoteMessage.getData().size() > 0) {
            Log.d(TAG, "Message data payload: " + remoteMessage.getData());
            handleDataMessage(remoteMessage.getData());
        }
        
        // Check if message contains a notification payload
        if (remoteMessage.getNotification() != null) {
            Log.d(TAG, "Message Notification Body: " + remoteMessage.getNotification().getBody());
            
            // Show general notification using NotificationHelper
            if (notificationHelper != null) {
                notificationHelper.showGeneralNotification(
                    remoteMessage.getNotification().getTitle(),
                    remoteMessage.getNotification().getBody(),
                    "view_notifications"
                );
            }
        }
    }
    
    @Override
    public void onNewToken(String token) {
        super.onNewToken(token);
        Log.d(TAG, "FCM token refreshed: " + token);
        
        // Update token using FCM token manager
        fcmTokenManager.updateToken(token);
    }
    
    /**
     * Handle FCM data messages containing geofence alert and missed medication information
     */
    private void handleDataMessage(Map<String, String> data) {
        try {
            String alertType = data.get("alertType");
            
            if ("geofence_alert".equals(alertType)) {
                String patientName = data.get("patientName");
                String patientId = data.get("patientId");
                String geofenceName = data.get("geofenceName");
                String transitionType = data.get("transitionType");
                String severity = data.get("severity");
                String alertId = data.get("alertId");
                
                // Use NotificationHelper to show enhanced notification
                notificationHelper.showGeofenceAlert(
                    patientName != null ? patientName : "Patient",
                    geofenceName != null ? geofenceName : "Safe Zone",
                    transitionType != null ? transitionType : "TRANSITION",
                    severity != null ? severity : "medium",
                    alertId != null ? alertId : "unknown",
                    patientId != null ? patientId : "unknown"
                );
                
                Log.d(TAG, "Geofence alert displayed: " + patientName + " - " + 
                      transitionType + " " + geofenceName);
                
            } else if ("missed_medication".equals(alertType)) {
                // 💊 Handle missed medication notifications from Patient app
                String patientName = data.get("patientName");
                String medicationName = data.get("medicationName");
                String scheduledTime = data.get("scheduledTime");
                String timestamp = data.get("timestamp");
                
                Log.d(TAG, "🚨 MISSED MEDICATION ALERT RECEIVED!");
                Log.d(TAG, "👤 Patient: " + patientName);
                Log.d(TAG, "💊 Medication: " + medicationName);
                Log.d(TAG, "⏰ Scheduled Time: " + scheduledTime);
                
                // Show missed medication notification
                if (notificationHelper != null) {
                    notificationHelper.showMissedMedicationAlert(
                        patientName != null ? patientName : "Patient",
                        medicationName != null ? medicationName : "Medication",
                        scheduledTime != null ? scheduledTime : "Unknown time"
                    );
                    
                    Log.d(TAG, "✅ Missed medication notification displayed to caretaker");
                } else {
                    Log.e(TAG, "❌ NotificationHelper is null - cannot display notification");
                }
                
            } else {
                Log.w(TAG, "Unknown alert type: " + alertType);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error handling data message", e);
        }
    }
    

}