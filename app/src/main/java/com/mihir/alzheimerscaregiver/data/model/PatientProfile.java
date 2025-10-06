package com.mihir.alzheimerscaregiver.data.model;

/**
 * Enhanced patient details model that matches Firebase Firestore structure
 * Compatible with existing GeminiStoryGenerator.PatientDetails
 */
public class PatientProfile {
    private String patientId;
    private String name;
    private String birthYear;
    private String birthplace;
    private String profession;
    private String otherDetails;
    
    // Additional fields for richer story generation
    private String age;
    private String hobbies;
    private String familyInfo;
    private String favoritePlaces;
    private String personalityTraits;
    private String significantEvents;
    
    // Required empty constructor for Firebase
    public PatientProfile() {}
    
    public PatientProfile(String patientId, String name, String birthYear, String birthplace, 
                         String profession, String otherDetails) {
        this.patientId = patientId;
        this.name = name;
        this.birthYear = birthYear;
        this.birthplace = birthplace;
        this.profession = profession;
        this.otherDetails = otherDetails;
    }
    
    /**
     * Convert to GeminiStoryGenerator.PatientDetails for API compatibility
     */
    public com.mihir.alzheimerscaregiver.reminiscence.GeminiStoryGenerator.PatientDetails toGeminiPatientDetails() {
        // Combine all details into a comprehensive description
        StringBuilder combinedDetails = new StringBuilder();
        if (otherDetails != null && !otherDetails.trim().isEmpty()) {
            combinedDetails.append(otherDetails);
        }
        if (hobbies != null && !hobbies.trim().isEmpty()) {
            if (combinedDetails.length() > 0) combinedDetails.append(" ");
            combinedDetails.append("Hobbies: ").append(hobbies).append(".");
        }
        if (familyInfo != null && !familyInfo.trim().isEmpty()) {
            if (combinedDetails.length() > 0) combinedDetails.append(" ");
            combinedDetails.append("Family: ").append(familyInfo).append(".");
        }
        if (favoritePlaces != null && !favoritePlaces.trim().isEmpty()) {
            if (combinedDetails.length() > 0) combinedDetails.append(" ");
            combinedDetails.append("Favorite places: ").append(favoritePlaces).append(".");
        }
        if (personalityTraits != null && !personalityTraits.trim().isEmpty()) {
            if (combinedDetails.length() > 0) combinedDetails.append(" ");
            combinedDetails.append("Personality: ").append(personalityTraits).append(".");
        }
        if (significantEvents != null && !significantEvents.trim().isEmpty()) {
            if (combinedDetails.length() > 0) combinedDetails.append(" ");
            combinedDetails.append("Significant events: ").append(significantEvents).append(".");
        }
        
        return new com.mihir.alzheimerscaregiver.reminiscence.GeminiStoryGenerator.PatientDetails(
            name != null ? name : "",
            birthYear != null ? birthYear : "",
            birthplace != null ? birthplace : "",
            profession != null ? profession : "",
            combinedDetails.toString()
        );
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
}