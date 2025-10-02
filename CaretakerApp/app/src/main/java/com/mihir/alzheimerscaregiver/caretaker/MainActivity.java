package com.mihir.alzheimerscaregiver.caretaker;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;

public class MainActivity extends AppCompatActivity {

    private Button addMedicationButton, addTaskButton, addEmergencyContactButton, logoutButton, viewMmseResultsButton;
    private Button scheduleMmseTestButton, addCustomQuestionsButton, addPatientProfileButton;
    private TextView welcomeText;
    private FirebaseAuth mAuth;
    private SharedPreferences prefs;
    private String linkedPatientId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize Firebase Auth
        mAuth = FirebaseAuth.getInstance();
        prefs = getSharedPreferences("CaretakerApp", MODE_PRIVATE);

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
        addTaskButton = findViewById(R.id.addTaskButton);
        addEmergencyContactButton = findViewById(R.id.addEmergencyContactButton);
        logoutButton = findViewById(R.id.logoutButton);
        viewMmseResultsButton = findViewById(R.id.viewMmseResultsButton);
        welcomeText = findViewById(R.id.welcomeText);

    scheduleMmseTestButton = findViewById(R.id.scheduleMmseTestButton);
    addCustomQuestionsButton = findViewById(R.id.addCustomQuestionsButton);
    addPatientProfileButton = findViewById(R.id.addPatientProfileButton);

        // Set welcome text
        welcomeText.setText("Welcome! You are linked to Patient ID: " + linkedPatientId);

        // Set click listeners
        addMedicationButton.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, AddMedicationActivity.class);
            intent.putExtra("patientId", linkedPatientId);
            startActivity(intent);
        });

        addTaskButton.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, AddTaskActivity.class);
            intent.putExtra("patientId", linkedPatientId);
            startActivity(intent);
        });

        addEmergencyContactButton.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, AddEmergencyContactActivity.class);
            intent.putExtra("patientId", linkedPatientId);
            startActivity(intent);
        });

        logoutButton.setOnClickListener(v -> logout());

        viewMmseResultsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, MmseResultsActivity.class);
                intent.putExtra(MmseResultsActivity.EXTRA_PATIENT_ID, linkedPatientId);
                startActivity(intent);
            }
        });

        scheduleMmseTestButton.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, ScheduleMmseTestActivity.class);
            intent.putExtra("patientId", linkedPatientId);
            startActivity(intent);
        });

        addCustomQuestionsButton.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, CustomMmseQuestionsActivity.class);
            intent.putExtra("patientId", linkedPatientId);
            startActivity(intent);
        });

        addPatientProfileButton.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, PatientProfileEntryActivity.class);
            startActivity(intent);
        });
    }

    private void logout() {
        // Sign out from Firebase
        mAuth.signOut();
        
        // Clear local preferences
        SharedPreferences.Editor editor = prefs.edit();
        editor.clear();
        editor.apply();
        
        // Go back to login
        Intent intent = new Intent(this, com.mihir.alzheimerscaregiver.caretaker.auth.LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Check if user is still authenticated
        if (mAuth.getCurrentUser() == null) {
            // User not authenticated, go to login
            Intent intent = new Intent(this, com.mihir.alzheimerscaregiver.caretaker.auth.LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        }
    }
}
