package com.mihir.alzheimerscaregiver.caretaker;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class PatientProfileEntryActivity extends AppCompatActivity {

    private TextInputEditText patientNameEditText;
    private TextInputEditText birthYearEditText;
    private TextInputEditText birthplaceEditText;
    private TextInputEditText professionEditText;
    private TextInputEditText otherDetailsEditText;
    private MaterialButton savePatientProfileButton;
    
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private SharedPreferences prefs;
    private String linkedPatientId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_patient_profile_entry);

        // Initialize Firebase
        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();
        
        // Get shared preferences and linked patient ID
        prefs = getSharedPreferences("CaretakerApp", MODE_PRIVATE);
        linkedPatientId = prefs.getString("linkedPatientId", null);
        
        // Check if patient is linked
        if (linkedPatientId == null) {
            Toast.makeText(this, "No patient linked. Please link a patient first.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // Initialize views
        initializeViews();
        
        // Set click listener
        savePatientProfileButton.setOnClickListener(v -> attemptSaveProfile());
    }

    private void initializeViews() {
        patientNameEditText = findViewById(R.id.patientNameEditText);
        birthYearEditText = findViewById(R.id.birthYearEditText);
        birthplaceEditText = findViewById(R.id.birthplaceEditText);
        professionEditText = findViewById(R.id.professionEditText);
        otherDetailsEditText = findViewById(R.id.otherDetailsEditText);
        savePatientProfileButton = findViewById(R.id.savePatientProfileButton);
    }

    private void attemptSaveProfile() {
        // Get input values
        String patientName = patientNameEditText.getText().toString().trim();
        String birthYearStr = birthYearEditText.getText().toString().trim();
        String birthplace = birthplaceEditText.getText().toString().trim();
        String profession = professionEditText.getText().toString().trim();
        String otherDetails = otherDetailsEditText.getText().toString().trim();

        // Validate required fields
        if (TextUtils.isEmpty(patientName)) {
            patientNameEditText.setError("Patient name is required");
            patientNameEditText.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(birthYearStr)) {
            birthYearEditText.setError("Birth year is required");
            birthYearEditText.requestFocus();
            return;
        }

        // Validate birth year format
        int birthYear;
        try {
            birthYear = Integer.parseInt(birthYearStr);
            int currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR);
            if (birthYear < 1900 || birthYear > currentYear) {
                birthYearEditText.setError("Please enter a valid birth year");
                birthYearEditText.requestFocus();
                return;
            }
        } catch (NumberFormatException e) {
            birthYearEditText.setError("Please enter a valid birth year");
            birthYearEditText.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(birthplace)) {
            birthplaceEditText.setError("Birthplace is required");
            birthplaceEditText.requestFocus();
            return;
        }

        // Disable save button and show progress
        savePatientProfileButton.setEnabled(false);
        savePatientProfileButton.setText("Saving...");

        // Create patient profile data
        Map<String, Object> patientProfile = new HashMap<>();
        patientProfile.put("name", patientName);
        patientProfile.put("birthYear", birthYear);
        patientProfile.put("birthplace", birthplace);
        patientProfile.put("profession", profession);
        patientProfile.put("otherDetails", otherDetails);
        patientProfile.put("lastUpdated", com.google.firebase.Timestamp.now());
        
        // Add caretaker information
        if (mAuth.getCurrentUser() != null) {
            patientProfile.put("caretakerId", mAuth.getCurrentUser().getUid());
            patientProfile.put("caretakerEmail", mAuth.getCurrentUser().getEmail());
        }

        // Save to Firestore in the patient's profile document
        db.collection("patients")
                .document(linkedPatientId)
                .collection("profile")
                .document("details")
                .set(patientProfile)
                .addOnCompleteListener(new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        if (task.isSuccessful()) {
                            // Profile saved successfully, now update the caretaker list
                            updatePatientCaretakerList();
                        } else {
                            // Re-enable save button on failure
                            savePatientProfileButton.setEnabled(true);
                            savePatientProfileButton.setText("Save Patient Profile");
                            
                            String errorMessage = task.getException() != null ? 
                                task.getException().getMessage() : "Unknown error occurred";
                            Toast.makeText(PatientProfileEntryActivity.this, 
                                "Failed to save patient profile: " + errorMessage, 
                                Toast.LENGTH_LONG).show();
                        }
                    }
                });
    }

    private void updatePatientCaretakerList() {
        if (mAuth.getCurrentUser() == null) {
            finalizeSave();
            return;
        }
        
        String caretakerId = mAuth.getCurrentUser().getUid();
        
        // First, get the current patient document to check existing caretakerIds
        db.collection("patients")
                .document(linkedPatientId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    java.util.List<String> caretakerIds = new java.util.ArrayList<>();
                    
                    // Get existing caretaker IDs if they exist
                    if (documentSnapshot.exists()) {
                        java.util.List<String> existingIds = (java.util.List<String>) documentSnapshot.get("caretakerIds");
                        if (existingIds != null) {
                            caretakerIds.addAll(existingIds);
                        }
                    }
                    
                    // Add current caretaker if not already in the list
                    if (!caretakerIds.contains(caretakerId)) {
                        caretakerIds.add(caretakerId);
                        
                        // Update the patient document with the new caretaker list
                        db.collection("patients")
                                .document(linkedPatientId)
                                .update("caretakerIds", caretakerIds)
                                .addOnSuccessListener(aVoid -> finalizeSave())
                                .addOnFailureListener(e -> {
                                    android.util.Log.w("PatientProfile", "Failed to update caretaker list", e);
                                    // Still complete the save even if caretaker list update fails
                                    finalizeSave();
                                });
                    } else {
                        // Caretaker already in list, just finalize
                        finalizeSave();
                    }
                })
                .addOnFailureListener(e -> {
                    android.util.Log.w("PatientProfile", "Failed to fetch patient document", e);
                    // Still complete the save even if caretaker list update fails
                    finalizeSave();
                });
    }
    
    private void finalizeSave() {
        // Re-enable save button
        savePatientProfileButton.setEnabled(true);
        savePatientProfileButton.setText("Save Patient Profile");
        
        Toast.makeText(PatientProfileEntryActivity.this, 
            "Patient profile saved successfully!", Toast.LENGTH_SHORT).show();
        
        // Clear form after successful save
        clearForm();
        
        // Optionally finish the activity
        finish();
    }

    private void clearForm() {
        patientNameEditText.setText("");
        birthYearEditText.setText("");
        birthplaceEditText.setText("");
        professionEditText.setText("");
        otherDetailsEditText.setText("");
    }
}