package com.mihir.alzheimerscaregiver.auth;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.mihir.alzheimerscaregiver.MainActivity;
import com.mihir.alzheimerscaregiver.R;

public class AuthenticationActivity extends AppCompatActivity {
    
    private static final String TAG = "AuthenticationActivity";
    
    private EditText emailEditText, passwordEditText, nameEditText;
    private Button signUpButton, signInButton, toggleModeButton, googleSignInButton;
    private TextView titleTextView, subtitleTextView;
    private ProgressBar progressBar;
    
    private boolean isSignUpMode = true;
    private FirebaseAuthManager authManager;
    private GoogleSignInClient googleSignInClient;
    private ActivityResultLauncher<Intent> googleSignInLauncher;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_authentication);
        
        // Initialize Firebase Auth Manager with context for OAuth
        authManager = new FirebaseAuthManager(this);
        googleSignInClient = authManager.getGoogleSignInClient();
        
        // Initialize Google Sign-In launcher
        googleSignInLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                Intent data = result.getData();
                Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
                handleGoogleSignInResult(task);
            }
        );
        
        // Check if user is already signed in
        if (authManager.isPatientSignedIn()) {
            navigateToMain();
            return;
        }
        
        initializeViews();
        setupClickListeners();
        updateUI();
    }
    
    private void initializeViews() {
        emailEditText = findViewById(R.id.emailEditText);
        passwordEditText = findViewById(R.id.passwordEditText);
        nameEditText = findViewById(R.id.nameEditText);
        signUpButton = findViewById(R.id.signUpButton);
        signInButton = findViewById(R.id.signInButton);
        googleSignInButton = findViewById(R.id.googleSignInButton);
        toggleModeButton = findViewById(R.id.toggleModeButton);
        titleTextView = findViewById(R.id.titleTextView);
        subtitleTextView = findViewById(R.id.subtitleTextView);
        progressBar = findViewById(R.id.progressBar);
    }
    
    private void setupClickListeners() {
        signUpButton.setOnClickListener(v -> handleSignUp());
        signInButton.setOnClickListener(v -> handleSignIn());
        googleSignInButton.setOnClickListener(v -> handleGoogleSignIn());
        toggleModeButton.setOnClickListener(v -> toggleMode());
    }
    
    private void toggleMode() {
        isSignUpMode = !isSignUpMode;
        updateUI();
    }
    
    private void updateUI() {
        if (isSignUpMode) {
            titleTextView.setText("Create Patient Account");
            subtitleTextView.setText("Sign up to get started");
            nameEditText.setVisibility(View.VISIBLE);
            signUpButton.setVisibility(View.VISIBLE);
            signInButton.setVisibility(View.GONE);
            toggleModeButton.setText("Already have an account? Sign In");
        } else {
            titleTextView.setText("Sign In");
            subtitleTextView.setText("Welcome back!");
            nameEditText.setVisibility(View.GONE);
            signUpButton.setVisibility(View.GONE);
            signInButton.setVisibility(View.VISIBLE);
            toggleModeButton.setText("Don't have an account? Sign Up");
        }
    }
    
    private void handleSignUp() {
        String email = emailEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString().trim();
        String name = nameEditText.getText().toString().trim();
        
        if (!validateInputs(email, password, name)) {
            return;
        }
        
        showProgress(true);
        
        authManager.signUpPatient(email, password, name, new FirebaseAuthManager.AuthCallback() {
            @Override
            public void onSuccess(String patientId) {
                showProgress(false);
                Toast.makeText(AuthenticationActivity.this, 
                        "Account created successfully! Patient ID: " + patientId, Toast.LENGTH_LONG).show();
                navigateToMain();
            }
            
            @Override
            public void onError(String error) {
                showProgress(false);
                Toast.makeText(AuthenticationActivity.this, "Sign up failed: " + error, Toast.LENGTH_LONG).show();
            }
        });
    }
    
    private void handleSignIn() {
        String email = emailEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString().trim();
        
        if (!validateInputs(email, password, null)) {
            return;
        }
        
        showProgress(true);
        
        authManager.signInPatient(email, password, new FirebaseAuthManager.AuthCallback() {
            @Override
            public void onSuccess(String patientId) {
                showProgress(false);
                Toast.makeText(AuthenticationActivity.this, 
                        "Welcome back! Patient ID: " + patientId, Toast.LENGTH_LONG).show();
                navigateToMain();
            }
            
            @Override
            public void onError(String error) {
                showProgress(false);
                Toast.makeText(AuthenticationActivity.this, "Sign in failed: " + error, Toast.LENGTH_LONG).show();
            }
        });
    }
    
    private boolean validateInputs(String email, String password, String name) {
        if (TextUtils.isEmpty(email)) {
            emailEditText.setError("Email is required");
            return false;
        }
        
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailEditText.setError("Enter a valid email address");
            return false;
        }
        
        if (TextUtils.isEmpty(password)) {
            passwordEditText.setError("Password is required");
            return false;
        }
        
        if (password.length() < 6) {
            passwordEditText.setError("Password must be at least 6 characters");
            return false;
        }
        
        if (isSignUpMode && TextUtils.isEmpty(name)) {
            nameEditText.setError("Name is required");
            return false;
        }
        
        return true;
    }
    
    private void showProgress(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        signUpButton.setEnabled(!show);
        signInButton.setEnabled(!show);
        googleSignInButton.setEnabled(!show);
        toggleModeButton.setEnabled(!show);
    }
    
    /**
     * Handle Google Sign-In button click
     */
    private void handleGoogleSignIn() {
        if (googleSignInClient == null) {
            Toast.makeText(this, "Google Sign-In not configured", Toast.LENGTH_SHORT).show();
            return;
        }
        
        showProgress(true);
        Intent signInIntent = googleSignInClient.getSignInIntent();
        googleSignInLauncher.launch(signInIntent);
    }
    
    /**
     * Handle Google Sign-In result
     */
    private void handleGoogleSignInResult(Task<GoogleSignInAccount> completedTask) {
        try {
            GoogleSignInAccount account = completedTask.getResult(ApiException.class);
            Log.d(TAG, "Google sign-in successful: " + account.getEmail());
            
            // Sign in with Firebase using Google credentials
            authManager.signInWithGoogle(account, new FirebaseAuthManager.AuthCallback() {
                @Override
                public void onSuccess(String patientId) {
                    showProgress(false);
                    Log.d(TAG, "Firebase authentication successful for patient: " + patientId);
                    Toast.makeText(AuthenticationActivity.this, 
                            "Welcome! Signed in with Google", Toast.LENGTH_LONG).show();
                    navigateToMain();
                }
                
                @Override
                public void onError(String error) {
                    showProgress(false);
                    Log.e(TAG, "Firebase authentication failed: " + error);
                    
                    if (error.contains("account with this email already exists")) {
                        showAccountCollisionDialog(account, error);
                    } else {
                        Toast.makeText(AuthenticationActivity.this, 
                                "Google sign-in failed: " + error, Toast.LENGTH_LONG).show();
                    }
                }
            });
            
        } catch (ApiException e) {
            showProgress(false);
            Log.w(TAG, "Google sign-in failed", e);
            Toast.makeText(this, "Google sign-in failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * Show dialog when account collision occurs
     */
    private void showAccountCollisionDialog(GoogleSignInAccount account, String error) {
        new AlertDialog.Builder(this)
            .setTitle("Account Already Exists")
            .setMessage("An account with this email already exists. Would you like to link your Google account to your existing account?")
            .setPositiveButton("Link Account", (dialog, which) -> {
                // For linking, user needs to sign in with email/password first
                Toast.makeText(this, "Please sign in with your email and password first, then you can link Google in settings.", Toast.LENGTH_LONG).show();
            })
            .setNegativeButton("Cancel", (dialog, which) -> {
                // Sign out from Google to avoid cached account
                if (googleSignInClient != null) {
                    googleSignInClient.signOut();
                }
            })
            .show();
    }
    
    private void navigateToMain() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
