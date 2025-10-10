package com.mihir.alzheimerscaregiver.alarm;

import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Centralized logging utility for the alarm system
 * Provides detailed, timestamped logging for debugging alarm issues
 */
public class AlarmLogger {
    private static final String BASE_TAG = "AlarmSystem";
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault());
    
    // Log levels
    public static final int DEBUG = Log.DEBUG;
    public static final int INFO = Log.INFO;
    public static final int WARN = Log.WARN;
    public static final int ERROR = Log.ERROR;
    
    /**
     * Log alarm scheduling events
     */
    public static void logScheduling(String reminderId, String title, long timeMillis, boolean isRepeating, boolean success) {
        String timestamp = DATE_FORMAT.format(new Date());
        String scheduledTime = DATE_FORMAT.format(new Date(timeMillis));
        
        String message = String.format(
            "[%s] ALARM_SCHEDULE | ID: %s | Title: %s | Time: %s | Repeating: %s | Success: %s",
            timestamp, reminderId, title, scheduledTime, isRepeating, success
        );
        
        if (success) {
            Log.i(BASE_TAG + "_Schedule", message);
        } else {
            Log.e(BASE_TAG + "_Schedule", message);
        }
    }
    
    /**
     * Log alarm triggering events
     */
    public static void logTriggering(String reminderId, String title, boolean isRepeating) {
        String timestamp = DATE_FORMAT.format(new Date());
        
        String message = String.format(
            "[%s] ALARM_TRIGGER | ID: %s | Title: %s | Repeating: %s",
            timestamp, reminderId, title, isRepeating
        );
        
        Log.i(BASE_TAG + "_Trigger", message);
    }
    
    /**
     * Log alarm rescheduling events for repeating alarms
     */
    public static void logRescheduling(String reminderId, String title, long nextTimeMillis, boolean success) {
        String timestamp = DATE_FORMAT.format(new Date());
        String nextTime = DATE_FORMAT.format(new Date(nextTimeMillis));
        
        String message = String.format(
            "[%s] ALARM_RESCHEDULE | ID: %s | Title: %s | Next Time: %s | Success: %s",
            timestamp, reminderId, title, nextTime, success
        );
        
        if (success) {
            Log.i(BASE_TAG + "_Reschedule", message);
        } else {
            Log.e(BASE_TAG + "_Reschedule", message);
        }
    }
    
    /**
     * Log alarm cancellation events
     */
    public static void logCancellation(String reminderId, String reason) {
        String timestamp = DATE_FORMAT.format(new Date());
        
        String message = String.format(
            "[%s] ALARM_CANCEL | ID: %s | Reason: %s",
            timestamp, reminderId, reason
        );
        
        Log.i(BASE_TAG + "_Cancel", message);
    }
    
    /**
     * Log boot restoration events
     */
    public static void logBootRestoration(int alarmCount, boolean success) {
        String timestamp = DATE_FORMAT.format(new Date());
        
        String message = String.format(
            "[%s] BOOT_RESTORE | Alarm Count: %d | Success: %s",
            timestamp, alarmCount, success
        );
        
        if (success) {
            Log.i(BASE_TAG + "_Boot", message);
        } else {
            Log.e(BASE_TAG + "_Boot", message);
        }
    }
    
    /**
     * Log midnight reset events
     */
    public static void logMidnightReset(boolean success, String details) {
        String timestamp = DATE_FORMAT.format(new Date());
        
        String message = String.format(
            "[%s] MIDNIGHT_RESET | Success: %s | Details: %s",
            timestamp, success, details
        );
        
        if (success) {
            Log.i(BASE_TAG + "_MidnightReset", message);
        } else {
            Log.e(BASE_TAG + "_MidnightReset", message);
        }
    }
    
    /**
     * Log alarm system errors
     */
    public static void logError(String component, String operation, String error, Exception exception) {
        String timestamp = DATE_FORMAT.format(new Date());
        
        String message = String.format(
            "[%s] ERROR | Component: %s | Operation: %s | Error: %s",
            timestamp, component, operation, error
        );
        
        if (exception != null) {
            Log.e(BASE_TAG + "_Error", message, exception);
        } else {
            Log.e(BASE_TAG + "_Error", message);
        }
    }
    
    /**
     * Log general alarm system debug information
     */
    public static void logDebug(String component, String message) {
        String timestamp = DATE_FORMAT.format(new Date());
        
        String debugMessage = String.format(
            "[%s] DEBUG | Component: %s | Message: %s",
            timestamp, component, message
        );
        
        Log.d(BASE_TAG + "_Debug", debugMessage);
    }
    
    /**
     * Log alarm permissions and capabilities
     */
    public static void logPermissions(boolean canScheduleExact, boolean hasNotificationPerm, int sdkVersion) {
        String timestamp = DATE_FORMAT.format(new Date());
        
        String message = String.format(
            "[%s] PERMISSIONS | Can Schedule Exact: %s | Has Notification: %s | SDK: %d",
            timestamp, canScheduleExact, hasNotificationPerm, sdkVersion
        );
        
        Log.i(BASE_TAG + "_Permissions", message);
    }
    
    /**
     * Create a summary log entry for debugging sessions
     */
    public static void logSessionSummary(int totalAlarms, int repeatingAlarms, int activeAlarms) {
        String timestamp = DATE_FORMAT.format(new Date());
        
        String message = String.format(
            "[%s] SESSION_SUMMARY | Total Alarms: %d | Repeating: %d | Active: %d",
            timestamp, totalAlarms, repeatingAlarms, activeAlarms
        );
        
        Log.i(BASE_TAG + "_Summary", message);
    }
}