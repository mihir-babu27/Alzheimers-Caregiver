package com.mihir.alzheimerscaregiver.caretaker.activities;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.mihir.alzheimerscaregiver.caretaker.R;
import com.mihir.alzheimerscaregiver.caretaker.adapters.EmergencyContactAdapter;
import com.mihir.alzheimerscaregiver.caretaker.data.entity.EmergencyContactEntity;
import com.mihir.alzheimerscaregiver.caretaker.data.repository.EmergencyContactRepository;

import java.util.ArrayList;
import java.util.List;

/**
 * EmergencyContactsActivity - Main activity for managing emergency contacts in CaretakerApp
 * Displays list of emergency contacts for a specific patient with CRUD operations
 */
public class EmergencyContactsActivity extends AppCompatActivity 
        implements EmergencyContactAdapter.OnEmergencyContactInteractionListener {

    // UI Elements
    private ImageButton backButton;
    private RecyclerView emergencyContactsRecyclerView;
    private FloatingActionButton addContactFab;

    // Data and adapters
    private EmergencyContactAdapter contactAdapter;
    private EmergencyContactRepository contactRepository;
    private List<EmergencyContactEntity> emergencyContacts;
    
    // Patient information
    private String patientId;
    private String patientName;

    // Permission request code
    private static final int CALL_PHONE_PERMISSION_REQUEST = 101;

    // Request codes for activities
    private static final int ADD_CONTACT_REQUEST = 1001;
    private static final int EDIT_CONTACT_REQUEST = 1002;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_emergency_contacts);

        // Get patient information from intent
        getPatientInfoFromIntent();

        // Initialize components
        initializeViews();
        setupClickListeners();
        setupRecyclerView();
        
        // Initialize repository and load contacts
        contactRepository = new EmergencyContactRepository();
        loadEmergencyContacts();

        // Check phone permission
        checkPhonePermission();
    }

    /**
     * Get patient ID and name from intent
     */
    private void getPatientInfoFromIntent() {
        patientId = getIntent().getStringExtra("patientId");
        patientName = getIntent().getStringExtra("patientName");
        
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
        backButton = findViewById(R.id.backButton);
        emergencyContactsRecyclerView = findViewById(R.id.emergencyContactsRecyclerView);
        addContactFab = findViewById(R.id.addContactFab);
    }

    /**
     * Set up click listeners for interactive elements
     */
    private void setupClickListeners() {
        // Back button
        backButton.setOnClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
            finish();
        });

        // Add contact FAB
        addContactFab.setOnClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
            openAddContactActivity();
        });
    }

    /**
     * Set up RecyclerView with adapter and ItemTouchHelper for swipe-to-delete
     */
    private void setupRecyclerView() {
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        emergencyContactsRecyclerView.setLayoutManager(layoutManager);

        emergencyContacts = new ArrayList<>();
        contactAdapter = new EmergencyContactAdapter(this, emergencyContacts);
        contactAdapter.setListener(this);
        emergencyContactsRecyclerView.setAdapter(contactAdapter);

        // Add swipe-to-delete functionality
        ItemTouchHelper helper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0, 
                ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, 
                                @NonNull RecyclerView.ViewHolder viewHolder, 
                                @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getAdapterPosition();
                if (position >= 0 && position < emergencyContacts.size()) {
                    onItemSwipedToDelete(emergencyContacts.get(position), position);
                }
            }
        });
        helper.attachToRecyclerView(emergencyContactsRecyclerView);
    }

    /**
     * Load emergency contacts from Firebase
     */
    private void loadEmergencyContacts() {
        contactRepository.getAllEmergencyContacts(patientId, 
                new EmergencyContactRepository.FirebaseCallback<List<EmergencyContactEntity>>() {
            @Override
            public void onSuccess(List<EmergencyContactEntity> contacts) {
                emergencyContacts.clear();
                if (contacts != null) {
                    emergencyContacts.addAll(contacts);
                }
                contactAdapter.notifyDataSetChanged();
            }

            @Override
            public void onError(String error) {
                Toast.makeText(EmergencyContactsActivity.this, error, Toast.LENGTH_LONG).show();
            }
        });
    }

    /**
     * Open Add Emergency Contact activity
     */
    private void openAddContactActivity() {
        Intent intent = new Intent(this, AddEditEmergencyContactActivity.class);
        intent.putExtra("patientId", patientId);
        intent.putExtra("patientName", patientName);
        startActivityForResult(intent, ADD_CONTACT_REQUEST);
    }

    /**
     * Open Edit Emergency Contact activity
     */
    private void openEditContactActivity(EmergencyContactEntity contact) {
        Intent intent = new Intent(this, AddEditEmergencyContactActivity.class);
        intent.putExtra("patientId", patientId);
        intent.putExtra("patientName", patientName);
        intent.putExtra("contactId", contact.getId());
        intent.putExtra("contactName", contact.getName());
        intent.putExtra("contactPhone", contact.getPhoneNumber());
        intent.putExtra("contactRelationship", contact.getRelationship());
        intent.putExtra("isPrimary", contact.isPrimary());
        startActivityForResult(intent, EDIT_CONTACT_REQUEST);
    }

    /**
     * Check if CALL_PHONE permission is granted, request if not
     */
    private void checkPhonePermission() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CALL_PHONE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.CALL_PHONE},
                    CALL_PHONE_PERMISSION_REQUEST);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                          @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == CALL_PHONE_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Phone permission granted. You can now make calls directly.", 
                              Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Phone permission denied. Calls will open the dialer instead.", 
                              Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (resultCode == RESULT_OK) {
            if (requestCode == ADD_CONTACT_REQUEST || requestCode == EDIT_CONTACT_REQUEST) {
                // Refresh the contacts list
                loadEmergencyContacts();
            }
        }
    }

    // EmergencyContactAdapter.OnEmergencyContactInteractionListener implementation
    
    @Override
    public void onItemClicked(EmergencyContactEntity contact) {
        openEditContactActivity(contact);
    }

    @Override
    public void onCallButtonClicked(EmergencyContactEntity contact) {
        makePhoneCall(contact.getPhoneNumber(), contact.getName());
    }

    @Override
    public void onItemSwipedToDelete(EmergencyContactEntity contact, int position) {
        showDeleteConfirmationDialog(contact, position);
    }

    @Override
    public void onSetPrimary(EmergencyContactEntity contact) {
        setPrimaryContact(contact);
    }

    /**
     * Make a phone call to the contact
     */
    private void makePhoneCall(String phoneNumber, String contactName) {
        try {
            Intent callIntent = new Intent(Intent.ACTION_CALL);
            callIntent.setData(Uri.parse("tel:" + phoneNumber));

            if (callIntent.resolveActivity(getPackageManager()) != null) {
                if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CALL_PHONE) 
                        == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "Calling " + contactName + "...", Toast.LENGTH_SHORT).show();
                    startActivity(callIntent);
                } else {
                    // Fallback to dialer
                    Intent dialIntent = new Intent(Intent.ACTION_DIAL);
                    dialIntent.setData(Uri.parse("tel:" + phoneNumber));
                    startActivity(dialIntent);
                    Toast.makeText(this, "Opening dialer for " + contactName, Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "Unable to make call", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Error making call: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Show confirmation dialog for deleting a contact
     */
    private void showDeleteConfirmationDialog(EmergencyContactEntity contact, int position) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Contact")
                .setMessage("Are you sure you want to delete '" + contact.getName() + "'?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    deleteContact(contact, position);
                })
                .setNegativeButton("Cancel", (dialog, which) -> {
                    // Restore the item in RecyclerView
                    contactAdapter.notifyItemChanged(position);
                })
                .setCancelable(false)
                .show();
    }

    /**
     * Delete a contact from Firebase
     */
    private void deleteContact(EmergencyContactEntity contact, int position) {
        contactRepository.deleteEmergencyContact(patientId, contact.getId(), 
                new EmergencyContactRepository.OnContactOperationListener() {
            @Override
            public void onSuccess(String message) {
                Toast.makeText(EmergencyContactsActivity.this, message, Toast.LENGTH_SHORT).show();
                loadEmergencyContacts(); // Refresh the list
            }

            @Override
            public void onError(String error) {
                Toast.makeText(EmergencyContactsActivity.this, error, Toast.LENGTH_LONG).show();
                contactAdapter.notifyItemChanged(position); // Restore the item
            }
        });
    }

    /**
     * Set a contact as primary
     */
    private void setPrimaryContact(EmergencyContactEntity contact) {
        contactRepository.setPrimaryContact(patientId, contact.getId(), 
                new EmergencyContactRepository.OnContactOperationListener() {
            @Override
            public void onSuccess(String message) {
                Toast.makeText(EmergencyContactsActivity.this, message, Toast.LENGTH_SHORT).show();
                loadEmergencyContacts(); // Refresh to show updated primary status
            }

            @Override
            public void onError(String error) {
                Toast.makeText(EmergencyContactsActivity.this, error, Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh contacts when returning to this activity
        loadEmergencyContacts();
    }
}