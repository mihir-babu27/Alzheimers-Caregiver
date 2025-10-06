package com.mihir.alzheimerscaregiver.repository;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.MutableLiveData;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.mihir.alzheimerscaregiver.data.model.PatientProfile;
import com.mihir.alzheimerscaregiver.data.model.StoryEntity;
import com.mihir.alzheimerscaregiver.reminiscence.GeminiStoryGenerator;
import com.mihir.alzheimerscaregiver.utils.LanguagePreferenceManager;

import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Repository that handles Firebase Firestore operations and Gemini API calls
 * Integrates with existing GeminiStoryGenerator for story generation
 */
public class StoryRepository {
    
    private static final String TAG = "StoryRepository";
    private static final String PATIENTS_COLLECTION = "patients";
    private static final String PROFILES_SUBCOLLECTION = "profile";
    private static final String PROFILE_DOCUMENT = "details";
    private static final String STORIES_SUBCOLLECTION = "stories";
    
    private final FirebaseFirestore firestore;
    private final GeminiStoryGenerator storyGenerator;
    private final Context context;
    
    // LiveData for UI observation
    private final MutableLiveData<PatientProfile> patientProfileLiveData = new MutableLiveData<>();
    private final MutableLiveData<List<StoryEntity>> storiesLiveData = new MutableLiveData<>();
    private final MutableLiveData<StoryEntity> latestStoryLiveData = new MutableLiveData<>();
    private final MutableLiveData<String> errorLiveData = new MutableLiveData<>();
    private final MutableLiveData<Boolean> loadingLiveData = new MutableLiveData<>();
    
    public StoryRepository(Context context) {
        this.context = context;
        this.firestore = FirebaseFirestore.getInstance();
        this.storyGenerator = new GeminiStoryGenerator();
    }
    
