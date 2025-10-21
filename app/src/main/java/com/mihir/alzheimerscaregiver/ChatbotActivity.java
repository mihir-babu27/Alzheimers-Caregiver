package com.mihir.alzheimerscaregiver;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.view.inputmethod.EditorInfo;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.mihir.alzheimerscaregiver.utils.LanguagePreferenceManager;
import com.mihir.alzheimerscaregiver.utils.TextToSpeechManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
public class ChatbotActivity extends AppCompatActivity {

    private static final String TAG = "ChatbotActivity";
    private static final int RECORD_AUDIO_PERMISSION_CODE = 1001;
    
    // UI Components
    private RecyclerView chatRecyclerView;
    private TextView statusText;
    private ImageButton backButton;
    private EditText textInput;
    private ImageButton sendButton;
    
    // Chat Components
    private ChatAdapter chatAdapter;
    private List<ChatMessage> chatMessages;
    
    // Speech Components
    private SpeechRecognizer speechRecognizer;
    private TextToSpeechManager textToSpeechManager;
    private boolean isListening = false;
    private boolean isTtsReady = false;
    private String lastPartialResult = ""; // Store last partial result as fallback
    
    // Language support
    private String currentLanguage;
    private Map<String, Locale> languageLocales;
    
    // AI Service
    private GeminiChatService geminiChatService;
    
