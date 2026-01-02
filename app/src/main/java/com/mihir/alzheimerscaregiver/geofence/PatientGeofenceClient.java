package com.mihir.alzheimerscaregiver.geofence;

import android.Manifest;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingClient;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationServices;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import com.mihir.alzheimerscaregiver.entities.GeofenceDefinition;
import com.mihir.alzheimerscaregiver.utils.FCMNotificationSender;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * PatientGeofenceClient - Manages geofences on the patient device
 * 
 * Features:
 * - Reads geofence definitions from Firebase
 * - Registers geofences with Android GeofencingClient
 * - Handles geofence transition events
 * - Sends alerts to Firebase for caretaker notifications
 * - Manages geofence lifecycle and updates
 */
public class PatientGeofenceClient {

    private static final String TAG = "PatientGeofenceClient";
    private static final long GEOFENCE_EXPIRATION_TIME = 24 * 60 * 60 * 1000; // 24 hours
    
    // Android Services
    private Context context;
    private GeofencingClient geofencingClient;
    private PendingIntent geofencePendingIntent;
    
    // Firebase
    private DatabaseReference databaseReference;
    private ValueEventListener geofenceListener;
    
    // Data
    private String patientId;
    private List<PatientGeofence> activeGeofences;
    private Map<String, GeofenceDefinition> geofenceDefinitions;
    
    // FCM Notifications
    private FCMNotificationSender fcmNotificationSender;
    
    public PatientGeofenceClient(Context context, String patientId) {
        this.context = context;
        this.patientId = patientId;
        this.geofencingClient = LocationServices.getGeofencingClient(context);
        this.databaseReference = FirebaseDatabase.getInstance().getReference();
        this.activeGeofences = new ArrayList<>();
        this.geofenceDefinitions = new HashMap<>();
        this.fcmNotificationSender = new FCMNotificationSender(context);
        
        Log.d(TAG, "PatientGeofenceClient initialized for patient: " + patientId);
    }
    
    /**
     * Start monitoring geofences for the patient
     */
    public void startGeofenceMonitoring() {
        if (!hasLocationPermissions()) {
            Log.w(TAG, "Location permissions not granted, cannot start geofence monitoring");
            return;
        }
        
        // Listen for geofence updates from Firebase
        startGeofenceUpdatesListener();
        
        // Load and register initial geofences
        loadAndRegisterGeofences();
        
        // Add test geofence for debugging if no geofences exist
        addTestGeofenceIfNeeded();
        
        Log.d(TAG, "Geofence monitoring started");
    }
    
    /**
     * Stop monitoring geofences
     */
    public void stopGeofenceMonitoring() {
        // Remove geofence listener
        if (geofenceListener != null && databaseReference != null) {
            databaseReference.child("geofences").child(patientId)
                    .removeEventListener(geofenceListener);
        }
        
        // Remove all geofences
        removeAllGeofences();
        
        Log.d(TAG, "Geofence monitoring stopped");
    }
    
    /**
     * Start listening for geofence updates from Firebase
     */
    private void startGeofenceUpdatesListener() {
        DatabaseReference geofencesRef = databaseReference.child("patients").child(patientId).child("geofences");
        
        geofenceListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                Log.i(TAG, "🌍 Loading " + dataSnapshot.getChildrenCount() + " geofences from Firebase for patient: " + patientId);
                
                List<PatientGeofence> updatedGeofences = new ArrayList<>();
                geofenceDefinitions.clear();
                
                // Parse geofences using GeofenceDefinition
                for (DataSnapshot geofenceSnapshot : dataSnapshot.getChildren()) {
                    try {
                        Map<String, Object> data = (Map<String, Object>) geofenceSnapshot.getValue();
                        if (data != null) {
                            GeofenceDefinition geofenceDefinition = GeofenceDefinition.fromFirebase(data);
                            if (geofenceDefinition.isValid() && geofenceDefinition.active) {
                                geofenceDefinitions.put(geofenceDefinition.id, geofenceDefinition);
                                Log.i(TAG, "🎯 Loaded geofence: " + geofenceDefinition.label + " at " + 
                                      geofenceDefinition.lat + "," + geofenceDefinition.lng + " (radius: " + 
                                      geofenceDefinition.radius + "m)");
                                
                                PatientGeofence patientGeofence = convertToPatientGeofence(geofenceDefinition);
                                updatedGeofences.add(patientGeofence);
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing geofence during update", e);
                    }
                }
                
                // Update active geofences
                updateActiveGeofences(updatedGeofences);
                
                Log.i(TAG, "✅ Successfully loaded and registered " + updatedGeofences.size() + " geofences for monitoring!");
            }
            
            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.e(TAG, "Failed to load geofences", databaseError.toException());
            }
        };
        
