package com.mihir.alzheimerscaregiver.auth;

import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.mihir.alzheimerscaregiver.data.entity.PatientEntity;
import com.mihir.alzheimerscaregiver.data.FirebaseConfig;

public class FirebaseAuthManager implements IAuthManager {
    
    private static final String TAG = "FirebaseAuthManager";
    private final FirebaseAuth auth;
    private final FirebaseFirestore db;
    
    public FirebaseAuthManager() {
        this.auth = FirebaseAuth.getInstance();
        this.db = FirebaseConfig.getInstance();
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