package com.mihir.alzheimerscaregiver.reminiscence;

import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.mihir.alzheimerscaregiver.R;
import com.mihir.alzheimerscaregiver.auth.FirebaseAuthManager;

import java.util.Locale;

/**
 * Activity for generating and displaying reminiscence stories using patient profile data
 * Includes Text-to-Speech functionality for accessibility
 */
public class ReminiscenceStoryActivity extends AppCompatActivity implements TextToSpeech.OnInitListener {
    
    private static final String TAG = "ReminiscenceStory";
    private static final int TTS_REQUEST_CODE = 100;
    
    // UI Elements
    private MaterialButton generateStoryButton;
    private MaterialButton readAloudButton;
    private MaterialCardView storyCard;
    private TextView storyTextView;
    private TextView storyTitleTextView;
    private CircularProgressIndicator progressIndicator;
    
    // Core components
    private GeminiStoryGenerator storyGenerator;
    private FirebaseAuthManager authManager;
    private FirebaseFirestore db;
    private TextToSpeech textToSpeech;
    
    // Current story data
    private String currentStory = null;
    private GeminiStoryGenerator.PatientDetails currentPatientDetails = null;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reminiscence_story);
        
        // Initialize components
        initializeComponents();
        initializeViews();
        setupClickListeners();
        
        // Check if user is signed in
        if (!authManager.isPatientSignedIn()) {
            Toast.makeText(this, "Please sign in first", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        
        // Initialize Text-to-Speech
        textToSpeech = new TextToSpeech(this, this);
        
        // Load patient data for story generation
        loadPatientProfileData();
    }
    
    private void initializeComponents() {
        storyGenerator = new GeminiStoryGenerator();
        authManager = new FirebaseAuthManager();
        db = FirebaseFirestore.getInstance();
    }
    
    private void initializeViews() {
        generateStoryButton = findViewById(R.id.generateStoryButton);
        readAloudButton = findViewById(R.id.readAloudButton);
        storyCard = findViewById(R.id.storyCard);
        storyTextView = findViewById(R.id.storyTextView);
        storyTitleTextView = findViewById(R.id.storyTitleTextView);
        progressIndicator = findViewById(R.id.progressIndicator);
        
        // Initially hide story card and read aloud button
        storyCard.setVisibility(View.GONE);
        readAloudButton.setVisibility(View.GONE);
    }
    
    private void setupClickListeners() {
        generateStoryButton.setOnClickListener(v -> generateStory());
        readAloudButton.setOnClickListener(v -> readStoryAloud());
    }
    
    private void loadPatientProfileData() {
        String patientId = authManager.getCurrentPatientId();
        if (patientId == null) {
            Toast.makeText(this, "Unable to get patient ID", Toast.LENGTH_LONG).show();
            return;
        }
        
        // Fetch profile data from Firestore
        db.collection("patients")
                .document(patientId)
                .collection("profile")
                .document("details")
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        DocumentSnapshot document = task.getResult();
                        if (document.exists()) {
                            parsePatientDetails(document);
                        } else {
                            showNoProfileMessage();
                        }
                    } else {
                        String errorMessage = task.getException() != null ? 
                            task.getException().getMessage() : "Unknown error occurred";
                        Toast.makeText(this,
                            "Failed to fetch profile data: " + errorMessage, 
                            Toast.LENGTH_LONG).show();
                    }
                });
    }
    
    private void parsePatientDetails(DocumentSnapshot document) {
        try {
            String name = document.getString("name");
            Long birthYear = document.getLong("birthYear");
            String birthplace = document.getString("birthplace");
            String profession = document.getString("profession");
            String otherDetails = document.getString("otherDetails");
            
            // Convert birthYear to string
            String birthYearStr = birthYear != null ? String.valueOf(birthYear) : null;
            
            currentPatientDetails = new GeminiStoryGenerator.PatientDetails(
                name, birthYearStr, birthplace, profession, otherDetails
            );
            
            // Enable generate button if details are valid
            if (currentPatientDetails.isValid()) {
                generateStoryButton.setEnabled(true);
                generateStoryButton.setText("Generate My Story");
            } else {
                generateStoryButton.setEnabled(false);
                generateStoryButton.setText("Profile Incomplete");
                Toast.makeText(this, 
                    "Profile information is incomplete: " + currentPatientDetails.getValidationError(), 
                    Toast.LENGTH_LONG).show();
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error parsing patient details", e);
            Toast.makeText(this, "Error loading patient details", Toast.LENGTH_LONG).show();
        }
    }
    
    private void showNoProfileMessage() {
        generateStoryButton.setEnabled(false);
        generateStoryButton.setText("No Profile Found");
        Toast.makeText(this, 
            "No profile found. Ask your caretaker to enter your profile details first.", 
            Toast.LENGTH_LONG).show();
    }
    
    private void generateStory() {
        if (currentPatientDetails == null || !currentPatientDetails.isValid()) {
            Toast.makeText(this, "Patient profile information is missing or incomplete", 
                Toast.LENGTH_LONG).show();
            return;
        }
        
        // Show loading state
        showLoadingState(true);
        
        // Generate story using Gemini API
        storyGenerator.generateReminiscenceStory(currentPatientDetails, new GeminiStoryGenerator.StoryGenerationCallback() {
            @Override
            public void onSuccess(String story) {
                currentStory = story;
                displayStory(story);
                showLoadingState(false);
            }
            
            @Override
            public void onError(String errorMessage) {
                Log.e(TAG, "Story generation error: " + errorMessage);
                Toast.makeText(ReminiscenceStoryActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                showLoadingState(false);
            }
        });
    }
    
    private void displayStory(String story) {
        // Set story title
        String patientName = currentPatientDetails.name != null ? currentPatientDetails.name : "Your";
        storyTitleTextView.setText(patientName + "'s Story");
        
        // Set story content
        storyTextView.setText(story);
        
        // Show story card and read aloud button
        storyCard.setVisibility(View.VISIBLE);
        readAloudButton.setVisibility(View.VISIBLE);
        
        Toast.makeText(this, "Story generated successfully!", Toast.LENGTH_SHORT).show();
    }
    
    private void showLoadingState(boolean loading) {
        progressIndicator.setVisibility(loading ? View.VISIBLE : View.GONE);
        generateStoryButton.setEnabled(!loading);
        generateStoryButton.setText(loading ? "Generating..." : "Generate My Story");
        
        if (loading) {
            readAloudButton.setVisibility(View.GONE);
        }
    }
    
    private void readStoryAloud() {
        if (currentStory == null || currentStory.trim().isEmpty()) {
            Toast.makeText(this, "No story to read", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (textToSpeech != null) {
            // Stop any current speech
            textToSpeech.stop();
            
            // Speak the story
            int result = textToSpeech.speak(currentStory, TextToSpeech.QUEUE_FLUSH, null, "story_utterance");
            if (result == TextToSpeech.ERROR) {
                Toast.makeText(this, "Error starting text-to-speech", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Reading story aloud...", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "Text-to-speech not available", Toast.LENGTH_SHORT).show();
        }
    }
    
    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            // Set language to US English
            int result = textToSpeech.setLanguage(Locale.US);
            
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e(TAG, "Language not supported for TTS");
                Toast.makeText(this, "Text-to-speech language not supported", Toast.LENGTH_SHORT).show();
            } else {
                Log.d(TAG, "Text-to-speech initialized successfully");
            }
        } else {
            Log.e(TAG, "Text-to-speech initialization failed");
            Toast.makeText(this, "Text-to-speech initialization failed", Toast.LENGTH_SHORT).show();
        }
    }
    
    @Override
    protected void onDestroy() {
        // Cleanup Text-to-Speech
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        super.onDestroy();
    }
}