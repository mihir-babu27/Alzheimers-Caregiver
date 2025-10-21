package com.mihir.alzheimerscaregiver.testing;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.mihir.alzheimerscaregiver.R;

/**
 * Enhanced MMSE Testing Activity
 * 
 * Provides UI for running comprehensive tests on the Enhanced MMSE system:
 * - Question generation quality testing
 * - Answer evaluation accuracy testing
 * - Performance and error handling testing
 * 
 * This activity is intended for development and QA use only.
 */
public class EnhancedMMSETestActivity extends AppCompatActivity {
    private static final String TAG = "EnhancedMMSETestActivity";
    
    private EnhancedMMSETester tester;
    private TextView testResultsText;
    private ScrollView scrollView;
    private Button runAllTestsButton;
    private Button runQuestionTestButton;
    private Button runEvaluationTestButton;
    private Button runPerformanceTestButton;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        Log.d(TAG, "Starting Enhanced MMSE Test Activity");
        
        // Create simple layout programmatically
        createTestLayout();
        
        // Initialize tester
        tester = new EnhancedMMSETester(this);
        
        // Setup button listeners
        setupButtonListeners();
    }
    
    private void createTestLayout() {
        // Create main layout
        android.widget.LinearLayout mainLayout = new android.widget.LinearLayout(this);
        mainLayout.setOrientation(android.widget.LinearLayout.VERTICAL);
        mainLayout.setPadding(32, 32, 32, 32);
        
        // Title
        TextView titleText = new TextView(this);
        titleText.setText("Enhanced MMSE Testing Suite");
        titleText.setTextSize(24);
        titleText.setTextColor(getResources().getColor(android.R.color.black));
        titleText.setPadding(0, 0, 0, 24);
        mainLayout.addView(titleText);
        
        // Description
        TextView descriptionText = new TextView(this);
        descriptionText.setText("This testing suite validates AI-powered MMSE functionality:\n" +
                               "• Question generation quality (40/30/30 distribution)\n" +
                               "• Answer evaluation accuracy (synonyms, partial credit)\n" +
                               "• Performance and error handling\n\n" +
                               "Check logcat output for detailed test results.");
        descriptionText.setTextSize(14);
        descriptionText.setTextColor(getResources().getColor(android.R.color.darker_gray));
        descriptionText.setPadding(0, 0, 0, 32);
        mainLayout.addView(descriptionText);
        
        // Test buttons
        runAllTestsButton = createTestButton("Run All Tests", android.R.color.holo_blue_bright);
        mainLayout.addView(runAllTestsButton);
        
        runQuestionTestButton = createTestButton("Test Question Generation", android.R.color.holo_green_light);
        mainLayout.addView(runQuestionTestButton);
        
        runEvaluationTestButton = createTestButton("Test Answer Evaluation", android.R.color.holo_orange_light);
        mainLayout.addView(runEvaluationTestButton);
        
        runPerformanceTestButton = createTestButton("Test Performance & Error Handling", android.R.color.holo_red_light);
        mainLayout.addView(runPerformanceTestButton);
        
        // Results section
        TextView resultsTitle = new TextView(this);
        resultsTitle.setText("Test Results (see logcat for details):");
        resultsTitle.setTextSize(16);
        resultsTitle.setTextColor(getResources().getColor(android.R.color.black));
        resultsTitle.setPadding(0, 32, 0, 16);
        mainLayout.addView(resultsTitle);
        
        // Scrollable results text
        testResultsText = new TextView(this);
        testResultsText.setText("No tests run yet. Click a button above to start testing.\n\n" +
                                "Monitor logcat for detailed output:\n" +
                                "adb logcat | grep 'EnhancedMMSE'");
        testResultsText.setTextSize(12);
        testResultsText.setTextColor(getResources().getColor(android.R.color.black));
        testResultsText.setBackgroundColor(getResources().getColor(android.R.color.background_light));
        testResultsText.setPadding(16, 16, 16, 16);
        
        scrollView = new ScrollView(this);
        scrollView.addView(testResultsText);
        scrollView.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 400));
        mainLayout.addView(scrollView);
        
        // Back to main button
        Button backButton = createTestButton("Back to Main Menu", android.R.color.darker_gray);
        backButton.setOnClickListener(v -> {
            Log.d(TAG, "Returning to main menu");
            finish();
        });
        mainLayout.addView(backButton);
        
        setContentView(mainLayout);
    }
    
    private Button createTestButton(String text, int colorRes) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(getResources().getColor(android.R.color.white));
        button.setBackgroundColor(getResources().getColor(colorRes));
        
        android.widget.LinearLayout.LayoutParams params = new android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 8, 0, 8);
        button.setLayoutParams(params);
        
        return button;
    }
    
    private void setupButtonListeners() {
        runAllTestsButton.setOnClickListener(v -> {
            Log.d(TAG, "Starting comprehensive test suite");
            updateResults("🚀 Starting comprehensive test suite...\n" +
                         "This will run all tests in sequence.\n" +
                         "Check logcat for detailed progress.\n\n");
            
            tester.runAllTests();
            
            updateResults("📊 Test suite initiated.\n" +
                         "Monitor logcat with: adb logcat | grep 'EnhancedMMSE'\n\n" +
                         "Expected output:\n" +
                         "• Question Generation Quality Test\n" +
                         "• Answer Evaluation Accuracy Test\n" +
                         "• Performance and Error Handling Test\n");
        });
        
        runQuestionTestButton.setOnClickListener(v -> {
            Log.d(TAG, "Starting question generation test");
            updateResults("🎯 Testing question generation quality...\n" +
                         "This test validates:\n" +
                         "• 40% memory-based questions\n" +
                         "• 30% profile-based questions\n" +
                         "• 30% standard questions\n" +
                         "• Indian cultural context\n\n");
            
            tester.testQuestionGenerationQuality();
        });
        
        runEvaluationTestButton.setOnClickListener(v -> {
            Log.d(TAG, "Starting answer evaluation test");
            updateResults("🎯 Testing answer evaluation accuracy...\n" +
                         "This test validates:\n" +
                         "• Exact answer matching\n" +
                         "• Synonym recognition\n" +
                         "• Partial credit system\n" +
                         "• Cultural context understanding\n\n");
            
            tester.testAnswerEvaluationAccuracy();
        });
        
        runPerformanceTestButton.setOnClickListener(v -> {
            Log.d(TAG, "Starting performance and error handling test");
            updateResults("⚡ Testing performance and error handling...\n" +
                         "This test validates:\n" +
                         "• Response time performance\n" +
                         "• Network error handling\n" +
                         "• Fallback mechanisms\n" +
                         "• Graceful degradation\n\n");
            
            tester.testPerformanceAndErrorHandling();
        });
    }
    
    private void updateResults(String newText) {
        runOnUiThread(() -> {
            String currentText = testResultsText.getText().toString();
            testResultsText.setText(currentText + newText);
            
            // Scroll to bottom
            scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
        });
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Enhanced MMSE Test Activity destroyed");
    }
}