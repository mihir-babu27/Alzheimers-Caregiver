package com.mihir.alzheimerscaregiver;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.mihir.alzheimerscaregiver.alarm.AlarmScheduler;

/**
 * Simple test activity to verify alarm functionality
 */
public class AlarmTestActivity extends AppCompatActivity {
    private static final String TAG = "AlarmTestActivity";
    
    private TextView statusText;
    private Button testAlarmButton;
    private Button permissionButton;
    private AlarmScheduler alarmScheduler;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Create a simple layout programmatically
        createLayout();
        
        alarmScheduler = new AlarmScheduler(this);
        updateStatus();
    }
    
    private void createLayout() {
        // Create main layout
        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(50, 50, 50, 50);
        
        // Title
        TextView title = new TextView(this);
        title.setText("Alarm System Test");
        title.setTextSize(24);
        title.setPadding(0, 0, 0, 30);
        layout.addView(title);
        
        // Status text
        statusText = new TextView(this);
        statusText.setText("Checking alarm permissions...");
        statusText.setPadding(0, 0, 0, 30);
        layout.addView(statusText);
        
        // Test alarm button
        testAlarmButton = new Button(this);
        testAlarmButton.setText("Test Alarm (10 seconds)");
        testAlarmButton.setOnClickListener(v -> scheduleTestAlarm());
        layout.addView(testAlarmButton);
        
        // Permission button
        permissionButton = new Button(this);
        permissionButton.setText("Open Permission Settings");
        permissionButton.setOnClickListener(v -> openPermissionSettings());
        layout.addView(permissionButton);
        
        // Back button
        Button backButton = new Button(this);
        backButton.setText("Back");
        backButton.setOnClickListener(v -> finish());
        layout.addView(backButton);
        
        setContentView(layout);
    }
    
    private void updateStatus() {
        StringBuilder status = new StringBuilder();
        boolean allGood = true;
        
        // Check exact alarm permission
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            android.app.AlarmManager alarmManager = (android.app.AlarmManager) getSystemService(ALARM_SERVICE);
            boolean canScheduleExact = alarmManager.canScheduleExactAlarms();
            status.append("Exact Alarms: ").append(canScheduleExact ? "✓ GRANTED" : "✗ DENIED").append("\n");
            if (!canScheduleExact) allGood = false;
        } else {
            status.append("Exact Alarms: ✓ NOT REQUIRED (Android < 12)\n");
        }
        
        // Check battery optimization
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            android.os.PowerManager powerManager = (android.os.PowerManager) getSystemService(POWER_SERVICE);
            boolean batteryOptDisabled = powerManager.isIgnoringBatteryOptimizations(getPackageName());
            status.append("Battery Optimization: ").append(batteryOptDisabled ? "✓ DISABLED" : "✗ ENABLED").append("\n");
            if (!batteryOptDisabled) allGood = false;
        } else {
            status.append("Battery Optimization: ✓ NOT REQUIRED (Android < 6)\n");
        }
        
        status.append("\nOverall Status: ").append(allGood ? "✓ READY FOR ALARMS" : "⚠ NEEDS PERMISSIONS");
        
        statusText.setText(status.toString());
        testAlarmButton.setEnabled(true);
    }
    
    private void scheduleTestAlarm() {
        Log.d(TAG, "Scheduling test alarm...");
        
        boolean success = alarmScheduler.scheduleTestAlarm();
        
        if (success) {
            Toast.makeText(this, "Test alarm scheduled! Should trigger in 10 seconds", Toast.LENGTH_LONG).show();
            Log.d(TAG, "Test alarm scheduled successfully");
        } else {
            Toast.makeText(this, "Failed to schedule test alarm. Check permissions.", Toast.LENGTH_LONG).show();
            Log.e(TAG, "Failed to schedule test alarm");
        }
    }
    
    private void openPermissionSettings() {
        // Check what permissions are needed and open appropriate settings
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            android.app.AlarmManager alarmManager = (android.app.AlarmManager) getSystemService(ALARM_SERVICE);
            if (!alarmManager.canScheduleExactAlarms()) {
                try {
                    android.content.Intent intent = new android.content.Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                    intent.setData(android.net.Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                    return;
                } catch (Exception e) {
                    Log.e(TAG, "Could not open exact alarm settings", e);
                }
            }
        }
        
        // Fall back to battery optimization
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            android.os.PowerManager powerManager = (android.os.PowerManager) getSystemService(POWER_SERVICE);
            if (!powerManager.isIgnoringBatteryOptimizations(getPackageName())) {
                try {
                    android.content.Intent intent = new android.content.Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                    intent.setData(android.net.Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                    return;
                } catch (Exception e) {
                    Log.e(TAG, "Could not open battery optimization settings", e);
                }
            }
        }
        
        // Final fallback
        try {
            android.content.Intent intent = new android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(android.net.Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Could not open app settings", e);
        }
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        // Update status when returning from settings
        updateStatus();
    }
}