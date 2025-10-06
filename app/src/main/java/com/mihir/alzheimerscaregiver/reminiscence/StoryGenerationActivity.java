package com.mihir.alzheimerscaregiver.reminiscence;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import com.google.android.material.button.MaterialButton;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.lifecycle.ViewModelProvider;

import com.mihir.alzheimerscaregiver.R;
import com.mihir.alzheimerscaregiver.data.model.StoryEntity;
import com.mihir.alzheimerscaregiver.utils.TextToSpeechManager;
import com.mihir.alzheimerscaregiver.viewmodel.StoryViewModel;

import java.text.SimpleDateFormat;
import java.util.Locale;

/**
 * Activity for displaying AI-generated reminiscence stories
 * Integrates with StoryViewModel and demonstrates Firebase + Gemini integration
 */
public class StoryGenerationActivity extends AppCompatActivity {
    
    private TextView storyTextView;
    private TextView timestampTextView;
    private TextView languageTextView;
    private MaterialButton regenerateButton;
    private MaterialButton viewAllStoriesButton;
    private ProgressBar loadingProgressBar;
    private ScrollView storyScrollView;
    
    // TTS Components
    private CardView ttsControlsCard;
    private MaterialButton playPauseButton;
    private MaterialButton stopButton;
    private TextView ttsStatusTextView;
    private TextToSpeechManager ttsManager;
    
