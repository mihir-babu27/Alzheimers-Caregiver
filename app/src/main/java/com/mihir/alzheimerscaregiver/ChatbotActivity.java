package com.mihir.alzheimerscaregiver;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * ChatbotActivity - Voice-enabled AI chatbot for Alzheimer's patients
 * Features:
 * - Speech-to-Text input for easy interaction
 * - Text-to-Speech output for clear communication
 * - Gemini AI integration for natural conversation
 * - MMSE assessment extraction from conversations
 * - Conversation history management
 * - Elderly-friendly UI with large buttons
 */
public class ChatbotActivity extends AppCompatActivity implements TextToSpeech.OnInitListener {

    private static final String TAG = "ChatbotActivity";
    private static final int RECORD_AUDIO_PERMISSION_CODE = 1001;
    
    // UI Components
    private RecyclerView chatRecyclerView;
    private TextView statusText;
    private FloatingActionButton micButton;
    private ImageButton backButton;
    
    // Chat Components
    private ChatAdapter chatAdapter;
    private List<ChatMessage> chatMessages;
    
    // Speech Components
    private SpeechRecognizer speechRecognizer;
    private TextToSpeech textToSpeech;
    private boolean isListening = false;
    private boolean isTtsReady = false;
    private String lastPartialResult = ""; // Store last partial result as fallback
    
    // AI Service
    private GeminiChatService geminiChatService;
    
