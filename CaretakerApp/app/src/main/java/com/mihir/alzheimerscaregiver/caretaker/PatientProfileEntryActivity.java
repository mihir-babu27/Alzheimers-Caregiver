package com.mihir.alzheimerscaregiver.caretaker;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class PatientProfileEntryActivity extends AppCompatActivity {

    // UI Elements - using basic Android components for compatibility
    private android.widget.EditText patientNameEditText;
    private android.widget.EditText birthYearEditText;
    private android.widget.EditText birthplaceEditText;
    private android.widget.EditText professionEditText;
    private android.widget.EditText otherDetailsEditText;
    private android.widget.Button savePatientProfileButton;
    
    // Additional UI elements for enhanced interface
    private TextView toolbarTitle;
    private TextView modeIndicatorText;
    private TextView profileStatusText;
    private TextView statusText;
    private ProgressBar progressBar;
    private ImageButton backButton;
    
    // Firebase and data
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private SharedPreferences prefs;
    private String linkedPatientId;
    
    // Mode tracking for edit functionality
    private boolean isEditMode = false;
    private Map<String, Object> existingProfile = null;

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
        
        // Check for existing profile to determine mode
        checkForExistingProfile();
        
        // Set click listener
        savePatientProfileButton.setOnClickListener(v -> attemptSaveProfile());
    }

    private void initializeViews() {
        // Form elements
        patientNameEditText = findViewById(R.id.patientNameEditText);
        birthYearEditText = findViewById(R.id.birthYearEditText);
        birthplaceEditText = findViewById(R.id.birthplaceEditText);
        professionEditText = findViewById(R.id.professionEditText);
        otherDetailsEditText = findViewById(R.id.otherDetailsEditText);
        savePatientProfileButton = findViewById(R.id.savePatientProfileButton);
        
        // New UI elements
        toolbarTitle = findViewById(R.id.toolbarTitle);
        modeIndicatorText = findViewById(R.id.modeIndicatorText);
        profileStatusText = findViewById(R.id.profileStatusText);
        statusText = findViewById(R.id.statusText);
        progressBar = findViewById(R.id.progressBar);
        backButton = findViewById(R.id.backButton);
        
        // Set up back button
        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }
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

        // Show loading state
        showLoading(true);

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

    /**
     * Check for existing profile and determine mode
     */
    private void checkForExistingProfile() {
        if (progressBar != null) {
            progressBar.setVisibility(android.view.View.VISIBLE);
        }
        
        db.collection("patients")
                .document(linkedPatientId)
                .collection("profile")
                .document("details")
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (progressBar != null) {
                        progressBar.setVisibility(android.view.View.GONE);
                    }
                    
                    if (documentSnapshot.exists()) {
                        // Profile exists - enter edit mode
                        isEditMode = true;
                        existingProfile = documentSnapshot.getData();
                        populateFieldsForEdit();
                        updateUIForEditMode();
                    } else {
                        // No profile - create mode
                        isEditMode = false;
                        updateUIForCreateMode();
                    }
                })
                .addOnFailureListener(e -> {
                    if (progressBar != null) {
                        progressBar.setVisibility(android.view.View.GONE);
                    }
                    Log.e("PatientProfile", "Error checking for existing profile", e);
                    Toast.makeText(this, "Error loading profile data", Toast.LENGTH_SHORT).show();
                    // Default to create mode
                    isEditMode = false;
                    updateUIForCreateMode();
                });
    }

    /**
     * Populate form fields with existing profile data
     */
    private void populateFieldsForEdit() {
        if (existingProfile == null) return;
        
        // Populate name
        String name = (String) existingProfile.get("name");
        if (name != null) {
            patientNameEditText.setText(name);
        }
        
        // Populate birth year
        Object birthYear = existingProfile.get("birthYear");
        if (birthYear != null) {
            birthYearEditText.setText(String.valueOf(birthYear));
        }
        
        // Populate birthplace
        String birthplace = (String) existingProfile.get("birthplace");
        if (birthplace != null) {
            birthplaceEditText.setText(birthplace);
        }
        
        // Populate profession
        String profession = (String) existingProfile.get("profession");
        if (profession != null) {
            professionEditText.setText(profession);
        }
        
        // Populate other details
        String otherDetails = (String) existingProfile.get("otherDetails");
        if (otherDetails != null) {
            otherDetailsEditText.setText(otherDetails);
        }
    }

    /**
     * Update UI for edit mode
     */
    private void updateUIForEditMode() {
        if (toolbarTitle != null) {
            toolbarTitle.setText("Edit Patient Profile");
        }
        if (modeIndicatorText != null) {
            modeIndicatorText.setText("EDIT");
            modeIndicatorText.setBackgroundResource(android.R.drawable.btn_default);
        }
        if (profileStatusText != null) {
            profileStatusText.setText("Update patient information");
        }
        if (savePatientProfileButton != null) {
            savePatientProfileButton.setText("Update Profile");
        }
        if (statusText != null) {
            statusText.setText("Changes will be saved securely");
        }
        Toast.makeText(this, "Editing existing profile", Toast.LENGTH_SHORT).show();
    }

    /**
     * Update UI for create mode
     */
    private void updateUIForCreateMode() {
        if (toolbarTitle != null) {
            toolbarTitle.setText("Patient Profile");
        }
        if (modeIndicatorText != null) {
            modeIndicatorText.setText("CREATE");
            modeIndicatorText.setBackgroundResource(android.R.drawable.btn_default);
        }
        if (profileStatusText != null) {
            profileStatusText.setText("Create a comprehensive patient profile");
        }
        if (savePatientProfileButton != null) {
            savePatientProfileButton.setText("Save Profile");
        }
        if (statusText != null) {
            statusText.setText("All information is securely encrypted");
        }
    }

    /**
     * Show loading state
     */
    private void showLoading(boolean loading) {
        if (progressBar != null) {
            progressBar.setVisibility(loading ? android.view.View.VISIBLE : android.view.View.GONE);
        }
        if (savePatientProfileButton != null) {
            savePatientProfileButton.setEnabled(!loading);
            if (loading) {
                savePatientProfileButton.setText(isEditMode ? "Updating..." : "Saving...");
            } else {
                savePatientProfileButton.setText(isEditMode ? "Update Profile" : "Save Profile");
            }
        }
    }

    private void clearForm() {
        patientNameEditText.setText("");
        birthYearEditText.setText("");
        birthplaceEditText.setText("");
        professionEditText.setText("");
        otherDetailsEditText.setText("");
    }
}