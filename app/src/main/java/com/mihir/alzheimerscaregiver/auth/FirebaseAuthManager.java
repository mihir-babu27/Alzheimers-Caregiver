package com.mihir.alzheimerscaregiver.auth;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.firestore.FirebaseFirestore;
import com.mihir.alzheimerscaregiver.data.entity.PatientEntity;
import com.mihir.alzheimerscaregiver.data.FirebaseConfig;

public class FirebaseAuthManager implements IAuthManager {
    
    private static final String TAG = "FirebaseAuthManager";
    private final FirebaseAuth auth;
    private final FirebaseFirestore db;
    private final Context context;
    private GoogleSignInClient googleSignInClient;
    
    public FirebaseAuthManager() {
        this.auth = FirebaseAuth.getInstance();
        this.db = FirebaseFirestore.getInstance();
        this.context = null; // For backward compatibility with existing code
    }
    
    public FirebaseAuthManager(Context context) {
        this.auth = FirebaseAuth.getInstance();
        this.db = FirebaseFirestore.getInstance();
        this.context = context;
        initializeGoogleSignIn();
    }
    
    /**
     * Initialize Google Sign-In configuration
     */
    private void initializeGoogleSignIn() {
        if (context == null) return;
        
        try {
            // The web client ID is automatically generated from google-services.json
            GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestIdToken(context.getString(context.getResources().getIdentifier("default_web_client_id", "string", context.getPackageName())))
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
     * Sign up a new patient with email and password
     */
    public void signUpPatient(String email, String password, String name, AuthCallback callback) {
        if (email == null || password == null || name == null) {
            callback.onError("Email, password, and name are required");
            return;
        }
        
        auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = auth.getCurrentUser();
                        if (user != null) {
                            // Create patient document in Firestore
                            createPatientDocument(user.getUid(), name, email, callback);
                        } else {
                            callback.onError("Failed to get user after sign up");
                        }
                    } else {
                        String errorMessage = task.getException() != null ? 
                                task.getException().getMessage() : "Sign up failed";
                        callback.onError(errorMessage);
                    }
                });
    }
    
    /**
     * Sign in existing patient
     */
    public void signInPatient(String email, String password, AuthCallback callback) {
        if (email == null || password == null) {
            callback.onError("Email and password are required");
            return;
        }
        
        auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = auth.getCurrentUser();
                        if (user != null) {
                            callback.onSuccess(user.getUid());
                        } else {
                            callback.onError("Failed to get user after sign in");
                        }
                    } else {
                        String errorMessage = task.getException() != null ? 
                                task.getException().getMessage() : "Sign in failed";
                        callback.onError(errorMessage);
                    }
                });
    }
    
    /**
     * Sign out current patient
     */
    @Override
    public void signOut() {
        auth.signOut();
    }
    
    /**
     * Get current signed-in patient
     */
    @Override
    public FirebaseUser getCurrentUser() {
        return auth.getCurrentUser();
    }
    
    /**
     * Check if patient is currently signed in
     */
    @Override
    public boolean isPatientSignedIn() {
        return auth.getCurrentUser() != null;
    }
    
    /**
     * Get current patient's UID
     */
    @Override
    public String getCurrentPatientId() {
        FirebaseUser user = auth.getCurrentUser();
        return user != null ? user.getUid() : null;
    }
    
    /**
     * Create patient document in Firestore after successful sign up
     */
    private void createPatientDocument(String patientId, String name, String email, AuthCallback callback) {
        PatientEntity patient = new PatientEntity(patientId, name, email);
        
        db.collection("patients").document(patientId)
                .set(patient)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "Patient document created successfully");
                        callback.onSuccess(patientId);
                    } else {
                        String errorMessage = task.getException() != null ? 
                                task.getException().getMessage() : "Failed to create patient document";
                        Log.e(TAG, "Error creating patient document: " + errorMessage);
                        callback.onError(errorMessage);
                    }
                });
    }
    
    /**
     * Get patient data from Firestore
     */
    public void getPatientData(String patientId, PatientDataCallback callback) {
        db.collection("patients").document(patientId)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        PatientEntity patient = task.getResult().toObject(PatientEntity.class);
                        if (patient != null) {
                            callback.onSuccess(patient);
                        } else {
                            callback.onError("Failed to parse patient data");
                        }
                    } else {
                        String errorMessage = task.getException() != null ? 
                                task.getException().getMessage() : "Failed to get patient data";
                        callback.onError(errorMessage);
                    }
                });
    }
    
    /**
     * Update patient data in Firestore
     */
    public void updatePatientData(PatientEntity patient, PatientDataCallback callback) {
        db.collection("patients").document(patient.patientId)
                .set(patient)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        callback.onSuccess(patient);
                    } else {
                        String errorMessage = task.getException() != null ? 
                                task.getException().getMessage() : "Failed to update patient data";
                        callback.onError(errorMessage);
                    }
                });
    }
    
    /**
     * Add caretaker to patient's caretakerIds list
     */
    public void addCaretaker(String patientId, String caretakerId, PatientDataCallback callback) {
        // First get the current patient document to check existing caretakerIds
        db.collection("patients").document(patientId)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        java.util.List<String> caretakerIds = new java.util.ArrayList<>();
                        
                        // Get existing caretaker IDs if they exist
                        PatientEntity patient = task.getResult().toObject(PatientEntity.class);
                        if (patient != null && patient.caretakerIds != null) {
                            caretakerIds.addAll(patient.caretakerIds);
                        }
                        
                        // Add new caretaker if not already in the list
                        if (!caretakerIds.contains(caretakerId)) {
                            caretakerIds.add(caretakerId);
                            
                            // Update the document with the new caretaker list
                            db.collection("patients").document(patientId)
                                    .update("caretakerIds", caretakerIds)
                                    .addOnCompleteListener(updateTask -> {
                                        if (updateTask.isSuccessful()) {
                                            // Refresh patient data
                                            getPatientData(patientId, callback);
                                        } else {
                                            String errorMessage = updateTask.getException() != null ? 
                                                    updateTask.getException().getMessage() : "Failed to add caretaker";
                                            callback.onError(errorMessage);
                                        }
                                    });
                        } else {
                            // Caretaker already exists, just refresh data
                            getPatientData(patientId, callback);
                        }
                    } else {
                        String errorMessage = task.getException() != null ? 
                                task.getException().getMessage() : "Failed to get patient data";
                        callback.onError(errorMessage);
                    }
                });
    }
    
    /**
     * Reload current user's authentication state
     */
    @Override
    public void reloadCurrentUser(OnReloadCompleteCallback callback) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            if (callback != null) {
                callback.onComplete(false);
            }
            return;
        }
        
        user.reload()
            .addOnSuccessListener(aVoid -> {
                if (callback != null) {
                    callback.onComplete(true);
                }
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Failed to reload user", e);
                if (callback != null) {
                    callback.onComplete(false);
                }
            });
    }
    
    /**
     * Update email in Firestore to match Firebase Auth
     */
    @Override
    public void syncEmailWithFirestore(OnFirestoreSyncCallback callback) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            if (callback != null) {
                callback.onComplete(false);
            }
            return;
        }
        
        String patientId = user.getUid();
        String email = user.getEmail();
        
        if (email == null) {
            if (callback != null) {
                callback.onComplete(false);
            }
            return;
        }
        
        Log.d(TAG, "Syncing email with Firestore: " + email);
        
        db.collection("patients").document(patientId)
            .update("email", email)
            .addOnSuccessListener(aVoid -> {
                Log.d(TAG, "Email synced with Firestore successfully");
                if (callback != null) {
                    callback.onComplete(true);
                }
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Failed to sync email with Firestore", e);
                if (callback != null) {
                    callback.onComplete(false);
                }
            });
    }
    
    /**
     * Reload and sync email with Firestore in one operation
     * This is useful after email verification
     */
    public void reloadAndSyncEmail(OnFirestoreSyncCallback callback) {
        reloadCurrentUser(success -> {
            if (success) {
                syncEmailWithFirestore(callback);
            } else {
                if (callback != null) {
                    callback.onComplete(false);
                }
            }
        });
    }
    
    /**
     * Check if email is verified after reloading user.
     * If verified, also sync with Firestore.
     *
     * @param emailToVerify The email address we expect to be verified
     * @param callback Callback with verification result
     */
    public void checkEmailVerificationStatus(String emailToVerify, EmailVerificationCallback callback) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            if (callback != null) {
                callback.onResult(false, "User not signed in");
            }
            return;
        }
        
        // Force reload to get latest verification status
        user.reload()
            .addOnSuccessListener(aVoid -> {
                // Get fresh user instance after reload
                FirebaseUser refreshedUser = auth.getCurrentUser();
                if (refreshedUser != null) {
                    String currentEmail = refreshedUser.getEmail();
                    boolean isVerified = refreshedUser.isEmailVerified();
                    
                    if (emailToVerify != null && emailToVerify.equals(currentEmail) && isVerified) {
                        // Email matches what we expected and is verified
                        syncEmailWithFirestore(syncSuccess -> {
                            if (syncSuccess) {
                                if (callback != null) {
                                    callback.onResult(true, "Email verified and profile updated");
                                }
                            } else {
                                if (callback != null) {
                                    callback.onResult(true, "Email verified but profile update failed");
                                }
                            }
                        });
                    } else if (isVerified) {
                        // Email is verified but doesn't match what we expected
                        if (callback != null) {
                            callback.onResult(true, "Email is verified");
                        }
                    } else {
                        // Email is not yet verified
                        if (callback != null) {
                            callback.onResult(false, "Email not yet verified");
                        }
                    }
                } else {
                    if (callback != null) {
                        callback.onResult(false, "Session error after reload");
                    }
                }
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Failed to reload user for verification", e);
                if (callback != null) {
                    callback.onResult(false, "Failed to check verification status");
                }
            });
    }
    
    /**
     * Sign in with Google OAuth
     */
    public void signInWithGoogle(GoogleSignInAccount account, AuthCallback callback) {
        if (account == null) {
            if (callback != null) {
                callback.onError("Google Sign-In failed: No account received");
            }
            return;
        }
        
        Log.d(TAG, "Signing in with Google: " + account.getEmail());
        
        AuthCredential credential = GoogleAuthProvider.getCredential(account.getIdToken(), null);
        
        auth.signInWithCredential(credential)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = auth.getCurrentUser();
                        if (user != null) {
                            String patientId = user.getUid();
                            Log.d(TAG, "Google sign-in successful for patient: " + patientId);
                            
                            // Create or update patient document in Firestore
                            createOrUpdatePatientDocument(user, account.getDisplayName(), callback);
                        } else {
                            if (callback != null) {
                                callback.onError("Authentication succeeded but user is null");
                            }
                        }
                    } else {
                        Exception exception = task.getException();
                        if (exception instanceof FirebaseAuthUserCollisionException) {
                            // Email already exists with different provider
                            handleAccountCollision(credential, exception, callback);
                        } else {
                            String errorMessage = exception != null ? exception.getMessage() : "Google sign-in failed";
                            Log.e(TAG, "Google sign-in failed: " + errorMessage);
                            if (callback != null) {
                                callback.onError("Google sign-in failed: " + errorMessage);
                            }
                        }
                    }
                });
    }
    
    /**
     * Link Google credential to existing account
     */
    public void linkGoogleCredential(GoogleSignInAccount account, AuthCallback callback) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            if (callback != null) {
                callback.onError("No user signed in to link Google account");
            }
            return;
        }
        
        AuthCredential credential = GoogleAuthProvider.getCredential(account.getIdToken(), null);
        
        user.linkWithCredential(credential)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "Google account linked successfully");
                        if (callback != null) {
                            callback.onSuccess(user.getUid());
                        }
                    } else {
                        String errorMessage = task.getException() != null ? 
                                task.getException().getMessage() : "Failed to link Google account";
                        Log.e(TAG, "Failed to link Google account: " + errorMessage);
                        if (callback != null) {
                            callback.onError("Failed to link Google account: " + errorMessage);
                        }
                    }
                });
    }
    
    /**
     * Handle account collision when email already exists
     */
    private void handleAccountCollision(AuthCredential credential, Exception exception, AuthCallback callback) {
        Log.d(TAG, "Handling account collision: " + exception.getMessage());
        
        if (callback != null) {
            callback.onError("An account with this email already exists. Please sign in with your email and password, then link your Google account in settings.");
        }
    }
    
    /**
     * Create or update patient document for OAuth users
     */
    private void createOrUpdatePatientDocument(FirebaseUser user, String displayName, AuthCallback callback) {
        String patientId = user.getUid();
        String email = user.getEmail();
        
        // Check if patient document already exists
        db.collection("patients").document(patientId)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        if (task.getResult().exists()) {
                            // Patient exists, just update email if needed
                            Log.d(TAG, "Patient document exists, updating email if needed");
                            updatePatientEmail(patientId, email, callback);
                        } else {
                            // Create new patient document
                            Log.d(TAG, "Creating new patient document for OAuth user");
                            PatientEntity newPatient = new PatientEntity();
                            newPatient.patientId = patientId;
                            newPatient.email = email;
                            if (displayName != null && !displayName.trim().isEmpty()) {
                                newPatient.name = displayName;
                            }
                            
                            db.collection("patients").document(patientId)
                                    .set(newPatient)
                                    .addOnSuccessListener(aVoid -> {
                                        Log.d(TAG, "Patient document created successfully for OAuth user");
                                        if (callback != null) {
                                            callback.onSuccess(patientId);
                                        }
                                    })
                                    .addOnFailureListener(e -> {
                                        String errorMessage = "Failed to create patient profile: " + e.getMessage();
                                        Log.e(TAG, errorMessage, e);
                                        if (callback != null) {
                                            callback.onError(errorMessage);
                                        }
                                    });
                        }
                    } else {
                        String errorMessage = "Failed to check existing patient: " + 
                                (task.getException() != null ? task.getException().getMessage() : "Unknown error");
                        Log.e(TAG, errorMessage);
                        if (callback != null) {
                            callback.onError(errorMessage);
                        }
                    }
                });
    }
    
    /**
     * Update patient email in Firestore
     */
    private void updatePatientEmail(String patientId, String email, AuthCallback callback) {
        db.collection("patients").document(patientId)
                .update("email", email)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Patient email updated successfully");
                    if (callback != null) {
                        callback.onSuccess(patientId);
                    }
                })
                .addOnFailureListener(e -> {
                    String errorMessage = "Failed to update patient email: " + e.getMessage();
                    Log.e(TAG, errorMessage, e);
                    if (callback != null) {
                        callback.onError(errorMessage);
                    }
                });
    }
    
    /**
     * Sign out from Google
     */
    public void signOutFromGoogle(Runnable callback) {
        if (googleSignInClient != null) {
            googleSignInClient.signOut()
                    .addOnCompleteListener(task -> {
                        Log.d(TAG, "Signed out from Google");
                        if (callback != null) {
                            callback.run();
                        }
                    });
        } else if (callback != null) {
            callback.run();
        }
    }
    
    public interface AuthCallback {
        void onSuccess(String patientId);
        void onError(String error);
    }
    
    public interface PatientDataCallback {
        void onSuccess(PatientEntity patient);
        void onError(String error);
    }
    
    /**
     * Interface for email verification callbacks
     */
    public interface EmailVerificationCallback {
        void onResult(boolean isVerified, String message);
    }
}