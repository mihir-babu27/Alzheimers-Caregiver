package com.mihir.alzheimerscaregiver;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import com.mihir.alzheimerscaregiver.BuildConfig;
import com.mihir.alzheimerscaregiver.utils.LanguagePreferenceManager;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * GeminiChatService - Handles communication with Google's Gemini AI using REST API
 * Specialized for Alzheimer's patient conversations with MMSE assessment capabilities
 * Uses same model fallback system as story generation for reliability
 */
public class GeminiChatService {
    
    private static final String TAG = "GeminiChatService";
    
    // API Configuration - Same as GeminiStoryGenerator for consistency
    private static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/";
    private static final String[] MODEL_NAMES = {
        "gemini-2.5-flash",          // Recommended: Fast, efficient, multimodal (Active)
        "gemini-2.5-flash-lite",     // Extremely low cost/latency fallback
        "gemini-2.5-pro"             // High intelligence for complex reasoning
    };
    private static final String GENERATE_ENDPOINT = ":generateContent?key=";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    
    private final OkHttpClient httpClient;
    private final ExecutorService executor;
    private final Handler mainHandler;
    private StringBuilder conversationHistory;
    private String preferredLanguage;
    
    public GeminiChatService(String language) {
        httpClient = new OkHttpClient.Builder().build();
        executor = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());
        conversationHistory = new StringBuilder();
        preferredLanguage = language != null ? language : LanguagePreferenceManager.DEFAULT_LANGUAGE;
        setupAlzheimerSpecificPrompt();
        
