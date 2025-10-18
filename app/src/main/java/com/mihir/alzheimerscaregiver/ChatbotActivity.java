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
    
    // AI Service
    private GeminiChatService geminiChatService;
    
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
        if (isListening) {
            stopListening();
        } else {
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
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000);
        
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
        // TODO: Implement conversation saving to Firebase for caregiver review
        // This will store conversations for MMSE analysis and reminiscence therapy
        Log.d(TAG, "Saving conversation - User: " + userInput + ", AI: " + aiResponse);
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
                Log.d(TAG, "Recognized text: " + recognizedText);
                
                isListening = false;
                updateMicButtonState();
                statusText.setVisibility(View.GONE);
                
                // Process the recognized text
                processUserInput(recognizedText);
            }
        }
        
        @Override
        public void onPartialResults(Bundle partialResults) {
            // Could show partial results here for better UX
        }
        
        @Override
        public void onEvent(int eventType, Bundle params) {
            // Not used
        }
    }
}