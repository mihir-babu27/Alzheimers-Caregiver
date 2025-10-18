package com.mihir.alzheimerscaregiver.location;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.location.Location;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.mihir.alzheimerscaregiver.MainActivity;
import com.mihir.alzheimerscaregiver.R;
import com.mihir.alzheimerscaregiver.entities.LocationEntity;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Foreground service for continuous patient location tracking
 * 
 * Features:
 * - Uses FusedLocationProviderClient with PRIORITY_BALANCED_POWER_ACCURACY for battery efficiency
 * - Uploads location to Firebase Realtime Database at configurable intervals (default: 5 minutes)
 * - Maintains location history with bounded storage (144 points per day max)
 * - Implements security measures to detect mock locations
 * - Handles network failures with exponential backoff
 * - Respects user privacy settings and location sharing toggles
 */
public class PatientLocationService extends Service {
    
    private static final String TAG = "PatientLocationService";
    private static final String CHANNEL_ID = "patient_location_channel";
    private static final int NOTIFICATION_ID = 2001;
    
    // Intent actions
    public static final String ACTION_START_TRACKING = "start_tracking";
    public static final String ACTION_STOP_TRACKING = "stop_tracking";
    public static final String ACTION_REQUEST_CURRENT_LOCATION = "request_current_location";
    public static final String ACTION_VERIFY_LOCATION_UPDATES = "verify_location_updates";
    
    // Configuration constants imported from centralized LocationConfig
    // Best practices configuration values as per requirements
    
    // Exponential backoff for network failures
    private static final long INITIAL_RETRY_DELAY_MS = 1000; // 1 second
    private static final long MAX_RETRY_DELAY_MS = 5 * 60 * 1000; // 5 minutes
    private long currentRetryDelay = INITIAL_RETRY_DELAY_MS;
    private int consecutiveFailures = 0;
    
    // Service components
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private LocationRequest locationRequest;
    private FirebaseDatabase realtimeDb;
    private FirebaseAuth auth;
    private SharedPreferences prefs;
    private Handler mainHandler;
    private LocationUploader locationUploader;
    
    // State tracking
    private boolean isTracking = false;
    private Location lastKnownLocation;
    private long lastUploadTime = 0;
    private String currentPatientId;
    
