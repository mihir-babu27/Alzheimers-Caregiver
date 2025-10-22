package com.mihir.alzheimerscaregiver.caretaker.data.entity;

import com.google.firebase.Timestamp;

/**
 * PatientProfileEntity - Data model for patient profile information in CaretakerApp
 * Represents comprehensive patient details stored in Firebase Firestore
 */
public class PatientProfileEntity {
    
    // Primary profile information
    public String patientId;
    public String name;
    public String birthYear;
    public String birthplace;
    public String profession;
    public String otherDetails;
    
    // Additional detailed information for comprehensive care
    public String age;
    public String hobbies;
    public String familyInfo;
    public String favoritePlaces;
    public String personalityTraits;
    public String significantEvents;
    public String medicalHistory;
    public String allergies;
    public String emergencyNotes;
    
    // Metadata
    public String caretakerId;
    public String caretakerEmail;
    public Timestamp lastUpdated;
    public Timestamp createdAt;
    
    // Default constructor for Firebase
    public PatientProfileEntity() {}
    
    // Primary constructor
    public PatientProfileEntity(String patientId, String name, String birthYear, 
                               String birthplace, String profession, String otherDetails) {
        this.patientId = patientId;
        this.name = name;
        this.birthYear = birthYear;
        this.birthplace = birthplace;
        this.profession = profession;
        this.otherDetails = otherDetails;
        this.lastUpdated = Timestamp.now();
        this.createdAt = Timestamp.now();
    }
    
    // Comprehensive constructor
    public PatientProfileEntity(String patientId, String name, String birthYear, String birthplace,
                               String profession, String otherDetails, String age, String hobbies,
                               String familyInfo, String favoritePlaces, String personalityTraits,
                               String significantEvents, String medicalHistory, String allergies,
                               String emergencyNotes, String caretakerId, String caretakerEmail) {
        this(patientId, name, birthYear, birthplace, profession, otherDetails);
        this.age = age;
        this.hobbies = hobbies;
        this.familyInfo = familyInfo;
        this.favoritePlaces = favoritePlaces;
        this.personalityTraits = personalityTraits;
        this.significantEvents = significantEvents;
        this.medicalHistory = medicalHistory;
        this.allergies = allergies;
        this.emergencyNotes = emergencyNotes;
        this.caretakerId = caretakerId;
        this.caretakerEmail = caretakerEmail;
    }
    
    // Getters and Setters
    public String getPatientId() { return patientId; }
    public void setPatientId(String patientId) { this.patientId = patientId; }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getBirthYear() { return birthYear; }
    public void setBirthYear(String birthYear) { this.birthYear = birthYear; }
    
    public String getBirthplace() { return birthplace; }
    public void setBirthplace(String birthplace) { this.birthplace = birthplace; }
    
    public String getProfession() { return profession; }
    public void setProfession(String profession) { this.profession = profession; }
    
    public String getOtherDetails() { return otherDetails; }
    public void setOtherDetails(String otherDetails) { this.otherDetails = otherDetails; }
    
    public String getAge() { return age; }
    public void setAge(String age) { this.age = age; }
    
    public String getHobbies() { return hobbies; }
    public void setHobbies(String hobbies) { this.hobbies = hobbies; }
    
    public String getFamilyInfo() { return familyInfo; }
    public void setFamilyInfo(String familyInfo) { this.familyInfo = familyInfo; }
    
    public String getFavoritePlaces() { return favoritePlaces; }
    public void setFavoritePlaces(String favoritePlaces) { this.favoritePlaces = favoritePlaces; }
    
    public String getPersonalityTraits() { return personalityTraits; }
    public void setPersonalityTraits(String personalityTraits) { this.personalityTraits = personalityTraits; }
    
    public String getSignificantEvents() { return significantEvents; }
    public void setSignificantEvents(String significantEvents) { this.significantEvents = significantEvents; }
    
    public String getMedicalHistory() { return medicalHistory; }
    public void setMedicalHistory(String medicalHistory) { this.medicalHistory = medicalHistory; }
    
    public String getAllergies() { return allergies; }
    public void setAllergies(String allergies) { this.allergies = allergies; }
    
    public String getEmergencyNotes() { return emergencyNotes; }
    public void setEmergencyNotes(String emergencyNotes) { this.emergencyNotes = emergencyNotes; }
    
    public String getCaretakerId() { return caretakerId; }
    public void setCaretakerId(String caretakerId) { this.caretakerId = caretakerId; }
    
    public String getCaretakerEmail() { return caretakerEmail; }
    public void setCaretakerEmail(String caretakerEmail) { this.caretakerEmail = caretakerEmail; }
    
    public Timestamp getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(Timestamp lastUpdated) { this.lastUpdated = lastUpdated; }
    
    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
    
    // Utility methods
    
    /**
     * Check if profile has basic required information
     */
    public boolean hasBasicInfo() {
        return name != null && !name.trim().isEmpty() &&
               birthYear != null && !birthYear.trim().isEmpty() &&
               birthplace != null && !birthplace.trim().isEmpty();
    }
    
    /**
     * Get display name with fallback
     */
    public String getDisplayName() {
        return (name != null && !name.trim().isEmpty()) ? name : "Unknown Patient";
    }
    
    /**
     * Get formatted birth information
     */
    public String getBirthInfo() {
        StringBuilder birthInfo = new StringBuilder();
        if (birthYear != null && !birthYear.trim().isEmpty()) {
            birthInfo.append("Born: ").append(birthYear);
        }
        if (birthplace != null && !birthplace.trim().isEmpty()) {
            if (birthInfo.length() > 0) birthInfo.append(", ");
            birthInfo.append(birthplace);
        }
        return birthInfo.toString();
    }
    
    /**
     * Update metadata for edit operations
     */
    public void updateMetadata(String caretakerId, String caretakerEmail) {
        this.caretakerId = caretakerId;
        this.caretakerEmail = caretakerEmail;
        this.lastUpdated = Timestamp.now();
        
        // Set created timestamp if not already set
        if (this.createdAt == null) {
            this.createdAt = Timestamp.now();
        }
    }
    
    @Override
    public String toString() {
        return "PatientProfileEntity{" +
                "patientId='" + patientId + '\'' +
                ", name='" + name + '\'' +
                ", birthYear='" + birthYear + '\'' +
                ", birthplace='" + birthplace + '\'' +
                ", profession='" + profession + '\'' +
                ", lastUpdated=" + lastUpdated +
                '}';
    }
}