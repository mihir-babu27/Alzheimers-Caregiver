package com.mihir.alzheimerscaregiver.utils;

import android.content.Context;
import android.util.Log;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.mihir.alzheimerscaregiver.BuildConfig;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Helper class for sending FCM notifications using HTTP v1 API from Patient App to CaretakerApp
 * Uses OAuth 2.0 service account authentication instead of deprecated server keys
 */
public class FCMNotificationSender {
    
    private static final String TAG = "FCMNotificationSender";
    private static final String FCM_SCOPE = "https://www.googleapis.com/auth/firebase.messaging";
    
    private Context context;
    private DatabaseReference databaseReference;
    private OkHttpClient httpClient;
    
    // Get FCM HTTP v1 API URL with project ID
    private static String getFCMUrl() {
        String projectId = BuildConfig.FIREBASE_PROJECT_ID;
        if ("placeholder".equals(projectId)) {
            Log.w(TAG, "Firebase Project ID not configured! Please add it to fcm-keys.properties");
            return null;
        }
        return "https://fcm.googleapis.com/v1/projects/" + projectId + "/messages:send";
    }
    
    /**
     * Get OAuth 2.0 access token using service account credentials (Async - prevents ANR)
     */
    private String getAccessToken() {
        try {
            String serviceAccountPath = BuildConfig.FIREBASE_SERVICE_ACCOUNT_PATH;
            if ("firebase-service-account.json.template".equals(serviceAccountPath) || 
                serviceAccountPath == null || serviceAccountPath.isEmpty()) {
                Log.w(TAG, "🔐 Firebase service account not configured! FCM notifications disabled.");
                Log.w(TAG, "📋 To enable notifications: Replace placeholder values in firebase-service-account.json");
                return null;
            }
            
            // Load service account from assets folder
            InputStream serviceAccount = context.getAssets().open(serviceAccountPath);
            GoogleCredentials googleCredentials = GoogleCredentials
                    .fromStream(serviceAccount)
                    .createScoped(Arrays.asList(FCM_SCOPE));
            
            // Note: This should ideally be called on background thread to prevent ANR
            // But for FCM notifications, this is already called from notification background context
            googleCredentials.refreshIfExpired();
            return googleCredentials.getAccessToken().getTokenValue();
            
        } catch (IOException e) {
            Log.e(TAG, "Error getting OAuth 2.0 access token - check firebase-service-account.json in assets/", e);
            return null;
        }
    }
    
    public FCMNotificationSender(Context context) {
        this.context = context;
        this.databaseReference = FirebaseDatabase.getInstance().getReference();
        this.httpClient = new OkHttpClient();
    }
    