    // Test mode timer for forcing location updates
    private Handler testModeHandler;
    private Runnable testModeLocationUpdater;
    
    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "PatientLocationService created");
        
        // Initialize components
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        realtimeDb = FirebaseDatabase.getInstance();
        auth = FirebaseAuth.getInstance();
        prefs = getSharedPreferences("location_prefs", MODE_PRIVATE);
        mainHandler = new Handler(Looper.getMainLooper());
        locationUploader = new LocationUploader();
        
        // Initialize test mode components
        testModeHandler = new Handler(Looper.getMainLooper());
        setupTestModeLocationUpdater();
        
        // Create notification channel
        createNotificationChannel();
        
        // Setup location request
        setupLocationRequest();
        
        // Setup location callback
        setupLocationCallback();
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            stopSelf();
            return START_NOT_STICKY;
        }
        
        String action = intent.getAction();
        Log.d(TAG, "onStartCommand with action: " + action);
        
        // Verify user is authenticated
        if (auth.getCurrentUser() == null) {
            Log.e(TAG, "No authenticated user, stopping service");
            stopSelf();
            return START_NOT_STICKY;
        }
        
        currentPatientId = auth.getCurrentUser().getUid();
        
        switch (action != null ? action : "") {
            case ACTION_START_TRACKING:
                boolean isPostBootRestart = intent.getBooleanExtra("post_boot_restart", false);
                if (isPostBootRestart) {
                    Log.d(TAG, "Starting location tracking after device boot");
                    // For post-boot restart, ensure we completely reinitialize the location client
                    reinitializeLocationClientAfterBoot();
                }
                startLocationTracking();
                break;
            case ACTION_STOP_TRACKING:
                stopLocationTracking();
                break;
            case ACTION_REQUEST_CURRENT_LOCATION:
                requestCurrentLocation();
                break;
            case ACTION_VERIFY_LOCATION_UPDATES:
                verifyLocationUpdatesWorking();
                break;
            default:
                Log.w(TAG, "Unknown action: " + action);
                stopSelf();
                return START_NOT_STICKY;
        }
        
        return START_STICKY; // Restart if killed by system
    }
    
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null; // Not a bound service
    }
    
    @Override
    public void onDestroy() {
        Log.d(TAG, "PatientLocationService destroyed");
        stopLocationTracking();
        super.onDestroy();
    }
    
    /**
     * Start continuous location tracking
     */
    private void startLocationTracking() {
        if (isTracking) {
            Log.d(TAG, "Already tracking location");
            return;
        }
        
        if (!hasLocationPermissions()) {
            Log.e(TAG, "Location permissions not granted");
            stopSelf();
            return;
        }
        
        // Check if location sharing is enabled in settings
        if (!isLocationSharingEnabled()) {
            Log.d(TAG, "Location sharing disabled in settings");
            stopSelf();
            return;
        }
        
        Log.d(TAG, "Starting location tracking");
        isTracking = true;
        
        // Start foreground service with notification
        startForeground(NOTIFICATION_ID, createTrackingNotification(), getLocationForegroundServiceType());
        
        // Request location updates
        try {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
            Log.d(TAG, "Location updates requested successfully");
            
            // Reset retry delay on successful start
            currentRetryDelay = INITIAL_RETRY_DELAY_MS;
            consecutiveFailures = 0;
            
            // Start test mode periodic updates if enabled
            if (LocationConfig.TEST_MODE && testModeLocationUpdater != null) {
                Log.i(TAG, "🚀 Starting TEST MODE periodic location updates every " + LocationConfig.TEST_UPLOAD_INTERVAL_MS + "ms");
                testModeHandler.postDelayed(testModeLocationUpdater, LocationConfig.TEST_UPLOAD_INTERVAL_MS);
            }
            
        } catch (SecurityException e) {
            Log.e(TAG, "Security exception requesting location updates", e);
            stopSelf();
        }
    }
    
    /**
     * Stop location tracking
     */
    private void stopLocationTracking() {
        if (!isTracking) {
            Log.d(TAG, "Not currently tracking location");
            return;
        }
        
        Log.d(TAG, "Stopping location tracking");
        isTracking = false;
        
        // Stop test mode updates
        if (LocationConfig.TEST_MODE && testModeHandler != null && testModeLocationUpdater != null) {
            testModeHandler.removeCallbacks(testModeLocationUpdater);
            Log.i(TAG, "🛑 Stopped TEST MODE periodic location updates");
        }
        
        // Stop location updates
        if (fusedLocationClient != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
        
        // Remove from Firebase (optional - for privacy)
        if (isLocationSharingEnabled()) {
            removeLocationFromFirebase();
        }
        
        // Stop foreground service
        stopForeground(true);
        stopSelf();
    }
    
    /**
     * Request immediate current location
     */
    private void requestCurrentLocation() {
        if (!hasLocationPermissions()) {
            Log.e(TAG, "Location permissions not granted for current location request");
            return;
        }
        
        Log.d(TAG, "Requesting current location");
        
        try {
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                    .addOnSuccessListener(location -> {
                        if (location != null) {
                            Log.d(TAG, "Current location obtained: " + location.getLatitude() + ", " + location.getLongitude());
                            processLocation(location, true); // Force upload
                        } else {
                            Log.w(TAG, "Current location is null");
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to get current location", e);
                    });
        } catch (SecurityException e) {
            Log.e(TAG, "Security exception requesting current location", e);
        }
    }
    
    /**
     * Setup location request parameters
     */
    private void setupLocationRequest() {
        long intervalMillis = prefs.getLong("location_interval_millis", LocationConfig.DEFAULT_LOCATION_INTERVAL_MS);
        float displacementMeters = prefs.getFloat("smallest_displacement_meters", LocationConfig.getEffectiveDisplacement());
        
        // Use effective intervals from config (TEST_MODE gives 10 seconds)
        long effectiveInterval = LocationConfig.getEffectiveUploadInterval(intervalMillis);
        
        locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, effectiveInterval)
                .setMinUpdateIntervalMillis(effectiveInterval / 2) // Allow faster updates
                .setMinUpdateDistanceMeters(displacementMeters) // 0 in test mode
                .setMaxUpdateDelayMillis(effectiveInterval * 2) // Double the interval for max delay
                .setWaitForAccurateLocation(false) // Don't wait indefinitely for high accuracy
                .build();
        
        Log.i(TAG, "Location request configured: interval=" + effectiveInterval + "ms, displacement=" + displacementMeters + "m, priority=HIGH_ACCURACY");
        
        if (LocationConfig.TEST_MODE) {
            Log.i(TAG, "⚠️ TEST MODE ENABLED - Using " + effectiveInterval + "ms intervals and " + displacementMeters + "m displacement for testing");
        }
    }
    
    /**
     * Setup test mode periodic location updater for reliable emulator testing
     */
    private void setupTestModeLocationUpdater() {
        if (!LocationConfig.TEST_MODE) return;
        
        testModeLocationUpdater = new Runnable() {
            @Override
            public void run() {
                if (isTracking && hasLocationPermissions()) {
                    Log.d(TAG, "🔄 TEST MODE: Forcing location request");
                    requestCurrentLocationForTesting();
                }
                // Schedule next update
                if (isTracking) {
                    testModeHandler.postDelayed(this, LocationConfig.TEST_UPLOAD_INTERVAL_MS);
                }
            }
        };
    }
    
    /**
     * Force location request specifically for test mode
     */
    private void requestCurrentLocationForTesting() {
        if (!hasLocationPermissions()) return;
        
        try {
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                    .addOnSuccessListener(location -> {
                        if (location != null) {
                            Log.i(TAG, "🎯 TEST MODE Location obtained: " + String.format("%.6f", location.getLatitude()) + 
                                     ", " + String.format("%.6f", location.getLongitude()));
                            processLocation(location, true); // Force upload in test mode
                        } else {
                            Log.w(TAG, "TEST MODE: Got null location from getCurrentLocation");
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "TEST MODE: Failed to get current location", e);
                    });
        } catch (SecurityException e) {
            Log.e(TAG, "TEST MODE: Security exception getting location", e);
        }
    }
    
    /**
     * Setup location callback to handle location updates
     */
    private void setupLocationCallback() {
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                super.onLocationResult(locationResult);
                
                Location location = locationResult.getLastLocation();
                if (location != null) {
                    // Update last location update timestamp for verification
                    prefs.edit().putLong("last_location_update", System.currentTimeMillis()).apply();
                    
                    String logMessage = "📍 Location update: " + String.format("%.6f", location.getLatitude()) + 
                                       ", " + String.format("%.6f", location.getLongitude()) + 
                                       " (accuracy: " + location.getAccuracy() + "m, provider: " + location.getProvider() + ")";
                    
                    if (LocationConfig.TEST_MODE) {
                        Log.i(TAG, logMessage + " - TEST MODE");
                    } else {
                        Log.d(TAG, logMessage);
                    }
                    
                    processLocation(location, false);
                } else {
                    Log.w(TAG, "Received null location in callback");
                }
            }
        };
    }
    
    /**
     * Process new location and upload to Firebase if necessary
     */
    private void processLocation(Location location, boolean forceUpload) {
        if (location == null) return;
        
        lastKnownLocation = location;
        
        // Check if we should upload this location
        long currentTime = System.currentTimeMillis();
        long timeSinceLastUpload = currentTime - lastUploadTime;
        long minInterval = LocationConfig.getEffectiveUploadInterval(
            prefs.getLong("location_interval_millis", LocationConfig.DEFAULT_LOCATION_INTERVAL_MS)
        );
        
        if (!forceUpload && timeSinceLastUpload < minInterval) {
            Log.d(TAG, "Skipping upload - too soon since last upload (" + timeSinceLastUpload + "ms < " + minInterval + "ms)");
            return;
        }
        
        // Create location entity
        LocationEntity locationEntity = new LocationEntity(
                currentPatientId,
                location.getLatitude(),
                location.getLongitude(),
                currentTime,
                location.getAccuracy(),
                location.getProvider(),
                location.isFromMockProvider()
        );
        
        // Upload to Firebase
        uploadLocationToFirebase(locationEntity);
        
        // Update notification with current location info
        updateTrackingNotification(location);
    }
    
    /**
     * Upload location to Firebase using LocationUploader with error handling
     */
    private void uploadLocationToFirebase(LocationEntity locationEntity) {
        if (currentPatientId == null) {
            Log.e(TAG, "No patient ID available for upload");
            return;
        }
        
        Log.d(TAG, "Uploading location to Firebase using LocationUploader");
        
        // Convert LocationEntity to Location for LocationUploader
        Location location = new Location("fused");
        location.setLatitude(locationEntity.latitude);
        location.setLongitude(locationEntity.longitude);
        location.setTime(locationEntity.timestamp);
        location.setAccuracy(locationEntity.accuracy);
        if (locationEntity.provider != null) {
            location.setProvider(locationEntity.provider);
        }
        
        locationUploader.uploadCurrentLocation(currentPatientId, location, new LocationUploader.UploadCallback() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "Location uploaded successfully via LocationUploader");
                lastUploadTime = System.currentTimeMillis();
                
                // Reset retry delay on success
                currentRetryDelay = INITIAL_RETRY_DELAY_MS;
                consecutiveFailures = 0;
            }
            
            @Override
            public void onError(String error) {
                Log.e(TAG, "Failed to upload location via LocationUploader: " + error);
                handleUploadFailure(locationEntity);
            }
        });
    }
    

    
    /**
     * Handle upload failure with exponential backoff retry
     */
    private void handleUploadFailure(LocationEntity locationEntity) {
        consecutiveFailures++;
        
        Log.w(TAG, "Upload failed, scheduling retry #" + consecutiveFailures + " in " + currentRetryDelay + "ms");
        
        // Schedule retry with exponential backoff
        mainHandler.postDelayed(() -> {
            Log.d(TAG, "Retrying location upload after " + currentRetryDelay + "ms delay");
            uploadLocationToFirebase(locationEntity);
        }, currentRetryDelay);
        
        // Increase delay for next retry (exponential backoff)
        currentRetryDelay = Math.min(currentRetryDelay * 2, MAX_RETRY_DELAY_MS);
    }
    
    /**
     * Remove current location from Firebase (for privacy when stopping tracking)
     */
    private void removeLocationFromFirebase() {
        if (currentPatientId == null) return;
        
        locationUploader.clearLocationOnStop(currentPatientId, new LocationUploader.UploadCallback() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "Location cleared from Firebase successfully");
            }
            
            @Override
            public void onError(String error) {
                Log.w(TAG, "Failed to clear location from Firebase: " + error);
            }
        });
    }
    
    /**
     * Check if location sharing is enabled in user preferences
     */
    private boolean isLocationSharingEnabled() {
        return prefs.getBoolean("location_sharing_enabled", false);
    }
    
    /**
     * Check if app has required location permissions
     */
    private boolean hasLocationPermissions() {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }
    
    /**
     * Get appropriate foreground service type for location tracking
     */
    private int getLocationForegroundServiceType() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION;
        }
        return 0;
    }
    
    /**
     * Create notification channel for Android O+
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Location Tracking",
                    NotificationManager.IMPORTANCE_LOW // Low importance to minimize interruption
            );
            channel.setDescription("Continuous location tracking for caregiver safety");
            channel.setShowBadge(false);
            channel.enableVibration(false);
            channel.setSound(null, null); // Silent notifications
            
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }
    
    /**
     * Create initial tracking notification
     */
    private Notification createTrackingNotification() {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, 
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Location Sharing Active")
                .setContentText("Sharing location securely with your caregiver")
                .setSmallIcon(R.drawable.ic_notification) // You'll need to add this icon
                .setContentIntent(pendingIntent)
                .setOngoing(true) // Cannot be dismissed by user
                .setSilent(true) // No sound/vibration
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .addAction(R.drawable.ic_notification, "Stop Sharing", createStopTrackingPendingIntent())
                .build();
    }
    
    /**
     * Update tracking notification with current location info
     */
    private void updateTrackingNotification(Location location) {
        String locationText = String.format(Locale.getDefault(), 
                "Last update: %s (±%.0fm)", 
                new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date()),
                location.getAccuracy());
        
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Location Sharing Active")
                .setContentText(locationText)
                .setSmallIcon(R.drawable.ic_notification)
                .setOngoing(true)
                .setSilent(true)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .addAction(R.drawable.ic_notification, "Stop Sharing", createStopTrackingPendingIntent())
                .build();
        
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, notification);
        }
    }
    
    /**
     * Create pending intent to stop tracking from notification action
     */
    private PendingIntent createStopTrackingPendingIntent() {
        Intent stopIntent = new Intent(this, PatientLocationService.class);
        stopIntent.setAction(ACTION_STOP_TRACKING);
        return PendingIntent.getService(this, 0, stopIntent, 
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
    
    /**
     * Reinitialize the location client after device boot
     * This ensures the FusedLocationProviderClient is properly reset
     */
    private void reinitializeLocationClientAfterBoot() {
        Log.d(TAG, "Reinitializing location client after device boot");
        
        // Stop any existing location updates first
        if (fusedLocationClient != null && isTracking) {
            try {
                fusedLocationClient.removeLocationUpdates(locationCallback);
                Log.d(TAG, "Removed existing location updates before reinitializing");
            } catch (Exception e) {
                Log.w(TAG, "Error removing location updates during reinitialization", e);
            }
        }
        
        // Recreate the location client
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        
        // Reset tracking state to ensure clean restart
        isTracking = false;
        
        Log.d(TAG, "Location client reinitialized successfully");
    }
    
    /**
     * Verify that location updates are actually working
     * Called by LocationServiceManager to check post-boot functionality
     */
    private void verifyLocationUpdatesWorking() {
        Log.d(TAG, "Verifying location updates are working...");
        
        if (!isTracking) {
            Log.w(TAG, "Location tracking not active during verification");
            // Restart tracking if it should be active but isn't
            if (isLocationSharingEnabled()) {
                Log.d(TAG, "Restarting location tracking during verification");
                startLocationTracking();
            }
            return;
        }
        
        // Check if we've received a location update recently (within last 5 minutes)
        long lastUpdateTime = prefs.getLong("last_location_update", 0);
        long currentTime = System.currentTimeMillis();
        long timeSinceLastUpdate = currentTime - lastUpdateTime;
        
        if (timeSinceLastUpdate > 300000) { // 5 minutes
            Log.w(TAG, "No location updates received in last 5 minutes (" + timeSinceLastUpdate + "ms), restarting location client");
            
            // Restart location tracking to fix the issue
            stopLocationTracking();
            
            // Wait a moment before restarting
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                startLocationTracking();
            }, 3000); // 3 second delay
        } else {
            Log.d(TAG, "Location updates are working correctly (last update " + timeSinceLastUpdate + "ms ago)");
        }
    }
}