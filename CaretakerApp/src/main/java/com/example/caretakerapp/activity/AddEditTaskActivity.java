package com.example.caretakerapp.activity;

import android.app.TimePickerDialog;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import androidx.appcompat.app.AppCompatActivity;

import com.example.caretakerapp.R;
import com.example.caretakerapp.entity.TaskEntity;
import com.example.caretakerapp.repository.TaskRepository;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class AddEditTaskActivity extends AppCompatActivity {
    private static final String TAG = "AddEditTaskActivity";
    
    public static final String EXTRA_PATIENT_ID = "patientId";
    public static final String EXTRA_TASK_ID = "taskId";
    public static final String EXTRA_EDIT_MODE = "editMode";
    public static final int REQUEST_ADD_TASK = 1001;
    public static final int REQUEST_EDIT_TASK = 1002;

    // Views
    private EditText inputTaskName;
    private EditText inputTaskDescription;
    private EditText inputTaskCategory;
    private EditText inputTaskTime;
    private CheckBox checkRepeating;
    private TextView labelDaysOfWeek;
    private LinearLayout layoutDaysOfWeek;
    private LinearLayout layoutQuickRepeat;
    private ToggleButton buttonSunday, buttonMonday, buttonTuesday, buttonWednesday;
    private ToggleButton buttonThursday, buttonFriday, buttonSaturday;
    private Button buttonDaily, buttonWeekdays, buttonWeekends;
    private CheckBox checkEnableAlarm;
    private CheckBox checkCaretakerNotification;
    private Button buttonSave, buttonCancel;

    // Data
    private String patientId;
    private String taskId;
    private boolean isEditMode;
    private TaskRepository taskRepository;
    private TaskEntity existingTask;
    private Calendar selectedTime;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_edit_task);

        // Get intent data
        patientId = getIntent().getStringExtra(EXTRA_PATIENT_ID);
        taskId = getIntent().getStringExtra(EXTRA_TASK_ID);
        isEditMode = getIntent().getBooleanExtra(EXTRA_EDIT_MODE, false);

        if (patientId == null || patientId.isEmpty()) {
            Toast.makeText(this, "Error: Patient ID not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initializeViews();
        setupClickListeners();
        
        taskRepository = new TaskRepository(this);
        selectedTime = Calendar.getInstance();

        if (isEditMode && taskId != null) {
            loadExistingTaskData();
        }

        // Setup toolbar
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(isEditMode ? "Edit Task" : "Add New Task");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
    }

    private void initializeViews() {
        inputTaskName = findViewById(R.id.inputTaskName);
        inputTaskDescription = findViewById(R.id.inputTaskDescription);
        inputTaskCategory = findViewById(R.id.inputTaskCategory);
        inputTaskTime = findViewById(R.id.inputTaskTime);
        
        checkRepeating = findViewById(R.id.checkRepeating);
        labelDaysOfWeek = findViewById(R.id.labelDaysOfWeek);
        layoutDaysOfWeek = findViewById(R.id.layoutDaysOfWeek);
        layoutQuickRepeat = findViewById(R.id.layoutQuickRepeat);
        
        buttonSunday = findViewById(R.id.buttonSunday);
        buttonMonday = findViewById(R.id.buttonMonday);
        buttonTuesday = findViewById(R.id.buttonTuesday);
        buttonWednesday = findViewById(R.id.buttonWednesday);
        buttonThursday = findViewById(R.id.buttonThursday);
        buttonFriday = findViewById(R.id.buttonFriday);
        buttonSaturday = findViewById(R.id.buttonSaturday);
        
        buttonDaily = findViewById(R.id.buttonDaily);
        buttonWeekdays = findViewById(R.id.buttonWeekdays);
        buttonWeekends = findViewById(R.id.buttonWeekends);
        
        checkEnableAlarm = findViewById(R.id.checkEnableAlarm);
        checkCaretakerNotification = findViewById(R.id.checkCaretakerNotification);
        
        buttonSave = findViewById(R.id.buttonSave);
        buttonCancel = findViewById(R.id.buttonCancel);
    }

    private void setupClickListeners() {
        // Time picker
        inputTaskTime.setOnClickListener(v -> showTimePicker());
        
        // Repeat checkbox
        checkRepeating.setOnCheckedChangeListener((buttonView, isChecked) -> {
            toggleRepeatOptions(isChecked);
        });
        
        // Quick repeat buttons
        buttonDaily.setOnClickListener(v -> setDailyRepeat());
        buttonWeekdays.setOnClickListener(v -> setWeekdaysRepeat());
        buttonWeekends.setOnClickListener(v -> setWeekendsRepeat());
        
        // Save and cancel buttons
        buttonSave.setOnClickListener(v -> saveTask());
        buttonCancel.setOnClickListener(v -> finish());
    }

    private void showTimePicker() {
        TimePickerDialog timePickerDialog = new TimePickerDialog(
            this,
            (view, hourOfDay, minute) -> {
                selectedTime.set(Calendar.HOUR_OF_DAY, hourOfDay);
                selectedTime.set(Calendar.MINUTE, minute);
                
                SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mm a", Locale.getDefault());
                String timeString = timeFormat.format(selectedTime.getTime());
                inputTaskTime.setText(timeString);
            },
            selectedTime.get(Calendar.HOUR_OF_DAY),
            selectedTime.get(Calendar.MINUTE),
            false
        );
        timePickerDialog.show();
    }

    private void toggleRepeatOptions(boolean show) {
        int visibility = show ? View.VISIBLE : View.GONE;
        labelDaysOfWeek.setVisibility(visibility);
        layoutDaysOfWeek.setVisibility(visibility);
        layoutQuickRepeat.setVisibility(visibility);
    }

    private void setDailyRepeat() {
        buttonSunday.setChecked(true);
        buttonMonday.setChecked(true);
        buttonTuesday.setChecked(true);
        buttonWednesday.setChecked(true);
        buttonThursday.setChecked(true);
        buttonFriday.setChecked(true);
        buttonSaturday.setChecked(true);
    }

    private void setWeekdaysRepeat() {
        buttonSunday.setChecked(false);
        buttonMonday.setChecked(true);
        buttonTuesday.setChecked(true);
        buttonWednesday.setChecked(true);
        buttonThursday.setChecked(true);
        buttonFriday.setChecked(true);
        buttonSaturday.setChecked(false);
    }

    private void setWeekendsRepeat() {
        buttonSunday.setChecked(true);
        buttonMonday.setChecked(false);
        buttonTuesday.setChecked(false);
        buttonWednesday.setChecked(false);
        buttonThursday.setChecked(false);
        buttonFriday.setChecked(false);
        buttonSaturday.setChecked(true);
    }

    private void loadExistingTaskData() {
        taskRepository.getTaskById(patientId, taskId, new TaskRepository.OnTaskLoadedListener() {
            @Override
            public void onTaskLoaded(TaskEntity task) {
                runOnUiThread(() -> {
                    existingTask = task;
                    populateFieldsWithExistingData();
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    Toast.makeText(AddEditTaskActivity.this, "Error loading task: " + error, Toast.LENGTH_SHORT).show();
                    finish();
                });
            }
        });
    }

    private void populateFieldsWithExistingData() {
        if (existingTask == null) return;

        // Basic fields
        inputTaskName.setText(existingTask.getTaskName());
        inputTaskDescription.setText(existingTask.getTaskDescription());
        inputTaskCategory.setText(existingTask.getCategory());
        inputTaskTime.setText(existingTask.getScheduledTime());

        // Repeat settings
        checkRepeating.setChecked(existingTask.isRepeating());
        toggleRepeatOptions(existingTask.isRepeating());

        if (existingTask.isRepeating()) {
            buttonSunday.setChecked(existingTask.isRepeatOnSunday());
            buttonMonday.setChecked(existingTask.isRepeatOnMonday());
            buttonTuesday.setChecked(existingTask.isRepeatOnTuesday());
            buttonWednesday.setChecked(existingTask.isRepeatOnWednesday());
            buttonThursday.setChecked(existingTask.isRepeatOnThursday());
            buttonFriday.setChecked(existingTask.isRepeatOnFriday());
            buttonSaturday.setChecked(existingTask.isRepeatOnSaturday());
        }

        // Notification settings
        checkEnableAlarm.setChecked(existingTask.isEnableAlarm());
        checkCaretakerNotification.setChecked(existingTask.isEnableCaretakerNotification());
    }

    private void saveTask() {
        // Validate input
        String taskName = inputTaskName.getText().toString().trim();
        if (taskName.isEmpty()) {
            Toast.makeText(this, "Please enter a task name", Toast.LENGTH_SHORT).show();
            return;
        }

        String taskDescription = inputTaskDescription.getText().toString().trim();
        String category = inputTaskCategory.getText().toString().trim();
        String scheduledTime = inputTaskTime.getText().toString().trim();

        // Create or update task
        TaskEntity task;
        if (isEditMode && existingTask != null) {
            task = existingTask;
            task.setTaskName(taskName);
            task.setTaskDescription(taskDescription);
            task.setCategory(category);
            task.setScheduledTime(scheduledTime);
        } else {
            task = new TaskEntity(taskName, taskDescription, category, scheduledTime);
        }

        // Set repeat settings
        task.setRepeating(checkRepeating.isChecked());
        if (checkRepeating.isChecked()) {
            task.setRepeatOnSunday(buttonSunday.isChecked());
            task.setRepeatOnMonday(buttonMonday.isChecked());
            task.setRepeatOnTuesday(buttonTuesday.isChecked());
            task.setRepeatOnWednesday(buttonWednesday.isChecked());
            task.setRepeatOnThursday(buttonThursday.isChecked());
            task.setRepeatOnFriday(buttonFriday.isChecked());
            task.setRepeatOnSaturday(buttonSaturday.isChecked());
        }

        // Set notification settings
        task.setEnableAlarm(checkEnableAlarm.isChecked());
        task.setEnableCaretakerNotification(checkCaretakerNotification.isChecked());

        // Save to repository
        TaskRepository.OnTaskOperationListener listener = new TaskRepository.OnTaskOperationListener() {
            @Override
            public void onSuccess(String message) {
                runOnUiThread(() -> {
                    Toast.makeText(AddEditTaskActivity.this, message, Toast.LENGTH_SHORT).show();
                    setResult(RESULT_OK);
                    finish();
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    Toast.makeText(AddEditTaskActivity.this, "Error: " + error, Toast.LENGTH_SHORT).show();
                });
            }
        };

        if (isEditMode) {
            taskRepository.updateTask(patientId, task, listener);
        } else {
            taskRepository.createTask(patientId, task, listener);
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}