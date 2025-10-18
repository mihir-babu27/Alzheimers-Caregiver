package com.mihir.alzheimerscaregiver.services;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MemoryExtractionService - Advanced memory and experience extraction from conversations
 * This service analyzes patient conversations to identify key memories, experiences,
 * and personal details that can be used for personalized story generation and MMSE assessment
 */
public class MemoryExtractionService {
    
    private static final String TAG = "MemoryExtractionService";
    private static final String PREFS_NAME = "ExtractedMemories";
    
    private final Context context;
    
    // Memory categories for organizing extracted content
    public enum MemoryCategory {
        FAMILY("family"),
        WORK("work"), 
        CHILDHOOD("childhood"),
        MARRIAGE("marriage"),
        ACHIEVEMENTS("achievements"),
        TRAVEL("travel"),
        HOBBIES("hobbies"),
        FRIENDS("friends"),
        HEALTH("health"),
        GENERAL("general");
        
        private final String value;
        MemoryCategory(String value) { this.value = value; }
        public String getValue() { return value; }
    }
    
    // Extracted memory data structure
    public static class ExtractedMemory {
        public String text;
        public MemoryCategory category;
        public String emotionalTone; // "positive", "negative", "neutral"
        public List<String> peopleInvolved;
        public String timeReference;
        public String location;
        public double confidenceScore; // 0.0 to 1.0
        public boolean isTherapeuticallyValuable;
        public long extractedTimestamp;
        
        public ExtractedMemory(String text) {
            this.text = text;
            this.peopleInvolved = new ArrayList<>();
            this.extractedTimestamp = System.currentTimeMillis();
            this.confidenceScore = 0.5;
            this.category = MemoryCategory.GENERAL;
            this.emotionalTone = "neutral";
        }
    }
    
    public MemoryExtractionService(Context context) {
        this.context = context;
    }
    
    /**
     * Main method to extract memories from conversation text
     */
    public List<ExtractedMemory> extractMemoriesFromConversation(String conversationText) {
        List<ExtractedMemory> memories = new ArrayList<>();
        
        if (conversationText == null || conversationText.trim().isEmpty()) {
            return memories;
        }
        
        Log.d(TAG, "Extracting memories from conversation text of length: " + conversationText.length());
        
        // Split into sentences for individual analysis
        String[] sentences = splitIntoSentences(conversationText);
        
        for (String sentence : sentences) {
            ExtractedMemory memory = analyzeSentenceForMemory(sentence);
            if (memory != null && memory.confidenceScore >= 0.3) { // Only keep high-confidence memories
                memories.add(memory);
                Log.d(TAG, "Extracted memory: " + memory.text.substring(0, Math.min(50, memory.text.length())) + 
                      " (Category: " + memory.category + ", Confidence: " + memory.confidenceScore + ")");
            }
        }
        
        // Post-process memories to enhance categorization and confidence
        enhanceExtractedMemories(memories);
        
        return memories;
    }
    
    private String[] splitIntoSentences(String text) {
        // Split on sentence boundaries while preserving some context
        return text.split("[.!?]+\\s*");
    }
    
    private ExtractedMemory analyzeSentenceForMemory(String sentence) {
        if (sentence.trim().length() < 10) return null; // Too short to be meaningful
        
        String lowerSentence = sentence.toLowerCase().trim();
        
        // Check if sentence contains memory indicators
        if (!containsMemoryIndicators(lowerSentence)) {
            return null;
        }
        
        ExtractedMemory memory = new ExtractedMemory(sentence.trim());
        
        // Analyze different aspects of the memory
        analyzeCategory(memory, lowerSentence);
        analyzeEmotionalTone(memory, lowerSentence);
        extractPeople(memory, lowerSentence);
        extractTimeReferences(memory, lowerSentence);
        extractLocations(memory, lowerSentence);
        calculateConfidenceScore(memory, lowerSentence);
        assessTherapeuticValue(memory, lowerSentence);
        
        return memory;
    }
    
    private boolean containsMemoryIndicators(String text) {
        String[] memoryIndicators = {
            "i remember", "when i was", "back when", "years ago", "i used to", 
            "we used to", "my late", "my husband", "my wife", "my children", 
            "my mother", "my father", "growing up", "as a child", "in my youth",
            "during", "before", "after", "i worked", "i lived", "we lived",
            "i loved", "i enjoyed", "my favorite", "i'll never forget",
            "that reminds me", "i once", "there was a time"
        };
        
        for (String indicator : memoryIndicators) {
            if (text.contains(indicator)) {
                return true;
            }
        }
        
        return false;
    }
    
