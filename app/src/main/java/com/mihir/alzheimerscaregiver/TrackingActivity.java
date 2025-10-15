package com.mihir.alzheimerscaregiver;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.mihir.alzheimerscaregiver.location.LocationServiceManager;
import com.mihir.alzheimerscaregiver.location.LocationUploader;

/**
 * Activity for managing patient location tracking settings
 * Handles permission requests, location sharing toggle, and configuration
 */
public class TrackingActivity extends AppCompatActivity {
    
    private static final String TAG = "TrackingActivity";
    private static final int REQUEST_LOCATION_PERMISSIONS = 1001;
    private static final int REQUEST_BACKGROUND_LOCATION = 1002;
    
    // UI Components
    private SwitchMaterial switchLocationSharing;
    private TextView textLocationStatus;
    private TextView textCurrentInterval;
    private MaterialButton buttonRequestPermissions;
    private MaterialButton buttonChangeInterval;
    private MaterialButton buttonRequestLocation;
    private MaterialButton buttonDebugFirebase;
    private LinearLayout cardPermissions;
    private LinearLayout layoutStatusIndicator;
    private ImageView imageStatusIcon;
    private TextView textStatusMessage;
    
    // Services
    private LocationServiceManager locationManager;
    private LocationUploader locationUploader;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tracking);
        
        // Initialize services
        locationManager = new LocationServiceManager(this);
        locationUploader = new LocationUploader();
        
        // Initialize UI
        initViews();
        setupEventListeners();
        
        // Check permissions and update UI
        updateUI();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        updateUI();
    }
    
    /**
     * Initialize UI components
     */
    private void initViews() {
        switchLocationSharing = findViewById(R.id.switchLocationSharing);
        textLocationStatus = findViewById(R.id.textLocationStatus);
        textCurrentInterval = findViewById(R.id.textCurrentInterval);
        buttonRequestPermissions = findViewById(R.id.buttonRequestPermissions);
        buttonChangeInterval = findViewById(R.id.buttonChangeInterval);
        buttonRequestLocation = findViewById(R.id.buttonRequestLocation);
        buttonDebugFirebase = findViewById(R.id.buttonDebugFirebase);
        cardPermissions = findViewById(R.id.cardPermissions);
        layoutStatusIndicator = findViewById(R.id.layoutStatusIndicator);
        imageStatusIcon = findViewById(R.id.imageStatusIcon);
        textStatusMessage = findViewById(R.id.textStatusMessage);
        
        // Set current interval
        textCurrentInterval.setText(locationManager.getCurrentIntervalLabel());
    }
    
    /**
     * Setup event listeners for UI components
     */
    private void setupEventListeners() {
        // Location sharing toggle
        switchLocationSharing.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!buttonView.isPressed()) return; // Ignore programmatic changes
            
            if (isChecked) {
                startLocationSharing();
            } else {
                stopLocationSharing();
            }
        });
        
        // Permission request button
        buttonRequestPermissions.setOnClickListener(v -> requestLocationPermissions());
        
        // Change interval button
        buttonChangeInterval.setOnClickListener(v -> showIntervalSelectionDialog());
        
        // Request current location button
        buttonRequestLocation.setOnClickListener(v -> requestCurrentLocation());
        
        // Debug Firebase button
        buttonDebugFirebase.setOnClickListener(v -> debugFirebaseConnection());
    }
    
    /**
     * Update UI based on current state
     */
    private void updateUI() {
        boolean hasPermissions = hasLocationPermissions();
        boolean hasBackgroundPermission = hasBackgroundLocationPermission();
        
        // Get local sharing state for now, Firebase will sync async
        boolean isSharing = locationManager.isLocationSharingEnabled();
        
        // Also check Firebase async (this will update UI when loaded)
        checkFirebaseSharingStateAsync();
        
        // Update permission card visibility
        cardPermissions.setVisibility(hasPermissions && hasBackgroundPermission ? View.GONE : View.VISIBLE);
        
        // Update switch state
        switchLocationSharing.setChecked(isSharing);
        switchLocationSharing.setEnabled(hasPermissions && hasBackgroundPermission);
        
        // Update status text
        if (!hasPermissions) {
            textLocationStatus.setText("Permissions required");
            textLocationStatus.setTextColor(ContextCompat.getColor(this, R.color.error));
        } else if (!hasBackgroundPermission) {
            textLocationStatus.setText("Background permission required");
            textLocationStatus.setTextColor(ContextCompat.getColor(this, R.color.warning));
        } else if (isSharing) {
            textLocationStatus.setText("Currently active");
            textLocationStatus.setTextColor(ContextCompat.getColor(this, R.color.success));
        } else {
            textLocationStatus.setText("Currently disabled");
            textLocationStatus.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        }
        
        // Update status indicator
        updateStatusIndicator(isSharing, hasPermissions && hasBackgroundPermission);
        
        // Update button states
        buttonRequestLocation.setEnabled(hasPermissions);
        buttonChangeInterval.setEnabled(hasPermissions);
    }
    
    /**
     * Update status indicator appearance
     */
    private void updateStatusIndicator(boolean isSharing, boolean hasPermissions) {
        if (isSharing && hasPermissions) {
            layoutStatusIndicator.setVisibility(View.VISIBLE);
            layoutStatusIndicator.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.success_light));
            imageStatusIcon.setImageTintList(ContextCompat.getColorStateList(this, R.color.success));
            textStatusMessage.setText("Location sharing active");
            textStatusMessage.setTextColor(ContextCompat.getColor(this, R.color.success));
        } else if (!hasPermissions) {
            layoutStatusIndicator.setVisibility(View.VISIBLE);
            layoutStatusIndicator.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.error_light));
            imageStatusIcon.setImageTintList(ContextCompat.getColorStateList(this, R.color.error));
            textStatusMessage.setText("Permissions required");
            textStatusMessage.setTextColor(ContextCompat.getColor(this, R.color.error));
        } else {
            layoutStatusIndicator.setVisibility(View.GONE);
        }
    }
    
    /**
     * Start location sharing with proper validation
     */
    private void startLocationSharing() {
        if (!hasLocationPermissions()) {
            showPermissionRationaleDialog();
            switchLocationSharing.setChecked(false);
            return;
        }
        
        if (!hasBackgroundLocationPermission()) {
            showBackgroundPermissionRationaleDialog();
            switchLocationSharing.setChecked(false);
            return;
        }
        
        // Update Firebase sharing state
        String patientId = locationUploader.getCurrentPatientId();
        if (patientId == null) {
            Toast.makeText(this, "Please sign in to enable location sharing", Toast.LENGTH_SHORT).show();
            switchLocationSharing.setChecked(false);
            return;
        }
        
        locationUploader.updateSharingEnabled(patientId, true, new LocationUploader.UploadCallback() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "Firebase sharing state updated to enabled");
                // Start the service
                locationManager.startTracking();
                runOnUiThread(() -> {
                    updateUI();
                    Toast.makeText(TrackingActivity.this, "Location sharing enabled", Toast.LENGTH_SHORT).show();
                });
            }
            
            @Override
            public void onError(String error) {
                Log.e(TAG, "Failed to update Firebase sharing state: " + error);
                runOnUiThread(() -> {
                    switchLocationSharing.setChecked(false);
                    Toast.makeText(TrackingActivity.this, "Failed to enable sharing: " + error, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }
    
    /**
     * Stop location sharing
     */
    private void stopLocationSharing() {
        String patientId = locationUploader.getCurrentPatientId();
        if (patientId != null) {
            // Update Firebase sharing state
            locationUploader.updateSharingEnabled(patientId, false, new LocationUploader.UploadCallback() {
                @Override
                public void onSuccess() {
                    Log.d(TAG, "Firebase sharing state updated to disabled");
                    // Clear current location for privacy
                    locationUploader.clearLocationOnStop(patientId, new LocationUploader.UploadCallback() {
                        @Override
                        public void onSuccess() {
                            Log.d(TAG, "Current location cleared");
                        }
                        
                        @Override
                        public void onError(String error) {
                            Log.w(TAG, "Failed to clear current location: " + error);
                        }
                    });
                }
                
                @Override
                public void onError(String error) {
                    Log.e(TAG, "Failed to update Firebase sharing state: " + error);
                }
            });
        }
        
        // Stop the service
        locationManager.stopTracking();
        
        updateUI();
        Toast.makeText(this, "Location sharing disabled", Toast.LENGTH_SHORT).show();
    }
    
    /**
     * Request location permissions with proper rationale
     */
    private void requestLocationPermissions() {
        if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
            showPermissionRationaleDialog();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                    },
                    REQUEST_LOCATION_PERMISSIONS);
        }
    }
    
    /**
     * Request background location permission (Android 10+)
     */
    private void requestBackgroundLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
                showBackgroundPermissionRationaleDialog();
            } else {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION},
                        REQUEST_BACKGROUND_LOCATION);
            }
        }
    }
    
    /**
     * Show permission rationale dialog
     */
    private void showPermissionRationaleDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Location Permission Required")
                .setMessage("This app needs location access to share your location with your caregiver for safety purposes. Your location data is encrypted and only shared with your designated caregiver.")
                .setPositiveButton("Grant Permission", (dialog, which) -> {
                    ActivityCompat.requestPermissions(TrackingActivity.this,
                            new String[]{
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                            },
                            REQUEST_LOCATION_PERMISSIONS);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
    
    /**
     * Show background location permission rationale dialog
     */
    private void showBackgroundPermissionRationaleDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Background Location Required")
                .setMessage("To ensure continuous location sharing for your safety, this app needs permission to access location even when the app is closed. Please select 'Allow all the time' in the next screen.")
                .setPositiveButton("Continue", (dialog, which) -> {
                    requestBackgroundLocationPermission();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
    
    /**
     * Show interval selection dialog
     */
    private void showIntervalSelectionDialog() {
        LocationServiceManager.IntervalOption[] options = LocationServiceManager.getIntervalOptions();
        String[] labels = new String[options.length];
        
        for (int i = 0; i < options.length; i++) {
            labels[i] = options[i].label;
        }
        
        int currentSelection = getCurrentIntervalIndex(options);
        
        new MaterialAlertDialogBuilder(this)
                .setTitle("Select Update Interval")
                .setSingleChoiceItems(labels, currentSelection, (dialog, which) -> {
                    LocationServiceManager.IntervalOption selected = options[which];
                    locationManager.setLocationInterval(selected.milliseconds);
                    textCurrentInterval.setText(selected.label);
                    
                    // Restart service if currently running to apply new interval
                    if (locationManager.isLocationSharingEnabled()) {
                        locationManager.stopTracking();
                        locationManager.startTracking();
                    }
                    
                    dialog.dismiss();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
    
    /**
     * Get current interval index for selection dialog
     */
    private int getCurrentIntervalIndex(LocationServiceManager.IntervalOption[] options) {
        long current = locationManager.getLocationInterval();
        for (int i = 0; i < options.length; i++) {
            if (options[i].milliseconds == current) {
                return i;
            }
        }
        return 1; // Default to 5 minutes
    }
    
    /**
     * Request current location immediately
     */
    private void requestCurrentLocation() {
        if (!hasLocationPermissions()) {
            Toast.makeText(this, "Location permissions required", Toast.LENGTH_SHORT).show();
            return;
        }
        
        locationManager.requestCurrentLocationNow();
        Toast.makeText(this, "Requesting current location...", Toast.LENGTH_SHORT).show();
    }
    
    /**
     * Check if basic location permissions are granted
     */
    private boolean hasLocationPermissions() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }
    
    /**
     * Check if background location permission is granted (Android 10+)
     */
    private boolean hasBackgroundLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED;
        }
        return true; // Not required on older versions
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        switch (requestCode) {
            case REQUEST_LOCATION_PERMISSIONS:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "Location permissions granted");
                    
                    // Request background permission if needed
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !hasBackgroundLocationPermission()) {
                        requestBackgroundLocationPermission();
                    } else {
                        updateUI();
                        Toast.makeText(this, "Location permissions granted", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Log.d(TAG, "Location permissions denied");
                    showPermissionDeniedDialog();
                }
                break;
                
            case REQUEST_BACKGROUND_LOCATION:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "Background location permission granted");
                    updateUI();
                    Toast.makeText(this, "Background location permission granted", Toast.LENGTH_SHORT).show();
                } else {
                    Log.d(TAG, "Background location permission denied");
                    showBackgroundPermissionDeniedDialog();
                }
                break;
        }
    }
    
    /**
     * Show dialog when permissions are denied
     */
    private void showPermissionDeniedDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Permission Required")
                .setMessage("Location permissions are essential for sharing your location with your caregiver. Without these permissions, the safety features cannot work.")
                .setPositiveButton("Settings", (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    intent.setData(Uri.fromParts("package", getPackageName(), null));
                    startActivity(intent);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
    
    /**
     * Show dialog when background permission is denied
     */
    private void showBackgroundPermissionDeniedDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Background Location Required")
                .setMessage("Background location access is needed to ensure continuous location sharing for your safety, even when the app is not actively being used.")
                .setPositiveButton("Settings", (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    intent.setData(Uri.fromParts("package", getPackageName(), null));
                    startActivity(intent);
                })
                .setNegativeButton("Continue without", (dialog, which) -> updateUI())
                .show();
    }
    
    /**
     * Check Firebase sharing state asynchronously and update UI
     */
    private void checkFirebaseSharingStateAsync() {
        LocationUploader uploader = new LocationUploader();
        String patientId = locationManager.getCurrentPatientId();
        
        if (patientId != null) {
            uploader.getSharingEnabled(patientId, new LocationUploader.SharingStateCallback() {
                @Override
                public void onSharingState(boolean enabled) {
                    runOnUiThread(() -> {
                        // Update local preference to match Firebase
                        boolean currentLocal = locationManager.isLocationSharingEnabled();
                        if (currentLocal != enabled) {
                            // Update local state to match Firebase without triggering Firebase update
                            locationManager.setLocationSharingEnabledLocally(enabled);
                            // Refresh UI
                            updateUIWithState(enabled);
                        }
                    });
                }
                
                @Override
                public void onError(String error) {
                    Log.w(TAG, "Failed to get Firebase sharing state: " + error);
                }
            });
        }
    }
    
    /**
     * Update UI with specific sharing state
     */
    private void updateUIWithState(boolean isSharing) {
        boolean hasPermissions = hasLocationPermissions();
        boolean hasBackgroundPermission = hasBackgroundLocationPermission();
        
        // Update switch state
        switchLocationSharing.setChecked(isSharing);
        switchLocationSharing.setEnabled(hasPermissions && hasBackgroundPermission);
        
        // Update status text
        if (!hasPermissions) {
            textLocationStatus.setText("Permissions required");
            textLocationStatus.setTextColor(ContextCompat.getColor(this, R.color.error));
        } else if (!hasBackgroundPermission) {
            textLocationStatus.setText("Background permission required");
            textLocationStatus.setTextColor(ContextCompat.getColor(this, R.color.warning));
        } else if (isSharing) {
            textLocationStatus.setText("Currently active");
            textLocationStatus.setTextColor(ContextCompat.getColor(this, R.color.success));
        } else {
            textLocationStatus.setText("Currently disabled");
            textLocationStatus.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        }
        
        // Update status indicator
        updateStatusIndicator(isSharing, hasPermissions && hasBackgroundPermission);
    }
    
    /**
     * Debug Firebase connection and location service configuration
     */
    private void debugFirebaseConnection() {
        Log.d(TAG, "=== COMPREHENSIVE DEBUG TEST ===");
        
        // Show enhanced debug dialog
        StringBuilder debugInfo = new StringBuilder();
        debugInfo.append("🔧 Running comprehensive tests...\n\n");
        
        // Check current configuration
        debugInfo.append("📋 Current Configuration:\n");
        debugInfo.append("• Interval: ").append(locationManager.getCurrentIntervalLabel()).append("\n");
        debugInfo.append("• Sharing: ").append(locationManager.isLocationSharingEnabled() ? "Enabled" : "Disabled").append("\n");
        debugInfo.append("• Permissions: ").append(hasLocationPermissions() ? "✅" : "❌").append("\n");
        debugInfo.append("• Background: ").append(hasBackgroundLocationPermission() ? "✅" : "❌").append("\n\n");
        debugInfo.append("Check logcat for detailed results...");
        
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle("Firebase & Location Debug");
        builder.setMessage(debugInfo.toString());
        builder.setPositiveButton("OK", null);
        builder.show();
        
        // Run debug tests
        new Thread(() -> {
            try {
                Log.d(TAG, "=== CONFIGURATION DEBUG ===");
                Log.d(TAG, "Update Interval: " + locationManager.getLocationInterval() + "ms (" + locationManager.getCurrentIntervalLabel() + ")");
                Log.d(TAG, "Location Sharing: " + (locationManager.isLocationSharingEnabled() ? "ENABLED" : "DISABLED"));
                Log.d(TAG, "Smallest Displacement: " + locationManager.getSmallestDisplacement() + "m");
                
                // Test 1: Check Firebase Auth
                if (locationUploader.isAuthenticated()) {
                    Log.d(TAG, "✅ Firebase Auth: User is authenticated");
                    String patientId = locationUploader.getCurrentPatientId();
                    Log.d(TAG, "Patient ID: " + patientId);
                } else {
                    Log.e(TAG, "❌ Firebase Auth: User is NOT authenticated!");
                    return;
                }
                
                // Test 2: Test location upload
                String patientId = locationUploader.getCurrentPatientId();
                android.location.Location testLocation = new android.location.Location("debug");
                testLocation.setLatitude(37.7749); // San Francisco
                testLocation.setLongitude(-122.4194);
                testLocation.setAccuracy(10.0f);
                testLocation.setTime(System.currentTimeMillis());
                
                locationUploader.uploadCurrentLocation(patientId, testLocation, new LocationUploader.UploadCallback() {
                    @Override
                    public void onSuccess() {
                        Log.d(TAG, "✅ Location Upload: SUCCESS! Location data uploaded to Firebase");
                        runOnUiThread(() -> {
                            Toast.makeText(TrackingActivity.this, 
                                "✅ Firebase test successful! Check logcat for details.", 
                                Toast.LENGTH_LONG).show();
                        });
                    }
                    
                    @Override
                    public void onError(String error) {
                        Log.e(TAG, "❌ Location Upload: FAILED - " + error);
                        runOnUiThread(() -> {
                            Toast.makeText(TrackingActivity.this, 
                                "❌ Firebase test failed: " + error, 
                                Toast.LENGTH_LONG).show();
                        });
                    }
                });
                
                // Test 3: Test sharing state
                locationUploader.getSharingEnabled(patientId, new LocationUploader.SharingStateCallback() {
                    @Override
                    public void onSharingState(boolean enabled) {
                        Log.d(TAG, "✅ Sharing State: Read successful - " + enabled);
                    }
                    
                    @Override
                    public void onError(String error) {
                        Log.e(TAG, "❌ Sharing State: Read failed - " + error);
                    }
                });
                
            } catch (Exception e) {
                Log.e(TAG, "❌ Debug test exception: " + e.getMessage(), e);
                runOnUiThread(() -> {
                    Toast.makeText(this, "Debug test error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }
}