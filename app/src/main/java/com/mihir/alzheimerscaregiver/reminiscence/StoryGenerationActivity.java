package com.mihir.alzheimerscaregiver.reminiscence;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import com.google.android.material.button.MaterialButton;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.Manifest;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.lifecycle.ViewModelProvider;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.widget.ImageView;

import com.mihir.alzheimerscaregiver.R;
import com.mihir.alzheimerscaregiver.data.model.StoryEntity;
import com.mihir.alzheimerscaregiver.utils.TextToSpeechManager;
import com.mihir.alzheimerscaregiver.utils.ImageGenerationManager;
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
    private MaterialButton generatePhotosButton;
    private MaterialButton viewAllStoriesButton;
    private ProgressBar loadingProgressBar;
    private ScrollView storyScrollView;
    
    // TTS Components
    private CardView ttsControlsCard;
    private MaterialButton playPauseButton;
    private MaterialButton stopButton;
    private TextView ttsStatusTextView;
    private TextToSpeechManager ttsManager;
    
    // Image Generation Components
    private CardView illustrationCard;
    private ImageView illustrationImageView;
    private ProgressBar illustrationLoadingProgressBar;
    private TextView illustrationDescriptionTextView;
    private MaterialButton saveImageButton;
    private ImageGenerationManager imageGenerationManager;
    
    // For saving images
    private static final int STORAGE_PERMISSION_REQUEST_CODE = 100;
    private String currentImagePath = null;
    
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
        
        // Initialize Image Generation
        initializeImageGeneration();
        
        // Load initial data
        storyViewModel.initializeData();
    }
    
    private void initializeViews() {
        storyTextView = findViewById(R.id.storyTextView);
        timestampTextView = findViewById(R.id.timestampTextView);
        languageTextView = findViewById(R.id.languageTextView);
        regenerateButton = findViewById(R.id.regenerateButton);
        generatePhotosButton = findViewById(R.id.generatePhotosButton);
        viewAllStoriesButton = findViewById(R.id.viewAllStoriesButton);
        loadingProgressBar = findViewById(R.id.loadingProgressBar);
        storyScrollView = findViewById(R.id.storyScrollView);
        
        // TTS Views
        ttsControlsCard = findViewById(R.id.ttsControlsCard);
        playPauseButton = findViewById(R.id.playPauseButton);
        stopButton = findViewById(R.id.stopButton);
        ttsStatusTextView = findViewById(R.id.ttsStatusTextView);
        
        // Image Views
        illustrationCard = findViewById(R.id.illustrationCard);
        illustrationImageView = findViewById(R.id.illustrationImageView);
        illustrationLoadingProgressBar = findViewById(R.id.illustrationLoadingProgressBar);
        illustrationDescriptionTextView = findViewById(R.id.illustrationDescriptionTextView);
        saveImageButton = findViewById(R.id.saveImageButton);
    }
    
    private void setupViewModel() {
        storyViewModel = new ViewModelProvider(this).get(StoryViewModel.class);
    }
    
    private void setupClickListeners() {
        regenerateButton.setOnClickListener(v -> {
            showLoadingState();
            storyViewModel.triggerStoryGeneration();
        });
        
        generatePhotosButton.setOnClickListener(v -> {
            // Trigger FLUX.1-dev image generation
            if (currentStory != null) {
                generateIllustrationScene();
            } else {
                Toast.makeText(this, "Please generate a story first", Toast.LENGTH_SHORT).show();
            }
        });
        
        saveImageButton.setOnClickListener(v -> {
            if (currentImagePath != null) {
                saveImageToGallery();
            } else {
                Toast.makeText(this, "No image to save", Toast.LENGTH_SHORT).show();
            }
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
        
        // Display existing illustration if available
        if (story.getIllustrationUrl() != null && !story.getIllustrationUrl().isEmpty()) {
            displayIllustration(story.getIllustrationUrl(), story.getIllustrationDescription());
        } else {
            // Hide illustration card when no image is available
            illustrationCard.setVisibility(View.GONE);
        }
        
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
    
    // Image Generation Methods
    private void initializeImageGeneration() {
        imageGenerationManager = new ImageGenerationManager(this);
    }
    
    private void generateIllustrationScene() {
        // Get patient profile from ViewModel
        storyViewModel.getPatientProfile().observe(this, profile -> {
            if (profile != null) {
                showIllustrationLoadingState();
                
                // Create FLUX.1-dev optimized parameters
                ImageGenerationManager.SceneImageParams params = new ImageGenerationManager.SceneImageParams(
                    "photorealistic digital painting",
                    "serene and therapeutic"
                );
                params.inferenceSteps = 20; // FLUX.1-dev quality
                params.guidanceScale = 3.5;
                
                // Get the current story content to use as scene context
                String storyContent = currentStory != null ? currentStory.getGeneratedStory() : null;
                
                imageGenerationManager.generateSceneImage(profile, storyContent, params, new ImageGenerationManager.ImageGenerationCallback() {
                    @Override
                    public void onImageGenerated(String imagePath, String description) {
                        runOnUiThread(() -> {
                            displayGeneratedImage(imagePath, description);
                            hideIllustrationLoadingState();
                        });
                    }
                    
                    @Override
                    public void onImageGenerationFailed(String error) {
                        runOnUiThread(() -> {
                            hideIllustrationCard();
                            Toast.makeText(StoryGenerationActivity.this, "Could not generate illustration: " + error, Toast.LENGTH_SHORT).show();
                        });
                    }
                });
            }
        });
    }
    
    private void displayIllustration(String imageUrl, String description) {
        try {
            if (imageUrl != null && imageUrl.startsWith("data:image")) {
                // Handle base64 encoded image (legacy format)
                String base64Data = imageUrl.substring(imageUrl.indexOf(",") + 1);
                byte[] decodedBytes = Base64.decode(base64Data, Base64.DEFAULT);
                Bitmap bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);
                
                if (bitmap != null) {
                    illustrationImageView.setImageBitmap(bitmap);
                    illustrationDescriptionTextView.setText(description);
                    illustrationCard.setVisibility(View.VISIBLE);
                } else {
                    hideIllustrationCard();
                }
            } else {
                // Handle URL-based images (would need image loading library like Glide for production)
                hideIllustrationCard();
                Toast.makeText(this, "Legacy image format not supported - use Generate Photos button", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            // Log.e("StoryGenerationActivity", "Error displaying illustration", e);
            hideIllustrationCard();
            Toast.makeText(this, "Error displaying illustration", Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * Display generated image from FLUX.1-dev (handles file paths)
     */
    private void displayGeneratedImage(String imagePath, String description) {
        try {
            if (imagePath != null && !imagePath.isEmpty()) {
                // Load image from file path (FLUX.1-dev generates local files)
                Bitmap bitmap = BitmapFactory.decodeFile(imagePath);
                
                if (bitmap != null) {
                    illustrationImageView.setImageBitmap(bitmap);
                    illustrationDescriptionTextView.setText(description);
                    illustrationCard.setVisibility(View.VISIBLE);
                    
                    // Store image path for saving and show save button
                    currentImagePath = imagePath;
                    saveImageButton.setVisibility(View.VISIBLE);
                    
                    // Note: Image generated successfully - could save to story if updateStory method exists
                } else {
                    hideIllustrationCard();
                    Toast.makeText(this, "Generated image could not be loaded", Toast.LENGTH_SHORT).show();
                }
            } else {
                hideIllustrationCard();
                Toast.makeText(this, "No image path provided", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            hideIllustrationCard();
            Toast.makeText(this, "Error displaying generated image: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    private void showIllustrationLoadingState() {
        illustrationCard.setVisibility(View.VISIBLE);
        illustrationImageView.setVisibility(View.GONE);
        illustrationLoadingProgressBar.setVisibility(View.VISIBLE);
        illustrationDescriptionTextView.setText("Generating therapeutic illustration...");
    }
    
    private void hideIllustrationLoadingState() {
        illustrationLoadingProgressBar.setVisibility(View.GONE);
        illustrationImageView.setVisibility(View.VISIBLE);
    }
    
    private void hideIllustrationCard() {
        illustrationCard.setVisibility(View.GONE);
        saveImageButton.setVisibility(View.GONE);
        currentImagePath = null;
    }
    
    /**
     * Save the generated image to device gallery
     */
    private void saveImageToGallery() {
        // Check for storage permissions
        if (!hasStoragePermission()) {
            requestStoragePermission();
            return;
        }
        
        if (currentImagePath == null) {
            Toast.makeText(this, "No image to save", Toast.LENGTH_SHORT).show();
            return;
        }
        
        try {
            File sourceFile = new File(currentImagePath);
            if (!sourceFile.exists()) {
                Toast.makeText(this, "Image file not found", Toast.LENGTH_SHORT).show();
                return;
            }
            
            // Create a unique filename
            String timestamp = String.valueOf(System.currentTimeMillis());
            String filename = "AlzheimersCaregiver_" + timestamp + ".jpg";
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ - Use MediaStore API
                saveImageUsingMediaStore(sourceFile, filename);
            } else {
                // Android 9 and below - Use legacy external storage
                saveImageToExternalStorage(sourceFile, filename);
            }
            
        } catch (Exception e) {
            Toast.makeText(this, "Error saving image: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
    
    /**
     * Save image using MediaStore API (Android 10+)
     */
    private void saveImageUsingMediaStore(File sourceFile, String filename) throws IOException {
        ContentResolver resolver = getContentResolver();
        ContentValues contentValues = new ContentValues();
        
        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, filename);
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
        contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/AlzheimersCaregiver");
        
        Uri imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues);
        
        if (imageUri != null) {
            try (OutputStream outputStream = resolver.openOutputStream(imageUri);
                 FileInputStream inputStream = new FileInputStream(sourceFile)) {
                
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
                
                Toast.makeText(this, "Image saved to gallery", Toast.LENGTH_SHORT).show();
            }
        } else {
            throw new IOException("Failed to create image URI");
        }
    }
    
    /**
     * Save image to external storage (Android 9 and below)
     */
    private void saveImageToExternalStorage(File sourceFile, String filename) throws IOException {
        File picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        File appDir = new File(picturesDir, "AlzheimersCaregiver");
        
        if (!appDir.exists() && !appDir.mkdirs()) {
            throw new IOException("Failed to create directory");
        }
        
        File destFile = new File(appDir, filename);
        
        try (FileInputStream inputStream = new FileInputStream(sourceFile);
             java.io.FileOutputStream outputStream = new java.io.FileOutputStream(destFile)) {
            
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            
            // Notify media scanner
            Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
            Uri contentUri = Uri.fromFile(destFile);
            mediaScanIntent.setData(contentUri);
            sendBroadcast(mediaScanIntent);
            
            Toast.makeText(this, "Image saved to gallery", Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * Check if app has storage permissions
     */
    private boolean hasStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ - No storage permission needed for MediaStore
            return true;
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10-12 - READ_EXTERNAL_STORAGE for MediaStore
            return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) 
                   == PackageManager.PERMISSION_GRANTED;
        } else {
            // Android 9 and below - WRITE_EXTERNAL_STORAGE
            return ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) 
                   == PackageManager.PERMISSION_GRANTED;
        }
    }
    
    /**
     * Request storage permissions
     */
    private void requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ - No permission needed
            saveImageToGallery();
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ActivityCompat.requestPermissions(this, 
                new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 
                STORAGE_PERMISSION_REQUEST_CODE);
        } else {
            ActivityCompat.requestPermissions(this, 
                new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 
                STORAGE_PERMISSION_REQUEST_CODE);
        }
    }
    
    /**
     * Handle permission request result
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == STORAGE_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                saveImageToGallery();
            } else {
                Toast.makeText(this, "Storage permission is required to save images", Toast.LENGTH_LONG).show();
            }
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (ttsManager != null) {
            ttsManager.shutdown();
        }
        if (imageGenerationManager != null) {
            imageGenerationManager.cleanupCache();
        }
    }
}