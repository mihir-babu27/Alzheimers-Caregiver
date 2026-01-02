package com.mihir.alzheimerscaregiver.geofence;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.util.Log;

import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingEvent;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.mihir.alzheimerscaregiver.location.LocationUploader;

import java.util.List;

/**
 * GeofenceTransitionReceiver - Handles geofence transition events
 * 
 * Receives geofence transition broadcasts from Android system
 * and forwards them to PatientGeofenceClient for processing
 */
public class GeofenceTransitionReceiver extends BroadcastReceiver {

    private static final String TAG = "GeofenceTransitionReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.i(TAG, "🔔 GeofenceTransitionReceiver.onReceive() called - Android system detected geofence transition");
        
        GeofencingEvent geofencingEvent = GeofencingEvent.fromIntent(intent);
        
        if (geofencingEvent == null) {
            Log.e(TAG, "GeofencingEvent is null");
            return;
        }
        
        if (geofencingEvent.hasError()) {
            Log.e(TAG, "Geofencing error: " + geofencingEvent.getErrorCode());
            return;
        }

        // Get the transition type
        int geofenceTransition = geofencingEvent.getGeofenceTransition();

        // Test that the reported transition was of interest
        if (geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER ||
            geofenceTransition == Geofence.GEOFENCE_TRANSITION_EXIT ||
            geofenceTransition == Geofence.GEOFENCE_TRANSITION_DWELL) {

            // Get the geofences that were triggered
            List<Geofence> triggeringGeofences = geofencingEvent.getTriggeringGeofences();
            
            // Get the location that triggered the transition
            Location triggeringLocation = geofencingEvent.getTriggeringLocation();
            
            if (triggeringGeofences != null && triggeringLocation != null) {
                Log.i(TAG, "📍 Processing " + triggeringGeofences.size() + " triggered geofence(s)");
                Log.i(TAG, "📍 Trigger location: " + triggeringLocation.getLatitude() + ", " + triggeringLocation.getLongitude());
                
                // Process each triggered geofence
                for (Geofence geofence : triggeringGeofences) {
                    Log.i(TAG, "🚨 Geofence triggered: " + geofence.getRequestId() + " - " + getTransitionString(geofenceTransition));
                    handleGeofenceTransition(
                            context,
                            geofence.getRequestId(),
                            geofenceTransition,
                            triggeringLocation.getLatitude(),
                            triggeringLocation.getLongitude()
                    );
                }
            } else {
                Log.w(TAG, "⚠️ Geofence event missing geofences or location data");
            }
        } else {
            Log.e(TAG, "Invalid geofence transition type: " + geofenceTransition);
        }
    }

    /**
     * Handle individual geofence transition
     */
    private void handleGeofenceTransition(Context context, String geofenceId, 
                                        int transitionType, double latitude, double longitude) {
        
        Log.d(TAG, "Geofence transition: " + geofenceId + " - " + getTransitionString(transitionType));
        
        // Get patient ID from preferences or auth
        String patientId = getPatientId(context);
        if (patientId == null) {
            Log.e(TAG, "Patient ID not found, cannot process geofence transition");
            return;
        }
        
        // Send alert to Firebase directly (this is the main notification path)
        sendGeofenceAlert(patientId, geofenceId, transitionType, latitude, longitude);
        
        // Load geofence details and send FCM notification directly
        com.google.firebase.database.FirebaseDatabase.getInstance()
            .getReference("patients")
            .child(patientId)
            .child("geofences")
            .child(geofenceId)
            .addListenerForSingleValueEvent(new com.google.firebase.database.ValueEventListener() {
                @Override
                public void onDataChange(com.google.firebase.database.DataSnapshot snapshot) {
                    if (snapshot.exists()) {
                        try {
                            // Parse geofence data
                            java.util.Map<String, Object> data = (java.util.Map<String, Object>) snapshot.getValue();
                            String geofenceName = data != null ? (String) data.get("label") : "Unknown Zone";
                            String zoneType = data != null ? (String) data.get("type") : "safe_zone";
                            
                            Log.d(TAG, "✅ Loaded geofence: " + geofenceName);
                            
                            // Create enhanced alert in Firebase
                            String alertId = geofenceId + "_" + transitionType + "_" + System.currentTimeMillis();
                            String severity = determineSeverity(transitionType, zoneType);
                            
                            java.util.Map<String, Object> alertData = new java.util.HashMap<>();
                            alertData.put("id", alertId);
                            alertData.put("patientId", patientId);
                            alertData.put("geofenceId", geofenceId);
                            alertData.put("geofenceName", geofenceName);
                            alertData.put("transitionType", getTransitionString(transitionType));
                            alertData.put("severity", severity);
                            alertData.put("timestamp", System.currentTimeMillis());
                            alertData.put("patientLocation", new java.util.HashMap<String, Object>() {{
                                put("lat", latitude);
                                put("lng", longitude);
                            }});
                            alertData.put("processed", false);
                            alertData.put("acknowledged", false);
                            
                            // Save alert to Firebase
                            com.google.firebase.database.FirebaseDatabase.getInstance()
                                .getReference("patients")
                                .child(patientId)
                                .child("alerts")
                                .child(alertId)
                                .setValue(alertData)
                                .addOnSuccessListener(aVoid -> {
                                    Log.i(TAG, "✅ Enhanced alert saved to Firebase: " + geofenceName);
                                    
                                    // Send FCM notification to caretakers
                                    sendFCMNotificationToCaretakers(context, patientId, geofenceName, 
                                                                   getTransitionString(transitionType), 
                                                                   severity, alertId);
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "Failed to save enhanced alert", e);
                                });
                            
                        } catch (Exception e) {
                            Log.e(TAG, "Error processing geofence data", e);
                        }
                    } else {
                        Log.w(TAG, "⚠️ Geofence definition not found in Firebase: " + geofenceId);
                    }
                }
                
                @Override
                public void onCancelled(com.google.firebase.database.DatabaseError error) {
                    Log.e(TAG, "Failed to load geofence definition", error.toException());
                }
            });
    }
    
    /**
     * Determine alert severity based on transition type and zone type
     */
    private String determineSeverity(int transitionType, String zoneType) {
        if ("safe_zone".equals(zoneType) && transitionType == Geofence.GEOFENCE_TRANSITION_EXIT) {
            return "high"; // Exiting safe zone is high priority
        }
        return "medium";
    }
    
    /**
     * Send FCM notification to all caretakers
     */
    private void sendFCMNotificationToCaretakers(Context context, String patientId, 
                                                String geofenceName, String transitionType,
                                                String severity, String alertId) {
        Log.i(TAG, "📤 Sending FCM notifications to caretakers...");
        
        com.mihir.alzheimerscaregiver.utils.FCMNotificationSender fcmSender = 
            new com.mihir.alzheimerscaregiver.utils.FCMNotificationSender(context);
        
        // Get patient name first
        com.google.firebase.database.FirebaseDatabase.getInstance()
            .getReference("patients")
            .child(patientId)
            .child("name")
            .addListenerForSingleValueEvent(new com.google.firebase.database.ValueEventListener() {
                @Override
                public void onDataChange(com.google.firebase.database.DataSnapshot snapshot) {
                    String patientName = snapshot.getValue(String.class);
                    if (patientName == null) {
                        patientName = "Patient";
                    }
                    
                    Log.i(TAG, "📤 Sending FCM for patient: " + patientName);
                    fcmSender.sendGeofenceAlert(patientId, patientName, geofenceName, 
                                               transitionType, severity, alertId);
                }
                
                @Override
                public void onCancelled(com.google.firebase.database.DatabaseError error) {
                    Log.w(TAG, "Failed to get patient name, using default");
                    fcmSender.sendGeofenceAlert(patientId, "Patient", geofenceName, 
                                               transitionType, severity, alertId);
                }
            });
    }
    
    /**
     * Send geofence alert to Firebase for caretaker notification
     */
    private void sendGeofenceAlert(String patientId, String geofenceId, 
                                  int transitionType, double latitude, double longitude) {
        
        String alertType = "GEOFENCE_" + getTransitionString(transitionType);
        String alertDetails = String.format(
                "Patient %s geofence %s at location (%.6f, %.6f)",
                getTransitionString(transitionType).toLowerCase(),
                geofenceId,
                latitude,
                longitude
        );
        
        Log.d(TAG, "Sending geofence alert: " + alertType);
        
        LocationUploader uploader = new LocationUploader();
        uploader.addAlert(patientId, alertType, alertDetails, new LocationUploader.UploadCallback() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "Geofence alert sent successfully");
            }
            
            @Override
            public void onError(String error) {
                Log.e(TAG, "Failed to send geofence alert: " + error);
            }
        });
    }
    
    /**
     * Get patient ID from SharedPreferences or Firebase Auth
     */
    private String getPatientId(Context context) {
        try {
            // Try to get from Firebase Auth first
            FirebaseAuth auth = FirebaseAuth.getInstance();
            FirebaseUser user = auth.getCurrentUser();
            
            if (user != null) {
                return user.getUid();
            }
            
            // Fallback to SharedPreferences
            SharedPreferences prefs = context.getSharedPreferences(
                    "AlzheimersCaregiver", Context.MODE_PRIVATE);
            return prefs.getString("patient_id", null);
            
        } catch (Exception e) {
            Log.e(TAG, "Error getting patient ID", e);
            return null;
        }
    }
    
    /**
     * Get transition type as string for logging
     */
    private String getTransitionString(int transitionType) {
        switch (transitionType) {
            case Geofence.GEOFENCE_TRANSITION_ENTER:
                return "ENTER";
            case Geofence.GEOFENCE_TRANSITION_EXIT:
                return "EXIT";
            case Geofence.GEOFENCE_TRANSITION_DWELL:
                return "DWELL";
            default:
                return "UNKNOWN";
        }
    }
}