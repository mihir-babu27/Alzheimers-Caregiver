package com.mihir.alzheimerscaregiver.caretaker;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.slider.Slider;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.mihir.alzheimerscaregiver.caretaker.R;
import com.mihir.alzheimerscaregiver.entities.GeofenceDefinition;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * GeofenceManagementActivity - Manage geofences for patient safety zones
 * 
 * Features:
 * - Display patient location and existing geofences on map
 * - Long press to create new geofences
 * - Click existing geofences to edit/delete
 * - Real-time sync with Firebase
 * - Visual geofence representation with circles and labels
 */
public class GeofenceManagementActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final String TAG = "GeofenceManagement";
    private static final float DEFAULT_ZOOM = 15.0f;
    private static final LatLng DEFAULT_LOCATION = new LatLng(12.9716, 77.5946); // Bangalore

    // UI Components
    private MapView mapView;
    private GoogleMap googleMap;
    private FloatingActionButton fabAddGeofence;
    private MaterialButton buttonViewList;
    private MaterialButton buttonPatientLocation;

    // Data
    private String patientId;
    private String patientName;
    private String currentCaretakerId;
    
    // Firebase
    private DatabaseReference geofencesRef;
    private ValueEventListener geofenceListener;
    
    // Map objects
    private Map<String, Circle> geofenceCircles;
    private Map<String, Marker> geofenceMarkers;
    private Map<String, GeofenceDefinition> geofenceDefinitions;
    
    // State
    private boolean isCreatingGeofence = false;
    private LatLng selectedLocation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_geofence_management);

        // Get intent data
        patientId = getIntent().getStringExtra("patientId");
        patientName = getIntent().getStringExtra("patientName");
        currentCaretakerId = "caretaker_" + System.currentTimeMillis(); // TODO: Get from auth

        if (patientId == null) {
            Toast.makeText(this, "Patient ID is required", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Initialize data structures
        geofenceCircles = new HashMap<>();
        geofenceMarkers = new HashMap<>();
        geofenceDefinitions = new HashMap<>();

        // Initialize UI
        initViews();
        setupToolbar();
        setupEventListeners();

        // Initialize map
        mapView = findViewById(R.id.mapView);
        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(this);

        // Initialize Firebase
        setupFirebase();

        Log.d(TAG, "GeofenceManagementActivity initialized for patient: " + patientId);
    }

    /**
     * Initialize UI components
     */
    private void initViews() {
        fabAddGeofence = findViewById(R.id.fabAddGeofence);
        buttonViewList = findViewById(R.id.buttonViewList);
        buttonPatientLocation = findViewById(R.id.buttonPatientLocation);
    }

    /**
     * Setup toolbar
     */
    private void setupToolbar() {
        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
            
            String title = patientName != null ? 
                patientName + "'s Safe Zones" : 
                "Safe Zone Management";
            getSupportActionBar().setTitle(title);
        }
        
        toolbar.setNavigationOnClickListener(v -> onBackPressed());
    }

    /**
     * Setup event listeners
     */
    private void setupEventListeners() {
        // Add geofence FAB
        fabAddGeofence.setOnClickListener(v -> toggleGeofenceCreationMode());
        
        // View list button
        buttonViewList.setOnClickListener(v -> showGeofenceListDialog());
        
        // Patient location button
        buttonPatientLocation.setOnClickListener(v -> navigateToPatientLocation());
    }

    /**
     * Setup Firebase listeners
     */
    private void setupFirebase() {
        geofencesRef = FirebaseDatabase.getInstance()
                .getReference("geofences")
                .child(patientId);

        geofenceListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                loadGeofencesFromSnapshot(snapshot);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to load geofences", error.toException());
                Toast.makeText(GeofenceManagementActivity.this, 
                    "Failed to load geofences", Toast.LENGTH_SHORT).show();
            }
        };

        geofencesRef.addValueEventListener(geofenceListener);
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        this.googleMap = googleMap;
        
        // Configure map
        googleMap.getUiSettings().setZoomControlsEnabled(true);
        googleMap.getUiSettings().setCompassEnabled(true);
        googleMap.getUiSettings().setMyLocationButtonEnabled(false);

        // Set default location
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(DEFAULT_LOCATION, DEFAULT_ZOOM));

        // Setup map click listeners
        setupMapListeners();

        Log.d(TAG, "Map ready");
    }

    /**
     * Setup map click listeners
     */
    private void setupMapListeners() {
        // Long press to create geofence
        googleMap.setOnMapLongClickListener(latLng -> {
            if (isCreatingGeofence) {
                selectedLocation = latLng;
                showCreateGeofenceDialog(latLng);
            } else {
                // Enable geofence creation mode
                toggleGeofenceCreationMode();
                Toast.makeText(this, "Tap and hold again to create a safe zone", 
                    Toast.LENGTH_LONG).show();
            }
        });

        // Click on existing geofence markers
        googleMap.setOnMarkerClickListener(marker -> {
            String geofenceId = (String) marker.getTag();
            if (geofenceId != null) {
                showGeofenceDetailsDialog(geofenceId);
                return true;
            }
            return false;
        });

        // Regular map click to cancel geofence creation
        googleMap.setOnMapClickListener(latLng -> {
            if (isCreatingGeofence) {
                toggleGeofenceCreationMode();
            }
        });
    }

    /**
     * Toggle geofence creation mode
     */
    private void toggleGeofenceCreationMode() {
        isCreatingGeofence = !isCreatingGeofence;
        
        if (isCreatingGeofence) {
            fabAddGeofence.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
            Toast.makeText(this, "Long press on map to create safe zone", Toast.LENGTH_LONG).show();
        } else {
            fabAddGeofence.setImageResource(android.R.drawable.ic_input_add);
        }
    }

    /**
     * Show create geofence dialog
     */
    private void showCreateGeofenceDialog(LatLng location) {
        View dialogView = LayoutInflater.from(this)
                .inflate(R.layout.dialog_create_geofence, null);

        TextInputEditText editName = dialogView.findViewById(R.id.editTextZoneName);
        TextInputEditText editDescription = dialogView.findViewById(R.id.editTextZoneDescription);
        Slider sliderRadius = dialogView.findViewById(R.id.sliderRadius);
        TextView textRadius = dialogView.findViewById(R.id.textRadiusValue);
        TextView textLocation = dialogView.findViewById(R.id.textLocationCoords);
        MaterialButton buttonCancel = dialogView.findViewById(R.id.buttonCancel);
        MaterialButton buttonCreate = dialogView.findViewById(R.id.buttonCreateZone);

        // Set location info
        textLocation.setText(String.format("Lat: %.6f, Lng: %.6f", 
                location.latitude, location.longitude));

        // Setup radius slider
        sliderRadius.addOnChangeListener((slider, value, fromUser) -> {
            textRadius.setText((int) value + "m");
        });

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(true)
                .create();

        buttonCancel.setOnClickListener(v -> {
            dialog.dismiss();
            toggleGeofenceCreationMode();
        });

        buttonCreate.setOnClickListener(v -> {
            String name = editName.getText().toString().trim();
            String description = editDescription.getText().toString().trim();
            float radius = sliderRadius.getValue();

            if (name.isEmpty()) {
                editName.setError("Zone name is required");
                return;
            }

            createGeofence(name, description, location, radius);
            dialog.dismiss();
            toggleGeofenceCreationMode();
        });

        dialog.show();
    }

    /**
     * Create new geofence
     */
    private void createGeofence(String name, String description, LatLng location, float radius) {
        String geofenceId = UUID.randomUUID().toString();
        
        GeofenceDefinition geofence = new GeofenceDefinition(
                geofenceId, name, description, 
                location.latitude, location.longitude, 
                radius, currentCaretakerId
        );

        if (!geofence.isValid()) {
            Toast.makeText(this, "Invalid geofence data: " + geofence.getValidationError(), 
                    Toast.LENGTH_SHORT).show();
            return;
        }

        // Save to Firebase
        geofencesRef.child(geofenceId).setValue(geofence.toFirebaseMap())
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Geofence created successfully: " + geofenceId);
                    Toast.makeText(this, "Safe zone '" + name + "' created", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to create geofence", e);
                    Toast.makeText(this, "Failed to create safe zone", Toast.LENGTH_SHORT).show();
                });
    }

    /**
     * Load geofences from Firebase snapshot
     */
    private void loadGeofencesFromSnapshot(DataSnapshot snapshot) {
        // Clear existing geofences from map
        clearGeofencesFromMap();
        geofenceDefinitions.clear();

        for (DataSnapshot child : snapshot.getChildren()) {
            try {
                Map<String, Object> data = (Map<String, Object>) child.getValue();
                if (data != null) {
                    GeofenceDefinition geofence = GeofenceDefinition.fromFirebase(data);
                    if (geofence.isValid() && geofence.active) {
                        geofenceDefinitions.put(geofence.id, geofence);
                        addGeofenceToMap(geofence);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error parsing geofence data", e);
            }
        }

        Log.d(TAG, "Loaded " + geofenceDefinitions.size() + " geofences");
    }

    /**
     * Add geofence to map
     */
    private void addGeofenceToMap(GeofenceDefinition geofence) {
        if (googleMap == null) return;

        LatLng center = new LatLng(geofence.lat, geofence.lng);

        // Add circle
        CircleOptions circleOptions = new CircleOptions()
                .center(center)
                .radius(geofence.radius)
                .strokeColor(Color.parseColor(geofence.color))
                .strokeWidth(3)
                .fillColor(Color.parseColor(geofence.color + "40")); // 25% opacity

        Circle circle = googleMap.addCircle(circleOptions);
        geofenceCircles.put(geofence.id, circle);

        // Add marker
        MarkerOptions markerOptions = new MarkerOptions()
                .position(center)
                .title(geofence.getDisplayLabel())
                .snippet(geofence.getDisplayDescription());

        Marker marker = googleMap.addMarker(markerOptions);
        if (marker != null) {
            marker.setTag(geofence.id);
            geofenceMarkers.put(geofence.id, marker);
        }
    }

    /**
     * Clear all geofences from map
     */
    private void clearGeofencesFromMap() {
        for (Circle circle : geofenceCircles.values()) {
            circle.remove();
        }
        geofenceCircles.clear();

        for (Marker marker : geofenceMarkers.values()) {
            marker.remove();
        }
        geofenceMarkers.clear();
    }

    /**
     * Show geofence details dialog
     */
    private void showGeofenceDetailsDialog(String geofenceId) {
        GeofenceDefinition geofence = geofenceDefinitions.get(geofenceId);
        if (geofence == null) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(geofence.getDisplayLabel())
               .setMessage("Description: " + geofence.getDisplayDescription() + 
                          "\nRadius: " + (int)geofence.radius + "m" +
                          "\nLocation: " + String.format("%.6f, %.6f", geofence.lat, geofence.lng))
               .setPositiveButton("Delete", (dialog, which) -> deleteGeofence(geofenceId))
               .setNeutralButton("Edit", (dialog, which) -> editGeofence(geofenceId))
               .setNegativeButton("Close", null)
               .show();
    }

    /**
     * Delete geofence
     */
    private void deleteGeofence(String geofenceId) {
        GeofenceDefinition geofence = geofenceDefinitions.get(geofenceId);
        if (geofence == null) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Delete Safe Zone")
               .setMessage("Are you sure you want to delete '" + geofence.getDisplayLabel() + "'?")
               .setPositiveButton("Delete", (dialog, which) -> {
                   geofencesRef.child(geofenceId).removeValue()
                           .addOnSuccessListener(aVoid -> {
                               Toast.makeText(this, "Safe zone deleted", Toast.LENGTH_SHORT).show();
                           })
                           .addOnFailureListener(e -> {
                               Toast.makeText(this, "Failed to delete safe zone", Toast.LENGTH_SHORT).show();
                           });
               })
               .setNegativeButton("Cancel", null)
               .show();
    }

    /**
     * Edit geofence (placeholder for future implementation)
     */
    private void editGeofence(String geofenceId) {
        Toast.makeText(this, "Edit functionality coming soon", Toast.LENGTH_SHORT).show();
        // TODO: Implement edit geofence dialog
    }

    /**
     * Show geofence list dialog
     */
    private void showGeofenceListDialog() {
        if (geofenceDefinitions.isEmpty()) {
            Toast.makeText(this, "No safe zones created yet", Toast.LENGTH_SHORT).show();
            return;
        }

        StringBuilder listText = new StringBuilder();
        int index = 1;
        for (GeofenceDefinition geofence : geofenceDefinitions.values()) {
            listText.append(index++).append(". ")
                   .append(geofence.getDisplayLabel())
                   .append(" (").append((int)geofence.radius).append("m)")
                   .append("\n");
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Safe Zones (" + geofenceDefinitions.size() + ")")
               .setMessage(listText.toString().trim())
               .setPositiveButton("OK", null)
               .show();
    }

    /**
     * Navigate to patient location
     */
    private void navigateToPatientLocation() {
        Intent intent = new Intent(this, CaretakerMapActivity.class);
        intent.putExtra("patientId", patientId);
        intent.putExtra("patientName", patientName);
        startActivity(intent);
    }

    // Activity lifecycle methods
    @Override
    protected void onStart() {
        super.onStart();
        mapView.onStart();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mapView.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();
        mapView.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        if (geofenceListener != null && geofencesRef != null) {
            geofencesRef.removeEventListener(geofenceListener);
        }
        
        mapView.onDestroy();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mapView.onLowMemory();
    }
}