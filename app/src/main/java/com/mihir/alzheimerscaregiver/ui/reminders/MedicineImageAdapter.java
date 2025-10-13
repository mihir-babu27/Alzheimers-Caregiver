package com.mihir.alzheimerscaregiver.ui.reminders;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
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
                } else if (imageUrl.startsWith("data:image/")) {
                    // Load from Base64 string (Free cross-app solution)
                    try {
                        // Extract Base64 data from data URL
                        String base64Data = imageUrl.substring(imageUrl.indexOf(",") + 1);
                        byte[] decodedBytes = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT);
                        Bitmap bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);
                        medicineImageView.setImageBitmap(bitmap);
                        Log.d("MedicineImageAdapter", "Successfully loaded Base64 image");
                    } catch (Exception e) {
                        Log.e("MedicineImageAdapter", "Failed to decode Base64 image", e);
                        medicineImageView.setImageResource(R.drawable.ic_image_placeholder);
                    }
                } else if (imageUrl.startsWith("https://") && imageUrl.contains("firebasestorage.googleapis.com")) {
                    // Load from Firebase Storage URL (if Firebase Storage is enabled)
                    FirebaseStorage storage = FirebaseStorage.getInstance();
                    StorageReference imageRef = storage.getReferenceFromUrl(imageUrl);
                    
                    // Download image and display it
                    final long ONE_MEGABYTE = 1024 * 1024;
                    imageRef.getBytes(ONE_MEGABYTE).addOnSuccessListener(bytes -> {
                        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                        medicineImageView.setImageBitmap(bitmap);
                    }).addOnFailureListener(exception -> {
                        Log.e("MedicineImageAdapter", "Failed to load image from Firebase Storage", exception);
                        medicineImageView.setImageResource(R.drawable.ic_image_placeholder);
                    });
                } else {
                    // For other remote images or unknown formats
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