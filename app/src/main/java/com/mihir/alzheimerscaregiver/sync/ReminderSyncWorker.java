package com.mihir.alzheimerscaregiver.sync;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.mihir.alzheimerscaregiver.alarm.AlarmScheduler;
import com.mihir.alzheimerscaregiver.data.ReminderRepository;

/**
 * Periodic background worker to ensure reminders are synced and alarms scheduled.
 * This acts as a safety net if FCM is delayed or missed.
 */
public class ReminderSyncWorker extends Worker {
    private static final String TAG = "ReminderSyncWorker";

    public ReminderSyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            AlarmScheduler scheduler = new AlarmScheduler(getApplicationContext());
            ReminderRepository repo = new ReminderRepository(scheduler);
            repo.rescheduleAllAlarms();
            return Result.success();
        } catch (Exception e) {
            Log.e(TAG, "Reminder sync failed", e);
            return Result.retry();
        }
    }
}
