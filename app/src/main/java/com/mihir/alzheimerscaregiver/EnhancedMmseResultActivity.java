package com.mihir.alzheimerscaregiver;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * Enhanced MMSE Results Activity
 * Shows detailed AI-powered evaluation results with breakdown by question source
 */
public class EnhancedMmseResultActivity extends AppCompatActivity {
    
    private TextView totalScoreText;
    private TextView interpretationText;
    private TextView overallFeedbackText;
    private TextView performanceBreakdownText;
    private LinearLayout evaluationDetailsContainer;
    private Button shareResultsButton;
    private Button backToMainButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_enhanced_mmse_result);

        initializeViews();
        displayResults();
        setupButtons();
    }
    
    private void initializeViews() {
        totalScoreText = findViewById(R.id.totalScoreText);
        interpretationText = findViewById(R.id.interpretationText);
        overallFeedbackText = findViewById(R.id.overallFeedbackText);
        performanceBreakdownText = findViewById(R.id.performanceBreakdownText);
        evaluationDetailsContainer = findViewById(R.id.evaluationDetailsContainer);
        shareResultsButton = findViewById(R.id.shareResultsButton);
        backToMainButton = findViewById(R.id.backToMainButton);
    }
    
    private void displayResults() {
        Intent intent = getIntent();
        
        int totalScore = intent.getIntExtra("totalScore", 0);
        int maxScore = intent.getIntExtra("maxScore", 30);
        String interpretation = intent.getStringExtra("interpretation");
        String overallFeedback = intent.getStringExtra("overallFeedback");
        boolean isPersonalized = intent.getBooleanExtra("isPersonalized", false);
        
        // Display main results
        totalScoreText.setText("Total Score: " + totalScore + "/" + maxScore);
        interpretationText.setText(interpretation != null ? interpretation : "Assessment completed");
        overallFeedbackText.setText(overallFeedback != null ? overallFeedback : "");
        
        // Show assessment type indicator
        if (isPersonalized) {
            performanceBreakdownText.setText("✨ AI-Personalized Assessment Results");
            
            // Display detailed evaluation breakdown
            ArrayList<String> evaluationDetails = intent.getStringArrayListExtra("evaluationDetails");
            if (evaluationDetails != null) {
                displayEvaluationDetails(evaluationDetails);
            }
        } else {
            performanceBreakdownText.setText("📋 Standard MMSE Assessment Results");
        }
    }
    
    private void displayEvaluationDetails(ArrayList<String> evaluationDetails) {
        evaluationDetailsContainer.removeAllViews();
        
        int memoryQuestions = 0, memoryCorrect = 0;
        int profileQuestions = 0, profileCorrect = 0;
        int standardQuestions = 0, standardCorrect = 0;
        
        for (String evalDetail : evaluationDetails) {
            String[] parts = evalDetail.split("\\|");
            if (parts.length >= 5) {
                String questionId = parts[0];
                double score = Double.parseDouble(parts[1]);
                String evaluation = parts[2];
                String feedback = parts[3];
                String source = parts[4];
                
                // Count performance by source
                boolean isCorrect = score >= 0.8; // Consider 80%+ as correct
                
                switch (source) {
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
                
                // Create detail view for each question
                View questionDetailView = createQuestionDetailView(questionId, score, evaluation, feedback, source);
                evaluationDetailsContainer.addView(questionDetailView);
            }
        }
        
        // Add performance summary
        View summaryView = createPerformanceSummaryView(
            memoryQuestions, memoryCorrect,
            profileQuestions, profileCorrect,
            standardQuestions, standardCorrect
        );
        evaluationDetailsContainer.addView(summaryView, 0); // Add at top
    }
    
    private View createQuestionDetailView(String questionId, double score, String evaluation, 
                                         String feedback, String source) {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(16, 12, 16, 12);
        container.setBackgroundResource(android.R.drawable.dialog_holo_light_frame);
        
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        layoutParams.setMargins(0, 0, 0, 8);
        container.setLayoutParams(layoutParams);
        
        // Question header
        TextView headerText = new TextView(this);
        headerText.setText(questionId + " (" + source + " question)");
        headerText.setTextSize(14);
        headerText.setTextColor(getResources().getColor(android.R.color.black));
        headerText.setTypeface(null, android.graphics.Typeface.BOLD);
        container.addView(headerText);
        
        // Score and evaluation
        TextView scoreText = new TextView(this);
        scoreText.setText("Score: " + String.format("%.1f", score) + "/1.0 - " + evaluation);
        scoreText.setTextSize(13);
        scoreText.setTextColor(getScoreColor(score));
        container.addView(scoreText);
        
        // Feedback
        TextView feedbackText = new TextView(this);
        feedbackText.setText(feedback);
        feedbackText.setTextSize(12);
        feedbackText.setTextColor(getResources().getColor(android.R.color.darker_gray));
        feedbackText.setPadding(0, 4, 0, 0);
        container.addView(feedbackText);
        
        return container;
    }
    
    private View createPerformanceSummaryView(int memoryQuestions, int memoryCorrect,
                                            int profileQuestions, int profileCorrect,
                                            int standardQuestions, int standardCorrect) {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(20, 16, 20, 16);
        container.setBackgroundResource(android.R.color.holo_blue_light);
        
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        layoutParams.setMargins(0, 0, 0, 16);
        container.setLayoutParams(layoutParams);
        
        TextView titleText = new TextView(this);
        titleText.setText("Performance Breakdown");
        titleText.setTextSize(16);
        titleText.setTypeface(null, android.graphics.Typeface.BOLD);
        titleText.setTextColor(getResources().getColor(android.R.color.white));
        container.addView(titleText);
        
        if (memoryQuestions > 0) {
            TextView memoryText = new TextView(this);
            memoryText.setText("• Personal Memories: " + memoryCorrect + "/" + memoryQuestions + 
                             " (" + (memoryCorrect * 100 / memoryQuestions) + "%)");
            memoryText.setTextColor(getResources().getColor(android.R.color.white));
            memoryText.setPadding(0, 8, 0, 0);
            container.addView(memoryText);
        }
        
        if (profileQuestions > 0) {
            TextView profileText = new TextView(this);
            profileText.setText("• Profile Information: " + profileCorrect + "/" + profileQuestions + 
                             " (" + (profileCorrect * 100 / profileQuestions) + "%)");
            profileText.setTextColor(getResources().getColor(android.R.color.white));
            profileText.setPadding(0, 4, 0, 0);
            container.addView(profileText);
        }
        
        if (standardQuestions > 0) {
            TextView standardText = new TextView(this);
            standardText.setText("• Standard Assessment: " + standardCorrect + "/" + standardQuestions + 
                               " (" + (standardCorrect * 100 / standardQuestions) + "%)");
            standardText.setTextColor(getResources().getColor(android.R.color.white));
            standardText.setPadding(0, 4, 0, 0);
            container.addView(standardText);
        }
        
        return container;
    }
    
    private int getScoreColor(double score) {
        if (score >= 0.8) {
            return getResources().getColor(android.R.color.holo_green_dark);
        } else if (score >= 0.5) {
            return getResources().getColor(android.R.color.holo_orange_dark);
        } else {
            return getResources().getColor(android.R.color.holo_red_dark);
        }
    }
    
    private void setupButtons() {
        shareResultsButton.setOnClickListener(v -> shareResults());
        backToMainButton.setOnClickListener(v -> {
            finish();
        });
    }
    
    private void shareResults() {
        Intent intent = getIntent();
        int totalScore = intent.getIntExtra("totalScore", 0);
        String interpretation = intent.getStringExtra("interpretation");
        boolean isPersonalized = intent.getBooleanExtra("isPersonalized", false);
        
        StringBuilder shareText = new StringBuilder();
        shareText.append("MMSE Assessment Results\n\n");
        shareText.append("Score: ").append(totalScore).append("/30\n");
        shareText.append("Assessment: ").append(interpretation).append("\n");
        
        if (isPersonalized) {
            shareText.append("\n✨ This was an AI-personalized assessment using the patient's memories and profile information.");
        }
        
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, "MMSE Assessment Results");
        shareIntent.putExtra(Intent.EXTRA_TEXT, shareText.toString());
        
        startActivity(Intent.createChooser(shareIntent, "Share MMSE Results"));
    }
}