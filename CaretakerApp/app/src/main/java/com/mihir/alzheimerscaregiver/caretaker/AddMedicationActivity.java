package com.mihir.alzheimerscaregiver.caretaker;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import com.mihir.alzheimerscaregiver.caretaker.R;
import com.mihir.alzheimerscaregiver.caretaker.data.entity.ReminderEntity;
import com.mihir.alzheimerscaregiver.caretaker.ui.MedicineImageAdapter;

import android.app.TimePickerDialog;
import android.app.DatePickerDialog;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.util.Base64;


public class AddMedicationActivity extends AppCompatActivity implements MedicineImageAdapter.OnImageActionListener {

    private static final String TAG = "AddMedicationActivity";
    
    private EditText dosageEditText, timeEditText, scheduledDateEditText, descriptionEditText;
    private Button saveButton, cancelButton, addMedicineButton, addImageButton;
    private ProgressBar progressBar;
    private LinearLayout medicineNamesContainer;
    private RecyclerView imagesRecyclerView;
    private MedicineImageAdapter imageAdapter;
    private CheckBox checkRepeating;
    
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private FirebaseStorage storage;
    private String patientId;
    
    // Data lists
    private List<String> medicineNames = new ArrayList<>();
    private List<String> imageUrls = new ArrayList<>();
    
