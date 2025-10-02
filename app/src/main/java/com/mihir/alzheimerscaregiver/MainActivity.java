package com.mihir.alzheimerscaregiver;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Calendar;
import java.util.UUID;

import com.mihir.alzheimerscaregiver.auth.FirebaseAuthManager;
import com.mihir.alzheimerscaregiver.face_recognition.FaceRecognitionActivity;


public class MainActivity extends AppCompatActivity {

    // UI Elements
    private TextView welcomeText;
    private TextView nameText;
    private CardView medicationCard, tasksCard, memoryCard, photosCard, emergencyCard, mmseCard, objectDetectionCard, storiesCard;
    
    // Firebase Auth Manager
    private FirebaseAuthManager authManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize Firebase securely with API keys from BuildConfig
        try {
            // Use our secure initializer instead of FirebaseInitializer
            com.mihir.alzheimerscaregiver.data.SecureFirebaseInitializer.initialize(this);
        } catch (Exception e) {
            android.util.Log.e("MainActivity", "Error initializing Firebase securely", e);
            
            // Fallback to traditional initialization if secure method fails
            try {
                com.mihir.alzheimerscaregiver.data.FirebaseInitializer.initialize(this);
                android.util.Log.d("MainActivity", "Firebase initialized with traditional method");
            } catch (Exception fallbackEx) {
                android.util.Log.e("MainActivity", "Error initializing Firebase with fallback method", fallbackEx);
                // Continue with app initialization even if Firebase fails
            }
        }

        // Initialize notification channels early
        com.mihir.alzheimerscaregiver.notifications.NotificationUtils.ensureChannels(this);
        
