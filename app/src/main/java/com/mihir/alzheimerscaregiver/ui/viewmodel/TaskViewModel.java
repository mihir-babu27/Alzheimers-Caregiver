package com.mihir.alzheimerscaregiver.ui.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.mihir.alzheimerscaregiver.data.entity.TaskEntity;
import com.mihir.alzheimerscaregiver.repository.TaskRepository;

import java.util.List;

public class TaskViewModel extends AndroidViewModel {

    private final TaskRepository repository;
    private final MutableLiveData<List<TaskEntity>> todayTasks;
    private final MutableLiveData<List<TaskEntity>> allTasks;
    private final MutableLiveData<List<TaskEntity>> pendingTasks;
    private final MutableLiveData<String> errorMessage;

    public TaskViewModel(@NonNull Application application) {
        super(application);
        // Use context-aware constructor to enable caretaker notifications
        repository = new TaskRepository(application.getApplicationContext());
        todayTasks = new MutableLiveData<>();
        allTasks = new MutableLiveData<>();
        pendingTasks = new MutableLiveData<>();
        errorMessage = new MutableLiveData<>();
        loadTasks();
        
        // Reset daily completion status for repeating tasks
        repository.resetDailyCompletionStatus();
    }

    private void loadTasks() {
        loadTodayTasks();
        loadAllTasks();
        loadPendingTasks();
    }

    private void loadTodayTasks() {
        repository.getTodayTasks(new TaskRepository.FirebaseCallback<List<TaskEntity>>() {
            @Override
            public void onSuccess(List<TaskEntity> result) {
                todayTasks.setValue(result);
            }

            @Override
            public void onError(String error) {
                todayTasks.setValue(null);
            }
        });
    }

    private void loadAllTasks() {
        repository.getAllSortedBySchedule(new TaskRepository.FirebaseCallback<List<TaskEntity>>() {
            @Override
            public void onSuccess(List<TaskEntity> result) {
                allTasks.setValue(result);
            }

            @Override
            public void onError(String error) {
                allTasks.setValue(null);
            }
        });
    }

    private void loadPendingTasks() {
        repository.getPendingTasks(new TaskRepository.FirebaseCallback<List<TaskEntity>>() {
            @Override
            public void onSuccess(List<TaskEntity> result) {
                pendingTasks.setValue(result);
            }

            @Override
            public void onError(String error) {
                pendingTasks.setValue(null);
            }
        });
    }

    public LiveData<List<TaskEntity>> getTodayTasks() {
        return todayTasks;
    }

    public LiveData<List<TaskEntity>> getAllSortedBySchedule() {
        return allTasks;
    }

    public LiveData<List<TaskEntity>> getPendingTasks() {
        return pendingTasks;
    }

    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }

    public void search(String query) {
        repository.search(query, new TaskRepository.FirebaseCallback<List<TaskEntity>>() {
            @Override
            public void onSuccess(List<TaskEntity> result) {
                allTasks.setValue(result);
            }

            @Override
            public void onError(String error) {
                allTasks.setValue(null);
            }
        });
    }

    public void getByCategory(String category) {
        repository.getByCategory(category, new TaskRepository.FirebaseCallback<List<TaskEntity>>() {
            @Override
            public void onSuccess(List<TaskEntity> result) {
                allTasks.setValue(result);
            }

            @Override
            public void onError(String error) {
                allTasks.setValue(null);
            }
        });
    }

    public void insert(TaskEntity entity) {
        // Use addTask instead of insert to enable caretaker notifications
        repository.addTask(entity, new TaskRepository.FirebaseCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                loadTasks(); // Refresh all lists
            }

            @Override
            public void onError(String error) {
                errorMessage.setValue("Failed to add task: " + error);
            }
        });
    }

    public void update(TaskEntity entity) {
        repository.update(entity, new TaskRepository.FirebaseCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                loadTasks(); // Refresh all lists
            }

            @Override
            public void onError(String error) {
                // Handle error
            }
        });
    }

    public void delete(TaskEntity entity) {
        repository.delete(entity, new TaskRepository.FirebaseCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                loadTasks(); // Refresh all lists
            }

            @Override
            public void onError(String error) {
                // Handle error
            }
        });
    }

    public void markCompleted(String id, boolean completed) {
        if (completed) {
            // Use completeTask to handle daily repeating tasks and caretaker notifications
            repository.completeTask(id, new TaskRepository.FirebaseCallback<Void>() {
                @Override
                public void onSuccess(Void result) {
                    loadTasks(); // Refresh all lists
                }

                @Override
                public void onError(String error) {
                    errorMessage.setValue("Failed to complete task: " + error);
                }
            });
        } else {
            // For unchecking tasks, use the original markCompleted method
            repository.markCompleted(id, completed, new TaskRepository.FirebaseCallback<Void>() {
                @Override
                public void onSuccess(Void result) {
                    loadTasks(); // Refresh all lists
                }

                @Override
                public void onError(String error) {
                    errorMessage.setValue("Failed to update task: " + error);
                }
            });
        }
    }

    public void refresh() {
        loadTasks();
    }
}