    private void analyzeCategory(ExtractedMemory memory, String text) {
        // Family-related
        if (containsAny(text, new String[]{"husband", "wife", "children", "mother", "father", 
                                         "brother", "sister", "family", "grandson", "granddaughter"})) {
            memory.category = MemoryCategory.FAMILY;
        }
        // Work-related
        else if (containsAny(text, new String[]{"worked", "job", "office", "boss", "colleague", 
                                              "career", "retired", "business"})) {
            memory.category = MemoryCategory.WORK;
        }
        // Childhood
        else if (containsAny(text, new String[]{"child", "young", "school", "playground", 
                                              "growing up", "as a kid"})) {
            memory.category = MemoryCategory.CHILDHOOD;
        }
        // Marriage
        else if (containsAny(text, new String[]{"wedding", "married", "honeymoon", "anniversary"})) {
            memory.category = MemoryCategory.MARRIAGE;
        }
        // Travel
        else if (containsAny(text, new String[]{"traveled", "vacation", "trip", "visited", "journey"})) {
            memory.category = MemoryCategory.TRAVEL;
        }
        // Hobbies
        else if (containsAny(text, new String[]{"hobby", "gardening", "cooking", "reading", 
                                              "music", "dancing", "sport"})) {
            memory.category = MemoryCategory.HOBBIES;
        }
    }
    
    private void analyzeEmotionalTone(ExtractedMemory memory, String text) {
        String[] positiveWords = {"happy", "joy", "love", "wonderful", "beautiful", "proud", 
                                "excited", "delighted", "pleased", "grateful", "blessed"};
        String[] negativeWords = {"sad", "difficult", "hard", "painful", "worried", "scared", 
                                "upset", "angry", "disappointed", "lonely"};
        
        int positiveScore = 0;
        int negativeScore = 0;
        
        for (String word : positiveWords) {
            if (text.contains(word)) positiveScore++;
        }
        
        for (String word : negativeWords) {
            if (text.contains(word)) negativeScore++;
        }
        
        if (positiveScore > negativeScore) {
            memory.emotionalTone = "positive";
        } else if (negativeScore > positiveScore) {
            memory.emotionalTone = "negative";
        } else {
            memory.emotionalTone = "neutral";
        }
    }
    
    private void extractPeople(ExtractedMemory memory, String text) {
        String[] relationshipWords = {"husband", "wife", "mother", "father", "son", "daughter", 
                                    "brother", "sister", "friend", "neighbor", "colleague"};
        
        for (String relationship : relationshipWords) {
            if (text.contains(relationship)) {
                memory.peopleInvolved.add(relationship);
            }
        }
        
        // Extract names (basic pattern matching)
        Pattern namePattern = Pattern.compile("\\b[A-Z][a-z]+ [A-Z][a-z]+\\b");
        Matcher matcher = namePattern.matcher(memory.text);
        while (matcher.find()) {
            memory.peopleInvolved.add(matcher.group());
        }
    }
    
    private void extractTimeReferences(ExtractedMemory memory, String text) {
        // Extract decade/year references
        Pattern yearPattern = Pattern.compile("\\b(19[0-9][0-9]|20[0-9][0-9])\\b");
        Matcher matcher = yearPattern.matcher(text);
        if (matcher.find()) {
            memory.timeReference = matcher.group();
            return;
        }
        
        // Extract relative time references
        String[] timeReferences = {"years ago", "decades ago", "when i was young", 
                                 "in my twenties", "in my thirties", "during the war"};
        
        for (String timeRef : timeReferences) {
            if (text.contains(timeRef)) {
                memory.timeReference = timeRef;
                break;
            }
        }
    }
    
    private void extractLocations(ExtractedMemory memory, String text) {
        String[] locationIndicators = {"lived in", "grew up in", "moved to", "visited", 
                                     "hometown", "city", "country", "house", "home"};
        
        for (String indicator : locationIndicators) {
            if (text.contains(indicator)) {
                // Extract the word after the indicator
                int index = text.indexOf(indicator);
                if (index != -1) {
                    String remaining = text.substring(index + indicator.length()).trim();
                    String[] words = remaining.split("\\s+");
                    if (words.length > 0 && words[0].length() > 2) {
                        memory.location = words[0];
                        break;
                    }
                }
            }
        }
    }
    
