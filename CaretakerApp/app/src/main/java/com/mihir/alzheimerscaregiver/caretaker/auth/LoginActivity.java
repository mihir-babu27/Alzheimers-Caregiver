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

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

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
    private Button loginButton;
    private TextView registerLink, forgotPasswordLink;
    private ProgressBar progressBar;
    private FirebaseAuth mAuth;
    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        Log.d(TAG, "LoginActivity created");

        // Initialize Firebase Auth and SessionManager
        mAuth = FirebaseAuth.getInstance();
        sessionManager = new SessionManager(this);
        
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
        registerLink = findViewById(R.id.registerLink);
        forgotPasswordLink = findViewById(R.id.forgotPasswordLink);
        progressBar = findViewById(R.id.progressBar);

        // Set click listeners
        loginButton.setOnClickListener(v -> attemptLogin());
        registerLink.setOnClickListener(v -> goToRegister());
        forgotPasswordLink.setOnClickListener(v -> handleForgotPassword());
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

        // Show progress
        progressBar.setVisibility(View.VISIBLE);
        loginButton.setEnabled(false);

        // Attempt to sign in
        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        progressBar.setVisibility(View.GONE);
                        loginButton.setEnabled(true);

                        if (task.isSuccessful()) {
                            FirebaseUser user = mAuth.getCurrentUser();
                            if (user != null) {
                                Log.d(TAG, "Login successful for user: " + user.getEmail());
                                
                                // Session will be automatically managed by Firebase Auth persistence
                                // Go to patient link activity
                                goToPatientLink();
                            } else {
                                Log.e(TAG, "Login successful but user is null");
                                Toast.makeText(LoginActivity.this, "Login error occurred", Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            // If sign in fails, display a message to the user.
                            String errorMessage = "Authentication failed";
                            if (task.getException() != null) {
                                errorMessage += ": " + task.getException().getMessage();
                            }
                            Log.w(TAG, errorMessage);
                            Toast.makeText(LoginActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                        }
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

    private void handleForgotPassword() {
        // TODO: Implement forgot password functionality
        Toast.makeText(this, "Forgot password functionality coming soon", Toast.LENGTH_SHORT).show();
    }
}
