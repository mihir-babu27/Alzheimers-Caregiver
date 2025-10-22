package com.mihir.alzheimerscaregiver.caretaker.auth;

import android.app.AlertDialog;
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
import com.google.firebase.auth.FirebaseUser;
import com.mihir.alzheimerscaregiver.caretaker.PatientLinkActivity;
import com.mihir.alzheimerscaregiver.caretaker.R;

public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "LoginActivity";
    
    private EditText emailEditText, passwordEditText;
    private Button loginButton, googleSignInButton;
    private TextView registerLink, forgotPasswordLink;
    private ProgressBar progressBar;
    private FirebaseAuth mAuth;
    private SessionManager sessionManager;
    private FirebaseAuthManager authManager;
    
    // Activity Result Launcher for Google Sign-In
    private ActivityResultLauncher<Intent> googleSignInLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        Log.d(TAG, "LoginActivity created");

        // Initialize Firebase Auth, SessionManager, and AuthManager
        mAuth = FirebaseAuth.getInstance();
        sessionManager = new SessionManager(this);
        authManager = new FirebaseAuthManager(this);
        
        // Initialize Google Sign-In result launcher
        initializeGoogleSignInLauncher();
        
        // Check if user is already authenticated (shouldn't happen with SplashActivity, but safety check)
        if (sessionManager.isUserAuthenticated()) {
            Log.d(TAG, "User already authenticated, redirecting to patient link");
            goToPatientLink();
            return;
        }

        // Initialize views
        emailEditText = findViewById(R.id.emailEditText);
        passwordEditText = findViewById(R.id.passwordEditText);
        loginButton = findViewById(R.id.loginButton);
        googleSignInButton = findViewById(R.id.googleSignInButton);
        registerLink = findViewById(R.id.registerLink);
        forgotPasswordLink = findViewById(R.id.forgotPasswordLink);
        progressBar = findViewById(R.id.progressBar);

        // Set click listeners
        loginButton.setOnClickListener(v -> attemptLogin());
        googleSignInButton.setOnClickListener(v -> attemptGoogleSignIn());
        registerLink.setOnClickListener(v -> goToRegister());
        forgotPasswordLink.setOnClickListener(v -> handleForgotPassword());
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
                            handleGoogleSignInResult(account);
                        } catch (ApiException e) {
                            Log.w(TAG, "Google sign-in failed", e);
                            hideProgress();
                            Toast.makeText(this, "Google sign-in failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    } else {
                        hideProgress();
                        Toast.makeText(this, "Google sign-in cancelled", Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    private void attemptLogin() {
        String email = emailEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString().trim();

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

        // Show progress and use new auth manager
        showProgress();
        
        authManager.signInWithEmailAndPassword(email, password, new FirebaseAuthManager.AuthCallback() {
            @Override
            public void onSuccess(String userId) {
                Log.d(TAG, "Email login successful for user: " + userId);
                hideProgress();
                goToPatientLink();
            }

            @Override
            public void onError(String error) {
                Log.w(TAG, "Email login failed: " + error);
                hideProgress();
                Toast.makeText(LoginActivity.this, error, Toast.LENGTH_LONG).show();
            }

            @Override
            public void onAccountExists(String email, String existingProvider) {
                hideProgress();
                showAccountExistsDialog(email, existingProvider);
            }
        });
    }
    
    /**
     * Attempt Google Sign-In
     */
    private void attemptGoogleSignIn() {
        if (authManager.getGoogleSignInClient() == null) {
            Toast.makeText(this, "Google Sign-In not configured. Please check your setup.", Toast.LENGTH_LONG).show();
            return;
        }
        
        showProgress();
        Intent signInIntent = authManager.getGoogleSignInClient().getSignInIntent();
        googleSignInLauncher.launch(signInIntent);
    }
    
    /**
     * Handle Google Sign-In result
     */
    private void handleGoogleSignInResult(GoogleSignInAccount account) {
        Log.d(TAG, "Handling Google sign-in result for: " + account.getEmail());
        
        authManager.signInWithGoogle(account, new FirebaseAuthManager.AuthCallback() {
            @Override
            public void onSuccess(String userId) {
                Log.d(TAG, "Google sign-in successful for user: " + userId);
                hideProgress();
                goToPatientLink();
            }

            @Override
            public void onError(String error) {
                Log.w(TAG, "Google sign-in failed: " + error);
                hideProgress();
                Toast.makeText(LoginActivity.this, error, Toast.LENGTH_LONG).show();
            }

            @Override
            public void onAccountExists(String email, String existingProvider) {
                hideProgress();
                showAccountExistsDialog(email, existingProvider, account);
            }
        });
    }

    private void goToRegister() {
        Intent intent = new Intent(LoginActivity.this, RegisterActivity.class);
        startActivity(intent);
    }

    private void goToPatientLink() {
        Intent intent = new Intent(LoginActivity.this, PatientLinkActivity.class);
        startActivity(intent);
        finish();
    }

    /**
     * Show account exists dialog when trying to sign in with a provider that conflicts
     */
    private void showAccountExistsDialog(String email, String existingProvider) {
        showAccountExistsDialog(email, existingProvider, null);
    }
    
    /**
     * Show account exists dialog with option to link Google account
     */
    private void showAccountExistsDialog(String email, String existingProvider, GoogleSignInAccount googleAccount) {
        String providerName = getProviderDisplayName(existingProvider);
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Account Already Exists");
        builder.setMessage("An account with email " + email + " already exists using " + providerName + 
                          ". Would you like to sign in with " + providerName + " and link your Google account?");
        
        builder.setPositiveButton("Sign in with " + providerName, (dialog, which) -> {
            if (existingProvider.contains("password")) {
                // Show email/password login for linking
                showLinkAccountDialog(email, googleAccount);
            } else {
                Toast.makeText(this, "Please sign in with " + providerName + " first", Toast.LENGTH_LONG).show();
            }
        });
        
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());
        builder.show();
    }
    
    /**
     * Show dialog to enter password for account linking
     */
    private void showLinkAccountDialog(String email, GoogleSignInAccount googleAccount) {
        if (googleAccount == null) return;
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Link Accounts");
        
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_link_account, null);
        EditText passwordEditText = dialogView.findViewById(R.id.passwordEditText);
        
        builder.setView(dialogView);
        builder.setMessage("Enter your password for " + email + " to link with Google:");
        
        builder.setPositiveButton("Link", (dialog, which) -> {
            String password = passwordEditText.getText().toString().trim();
            if (!TextUtils.isEmpty(password)) {
                linkAccountWithPassword(email, password, googleAccount);
            } else {
                Toast.makeText(this, "Password is required", Toast.LENGTH_SHORT).show();
            }
        });
        
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());
        builder.show();
    }
    
    /**
     * Link Google account after signing in with email/password
     */
    private void linkAccountWithPassword(String email, String password, GoogleSignInAccount googleAccount) {
        showProgress();
        
        // First sign in with email/password
        authManager.signInWithEmailAndPassword(email, password, new FirebaseAuthManager.AuthCallback() {
            @Override
            public void onSuccess(String userId) {
                // Now link the Google credential
                authManager.linkGoogleCredential(googleAccount, new FirebaseAuthManager.AuthCallback() {
                    @Override
                    public void onSuccess(String linkedUserId) {
                        hideProgress();
                        Toast.makeText(LoginActivity.this, "Accounts linked successfully!", Toast.LENGTH_SHORT).show();
                        goToPatientLink();
                    }

                    @Override
                    public void onError(String error) {
                        hideProgress();
                        Toast.makeText(LoginActivity.this, "Failed to link accounts: " + error, Toast.LENGTH_LONG).show();
                    }

                    @Override
                    public void onAccountExists(String email, String existingProvider) {
                        // This shouldn't happen in linking flow
                        hideProgress();
                        Toast.makeText(LoginActivity.this, "Unexpected error during linking", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onError(String error) {
                hideProgress();
                Toast.makeText(LoginActivity.this, "Failed to sign in: " + error, Toast.LENGTH_LONG).show();
            }

            @Override
            public void onAccountExists(String email, String existingProvider) {
                // This shouldn't happen since we're signing in with known credentials
                hideProgress();
                Toast.makeText(LoginActivity.this, "Unexpected error during sign in", Toast.LENGTH_SHORT).show();
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
        loginButton.setEnabled(false);
        googleSignInButton.setEnabled(false);
    }
    
    /**
     * Hide loading progress
     */
    private void hideProgress() {
        progressBar.setVisibility(View.GONE);
        loginButton.setEnabled(true);
        googleSignInButton.setEnabled(true);
    }

    private void handleForgotPassword() {
        // TODO: Implement forgot password functionality
        Toast.makeText(this, "Forgot password functionality coming soon", Toast.LENGTH_SHORT).show();
    }
}
