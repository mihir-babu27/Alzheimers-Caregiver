# Geofence Notification Fix - NetworkOnMainThreadException

## Issue Found

The error `android.os.NetworkOnMainThreadException` occurs when trying to get an OAuth access token on the main thread. FCM notification sending is now wrapped in a background thread to fix this.

## What Was Fixed

1. **Wrapped FCM sending in background thread** to prevent NetworkOnMainThreadException
2. **Fixed token extraction** to handle both direct strings and nested objects
3. **Fixed PendingIntent** to use FLAG_MUTABLE for Android 12+
4. **Fixed alert ID generation** to prevent duplicates

## Current Token Structure

Your Firebase structure shows:
```
patient_caretaker_tokens/
  AcmlFWnzOyQCg358jierOaGv75w1/
    active: true
    caretakerId: "AcmlFWnzOyQCg358jierOaGv75w1"
    deviceInfo: "CaretakerApp Android"
    patientId: "AcmlFWnzOyQCg358jierOaGv75w1"
    registeredAt: 1761498735293
    token: "d4PTQ47IQ9e1ZEQWHiIwe-:APA91b..."
```

The code now handles this nested structure correctly.

## Testing After Fix

After rebuilding, the logs should show:

```bash
adb logcat | grep -E "(📤 Starting FCM|FCM notification sent successfully)"
```

If successful, you'll see:
```
📤 Starting FCM send on background thread...
FCM HTTP v1 notification sent successfully: [patient] - EXIT [zone]
```

## Known Issues

1. **FCM service account** - Make sure `firebase-service-account.json` has valid credentials
2. **CaretakerApp notification receiver** - Must be running to receive notifications

