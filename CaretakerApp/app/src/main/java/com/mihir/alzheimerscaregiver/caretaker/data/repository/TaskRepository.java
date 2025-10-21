package com.mihir.alzheimerscaregiver.caretaker.data.repository;

import android.content.Context;
import android.util.Log;

import com.mihir.alzheimerscaregiver.caretaker.data.entity.TaskEntity;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
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
            task.id = taskId;

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
            getTasksCollection(patientId)
                    .document(task.id)
                    .set(task)
                    .addOnSuccessListener(aVoid -> {
                        Log.d(TAG, "Task updated successfully: " + task.id);
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
                    .orderBy("name")
                    .get()
                    .addOnSuccessListener(queryDocumentSnapshots -> {
                        List<TaskEntity> tasks = new ArrayList<>();
                        for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                            TaskEntity task = document.toObject(TaskEntity.class);
                            task.id = document.getId();
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
                    .get()
                    .addOnSuccessListener(queryDocumentSnapshots -> {
                        List<TaskEntity> todayTasks = new ArrayList<>();
                        for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                            TaskEntity task = document.toObject(TaskEntity.class);
                            task.id = document.getId();
                            
                            // Filter tasks scheduled for today
                            if (isScheduledForToday(task)) {
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

    // Check if task is scheduled for today
    private boolean isScheduledForToday(TaskEntity task) {
        if (!task.isRepeating) {
            return true; // One-time tasks are always "scheduled"
        }

        java.util.Calendar calendar = java.util.Calendar.getInstance();
        int dayOfWeek = calendar.get(java.util.Calendar.DAY_OF_WEEK);
        return task.shouldRepeatOnDay(dayOfWeek);
    }

    // Mark task as completed for today
    public void markCompletedForToday(String patientId, TaskEntity task, OnTaskOperationListener listener) {
        try {
            task.markCompletedToday();

            getTasksCollection(patientId)
                    .document(task.id)
                    .set(task)
                    .addOnSuccessListener(aVoid -> {
                        Log.d(TAG, "Task marked as completed for today: " + task.id);
                        
                        // Send notification to caretaker if enabled
                        if (task.enableCaretakerNotification) {
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
            task.isCompleted = true;

            getTasksCollection(patientId)
                    .document(task.id)
                    .set(task)
                    .addOnSuccessListener(aVoid -> {
                        Log.d(TAG, "Task completed: " + task.id);
                        
                        // Send notification to caretaker if enabled
                        if (task.enableCaretakerNotification) {
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
                                task.id = documentSnapshot.getId();
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

    // Notify caretaker about task completion
    private void notifyCaretakerOfCompletion(String patientId, TaskEntity task) {
        try {
            Map<String, Object> notification = new HashMap<>();
            notification.put("type", "task_completed");
            notification.put("patientId", patientId);
            notification.put("taskName", task.name);
            notification.put("taskCategory", task.category);
            notification.put("completedAt", System.currentTimeMillis());
            notification.put("message", "Task '" + task.name + "' has been completed");

            db.collection("caretaker_notifications")
                    .add(notification)
                    .addOnSuccessListener(documentReference -> {
                        Log.d(TAG, "Caretaker notification sent for task completion: " + task.id);
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
}