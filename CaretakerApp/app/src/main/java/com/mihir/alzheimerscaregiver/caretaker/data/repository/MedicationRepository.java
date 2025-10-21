package com.mihir.alzheimerscaregiver.caretaker.data.repository;

import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.android.gms.tasks.Task;
import com.mihir.alzheimerscaregiver.caretaker.data.entity.MedicationEntity;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

/**
 * MedicationRepository for CaretakerApp - Handles CRUD operations for medications
 * Manages medications at the patient level for caretaker oversight
 */
public class MedicationRepository {

    private static final String COLLECTION_PATIENTS = "patients";
    private static final String SUBCOLLECTION_MEDICATIONS = "reminders"; // Changed from "medications" to "reminders"
    
    private final FirebaseFirestore db;
    
    public MedicationRepository() {
        this.db = FirebaseFirestore.getInstance();
    }

    /**
     * Get medications collection reference for a specific patient
     */
    private CollectionReference getMedicationsCollection(String patientId) {
        return db.collection(COLLECTION_PATIENTS)
                .document(patientId)
                .collection(SUBCOLLECTION_MEDICATIONS);
    }

    /**
     * Add a new medication for a patient
     */
    public Task<DocumentReference> addMedication(String patientId, MedicationEntity medication) {
        medication.patientId = patientId;
        medication.createdAt = System.currentTimeMillis();
        medication.updatedAt = medication.createdAt;
        
        return getMedicationsCollection(patientId).add(medication);
    }

    /**
     * Update an existing medication
     */
    public Task<Void> updateMedication(String patientId, String medicationId, MedicationEntity medication) {
        medication.updatedAt = System.currentTimeMillis();
        
        return getMedicationsCollection(patientId)
                .document(medicationId)
                .set(medication);
    }

    /**
     * Update specific fields of a medication
     */
    public Task<Void> updateMedicationFields(String patientId, String medicationId, Map<String, Object> updates) {
        updates.put("updatedAt", System.currentTimeMillis());
        
        return getMedicationsCollection(patientId)
                .document(medicationId)
                .update(updates);
    }

    /**
     * Delete a medication
     */
    public Task<Void> deleteMedication(String patientId, String medicationId) {
        return getMedicationsCollection(patientId)
                .document(medicationId)
                .delete();
    }

    /**
     * Get all medications for a patient
     */
    public Task<QuerySnapshot> getAllMedications(String patientId) {
        // Remove orderBy to avoid filtering out existing reminders that don't have createdAt field
        return getMedicationsCollection(patientId).get();
    }

    /**
     * Get active medications for a patient
     */
    public Task<QuerySnapshot> getActiveMedications(String patientId) {
        return getMedicationsCollection(patientId)
                .whereEqualTo("isActive", true)
                .orderBy("time")
                .get();
    }

    /**
     * Get medications due today for a patient
     */
    public Task<QuerySnapshot> getTodaysMedications(String patientId) {
        long startOfDay = getStartOfDay();
        long endOfDay = getEndOfDay();
        
        return getMedicationsCollection(patientId)
                .whereEqualTo("isActive", true)
                .whereGreaterThanOrEqualTo("nextDueTime", startOfDay)
                .whereLessThan("nextDueTime", endOfDay)
                .orderBy("nextDueTime")
                .get();
    }

    /**
     * Get overdue medications for a patient
     */
    public Task<QuerySnapshot> getOverdueMedications(String patientId) {
        long currentTime = System.currentTimeMillis();
        
        return getMedicationsCollection(patientId)
                .whereEqualTo("isActive", true)
                .whereLessThan("nextDueTime", currentTime)
                .orderBy("nextDueTime")
                .get();
    }

    /**
     * Search medications by name
     */
    public Task<QuerySnapshot> searchMedicationsByName(String patientId, String searchTerm) {
        return getMedicationsCollection(patientId)
                .whereGreaterThanOrEqualTo("name", searchTerm)
                .whereLessThanOrEqualTo("name", searchTerm + "\uf8ff")
                .get();
    }

