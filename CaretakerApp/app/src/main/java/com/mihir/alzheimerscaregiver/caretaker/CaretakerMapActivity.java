package com.mihir.alzheimerscaregiver.caretaker;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * CaretakerMapActivity - Real-time patient location monitoring
 * 
 * Features:
 * - Real-time location updates from Firebase
 * - Stale location detection and warnings
 * - Camera animation to patient location
 * - Location status indicators
 * - Last update timestamp display
 */
public class CaretakerMapActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final String TAG = "CaretakerMapActivity";
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;
    private static final long STALE_THRESHOLD_MS = 15 * 60 * 1000; // 15 minutes (best practice)
    private static final long MIN_UPDATE_INTERVAL_MS = 5000; // Minimum 5 seconds between updates
    
    // UI Components
    private MapView mapView;
    private GoogleMap googleMap;
    private TextView textPatientName;
    private TextView textLastUpdate;
    private TextView textLocationStatus;
    private LinearLayout layoutStaleWarning;
    private ImageView imageStatusIcon;
    private MaterialButton buttonViewHistory;
    private MaterialButton buttonGeofences;
    
    // Firebase
    private DatabaseReference databaseReference;
    private ValueEventListener locationListener;
    
    // Map data
    private Marker patientMarker;
    private String patientId;
    private String patientName;
    private boolean isFirstLocationUpdate = true;
    private long lastUpdateTime = 0;
    private double lastLatitude = 0;
    private double lastLongitude = 0;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_caretaker_map);
        
        // Get patient info from intent
        patientId = getIntent().getStringExtra("patientId");
        patientName = getIntent().getStringExtra("patientName");
        
        if (patientId == null) {
            Toast.makeText(this, "Patient ID is required", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        // Initialize UI
        initViews();
        setupToolbar();
        
        // Initialize map
        mapView = findViewById(R.id.mapView);
        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(this);
        
        // Initialize Firebase
        databaseReference = FirebaseDatabase.getInstance().getReference();
        
        Log.d(TAG, "CaretakerMapActivity initialized for patient: " + patientId);
    }
    
    /**
     * Initialize UI components
     */
    private void initViews() {
        textPatientName = findViewById(R.id.textPatientName);
        textLastUpdate = findViewById(R.id.textLastUpdate);
        textLocationStatus = findViewById(R.id.textLocationStatus);
        layoutStaleWarning = findViewById(R.id.layoutStaleWarning);
        imageStatusIcon = findViewById(R.id.imageStatusIcon);
        buttonViewHistory = findViewById(R.id.buttonViewHistory);
        buttonGeofences = findViewById(R.id.buttonGeofences);
        
        // Set patient name
        if (patientName != null && !patientName.isEmpty()) {
            textPatientName.setText(patientName + "'s Location");
        } else {
            textPatientName.setText("Patient Location");
        }
        
        // Initial status
        textLocationStatus.setText("Waiting for location data...");
        textLastUpdate.setText("No updates yet");
        layoutStaleWarning.setVisibility(View.GONE);
        
        // Setup button click listeners
        setupButtonListeners();
    }
    
    /**
     * Setup click listeners for buttons
     */
    private void setupButtonListeners() {
        // History button - launch HistoryActivity
        buttonViewHistory.setOnClickListener(v -> {
            Intent intent = new Intent(CaretakerMapActivity.this, HistoryActivity.class);
            intent.putExtra("patientId", patientId);
            if (patientName != null) {
                intent.putExtra("patientName", patientName);
            }
            startActivity(intent);
        });
        
        // Geofences button - manage safe zones
        buttonGeofences.setOnClickListener(v -> {
            showGeofenceManagementDialog();
        });
    }
    
    /**
     * Show geofence management dialog with available actions
     */
    private void showGeofenceManagementDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Safe Zone Management")
               .setMessage("Choose an action for managing patient safety zones:")
               .setPositiveButton("Manage Safe Zones", (dialog, which) -> {
                   // Launch dedicated GeofenceManagementActivity
                   Intent intent = new Intent(CaretakerMapActivity.this, GeofenceManagementActivity.class);
                   intent.putExtra("patientId", patientId);
                   intent.putExtra("patientName", patientName);
                   startActivity(intent);
               })
               .setNeutralButton("Quick View", (dialog, which) -> {
                   // TODO: Show existing geofences on current map as overlay
                   Toast.makeText(this, "Geofence overlay coming soon", Toast.LENGTH_SHORT).show();
               })
               .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
               .show();
    }
    
    /**
     * Setup toolbar with navigation
     */
    private void setupToolbar() {
        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
            getSupportActionBar().setTitle("Patient Location");
        }
        
        toolbar.setNavigationOnClickListener(v -> onBackPressed());
    }
    
    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        this.googleMap = googleMap;
        
        // Configure map
        googleMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
        googleMap.getUiSettings().setZoomControlsEnabled(true);
        googleMap.getUiSettings().setMyLocationButtonEnabled(true);
        googleMap.getUiSettings().setCompassEnabled(true);
        
        // Check location permissions
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            googleMap.setMyLocationEnabled(true);
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
        }
        
        // Start listening for patient location updates
        startLocationListener();
        
        Log.d(TAG, "Google Map ready and configured");
    }
    
    /**
     * Start listening for real-time location updates from Firebase
     */
    private void startLocationListener() {
        if (databaseReference == null || patientId == null) {
            Log.e(TAG, "Database reference or patient ID is null");
            return;
        }
        
        DatabaseReference locationRef = databaseReference
                .child("locations")
                .child(patientId);
        
        locationListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    try {
                        Double latitude = dataSnapshot.child("latitude").getValue(Double.class);
                        Double longitude = dataSnapshot.child("longitude").getValue(Double.class);
                        Long timestamp = dataSnapshot.child("timestamp").getValue(Long.class);
                        
                        if (latitude != null && longitude != null && timestamp != null) {
                            updatePatientLocation(latitude, longitude, timestamp);
                        } else {
                            Log.w(TAG, "Incomplete location data received");
                            updateLocationStatus("Incomplete location data", false);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing location data", e);
                        updateLocationStatus("Error parsing location data", false);
                    }
                } else {
                    Log.d(TAG, "No location data found for patient: " + patientId);
                    updateLocationStatus("No location data available", false);
                }
            }
            
            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e(TAG, "Failed to read location data", databaseError.toException());
                updateLocationStatus("Failed to load location data", false);
            }
        };
        
        locationRef.addValueEventListener(locationListener);
        Log.d(TAG, "Started listening for location updates for patient: " + patientId);
    }
    
    /**
     * Update patient location on map with stale detection and throttling
     */
    private void updatePatientLocation(double latitude, double longitude, long timestamp) {
        if (googleMap == null) {
            Log.w(TAG, "Google Map is not ready");
            return;
        }
        
        // Throttle updates - only update if enough time has passed and location has changed significantly
        long currentTime = System.currentTimeMillis();
        boolean hasLocationChanged = (Math.abs(latitude - lastLatitude) > 0.0001 || Math.abs(longitude - lastLongitude) > 0.0001);
        boolean enoughTimePassed = (currentTime - lastUpdateTime) > MIN_UPDATE_INTERVAL_MS;
        
        if (!isFirstLocationUpdate && !hasLocationChanged && !enoughTimePassed) {
            Log.d(TAG, "Throttling location update");
            return;
        }
        
        LatLng patientLocation = new LatLng(latitude, longitude);
        boolean isStale = (currentTime - timestamp) > STALE_THRESHOLD_MS;
        
        // Update only if marker position changed or marker doesn't exist
        if (patientMarker == null || hasLocationChanged) {
            // Remove existing marker
            if (patientMarker != null) {
                patientMarker.remove();
            }
        
        // Create new marker
        MarkerOptions markerOptions = new MarkerOptions()
                .position(patientLocation)
                .title(patientName != null ? patientName : "Patient")
                .snippet("Last updated: " + formatTimestamp(timestamp));
        
        // Set marker color based on staleness
        if (isStale) {
            markerOptions.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED));
        } else {
            markerOptions.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN));
        }
        
            patientMarker = googleMap.addMarker(markerOptions);
            
            // Animate camera on first update only
            if (isFirstLocationUpdate) {
                googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(patientLocation, 15f));
                isFirstLocationUpdate = false;
            }
        }
        
        // Update tracking variables
        lastUpdateTime = currentTime;
        lastLatitude = latitude;
        lastLongitude = longitude;
        
        // Update UI (throttled)
        if (enoughTimePassed || isFirstLocationUpdate) {
            updateLocationStatus("Location updated", !isStale);
            updateLastUpdateTime(timestamp);
            showStaleWarning(isStale, timestamp);
        }
        
        Log.d(TAG, String.format("Updated patient location: %.6f, %.6f (stale: %s)", 
                latitude, longitude, isStale));
    }
    
    /**
     * Update location status text and icon
     */
    private void updateLocationStatus(String status, boolean isActive) {
        if (textLocationStatus != null) {
            textLocationStatus.setText(status);
        }
        
        if (imageStatusIcon != null) {
            if (isActive) {
                imageStatusIcon.setColorFilter(ContextCompat.getColor(this, 
                        com.mihir.alzheimerscaregiver.caretaker.R.color.success));
            } else {
                imageStatusIcon.setColorFilter(ContextCompat.getColor(this, 
                        com.mihir.alzheimerscaregiver.caretaker.R.color.warning));
            }
        }
    }
    
    /**
     * Update last update time display
     */
    private void updateLastUpdateTime(long timestamp) {
        if (textLastUpdate != null) {
            String timeText = "Last update: " + formatTimestamp(timestamp);
            textLastUpdate.setText(timeText);
        }
    }
    
    /**
     * Show or hide stale location warning
     */
    private void showStaleWarning(boolean isStale, long timestamp) {
        if (layoutStaleWarning == null) return;
        
        if (isStale) {
            layoutStaleWarning.setVisibility(View.VISIBLE);
            long minutesStale = (System.currentTimeMillis() - timestamp) / (60 * 1000);
            TextView warningText = layoutStaleWarning.findViewById(R.id.textStaleWarning);
            if (warningText != null) {
                warningText.setText(String.format("Location data is %d minutes old", minutesStale));
            }
        } else {
            layoutStaleWarning.setVisibility(View.GONE);
        }
    }
    
    /**
     * Format timestamp for display
     */
    private String formatTimestamp(long timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, HH:mm:ss", Locale.getDefault());
        return sdf.format(new Date(timestamp));
    }
    
    /**
     * Center map on patient location
     */
    public void centerOnPatient(View view) {
        if (googleMap != null && patientMarker != null) {
            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(
                    patientMarker.getPosition(), 15f));
        }
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, 
            @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (googleMap != null) {
                    try {
                        googleMap.setMyLocationEnabled(true);
                    } catch (SecurityException e) {
                        Log.e(TAG, "Location permission error", e);
                    }
                }
            } else {
                Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        if (mapView != null) {
            mapView.onResume();
        }
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        if (mapView != null) {
            mapView.onPause();
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        // Clean up location listener
        if (locationListener != null && databaseReference != null && patientId != null) {
            databaseReference.child("locations").child(patientId)
                    .removeEventListener(locationListener);
        }
        
        if (mapView != null) {
            mapView.onDestroy();
        }
        
        Log.d(TAG, "CaretakerMapActivity destroyed");
    }
    
    @Override
    public void onLowMemory() {
        super.onLowMemory();
        if (mapView != null) {
            mapView.onLowMemory();
        }
    }
}