package com.mihir.alzheimerscaregiver.mmse;

import android.content.Context;
import android.util.Log;

import com.mihir.alzheimerscaregiver.BuildConfig;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Intelligent MMSE Answer Evaluator using Gemini AI
 * 
 * Provides sophisticated answer evaluation that:
 * - Accepts partial correct answers (e.g., "Bengaluru" = "Bangalore")
 * - Recognizes synonyms and variations
 * - Provides partial scoring for close answers
 * - Uses contextual understanding rather than exact string matching
 * - Generates explanatory feedback for incorrect answers
 */
public class GeminiMMSEEvaluator {
    private static final String TAG = "GeminiMMSEEvaluator";
    
    // Gemini API Configuration - Updated with newer available models
    private static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/";
    private static final String[] MODEL_NAMES = {
        
        "gemini-2.5-flash",          // Recommended: Fast, efficient, multimodal (Active)
        "gemini-2.5-flash-lite",     // Extremely low cost/latency fallback
        "gemini-2.5-pro"             // High intelligence for complex reasoning
    };
    private static final String GENERATE_ENDPOINT = ":generateContent?key=";
    private static final String API_KEY = BuildConfig.GEMINI_API_KEY;
    
    private final Context context;
    private final ExecutorService executorService;
    private final OkHttpClient httpClient;
    
    // Track current model index for fallback
    private int currentModelIndex = 0;
    
    public interface MMSEEvaluationCallback {
        void onEvaluationComplete(List<AnswerEvaluation> evaluations, int totalScore, String overallFeedback);
        void onEvaluationFailed(String error);
    }
    
    /**
     * Detailed answer evaluation result
     */
    public static class AnswerEvaluation {
        public final String questionId;
        public final String question;
        public final String patientAnswer;
        public final String correctAnswer;
        public final double score; // 0.0 to 1.0 (allows partial credit)
        public final int maxScore;
        public final String evaluation; // "Correct", "Partially Correct", "Incorrect"
        public final String feedback; // Explanation of scoring
        public final String difficulty;
        public final String source;
        
        public AnswerEvaluation(String questionId, String question, String patientAnswer, 
                              String correctAnswer, double score, int maxScore, String evaluation,
                              String feedback, String difficulty, String source) {
            this.questionId = questionId;
            this.question = question;
            this.patientAnswer = patientAnswer;
            this.correctAnswer = correctAnswer;
            this.score = score;
            this.maxScore = maxScore;
            this.evaluation = evaluation;
            this.feedback = feedback;
            this.difficulty = difficulty;
            this.source = source;
        }
    }
    
    public GeminiMMSEEvaluator(Context context) {
        this.context = context;
        this.executorService = Executors.newCachedThreadPool();
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .build();
    }
    
    /**
     * Evaluate patient answers using fallback method (bypassing AI evaluation)
     */
    public void evaluateAnswers(List<GeminiMMSEGenerator.PersonalizedMMSEQuestion> questions, 
                               Map<String, String> patientAnswers, 
                               MMSEEvaluationCallback callback) {
        
        if (questions == null || questions.isEmpty()) {
            callback.onEvaluationFailed("No questions to evaluate");
            return;
        }
        
        executorService.execute(() -> {
            try {
                // Use fallback evaluation method directly (skip AI evaluation)
                Log.d(TAG, "Using fallback evaluation method");
                List<AnswerEvaluation> evaluations = performBasicEvaluation(questions, patientAnswers);
                int totalScore = calculateTotalScore(evaluations);
                String overallFeedback = generateOverallFeedback(evaluations, totalScore);
                callback.onEvaluationComplete(evaluations, totalScore, overallFeedback);
            } catch (Exception e) {
                Log.e(TAG, "Error evaluating MMSE answers", e);
                callback.onEvaluationFailed("Error evaluating answers: " + e.getMessage());
            }
        });
    }
    