    /**
     * Get medications by category
     */
    public Task<QuerySnapshot> getMedicationsByCategory(String patientId, String category) {
        return getMedicationsCollection(patientId)
                .whereEqualTo("category", category)
                .whereEqualTo("isActive", true)
                .orderBy("time")
                .get();
    }

    /**
     * Toggle medication active status
     */
    public Task<Void> toggleMedicationStatus(String patientId, String medicationId, boolean isActive) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("isActive", isActive);
        updates.put("updatedAt", System.currentTimeMillis());
        
        return getMedicationsCollection(patientId)
                .document(medicationId)
                .update(updates);
    }

    /**
     * Update medication's next due time (for reminders)
     */
    public Task<Void> updateNextDueTime(String patientId, String medicationId, long nextDueTime) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("nextDueTime", nextDueTime);
        updates.put("updatedAt", System.currentTimeMillis());
        
        return getMedicationsCollection(patientId)
                .document(medicationId)
                .update(updates);
    }

    /**
     * Get a specific medication by ID
     */
    public Task<com.google.firebase.firestore.DocumentSnapshot> getMedication(String patientId, String medicationId) {
        return getMedicationsCollection(patientId)
                .document(medicationId)
                .get();
    }

    /**
     * Batch update multiple medications
     */
    public Task<Void> batchUpdateMedications(String patientId, List<MedicationUpdateBatch> updates) {
        com.google.firebase.firestore.WriteBatch batch = db.batch();
        
        for (MedicationUpdateBatch update : updates) {
            DocumentReference docRef = getMedicationsCollection(patientId).document(update.medicationId);
            batch.update(docRef, update.updates);
        }
        
        return batch.commit();
    }

    /**
     * Get medication statistics for a patient
     */
    public Task<QuerySnapshot> getMedicationStats(String patientId) {
        return getMedicationsCollection(patientId).get();
    }

    /**
     * Debug method to check different Firebase paths for medications
     * This will help identify where existing medications are stored
     */
    public Task<QuerySnapshot> debugCheckFirebasePaths(String patientId) {
        android.util.Log.d("MedicationRepository", "Checking Firebase path: patients/" + patientId + "/reminders");
        
        return getMedicationsCollection(patientId).get()
                .continueWithTask(task -> {
                    if (task.isSuccessful()) {
                        QuerySnapshot result = task.getResult();
                        android.util.Log.d("MedicationRepository", "Found " + result.size() + " documents in patients/" + patientId + "/reminders");
                        
                        // Also check the old medications path for comparison
                        return db.collection("patients")
                                .document(patientId)
                                .collection("medications")
                                .get()
                                .continueWithTask(task2 -> {
                                    if (task2.isSuccessful()) {
                                        QuerySnapshot result2 = task2.getResult();
                                        android.util.Log.d("MedicationRepository", "Found " + result2.size() + " documents in patients/" + patientId + "/medications (old path)");
                                    }
                                    return com.google.android.gms.tasks.Tasks.forResult(result);
                                });
                    }
                    return task;
                });
    }

    // Helper methods
    private long getStartOfDay() {
        java.util.Calendar calendar = java.util.Calendar.getInstance();
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0);
        calendar.set(java.util.Calendar.MINUTE, 0);
        calendar.set(java.util.Calendar.SECOND, 0);
        calendar.set(java.util.Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    private long getEndOfDay() {
        java.util.Calendar calendar = java.util.Calendar.getInstance();
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 23);
        calendar.set(java.util.Calendar.MINUTE, 59);
        calendar.set(java.util.Calendar.SECOND, 59);
        calendar.set(java.util.Calendar.MILLISECOND, 999);
        return calendar.getTimeInMillis();
    }

    /**
     * Helper class for batch updates
     */
    public static class MedicationUpdateBatch {
        public String medicationId;
        public Map<String, Object> updates;
        
        public MedicationUpdateBatch(String medicationId, Map<String, Object> updates) {
            this.medicationId = medicationId;
            this.updates = updates;
            this.updates.put("updatedAt", System.currentTimeMillis());
        }
    }
}