    // Activity result launchers
    private ActivityResultLauncher<Intent> imagePickerLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_medication);

        // Get patient ID from intent
        patientId = getIntent().getStringExtra("patientId");
        if (patientId == null) {
            Toast.makeText(this, "Error: Patient ID not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Initialize Firebase
        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();
        storage = FirebaseStorage.getInstance();

        // Initialize views
        initializeViews();
        
        // Initialize activity result launchers
        initializeActivityResultLaunchers();
        
        // Set up image RecyclerView
        setupImageRecyclerView();

        // Set click listeners
        setupClickListeners();
        
        // Set up time and date pickers
        setupTimeAndDatePickers();
        
        // Add initial medicine field
        addMedicineField();
    }

    private void initializeViews() {
        dosageEditText = findViewById(R.id.dosageEditText);
        timeEditText = findViewById(R.id.timeEditText);
        descriptionEditText = findViewById(R.id.descriptionEditText);
        scheduledDateEditText = findViewById(R.id.scheduledDateEditText);
        saveButton = findViewById(R.id.saveButton);
        cancelButton = findViewById(R.id.cancelButton);
        progressBar = findViewById(R.id.progressBar);
        addMedicineButton = findViewById(R.id.addMedicineButton);
        addImageButton = findViewById(R.id.addImageButton);
        medicineNamesContainer = findViewById(R.id.medicineNamesContainer);
        imagesRecyclerView = findViewById(R.id.imagesRecyclerView);
        checkRepeating = findViewById(R.id.checkRepeating);
    }

    private void initializeActivityResultLaunchers() {
        // Image picker launcher
        imagePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri imageUri = result.getData().getData();
                    if (imageUri != null) {
                        handleSelectedImage(imageUri);
                    }
                }
            }
        );
    }

    private void handleSelectedImage(Uri imageUri) {
        try {
            // Check authentication first
            if (mAuth.getCurrentUser() == null) {
                Toast.makeText(this, "Please login first before uploading images", Toast.LENGTH_LONG).show();
                return;
            }
            
            Log.d(TAG, "User authenticated: " + mAuth.getCurrentUser().getUid());
            Log.d(TAG, "Patient ID: " + patientId);
            
            // Take persistent URI permission if possible
            try {
                getContentResolver().takePersistableUriPermission(imageUri, 
                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
                Log.d(TAG, "Persistent URI permission taken for: " + imageUri);
            } catch (SecurityException e) {
                Log.w(TAG, "Could not take persistent permission for URI: " + imageUri + ", error: " + e.getMessage());
                // Continue anyway, as some URIs don't support persistent permissions
            }
            
            // Verify the image exists and is accessible
            try {
                InputStream testStream = getContentResolver().openInputStream(imageUri);
                if (testStream != null) {
                    testStream.close();
                    Log.d(TAG, "Image URI is accessible: " + imageUri);
                } else {
                    throw new Exception("Could not open input stream for URI");
                }
            } catch (Exception e) {
                Toast.makeText(this, "Selected image is not accessible: " + e.getMessage(), Toast.LENGTH_LONG).show();
                return;
            }
            
            // Show progress
            progressBar.setVisibility(View.VISIBLE);
            Toast.makeText(this, "Uploading image...", Toast.LENGTH_SHORT).show();
            
            // Upload image to Firebase Storage
            uploadImageToFirebaseStorage(imageUri);
            
        } catch (Exception e) {
            Log.e(TAG, "Error handling selected image: " + e.getMessage());
            Toast.makeText(this, "Error processing selected image: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void uploadImageToFirebaseStorage(Uri imageUri) {
        try {
            Log.d(TAG, "Converting image to Base64 for URI: " + imageUri);
            
            // Convert image to Base64 string (Free alternative to Firebase Storage)
            String base64Image = convertImageToBase64(imageUri);
            if (base64Image != null) {
                // Add Base64 string to list (prefixed to indicate it's Base64)
                String base64Url = "data:image/jpeg;base64," + base64Image;
                imageUrls.add(base64Url);
                imageAdapter.notifyItemInserted(imageUrls.size() - 1);
                
                progressBar.setVisibility(View.GONE);
                Log.d(TAG, "Image converted to Base64 successfully");
                Toast.makeText(this, "Image added successfully", Toast.LENGTH_SHORT).show();
            } else {
                progressBar.setVisibility(View.GONE);
                Log.e(TAG, "Failed to convert image to Base64");
                Toast.makeText(this, "Failed to process image", Toast.LENGTH_SHORT).show();
            }
            
        } catch (Exception e) {
            progressBar.setVisibility(View.GONE);
            Log.e(TAG, "Error processing image: " + e.getMessage(), e);
            Toast.makeText(this, "Error uploading image: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void setupImageRecyclerView() {
        imageAdapter = new MedicineImageAdapter(imageUrls, this);
        imagesRecyclerView.setLayoutManager(new GridLayoutManager(this, 3));
        imagesRecyclerView.setAdapter(imageAdapter);
    }

    private void setupClickListeners() {
        saveButton.setOnClickListener(v -> attemptSave());
        cancelButton.setOnClickListener(v -> finish());
        addMedicineButton.setOnClickListener(v -> addMedicineField());
        addImageButton.setOnClickListener(v -> openImagePicker());
    }

    private void addMedicineField() {
        // Create new EditText for medicine name
        EditText medicineNameEditText = new EditText(this);
        medicineNameEditText.setHint(R.string.medicine_name_hint_multiple);
        medicineNameEditText.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        medicineNameEditText.setPadding(16, 16, 16, 16);

        // Add remove button
        Button removeButton = new Button(this);
        removeButton.setText(R.string.remove_medicine);
        removeButton.setOnClickListener(v -> {
            medicineNamesContainer.removeView((View) medicineNameEditText.getParent());
        });

        // Create horizontal layout for medicine name and remove button
        LinearLayout medicineLayout = new LinearLayout(this);
        medicineLayout.setOrientation(LinearLayout.HORIZONTAL);
        medicineLayout.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        
        LinearLayout.LayoutParams editTextParams = new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f
        );
        medicineNameEditText.setLayoutParams(editTextParams);
        
        medicineLayout.addView(medicineNameEditText);
        medicineLayout.addView(removeButton);
        
        // Add to container
        medicineNamesContainer.addView(medicineLayout);
    }

    private void openImagePicker() {
        // Create multiple intents for different image sources
        Intent documentIntent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        documentIntent.setType("image/*");
        documentIntent.addCategory(Intent.CATEGORY_OPENABLE);
        documentIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        documentIntent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        
        Intent galleryIntent = new Intent(Intent.ACTION_PICK);
        galleryIntent.setType("image/*");
        galleryIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        
        Intent getContentIntent = new Intent(Intent.ACTION_GET_CONTENT);
        getContentIntent.setType("image/*");
        getContentIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        
        // Create chooser with document picker as primary (best for persistent permissions)
        Intent chooser = Intent.createChooser(documentIntent, "Select Medicine Image");
        
        // Add alternative intents
        Intent[] alternativeIntents = { galleryIntent, getContentIntent };
        chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, alternativeIntents);
        
        imagePickerLauncher.launch(chooser);
    }

    @Override
    public void onImageRemove(int position) {
        if (position >= 0 && position < imageUrls.size()) {
            String imageUrl = imageUrls.get(position);
            
            // Remove from Firebase Storage if it's a Firebase Storage URL
            if (imageUrl.contains("firebasestorage.googleapis.com")) {
                deleteImageFromFirebaseStorage(imageUrl);
            }
            
            imageUrls.remove(position);
            imageAdapter.notifyItemRemoved(position);
            imageAdapter.notifyItemRangeChanged(position, imageUrls.size());
        }
    }

    private void deleteImageFromFirebaseStorage(String downloadUrl) {
        try {
            StorageReference storageRef = storage.getReferenceFromUrl(downloadUrl);
            storageRef.delete().addOnSuccessListener(aVoid -> {
                Log.d(TAG, "Image deleted from Firebase Storage: " + downloadUrl);
            }).addOnFailureListener(e -> {
                Log.w(TAG, "Failed to delete image from Firebase Storage: " + e.getMessage());
            });
        } catch (Exception e) {
            Log.e(TAG, "Error deleting image from Firebase Storage: " + e.getMessage());
        }
    }
    
    /**
     * Convert image URI to Base64 string (Free alternative to Firebase Storage)
     */
    private String convertImageToBase64(Uri imageUri) {
        try {
            InputStream inputStream = getContentResolver().openInputStream(imageUri);
            if (inputStream == null) {
                Log.e(TAG, "Cannot open input stream for URI: " + imageUri);
                return null;
            }

            // Decode and compress the image to reduce size
            Bitmap originalBitmap = BitmapFactory.decodeStream(inputStream);
            inputStream.close();
            
            if (originalBitmap == null) {
                Log.e(TAG, "Cannot decode bitmap from URI: " + imageUri);
                return null;
            }

            // Compress image to reduce Base64 size (important for Firestore limits)
            Bitmap compressedBitmap = compressImage(originalBitmap, 800, 600);
            
            // Convert to Base64
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            compressedBitmap.compress(Bitmap.CompressFormat.JPEG, 70, byteArrayOutputStream);
            byte[] byteArray = byteArrayOutputStream.toByteArray();
            
            String base64String = Base64.encodeToString(byteArray, Base64.DEFAULT);
            
            Log.d(TAG, "Image converted to Base64. Size: " + base64String.length() + " characters");
            
            // Clean up
            byteArrayOutputStream.close();
            if (!originalBitmap.isRecycled()) {
                originalBitmap.recycle();
            }
            if (!compressedBitmap.isRecycled()) {
                compressedBitmap.recycle();
            }
            
            return base64String;
            
        } catch (Exception e) {
            Log.e(TAG, "Error converting image to Base64: " + e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Compress image to fit within Firestore document size limits
     */
    private Bitmap compressImage(Bitmap original, int maxWidth, int maxHeight) {
        int width = original.getWidth();
        int height = original.getHeight();
        
        // Calculate scale factor
        float scaleWidth = ((float) maxWidth) / width;
        float scaleHeight = ((float) maxHeight) / height;
        float scale = Math.min(scaleWidth, scaleHeight);
        
        // Don't upscale
        if (scale >= 1) {
            return original;
        }
        
        // Create scaled bitmap
        Matrix matrix = new Matrix();
        matrix.postScale(scale, scale);
        
        return Bitmap.createBitmap(original, 0, 0, width, height, matrix, false);
    }

    @Override
    public void onImageClick(int position) {
        // Optional: Show full-screen image view or do nothing
        Toast.makeText(this, "Image clicked", Toast.LENGTH_SHORT).show();
    }

    private void attemptSave() {
        // Collect medicine names from dynamic EditTexts
        collectMedicineNames();
        
        String dosage = dosageEditText.getText().toString().trim();
        String time = timeEditText.getText().toString().trim();

        // Validate input
        if (medicineNames.isEmpty()) {
            Toast.makeText(this, "At least one medicine name is required", Toast.LENGTH_SHORT).show();
            return;
        }

        if (TextUtils.isEmpty(dosage)) {
            dosageEditText.setError("Dosage is required");
            dosageEditText.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(time)) {
            timeEditText.setError("Time is required");
            timeEditText.requestFocus();
            return;
        }

        // Show progress
        progressBar.setVisibility(View.VISIBLE);
        saveButton.setEnabled(false);

        // Parse the scheduled date and time to create proper timestamp
        Long scheduledTime = parseScheduledDateTime();
        if (scheduledTime == null) {
            Toast.makeText(this, "Please select a valid date and time", Toast.LENGTH_SHORT).show();
            progressBar.setVisibility(View.GONE);
            saveButton.setEnabled(true);
            return;
        }
        
        // Get repeat option
        boolean isRepeating = checkRepeating.isChecked();
        
        // Create medication reminder entity with enhanced fields
        String reminderTitle = medicineNames.get(0) + " - " + dosage; // Use first medicine as title
        
        // Use user-provided description or generate default
        String userDescription = descriptionEditText.getText().toString().trim();
        String reminderDescription;
        if (!userDescription.isEmpty()) {
            reminderDescription = userDescription;
        } else {
            reminderDescription = "Take at " + time + " - " + medicineNames.size() + " medicine(s)";
            if (isRepeating) {
                reminderDescription += " (Daily)";
            }
        }
        
        // If there are images, upload them to Firebase Storage first
        if (!imageUrls.isEmpty()) {
            uploadImagesAndSaveReminder(reminderTitle, reminderDescription, scheduledTime, isRepeating, medicineNames);
        } else {
            // No images to upload, save directly
            saveReminderToFirestore(reminderTitle, reminderDescription, scheduledTime, isRepeating, medicineNames, new ArrayList<>());
        }
    }
    
    private void setupTimeAndDatePickers() {
        // Time picker
        timeEditText.setOnClickListener(v -> {
            Calendar calendar = Calendar.getInstance();
            int hour = calendar.get(Calendar.HOUR_OF_DAY);
            int minute = calendar.get(Calendar.MINUTE);
            
            TimePickerDialog timePickerDialog = new TimePickerDialog(
                this,
                (view, hourOfDay, minuteOfDay) -> {
                    String timeString = String.format(Locale.getDefault(), "%02d:%02d %s", 
                        hourOfDay > 12 ? hourOfDay - 12 : hourOfDay,
                        minuteOfDay,
                        hourOfDay >= 12 ? "PM" : "AM");
                    timeEditText.setText(timeString);
                },
                hour, minute, false);
            timePickerDialog.show();
        });
        
        // Date picker
        scheduledDateEditText.setOnClickListener(v -> {
            Calendar calendar = Calendar.getInstance();
            int year = calendar.get(Calendar.YEAR);
            int month = calendar.get(Calendar.MONTH);
            int day = calendar.get(Calendar.DAY_OF_MONTH);
            
            DatePickerDialog datePickerDialog = new DatePickerDialog(
                this,
                (view, selectedYear, selectedMonth, selectedDay) -> {
                    String dateString = String.format(Locale.getDefault(), "%d-%02d-%02d", 
                        selectedYear, selectedMonth + 1, selectedDay);
                    scheduledDateEditText.setText(dateString);
                },
                year, month, day);
            datePickerDialog.show();
        });
    }

    private void collectMedicineNames() {
        medicineNames.clear();
        
        // Go through all medicine name EditTexts in the container
        for (int i = 0; i < medicineNamesContainer.getChildCount(); i++) {
            View childView = medicineNamesContainer.getChildAt(i);
            if (childView instanceof LinearLayout) {
                LinearLayout medicineLayout = (LinearLayout) childView;
                if (medicineLayout.getChildCount() > 0) {
                    View firstChild = medicineLayout.getChildAt(0);
                    if (firstChild instanceof EditText) {
                        EditText medicineEditText = (EditText) firstChild;
                        String medicineName = medicineEditText.getText().toString().trim();
                        if (!TextUtils.isEmpty(medicineName)) {
                            medicineNames.add(medicineName);
                        }
                    }
                }
            }
        }
        
        Log.d(TAG, "Collected medicine names: " + medicineNames.toString());
    }
    
    private Long parseScheduledDateTime() {
        String timeText = timeEditText.getText().toString().trim();
        String dateText = scheduledDateEditText.getText().toString().trim();
        
        if (timeText.isEmpty() || dateText.isEmpty()) {
            return null;
        }
        
        try {
            // Parse date (format: YYYY-MM-DD)
            String[] dateParts = dateText.split("-");
            int year = Integer.parseInt(dateParts[0]);
            int month = Integer.parseInt(dateParts[1]) - 1; // Calendar months are 0-based
            int day = Integer.parseInt(dateParts[2]);
            
            // Parse time (format: HH:MM AM/PM)
            String[] timeParts = timeText.split(":");
            int hour = Integer.parseInt(timeParts[0]);
            int minute = Integer.parseInt(timeParts[1].split(" ")[0]);
            boolean isPM = timeParts[1].contains("PM");
            
            if (isPM && hour != 12) hour += 12;
            if (!isPM && hour == 12) hour = 0;
            
            Calendar calendar = Calendar.getInstance();
            calendar.set(year, month, day, hour, minute, 0);
            calendar.set(Calendar.MILLISECOND, 0);
            
            return calendar.getTimeInMillis();
        } catch (Exception e) {
            return null;
        }
    }
    
    private void uploadImagesAndSaveReminder(String title, String description, Long scheduledTime, 
                                           boolean isRepeating, List<String> medicineNames) {
        // With Base64 solution, images are already processed and stored in imageUrls
        // No need for async upload - images are already converted to Base64
        saveReminderToFirestore(title, description, scheduledTime, isRepeating, medicineNames, new ArrayList<>(imageUrls));
    }
    
    private void saveReminderToFirestore(String title, String description, Long scheduledTime, 
                                       boolean isRepeating, List<String> medicineNames, List<String> firebaseImageUrls) {
        ReminderEntity medicationReminder = new ReminderEntity(
            title,
            description,
            scheduledTime,
            false,
            isRepeating,
            new ArrayList<>(medicineNames), // Copy medicine names
            new ArrayList<>(firebaseImageUrls) // Use Firebase Storage URLs
        );
        
        // Save to Firestore - use 'reminders' collection to match patient app
        db.collection("patients")
                .document(patientId)
                .collection("reminders")
                .add(medicationReminder)
                .addOnCompleteListener(task -> {
                    progressBar.setVisibility(View.GONE);
                    saveButton.setEnabled(true);

                    if (task.isSuccessful()) {
                        Toast.makeText(AddMedicationActivity.this, 
                            R.string.medication_added_successfully, Toast.LENGTH_SHORT).show();
                        finish();
                    } else {
                        Toast.makeText(AddMedicationActivity.this, 
                            "Failed to add medication: " + task.getException().getMessage(),
                            Toast.LENGTH_LONG).show();
                    }
                });
    }
}
