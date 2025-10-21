package com.mihir.alzheimerscaregiver.caretaker.data.repository;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.mihir.alzheimerscaregiver.caretaker.data.entity.EmergencyContactEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * EmergencyContactRepository - Handles Firebase CRUD operations for emergency contacts in CaretakerApp
 * Manages patient-specific emergency contacts with proper error handling
 */
public class EmergencyContactRepository {

    private final FirebaseFirestore db;
    private final FirebaseAuth auth;

    public EmergencyContactRepository() {
        this.db = FirebaseFirestore.getInstance();
        this.auth = FirebaseAuth.getInstance();
    }

    /**
     * Get Firebase collection reference for a specific patient's emergency contacts
     */
    private CollectionReference getEmergencyContactsRef(String patientId) {
        return db.collection("patients").document(patientId).collection("contacts");
    }

    /**
     * Callback interface for Firebase operations
     */
    public interface FirebaseCallback<T> {
        void onSuccess(T result);
        void onError(String error);
    }

    /**
     * Callback interface for contact operations with notifications
     */
    public interface OnContactOperationListener {
        void onSuccess(String message);
        void onError(String error);
    }

    /**
     * Get all emergency contacts for a specific patient
     */
    public void getAllEmergencyContacts(String patientId, FirebaseCallback<List<EmergencyContactEntity>> callback) {
        getEmergencyContactsRef(patientId)
                .orderBy("name")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<EmergencyContactEntity> contacts = new ArrayList<>();
                    if (queryDocumentSnapshots != null) {
                        for (var doc : queryDocumentSnapshots) {
                            EmergencyContactEntity contact = doc.toObject(EmergencyContactEntity.class);
                            if (contact != null) {
                                contact.id = doc.getId();
                                contacts.add(contact);
                            }
                        }
                    }
                    callback.onSuccess(contacts);
                })
                .addOnFailureListener(e -> {
                    callback.onError("Failed to load emergency contacts: " + e.getMessage());
                });
    }

    /**
     * Create a new emergency contact
     */
    public void createEmergencyContact(String patientId, EmergencyContactEntity contact, 
                                     OnContactOperationListener listener) {
        if (contact.id == null || contact.id.isEmpty()) {
            DocumentReference docRef = getEmergencyContactsRef(patientId).document();
            contact.id = docRef.getId();
        }

        getEmergencyContactsRef(patientId).document(contact.id)
                .set(contact)
                .addOnSuccessListener(aVoid -> {
                    listener.onSuccess("Emergency contact added successfully");
                })
                .addOnFailureListener(e -> {
                    listener.onError("Failed to add emergency contact: " + e.getMessage());
                });
    }

    /**
     * Update an existing emergency contact
     */
    public void updateEmergencyContact(String patientId, EmergencyContactEntity contact, 
                                     OnContactOperationListener listener) {
        if (contact.id == null || contact.id.isEmpty()) {
            listener.onError("Contact ID is required for update");
            return;
        }

        getEmergencyContactsRef(patientId).document(contact.id)
                .set(contact)
                .addOnSuccessListener(aVoid -> {
                    listener.onSuccess("Emergency contact updated successfully");
                })
                .addOnFailureListener(e -> {
                    listener.onError("Failed to update emergency contact: " + e.getMessage());
                });
    }

    /**
     * Delete an emergency contact
     */
    public void deleteEmergencyContact(String patientId, String contactId, 
                                     OnContactOperationListener listener) {
        if (contactId == null || contactId.isEmpty()) {
            listener.onError("Contact ID is required for deletion");
            return;
        }

        getEmergencyContactsRef(patientId).document(contactId)
                .delete()
                .addOnSuccessListener(aVoid -> {
                    listener.onSuccess("Emergency contact deleted successfully");
                })
                .addOnFailureListener(e -> {
                    listener.onError("Failed to delete emergency contact: " + e.getMessage());
                });
    }

    /**
     * Set a contact as primary (and unset others)
     */
    public void setPrimaryContact(String patientId, String contactId, 
                                OnContactOperationListener listener) {
        if (contactId == null || contactId.isEmpty()) {
            listener.onError("Contact ID is required");
            return;
        }

        // First, get all contacts to unset any existing primary
        getAllEmergencyContacts(patientId, new FirebaseCallback<List<EmergencyContactEntity>>() {
            @Override
            public void onSuccess(List<EmergencyContactEntity> contacts) {
                // Batch operation to update all contacts
                for (EmergencyContactEntity contact : contacts) {
                    contact.isPrimary = contact.id.equals(contactId);
                    updateEmergencyContact(patientId, contact, new OnContactOperationListener() {
                        @Override
                        public void onSuccess(String message) {
                            // Individual update success - don't notify for each one
                        }

                        @Override
                        public void onError(String error) {
                            listener.onError(error);
                        }
                    });
                }
                listener.onSuccess("Primary contact updated successfully");
            }

            @Override
            public void onError(String error) {
                listener.onError("Failed to set primary contact: " + error);
            }
        });
    }

    /**
     * Search emergency contacts by name or phone
     */
    public void searchEmergencyContacts(String patientId, String query, 
                                      FirebaseCallback<List<EmergencyContactEntity>> callback) {
        getAllEmergencyContacts(patientId, new FirebaseCallback<List<EmergencyContactEntity>>() {
            @Override
            public void onSuccess(List<EmergencyContactEntity> contacts) {
                List<EmergencyContactEntity> filteredContacts = new ArrayList<>();
                String lowerQuery = query.toLowerCase().trim();
                
                for (EmergencyContactEntity contact : contacts) {
                    if (contact.name != null && contact.name.toLowerCase().contains(lowerQuery) ||
                        contact.phoneNumber != null && contact.phoneNumber.contains(query) ||
                        contact.relationship != null && contact.relationship.toLowerCase().contains(lowerQuery)) {
                        filteredContacts.add(contact);
                    }
                }
                callback.onSuccess(filteredContacts);
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    /**
     * Get primary emergency contact for a patient
     */
    public void getPrimaryContact(String patientId, FirebaseCallback<EmergencyContactEntity> callback) {
        getEmergencyContactsRef(patientId)
                .whereEqualTo("isPrimary", true)
                .limit(1)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (queryDocumentSnapshots != null && !queryDocumentSnapshots.isEmpty()) {
                        var doc = queryDocumentSnapshots.getDocuments().get(0);
                        EmergencyContactEntity contact = doc.toObject(EmergencyContactEntity.class);
                        if (contact != null) {
                            contact.id = doc.getId();
                            callback.onSuccess(contact);
                        } else {
                            callback.onSuccess(null);
                        }
                    } else {
                        callback.onSuccess(null);
                    }
                })
                .addOnFailureListener(e -> {
                    callback.onError("Failed to get primary contact: " + e.getMessage());
                });
    }

    /**
     * Validate contact data before saving
     */
    public boolean isValidContact(EmergencyContactEntity contact) {
        return contact != null &&
               contact.name != null && !contact.name.trim().isEmpty() &&
               contact.phoneNumber != null && !contact.phoneNumber.trim().isEmpty();
    }

    /**
     * Check if current user is authenticated
     */
    public boolean isAuthenticated() {
        return auth.getCurrentUser() != null;
    }

    /**
     * Get current caretaker ID
     */
    public String getCurrentCaretakerId() {
        return auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : null;
    }
}