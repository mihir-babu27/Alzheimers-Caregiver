
package com.mihir.alzheimerscaregiver.repository;

import android.content.Context;
import android.util.Log;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.mihir.alzheimerscaregiver.data.entity.ReminderEntity;
import com.mihir.alzheimerscaregiver.data.FirebaseConfig;
import com.mihir.alzheimerscaregiver.caretaker.CaretakerNotificationScheduler;

import java.util.ArrayList;
import java.util.List;

public class ReminderRepository {

    private static final String TAG = "ReminderRepository";
    private final FirebaseFirestore db;
    private final FirebaseAuth auth;
    private final CaretakerNotificationScheduler caretakerScheduler;

    public ReminderRepository() {
        this(null);
    }

    public ReminderRepository(Context context) {
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
            } else {
                caretakerScheduler = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error initializing ReminderRepository", e);
            throw new RuntimeException("Failed to initialize ReminderRepository: " + e.getMessage(), e);
        }
    }

    private CollectionReference getRemindersRef() {
        try {
            String patientId = auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : "default";
            return db.collection("patients").document(patientId).collection("reminders");
        } catch (Exception e) {
            Log.e(TAG, "Error getting reminders reference", e);
            throw new RuntimeException("Failed to get reminders reference: " + e.getMessage(), e);
        }
    }

    public interface FirebaseCallback<T> {
        void onSuccess(T result);
        void onError(String error);
    }

    public void getAllRemindersSortedByDate(FirebaseCallback<List<ReminderEntity>> callback) {
        try {
            CollectionReference remindersRef = getRemindersRef();
            remindersRef.orderBy("scheduledTimeEpochMillis")
                    .get()
                    .addOnSuccessListener(queryDocumentSnapshots -> {
                        try {
                            List<ReminderEntity> list = new ArrayList<>();
                            if (queryDocumentSnapshots != null) {
                                for (var doc : queryDocumentSnapshots) {
                                    ReminderEntity entity = doc.toObject(ReminderEntity.class);
                                    if (entity != null) {
                                        entity.id = doc.getId();
                                        list.add(entity);
                                    }
                                }
                            }
                            callback.onSuccess(list);
                        } catch (Exception e) {
                            Log.e(TAG, "Error processing reminders data", e);
                            callback.onError("Error processing reminders data: " + e.getMessage());
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error fetching reminders", e);
                        callback.onError(e.getMessage());
                    });
        } catch (Exception e) {
            Log.e(TAG, "Exception in getAllRemindersSortedByDate", e);
            callback.onError("Failed to fetch reminders: " + e.getMessage());
        }
    }

    public void search(String query, FirebaseCallback<List<ReminderEntity>> callback) {
        try {
            CollectionReference remindersRef = getRemindersRef();
            remindersRef.whereGreaterThanOrEqualTo("title", query)
                    .whereLessThanOrEqualTo("title", query + '\uf8ff')
                    .get()
                    .addOnSuccessListener(queryDocumentSnapshots -> {
                        try {
                            List<ReminderEntity> list = new ArrayList<>();
                            if (queryDocumentSnapshots != null) {
                                for (var doc : queryDocumentSnapshots) {
                                    ReminderEntity entity = doc.toObject(ReminderEntity.class);
                                    if (entity != null) {
                                        entity.id = doc.getId();
                                        list.add(entity);
                                    }
                                }
                            }
                            callback.onSuccess(list);
                        } catch (Exception e) {
                            Log.e(TAG, "Error processing search results", e);
                            callback.onError("Error processing search results: " + e.getMessage());
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error searching reminders", e);
                        callback.onError(e.getMessage());
                    });
        } catch (Exception e) {
            Log.e(TAG, "Exception in search", e);
            callback.onError("Failed to search reminders: " + e.getMessage());
        }
    }

    public void insert(ReminderEntity reminder, FirebaseCallback<Void> callback) {
        try {
            CollectionReference remindersRef = getRemindersRef();
            if (reminder.id == null || reminder.id.isEmpty()) {
                // Generate a new ID if not present
                DocumentReference docRef = remindersRef.document();
                reminder.id = docRef.getId();
                docRef.set(reminder)
                        .addOnSuccessListener(aVoid -> {
                            callback.onSuccess(null);
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Error inserting reminder", e);
                            callback.onError(e.getMessage());
                        });
            } else {
                remindersRef.document(reminder.id).set(reminder)
                        .addOnSuccessListener(aVoid -> {
                            callback.onSuccess(null);
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Error inserting reminder", e);
                            callback.onError(e.getMessage());
                        });
            }
        } catch (Exception e) {
            Log.e(TAG, "Exception in insert", e);
            callback.onError("Failed to insert reminder: " + e.getMessage());
        }
    }

    public void addReminder(ReminderEntity reminder, FirebaseCallback<Void> callback) {
        try {
            // First insert the reminder
            insert(reminder, new FirebaseCallback<Void>() {
                @Override
                public void onSuccess(Void result) {
                    // If caretaker notifications are enabled and reminder has a scheduled time
                    if (caretakerScheduler != null && reminder.scheduledTimeEpochMillis != null && 
                        reminder.scheduledTimeEpochMillis > System.currentTimeMillis()) {
                        
                        Log.d(TAG, "Scheduling caretaker notifications for reminder: " + reminder.id);
                        caretakerScheduler.scheduleCaretakerNotifications(reminder);
                    }
                    callback.onSuccess(result);
                }

                @Override
                public void onError(String error) {
                    callback.onError(error);
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Exception in addReminder", e);
            callback.onError("Failed to add reminder: " + e.getMessage());
        }
    }

    public void update(ReminderEntity reminder, FirebaseCallback<Void> callback) {
        try {
            CollectionReference remindersRef = getRemindersRef();
            if (reminder.id != null && !reminder.id.isEmpty()) {
                remindersRef.document(reminder.id).set(reminder)
                        .addOnSuccessListener(aVoid -> {
                            callback.onSuccess(null);
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Error updating reminder", e);
                            callback.onError(e.getMessage());
                        });
            } else {
                callback.onError("Reminder ID is required for update");
            }
        } catch (Exception e) {
            Log.e(TAG, "Exception in update", e);
            callback.onError("Failed to update reminder: " + e.getMessage());
        }
    }

    public void delete(ReminderEntity reminder, FirebaseCallback<Void> callback) {
        try {
            CollectionReference remindersRef = getRemindersRef();
            if (reminder.id != null && !reminder.id.isEmpty()) {
                remindersRef.document(reminder.id).delete()
                        .addOnSuccessListener(aVoid -> {
                            callback.onSuccess(null);
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Error deleting reminder", e);
                            callback.onError(e.getMessage());
                        });
            } else {
                callback.onError("Reminder ID is required for deletion");
            }
        } catch (Exception e) {
            Log.e(TAG, "Exception in delete", e);
            callback.onError("Failed to delete reminder: " + e.getMessage());
        }
    }

    public void markCompleted(String id, boolean completed, FirebaseCallback<Void> callback) {
        try {
            CollectionReference remindersRef = getRemindersRef();
            remindersRef.document(id).update("isCompleted", completed)
                    .addOnSuccessListener(aVoid -> {
                        callback.onSuccess(null);
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error marking reminder completed", e);
                        callback.onError(e.getMessage());
                    });
        } catch (Exception e) {
            Log.e(TAG, "Exception in markCompleted", e);
            callback.onError("Failed to mark reminder completed: " + e.getMessage());
        }
    }

    public void completeReminder(String id, FirebaseCallback<Void> callback) {
        try {
            // First mark as completed
            markCompleted(id, true, new FirebaseCallback<Void>() {
                @Override
                public void onSuccess(Void result) {
                    // If caretaker notifications are enabled, resolve any pending alerts
                    if (caretakerScheduler != null) {
                        Log.d(TAG, "Resolving caretaker notifications for completed reminder: " + id);
                        caretakerScheduler.resolveIncompleteReminderAlert(id);
                    }
                    callback.onSuccess(result);
                }

                @Override
                public void onError(String error) {
                    callback.onError(error);
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Exception in completeReminder", e);
            callback.onError("Failed to complete reminder: " + e.getMessage());
        }
    }
    
    /**
     * Get a reminder by its ID
     * 
     * @param id The ID of the reminder to retrieve
     * @param callback Callback to handle success or error
     */
    public void getById(String id, FirebaseCallback<ReminderEntity> callback) {
        try {
            if (id == null || id.isEmpty()) {
                callback.onError("Reminder ID cannot be null or empty");
                return;
            }
            
            CollectionReference remindersRef = getRemindersRef();
            remindersRef.document(id).get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (documentSnapshot.exists()) {
                            ReminderEntity entity = documentSnapshot.toObject(ReminderEntity.class);
                            if (entity != null) {
                                entity.id = documentSnapshot.getId();
                                callback.onSuccess(entity);
                            } else {
                                callback.onError("Failed to parse reminder data");
                            }
                        } else {
                            callback.onError("Reminder not found with ID: " + id);
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error getting reminder by ID: " + id, e);
                        callback.onError("Error retrieving reminder: " + e.getMessage());
                    });
        } catch (Exception e) {
            Log.e(TAG, "Exception in getById", e);
            callback.onError("Failed to get reminder: " + e.getMessage());
        }
    }
    
    /**
     * Get all active (non-completed) reminders sorted by date
     * 
     * @param callback Callback to handle success or error
     */
    public void getAllActiveReminders(FirebaseCallback<List<ReminderEntity>> callback) {
        try {
            CollectionReference remindersRef = getRemindersRef();
            remindersRef.whereEqualTo("isCompleted", false)
                    .orderBy("scheduledTimeEpochMillis")
                    .get()
                    .addOnSuccessListener(queryDocumentSnapshots -> {
                        try {
                            List<ReminderEntity> list = new ArrayList<>();
                            if (queryDocumentSnapshots != null) {
                                for (var doc : queryDocumentSnapshots) {
                                    ReminderEntity entity = doc.toObject(ReminderEntity.class);
                                    if (entity != null) {
                                        entity.id = doc.getId();
                                        list.add(entity);
                                    }
                                }
                            }
                            callback.onSuccess(list);
                        } catch (Exception e) {
                            Log.e(TAG, "Error processing active reminders data", e);
                            callback.onError("Error processing active reminders: " + e.getMessage());
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error fetching active reminders", e);
                        callback.onError(e.getMessage());
                    });
        } catch (Exception e) {
            Log.e(TAG, "Exception in getAllActiveReminders", e);
            callback.onError("Failed to fetch active reminders: " + e.getMessage());
        }
    }
}


