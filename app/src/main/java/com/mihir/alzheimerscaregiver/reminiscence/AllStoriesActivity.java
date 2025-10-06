package com.mihir.alzheimerscaregiver.reminiscence;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.mihir.alzheimerscaregiver.R;
import com.mihir.alzheimerscaregiver.data.model.StoryEntity;
import com.mihir.alzheimerscaregiver.utils.TextToSpeechManager;
import com.mihir.alzheimerscaregiver.viewmodel.StoryViewModel;

import java.util.List;

/**
 * Activity to display all stored stories for the current patient
 * Shows stories in chronological order with timestamps and language info
 */
public class AllStoriesActivity extends AppCompatActivity implements StoriesAdapter.OnStoryActionListener {
    
    private static final String TAG = "AllStoriesActivity";
    
    // UI Components
    private RecyclerView storiesRecyclerView;
    private CircularProgressIndicator loadingProgressBar;
    private View emptyStateView;
    
    // ViewModel
    private StoryViewModel storyViewModel;
    
    // Adapter
    private StoriesAdapter storiesAdapter;
    
    // TTS Manager
    private TextToSpeechManager ttsManager;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_all_stories);
        
        // Setup toolbar
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("All Stories");
        }
        
        initializeViews();
        setupRecyclerView();
        setupViewModel();
        initializeTTS();
        observeViewModel();
        
        // Load all stories
        storyViewModel.fetchAllStories();
    }
    
    private void initializeViews() {
        storiesRecyclerView = findViewById(R.id.storiesRecyclerView);
        loadingProgressBar = findViewById(R.id.loadingProgressBar);
        emptyStateView = findViewById(R.id.emptyStateView);
        
        // Setup empty state button
        findViewById(R.id.generateFirstStoryButton).setOnClickListener(v -> {
            // Go back to story generation activity
            finish(); // This will return to StoryGenerationActivity
        });
    }
    
    private void setupRecyclerView() {
        storiesAdapter = new StoriesAdapter(this);
        storiesRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        storiesRecyclerView.setAdapter(storiesAdapter);
    }
    
    private void setupViewModel() {
        storyViewModel = new ViewModelProvider(this).get(StoryViewModel.class);
    }
    
    private void observeViewModel() {
        // Observe all stories
        storyViewModel.getAllStories().observe(this, stories -> {
            hideLoadingState();
            if (stories != null && !stories.isEmpty()) {
                showStoriesList(stories);
            } else {
                showEmptyState();
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
        
        // Observe patient profile for title
        storyViewModel.getPatientProfile().observe(this, profile -> {
            if (profile != null && getSupportActionBar() != null) {
                getSupportActionBar().setTitle("Stories for " + profile.getName());
            }
        });
    }
    
    private void showStoriesList(List<StoryEntity> stories) {
        storiesAdapter.updateStories(stories);
        storiesRecyclerView.setVisibility(View.VISIBLE);
        emptyStateView.setVisibility(View.GONE);
    }
    
    private void showEmptyState() {
        storiesRecyclerView.setVisibility(View.GONE);
        emptyStateView.setVisibility(View.VISIBLE);
    }
    
    private void showLoadingState() {
        loadingProgressBar.setVisibility(View.VISIBLE);
        storiesRecyclerView.setVisibility(View.GONE);
        emptyStateView.setVisibility(View.GONE);
    }
    
    private void hideLoadingState() {
        loadingProgressBar.setVisibility(View.GONE);
    }
    
    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
    
    // TextToSpeech Methods
    private void initializeTTS() {
        ttsManager = new TextToSpeechManager(this, new TextToSpeechManager.TTSCallback() {
            @Override
            public void onTTSInitialized() {
                // TTS is ready
            }
            
            @Override
            public void onTTSError(String error) {
                runOnUiThread(() -> {
                    Toast.makeText(AllStoriesActivity.this, "Text-to-Speech error: " + error, Toast.LENGTH_SHORT).show();
                });
            }
            
            @Override
            public void onSpeechStart() {
                // Speech started
            }
            
            @Override
            public void onSpeechDone() {
                // Speech completed
            }
        });
    }
    
    @Override
    public void onPlayStory(StoryEntity story) {
        if (ttsManager != null && story != null) {
            // Stop current speech if playing
            if (ttsManager.isSpeaking()) {
                ttsManager.stop();
            }
            
            // Get language code
            String languageCode = story.getLanguage();
            if (languageCode == null || languageCode.trim().isEmpty()) {
                languageCode = "English"; // Default fallback
            }
            
            // Play the story
            ttsManager.speak(story.getGeneratedStory(), languageCode);
            
            Toast.makeText(this, "Reading story...", Toast.LENGTH_SHORT).show();
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