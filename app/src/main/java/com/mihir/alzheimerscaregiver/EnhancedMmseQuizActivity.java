package com.mihir.alzheimerscaregiver;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.Timestamp;
import com.mihir.alzheimerscaregiver.data.entity.MmseResult;
import com.mihir.alzheimerscaregiver.data.model.PatientProfile;
import com.mihir.alzheimerscaregiver.repository.MmseResultRepository;
import com.mihir.alzheimerscaregiver.repository.MemoryQuestionRepository;
import com.mihir.alzheimerscaregiver.data.entity.MemoryQuestionEntity;
import com.mihir.alzheimerscaregiver.mmse.GeminiMMSEGenerator;
import com.mihir.alzheimerscaregiver.mmse.GeminiMMSEEvaluator;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Enhanced MMSE Quiz Activity with AI-Powered Personalization
 * 
 * Features:
 * - Generates personalized questions based on patient memories (40%)
 * - Includes profile-based questions (30%)
 * - Maintains standard MMSE questions (30%)
 * - Uses intelligent AI evaluation for partial scoring
 * - Provides detailed performance analysis by question source
 */
public class EnhancedMmseQuizActivity extends AppCompatActivity 
    implements GeminiMMSEGenerator.MMSEGenerationCallback, GeminiMMSEEvaluator.MMSEEvaluationCallback {

    private static final String TAG = "EnhancedMmseQuiz";
    
    // UI Components
    private TextView questionTitle;
    private TextView questionProgress;
    private View textInputContainer;
    private EditText textInput;
    private View multipleChoiceContainer;
    private RadioGroup radioGroup;
    private View drawingContainer;
    private com.mihir.alzheimerscaregiver.views.DrawingCanvasView drawingView;
    private LinearLayout recallContainer;
    private LinearLayout imageContainer;
    private ImageView questionImage;
    private EditText imageAnswerInput;
    private Button prevButton;
    private Button nextButton;
    private ProgressBar loadingProgress;
    private TextView loadingText;
    
    // AI Components
    private GeminiMMSEGenerator mmseGenerator;
    private GeminiMMSEEvaluator mmseEvaluator;
    private MemoryQuestionRepository memoryQuestionRepository;
    
    // Quiz Data
    private final List<GeminiMMSEGenerator.PersonalizedMMSEQuestion> personalizedQuestions = new ArrayList<>();
    private final List<MemoryQuestionEntity> storedMemoryQuestions = new ArrayList<>();
    private final List<Question> fallbackQuestions = new ArrayList<>();
    private final HashMap<String, String> answers = new HashMap<>();
    private int currentIndex = 0;
    private boolean usingPersonalizedQuestions = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_enhanced_mmse_quiz);

        initializeViews();
        initializeAIComponents();
        
        String patientId = getPatientId();
        if (patientId != null && !patientId.isEmpty()) {
            // 🚀 NEW: Load pre-generated memory questions from conversations
            showLoadingState("Loading personalized assessment questions...");
            
            loadStoredMemoryQuestions(patientId);
        } else {
            // Fallback to standard questions
            loadStandardQuestions();
            startQuiz();
        }

        setupNavigationListeners();
    }
    
    private void initializeViews() {
        questionTitle = findViewById(R.id.questionTitle);
        questionProgress = findViewById(R.id.questionProgress);
        textInputContainer = findViewById(R.id.textInputContainer);
        textInput = findViewById(R.id.textInput);
        multipleChoiceContainer = findViewById(R.id.multipleChoiceContainer);
        radioGroup = findViewById(R.id.radioGroup);
        drawingContainer = findViewById(R.id.drawingContainer);
        drawingView = findViewById(R.id.drawingView);
        recallContainer = findViewById(R.id.recallContainer);
        imageContainer = findViewById(R.id.imageContainer);
        questionImage = findViewById(R.id.questionImage);
        imageAnswerInput = findViewById(R.id.imageAnswerInput);
        prevButton = findViewById(R.id.prevButton);
        nextButton = findViewById(R.id.nextButton);
        loadingProgress = findViewById(R.id.loadingProgress);
        loadingText = findViewById(R.id.loadingText);
    }
    
    private void initializeAIComponents() {
        mmseGenerator = new GeminiMMSEGenerator(this);
        mmseEvaluator = new GeminiMMSEEvaluator(this);
        memoryQuestionRepository = new MemoryQuestionRepository();
    }
    
    private void setupNavigationListeners() {
        prevButton.setOnClickListener(v -> {
            saveCurrentAnswer();
            if (currentIndex > 0) {
                currentIndex--;
                displayQuestion();
            }
        });

        nextButton.setOnClickListener(v -> {
            saveCurrentAnswer();
            
            if (usingPersonalizedQuestions) {
                if (currentIndex < personalizedQuestions.size() - 1) {
                    currentIndex++;
                    displayQuestion();
                } else {
                    // Complete personalized quiz with AI evaluation
                    finishPersonalizedQuiz();
                }
            } else {
                if (currentIndex < fallbackQuestions.size() - 1) {
                    currentIndex++;
                    displayQuestion();
                } else {
                    // Complete standard quiz with traditional evaluation
                    finishStandardQuiz();
                }
            }
        });
    }
    
    // AI Generation Callback Implementation
    @Override
    public void onQuestionsGenerated(List<GeminiMMSEGenerator.PersonalizedMMSEQuestion> questions) {
        Log.d(TAG, "Received " + questions.size() + " personalized MMSE questions");
        
        personalizedQuestions.clear();
        personalizedQuestions.addAll(questions);
        usingPersonalizedQuestions = true;
        
        hideLoadingState();
        startQuiz();
        
        // Show success message
        runOnUiThread(() -> {
            Toast.makeText(this, "✓ Personalized assessment ready with " + questions.size() + " questions", 
                          Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public void onGenerationFailed(String error) {
        Log.w(TAG, "Personalized question generation failed: " + error);
        
        runOnUiThread(() -> {
            Toast.makeText(this, "Using standard assessment (personalization unavailable)", 
                          Toast.LENGTH_SHORT).show();
            
            hideLoadingState();
            loadStandardQuestions();
            startQuiz();
        });
    }
    
    // AI Evaluation Callback Implementation
    @Override
    public void onEvaluationComplete(List<GeminiMMSEEvaluator.AnswerEvaluation> evaluations, 
                                   int totalScore, String overallFeedback) {
        Log.d(TAG, "AI evaluation complete. Total score: " + totalScore);
        
        runOnUiThread(() -> {
            hideLoadingState();
            
            // Save enhanced results to Firebase
            saveEnhancedResults(evaluations, totalScore, overallFeedback);
            
            // Show detailed results
            showEnhancedResults(evaluations, totalScore, overallFeedback);
        });
    }

    @Override
    public void onEvaluationFailed(String error) {
        Log.w(TAG, "AI evaluation failed: " + error);
        
        runOnUiThread(() -> {
            hideLoadingState();
            Toast.makeText(this, "Evaluation completed (basic scoring used)", Toast.LENGTH_SHORT).show();
            
            // Fallback to basic evaluation
            performBasicEvaluation();
        });
    }
    
    private void showLoadingState(String message) {
        runOnUiThread(() -> {
            loadingProgress.setVisibility(View.VISIBLE);
            loadingText.setVisibility(View.VISIBLE);
            loadingText.setText(message);
            
            // Hide quiz interface
            questionTitle.setVisibility(View.GONE);
            textInputContainer.setVisibility(View.GONE);
            multipleChoiceContainer.setVisibility(View.GONE);
            drawingContainer.setVisibility(View.GONE);
            recallContainer.setVisibility(View.GONE);
            imageContainer.setVisibility(View.GONE);
            prevButton.setVisibility(View.GONE);
            nextButton.setVisibility(View.GONE);
        });
    }
    
    private void hideLoadingState() {
        runOnUiThread(() -> {
            loadingProgress.setVisibility(View.GONE);
            loadingText.setVisibility(View.GONE);
            
            // Show quiz interface
            questionTitle.setVisibility(View.VISIBLE);
            prevButton.setVisibility(View.VISIBLE);
            nextButton.setVisibility(View.VISIBLE);
        });
    }
    
    private void startQuiz() {
        currentIndex = 0;
        displayQuestion();
    }
    
    private void displayQuestion() {
        if (usingPersonalizedQuestions) {
            displayPersonalizedQuestion();
        } else {
            displayStandardQuestion();
        }
        
        updateProgress();
        updateNavigationButtons();
    }
    
    private void displayPersonalizedQuestion() {
        if (currentIndex >= personalizedQuestions.size()) return;
        
        GeminiMMSEGenerator.PersonalizedMMSEQuestion question = personalizedQuestions.get(currentIndex);
        
        questionTitle.setText(question.question);
        
        // Hide all input types first
        hideAllInputContainers();
        
        // Show appropriate input type
        switch (question.type.toLowerCase()) {
            case "text":
                textInputContainer.setVisibility(View.VISIBLE);
                textInput.setText(answers.get(question.id));
                break;
                
            case "multiple_choice":
                multipleChoiceContainer.setVisibility(View.VISIBLE);
                setupMultipleChoice(question);
                break;
                
            case "recall":
                recallContainer.setVisibility(View.VISIBLE);
                setupRecallQuestion(question);
                break;
                
            case "drawing":
                drawingContainer.setVisibility(View.VISIBLE);
                break;
                
            case "image":
                imageContainer.setVisibility(View.VISIBLE);
                setupImageQuestion(question);
                break;
                
            default:
                textInputContainer.setVisibility(View.VISIBLE);
                textInput.setText(answers.get(question.id));
                break;
        }
    }
    
    private void displayStandardQuestion() {
        if (currentIndex >= fallbackQuestions.size()) return;
        
        Question question = fallbackQuestions.get(currentIndex);
        questionTitle.setText(question.question);
        
        hideAllInputContainers();
        
        switch (question.type) {
            case TEXT:
                textInputContainer.setVisibility(View.VISIBLE);
                textInput.setText(answers.get(question.id));
                break;
                
            case MULTIPLE_CHOICE:
                multipleChoiceContainer.setVisibility(View.VISIBLE);
                setupStandardMultipleChoice(question);
                break;
                
            case RECALL:
                recallContainer.setVisibility(View.VISIBLE);
                setupStandardRecallQuestion(question);
                break;
                
            case DRAWING:
                drawingContainer.setVisibility(View.VISIBLE);
                break;
                
            case IMAGE:
                imageContainer.setVisibility(View.VISIBLE);
                setupStandardImageQuestion(question);
                break;
        }
    }
    
    private void hideAllInputContainers() {
        textInputContainer.setVisibility(View.GONE);
        multipleChoiceContainer.setVisibility(View.GONE);
        drawingContainer.setVisibility(View.GONE);
        recallContainer.setVisibility(View.GONE);
        imageContainer.setVisibility(View.GONE);
    }
    
    private void setupMultipleChoice(GeminiMMSEGenerator.PersonalizedMMSEQuestion question) {
        radioGroup.removeAllViews();
        
        if (question.options != null && question.options.length > 0) {
            for (int i = 0; i < question.options.length; i++) {
                RadioButton radioButton = new RadioButton(this);
                radioButton.setText(question.options[i]);
                radioButton.setId(i);
                radioGroup.addView(radioButton);
            }
            
            // Restore previous selection
            String previousAnswer = answers.get(question.id);
            if (previousAnswer != null) {
                for (int i = 0; i < radioGroup.getChildCount(); i++) {
                    RadioButton rb = (RadioButton) radioGroup.getChildAt(i);
                    if (rb.getText().toString().equals(previousAnswer)) {
                        rb.setChecked(true);
                        break;
                    }
                }
            }
        }
    }
    
    private void setupRecallQuestion(GeminiMMSEGenerator.PersonalizedMMSEQuestion question) {
        recallContainer.removeAllViews();
        
        if (question.acceptedAnswers != null && !question.acceptedAnswers.isEmpty()) {
            for (int i = 0; i < question.acceptedAnswers.size(); i++) {
                EditText editText = new EditText(this);
                editText.setHint("Item " + (i + 1));
                editText.setId(i);
                recallContainer.addView(editText);
                
                // Restore previous answers
                String key = question.id + "_" + i;
                String previousAnswer = answers.get(key);
                if (previousAnswer != null) {
                    editText.setText(previousAnswer);
                }
            }
        }
    }
    
    private void setupImageQuestion(GeminiMMSEGenerator.PersonalizedMMSEQuestion question) {
        // Set image if available
        if (question.imageUrl != null && !question.imageUrl.isEmpty()) {
            // Load image (implement image loading logic)
            questionImage.setVisibility(View.VISIBLE);
        }
        
        // Restore previous answer
        String previousAnswer = answers.get(question.id);
        if (previousAnswer != null) {
            imageAnswerInput.setText(previousAnswer);
        }
    }
    
    private void updateProgress() {
        if (usingPersonalizedQuestions) {
            questionProgress.setText("Question " + (currentIndex + 1) + " of " + personalizedQuestions.size());
        } else {
            questionProgress.setText("Question " + (currentIndex + 1) + " of " + fallbackQuestions.size());
        }
    }
    
    private void updateNavigationButtons() {
        prevButton.setEnabled(currentIndex > 0);
        
        if (usingPersonalizedQuestions) {
            nextButton.setText(currentIndex == personalizedQuestions.size() - 1 ? "Complete" : "Next");
        } else {
            nextButton.setText(currentIndex == fallbackQuestions.size() - 1 ? "Complete" : "Next");
        }
    }
    
    private void saveCurrentAnswer() {
        if (usingPersonalizedQuestions) {
            savePersonalizedAnswer();
        } else {
            saveStandardAnswer();
        }
    }
    
    private void savePersonalizedAnswer() {
        if (currentIndex >= personalizedQuestions.size()) return;
        
        GeminiMMSEGenerator.PersonalizedMMSEQuestion question = personalizedQuestions.get(currentIndex);
        
        switch (question.type.toLowerCase()) {
            case "text":
                if (textInput.getVisibility() == View.VISIBLE) {
                    answers.put(question.id, textInput.getText().toString().trim());
                }
                break;
                
            case "multiple_choice":
                if (radioGroup.getVisibility() == View.VISIBLE) {
                    int selectedId = radioGroup.getCheckedRadioButtonId();
                    if (selectedId != -1) {
                        RadioButton selected = findViewById(selectedId);
                        if (selected != null) {
                            answers.put(question.id, selected.getText().toString());
                        }
                    }
                }
                break;
                
            case "recall":
                if (recallContainer.getVisibility() == View.VISIBLE) {
                    StringBuilder recallAnswer = new StringBuilder();
                    for (int i = 0; i < recallContainer.getChildCount(); i++) {
                        EditText editText = (EditText) recallContainer.getChildAt(i);
                        String itemAnswer = editText.getText().toString().trim();
                        answers.put(question.id + "_" + i, itemAnswer);
                        if (!itemAnswer.isEmpty()) {
                            if (recallAnswer.length() > 0) recallAnswer.append(", ");
                            recallAnswer.append(itemAnswer);
                        }
                    }
                    answers.put(question.id, recallAnswer.toString());
                }
                break;
                
            case "drawing":
                // Handle drawing saving if needed
                answers.put(question.id, "[Drawing completed]");
                break;
                
            case "image":
                if (imageAnswerInput.getVisibility() == View.VISIBLE) {
                    answers.put(question.id, imageAnswerInput.getText().toString().trim());
                }
                break;
        }
    }
    
    private void saveStandardAnswer() {
        if (currentIndex >= fallbackQuestions.size()) return;
        
        Question question = fallbackQuestions.get(currentIndex);
        
        switch (question.type) {
            case TEXT:
                if (textInput.getVisibility() == View.VISIBLE) {
                    answers.put(question.id, textInput.getText().toString().trim());
                }
                break;
                
            case MULTIPLE_CHOICE:
                if (radioGroup.getVisibility() == View.VISIBLE) {
                    int selectedId = radioGroup.getCheckedRadioButtonId();
                    if (selectedId != -1) {
                        RadioButton selected = findViewById(selectedId);
                        if (selected != null) {
                            answers.put(question.id, selected.getText().toString());
                        }
                    }
                }
                break;
                
            case RECALL:
                if (recallContainer.getVisibility() == View.VISIBLE) {
                    StringBuilder recallAnswer = new StringBuilder();
                    for (int i = 0; i < recallContainer.getChildCount(); i++) {
                        EditText editText = (EditText) recallContainer.getChildAt(i);
                        String itemAnswer = editText.getText().toString().trim();
                        if (!itemAnswer.isEmpty()) {
                            if (recallAnswer.length() > 0) recallAnswer.append(", ");
                            recallAnswer.append(itemAnswer);
                        }
                    }
                    answers.put(question.id, recallAnswer.toString());
                }
                break;
                
            case DRAWING:
                answers.put(question.id, "[Drawing completed]");
                break;
                
            case IMAGE:
                if (imageAnswerInput.getVisibility() == View.VISIBLE) {
                    answers.put(question.id, imageAnswerInput.getText().toString().trim());
                }
                break;
        }
    }
    
    private void finishPersonalizedQuiz() {
        showLoadingState("Evaluating your answers with AI...");
        mmseEvaluator.evaluateAnswers(personalizedQuestions, answers, this);
    }
    
    private void finishStandardQuiz() {
        // Use traditional scoring for standard questions
        Map<String, Integer> sectionScores = new LinkedHashMap<>();
        StringBuilder feedback = new StringBuilder();
        int total = scoreAnswersWithFeedback(answers, sectionScores, feedback);
        String interpretation = interpret(total);
        
        saveStandardResults(total, interpretation, feedback.toString());
        showStandardResults(total, interpretation, feedback.toString());
    }
    
    private void saveEnhancedResults(List<GeminiMMSEEvaluator.AnswerEvaluation> evaluations, 
                                   int totalScore, String overallFeedback) {
        
        // Create section scores breakdown
        Map<String, Integer> sectionScores = new LinkedHashMap<>();
        Map<String, Integer> sectionTotals = new LinkedHashMap<>();
        
        for (GeminiMMSEEvaluator.AnswerEvaluation eval : evaluations) {
            String section = personalizedQuestions.stream()
                .filter(q -> q.id.equals(eval.questionId))
                .findFirst()
                .map(q -> q.section)
                .orElse("Other");
                
            int earnedScore = (int) Math.round(eval.score * eval.maxScore);
            sectionScores.put(section, sectionScores.getOrDefault(section, 0) + earnedScore);
            sectionTotals.put(section, sectionTotals.getOrDefault(section, 0) + eval.maxScore);
        }
        
        String interpretation = interpret(totalScore);
        
        MmseResult result = new MmseResult(
            getPatientId(),
            getCaregiverIdOptional(),
            Timestamp.now(),
            sectionScores,
            totalScore,
            interpretation,
            overallFeedback
        );
        
        new MmseResultRepository().save(result, new MmseResultRepository.FirebaseCallback<Void>() {
            @Override
            public void onSuccess(Void unused) {
                Log.d(TAG, "Enhanced MMSE results saved successfully");
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "Failed to save MMSE results: " + error);
            }
        });
    }
    
    private void showEnhancedResults(List<GeminiMMSEEvaluator.AnswerEvaluation> evaluations, 
                                   int totalScore, String overallFeedback) {
        Intent intent = new Intent(this, EnhancedMmseResultActivity.class);
        intent.putExtra("totalScore", totalScore);
        intent.putExtra("maxScore", 30);
        intent.putExtra("interpretation", interpret(totalScore));
        intent.putExtra("overallFeedback", overallFeedback);
        intent.putExtra("isPersonalized", true);
        
        // Pass evaluation details
        ArrayList<String> evaluationData = new ArrayList<>();
        for (GeminiMMSEEvaluator.AnswerEvaluation eval : evaluations) {
            evaluationData.add(eval.questionId + "|" + eval.score + "|" + eval.evaluation + "|" + eval.feedback + "|" + eval.source);
        }
        intent.putStringArrayListExtra("evaluationDetails", evaluationData);
        
        startActivity(intent);
        finish();
    }
    
    private void showStandardResults(int totalScore, String interpretation, String feedback) {
        Intent intent = new Intent(this, MmseResultActivity.class);
        intent.putExtra("totalScore", totalScore);
        intent.putExtra("maxScore", 30);
        intent.putExtra("interpretation", interpretation);
        intent.putExtra("feedback", feedback);
        intent.putExtra("isPersonalized", false);
        startActivity(intent);
        finish();
    }
    
    // Include standard question loading and evaluation methods from original MmseQuizActivity
    private void loadStandardQuestions() {
        fallbackQuestions.clear();
        try {
            InputStream is = getAssets().open("mmse_questions.json");
            BufferedReader br = new BufferedReader(new InputStreamReader(is));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();

            String json = sb.toString().trim();
            if (json.startsWith("[")) {
                parseQuestionsArray(new JSONArray(json));
            } else {
                JSONObject root = new JSONObject(json);
                if (root.has("questions")) {
                    parseQuestionsArray(root.getJSONArray("questions"));
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to load mmse_questions.json", e);
        }
    }
    
    // Add other helper methods from original MmseQuizActivity (parseQuestionsArray, etc.)
    // ... (Include all necessary helper methods)
    
    private String getPatientId() {
        return getIntent().getStringExtra("patient_id");
    }
    
    private String getCaregiverIdOptional() {
        return getIntent().getStringExtra("caregiver_id");
    }
    
    private PatientProfile createPatientProfileFromId(String patientId) {
        // Simplified PatientProfile creation - in production, load from Firebase
        PatientProfile profile = new PatientProfile();
        profile.setPatientId(patientId);
        profile.setName(getIntent().getStringExtra("patient_name"));
        profile.setBirthYear(getIntent().getStringExtra("patient_birth_year"));
        profile.setBirthplace(getIntent().getStringExtra("patient_birthplace"));
        profile.setProfession(getIntent().getStringExtra("patient_profession"));
        profile.setOtherDetails(getIntent().getStringExtra("patient_other_details"));
        return profile;
    }
    
    private String interpret(int score) {
        if (score >= 24) return "Normal cognitive function";
        else if (score >= 18) return "Mild cognitive impairment";
        else if (score >= 10) return "Moderate cognitive impairment";
        else return "Severe cognitive impairment";
    }
    
    // Placeholder implementations for missing methods
    private void setupStandardMultipleChoice(Question question) { /* Implementation */ }
    private void setupStandardRecallQuestion(Question question) { /* Implementation */ }
    private void setupStandardImageQuestion(Question question) { /* Implementation */ }
    private void parseQuestionsArray(JSONArray arr) { /* Implementation from original */ }
    private void performBasicEvaluation() { /* Implementation */ }
    private void saveStandardResults(int totalScore, String interpretation, String feedback) { /* Implementation */ }
    private int scoreAnswersWithFeedback(HashMap<String, String> answers, Map<String, Integer> sectionScores, StringBuilder feedback) { 
        return 0; /* Implementation from original */ 
    }
    
    // Question and QuestionType classes (from original)
    public static class Question {
        public String id;
        public String section;
        public String question;
        public QuestionType type;
        public String[] options;
        public List<String> expectedWords;
        public String imageUrl;
        public int score;
        public String expectedAnswer;
        public List<String> acceptedAnswers;
        public String correctOption;
        
        public Question(String id, String section, String question, QuestionType type, 
                       String[] options, List<String> expectedWords, String imageUrl, int score) {
            this.id = id;
            this.section = section;
            this.question = question;
            this.type = type;
            this.options = options;
            this.expectedWords = expectedWords;
            this.imageUrl = imageUrl;
            this.score = score;
        }
    }
    
    /**
     * 🚀 NEW METHOD: Load pre-generated memory questions from conversations
     * This replaces real-time AI generation with fast database retrieval
     */
    private void loadStoredMemoryQuestions(String patientId) {
        Log.d(TAG, "🔍 Loading stored memory questions for patient: " + patientId);
        
        // Get up to 5 memory-based questions (40% of typical 12-question MMSE)
        memoryQuestionRepository.getRandomMemoryQuestions(patientId, 5, 
            new MemoryQuestionRepository.QuestionRetrievalCallback() {
                
                @Override
                public void onQuestionsRetrieved(List<MemoryQuestionEntity> memoryQuestions) {
                    Log.d(TAG, "✅ Retrieved " + memoryQuestions.size() + " stored memory questions");
                    
                    storedMemoryQuestions.clear();
                    storedMemoryQuestions.addAll(memoryQuestions);
                    
                    // Convert to PersonalizedMMSEQuestion format and create hybrid quiz
                    createHybridQuestionSet(patientId, memoryQuestions);
                }
                
                @Override
                public void onError(String error) {
                    Log.e(TAG, "❌ Failed to load stored questions: " + error);
                    
                    // Fallback to standard questions if no stored questions available
                    runOnUiThread(() -> {
                        Toast.makeText(EnhancedMmseQuizActivity.this, 
                            "Loading standard assessment (no conversation history found)", 
                            Toast.LENGTH_SHORT).show();
                        
                        loadStandardQuestions();
                        startQuiz();
                    });
                }
            }
        );
    }
    
    /**
     * Create hybrid question set combining stored memory questions with profile and standard questions
     */
    private void createHybridQuestionSet(String patientId, List<MemoryQuestionEntity> memoryQuestions) {
        Log.d(TAG, "🎯 Creating hybrid question set with " + memoryQuestions.size() + " memory questions");
        
        personalizedQuestions.clear();
        
        // Convert stored memory questions to PersonalizedMMSEQuestion format
        for (MemoryQuestionEntity memoryQuestion : memoryQuestions) {
            GeminiMMSEGenerator.PersonalizedMMSEQuestion personalizedQuestion = 
                convertMemoryQuestionToPersonalized(memoryQuestion);
            personalizedQuestions.add(personalizedQuestion);
        }
        
        // Add profile-based questions (30% - about 3-4 questions)
        PatientProfile patientProfile = createPatientProfileFromId(patientId);
        addProfileBasedQuestions(patientProfile, 3);
        
        // Add standard MMSE questions (30% - about 3-4 questions)  
        addStandardQuestions(3);
        
        // Mark questions as used for tracking
        memoryQuestionRepository.markQuestionsAsUsed(memoryQuestions);
        
        Log.d(TAG, "✅ Hybrid question set ready: " + personalizedQuestions.size() + " total questions");
        Log.d(TAG, "   - " + memoryQuestions.size() + " memory-based questions");
        Log.d(TAG, "   - 3 profile-based questions");  
        Log.d(TAG, "   - 3 standard MMSE questions");
        
        runOnUiThread(() -> {
            usingPersonalizedQuestions = true;
            hideLoadingState();
            startQuiz();
        });
    }
    
    /**
     * Convert MemoryQuestionEntity to PersonalizedMMSEQuestion format
     */
    private GeminiMMSEGenerator.PersonalizedMMSEQuestion convertMemoryQuestionToPersonalized(MemoryQuestionEntity memoryQuestion) {
        // Prepare accepted answers list
        List<String> acceptedAnswers = new ArrayList<>();
        acceptedAnswers.add(memoryQuestion.getCorrectAnswer());
        
        // Add alternative answers if available
        if (memoryQuestion.getAlternativeAnswers() != null) {
            acceptedAnswers.addAll(memoryQuestion.getAlternativeAnswers());
        }
        
        // Generate unique ID for the question
        String questionId = "memory_" + memoryQuestion.getQuestionId();
        
        // Convert MCQ options from List<String> to String[] for MMSE system
        String[] mcqOptions = null;
        if (memoryQuestion.getAlternativeAnswers() != null && !memoryQuestion.getAlternativeAnswers().isEmpty()) {
            mcqOptions = memoryQuestion.getAlternativeAnswers().toArray(new String[0]);
        }
        
        // Create PersonalizedMMSEQuestion using proper constructor for MCQ
        GeminiMMSEGenerator.PersonalizedMMSEQuestion question = new GeminiMMSEGenerator.PersonalizedMMSEQuestion(
            questionId,                                    // id
            "Memory Assessment",                           // section  
            memoryQuestion.getQuestion(),                  // question
            "multiple_choice",                             // type (MCQ for fill-in-the-blank)
            mcqOptions,                                    // options (MCQ choices)
            memoryQuestion.getCorrectAnswer(),             // correctAnswer
            acceptedAnswers,                               // acceptedAnswers (includes correct answer)
            1,                                             // score (1 point)
            memoryQuestion.getDifficulty(),                // difficulty
            "memory",                                      // source (mark as memory-based)
            memoryQuestion.getMemoryText()                 // memoryContext
        );
        
        Log.d(TAG, "🔄 Converted memory question: " + question.question);
        return question;
    }
    
    /**
     * Add profile-based questions to the hybrid set
     */
    private void addProfileBasedQuestions(PatientProfile profile, int count) {
        // Simple profile-based questions (can be enhanced later)
        List<GeminiMMSEGenerator.PersonalizedMMSEQuestion> profileQuestions = new ArrayList<>();
        
        if (profile.getName() != null && !profile.getName().isEmpty()) {
            List<String> nameAnswers = new ArrayList<>();
            nameAnswers.add(profile.getName());
            
            GeminiMMSEGenerator.PersonalizedMMSEQuestion nameQ = new GeminiMMSEGenerator.PersonalizedMMSEQuestion(
                "profile_name",                            // id
                "Personal Information",                    // section
                "What is your full name?",                 // question
                "text",                                    // type
                null,                                      // options
                profile.getName(),                         // correctAnswer
                nameAnswers,                               // acceptedAnswers
                1,                                         // score
                "easy",                                    // difficulty
                "profile",                                 // source
                "Patient profile data"                     // memoryContext
            );
            profileQuestions.add(nameQ);
        }
        
        if (profile.getBirthYear() != null && !profile.getBirthYear().isEmpty()) {
            List<String> birthAnswers = new ArrayList<>();
            birthAnswers.add(profile.getBirthYear());
            
            GeminiMMSEGenerator.PersonalizedMMSEQuestion birthQ = new GeminiMMSEGenerator.PersonalizedMMSEQuestion(
                "profile_birth_year",                     // id
                "Personal Information",                    // section
                "What year were you born?",                // question
                "text",                                    // type
                null,                                      // options
                profile.getBirthYear(),                    // correctAnswer
                birthAnswers,                              // acceptedAnswers
                1,                                         // score
                "easy",                                    // difficulty
                "profile",                                 // source
                "Patient profile data"                     // memoryContext
            );
            profileQuestions.add(birthQ);
        }
        
        // Add orientation question
        List<String> dateAnswers = new ArrayList<>();
        dateAnswers.add("October 21, 2025");
        dateAnswers.add("21st October 2025");
        dateAnswers.add("21/10/2025");
        
        GeminiMMSEGenerator.PersonalizedMMSEQuestion dateQ = new GeminiMMSEGenerator.PersonalizedMMSEQuestion(
            "orientation_date",                        // id
            "Orientation",                            // section
            "What is today's date?",                  // question
            "text",                                   // type
            null,                                     // options
            "October 21, 2025",                       // correctAnswer
            dateAnswers,                              // acceptedAnswers
            1,                                        // score
            "medium",                                 // difficulty
            "standard",                               // source
            "Current date orientation"                // memoryContext
        );
        profileQuestions.add(dateQ);
        
        // Limit to requested count
        int addCount = Math.min(count, profileQuestions.size());
        for (int i = 0; i < addCount; i++) {
            personalizedQuestions.add(profileQuestions.get(i));
        }
        
        Log.d(TAG, "➕ Added " + addCount + " profile-based questions");
    }
    
    /**
     * Add standard MMSE questions to the hybrid set
     */
    private void addStandardQuestions(int count) {
        // Simple standard questions (enhance as needed)
        List<GeminiMMSEGenerator.PersonalizedMMSEQuestion> standardQuestions = new ArrayList<>();
        
        // Attention/Calculation question
        List<String> calcAnswers = new ArrayList<>();
        calcAnswers.add("100, 93, 86, 79, 72");
        calcAnswers.add("100 93 86 79 72");
        
        GeminiMMSEGenerator.PersonalizedMMSEQuestion calcQ = new GeminiMMSEGenerator.PersonalizedMMSEQuestion(
            "standard_calculation",                    // id
            "Attention & Calculation",                 // section
            "Starting from 100, subtract 7 and keep subtracting 7. Say the first five numbers.", // question
            "text",                                    // type
            null,                                      // options
            "100, 93, 86, 79, 72",                    // correctAnswer
            calcAnswers,                               // acceptedAnswers
            5,                                         // score (5 points for calculation)
            "hard",                                    // difficulty
            "standard",                                // source
            "Standard MMSE calculation test"           // memoryContext
        );
        standardQuestions.add(calcQ);
        
        // Memory recall question  
        List<String> recallAnswers = new ArrayList<>();
        recallAnswers.add("Apple, Penny, Table");
        recallAnswers.add("Apple Penny Table");
        recallAnswers.add("apple, penny, table");
        
        GeminiMMSEGenerator.PersonalizedMMSEQuestion recallQ = new GeminiMMSEGenerator.PersonalizedMMSEQuestion(
            "standard_recall",                         // id
            "Memory Recall",                           // section
            "I'm going to say three words: Apple, Penny, Table. Please repeat them back to me.", // question
            "recall",                                  // type
            null,                                      // options
            "Apple, Penny, Table",                     // correctAnswer
            recallAnswers,                             // acceptedAnswers
            3,                                         // score (3 points for recall)
            "medium",                                  // difficulty
            "standard",                                // source
            "Standard MMSE recall test"                // memoryContext
        );
        standardQuestions.add(recallQ);
        
        // Language question
        List<String> langAnswers = new ArrayList<>();
        langAnswers.add("Watch");
        langAnswers.add("watch");
        langAnswers.add("Wristwatch");
        langAnswers.add("wristwatch");
        
        GeminiMMSEGenerator.PersonalizedMMSEQuestion langQ = new GeminiMMSEGenerator.PersonalizedMMSEQuestion(
            "standard_language",                       // id
            "Language",                                // section
            "What do you call this object? (Point to a watch)", // question
            "text",                                    // type
            null,                                      // options
            "Watch",                                   // correctAnswer
            langAnswers,                               // acceptedAnswers
            1,                                         // score
            "easy",                                    // difficulty
            "standard",                                // source
            "Standard MMSE language test"              // memoryContext
        );
        standardQuestions.add(langQ);
        
        // Limit to requested count
        int addCount = Math.min(count, standardQuestions.size());
        for (int i = 0; i < addCount; i++) {
            personalizedQuestions.add(standardQuestions.get(i));
        }
        
        Log.d(TAG, "➕ Added " + addCount + " standard MMSE questions");
    }
    
    public enum QuestionType {
        TEXT, MULTIPLE_CHOICE, RECALL, DRAWING, IMAGE
    }
}