        Log.d(TAG, "GeminiChatService initialized with REST API for language: " + preferredLanguage);
    }
    
    private void setupAlzheimerSpecificPrompt() {
        // Get language-specific instructions
        String culturalContext = LanguagePreferenceManager.getCulturalContext(preferredLanguage);
        String languageSpecificPhrases = LanguagePreferenceManager.getLanguageSpecificPhrases(preferredLanguage);
        
        // Build language-aware system prompt
        String languageInstruction = "";
        if (!preferredLanguage.equals(LanguagePreferenceManager.LANGUAGE_ENGLISH)) {
            languageInstruction = "IMPORTANT: Respond ONLY in " + preferredLanguage + ". " +
                    "Use native " + preferredLanguage + " words and expressions naturally. " +
                    "DO NOT provide translations, transliterations, or English explanations. " +
                    "Keep the conversation purely in " + preferredLanguage + ". " +
                    languageSpecificPhrases + " " + culturalContext + "\n\n";
        }
        
        String systemPrompt = languageInstruction +
                "You are a compassionate AI assistant designed to help elderly people with Alzheimer's disease. " +
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
        // Add user message to conversation history
        conversationHistory.append("User: ").append(userMessage).append("\n");
        
        executor.execute(() -> {
            try {
                // Get API key
                String apiKey = BuildConfig.GOOGLE_API_KEY;
                if (apiKey == null || apiKey.isEmpty()) {
                    mainHandler.post(() -> callback.onError("API configuration error. Please check settings."));
                    return;
                }
                
                // Create prompt with conversation context  
                String prompt = conversationHistory.toString();
                
                // Try first model
                tryModelOrFallback(userMessage, callback, 0, prompt);
                
            } catch (Exception e) {
                Log.e(TAG, "Error preparing chat request", e);
                mainHandler.post(() -> callback.onError("Unable to process your message. Please try again."));
            }
        });
    }
    
    /**
     * Tries the next available model or shows appropriate error if all models fail
     */
    private void tryModelOrFallback(String userMessage, ChatCallback callback, int modelIndex, String prompt) {
        if (modelIndex >= MODEL_NAMES.length) {
            Log.e(TAG, "All chat models failed");
            mainHandler.post(() -> callback.onError("Chat service is temporarily unavailable. Please try again in a few minutes."));
            return;
        }

        String currentModel = MODEL_NAMES[modelIndex];
        Log.d(TAG, "Trying chat model: " + currentModel + " (attempt " + (modelIndex + 1) + "/" + MODEL_NAMES.length + ")");

        try {
            JSONObject requestBody = createRequestBody(prompt);
            String url = BASE_URL + currentModel + GENERATE_ENDPOINT + BuildConfig.GOOGLE_API_KEY;
            
            Request request = new Request.Builder()
                    .url(url)
                    .post(RequestBody.create(requestBody.toString(), JSON))
                    .addHeader("Content-Type", "application/json")
                    .build();

            httpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "Failed to generate response with " + currentModel, e);
                    mainHandler.post(() -> tryModelOrFallback(userMessage, callback, modelIndex + 1, prompt));
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    try (Response r = response) {
                        if (!r.isSuccessful()) {
                            String responseBody = r.body() != null ? r.body().string() : "No response body";
                            Log.e(TAG, "API call unsuccessful for " + currentModel + ": " + r.code() + " - " + r.message());
                            
                            // Handle rate limiting with exponential backoff
                            if (r.code() == 429) {
                                int delaySeconds = (int) Math.pow(2, modelIndex) * 3; // 3s, 6s, 12s, 24s
                                Log.w(TAG, "Rate limit hit for chat model " + currentModel + ", waiting " + delaySeconds + " seconds before retry");
                                
                                // Use executor to handle the delay without blocking main thread
                                executor.execute(() -> {
                                    try {
                                        Thread.sleep(delaySeconds * 1000);
                                        mainHandler.post(() -> tryModelOrFallback(userMessage, callback, modelIndex + 1, prompt));
                                    } catch (InterruptedException ie) {
                                        Thread.currentThread().interrupt();
                                        Log.w(TAG, "Rate limit delay interrupted");
                                        mainHandler.post(() -> tryModelOrFallback(userMessage, callback, modelIndex + 1, prompt));
                                    }
                                });
                                return;
                            }
                            
                            // Try next model for other errors
                            mainHandler.post(() -> tryModelOrFallback(userMessage, callback, modelIndex + 1, prompt));
                            return;
                        }

                        String responseBody = r.body().string();
                        String responseText = parseResponseFromJson(responseBody);
                        
                        if (responseText == null || responseText.trim().isEmpty()) {
                            Log.w(TAG, "Generated response is empty for " + currentModel);
                            mainHandler.post(() -> tryModelOrFallback(userMessage, callback, modelIndex + 1, prompt));
                            return;
                        }
                        
                        Log.d(TAG, "Successfully generated chat response with model: " + currentModel);
                        
                        // Add AI response to conversation history
                        conversationHistory.append("Assistant: ").append(responseText).append("\n");
                        
                        // Trim conversation history if it gets too long
                        trimConversationHistory();
                        
                        mainHandler.post(() -> {
                            callback.onResponse(responseText.trim());
                            
                            // Analyze for MMSE elements and memories
                            analyzeForMmseElements(userMessage, responseText);
                        });
                        
                    } catch (Exception e) {
                        Log.e(TAG, "Error processing response for " + currentModel, e);
                        mainHandler.post(() -> tryModelOrFallback(userMessage, callback, modelIndex + 1, prompt));
                    }
                }
            });
            
        } catch (Exception e) {
            Log.e(TAG, "Error creating request for " + currentModel, e);
            mainHandler.post(() -> tryModelOrFallback(userMessage, callback, modelIndex + 1, prompt));
        }
    }
    
    /**
     * Creates the JSON request body for the Gemini API
     */
    private JSONObject createRequestBody(String prompt) throws JSONException {
        JSONObject requestBody = new JSONObject();
        JSONArray contents = new JSONArray();
        JSONObject content = new JSONObject();
        JSONArray parts = new JSONArray();
        JSONObject part = new JSONObject();
        
        part.put("text", prompt);
        parts.put(part);
        content.put("parts", parts);
        contents.put(content);
        requestBody.put("contents", contents);
        
        return requestBody;
    }
    
    /**
     * Parses the response from the Gemini API JSON
     */
    private String parseResponseFromJson(String responseBody) {
        try {
            JSONObject response = new JSONObject(responseBody);
            JSONArray candidates = response.getJSONArray("candidates");
            
            if (candidates.length() > 0) {
                JSONObject candidate = candidates.getJSONObject(0);
                JSONObject content = candidate.getJSONObject("content");
                JSONArray parts = content.getJSONArray("parts");
                
                if (parts.length() > 0) {
                    JSONObject part = parts.getJSONObject(0);
                    return part.getString("text");
                }
            }
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing response", e);
        }
        return null;
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
        // Enhanced analysis for memory extraction and cognitive assessment
        Log.d(TAG, "Analyzing conversation for MMSE elements and memories:");
        Log.d(TAG, "User: " + userInput);
        Log.d(TAG, "AI: " + aiResponse);
        
        // Basic memory extraction patterns
        extractMemoriesFromText(userInput);
        
        // Basic MMSE element detection
        detectCognitiveMarkers(userInput);
        
        // Store for later processing
        // TODO: Integrate with ConversationRepository for Firebase storage
    }
    
    /**
     * Extract potential memories and personal experiences from user text
     */
    private void extractMemoriesFromText(String text) {
        if (text == null || text.trim().isEmpty()) return;
        
        String lowerText = text.toLowerCase();
        
        // Memory indicators - phrases that often precede personal memories
        String[] memoryIndicators = {
            "i remember", "when i was", "back in", "years ago", "i used to", 
            "my husband", "my wife", "my children", "my mother", "my father",
            "we lived", "i worked", "i loved", "i enjoyed", "my favorite",
            "in my youth", "growing up", "during the war", "as a child"
        };
        
        // Emotional indicators
        String[] emotionalIndicators = {
            "happy", "sad", "proud", "excited", "worried", "loved", 
            "missed", "enjoyed", "beautiful", "wonderful", "terrible"
        };
        
        // Check for memory patterns
        for (String indicator : memoryIndicators) {
            if (lowerText.contains(indicator)) {
                Log.d(TAG, "Found memory indicator: " + indicator + " in text: " + text.substring(0, Math.min(50, text.length())));
                // TODO: Extract and store the full memory context
            }
        }
        
        // Check for emotional context
        for (String emotion : emotionalIndicators) {
            if (lowerText.contains(emotion)) {
                Log.d(TAG, "Found emotional context: " + emotion);
                // TODO: Store emotional markers for therapeutic assessment
            }
        }
        
        // Extract people mentioned
        extractPeopleMentioned(text);
        
        // Extract time references
        extractTimeReferences(text);
        
        // Extract location references
        extractLocationReferences(text);
    }
    
    private void extractPeopleMentioned(String text) {
        String[] relationshipWords = {
            "husband", "wife", "mother", "father", "son", "daughter", 
            "brother", "sister", "friend", "neighbor", "colleague", 
            "boss", "teacher", "doctor", "nurse", "grandson", "granddaughter"
        };
        
        String lowerText = text.toLowerCase();
        for (String relationship : relationshipWords) {
            if (lowerText.contains(relationship)) {
                Log.d(TAG, "Found relationship mention: " + relationship);
            }
        }
    }
    
    private void extractTimeReferences(String text) {
        String[] timeWords = {
            "1950", "1960", "1970", "1980", "1990", "2000",
            "fifty years ago", "forty years ago", "thirty years ago",
            "when i was young", "in my twenties", "in my thirties",
            "during the war", "after the war", "before the war"
        };
        
        String lowerText = text.toLowerCase();
        for (String timeRef : timeWords) {
            if (lowerText.contains(timeRef)) {
                Log.d(TAG, "Found time reference: " + timeRef);
            }
        }
    }
    
    private void extractLocationReferences(String text) {
        String[] locationWords = {
            "lived in", "grew up in", "moved to", "traveled to", "visited",
            "hometown", "neighborhood", "city", "country", "house", "home",
            "school", "church", "hospital", "work", "office", "factory"
        };
        
        String lowerText = text.toLowerCase();
        for (String location : locationWords) {
            if (lowerText.contains(location)) {
                Log.d(TAG, "Found location reference: " + location);
            }
        }
    }
    
    /**
     * Detect cognitive markers that might indicate memory or cognitive issues
     */
    private void detectCognitiveMarkers(String text) {
        if (text == null || text.trim().isEmpty()) return;
        
        String lowerText = text.toLowerCase();
        
        // Confusion markers
        String[] confusionMarkers = {
            "i don't remember", "i can't recall", "i forget", "i'm confused",
            "what was i saying", "where am i", "what day is it", "i'm lost"
        };
        
        // Clarity markers (positive indicators)
        String[] clarityMarkers = {
            "i clearly remember", "i'll never forget", "i remember exactly",
            "that was on", "it happened in", "i was with"
        };
        
        boolean hasConfusionMarkers = false;
        boolean hasClarityMarkers = false;
        
        for (String marker : confusionMarkers) {
            if (lowerText.contains(marker)) {
                Log.d(TAG, "Found confusion marker: " + marker);
                hasConfusionMarkers = true;
            }
        }
        
        for (String marker : clarityMarkers) {
            if (lowerText.contains(marker)) {
                Log.d(TAG, "Found clarity marker: " + marker);
                hasClarityMarkers = true;
            }
        }
        
        // Log cognitive assessment notes
        if (hasConfusionMarkers) {
            Log.d(TAG, "COGNITIVE ASSESSMENT: Potential memory difficulties detected");
        }
        if (hasClarityMarkers) {
            Log.d(TAG, "COGNITIVE ASSESSMENT: Clear memory recall detected");
        }
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
     * Extract memories using AI analysis instead of keyword matching
     * This method is much more effective for multi-language content
     */
    public void extractMemoriesWithAI(String conversationText, MemoryExtractionCallback callback) {
        Log.d(TAG, "🔍 Starting AI memory extraction...");
        
        if (conversationText == null || conversationText.trim().isEmpty()) {
            Log.w(TAG, "Empty conversation text, returning empty memories");
            callback.onMemoriesExtracted(new java.util.ArrayList<>());
            return;
        }
        
        Log.d(TAG, "📝 Conversation to analyze: " + conversationText);
        Log.d(TAG, "🌐 Language: " + preferredLanguage);
        
        // Create specialized memory extraction prompt
        String memoryExtractionPrompt = createMemoryExtractionPrompt(conversationText);
        
        Log.d(TAG, "💭 Memory extraction prompt created, executing...");
        
        executor.execute(() -> {
            // Try with the same model fallback system
            tryMemoryExtractionOrFallback(memoryExtractionPrompt, callback, 0);
        });
    }
    
    private String createMemoryExtractionPrompt(String conversationText) {
        // Truncate very long conversations to avoid API limits
        String truncatedConversation = conversationText;
        if (conversationText.length() > 2000) {
            truncatedConversation = conversationText.substring(0, 2000) + "...";
        }
        
        // Enhanced multi-language instructions with cultural context
        String enhancedLanguageInstructions = getEnhancedLanguageInstructions(preferredLanguage);
        String cultureSpecificExamples = getCultureSpecificMemoryExamples(preferredLanguage);
        
        return "You are an expert memory analyst specialized in Alzheimer's patient conversations across multiple languages and cultures.\n\n" +
                enhancedLanguageInstructions + "\n\n" +
                "CRITICAL MEMORY EXTRACTION GUIDELINES:\n" +
                "• Extract ONLY the most significant memories from the conversation\n" +
                "• Focus on: personal activities, relationships, places, hobbies, experiences\n" +
                "• Preserve original language terms for cultural elements\n" +
                "• Prioritize memories suitable for cognitive assessment questions\n" +
                "• LIMIT to maximum 5-6 most important memories to avoid overwhelming\n\n" +
                "CONVERSATION TO ANALYZE:\n" + truncatedConversation + "\n\n" +
                cultureSpecificExamples + "\n\n" +
                "Extract the TOP 5 most important memories as a simple JSON array:\n" +
                "[\"activity: specific activity mentioned\", \"memory: significant personal experience\", \"location: important place\", \"relationship: family/friends mentioned\", \"hobby: interests shared\"]\n\n" +
                "FOCUS on memories that would make good fill-in-the-blank questions.\n" +
                "Return ONLY the JSON array, nothing else. If no significant memories found, return []";
    }
    
    /**
     * Get enhanced language instructions based on preferred language
     */
    private String getEnhancedLanguageInstructions(String language) {
        StringBuilder instructions = new StringBuilder();
        instructions.append("LANGUAGE & CULTURAL ANALYSIS:\n");
        
        switch (language) {
            case "Hindi":
                instructions.append("• The conversation may contain Hindi (हिंदी) text with terms like: माँ (mother), पापा (father), गाँव (village), याद है (I remember)\n");
                instructions.append("• Look for cultural references: त्योहार (festivals), रिश्तेदार (relatives), पुराने दिन (old days)\n");
                instructions.append("• Extract Hindi place names, relationship terms, and activity descriptions naturally\n");
                break;
                
            case "Tamil":
                instructions.append("• The conversation may contain Tamil (தமிழ்) text with terms like: அம்மா (mother), அப்பா (father), ஊர் (town), நினைவிருக்கிறது (I remember)\n");
                instructions.append("• Look for cultural references: பண்டிகை (festivals), உறவினர் (relatives), பழைய காலம் (old times)\n");
                instructions.append("• Extract Tamil place names, relationship terms, and cultural activities\n");
                break;
                
            case "Telugu":
                instructions.append("• The conversation may contain Telugu (తెలుగు) text with terms like: అమ్మ (mother), నాన్న (father), ఊరు (town), గుర్తుంది (I remember)\n");
                instructions.append("• Look for cultural references: పండుగలు (festivals), బంధువులు (relatives), పాత రోజులు (old days)\n");
                instructions.append("• Extract Telugu place names, relationship terms, and traditional activities\n");
                break;
                
            case "Kannada":
                instructions.append("• The conversation may contain Kannada (ಕನ್ನಡ) text with terms like: ಅಮ್ಮ (mother), ಅಪ್ಪ (father), ಊರು (town), ನೆನಪಿದೆ (I remember)\n");
                instructions.append("• Look for cultural references: ಹಬ್ಬಗಳು (festivals), ಬಂಧುಗಳು (relatives), ಹಳೆಯ ದಿನಗಳು (old days)\n");
                instructions.append("• Extract Kannada place names, relationship terms, and cultural practices\n");
                break;
                
            case "Malayalam":
                instructions.append("• The conversation may contain Malayalam (മലയാളം) text with terms like: അമ്മ (mother), അച്ഛൻ (father), നാട് (place), ഓർമയുണ്ട് (I remember)\n");
                instructions.append("• Look for cultural references: ഉത്സവങ്ങൾ (festivals), ബന്ധുക്കൾ (relatives), പഴയ കാലം (old times)\n");
                instructions.append("• Extract Malayalam place names, relationship terms, and traditional activities\n");
                break;
                
            default: // English
                instructions.append("• The conversation is primarily in English but may contain mixed language terms\n");
                instructions.append("• Look for Indian cultural references: family terms, place names, festivals, traditions\n");
                instructions.append("• Extract memories in their original language when mentioned\n");
                break;
        }
        
        instructions.append("• IMPORTANT: Preserve original language terms - don't translate cultural elements\n");
        instructions.append("• Multi-language conversations are common - extract memories from ALL languages used\n");
        
        return instructions.toString();
    }
    
    /**
     * Get culture-specific memory examples to guide AI extraction
     */
    private String getCultureSpecificMemoryExamples(String language) {
        StringBuilder examples = new StringBuilder();
        examples.append("EXTRACTION EXAMPLES FOR ").append(language.toUpperCase()).append(":\n");
        
        switch (language) {
            case "Hindi":
                examples.append("Input: \"मैं अपने गाँव में माँ के साथ रहता था\"\n");
                examples.append("Extract: [\"memory: lived with माँ in गाँव\", \"relationship: माँ (mother)\", \"location: गाँव\"]\n\n");
                examples.append("Input: \"हमारे यहाँ दिवाली बहुत धूमधाम से मनाते थे\"\n");
                examples.append("Extract: [\"festival: दिवाली celebration\", \"activity: धूमधाम से मनाना\", \"cultural: traditional celebration\"]\n");
                break;
                
            case "Tamil":
                examples.append("Input: \"என் அம்மா என்னை சென்னையில் வளர்த்தார்\"\n");
                examples.append("Extract: [\"memory: raised by அம்மா in Chennai\", \"relationship: அம்மா (mother)\", \"location: சென்னை\"]\n\n");
                examples.append("Input: \"பொங்கல் நாள்ல எங்க வீட்ல பெரிய கொண்டாட்டம்\"\n");
                examples.append("Extract: [\"festival: பொங்கல் celebration\", \"location: எங்க வீடு\", \"activity: பெரிய கொண்டாட்டம்\"]\n");
                break;
                
            case "Telugu":
                examples.append("Input: \"మా అమ్మ నన్ను హైదరాబాద్‌లో పెంచింది\"\n");
                examples.append("Extract: [\"memory: raised by అమ్మ in Hyderabad\", \"relationship: అమ్మ (mother)\", \"location: హైదరాబాద్\"]\n\n");
                examples.append("Input: \"మా ఊల్లో ఉగాది చాలా గొప్పగా జరుపుకుంటాం\"\n");
                examples.append("Extract: [\"festival: ఉగాది celebration\", \"location: మా ఊరు\", \"activity: గొప్పగా జరుపుకోవడం\"]\n");
                break;
                
            case "Kannada":
                examples.append("Input: \"ನಮ್ಮ ಅಮ್ಮ ನನ್ನನ್ನು ಬೆಂಗಳೂರಿನಲ್ಲಿ ಬೆಳೆಸಿದ್ದು\"\n");
                examples.append("Extract: [\"memory: raised by ಅಮ್ಮ in Bengaluru\", \"relationship: ಅಮ್ಮ (mother)\", \"location: ಬೆಂಗಳೂರು\"]\n\n");
                examples.append("Input: \"ದಸರಾ ಹಬ್ಬದಲ್ಲಿ ನಮ್ಮ ಮನೆಯಲ್ಲಿ ದೊಡ್ಡ ಸಂಭ್ರಮ\"\n");
                examples.append("Extract: [\"festival: ದಸರಾ celebration\", \"location: ನಮ್ಮ ಮನೆ\", \"activity: ದೊಡ್ಡ ಸಂಭ್ರಮ\"]\n");
                break;
                
            case "Malayalam":
                examples.append("Input: \"എന്റെ അമ്മ എന്നെ കൊച്ചിയിൽ വളർത്തി\"\n");
                examples.append("Extract: [\"memory: raised by അമ്മ in Kochi\", \"relationship: അമ്മ (mother)\", \"location: കൊച്ചി\"]\n\n");
                examples.append("Input: \"ഓണാഘോഷം ഞങ്ങളുടെ വീട്ടിൽ വലിയ ആഘോഷം\"\n");
                examples.append("Extract: [\"festival: ഓണം celebration\", \"location: ഞങ്ങളുടെ വീട്\", \"activity: വലിയ ആഘോഷം\"]\n");
                break;
                
            default: // English
                examples.append("Input: \"My grandmother used to make amazing biryani during Eid\"\n");
                examples.append("Extract: [\"memory: grandmother's biryani during Eid\", \"relationship: grandmother\", \"activity: making biryani\", \"festival: Eid\"]\n\n");
                examples.append("Input: \"We lived in Mumbai near the beach\"\n");
                examples.append("Extract: [\"memory: lived in Mumbai\", \"location: Mumbai near beach\", \"activity: living near beach\"]\n");
                break;
        }
        
        return examples.toString();
    }
    
    private void tryMemoryExtractionOrFallback(String prompt, MemoryExtractionCallback callback, int modelIndex) {
        if (modelIndex >= MODEL_NAMES.length) {
            Log.e(TAG, "❌ All memory extraction models failed");
            mainHandler.post(() -> callback.onError("Memory extraction failed - all models unavailable"));
            return;
        }
        
        String currentModel = MODEL_NAMES[modelIndex];
        Log.d(TAG, "🤖 Trying memory extraction with model: " + currentModel + " (attempt " + (modelIndex + 1) + "/" + MODEL_NAMES.length + ")");
        
        String apiKey = BuildConfig.GOOGLE_API_KEY;
        String url = BASE_URL + currentModel + GENERATE_ENDPOINT + apiKey;
        
        try {
            // Create request body for memory extraction
            JSONObject requestBody = new JSONObject();
            JSONArray contents = new JSONArray();
            JSONObject content = new JSONObject();
            JSONArray parts = new JSONArray();
            JSONObject part = new JSONObject();
            part.put("text", prompt);
            parts.put(part);
            content.put("parts", parts);
            contents.put(content);
            requestBody.put("contents", contents);
            
            RequestBody body = RequestBody.create(requestBody.toString(), JSON);
            Request request = new Request.Builder()
                    .url(url)
                    .post(body)
                    .addHeader("Content-Type", "application/json")
                    .build();
            
            httpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.w(TAG, "Memory extraction failed with model " + currentModel + ": " + e.getMessage());
                    // Try next model
                    tryMemoryExtractionOrFallback(prompt, callback, modelIndex + 1);
                }
                
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    Log.d(TAG, "📡 Memory extraction API response received, code: " + response.code());
                    
                    if (response.isSuccessful()) {
                        try {
                            String responseBody = response.body().string();
                            Log.d(TAG, "📋 Raw memory extraction response: " + responseBody);
                            
                            JSONObject jsonResponse = new JSONObject(responseBody);
                            JSONArray candidates = jsonResponse.getJSONArray("candidates");
                            if (candidates.length() > 0) {
                                JSONObject firstCandidate = candidates.getJSONObject(0);
                                JSONObject content = firstCandidate.getJSONObject("content");
                                JSONArray parts = content.getJSONArray("parts");
                                if (parts.length() > 0) {
                                    String aiResponse = parts.getJSONObject(0).getString("text");
                                    Log.d(TAG, "🧠 AI memory extraction result: " + aiResponse);
                                    
                                    // Parse memories from AI response into list format
                                    java.util.List<String> memories = parseMemoriesFromAIResponse(aiResponse);
                                    Log.d(TAG, "✅ Parsed memories: " + memories.toString());
                                    
                                    mainHandler.post(() -> callback.onMemoriesExtracted(memories));
                                    return;
                                }
                            }
                            
                            Log.e(TAG, "❌ Invalid memory extraction response structure");
                            mainHandler.post(() -> callback.onError("Invalid response structure"));
                        } catch (Exception e) {
                            Log.e(TAG, "❌ Error parsing memory extraction response", e);
                            mainHandler.post(() -> callback.onError("Response parsing error: " + e.getMessage()));
                        }
                    } else {
                        String errorBody = "";
                        try {
                            errorBody = response.body().string();
                        } catch (Exception e) {
                            Log.e(TAG, "Error reading error response", e);
                        }
                        Log.e(TAG, "❌ Memory extraction API error: " + response.code() + " - " + response.message());
                        Log.e(TAG, "❌ Error body: " + errorBody);
                        
                        // Try next model if available
                        tryMemoryExtractionOrFallback(prompt, callback, modelIndex + 1);
                    }
                }
            });
            
        } catch (Exception e) {
            Log.e(TAG, "Error creating memory extraction request", e);
            // Try next model
            tryMemoryExtractionOrFallback(prompt, callback, modelIndex + 1);
        }
    }
    
    private java.util.List<String> parseMemoriesFromAIResponse(String aiResponse) {
        java.util.List<String> memories = new java.util.ArrayList<>();
        
        try {
            // The AI should return JSON, but let's be defensive
            String jsonText = aiResponse.trim();
            
            // Remove any markdown formatting if present
            if (jsonText.startsWith("```json")) {
                jsonText = jsonText.substring(7);
            }
            if (jsonText.endsWith("```")) {
                jsonText = jsonText.substring(0, jsonText.length() - 3);
            }
            jsonText = jsonText.trim();
            
            // Parse the JSON array (simple string format)
            JSONArray memoriesArray = new JSONArray(jsonText);
            
            for (int i = 0; i < memoriesArray.length(); i++) {
                String memoryStr = memoriesArray.getString(i).trim();
                if (!memoryStr.isEmpty()) {
                    memories.add(memoryStr);
                }
            }
            
            Log.d(TAG, "Successfully parsed " + memories.size() + " memories from AI response");
            
        } catch (Exception e) {
            Log.e(TAG, "Error parsing AI memory response: " + aiResponse, e);
            // If JSON parsing fails, try to extract some basic info from the raw text
            if (aiResponse.contains("sister") || aiResponse.contains("brother") || 
                aiResponse.contains("mother") || aiResponse.contains("father")) {
                memories.add("Memory: " + aiResponse.substring(0, Math.min(100, aiResponse.length())));
            }
        }
        
        return memories;
    }
    
    /**
     * Callback interface for memory extraction
     */
    public interface MemoryExtractionCallback {
        void onMemoriesExtracted(java.util.List<String> memories);
        void onError(String error);
    }
    
    /**
     * Callback interface for chat responses
     */
    public interface ChatCallback {
        void onResponse(String response);
        void onError(String error);
    }
}