    private void calculateConfidenceScore(ExtractedMemory memory, String text) {
        double score = 0.3; // Base score
        
        // Higher confidence for detailed memories
        if (memory.text.length() > 50) score += 0.2;
        if (memory.peopleInvolved.size() > 0) score += 0.2;
        if (memory.timeReference != null) score += 0.2;
        if (memory.location != null) score += 0.1;
        
        // Higher confidence for emotional memories
        if (!memory.emotionalTone.equals("neutral")) score += 0.1;
        
        // Higher confidence for specific categories
        if (memory.category == MemoryCategory.FAMILY || memory.category == MemoryCategory.MARRIAGE) {
            score += 0.1;
        }
        
        memory.confidenceScore = Math.min(1.0, score);
    }
    
    private void assessTherapeuticValue(ExtractedMemory memory, String text) {
        // High therapeutic value indicators
        String[] therapeuticIndicators = {"happy", "proud", "love", "family", "achievement", 
                                        "success", "joy", "wedding", "children", "grandchildren"};
        
        for (String indicator : therapeuticIndicators) {
            if (text.contains(indicator)) {
                memory.isTherapeuticallyValuable = true;
                memory.confidenceScore += 0.1; // Boost confidence for therapeutic memories
                break;
            }
        }
    }
    
    private void enhanceExtractedMemories(List<ExtractedMemory> memories) {
        // Group similar memories and enhance confidence scores
        for (int i = 0; i < memories.size(); i++) {
            for (int j = i + 1; j < memories.size(); j++) {
                ExtractedMemory m1 = memories.get(i);
                ExtractedMemory m2 = memories.get(j);
                
                // If memories are about same category and people, boost confidence
                if (m1.category == m2.category && !m1.peopleInvolved.isEmpty() && 
                    !Collections.disjoint(m1.peopleInvolved, m2.peopleInvolved)) {
                    m1.confidenceScore = Math.min(1.0, m1.confidenceScore + 0.1);
                    m2.confidenceScore = Math.min(1.0, m2.confidenceScore + 0.1);
                }
            }
        }
    }
    
    private boolean containsAny(String text, String[] words) {
        for (String word : words) {
            if (text.contains(word)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Save extracted memories to SharedPreferences for story generation
     */
    public void saveMemoriesForStoryGeneration(List<ExtractedMemory> memories) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        
        int savedCount = 0;
        for (ExtractedMemory memory : memories) {
            if (memory.isTherapeuticallyValuable && memory.confidenceScore >= 0.6) {
                String key = "memory_" + System.currentTimeMillis() + "_" + savedCount;
                editor.putString(key + "_text", memory.text);
                editor.putString(key + "_category", memory.category.getValue());
                editor.putString(key + "_emotion", memory.emotionalTone);
                editor.putFloat(key + "_confidence", (float) memory.confidenceScore);
                savedCount++;
            }
        }
        
        editor.apply();
        Log.d(TAG, "Saved " + savedCount + " high-quality memories for story generation");
    }
    
    /**
     * Get saved memories for story generation
     */
    public List<ExtractedMemory> getMemoriesForStoryGeneration() {
        List<ExtractedMemory> memories = new ArrayList<>();
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        
        Map<String, ?> allPrefs = prefs.getAll();
        Map<String, ExtractedMemory> memoryMap = new HashMap<>();
        
        for (String key : allPrefs.keySet()) {
            if (key.contains("_text")) {
                String baseKey = key.replace("_text", "");
                ExtractedMemory memory = new ExtractedMemory((String) allPrefs.get(key));
                
                String category = prefs.getString(baseKey + "_category", "general");
                memory.category = MemoryCategory.valueOf(category.toUpperCase());
                memory.emotionalTone = prefs.getString(baseKey + "_emotion", "neutral");
                memory.confidenceScore = prefs.getFloat(baseKey + "_confidence", 0.5f);
                memory.isTherapeuticallyValuable = true;
                
                memoryMap.put(baseKey, memory);
            }
        }
        
        memories.addAll(memoryMap.values());
        Log.d(TAG, "Retrieved " + memories.size() + " memories for story generation");
        
        return memories;
    }
}