        // Request notification permission on Android 13+
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) 
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                androidx.activity.result.ActivityResultLauncher<String> launcher = registerForActivityResult(
                    new androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
                    isGranted -> {
                        if (isGranted) {
                            android.util.Log.d("MainActivity", "Notification permission granted");
                        } else {
                            android.util.Log.w("MainActivity", "Notification permission denied");
                        }
                    }
                );
                launcher.launch(android.Manifest.permission.POST_NOTIFICATIONS);
            }
        }
        
        // Check and request alarm permissions for reliable reminders
        checkAlarmPermissions();

        // Initialize Firebase Auth Manager
        authManager = new FirebaseAuthManager();

        // Check if user is signed in
        if (!authManager.isPatientSignedIn()) {
            navigateToAuth();
            return;
        }
        
        // Try to update Firestore with the latest verified email
        try {
            com.google.firebase.auth.FirebaseUser user = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
            if (user != null) {
                // Force reload to get fresh data
                user.reload().addOnSuccessListener(aVoid -> {
                    // Get fresh user instance
                    com.google.firebase.auth.FirebaseUser refreshedUser = 
                        com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
                    
                    if (refreshedUser != null) {
                        String patientId = authManager.getCurrentPatientId();
                        String currentEmail = refreshedUser.getEmail();
                        
                        if (patientId != null && currentEmail != null) {
                            android.util.Log.d("MainActivity", "Updating Firestore email to: " + currentEmail);
                            com.google.firebase.firestore.FirebaseFirestore.getInstance()
                                .collection("patients")
                                .document(patientId)
                                .update("email", currentEmail)
                                .addOnSuccessListener(unused -> {
                                    android.util.Log.d("MainActivity", "Email updated in Firestore");
                                })
                                .addOnFailureListener(e -> {
                                    android.util.Log.e("MainActivity", "Failed to update email in Firestore", e);
                                });
                        }
                    }
                });
            }
        } catch (Exception e) {
            android.util.Log.e("MainActivity", "Error updating email", e);
        }

        // Set up toolbar
        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // Initialize UI elements
        initializeViews();

        // Set up dynamic welcome message
        setupWelcomeMessage();

        // Set up click listeners for all cards
        setupClickListeners();

        // Hidden debug/test hook: long-press on the user's name to schedule a test alarm in 10 seconds
        try {
            if (nameText != null) {
                nameText.setOnLongClickListener(v -> {
                    scheduleTestAlarm();
                    return true;
                });
            }
        } catch (Exception ignore) {}

        // Schedule MMSE test notifications for patient
        try {
            String patientId = authManager.getCurrentPatientId();
            if (patientId != null) {
                com.mihir.alzheimerscaregiver.mmse.MmseScheduleManager.scheduleAll(this, patientId);
            }
        } catch (Exception ignore) {}

        // Sync MMSE monthly reminder from settings if available
        try {
            syncMmseReminder();
        } catch (Exception ignore) {}

        // Ensure background periodic sync is scheduled (safety net if FCM missed)
        try {
            com.mihir.alzheimerscaregiver.sync.ReminderSyncScheduler.schedulePeriodic(this.getApplicationContext());
        } catch (Exception e) {
            android.util.Log.w("MainActivity", "Failed to schedule periodic sync", e);
        }

        // Client-only mode: no FCM; WorkManager periodic sync is sufficient for background updates.
    }

    // Schedules a quick test alarm in ~10 seconds to validate end-to-end alarm UX
    private void scheduleTestAlarm() {
        try {
            com.mihir.alzheimerscaregiver.alarm.AlarmScheduler scheduler =
                    new com.mihir.alzheimerscaregiver.alarm.AlarmScheduler(this);
            long triggerAt = System.currentTimeMillis() + 10_000L;
            String id = UUID.randomUUID().toString();
            boolean ok = scheduler.scheduleAlarm(id, "Test Alarm", "This is a test alarm", triggerAt);
            if (ok) {
                Toast.makeText(this, "Test alarm set for 10 seconds", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Failed to set test alarm", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            android.util.Log.e("MainActivity", "Error scheduling test alarm", e);
            Toast.makeText(this, "Error scheduling test alarm", Toast.LENGTH_SHORT).show();
        }
    }

    private void syncMmseReminder() {
        String patientId = authManager.getCurrentPatientId();
        if (patientId == null) return;
        com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("patients").document(patientId)
                .collection("settings").document("reminders")
                .get()
                .addOnSuccessListener(doc -> {
                    boolean enabled = doc != null && doc.exists() && Boolean.TRUE.equals(doc.getBoolean("mmseMonthlyEnabled"));
                    if (enabled) {
                        com.mihir.alzheimerscaregiver.notifications.MmseReminderScheduler.scheduleMonthly(this);
                    } else {
                        com.mihir.alzheimerscaregiver.notifications.MmseReminderScheduler.cancel(this);
                    }
                });
    }

    /**
     * Initialize all UI elements
     */
    private void initializeViews() {
        // Text views
        welcomeText = findViewById(R.id.welcomeText);
        nameText = findViewById(R.id.nameText);

        // Feature cards
        medicationCard = findViewById(R.id.medicationCard);
        tasksCard = findViewById(R.id.tasksCard);
        memoryCard = findViewById(R.id.memoryCard);
        photosCard = findViewById(R.id.photosCard);
        mmseCard = findViewById(R.id.mmseCard);
        emergencyCard = findViewById(R.id.emergencyCard);
        objectDetectionCard = findViewById(R.id.objectDetectionCard);
        storiesCard = findViewById(R.id.storiesCard);
    }

    /**
     * Set up dynamic welcome message based on time of day
     */
    private void setupWelcomeMessage() {
        Calendar calendar = Calendar.getInstance();
        int hour = calendar.get(Calendar.HOUR_OF_DAY);

        String greeting;
        if (hour < 12) {
            greeting = "Good Morning";
        } else if (hour < 17) {
            greeting = "Good Afternoon";
        } else {
            greeting = "Good Evening";
        }

        welcomeText.setText(greeting);

        // Get patient name from Firebase Auth
        String patientId = authManager.getCurrentPatientId();
        if (patientId != null) {
            authManager.getPatientData(patientId, new FirebaseAuthManager.PatientDataCallback() {
                @Override
                public void onSuccess(com.mihir.alzheimerscaregiver.data.entity.PatientEntity patient) {
                    if (patient != null && patient.name != null) {
                        nameText.setText(patient.name);
                    } else {
                        nameText.setText("Friend");
                    }
                }
                
                @Override
                public void onError(String error) {
                    nameText.setText("Friend");
                }
            });
        } else {
            nameText.setText("Friend");
        }
    }

    /**
     * Set up click listeners for all feature cards
     */
    private void setupClickListeners() {

        // Medication Card
        medicationCard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Add haptic feedback
                v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);

                showToast("Opening Medication Reminders...");
                Intent intent = new Intent(MainActivity.this, RemindersActivity.class);
                intent.putExtra(RemindersActivity.EXTRA_MEDICATION_MODE, true);
                startActivity(intent);
            }
        });

        // Daily Tasks Card
        tasksCard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Add haptic feedback
                v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);

                // Create intent to start TasksActivity
                Intent intent = new Intent(MainActivity.this, TasksActivity.class);
                startActivity(intent);
            }
        });

        // Memory Games Card
        memoryCard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);

                // Navigate to GameSelectionActivity
                Intent intent = new Intent(MainActivity.this, GameSelectionActivity.class);
                startActivity(intent);
            }
        });

        // Face Recognition Card
        photosCard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Add haptic feedback
                v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);

                // Open Face Recognition
                Intent intent = new Intent(MainActivity.this, FaceRecognitionActivity.class);
                startActivity(intent);

            }
        });
        // Emergency Card
        emergencyCard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);

                // Create intent to start EmergencyActivity
                Intent intent = new Intent(MainActivity.this, EmergencyActivity.class);
                startActivity(intent);
            }
        });

        // MMSE Quiz Card
        mmseCard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);

                Intent intent = new Intent(MainActivity.this, MmseQuizActivity.class);
                startActivity(intent);
            }
        });

        // Object Detection Card
        objectDetectionCard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);

                showToast("Opening Object Detection...");
                Intent intent = new Intent(MainActivity.this, ObjectDetectionActivity.class);
                startActivity(intent);
            }
        });

        // Settings Card
        View settingsCard = findViewById(R.id.settingsCard);
        if (settingsCard != null) {
            settingsCard.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
                    Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
                    startActivity(intent);
                }
            });
        }

        // Stories Card
        if (storiesCard != null) {
            storiesCard.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
                    showToast("Opening My Stories...");
                    Intent intent = new Intent(MainActivity.this, com.mihir.alzheimerscaregiver.reminiscence.ReminiscenceStoryActivity.class);
                    startActivity(intent);
                }
            });
        }
    }

    /**
     * Helper method to show toast messages
     */
    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    /**
     * Method to update the tasks count (you can call this from other activities)
     */
    public void updateTasksRemaining(int count) {
        // You can add logic here to update the "3 tasks remaining" text
        // This would typically be connected to a database or shared preferences
    }

    @Override