    private StoryViewModel storyViewModel;
    private SimpleDateFormat dateFormat;
    private StoryEntity currentStory;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_story_generation);
        
        initializeViews();
        setupViewModel();
        setupClickListeners();
        observeViewModel();
        
        // Initialize date format
        dateFormat = new SimpleDateFormat("MMM dd, yyyy 'at' HH:mm", Locale.getDefault());
        
        // Initialize TextToSpeech
        initializeTTS();
        
        // Load initial data
        storyViewModel.initializeData();
    }
    
    private void initializeViews() {
        storyTextView = findViewById(R.id.storyTextView);
        timestampTextView = findViewById(R.id.timestampTextView);
        languageTextView = findViewById(R.id.languageTextView);
        regenerateButton = findViewById(R.id.regenerateButton);
        viewAllStoriesButton = findViewById(R.id.viewAllStoriesButton);
        loadingProgressBar = findViewById(R.id.loadingProgressBar);
        storyScrollView = findViewById(R.id.storyScrollView);
        
        // TTS Views
        ttsControlsCard = findViewById(R.id.ttsControlsCard);
        playPauseButton = findViewById(R.id.playPauseButton);
        stopButton = findViewById(R.id.stopButton);
        ttsStatusTextView = findViewById(R.id.ttsStatusTextView);
    }
    
    private void setupViewModel() {
        storyViewModel = new ViewModelProvider(this).get(StoryViewModel.class);
    }
    
    private void setupClickListeners() {
        regenerateButton.setOnClickListener(v -> {
            showLoadingState();
            storyViewModel.triggerStoryGeneration();
        });
        
        viewAllStoriesButton.setOnClickListener(v -> {
            // Navigate to all stories activity
            Intent intent = new Intent(this, AllStoriesActivity.class);
            startActivity(intent);
        });
        
        // TTS Click Listeners
        playPauseButton.setOnClickListener(v -> {
            if (ttsManager != null && currentStory != null) {
                if (ttsManager.isSpeaking()) {
                    pauseSpeech();
                } else {
                    playSpeech();
                }
            }
        });
        
        stopButton.setOnClickListener(v -> {
            if (ttsManager != null) {
                stopSpeech();
            }
        });
    }
    
    private void observeViewModel() {
        // Observe latest story
        storyViewModel.getLatestStory().observe(this, story -> {
            if (story != null) {
                displayStory(story);
                hideLoadingState();
            } else {
                displayNoStoryMessage();
                hideLoadingState();
            }
        });
        
        // Observe patient profile (for additional context if needed)
        storyViewModel.getPatientProfile().observe(this, profile -> {
            if (profile != null && getSupportActionBar() != null) {
                // Update UI with patient info if needed
                getSupportActionBar().setTitle("Stories for " + profile.getName());
            }
        });
        
        // Observe loading state
        storyViewModel.getLoadingState().observe(this, isLoading -> {
            if (isLoading != null) {
                if (isLoading) {
                    showLoadingState();
                } else {
                    hideLoadingState();
                }
            }
        });
        
        // Observe errors
        storyViewModel.getErrorMessages().observe(this, errorMessage -> {
            if (errorMessage != null && !errorMessage.isEmpty()) {
                hideLoadingState();
                Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show();
            }
        });
    }
    
    private void displayStory(StoryEntity story) {
        this.currentStory = story;
        storyTextView.setText(story.getGeneratedStory());
        
        // Display timestamp
        if (story.getTimestamp() != null) {
            String formattedDate = dateFormat.format(story.getTimestamp());
            timestampTextView.setText("Generated on " + formattedDate);
            timestampTextView.setVisibility(View.VISIBLE);
        } else {
            timestampTextView.setVisibility(View.GONE);
        }
        
        // Display language if not English
        if (story.getLanguage() != null && !story.getLanguage().equals("English")) {
            languageTextView.setText("Language: " + story.getLanguage());
            languageTextView.setVisibility(View.VISIBLE);
        } else {
            languageTextView.setVisibility(View.GONE);
        }
        
        // Show TTS controls when story is available
        ttsControlsCard.setVisibility(View.VISIBLE);
        
        storyScrollView.setVisibility(View.VISIBLE);
        regenerateButton.setText("Generate New Story");
    }
    
    private void displayNoStoryMessage() {
        this.currentStory = null;
        storyTextView.setText("Welcome! Let's create your first personalized reminiscence story.\n\nTap 'Generate Story' below to begin.");
        timestampTextView.setVisibility(View.GONE);
        languageTextView.setVisibility(View.GONE);
        
        // Hide TTS controls when no story
        ttsControlsCard.setVisibility(View.GONE);
        
        storyScrollView.setVisibility(View.VISIBLE);
        regenerateButton.setText("Generate Story");
    }
    
    private void showLoadingState() {
        loadingProgressBar.setVisibility(View.VISIBLE);
        regenerateButton.setEnabled(false);
        regenerateButton.setText("Generating...");
    }
    
    private void hideLoadingState() {
        loadingProgressBar.setVisibility(View.GONE);
        regenerateButton.setEnabled(true);
    }
    
    // TextToSpeech Methods
    private void initializeTTS() {
        ttsManager = new TextToSpeechManager(this, new TextToSpeechManager.TTSCallback() {
            @Override
            public void onTTSInitialized() {
                runOnUiThread(() -> {
                    ttsStatusTextView.setText("Text-to-Speech ready");
                    ttsStatusTextView.setVisibility(View.VISIBLE);
                });
            }
            
            @Override
            public void onTTSError(String error) {
                runOnUiThread(() -> {
                    ttsStatusTextView.setText("TTS Error: " + error);
                    ttsStatusTextView.setVisibility(View.VISIBLE);
                    Toast.makeText(StoryGenerationActivity.this, "Text-to-Speech error: " + error, Toast.LENGTH_SHORT).show();
                });
            }
            
            @Override
            public void onSpeechStart() {
                runOnUiThread(() -> {
                    playPauseButton.setText("Pause");
                    ttsStatusTextView.setText("Reading story...");
                    ttsStatusTextView.setVisibility(View.VISIBLE);
                });
            }
            
            @Override
            public void onSpeechDone() {
                runOnUiThread(() -> {
                    playPauseButton.setText("Play");
                    ttsStatusTextView.setText("Story reading completed");
                });
            }
        });
    }
    
    private void playSpeech() {
        if (currentStory != null && ttsManager != null) {
            String languageCode = currentStory.getLanguage();
            if (languageCode == null || languageCode.trim().isEmpty()) {
                languageCode = "English"; // Default fallback
            }
            ttsManager.speak(currentStory.getGeneratedStory(), languageCode);
        }
    }
    
    private void pauseSpeech() {
        if (ttsManager != null) {
            ttsManager.stop();
            playPauseButton.setText("Play");
            ttsStatusTextView.setText("Paused");
        }
    }
    
    private void stopSpeech() {
        if (ttsManager != null) {
            ttsManager.stop();
            playPauseButton.setText("Play");
            ttsStatusTextView.setText("Stopped");
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (ttsManager != null) {
            ttsManager.shutdown();
        }
    }
}