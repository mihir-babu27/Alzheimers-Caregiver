package com.mihir.alzheimerscaregiver.caretaker.activities;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.mihir.alzheimerscaregiver.caretaker.R;
import com.mihir.alzheimerscaregiver.caretaker.data.entity.EmergencyContactEntity;
import com.mihir.alzheimerscaregiver.caretaker.data.repository.EmergencyContactRepository;

/**
 * AddEditEmergencyContactActivity - Activity for adding or editing emergency contacts in CaretakerApp
 * Supports both create and update operations with input validation
 */
public class AddEditEmergencyContactActivity extends AppCompatActivity {

    // UI Elements
    private TextView titleText;
    private EditText contactNameEditText;
    private EditText phoneNumberEditText;
    private EditText relationshipEditText;
    private CheckBox primaryCheckBox;
    private Button saveButton;
    private Button cancelButton;
    private ProgressBar progressBar;

    // Data
    private EmergencyContactRepository contactRepository;
    private String patientId;
    private String patientName;
    private String contactId; // null for new contacts
    private boolean isEditMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_edit_emergency_contact);

        // Get data from intent
        getDataFromIntent();

        // Initialize components
        initializeViews();
        setupClickListeners();

        // Initialize repository
        contactRepository = new EmergencyContactRepository();

        // Setup UI based on mode (add vs edit)
        setupUI();

        // Pre-fill data if editing
        if (isEditMode) {
            prefillEditData();
        }
    }

    /**
     * Get patient and contact data from intent
     */
    private void getDataFromIntent() {
        patientId = getIntent().getStringExtra("patientId");
        patientName = getIntent().getStringExtra("patientName");
        contactId = getIntent().getStringExtra("contactId");
        
        isEditMode = (contactId != null && !contactId.isEmpty());

        if (patientId == null) {
            Toast.makeText(this, "Error: Patient information not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
    }

    /**
     * Initialize all UI elements
     */
    private void initializeViews() {
        titleText = findViewById(R.id.titleText);
        contactNameEditText = findViewById(R.id.contactNameEditText);
        phoneNumberEditText = findViewById(R.id.phoneNumberEditText);
        relationshipEditText = findViewById(R.id.relationshipEditText);
        primaryCheckBox = findViewById(R.id.primaryCheckBox);
        saveButton = findViewById(R.id.saveButton);
        cancelButton = findViewById(R.id.cancelButton);
        progressBar = findViewById(R.id.progressBar);
    }

    /**
     * Set up click listeners
     */
    private void setupClickListeners() {
        saveButton.setOnClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
            attemptSave();
        });

        cancelButton.setOnClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
            finish();
        });
    }

    /**
     * Setup UI based on add/edit mode
     */
    private void setupUI() {
        if (isEditMode) {
            titleText.setText("Edit Emergency Contact");
            saveButton.setText("Update Contact");
        } else {
            titleText.setText("Add Emergency Contact");
            saveButton.setText("Add Contact");
        }

        // Show patient name in subtitle if available
        if (patientName != null && !patientName.isEmpty()) {
            // You could add a subtitle TextView to show "For: [Patient Name]"
        }
    }

    /**
     * Pre-fill form data when editing existing contact
     */
    private void prefillEditData() {
        String contactName = getIntent().getStringExtra("contactName");
        String contactPhone = getIntent().getStringExtra("contactPhone");
        String contactRelationship = getIntent().getStringExtra("contactRelationship");
        boolean isPrimary = getIntent().getBooleanExtra("isPrimary", false);

        if (contactName != null) {
            contactNameEditText.setText(contactName);
        }
        if (contactPhone != null) {
            phoneNumberEditText.setText(contactPhone);
        }
        if (contactRelationship != null) {
            relationshipEditText.setText(contactRelationship);
        }
        primaryCheckBox.setChecked(isPrimary);
    }

    /**
     * Attempt to save the contact with validation
     */
    private void attemptSave() {
        // Get input values
        String name = contactNameEditText.getText().toString().trim();
        String phone = phoneNumberEditText.getText().toString().trim();
        String relationship = relationshipEditText.getText().toString().trim();
        boolean isPrimary = primaryCheckBox.isChecked();

        // Format phone number with +91 for Indian numbers
        phone = formatIndianPhoneNumber(phone);

        // Validate input
        if (!validateInput(name, phone, relationship)) {
            return;
        }

        // Show loading state
        setLoadingState(true);

        // Create or update contact
        EmergencyContactEntity contact = new EmergencyContactEntity(name, phone, relationship, isPrimary);
        
        if (isEditMode) {
            contact.setId(contactId);
            updateContact(contact);
        } else {
            createContact(contact);
        }
    }

    /**
     * Validate form input
     */
    private boolean validateInput(String name, String phone, String relationship) {
        // Reset any previous errors
        contactNameEditText.setError(null);
        phoneNumberEditText.setError(null);

        boolean isValid = true;

        // Validate name
        if (TextUtils.isEmpty(name)) {
            contactNameEditText.setError("Contact name is required");
            contactNameEditText.requestFocus();
            isValid = false;
        }

        // Validate phone number
        if (TextUtils.isEmpty(phone)) {
            phoneNumberEditText.setError("Phone number is required");
            if (isValid) phoneNumberEditText.requestFocus();
            isValid = false;
        } else if (!isValidPhoneNumber(phone)) {
            phoneNumberEditText.setError("Please enter a valid phone number");
            if (isValid) phoneNumberEditText.requestFocus();
            isValid = false;
        }

        return isValid;
    }

    /**
     * Format phone number with +91 country code for Indian numbers
     */
    private String formatIndianPhoneNumber(String phone) {
        if (TextUtils.isEmpty(phone)) return phone;
        
        // Remove all non-digit characters except + (for international format)
        String cleanPhone = phone.replaceAll("[^0-9+]", "");
        String digits = phone.replaceAll("[^0-9]", "");
        
        // If already formatted with +91, return as is
        if (cleanPhone.startsWith("+91") && digits.length() == 12) {
            return phone;
        }
        
        // If starts with just + but not +91, return as is (international number)
        if (phone.startsWith("+") && !phone.startsWith("+91")) {
            return phone;
        }
        
        // If it's a 10-digit Indian mobile number, add +91
        if (digits.length() == 10 && (digits.startsWith("6") || digits.startsWith("7") || 
                                      digits.startsWith("8") || digits.startsWith("9"))) {
            return "+91-" + digits;
        }
        
        // If it's 12 digits and starts with 91, format it properly
        if (digits.length() == 12 && digits.startsWith("91")) {
            String mobileNumber = digits.substring(2);
            return "+91-" + mobileNumber;
        }
        
        // If user entered +91 without the number, keep it as is
        if (cleanPhone.equals("+91")) {
            return phone;
        }
        
        // For other cases (landlines, other formats), return as entered
        return phone;
    }

    /**
     * Basic phone number validation for Indian numbers
     */
    private boolean isValidPhoneNumber(String phone) {
        if (TextUtils.isEmpty(phone)) return false;
        
        // Remove non-digit characters for validation
        String digits = phone.replaceAll("[^0-9]", "");
        
        // For Indian numbers: accept 10 digits (mobile) or 12 digits with country code (91xxxxxxxxxx)
        if (digits.length() == 10) {
            // Valid Indian mobile number starts with 6, 7, 8, or 9
            return digits.startsWith("6") || digits.startsWith("7") || 
                   digits.startsWith("8") || digits.startsWith("9");
        }
        
        // For international format: 11-15 digits
        if (digits.length() >= 11 && digits.length() <= 15) {
            // If starts with 91, check if the remaining 10 digits are valid Indian mobile
            if (digits.startsWith("91") && digits.length() == 12) {
                String mobileDigits = digits.substring(2);
                return mobileDigits.startsWith("6") || mobileDigits.startsWith("7") || 
                       mobileDigits.startsWith("8") || mobileDigits.startsWith("9");
            }
            return true; // Accept other international formats
        }
        
        return false;
    }

    /**
     * Create a new contact
     */
    private void createContact(EmergencyContactEntity contact) {
        contactRepository.createEmergencyContact(patientId, contact, 
                new EmergencyContactRepository.OnContactOperationListener() {
            @Override
            public void onSuccess(String message) {
                setLoadingState(false);
                Toast.makeText(AddEditEmergencyContactActivity.this, message, Toast.LENGTH_SHORT).show();
                setResult(RESULT_OK);
                finish();
            }

            @Override
            public void onError(String error) {
                setLoadingState(false);
                Toast.makeText(AddEditEmergencyContactActivity.this, error, Toast.LENGTH_LONG).show();
            }
        });
    }

    /**
     * Update existing contact
     */
    private void updateContact(EmergencyContactEntity contact) {
        contactRepository.updateEmergencyContact(patientId, contact, 
                new EmergencyContactRepository.OnContactOperationListener() {
            @Override
            public void onSuccess(String message) {
                setLoadingState(false);
                Toast.makeText(AddEditEmergencyContactActivity.this, message, Toast.LENGTH_SHORT).show();
                setResult(RESULT_OK);
                finish();
            }

            @Override
            public void onError(String error) {
                setLoadingState(false);
                Toast.makeText(AddEditEmergencyContactActivity.this, error, Toast.LENGTH_LONG).show();
            }
        });
    }

    /**
     * Set loading state for UI
     */
    private void setLoadingState(boolean isLoading) {
        if (isLoading) {
            progressBar.setVisibility(View.VISIBLE);
            saveButton.setEnabled(false);
            cancelButton.setEnabled(false);
            
            // Disable form inputs
            contactNameEditText.setEnabled(false);
            phoneNumberEditText.setEnabled(false);
            relationshipEditText.setEnabled(false);
            primaryCheckBox.setEnabled(false);
        } else {
            progressBar.setVisibility(View.GONE);
            saveButton.setEnabled(true);
            cancelButton.setEnabled(true);
            
            // Re-enable form inputs
            contactNameEditText.setEnabled(true);
            phoneNumberEditText.setEnabled(true);
            relationshipEditText.setEnabled(true);
            primaryCheckBox.setEnabled(true);
        }
    }

    /**
     * Handle back button press
     */
    @Override
    public void onBackPressed() {
        // Check if there are unsaved changes
        if (hasUnsavedChanges()) {
            showUnsavedChangesDialog();
        } else {
            super.onBackPressed();
        }
    }

    /**
     * Check if there are unsaved changes
     */
    private boolean hasUnsavedChanges() {
        String currentName = contactNameEditText.getText().toString().trim();
        String currentPhone = phoneNumberEditText.getText().toString().trim();
        String currentRelationship = relationshipEditText.getText().toString().trim();
        boolean currentPrimary = primaryCheckBox.isChecked();

        if (isEditMode) {
            String originalName = getIntent().getStringExtra("contactName");
            String originalPhone = getIntent().getStringExtra("contactPhone");
            String originalRelationship = getIntent().getStringExtra("contactRelationship");
            boolean originalPrimary = getIntent().getBooleanExtra("isPrimary", false);

            return !currentName.equals(originalName != null ? originalName : "") ||
                   !currentPhone.equals(originalPhone != null ? originalPhone : "") ||
                   !currentRelationship.equals(originalRelationship != null ? originalRelationship : "") ||
                   currentPrimary != originalPrimary;
        } else {
            return !currentName.isEmpty() || !currentPhone.isEmpty() || !currentRelationship.isEmpty() || currentPrimary;
        }
    }

    /**
     * Show dialog for unsaved changes
     */
    private void showUnsavedChangesDialog() {
        new android.app.AlertDialog.Builder(this)
                .setTitle("Unsaved Changes")
                .setMessage("You have unsaved changes. Are you sure you want to leave?")
                .setPositiveButton("Leave", (dialog, which) -> {
                    finish();
                })
                .setNegativeButton("Stay", null)
                .show();
    }
}