package com.mihir.alzheimerscaregiver.caretaker.data.repository;

import android.util.Log;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.mihir.alzheimerscaregiver.caretaker.data.entity.PatientProfileEntity;

import java.util.HashMap;
import java.util.Map;

/**
 * PatientProfileRepository - Handles Firebase operations for patient profile data
 * Provides comprehensive CRUD operations for patient profiles with proper error handling
 */
public class PatientProfileRepository {

    private static final String TAG = "PatientProfileRepo";
    private static final String COLLECTION_PATIENTS = "patients";
    private static final String SUBCOLLECTION_PROFILE = "profile";
    private static final String DOCUMENT_DETAILS = "details";
    
    private final FirebaseFirestore db;

    public PatientProfileRepository() {
        this.db = FirebaseFirestore.getInstance();
    }

    // Callback interfaces
    public interface OnProfileOperationListener {
        void onSuccess(String message);
        void onError(String error);
    }

    public interface OnProfileLoadListener {
        void onSuccess(PatientProfileEntity profile);
        void onError(String error);
        void onNotFound(); // When no profile exists yet
    }

    /**
     * Create or update a patient profile
     */
    public void savePatientProfile(String patientId, PatientProfileEntity profile, 
                                  OnProfileOperationListener listener) {
        if (patientId == null || patientId.trim().isEmpty()) {
            listener.onError("Patient ID is required");
            return;
        }

        if (profile == null) {
            listener.onError("Profile data is required");
            return;
        }

        // Convert entity to Firebase-compatible map
        Map<String, Object> profileData = entityToMap(profile);
        
        // Ensure patientId matches
        profileData.put("patientId", patientId);

        Log.d(TAG, "Saving profile for patient: " + patientId);

        db.collection(COLLECTION_PATIENTS)
                .document(patientId)
                .collection(SUBCOLLECTION_PROFILE)
                .document(DOCUMENT_DETAILS)
                .set(profileData)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Profile saved successfully for patient: " + patientId);
                    listener.onSuccess("Profile saved successfully");
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error saving profile for patient: " + patientId, e);
                    listener.onError("Failed to save profile: " + e.getMessage());
                });
    }

    /**
     * Update specific fields of a patient profile
     */
    public void updatePatientProfile(String patientId, Map<String, Object> updates, 
                                   OnProfileOperationListener listener) {
        if (patientId == null || patientId.trim().isEmpty()) {
            listener.onError("Patient ID is required");
            return;
        }

        if (updates == null || updates.isEmpty()) {
            listener.onError("Update data is required");
            return;
        }

        // Add timestamp to updates
        updates.put("lastUpdated", com.google.firebase.Timestamp.now());

        Log.d(TAG, "Updating profile fields for patient: " + patientId);

        db.collection(COLLECTION_PATIENTS)
                .document(patientId)
                .collection(SUBCOLLECTION_PROFILE)
                .document(DOCUMENT_DETAILS)
                .update(updates)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Profile updated successfully for patient: " + patientId);
                    listener.onSuccess("Profile updated successfully");
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error updating profile for patient: " + patientId, e);
                    listener.onError("Failed to update profile: " + e.getMessage());
                });
    }

    /**
     * Load patient profile from Firebase
     */
    public void getPatientProfile(String patientId, OnProfileLoadListener listener) {
        if (patientId == null || patientId.trim().isEmpty()) {
            listener.onError("Patient ID is required");
            return;
        }

        Log.d(TAG, "Loading profile for patient: " + patientId);

        db.collection(COLLECTION_PATIENTS)
                .document(patientId)
                .collection(SUBCOLLECTION_PROFILE)
                .document(DOCUMENT_DETAILS)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        try {
                            PatientProfileEntity profile = documentToEntity(documentSnapshot);
                            Log.d(TAG, "Profile loaded successfully for patient: " + patientId);
                            listener.onSuccess(profile);
                        } catch (Exception e) {
                            Log.e(TAG, "Error parsing profile for patient: " + patientId, e);
                            listener.onError("Error parsing profile data: " + e.getMessage());
                        }
                    } else {
                        Log.d(TAG, "No profile found for patient: " + patientId);
                        listener.onNotFound();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error loading profile for patient: " + patientId, e);
                    listener.onError("Failed to load profile: " + e.getMessage());
                });
    }

    /**
     * Check if a patient profile exists
     */
    public void checkProfileExists(String patientId, OnProfileOperationListener listener) {
        if (patientId == null || patientId.trim().isEmpty()) {
            listener.onError("Patient ID is required");
            return;
        }

        Log.d(TAG, "Checking if profile exists for patient: " + patientId);

        db.collection(COLLECTION_PATIENTS)
                .document(patientId)
                .collection(SUBCOLLECTION_PROFILE)
                .document(DOCUMENT_DETAILS)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        listener.onSuccess("Profile exists");
                    } else {
                        listener.onError("Profile does not exist");
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error checking profile existence for patient: " + patientId, e);
                    listener.onError("Failed to check profile: " + e.getMessage());
                });
    }

    /**
     * Delete a patient profile
     */
    public void deletePatientProfile(String patientId, OnProfileOperationListener listener) {
        if (patientId == null || patientId.trim().isEmpty()) {
            listener.onError("Patient ID is required");
            return;
        }

        Log.d(TAG, "Deleting profile for patient: " + patientId);

        db.collection(COLLECTION_PATIENTS)
                .document(patientId)
                .collection(SUBCOLLECTION_PROFILE)
                .document(DOCUMENT_DETAILS)
                .delete()
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Profile deleted successfully for patient: " + patientId);
                    listener.onSuccess("Profile deleted successfully");
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error deleting profile for patient: " + patientId, e);
                    listener.onError("Failed to delete profile: " + e.getMessage());
                });
    }

    // Helper methods

    /**
     * Convert PatientProfileEntity to Firebase-compatible Map
     */
    private Map<String, Object> entityToMap(PatientProfileEntity profile) {
        Map<String, Object> map = new HashMap<>();
        
        // Basic information
        if (profile.getPatientId() != null) map.put("patientId", profile.getPatientId());
        if (profile.getName() != null) map.put("name", profile.getName());
        if (profile.getBirthYear() != null) map.put("birthYear", profile.getBirthYear());
        if (profile.getBirthplace() != null) map.put("birthplace", profile.getBirthplace());
        if (profile.getProfession() != null) map.put("profession", profile.getProfession());
        if (profile.getOtherDetails() != null) map.put("otherDetails", profile.getOtherDetails());
        
        // Extended information
        if (profile.getAge() != null) map.put("age", profile.getAge());
        if (profile.getHobbies() != null) map.put("hobbies", profile.getHobbies());
        if (profile.getFamilyInfo() != null) map.put("familyInfo", profile.getFamilyInfo());
        if (profile.getFavoritePlaces() != null) map.put("favoritePlaces", profile.getFavoritePlaces());
        if (profile.getPersonalityTraits() != null) map.put("personalityTraits", profile.getPersonalityTraits());
        if (profile.getSignificantEvents() != null) map.put("significantEvents", profile.getSignificantEvents());
        if (profile.getMedicalHistory() != null) map.put("medicalHistory", profile.getMedicalHistory());
        if (profile.getAllergies() != null) map.put("allergies", profile.getAllergies());
        if (profile.getEmergencyNotes() != null) map.put("emergencyNotes", profile.getEmergencyNotes());
        
        // Metadata
        if (profile.getCaretakerId() != null) map.put("caretakerId", profile.getCaretakerId());
        if (profile.getCaretakerEmail() != null) map.put("caretakerEmail", profile.getCaretakerEmail());
        
        // Timestamps
        map.put("lastUpdated", com.google.firebase.Timestamp.now());
        if (profile.getCreatedAt() != null) {
            map.put("createdAt", profile.getCreatedAt());
        } else {
            map.put("createdAt", com.google.firebase.Timestamp.now());
        }
        
        return map;
    }

    /**
     * Convert Firebase DocumentSnapshot to PatientProfileEntity
     */
    private PatientProfileEntity documentToEntity(DocumentSnapshot document) {
        PatientProfileEntity profile = new PatientProfileEntity();
        
        // Basic information
        profile.setPatientId(document.getString("patientId"));
        profile.setName(document.getString("name"));
        profile.setBirthYear(document.getString("birthYear"));
        profile.setBirthplace(document.getString("birthplace"));
        profile.setProfession(document.getString("profession"));
        profile.setOtherDetails(document.getString("otherDetails"));
        
        // Extended information
        profile.setAge(document.getString("age"));
        profile.setHobbies(document.getString("hobbies"));
        profile.setFamilyInfo(document.getString("familyInfo"));
        profile.setFavoritePlaces(document.getString("favoritePlaces"));
        profile.setPersonalityTraits(document.getString("personalityTraits"));
        profile.setSignificantEvents(document.getString("significantEvents"));
        profile.setMedicalHistory(document.getString("medicalHistory"));
        profile.setAllergies(document.getString("allergies"));
        profile.setEmergencyNotes(document.getString("emergencyNotes"));
        
        // Metadata
        profile.setCaretakerId(document.getString("caretakerId"));
        profile.setCaretakerEmail(document.getString("caretakerEmail"));
        
        // Timestamps
        profile.setLastUpdated(document.getTimestamp("lastUpdated"));
        profile.setCreatedAt(document.getTimestamp("createdAt"));
        
        // Handle birthYear data type conversion (could be Long or String)
        if (document.contains("birthYear") && document.get("birthYear") != null) {
            Object birthYearObj = document.get("birthYear");
            if (birthYearObj instanceof Long) {
                profile.setBirthYear(String.valueOf((Long) birthYearObj));
            } else if (birthYearObj instanceof String) {
                profile.setBirthYear((String) birthYearObj);
            }
        }
        
        return profile;
    }
}