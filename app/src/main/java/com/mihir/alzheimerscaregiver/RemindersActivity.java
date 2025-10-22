package com.mihir.alzheimerscaregiver;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.Toast;
import android.os.Build;
import android.provider.Settings;
import android.content.Intent;
import android.app.AlarmManager;
import android.content.Context;
import android.net.Uri;
import android.widget.Button;
import android.widget.LinearLayout;
import com.mihir.alzheimerscaregiver.notifications.MissedMedicationScheduler;
import android.widget.TextView;
import android.text.InputType;
import java.util.List;
import java.util.ArrayList;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.mihir.alzheimerscaregiver.alarm.AlarmScheduler;
import com.mihir.alzheimerscaregiver.data.entity.ReminderEntity;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.mihir.alzheimerscaregiver.ui.reminders.ReminderEntityAdapter;
import com.mihir.alzheimerscaregiver.ui.reminders.MedicineImageAdapter;
import com.mihir.alzheimerscaregiver.ui.viewmodel.ReminderViewModel;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class RemindersActivity extends AppCompatActivity implements ReminderEntityAdapter.OnReminderInteractionListener {

    private ImageButton backButton;
    private ImageButton debugButton;
    private RecyclerView remindersRecyclerView;
    private FloatingActionButton addReminderFab;

    private ReminderEntityAdapter adapter;
    private ReminderViewModel viewModel;

    public static final String EXTRA_MEDICATION_MODE = "EXTRA_MEDICATION_MODE";
    private boolean medicationMode = false;

    // Fields for enhanced reminder dialog
    private ActivityResultLauncher<Intent> imagePickerLauncher;
    private MedicineImageAdapter currentImageAdapter;
    private java.util.List<String> currentImageUrls;
    private java.util.List<String> currentMedicineNames;

    // Call this before scheduling an exact alarm
    private boolean checkAndRequestExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // Android 12+
            AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            if (alarmManager != null && !alarmManager.canScheduleExactAlarms()) {
                Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                intent.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
                startActivity(intent);
                toast("Please grant permission to schedule alarms");
                return false;
            }
        }
        return true;
    }
    
    // Request notification permission for Android 13+
    private static final int REQUEST_NOTIFICATION_PERMISSION = 1001;
    private boolean checkAndRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33) { // Android 13+
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != 
                PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 
                    REQUEST_NOTIFICATION_PERMISSION);
                toast("Please grant notification permission");
                return false;
            }
        }
        return true;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reminders);

        // Initialize image picker
        initializeImagePicker();

        // Check for alarm permissions as early as possible
        checkAndRequestExactAlarmPermission();
        
        // Also check for notification permissions on Android 13+
        checkAndRequestNotificationPermission();
        
        backButton = findViewById(R.id.backButton);
        debugButton = findViewById(R.id.debugButton);
        remindersRecyclerView = findViewById(R.id.remindersRecyclerView);
        addReminderFab = findViewById(R.id.addReminderFab);

        remindersRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ReminderEntityAdapter();
        adapter.setListener(this);
        remindersRecyclerView.setAdapter(adapter);

        medicationMode = getIntent().getBooleanExtra(EXTRA_MEDICATION_MODE, false);

        ItemTouchHelper helper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
            @Override
            public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target) { return false; }

            @Override
            public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getAdapterPosition();
                onItemSwipedToDelete(adapter.getItem(position), position);
            }
        });
        helper.attachToRecyclerView(remindersRecyclerView);

        viewModel = new ViewModelProvider(this).get(ReminderViewModel.class);
        
        // Observe error messages
        viewModel.getErrorMessage().observe(this, error -> {
            if (error != null && !error.isEmpty()) {
                Toast.makeText(this, "Error: " + error, Toast.LENGTH_LONG).show();
            }
        });
        
        viewModel.getAllReminders().observe(this, reminders -> {
            if (reminders == null) {
                adapter.submitList(null);
                return;
            }
            java.util.List<com.mihir.alzheimerscaregiver.data.entity.ReminderEntity> activeReminders = new java.util.ArrayList<>();
            for (com.mihir.alzheimerscaregiver.data.entity.ReminderEntity r : reminders) {
                // Show reminders that aren't completed today
                // For non-repeating reminders, check isCompleted
                // For repeating reminders, check isCompletedToday()
                if (r.isRepeating) {
                    // Always show repeating reminders (completion status checked individually)
                    activeReminders.add(r);
                } else if (!r.isCompleted) {
                    // Show non-repeating reminders that aren't permanently completed
                    activeReminders.add(r);
                }
            }
            adapter.submitList(activeReminders);
        });

        backButton.setOnClickListener(v -> finish());
        debugButton.setOnClickListener(v -> testAlarm());
        addReminderFab.setOnClickListener(v -> showAddOrEditDialog(null));
    }

    @Override
    public void onCompletionToggled(ReminderEntity reminder) {
        // For repeating reminders, use isCompletedToday(); for others, use isCompleted
        boolean completionStatus = reminder.isRepeating ? reminder.isCompletedToday() : reminder.isCompleted;
        viewModel.markCompleted(reminder.id, completionStatus);
    }

    @Override
    public void onItemClicked(ReminderEntity reminder) {
        showAddOrEditDialog(reminder);
    }

    @Override
    public void onItemSwipedToDelete(ReminderEntity reminder, int position) {
        new AlertDialog.Builder(this)
                .setTitle("Delete reminder")
                .setMessage("Are you sure you want to delete this reminder?")
                .setPositiveButton("Delete", (d, w) -> viewModel.delete(reminder))
                .setNegativeButton("Cancel", (d, w) -> adapter.notifyItemChanged(position))
                .setOnCancelListener(dialog -> adapter.notifyItemChanged(position))
                .show();
    }

    private void initializeImagePicker() {
        imagePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri imageUri = result.getData().getData();
                    if (imageUri != null && currentImageUrls != null) {
                        // Take persistent URI permission so the image remains accessible
                        try {
                            getContentResolver().takePersistableUriPermission(imageUri, 
                                Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        } catch (SecurityException e) {
                            // Some URIs may not support persistent permissions
                            android.util.Log.w("RemindersActivity", "Could not take persistent permission for URI: " + imageUri);
                        }
                        
                        currentImageUrls.add(imageUri.toString());
                        if (currentImageAdapter != null) {
                            currentImageAdapter.notifyItemInserted(currentImageUrls.size() - 1);
                        }
                    }
                }
            }
        );
    }

    private void showAddOrEditDialog(ReminderEntity existing) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_add_edit_reminder, null, false);
        EditText inputTitle = view.findViewById(R.id.inputTitle);
        EditText inputDescription = view.findViewById(R.id.inputDescription);
        EditText inputDateTime = view.findViewById(R.id.inputDateTime);
        CheckBox checkCompleted = view.findViewById(R.id.checkCompleted);
        CheckBox checkRepeating = view.findViewById(R.id.checkRepeating);
        
        // Enhanced components for medicine management
        LinearLayout medicineNamesContainer = view.findViewById(R.id.medicineNamesContainer);
        Button btnAddMedicine = view.findViewById(R.id.btnAddMedicine);
        Button btnAddImage = view.findViewById(R.id.btnAddImage);
        RecyclerView imagesRecyclerView = view.findViewById(R.id.imagesRecyclerView);
        
        // Collections to track medicine names and images
        List<String> medicineNames = new ArrayList<>();
        currentImageUrls = new ArrayList<>();
        
        // Setup images RecyclerView
        imagesRecyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        currentImageAdapter = new MedicineImageAdapter(this, currentImageUrls);
        currentImageAdapter.setOnImageActionListener(new MedicineImageAdapter.OnImageActionListener() {
            @Override
            public void onImageRemoved(int position) {
                if (position < currentImageUrls.size()) {
                    currentImageUrls.remove(position);
                    currentImageAdapter.notifyItemRemoved(position);
                    toast("Image removed");
                }
            }
        });
        imagesRecyclerView.setAdapter(currentImageAdapter);
        
        // Add Medicine button functionality
        btnAddMedicine.setOnClickListener(v -> {
            addMedicineNameField(medicineNamesContainer);
        });
        
        // Add Image button functionality
        btnAddImage.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            imagePickerLauncher.launch(intent);
        });
        
        final Long[] scheduledAt = {null};
        inputTitle.setHint("Medicine (e.g., Aspirin)");
        inputDescription.setHint("Dosage (e.g., 1 tablet)");
        if (existing != null) {
            // Load basic reminder data
            inputTitle.setText(existing.title);
            inputDescription.setText(existing.description);
            checkCompleted.setChecked(existing.isCompleted);
            checkRepeating.setChecked(existing.isRepeating);
            if (existing.scheduledTimeEpochMillis != null) {
                scheduledAt[0] = existing.scheduledTimeEpochMillis;
                inputDateTime.setText(new SimpleDateFormat("EEE, MMM d h:mm a", Locale.getDefault())
                        .format(new Date(existing.scheduledTimeEpochMillis)));
            }
            
            // Load existing medicine names
            if (existing.medicineNames != null && !existing.medicineNames.isEmpty()) {
                // Set first medicine name in the main input field
                inputTitle.setText(existing.medicineNames.get(0));
                
                // Add additional medicine name fields for remaining medicines
                for (int i = 1; i < existing.medicineNames.size(); i++) {
                    addMedicineNameField(medicineNamesContainer);
                    // Get the last added field and set its text
                    EditText lastField = (EditText) medicineNamesContainer.getChildAt(medicineNamesContainer.getChildCount() - 1);
                    lastField.setText(existing.medicineNames.get(i));
                }
            }
            
            // Load existing images
            if (existing.imageUrls != null && !existing.imageUrls.isEmpty()) {
                currentImageUrls.clear();
                currentImageUrls.addAll(existing.imageUrls);
                currentImageAdapter.notifyDataSetChanged();
            }
        }

        inputDateTime.setOnClickListener(v -> pickDateTime(scheduledAt, inputDateTime));

        new AlertDialog.Builder(this)
                .setTitle(existing == null ? "Add reminder" : "Edit reminder")
                .setView(view)
                .setPositiveButton(existing == null ? "Add" : "Save", (dialog, which) -> {
                    // Collect all medicine names
                    medicineNames.clear();
                    String firstMedicine = inputTitle.getText().toString().trim();
                    if (!TextUtils.isEmpty(firstMedicine)) {
                        medicineNames.add(firstMedicine);
                    }
                    
                    // Collect additional medicine names from dynamically added fields
                    for (int i = 1; i < medicineNamesContainer.getChildCount(); i++) {
                        View child = medicineNamesContainer.getChildAt(i);
                        if (child instanceof EditText) {
                            String medicineName = ((EditText) child).getText().toString().trim();
                            if (!TextUtils.isEmpty(medicineName)) {
                                medicineNames.add(medicineName);
                            }
                        }
                    }
                    
                    if (medicineNames.isEmpty()) { 
                        toast("At least one medicine name required"); 
                        return; 
                    }
                    
                    String title = medicineNames.get(0); // Use first medicine as title for backward compatibility
                    String desc = emptyToNull(inputDescription.getText().toString().trim());
                    boolean completed = checkCompleted.isChecked();
                    boolean repeating = checkRepeating.isChecked();
                    
                    if (existing == null) {
                        ReminderEntity entity = new ReminderEntity(title, desc, scheduledAt[0], completed, repeating);
                        // Store multiple medicine names and images
                        entity.medicineNames = new ArrayList<>(medicineNames);
                        entity.imageUrls = new ArrayList<>(currentImageUrls);
                        toast("Inserting reminder with " + medicineNames.size() + " medicine(s) and " + currentImageUrls.size() + " image(s): " + title);
                        viewModel.insert(entity);
                        
                        // Schedule alarm for non-completed reminders with a scheduled time
                        if (!completed && scheduledAt[0] != null) {
                            if (checkAndRequestExactAlarmPermission()) {
                                // Use the enhanced AlarmScheduler with medicine names and images
                                AlarmScheduler alarmScheduler = new AlarmScheduler(this);
                                boolean scheduled = alarmScheduler.scheduleAlarmWithExtras(entity.id, title, desc, scheduledAt[0], repeating, entity.medicineNames, entity.imageUrls);
                                
                                if (scheduled) {
                                    String alarmType = repeating ? "Daily repeating alarm" : "Alarm";
                                    toast(alarmType + " scheduled for: " + new SimpleDateFormat("EEE, MMM d h:mm a", 
                                          Locale.getDefault()).format(new Date(scheduledAt[0])));
                                    
                                    // NEW: Schedule missed medication check (5 minutes after medication time)
                                    if (entity.medicineNames != null && !entity.medicineNames.isEmpty()) {
                                        MissedMedicationScheduler missedScheduler = new MissedMedicationScheduler(this);
                                        missedScheduler.scheduleMissedMedicationCheck(entity);
                                        android.util.Log.d("RemindersActivity", "Scheduled missed medication check for: " + entity.title);
                                    }
                                } else {
                                    toast("Failed to schedule alarm");
                                }
                            }
                        }
                    } else {
                        existing.title = title;
                        existing.description = desc;
                        existing.scheduledTimeEpochMillis = scheduledAt[0];
                        existing.isCompleted = completed;
                        existing.isRepeating = repeating;
                        // Update medicine names and images
                        existing.medicineNames = new ArrayList<>(medicineNames);
                        existing.imageUrls = new ArrayList<>(currentImageUrls);
                        toast("Updating reminder with " + medicineNames.size() + " medicine(s) and " + currentImageUrls.size() + " image(s): " + title);
                        viewModel.update(existing);
                        
                        // For existing reminders, reschedule or cancel alarm as needed
                        if (!completed && scheduledAt[0] != null) {
                            if (checkAndRequestExactAlarmPermission()) {
                                // First cancel any existing alarm
                                AlarmScheduler alarmScheduler = new AlarmScheduler(this);
                                alarmScheduler.cancelAlarm(existing.id);
                                
                                // Then schedule the new alarm time with enhanced data
                                boolean scheduled = alarmScheduler.scheduleAlarmWithExtras(existing.id, title, desc, scheduledAt[0], repeating, existing.medicineNames, existing.imageUrls);
                                
                                if (scheduled) {
                                    String alarmType = repeating ? "Daily repeating alarm" : "Alarm";
                                    toast(alarmType + " rescheduled for: " + new SimpleDateFormat("EEE, MMM d h:mm a", 
                                          Locale.getDefault()).format(new Date(scheduledAt[0])));
                                    
                                    // NEW: Schedule missed medication check for updated reminder
                                    if (existing.medicineNames != null && !existing.medicineNames.isEmpty()) {
                                        MissedMedicationScheduler missedScheduler = new MissedMedicationScheduler(this);
                                        missedScheduler.scheduleMissedMedicationCheck(existing);
                                        android.util.Log.d("RemindersActivity", "Scheduled missed medication check for updated reminder: " + existing.title);
                                    }
                                } else {
                                    toast("Failed to schedule alarm");
                                }
                            }
                        } else if ((completed && !repeating) || scheduledAt[0] == null) {
                            // If marked as completed (and not repeating) or time removed, cancel any existing alarm
                            AlarmScheduler alarmScheduler = new AlarmScheduler(this);
                            alarmScheduler.cancelAlarm(existing.id);
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void pickDateTime(Long[] scheduledAt, EditText input) {
        Calendar now = Calendar.getInstance();
        DatePickerDialog datePicker = new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            Calendar selected = Calendar.getInstance();
            selected.set(Calendar.YEAR, year);
            selected.set(Calendar.MONTH, month);
            selected.set(Calendar.DAY_OF_MONTH, dayOfMonth);
            TimePickerDialog timePicker = new TimePickerDialog(this, (timeView, hour, minute) -> {
                selected.set(Calendar.HOUR_OF_DAY, hour);
                selected.set(Calendar.MINUTE, minute);
                selected.set(Calendar.SECOND, 0);
                selected.set(Calendar.MILLISECOND, 0);
                scheduledAt[0] = selected.getTimeInMillis();
                input.setText(new SimpleDateFormat("EEE, MMM d h:mm a", Locale.getDefault()).format(selected.getTime()));
            }, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), false);
            timePicker.show();
        }, now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH));
        datePicker.show();
    }

    private String emptyToNull(String s) { return TextUtils.isEmpty(s) ? null : s; }
    private void testAlarm() {
        // Check both required permissions
        if (checkAndRequestExactAlarmPermission() && checkAndRequestNotificationPermission()) {
            AlarmScheduler alarmScheduler = new AlarmScheduler(this);
            boolean scheduled = alarmScheduler.scheduleTestAlarm();
            
            if (scheduled) {
                toast("Test alarm scheduled! Should trigger in 10 seconds");
            } else {
                toast("Failed to schedule test alarm");
            }
        } else {
            toast("Cannot schedule test alarm - permissions not granted");
        }
    }
    

    
    private void addMedicineNameField(LinearLayout container) {
        EditText medicineInput = new EditText(this);
        medicineInput.setHint("Medicine Name (e.g., Ibuprofen)");
        medicineInput.setInputType(InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        
        // Set layout parameters
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.topMargin = 16; // Add some margin between fields
        medicineInput.setLayoutParams(params);
        
        container.addView(medicineInput);
    }
    
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
}


