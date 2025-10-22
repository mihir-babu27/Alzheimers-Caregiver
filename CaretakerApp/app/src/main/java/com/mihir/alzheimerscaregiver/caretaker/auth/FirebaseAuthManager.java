package com.mihir.alzheimerscaregiver.caretaker.auth;

import android.content.Context;
import android.util.Log;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.mihir.alzheimerscaregiver.caretaker.R;
import com.mihir.alzheimerscaregiver.firebase.UserManager;

/**
 * FirebaseAuthManager - Centralized authentication management for CaretakerApp
 * 
 * Handles:
 * - Email/password authentication
 * - Google OAuth authentication  
 * - Account linking for multiple providers
 * - Integration with UserManager for caretaker initialization
 */
public class FirebaseAuthManager {
    
    private static final String TAG = "FirebaseAuthManager";
    
    private final FirebaseAuth auth;
    private final Context context;
    private GoogleSignInClient googleSignInClient;
    private final UserManager userManager;
    
    /**
     * Callback interface for authentication operations
     */
    public interface AuthCallback {
        void onSuccess(String userId);
        void onError(String error);
        void onAccountExists(String email, String existingProvider);
    }
    
    public FirebaseAuthManager(Context context) {
        this.context = context;
        this.auth = FirebaseAuth.getInstance();
        this.userManager = new UserManager();
        initializeGoogleSignIn();
    }
    
    /**
     * Initialize Google Sign-In configuration
     */
    private void initializeGoogleSignIn() {
        try {
            // The web client ID is automatically generated from google-services.json
            // by the Google Services Gradle plugin as R.string.default_web_client_id
            GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestIdToken(context.getString(R.string.default_web_client_id))
                    .requestEmail()
                    .build();
            
            googleSignInClient = GoogleSignIn.getClient(context, gso);
            Log.d(TAG, "Google Sign-In initialized successfully");
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize Google Sign-In: " + e.getMessage());
            // Continue without Google Sign-In if configuration fails
        }
    }
    
    /**
     * Get Google Sign-In client for starting sign-in flow
     */
    public GoogleSignInClient getGoogleSignInClient() {
        return googleSignInClient;
    }
    
    /**
     * Sign in with email and password
     */
    public void signInWithEmailAndPassword(String email, String password, AuthCallback callback) {
        auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = auth.getCurrentUser();
                        if (user != null) {
                            Log.d(TAG, "Email sign-in successful for user: " + user.getEmail());
                            initializeCaretakerAndCallback(user.getUid(), callback);
                        } else {
                            callback.onError("Sign-in successful but user is null");
                        }
                    } else {
                        String errorMessage = "Authentication failed";
                        if (task.getException() != null) {
                            errorMessage += ": " + task.getException().getMessage();
                        }
                        Log.w(TAG, errorMessage);
                        callback.onError(errorMessage);
                    }
                });
    }
    
    /**
     * Create account with email and password
     */
    public void createUserWithEmailAndPassword(String email, String password, AuthCallback callback) {
        auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = auth.getCurrentUser();
                        if (user != null) {
                            Log.d(TAG, "Account creation successful for user: " + user.getEmail());
                            initializeCaretakerAndCallback(user.getUid(), callback);
                        } else {
                            callback.onError("Account creation successful but user is null");
                        }
                    } else {
                        String errorMessage = "Account creation failed";
                        if (task.getException() != null) {
                            errorMessage += ": " + task.getException().getMessage();
                        }
                        Log.w(TAG, errorMessage);
                        callback.onError(errorMessage);
                    }
                });
    }
    
    /**
     * Sign in with Google OAuth
     */
    public void signInWithGoogle(GoogleSignInAccount account, AuthCallback callback) {
        Log.d(TAG, "Signing in with Google for account: " + account.getEmail());
        
        AuthCredential credential = GoogleAuthProvider.getCredential(account.getIdToken(), null);
        
        auth.signInWithCredential(credential)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = auth.getCurrentUser();
                        if (user != null) {
                            Log.d(TAG, "Google sign-in successful for user: " + user.getEmail());
                            initializeCaretakerAndCallback(user.getUid(), callback);
                        } else {
                            callback.onError("Google sign-in successful but user is null");
                        }
                    } else {
                        Exception exception = task.getException();
                        if (exception instanceof FirebaseAuthUserCollisionException) {
                            // Account exists with different credential
                            handleAccountCollision(account.getEmail(), "google.com", callback);
                        } else {
                            String errorMessage = "Google sign-in failed";
                            if (exception != null) {
                                errorMessage += ": " + exception.getMessage();
                            }
                            Log.w(TAG, errorMessage);
                            callback.onError(errorMessage);
                        }
                    }
                });
    }
    
    /**
     * Link Google credential to currently signed-in user
     */
    public void linkGoogleCredential(GoogleSignInAccount account, AuthCallback callback) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            callback.onError("No signed-in user to link with");
            return;
        }
        
        AuthCredential credential = GoogleAuthProvider.getCredential(account.getIdToken(), null);
        
        user.linkWithCredential(credential)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser linkedUser = auth.getCurrentUser();
                        if (linkedUser != null) {
                            Log.d(TAG, "Successfully linked Google account for user: " + linkedUser.getEmail());
                            callback.onSuccess(linkedUser.getUid());
                        } else {
                            callback.onError("Account linking successful but user is null");
                        }
                    } else {
                        String errorMessage = "Failed to link Google account";
                        if (task.getException() != null) {
                            errorMessage += ": " + task.getException().getMessage();
                        }
                        Log.w(TAG, errorMessage);
                        callback.onError(errorMessage);
                    }
                });
    }
    
    /**
     * Handle account collision when different provider is already linked
     */
    private void handleAccountCollision(String email, String newProvider, AuthCallback callback) {
        Log.d(TAG, "Account collision detected for email: " + email);
        
        auth.fetchSignInMethodsForEmail(email)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        java.util.List<String> signInMethods = task.getResult().getSignInMethods();
                        if (signInMethods != null && !signInMethods.isEmpty()) {
                            String existingProvider = signInMethods.get(0);
                            Log.d(TAG, "Existing provider found: " + existingProvider);
                            callback.onAccountExists(email, existingProvider);
                        } else {
                            callback.onError("Unable to determine existing sign-in method");
                        }
                    } else {
                        callback.onError("Failed to fetch existing sign-in methods");
                    }
                });
    }
    
    /**
     * Initialize caretaker data after successful authentication and call callback
     */
    private void initializeCaretakerAndCallback(String userId, AuthCallback callback) {
        userManager.initializeCaretaker(new UserManager.UserCallback() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "Caretaker initialization successful");
                callback.onSuccess(userId);
            }
            
            @Override
            public void onError(String error) {
                Log.e(TAG, "Caretaker initialization failed: " + error);
                // Still call success for auth, but log the initialization error
                // User can still proceed, but caretaker data might need manual setup
                callback.onSuccess(userId);
            }
        });
    }
    
    /**
     * Get current user ID
     */
    public String getCurrentUserId() {
        FirebaseUser user = auth.getCurrentUser();
        return user != null ? user.getUid() : null;
    }
    
    /**
     * Check if user is authenticated
     */
    public boolean isAuthenticated() {
        return auth.getCurrentUser() != null;
    }
    
    /**
     * Get current user email
     */
    public String getCurrentUserEmail() {
        FirebaseUser user = auth.getCurrentUser();
        return user != null ? user.getEmail() : null;
    }
    
    /**
     * Sign out user
     */
    public void signOut() {
        auth.signOut();
        googleSignInClient.signOut();
    }
}