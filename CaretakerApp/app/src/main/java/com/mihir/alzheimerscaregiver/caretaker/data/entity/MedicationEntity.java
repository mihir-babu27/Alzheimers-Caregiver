package com.mihir.alzheimerscaregiver.caretaker.data.entity;

import java.util.List;

/**
 * MedicationEntity for CaretakerApp - Enhanced version for caretaker management
 * Based on main app's MedicationEntity but with additional fields for caretaker use
 */
public class MedicationEntity {

    public String id;

    public String name;

    public String dosage;

    public String time;

    public String description;

    public String patientId;

    public String createdBy;

    public Long createdAt;

    public Long updatedAt;

    public boolean isActive;

    public boolean isRepeating;

    public Long nextDueTime;

    public List<String> medicineNames;

    public List<String> imageUrls;

    public String category; // e.g., "Blood Pressure", "Diabetes", "Heart"

    public String frequency; // e.g., "Daily", "Twice a day", "Weekly"

    public String instructions; // Special instructions for taking the medication

    public MedicationEntity(String name,
                           String dosage,
                           String time,
                           String description,
                           String patientId,
                           String createdBy,
                           Long createdAt,
                           boolean isActive,
                           boolean isRepeating) {
        this.name = name;
        this.dosage = dosage;
        this.time = time;
        this.description = description;
        this.patientId = patientId;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
        this.isActive = isActive;
        this.isRepeating = isRepeating;
    }

    // Default constructor for Firebase
    public MedicationEntity() {}

    // Convenience constructors
    public MedicationEntity(String name,
                           String dosage,
                           String time) {
        this.name = name;
        this.dosage = dosage;
        this.time = time;
        this.isActive = true;
        this.isRepeating = false;
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = this.createdAt;
    }

    // Builder pattern methods for easy construction
    public MedicationEntity setDescription(String description) {
        this.description = description;
        return this;
    }

    public MedicationEntity setPatientId(String patientId) {
        this.patientId = patientId;
        return this;
    }

    public MedicationEntity setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
        return this;
    }

    public MedicationEntity setCategory(String category) {
        this.category = category;
        return this;
    }

    public MedicationEntity setFrequency(String frequency) {
        this.frequency = frequency;
        return this;
    }

    public MedicationEntity setInstructions(String instructions) {
        this.instructions = instructions;
        return this;
    }

    public MedicationEntity setMedicineNames(List<String> medicineNames) {
        this.medicineNames = medicineNames;
        return this;
    }

    public MedicationEntity setImageUrls(List<String> imageUrls) {
        this.imageUrls = imageUrls;
        return this;
    }

    public MedicationEntity setRepeating(boolean repeating) {
        this.isRepeating = repeating;
        return this;
    }

    public MedicationEntity setActive(boolean active) {
        this.isActive = active;
        return this;
    }

    public void updateTimestamp() {
        this.updatedAt = System.currentTimeMillis();
    }

    // Helper methods
    public String getDisplayName() {
        return name != null ? name : (medicineNames != null && !medicineNames.isEmpty() 
            ? medicineNames.get(0) : "Unknown Medication");
    }

    public String getFullDescription() {
        StringBuilder desc = new StringBuilder();
        desc.append(getDisplayName()).append(" - ").append(dosage != null ? dosage : "");
        
        if (frequency != null && !frequency.isEmpty()) {
            desc.append(" (").append(frequency).append(")");
        }
        
        if (instructions != null && !instructions.isEmpty()) {
            desc.append("\nInstructions: ").append(instructions);
        }
        
        return desc.toString();
    }

    public boolean isDue() {
        return nextDueTime != null && System.currentTimeMillis() >= nextDueTime;
    }

    public boolean isOverdue() {
        return isDue() && System.currentTimeMillis() > (nextDueTime + (2 * 60 * 60 * 1000)); // 2 hours
    }
}