    /**
     * Send geofence alert notification to all caretakers associated with patient
     */
    public void sendGeofenceAlert(String patientId, String patientName, String geofenceName, 
                                 String transitionType, String severity, String alertId) {
        
        Log.i(TAG, "🚨 sendGeofenceAlert() called:");
        Log.i(TAG, "   Patient: " + patientName + " (" + patientId + ")");
        Log.i(TAG, "   Geofence: " + geofenceName);
        Log.i(TAG, "   Transition: " + transitionType);
        Log.i(TAG, "   Severity: " + severity);
        
        // Get all caretaker tokens for this patient
        Log.i(TAG, "🔍 Looking up FCM tokens at: patient_caretaker_tokens/" + patientId);
        databaseReference.child("patient_caretaker_tokens")
                .child(patientId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        if (!dataSnapshot.exists()) {
                            Log.w(TAG, "❌ No caretaker tokens found at patient_caretaker_tokens/" + patientId);
                            return;
                        }
                        
                        int tokenCount = 0;
                        for (DataSnapshot caretakerSnapshot : dataSnapshot.getChildren()) {
                            try {
                                // Check if this is a direct token string or nested object
                                Object snapshotValue = caretakerSnapshot.getValue();
                                
                                String token = null;
                                if (snapshotValue instanceof String) {
                                    // Direct string token
                                    token = (String) snapshotValue;
                                    Log.d(TAG, "Found direct string token for caretaker: " + caretakerSnapshot.getKey());
                                } else {
                                    // Nested object with token field
                                    DataSnapshot tokenField = caretakerSnapshot.child("token");
                                    if (tokenField.exists()) {
                                        token = tokenField.getValue(String.class);
                                        Log.d(TAG, "Found nested token for caretaker: " + caretakerSnapshot.getKey());
                                    }
                                    
                                    // Also check for active status
                                    DataSnapshot activeField = caretakerSnapshot.child("active");
                                    Boolean active = activeField.exists() ? activeField.getValue(Boolean.class) : true;
                                    if (active != null && !active) {
                                        Log.d(TAG, "Skipping inactive token for caretaker: " + caretakerSnapshot.getKey());
                                        continue;
                                    }
                                }
                                
                                if (token != null && !token.isEmpty()) {
                                    Log.i(TAG, "📤 Sending FCM to caretaker: " + caretakerSnapshot.getKey());
                                    sendFCMNotification(token, patientId, patientName, geofenceName,
                                                      transitionType, severity, alertId);
                                    tokenCount++;
                                } else {
                                    Log.w(TAG, "Empty or missing token for caretaker: " + caretakerSnapshot.getKey());
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Error processing caretaker token for: " + caretakerSnapshot.getKey(), e);
                            }
                        }
                        
                        Log.i(TAG, "✅ FCM notification sent to " + tokenCount + " caretaker(s)");
                    }
                    
                    @Override
                    public void onCancelled(DatabaseError databaseError) {
                        Log.e(TAG, "Failed to load caretaker tokens", databaseError.toException());
                    }
                });
    }
    
    /**
     * Send FCM notification to specific caretaker token using HTTP v1 API
     */
    private void sendFCMNotification(String token, String patientId, String patientName, 
                                   String geofenceName, String transitionType, 
                                   String severity, String alertId) {
        
        // Move FCM operations to background thread to prevent NetworkOnMainThreadException
        new Thread(() -> {
            try {
                Log.d(TAG, "📤 Starting FCM send on background thread...");
                
                // Get OAuth 2.0 access token
                String accessToken = getAccessToken();
                if (accessToken == null) {
                    Log.e(TAG, "Cannot send FCM notification: Access token not available");
                    return;
                }
            
            String fcmUrl = getFCMUrl();
            if (fcmUrl == null) {
                Log.e(TAG, "Cannot send FCM notification: FCM URL not configured");
                return;
            }
            
            // Create notification object (HTTP v1 format)
            JSONObject notification = new JSONObject();
            
            // Dynamic title based on severity and transition type
            String title;
            if ("high".equals(severity)) {
                title = "🚨 URGENT: Patient Safety Alert";
            } else if ("EXIT".equals(transitionType)) {
                title = "📍 Patient Location Update";
            } else {
                title = "✅ Safe Zone Activity";
            }
            notification.put("title", title);
            
            // Enhanced body with emojis and clear messaging
            String body;
            if ("EXIT".equals(transitionType)) {
                if ("high".equals(severity)) {
                    body = "⚠️ " + patientName + " has LEFT the " + geofenceName + " safe zone. Please check their status immediately.";
                } else {
                    body = "📤 " + patientName + " has left " + geofenceName + ". They may be heading out.";
                }
            } else if ("ENTER".equals(transitionType)) {
                body = "🏠 " + patientName + " has safely entered " + geofenceName + ".";
            } else {
                body = "📱 " + patientName + " - " + transitionType + " detected at " + geofenceName;
            }
            notification.put("body", body);
            
            // Create data payload - all values must be strings in HTTP v1
            JSONObject data = new JSONObject();
            data.put("alertType", "geofence_alert");
            data.put("patientId", patientId);
            data.put("patientName", patientName);
            data.put("geofenceName", geofenceName);
            data.put("transitionType", transitionType);
            data.put("severity", severity);
            data.put("alertId", alertId);
            data.put("timestamp", String.valueOf(System.currentTimeMillis()));
            
            // Create Android-specific configuration
            JSONObject androidNotification = new JSONObject();
            androidNotification.put("icon", "ic_notification_location");
            androidNotification.put("click_action", "OPEN_GEOFENCE_MANAGEMENT");
            androidNotification.put("tag", "geofence_alert_" + patientId);
            
            if ("high".equals(severity)) {
                androidNotification.put("sound", "urgent_alert");
                androidNotification.put("color", "#FF4444"); // Red color for urgent alerts
            } else {
                androidNotification.put("sound", "gentle_chime");
                androidNotification.put("color", "#4CAF50"); // Green color for normal alerts
            }
            
            JSONObject androidConfig = new JSONObject();
            androidConfig.put("notification", androidNotification);
            androidConfig.put("priority", "high");
            
            // Create the message object (HTTP v1 format)
            JSONObject message = new JSONObject();
            message.put("token", token); // Use 'token' instead of 'to'
            message.put("notification", notification);
            message.put("data", data);
            message.put("android", androidConfig);
            
            // Create the root payload
            JSONObject payload = new JSONObject();
            payload.put("message", message);
            
            // Send HTTP v1 request to FCM
            RequestBody requestBody = RequestBody.create(
                MediaType.parse("application/json"), 
                payload.toString() // Send the complete payload, not just message
            );
            
            Request request = new Request.Builder()
                    .url(fcmUrl) // Use HTTP v1 URL
                    .post(requestBody)
                    .addHeader("Authorization", "Bearer " + accessToken) // OAuth 2.0 Bearer token
                    .addHeader("Content-Type", "application/json")
                    .build();
            
            httpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "Failed to send FCM HTTP v1 notification", e);
                }
                
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (response.isSuccessful()) {
                        Log.d(TAG, "FCM HTTP v1 notification sent successfully: " + 
                              patientName + " - " + transitionType + " " + geofenceName);
                    } else {
                        String responseBody = response.body() != null ? response.body().string() : "No response body";
                        Log.w(TAG, "FCM HTTP v1 notification failed: " + response.code() + 
                              " - " + response.message() + " - " + responseBody);
                    }
                    response.close();
                }
            });
            
            } catch (JSONException e) {
                Log.e(TAG, "Error creating FCM notification JSON", e);
            } catch (Exception e) {
                Log.e(TAG, "Error sending FCM notification", e);
            }
        }).start(); // Start the background thread
    }
    
    /**
     * Send general notification to caretakers
     */
    public void sendGeneralNotification(String patientId, String title, String message) {
        databaseReference.child("patient_caretaker_tokens")
                .child(patientId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        for (DataSnapshot caretakerSnapshot : dataSnapshot.getChildren()) {
                            try {
                                String token = caretakerSnapshot.child("token").getValue(String.class);
                                Boolean active = caretakerSnapshot.child("active").getValue(Boolean.class);
                                
                                if (token != null && (active == null || active)) {
                                    sendSimpleFCMNotification(token, title, message);
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Error processing caretaker token", e);
                            }
                        }
                    }
                    
                    @Override
                    public void onCancelled(DatabaseError databaseError) {
                        Log.e(TAG, "Failed to load caretaker tokens", databaseError.toException());
                    }
                });
    }
    
    /**
     * Send simple FCM notification using HTTP v1 API
     */
    private void sendSimpleFCMNotification(String token, String title, String body) {
        try {
            // Get OAuth 2.0 access token
            String accessToken = getAccessToken();
            if (accessToken == null) {
                Log.e(TAG, "Cannot send simple FCM notification: Access token not available");
                return;
            }
            
            String fcmUrl = getFCMUrl();
            if (fcmUrl == null) {
                Log.e(TAG, "Cannot send simple FCM notification: FCM URL not configured");
                return;
            }
            
            // Create notification object (HTTP v1 format)
            JSONObject notification = new JSONObject();
            notification.put("title", title);
            notification.put("body", body);
            
            // Create the message object (HTTP v1 format)
            JSONObject message = new JSONObject();
            message.put("token", token); // Use 'token' instead of 'to'
            message.put("notification", notification);
            
            // Create the root payload
            JSONObject payload = new JSONObject();
            payload.put("message", message);
            
            // Send HTTP v1 request to FCM
            RequestBody requestBody = RequestBody.create(
                MediaType.parse("application/json"), 
                payload.toString()
            );
            
            Request request = new Request.Builder()
                    .url(fcmUrl) // Use HTTP v1 URL
                    .post(requestBody)
                    .addHeader("Authorization", "Bearer " + accessToken) // OAuth 2.0 Bearer token
                    .addHeader("Content-Type", "application/json")
                    .build();
            
            httpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "Failed to send simple FCM HTTP v1 notification", e);
                }
                
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (response.isSuccessful()) {
                        Log.d(TAG, "Simple FCM HTTP v1 notification sent successfully: " + title);
                    } else {
                        String responseBody = response.body() != null ? response.body().string() : "No response body";
                        Log.w(TAG, "Simple FCM HTTP v1 notification failed: " + response.code() + 
                              " - " + response.message() + " - " + responseBody);
                    }
                    response.close();
                }
            });
            
        } catch (JSONException e) {
            Log.e(TAG, "Error creating simple FCM HTTP v1 notification JSON", e);
        }
    }
    
    /**
     * Send missed medication alert notification to caretakers
     */
    public void sendMissedMedicationAlert(String patientId, String patientName, String medicationName, String scheduledTime) {
        databaseReference.child("patient_caretaker_tokens")
                .child(patientId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        for (DataSnapshot caretakerSnapshot : dataSnapshot.getChildren()) {
                            try {
                                String token = caretakerSnapshot.child("token").getValue(String.class);
                                Boolean active = caretakerSnapshot.child("active").getValue(Boolean.class);
                                
                                if (token != null && (active == null || active)) {
                                    sendMissedMedicationFCM(token, patientName, medicationName, scheduledTime);
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Error processing caretaker token for missed medication alert", e);
                            }
                        }
                    }
                    
                    @Override
                    public void onCancelled(DatabaseError databaseError) {
                        Log.e(TAG, "Failed to load caretaker tokens for missed medication alert", databaseError.toException());
                    }
                });
    }
    
    /**
     * Send missed medication FCM notification using HTTP v1 API
     * FIXED: Now runs on background thread to prevent NetworkOnMainThreadException
     */
    private void sendMissedMedicationFCM(String token, String patientName, String medicationName, String scheduledTime) {
        // Move FCM operations to background thread to prevent NetworkOnMainThreadException
        new Thread(() -> {
            try {
                Log.d(TAG, "🔄 Starting FCM notification on background thread...");
                
                // Get OAuth 2.0 access token (network operation)
                String accessToken = getAccessToken();
            if (accessToken == null) {
                Log.e(TAG, "Cannot send missed medication FCM notification: Access token not available");
                return;
            }
            
            String fcmUrl = getFCMUrl();
            if (fcmUrl == null) {
                Log.e(TAG, "Cannot send missed medication FCM notification: FCM URL not configured");
                return;
            }
            
            // Create notification object (HTTP v1 format)
            JSONObject notification = new JSONObject();
            notification.put("title", "💊 Medication Reminder Missed");
            notification.put("body", patientName + " has not taken their " + medicationName + " medication scheduled at " + scheduledTime + ". Please check on them.");
            
            // Create data payload - all values must be strings in HTTP v1
            JSONObject data = new JSONObject();
            data.put("alertType", "missed_medication");
            data.put("patientName", patientName);
            data.put("medicationName", medicationName);
            data.put("scheduledTime", scheduledTime);
            data.put("timestamp", String.valueOf(System.currentTimeMillis()));
            
            // Create Android-specific configuration
            JSONObject androidNotification = new JSONObject();
            androidNotification.put("icon", "ic_notification_medication");
            androidNotification.put("click_action", "OPEN_MEDICATION_MANAGEMENT");
            androidNotification.put("tag", "missed_medication_alert");
            androidNotification.put("sound", "medication_alert");
            androidNotification.put("color", "#FF9800"); // Orange color for medication alerts
            
            JSONObject androidConfig = new JSONObject();
            androidConfig.put("notification", androidNotification);
            androidConfig.put("priority", "high");
            
            // Create the message object (HTTP v1 format)
            JSONObject message = new JSONObject();
            message.put("token", token); // Use 'token' instead of 'to'
            message.put("notification", notification);
            message.put("data", data);
            message.put("android", androidConfig);
            
            // Create the root payload
            JSONObject payload = new JSONObject();
            payload.put("message", message);
            
            // Send HTTP v1 request to FCM
            RequestBody requestBody = RequestBody.create(
                MediaType.parse("application/json"), 
                payload.toString()
            );
            
            Request request = new Request.Builder()
                    .url(fcmUrl) // Use HTTP v1 URL
                    .post(requestBody)
                    .addHeader("Authorization", "Bearer " + accessToken) // OAuth 2.0 Bearer token
                    .addHeader("Content-Type", "application/json")
                    .build();
            
            httpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "Failed to send missed medication FCM HTTP v1 notification", e);
                }
                
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (response.isSuccessful()) {
                        Log.d(TAG, "Missed medication FCM HTTP v1 notification sent successfully: " + 
                              patientName + " - " + medicationName);
                    } else {
                        String responseBody = response.body() != null ? response.body().string() : "No response body";
                        Log.w(TAG, "Missed medication FCM HTTP v1 notification failed: " + response.code() + 
                              " - " + response.message() + " - " + responseBody);
                    }
                    response.close();
                }
            });
            
            } catch (JSONException e) {
                Log.e(TAG, "Error creating missed medication FCM notification JSON", e);
            } catch (Exception e) {
                Log.e(TAG, "Error sending missed medication FCM notification", e);
            }
        }).start(); // Start the background thread
    }
    
    /**
     * Get appropriate notification image URL based on transition type and severity
     */
    private String getNotificationImageUrl(String transitionType, String severity) {
        // You can host these images on Firebase Storage or your server
        String baseUrl = "https://your-app-domain.com/notification-images/";
        
        if ("high".equals(severity)) {
            return baseUrl + "urgent_alert_banner.png"; // Red warning image
        } else if ("EXIT".equals(transitionType)) {
            return baseUrl + "patient_leaving_banner.png"; // Person leaving safe zone
        } else if ("ENTER".equals(transitionType)) {
            return baseUrl + "patient_safe_banner.png"; // Person in safe zone
        } else {
            return baseUrl + "location_update_banner.png"; // General location icon
        }
    }
}