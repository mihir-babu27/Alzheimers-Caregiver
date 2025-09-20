package com.mihir.alzheimerscaregiver.settings;

import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;
import android.util.Log;
import android.util.Patterns;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import com.mihir.alzheimerscaregiver.EmailVerificationActivity;

import java.util.HashMap;
import java.util.Map;

public class SettingsViewModel extends ViewModel {

    public interface EmailVerificationCallback {
        void launchEmailVerification(String email);
    }

    public static class UiState {
        public final boolean loading;
        public final String message;
        public final boolean success;

        public UiState(boolean loading, String message, boolean success) {
            this.loading = loading;
            this.message = message;
            this.success = success;
        }
    }

    private final MutableLiveData<UiState> state = new MutableLiveData<>(new UiState(false, null, false));
    private Context context;
    private EmailVerificationCallback emailVerificationCallback;

    public LiveData<UiState> getState() { return state; }

    private final FirebaseAuth auth = FirebaseAuth.getInstance();
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public void setContext(Context context) {
        this.context = context;
    }
    
    public void setEmailVerificationCallback(EmailVerificationCallback callback) {
        this.emailVerificationCallback = callback;
    }

    public void loadInitial(java.util.function.BiConsumer<String, Map<String, String>> onLoaded) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;
        db.collection("patients").document(user.getUid()).get().addOnSuccessListener(doc -> {
            Map<String, String> data = new HashMap<>();
            data.put("name", doc.getString("name"));
            data.put("email", doc.getString("email"));
            onLoaded.accept(user.getUid(), data);
        });
    }

    public void save(String name, String email, String currentPassword, String newPassword) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        if (TextUtils.isEmpty(name)) { state.setValue(new UiState(false, "Name cannot be empty", false)); return; }
        if (TextUtils.isEmpty(email) || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) { state.setValue(new UiState(false, "Enter a valid email", false)); return; }
        if (!TextUtils.isEmpty(newPassword) && newPassword.length() < 6) { state.setValue(new UiState(false, "Password must be at least 6 characters", false)); return; }

        state.setValue(new UiState(true, null, false));

        boolean emailChanged = user.getEmail() != null && !user.getEmail().equals(email);
        boolean passwordChanged = !TextUtils.isEmpty(newPassword);

        Runnable updateFirestore = () -> db.collection("patients").document(user.getUid())
                .update("name", name, "email", user.getEmail()) // Always use the latest email from Firebase Auth
                .addOnSuccessListener(unused -> state.setValue(new UiState(false, "Profile updated", true)))
                .addOnFailureListener(e -> state.setValue(new UiState(false, "Failed to update profile: " + e.getMessage(), false)));

        if (!emailChanged && !passwordChanged) {
            updateFirestore.run();
            return;
        }

        // Require re-authentication for sensitive changes
        if (emailChanged || passwordChanged) {
            if (TextUtils.isEmpty(currentPassword)) {
                state.setValue(new UiState(false, "Enter your current password to continue", false));
                return;
            }
        }

        AuthCredential cred = !TextUtils.isEmpty(currentPassword) ?
                EmailAuthProvider.getCredential(user.getEmail(), currentPassword) : null;
        if (cred == null) {
            updateFirestore.run();
            return;
        }

        user.reauthenticate(cred).addOnSuccessListener(aVoid -> {
            if (emailChanged) {
                // Store the target email for verification
                String targetEmail = email;
                
                user.verifyBeforeUpdateEmail(targetEmail)
                    .addOnSuccessListener(unused -> {
                        db.collection("patients").document(user.getUid())
                            .update("name", name)
                            .addOnCompleteListener(__ -> {
                                state.setValue(new UiState(false, "We've sent a verification link to your new email. Please verify your email.", true));
                                
                                // Launch EmailVerificationActivity via callback if provided
                                if (emailVerificationCallback != null) {
                                    emailVerificationCallback.launchEmailVerification(targetEmail);
                                } else if (context != null) {
                                    // Fallback if callback is not set but context is available
                                    Intent intent = new Intent(context, EmailVerificationActivity.class);
                                    intent.putExtra("EMAIL_TO_VERIFY", email);
                                    context.startActivity(intent);
                                }
                            });
                    })
                    .addOnFailureListener(e -> {
                        Log.e("SettingsViewModel", "Email verification error: " + e.getMessage(), e);
                        String msg = mapEmailError(e);
                        state.setValue(new UiState(false, msg, false));
                    });
            } else if (passwordChanged) {
                user.updatePassword(newPassword)
                    .addOnSuccessListener(unused -> updateFirestore.run())
                    .addOnFailureListener(e -> {
                        Log.e("SettingsViewModel", "Password update error: " + e.getMessage(), e);
                        state.setValue(new UiState(false, mapPasswordError(e), false));
                    });
            }
        }).addOnFailureListener(e -> {
            Log.e("SettingsViewModel", "Re-authentication error: " + e.getMessage(), e);
            String msg = mapReauthError(e);
            state.setValue(new UiState(false, msg, false));
        });
    }
    
    /**
     * Call this after login to reload the user and sync Firestore with the latest email.
     */
    public void reloadAndSyncEmail() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;
        
        // Force a reload to get the latest user data from Firebase Auth
        user.reload()
            .addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    // Get fresh user instance after reload
                    FirebaseUser refreshedUser = auth.getCurrentUser();
                    if (refreshedUser != null) {
                        String latestEmail = refreshedUser.getEmail();
                        Log.d("SettingsViewModel", "Updating Firestore with email: " + latestEmail);
                        
                        db.collection("patients").document(refreshedUser.getUid())
                            .update("email", latestEmail)
                            .addOnSuccessListener(unused -> {
                                Log.d("SettingsViewModel", "Email updated in Firestore successfully");
                            })
                            .addOnFailureListener(e -> {
                                Log.e("SettingsViewModel", "Firestore email sync error: " + e.getMessage(), e);
                            });
                    }
                } else {
                    Log.e("SettingsViewModel", "User reload error: " + 
                          (task.getException() != null ? task.getException().getMessage() : "Unknown error"));
                }
            });
    }
    
    /**
     * Reload the user and check if a specific email address is verified.
     * Also updates Firestore if the email is verified.
     * 
     * @param email The email address to verify
     */
    public void reloadAndVerifyEmail(String email) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            state.setValue(new UiState(false, "User session expired. Please sign in again.", false));
            return;
        }
        
        state.setValue(new UiState(true, "Checking verification status...", false));
        
        user.reload()
            .addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    // Get fresh user instance after reload
                    FirebaseUser refreshedUser = auth.getCurrentUser();
                    if (refreshedUser != null) {
                        String currentEmail = refreshedUser.getEmail();
                        boolean isVerified = refreshedUser.isEmailVerified();
                        
                        // Log the status for debugging
                        Log.d("SettingsViewModel", "User reloaded: email=" + currentEmail + 
                              ", target email=" + email + 
                              ", verified=" + isVerified);
                        
                        if (email.equals(currentEmail) && isVerified) {
                            // Update Firestore with verified email
                            db.collection("patients").document(refreshedUser.getUid())
                                .update("email", email)
                                .addOnSuccessListener(unused -> {
                                    Log.d("SettingsViewModel", "Email updated in Firestore after verification");
                                    state.setValue(new UiState(false, "Email verified and updated successfully.", true));
                                })
                                .addOnFailureListener(e -> {
                                    Log.e("SettingsViewModel", "Failed to update email in Firestore", e);
                                    state.setValue(new UiState(false, 
                                        "Email verified but failed to update profile. Please try again.", false));
                                });
                        } else if (email.equals(currentEmail) && !isVerified) {
                            Log.d("SettingsViewModel", "Email matches but not yet verified");
                            state.setValue(new UiState(false, 
                                "Email verification pending. Please check your inbox and click the verification link.", false));
                        } else {
                            Log.w("SettingsViewModel", "Email mismatch after reload: current=" + currentEmail + ", expected=" + email);
                            state.setValue(new UiState(false, "Email update verification failed. Please try again.", false));
                        }
                    } else {
                        Log.e("SettingsViewModel", "User is null after reload");
                        state.setValue(new UiState(false, "Session error. Please sign out and sign in again.", false));
                    }
                } else {
                    Log.e("SettingsViewModel", "Failed to reload user", task.getException());
                    state.setValue(new UiState(false, "Failed to refresh session. Please sign out and sign in again.", false));
                }
            });
    }

    private String mapReauthError(Exception e) {
        String msg = e.getMessage() == null ? "Re-authentication failed" : e.getMessage();
        if (msg.toLowerCase().contains("password")) return "Wrong password";
        if (msg.toLowerCase().contains("recent login")) return "Session expired. Please log in again.";
        return msg;
    }

    private String mapEmailError(Exception e) {
        String msg = e.getMessage() == null ? "Failed to update email" : e.getMessage();
        if (msg.toLowerCase().contains("already in use")) return "Email already in use";
        if (msg.toLowerCase().contains("operation not allowed")) return "Email/Password provider is disabled. Enable it in Firebase Auth > Sign-in method.";
        if (msg.toLowerCase().contains("invalid")) return "Invalid email address";
        return msg;
    }

    private String mapPasswordError(Exception e) {
        String msg = e.getMessage() == null ? "Failed to update password" : e.getMessage();
        if (msg.toLowerCase().contains("weak password")) return "Password too weak";
        return msg;
    }
}