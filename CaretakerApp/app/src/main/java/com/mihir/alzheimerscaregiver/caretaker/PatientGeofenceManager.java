package com.mihir.alzheimerscaregiver.caretaker;

import android.content.Context;
import android.util.Log;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * PatientGeofenceManager - Manages geofence creation and monitoring for patients
 * 
 * Features:
 * - Create/update/delete geofences for patients
 * - Store geofence definitions in Firebase
 * - Monitor geofence alerts and violations
 * - Send notifications for geofence events
 * 
 * Firebase Structure:
 * /geofences/{patientId}/{geofenceId}
 * /alerts/{patientId}/{alertId}
 * /geofenceSettings/{patientId}
 */
public class PatientGeofenceManager {

    private static final String TAG = "PatientGeofenceManager";
    
    // Firebase references
    private DatabaseReference databaseReference;
    private DatabaseReference geofencesRef;
    private DatabaseReference alertsRef;
    private DatabaseReference settingsRef;
    
    // Listeners
    private Map<String, ValueEventListener> alertListeners;
    
    // Context
    private Context context;
    
    public PatientGeofenceManager(Context context) {
        this.context = context;
        this.databaseReference = FirebaseDatabase.getInstance().getReference();
        this.alertListeners = new HashMap<>();
        
        Log.d(TAG, "PatientGeofenceManager initialized");
    }
    
