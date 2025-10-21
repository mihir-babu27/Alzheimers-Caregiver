package com.mihir.alzheimerscaregiver.caretaker.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import androidx.recyclerview.widget.RecyclerView;


import com.mihir.alzheimerscaregiver.caretaker.R;
import com.mihir.alzheimerscaregiver.caretaker.data.entity.MedicationEntity;


import java.util.List;
import java.util.Locale;

/**
 * RecyclerView adapter for displaying medication list in CaretakerApp
 */
public class MedicationAdapter extends RecyclerView.Adapter<MedicationAdapter.MedicationViewHolder> {

    private List<MedicationEntity> medications;
    private OnMedicationClickListener listener;
    private Context context;

    public interface OnMedicationClickListener {
        void onMedicationClick(MedicationEntity medication);
        void onEditClick(MedicationEntity medication);
        void onDeleteClick(MedicationEntity medication);
        void onToggleStatusClick(MedicationEntity medication);
    }

    public MedicationAdapter(List<MedicationEntity> medications, OnMedicationClickListener listener) {
        this.medications = medications;
        this.listener = listener;
    }

    @NonNull
    @Override
    public MedicationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        this.context = parent.getContext();
        View view = LayoutInflater.from(context).inflate(R.layout.item_medication, parent, false);
        return new MedicationViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MedicationViewHolder holder, int position) {
        MedicationEntity medication = medications.get(position);
        holder.bind(medication);
    }

    @Override
    public int getItemCount() {
        return medications.size();
    }

    public void updateMedications(List<MedicationEntity> newMedications) {
        this.medications = newMedications;
        notifyDataSetChanged();
    }

    class MedicationViewHolder extends RecyclerView.ViewHolder {
        
        private android.widget.CheckBox checkBox;
        private TextView textName;
        private TextView textDosage;
        private TextView textTime;
        private TextView textStatus;
        private ImageButton buttonEdit;
        private ImageButton buttonDelete;

        public MedicationViewHolder(@NonNull View itemView) {
            super(itemView);
            
            checkBox = itemView.findViewById(R.id.checkBox);
            textName = itemView.findViewById(R.id.text_medication_name);
            textDosage = itemView.findViewById(R.id.text_dosage);
            textTime = itemView.findViewById(R.id.text_time);
            textStatus = itemView.findViewById(R.id.text_status);
            buttonEdit = itemView.findViewById(R.id.button_edit);
            buttonDelete = itemView.findViewById(R.id.button_delete);
        }

        public void bind(MedicationEntity medication) {
            // Basic information
            textName.setText(medication.getDisplayName());
            textDosage.setText(medication.dosage != null ? medication.dosage : "No dosage");
            textTime.setText(medication.time != null ? medication.time : "No time");
            
            // Checkbox - checked if medication is active
            checkBox.setChecked(medication.isActive);
            checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (listener != null) {
                    listener.onToggleStatusClick(medication);
                }
            });

            // Status (show only if there's something important to show)
            if (medication.isActive && medication.isOverdue()) {
                textStatus.setText("OVERDUE");
                textStatus.setTextColor(context.getResources().getColor(android.R.color.holo_red_dark));
                textStatus.setVisibility(View.VISIBLE);
            } else if (medication.isActive && medication.isDue()) {
                textStatus.setText("DUE NOW");
                textStatus.setTextColor(context.getResources().getColor(android.R.color.holo_orange_dark));
                textStatus.setVisibility(View.VISIBLE);
            } else {
                textStatus.setVisibility(View.GONE);
            }

            // Click listeners
            setupClickListeners(medication);
        }

        private void setupClickListeners(MedicationEntity medication) {
            // Item view click - view details
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onMedicationClick(medication);
                }
            });

            // Edit button
            buttonEdit.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onEditClick(medication);
                }
            });

            // Delete button
            buttonDelete.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onDeleteClick(medication);
                }
            });
        }
    }
}