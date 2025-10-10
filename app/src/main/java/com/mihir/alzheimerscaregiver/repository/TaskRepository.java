
package com.mihir.alzheimerscaregiver.repository;

import android.content.Context;
import android.util.Log;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.mihir.alzheimerscaregiver.data.entity.TaskEntity;
import com.mihir.alzheimerscaregiver.data.FirebaseConfig;
import com.mihir.alzheimerscaregiver.caretaker.CaretakerNotificationScheduler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TaskRepository {

    private static final String TAG = "TaskRepository";
    private final FirebaseFirestore db;
    private final FirebaseAuth auth;
    private final CaretakerNotificationScheduler caretakerScheduler;

    public TaskRepository() {
        this(null);
    }

    public TaskRepository(Context context) {
        try {
            db = FirebaseConfig.getInstance();
            auth = FirebaseAuth.getInstance();
            
            if (db == null) {
                throw new RuntimeException("Firebase Firestore instance is null");
            }
            if (auth == null) {
                throw new RuntimeException("Firebase Auth instance is null");
            }
            
            // Initialize caretaker scheduler if context is provided
            if (context != null) {
                caretakerScheduler = new CaretakerNotificationScheduler(context);
                Log.d(TAG, "TaskRepository initialized with caretaker notifications enabled");
            } else {
                caretakerScheduler = null;
                Log.d(TAG, "TaskRepository initialized without caretaker notifications");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error initializing TaskRepository", e);
            throw new RuntimeException("Failed to initialize TaskRepository: " + e.getMessage(), e);
        }
    }

    private CollectionReference getTasksRef() {
        String patientId = auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : "default";
        return db.collection("patients").document(patientId).collection("tasks");
    }

    public interface FirebaseCallback<T> {
        void onSuccess(T result);
        void onError(String error);
    }

    public void getAllSortedBySchedule(FirebaseCallback<List<TaskEntity>> callback) {
        getTasksRef().orderBy("scheduledTimeEpochMillis")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<TaskEntity> list = new ArrayList<>();
                    if (queryDocumentSnapshots != null) {
                        for (var doc : queryDocumentSnapshots) {
                            TaskEntity entity = doc.toObject(TaskEntity.class);
                            if (entity != null) {
                                entity.id = doc.getId();
                                list.add(entity);
                            }
                        }
                    }
                    callback.onSuccess(list);
                })
                .addOnFailureListener(e -> {
                    callback.onError(e.getMessage());
                });
    }

    public void getTodayTasks(FirebaseCallback<List<TaskEntity>> callback) {
        long startOfDay = getStartOfDay();
        long endOfDay = getEndOfDay();
        
        getTasksRef().whereGreaterThanOrEqualTo("scheduledTimeEpochMillis", startOfDay)
                .whereLessThanOrEqualTo("scheduledTimeEpochMillis", endOfDay)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<TaskEntity> list = new ArrayList<>();
                    if (queryDocumentSnapshots != null) {
                        for (var doc : queryDocumentSnapshots) {
                            TaskEntity entity = doc.toObject(TaskEntity.class);
                            if (entity != null) {
                                entity.id = doc.getId();
                                list.add(entity);
                            }
                        }
                    }
                    callback.onSuccess(list);
                })
                .addOnFailureListener(e -> {
                    callback.onError(e.getMessage());
                });
    }

    public void getPendingTasks(FirebaseCallback<List<TaskEntity>> callback) {
        getTasksRef().whereEqualTo("isCompleted", false)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<TaskEntity> list = new ArrayList<>();
                    if (queryDocumentSnapshots != null) {
                        for (var doc : queryDocumentSnapshots) {
                            TaskEntity entity = doc.toObject(TaskEntity.class);
                            if (entity != null) {
                                entity.id = doc.getId();
                                list.add(entity);
                            }
                        }
                    }
                    callback.onSuccess(list);
                })
                .addOnFailureListener(e -> {
                    callback.onError(e.getMessage());
                });
    }

    public void search(String query, FirebaseCallback<List<TaskEntity>> callback) {
        getTasksRef().whereGreaterThanOrEqualTo("name", query)
                .whereLessThanOrEqualTo("name", query + '\uf8ff')
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<TaskEntity> list = new ArrayList<>();
                    if (queryDocumentSnapshots != null) {
                        for (var doc : queryDocumentSnapshots) {
                            TaskEntity entity = doc.toObject(TaskEntity.class);
                            if (entity != null) {
                                entity.id = doc.getId();
                                list.add(entity);
                            }
                        }
                    }
                    callback.onSuccess(list);
                })
                .addOnFailureListener(e -> {
                    callback.onError(e.getMessage());
                });
    }

    public void getByCategory(String category, FirebaseCallback<List<TaskEntity>> callback) {
        getTasksRef().whereEqualTo("category", category)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<TaskEntity> list = new ArrayList<>();
                    if (queryDocumentSnapshots != null) {
                        for (var doc : queryDocumentSnapshots) {
                            TaskEntity entity = doc.toObject(TaskEntity.class);
                            if (entity != null) {
                                entity.id = doc.getId();
                                list.add(entity);
                            }
                        }
                    }
                    callback.onSuccess(list);
                })
                .addOnFailureListener(e -> {
                    callback.onError(e.getMessage());
                });
    }

    public void insert(TaskEntity task, FirebaseCallback<Void> callback) {
        if (task.id == null || task.id.isEmpty()) {
            DocumentReference docRef = getTasksRef().document();
            task.id = docRef.getId();
            docRef.set(task)
                    .addOnSuccessListener(aVoid -> {
                        callback.onSuccess(null);
                    })
                    .addOnFailureListener(e -> {
                        callback.onError(e.getMessage());
                    });
        } else {
            getTasksRef().document(task.id).set(task)
                    .addOnSuccessListener(aVoid -> {
                        callback.onSuccess(null);
                    })
                    .addOnFailureListener(e -> {
                        callback.onError(e.getMessage());
                    });
        }
    }

    public void addTask(TaskEntity task, FirebaseCallback<Void> callback) {
        try {
            // First insert the task
            insert(task, new FirebaseCallback<Void>() {
                @Override
                public void onSuccess(Void result) {
                    // If caretaker notifications are enabled and task has a scheduled time
                    if (caretakerScheduler != null && task.scheduledTimeEpochMillis != null && 
                        task.scheduledTimeEpochMillis > System.currentTimeMillis()) {
                        
                        Log.d(TAG, "Scheduling caretaker notifications for task: " + task.id);
                        caretakerScheduler.scheduleCaretakerNotifications(createReminderFromTask(task));
                    }
                    callback.onSuccess(result);
                }

                @Override
                public void onError(String error) {
                    callback.onError(error);
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Exception in addTask", e);
            callback.onError("Failed to add task: " + e.getMessage());
        }
    }

    public void update(TaskEntity task, FirebaseCallback<Void> callback) {
        if (task.id != null && !task.id.isEmpty()) {
            getTasksRef().document(task.id).set(task)
                    .addOnSuccessListener(aVoid -> {
                        callback.onSuccess(null);
                    })
                    .addOnFailureListener(e -> {
                        callback.onError(e.getMessage());
                    });
        } else {
            callback.onError("Task ID is required for update");
        }
    }

    public void delete(TaskEntity task, FirebaseCallback<Void> callback) {
        if (task.id != null && !task.id.isEmpty()) {
            getTasksRef().document(task.id).delete()
                    .addOnSuccessListener(aVoid -> {
                        callback.onSuccess(null);
                    })
                    .addOnFailureListener(e -> {
                        callback.onError(e.getMessage());
                    });
        } else {
            callback.onError("Task ID is required for deletion");
        }
    }

    public void markCompleted(String id, boolean completed, FirebaseCallback<Void> callback) {
        getTasksRef().document(id).update("isCompleted", completed)
                .addOnSuccessListener(aVoid -> {
                    callback.onSuccess(null);
                })
                .addOnFailureListener(e -> {
                    callback.onError(e.getMessage());
                });
    }

    public void markCompletedForToday(String id, FirebaseCallback<Void> callback) {
        try {
            // Get today's date in same format as TaskEntity.markCompletedToday()
            java.text.SimpleDateFormat dateFormat = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault());
            String today = dateFormat.format(new java.util.Date());
            
            getTasksRef().document(id)
                    .update("lastCompletedDate", today)
                    .addOnSuccessListener(aVoid -> {
                        Log.d(TAG, "Task marked completed for today: " + id);
                        callback.onSuccess(null);
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error marking task completed for today: " + id, e);
                        callback.onError(e.getMessage());
                    });
        } catch (Exception e) {
            Log.e(TAG, "Exception in markCompletedForToday", e);
            callback.onError("Failed to mark task completed for today: " + e.getMessage());
        }
    }

    public void completeTask(String id, FirebaseCallback<Void> callback) {
        try {
            // Follow exact same pattern as ReminderRepository.completeReminder()
            getTasksRef().document(id).get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (!documentSnapshot.exists()) {
                            callback.onError("Task not found");
                            return;
                        }
                        
                        TaskEntity task = documentSnapshot.toObject(TaskEntity.class);
                        if (task == null) {
                            callback.onError("Failed to parse task");
                            return;
                        }
                        
                        // Mark as completed for today (same as ReminderEntity.markCompletedToday())
                        task.markCompletedToday();
                        
                        // Update the task in Firestore
                        Map<String, Object> updates = new HashMap<>();
                        updates.put("lastCompletedDate", task.getLastCompletedDate());
                        
                        // Only mark as permanently completed if it's not a repeating task
                        if (!task.isRepeating) {
                            updates.put("isCompleted", true);
                            Log.d(TAG, "Non-repeating task marked complete: " + id);
                        } else {
                            Log.d(TAG, "Repeating task marked complete for today: " + id);
                        }
                        
                        // Resolve caretaker notifications when task is completed
                        if (caretakerScheduler != null) {
                            caretakerScheduler.resolveIncompleteReminderAlert(id);
                        }
                        
                        getTasksRef().document(id)
                                .update(updates)
                                .addOnSuccessListener(aVoid -> callback.onSuccess(null))
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "Error updating task completion", e);
                                    callback.onError(e.getMessage());
                                });
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error getting task for completion", e);
                        callback.onError(e.getMessage());
                    });
        } catch (Exception e) {
            Log.e(TAG, "Exception in completeTask", e);
            callback.onError("Failed to complete task: " + e.getMessage());
        }
    }

    /**
     * Reset daily completion status for all repeating tasks (exact same logic as ReminderRepository)
     * This should be called at midnight or app startup to reset task completion for the new day
     */
    public void resetDailyCompletionStatus() {
        try {
            Log.d(TAG, "Starting daily completion status reset for tasks");
            
            getTasksRef()
                    .whereEqualTo("isRepeating", true)
                    .get()
                    .addOnSuccessListener(queryDocumentSnapshots -> {
                        for (var doc : queryDocumentSnapshots) {
                            TaskEntity task = doc.toObject(TaskEntity.class);
                            if (task != null && task.isCompletedToday()) {
                                // Reset the lastCompletedDate to make it appear unchecked (same as ReminderRepository)
                                Map<String, Object> updates = new HashMap<>();
                                updates.put("lastCompletedDate", null);
                                
                                getTasksRef()
                                        .document(doc.getId())
                                        .update(updates)
                                        .addOnSuccessListener(aVoid -> 
                                            Log.d(TAG, "Reset completion status for task: " + doc.getId()))
                                        .addOnFailureListener(e -> 
                                            Log.e(TAG, "Error resetting completion status for " + doc.getId(), e));
                            }
                        }
                        Log.d(TAG, "Daily completion status reset completed for tasks");
                    })
                    .addOnFailureListener(e -> Log.e(TAG, "Error fetching repeating tasks for reset", e));
        } catch (Exception e) {
            Log.e(TAG, "Exception in resetDailyCompletionStatus", e);
        }
    }

    /**
     * Convert TaskEntity to ReminderEntity for caretaker notification compatibility
     */
    private com.mihir.alzheimerscaregiver.data.entity.ReminderEntity createReminderFromTask(TaskEntity task) {
        com.mihir.alzheimerscaregiver.data.entity.ReminderEntity reminder = 
                new com.mihir.alzheimerscaregiver.data.entity.ReminderEntity(
                    task.name, 
                    task.description != null ? task.description : "", 
                    task.scheduledTimeEpochMillis != null ? task.scheduledTimeEpochMillis : 0L, 
                    task.isCompleted,
                    task.isRepeating
                );
        reminder.id = task.id;
        reminder.lastCompletedDate = task.lastCompletedDate;
        return reminder;
    }

    /**
     * Reschedule all task alarms - called during midnight reset
     * This ensures repeating tasks get rescheduled for the new day
     */
    public void rescheduleAllTaskAlarms(Context context) {
        try {
            Log.d(TAG, "Starting task alarm rescheduling");
            
            getTasksRef()
                    .whereEqualTo("isRepeating", true)
                    .whereEqualTo("enableAlarm", true)
                    .get()
                    .addOnSuccessListener(queryDocumentSnapshots -> {
                        for (var doc : queryDocumentSnapshots) {
                            TaskEntity task = doc.toObject(TaskEntity.class);
                            if (task != null && task.scheduledTimeEpochMillis != null) {
                                // Check if task should repeat today
                                if (shouldTaskRepeatToday(task)) {
                                    // Calculate next alarm time for today
                                    long nextAlarmTime = calculateNextAlarmTime(task.scheduledTimeEpochMillis);
                                    
                                    // Only reschedule if the time is in the future
                                    if (nextAlarmTime > System.currentTimeMillis()) {
                                        com.mihir.alzheimerscaregiver.notifications.TaskReminderScheduler.schedule(
                                            context,
                                            nextAlarmTime,
                                            task.name,
                                            task.description != null ? task.description : ""
                                        );
                                        Log.d(TAG, "Rescheduled task alarm: " + task.name + " for " + nextAlarmTime);
                                    }
                                }
                            }
                        }
                        Log.d(TAG, "Task alarm rescheduling completed");
                    })
                    .addOnFailureListener(e -> Log.e(TAG, "Error fetching tasks for rescheduling", e));
        } catch (Exception e) {
            Log.e(TAG, "Exception in rescheduleAllTaskAlarms", e);
        }
    }
    
    /**
     * Check if a task should repeat today based on its day settings
     */
    private boolean shouldTaskRepeatToday(TaskEntity task) {
        java.time.LocalDate today = java.time.LocalDate.now();
        java.time.DayOfWeek dayOfWeek = today.getDayOfWeek();
        
        switch (dayOfWeek) {
            case SUNDAY: return task.repeatOnSunday;
            case MONDAY: return task.repeatOnMonday;
            case TUESDAY: return task.repeatOnTuesday;
            case WEDNESDAY: return task.repeatOnWednesday;
            case THURSDAY: return task.repeatOnThursday;
            case FRIDAY: return task.repeatOnFriday;
            case SATURDAY: return task.repeatOnSaturday;
            default: return false;
        }
    }
    
    /**
     * Calculate the next alarm time for today based on the original scheduled time
     */
    private long calculateNextAlarmTime(long originalScheduledTime) {
        java.time.Instant originalInstant = java.time.Instant.ofEpochMilli(originalScheduledTime);
        java.time.LocalTime originalTime = originalInstant.atZone(java.time.ZoneId.systemDefault()).toLocalTime();
        
        // Get today's date with the same time
        java.time.LocalDate today = java.time.LocalDate.now();
        java.time.LocalDateTime todayAtTime = today.atTime(originalTime);
        
        return todayAtTime.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    // Helper methods for date calculations
    private long getStartOfDay() {
        java.time.LocalDate today = java.time.LocalDate.now();
        return today.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private long getEndOfDay() {
        java.time.LocalDate today = java.time.LocalDate.now();
        return today.atTime(23, 59, 59).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}


