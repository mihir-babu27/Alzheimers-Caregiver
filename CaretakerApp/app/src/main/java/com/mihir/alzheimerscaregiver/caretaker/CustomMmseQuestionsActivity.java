package com.mihir.alzheimerscaregiver.caretaker;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Toast;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.mihir.alzheimerscaregiver.caretaker.R;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class CustomMmseQuestionsActivity extends AppCompatActivity {
    private RecyclerView recyclerView;
    private CustomMmseQuestionAdapter adapter;
    private List<CustomMmseQuestion> questionList = new ArrayList<>();
    private String patientId;
    private FirebaseFirestore db;

    // Add question UI
    private EditText questionInput, expectedAnswerInput, option1Input, option2Input, option3Input, option4Input, scoreInput;
    private Spinner typeSpinner;
    private LinearLayout optionsContainer;
    private Button addButton;
    private Button addPhotoButton, takePhotoButton;
    private ImageView imagePreview;
    private Uri cameraPhotoUri;
    private String pendingImageBase64;

    private static final int REQ_PICK_IMAGE = 1001;
    private static final int REQ_TAKE_PHOTO = 1002;
    private static final int REQ_PERMS = 1100;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_custom_mmse_questions);

        patientId = getIntent().getStringExtra("patientId");
        db = FirebaseFirestore.getInstance();

        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new CustomMmseQuestionAdapter(questionList, this::deleteQuestion);
        recyclerView.setAdapter(adapter);

        questionInput = findViewById(R.id.questionInput);
        expectedAnswerInput = findViewById(R.id.expectedAnswerInput);
        option1Input = findViewById(R.id.option1Input);
        option2Input = findViewById(R.id.option2Input);
        option3Input = findViewById(R.id.option3Input);
        option4Input = findViewById(R.id.option4Input);
        scoreInput = findViewById(R.id.scoreInput);
        typeSpinner = findViewById(R.id.typeSpinner);
        optionsContainer = findViewById(R.id.optionsContainer);
        addButton = findViewById(R.id.addButton);
        addPhotoButton = findViewById(R.id.addPhotoButton);
        takePhotoButton = findViewById(R.id.takePhotoButton);
        imagePreview = findViewById(R.id.imagePreview);

        ArrayAdapter<CharSequence> spinnerAdapter = ArrayAdapter.createFromResource(this,
                R.array.mmse_question_types, android.R.layout.simple_spinner_item);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        typeSpinner.setAdapter(spinnerAdapter);

        typeSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                String type = (String) parent.getItemAtPosition(position);
                optionsContainer.setVisibility("mcq".equals(type) ? View.VISIBLE : View.GONE);
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        addButton.setOnClickListener(v -> addQuestion());
        addPhotoButton.setOnClickListener(v -> onPickImage());
        takePhotoButton.setOnClickListener(v -> onTakePhoto());

        loadQuestions();
    }

    private void loadQuestions() {
        if (TextUtils.isEmpty(patientId)) return;
        db.collection("patients").document(patientId)
                .collection("custom_mmse_questions")
                .orderBy("question", Query.Direction.ASCENDING)
                .get()
                .addOnSuccessListener(query -> {
                    questionList.clear();
                    for (var doc : query) {
                        CustomMmseQuestion q = doc.toObject(CustomMmseQuestion.class);
                        q.id = doc.getId();
                        questionList.add(q);
                    }
                    adapter.notifyDataSetChanged();
                });
    }

    private void addQuestion() {
        String question = questionInput.getText().toString().trim();
        String type = typeSpinner.getSelectedItem().toString();
        String expectedAnswer = expectedAnswerInput.getText().toString().trim();
        String scoreStr = scoreInput.getText().toString().trim();
        int score = 1;
        try { score = Integer.parseInt(scoreStr); } catch (Exception ignore) {}
        List<String> options = new ArrayList<>();
        if ("mcq".equals(type)) {
            if (!TextUtils.isEmpty(option1Input.getText())) options.add(option1Input.getText().toString().trim());
            if (!TextUtils.isEmpty(option2Input.getText())) options.add(option2Input.getText().toString().trim());
            if (!TextUtils.isEmpty(option3Input.getText())) options.add(option3Input.getText().toString().trim());
            if (!TextUtils.isEmpty(option4Input.getText())) options.add(option4Input.getText().toString().trim());
        }
        if (TextUtils.isEmpty(question) || TextUtils.isEmpty(type) || TextUtils.isEmpty(expectedAnswer)) {
            Toast.makeText(this, "Please fill all required fields", Toast.LENGTH_SHORT).show();
            return;
        }
        Map<String, Object> data = new HashMap<>();
        data.put("question", question);
        data.put("type", type);
        data.put("expectedAnswer", expectedAnswer);
        data.put("score", score);
        if (!options.isEmpty()) data.put("options", options);
        if (!TextUtils.isEmpty(pendingImageBase64)) data.put("imageBase64", pendingImageBase64);
        String id = UUID.randomUUID().toString();
        db.collection("patients").document(patientId)
                .collection("custom_mmse_questions").document(id)
                .set(data)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Question added", Toast.LENGTH_SHORT).show();
                    clearInputs();
                    loadQuestions();
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }

    private void clearInputs() {
        questionInput.setText("");
        expectedAnswerInput.setText("");
        option1Input.setText("");
        option2Input.setText("");
        option3Input.setText("");
        option4Input.setText("");
        scoreInput.setText("");
        pendingImageBase64 = null;
        imagePreview.setImageDrawable(null);
        imagePreview.setVisibility(View.GONE);
    }

    private void deleteQuestion(CustomMmseQuestion q) {
        if (TextUtils.isEmpty(patientId) || q == null || TextUtils.isEmpty(q.id)) return;
        db.collection("patients").document(patientId)
                .collection("custom_mmse_questions").document(q.id)
                .delete()
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show();
                    loadQuestions();
                });
    }

    // --- Adapter and Model ---
    public static class CustomMmseQuestion {
        public String id;
        public String question;
        public String type;
        public String expectedAnswer;
        public List<String> options;
        public int score;
        public CustomMmseQuestion() {}
    }

    public static class CustomMmseQuestionAdapter extends RecyclerView.Adapter<CustomMmseQuestionAdapter.ViewHolder> {
        private final List<CustomMmseQuestion> items;
        private final java.util.function.Consumer<CustomMmseQuestion> onDelete;
        public CustomMmseQuestionAdapter(List<CustomMmseQuestion> items, java.util.function.Consumer<CustomMmseQuestion> onDelete) {
            this.items = items;
            this.onDelete = onDelete;
        }
        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_custom_mmse_question, parent, false);
            return new ViewHolder(v);
        }
        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            CustomMmseQuestion q = items.get(position);
            holder.questionText.setText(q.question);
            holder.typeText.setText(q.type);
            holder.answerText.setText(q.expectedAnswer);
            if (q.options != null && !q.options.isEmpty()) {
                holder.optionsText.setText(android.text.TextUtils.join(", ", q.options));
                holder.optionsText.setVisibility(View.VISIBLE);
            } else {
                holder.optionsText.setVisibility(View.GONE);
            }
            holder.scoreText.setText("Score: " + q.score);
            holder.deleteButton.setOnClickListener(v -> onDelete.accept(q));
        }
        @Override
        public int getItemCount() { return items.size(); }
        public static class ViewHolder extends RecyclerView.ViewHolder {
            TextView questionText, typeText, answerText, optionsText, scoreText;
            Button deleteButton;
            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                questionText = itemView.findViewById(R.id.questionText);
                typeText = itemView.findViewById(R.id.typeText);
                answerText = itemView.findViewById(R.id.answerText);
                optionsText = itemView.findViewById(R.id.optionsText);
                scoreText = itemView.findViewById(R.id.scoreText);
                deleteButton = itemView.findViewById(R.id.deleteButton);
            }
        }
    }

    // --- Image helpers ---
    private void onPickImage() {
        if (!ensureMediaPermission()) return;
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        intent.setType("image/*");
        startActivityForResult(intent, REQ_PICK_IMAGE);
    }

    private void onTakePhoto() {
        if (!ensureCameraPermission()) return;
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.TITLE, "mmse_question_" + System.currentTimeMillis());
        ContentResolver resolver = getContentResolver();
        cameraPhotoUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, cameraPhotoUri);
        startActivityForResult(intent, REQ_TAKE_PHOTO);
    }

    private boolean ensureMediaPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_MEDIA_IMAGES}, REQ_PERMS);
                return false;
            }
            return true;
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQ_PERMS);
                return false;
            }
            return true;
        }
    }

    private boolean ensureCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQ_PERMS);
            return false;
        }
        return ensureMediaPermission();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK) return;
        try {
            Uri uri = null;
            if (requestCode == REQ_PICK_IMAGE && data != null) {
                uri = data.getData();
            } else if (requestCode == REQ_TAKE_PHOTO) {
                uri = cameraPhotoUri;
            }
            if (uri != null) {
                Bitmap bitmap = decodeDownsampledBitmap(uri, 1024, 1024);
                if (bitmap != null) {
                    pendingImageBase64 = encodeToBase64(bitmap, 80);
                    imagePreview.setImageBitmap(bitmap);
                    imagePreview.setVisibility(View.VISIBLE);
                }
            }
        } catch (Exception e) {
            Toast.makeText(this, "Image error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private Bitmap decodeDownsampledBitmap(Uri uri, int reqWidth, int reqHeight) throws Exception {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        try (var is1 = getContentResolver().openInputStream(uri)) {
            BitmapFactory.decodeStream(is1, null, options);
        }
        int inSampleSize = 1;
        while ((options.outWidth / inSampleSize) > reqWidth || (options.outHeight / inSampleSize) > reqHeight) {
            inSampleSize *= 2;
        }
        BitmapFactory.Options options2 = new BitmapFactory.Options();
        options2.inSampleSize = inSampleSize;
        try (var is2 = getContentResolver().openInputStream(uri)) {
            return BitmapFactory.decodeStream(is2, null, options2);
        }
    }

    private String encodeToBase64(Bitmap bitmap, int quality) throws Exception {
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos);
        byte[] bytes = baos.toByteArray();
        // Warn if large (~>500KB)
        if (bytes.length > 500 * 1024) {
            Toast.makeText(this, "Image is large (" + (bytes.length/1024) + "KB)", Toast.LENGTH_SHORT).show();
        }
        return Base64.encodeToString(bytes, Base64.NO_WRAP);
    }
}
