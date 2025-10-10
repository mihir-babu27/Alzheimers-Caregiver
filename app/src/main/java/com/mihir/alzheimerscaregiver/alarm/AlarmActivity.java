package com.mihir.alzheimerscaregiver.alarm;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.mihir.alzheimerscaregiver.R;
import com.mihir.alzheimerscaregiver.ui.reminders.MedicineImageAdapter;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Vibrator;
import android.os.VibrationEffect;
import android.os.Build;
import android.app.NotificationManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.Arrays;

public class AlarmActivity extends AppCompatActivity {

    private static final String EXTRA_TITLE = "title";
    private static final String EXTRA_MESSAGE = "message";
    private static final String EXTRA_REMINDER_ID = "reminder_id";
    private static final String EXTRA_MEDICINE_NAMES = "medicine_names";
    private static final String EXTRA_IMAGE_URLS = "image_urls";
    private static final long SNOOZE_DURATION_MS = 10 * 60 * 1000; // 10 minutes
    private Ringtone ringtone;
    private Vibrator vibrator;
    private int notificationId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_alarm);

        // Make the activity show over the lock screen and wake up the device
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);

        TextView titleTextView = findViewById(R.id.alarmTitle);
        TextView messageTextView = findViewById(R.id.alarmMessage);
        TextView medicineNamesText = findViewById(R.id.medicineNamesText);
        TextView imagesLabel = findViewById(R.id.imagesLabel);
        RecyclerView medicineImagesRecyclerView = findViewById(R.id.medicineImagesRecyclerView);
        Button dismissButton = findViewById(R.id.dismissButton);
        Button snoozeButton = findViewById(R.id.snoozeButton);

        String title = getIntent().getStringExtra(EXTRA_TITLE);
        String message = getIntent().getStringExtra(EXTRA_MESSAGE);
        String reminderId = getIntent().getStringExtra(EXTRA_REMINDER_ID);
        String[] medicineNames = getIntent().getStringArrayExtra(EXTRA_MEDICINE_NAMES);
        String[] imageUrls = getIntent().getStringArrayExtra(EXTRA_IMAGE_URLS);
        
        // Debug logging to see what data we receive
        android.util.Log.d("AlarmActivity", "Title: " + title);
        android.util.Log.d("AlarmActivity", "Medicine names: " + (medicineNames != null ? java.util.Arrays.toString(medicineNames) : "null"));
        android.util.Log.d("AlarmActivity", "Image URLs: " + (imageUrls != null ? java.util.Arrays.toString(imageUrls) : "null"));
        
        // Prefer a direct notification_id from the intent for exact cancellation; fallback to reminderId hash
        int passedNotificationId = getIntent().getIntExtra("notification_id", 0);
        notificationId = (passedNotificationId != 0) ? passedNotificationId : ((reminderId != null) ? reminderId.hashCode() : 0);

        titleTextView.setText(title != null ? title : "Reminder");
        messageTextView.setText(message != null ? message : "You have a new reminder.");
        
        // Display medicine names if available
        if (medicineNames != null && medicineNames.length > 0) {
            android.util.Log.d("AlarmActivity", "Setting medicine names visible: " + java.util.Arrays.toString(medicineNames));
            medicineNamesText.setVisibility(TextView.VISIBLE);
            if (medicineNames.length == 1) {
                medicineNamesText.setText("Medicine: " + medicineNames[0]);
            } else {
                medicineNamesText.setText("Medicines: " + String.join(", ", medicineNames));
            }
        } else {
            android.util.Log.d("AlarmActivity", "No medicine names found, hiding section");
            medicineNamesText.setVisibility(TextView.GONE);
        }
        
        // Display medicine images if available
        if (imageUrls != null && imageUrls.length > 0) {
            android.util.Log.d("AlarmActivity", "Setting images visible: " + java.util.Arrays.toString(imageUrls));
            imagesLabel.setVisibility(TextView.VISIBLE);
            medicineImagesRecyclerView.setVisibility(RecyclerView.VISIBLE);
            medicineImagesRecyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
            
            ArrayList<String> imageUrlsList = new ArrayList<>(Arrays.asList(imageUrls));
            // Use display-only mode (no remove buttons) for alarm
            MedicineImageAdapter imageAdapter = new MedicineImageAdapter(this, imageUrlsList, false);
            medicineImagesRecyclerView.setAdapter(imageAdapter);
        } else {
            android.util.Log.d("AlarmActivity", "No image URLs found, hiding images section");
            imagesLabel.setVisibility(TextView.GONE);
            medicineImagesRecyclerView.setVisibility(RecyclerView.GONE);
        }

        startAlarmFeedback();

        dismissButton.setOnClickListener(v -> {
            stopAlarmFeedback();
            cancelNotification();
            stopAlarmService();
            finish();
        });

        snoozeButton.setOnClickListener(v -> {
            snoozeAlarm();
            stopAlarmFeedback();
            cancelNotification();
            stopAlarmService();
            finish();
        });
    }

    private void startAlarmFeedback() {
        // Play alarm tone
        try {
            Uri alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (alarmSound == null) {
                alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            }
            ringtone = RingtoneManager.getRingtone(getApplicationContext(), alarmSound);
            if (ringtone != null) {
                ringtone.play();
            }
        } catch (Exception ignored) {}

        // Vibrate in a repeating pattern
        try {
            vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null) {
                long[] pattern = {0, 500, 500, 500, 500};
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    VibrationEffect effect = VibrationEffect.createWaveform(pattern, 0);
                    vibrator.vibrate(effect);
                } else {
                    vibrator.vibrate(pattern, 0);
                }
            }
        } catch (Exception ignored) {}
    }

    private void stopAlarmFeedback() {
        try {
            if (ringtone != null && ringtone.isPlaying()) ringtone.stop();
        } catch (Exception ignored) {}
        try {
            if (vibrator != null) vibrator.cancel();
        } catch (Exception ignored) {}
    }

    private void cancelNotification() {
        try {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null && notificationId != 0) nm.cancel(notificationId);
        } catch (Exception ignored) {}
    }

    private void stopAlarmService() {
        try {
            Intent stopIntent = new Intent(this, AlarmForegroundService.class);
            stopService(stopIntent);
        } catch (Exception ignored) {}
    }

    private void snoozeAlarm() {
        Intent intent = new Intent(this, AlarmReceiver.class);
        intent.putExtras(getIntent().getExtras()); // Forward all original details

        String reminderId = getIntent().getStringExtra(EXTRA_REMINDER_ID);
        int requestCode = (reminderId != null) ? reminderId.hashCode() : (int) System.currentTimeMillis();

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                this,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        long snoozeTime = SystemClock.elapsedRealtime() + SNOOZE_DURATION_MS;
        
        try {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, snoozeTime, pendingIntent);
            Toast.makeText(this, "Snoozed for 10 minutes", Toast.LENGTH_SHORT).show();
        } catch (SecurityException e) {
            Toast.makeText(this, "Could not snooze. Permission missing.", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onDestroy() {
        stopAlarmFeedback();
        super.onDestroy();
    }
}