    /**
     * Evaluate answers using Gemini API for intelligent scoring
     */
    private void evaluateAnswersWithAPI(List<GeminiMMSEGenerator.PersonalizedMMSEQuestion> questions,
                                       Map<String, String> patientAnswers,
                                       MMSEEvaluationCallback callback) {
        try {
            String prompt = buildEvaluationPrompt(questions, patientAnswers);
            
            Log.d(TAG, "Evaluating MMSE answers with Gemini AI");
            
            // Create request body
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
            
            // Add generation config for structured output
            JSONObject generationConfig = new JSONObject();
            generationConfig.put("temperature", 0.3); // Lower temperature for consistent evaluation
            generationConfig.put("topK", 20);
            generationConfig.put("topP", 0.8);
            generationConfig.put("maxOutputTokens", 2048);
            requestBody.put("generationConfig", generationConfig);
            
            RequestBody body = RequestBody.create(
                MediaType.parse("application/json"),
                requestBody.toString()
            );
            
            // Build URL with current model
            String currentModel = MODEL_NAMES[currentModelIndex];
            String apiUrl = BASE_URL + currentModel + GENERATE_ENDPOINT + API_KEY;
            
            Request request = new Request.Builder()
                    .url(apiUrl)
                    .addHeader("Content-Type", "application/json")
                    .post(body)
                    .build();
            
            httpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "Gemini API evaluation call failed", e);
                    // Fallback to basic evaluation
                    List<AnswerEvaluation> fallbackEvaluations = performBasicEvaluation(questions, patientAnswers);
                    int totalScore = calculateTotalScore(fallbackEvaluations);
                    callback.onEvaluationComplete(fallbackEvaluations, totalScore, "Evaluation completed (basic mode)");
                }
                
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    try {
                        if (response.isSuccessful() && response.body() != null) {
                            String responseBody = response.body().string();
                            List<AnswerEvaluation> evaluations = parseEvaluationResponse(responseBody, questions, patientAnswers);
                            
                            if (evaluations.isEmpty()) {
                                Log.w(TAG, "No evaluations parsed, using fallback");
                                evaluations = performBasicEvaluation(questions, patientAnswers);
                            }
                            
                            int totalScore = calculateTotalScore(evaluations);
                            String overallFeedback = generateOverallFeedback(evaluations, totalScore);
                            
                            callback.onEvaluationComplete(evaluations, totalScore, overallFeedback);
                        } else {
                            String errorBody = response.body() != null ? response.body().string() : "";
                            Log.e(TAG, "Gemini evaluation API error: " + response.code() + " - " + errorBody);
                            
                            // Fallback to basic evaluation
                            List<AnswerEvaluation> fallbackEvaluations = performBasicEvaluation(questions, patientAnswers);
                            int totalScore = calculateTotalScore(fallbackEvaluations);
                            callback.onEvaluationComplete(fallbackEvaluations, totalScore, "Evaluation completed (fallback mode)");
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error processing evaluation response", e);
                        callback.onEvaluationFailed("Error processing evaluation: " + e.getMessage());
                    } finally {
                        if (response.body() != null) {
                            response.body().close();
                        }
                    }
                }
            });
            
        } catch (Exception e) {
            Log.e(TAG, "Error creating evaluation request", e);
            callback.onEvaluationFailed("Error creating evaluation request: " + e.getMessage());
        }
    }
    
    /**
     * Build comprehensive evaluation prompt for Gemini
     */
    private String buildEvaluationPrompt(List<GeminiMMSEGenerator.PersonalizedMMSEQuestion> questions,
                                        Map<String, String> patientAnswers) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("You are an expert neuropsychologist evaluating MMSE (Mini-Mental State Examination) answers. ");
        prompt.append("Provide intelligent, contextually-aware scoring that recognizes partial correctness, synonyms, and cultural variations.\n\n");
        
        prompt.append("EVALUATION GUIDELINES:\n");
        prompt.append("- Accept synonym variations (e.g., 'Bengaluru' = 'Bangalore')\n");
        prompt.append("- Provide partial credit for close answers (0.5 points)\n");
        prompt.append("- Consider cultural context for Indian patients\n");
        prompt.append("- Be lenient with spelling variations\n");
        prompt.append("- For memory-based questions, accept reasonable approximations\n");
        prompt.append("- Maintain clinical standards while being patient-centered\n\n");
        
        prompt.append("QUESTIONS AND PATIENT ANSWERS:\n");
        
        for (int i = 0; i < questions.size(); i++) {
            GeminiMMSEGenerator.PersonalizedMMSEQuestion question = questions.get(i);
            String patientAnswer = patientAnswers.get(question.id);
            
            prompt.append("Question ").append(i + 1).append(":\n");
            prompt.append("ID: ").append(question.id).append("\n");
            prompt.append("Section: ").append(question.section).append("\n");
            prompt.append("Question: ").append(question.question).append("\n");
            prompt.append("Type: ").append(question.type).append("\n");
            prompt.append("Expected Answer: ").append(question.correctAnswer).append("\n");
            if (question.acceptedAnswers != null && !question.acceptedAnswers.isEmpty()) {
                prompt.append("Accepted Variations: ").append(String.join(", ", question.acceptedAnswers)).append("\n");
            }
            prompt.append("Patient Answer: ").append(patientAnswer != null ? patientAnswer : "[No Answer]").append("\n");
            prompt.append("Max Score: ").append(question.score).append("\n");
            prompt.append("Difficulty: ").append(question.difficulty).append("\n");
            prompt.append("Source: ").append(question.source).append("\n");
            if (question.memoryContext != null && !question.memoryContext.isEmpty()) {
                prompt.append("Memory Context: ").append(question.memoryContext).append("\n");
            }
            prompt.append("---\n");
        }
        
        prompt.append("\nOUTPUT FORMAT: Return ONLY a valid JSON object with this exact structure:\n");
        prompt.append("{\n");
        prompt.append("  \"evaluations\": [\n");
        prompt.append("    {\n");
        prompt.append("      \"questionId\": \"question_id\",\n");
        prompt.append("      \"score\": 0.0-1.0, // Decimal score (0.5 for partial credit)\n");
        prompt.append("      \"evaluation\": \"Correct|Partially Correct|Incorrect\",\n");
        prompt.append("      \"feedback\": \"Detailed explanation of scoring decision\"\n");
        prompt.append("    }\n");
        prompt.append("  ],\n");
        prompt.append("  \"overallAnalysis\": \"Summary of cognitive performance patterns\"\n");
        prompt.append("}\n\n");
        
        prompt.append("SCORING EXAMPLES:\n");
        prompt.append("- 'Bengaluru' for 'Bangalore': 1.0 (Full credit - same city)\n");
        prompt.append("- 'Karnataka' for 'Bangalore': 0.5 (Partial credit - correct state)\n");
        prompt.append("- 'Cricket' for 'Playing Cricket': 1.0 (Full credit - core answer correct)\n");
        prompt.append("- 'Cat' for 'Yellow Cat named Tommy': 0.5 (Partial credit - animal type correct)\n");
        prompt.append("- 'School' for 'The New Cambridge English School': 0.3 (Minimal credit - institution type correct)\n\n");
        
        prompt.append("Evaluate all answers now. Return ONLY the JSON - no additional text.");
        
        return prompt.toString();
    }
    
    /**
     * Parse Gemini evaluation response
     */
    private List<AnswerEvaluation> parseEvaluationResponse(String responseBody,
                                                          List<GeminiMMSEGenerator.PersonalizedMMSEQuestion> questions,
                                                          Map<String, String> patientAnswers) {
        List<AnswerEvaluation> evaluations = new ArrayList<>();
        
        try {
            JSONObject response = new JSONObject(responseBody);
            JSONArray candidates = response.getJSONArray("candidates");
            
            if (candidates.length() > 0) {
                JSONObject candidate = candidates.getJSONObject(0);
                JSONObject content = candidate.getJSONObject("content");
                JSONArray parts = content.getJSONArray("parts");
                
                if (parts.length() > 0) {
                    String generatedText = parts.getJSONObject(0).getString("text");
                    
                    // Clean up the response to extract JSON
                    generatedText = generatedText.trim();
                    if (generatedText.startsWith("```json")) {
                        generatedText = generatedText.substring(7);
                    }
                    if (generatedText.endsWith("```")) {
                        generatedText = generatedText.substring(0, generatedText.length() - 3);
                    }
                    
                    Log.d(TAG, "Evaluation response: " + generatedText.substring(0, Math.min(300, generatedText.length())));
                    
                    JSONObject evaluationJson = new JSONObject(generatedText);
                    JSONArray evaluationArray = evaluationJson.getJSONArray("evaluations");
                    
                    // Create map for quick question lookup
                    Map<String, GeminiMMSEGenerator.PersonalizedMMSEQuestion> questionMap = new HashMap<>();
                    for (GeminiMMSEGenerator.PersonalizedMMSEQuestion q : questions) {
                        questionMap.put(q.id, q);
                    }
                    
                    for (int i = 0; i < evaluationArray.length(); i++) {
                        JSONObject evalObj = evaluationArray.getJSONObject(i);
                        
                        String questionId = evalObj.getString("questionId");
                        double score = evalObj.getDouble("score");
                        String evaluation = evalObj.getString("evaluation");
                        String feedback = evalObj.getString("feedback");
                        
                        GeminiMMSEGenerator.PersonalizedMMSEQuestion question = questionMap.get(questionId);
                        if (question != null) {
                            String patientAnswer = patientAnswers.get(questionId);
                            
                            AnswerEvaluation answerEval = new AnswerEvaluation(
                                questionId, question.question, 
                                patientAnswer != null ? patientAnswer : "",
                                question.correctAnswer, score, question.score,
                                evaluation, feedback, question.difficulty, question.source
                            );
                            
                            evaluations.add(answerEval);
                        }
                    }
                }
            }
            
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing evaluation response", e);
        }
        
        return evaluations;
    }
    
    /**
     * Fallback basic evaluation when AI is unavailable
     */
    private List<AnswerEvaluation> performBasicEvaluation(List<GeminiMMSEGenerator.PersonalizedMMSEQuestion> questions,
                                                         Map<String, String> patientAnswers) {
        List<AnswerEvaluation> evaluations = new ArrayList<>();
        
        for (GeminiMMSEGenerator.PersonalizedMMSEQuestion question : questions) {
            String patientAnswer = patientAnswers.get(question.id);
            String normalizedPatient = patientAnswer != null ? patientAnswer.trim().toLowerCase() : "";
            
            double score = 0.0;
            String evaluation = "Incorrect";
            String feedback = "Answer does not match expected response.";
            
            if (!normalizedPatient.isEmpty()) {
                String normalizedCorrect = question.correctAnswer.trim().toLowerCase();
                
                // Check exact match
                if (normalizedPatient.equals(normalizedCorrect)) {
                    score = 1.0;
                    evaluation = "Correct";
                    feedback = "Perfect match with expected answer.";
                }
                // Check accepted answers
                /* else if (question.acceptedAnswers != null) {
                    for (String accepted : question.acceptedAnswers) {
                        if (normalizedPatient.equals(accepted.trim().toLowerCase())) {
                            score = 1.0;
                            evaluation = "Correct";
                            feedback = "Matches accepted alternative answer.";
                            break;
                        }
                    }
                } */
                // Basic partial credit for contains
                else if (normalizedCorrect.contains(normalizedPatient) || normalizedPatient.contains(normalizedCorrect)) {
                    score = 0.5;
                    evaluation = "Partially Correct";
                    feedback = "Contains elements of the correct answer.";
                }
            }
            
            AnswerEvaluation answerEval = new AnswerEvaluation(
                question.id, question.question, 
                patientAnswer != null ? patientAnswer : "",
                question.correctAnswer, score, question.score,
                evaluation, feedback, question.difficulty, question.source
            );
            
            evaluations.add(answerEval);
        }
        
        return evaluations;
    }
    
    /**
     * Calculate total score from evaluations
     */
    private int calculateTotalScore(List<AnswerEvaluation> evaluations) {
        double totalScore = 0.0;
        for (AnswerEvaluation eval : evaluations) {
            totalScore += eval.score * eval.maxScore;
        }
        return (int) Math.round(totalScore);
    }
    
    /**
     * Generate overall feedback based on performance patterns
     */
    private String generateOverallFeedback(List<AnswerEvaluation> evaluations, int totalScore) {
        StringBuilder feedback = new StringBuilder();
        
        int memoryQuestions = 0, memoryCorrect = 0;
        int profileQuestions = 0, profileCorrect = 0;
        int standardQuestions = 0, standardCorrect = 0;
        
        for (AnswerEvaluation eval : evaluations) {
            boolean isCorrect = eval.score >= 0.8; // Consider 80%+ as correct
            
            switch (eval.source) {
                case "Memory":
                    memoryQuestions++;
                    if (isCorrect) memoryCorrect++;
                    break;
                case "Profile":
                    profileQuestions++;
                    if (isCorrect) profileCorrect++;
                    break;
                case "Standard":
                    standardQuestions++;
                    if (isCorrect) standardCorrect++;
                    break;
            }
        }
        
        feedback.append("MMSE Score: ").append(totalScore).append("/30\n\n");
        
        feedback.append("Performance Breakdown:\n");
        if (memoryQuestions > 0) {
            feedback.append("• Personal Memories: ").append(memoryCorrect).append("/").append(memoryQuestions)
                   .append(" (").append((memoryCorrect * 100 / memoryQuestions)).append("%)\n");
        }
        if (profileQuestions > 0) {
            feedback.append("• Profile Information: ").append(profileCorrect).append("/").append(profileQuestions)
                   .append(" (").append((profileCorrect * 100 / profileQuestions)).append("%)\n");
        }
        if (standardQuestions > 0) {
            feedback.append("• Standard Assessment: ").append(standardCorrect).append("/").append(standardQuestions)
                   .append(" (").append((standardCorrect * 100 / standardQuestions)).append("%)\n");
        }
        
        // Clinical interpretation
        feedback.append("\nCognitive Assessment: ");
        if (totalScore >= 24) {
            feedback.append("Normal cognitive function");
        } else if (totalScore >= 18) {
            feedback.append("Mild cognitive impairment");
        } else if (totalScore >= 10) {
            feedback.append("Moderate cognitive impairment");
        } else {
            feedback.append("Severe cognitive impairment");
        }
        
        return feedback.toString();
    }
}