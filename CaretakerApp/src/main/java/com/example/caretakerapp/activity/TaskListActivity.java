package com.example.caretakerapp.activity;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.caretakerapp.R;
import com.example.caretakerapp.adapter.TaskAdapter;
import com.example.caretakerapp.entity.TaskEntity;
import com.example.caretakerapp.repository.TaskRepository;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TaskListActivity extends AppCompatActivity {
    private static final String TAG = "TaskListActivity";
    public static final String EXTRA_PATIENT_ID = "patientId";

    private RecyclerView recyclerViewTasks;
    private TaskAdapter taskAdapter;
    private FloatingActionButton fabAddTask;
    
    private TaskRepository taskRepository;
    private String patientId;
    private List<TaskEntity> taskList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_task_list);

        // Get patient ID from intent
        patientId = getIntent().getStringExtra(EXTRA_PATIENT_ID);
        if (patientId == null || patientId.isEmpty()) {
            Toast.makeText(this, "Error: Patient ID not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initializeViews();
        setupRecyclerView();
        setupClickListeners();
        loadTasks();
    }

    private void initializeViews() {
        recyclerViewTasks = findViewById(R.id.recyclerViewTasks);
        fabAddTask = findViewById(R.id.fabAddTask);
        
        taskRepository = new TaskRepository(this);
        taskList = new ArrayList<>();

        // Setup toolbar
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Task Management");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            
            // Set subtitle with current date
            SimpleDateFormat dateFormat = new SimpleDateFormat("EEEE, MMM dd, yyyy", Locale.getDefault());
            String currentDate = dateFormat.format(new Date());
            getSupportActionBar().setSubtitle(currentDate);
        }
    }

    private void setupRecyclerView() {
        taskAdapter = new TaskAdapter(this, taskList, new TaskAdapter.OnTaskInteractionListener() {
            @Override
            public void onTaskCompleted(TaskEntity task, boolean isCompleted) {
                handleTaskCompletion(task, isCompleted);
            }

            @Override
            public void onTaskEdit(TaskEntity task) {
                openEditTaskDialog(task);
            }

            @Override
            public void onTaskDelete(TaskEntity task) {
                deleteTask(task);
            }
        });
        
        recyclerViewTasks.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewTasks.setAdapter(taskAdapter);
    }

    private void setupClickListeners() {
        fabAddTask.setOnClickListener(v -> openAddTaskDialog());
    }

    private void loadTasks() {
        taskRepository.getAllTasks(patientId, new TaskRepository.OnTasksLoadedListener() {
            @Override
            public void onTasksLoaded(List<TaskEntity> tasks) {
                runOnUiThread(() -> {
                    taskList.clear();
                    taskList.addAll(tasks);
                    taskAdapter.notifyDataSetChanged();
                    
                    Log.d(TAG, "Tasks loaded: " + tasks.size());
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    Toast.makeText(TaskListActivity.this, error, Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "Error loading tasks: " + error);
                });
            }
        });
    }

    private void openAddTaskDialog() {
        Intent intent = new Intent(this, AddEditTaskActivity.class);
        intent.putExtra(AddEditTaskActivity.EXTRA_PATIENT_ID, patientId);
        startActivityForResult(intent, AddEditTaskActivity.REQUEST_ADD_TASK);
    }

    private void openEditTaskDialog(TaskEntity task) {
        Intent intent = new Intent(this, AddEditTaskActivity.class);
        intent.putExtra(AddEditTaskActivity.EXTRA_PATIENT_ID, patientId);
        intent.putExtra(AddEditTaskActivity.EXTRA_TASK_ID, task.getTaskId());
        intent.putExtra(AddEditTaskActivity.EXTRA_EDIT_MODE, true);
        startActivityForResult(intent, AddEditTaskActivity.REQUEST_EDIT_TASK);
    }

    private void handleTaskCompletion(TaskEntity task, boolean isCompleted) {
        if (isCompleted) {
            if (task.isRepeating()) {
                // Mark as completed for today
                taskRepository.markCompletedForToday(patientId, task, new TaskRepository.OnTaskOperationListener() {
                    @Override
                    public void onSuccess(String message) {
                        runOnUiThread(() -> {
                            Toast.makeText(TaskListActivity.this, "Task completed!", Toast.LENGTH_SHORT).show();
                            loadTasks(); // Refresh the list
                        });
                    }

                    @Override
                    public void onError(String error) {
                        runOnUiThread(() -> {
                            Toast.makeText(TaskListActivity.this, "Error: " + error, Toast.LENGTH_SHORT).show();
                            taskAdapter.notifyDataSetChanged(); // Reset checkbox state
                        });
                    }
                });
            } else {
                // Complete one-time task
                taskRepository.completeTask(patientId, task, new TaskRepository.OnTaskOperationListener() {
                    @Override
                    public void onSuccess(String message) {
                        runOnUiThread(() -> {
                            Toast.makeText(TaskListActivity.this, "Task completed!", Toast.LENGTH_SHORT).show();
                            loadTasks(); // Refresh the list
                        });
                    }

                    @Override
                    public void onError(String error) {
                        runOnUiThread(() -> {
                            Toast.makeText(TaskListActivity.this, "Error: " + error, Toast.LENGTH_SHORT).show();
                            taskAdapter.notifyDataSetChanged(); // Reset checkbox state
                        });
                    }
                });
            }
        } else {
            // Uncheck task - reset completion status
            // For simplicity, we'll just reload the tasks
            loadTasks();
        }
    }

    private void deleteTask(TaskEntity task) {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Delete Task")
                .setMessage("Are you sure you want to delete the task '" + task.getTaskName() + "'?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    taskRepository.deleteTask(patientId, task.getTaskId(), new TaskRepository.OnTaskOperationListener() {
                        @Override
                        public void onSuccess(String message) {
                            runOnUiThread(() -> {
                                Toast.makeText(TaskListActivity.this, "Task deleted successfully", Toast.LENGTH_SHORT).show();
                                loadTasks(); // Refresh the list
                            });
                        }

                        @Override
                        public void onError(String error) {
                            runOnUiThread(() -> {
                                Toast.makeText(TaskListActivity.this, "Error: " + error, Toast.LENGTH_SHORT).show();
                            });
                        }
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (resultCode == RESULT_OK) {
            if (requestCode == AddEditTaskActivity.REQUEST_ADD_TASK || 
                requestCode == AddEditTaskActivity.REQUEST_EDIT_TASK) {
                // Refresh the task list
                loadTasks();
            }
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh tasks when returning to the activity
        loadTasks();
    }
}