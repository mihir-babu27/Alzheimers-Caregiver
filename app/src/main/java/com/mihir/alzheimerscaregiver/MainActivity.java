package com.mihir.alzheimerscaregiver;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.Manifest;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Calendar;
import java.util.Map;
import java.util.UUID;

import com.google.firebase.database.FirebaseDatabase;

import com.mihir.alzheimerscaregiver.auth.FirebaseAuthManager;
import com.mihir.alzheimerscaregiver.face_recognition.FaceRecognitionActivity;
import com.mihir.alzheimerscaregiver.geofence.PatientGeofenceClient;
import com.mihir.alzheimerscaregiver.testing.EnhancedMMSETester;


public class MainActivity extends AppCompatActivity {

    // UI Elements
    private TextView welcomeText;
    private TextView nameText;
    private CardView medicationCard, tasksCard, memoryCard, photosCard, emergencyCard, mmseCard, objectDetectionCard, storiesCard, locationCard, chatbotCard;
    
    // Firebase Auth Manager
    private FirebaseAuthManager authManager;
    
    // Geofence Client
    private PatientGeofenceClient geofenceClient;

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
        
        // Check and request alarm permissions for reliable reminders (disabled to remove annoying popup)
        // checkAlarmPermissions();

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

        // Schedule midnight alarm reset job for daily alarm maintenance
        try {
            com.mihir.alzheimerscaregiver.alarm.MidnightAlarmResetScheduler.scheduleMidnightReset(this.getApplicationContext());
        } catch (Exception e) {
            android.util.Log.w("MainActivity", "Failed to schedule midnight alarm reset", e);
        }

        // Client-only mode: no FCM; WorkManager periodic sync is sufficient for background updates.
        
        // Initialize and start geofence monitoring for patient safety
        initializeGeofenceMonitoring();
        
        // Set up cross-device notification listeners for CaretakerApp created data
        setupCrossDeviceNotificationListeners();
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
        locationCard = findViewById(R.id.locationCard);
        chatbotCard = findViewById(R.id.chatbotCard);
        
        // Location card is now enabled for location sharing functionality
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

                // Get current patient information
                String patientId = authManager.getCurrentPatientId();
                
                if (patientId != null) {
                    // Launch Enhanced MMSE with AI personalization
                    Intent intent = new Intent(MainActivity.this, EnhancedMmseQuizActivity.class);
                    intent.putExtra("patient_id", patientId);
                    
                    // Add patient profile data for AI generation
                    // These will be populated from patient profile when available
                    intent.putExtra("patient_name", ""); // TODO: Get from profile
                    intent.putExtra("patient_birth_year", ""); // TODO: Get from profile  
                    intent.putExtra("patient_birthplace", ""); // TODO: Get from profile
                    intent.putExtra("patient_profession", ""); // TODO: Get from profile
                    intent.putExtra("patient_other_details", ""); // TODO: Get from profile
                    
                    startActivity(intent);
                } else {
                    // Fallback to standard MMSE if no patient ID
                    showToast("Please complete profile setup first");
                    Intent intent = new Intent(MainActivity.this, MmseQuizActivity.class);
                    startActivity(intent);
                }
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

