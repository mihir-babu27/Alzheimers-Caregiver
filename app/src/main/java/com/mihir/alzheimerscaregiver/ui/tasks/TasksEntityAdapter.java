package com.mihir.alzheimerscaregiver.ui.tasks;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.mihir.alzheimerscaregiver.R;
import com.mihir.alzheimerscaregiver.data.entity.TaskEntity;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TasksEntityAdapter extends RecyclerView.Adapter<TasksEntityAdapter.TaskViewHolder> {

    public interface OnTaskEntityInteractionListener {
        void onCompletionToggled(TaskEntity task);
        void onItemClicked(TaskEntity task);
        void onItemSwipedToDelete(TaskEntity task, int position);
    }

    private final List<TaskEntity> tasks = new ArrayList<>();
    private OnTaskEntityInteractionListener listener;

    public void setListener(OnTaskEntityInteractionListener listener) {
        this.listener = listener;
    }

    public void submitList(List<TaskEntity> newTasks) {
        tasks.clear();
        if (newTasks != null) {
            tasks.addAll(newTasks);
        }
        notifyDataSetChanged();
    }

    public TaskEntity getItem(int position) {
        return tasks.get(position);
    }

    @NonNull
    @Override
    public TaskViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_task, parent, false);
        return new TaskViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull TaskViewHolder holder, int position) {
        holder.bind(tasks.get(position));
    }

    @Override
    public int getItemCount() {
        return tasks.size();
    }

    public class TaskViewHolder extends RecyclerView.ViewHolder {
        private final CheckBox taskCheckBox;
        private final TextView taskStatusText;

        public TaskViewHolder(@NonNull View itemView) {
            super(itemView);
            taskCheckBox = itemView.findViewById(R.id.taskCheckBox);
            taskStatusText = itemView.findViewById(R.id.taskStatusText);
        }

        public void bind(TaskEntity task) {
            String title = task.name;
            
            // Add time information if scheduled
            if (task.scheduledTimeEpochMillis != null) {
                String time = new SimpleDateFormat("h:mm a", Locale.getDefault())
                        .format(new Date(task.scheduledTimeEpochMillis));
                title = title + " • " + time;
            }
            
            // Add repeat information for repeating tasks
            if (task.isRepeating) {
                String repeatInfo = task.getRepeatDaysDescription();
                title = title + " • " + repeatInfo;
            }

            taskCheckBox.setText(title);
            
            // For repeating tasks, check if completed today; for others, check isCompleted (same as ReminderEntityAdapter)
            boolean isChecked = task.isRepeating ? task.isCompletedToday() : task.isCompleted;
            taskCheckBox.setChecked(isChecked);
            updateStatusText(isChecked);

            taskCheckBox.setOnCheckedChangeListener(null);
            taskCheckBox.setOnCheckedChangeListener((buttonView, checked) -> {
                buttonView.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
                
                if (task.isRepeating) {
                    // For repeating tasks, update the lastCompletedDate locally for immediate UI feedback (same as ReminderEntityAdapter)
                    if (checked) {
                        task.markCompletedToday();
                    } else {
                        task.lastCompletedDate = null;
                    }
                } else {
                    task.isCompleted = checked;
                }
                
                updateStatusText(checked);
                if (listener != null) listener.onCompletionToggled(task);
            });

            itemView.setOnClickListener(v -> {
                v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
                if (listener != null) listener.onItemClicked(task);
            });

            // Apply styling based on correct completion status
            boolean isCompleted = task.isRepeating ? task.isCompletedToday() : task.isCompleted;
            applyCompletionStyling(isCompleted);
        }

        private void updateStatusText(boolean isCompleted) {
            if (isCompleted) {
                taskStatusText.setText("✓ Completed");
                taskStatusText.setTextColor(itemView.getContext().getResources().getColor(R.color.success));
            } else {
                taskStatusText.setText("Pending");
                taskStatusText.setTextColor(itemView.getContext().getResources().getColor(R.color.text_secondary));
            }
        }

        private void applyCompletionStyling(boolean isCompleted) {
            itemView.setAlpha(isCompleted ? 0.7f : 1.0f);
            taskCheckBox.setAlpha(isCompleted ? 0.8f : 1.0f);
        }
    }
}


