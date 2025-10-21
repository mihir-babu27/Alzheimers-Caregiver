package com.mihir.alzheimerscaregiver.repository;

import android.util.Log;

import com.mihir.alzheimerscaregiver.data.entity.MemoryQuestionEntity;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;

/**
 * MemoryQuestionRepository - Handles Firebase operations for memory-based MMSE questions
 * Manages retrieval of pre-generated questions created during conversations
 */
public class MemoryQuestionRepository {
    
    private static final String TAG = "MemoryQuestionRepo";
    private static final String COLLECTION_NAME = "patients";
    
    private final FirebaseFirestore db;
    
    public interface QuestionRetrievalCallback {
        void onQuestionsRetrieved(List<MemoryQuestionEntity> questions);
        void onError(String error);
    }
    
    public MemoryQuestionRepository() {
        this.db = FirebaseFirestore.getInstance();
    }
    
    /**
     * Retrieve random memory-based questions for a patient's MMSE assessment
     * @param patientId Patient identifier
     * @param maxQuestions Maximum number of questions to retrieve
     * @param callback Callback for results
     */
    public void getRandomMemoryQuestions(String patientId, int maxQuestions, QuestionRetrievalCallback callback) {
        Log.d(TAG, "🔍 Retrieving memory questions for patient: " + patientId + " (max: " + maxQuestions + ")");
        
        db.collection(COLLECTION_NAME)
            .document(patientId)
            .collection("memory_questions")
            .orderBy("createdDate", Query.Direction.DESCENDING)
            .limit(50)  // Get more than needed for randomization
            .get()
            .addOnSuccessListener(queryDocumentSnapshots -> {
                List<MemoryQuestionEntity> allQuestions = new ArrayList<>();
                
                for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                    try {
                        MemoryQuestionEntity question = document.toObject(MemoryQuestionEntity.class);
                        
                        // Skip questions that should be refreshed and only include active questions
                        if (question != null && !question.shouldRefresh() && question.isActive()) {
                            allQuestions.add(question);
                        }
                        
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing question document", e);
                    }
                }
                
                Log.d(TAG, "✅ Retrieved " + allQuestions.size() + " valid memory questions");
                
                // Randomize and limit the questions
                Collections.shuffle(allQuestions);
                
                List<MemoryQuestionEntity> selectedQuestions = allQuestions.size() > maxQuestions 
                    ? allQuestions.subList(0, maxQuestions) 
                    : allQuestions;
                
                Log.d(TAG, "🎲 Selected " + selectedQuestions.size() + " random questions for MMSE");
                
                callback.onQuestionsRetrieved(selectedQuestions);
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "❌ Error retrieving memory questions", e);
                callback.onError("Failed to retrieve questions: " + e.getMessage());
            });
    }
    
    /**
     * Mark questions as used after they are presented in MMSE
     */
    public void markQuestionsAsUsed(List<MemoryQuestionEntity> questions) {
        Log.d(TAG, "📝 Marking " + questions.size() + " questions as used");
        
        for (MemoryQuestionEntity question : questions) {
            if (question.getQuestionId() != null) {
                
                question.markAsUsed();
                
                // Update in Firebase
                db.collection(COLLECTION_NAME)
                    .document(question.getPatientId())
                    .collection("memory_questions")
                    .whereEqualTo("questionId", question.getQuestionId())
                    .get()
                    .addOnSuccessListener(querySnapshot -> {
                        for (QueryDocumentSnapshot document : querySnapshot) {
                            document.getReference()
                                .update(
                                    "timesUsed", question.getTimesUsed(),
                                    "lastUsedDate", question.getLastUsedDate()
                                )
                                .addOnSuccessListener(aVoid -> 
                                    Log.d(TAG, "✅ Updated usage for question: " + question.getQuestionId())
                                )
                                .addOnFailureListener(e -> 
                                    Log.e(TAG, "❌ Failed to update question usage", e)
                                );
                        }
                    })
                    .addOnFailureListener(e -> 
                        Log.e(TAG, "❌ Failed to find question for usage update", e)
                    );
            }
        }
    }
    
    /**
     * Get count of available memory questions for a patient
     */
    public void getQuestionCount(String patientId, QuestionCountCallback callback) {
        db.collection(COLLECTION_NAME)
            .document(patientId)
            .collection("memory_questions")
            .get()
            .addOnSuccessListener(queryDocumentSnapshots -> {
                // Count only active questions in app logic
                int count = 0;
                for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                    try {
                        MemoryQuestionEntity question = document.toObject(MemoryQuestionEntity.class);
                        if (question != null && question.isActive()) {
                            count++;
                        }
                    } catch (Exception e) {
                        // Skip invalid documents
                    }
                }
                Log.d(TAG, "📊 Patient " + patientId + " has " + count + " memory questions available");
                callback.onCountRetrieved(count);
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "❌ Error getting question count", e);
                callback.onError("Failed to get question count: " + e.getMessage());
            });
    }
    
    public interface QuestionCountCallback {
        void onCountRetrieved(int count);
        void onError(String error);
    }
    
    /**
     * Clean up old or overused questions
     */
    public void cleanupOldQuestions(String patientId) {
        Log.d(TAG, "🧹 Starting cleanup of old questions for patient: " + patientId);
        
        db.collection(COLLECTION_NAME)
            .document(patientId)
            .collection("memory_questions")
            .get()
            .addOnSuccessListener(queryDocumentSnapshots -> {
                int deletedCount = 0;
                
                for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                    try {
                        MemoryQuestionEntity question = document.toObject(MemoryQuestionEntity.class);
                        
                        if (question != null && question.isActive() && question.shouldRefresh()) {
                            // Mark as inactive instead of deleting
                            document.getReference()
                                .update("active", false)
                                .addOnSuccessListener(aVoid -> 
                                    Log.d(TAG, "🗑️ Deactivated old question: " + question.getQuestionId())
                                );
                            deletedCount++;
                        }
                        
                    } catch (Exception e) {
                        Log.e(TAG, "Error processing question during cleanup", e);
                    }
                }
                
                Log.d(TAG, "✅ Cleanup complete. Deactivated " + deletedCount + " old questions");
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "❌ Error during question cleanup", e);
            });
    }
    
    /**
     * Get questions by difficulty level
     */
    public void getQuestionsByDifficulty(String patientId, String difficulty, int maxQuestions, 
                                       QuestionRetrievalCallback callback) {
        Log.d(TAG, "🎯 Retrieving " + difficulty + " questions for patient: " + patientId);
        
        db.collection(COLLECTION_NAME)
            .document(patientId)
            .collection("memory_questions")
            .whereEqualTo("difficulty", difficulty)
            .orderBy("createdDate", Query.Direction.DESCENDING)
            .limit(maxQuestions * 2)  // Get more for randomization
            .get()
            .addOnSuccessListener(queryDocumentSnapshots -> {
                List<MemoryQuestionEntity> questions = new ArrayList<>();
                
                for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                    try {
                        MemoryQuestionEntity question = document.toObject(MemoryQuestionEntity.class);
                        // Only include active questions that don't need refreshing
                        if (question != null && !question.shouldRefresh() && question.isActive()) {
                            questions.add(question);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing question document", e);
                    }
                }
                
                // Randomize and limit
                Collections.shuffle(questions);
                if (questions.size() > maxQuestions) {
                    questions = questions.subList(0, maxQuestions);
                }
                
                Log.d(TAG, "✅ Retrieved " + questions.size() + " " + difficulty + " questions");
                callback.onQuestionsRetrieved(questions);
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "❌ Error retrieving questions by difficulty", e);
                callback.onError("Failed to retrieve " + difficulty + " questions: " + e.getMessage());
            });
    }
}