package com.mihir.alzheimerscaregiver.caretaker.adapters;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.mihir.alzheimerscaregiver.caretaker.R;
import com.mihir.alzheimerscaregiver.caretaker.data.entity.EmergencyContactEntity;

import java.util.List;

/**
 * EmergencyContactAdapter - RecyclerView adapter for emergency contacts in CaretakerApp
 * Handles display and interaction for emergency contact items with call functionality
 */
public class EmergencyContactAdapter extends RecyclerView.Adapter<EmergencyContactAdapter.EmergencyContactViewHolder> {

    public interface OnEmergencyContactInteractionListener {
        void onItemClicked(EmergencyContactEntity contact);
        void onCallButtonClicked(EmergencyContactEntity contact);
        void onItemSwipedToDelete(EmergencyContactEntity contact, int position);
        void onSetPrimary(EmergencyContactEntity contact);
    }

    private final Context context;
    private final List<EmergencyContactEntity> contacts;
    private OnEmergencyContactInteractionListener listener;

    public EmergencyContactAdapter(Context context, List<EmergencyContactEntity> contacts) {
        this.context = context;
        this.contacts = contacts;
    }

    public void setListener(OnEmergencyContactInteractionListener listener) {
        this.listener = listener;
    }

    public EmergencyContactEntity getItem(int position) {
        return contacts.get(position);
    }

    @NonNull
    @Override
    public EmergencyContactViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_emergency_contact, parent, false);
        return new EmergencyContactViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull EmergencyContactViewHolder holder, int position) {
        holder.bind(contacts.get(position));
    }

    @Override
    public int getItemCount() {
        return contacts.size();
    }

    /**
     * ViewHolder class for emergency contact items
     */
    public class EmergencyContactViewHolder extends RecyclerView.ViewHolder {
        
        private TextView contactNameText;
        private TextView phoneNumberText;
        private TextView relationshipText;
        private ImageButton callButton;
        private View primaryIndicator;

        public EmergencyContactViewHolder(@NonNull View itemView) {
            super(itemView);
            
            contactNameText = itemView.findViewById(R.id.contactNameText);
            phoneNumberText = itemView.findViewById(R.id.phoneNumberText);
            relationshipText = itemView.findViewById(R.id.relationshipText);
            callButton = itemView.findViewById(R.id.callButton);
            primaryIndicator = itemView.findViewById(R.id.primaryIndicator);
        }

        public void bind(EmergencyContactEntity contact) {
            // Set contact name
            contactNameText.setText(contact.getName());
            
            // Set phone number
            phoneNumberText.setText(contact.getPhoneNumber());
            
            // Set relationship with visibility logic
            if (contact.getRelationship() != null && !contact.getRelationship().trim().isEmpty()) {
                relationshipText.setText(contact.getRelationship());
                relationshipText.setVisibility(View.VISIBLE);
            } else {
                relationshipText.setVisibility(View.GONE);
            }
            
            // Show/hide primary indicator
            if (contact.isPrimary()) {
                primaryIndicator.setVisibility(View.VISIBLE);
            } else {
                primaryIndicator.setVisibility(View.GONE);
            }

            // Set up click listeners
            setupClickListeners(contact);
        }

        private void setupClickListeners(EmergencyContactEntity contact) {
            // Call button click
            callButton.setOnClickListener(v -> {
                v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
                if (listener != null) {
                    listener.onCallButtonClicked(contact);
                } else {
                    // Fallback: direct call attempt
                    makeDirectPhoneCall(contact.getPhoneNumber(), contact.getName());
                }
            });

            // Item click for editing
            itemView.setOnClickListener(v -> {
                v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
                if (listener != null) {
                    listener.onItemClicked(contact);
                }
            });

            // Long press for primary setting (optional feature)
            itemView.setOnLongClickListener(v -> {
                v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                if (listener != null && !contact.isPrimary()) {
                    // Show confirmation for setting as primary
                    showSetPrimaryDialog(contact);
                }
                return true;
            });
        }

        /**
         * Show dialog to confirm setting contact as primary
         */
        private void showSetPrimaryDialog(EmergencyContactEntity contact) {
            new android.app.AlertDialog.Builder(context)
                    .setTitle("Set Primary Contact")
                    .setMessage("Set '" + contact.getName() + "' as the primary emergency contact?")
                    .setPositiveButton("Yes", (dialog, which) -> {
                        if (listener != null) {
                            listener.onSetPrimary(contact);
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        }

        /**
         * Direct phone call method as fallback
         */
        private void makeDirectPhoneCall(String phoneNumber, String contactName) {
            try {
                Intent callIntent = new Intent(Intent.ACTION_CALL);
                callIntent.setData(Uri.parse("tel:" + phoneNumber));
                
                if (callIntent.resolveActivity(context.getPackageManager()) != null) {
                    Toast.makeText(context, "Calling " + contactName + "...", Toast.LENGTH_SHORT).show();
                    context.startActivity(callIntent);
                } else {
                    // Fallback to dialer
                    Intent dialIntent = new Intent(Intent.ACTION_DIAL);
                    dialIntent.setData(Uri.parse("tel:" + phoneNumber));
                    context.startActivity(dialIntent);
                    Toast.makeText(context, "Opening dialer for " + contactName, Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                Toast.makeText(context, "Unable to make call: " + e.getMessage(), 
                              Toast.LENGTH_SHORT).show();
            }
        }
    }

    /**
     * Update the entire contacts list
     */
    public void updateContacts(List<EmergencyContactEntity> newContacts) {
        contacts.clear();
        if (newContacts != null) {
            contacts.addAll(newContacts);
        }
        notifyDataSetChanged();
    }

    /**
     * Add a new contact to the list
     */
    public void addContact(EmergencyContactEntity contact) {
        if (contact != null) {
            contacts.add(contact);
            notifyItemInserted(contacts.size() - 1);
        }
    }

    /**
     * Remove a contact from the list
     */
    public void removeContact(int position) {
        if (position >= 0 && position < contacts.size()) {
            contacts.remove(position);
            notifyItemRemoved(position);
        }
    }

    /**
     * Update a specific contact
     */
    public void updateContact(int position, EmergencyContactEntity contact) {
        if (position >= 0 && position < contacts.size() && contact != null) {
            contacts.set(position, contact);
            notifyItemChanged(position);
        }
    }

    /**
     * Find contact position by ID
     */
    public int findContactPosition(String contactId) {
        for (int i = 0; i < contacts.size(); i++) {
            if (contacts.get(i).getId().equals(contactId)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Check if list is empty
     */
    public boolean isEmpty() {
        return contacts.isEmpty();
    }

    /**
     * Get contacts count
     */
    public int getContactsCount() {
        return contacts.size();
    }

    /**
     * Filter contacts by search query
     */
    public void filterContacts(String query, List<EmergencyContactEntity> originalContacts) {
        contacts.clear();
        
        if (query == null || query.trim().isEmpty()) {
            // Show all contacts if no query
            contacts.addAll(originalContacts);
        } else {
            String lowerQuery = query.toLowerCase().trim();
            
            for (EmergencyContactEntity contact : originalContacts) {
                if ((contact.getName() != null && contact.getName().toLowerCase().contains(lowerQuery)) ||
                    (contact.getPhoneNumber() != null && contact.getPhoneNumber().contains(query)) ||
                    (contact.getRelationship() != null && contact.getRelationship().toLowerCase().contains(lowerQuery))) {
                    contacts.add(contact);
                }
            }
        }
        
        notifyDataSetChanged();
    }
}