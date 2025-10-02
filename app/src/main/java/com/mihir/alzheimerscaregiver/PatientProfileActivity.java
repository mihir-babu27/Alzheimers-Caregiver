package com.mihir.alzheimerscaregiver;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.mihir.alzheimerscaregiver.auth.FirebaseAuthManager;
import com.mihir.alzheimerscaregiver.data.entity.PatientEntity;

public class PatientProfileActivity extends AppCompatActivity {
    
    private TextView patientIdTextView, nameTextView, emailTextView, caretakerCountTextView;
    private Button signOutButton, sharePatientIdButton, viewDetailedProfileButton;
    private FirebaseAuthManager authManager;
    private PatientEntity currentPatient;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_patient_profile);
        
        // Initialize Firebase Auth Manager
        authManager = new FirebaseAuthManager();
        
        // Check if user is signed in
        if (!authManager.isPatientSignedIn()) {
            navigateToAuth();
            return;
        }
        
        initializeViews();
        setupClickListeners();
        loadPatientData();
    }
    
    private void initializeViews() {
        patientIdTextView = findViewById(R.id.patientIdTextView);
        nameTextView = findViewById(R.id.nameTextView);
        emailTextView = findViewById(R.id.emailTextView);
        caretakerCountTextView = findViewById(R.id.caretakerCountTextView);
        signOutButton = findViewById(R.id.signOutButton);
        sharePatientIdButton = findViewById(R.id.sharePatientIdButton);
        viewDetailedProfileButton = findViewById(R.id.viewDetailedProfileButton);
    }
    
    private void setupClickListeners() {
        signOutButton.setOnClickListener(v -> handleSignOut());
        sharePatientIdButton.setOnClickListener(v -> sharePatientId());
        viewDetailedProfileButton.setOnClickListener(v -> viewDetailedProfile());
    }
    
    private void loadPatientData() {
        String patientId = authManager.getCurrentPatientId();
        if (patientId != null) {
            authManager.getPatientData(patientId, new FirebaseAuthManager.PatientDataCallback() {
                @Override
                public void onSuccess(PatientEntity patient) {
                    currentPatient = patient;
                    displayPatientData(patient);
                }
                
                @Override
                public void onError(String error) {
                    Toast.makeText(PatientProfileActivity.this, 
                            "Failed to load patient data: " + error, Toast.LENGTH_LONG).show();
                }
            });
        }
    }
    
    private void displayPatientData(PatientEntity patient) {
        if (patient != null) {
            patientIdTextView.setText(patient.patientId);
            nameTextView.setText(patient.name);
            emailTextView.setText(patient.email);
            
            int caretakerCount = patient.caretakerIds != null ? patient.caretakerIds.size() : 0;
            caretakerCountTextView.setText(String.valueOf(caretakerCount));
        }
    }
    
    private void handleSignOut() {
        authManager.signOut();
        Toast.makeText(this, "Signed out successfully", Toast.LENGTH_SHORT).show();
        navigateToAuth();
    }
    
    private void sharePatientId() {
        if (currentPatient != null) {
            String shareText = "My Patient ID: " + currentPatient.patientId + 
                              "\n\nShare this ID with your caretaker to link your accounts.";
            
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_TEXT, shareText);
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Patient ID for Alzheimer's Caregiver App");
            
            startActivity(Intent.createChooser(shareIntent, "Share Patient ID"));
        } else {
            Toast.makeText(this, "Patient data not loaded yet", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void navigateToAuth() {
        Intent intent = new Intent(this, com.mihir.alzheimerscaregiver.auth.AuthenticationActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
    
    private void viewDetailedProfile() {
        Intent intent = new Intent(this, DetailedPatientProfileActivity.class);
        startActivity(intent);
    }
    
    @Override
    public void onBackPressed() {
        // Navigate back to MainActivity instead of closing the app
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
        finish();
    }
}