        // Stories Card - Updated to use Firebase-integrated Story Generation
        if (storiesCard != null) {
            storiesCard.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
                    showToast("Opening AI Stories...");
                    Intent intent = new Intent(MainActivity.this, com.mihir.alzheimerscaregiver.reminiscence.StoryGenerationActivity.class);
                    startActivity(intent);
                }
            });
        }
        
        // Chat Assistant Card - Voice-enabled AI chatbot for conversation and cognitive assessment
        if (chatbotCard != null) {
            chatbotCard.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
                    showToast("Opening Chat Assistant...");
                    Intent intent = new Intent(MainActivity.this, ChatbotActivity.class);
                    startActivity(intent);
                }
            });
        }
        
        // Location Card - Opens Location Sharing settings
        if (locationCard != null) {
            locationCard.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
                    
                    // Create intent to start TrackingActivity
                    Intent intent = new Intent(MainActivity.this, TrackingActivity.class);
                    startActivity(intent);
                }
            });
            
            // Add test functionality for debugging
            addBootReceiverTestButton();
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
        } else if (id == R.id.action_developer_test) {
            // Run Enhanced MMSE Tests
            runEnhancedMMSETests();
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
     * DISABLED: This method was causing annoying popups on app startup
     */
    private void checkAlarmPermissions() {
        // Method disabled to remove annoying battery optimization popup
        // The app will still work fine without these permissions for most users
        
        /* ORIGINAL CODE (disabled):
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
        */
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
    
    /**
     * Set up real-time Firestore listeners to detect new reminders and tasks 
     * created by the CaretakerApp and schedule local notifications for them
     */
    private void setupCrossDeviceNotificationListeners() {
        String patientId = authManager.getCurrentPatientId();
        if (patientId == null) {
            android.util.Log.w("MainActivity", "No patient ID available for cross-device notifications");
            return;
        }
        
        // Set up listener for medication reminders from CaretakerApp
        setupRemindersListener(patientId);
        
        // Set up listener for tasks from CaretakerApp  
        setupTasksListener(patientId);
        
        android.util.Log.d("MainActivity", "Cross-device notification listeners set up for patient: " + patientId);
    }
    
    /**
     * Set up real-time listener for medication reminders created by CaretakerApp
     */
    private void setupRemindersListener(String patientId) {
        com.google.firebase.firestore.FirebaseFirestore db = com.google.firebase.firestore.FirebaseFirestore.getInstance();
        
        db.collection("patients")
            .document(patientId)
            .collection("reminders")
            .addSnapshotListener(new com.google.firebase.firestore.EventListener<com.google.firebase.firestore.QuerySnapshot>() {
                @Override
                public void onEvent(com.google.firebase.firestore.QuerySnapshot value, com.google.firebase.firestore.FirebaseFirestoreException error) {
                    if (error != null) {
                        android.util.Log.e("RemindersListener", "Error listening for reminders", error);
                        return;
                    }
                    
                    if (value != null) {
                        for (com.google.firebase.firestore.DocumentChange change : value.getDocumentChanges()) {
                            if (change.getType() == com.google.firebase.firestore.DocumentChange.Type.ADDED) {
                                // New reminder added by CaretakerApp - schedule local notification
                                com.google.firebase.firestore.DocumentSnapshot doc = change.getDocument();
                                scheduleReminderNotification(doc);
                            }
                        }
                    }
                }
            });
    }
    
    /**
     * Set up real-time listener for tasks created by CaretakerApp
     */
    private void setupTasksListener(String patientId) {
        com.google.firebase.firestore.FirebaseFirestore db = com.google.firebase.firestore.FirebaseFirestore.getInstance();
        
        db.collection("patients")
            .document(patientId)
            .collection("tasks")
            .addSnapshotListener(new com.google.firebase.firestore.EventListener<com.google.firebase.firestore.QuerySnapshot>() {
                @Override
                public void onEvent(com.google.firebase.firestore.QuerySnapshot value, com.google.firebase.firestore.FirebaseFirestoreException error) {
                    if (error != null) {
                        android.util.Log.e("TasksListener", "Error listening for tasks", error);
                        return;
                    }
                    
                    if (value != null) {
                        for (com.google.firebase.firestore.DocumentChange change : value.getDocumentChanges()) {
                            if (change.getType() == com.google.firebase.firestore.DocumentChange.Type.ADDED) {
                                // New task added by CaretakerApp - schedule local notification
                                com.google.firebase.firestore.DocumentSnapshot doc = change.getDocument();
                                scheduleTaskNotification(doc);
                            }
                        }
                    }
                }
            });
    }
    
    /**
     * Schedule a local alarm for a medication reminder created by CaretakerApp using the existing alarm system
     */
    private void scheduleReminderNotification(com.google.firebase.firestore.DocumentSnapshot doc) {
        try {
            String title = doc.getString("title");
            String description = doc.getString("description");
            Long scheduledTime = doc.getLong("scheduledTimeEpochMillis");
            Boolean isRepeating = doc.getBoolean("isRepeating");
            
            // Get enhanced data for cross-device reminders
            @SuppressWarnings("unchecked")
            java.util.List<String> medicineNames = (java.util.List<String>) doc.get("medicineNames");
            @SuppressWarnings("unchecked")
            java.util.List<String> imageUrls = (java.util.List<String>) doc.get("imageUrls");
            
            if (title != null && description != null && scheduledTime != null) {
                // Only schedule future alarms
                if (scheduledTime > System.currentTimeMillis()) {
                    // Use the existing AlarmScheduler for proper alarm functionality
                    com.mihir.alzheimerscaregiver.alarm.AlarmScheduler alarmScheduler = 
                        new com.mihir.alzheimerscaregiver.alarm.AlarmScheduler(this);
                    
                    // Create a unique reminder ID for cross-device reminders
                    String reminderId = "caretaker_" + doc.getId();
                    
                    // Handle null values gracefully
                    if (medicineNames == null) medicineNames = new java.util.ArrayList<>();
                    if (imageUrls == null) imageUrls = new java.util.ArrayList<>();
                    if (isRepeating == null) isRepeating = false;
                    
                    boolean scheduled = alarmScheduler.scheduleAlarmWithExtras(
                        reminderId, title, description, scheduledTime, 
                        isRepeating, medicineNames, imageUrls
                    );
                    
                    if (scheduled) {
                        String alarmType = isRepeating ? "Daily repeating alarm" : "Alarm";
                        android.util.Log.d("MainActivity", alarmType + " scheduled from CaretakerApp: " + title + " at " + scheduledTime);
                    } else {
                        android.util.Log.w("MainActivity", "Failed to schedule alarm for reminder: " + title);
                    }
                } else {
                    android.util.Log.d("MainActivity", "Skipping past reminder: " + title);
                }
            }
        } catch (Exception e) {
            android.util.Log.e("MainActivity", "Error scheduling reminder alarm", e);
        }
    }
    
    /**
     * Schedule a local alarm for a task created by CaretakerApp using the existing alarm system
     */
    private void scheduleTaskNotification(com.google.firebase.firestore.DocumentSnapshot doc) {
        try {
            String name = doc.getString("name");
            String description = doc.getString("description");
            Long scheduledTime = doc.getLong("scheduledTimeEpochMillis");
            
            if (name != null && description != null && scheduledTime != null) {
                // Only schedule future alarms
                if (scheduledTime > System.currentTimeMillis()) {
                    // Use the existing AlarmScheduler for proper alarm functionality
                    com.mihir.alzheimerscaregiver.alarm.AlarmScheduler alarmScheduler = 
                        new com.mihir.alzheimerscaregiver.alarm.AlarmScheduler(this);
                    
                    // Create a unique reminder ID for cross-device tasks
                    String reminderId = "caretaker_task_" + doc.getId();
                    
                    boolean scheduled = alarmScheduler.scheduleAlarm(
                        reminderId, name, description, scheduledTime, false
                    );
                    
                    if (scheduled) {
                        android.util.Log.d("MainActivity", "Task alarm scheduled from CaretakerApp: " + name + " at " + scheduledTime);
                    } else {
                        android.util.Log.w("MainActivity", "Failed to schedule alarm for task: " + name);
                    }
                } else {
                    android.util.Log.d("MainActivity", "Skipping past task: " + name);
                }
            }
        } catch (Exception e) {
            android.util.Log.e("MainActivity", "Error scheduling task alarm", e);
        }
    }

    /**
     * Initialize and start geofence monitoring for patient safety
     */
    private void initializeGeofenceMonitoring() {
        try {
            String patientId = authManager.getCurrentPatientId();
            if (patientId == null) {
                Log.w("MainActivity", "No patient ID available for geofence monitoring");
                return;
            }

            // Check location permissions first
            checkLocationPermissions();
            
            Log.i("MainActivity", "🌍 Initializing geofence monitoring for patient: " + patientId);
            
            // Initialize geofence client
            geofenceClient = new PatientGeofenceClient(this, patientId);
            Log.i("MainActivity", "✅ PatientGeofenceClient created successfully");
            
            // Start geofence monitoring
            geofenceClient.startGeofenceMonitoring();
            Log.i("MainActivity", "✅ Geofence monitoring started");
            
            Log.d("MainActivity", "Geofence monitoring initialized for patient: " + patientId);
            
            // Show a toast to inform the user that geofence monitoring has started
            Toast.makeText(this, "Safety monitoring started", Toast.LENGTH_SHORT).show();
            
            // Test geofence exit after 10 seconds
            new android.os.Handler().postDelayed(() -> {
                Log.i("MainActivity", "🧪 Auto-testing geofence exit in 10 seconds...");
                if (geofenceClient != null) {
                    testGeofenceExit();
                }
            }, 10000);
            
        } catch (Exception e) {
            Log.e("MainActivity", "Error initializing geofence monitoring", e);
        }
    }
    
    /**
     * Add a test method for debugging boot receiver functionality
     */
    private void addBootReceiverTestButton() {
        // Test LocationBootReceiver by long-pressing the location card
        if (locationCard != null) {
            locationCard.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    testLocationBootReceiver();
                    return true;
                }
            });
        }
    }
    
    private void testLocationBootReceiver() {
        Log.i("MainActivity", "*** TESTING LocationBootReceiver manually ***");
        Toast.makeText(this, "Testing LocationBootReceiver - check logs", Toast.LENGTH_SHORT).show();
        
        try {
            // Check if receiver is registered in manifest
            android.content.pm.PackageManager pm = getPackageManager();
            android.content.pm.PackageInfo packageInfo = pm.getPackageInfo(getPackageName(), 
                android.content.pm.PackageManager.GET_RECEIVERS);
            
            if (packageInfo.receivers != null) {
                Log.i("MainActivity", "Found " + packageInfo.receivers.length + " receivers in manifest");
                for (android.content.pm.ActivityInfo receiver : packageInfo.receivers) {
                    Log.i("MainActivity", "Receiver: " + receiver.name + " (enabled=" + receiver.enabled + ", exported=" + receiver.exported + ")");
                    if (receiver.name.contains("LocationBootReceiver")) {
                        Log.i("MainActivity", "✅ LocationBootReceiver found in manifest!");
                    }
                }
            }
            
            // Test manual instantiation
            Log.i("MainActivity", "Testing manual LocationBootReceiver instantiation...");
            com.mihir.alzheimerscaregiver.location.LocationBootReceiver testReceiver = 
                new com.mihir.alzheimerscaregiver.location.LocationBootReceiver();
            Log.i("MainActivity", "✅ LocationBootReceiver instantiated successfully");
            
            // Create and send custom broadcast to test the receiver
            Intent testIntent = new Intent("com.mihir.alzheimerscaregiver.TEST_BOOT_RECEIVER");
            testIntent.setPackage(getPackageName());
            sendBroadcast(testIntent);
            Log.i("MainActivity", "Test broadcast sent to LocationBootReceiver");
            
            // Also test with BOOT_COMPLETED action
            Intent bootIntent = new Intent(Intent.ACTION_BOOT_COMPLETED);
            bootIntent.setPackage(getPackageName());
            sendBroadcast(bootIntent);
            Log.i("MainActivity", "BOOT_COMPLETED broadcast sent to LocationBootReceiver");
            
            // Test manual call
            testReceiver.onReceive(this, bootIntent);
            Log.i("MainActivity", "Manual onReceive call completed");
            
            // Also schedule aggressive JobService for stopped app compatibility
            scheduleAggressiveBootJob();
            
        } catch (Exception e) {
            Log.e("MainActivity", "Error testing LocationBootReceiver", e);
            Toast.makeText(this, "Error testing receiver: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
    
    /**
     * Schedule aggressive boot job for stopped app compatibility
     */
    private void scheduleAggressiveBootJob() {
        try {
            Log.i("MainActivity", "Scheduling aggressive boot job for stopped app compatibility...");
            
            android.app.job.JobScheduler jobScheduler = 
                (android.app.job.JobScheduler) getSystemService(JOB_SCHEDULER_SERVICE);
            
            if (jobScheduler == null) {
                Log.e("MainActivity", "JobScheduler not available");
                return;
            }
            
            // Cancel existing jobs
            jobScheduler.cancel(1001);
            jobScheduler.cancel(1002);
            
            android.content.ComponentName serviceName = 
                new android.content.ComponentName(this, com.mihir.alzheimerscaregiver.location.LocationBootJobService.class);
            
            android.os.PersistableBundle extras = new android.os.PersistableBundle();
            extras.putString("reason", "manual_aggressive_schedule");
            extras.putLong("scheduled_time", System.currentTimeMillis());
            
            // Create very aggressive boot job
            android.app.job.JobInfo bootJob = new android.app.job.JobInfo.Builder(1001, serviceName)
                    .setRequiredNetworkType(android.app.job.JobInfo.NETWORK_TYPE_NONE)
                    .setPersisted(true) // Critical: survive reboots even for stopped apps
                    .setRequiresCharging(false)
                    .setRequiresDeviceIdle(false)
                    .setRequiresBatteryNotLow(false)
                    .setMinimumLatency(500) // Start immediately after boot
                    .setOverrideDeadline(15000) // Must run within 15 seconds
                    .setExtras(extras)
                    .build();
            
            int result = jobScheduler.schedule(bootJob);
            
            if (result == android.app.job.JobScheduler.RESULT_SUCCESS) {
                Log.i("MainActivity", "✅ Aggressive boot job scheduled successfully!");
                Toast.makeText(this, "✅ Boot restart job scheduled!", Toast.LENGTH_SHORT).show();
                
                // Also schedule periodic backup
                schedulePeriodicBackup(jobScheduler, serviceName);
                
            } else {
                Log.e("MainActivity", "❌ Failed to schedule boot job");
                Toast.makeText(this, "❌ Failed to schedule boot job", Toast.LENGTH_SHORT).show();
            }
            
        } catch (Exception e) {
            Log.e("MainActivity", "Error scheduling aggressive boot job", e);
        }
    }
    
    /**
     * Schedule periodic backup job
     */
    private void schedulePeriodicBackup(android.app.job.JobScheduler jobScheduler, 
                                      android.content.ComponentName serviceName) {
        try {
            android.os.PersistableBundle extras = new android.os.PersistableBundle();
            extras.putString("reason", "periodic_backup");
            
            android.app.job.JobInfo periodicJob = new android.app.job.JobInfo.Builder(1002, serviceName)
                    .setRequiredNetworkType(android.app.job.JobInfo.NETWORK_TYPE_NONE)
                    .setPersisted(true)
                    .setRequiresCharging(false)
                    .setRequiresDeviceIdle(false)
                    .setPeriodic(24 * 60 * 60 * 1000) // Daily check
                    .setExtras(extras)
                    .build();
            
            jobScheduler.schedule(periodicJob);
            Log.i("MainActivity", "✅ Periodic backup job scheduled");
            
        } catch (Exception e) {
            Log.e("MainActivity", "Error scheduling periodic job", e);
        }
    }
    
    /**
     * Test method to manually trigger a geofence exit event for debugging
     */
    private void testGeofenceExit() {
        try {
            String patientId = authManager.getCurrentPatientId();
            if (patientId == null) {
                Log.w("MainActivity", "No patient ID available for geofence test");
                Toast.makeText(this, "No patient ID available", Toast.LENGTH_SHORT).show();
                return;
            }
            
            if (geofenceClient != null) {
                // Get the first available geofence ID from Firebase
                // For testing, we'll simulate an exit from any existing geofence
                FirebaseDatabase.getInstance()
                    .getReference("patients")
                    .child(patientId)
                    .child("geofences")
                    .addListenerForSingleValueEvent(new com.google.firebase.database.ValueEventListener() {
                        @Override
                        public void onDataChange(com.google.firebase.database.DataSnapshot snapshot) {
                            if (snapshot.exists() && snapshot.hasChildren()) {
                                String testGeofenceId = "";
                                String geofenceName = "Test Zone";
                                
                                for (com.google.firebase.database.DataSnapshot geofenceSnapshot : snapshot.getChildren()) {
                                    testGeofenceId = geofenceSnapshot.getKey();
                                    Map<String, Object> data = (Map<String, Object>) geofenceSnapshot.getValue();
                                    if (data != null) {
                                        geofenceName = (String) data.getOrDefault("label", "Unknown Zone");
                                    }
                                    break; // Just use the first one for testing
                                }
                                
                                // Simulate current location outside the safe zone
                                double currentLat = 12.9716 + 0.02; // Move slightly outside
                                double currentLng = 77.5946 + 0.02;
                                
                                Log.d("MainActivity", "Testing geofence exit notification for: " + geofenceName);
                                Toast.makeText(MainActivity.this, "Testing exit from: " + geofenceName, Toast.LENGTH_SHORT).show();
                                
                                // Manually trigger the geofence transition handler
                                geofenceClient.handleGeofenceTransition(
                                    testGeofenceId, 
                                    com.google.android.gms.location.Geofence.GEOFENCE_TRANSITION_EXIT,
                                    currentLat, 
                                    currentLng
                                );
                                
                                Log.d("MainActivity", "✅ Geofence exit test triggered successfully");
                            } else {
                                Toast.makeText(MainActivity.this, "No geofences found to test", Toast.LENGTH_SHORT).show();
                            }
                        }
                        
                        @Override
                        public void onCancelled(com.google.firebase.database.DatabaseError error) {
                            Log.e("MainActivity", "Error loading geofences for test", error.toException());
                        }
                    });
            } else {
                Log.w("MainActivity", "GeofenceClient is null");
            }
        } catch (Exception e) {
            Log.e("MainActivity", "Error testing geofence exit", e);
            Toast.makeText(this, "Error testing geofence: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
    
    /**
     * Check and request location permissions for geofencing
     */
    private void checkLocationPermissions() {
        boolean needsFineLocation = androidx.core.content.ContextCompat.checkSelfPermission(this, 
                android.Manifest.permission.ACCESS_FINE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED;
        
        boolean needsCoarseLocation = androidx.core.content.ContextCompat.checkSelfPermission(this, 
                android.Manifest.permission.ACCESS_COARSE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED;
        
        if (needsFineLocation || needsCoarseLocation) {
            // Request location permissions
            androidx.activity.result.ActivityResultLauncher<String[]> locationPermissionLauncher = 
                registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions(),
                    permissions -> {
                        boolean fineLocationGranted = permissions.getOrDefault(android.Manifest.permission.ACCESS_FINE_LOCATION, false);
                        boolean coarseLocationGranted = permissions.getOrDefault(android.Manifest.permission.ACCESS_COARSE_LOCATION, false);
                        
                        if (fineLocationGranted && coarseLocationGranted) {
                            Log.d("MainActivity", "Location permissions granted for geofencing");
                            
                            // Check for background location permission on Android 10+
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                                checkBackgroundLocationPermission();
                            }
                        } else {
                            Log.w("MainActivity", "Location permissions denied - geofencing may not work");
                            Toast.makeText(this, "Location permissions required for safety monitoring", Toast.LENGTH_LONG).show();
                        }
                    });
            
            locationPermissionLauncher.launch(new String[]{
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            });
        } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            // Location permissions already granted, check background location
            checkBackgroundLocationPermission();
        }
    }
    
    /**
     * Check and request background location permission for Android 10+
     */
    private void checkBackgroundLocationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            boolean needsBackgroundLocation = androidx.core.content.ContextCompat.checkSelfPermission(this, 
                    android.Manifest.permission.ACCESS_BACKGROUND_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED;
            
            if (needsBackgroundLocation) {
                androidx.activity.result.ActivityResultLauncher<String> backgroundLocationLauncher = 
                    registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
                        isGranted -> {
                            if (isGranted) {
                                Log.d("MainActivity", "Background location permission granted");
                                Toast.makeText(this, "Background location monitoring enabled", Toast.LENGTH_SHORT).show();
                            } else {
                                Log.w("MainActivity", "Background location permission denied - geofencing may be limited");
                                Toast.makeText(this, "For best safety monitoring, please enable 'Allow all the time' location access", Toast.LENGTH_LONG).show();
                            }
                        });
                
                backgroundLocationLauncher.launch(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION);
            }
        }
    }
    
    /**
     * Run Enhanced MMSE Testing Suite
     * 
     * This method initiates comprehensive testing of the Enhanced MMSE system:
     * - AI question generation quality (40/30/30 distribution)
     * - Answer evaluation accuracy (synonyms, partial credit)
     * - Performance and error handling
     * 
     * Results are displayed in Toast messages and detailed logs in logcat.
     */
    private void runEnhancedMMSETests() {
        Log.d("MainActivity", "🚀 Starting Enhanced MMSE Testing Suite...");
        
        Toast.makeText(this, "Starting Enhanced MMSE Tests\nCheck logcat for detailed results", 
                      Toast.LENGTH_LONG).show();
        
        try {
            // Initialize the tester
            EnhancedMMSETester tester = new EnhancedMMSETester(this);
            
            // Show initial test status
            showToast("Running Question Generation Test...");
            
            // Run question generation quality test
            tester.testQuestionGenerationQuality();
            
            // Schedule evaluation test after a delay to avoid overwhelming API
            new android.os.Handler().postDelayed(() -> {
                showToast("Running Answer Evaluation Test...");
                tester.testAnswerEvaluationAccuracy();
            }, 8000); // 8 second delay
            
            // Schedule performance test
            new android.os.Handler().postDelayed(() -> {
                showToast("Running Performance Test...");
                tester.testPerformanceAndErrorHandling();
                
                // Final summary
                new android.os.Handler().postDelayed(() -> {
                    showToast("Enhanced MMSE Tests Completed!\nCheck logcat for detailed results:\nadb logcat | grep 'EnhancedMMSE'");
                }, 5000);
            }, 15000); // 15 second delay
            
            Log.d("MainActivity", "📊 Enhanced MMSE Testing Suite initiated successfully");
            Log.d("MainActivity", "Monitor progress with: adb logcat | grep 'EnhancedMMSE'");
            
        } catch (Exception e) {
            Log.e("MainActivity", "❌ Failed to start Enhanced MMSE tests: " + e.getMessage());
            showToast("Failed to start tests: " + e.getMessage());
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        // Clean up geofence monitoring when activity is destroyed
        if (geofenceClient != null) {
            geofenceClient.stopGeofenceMonitoring();
        }
    }
}