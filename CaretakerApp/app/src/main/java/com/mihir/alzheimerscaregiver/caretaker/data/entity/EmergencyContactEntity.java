package com.mihir.alzheimerscaregiver.caretaker.data.entity;

/**
 * EmergencyContactEntity - Entity class for emergency contact data in CaretakerApp
 * Used for managing emergency contacts for patients with Firebase Firestore integration
 */
public class EmergencyContactEntity {

    public String id;           // Firebase document ID
    public String name;         // Contact name (e.g., "Dr. Sarah Johnson")
    public String phoneNumber;  // Phone number (e.g., "+91-9876543210") 
    public String relationship; // Relationship (e.g., "Primary Doctor", "Family Member")
    public boolean isPrimary;   // Whether this is the primary contact

    // Default constructor for Firebase
    public EmergencyContactEntity() {}

    public EmergencyContactEntity(String name,
                                 String phoneNumber,
                                 String relationship,
                                 boolean isPrimary) {
        this.name = name;
        this.phoneNumber = phoneNumber;
        this.relationship = relationship;
        this.isPrimary = isPrimary;
    }

    public EmergencyContactEntity(String id,
                                 String name,
                                 String phoneNumber,
                                 String relationship,
                                 boolean isPrimary) {
        this.id = id;
        this.name = name;
        this.phoneNumber = phoneNumber;
        this.relationship = relationship;
        this.isPrimary = isPrimary;
    }

    // Getter methods for UI binding
    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public String getRelationship() {
        return relationship;
    }

    public boolean isPrimary() {
        return isPrimary;
    }

    // Setter methods for Firebase compatibility
    public void setId(String id) {
        this.id = id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public void setRelationship(String relationship) {
        this.relationship = relationship;
    }

    public void setPrimary(boolean primary) {
        isPrimary = primary;
    }

    /**
     * Get display name with relationship in parentheses
     */
    public String getDisplayName() {
        if (relationship != null && !relationship.trim().isEmpty()) {
            return name + " (" + relationship + ")";
        }
        return name;
    }

    /**
     * Get full display name including primary status
     */
    public String getFullDisplayName() {
        String displayName = getDisplayName();
        if (isPrimary) {
            displayName += " (Primary)";
        }
        return displayName;
    }

    @Override
    public String toString() {
        return "EmergencyContactEntity{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", phoneNumber='" + phoneNumber + '\'' +
                ", relationship='" + relationship + '\'' +
                ", isPrimary=" + isPrimary +
                '}';
    }
}