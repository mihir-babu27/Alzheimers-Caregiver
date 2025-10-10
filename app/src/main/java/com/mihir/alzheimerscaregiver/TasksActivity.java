package com.mihir.alzheimerscaregiver;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.mihir.alzheimerscaregiver.data.entity.TaskEntity;
import com.mihir.alzheimerscaregiver.notifications.TaskReminderScheduler;
import com.mihir.alzheimerscaregiver.ui.tasks.TasksEntityAdapter;
import com.mihir.alzheimerscaregiver.ui.viewmodel.TaskViewModel;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class TasksActivity extends AppCompatActivity implements TasksAdapter.OnTaskInteractionListener, TasksEntityAdapter.OnTaskEntityInteractionListener {

    // UI Elements
    private ImageButton backButton;
    private TextView todayDateText, currentTimeText, tasksRemainingText;
    private RecyclerView tasksRecyclerView;
    private FloatingActionButton addTaskFab;

    // Adapter and data (Room version)
    private TasksEntityAdapter entityAdapter;
    private TaskViewModel taskViewModel;

    // Handler for updating time
    private Handler timeHandler;
    private Runnable timeRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tasks);

        // Initialize UI elements
        initializeViews();

        // Set up current date and time
        setupCurrentDateTime();

        // Start time updates
        startTimeUpdates();

        // Initialize RecyclerView and adapter (Room-backed)
        setupRecyclerView();

        // Set up click listeners
        setupClickListeners();

        // Update tasks remaining counter
        updateTasksRemainingCounter();
    }

    /**
     * Initialize all UI elements
     */
    private void initializeViews() {
        backButton = findViewById(R.id.backButton);
        todayDateText = findViewById(R.id.todayDateText);
        currentTimeText = findViewById(R.id.currentTimeText);
        tasksRemainingText = findViewById(R.id.tasksRemainingText);
        tasksRecyclerView = findViewById(R.id.tasksRecyclerView);
        addTaskFab = findViewById(R.id.addTaskFab);
    }

    /**
     * Set up current date and time display
     */
    private void setupCurrentDateTime() {
        Calendar calendar = Calendar.getInstance();
        SimpleDateFormat dateFormat = new SimpleDateFormat("EEEE, MMMM d", Locale.getDefault());
        String todayDate = "Today, " + dateFormat.format(calendar.getTime());
        todayDateText.setText(todayDate);

        updateCurrentTime();
    }

    /**
     * Start updating time every minute
     */
    private void startTimeUpdates() {
        timeHandler = new Handler();
        timeRunnable = new Runnable() {
            @Override
            public void run() {
                updateCurrentTime();
                // Update every minute (60000 milliseconds)
                timeHandler.postDelayed(this, 60000);
            }
        };

        timeHandler.post(timeRunnable);
    }

    /**
     * Update current time display
     */
    private void updateCurrentTime() {
        SimpleDateFormat timeFormat = new SimpleDateFormat("h:mm a", Locale.getDefault());
        String currentTime = timeFormat.format(new Date());
        currentTimeText.setText(currentTime);
    }

    // Sample tasks creation removed (now using Room)

    /**
     * Set up RecyclerView with adapter
     */
    private void setupRecyclerView() {
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        tasksRecyclerView.setLayoutManager(layoutManager);

        entityAdapter = new TasksEntityAdapter();
        entityAdapter.setListener(this);
        tasksRecyclerView.setAdapter(entityAdapter);

        // Swipe to delete
        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
            @Override
            public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getAdapterPosition();
                TaskEntity task = entityAdapter.getItem(position);
                confirmDeleteTask(task, position);
            }
        });
        itemTouchHelper.attachToRecyclerView(tasksRecyclerView);

        taskViewModel = new ViewModelProvider(this).get(TaskViewModel.class);
        // Use getAllSortedBySchedule() instead of getTodayTasks() to show all tasks like RemindersActivity does
        taskViewModel.getAllSortedBySchedule().observe(this, tasks -> {
            entityAdapter.submitList(tasks);
            updateTasksRemainingCounter();
        });
    }

    /**
     * Set up click listeners for all interactive elements
     */
    private void setupClickListeners() {
        // Back button
        backButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Go back to main activity
                finish();
            }
        });

        // Add task FAB
        addTaskFab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Provide haptic feedback
                v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
                showAddOrEditTaskDialog(null);
            }
        });
    }

    /**
     * Update the tasks remaining counter
     */
    private void updateTasksRemainingCounter() {
        if (entityAdapter != null) {
            int total = entityAdapter.getItemCount();
            int pending = 0;
            for (int i = 0; i < total; i++) {
                if (!entityAdapter.getItem(i).isCompleted) pending++;
            }
            if (pending == 0) {
                tasksRemainingText.setText("All done! 🎉");
            } else if (pending == 1) {
                tasksRemainingText.setText("1 remaining");
            } else {
                tasksRemainingText.setText(pending + " remaining");
            }
        }
    }

    /**
     * Helper method to show toast messages
     */
    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    // Legacy TasksAdapter listener (unused now), kept for compatibility

    @Override
    public void onTaskCompleted(Task task, int position) {
        // Handle task completion
        String message = task.isCompleted() ?
                "Great job! Task completed: " + task.getTaskName() :
                "Task marked as pending: " + task.getTaskName();

        showToast(message);

        // Update tasks remaining counter
        updateTasksRemainingCounter();

        // Simulate caregiver notification for completed tasks
        if (task.isCompleted()) {
            simulateCaregiverNotification(task);
        }
    }

    @Override
    public void onTaskClicked(Task task, int position) {
        // Handle task item click (not checkbox click)
        showToast("Task: " + task.getTaskName());
    }

    // New Room-backed adapter callbacks
    @Override
    public void onCompletionToggled(TaskEntity task) {
        // Use effective completion status for repeating tasks (same logic as adapter)
        boolean isCompleted = task.isRepeating ? task.isCompletedToday() : task.isCompleted;
        taskViewModel.markCompleted(task.id, isCompleted);
        if (isCompleted) {
            simulateCaregiverNotification(new Task(task.name, true));
        }
        updateTasksRemainingCounter();
    }

    @Override
    public void onItemClicked(TaskEntity task) {
        showAddOrEditTaskDialog(task);
    }

    @Override
    public void onItemSwipedToDelete(TaskEntity task, int position) {
        confirmDeleteTask(task, position);
    }

    /**
     * Simulate sending notification to caregiver when task is completed
     */
    private void simulateCaregiverNotification(Task task) {
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                showToast("✓ Caregiver notified: " + task.getTaskName() + " completed");
            }
        }, 1500);
    }

    /**
     * Method to add a new task (for future use)
     */
    public void addNewTask(String taskName) {
        if (taskName != null && !taskName.trim().isEmpty()) {
            TaskEntity entity = new TaskEntity(taskName.trim(), null, false, null, null, false, null);
            taskViewModel.insert(entity);
            showToast("New task added: " + taskName);
        }
    }

    /**
     * Get greeting based on time of day
     */
    private String getTimeBasedGreeting() {
        Calendar calendar = Calendar.getInstance();
        int hour = calendar.get(Calendar.HOUR_OF_DAY);

        if (hour < 12) {
            return "Good Morning";
        } else if (hour < 17) {
            return "Good Afternoon";
        } else {
            return "Good Evening";
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Stop time updates when activity is destroyed
        if (timeHandler != null && timeRunnable != null) {
            timeHandler.removeCallbacks(timeRunnable);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Update time and task counter when returning to this activity
        updateCurrentTime();
        updateTasksRemainingCounter();
    }

    private void confirmDeleteTask(TaskEntity task, int position) {
        new AlertDialog.Builder(this)
                .setTitle("Delete task")
                .setMessage("Are you sure you want to delete this task?")
                .setPositiveButton("Delete", (d, w) -> {
                    taskViewModel.delete(task);
                })
                .setNegativeButton("Cancel", (d, w) -> {
                    // refresh list to undo swipe
                    entityAdapter.notifyItemChanged(position);
                })
                .setOnCancelListener(dialog -> entityAdapter.notifyItemChanged(position))
                .show();
    }

    private void showAddOrEditTaskDialog(TaskEntity existing) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_add_edit_task, null, false);
        
        // Basic task info fields
        EditText inputName = view.findViewById(R.id.inputTaskName);
        EditText inputDescription = view.findViewById(R.id.inputTaskDescription);
        EditText inputCategory = view.findViewById(R.id.inputTaskCategory);
        EditText inputTime = view.findViewById(R.id.inputTaskTime);
        
        // Repeat options
        CheckBox checkRepeating = view.findViewById(R.id.checkRepeating);
        TextView labelDaysOfWeek = view.findViewById(R.id.labelDaysOfWeek);
        LinearLayout layoutDaysOfWeek = view.findViewById(R.id.layoutDaysOfWeek);
        LinearLayout layoutQuickRepeat = view.findViewById(R.id.layoutQuickRepeat);
        
        // Day selection toggle buttons
        ToggleButton[] dayButtons = {
                view.findViewById(R.id.buttonSunday),
                view.findViewById(R.id.buttonMonday),
                view.findViewById(R.id.buttonTuesday),
                view.findViewById(R.id.buttonWednesday),
                view.findViewById(R.id.buttonThursday),
                view.findViewById(R.id.buttonFriday),
                view.findViewById(R.id.buttonSaturday)
        };
        
        // Quick repeat buttons
        Button buttonDaily = view.findViewById(R.id.buttonDaily);
        Button buttonWeekdays = view.findViewById(R.id.buttonWeekdays);
        Button buttonWeekends = view.findViewById(R.id.buttonWeekends);
        
        // Alarm/notification options
        CheckBox checkEnableAlarm = view.findViewById(R.id.checkEnableAlarm);
        CheckBox checkCaretakerNotification = view.findViewById(R.id.checkCaretakerNotification);

        final Long[] scheduledAt = {null};

        // Initialize fields if editing existing task
        if (existing != null) {
            inputName.setText(existing.name);
            inputDescription.setText(existing.description);
            inputCategory.setText(existing.category);
            checkRepeating.setChecked(existing.isRepeating);
            checkEnableAlarm.setChecked(existing.enableAlarm);
            checkCaretakerNotification.setChecked(existing.enableCaretakerNotification);
            
            // Set day selection buttons
            boolean[] existingDays = {existing.repeatOnSunday, existing.repeatOnMonday, existing.repeatOnTuesday,
                                   existing.repeatOnWednesday, existing.repeatOnThursday, existing.repeatOnFriday, existing.repeatOnSaturday};
            for (int i = 0; i < dayButtons.length; i++) {
                dayButtons[i].setChecked(existingDays[i]);
            }
            
            if (existing.scheduledTimeEpochMillis != null) {
                scheduledAt[0] = existing.scheduledTimeEpochMillis;
                inputTime.setText(new SimpleDateFormat("EEE, MMM d h:mm a", Locale.getDefault())
                        .format(new Date(existing.scheduledTimeEpochMillis)));
            }
        }

        // Show/hide repeat options based on checkbox
        checkRepeating.setOnCheckedChangeListener((buttonView, isChecked) -> {
            int visibility = isChecked ? View.VISIBLE : View.GONE;
            labelDaysOfWeek.setVisibility(visibility);
            layoutDaysOfWeek.setVisibility(visibility);
            layoutQuickRepeat.setVisibility(visibility);
        });
        
        // Trigger initial visibility
        checkRepeating.setOnCheckedChangeListener(null);
        int initialVisibility = checkRepeating.isChecked() ? View.VISIBLE : View.GONE;
        labelDaysOfWeek.setVisibility(initialVisibility);
        layoutDaysOfWeek.setVisibility(initialVisibility);
        layoutQuickRepeat.setVisibility(initialVisibility);
        checkRepeating.setOnCheckedChangeListener((buttonView, isChecked) -> {
            int visibility = isChecked ? View.VISIBLE : View.GONE;
            labelDaysOfWeek.setVisibility(visibility);
            layoutDaysOfWeek.setVisibility(visibility);
            layoutQuickRepeat.setVisibility(visibility);
        });

        // Quick repeat button handlers
        buttonDaily.setOnClickListener(v -> {
            for (ToggleButton btn : dayButtons) btn.setChecked(true);
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

        inputTime.setOnClickListener(v -> pickDateTime(scheduledAt, inputTime));

        new AlertDialog.Builder(this)
                .setTitle(existing == null ? "Add Task" : "Edit Task")
                .setView(view)
                .setPositiveButton(existing == null ? "Add" : "Save", (dialog, which) -> {
                    String name = inputName.getText().toString().trim();
                    if (TextUtils.isEmpty(name)) {
                        showToast("Task name required");
                        return;
                    }
                    
                    String desc = inputDescription.getText().toString().trim();
                    String cat = inputCategory.getText().toString().trim();
                    boolean repeating = checkRepeating.isChecked();
                    boolean enableAlarm = checkEnableAlarm.isChecked();
                    boolean enableCaretakerNotif = checkCaretakerNotification.isChecked();

                    if (existing == null) {
                        // Create new task with enhanced scheduling
                        TaskEntity entity = new TaskEntity(name, emptyToNull(desc), false, emptyToNull(cat), scheduledAt[0], false, null, repeating);
                        
                        // Set day selection
                        entity.repeatOnSunday = dayButtons[0].isChecked();
                        entity.repeatOnMonday = dayButtons[1].isChecked();
                        entity.repeatOnTuesday = dayButtons[2].isChecked();
                        entity.repeatOnWednesday = dayButtons[3].isChecked();
                        entity.repeatOnThursday = dayButtons[4].isChecked();
                        entity.repeatOnFriday = dayButtons[5].isChecked();
                        entity.repeatOnSaturday = dayButtons[6].isChecked();
                        
                        // Set alarm options
                        entity.enableAlarm = enableAlarm;
                        entity.enableCaretakerNotification = enableCaretakerNotif;
                        
                        taskViewModel.insert(entity);
                        
                        if (scheduledAt[0] != null && enableAlarm) {
                            TaskReminderScheduler.schedule(this, scheduledAt[0], name, desc);
                        }
                    } else {
                        // Update existing task
                        existing.name = name;
                        existing.description = emptyToNull(desc);
                        existing.category = emptyToNull(cat);
                        existing.scheduledTimeEpochMillis = scheduledAt[0];
                        existing.isRepeating = repeating;
                        
                        // Update day selection
                        existing.repeatOnSunday = dayButtons[0].isChecked();
                        existing.repeatOnMonday = dayButtons[1].isChecked();
                        existing.repeatOnTuesday = dayButtons[2].isChecked();
                        existing.repeatOnWednesday = dayButtons[3].isChecked();
                        existing.repeatOnThursday = dayButtons[4].isChecked();
                        existing.repeatOnFriday = dayButtons[5].isChecked();
                        existing.repeatOnSaturday = dayButtons[6].isChecked();
                        
                        // Update alarm options
                        existing.enableAlarm = enableAlarm;
                        existing.enableCaretakerNotification = enableCaretakerNotif;
                        
                        taskViewModel.update(existing);
                        
                        if (scheduledAt[0] != null && enableAlarm) {
                            TaskReminderScheduler.schedule(this, scheduledAt[0], name, desc);
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void pickDateTime(Long[] scheduledAt, EditText inputTime) {
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

                scheduledAt[0] = selected.getTimeInMillis();
                inputTime.setText(new SimpleDateFormat("EEE, MMM d h:mm a", Locale.getDefault()).format(selected.getTime()));
            }, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), false);

            timePicker.show();
        }, now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH));
        datePicker.show();
    }

    private String emptyToNull(String s) {
        return TextUtils.isEmpty(s) ? null : s;
    }
}
