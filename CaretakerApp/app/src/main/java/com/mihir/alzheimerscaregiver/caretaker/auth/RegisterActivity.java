package com.mihir.alzheimerscaregiver.caretaker.auth;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.mihir.alzheimerscaregiver.caretaker.PatientLinkActivity;
import com.mihir.alzheimerscaregiver.caretaker.R;

public class RegisterActivity extends AppCompatActivity {

    private static final String TAG = "RegisterActivity";
    
    private EditText emailEditText, passwordEditText, confirmPasswordEditText;
    private Button registerButton, googleSignUpButton;
    private TextView loginLink;
    private ProgressBar progressBar;
    private FirebaseAuth mAuth;
    private FirebaseAuthManager authManager;
    
    // Activity Result Launcher for Google Sign-In
    private ActivityResultLauncher<Intent> googleSignInLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        Log.d(TAG, "RegisterActivity created");

        // Initialize Firebase Auth and AuthManager
        mAuth = FirebaseAuth.getInstance();
        authManager = new FirebaseAuthManager(this);
        
        // Initialize Google Sign-In result launcher
        initializeGoogleSignInLauncher();

        // Initialize views
        emailEditText = findViewById(R.id.emailEditText);
        passwordEditText = findViewById(R.id.passwordEditText);
        confirmPasswordEditText = findViewById(R.id.confirmPasswordEditText);
        registerButton = findViewById(R.id.registerButton);
        googleSignUpButton = findViewById(R.id.googleSignUpButton);
        loginLink = findViewById(R.id.loginLink);
        progressBar = findViewById(R.id.progressBar);

        // Set click listeners
        registerButton.setOnClickListener(v -> attemptRegister());
        googleSignUpButton.setOnClickListener(v -> attemptGoogleSignUp());
        loginLink.setOnClickListener(v -> goToLogin());
    }

    /**
     * Initialize Google Sign-In result launcher
     */
    private void initializeGoogleSignInLauncher() {
        googleSignInLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(result.getData());
                        try {
                            GoogleSignInAccount account = task.getResult(ApiException.class);
                            handleGoogleSignUpResult(account);
                        } catch (ApiException e) {
                            Log.w(TAG, "Google sign-up failed", e);
                            hideProgress();
                            Toast.makeText(this, "Google sign-up failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    } else {
                        hideProgress();
                        Toast.makeText(this, "Google sign-up cancelled", Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    private void attemptRegister() {
        String email = emailEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString().trim();
        String confirmPassword = confirmPasswordEditText.getText().toString().trim();

        // Validate input
        if (TextUtils.isEmpty(email)) {
            emailEditText.setError("Email is required");
            emailEditText.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(password)) {
            passwordEditText.setError("Password is required");
            passwordEditText.requestFocus();
            return;
        }

        if (password.length() < 6) {
            passwordEditText.setError("Password must be at least 6 characters");
            passwordEditText.requestFocus();
            return;
        }

        if (!password.equals(confirmPassword)) {
            confirmPasswordEditText.setError("Passwords do not match");
            confirmPasswordEditText.requestFocus();
            return;
        }

        // Show progress and use new auth manager
        showProgress();

        authManager.createUserWithEmailAndPassword(email, password, new FirebaseAuthManager.AuthCallback() {
            @Override
            public void onSuccess(String userId) {
                Log.d(TAG, "Email registration successful for user: " + userId);
                hideProgress();
                Toast.makeText(RegisterActivity.this, "Registration successful!", Toast.LENGTH_SHORT).show();
                goToPatientLink();
            }

            @Override
            public void onError(String error) {
                Log.w(TAG, "Email registration failed: " + error);
                hideProgress();
                Toast.makeText(RegisterActivity.this, "Registration failed: " + error, Toast.LENGTH_LONG).show();
            }

            @Override
            public void onAccountExists(String email, String existingProvider) {
                hideProgress();
                String providerName = getProviderDisplayName(existingProvider);
                Toast.makeText(RegisterActivity.this, 
                    "Account already exists with " + providerName + ". Please sign in instead.", 
                    Toast.LENGTH_LONG).show();
            }
        });
    }
    
    /**
     * Attempt Google Sign-Up
     */
    private void attemptGoogleSignUp() {
        if (authManager.getGoogleSignInClient() == null) {
            Toast.makeText(this, "Google Sign-In not configured. Please check your setup.", Toast.LENGTH_LONG).show();
            return;
        }
        
        showProgress();
        Intent signInIntent = authManager.getGoogleSignInClient().getSignInIntent();
        googleSignInLauncher.launch(signInIntent);
    }
    
    /**
     * Handle Google Sign-Up result
     */
    private void handleGoogleSignUpResult(GoogleSignInAccount account) {
        Log.d(TAG, "Handling Google sign-up result for: " + account.getEmail());
        
        authManager.signInWithGoogle(account, new FirebaseAuthManager.AuthCallback() {
            @Override
            public void onSuccess(String userId) {
                Log.d(TAG, "Google sign-up successful for user: " + userId);
                hideProgress();
                Toast.makeText(RegisterActivity.this, "Registration successful!", Toast.LENGTH_SHORT).show();
                goToPatientLink();
            }

            @Override
            public void onError(String error) {
                Log.w(TAG, "Google sign-up failed: " + error);
                hideProgress();
                Toast.makeText(RegisterActivity.this, "Registration failed: " + error, Toast.LENGTH_LONG).show();
            }

            @Override
            public void onAccountExists(String email, String existingProvider) {
                hideProgress();
                String providerName = getProviderDisplayName(existingProvider);
                Toast.makeText(RegisterActivity.this, 
                    "Account already exists with " + providerName + ". Please sign in instead.", 
                    Toast.LENGTH_LONG).show();
            }
        });
    }

    /**
     * Get display name for provider
     */
    private String getProviderDisplayName(String provider) {
        if (provider.contains("password")) {
            return "Email/Password";
        } else if (provider.contains("google")) {
            return "Google";
        } else if (provider.contains("facebook")) {
            return "Facebook";
        } else {
            return "existing method";
        }
    }
    
    /**
     * Show loading progress
     */
    private void showProgress() {
        progressBar.setVisibility(View.VISIBLE);
        registerButton.setEnabled(false);
        googleSignUpButton.setEnabled(false);
    }
    
    /**
     * Hide loading progress
     */
    private void hideProgress() {
        progressBar.setVisibility(View.GONE);
        registerButton.setEnabled(true);
        googleSignUpButton.setEnabled(true);
    }

    private void goToLogin() {
        Intent intent = new Intent(RegisterActivity.this, LoginActivity.class);
        startActivity(intent);
        finish();
    }

    private void goToPatientLink() {
        Intent intent = new Intent(RegisterActivity.this, PatientLinkActivity.class);
        startActivity(intent);
        finish();
    }
}
