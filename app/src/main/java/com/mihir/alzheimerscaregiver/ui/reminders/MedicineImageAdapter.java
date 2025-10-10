package com.mihir.alzheimerscaregiver.ui.reminders;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.mihir.alzheimerscaregiver.R;

import java.io.InputStream;
import java.util.List;

public class MedicineImageAdapter extends RecyclerView.Adapter<MedicineImageAdapter.ImageViewHolder> {

    public interface OnImageActionListener {
        void onImageRemoved(int position);
    }

    private final List<String> imageUrls;
    private final Context context;
    private OnImageActionListener listener;
    private boolean showRemoveButton = true;

    public MedicineImageAdapter(Context context, List<String> imageUrls) {
        this.context = context;
        this.imageUrls = imageUrls;
    }

    public MedicineImageAdapter(Context context, List<String> imageUrls, boolean showRemoveButton) {
        this.context = context;
        this.imageUrls = imageUrls;
        this.showRemoveButton = showRemoveButton;
    }

    public void setOnImageActionListener(OnImageActionListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public ImageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_medicine_image, parent, false);
        return new ImageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ImageViewHolder holder, int position) {
        String imageUrl = imageUrls.get(position);
        holder.bind(imageUrl, position);
    }

    @Override
    public int getItemCount() {
        return imageUrls.size();
    }

    class ImageViewHolder extends RecyclerView.ViewHolder {
        private final ImageView medicineImageView;
        private final ImageButton btnRemoveImage;

        public ImageViewHolder(@NonNull View itemView) {
            super(itemView);
            medicineImageView = itemView.findViewById(R.id.medicineImageView);
            btnRemoveImage = itemView.findViewById(R.id.btnRemoveImage);
        }

        public void bind(String imageUrl, int position) {
            // Load image from URI
            try {
                if (imageUrl.startsWith("content://") || imageUrl.startsWith("file://")) {
                    // Load from local URI
                    Uri uri = Uri.parse(imageUrl);
                    InputStream inputStream = context.getContentResolver().openInputStream(uri);
                    Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                    medicineImageView.setImageBitmap(bitmap);
                    if (inputStream != null) {
                        inputStream.close();
                    }
                } else {
                    // For now, show placeholder for Firebase URLs or other remote images
                    // In a full implementation, you'd use Glide or Picasso here
                    medicineImageView.setImageResource(R.drawable.ic_image_placeholder);
                }
            } catch (Exception e) {
                medicineImageView.setImageResource(R.drawable.ic_image_placeholder);
            }

            // Show/hide remove button based on configuration
            if (showRemoveButton) {
                btnRemoveImage.setVisibility(View.VISIBLE);
                btnRemoveImage.setOnClickListener(v -> {
                    if (listener != null) {
                        listener.onImageRemoved(position);
                    }
                });
            } else {
                btnRemoveImage.setVisibility(View.GONE);
            }
        }
    }
}