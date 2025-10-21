package com.mihir.alzheimerscaregiver.caretaker.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.mihir.alzheimerscaregiver.caretaker.R;
import com.mihir.alzheimerscaregiver.caretaker.adapters.MedicationAdapter;
import com.mihir.alzheimerscaregiver.caretaker.data.entity.MedicationEntity;
import com.mihir.alzheimerscaregiver.caretaker.data.repository.MedicationRepository;

import java.util.ArrayList;
import java.util.List;

public class MedicationListActivity extends AppCompatActivity implements MedicationAdapter.OnMedicationClickListener {

    private static final String EXTRA_PATIENT_ID = "patientId";
    private static final String EXTRA_PATIENT_NAME = "patientName";

    private RecyclerView recyclerView;
    private MedicationAdapter adapter;
    private ProgressBar progressBar;
    private TextView emptyView;
    private EditText searchEditText;
    private FloatingActionButton fabAdd;

    private MedicationRepository repository;
    private String patientId;
    private String patientName;
    private List<MedicationEntity> allMedications;
    private List<MedicationEntity> filteredMedications;
    
    private enum FilterType {
        ALL, ACTIVE, OVERDUE, TODAY
    }
    
    private FilterType currentFilter = FilterType.ALL;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_medication_list);

        // Get patient info from intent
        Intent intent = getIntent();
        patientId = intent.getStringExtra(EXTRA_PATIENT_ID);
        patientName = intent.getStringExtra(EXTRA_PATIENT_NAME);

        if (patientId == null) {
            Toast.makeText(this, "Patient ID not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initViews();
        setupRecyclerView();
        setupSearchFunctionality();
        setupRepository();
        loadMedications();
    }

    private void initViews() {
        recyclerView = findViewById(R.id.recycler_view_medications);
        progressBar = findViewById(R.id.progress_bar);
        emptyView = findViewById(R.id.text_empty_view);
        searchEditText = findViewById(R.id.edit_text_search);
        fabAdd = findViewById(R.id.fab_add_medication);

        // Setup back button
        findViewById(R.id.backButton).setOnClickListener(v -> onBackPressed());

        // Setup search button
        findViewById(R.id.searchButton).setOnClickListener(v -> toggleSearchLayout());

        // Setup FAB
        fabAdd.setOnClickListener(v -> {
            Intent intent = new Intent(this, com.mihir.alzheimerscaregiver.caretaker.AddMedicationActivity.class);
            intent.putExtra(EXTRA_PATIENT_ID, patientId);
            intent.putExtra(EXTRA_PATIENT_NAME, patientName);
            startActivity(intent);
        });
    }

    private void toggleSearchLayout() {
        View searchLayout = findViewById(R.id.searchLayout);
        if (searchLayout.getVisibility() == View.GONE) {
            searchLayout.setVisibility(View.VISIBLE);
            searchEditText.requestFocus();
            // Show keyboard
            android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(searchEditText, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
            }
        } else {
            searchLayout.setVisibility(View.GONE);
            searchEditText.setText("");
            searchEditText.clearFocus();
            // Hide keyboard
            android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(searchEditText.getWindowToken(), 0);
            }
        }
    }

    // Removed setupToolbar() since we're using custom header layout

    private void setupRecyclerView() {
        allMedications = new ArrayList<>();
        filteredMedications = new ArrayList<>();
        
        adapter = new MedicationAdapter(filteredMedications, this);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
    }

    private void setupSearchFunctionality() {
        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                filterMedications(s.toString().trim());
            }
        });
    }

    private void setupRepository() {
        repository = new MedicationRepository();
    }

    private void loadMedications() {
        showProgress(true);
        
        android.util.Log.d("MedicationListActivity", "Loading medications for patientId: " + patientId);
        
        // First run debug check to see what's in Firebase
        repository.debugCheckFirebasePaths(patientId)
                .addOnCompleteListener(debugTask -> {
                    // Now run the actual query
                    repository.getAllMedications(patientId)
                            .addOnSuccessListener(querySnapshot -> {
                                android.util.Log.d("MedicationListActivity", "Firebase query successful. Document count: " + querySnapshot.size());
                                handleMedicationsLoaded(querySnapshot);
                            })
                            .addOnFailureListener(exception -> {
                                android.util.Log.e("MedicationListActivity", "Firebase query failed: " + exception.getMessage(), exception);
                                handleLoadError(exception);
                            });
                });
    }

    private void handleMedicationsLoaded(QuerySnapshot querySnapshot) {
        showProgress(false);
        
        allMedications.clear();
        
        for (DocumentSnapshot document : querySnapshot.getDocuments()) {
            MedicationEntity medication = document.toObject(MedicationEntity.class);
            if (medication != null) {
                medication.id = document.getId();
                allMedications.add(medication);
            }
        }
        
        applyCurrentFilter();
        updateEmptyView();
    }

    private void handleLoadError(Exception e) {
        showProgress(false);
        Toast.makeText(this, "Failed to load medications: " + e.getMessage(), Toast.LENGTH_LONG).show();
        updateEmptyView();
    }

    private void filterMedications(String searchQuery) {
        filteredMedications.clear();
        
        List<MedicationEntity> sourceList = getFilteredByType();
        
        if (searchQuery.isEmpty()) {
            filteredMedications.addAll(sourceList);
        } else {
            String query = searchQuery.toLowerCase();
            
            for (MedicationEntity medication : sourceList) {
                if (matchesSearchQuery(medication, query)) {
                    filteredMedications.add(medication);
                }
            }
        }
        
        adapter.notifyDataSetChanged();
        updateEmptyView();
    }

    private boolean matchesSearchQuery(MedicationEntity medication, String query) {
        return (medication.name != null && medication.name.toLowerCase().contains(query)) ||
               (medication.category != null && medication.category.toLowerCase().contains(query)) ||
               (medication.dosage != null && medication.dosage.toLowerCase().contains(query)) ||
               (medication.description != null && medication.description.toLowerCase().contains(query));
    }

    private List<MedicationEntity> getFilteredByType() {
        List<MedicationEntity> filtered = new ArrayList<>();
        long currentTime = System.currentTimeMillis();
        
        for (MedicationEntity medication : allMedications) {
            switch (currentFilter) {
                case ALL:
                    filtered.add(medication);
                    break;
                case ACTIVE:
                    if (medication.isActive) {
                        filtered.add(medication);
                    }
                    break;
                case OVERDUE:
                    if (medication.isActive && medication.isOverdue()) {
                        filtered.add(medication);
                    }
                    break;
                case TODAY:
                    if (medication.isActive && isTodaysMedication(medication, currentTime)) {
                        filtered.add(medication);
                    }
                    break;
            }
        }
        
        return filtered;
    }

    private boolean isTodaysMedication(MedicationEntity medication, long currentTime) {
        if (medication.nextDueTime == null) return false;
        
        java.util.Calendar today = java.util.Calendar.getInstance();
        today.setTimeInMillis(currentTime);
        
        java.util.Calendar dueDate = java.util.Calendar.getInstance();
        dueDate.setTimeInMillis(medication.nextDueTime);
        
        return today.get(java.util.Calendar.YEAR) == dueDate.get(java.util.Calendar.YEAR) &&
               today.get(java.util.Calendar.DAY_OF_YEAR) == dueDate.get(java.util.Calendar.DAY_OF_YEAR);
    }

    private void applyCurrentFilter() {
        filterMedications(searchEditText.getText().toString().trim());
    }

    private void showProgress(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(show ? View.GONE : View.VISIBLE);
    }

    private void updateEmptyView() {
        boolean isEmpty = filteredMedications.isEmpty();
        emptyView.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        
        if (isEmpty) {
            String message = allMedications.isEmpty() ? 
                "No medications added yet.\nTap + to add the first medication." :
                "No medications match your current filter.";
            emptyView.setText(message);
        }
    }

    private void setFilter(FilterType filter) {
        currentFilter = filter;
        applyCurrentFilter();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadMedications(); // Refresh when returning from add/edit
    }

    // MedicationAdapter.OnMedicationClickListener implementation
    @Override
    public void onMedicationClick(MedicationEntity medication) {
        Intent intent = new Intent(this, MedicationDetailActivity.class);
        intent.putExtra("medication_id", medication.id);
        intent.putExtra(EXTRA_PATIENT_ID, patientId);
        intent.putExtra(EXTRA_PATIENT_NAME, patientName);
        startActivity(intent);
    }

    @Override
    public void onEditClick(MedicationEntity medication) {
        Intent intent = new Intent(this, com.mihir.alzheimerscaregiver.caretaker.AddMedicationActivity.class);
        intent.putExtra("medication_id", medication.id);
        intent.putExtra(EXTRA_PATIENT_ID, patientId);
        intent.putExtra(EXTRA_PATIENT_NAME, patientName);
        intent.putExtra("edit_mode", true);
        startActivity(intent);
    }

    @Override
    public void onDeleteClick(MedicationEntity medication) {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Delete Medication")
                .setMessage("Are you sure you want to delete '" + medication.getDisplayName() + "'?")
                .setPositiveButton("Delete", (dialog, which) -> deleteMedication(medication))
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public void onToggleStatusClick(MedicationEntity medication) {
        showProgress(true);
        
        boolean newStatus = !medication.isActive;
        
        repository.toggleMedicationStatus(patientId, medication.id, newStatus)
                .addOnSuccessListener(aVoid -> {
                    medication.isActive = newStatus;
                    loadMedications(); // Refresh the list
                    
                    String message = newStatus ? "Medication activated" : "Medication deactivated";
                    Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    showProgress(false);
                    Toast.makeText(this, "Failed to update medication status", Toast.LENGTH_SHORT).show();
                });
    }

    private void deleteMedication(MedicationEntity medication) {
        showProgress(true);
        
        repository.deleteMedication(patientId, medication.id)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Medication deleted", Toast.LENGTH_SHORT).show();
                    loadMedications(); // Refresh the list
                })
                .addOnFailureListener(e -> {
                    showProgress(false);
                    Toast.makeText(this, "Failed to delete medication: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    public static Intent createIntent(android.content.Context context, String patientId, String patientName) {
        Intent intent = new Intent(context, MedicationListActivity.class);
        intent.putExtra(EXTRA_PATIENT_ID, patientId);
        intent.putExtra(EXTRA_PATIENT_NAME, patientName);
        return intent;
    }
}