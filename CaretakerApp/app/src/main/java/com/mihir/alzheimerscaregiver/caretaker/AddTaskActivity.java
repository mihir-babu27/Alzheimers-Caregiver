package com.mihir.alzheimerscaregiver.caretaker;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.FirebaseFirestore;
import com.mihir.alzheimerscaregiver.caretaker.R;
import com.mihir.alzheimerscaregiver.caretaker.data.entity.TaskEntity;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;


public class AddTaskActivity extends AppCompatActivity {

    private static final String TAG = "AddTaskActivity";
    
    // Basic UI elements
    private EditText taskNameEditText, taskDescriptionEditText, taskCategoryEditText, dueTimeEditText;
    private Button saveButton, cancelButton;
    private ProgressBar progressBar;
    private FirebaseFirestore db;
    private String patientId;

    // Enhanced UI elements
    private CheckBox checkTaskRepeating, checkTaskEnableAlarm, checkTaskCaretakerNotification;
    private TextView labelDaysOfWeek;
    private LinearLayout layoutDaysOfWeek, layoutQuickRepeat;
    private ToggleButton[] dayButtons;
    private Button buttonDaily, buttonWeekdays, buttonWeekends;
    private Long scheduledTimeMillis;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_task);

        Log.d(TAG, "AddTaskActivity created");

        // Get patient ID from intent
        patientId = getIntent().getStringExtra("patientId");
        if (patientId == null) {
            Toast.makeText(this, "Error: Patient ID not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Initialize Firebase
        db = FirebaseFirestore.getInstance();

        // Initialize all views
        initializeViews();
        
        // Set up enhanced functionality
        setupEnhancedFeatures();
    }
    
    /**
     * Initialize all UI elements from the enhanced layout
     */
    private void initializeViews() {
        // Basic task fields
        taskNameEditText = findViewById(R.id.taskNameEditText);
        taskDescriptionEditText = findViewById(R.id.taskDescriptionEditText);
        taskCategoryEditText = findViewById(R.id.taskCategoryEditText);
        dueTimeEditText = findViewById(R.id.dueTimeEditText);
        saveButton = findViewById(R.id.saveButton);
        cancelButton = findViewById(R.id.cancelButton);
        progressBar = findViewById(R.id.progressBar);

        // Enhanced settings
        checkTaskRepeating = findViewById(R.id.checkTaskRepeating);
        checkTaskEnableAlarm = findViewById(R.id.checkTaskEnableAlarm);
        checkTaskCaretakerNotification = findViewById(R.id.checkTaskCaretakerNotification);
        
        // Day selection
        labelDaysOfWeek = findViewById(R.id.labelDaysOfWeek);
        layoutDaysOfWeek = findViewById(R.id.layoutDaysOfWeek);
        layoutQuickRepeat = findViewById(R.id.layoutQuickRepeat);
        
        // Day toggle buttons
        dayButtons = new ToggleButton[7];
        dayButtons[0] = findViewById(R.id.buttonSunday);
        dayButtons[1] = findViewById(R.id.buttonMonday);
        dayButtons[2] = findViewById(R.id.buttonTuesday);
        dayButtons[3] = findViewById(R.id.buttonWednesday);
        dayButtons[4] = findViewById(R.id.buttonThursday);
        dayButtons[5] = findViewById(R.id.buttonFriday);
        dayButtons[6] = findViewById(R.id.buttonSaturday);
        
        // Quick repeat buttons
        buttonDaily = findViewById(R.id.buttonDaily);
        buttonWeekdays = findViewById(R.id.buttonWeekdays);
        buttonWeekends = findViewById(R.id.buttonWeekends);
    }
    
    /**
     * Set up all enhanced functionality and listeners
     */
    private void setupEnhancedFeatures() {
        // Main action buttons
        saveButton.setOnClickListener(v -> attemptEnhancedSave());
        cancelButton.setOnClickListener(v -> finish());
        
        // Date/time picker
        dueTimeEditText.setOnClickListener(v -> pickDateTimeEnhanced());
        
        // Show/hide day selection based on repeating checkbox
        checkTaskRepeating.setOnCheckedChangeListener((buttonView, isChecked) -> {
            int visibility = isChecked ? View.VISIBLE : View.GONE;
            labelDaysOfWeek.setVisibility(visibility);
            layoutDaysOfWeek.setVisibility(visibility);
            layoutQuickRepeat.setVisibility(visibility);
        });
        
        // Quick repeat button handlers
        buttonDaily.setOnClickListener(v -> {
            for (ToggleButton btn : dayButtons) {
                btn.setChecked(true);
            }
        });
        
        buttonWeekdays.setOnClickListener(v -> {
            dayButtons[0].setChecked(false); // Sunday
            dayButtons[1].setChecked(true);  // Monday
            dayButtons[2].setChecked(true);  // Tuesday
            dayButtons[3].setChecked(true);  // Wednesday
            dayButtons[4].setChecked(true);  // Thursday
            dayButtons[5].setChecked(true);  // Friday
            dayButtons[6].setChecked(false); // Saturday
        });
        
        buttonWeekends.setOnClickListener(v -> {
            dayButtons[0].setChecked(true);  // Sunday
            dayButtons[1].setChecked(false); // Monday
            dayButtons[2].setChecked(false); // Tuesday
            dayButtons[3].setChecked(false); // Wednesday
            dayButtons[4].setChecked(false); // Thursday
            dayButtons[5].setChecked(false); // Friday
            dayButtons[6].setChecked(true);  // Saturday
        });
        
        // Set default values
        checkTaskEnableAlarm.setChecked(true);
        checkTaskCaretakerNotification.setChecked(true);
    }
    
    /**
     * Enhanced date/time picker for the main activity
     */
    private void pickDateTimeEnhanced() {
        Calendar now = Calendar.getInstance();
        DatePickerDialog datePicker = new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            Calendar selected = Calendar.getInstance();
            selected.set(Calendar.YEAR, year);
            selected.set(Calendar.MONTH, month);
            selected.set(Calendar.DAY_OF_MONTH, dayOfMonth);

            TimePickerDialog timePicker = new TimePickerDialog(this, (timeView, hourOfDay, minute) -> {
                selected.set(Calendar.HOUR_OF_DAY, hourOfDay);
                selected.set(Calendar.MINUTE, minute);
                selected.set(Calendar.SECOND, 0);
                selected.set(Calendar.MILLISECOND, 0);

                scheduledTimeMillis = selected.getTimeInMillis();
                dueTimeEditText.setText(new SimpleDateFormat("EEE, MMM d h:mm a", Locale.getDefault()).format(selected.getTime()));
            }, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), false);

            timePicker.show();
        }, now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH));
        datePicker.show();
    }
    
    /**
     * Save the enhanced task with all new features
     */
    private void attemptEnhancedSave() {
        String taskName = taskNameEditText.getText().toString().trim();
        if (TextUtils.isEmpty(taskName)) {
            Toast.makeText(this, "Task name required", Toast.LENGTH_SHORT).show();
            return;
        }
        
        String description = taskDescriptionEditText.getText().toString().trim();
        String category = taskCategoryEditText.getText().toString().trim();
        boolean repeating = checkTaskRepeating.isChecked();
        boolean enableAlarm = checkTaskEnableAlarm.isChecked();
        boolean enableCaretakerNotif = checkTaskCaretakerNotification.isChecked();
        
        // Create enhanced task with all new fields
        TaskEntity task = new TaskEntity(
                taskName,
                TextUtils.isEmpty(description) ? null : description,
                false,
                TextUtils.isEmpty(category) ? "General" : category,
                scheduledTimeMillis,
                false, // legacy isRecurring
                null,  // legacy recurrenceRule
                repeating
        );
        
        // Set day selection
        task.repeatOnSunday = dayButtons[0].isChecked();
        task.repeatOnMonday = dayButtons[1].isChecked();
        task.repeatOnTuesday = dayButtons[2].isChecked();
        task.repeatOnWednesday = dayButtons[3].isChecked();
        task.repeatOnThursday = dayButtons[4].isChecked();
        task.repeatOnFriday = dayButtons[5].isChecked();
        task.repeatOnSaturday = dayButtons[6].isChecked();
        
        // Set alarm options
        task.enableAlarm = enableAlarm;
        task.enableCaretakerNotification = enableCaretakerNotif;
        
        // Save to Firestore
        saveEnhancedTaskToFirestore(task);
    }
    
    /**
     * Save enhanced task to Firestore
     */
    private void saveEnhancedTaskToFirestore(TaskEntity task) {
        Log.d(TAG, "Saving enhanced task: " + task.name);
        
        // Show progress
        progressBar.setVisibility(View.VISIBLE);
        saveButton.setEnabled(false);
        
        // Save to Firestore
        db.collection("patients")
                .document(patientId)
                .collection("tasks")
                .add(task)
                .addOnCompleteListener(new OnCompleteListener<com.google.firebase.firestore.DocumentReference>() {
                    @Override
                    public void onComplete(@NonNull Task<com.google.firebase.firestore.DocumentReference> firestoreTask) {
                        progressBar.setVisibility(View.GONE);
                        saveButton.setEnabled(true);
                        
                        if (firestoreTask.isSuccessful()) {
                            Log.d(TAG, "Enhanced task saved successfully");
                            Toast.makeText(AddTaskActivity.this, "Task created successfully!", Toast.LENGTH_SHORT).show();
                            finish();
                        } else {
                            Log.e(TAG, "Failed to save enhanced task", firestoreTask.getException());
                            Toast.makeText(AddTaskActivity.this, "Failed to create task: " + 
                                (firestoreTask.getException() != null ? firestoreTask.getException().getMessage() : "Unknown error"), 
                                Toast.LENGTH_LONG).show();
                        }
                    }
                });
    }
}
