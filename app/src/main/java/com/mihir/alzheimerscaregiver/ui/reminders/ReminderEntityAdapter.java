package com.mihir.alzheimerscaregiver.ui.reminders;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.mihir.alzheimerscaregiver.R;
import com.mihir.alzheimerscaregiver.data.entity.ReminderEntity;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ReminderEntityAdapter extends RecyclerView.Adapter<ReminderEntityAdapter.ReminderViewHolder> {

    public interface OnReminderInteractionListener {
        void onCompletionToggled(ReminderEntity reminder);
        void onItemClicked(ReminderEntity reminder);
        void onItemSwipedToDelete(ReminderEntity reminder, int position);
    }

    private final List<ReminderEntity> reminders = new ArrayList<>();
    private OnReminderInteractionListener listener;

    public void setListener(OnReminderInteractionListener listener) { this.listener = listener; }

    public void submitList(List<ReminderEntity> list) {
        reminders.clear();
        if (list != null) reminders.addAll(list);
        notifyDataSetChanged();
    }

    public ReminderEntity getItem(int position) { return reminders.get(position); }

    @NonNull
    @Override
    public ReminderViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_enhanced_reminder, parent, false);
        return new ReminderViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ReminderViewHolder holder, int position) {
        holder.bind(reminders.get(position));
    }

    @Override
    public int getItemCount() { return reminders.size(); }

    class ReminderViewHolder extends RecyclerView.ViewHolder {
        private final CheckBox taskCheckBox;
        private final TextView taskStatusText;
        private final TextView medicineNamesText;
        private final RecyclerView medicineImagesRecyclerView;

        ReminderViewHolder(@NonNull View itemView) {
            super(itemView);
            taskCheckBox = itemView.findViewById(R.id.taskCheckBox);
            taskStatusText = itemView.findViewById(R.id.taskStatusText);
            medicineNamesText = itemView.findViewById(R.id.medicineNamesText);
            medicineImagesRecyclerView = itemView.findViewById(R.id.medicineImagesRecyclerView);
            
            // Setup images RecyclerView
            medicineImagesRecyclerView.setLayoutManager(new LinearLayoutManager(itemView.getContext(), LinearLayoutManager.HORIZONTAL, false));
        }

        void bind(ReminderEntity r) {
            String subtitle = r.scheduledTimeEpochMillis == null ? "" :
                    new SimpleDateFormat("EEE, MMM d h:mm a", Locale.getDefault()).format(new Date(r.scheduledTimeEpochMillis));
            String title = r.title + (subtitle.isEmpty() ? "" : (" • " + subtitle));
            taskCheckBox.setText(title);
            
            // Display multiple medicine names if available
            if (r.medicineNames != null && r.medicineNames.size() > 1) {
                medicineNamesText.setVisibility(View.VISIBLE);
                medicineNamesText.setText("Medicines: " + String.join(", ", r.medicineNames));
            } else {
                medicineNamesText.setVisibility(View.GONE);
            }
            
            // Display medicine images if available
            if (r.imageUrls != null && !r.imageUrls.isEmpty()) {
                medicineImagesRecyclerView.setVisibility(View.VISIBLE);
                MedicineImageAdapter imageAdapter = new MedicineImageAdapter(itemView.getContext(), r.imageUrls);
                medicineImagesRecyclerView.setAdapter(imageAdapter);
            } else {
                medicineImagesRecyclerView.setVisibility(View.GONE);
            }
            
            // For repeating reminders, check if completed today; for others, check isCompleted
            boolean isChecked = r.isRepeating ? r.isCompletedToday() : r.isCompleted;
            taskCheckBox.setChecked(isChecked);
            updateStatus(r);

            taskCheckBox.setOnCheckedChangeListener(null);
            taskCheckBox.setOnCheckedChangeListener((b, checked) -> {
                if (r.isRepeating) {
                    // For repeating reminders, update the lastCompletedDate locally for immediate UI feedback
                    if (checked) {
                        r.markCompletedToday();
                    } else {
                        r.lastCompletedDate = null;
                    }
                } else {
                    r.isCompleted = checked;
                }
                updateStatus(r);
                if (listener != null) listener.onCompletionToggled(r);
            });
            itemView.setOnClickListener(v -> { if (listener != null) listener.onItemClicked(r); });
        }

        private void updateStatus(ReminderEntity r) {
            // Check completion status based on reminder type
            boolean isCompleted = r.isRepeating ? r.isCompletedToday() : r.isCompleted;
            
            if (isCompleted) {
                taskStatusText.setText("✓ Completed");
                taskStatusText.setTextColor(itemView.getContext().getResources().getColor(R.color.success));
            } else {
                taskStatusText.setText("Scheduled");
                taskStatusText.setTextColor(itemView.getContext().getResources().getColor(R.color.text_secondary));
            }
        }
    }
}


