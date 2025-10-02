package com.mihir.alzheimerscaregiver.utils;

import android.app.Activity;
import android.app.AlarmManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import androidx.appcompat.app.AlertDialog;

/**
 * Helper class to manage alarm-related permissions and settings
 */
public class AlarmPermissionHelper {
    private static final String TAG = "AlarmPermissionHelper";
    
    /**
     * Check if all necessary permissions are granted for alarms to work properly
     */
    public static boolean areAllPermissionsGranted(Context context) {
        boolean exactAlarmPermission = true;
        boolean batteryOptimizationDisabled = true;
        
        // Check exact alarm permission on Android 12+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            exactAlarmPermission = alarmManager.canScheduleExactAlarms();
            Log.d(TAG, "Exact alarm permission granted: " + exactAlarmPermission);
        }
        
        // Check battery optimization
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            batteryOptimizationDisabled = powerManager.isIgnoringBatteryOptimizations(context.getPackageName());
            Log.d(TAG, "Battery optimization disabled: " + batteryOptimizationDisabled);
        }
        
        return exactAlarmPermission && batteryOptimizationDisabled;
    }
    
    /**
     * Show dialog to guide user through necessary permission settings
     */
    public static void showPermissionDialog(Activity activity) {
        StringBuilder message = new StringBuilder();
        message.append("For alarms to work reliably, please enable these settings:\n\n");
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AlarmManager alarmManager = (AlarmManager) activity.getSystemService(Context.ALARM_SERVICE);
            if (!alarmManager.canScheduleExactAlarms()) {
                message.append("1. Allow 'Alarms & reminders' permission\n");
            }
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager powerManager = (PowerManager) activity.getSystemService(Context.POWER_SERVICE);
            if (!powerManager.isIgnoringBatteryOptimizations(activity.getPackageName())) {
                message.append("2. Disable battery optimization for this app\n");
            }
        }
        
        message.append("\nThis ensures alarms work even when the app is in the background.");
        
        new AlertDialog.Builder(activity)
                .setTitle("Permission Settings Required")
                .setMessage(message.toString())
                .setPositiveButton("Open Settings", (dialog, which) -> openPermissionSettings(activity))
                .setNegativeButton("Cancel", null)
                .show();
    }
    
    /**
     * Open the appropriate settings page for alarm permissions
     */
    public static void openPermissionSettings(Activity activity) {
        // First try to open exact alarm settings (Android 12+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AlarmManager alarmManager = (AlarmManager) activity.getSystemService(Context.ALARM_SERVICE);
            if (!alarmManager.canScheduleExactAlarms()) {
                try {
                    Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                    intent.setData(Uri.parse("package:" + activity.getPackageName()));
                    activity.startActivity(intent);
                    return;
                } catch (Exception e) {
                    Log.e(TAG, "Could not open exact alarm settings", e);
                }
            }
        }
        
        // Fall back to battery optimization settings
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager powerManager = (PowerManager) activity.getSystemService(Context.POWER_SERVICE);
            if (!powerManager.isIgnoringBatteryOptimizations(activity.getPackageName())) {
                try {
                    Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                    intent.setData(Uri.parse("package:" + activity.getPackageName()));
                    activity.startActivity(intent);
                    return;
                } catch (Exception e) {
                    Log.e(TAG, "Could not open battery optimization settings", e);
                }
            }
        }
        
        // Final fallback to general app settings
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + activity.getPackageName()));
            activity.startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Could not open app settings", e);
        }
    }
    
    /**
     * Request battery optimization exemption
     */
    public static void requestBatteryOptimizationExemption(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager powerManager = (PowerManager) activity.getSystemService(Context.POWER_SERVICE);
            if (!powerManager.isIgnoringBatteryOptimizations(activity.getPackageName())) {
                new AlertDialog.Builder(activity)
                        .setTitle("Battery Optimization")
                        .setMessage("To ensure alarms work properly, please disable battery optimization for this app.\n\n" +
                                "This allows the app to wake up the device for important reminders.")
                        .setPositiveButton("Disable Optimization", (dialog, which) -> {
                            try {
                                Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                                intent.setData(Uri.parse("package:" + activity.getPackageName()));
                                activity.startActivity(intent);
                            } catch (Exception e) {
                                Log.e(TAG, "Could not open battery settings", e);
                            }
                        })
                        .setNegativeButton("Skip", null)
                        .show();
            }
        }
    }
}