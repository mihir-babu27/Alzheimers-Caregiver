package com.example.caretakerapp.adapter;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.example.caretakerapp.R;
import com.example.caretakerapp.entity.TaskEntity;

import java.util.List;

public class TaskAdapter extends RecyclerView.Adapter<TaskAdapter.TaskViewHolder> {
    private final Context context;
    private final List<TaskEntity> taskList;
    private final OnTaskInteractionListener listener;

    public TaskAdapter(Context context, List<TaskEntity> taskList, OnTaskInteractionListener listener) {
        this.context = context;
        this.taskList = taskList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public TaskViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_task, parent, false);
        return new TaskViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TaskViewHolder holder, int position) {
        TaskEntity task = taskList.get(position);
        
        // Set task name
        holder.textTaskName.setText(task.getTaskName());
        
        // Set task description
        if (task.getTaskDescription() != null && !task.getTaskDescription().trim().isEmpty()) {
            holder.textTaskDescription.setText(task.getTaskDescription());
            holder.textTaskDescription.setVisibility(View.VISIBLE);
        } else {
            holder.textTaskDescription.setVisibility(View.GONE);
        }
        
        // Set category
        if (task.getCategory() != null && !task.getCategory().trim().isEmpty()) {
            holder.textTaskCategory.setText(task.getCategory());
            holder.textTaskCategory.setVisibility(View.VISIBLE);
        } else {
            holder.textTaskCategory.setVisibility(View.GONE);
        }
        
        // Set scheduled time
        if (task.getScheduledTime() != null && !task.getScheduledTime().trim().isEmpty()) {
            holder.textScheduledTime.setText("⏰ " + task.getScheduledTime());
            holder.textScheduledTime.setVisibility(View.VISIBLE);
        } else {
            holder.textScheduledTime.setVisibility(View.GONE);
        }
        
        // Set repeat pattern
        if (task.isRepeating()) {
            String repeatPattern = task.getRepeatPatternString();
            if (task.isDailyRepeat()) {
                holder.textRepeatPattern.setText("🔄 Daily");
            } else if (task.isWeekdaysRepeat()) {
                holder.textRepeatPattern.setText("🔄 Weekdays");
            } else if (task.isWeekendsRepeat()) {
                holder.textRepeatPattern.setText("🔄 Weekends");
            } else {
                holder.textRepeatPattern.setText("🔄 " + repeatPattern);
            }
            holder.textRepeatPattern.setVisibility(View.VISIBLE);
        } else {
            holder.textRepeatPattern.setText("📅 One-time");
            holder.textRepeatPattern.setVisibility(View.VISIBLE);
        }
        
        // Set completion status
        boolean isCompleted;
        if (task.isRepeating()) {
            isCompleted = task.isCompletedToday() && task.isScheduledForToday();
        } else {
            isCompleted = task.isCompleted();
        }
        
        holder.checkboxCompleted.setChecked(isCompleted);
        
        // Set status text and styling
        if (task.isRepeating()) {
            if (task.isScheduledForToday()) {
                if (task.isCompletedToday()) {
                    holder.textStatus.setText("✅ Completed today");
                    holder.textStatus.setTextColor(Color.GREEN);
                    holder.cardView.setAlpha(0.7f);
                } else {
                    holder.textStatus.setText("⏳ Pending today");
                    holder.textStatus.setTextColor(Color.ORANGE);
                    holder.cardView.setAlpha(1.0f);
                }
            } else {
                holder.textStatus.setText("📋 Not scheduled today");
                holder.textStatus.setTextColor(Color.GRAY);
                holder.cardView.setAlpha(0.6f);
            }
        } else {
            if (task.isCompleted()) {
                holder.textStatus.setText("✅ Completed");
                holder.textStatus.setTextColor(Color.GREEN);
                holder.cardView.setAlpha(0.7f);
            } else {
                holder.textStatus.setText("⏳ Pending");
                holder.textStatus.setTextColor(Color.ORANGE);
                holder.cardView.setAlpha(1.0f);
            }
        }
        
        // Set notification icons
        if (task.isEnableAlarm()) {
            holder.iconAlarm.setVisibility(View.VISIBLE);
        } else {
            holder.iconAlarm.setVisibility(View.GONE);
        }
        
        if (task.isEnableCaretakerNotification()) {
            holder.iconCaretakerNotification.setVisibility(View.VISIBLE);
        } else {
            holder.iconCaretakerNotification.setVisibility(View.GONE);
        }
        
        // Set checkbox listener
        holder.checkboxCompleted.setOnCheckedChangeListener(null); // Clear previous listener
        holder.checkboxCompleted.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (listener != null) {
                listener.onTaskCompleted(task, isChecked);
            }
        });
        
        // Set edit button listener
        holder.buttonEdit.setOnClickListener(v -> {
            if (listener != null) {
                listener.onTaskEdit(task);
            }
        });
        
        // Set delete button listener
        holder.buttonDelete.setOnClickListener(v -> {
            if (listener != null) {
                listener.onTaskDelete(task);
            }
        });
        
        // Set card click listener for details
        holder.cardView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onTaskEdit(task);
            }
        });
    }

    @Override
    public int getItemCount() {
        return taskList.size();
    }

    public static class TaskViewHolder extends RecyclerView.ViewHolder {
        CardView cardView;
        TextView textTaskName;
        TextView textTaskDescription;
        TextView textTaskCategory;
        TextView textScheduledTime;
        TextView textRepeatPattern;
        TextView textStatus;
        CheckBox checkboxCompleted;
        ImageView buttonEdit;
        ImageView buttonDelete;
        ImageView iconAlarm;
        ImageView iconCaretakerNotification;

        public TaskViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = itemView.findViewById(R.id.cardViewTask);
            textTaskName = itemView.findViewById(R.id.textTaskName);
            textTaskDescription = itemView.findViewById(R.id.textTaskDescription);
            textTaskCategory = itemView.findViewById(R.id.textTaskCategory);
            textScheduledTime = itemView.findViewById(R.id.textScheduledTime);
            textRepeatPattern = itemView.findViewById(R.id.textRepeatPattern);
            textStatus = itemView.findViewById(R.id.textStatus);
            checkboxCompleted = itemView.findViewById(R.id.checkboxCompleted);
            buttonEdit = itemView.findViewById(R.id.buttonEditTask);
            buttonDelete = itemView.findViewById(R.id.buttonDeleteTask);
            iconAlarm = itemView.findViewById(R.id.iconAlarm);
            iconCaretakerNotification = itemView.findViewById(R.id.iconCaretakerNotification);
        }
    }

    public interface OnTaskInteractionListener {
        void onTaskCompleted(TaskEntity task, boolean isCompleted);
        void onTaskEdit(TaskEntity task);
        void onTaskDelete(TaskEntity task);
    }
}