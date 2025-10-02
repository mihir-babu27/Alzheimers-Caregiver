package com.mihir.alzheimerscaregiver.alarm;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.util.Log;

/**
 * Helper class to play alarm sounds for medication reminders
 */
public class AlarmSoundPlayer {
    private static final String TAG = "AlarmSoundPlayer";
    private static MediaPlayer mediaPlayer;
    private static Vibrator vibrator;
    private static Handler handler;
    private static Runnable stopRunnable;
    
    /**
     * Start playing an alarm sound with vibration
     * 
     * @param context Application context
     * @param isMedication Whether this is a critical medication reminder
     * @param durationSeconds How long to play the sound (0 = until stopped manually)
     */
    public static void playAlarmSound(Context context, boolean isMedication, int durationSeconds) {
        try {
            stopAlarmSound(); // Stop any existing alarm first
            
            // Initialize handler if needed
            if (handler == null) {
                handler = new Handler(Looper.getMainLooper());
            }
            
            // Get appropriate sound URI
            Uri soundUri = isMedication ?
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) :
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            
            // Fallback if no alarm sound available
            if (soundUri == null) {
                soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
            }
            
            // Create and configure media player
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(context, soundUri);
            
            // Set audio attributes for alarm sounds
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                AudioAttributes attributes = new AudioAttributes.Builder()
                        .setUsage(isMedication ? 
                                AudioAttributes.USAGE_ALARM : 
                                AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build();
                mediaPlayer.setAudioAttributes(attributes);
            } else {
                // For older devices
                mediaPlayer.setAudioStreamType(isMedication ? 
                        android.media.AudioManager.STREAM_ALARM : 
                        android.media.AudioManager.STREAM_NOTIFICATION);
            }
            
            // Loop the sound for medication alarms
            if (isMedication) {
                mediaPlayer.setLooping(true);
            }
            
            // Prepare and start playback
            mediaPlayer.setOnPreparedListener(mp -> {
                mp.start();
                Log.d(TAG, "Alarm sound started");
            });
            
            mediaPlayer.prepareAsync();
            
            // Start vibration
            startVibration(context, isMedication);
            
            // Set up auto-stop if duration is specified
            if (durationSeconds > 0) {
                stopRunnable = () -> {
                    stopAlarmSound();
                    Log.d(TAG, "Alarm sound stopped after " + durationSeconds + " seconds");
                };
                handler.postDelayed(stopRunnable, durationSeconds * 1000L);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error playing alarm sound", e);
        }
    }
    
    /**
     * Stop any currently playing alarm sound and vibration
     */
    public static void stopAlarmSound() {
        // Stop media player
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
                mediaPlayer.reset();
                mediaPlayer.release();
                Log.d(TAG, "Alarm sound stopped");
            } catch (Exception e) {
                Log.e(TAG, "Error stopping alarm sound", e);
            } finally {
                mediaPlayer = null;
            }
        }
        
        // Stop vibration
        if (vibrator != null) {
            vibrator.cancel();
            vibrator = null;
        }
        
        // Remove callbacks
        if (handler != null && stopRunnable != null) {
            handler.removeCallbacks(stopRunnable);
            stopRunnable = null;
        }
    }
    
    /**
     * Start vibration pattern for alarm
     */
    private static void startVibration(Context context, boolean isMedication) {
        try {
            // Get vibrator service
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                VibratorManager vibratorManager = (VibratorManager) context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
                if (vibratorManager != null) {
                    vibrator = vibratorManager.getDefaultVibrator();
                }
            } else {
                vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            }
            
            if (vibrator == null || !vibrator.hasVibrator()) {
                return;
            }
            
            // Create vibration pattern (different for medication vs regular reminders)
            long[] pattern = isMedication ?
                    new long[]{0, 1000, 500, 1000, 500, 1000, 500, 2000} : // Strong pattern for medications
                    new long[]{0, 500, 500, 500}; // Gentle pattern for tasks
            
            // Start vibration
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                int amplitude = isMedication ? 
                        VibrationEffect.DEFAULT_AMPLITUDE : // Full strength for medications 
                        VibrationEffect.DEFAULT_AMPLITUDE / 2; // Half strength for tasks
                        
                VibrationEffect effect = VibrationEffect.createWaveform(pattern, 
                        isMedication ? new int[]{0, 255, 0, 255, 0, 255, 0, 255} : // Strong then weak pattern
                        new int[]{0, 128, 0, 128}, // Medium strength
                        isMedication ? 0 : -1); // Repeat for medications, no repeat for tasks
                
                vibrator.vibrate(effect);
            } else {
                // For older devices
                vibrator.vibrate(pattern, isMedication ? 0 : -1);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error starting vibration", e);
        }
    }
}