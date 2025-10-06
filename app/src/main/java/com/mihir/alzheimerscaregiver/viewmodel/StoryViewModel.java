package com.mihir.alzheimerscaregiver.viewmodel;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.mihir.alzheimerscaregiver.auth.FirebaseAuthManager;
import com.mihir.alzheimerscaregiver.data.model.PatientProfile;
import com.mihir.alzheimerscaregiver.data.model.StoryEntity;
import com.mihir.alzheimerscaregiver.repository.StoryRepository;

import java.util.List;

/**
 * ViewModel for story generation and management
 * Integrates with StoryRepository and provides LiveData for UI observation
 */
public class StoryViewModel extends AndroidViewModel {
    
    private final StoryRepository storyRepository;
    private final FirebaseAuthManager authManager;
    private String currentPatientId;
    
    public StoryViewModel(@NonNull Application application) {
        super(application);
        this.storyRepository = new StoryRepository(application);
        this.authManager = new FirebaseAuthManager();
        this.currentPatientId = authManager.getCurrentPatientId();
    }
    
    /**
     * Initialize data loading for current patient
     */
    public void initializeData() {
        if (currentPatientId != null) {
            storyRepository.fetchPatientProfile(currentPatientId);
            storyRepository.fetchLatestStory(currentPatientId);
        }
    }
    
    /**
     * Generate a new story for the current patient
     */
    public void generateNewStory() {
        if (currentPatientId != null) {
            storyRepository.generateAndSaveStory(currentPatientId);
        }
    }
    
    /**
     * Regenerate story (same as generate new story)
     */
    public void regenerateStory() {
        // First refresh patient profile, then generate story
        if (currentPatientId != null) {
            storyRepository.fetchPatientProfile(currentPatientId);
            // Story generation will be triggered after profile is fetched
            // This is automatically handled by the repository
        }
    }
    
    /**
     * Fetch all stories for current patient
     */
    public void fetchAllStories() {
        if (currentPatientId != null) {
            storyRepository.fetchStoriesForPatient(currentPatientId);
        }
    }
    
    /**
     * Manually trigger story generation after profile is available
     */
    public void triggerStoryGeneration() {
        if (currentPatientId != null) {
            storyRepository.generateAndSaveStory(currentPatientId);
        }
    }
    
    // Expose LiveData from repository
    public LiveData<PatientProfile> getPatientProfile() {
        return storyRepository.getPatientProfileLiveData();
    }
    
    public LiveData<StoryEntity> getLatestStory() {
        return storyRepository.getLatestStoryLiveData();
    }
    
    public LiveData<List<StoryEntity>> getAllStories() {
        return storyRepository.getStoriesLiveData();
    }
    
    public LiveData<String> getErrorMessages() {
        return storyRepository.getErrorLiveData();
    }
    
    public LiveData<Boolean> getLoadingState() {
        return storyRepository.getLoadingLiveData();
    }
    
    public String getCurrentPatientId() {
        return currentPatientId;
    }
    
    public void setCurrentPatientId(String patientId) {
        this.currentPatientId = patientId;
    }
}