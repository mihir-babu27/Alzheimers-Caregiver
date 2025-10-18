package com.mihir.alzheimerscaregiver.repository;

import android.util.Log;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.mihir.alzheimerscaregiver.data.entity.ConversationEntity;
import com.mihir.alzheimerscaregiver.data.entity.MessageEntity;
import com.mihir.alzheimerscaregiver.data.entity.ExtractedMemoryEntity;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * ConversationRepository - Handles Firebase storage and retrieval of conversations
 * Manages conversations, messages, and extracted memories for caregiver access
 */
public class ConversationRepository {
    
    private static final String TAG = "ConversationRepository";
    private static final String CONVERSATIONS_COLLECTION = "conversations";
    private static final String MESSAGES_COLLECTION = "messages";
    private static final String EXTRACTED_MEMORIES_COLLECTION = "extracted_memories";
    
    private final FirebaseFirestore db;
    
    public ConversationRepository() {
        this.db = FirebaseFirestore.getInstance();
    }
    
    /**
     * Firebase callback interface for async operations
     */
    public interface FirebaseCallback<T> {
        void onSuccess(T result);
        void onError(String error);
    }
    
    // ==================== CONVERSATION MANAGEMENT ====================
    
    /**
     * Start a new conversation session
     */
    public void startConversation(String patientId, FirebaseCallback<ConversationEntity> callback) {
        try {
            // Generate new conversation ID
            DocumentReference docRef = db.collection(CONVERSATIONS_COLLECTION).document();
            String conversationId = docRef.getId();
            
            // Create new conversation entity
            ConversationEntity conversation = new ConversationEntity(conversationId, patientId);
            
            // Save to Firebase
            docRef.set(conversation)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Conversation started successfully: " + conversationId);
                    callback.onSuccess(conversation);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error starting conversation", e);
                    callback.onError("Failed to start conversation: " + e.getMessage());
                });
                
        } catch (Exception e) {
            Log.e(TAG, "Exception starting conversation", e);
            callback.onError("Failed to start conversation: " + e.getMessage());
        }
    }
    
    /**
     * End a conversation session
     */
    public void endConversation(String conversationId, String conversationSummary, 
                               List<String> extractedMemories, FirebaseCallback<Void> callback) {
        try {
            DocumentReference docRef = db.collection(CONVERSATIONS_COLLECTION).document(conversationId);
            
            // Update conversation with end data
            docRef.update(
                "endTime", new Date(),
                "conversationSummary", conversationSummary,
                "extractedMemories", extractedMemories != null ? extractedMemories : new ArrayList<>()
            )
            .addOnSuccessListener(aVoid -> {
                Log.d(TAG, "Conversation ended successfully: " + conversationId);
                callback.onSuccess(null);
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error ending conversation", e);
                callback.onError("Failed to end conversation: " + e.getMessage());
            });
            
        } catch (Exception e) {
            Log.e(TAG, "Exception ending conversation", e);
            callback.onError("Failed to end conversation: " + e.getMessage());
        }
    }
    
    /**
     * Get all conversations for a patient
     */
    public void getPatientConversations(String patientId, FirebaseCallback<List<ConversationEntity>> callback) {
        try {
            db.collection(CONVERSATIONS_COLLECTION)
                .whereEqualTo("patientId", patientId)
                .orderBy("startTime", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<ConversationEntity> conversations = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        ConversationEntity conversation = doc.toObject(ConversationEntity.class);
                        conversations.add(conversation);
                    }
                    Log.d(TAG, "Retrieved " + conversations.size() + " conversations for patient: " + patientId);
                    callback.onSuccess(conversations);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error getting patient conversations", e);
                    callback.onError("Failed to get conversations: " + e.getMessage());
                });
                
        } catch (Exception e) {
            Log.e(TAG, "Exception getting patient conversations", e);
            callback.onError("Failed to get conversations: " + e.getMessage());
        }
    }
    
    // ==================== MESSAGE MANAGEMENT ====================
    
    /**
     * Save a message to a conversation
     */
    public void saveMessage(String conversationId, MessageEntity message, FirebaseCallback<Void> callback) {
        try {
            // Save message to messages collection
            DocumentReference messageRef = db.collection(MESSAGES_COLLECTION).document();
            message.setMessageId(messageRef.getId());
            message.setConversationId(conversationId);
            
            messageRef.set(message)
                .addOnSuccessListener(aVoid -> {
                    // Update conversation message count
                    updateMessageCount(conversationId);
                    Log.d(TAG, "Message saved successfully: " + message.getMessageId());
                    callback.onSuccess(null);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error saving message", e);
                    callback.onError("Failed to save message: " + e.getMessage());
                });
                
        } catch (Exception e) {
            Log.e(TAG, "Exception saving message", e);
            callback.onError("Failed to save message: " + e.getMessage());
        }
    }
    
    /**
     * Get all messages for a conversation
     */
    public void getConversationMessages(String conversationId, FirebaseCallback<List<MessageEntity>> callback) {
        try {
            db.collection(MESSAGES_COLLECTION)
                .whereEqualTo("conversationId", conversationId)
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<MessageEntity> messages = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        MessageEntity message = doc.toObject(MessageEntity.class);
                        messages.add(message);
                    }
                    Log.d(TAG, "Retrieved " + messages.size() + " messages for conversation: " + conversationId);
                    callback.onSuccess(messages);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error getting conversation messages", e);
                    callback.onError("Failed to get messages: " + e.getMessage());
                });
                
        } catch (Exception e) {
            Log.e(TAG, "Exception getting conversation messages", e);
            callback.onError("Failed to get messages: " + e.getMessage());
        }
    }
    
    // ==================== MEMORY MANAGEMENT ====================
    
    /**
     * Save an extracted memory
     */
    public void saveExtractedMemory(ExtractedMemoryEntity memory, FirebaseCallback<Void> callback) {
        try {
            DocumentReference memoryRef = db.collection(EXTRACTED_MEMORIES_COLLECTION).document();
            memory.setMemoryId(memoryRef.getId());
            
            memoryRef.set(memory)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Extracted memory saved successfully: " + memory.getMemoryId());
                    callback.onSuccess(null);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error saving extracted memory", e);
                    callback.onError("Failed to save memory: " + e.getMessage());
                });
                
        } catch (Exception e) {
            Log.e(TAG, "Exception saving extracted memory", e);
            callback.onError("Failed to save memory: " + e.getMessage());
        }
    }
    
    /**
     * Get all extracted memories for a patient
     */
    public void getPatientMemories(String patientId, FirebaseCallback<List<ExtractedMemoryEntity>> callback) {
        try {
            db.collection(EXTRACTED_MEMORIES_COLLECTION)
                .whereEqualTo("patientId", patientId)
                .orderBy("extractedDate", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<ExtractedMemoryEntity> memories = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        ExtractedMemoryEntity memory = doc.toObject(ExtractedMemoryEntity.class);
                        memories.add(memory);
                    }
                    Log.d(TAG, "Retrieved " + memories.size() + " memories for patient: " + patientId);
                    callback.onSuccess(memories);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error getting patient memories", e);
                    callback.onError("Failed to get memories: " + e.getMessage());
                });
                
        } catch (Exception e) {
            Log.e(TAG, "Exception getting patient memories", e);
            callback.onError("Failed to get memories: " + e.getMessage());
        }
    }
    
    /**
     * Get unused memories for story generation
     */
    public void getUnusedMemoriesForStories(String patientId, FirebaseCallback<List<ExtractedMemoryEntity>> callback) {
        try {
            db.collection(EXTRACTED_MEMORIES_COLLECTION)
                .whereEqualTo("patientId", patientId)
                .whereEqualTo("usedInStory", false)
                .whereGreaterThanOrEqualTo("therapeuticValue", 0.6) // High therapeutic value
                .orderBy("therapeuticValue", Query.Direction.DESCENDING)
                .limit(10) // Get top 10 unused memories
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<ExtractedMemoryEntity> memories = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        ExtractedMemoryEntity memory = doc.toObject(ExtractedMemoryEntity.class);
                        memories.add(memory);
                    }
                    Log.d(TAG, "Retrieved " + memories.size() + " unused memories for stories for patient: " + patientId);
                    callback.onSuccess(memories);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error getting unused memories", e);
                    callback.onError("Failed to get unused memories: " + e.getMessage());
                });
                
        } catch (Exception e) {
            Log.e(TAG, "Exception getting unused memories", e);
            callback.onError("Failed to get unused memories: " + e.getMessage());
        }
    }
    
    // ==================== HELPER METHODS ====================
    
    private void updateMessageCount(String conversationId) {
        try {
            db.collection(MESSAGES_COLLECTION)
                .whereEqualTo("conversationId", conversationId)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    int messageCount = queryDocumentSnapshots.size();
                    
                    db.collection(CONVERSATIONS_COLLECTION)
                        .document(conversationId)
                        .update("messageCount", messageCount)
                        .addOnSuccessListener(aVoid -> 
                            Log.d(TAG, "Updated message count for conversation: " + conversationId + " to " + messageCount))
                        .addOnFailureListener(e -> 
                            Log.e(TAG, "Error updating message count", e));
                })
                .addOnFailureListener(e -> 
                    Log.e(TAG, "Error getting message count", e));
                    
        } catch (Exception e) {
            Log.e(TAG, "Exception updating message count", e);
        }
    }
    
    /**
     * Update conversation engagement level and emotional tone
     */
    public void updateConversationAnalytics(String conversationId, double emotionalTone, 
                                          int engagementLevel, boolean containsCognitiveAssessment,
                                          FirebaseCallback<Void> callback) {
        try {
            db.collection(CONVERSATIONS_COLLECTION)
                .document(conversationId)
                .update(
                    "emotionalTone", emotionalTone,
                    "engagementLevel", engagementLevel,
                    "containsCognitiveAssessment", containsCognitiveAssessment
                )
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Updated conversation analytics for: " + conversationId);
                    if (callback != null) callback.onSuccess(null);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error updating conversation analytics", e);
                    if (callback != null) callback.onError("Failed to update analytics: " + e.getMessage());
                });
                
        } catch (Exception e) {
            Log.e(TAG, "Exception updating conversation analytics", e);
            if (callback != null) callback.onError("Failed to update analytics: " + e.getMessage());
        }
    }
}