        geofencesRef.addValueEventListener(geofenceListener);
        Log.d(TAG, "Started listening for geofence updates");
    }
    
    /**
     * Load and register geofences from Firebase
     */
    private void loadAndRegisterGeofences() {
        DatabaseReference geofencesRef = databaseReference.child("patients").child(patientId).child("geofences");
        
        geofencesRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                List<PatientGeofence> geofences = new ArrayList<>();
                geofenceDefinitions.clear();
                
                for (DataSnapshot geofenceSnapshot : dataSnapshot.getChildren()) {
                    try {
                        Map<String, Object> data = (Map<String, Object>) geofenceSnapshot.getValue();
                        if (data != null) {
                            GeofenceDefinition geofenceDefinition = GeofenceDefinition.fromFirebase(data);
                            if (geofenceDefinition.isValid() && geofenceDefinition.active) {
                                geofenceDefinitions.put(geofenceDefinition.id, geofenceDefinition);
                                
                                PatientGeofence patientGeofence = convertToPatientGeofence(geofenceDefinition);
                                geofences.add(patientGeofence);
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing geofence", e);
                    }
                }
                
                Log.d(TAG, "Loaded " + geofences.size() + " geofences from Firebase");
                registerGeofences(geofences);
            }
            
            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.e(TAG, "Failed to load initial geofences", databaseError.toException());
            }
        });
    }
    
    /**
     * Convert GeofenceDefinition to PatientGeofence
     */
    private PatientGeofence convertToPatientGeofence(GeofenceDefinition definition) {
        PatientGeofence patientGeofence = new PatientGeofence();
        patientGeofence.id = definition.id;
        patientGeofence.name = definition.label;
        patientGeofence.description = definition.description;
        patientGeofence.latitude = definition.lat;
        patientGeofence.longitude = definition.lng;
        patientGeofence.radius = definition.radius;
        patientGeofence.type = GeofenceType.ENTER_EXIT; // Trigger on both ENTER and EXIT for complete monitoring
        patientGeofence.enabled = definition.active;
        return patientGeofence;
    }
    
    /**
     * Update active geofences when Firebase data changes
     */
    private void updateActiveGeofences(List<PatientGeofence> updatedGeofences) {
        // Remove geofences that are no longer active
        removeAllGeofences();
        
        // Register new geofences
        registerGeofences(updatedGeofences);
        
        // Update active geofences list
        activeGeofences.clear();
        activeGeofences.addAll(updatedGeofences);
    }
    
    /**
     * Register geofences with Android GeofencingClient
     */
    private void registerGeofences(List<PatientGeofence> geofences) {
        if (!hasLocationPermissions() || geofences.isEmpty()) {
            return;
        }
        
        List<Geofence> androidGeofences = new ArrayList<>();
        
        for (PatientGeofence patientGeofence : geofences) {
            int transitionTypes = getTransitionTypes(patientGeofence.type);
            
            Geofence geofence = new Geofence.Builder()
                    .setRequestId(patientGeofence.id)
                    .setCircularRegion(
                            patientGeofence.latitude,
                            patientGeofence.longitude,
                            patientGeofence.radius)
                    .setExpirationDuration(GEOFENCE_EXPIRATION_TIME)
                    .setTransitionTypes(transitionTypes)
                    .build();
            
            androidGeofences.add(geofence);
        }
        
        GeofencingRequest geofencingRequest = new GeofencingRequest.Builder()
                .setInitialTrigger(0) // Disable initial triggers to prevent false alarms on registration
                .addGeofences(androidGeofences)
                .build();
        
        try {
            geofencingClient.addGeofences(geofencingRequest, getGeofencePendingIntent())
                    .addOnSuccessListener(aVoid -> {
                        Log.i(TAG, "🎯 Geofences registered successfully: " + androidGeofences.size());
                        for (PatientGeofence geofence : geofences) {
                            Log.d(TAG, "  ✅ " + geofence.name + " at (" + 
                                  String.format("%.4f", geofence.latitude) + ", " + 
                                  String.format("%.4f", geofence.longitude) + ") " +
                                  "radius: " + geofence.radius + "m");
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "❌ Failed to register geofences", e);
                        Log.e(TAG, "Error details: " + e.getMessage());
                    });
        } catch (SecurityException e) {
            Log.e(TAG, "Location permission not granted", e);
        }
    }
    
    /**
     * Remove all registered geofences
     */
    private void removeAllGeofences() {
        try {
            geofencingClient.removeGeofences(getGeofencePendingIntent())
                    .addOnSuccessListener(aVoid -> {
                        Log.d(TAG, "All geofences removed successfully");
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to remove geofences", e);
                    });
        } catch (Exception e) {
            Log.e(TAG, "Error removing geofences", e);
        }
    }
    
    /**
     * Handle geofence transition events
     */
    public void handleGeofenceTransition(String geofenceId, int transitionType, 
                                       double latitude, double longitude) {
        
        Log.i(TAG, "🔔 handleGeofenceTransition called: " + geofenceId + " - " + getTransitionName(transitionType));
        Log.i(TAG, "   Location: (" + latitude + ", " + longitude + ")");
        
        // Find the geofence definition from our new structure
        GeofenceDefinition geofenceDefinition = geofenceDefinitions.get(geofenceId);
        PatientGeofence geofence = findGeofenceById(geofenceId);
        
        if (geofence == null || geofenceDefinition == null) {
            Log.w(TAG, "Geofence not found for transition: " + geofenceId);
            return;
        }
        
        // Generate unique alert ID with timestamp to prevent duplicates
        String alertId = geofenceId + "_" + transitionType + "_" + System.currentTimeMillis();
        long timestamp = System.currentTimeMillis();
        
        // Create enhanced alert data using GeofenceDefinition
        Map<String, Object> alertData = new HashMap<>();
        alertData.put("id", alertId);
        alertData.put("patientId", patientId);
        alertData.put("geofenceId", geofenceId);
        alertData.put("geofenceName", geofenceDefinition.label);
        alertData.put("geofenceDescription", geofenceDefinition.description);
        alertData.put("zoneType", geofenceDefinition.type);
        alertData.put("transitionType", getTransitionName(transitionType));
        alertData.put("severity", determineSeverity(transitionType, geofenceDefinition.type));
        alertData.put("timestamp", timestamp);
        alertData.put("patientLocation", new HashMap<String, Object>() {{
            put("lat", latitude);
            put("lng", longitude);
        }});
        alertData.put("geofenceLocation", new HashMap<String, Object>() {{
            put("lat", geofenceDefinition.lat);
            put("lng", geofenceDefinition.lng);
        }});
        alertData.put("processed", false);
        alertData.put("acknowledged", false);
        
        // Send alert to Firebase with structured ID
        DatabaseReference alertRef = databaseReference
                .child("patients")
                .child(patientId)
                .child("alerts")
                .child(alertId);
        
        alertRef.setValue(alertData)
                .addOnSuccessListener(aVoid -> {
                    Log.i(TAG, "✅ Enhanced geofence alert saved to Firebase: " + geofenceDefinition.label + 
                           " - " + getTransitionName(transitionType) + 
                           " (Severity: " + determineSeverity(transitionType, geofenceDefinition.type) + ")");
                    
                    // Send FCM notification to all caretakers
                    Log.i(TAG, "📤 Sending FCM notification to caretakers...");
                    sendFCMNotificationToCaretakers(geofenceDefinition, transitionType, alertId);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to send geofence alert", e);
                });
        
        // Show local notification
        showGeofenceNotification(geofence, transitionType);
    }
    
    /**
     * Determine alert severity based on transition type and zone type
     */
    private String determineSeverity(int transitionType, String zoneType) {
        if (GeofenceDefinition.TYPE_SAFE_ZONE.equals(zoneType)) {
            // Exiting a safe zone is high priority
            return (transitionType == Geofence.GEOFENCE_TRANSITION_EXIT) ? "high" : "medium";
        }
        return "medium";
    }
    
    /**
     * Send FCM notification to all caretakers associated with this patient
     */
    private void sendFCMNotificationToCaretakers(GeofenceDefinition geofenceDefinition, 
                                                int transitionType, String alertId) {
        try {
            // Get patient name from Firebase or use default
            databaseReference.child("patients").child(patientId).child("name")
                    .addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(DataSnapshot dataSnapshot) {
                            String patientName = dataSnapshot.getValue(String.class);
                            if (patientName == null) {
                                patientName = "Patient"; // Default name
                            }
                            
                            String severity = determineSeverity(transitionType, geofenceDefinition.type);
                            
                            // Send FCM notification
                            fcmNotificationSender.sendGeofenceAlert(
                                patientId,
                                patientName,
                                geofenceDefinition.label,
                                getTransitionName(transitionType),
                                severity,
                                alertId
                            );
                            
                            Log.d(TAG, "FCM notification sent to caretakers: " + 
                                  patientName + " - " + getTransitionName(transitionType) + 
                                  " " + geofenceDefinition.label);
                        }
                        
                        @Override
                        public void onCancelled(DatabaseError databaseError) {
                            Log.w(TAG, "Failed to get patient name, using default", databaseError.toException());
                            
                            // Send with default patient name
                            String severity = determineSeverity(transitionType, geofenceDefinition.type);
                            fcmNotificationSender.sendGeofenceAlert(
                                patientId,
                                "Patient",
                                geofenceDefinition.label,
                                getTransitionName(transitionType),
                                severity,
                                alertId
                            );
                        }
                    });
                    
        } catch (Exception e) {
            Log.e(TAG, "Error sending FCM notification to caretakers", e);
        }
    }
    
    /**
     * Show local notification for geofence event
     */
    private void showGeofenceNotification(PatientGeofence geofence, int transitionType) {
        String title = "Geofence Alert";
        String message = getTransitionName(transitionType) + " " + geofence.name;
        
        // Create simple notification
        // TODO: Implement notification when needed
        Log.i(TAG, "Geofence notification: " + title + " - " + message);
    }
    
    /**
     * Get PendingIntent for geofence transitions
     */
    private PendingIntent getGeofencePendingIntent() {
        if (geofencePendingIntent != null) {
            return geofencePendingIntent;
        }
        
        Intent intent = new Intent(context, GeofenceTransitionReceiver.class);
        
        // Android 12+ requires PendingIntent.FLAG_MUTABLE
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_MUTABLE;
        }
        
        geofencePendingIntent = PendingIntent.getBroadcast(context, 0, intent, flags);
        
        return geofencePendingIntent;
    }
    

    
    /**
     * Find geofence by ID
     */
    private PatientGeofence findGeofenceById(String geofenceId) {
        for (PatientGeofence geofence : activeGeofences) {
            if (geofence.id.equals(geofenceId)) {
                return geofence;
            }
        }
        return null;
    }
    
    /**
     * Get Android transition types from geofence type
     */
    private int getTransitionTypes(GeofenceType type) {
        switch (type) {
            case ENTER_ONLY:
                return Geofence.GEOFENCE_TRANSITION_ENTER;
            case EXIT_ONLY:
                return Geofence.GEOFENCE_TRANSITION_EXIT;
            case ENTER_EXIT:
            default:
                return Geofence.GEOFENCE_TRANSITION_ENTER | Geofence.GEOFENCE_TRANSITION_EXIT;
        }
    }
    
    /**
     * Get transition name for display
     */
    private String getTransitionName(int transitionType) {
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
    
    /**
     * Check if location permissions are granted
     */
    private boolean hasLocationPermissions() {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }
    
    /**
     * Add a test geofence for debugging purposes if no geofences are loaded
     */
    private void addTestGeofenceIfNeeded() {
        // Wait a bit for Firebase to load, then check if we have any geofences
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            if (activeGeofences.isEmpty()) {
                Log.i(TAG, "🧪 No geofences loaded from Firebase, creating test geofence for debugging");
                createTestGeofence();
            } else {
                Log.d(TAG, "✅ Loaded " + activeGeofences.size() + " geofences from Firebase");
            }
        }, 3000); // Wait 3 seconds for Firebase loading
    }
    
    /**
     * Create a test geofence around New York for testing purposes
     */
    private void createTestGeofence() {
        try {
            // Create a test safe zone in New York (40.7128° N, 74.0060° W) with 1000m radius
            PatientGeofence testGeofence = new PatientGeofence();
            testGeofence.id = "test-safe-zone-ny";
            testGeofence.name = "Test Safe Zone (New York)";
            testGeofence.description = "Test geofence for debugging - triggers when patient leaves NYC area";
            testGeofence.latitude = 40.7128;  // New York latitude
            testGeofence.longitude = -74.0060; // New York longitude  
            testGeofence.radius = 1000.0f;    // 1km radius
            testGeofence.type = GeofenceType.EXIT_ONLY;
            testGeofence.enabled = true;
            
            // Create GeofenceDefinition for the test zone
            GeofenceDefinition testDefinition = new GeofenceDefinition();
            testDefinition.id = testGeofence.id;
            testDefinition.label = testGeofence.name;
            testDefinition.description = testGeofence.description;
            testDefinition.lat = testGeofence.latitude;
            testDefinition.lng = testGeofence.longitude;
            testDefinition.radius = testGeofence.radius;
            testDefinition.type = GeofenceDefinition.TYPE_SAFE_ZONE;
            testDefinition.active = true;
            
            // Add to our collections
            activeGeofences.add(testGeofence);
            geofenceDefinitions.put(testGeofence.id, testDefinition);
            
            // Register with Android
            java.util.List<PatientGeofence> testList = new java.util.ArrayList<>();
            testList.add(testGeofence);
            registerGeofences(testList);
            
            Log.i(TAG, "🎯 TEST GEOFENCE CREATED: " + testGeofence.name + 
                     " at (" + testGeofence.latitude + ", " + testGeofence.longitude + ") " +
                     "with " + testGeofence.radius + "m radius");
                     
        } catch (Exception e) {
            Log.e(TAG, "Error creating test geofence", e);
        }
    }
    
    /**
     * Data classes
     */
    public static class PatientGeofence {
        public String id;
        public String name;
        public String description;
        public double latitude;
        public double longitude;
        public float radius;
        public GeofenceType type = GeofenceType.ENTER_EXIT;
        public boolean enabled = true;
    }
    
    public enum GeofenceType {
        ENTER_ONLY,
        EXIT_ONLY,
        ENTER_EXIT
    }
}