    /**
     * Fetch patient profile from Firebase Firestore
     */
    public void fetchPatientProfile(String patientId) {
        loadingLiveData.setValue(true);
        
        firestore.collection(PATIENTS_COLLECTION)
                .document(patientId)
                .collection(PROFILES_SUBCOLLECTION)
                .document(PROFILE_DOCUMENT)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    loadingLiveData.setValue(false);
                    if (documentSnapshot.exists()) {
                        try {
                            PatientProfile profile = createPatientProfileFromDocument(documentSnapshot, patientId);
                            if (profile != null) {
                                patientProfileLiveData.setValue(profile);
                                Log.d(TAG, "Patient profile fetched successfully for: " + patientId);
                            } else {
                                errorLiveData.setValue("Unable to parse patient profile data");
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error parsing patient profile", e);
                            errorLiveData.setValue("Error parsing patient profile: " + e.getMessage());
                        }
                    } else {
                        errorLiveData.setValue("Patient profile not found");
                    }
                })
                .addOnFailureListener(e -> {
                    loadingLiveData.setValue(false);
                    Log.e(TAG, "Error fetching patient profile", e);
                    errorLiveData.setValue("Failed to fetch patient profile: " + e.getMessage());
                });
    }
    
    /**
     * Generate a new story using existing GeminiStoryGenerator and save to Firebase
     */
    public void generateAndSaveStory(String patientId) {
        PatientProfile profile = patientProfileLiveData.getValue();
        if (profile == null) {
            errorLiveData.setValue("Patient profile not available. Please fetch profile first.");
            return;
        }
        
        loadingLiveData.setValue(true);
        
        // Convert to GeminiStoryGenerator.PatientDetails format
        GeminiStoryGenerator.PatientDetails geminiDetails = profile.toGeminiPatientDetails();
        
        // Get user's preferred language
        String preferredLanguage = LanguagePreferenceManager.getPreferredLanguage(context);
        
        // Generate story using existing GeminiStoryGenerator
        storyGenerator.generateReminiscenceStory(geminiDetails, context, new GeminiStoryGenerator.StoryGenerationCallback() {
            @Override
            public void onSuccess(String generatedStory) {
                // Create story entity
                String storyId = UUID.randomUUID().toString();
                StoryEntity storyEntity = new StoryEntity(
                    storyId,
                    patientId,
                    generatedStory,
                    new Date(),
                    preferredLanguage,
                    "reminiscence" // Default theme, could be enhanced to track actual theme used
                );
                
                // Save to Firebase
                saveStoryToFirebase(storyEntity);
            }
            
            @Override
            public void onError(String errorMessage) {
                loadingLiveData.setValue(false);
                Log.e(TAG, "Story generation failed: " + errorMessage);
                errorLiveData.setValue("Story generation failed: " + errorMessage);
            }
        });
    }
    
    /**
     * Save generated story to Firebase Firestore
     */
    private void saveStoryToFirebase(StoryEntity story) {
        firestore.collection(PATIENTS_COLLECTION)
                .document(story.getPatientId())
                .collection(STORIES_SUBCOLLECTION)
                .document(story.getStoryId())
                .set(story)
                .addOnSuccessListener(aVoid -> {
                    loadingLiveData.setValue(false);
                    latestStoryLiveData.setValue(story);
                    Log.d(TAG, "Story saved successfully: " + story.getStoryId());
                    
                    // Refresh stories list
                    fetchStoriesForPatient(story.getPatientId());
                })
                .addOnFailureListener(e -> {
                    loadingLiveData.setValue(false);
                    Log.e(TAG, "Error saving story", e);
                    errorLiveData.setValue("Failed to save story: " + e.getMessage());
                });
    }
    
    /**
     * Fetch all stories for a patient from Firebase
     */
    public void fetchStoriesForPatient(String patientId) {
        firestore.collection(PATIENTS_COLLECTION)
                .document(patientId)
                .collection(STORIES_SUBCOLLECTION)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<StoryEntity> stories = queryDocumentSnapshots.toObjects(StoryEntity.class);
                    storiesLiveData.setValue(stories);
                    Log.d(TAG, "Fetched " + stories.size() + " stories for patient: " + patientId);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error fetching stories", e);
                    errorLiveData.setValue("Failed to fetch stories: " + e.getMessage());
                });
    }
    
    /**
     * Get the latest story for a patient
     */
    public void fetchLatestStory(String patientId) {
        firestore.collection(PATIENTS_COLLECTION)
                .document(patientId)
                .collection(STORIES_SUBCOLLECTION)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(1)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty()) {
                        DocumentSnapshot document = queryDocumentSnapshots.getDocuments().get(0);
                        StoryEntity story = document.toObject(StoryEntity.class);
                        latestStoryLiveData.setValue(story);
                        Log.d(TAG, "Latest story fetched for patient: " + patientId);
                    } else {
                        Log.d(TAG, "No stories found for patient: " + patientId);
                        latestStoryLiveData.setValue(null);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error fetching latest story", e);
                    errorLiveData.setValue("Failed to fetch latest story: " + e.getMessage());
                });
    }
    
    /**
     * Helper method to manually create PatientProfile from DocumentSnapshot
     * This handles data type conversion issues (Long to String for birthYear)
     */
    private PatientProfile createPatientProfileFromDocument(DocumentSnapshot document, String patientId) {
        PatientProfile profile = new PatientProfile();
        profile.setPatientId(patientId);
        
        // Handle each field with proper type conversion
        if (document.contains("name")) {
            profile.setName(document.getString("name"));
        }
        
        // Handle birthYear - could be String or Long in Firestore
        if (document.contains("birthYear")) {
            Object birthYearObj = document.get("birthYear");
            if (birthYearObj instanceof String) {
                profile.setBirthYear((String) birthYearObj);
            } else if (birthYearObj instanceof Long) {
                profile.setBirthYear(((Long) birthYearObj).toString());
            } else if (birthYearObj instanceof Integer) {
                profile.setBirthYear(((Integer) birthYearObj).toString());
            }
        }
        
        if (document.contains("birthplace")) {
            profile.setBirthplace(document.getString("birthplace"));
        }
        
        if (document.contains("profession")) {
            profile.setProfession(document.getString("profession"));
        }
        
        if (document.contains("otherDetails")) {
            profile.setOtherDetails(document.getString("otherDetails"));
        }
        
        // Handle additional fields
        if (document.contains("age")) {
            profile.setAge(document.getString("age"));
        }
        
        if (document.contains("hobbies")) {
            profile.setHobbies(document.getString("hobbies"));
        }
        
        if (document.contains("familyInfo")) {
            profile.setFamilyInfo(document.getString("familyInfo"));
        }
        
        if (document.contains("favoritePlaces")) {
            profile.setFavoritePlaces(document.getString("favoritePlaces"));
        }
        
        if (document.contains("personalityTraits")) {
            profile.setPersonalityTraits(document.getString("personalityTraits"));
        }
        
        if (document.contains("significantEvents")) {
            profile.setSignificantEvents(document.getString("significantEvents"));
        }
        
        return profile;
    }
    
    // LiveData getters for UI observation
    public MutableLiveData<PatientProfile> getPatientProfileLiveData() {
        return patientProfileLiveData;
    }
    
    public MutableLiveData<List<StoryEntity>> getStoriesLiveData() {
        return storiesLiveData;
    }
    
    public MutableLiveData<StoryEntity> getLatestStoryLiveData() {
        return latestStoryLiveData;
    }
    
    public MutableLiveData<String> getErrorLiveData() {
        return errorLiveData;
    }
    
    public MutableLiveData<Boolean> getLoadingLiveData() {
        return loadingLiveData;
    }
}