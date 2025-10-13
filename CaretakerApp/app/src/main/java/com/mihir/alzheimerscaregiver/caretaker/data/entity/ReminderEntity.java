package com.mihir.alzheimerscaregiver.caretaker.data.entity;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

public class ReminderEntity {

    public String id;

    @NonNull
    public String title;

    public String description;

    public Long scheduledTimeEpochMillis;

    public boolean isCompleted;
    
    // Repeat functionality
    public boolean isRepeating;
    
    // New fields for multiple medicines and images
    public List<String> medicineNames;
    public List<String> imageUrls;

        public ReminderEntity(@NonNull String title,
                                                  String description,
                                                  Long scheduledTimeEpochMillis,
                                                  boolean isCompleted) {
                this.title = title;
                this.description = description;
                this.scheduledTimeEpochMillis = scheduledTimeEpochMillis;
                this.isCompleted = isCompleted;
                this.isRepeating = false; // Default to non-repeating
                this.medicineNames = new ArrayList<>();
                this.imageUrls = new ArrayList<>();
        }

        // Enhanced constructor with medicine names and images
        public ReminderEntity(@NonNull String title, String description, Long scheduledTimeEpochMillis, 
                             boolean isCompleted, List<String> medicineNames, List<String> imageUrls) {
            this.title = title;
            this.description = description;
            this.scheduledTimeEpochMillis = scheduledTimeEpochMillis;
            this.isCompleted = isCompleted;
            this.isRepeating = false; // Default to non-repeating
            this.medicineNames = medicineNames != null ? new ArrayList<>(medicineNames) : new ArrayList<>();
            this.imageUrls = imageUrls != null ? new ArrayList<>(imageUrls) : new ArrayList<>();
        }

        // Full constructor with repeat functionality
        public ReminderEntity(@NonNull String title, String description, Long scheduledTimeEpochMillis, 
                             boolean isCompleted, boolean isRepeating, List<String> medicineNames, List<String> imageUrls) {
            this.title = title;
            this.description = description;
            this.scheduledTimeEpochMillis = scheduledTimeEpochMillis;
            this.isCompleted = isCompleted;
            this.isRepeating = isRepeating;
            this.medicineNames = medicineNames != null ? new ArrayList<>(medicineNames) : new ArrayList<>();
            this.imageUrls = imageUrls != null ? new ArrayList<>(imageUrls) : new ArrayList<>();
        }

        // Default constructor for Firebase
        public ReminderEntity() {
            this.isRepeating = false; // Default to non-repeating
            this.medicineNames = new ArrayList<>();
            this.imageUrls = new ArrayList<>();
        }

        // Helper methods for medicine names
        public void addMedicineName(String medicineName) {
            if (medicineNames == null) {
                medicineNames = new ArrayList<>();
            }
            if (medicineName != null && !medicineName.trim().isEmpty()) {
                medicineNames.add(medicineName.trim());
            }
        }

        public void removeMedicineName(int index) {
            if (medicineNames != null && index >= 0 && index < medicineNames.size()) {
                medicineNames.remove(index);
            }
        }

        public String getMedicineNamesString() {
            if (medicineNames == null || medicineNames.isEmpty()) {
                return "";
            }
            return String.join(", ", medicineNames);
        }

        // Helper methods for image URLs
        public void addImageUrl(String imageUrl) {
            if (imageUrls == null) {
                imageUrls = new ArrayList<>();
            }
            if (imageUrl != null && !imageUrl.trim().isEmpty()) {
                imageUrls.add(imageUrl.trim());
            }
        }

        public void removeImageUrl(int index) {
            if (imageUrls != null && index >= 0 && index < imageUrls.size()) {
                imageUrls.remove(index);
            }
        }

        public boolean hasImages() {
            return imageUrls != null && !imageUrls.isEmpty();
        }

        public boolean hasMedicines() {
            return medicineNames != null && !medicineNames.isEmpty();
        }

        // Migration helper method to convert single medicine name to list
        public void migrateSingleMedicineName(String singleMedicineName) {
            if (medicineNames == null) {
                medicineNames = new ArrayList<>();
            }
            if (singleMedicineName != null && !singleMedicineName.trim().isEmpty() && medicineNames.isEmpty()) {
                medicineNames.add(singleMedicineName.trim());
            }
        }
}
