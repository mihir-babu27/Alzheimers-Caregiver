package com.mihir.alzheimerscaregiver.caretaker.activities;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import com.mihir.alzheimerscaregiver.caretaker.R;
import com.mihir.alzheimerscaregiver.caretaker.data.entity.MedicationEntity;
import com.mihir.alzheimerscaregiver.caretaker.data.repository.MedicationRepository;

/**
 * Temporary MedicationDetailActivity - placeholder for detailed medication view
 */
public class MedicationDetailActivity extends AppCompatActivity {

    private static final String EXTRA_MEDICATION_ID = "medication_id";
    private static final String EXTRA_PATIENT_ID = "patientId";

    private String medicationId;
    private String patientId;
    private MedicationRepository repository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_medication_detail);

        // Get extras from intent
        Intent intent = getIntent();
        medicationId = intent.getStringExtra(EXTRA_MEDICATION_ID);
        patientId = intent.getStringExtra(EXTRA_PATIENT_ID);

        if (medicationId == null || patientId == null) {
            Toast.makeText(this, "Missing medication information", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        setupToolbar();
        setupRepository();
        loadMedicationDetails();
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Medication Details");
        }
    }

    private void setupRepository() {
        repository = new MedicationRepository();
    }

    private void loadMedicationDetails() {
        repository.getMedication(patientId, medicationId)
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        MedicationEntity medication = documentSnapshot.toObject(MedicationEntity.class);
                        if (medication != null) {
                            medication.id = documentSnapshot.getId();
                            displayMedicationDetails(medication);
                        }
                    } else {
                        Toast.makeText(this, "Medication not found", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to load medication: " + e.getMessage(), 
                            Toast.LENGTH_LONG).show();
                    finish();
                });
    }

    private void displayMedicationDetails(MedicationEntity medication) {
        // This is a placeholder - will be expanded later
        TextView textDetails = findViewById(R.id.text_medication_details);
        
        StringBuilder details = new StringBuilder();
        details.append("Name: ").append(medication.getDisplayName()).append("\n");
        details.append("Dosage: ").append(medication.dosage != null ? medication.dosage : "Not specified").append("\n");
        details.append("Time: ").append(medication.time != null ? medication.time : "Not set").append("\n");
        details.append("Active: ").append(medication.isActive ? "Yes" : "No").append("\n");
        
        if (medication.category != null && !medication.category.isEmpty()) {
            details.append("Category: ").append(medication.category).append("\n");
        }
        
        if (medication.description != null && !medication.description.isEmpty()) {
            details.append("Description: ").append(medication.description).append("\n");
        }

        textDetails.setText(details.toString());

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(medication.getDisplayName());
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}