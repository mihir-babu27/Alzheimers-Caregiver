package com.example.caretakerapp.repository;

import android.content.Context;
import android.util.Log;

import com.example.caretakerapp.entity.TaskEntity;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class TaskRepository {
    private static final String TAG = "TaskRepository";
    private static final String COLLECTION_PATIENTS = "patients";
    private static final String COLLECTION_TASKS = "tasks";
    
    private final FirebaseFirestore db;
    private final Context context;

    public TaskRepository(Context context) {
        this.context = context;
        this.db = FirebaseFirestore.getInstance();
    }

    // Get tasks collection for a specific patient
    private CollectionReference getTasksCollection(String patientId) {
        return db.collection(COLLECTION_PATIENTS)
                .document(patientId)
                .collection(COLLECTION_TASKS);
    }

    // Create a new task
    public void createTask(String patientId, TaskEntity task, OnTaskOperationListener listener) {
        try {
            String taskId = getTasksCollection(patientId).document().getId();
            task.setTaskId(taskId);
            task.setCreatedAt(System.currentTimeMillis());
            task.setUpdatedAt(System.currentTimeMillis());

            getTasksCollection(patientId)
                    .document(taskId)
                    .set(task)
                    .addOnSuccessListener(aVoid -> {
                        Log.d(TAG, "Task created successfully with ID: " + taskId);
                        if (listener != null) {
                            listener.onSuccess("Task created successfully");
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error creating task", e);
                        if (listener != null) {
                            listener.onError("Failed to create task: " + e.getMessage());
                        }
                    });
        } catch (Exception e) {
            Log.e(TAG, "Error in createTask", e);
            if (listener != null) {
                listener.onError("Failed to create task: " + e.getMessage());
            }
        }
    }

    // Update an existing task
    public void updateTask(String patientId, TaskEntity task, OnTaskOperationListener listener) {
        try {
            task.setUpdatedAt(System.currentTimeMillis());

            getTasksCollection(patientId)
                    .document(task.getTaskId())
                    .set(task)
                    .addOnSuccessListener(aVoid -> {
                        Log.d(TAG, "Task updated successfully: " + task.getTaskId());
                        if (listener != null) {
                            listener.onSuccess("Task updated successfully");
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error updating task", e);
                        if (listener != null) {
                            listener.onError("Failed to update task: " + e.getMessage());
                        }
                    });
        } catch (Exception e) {
            Log.e(TAG, "Error in updateTask", e);
            if (listener != null) {
                listener.onError("Failed to update task: " + e.getMessage());
            }
        }
    }

    // Delete a task
    public void deleteTask(String patientId, String taskId, OnTaskOperationListener listener) {
        try {
            getTasksCollection(patientId)
                    .document(taskId)
                    .delete()
                    .addOnSuccessListener(aVoid -> {
                        Log.d(TAG, "Task deleted successfully: " + taskId);
                        if (listener != null) {
                            listener.onSuccess("Task deleted successfully");
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error deleting task", e);
                        if (listener != null) {
                            listener.onError("Failed to delete task: " + e.getMessage());
                        }
                    });
        } catch (Exception e) {
            Log.e(TAG, "Error in deleteTask", e);
            if (listener != null) {
                listener.onError("Failed to delete task: " + e.getMessage());
            }
        }
    }

    // Get all tasks for a patient
    public void getAllTasks(String patientId, OnTasksLoadedListener listener) {
        try {
            getTasksCollection(patientId)
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .get()
                    .addOnSuccessListener(queryDocumentSnapshots -> {
                        List<TaskEntity> tasks = new ArrayList<>();
                        for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                            TaskEntity task = document.toObject(TaskEntity.class);
                            task.setTaskId(document.getId());
                            tasks.add(task);
                        }
                        Log.d(TAG, "Tasks loaded successfully: " + tasks.size() + " tasks");
                        if (listener != null) {
                            listener.onTasksLoaded(tasks);
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error loading tasks", e);
                        if (listener != null) {
                            listener.onError("Failed to load tasks: " + e.getMessage());
                        }
                    });
        } catch (Exception e) {
            Log.e(TAG, "Error in getAllTasks", e);
            if (listener != null) {
                listener.onError("Failed to load tasks: " + e.getMessage());
            }
        }
    }

    // Get tasks for today
    public void getTodayTasks(String patientId, OnTasksLoadedListener listener) {
        try {
            getTasksCollection(patientId)
                    .orderBy("scheduledTime")
                    .get()
                    .addOnSuccessListener(queryDocumentSnapshots -> {
                        List<TaskEntity> todayTasks = new ArrayList<>();
                        for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                            TaskEntity task = document.toObject(TaskEntity.class);
                            task.setTaskId(document.getId());
                            
                            // Filter tasks scheduled for today
                            if (task.isScheduledForToday()) {
                                todayTasks.add(task);
                            }
                        }
                        Log.d(TAG, "Today's tasks loaded successfully: " + todayTasks.size() + " tasks");
                        if (listener != null) {
                            listener.onTasksLoaded(todayTasks);
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error loading today's tasks", e);
                        if (listener != null) {
                            listener.onError("Failed to load today's tasks: " + e.getMessage());
                        }
                    });
        } catch (Exception e) {
            Log.e(TAG, "Error in getTodayTasks", e);
            if (listener != null) {
                listener.onError("Failed to load today's tasks: " + e.getMessage());
            }
        }
    }

    // Mark task as completed for today
    public void markCompletedForToday(String patientId, TaskEntity task, OnTaskOperationListener listener) {
        try {
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            String today = dateFormat.format(new Date());
            
            Map<String, Object> updates = new HashMap<>();
            updates.put("lastCompletedDate", today);
            updates.put("completed", true);
            updates.put("updatedAt", System.currentTimeMillis());

            getTasksCollection(patientId)
                    .document(task.getTaskId())
                    .update(updates)
                    .addOnSuccessListener(aVoid -> {
                        Log.d(TAG, "Task marked as completed for today: " + task.getTaskId());
                        
                        // Send notification to caretaker if enabled
                        if (task.isEnableCaretakerNotification()) {
                            notifyCaretakerOfCompletion(patientId, task);
                        }
                        
                        if (listener != null) {
                            listener.onSuccess("Task completed successfully");
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error marking task as completed", e);
                        if (listener != null) {
                            listener.onError("Failed to complete task: " + e.getMessage());
                        }
                    });
        } catch (Exception e) {
            Log.e(TAG, "Error in markCompletedForToday", e);
            if (listener != null) {
                listener.onError("Failed to complete task: " + e.getMessage());
            }
        }
    }

    // Complete task (for one-time tasks)
    public void completeTask(String patientId, TaskEntity task, OnTaskOperationListener listener) {
        try {
            Map<String, Object> updates = new HashMap<>();
            updates.put("completed", true);
            updates.put("updatedAt", System.currentTimeMillis());

            getTasksCollection(patientId)
                    .document(task.getTaskId())
                    .update(updates)
                    .addOnSuccessListener(aVoid -> {
                        Log.d(TAG, "Task completed: " + task.getTaskId());
                        
                        // Send notification to caretaker if enabled
                        if (task.isEnableCaretakerNotification()) {
                            notifyCaretakerOfCompletion(patientId, task);
                        }
                        
                        if (listener != null) {
                            listener.onSuccess("Task completed successfully");
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error completing task", e);
                        if (listener != null) {
                            listener.onError("Failed to complete task: " + e.getMessage());
                        }
                    });
        } catch (Exception e) {
            Log.e(TAG, "Error in completeTask", e);
            if (listener != null) {
                listener.onError("Failed to complete task: " + e.getMessage());
            }
        }
    }

    // Reset daily completion status (called at midnight)
    public void resetDailyCompletionStatus(String patientId, OnTaskOperationListener listener) {
        try {
            getTasksCollection(patientId)
                    .whereEqualTo("repeating", true)
                    .get()
                    .addOnSuccessListener(queryDocumentSnapshots -> {
                        int totalTasks = queryDocumentSnapshots.size();
                        if (totalTasks == 0) {
                            if (listener != null) {
                                listener.onSuccess("No repeating tasks to reset");
                            }
                            return;
                        }

                        int[] resetCount = {0};
                        for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                            TaskEntity task = document.toObject(TaskEntity.class);
                            
                            // Only reset if task is scheduled for today
                            if (task.isScheduledForToday()) {
                                Map<String, Object> updates = new HashMap<>();
                                updates.put("completed", false);
                                updates.put("updatedAt", System.currentTimeMillis());

                                getTasksCollection(patientId)
                                        .document(document.getId())
                                        .update(updates)
                                        .addOnSuccessListener(aVoid -> {
                                            resetCount[0]++;
                                            if (resetCount[0] == totalTasks && listener != null) {
                                                listener.onSuccess("Daily completion status reset for " + resetCount[0] + " tasks");
                                            }
                                        })
                                        .addOnFailureListener(e -> {
                                            Log.e(TAG, "Error resetting task: " + document.getId(), e);
                                        });
                            } else {
                                resetCount[0]++;
                            }
                        }
                        
                        if (resetCount[0] == totalTasks && listener != null) {
                            listener.onSuccess("Daily completion status reset");
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error resetting daily completion status", e);
                        if (listener != null) {
                            listener.onError("Failed to reset daily completion status: " + e.getMessage());
                        }
                    });
        } catch (Exception e) {
            Log.e(TAG, "Error in resetDailyCompletionStatus", e);
            if (listener != null) {
                listener.onError("Failed to reset daily completion status: " + e.getMessage());
            }
        }
    }

    // Get task by ID
    public void getTaskById(String patientId, String taskId, OnTaskLoadedListener listener) {
        try {
            getTasksCollection(patientId)
                    .document(taskId)
                    .get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (documentSnapshot.exists()) {
                            TaskEntity task = documentSnapshot.toObject(TaskEntity.class);
                            if (task != null) {
                                task.setTaskId(documentSnapshot.getId());
                                Log.d(TAG, "Task loaded successfully: " + taskId);
                                if (listener != null) {
                                    listener.onTaskLoaded(task);
                                }
                            } else {
                                if (listener != null) {
                                    listener.onError("Failed to parse task data");
                                }
                            }
                        } else {
                            if (listener != null) {
                                listener.onError("Task not found");
                            }
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error loading task", e);
                        if (listener != null) {
                            listener.onError("Failed to load task: " + e.getMessage());
                        }
                    });
        } catch (Exception e) {
            Log.e(TAG, "Error in getTaskById", e);
            if (listener != null) {
                listener.onError("Failed to load task: " + e.getMessage());
            }
        }
    }

    // Listen for real-time task updates
    public ListenerRegistration listenToTasks(String patientId, OnTasksLoadedListener listener) {
        return getTasksCollection(patientId)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .addSnapshotListener((queryDocumentSnapshots, e) -> {
                    if (e != null) {
                        Log.e(TAG, "Listen failed", e);
                        if (listener != null) {
                            listener.onError("Failed to listen for tasks: " + e.getMessage());
                        }
                        return;
                    }

                    if (queryDocumentSnapshots != null) {
                        List<TaskEntity> tasks = new ArrayList<>();
                        for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                            TaskEntity task = document.toObject(TaskEntity.class);
                            task.setTaskId(document.getId());
                            tasks.add(task);
                        }
                        Log.d(TAG, "Real-time tasks update: " + tasks.size() + " tasks");
                        if (listener != null) {
                            listener.onTasksLoaded(tasks);
                        }
                    }
                });
    }

    // Get task statistics
    public void getTaskStatistics(String patientId, OnTaskStatsListener listener) {
        try {
            getTasksCollection(patientId)
                    .get()
                    .addOnSuccessListener(queryDocumentSnapshots -> {
                        int totalTasks = 0;
                        int completedToday = 0;
                        int pendingToday = 0;
                        int totalCompleted = 0;

                        for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                            TaskEntity task = document.toObject(TaskEntity.class);
                            totalTasks++;

                            if (task.isCompleted()) {
                                totalCompleted++;
                            }

                            if (task.isScheduledForToday()) {
                                if (task.isCompletedToday()) {
                                    completedToday++;
                                } else {
                                    pendingToday++;
                                }
                            }
                        }

                        Map<String, Integer> stats = new HashMap<>();
                        stats.put("totalTasks", totalTasks);
                        stats.put("completedToday", completedToday);
                        stats.put("pendingToday", pendingToday);
                        stats.put("totalCompleted", totalCompleted);

                        Log.d(TAG, "Task statistics loaded: " + stats.toString());
                        if (listener != null) {
                            listener.onStatsLoaded(stats);
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error loading task statistics", e);
                        if (listener != null) {
                            listener.onError("Failed to load statistics: " + e.getMessage());
                        }
                    });
        } catch (Exception e) {
            Log.e(TAG, "Error in getTaskStatistics", e);
            if (listener != null) {
                listener.onError("Failed to load statistics: " + e.getMessage());
            }
        }
    }

    // Notify caretaker about task completion
    private void notifyCaretakerOfCompletion(String patientId, TaskEntity task) {
        try {
            Map<String, Object> notification = new HashMap<>();
            notification.put("type", "task_completed");
            notification.put("patientId", patientId);
            notification.put("taskName", task.getTaskName());
            notification.put("taskCategory", task.getCategory());
            notification.put("completedAt", System.currentTimeMillis());
            notification.put("message", "Task '" + task.getTaskName() + "' has been completed");

            db.collection("caretaker_notifications")
                    .add(notification)
                    .addOnSuccessListener(documentReference -> {
                        Log.d(TAG, "Caretaker notification sent for task completion: " + task.getTaskId());
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to send caretaker notification", e);
                    });
        } catch (Exception e) {
            Log.e(TAG, "Error sending caretaker notification", e);
        }
    }

    // Callback interfaces
    public interface OnTaskOperationListener {
        void onSuccess(String message);
        void onError(String error);
    }

    public interface OnTasksLoadedListener {
        void onTasksLoaded(List<TaskEntity> tasks);
        void onError(String error);
    }

    public interface OnTaskLoadedListener {
        void onTaskLoaded(TaskEntity task);
        void onError(String error);
    }

    public interface OnTaskStatsListener {
        void onStatsLoaded(Map<String, Integer> stats);
        void onError(String error);
    }
}