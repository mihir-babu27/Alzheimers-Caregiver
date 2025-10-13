package com.mihir.alzheimerscaregiver.caretaker.ui;

import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.mihir.alzheimerscaregiver.caretaker.R;

import java.util.ArrayList;
import java.util.List;

public class MedicineImageAdapter extends RecyclerView.Adapter<MedicineImageAdapter.ImageViewHolder> {
    private List<String> imageUrls;
    private OnImageActionListener listener;
    private boolean showRemoveButton;

    public interface OnImageActionListener {
        void onImageRemove(int position);
        void onImageClick(int position);
    }

    public MedicineImageAdapter(List<String> imageUrls, OnImageActionListener listener) {
        this.imageUrls = imageUrls != null ? imageUrls : new ArrayList<>();
        this.listener = listener;
        this.showRemoveButton = true; // Show remove button by default
    }

    public MedicineImageAdapter(List<String> imageUrls, OnImageActionListener listener, boolean showRemoveButton) {
        this.imageUrls = imageUrls != null ? imageUrls : new ArrayList<>();
        this.listener = listener;
        this.showRemoveButton = showRemoveButton;
    }

    @NonNull
    @Override
    public ImageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_medicine_image, parent, false);
        return new ImageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ImageViewHolder holder, int position) {
        String imageUrl = imageUrls.get(position);
        
        // Load image using Glide with URI
        try {
            Uri imageUri = Uri.parse(imageUrl);
            Glide.with(holder.itemView.getContext())
                    .load(imageUri)
                    .apply(new RequestOptions()
                            .placeholder(R.drawable.ic_medication_placeholder)
                            .error(R.drawable.ic_medication_placeholder)
                            .centerCrop())
                    .into(holder.imageView);
        } catch (Exception e) {
            // Fallback to placeholder if URI parsing fails
            Glide.with(holder.itemView.getContext())
                    .load(R.drawable.ic_medication_placeholder)
                    .into(holder.imageView);
        }

        // Configure remove button visibility
        holder.removeButton.setVisibility(showRemoveButton ? View.VISIBLE : View.GONE);

        // Set click listeners
        holder.removeButton.setOnClickListener(v -> {
            if (listener != null && showRemoveButton) {
                listener.onImageRemove(holder.getAdapterPosition());
            }
        });

        holder.imageView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onImageClick(holder.getAdapterPosition());
            }
        });
    }

    @Override
    public int getItemCount() {
        return imageUrls.size();
    }

    public void updateImages(List<String> newImages) {
        this.imageUrls = newImages != null ? newImages : new ArrayList<>();
        notifyDataSetChanged();
    }

    public void removeImage(int position) {
        if (position >= 0 && position < imageUrls.size()) {
            imageUrls.remove(position);
            notifyItemRemoved(position);
            notifyItemRangeChanged(position, imageUrls.size());
        }
    }

    public void addImage(String imageUrl) {
        if (imageUrl != null) {
            imageUrls.add(imageUrl);
            notifyItemInserted(imageUrls.size() - 1);
        }
    }

    static class ImageViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView;
        ImageButton removeButton;

        ImageViewHolder(View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.imageView);
            removeButton = itemView.findViewById(R.id.removeImageButton);
        }
    }
}