    // Session tracking
    private String currentSessionId;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chatbot);
        
        initializeLanguageSupport();
        initializeViews();
        setupRecyclerView();
        initializeSpeechServices();
        initializeGeminiService();
        checkPermissions();
        
        // Welcome message
        addWelcomeMessage();
    }
    
    /**
     * Initialize language support based on user preferences
     */
    private void initializeLanguageSupport() {
        // Get user's language preference
        currentLanguage = LanguagePreferenceManager.getPreferredLanguage(this);
        Log.d(TAG, "Initializing chatbot with language: " + currentLanguage);
        
        // Initialize language locale mapping (same as TextToSpeechManager)
        languageLocales = new HashMap<>();
        languageLocales.put(LanguagePreferenceManager.LANGUAGE_ENGLISH, Locale.ENGLISH);
        languageLocales.put(LanguagePreferenceManager.LANGUAGE_HINDI, new Locale("hi", "IN"));
        languageLocales.put(LanguagePreferenceManager.LANGUAGE_KANNADA, new Locale("kn", "IN"));
        languageLocales.put(LanguagePreferenceManager.LANGUAGE_TAMIL, new Locale("ta", "IN"));
        languageLocales.put(LanguagePreferenceManager.LANGUAGE_TELUGU, new Locale("te", "IN"));
        languageLocales.put(LanguagePreferenceManager.LANGUAGE_MALAYALAM, new Locale("ml", "IN"));
    }
    
    private void initializeViews() {
        chatRecyclerView = findViewById(R.id.chatRecyclerView);
        statusText = findViewById(R.id.statusText);
        backButton = findViewById(R.id.backButton);
        textInput = findViewById(R.id.textInput);
        sendButton = findViewById(R.id.sendButton);
        
        // Set up back button
        backButton.setOnClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
            finish();
        });
        
        // Set up send button for text input
        sendButton.setOnClickListener(v -> {
            String message = textInput.getText().toString().trim();
            if (!message.isEmpty()) {
                sendTextMessage(message);
                textInput.setText("");
                textInput.clearFocus();
            }
        });
        
        // Set up text input enter key
        textInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) {
                String message = textInput.getText().toString().trim();
                if (!message.isEmpty()) {
                    sendTextMessage(message);
                    textInput.setText("");
                    textInput.clearFocus();
                }
                return true;
            }
            return false;
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
        
        // Initialize Text-to-Speech with language support
        textToSpeechManager = new TextToSpeechManager(this, new TextToSpeechManager.TTSCallback() {
            @Override
            public void onTTSInitialized() {
                isTtsReady = true;
                Log.d(TAG, "TTS initialized for language: " + currentLanguage);
            }
            
            @Override
            public void onSpeechStart() {
                // TTS started speaking
            }
            
            @Override
            public void onSpeechDone() {
                // TTS finished speaking
            }
            
            @Override
            public void onTTSError(String error) {
                Log.e(TAG, "TTS Error: " + error);
            }
        });
    }
    
    private void initializeGeminiService() {
        geminiChatService = new GeminiChatService(currentLanguage);
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        // Refresh welcome message in case language changed
        refreshWelcomeMessage();
    }
    
    private void refreshWelcomeMessage() {
        // Update current language from preferences
        currentLanguage = LanguagePreferenceManager.getPreferredLanguage(this);
        
        // Update Gemini service language
        if (geminiChatService != null) {
            geminiChatService = new GeminiChatService(currentLanguage);
        }
        
        // Update welcome message if it's the first message in chat
        if (!chatMessages.isEmpty() && !chatMessages.get(0).isFromUser()) {
            String newWelcomeText = getWelcomeMessageForLanguage(currentLanguage);
            chatMessages.get(0).setText(newWelcomeText);
            chatAdapter.notifyItemChanged(0);
            
            // Speak the new welcome message
            speakText(newWelcomeText);
        }
    }
    
    private void addWelcomeMessage() {
        String welcomeText = getWelcomeMessageForLanguage(currentLanguage);
        
        ChatMessage welcomeMessage = new ChatMessage(welcomeText, false, System.currentTimeMillis());
        chatMessages.add(welcomeMessage);
        chatAdapter.notifyItemInserted(chatMessages.size() - 1);
        scrollToBottom();
        
        // Speak welcome message
        speakText(welcomeText);
    }
    
    private String getWelcomeMessageForLanguage(String language) {
        switch (language) {
            case "Hindi":
                return "नमस्ते! मैं आपका AI सहायक हूं। मैं आपसे बात करने और आपकी दैनिक गतिविधियों में मदद करने के लिए यहां हूं। आप टेक्स्ट बॉक्स का उपयोग करके मुझसे कुछ भी पूछ सकते हैं!";
            case "Tamil":
                return "வணக்கம்! நான் உங்கள் AI உதவியாளர். நான் உங்களுடன் பேசவும் உங்கள் தினசரி செயல்பாடுகளில் உதவவும் இங்கே இருக்கிறேன். நீங்கள் டெக்ஸ்ட் பாக்ஸைப் பயன்படுத்தி என்னிடம் எதுவும் கேட்கலாம்!";
            case "Telugu":
                return "నమస్కారం! నేను మీ AI సహాయకుడిని. మీతో మాట్లాడటానికి మరియు మీ రోజువారీ కార్యకలాపాలలో సహాయం చేయడానికి నేను ఇక్కడ ఉన్నాను. మీరు టెక్స్ట్ బాక్స్ ఉపయోగించి నన్ను ఏదైనా అడగవచ్చు!";
            case "Kannada":
                return "ನಮಸ್ಕಾರ! ನಾನು ನಿಮ್ಮ AI ಸಹಾಯಕ. ನಿಮ್ಮೊದನೆ ಮಾತನಾಡಲು ಮತ್ತು ನಿಮ್ಮ ದೈನಂದಿನ ಚಟುವಟಿಕೆಗಳಲ್ಲಿ ಸಹಾಯ ಮಾಡಲು ನಾನು ಇಲ್ಲಿದ್ದೇನೆ. ನೀವು ಟೆಕ್ಸ್ಟ್ ಬಾಕ್ಸ್ ಬಳಸಿ ನನ್ನನ್ನು ಏನು ಬೇಕಾದರೂ ಕೇಳಬಹುದು!";
            case "Malayalam":
                return "നമസ്കാരം! ഞാൻ നിങ്ങളുടെ AI സഹായകനാണ്. നിങ്ങളോട് സംസാരിക്കാനും നിങ്ങളുടെ ദൈനംദിന പ്രവർത്തനങ്ങളിൽ സഹായിക്കാനും ഞാൻ ഇവിടെയുണ്ട്. നിങ്ങൾക്ക് ടെക്സ്റ്റ് ബോക്സ് ഉപയോഗിച്ച് എന്നോട് എന്തും ചോദിക്കാം!";
            default: // English
                return "Hello! I'm your AI assistant. I'm here to chat with you and help with your daily activities. You can use the text box to ask me anything you'd like!";
        }
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
    
    /**
     * Send a text message (from keyboard input)
     */
    private void sendTextMessage(String message) {
        Log.d(TAG, "📝 Sending text message: " + message);
        
        // Process the text input (processUserInput will handle adding message to chat)
        processUserInput(message);
    }
    
    // Speech recognition methods removed - using text input only
    private void startListening() {
        // Method kept for compatibility but not used
    }
    
    private void stopListening() {
        // Method kept for compatibility but not used  
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
        if (textToSpeechManager != null && isTtsReady) {
            // Use TextToSpeechManager with current language
            textToSpeechManager.speak(text, currentLanguage);
        }
    }
    
    private void saveConversation(String userInput, String aiResponse) {
        // Enhanced conversation saving with AI-powered memory extraction
        Log.d(TAG, "Saving conversation - User: " + userInput + ", AI: " + aiResponse);
        
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
            
            // Use AI-powered memory extraction for better multi-language support
            extractMemoriesWithAI(userInput, aiResponse, conversationData, patientId);
            
        } catch (Exception e) {
            Log.e(TAG, "Exception saving conversation", e);
        }
        
        // Keep the old memory analysis for additional insights
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
            
        } catch (Exception e) {
            Log.e(TAG, "Exception saving conversation", e);
        }
    }
    
    /**
     * Extract memories using AI analysis for better multi-language support
     */
    private void extractMemoriesWithAI(String userInput, String aiResponse, java.util.Map<String, Object> conversationData, String patientId) {
        // Extract memories only from user input (not AI response to avoid false memories)
        String userOnlyText = "User said: " + userInput;
        
        Log.d(TAG, "🧠 Starting AI memory extraction for conversation");
        Log.d(TAG, "Analyzing user input only: " + userOnlyText);
        
        // Use GeminiChatService for AI-powered memory extraction (user input only)
        geminiChatService.extractMemoriesWithAI(userOnlyText, new GeminiChatService.MemoryExtractionCallback() {
            @Override
            public void onMemoriesExtracted(java.util.List<String> memories) {
                Log.d(TAG, "✅ AI memory extraction successful! Found " + memories.size() + " memories");
                
                // Add extracted memories to conversation data
                conversationData.put("detectedMemories", memories);
                
                // Save to Firebase with the extracted memories
                saveConversationToFirebase(conversationData, patientId);
                
                // Log for debugging
                Log.d(TAG, "AI extracted " + memories.size() + " memories:");
                for (String memory : memories) {
                    Log.d(TAG, "  - " + memory);
                }
            }
            
            @Override
            public void onError(String error) {
                Log.e(TAG, "❌ AI memory extraction failed: " + error);
                
                // Fallback to empty memories list and still save the conversation
                conversationData.put("detectedMemories", new java.util.ArrayList<String>());
                saveConversationToFirebase(conversationData, patientId);
            }
        });
    }
    
    /**
     * Save conversation data to Firebase
     */
    private void saveConversationToFirebase(java.util.Map<String, Object> conversationData, String patientId) {
        try {
            // Save to Firebase Firestore under patient's document
            com.google.firebase.firestore.FirebaseFirestore db = com.google.firebase.firestore.FirebaseFirestore.getInstance();
            db.collection("patients")
                .document(patientId)
                .collection("conversations")
                .add(conversationData)
                .addOnSuccessListener(documentReference -> {
                    Log.d(TAG, "Conversation saved successfully with ID: " + documentReference.getId());
                    
                    // Log the extracted memories for debugging
                    @SuppressWarnings("unchecked")
                    java.util.List<String> memories = (java.util.List<String>) conversationData.get("detectedMemories");
                    if (memories != null && !memories.isEmpty()) {
                        Log.d(TAG, "AI extracted " + memories.size() + " memories for story generation");
                        for (String memory : memories) {
                            Log.d(TAG, "  Memory: " + memory);
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error saving conversation", e);
                });
        } catch (Exception e) {
            Log.e(TAG, "Exception saving conversation to Firebase", e);
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
    
    /**
     * Get language code for speech recognition based on current language
     */
    private String getLanguageCode(String language) {
        Locale locale = languageLocales.get(language);
        if (locale != null) {
            return locale.toString();
        }
        return Locale.getDefault().toString();
    }
    
    private java.util.List<String> extractBasicMemories(String text) {
        java.util.List<String> memories = new java.util.ArrayList<>();
        if (text == null || text.trim().isEmpty()) return memories;
        
        String lowerText = text.toLowerCase();
        
        // Get language-specific memory patterns
        Map<String, String[]> languageMemoryPatterns = getLanguageSpecificMemoryPatterns();
        Map<String, String[]> languageRelationshipWords = getLanguageSpecificRelationshipWords();
        Map<String, String[]> languageLocationWords = getLanguageSpecificLocationWords();
        Map<String, String[]> languageTimeWords = getLanguageSpecificTimeWords();
        
        // Default English patterns
        String[] memoryIndicators = languageMemoryPatterns.getOrDefault(currentLanguage, languageMemoryPatterns.get(LanguagePreferenceManager.LANGUAGE_ENGLISH));
        String[] relationshipWords = languageRelationshipWords.getOrDefault(currentLanguage, languageRelationshipWords.get(LanguagePreferenceManager.LANGUAGE_ENGLISH));
        String[] locationWords = languageLocationWords.getOrDefault(currentLanguage, languageLocationWords.get(LanguagePreferenceManager.LANGUAGE_ENGLISH));
        String[] timeWords = languageTimeWords.getOrDefault(currentLanguage, languageTimeWords.get(LanguagePreferenceManager.LANGUAGE_ENGLISH));
        
        // Check for memory indicator phrases and extract surrounding context
        for (String indicator : memoryIndicators) {
            if (lowerText.contains(indicator.toLowerCase())) {
                // Extract the sentence containing the memory
                String[] sentences = text.split("[.!?]+");
                for (String sentence : sentences) {
                    if (sentence.toLowerCase().contains(indicator.toLowerCase())) {
                        memories.add("Memory: " + sentence.trim());
                        break;
                    }
                }
            }
        }
        
        // Check for relationship mentions
        for (String relationship : relationshipWords) {
            if (lowerText.contains(relationship.toLowerCase())) {
                memories.add("Relationship: " + relationship);
            }
        }
        
        // Check for location mentions
        for (String location : locationWords) {
            if (lowerText.contains(location.toLowerCase())) {
                memories.add("Location: " + location);
            }
        }
        
        // Check for time references
        for (String timeRef : timeWords) {
            if (lowerText.contains(timeRef.toLowerCase())) {
                memories.add("Time reference: " + timeRef);
            }
        }
        
        return memories;
    }
    
    /**
     * Get language-specific memory indicator patterns
     */
    private Map<String, String[]> getLanguageSpecificMemoryPatterns() {
        Map<String, String[]> patterns = new HashMap<>();
        
        // English patterns
        patterns.put(LanguagePreferenceManager.LANGUAGE_ENGLISH, new String[]{
            "i remember", "when i was", "back in", "years ago", "i used to", 
            "my husband", "my wife", "my children", "my mother", "my father",
            "my sister", "my brother", "childhood", "growing up", "we used to",
            "i was thinking about", "reminds me of"
        });
        
        // Hindi patterns
        patterns.put(LanguagePreferenceManager.LANGUAGE_HINDI, new String[]{
            "मुझे याद है", "जब मैं था", "बचपन में", "पहले", "मेरा पति", "मेरी पत्नी",
            "मेरे बच्चे", "मेरी माँ", "मेरे पिता", "मेरा भाई", "मेरी बहन",
            "बड़े होते समय", "हम करते थे", "याद आता है", "सोच रहा था"
        });
        
        // Tamil patterns
        patterns.put(LanguagePreferenceManager.LANGUAGE_TAMIL, new String[]{
            "எனக்கு நினைவிருக்கிறது", "நான் இருந்த போது", "சிறுவயதில்", "முன்பு", "என் கணவர்", "என் மனைவி",
            "என் குழந்தைகள்", "என் அம்மா", "என் அப்பா", "என் சகோதரன்", "என் சகோதரி",
            "வளர்ந்த காலம்", "நாங்கள் செய்தோம்", "நினைவு வருகிறது", "நினைத்துக் கொண்டிருந்தேன்"
        });
        
        // Telugu patterns
        patterns.put(LanguagePreferenceManager.LANGUAGE_TELUGU, new String[]{
            "నాకు గుర్తుంది", "నేను ఉన్నప్పుడు", "చిన్నప్పుడు", "మునుపు", "నా భర్త", "నా భార్య",
            "నా పిల్లలు", "నా అమ్మ", "నా నాన్న", "నా అన్న", "నా అక్క",
            "పెరిగిన కాలం", "మేము చేసేవాళ్ళం", "గుర్తు వస్తుంది", "ఆలోచిస్తున్నాను"
        });
        
        // Kannada patterns
        patterns.put(LanguagePreferenceManager.LANGUAGE_KANNADA, new String[]{
            "ನನಗೆ ನೆನಪಿದೆ", "ನಾನು ಇದ್ದಾಗ", "ಬಾಲ್ಯದಲ್ಲಿ", "ಮೊದಲು", "ನನ್ನ ಗಂಡ", "ನನ್ನ ಹೆಂಡತಿ",
            "ನನ್ನ ಮಕ್ಕಳು", "ನನ್ನ ಅಮ್ಮ", "ನನ್ನ ಅಪ್ಪ", "ನನ್ನ ಅಣ್ಣ", "ನನ್ನ ಅಕ್ಕ",
            "ಬೆಳೆದ ಕಾಲ", "ನಾವು ಮಾಡುತ್ತಿದ್ದೆವು", "ನೆನಪಾಗುತ್ತದೆ", "ಯೋಚಿಸುತ್ತಿದ್ದೆ"
        });
        
        // Malayalam patterns
        patterns.put(LanguagePreferenceManager.LANGUAGE_MALAYALAM, new String[]{
            "എനിക്ക് ഓർമയുണ്ട്", "ഞാൻ ഉണ്ടായിരുന്നപ്പോൾ", "കുട്ടിക്കാലത്ത്", "മുമ്പ്", "എന്റെ ഭർത്താവ്", "എന്റെ ഭാര്യ",
            "എന്റെ കുട്ടികൾ", "എന്റെ അമ്മ", "എന്റെ അച്ഛൻ", "എന്റെ സഹോദരൻ", "എന്റെ സഹോദരി",
            "വളർന്ന കാലം", "ഞങ്ങൾ ചെയ്തിരുന്നു", "ഓർമ വരുന്നു", "ചിന്തിക്കുകയായിരുന്നു"
        });
        
        return patterns;
    }
    
    /**
     * Get language-specific relationship words
     */
    private Map<String, String[]> getLanguageSpecificRelationshipWords() {
        Map<String, String[]> relationships = new HashMap<>();
        
        // English relationships
        relationships.put(LanguagePreferenceManager.LANGUAGE_ENGLISH, new String[]{
            "husband", "wife", "mother", "father", "son", "daughter", 
            "brother", "sister", "friend", "neighbor", "colleague", 
            "boss", "teacher", "doctor", "nurse", "grandson", "granddaughter"
        });
        
        // Hindi relationships
        relationships.put(LanguagePreferenceManager.LANGUAGE_HINDI, new String[]{
            "पति", "पत्नी", "माँ", "पिता", "बेटा", "बेटी", "भाई", "बहन",
            "दोस्त", "पड़ोसी", "सहयोगी", "बॉस", "शिक्षक", "डॉक्टर", "नर्स", "पोता", "पोती"
        });
        
        // Tamil relationships
        relationships.put(LanguagePreferenceManager.LANGUAGE_TAMIL, new String[]{
            "கணவர்", "மனைவி", "அம்மா", "அப்பா", "மகன்", "மகள்", "சகோதரன்", "சகோதரி",
            "நண்பர்", "அண்டை வீட்டார்", "சக பணியாளர்", "முதலாளி", "ஆசிரியர்", "மருத்துவர்", "செவிலியர்", "பேரன்", "பேத்தி"
        });
        
        // Telugu relationships
        relationships.put(LanguagePreferenceManager.LANGUAGE_TELUGU, new String[]{
            "భర్త", "భార్య", "అమ్మ", "నాన్న", "కొడుకు", "కూతురు", "అన్న", "అక్క",
            "స్నేహితుడు", "పొరుగువాడు", "సహోద్యోగి", "యజమాని", "గురువు", "వైద్యుడు", "నర్సు", "మనవడు", "మనవరాలు"
        });
        
        // Kannada relationships
        relationships.put(LanguagePreferenceManager.LANGUAGE_KANNADA, new String[]{
            "ಗಂಡ", "ಹೆಂಡತಿ", "ಅಮ್ಮ", "ಅಪ್ಪ", "ಮಗ", "ಮಗಳು", "ಅಣ್ಣ", "ಅಕ್ಕ",
            "ಸ್ನೇಹಿತ", "ನೆರೆಹೊರೆಯವರು", "ಸಹೋದ್ಯೋಗಿ", "ಮುಖ್ಯಸ್ಥ", "ಗುರು", "ವೈದ್ಯ", "ನರ್ಸ್", "ಮೊಮ್ಮಗ", "ಮೊಮ್ಮಗಳು"
        });
        
        // Malayalam relationships
        relationships.put(LanguagePreferenceManager.LANGUAGE_MALAYALAM, new String[]{
            "ഭർത്താവ്", "ഭാര്യ", "അമ്മ", "അച്ഛൻ", "മകൻ", "മകൾ", "സഹോദരൻ", "സഹോദരി",
            "സുഹൃത്ത്", "അയൽവാസി", "സഹപ്രവർത്തകൻ", "മുഖ്യൻ", "ഗുരു", "ഡോക്ടർ", "നഴ്സ്", "കൊച്ചുമകൻ", "കൊച്ചുമകൾ"
        });
        
        return relationships;
    }
    
    /**
     * Get language-specific location words
     */
    private Map<String, String[]> getLanguageSpecificLocationWords() {
        Map<String, String[]> locations = new HashMap<>();
        
        // English locations
        locations.put(LanguagePreferenceManager.LANGUAGE_ENGLISH, new String[]{
            "lived in", "grew up in", "moved to", "traveled to", "visited",
            "hometown", "neighborhood", "city", "country", "house", "home",
            "school", "church", "hospital", "work", "office", "factory", "garden"
        });
        
        // Hindi locations
        locations.put(LanguagePreferenceManager.LANGUAGE_HINDI, new String[]{
            "रहता था", "बड़ा हुआ", "गया", "घूमा", "गया था", "शहर", "गाँव", "घर",
            "स्कूल", "मंदिर", "अस्पताल", "काम", "ऑफिस", "फैक्टरी", "बगीचा"
        });
        
        // Tamil locations
        locations.put(LanguagePreferenceManager.LANGUAGE_TAMIL, new String[]{
            "வாழ்ந்தேன்", "வளர்ந்தேன்", "சென்றேன்", "பயணம்", "போனேன்", "ஊர்", "ஒரு", "வீடு",
            "பள்ளி", "கோயில்", "மருத்துவமனை", "வேலை", "அலுவலகம்", "தொழிற்சாலை", "தோட்டம்"
        });
        
        // Telugu locations
        locations.put(LanguagePreferenceManager.LANGUAGE_TELUGU, new String[]{
            "ఉన్నాను", "పెరిగాను", "వెళ్ళాను", "యాత్ర", "వెళ్ళాను", "ఊరు", "ఇంటి", "ఇల్లు",
            "పాఠశాల", "దేవాలయం", "ఆసుపత్రి", "పని", "కార్యాలయం", "కర్మాగారం", "తోట"
        });
        
        // Kannada locations
        locations.put(LanguagePreferenceManager.LANGUAGE_KANNADA, new String[]{
            "ಇದ್ದೆ", "ಬೆಳೆದೆ", "ಹೋದೆ", "ಪ್ರಯಾಣ", "ಹೋಗಿದ್ದೆ", "ಊರು", "ಮನೆ", "ಮನೆ",
            "ಶಾಲೆ", "ದೇವಾಲಯ", "ಆಸ್ಪತ್ರೆ", "ಕೆಲಸ", "ಕಚೇರಿ", "ಕಾರ್ಖಾನೆ", "ತೋಟ"
        });
        
        // Malayalam locations
        locations.put(LanguagePreferenceManager.LANGUAGE_MALAYALAM, new String[]{
            "താമസിച്ചു", "വളർന്നു", "പോയി", "യാത്ര", "പോയിരുന്നു", "നാട്", "വീട്", "വീട്",
            "സ്കൂൾ", "ക്ഷേത്രം", "ആശുപത്രി", "ജോലി", "ഓഫീസ്", "ഫാക്ടറി", "തോട്ടം"
        });
        
        return locations;
    }
    
    /**
     * Get language-specific time reference words
     */
    private Map<String, String[]> getLanguageSpecificTimeWords() {
        Map<String, String[]> timeWords = new HashMap<>();
        
        // English time references
        timeWords.put(LanguagePreferenceManager.LANGUAGE_ENGLISH, new String[]{
            "childhood", "when i was young", "years ago", "back then",
            "in my twenties", "in my thirties", "growing up"
        });
        
        // Hindi time references
        timeWords.put(LanguagePreferenceManager.LANGUAGE_HINDI, new String[]{
            "बचपन", "जब मैं छोटा था", "साल पहले", "उस समय", "बीस साल की उम्र में", "तीस साल की उम्र में", "बड़े होते समय"
        });
        
        // Tamil time references
        timeWords.put(LanguagePreferenceManager.LANGUAGE_TAMIL, new String[]{
            "சிறுவயது", "நான் சிறியவனாக இருந்தபோது", "வருடங்களுக்கு முன்பு", "அந்த நேரத்தில்", "இருபதுகளில்", "முப்பதுகளில்", "வளரும் போது"
        });
        
        // Telugu time references
        timeWords.put(LanguagePreferenceManager.LANGUAGE_TELUGU, new String[]{
            "చిన్నప్పుడు", "నేను చిన్నవాడిగా ఉన్నప్పుడు", "సంవత్సరాల క్రితం", "ఆ సమయంలో", "ఇరవైలలో", "ముప్పైలలో", "పెరుగుతున్న సమయంలో"
        });
        
        // Kannada time references
        timeWords.put(LanguagePreferenceManager.LANGUAGE_KANNADA, new String[]{
            "ಬಾಲ್ಯ", "ನಾನು ಚಿಕ್ಕವನಾಗಿದ್ದಾಗ", "ವರ್ಷಗಳ ಹಿಂದೆ", "ಆ ಸಮಯದಲ್ಲಿ", "ಇಪ್ಪತ್ತರಲ್ಲಿ", "ಮೂವತ್ತರಲ್ಲಿ", "ಬೆಳೆಯುತ್ತಿರುವಾಗ"
        });
        
        // Malayalam time references
        timeWords.put(LanguagePreferenceManager.LANGUAGE_MALAYALAM, new String[]{
            "കുട്ടിക്കാലം", "ഞാൻ ചെറുപ്പത്തിൽ", "വർഷങ്ങൾക്കു മുമ്പ്", "ആ കാലത്ത്", "ഇരുപതിലെത്തിയപ്പോൾ", "മുപ്പതിലെത്തിയപ്പോൾ", "വളരുന്ന സമയത്ത്"
        });
        
        return timeWords;
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
    protected void onDestroy() {
        super.onDestroy();
        
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }
        
        if (textToSpeechManager != null) {
            textToSpeechManager.shutdown();
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
            // Microphone state update removed - text input only
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
                // Microphone state update removed - text input only
                statusText.setVisibility(View.GONE);
                
                // Process the recognized text
                processUserInput(recognizedText);
            } else {
                Log.w(TAG, "❌ Speech Recognition - No results in bundle");
                
                // Fallback: Use last partial result if available
                if (lastPartialResult != null && !lastPartialResult.trim().isEmpty()) {
                    Log.d(TAG, "🔄 Using last partial result as fallback: " + lastPartialResult);
                    
                    isListening = false;
                    // Microphone state update removed - text input only
                    statusText.setVisibility(View.GONE);
                    
                    // Process the last partial result
                    processUserInput(lastPartialResult);
                    
                    // Clear the partial result after using it
                    lastPartialResult = "";
                } else {
                    Log.w(TAG, "❌ No partial results available for fallback");
                    isListening = false;
                    // Microphone state update removed - text input only
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