package com.mihir.alzheimerscaregiver;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.mihir.alzheimerscaregiver.auth.FirebaseAuthManager;

public class EmailVerificationActivity extends AppCompatActivity {

    private TextView statusText;
    private View checkButton;
    private View resendButton;
    private View signInButton;
    private View progressBar;
    
    private FirebaseAuthManager authManager;
    private String emailToVerify;
    
    // Handler for auto-refreshing verification status
    private final Handler refreshHandler = new Handler(Looper.getMainLooper());
    private final Runnable refreshRunnable = this::checkVerificationStatus;
    private static final long REFRESH_INTERVAL_MS = 10000; // 10 seconds
    
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_email_verification);
        
        authManager = new FirebaseAuthManager();
        
        // Get email from intent
        emailToVerify = getIntent().getStringExtra("EMAIL_TO_VERIFY");
        if (emailToVerify == null) {
            // If no email provided, use current user's email
            if (authManager.getCurrentUser() != null) {
                emailToVerify = authManager.getCurrentUser().getEmail();
            } else {
                // No user signed in, go back to sign in screen
                navigateToSignIn();
                return;
            }
        }
        
        statusText = findViewById(R.id.statusText);
        checkButton = findViewById(R.id.checkButton);
        resendButton = findViewById(R.id.resendButton);
        signInButton = findViewById(R.id.signInButton);
        progressBar = findViewById(android.R.id.progress);
        
        // Set initial status text
        statusText.setText(getString(R.string.verification_pending_message, emailToVerify));
        
        // Set up button listeners
        checkButton.setOnClickListener(v -> checkVerificationStatus());
        resendButton.setOnClickListener(v -> resendVerificationEmail());
        signInButton.setOnClickListener(v -> navigateToSignIn());
        
        // Check status when activity opens
        checkVerificationStatus();
        
        // Start automatic refresh
        startAutoRefresh();
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        stopAutoRefresh();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        checkVerificationStatus();
        startAutoRefresh();
    }
    
    private void checkVerificationStatus() {
        showProgress(true);
        
        authManager.checkEmailVerificationStatus(emailToVerify, (isVerified, message) -> {
            showProgress(false);
            
            if (isVerified) {
                // Email is verified, update UI and stop auto-refresh
                statusText.setText(R.string.verification_success_message);
                stopAutoRefresh();
                
                // Enable the sign in button
                checkButton.setEnabled(false);
                resendButton.setEnabled(false);
                signInButton.setEnabled(true);
                
                // Show success message
                showSnackbar(message);
            } else {
                // Email not yet verified, keep waiting
                statusText.setText(getString(R.string.verification_pending_message, emailToVerify));
                showSnackbar(message);
            }
        });
    }
    
    private void resendVerificationEmail() {
        showProgress(true);
        
        // Sign out current user to get a fresh session
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() != null) {
            auth.getCurrentUser().sendEmailVerification()
                .addOnSuccessListener(aVoid -> {
                    showProgress(false);
                    showSnackbar("Verification email sent again to " + emailToVerify);
                })
                .addOnFailureListener(e -> {
                    showProgress(false);
                    showSnackbar("Failed to resend: " + e.getMessage());
                });
        } else {
            showProgress(false);
            showSnackbar("Please sign in again to resend verification email");
            navigateToSignIn();
        }
    }
    
    private void navigateToSignIn() {
        // Stop auto refresh
        stopAutoRefresh();
        
        // Sign out current user
        authManager.signOut();
        
        // Navigate to authentication activity
        Intent intent = new Intent(this, com.mihir.alzheimerscaregiver.auth.AuthenticationActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
    
    private void showProgress(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        checkButton.setEnabled(!show);
        resendButton.setEnabled(!show);
        signInButton.setEnabled(!show);
    }
    
    private void showSnackbar(String message) {
        View root = findViewById(R.id.verificationCard);
        if (root != null) {
            Snackbar.make(root, message, Snackbar.LENGTH_LONG).show();
        }
    }
    
    private void startAutoRefresh() {
        stopAutoRefresh(); // Clear any existing callbacks
        refreshHandler.postDelayed(refreshRunnable, REFRESH_INTERVAL_MS);
    }
    
    private void stopAutoRefresh() {
        refreshHandler.removeCallbacks(refreshRunnable);
    }
}