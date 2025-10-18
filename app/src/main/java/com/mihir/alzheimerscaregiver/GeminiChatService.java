package com.mihir.alzheimerscaregiver;

import android.util.Log;
import com.google.ai.client.generativeai.GenerativeModel;
import com.google.ai.client.generativeai.java.GenerativeModelFutures;
import com.google.ai.client.generativeai.type.Content;
import com.google.ai.client.generativeai.type.GenerateContentResponse;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * GeminiChatService - Handles communication with Google's Gemini AI
 * Specialized for Alzheimer's patient conversations with MMSE assessment capabilities
 */
public class GeminiChatService {
    
    private static final String TAG = "GeminiChatService";
    private static final String MODEL_NAME = "gemini-1.5-flash";
    
    private GenerativeModelFutures model;
    private Executor executor;
    private StringBuilder conversationHistory;
    
    public GeminiChatService() {
        initializeModel();
        executor = Executors.newSingleThreadExecutor();
        conversationHistory = new StringBuilder();
        setupAlzheimerSpecificPrompt();
    }
    
    private void initializeModel() {
        try {
            // Get API key from BuildConfig (will be set in build.gradle)
            String apiKey = BuildConfig.GEMINI_API_KEY;
            
            if (apiKey == null || apiKey.isEmpty() || apiKey.equals("your_gemini_api_key_here")) {
                Log.e(TAG, "Gemini API key not configured. Please add GEMINI_API_KEY to gradle.properties");
                return;
            }
            
            GenerativeModel gm = new GenerativeModel(MODEL_NAME, apiKey);
            model = GenerativeModelFutures.from(gm);
            
            Log.d(TAG, "Gemini model initialized successfully");
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize Gemini model", e);
        }
    }
    
    private void setupAlzheimerSpecificPrompt() {
        String systemPrompt = "You are a compassionate AI assistant designed to help elderly people with Alzheimer's disease. " +
                "Your goals are to:\n" +
                "1. Provide emotional support and companionship\n" +
                "2. Engage in meaningful conversations that stimulate memory\n" +
                "3. Ask gentle questions that can help assess cognitive function (like MMSE elements)\n" +
                "4. Encourage reminiscence about past experiences\n" +
                "5. Be patient, kind, and understanding\n" +
                "6. Use simple, clear language\n" +
                "7. Repeat information when needed\n" +
                "8. Avoid complex topics that might cause confusion\n\n" +
                "Guidelines:\n" +
                "- Keep responses short and easy to understand\n" +
                "- Show genuine interest in their stories and memories\n" +
                "- Gently incorporate memory exercises into natural conversation\n" +
                "- Be encouraging and positive\n" +
                "- If they seem confused, redirect gently to simpler topics\n" +
                "- Remember that repetition is normal and be patient\n\n" +
                "Start each conversation warmly and adapt to their communication style.";
        
        conversationHistory.append("System: ").append(systemPrompt).append("\n\n");
    }
    
    public void sendMessage(String userMessage, ChatCallback callback) {
        if (model == null) {
            callback.onError("AI service not available. Please check your connection.");
            return;
        }
        
        // Add user message to conversation history
        conversationHistory.append("User: ").append(userMessage).append("\n");
        
        // Create content with conversation context
        Content content = new Content.Builder()
                .addText(conversationHistory.toString())
                .build();
        
        // Generate response
        ListenableFuture<GenerateContentResponse> response = model.generateContent(content);
        
        Futures.addCallback(response, new FutureCallback<GenerateContentResponse>() {
            @Override
            public void onSuccess(GenerateContentResponse result) {
                try {
                    String responseText = result.getText();
                    if (responseText != null && !responseText.trim().isEmpty()) {
                        // Add AI response to conversation history
                        conversationHistory.append("Assistant: ").append(responseText).append("\n");
                        
                        // Trim conversation history if it gets too long
                        trimConversationHistory();
                        
                        callback.onResponse(responseText.trim());
                        
                        // Log for MMSE analysis (in real implementation, this would be more sophisticated)
                        analyzeForMmseElements(userMessage, responseText);
                        
                    } else {
                        callback.onError("I didn't understand that. Could you please try again?");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error processing Gemini response", e);
                    callback.onError("I'm having trouble thinking right now. Please try again.");
                }
            }
            
            @Override
            public void onFailure(Throwable t) {
                Log.e(TAG, "Gemini API call failed", t);
                callback.onError("I'm having trouble connecting right now. Please try again in a moment.");
            }
        }, executor);
    }
    
    private void trimConversationHistory() {
        // Keep conversation history manageable (last 10 exchanges approximately)
        String history = conversationHistory.toString();
        String[] lines = history.split("\n");
        
        if (lines.length > 25) { // System prompt + ~12 exchanges
            StringBuilder trimmed = new StringBuilder();
            // Keep system prompt
            trimmed.append(lines[0]).append("\n").append(lines[1]).append("\n\n");
            
            // Keep last 20 lines
            for (int i = lines.length - 20; i < lines.length; i++) {
                if (i >= 0) {
                    trimmed.append(lines[i]).append("\n");
                }
            }
            
            conversationHistory = trimmed;
        }
    }
    
    private void analyzeForMmseElements(String userInput, String aiResponse) {
        // This is a simplified analysis - in a real implementation, this would be more sophisticated
        // and would integrate with the MMSE scoring system
        
        Log.d(TAG, "Analyzing conversation for MMSE elements:");
        Log.d(TAG, "User: " + userInput);
        Log.d(TAG, "AI: " + aiResponse);
        
        // TODO: Implement actual MMSE element detection
        // - Memory recall questions
        // - Orientation questions (time, place)
        // - Language processing
        // - Attention and calculation
        // - Following instructions
        
        // For now, just log the conversation for later analysis
    }
    
    public void clearConversationHistory() {
        conversationHistory = new StringBuilder();
        setupAlzheimerSpecificPrompt();
    }
    
    public String getConversationSummary() {
        // This would generate a summary of the conversation for caregiver review
        // and extract key points for MMSE assessment
        return conversationHistory.toString();
    }
    
    /**
     * Callback interface for chat responses
     */
    public interface ChatCallback {
        void onResponse(String response);
        void onError(String error);
    }
}