package com.mihir.alzheimerscaregiver.caretaker;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseUser;
import com.mihir.alzheimerscaregiver.caretaker.R;
import com.mihir.alzheimerscaregiver.caretaker.auth.LoginActivity;
import com.mihir.alzheimerscaregiver.caretaker.auth.SessionManager;

/**
 * SplashActivity serves as the entry point to the CaretakerApp.
 * It checks authentication state asynchronously and routes users to
 * the appropriate screen based on their session status.
 * 
 * Features:
 * - Lightweight splash screen with app branding
 * - Asynchronous authentication state checking
 * - Graceful error handling
 * - Session validation with Firebase
 * - Clean navigation flow
 */
public class SplashActivity extends AppCompatActivity {
    
    private static final String TAG = "SplashActivity";
    private static final long MIN_SPLASH_DURATION = 1000; // Minimum 1 second splash
    
    private SessionManager sessionManager;
    private ProgressBar progressBar;
    private TextView statusText;
    private long startTime;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
        
        Log.d(TAG, "SplashActivity created");
        startTime = System.currentTimeMillis();
        
        // Initialize views
        initializeViews();
        
        // Initialize SessionManager
        sessionManager = new SessionManager(this);
        
        // Start authentication check
        checkAuthenticationState();
    }
    
    private void initializeViews() {
        progressBar = findViewById(R.id.progressBar);
        statusText = findViewById(R.id.statusText);
        
        // Show initial loading state
        progressBar.setVisibility(View.VISIBLE);
        statusText.setText("Initializing...");
    }
    
    /**
     * Check authentication state asynchronously
     */
    private void checkAuthenticationState() {
        Log.d(TAG, "Checking authentication state...");
        updateStatus("Checking authentication...");
        
        // Run authentication check in background to avoid blocking main thread
        new Handler(Looper.getMainLooper()).post(() -> {
            if (sessionManager.isUserAuthenticated()) {
                Log.d(TAG, "User is authenticated, validating session...");
                updateStatus("Validating session...");
                validateAndProceed();
            } else {
                Log.d(TAG, "User is not authenticated");
                proceedToLogin();
            }
        });
    }
    
    /**
     * Validate the current session with Firebase
     */
    private void validateAndProceed() {
        sessionManager.validateSession(new SessionManager.SessionValidationCallback() {
            @Override
            public void onSessionValid(@NonNull FirebaseUser user) {
                Log.d(TAG, "Session is valid for user: " + user.getEmail());
                updateStatus("Session validated");
                proceedToMain();
            }
            
            @Override
            public void onSessionInvalid() {
                Log.w(TAG, "Session is invalid or expired");
                updateStatus("Session expired");
                
                // Sign out to clean up any invalid state
                sessionManager.signOut();
                proceedToLogin();
            }
        });
    }
    
    /**
     * Navigate to MainActivity (user is authenticated)
     */
    private void proceedToMain() {
        Log.d(TAG, "Proceeding to MainActivity");
        
        // Ensure minimum splash duration for better UX
        long elapsedTime = System.currentTimeMillis() - startTime;
        long remainingTime = MIN_SPLASH_DURATION - elapsedTime;
        
        if (remainingTime > 0) {
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                navigateToMain();
            }, remainingTime);
        } else {
            navigateToMain();
        }
    }
    
    /**
     * Navigate to LoginActivity (user is not authenticated)
     */
    private void proceedToLogin() {
        Log.d(TAG, "Proceeding to LoginActivity");
        
        // Ensure minimum splash duration for better UX
        long elapsedTime = System.currentTimeMillis() - startTime;
        long remainingTime = MIN_SPLASH_DURATION - elapsedTime;
        
        if (remainingTime > 0) {
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                navigateToLogin();
            }, remainingTime);
        } else {
            navigateToLogin();
        }
    }
    
    /**
     * Navigate to MainActivity and finish splash
     */
    private void navigateToMain() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
    
    /**
     * Navigate to LoginActivity and finish splash
     */
    private void navigateToLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
    
    /**
     * Update status text for user feedback
     */
    private void updateStatus(String status) {
        runOnUiThread(() -> {
            if (statusText != null) {
                statusText.setText(status);
            }
        });
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "SplashActivity destroyed");
    }
    
    @Override
    public void onBackPressed() {
        // Disable back button during splash to prevent navigation issues
        // Users should not be able to go back from splash screen
    }
}