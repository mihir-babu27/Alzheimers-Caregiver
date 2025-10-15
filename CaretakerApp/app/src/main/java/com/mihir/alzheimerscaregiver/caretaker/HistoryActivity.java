package com.mihir.alzheimerscaregiver.caretaker;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.android.material.button.MaterialButton;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * HistoryActivity - View patient location history for selected date
 * 
 * Features:
 * - Date picker for selecting history date
 * - Polyline visualization of patient movement
 * - Time labels at key points
 * - Movement statistics
 * - Export/share capabilities
 */
public class HistoryActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final String TAG = "HistoryActivity";
    private static final int DEFAULT_ZOOM = 15;
    
    // UI Components
    private MapView mapView;
    private GoogleMap googleMap;
    private TextView textPatientName;
    private TextView textSelectedDate;
    private TextView textMovementStats;
    private MaterialButton buttonSelectDate;
    private MaterialButton buttonExportData;
    private RecyclerView recyclerTimePoints;
    
    // Firebase
    private DatabaseReference databaseReference;
    
    // Map data
    private Polyline currentPolyline;
    private List<LocationPoint> locationHistory;
    private LocationTimePointsAdapter timePointsAdapter;
    
    // Patient data
    private String patientId;
    private String patientName;
    private String selectedDate;
    private Calendar selectedCalendar;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);
        
        // Get patient info from intent
        patientId = getIntent().getStringExtra("patientId");
        patientName = getIntent().getStringExtra("patientName");
        
        if (patientId == null) {
            Toast.makeText(this, "Patient ID is required", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        // Initialize components
        initViews();
        setupToolbar();
        setupRecyclerView();
        
        // Initialize map
        mapView = findViewById(R.id.mapView);
        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(this);
        
        // Initialize Firebase
        databaseReference = FirebaseDatabase.getInstance().getReference();
        
        // Initialize data
        locationHistory = new ArrayList<>();
        selectedCalendar = Calendar.getInstance();
        selectedDate = formatDate(selectedCalendar.getTime());
        
        // Load today's data by default
        updateSelectedDateDisplay();
        loadLocationHistory();
        
        Log.d(TAG, "HistoryActivity initialized for patient: " + patientId);
    }
    
    /**
     * Initialize UI components
     */
    private void initViews() {
        textPatientName = findViewById(R.id.textPatientName);
        textSelectedDate = findViewById(R.id.textSelectedDate);
        textMovementStats = findViewById(R.id.textMovementStats);
        buttonSelectDate = findViewById(R.id.buttonSelectDate);
        buttonExportData = findViewById(R.id.buttonExportData);
        recyclerTimePoints = findViewById(R.id.recyclerTimePoints);
        
        // Set patient name
        if (patientName != null && !patientName.isEmpty()) {
            textPatientName.setText(patientName + "'s Location History");
        } else {
            textPatientName.setText("Patient Location History");
        }
        
        // Set up click listeners
        buttonSelectDate.setOnClickListener(this::showDatePicker);
        buttonExportData.setOnClickListener(this::exportLocationData);
        
        // Initial stats
        textMovementStats.setText("No location data for selected date");
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
            getSupportActionBar().setTitle("Location History");
        }
        
        toolbar.setNavigationOnClickListener(v -> onBackPressed());
    }
    
    /**
     * Setup recycler view for time points
     */
    private void setupRecyclerView() {
        timePointsAdapter = new LocationTimePointsAdapter();
        recyclerTimePoints.setLayoutManager(new LinearLayoutManager(this));
        recyclerTimePoints.setAdapter(timePointsAdapter);
    }
    
    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        this.googleMap = googleMap;
        
        // Configure map
        googleMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
        googleMap.getUiSettings().setZoomControlsEnabled(true);
        googleMap.getUiSettings().setCompassEnabled(true);
        
        // Load and display current date's history
        displayLocationHistory();
        
        Log.d(TAG, "Google Map ready and configured");
    }
    
    /**
     * Show date picker dialog
     */
    public void showDatePicker(View view) {
        DatePickerDialog datePickerDialog = new DatePickerDialog(
                this,
                (datePicker, year, month, dayOfMonth) -> {
                    selectedCalendar.set(year, month, dayOfMonth);
                    selectedDate = formatDate(selectedCalendar.getTime());
                    updateSelectedDateDisplay();
                    loadLocationHistory();
                },
                selectedCalendar.get(Calendar.YEAR),
                selectedCalendar.get(Calendar.MONTH),
                selectedCalendar.get(Calendar.DAY_OF_MONTH)
        );
        
        // Don't allow future dates
        datePickerDialog.getDatePicker().setMaxDate(System.currentTimeMillis());
        datePickerDialog.show();
    }
    
    /**
     * Update selected date display
     */
    private void updateSelectedDateDisplay() {
        if (textSelectedDate != null) {
            SimpleDateFormat displayFormat = new SimpleDateFormat("EEEE, MMM dd, yyyy", Locale.getDefault());
            textSelectedDate.setText(displayFormat.format(selectedCalendar.getTime()));
        }
    }
    
    /**
     * Load location history for selected date from Firebase
     */
    private void loadLocationHistory() {
        if (databaseReference == null || patientId == null || selectedDate == null) {
            Log.e(TAG, "Required data is null");
            return;
        }
        
        DatabaseReference historyRef = databaseReference
                .child("locationHistory")
                .child(patientId)
                .child(selectedDate);
        
        historyRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                locationHistory.clear();
                
                if (dataSnapshot.exists()) {
                    for (DataSnapshot locationSnapshot : dataSnapshot.getChildren()) {
                        try {
                            Double latitude = locationSnapshot.child("latitude").getValue(Double.class);
                            Double longitude = locationSnapshot.child("longitude").getValue(Double.class);
                            Long timestamp = locationSnapshot.child("timestamp").getValue(Long.class);
                            
                            if (latitude != null && longitude != null && timestamp != null) {
                                LocationPoint point = new LocationPoint(latitude, longitude, timestamp);
                                locationHistory.add(point);
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error parsing location point", e);
                        }
                    }
                    
                    // Sort by timestamp
                    Collections.sort(locationHistory, new Comparator<LocationPoint>() {
                        @Override
                        public int compare(LocationPoint a, LocationPoint b) {
                            return Long.compare(a.timestamp, b.timestamp);
                        }
                    });
                    
                    Log.d(TAG, "Loaded " + locationHistory.size() + " location points for " + selectedDate);
                } else {
                    Log.d(TAG, "No location history found for " + selectedDate);
                }
                
                displayLocationHistory();
                updateMovementStats();
                updateTimePointsList();
            }
            
            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e(TAG, "Failed to load location history", databaseError.toException());
                Toast.makeText(HistoryActivity.this, "Failed to load location history", Toast.LENGTH_SHORT).show();
            }
        });
    }
    
    /**
     * Display location history on map with polyline
     */
    private void displayLocationHistory() {
        if (googleMap == null) {
            return;
        }
        
        // Clear existing polyline and markers
        if (currentPolyline != null) {
            currentPolyline.remove();
        }
        googleMap.clear();
        
        if (locationHistory.isEmpty()) {
            return;
        }
        
        // Create polyline points
        List<LatLng> polylinePoints = new ArrayList<>();
        LatLngBounds.Builder boundsBuilder = new LatLngBounds.Builder();
        
        for (LocationPoint point : locationHistory) {
            LatLng latLng = new LatLng(point.latitude, point.longitude);
            polylinePoints.add(latLng);
            boundsBuilder.include(latLng);
        }
        
        // Draw polyline
        if (polylinePoints.size() > 1) {
            PolylineOptions polylineOptions = new PolylineOptions()
                    .addAll(polylinePoints)
                    .color(getResources().getColor(com.mihir.alzheimerscaregiver.caretaker.R.color.primary_color, null))
                    .width(8f)
                    .geodesic(true);
            
            currentPolyline = googleMap.addPolyline(polylineOptions);
        }
        
        // Add markers for start and end points
        if (!locationHistory.isEmpty()) {
            LocationPoint firstPoint = locationHistory.get(0);
            LocationPoint lastPoint = locationHistory.get(locationHistory.size() - 1);
            
            // Start marker (green)
            googleMap.addMarker(new MarkerOptions()
                    .position(new LatLng(firstPoint.latitude, firstPoint.longitude))
                    .title("Start")
                    .snippet(formatTime(firstPoint.timestamp))
                    .icon(com.google.android.gms.maps.model.BitmapDescriptorFactory
                            .defaultMarker(com.google.android.gms.maps.model.BitmapDescriptorFactory.HUE_GREEN)));
            
            // End marker (red)
            if (locationHistory.size() > 1) {
                googleMap.addMarker(new MarkerOptions()
                        .position(new LatLng(lastPoint.latitude, lastPoint.longitude))
                        .title("End")
                        .snippet(formatTime(lastPoint.timestamp))
                        .icon(com.google.android.gms.maps.model.BitmapDescriptorFactory
                                .defaultMarker(com.google.android.gms.maps.model.BitmapDescriptorFactory.HUE_RED)));
            }
            
            // Fit map to show all points
            try {
                LatLngBounds bounds = boundsBuilder.build();
                int padding = 100; // padding in pixels
                googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, padding));
            } catch (Exception e) {
                // Fallback to centering on first point
                googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(
                        new LatLng(firstPoint.latitude, firstPoint.longitude), DEFAULT_ZOOM));
            }
        }
    }
    
    /**
     * Update movement statistics
     */
    private void updateMovementStats() {
        if (locationHistory.isEmpty()) {
            textMovementStats.setText("No location data for selected date");
            return;
        }
        
        // Calculate total distance
        double totalDistance = 0;
        for (int i = 1; i < locationHistory.size(); i++) {
            LocationPoint prev = locationHistory.get(i - 1);
            LocationPoint curr = locationHistory.get(i);
            totalDistance += calculateDistance(prev, curr);
        }
        
        // Calculate time span
        long timeSpan = locationHistory.get(locationHistory.size() - 1).timestamp - 
                       locationHistory.get(0).timestamp;
        long hours = timeSpan / (1000 * 60 * 60);
        long minutes = (timeSpan % (1000 * 60 * 60)) / (1000 * 60);
        
        String statsText = String.format(Locale.getDefault(),
                "%d points • %.1f km • %dh %dm",
                locationHistory.size(),
                totalDistance / 1000.0,
                hours,
                minutes);
        
        textMovementStats.setText(statsText);
    }
    
    /**
     * Update time points list
     */
    private void updateTimePointsList() {
        if (timePointsAdapter != null) {
            timePointsAdapter.updateLocationPoints(locationHistory);
        }
    }
    
    /**
     * Export location data
     */
    public void exportLocationData(View view) {
        if (locationHistory.isEmpty()) {
            Toast.makeText(this, "No location data to export", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Create CSV content
        StringBuilder csvContent = new StringBuilder();
        csvContent.append("Timestamp,Latitude,Longitude,Time\n");
        
        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        for (LocationPoint point : locationHistory) {
            csvContent.append(point.timestamp).append(",")
                     .append(point.latitude).append(",")
                     .append(point.longitude).append(",")
                     .append(timeFormat.format(new Date(point.timestamp)))
                     .append("\n");
        }
        
        // Share via intent
        android.content.Intent shareIntent = new android.content.Intent(android.content.Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, 
                "Location History - " + patientName + " - " + selectedDate);
        shareIntent.putExtra(android.content.Intent.EXTRA_TEXT, csvContent.toString());
        
        startActivity(android.content.Intent.createChooser(shareIntent, "Export Location Data"));
    }
    
    /**
     * Calculate distance between two location points in meters
     */
    private double calculateDistance(LocationPoint point1, LocationPoint point2) {
        float[] results = new float[1];
        android.location.Location.distanceBetween(
                point1.latitude, point1.longitude,
                point2.latitude, point2.longitude,
                results);
        return results[0];
    }
    
    /**
     * Format date for Firebase path (YYYY-MM-DD)
     */
    private String formatDate(Date date) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        return sdf.format(date);
    }
    
    /**
     * Format timestamp for display (HH:mm:ss)
     */
    private String formatTime(long timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        return sdf.format(new Date(timestamp));
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
        if (mapView != null) {
            mapView.onDestroy();
        }
        Log.d(TAG, "HistoryActivity destroyed");
    }
    
    @Override
    public void onLowMemory() {
        super.onLowMemory();
        if (mapView != null) {
            mapView.onLowMemory();
        }
    }
    
    /**
     * Location point data class
     */
    public static class LocationPoint {
        public final double latitude;
        public final double longitude;
        public final long timestamp;
        
        public LocationPoint(double latitude, double longitude, long timestamp) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.timestamp = timestamp;
        }
    }
}