    // Session tracking
    private String currentSessionId;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chatbot);
        
        initializeViews();
        setupRecyclerView();
        initializeSpeechServices();
        initializeGeminiService();
        checkPermissions();
        
        // Welcome message
        addWelcomeMessage();
    }
    
    private void initializeViews() {
        chatRecyclerView = findViewById(R.id.chatRecyclerView);
        statusText = findViewById(R.id.statusText);
        micButton = findViewById(R.id.micButton);
        backButton = findViewById(R.id.backButton);
        
        // Set up back button
        backButton.setOnClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
            finish();
        });
        
        // Set up microphone button
        micButton.setOnClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
            toggleListening();
        });
        
        // Add long-click for testing without speech recognition
        micButton.setOnLongClickListener(v -> {
            Log.d(TAG, "🧪 TEST MODE: Long-click detected - testing chatbot without speech");
            v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
            
            // Test with a sample message
            testChatbotWithSampleMessage();
            return true;
        });
    }
    
    private void setupRecyclerView() {
        chatMessages = new ArrayList<>();
        chatAdapter = new ChatAdapter(chatMessages);
        
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true); // Show latest messages at bottom
        
        chatRecyclerView.setLayoutManager(layoutManager);
        chatRecyclerView.setAdapter(chatAdapter);
    }
    
    private void initializeSpeechServices() {
        // Initialize Speech-to-Text
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
            speechRecognizer.setRecognitionListener(new SpeechRecognitionListener());
        } else {
            Log.e(TAG, "Speech recognition not available");
            Toast.makeText(this, "Speech recognition not available on this device", Toast.LENGTH_LONG).show();
        }
        
        // Initialize Text-to-Speech
        textToSpeech = new TextToSpeech(this, this);
    }
    
    private void initializeGeminiService() {
        geminiChatService = new GeminiChatService();
    }
    
    private void addWelcomeMessage() {
        String welcomeText = "Hello! I'm your AI assistant. I'm here to chat with you and help with your daily activities. " +
                           "You can tap the microphone button to speak with me, or we can have a conversation about anything you'd like!";
        
        ChatMessage welcomeMessage = new ChatMessage(welcomeText, false, System.currentTimeMillis());
        chatMessages.add(welcomeMessage);
        chatAdapter.notifyItemInserted(chatMessages.size() - 1);
        scrollToBottom();
        
        // Speak welcome message
        speakText(welcomeText);
    }
    
    private void checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) 
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, 
                new String[]{Manifest.permission.RECORD_AUDIO}, 
                RECORD_AUDIO_PERMISSION_CODE);
        }
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == RECORD_AUDIO_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Audio recording permission granted");
            } else {
                Toast.makeText(this, "Microphone permission is required for voice chat", Toast.LENGTH_LONG).show();
            }
        }
    }
    
    private void toggleListening() {
        Log.d(TAG, "🎤 Microphone button clicked - Currently listening: " + isListening);
        
        if (isListening) {
            Log.d(TAG, "Stopping listening...");
            stopListening();
        } else {
            Log.d(TAG, "Starting listening...");
            startListening();
        }
    }
    
    private void startListening() {
        if (speechRecognizer == null) {
            Toast.makeText(this, "Speech recognition not available", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) 
                != PackageManager.PERMISSION_GRANTED) {
            checkPermissions();
            return;
        }
        
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Please speak...");
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
        
        // Enhanced speech recognition settings for better reliability
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 5000); // 5 seconds
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3000); // 3 seconds
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 2000); // Minimum 2 seconds
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true); // Enable partial results
        intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getPackageName());
        
        // Add confidence and alternative results
        intent.putExtra(RecognizerIntent.EXTRA_CONFIDENCE_SCORES, true);
        
        isListening = true;
        updateMicButtonState();
        statusText.setText("Listening... Please speak");
        statusText.setVisibility(View.VISIBLE);
        
        speechRecognizer.startListening(intent);
    }
    
    private void stopListening() {
        if (speechRecognizer != null && isListening) {
            speechRecognizer.stopListening();
        }
        isListening = false;
        updateMicButtonState();
        statusText.setVisibility(View.GONE);
    }
    
    private void updateMicButtonState() {
        if (isListening) {
            micButton.setImageResource(R.drawable.ic_mic_off);
            micButton.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.error));
        } else {
            micButton.setImageResource(R.drawable.ic_mic);
            micButton.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.primary_color));
        }
    }
    
    /**
     * Test method to verify chatbot functionality without speech recognition
     */
    private void testChatbotWithSampleMessage() {
        Log.d(TAG, "🧪 Testing chatbot with sample message...");
        
        // Show test status
        statusText.setText("Testing chatbot...");
        statusText.setVisibility(View.VISIBLE);
        
        // Test with a sample patient message that should trigger memory extraction
        String testMessage = "Hello, I'm doing well today. I was just thinking about my childhood in Chicago with my sister Mary. We used to play in the garden behind our house.";
        
        Log.d(TAG, "🧪 TEST INPUT: " + testMessage);
        
        // Process the test message
        processUserInput(testMessage);
        
        // Hide status after a delay
        new android.os.Handler().postDelayed(() -> {
            statusText.setVisibility(View.GONE);
        }, 2000);
    }
    
    private void processUserInput(String userText) {
        // Add user message to chat
        ChatMessage userMessage = new ChatMessage(userText, true, System.currentTimeMillis());
        chatMessages.add(userMessage);
        chatAdapter.notifyItemInserted(chatMessages.size() - 1);
        scrollToBottom();
        
        // Show typing indicator
        statusText.setText("AI is thinking...");
        statusText.setVisibility(View.VISIBLE);
        
        // Process with Gemini AI
        geminiChatService.sendMessage(userText, new GeminiChatService.ChatCallback() {
            @Override
            public void onResponse(String response) {
                runOnUiThread(() -> {
                    statusText.setVisibility(View.GONE);
                    
                    // Add AI response to chat
                    ChatMessage aiMessage = new ChatMessage(response, false, System.currentTimeMillis());
                    chatMessages.add(aiMessage);
                    chatAdapter.notifyItemInserted(chatMessages.size() - 1);
                    scrollToBottom();
                    
                    // Speak the response
                    speakText(response);
                    
                    // Save conversation for analysis
                    saveConversation(userText, response);
                });
            }
            
            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    statusText.setVisibility(View.GONE);
                    Log.e(TAG, "Gemini API error: " + error);
                    
                    String errorResponse = "I'm sorry, I'm having trouble understanding right now. " +
                                         "Could you please try saying that again?";
                    
                    ChatMessage errorMessage = new ChatMessage(errorResponse, false, System.currentTimeMillis());
                    chatMessages.add(errorMessage);
                    chatAdapter.notifyItemInserted(chatMessages.size() - 1);
                    scrollToBottom();
                    
                    speakText(errorResponse);
                });
            }
        });
    }
    
    private void speakText(String text) {
        if (textToSpeech != null && isTtsReady) {
            // Configure TTS for elderly users
            textToSpeech.setSpeechRate(0.8f); // Slightly slower for better comprehension
            textToSpeech.setPitch(1.0f); // Normal pitch
            textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, "chatbot_response");
        }
    }
    
    private void saveConversation(String userInput, String aiResponse) {
        // Enhanced conversation saving with memory extraction
        Log.d(TAG, "Saving conversation - User: " + userInput + ", AI: " + aiResponse);
        
        // Save to Firebase (simplified for now - will enhance with full repository later)
        saveConversationToFirebase(userInput, aiResponse);
        
        // Extract and analyze for memories and cognitive markers
        analyzeConversationForMemories(userInput);
    }
    
    private void saveConversationToFirebase(String userInput, String aiResponse) {
        try {
            // Get current user ID
            String patientId = getCurrentPatientId();
            if (patientId == null) {
                Log.w(TAG, "No patient ID available, skipping conversation save");
                return;
            }
            
            // Create conversation data for Firebase
            java.util.Map<String, Object> conversationData = new java.util.HashMap<>();
            conversationData.put("patientId", patientId);
            conversationData.put("timestamp", new java.util.Date());
            conversationData.put("userInput", userInput);
            conversationData.put("aiResponse", aiResponse);
            conversationData.put("sessionId", getCurrentSessionId());
            
            // Extract basic memory indicators for immediate storage
            java.util.List<String> detectedMemories = extractBasicMemories(userInput);
            conversationData.put("detectedMemories", detectedMemories);
            
            // Save to Firebase Firestore under patient's document
            com.google.firebase.firestore.FirebaseFirestore db = com.google.firebase.firestore.FirebaseFirestore.getInstance();
            db.collection("patients")
                .document(patientId)
                .collection("conversations")
                .add(conversationData)
                .addOnSuccessListener(documentReference -> {
                    Log.d(TAG, "Conversation saved successfully with ID: " + documentReference.getId());
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error saving conversation", e);
                });
                
        } catch (Exception e) {
            Log.e(TAG, "Exception saving conversation", e);
        }
    }
    
    private String getCurrentPatientId() {
        // Get patient ID from Firebase Auth or SharedPreferences
        try {
            com.google.firebase.auth.FirebaseAuth auth = com.google.firebase.auth.FirebaseAuth.getInstance();
            com.google.firebase.auth.FirebaseUser user = auth.getCurrentUser();
            if (user != null) {
                return user.getUid();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting patient ID", e);
        }
        
        // Fallback to shared preferences or default
        android.content.SharedPreferences prefs = getSharedPreferences("AlzheimersCaregiverPrefs", MODE_PRIVATE);
        return prefs.getString("patientId", "default_patient");
    }
    
    private String getCurrentSessionId() {
        // Generate or retrieve current chat session ID
        if (currentSessionId == null) {
            currentSessionId = "chat_" + System.currentTimeMillis();
        }
        return currentSessionId;
    }
    
    private java.util.List<String> extractBasicMemories(String text) {
        java.util.List<String> memories = new java.util.ArrayList<>();
        if (text == null || text.trim().isEmpty()) return memories;
        
        String lowerText = text.toLowerCase();
        
        // Basic memory indicators
        String[] memoryIndicators = {
            "i remember", "when i was", "back in", "years ago", "i used to", 
            "my husband", "my wife", "my children", "my mother", "my father"
        };
        
        for (String indicator : memoryIndicators) {
            if (lowerText.contains(indicator)) {
                // Extract the sentence containing the memory
                String[] sentences = text.split("[.!?]+");
                for (String sentence : sentences) {
                    if (sentence.toLowerCase().contains(indicator)) {
                        memories.add(sentence.trim());
                        break;
                    }
                }
            }
        }
        
        return memories;
    }
    
    private void analyzeConversationForMemories(String userInput) {
        // Enhanced analysis for therapeutic value and memory extraction
        if (userInput == null || userInput.trim().isEmpty()) return;
        
        String lowerText = userInput.toLowerCase();
        
        // Check for high-value therapeutic content
        String[] therapeuticIndicators = {
            "happy", "proud", "loved", "family", "children", "wedding",
            "birthday", "holiday", "vacation", "achievement", "success"
        };
        
        // Check for memory indicators
        String[] memoryIndicators = {
            "i remember", "when i was", "back in", "years ago", "i used to", 
            "my husband", "my wife", "my children", "my mother", "my father"
        };
        
        boolean hasTherapeuticValue = false;
        boolean hasMemoryContent = false;
        
        for (String indicator : therapeuticIndicators) {
            if (lowerText.contains(indicator)) {
                hasTherapeuticValue = true;
                Log.d(TAG, "High therapeutic value detected - contains: " + indicator);
                break;
            }
        }
        
        for (String indicator : memoryIndicators) {
            if (lowerText.contains(indicator)) {
                hasMemoryContent = true;
                Log.d(TAG, "Memory content detected - contains: " + indicator);
                break;
            }
        }
        
        if (hasTherapeuticValue || hasMemoryContent) {
            // Mark this conversation as valuable for story generation
            markConversationForStoryUse(userInput);
            
            // Store for enhanced memory extraction
            storeConversationForMemoryExtraction(userInput, hasTherapeuticValue, hasMemoryContent);
        }
    }
    
    private void storeConversationForMemoryExtraction(String userInput, boolean hasTherapeuticValue, boolean hasMemoryContent) {
        try {
            // Store conversation data for later processing by memory extraction service
            android.content.SharedPreferences prefs = getSharedPreferences("ConversationMemories", MODE_PRIVATE);
            android.content.SharedPreferences.Editor editor = prefs.edit();
            
            String timestamp = String.valueOf(System.currentTimeMillis());
            String key = "conversation_" + timestamp;
            
            // Store conversation text and metadata
            editor.putString(key + "_text", userInput);
            editor.putBoolean(key + "_therapeutic", hasTherapeuticValue);
            editor.putBoolean(key + "_memory", hasMemoryContent);
            editor.putLong(key + "_timestamp", System.currentTimeMillis());
            editor.putString(key + "_session", getCurrentSessionId());
            
            editor.apply();
            
            Log.d(TAG, "Stored conversation for memory extraction: " + 
                  userInput.substring(0, Math.min(50, userInput.length())) + "...");
            
        } catch (Exception e) {
            Log.e(TAG, "Error storing conversation for memory extraction", e);
        }
    }
    
    private void markConversationForStoryUse(String userInput) {
        Log.d(TAG, "Marking conversation for potential story generation: " + 
              userInput.substring(0, Math.min(50, userInput.length())) + "...");
        
        // Store in SharedPreferences for later story generation
        android.content.SharedPreferences prefs = getSharedPreferences("StoryMemories", MODE_PRIVATE);
        android.content.SharedPreferences.Editor editor = prefs.edit();
        
        // Get existing memories and add new one
        java.util.Set<String> existingMemories = prefs.getStringSet("therapeutic_memories", new java.util.HashSet<>());
        java.util.Set<String> updatedMemories = new java.util.HashSet<>(existingMemories);
        updatedMemories.add(userInput);
        
        editor.putStringSet("therapeutic_memories", updatedMemories);
        editor.apply();
        
        Log.d(TAG, "Total therapeutic memories stored: " + updatedMemories.size());
    }
    
    private void scrollToBottom() {
        if (chatMessages.size() > 0) {
            chatRecyclerView.smoothScrollToPosition(chatMessages.size() - 1);
        }
    }
    
    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            int result = textToSpeech.setLanguage(Locale.getDefault());
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e(TAG, "TTS language not supported");
            } else {
                isTtsReady = true;
                Log.d(TAG, "TTS initialized successfully");
            }
        } else {
            Log.e(TAG, "TTS initialization failed");
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }
        
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
    }
    
    /**
     * Speech Recognition Listener
     */
    private class SpeechRecognitionListener implements RecognitionListener {
        
        @Override
        public void onReadyForSpeech(Bundle params) {
            Log.d(TAG, "Ready for speech");
        }
        
        @Override
        public void onBeginningOfSpeech() {
            Log.d(TAG, "Beginning of speech");
            statusText.setText("Listening...");
        }
        
        @Override
        public void onRmsChanged(float rmsdB) {
            // Visual feedback for voice level could be added here
        }
        
        @Override
        public void onBufferReceived(byte[] buffer) {
            // Not used
        }
        
        @Override
        public void onEndOfSpeech() {
            Log.d(TAG, "End of speech");
            statusText.setText("Processing...");
        }
        
        @Override
        public void onError(int error) {
            String errorMessage = "Speech recognition error";
            switch (error) {
                case SpeechRecognizer.ERROR_AUDIO:
                    errorMessage = "Audio recording error";
                    break;
                case SpeechRecognizer.ERROR_CLIENT:
                    errorMessage = "Client side error";
                    break;
                case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                    errorMessage = "Insufficient permissions";
                    break;
                case SpeechRecognizer.ERROR_NETWORK:
                    errorMessage = "Network error";
                    break;
                case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                    errorMessage = "Network timeout";
                    break;
                case SpeechRecognizer.ERROR_NO_MATCH:
                    errorMessage = "No speech input detected";
                    break;
                case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                    errorMessage = "Recognition service busy";
                    break;
                case SpeechRecognizer.ERROR_SERVER:
                    errorMessage = "Server error";
                    break;
                case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                    errorMessage = "No speech input detected";
                    break;
            }
            
            Log.e(TAG, "Speech recognition error: " + errorMessage);
            
            isListening = false;
            updateMicButtonState();
            statusText.setVisibility(View.GONE);
            
            if (error != SpeechRecognizer.ERROR_NO_MATCH && error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                Toast.makeText(ChatbotActivity.this, "Voice recognition error. Please try again.", Toast.LENGTH_SHORT).show();
            }
        }
        
        @Override
        public void onResults(Bundle results) {
            ArrayList<String> voiceResults = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            if (voiceResults != null && !voiceResults.isEmpty()) {
                String recognizedText = voiceResults.get(0);
                Log.d(TAG, "✅ Speech Recognition SUCCESS - Recognized text: " + recognizedText);
                Log.d(TAG, "Results count: " + voiceResults.size());
                
                // Log all results for debugging
                for (int i = 0; i < voiceResults.size(); i++) {
                    Log.d(TAG, "Result " + i + ": " + voiceResults.get(i));
                }
                
                isListening = false;
                updateMicButtonState();
                statusText.setVisibility(View.GONE);
                
                // Process the recognized text
                processUserInput(recognizedText);
            } else {
                Log.w(TAG, "❌ Speech Recognition - No results in bundle");
                
                // Fallback: Use last partial result if available
                if (lastPartialResult != null && !lastPartialResult.trim().isEmpty()) {
                    Log.d(TAG, "🔄 Using last partial result as fallback: " + lastPartialResult);
                    
                    isListening = false;
                    updateMicButtonState();
                    statusText.setVisibility(View.GONE);
                    
                    // Process the last partial result
                    processUserInput(lastPartialResult);
                    
                    // Clear the partial result after using it
                    lastPartialResult = "";
                } else {
                    Log.w(TAG, "❌ No partial results available for fallback");
                    isListening = false;
                    updateMicButtonState();
                    statusText.setVisibility(View.GONE);
                }
            }
        }
        
        @Override
        public void onPartialResults(Bundle partialResults) {
            ArrayList<String> partialVoiceResults = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            if (partialVoiceResults != null && !partialVoiceResults.isEmpty()) {
                String partialText = partialVoiceResults.get(0);
                Log.d(TAG, "Partial recognition: " + partialText);
                
                // Store the last partial result as fallback
                lastPartialResult = partialText;
                
                // Update status to show partial results for better user feedback
                statusText.setText("Listening: " + partialText);
            }
        }
        
        @Override
        public void onEvent(int eventType, Bundle params) {
            // Not used
        }
    }
}