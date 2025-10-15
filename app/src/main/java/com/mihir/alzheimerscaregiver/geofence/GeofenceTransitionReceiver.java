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
                // Process each triggered geofence
                for (Geofence geofence : triggeringGeofences) {
                    handleGeofenceTransition(
                            context,
                            geofence.getRequestId(),
                            geofenceTransition,
                            triggeringLocation.getLatitude(),
                            triggeringLocation.getLongitude()
                    );
                }
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
        
        // Send alert to Firebase for caretaker notification
        sendGeofenceAlert(patientId, geofenceId, transitionType, latitude, longitude);
        
        // Also handle locally on device if needed
        PatientGeofenceClient geofenceClient = new PatientGeofenceClient(context, patientId);
        geofenceClient.handleGeofenceTransition(geofenceId, transitionType, latitude, longitude);
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