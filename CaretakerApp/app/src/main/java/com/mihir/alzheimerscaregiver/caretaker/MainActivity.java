package com.mihir.alzheimerscaregiver.caretaker;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.mihir.alzheimerscaregiver.caretaker.activities.EmergencyContactsActivity;
import com.mihir.alzheimerscaregiver.caretaker.auth.SessionManager;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    
    private Button addMedicationButton, viewMedicationsButton, addTaskButton, addEmergencyContactButton, logoutButton, viewMmseResultsButton;
    private Button scheduleMmseTestButton, addCustomQuestionsButton, addPatientProfileButton;
    private Button viewLocationButton, viewHistoryButton, manageGeofencesButton;
    
    // New card views for modern UI
    private View addPatientProfileCard, scheduleMmseTestCard, addCustomQuestionsCard, viewMmseResultsCard;
    private View addTaskCard, addEmergencyContactCard, logoutCard;
    private TextView welcomeText;
    private SessionManager sessionManager;
    private SharedPreferences prefs;
    private String linkedPatientId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        Log.d(TAG, "MainActivity created");

        // Initialize SessionManager and SharedPreferences
        sessionManager = new SessionManager(this);
        prefs = getSharedPreferences("CaretakerApp", MODE_PRIVATE);
        
        // Verify user is still authenticated before proceeding
        if (!sessionManager.isUserAuthenticated()) {
            Log.w(TAG, "User not authenticated in MainActivity, redirecting to splash");
            redirectToSplash();
            return;
        }

        // Get linked patient ID
        linkedPatientId = prefs.getString("linkedPatientId", null);
        if (linkedPatientId == null) {
            // No patient linked, go back to patient link
            Intent intent = new Intent(this, PatientLinkActivity.class);
            startActivity(intent);
            finish();
            return;
        }

        // Initialize views
        addMedicationButton = findViewById(R.id.addMedicationButton);
        viewMedicationsButton = findViewById(R.id.viewMedicationsButton);
        welcomeText = findViewById(R.id.welcomeText);

        // Location tracking buttons
        viewLocationButton = findViewById(R.id.viewLocationButton);
        viewHistoryButton = findViewById(R.id.viewHistoryButton);
        //manageGeofencesButton = findViewById(R.id.manageGeofencesButton);
        
        // New card views for modern UI
        addPatientProfileCard = findViewById(R.id.addPatientProfileCard);
        scheduleMmseTestCard = findViewById(R.id.scheduleMmseTestCard);
        addCustomQuestionsCard = findViewById(R.id.addCustomQuestionsCard);
        viewMmseResultsCard = findViewById(R.id.viewMmseResultsCard);
        addTaskCard = findViewById(R.id.addTaskCard);
        addEmergencyContactCard = findViewById(R.id.addEmergencyContactCard);
        logoutCard = findViewById(R.id.logoutCard);
    
    // TODO: Hide geofencing functionality for now
    if (manageGeofencesButton != null) {
        manageGeofencesButton.setVisibility(View.GONE);
    }

        // Set welcome text
        welcomeText.setText("Welcome! You are linked to Patient ID: " + linkedPatientId);

        // Set click listeners
        addMedicationButton.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, AddMedicationActivity.class);
            intent.putExtra("patientId", linkedPatientId);
            startActivity(intent);
        });

        viewMedicationsButton.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, com.mihir.alzheimerscaregiver.caretaker.activities.MedicationListActivity.class);
            intent.putExtra("patientId", linkedPatientId);
            intent.putExtra("patientName", "Patient"); // TODO: Get actual patient name
            startActivity(intent);
        });

        // Set click listeners for modern card UI
        addTaskCard.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, com.mihir.alzheimerscaregiver.caretaker.activities.TaskListActivity.class);
            intent.putExtra("patientId", linkedPatientId);
            startActivity(intent);
        });

        addEmergencyContactCard.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, EmergencyContactsActivity.class);
            intent.putExtra("patientId", linkedPatientId);
            startActivity(intent);
        });

        logoutCard.setOnClickListener(v -> logout());

        viewMmseResultsCard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, MmseResultsActivity.class);
                intent.putExtra(MmseResultsActivity.EXTRA_PATIENT_ID, linkedPatientId);
                startActivity(intent);
            }
        });

        scheduleMmseTestCard.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, ScheduleMmseTestActivity.class);
            intent.putExtra("patientId", linkedPatientId);
            startActivity(intent);
        });

        addCustomQuestionsCard.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, CustomMmseQuestionsActivity.class);
            intent.putExtra("patientId", linkedPatientId);
            startActivity(intent);
        });

        addPatientProfileCard.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, PatientProfileEntryActivity.class);
            startActivity(intent);
        });
        
        // Location tracking click listeners
        viewLocationButton.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, CaretakerMapActivity.class);
            intent.putExtra("patientId", linkedPatientId);
            startActivity(intent);
        });
        
        viewHistoryButton.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, HistoryActivity.class);
            intent.putExtra("patientId", linkedPatientId);
            startActivity(intent);
        });
        
        // TODO: Geofence management functionality (disabled for now)
        /*
        manageGeofencesButton.setOnClickListener(v -> {
            // For now, direct to CaretakerMapActivity where geofence management can be accessed
            Intent intent = new Intent(MainActivity.this, CaretakerMapActivity.class);
            intent.putExtra("patientId", linkedPatientId);
            intent.putExtra("showGeofenceManagement", true); // Flag for geofence mode
            startActivity(intent);
        });
        */
    }

    private void logout() {
        Log.d(TAG, "User requesting logout");
        
        // Use SessionManager to handle proper logout
        sessionManager.signOut();
        
        // Clear local preferences
        SharedPreferences.Editor editor = prefs.edit();
        editor.clear();
        editor.apply();
        
        // Redirect to splash (which will route to login)
        redirectToSplash();
    }
    
    /**
     * Redirect to SplashActivity for proper authentication flow
     */
    private void redirectToSplash() {
        Intent intent = new Intent(this, SplashActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        
        // Check if user is still authenticated using SessionManager
        if (!sessionManager.isUserAuthenticated()) {
            Log.w(TAG, "User authentication lost, redirecting to splash");
            redirectToSplash();
        }
    }
}
