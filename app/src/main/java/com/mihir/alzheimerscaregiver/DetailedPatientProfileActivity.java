package com.mihir.alzheimerscaregiver;

import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.mihir.alzheimerscaregiver.auth.FirebaseAuthManager;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class DetailedPatientProfileActivity extends AppCompatActivity {
    
    private TextView patientNameTextView, birthYearTextView, birthplaceTextView, 
                     professionTextView, otherDetailsTextView, caretakerEmailTextView, 
                     lastUpdatedTextView;
    private MaterialButton refreshButton;
    private ProgressBar progressBar;
    
    private FirebaseAuthManager authManager;
    private FirebaseFirestore db;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detailed_patient_profile);
        
        // Initialize Firebase
        authManager = new FirebaseAuthManager();
        db = FirebaseFirestore.getInstance();
        
        // Check if user is signed in
        if (!authManager.isPatientSignedIn()) {
            Toast.makeText(this, "Please sign in first", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        
        initializeViews();
        setupClickListeners();
        loadPatientProfileData();
    }
    
    private void initializeViews() {
        patientNameTextView = findViewById(R.id.patientNameTextView);
        birthYearTextView = findViewById(R.id.birthYearTextView);
        birthplaceTextView = findViewById(R.id.birthplaceTextView);
        professionTextView = findViewById(R.id.professionTextView);
        otherDetailsTextView = findViewById(R.id.otherDetailsTextView);
        caretakerEmailTextView = findViewById(R.id.caretakerEmailTextView);
        lastUpdatedTextView = findViewById(R.id.lastUpdatedTextView);
        refreshButton = findViewById(R.id.refreshButton);
        progressBar = findViewById(R.id.progressBar);
    }
    
    private void setupClickListeners() {
        refreshButton.setOnClickListener(v -> loadPatientProfileData());
    }
    
    private void loadPatientProfileData() {
        String patientId = authManager.getCurrentPatientId();
        if (patientId == null) {
            Toast.makeText(this, "Unable to get patient ID", Toast.LENGTH_LONG).show();
            return;
        }
        
        // Show loading state
        showLoading(true);
        refreshButton.setEnabled(false);
        
        // Fetch profile data from Firestore
        db.collection("patients")
                .document(patientId)
                .collection("profile")
                .document("details")
                .get()
                .addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                        showLoading(false);
                        refreshButton.setEnabled(true);
                        
                        if (task.isSuccessful()) {
                            DocumentSnapshot document = task.getResult();
                            if (document.exists()) {
                                displayProfileData(document);
                            } else {
                                showNoProfileFoundMessage();
                            }
                        } else {
                            String errorMessage = task.getException() != null ? 
                                task.getException().getMessage() : "Unknown error occurred";
                            Toast.makeText(DetailedPatientProfileActivity.this,
                                "Failed to fetch profile data: " + errorMessage, 
                                Toast.LENGTH_LONG).show();
                        }
                    }
                });
    }
    
    private void displayProfileData(DocumentSnapshot document) {
        try {
            // Extract data from Firestore document
            String name = document.getString("name");
            Long birthYear = document.getLong("birthYear");
            String birthplace = document.getString("birthplace");
            String profession = document.getString("profession");
            String otherDetails = document.getString("otherDetails");
            String caretakerEmail = document.getString("caretakerEmail");
            com.google.firebase.Timestamp lastUpdated = document.getTimestamp("lastUpdated");
            
            // Display the data with fallbacks for missing fields
            patientNameTextView.setText(name != null ? name : "Not provided");
            birthYearTextView.setText(birthYear != null ? String.valueOf(birthYear) : "Not provided");
            birthplaceTextView.setText(birthplace != null ? birthplace : "Not provided");
            professionTextView.setText(profession != null && !profession.trim().isEmpty() ? 
                profession : "Not provided");
            otherDetailsTextView.setText(otherDetails != null && !otherDetails.trim().isEmpty() ? 
                otherDetails : "No additional details provided");
            
            // Debug information
            caretakerEmailTextView.setText(caretakerEmail != null ? caretakerEmail : "Not available");
            
            // Format and display last updated time
            if (lastUpdated != null) {
                Date date = lastUpdated.toDate();
                SimpleDateFormat formatter = new SimpleDateFormat("MMM dd, yyyy 'at' HH:mm", Locale.getDefault());
                lastUpdatedTextView.setText(formatter.format(date));
            } else {
                lastUpdatedTextView.setText("Not available");
            }
            
            Toast.makeText(this, "Profile data loaded successfully!", Toast.LENGTH_SHORT).show();
            
        } catch (Exception e) {
            Toast.makeText(this, "Error displaying profile data: " + e.getMessage(), 
                Toast.LENGTH_LONG).show();
        }
    }
    
    private void showNoProfileFoundMessage() {
        // Set all fields to indicate no profile found
        patientNameTextView.setText("No profile found");
        birthYearTextView.setText("No profile found");
        birthplaceTextView.setText("No profile found");
        professionTextView.setText("No profile found");
        otherDetailsTextView.setText("No profile found");
        caretakerEmailTextView.setText("No profile found");
        lastUpdatedTextView.setText("No profile found");
        
        Toast.makeText(this, "No profile found for this patient. Ask your caretaker to enter your profile details.", 
            Toast.LENGTH_LONG).show();
    }
    
    private void showLoading(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        refreshButton.setText(show ? "Loading..." : "Refresh Profile Data");
    }
}