protected void onResume() {
    super.onResume();
    
    // Check if auth is initialized to avoid crash
    if (authManager != null) {
        // First reload the user to get the latest auth state including email verification
        authManager.reloadCurrentUser(success -> {
            if (success) {
                // If reload is successful, sync the email and update UI
                authManager.syncEmailWithFirestore(syncSuccess -> {
                    if (syncSuccess) {
                        Log.d("MainActivity", "Email synced with Firestore on resume");
                    } else {
                        Log.w("MainActivity", "Failed to sync email with Firestore on resume");
                    }
                });
            }
            
            // Update welcome and name when returning to the app
            setupWelcomeMessage();
        });
    } else {
        // Update welcome and name even if auth is not initialized
        setupWelcomeMessage();
    }

    // You could also refresh task counts, medication reminders, etc.
}
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        
        if (id == R.id.action_profile) {
            // Navigate to Patient Profile
            Intent intent = new Intent(this, PatientProfileActivity.class);
            startActivity(intent);
            return true;
        } else if (id == R.id.action_sign_out) {
            // Sign out user
            authManager.signOut();
            Toast.makeText(this, "Signed out successfully", Toast.LENGTH_SHORT).show();
            navigateToAuth();
            return true;
        } else if (id == R.id.action_settings) {
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
            return true;
        }
        
        return super.onOptionsItemSelected(item);
    }
    
    private void navigateToAuth() {
        Intent intent = new Intent(this, com.mihir.alzheimerscaregiver.auth.AuthenticationActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
    
    /**
     * Check and request alarm permissions for reliable reminders
     */
    private void checkAlarmPermissions() {
        boolean needsPermission = false;
        StringBuilder message = new StringBuilder();
        message.append("For alarms to work reliably, please enable these settings:\n\n");
        
        // Check exact alarm permission on Android 12+
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            android.app.AlarmManager alarmManager = (android.app.AlarmManager) getSystemService(ALARM_SERVICE);
            if (!alarmManager.canScheduleExactAlarms()) {
                needsPermission = true;
                message.append("1. Allow 'Alarms & reminders' permission\n");
            }
        }
        
        // Check battery optimization
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            android.os.PowerManager powerManager = (android.os.PowerManager) getSystemService(POWER_SERVICE);
            if (!powerManager.isIgnoringBatteryOptimizations(getPackageName())) {
                needsPermission = true;
                message.append("2. Disable battery optimization for this app\n");
            }
        }
        
        if (needsPermission) {
            message.append("\nThis ensures alarms work even when the app is in the background.");
            
            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Permission Settings Required")
                    .setMessage(message.toString())
                    .setPositiveButton("Open Settings", (dialog, which) -> openAlarmSettings())
                    .setNegativeButton("Later", null)
                    .show();
        }
    }
    
    /**
     * Open appropriate settings for alarm permissions
     */
    private void openAlarmSettings() {
        // First try exact alarm settings (Android 12+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            android.app.AlarmManager alarmManager = (android.app.AlarmManager) getSystemService(ALARM_SERVICE);
            if (!alarmManager.canScheduleExactAlarms()) {
                try {
                    Intent intent = new Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                    intent.setData(android.net.Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                    return;
                } catch (Exception e) {
                    android.util.Log.e("MainActivity", "Could not open exact alarm settings", e);
                }
            }
        }
        
        // Fall back to battery optimization settings
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            android.os.PowerManager powerManager = (android.os.PowerManager) getSystemService(POWER_SERVICE);
            if (!powerManager.isIgnoringBatteryOptimizations(getPackageName())) {
                try {
                    Intent intent = new Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                    intent.setData(android.net.Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                    return;
                } catch (Exception e) {
                    android.util.Log.e("MainActivity", "Could not open battery optimization settings", e);
                }
            }
        }
        
        // Final fallback to general app settings
        try {
            Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(android.net.Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } catch (Exception e) {
            android.util.Log.e("MainActivity", "Could not open app settings", e);
        }
    }
}