package com.mihir.alzheimerscaregiver.caretaker;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Description;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.google.firebase.firestore.FirebaseFirestore;
import com.mihir.alzheimerscaregiver.caretaker.data.entity.MmseResult;
import com.mihir.alzheimerscaregiver.caretaker.ui.MmseResultsAdapter;
import com.mihir.alzheimerscaregiver.caretaker.R;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MmseResultsActivity extends AppCompatActivity implements MmseResultsAdapter.OnItemClickListener {

    public static final String EXTRA_PATIENT_ID = "patientId";

    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private TextView emptyText;
    private LineChart lineChart;
    private MmseResultsAdapter adapter;
    private View exportButton;

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_caretaker_mmse_results);

        recyclerView = findViewById(R.id.recyclerView);
        progressBar = findViewById(R.id.progressBar);
        emptyText = findViewById(R.id.emptyText);
        lineChart = findViewById(R.id.lineChart);
        exportButton = findViewById(R.id.exportButton);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new MmseResultsAdapter(new ArrayList<>(), this);
        recyclerView.setAdapter(adapter);

        String patientId = getIntent().getStringExtra(EXTRA_PATIENT_ID);
        if (patientId == null || patientId.isEmpty()) {
            emptyText.setText("No patient selected");
            emptyText.setVisibility(View.VISIBLE);
            return;
        }

        loadResults(patientId);

        exportButton.setOnClickListener(v -> exportToPdf(patientId));
    }

    private void loadResults(String patientId) {
        progressBar.setVisibility(View.VISIBLE);
    db.collection("patients").document(patientId).collection("mmse_results")
        .orderBy("dateTaken", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(snap -> {
                    progressBar.setVisibility(View.GONE);
                    List<MmseResult> list = new ArrayList<>();
                    if (snap != null) {
                        for (var doc : snap) {
                            MmseResult r = doc.toObject(MmseResult.class);
                            if (r != null) {
                                r.id = doc.getId();
                                list.add(r);
                            }
                        }
                    }
                    if (list.isEmpty()) {
                        emptyText.setText("No MMSE results yet");
                        emptyText.setVisibility(View.VISIBLE);
                        recyclerView.setVisibility(View.GONE);
                        lineChart.setVisibility(View.GONE);
                    } else {
                        emptyText.setVisibility(View.GONE);
                        recyclerView.setVisibility(View.VISIBLE);
                        adapter.updateData(list);
                        renderChart(list);
                    }
                })
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    emptyText.setText("Failed to load results");
                    emptyText.setVisibility(View.VISIBLE);
                });
    }

    private void renderChart(List<MmseResult> list) {
        List<Entry> entries = new ArrayList<>();
        int index = 0;
        // Iterate from the last index (list.size() - 1) down to 0
        for (int i = list.size() - 1; i >= 0; i--) {
            MmseResult r = list.get(i); // Get the element at the current index
            float score = r.totalScore != null ? r.totalScore : 0;
            entries.add(new Entry(index++, score));
        }
        LineDataSet dataSet = new LineDataSet(entries, "Quiz Total Score");
        dataSet.setColor(getColor(R.color.purple_700));
        dataSet.setCircleColor(getColor(R.color.purple_700));
        LineData lineData = new LineData(dataSet);
        lineChart.setData(lineData);
        Description d = new Description();
        d.setText("");
        lineChart.setDescription(d);
        lineChart.invalidate();
    }

    private void exportToPdf(String patientId) {
        try {
            // Prepare data
            List<MmseResult> results = new ArrayList<>(adapter.getItems());

            if (results.isEmpty()) {
                android.widget.Toast.makeText(this, "No results to export", android.widget.Toast.LENGTH_SHORT).show();
                return;
            }

            // Capture chart bitmap
            lineChart.measure(
                    View.MeasureSpec.makeMeasureSpec(lineChart.getWidth(), View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(lineChart.getHeight(), View.MeasureSpec.EXACTLY)
            );
            lineChart.layout(lineChart.getLeft(), lineChart.getTop(), lineChart.getRight(), lineChart.getBottom());
            android.graphics.Bitmap chartBitmap = android.graphics.Bitmap.createBitmap(lineChart.getWidth(), lineChart.getHeight(), android.graphics.Bitmap.Config.ARGB_8888);
            android.graphics.Canvas chartCanvas = new android.graphics.Canvas(chartBitmap);
            lineChart.draw(chartCanvas);

            // Build PDF
            android.graphics.pdf.PdfDocument document = new android.graphics.pdf.PdfDocument();
            int pageWidth = 595; // A4 width in points (approx for 72dpi)
            int pageHeight = 842; // A4 height in points

            android.graphics.pdf.PdfDocument.PageInfo pageInfo = new android.graphics.pdf.PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create();
            android.graphics.pdf.PdfDocument.Page page = document.startPage(pageInfo);
            android.graphics.Canvas canvas = page.getCanvas();
            android.graphics.Paint titlePaint = new android.graphics.Paint();
            titlePaint.setTextSize(18f);
            titlePaint.setFakeBoldText(true);
            android.graphics.Paint textPaint = new android.graphics.Paint();
            textPaint.setTextSize(12f);

            int y = 40;
            canvas.drawText("MMSE Report", 40, y, titlePaint); y += 24;
            canvas.drawText("Patient: " + patientId, 40, y, textPaint); y += 20;

            // List of results
            canvas.drawText("Results:", 40, y, titlePaint); y += 20;
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault());
            for (MmseResult r : results) {
                String dateStr = r.dateTaken != null ? sdf.format(r.dateTaken.toDate()) : "-";
                String line = dateStr + "  |  Score: " + (r.totalScore != null ? r.totalScore : 0) + "  |  " + (r.interpretation != null ? r.interpretation : "");
                canvas.drawText(line, 40, y, textPaint); y += 16;
                if (y > pageHeight - 80) { // new page if needed
                    document.finishPage(page);
                    pageInfo = new android.graphics.pdf.PdfDocument.PageInfo.Builder(pageWidth, pageHeight, document.getPages().size() + 1).create();
                    page = document.startPage(pageInfo);
                    canvas = page.getCanvas();
                    y = 40;
                }
            }

            // Section-wise for last test
            MmseResult last = results.get(results.size() - 1);
            y += 10;
            canvas.drawText("Last Test - Section Scores:", 40, y, titlePaint); y += 20;
            if (last.sectionScores != null) {
                for (java.util.Map.Entry<String, Integer> e : last.sectionScores.entrySet()) {
                    canvas.drawText(e.getKey() + ": " + e.getValue(), 60, y, textPaint); y += 16;
                    if (y > pageHeight - 80) {
                        document.finishPage(page);
                        pageInfo = new android.graphics.pdf.PdfDocument.PageInfo.Builder(pageWidth, pageHeight, document.getPages().size() + 1).create();
                        page = document.startPage(pageInfo);
                        canvas = page.getCanvas();
                        y = 40;
                    }
                }
            }

            // Chart
            y += 10;
            canvas.drawText("Trend:", 40, y, titlePaint); y += 10;
            int chartTargetWidth = pageWidth - 80;
            int chartTargetHeight = 200;
            android.graphics.Bitmap scaled = android.graphics.Bitmap.createScaledBitmap(chartBitmap, chartTargetWidth, chartTargetHeight, true);
            canvas.drawBitmap(scaled, 40, y, null); y += chartTargetHeight + 20;

            document.finishPage(page);

            // Save to MediaStore and share
            String fileName = "MMSE_Report_" + patientId + "_" + System.currentTimeMillis() + ".pdf";
            android.net.Uri uri = savePdf(fileName, document);
            document.close();

            if (uri != null) {
                sharePdf(uri);
            } else {
                android.widget.Toast.makeText(this, "Failed to save PDF", android.widget.Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            android.util.Log.e("MmseResultsActivity", "Error exporting PDF", e);
            android.widget.Toast.makeText(this, "Export failed", android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    private android.net.Uri savePdf(String fileName, android.graphics.pdf.PdfDocument document) throws java.io.IOException {
        android.content.ContentValues values = new android.content.ContentValues();
        values.put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName);
        values.put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/pdf");
        values.put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, "Documents/");

        android.content.ContentResolver resolver = getContentResolver();
        android.net.Uri uri = resolver.insert(android.provider.MediaStore.Files.getContentUri("external"), values);
        if (uri == null) return null;
        java.io.OutputStream os = resolver.openOutputStream(uri);
        if (os == null) return null;
        document.writeTo(os);
        os.flush();
        os.close();
        return uri;
    }

    private void sharePdf(android.net.Uri uri) {
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("application/pdf");
        shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(shareIntent, "Share MMSE Report"));
    }

    @Override
    public void onItemClick(MmseResult item) {
        Intent intent = new Intent(this, MmseResultDetailActivity.class);
        intent.putExtra("resultId", item.id);
        intent.putExtra(EXTRA_PATIENT_ID, item.patientId);
        startActivity(intent);
    }

    public static String formatDate(@Nullable com.google.firebase.Timestamp ts) {
        if (ts == null) return "";
        Date d = ts.toDate();
        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(d);
    }
}