    /**
     * Create a new geofence for a patient
     */
    public void createGeofence(String patientId, GeofenceDefinition geofence, GeofenceCallback callback) {
        if (patientId == null || geofence == null) {
            if (callback != null) {
                callback.onError("Invalid parameters");
            }
            return;
        }
        
        geofencesRef = databaseReference.child("geofences").child(patientId);
        
        // Generate unique ID if not provided
        if (geofence.id == null || geofence.id.isEmpty()) {
            geofence.id = UUID.randomUUID().toString();
        }
        
        // Create geofence data
        Map<String, Object> geofenceData = new HashMap<>();
        geofenceData.put("id", geofence.id);
        geofenceData.put("name", geofence.name);
        geofenceData.put("description", geofence.description);
        geofenceData.put("latitude", geofence.latitude);
        geofenceData.put("longitude", geofence.longitude);
        geofenceData.put("radius", geofence.radius);
        geofenceData.put("type", geofence.type.name());
        geofenceData.put("enabled", geofence.enabled);
        geofenceData.put("createdAt", System.currentTimeMillis());
        geofenceData.put("updatedAt", System.currentTimeMillis());
        
        // Save to Firebase
        geofencesRef.child(geofence.id).setValue(geofenceData)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Geofence created successfully: " + geofence.id);
                    if (callback != null) {
                        callback.onSuccess(geofence);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to create geofence: " + geofence.id, e);
                    if (callback != null) {
                        callback.onError("Failed to create geofence: " + e.getMessage());
                    }
                });
    }
    
    /**
     * Update an existing geofence
     */
    public void updateGeofence(String patientId, GeofenceDefinition geofence, GeofenceCallback callback) {
        if (patientId == null || geofence == null || geofence.id == null) {
            if (callback != null) {
                callback.onError("Invalid parameters");
            }
            return;
        }
        
        geofencesRef = databaseReference.child("geofences").child(patientId);
        
        // Update geofence data
        Map<String, Object> updates = new HashMap<>();
        updates.put("name", geofence.name);
        updates.put("description", geofence.description);
        updates.put("latitude", geofence.latitude);
        updates.put("longitude", geofence.longitude);
        updates.put("radius", geofence.radius);
        updates.put("type", geofence.type.name());
        updates.put("enabled", geofence.enabled);
        updates.put("updatedAt", System.currentTimeMillis());
        
        // Save updates
        geofencesRef.child(geofence.id).updateChildren(updates)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Geofence updated successfully: " + geofence.id);
                    if (callback != null) {
                        callback.onSuccess(geofence);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to update geofence: " + geofence.id, e);
                    if (callback != null) {
                        callback.onError("Failed to update geofence: " + e.getMessage());
                    }
                });
    }
    
    /**
     * Delete a geofence
     */
    public void deleteGeofence(String patientId, String geofenceId, GeofenceCallback callback) {
        if (patientId == null || geofenceId == null) {
            if (callback != null) {
                callback.onError("Invalid parameters");
            }
            return;
        }
        
        geofencesRef = databaseReference.child("geofences").child(patientId);
        
        geofencesRef.child(geofenceId).removeValue()
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Geofence deleted successfully: " + geofenceId);
                    if (callback != null) {
                        callback.onSuccess(null);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to delete geofence: " + geofenceId, e);
                    if (callback != null) {
                        callback.onError("Failed to delete geofence: " + e.getMessage());
                    }
                });
    }
    
    /**
     * Get all geofences for a patient
     */
    public void getGeofences(String patientId, GeofenceListCallback callback) {
        if (patientId == null || callback == null) {
            return;
        }
        
        geofencesRef = databaseReference.child("geofences").child(patientId);
        
        geofencesRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                List<GeofenceDefinition> geofences = new ArrayList<>();
                
                for (DataSnapshot geofenceSnapshot : dataSnapshot.getChildren()) {
                    try {
                        GeofenceDefinition geofence = parseGeofenceFromSnapshot(geofenceSnapshot);
                        if (geofence != null) {
                            geofences.add(geofence);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing geofence", e);
                    }
                }
                
                callback.onSuccess(geofences);
                Log.d(TAG, "Loaded " + geofences.size() + " geofences for patient: " + patientId);
            }
            
            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.e(TAG, "Failed to load geofences", databaseError.toException());
                callback.onError("Failed to load geofences: " + databaseError.getMessage());
            }
        });
    }
    
    /**
     * Start monitoring alerts for a patient
     */
    public void startAlertMonitoring(String patientId, AlertCallback callback) {
        if (patientId == null || callback == null) {
            return;
        }
        
        // Stop existing listener if any
        stopAlertMonitoring(patientId);
        
        alertsRef = databaseReference.child("alerts").child(patientId);
        
        ValueEventListener alertListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                for (DataSnapshot alertSnapshot : dataSnapshot.getChildren()) {
                    try {
                        GeofenceAlert alert = parseAlertFromSnapshot(alertSnapshot);
                        if (alert != null && !alert.processed) {
                            callback.onAlert(alert);
                            
                            // Mark alert as processed
                            alertSnapshot.getRef().child("processed").setValue(true);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing alert", e);
                    }
                }
            }
            
            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.e(TAG, "Alert monitoring cancelled", databaseError.toException());
                callback.onError("Alert monitoring failed: " + databaseError.getMessage());
            }
        };
        
        alertsRef.addValueEventListener(alertListener);
        alertListeners.put(patientId, alertListener);
        
        Log.d(TAG, "Started alert monitoring for patient: " + patientId);
    }
    
    /**
     * Stop monitoring alerts for a patient
     */
    public void stopAlertMonitoring(String patientId) {
        ValueEventListener listener = alertListeners.remove(patientId);
        if (listener != null && alertsRef != null) {
            alertsRef.removeEventListener(listener);
            Log.d(TAG, "Stopped alert monitoring for patient: " + patientId);
        }
    }
    
    /**
     * Get geofence settings for a patient
     */
    public void getGeofenceSettings(String patientId, GeofenceSettingsCallback callback) {
        if (patientId == null || callback == null) {
            return;
        }
        
        settingsRef = databaseReference.child("geofenceSettings").child(patientId);
        
        settingsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                GeofenceSettings settings = new GeofenceSettings();
                
                if (dataSnapshot.exists()) {
                    Boolean enabledValue = dataSnapshot.child("enabled").getValue(Boolean.class);
                    settings.enabled = enabledValue != null ? enabledValue : true;
                    
                    Boolean alertsEnabledValue = dataSnapshot.child("alertsEnabled").getValue(Boolean.class);
                    settings.alertsEnabled = alertsEnabledValue != null ? alertsEnabledValue : true;
                    
                    Integer checkIntervalValue = dataSnapshot.child("checkIntervalMinutes").getValue(Integer.class);
                    settings.checkIntervalMinutes = checkIntervalValue != null ? checkIntervalValue : 5;
                }
                
                callback.onSuccess(settings);
            }
            
            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.e(TAG, "Failed to load geofence settings", databaseError.toException());
                callback.onError("Failed to load settings: " + databaseError.getMessage());
            }
        });
    }
    
    /**
     * Update geofence settings for a patient
     */
    public void updateGeofenceSettings(String patientId, GeofenceSettings settings, GeofenceSettingsCallback callback) {
        if (patientId == null || settings == null) {
            if (callback != null) {
                callback.onError("Invalid parameters");
            }
            return;
        }
        
        settingsRef = databaseReference.child("geofenceSettings").child(patientId);
        
        Map<String, Object> settingsData = new HashMap<>();
        settingsData.put("enabled", settings.enabled);
        settingsData.put("alertsEnabled", settings.alertsEnabled);
        settingsData.put("checkIntervalMinutes", settings.checkIntervalMinutes);
        settingsData.put("updatedAt", System.currentTimeMillis());
        
        settingsRef.setValue(settingsData)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Geofence settings updated for patient: " + patientId);
                    if (callback != null) {
                        callback.onSuccess(settings);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to update geofence settings", e);
                    if (callback != null) {
                        callback.onError("Failed to update settings: " + e.getMessage());
                    }
                });
    }
    
    /**
     * Parse geofence definition from Firebase snapshot
     */
    private GeofenceDefinition parseGeofenceFromSnapshot(DataSnapshot snapshot) {
        try {
            GeofenceDefinition geofence = new GeofenceDefinition();
            geofence.id = snapshot.child("id").getValue(String.class);
            geofence.name = snapshot.child("name").getValue(String.class);
            geofence.description = snapshot.child("description").getValue(String.class);
            geofence.latitude = snapshot.child("latitude").getValue(Double.class);
            geofence.longitude = snapshot.child("longitude").getValue(Double.class);
            geofence.radius = snapshot.child("radius").getValue(Float.class);
            Boolean enabledValue = snapshot.child("enabled").getValue(Boolean.class);
            geofence.enabled = enabledValue != null ? enabledValue : true;
            
            String typeString = snapshot.child("type").getValue(String.class);
            geofence.type = typeString != null ? 
                    GeofenceType.valueOf(typeString) : GeofenceType.ENTER_EXIT;
            
            return geofence;
        } catch (Exception e) {
            Log.e(TAG, "Error parsing geofence from snapshot", e);
            return null;
        }
    }
    
    /**
     * Parse alert from Firebase snapshot
     */
    private GeofenceAlert parseAlertFromSnapshot(DataSnapshot snapshot) {
        try {
            GeofenceAlert alert = new GeofenceAlert();
            alert.id = snapshot.child("id").getValue(String.class);
            alert.geofenceId = snapshot.child("geofenceId").getValue(String.class);
            alert.geofenceName = snapshot.child("geofenceName").getValue(String.class);
            alert.timestamp = snapshot.child("timestamp").getValue(Long.class);
            alert.latitude = snapshot.child("latitude").getValue(Double.class);
            alert.longitude = snapshot.child("longitude").getValue(Double.class);
            Boolean processedValue = snapshot.child("processed").getValue(Boolean.class);
            alert.processed = processedValue != null ? processedValue : false;
            
            String transitionString = snapshot.child("transitionType").getValue(String.class);
            alert.transitionType = transitionString != null ? 
                    GeofenceTransition.valueOf(transitionString) : GeofenceTransition.ENTER;
            
            return alert;
        } catch (Exception e) {
            Log.e(TAG, "Error parsing alert from snapshot", e);
            return null;
        }
    }
    
    /**
     * Clean up resources
     */
    public void cleanup() {
        for (Map.Entry<String, ValueEventListener> entry : alertListeners.entrySet()) {
            String patientId = entry.getKey();
            stopAlertMonitoring(patientId);
        }
        alertListeners.clear();
        Log.d(TAG, "PatientGeofenceManager cleaned up");
    }
    
    // Data classes
    public static class GeofenceDefinition {
        public String id;
        public String name;
        public String description;
        public double latitude;
        public double longitude;
        public float radius;
        public GeofenceType type = GeofenceType.ENTER_EXIT;
        public boolean enabled = true;
    }
    
    public static class GeofenceAlert {
        public String id;
        public String geofenceId;
        public String geofenceName;
        public long timestamp;
        public double latitude;
        public double longitude;
        public GeofenceTransition transitionType;
        public boolean processed = false;
    }
    
    public static class GeofenceSettings {
        public boolean enabled = true;
        public boolean alertsEnabled = true;
        public int checkIntervalMinutes = 5;
    }
    
    public enum GeofenceType {
        ENTER_ONLY,
        EXIT_ONLY,
        ENTER_EXIT
    }
    
    public enum GeofenceTransition {
        ENTER,
        EXIT,
        DWELL
    }
    
    // Callback interfaces
    public interface GeofenceCallback {
        void onSuccess(GeofenceDefinition geofence);
        void onError(String error);
    }
    
    public interface GeofenceListCallback {
        void onSuccess(List<GeofenceDefinition> geofences);
        void onError(String error);
    }
    
    public interface AlertCallback {
        void onAlert(GeofenceAlert alert);
        void onError(String error);
    }
    
    public interface GeofenceSettingsCallback {
        void onSuccess(GeofenceSettings settings);
        void onError(String error);
    }
}