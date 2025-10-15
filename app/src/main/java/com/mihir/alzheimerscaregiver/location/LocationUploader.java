package com.mihir.alzheimerscaregiver.location;

import android.location.Location;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.mihir.alzheimerscaregiver.entities.LocationEntity;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Helper class for uploading location data to Firebase Realtime Database
 * Handles location uploads, history management, and cleanup operations
 */
public class LocationUploader {
    
    private static final String TAG = "LocationUploader";
    private static final int MAX_HISTORY_POINTS_PER_DAY = 144; // For 10-minute intervals
    
    private final FirebaseDatabase realtimeDb;
    private final FirebaseAuth auth;
    
    public LocationUploader() {
        this.realtimeDb = FirebaseDatabase.getInstance();
        this.auth = FirebaseAuth.getInstance();
    }
    
    /**
     * Interface for upload callbacks
     */
    public interface UploadCallback {
        void onSuccess();
        void onError(String error);
    }
    
    /**
     * Interface for history cleanup callbacks
     */
    public interface CleanupCallback {
        void onComplete(int entriesRemoved);
        void onError(String error);
    }
    
    /**
     * Upload current location to Firebase Realtime Database
     * Writes to /locations/{patientId} (latest) and appends to /locationHistory/{patientId}/{YYYY-MM-DD}
     * 
     * @param patientId The patient's unique identifier
     * @param location The location to upload
     * @param callback Callback for success/error handling
     */
    public void uploadCurrentLocation(String patientId, Location location, UploadCallback callback) {
        if (patientId == null || location == null) {
            if (callback != null) {
                callback.onError("Invalid patientId or location");
            }
            return;
        }
        
        long timestamp = System.currentTimeMillis();
        
        // Create location entity
        LocationEntity locationEntity = new LocationEntity(
                patientId,
                location.getLatitude(),
                location.getLongitude(),
                timestamp,
                location.getAccuracy(),
                location.getProvider(),
                location.isFromMockProvider()
        );
        
        Log.d(TAG, "Uploading location for patient: " + patientId + " at " + 
              locationEntity.latitude + ", " + locationEntity.longitude);
        
        // Update latest location first
        DatabaseReference latestRef = realtimeDb.getReference("locations").child(patientId);
        latestRef.setValue(locationEntity)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Latest location updated successfully");
                    
                    // Now add to history
                    addToHistory(patientId, locationEntity, callback);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to update latest location", e);
                    if (callback != null) {
                        callback.onError("Failed to upload location: " + e.getMessage());
                    }
                });
    }
    
    /**
     * Add location to daily history with automatic cleanup
     */
    private void addToHistory(String patientId, LocationEntity locationEntity, UploadCallback callback) {
        String dateKey = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date(locationEntity.timestamp));
        DatabaseReference historyRef = realtimeDb.getReference("locationHistory")
                .child(patientId)
                .child(dateKey);
        
        // Use push() to generate unique key for each location entry
        String pushKey = historyRef.push().getKey();
        if (pushKey != null) {
            historyRef.child(pushKey).setValue(locationEntity)
                    .addOnSuccessListener(aVoid -> {
                        Log.d(TAG, "Location added to history for date: " + dateKey);
                        
                        // Trigger cleanup for this date
                        trimHistoryForDate(patientId, dateKey, new CleanupCallback() {
                            @Override
                            public void onComplete(int entriesRemoved) {
                                if (entriesRemoved > 0) {
                                    Log.d(TAG, "History cleanup completed, removed " + entriesRemoved + " entries");
                                }
                                if (callback != null) {
                                    callback.onSuccess();
                                }
                            }
                            
                            @Override
                            public void onError(String error) {
                                Log.w(TAG, "History cleanup failed: " + error);
                                // Still consider the upload successful even if cleanup fails
                                if (callback != null) {
                                    callback.onSuccess();
                                }
                            }
                        });
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to add location to history", e);
                        if (callback != null) {
                            callback.onError("Failed to save to history: " + e.getMessage());
                        }
                    });
        } else {
            Log.e(TAG, "Failed to generate push key for history entry");
            if (callback != null) {
                callback.onError("Failed to generate unique key for history entry");
            }
        }
    }
    
    /**
     * Trim history for a specific date to keep only the last N entries
     * 
     * @param patientId The patient's unique identifier
     * @param date Date string in format YYYY-MM-DD
     * @param callback Callback for cleanup completion
     */
    public void trimHistoryForDate(String patientId, String date, CleanupCallback callback) {
        if (patientId == null || date == null) {
            if (callback != null) {
                callback.onError("Invalid patientId or date");
            }
            return;
        }
        
        DatabaseReference historyRef = realtimeDb.getReference("locationHistory")
                .child(patientId)
                .child(date);
        
        // Query all entries for this date, ordered by timestamp (oldest first)
        historyRef.orderByChild("timestamp").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                long totalCount = snapshot.getChildrenCount();
                
                if (totalCount <= MAX_HISTORY_POINTS_PER_DAY) {
                    Log.d(TAG, "History for date " + date + " has " + totalCount + " entries, no cleanup needed");
                    if (callback != null) {
                        callback.onComplete(0);
                    }
                    return;
                }
                
                Log.d(TAG, "Trimming history for date " + date + ": " + totalCount + " entries, keeping " + MAX_HISTORY_POINTS_PER_DAY);
                
                // Remove oldest entries to keep only the last MAX_HISTORY_POINTS_PER_DAY
                int toRemove = (int) (totalCount - MAX_HISTORY_POINTS_PER_DAY);
                int removed = 0;
                
                for (DataSnapshot child : snapshot.getChildren()) {
                    if (removed >= toRemove) {
                        break;
                    }
                    
                    child.getRef().removeValue()
                            .addOnSuccessListener(aVoid -> Log.d(TAG, "Removed old history entry: " + child.getKey()))
                            .addOnFailureListener(e -> Log.w(TAG, "Failed to remove history entry: " + child.getKey(), e));
                    
                    removed++;
                }
                
                if (callback != null) {
                    callback.onComplete(removed);
                }
            }
            
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to query history for cleanup", error.toException());
                if (callback != null) {
                    callback.onError("Database query failed: " + error.getMessage());
                }
            }
        });
    }
    
    /**
     * Trim history for all dates for a patient (for bulk cleanup)
     * 
     * @param patientId The patient's unique identifier
     * @param callback Callback for cleanup completion
     */
    public void trimAllHistory(String patientId, CleanupCallback callback) {
        if (patientId == null) {
            if (callback != null) {
                callback.onError("Invalid patientId");
            }
            return;
        }
        
        DatabaseReference historyRef = realtimeDb.getReference("locationHistory").child(patientId);
        
        historyRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                int totalDates = (int) snapshot.getChildrenCount();
                if (totalDates == 0) {
                    Log.d(TAG, "No history found for patient: " + patientId);
                    if (callback != null) {
                        callback.onComplete(0);
                    }
                    return;
                }
                
                Log.d(TAG, "Trimming history for " + totalDates + " dates for patient: " + patientId);
                
                final int[] processedDates = {0};
                final int[] totalRemoved = {0};
                
                for (DataSnapshot dateSnapshot : snapshot.getChildren()) {
                    String date = dateSnapshot.getKey();
                    if (date != null) {
                        trimHistoryForDate(patientId, date, new CleanupCallback() {
                            @Override
                            public void onComplete(int entriesRemoved) {
                                processedDates[0]++;
                                totalRemoved[0] += entriesRemoved;
                                
                                if (processedDates[0] >= totalDates) {
                                    Log.d(TAG, "All history trimmed, total removed: " + totalRemoved[0]);
                                    if (callback != null) {
                                        callback.onComplete(totalRemoved[0]);
                                    }
                                }
                            }
                            
                            @Override
                            public void onError(String error) {
                                processedDates[0]++;
                                Log.w(TAG, "Failed to trim history for date " + date + ": " + error);
                                
                                if (processedDates[0] >= totalDates) {
                                    if (callback != null) {
                                        callback.onComplete(totalRemoved[0]);
                                    }
                                }
                            }
                        });
                    }
                }
            }
            
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to query patient history for cleanup", error.toException());
                if (callback != null) {
                    callback.onError("Failed to query history: " + error.getMessage());
                }
            }
        });
    }
    
    /**
     * Clear current location from Firebase when location sharing is disabled
     * Removes the latest location entry for privacy
     * 
     * @param patientId The patient's unique identifier
     * @param callback Callback for success/error handling
     */
    public void clearLocationOnStop(String patientId, UploadCallback callback) {
        if (patientId == null) {
            if (callback != null) {
                callback.onError("Invalid patientId");
            }
            return;
        }
        
        Log.d(TAG, "Clearing current location for patient: " + patientId);
        
        DatabaseReference latestRef = realtimeDb.getReference("locations").child(patientId);
        latestRef.removeValue()
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Current location cleared successfully for patient: " + patientId);
                    if (callback != null) {
                        callback.onSuccess();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to clear current location for patient: " + patientId, e);
                    if (callback != null) {
                        callback.onError("Failed to clear location: " + e.getMessage());
                    }
                });
    }
    
    /**
     * Update sharing enabled state in Firebase
     * 
     * @param patientId The patient's unique identifier
     * @param enabled Whether location sharing is enabled
     * @param callback Callback for success/error handling
     */
    public void updateSharingEnabled(String patientId, boolean enabled, UploadCallback callback) {
        if (patientId == null) {
            if (callback != null) {
                callback.onError("Invalid patientId");
            }
            return;
        }
        
        Log.d(TAG, "Updating sharing enabled state for patient " + patientId + ": " + enabled);
        
        DatabaseReference sharingRef = realtimeDb.getReference("sharingEnabled").child(patientId);
        sharingRef.setValue(enabled)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Sharing enabled state updated successfully");
                    if (callback != null) {
                        callback.onSuccess();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to update sharing enabled state", e);
                    if (callback != null) {
                        callback.onError("Failed to update sharing state: " + e.getMessage());
                    }
                });
    }
    
    /**
     * Check if location sharing is enabled in Firebase
     * 
     * @param patientId The patient's unique identifier
     * @param callback Callback with the current sharing state
     */
    public void getSharingEnabled(String patientId, SharingStateCallback callback) {
        if (patientId == null) {
            if (callback != null) {
                callback.onError("Invalid patientId");
            }
            return;
        }
        
        DatabaseReference sharingRef = realtimeDb.getReference("sharingEnabled").child(patientId);
        sharingRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Boolean enabled = snapshot.getValue(Boolean.class);
                if (callback != null) {
                    callback.onSharingState(enabled != null ? enabled : false);
                }
            }
            
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to get sharing enabled state", error.toException());
                if (callback != null) {
                    callback.onError("Failed to get sharing state: " + error.getMessage());
                }
            }
        });
    }
    
    /**
     * Interface for sharing state callbacks
     */
    public interface SharingStateCallback {
        void onSharingState(boolean enabled);
        void onError(String error);
    }
    
    /**
     * Update patient-caretaker link in Realtime Database
     * This enables caretakers to access patient data via database rules
     * 
     * @param patientId The patient's unique identifier
     * @param caretakerId The caretaker's unique identifier
     * @param callback Callback for success/error handling
     */
    public void updatePatientCaretakerLink(String patientId, String caretakerId, UploadCallback callback) {
        if (patientId == null || caretakerId == null) {
            if (callback != null) {
                callback.onError("Invalid patientId or caretakerId");
            }
            return;
        }
        
        Log.d(TAG, "Linking patient " + patientId + " to caretaker " + caretakerId);
        
        DatabaseReference linkRef = realtimeDb.getReference("patientCaretakerLinks").child(patientId);
        linkRef.setValue(caretakerId)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Patient-Caretaker link updated successfully");
                    if (callback != null) {
                        callback.onSuccess();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to update patient-caretaker link", e);
                    if (callback != null) {
                        callback.onError("Failed to update link: " + e.getMessage());
                    }
                });
    }
    
    /**
     * Add an alert to Firebase (for geofence violations, etc.)
     * 
     * @param patientId The patient's unique identifier
     * @param alertType Type of alert (e.g., "GEOFENCE_EXIT", "GEOFENCE_ENTER")
     * @param details Additional details about the alert
     * @param callback Callback for success/error handling
     */
    public void addAlert(String patientId, String alertType, String details, UploadCallback callback) {
        if (patientId == null || alertType == null) {
            if (callback != null) {
                callback.onError("Invalid patientId or alertType");
            }
            return;
        }
        
        long timestamp = System.currentTimeMillis();
        
        // Create alert object
        AlertEntity alert = new AlertEntity(alertType, timestamp, details);
        
        Log.d(TAG, "Adding alert for patient " + patientId + ": " + alertType);
        
        DatabaseReference alertsRef = realtimeDb.getReference("alerts").child(patientId);
        String pushKey = alertsRef.push().getKey();
        
        if (pushKey != null) {
            alertsRef.child(pushKey).setValue(alert)
                    .addOnSuccessListener(aVoid -> {
                        Log.d(TAG, "Alert added successfully: " + alertType);
                        if (callback != null) {
                            callback.onSuccess();
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to add alert", e);
                        if (callback != null) {
                            callback.onError("Failed to add alert: " + e.getMessage());
                        }
                    });
        } else {
            if (callback != null) {
                callback.onError("Failed to generate unique key for alert");
            }
        }
    }
    
    /**
     * Alert entity class for Firebase storage
     */
    public static class AlertEntity {
        public String type;
        public long timestamp;
        public String details;
        
        // Default constructor required for Firebase
        public AlertEntity() {}
        
        public AlertEntity(String type, long timestamp, String details) {
            this.type = type;
            this.timestamp = timestamp;
            this.details = details;
        }
    }
    
    /**
     * Get current authenticated patient ID
     */
    public String getCurrentPatientId() {
        return auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : null;
    }
    
    /**
     * Check if user is authenticated
     */
    public boolean isAuthenticated() {
        return auth.getCurrentUser() != null;
    }
}