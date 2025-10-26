# Geofence Debugging Guide

## Issue
Geofences are being created successfully in CaretakerApp and stored in Firebase, but:
- No alerts are generated when patient exits safe zone
- No alerts stored in Firebase `/patients/{patientId}/alerts/`
- No FCM notifications sent to caretakers

## Testing Steps

### Step 1: Verify Geofences Are Loaded in Patient App

Run this command to check logs:
```bash
adb logcat | grep -E "(PatientGeofenceClient|Geofence)"
```

**Expected logs:**
```
🌍 Loading X geofences from Firebase for patient: [patientId]
🎯 Geofences registered successfully: X
✅ Loaded X geofences
```

### Step 2: Check Android Location Permissions

The patient app needs these permissions:
- ✅ ACCESS_FINE_LOCATION
- ✅ ACCESS_COARSE_LOCATION  
- ✅ ACCESS_BACKGROUND_LOCATION (for Android 10+)

To verify:
1. Settings → Apps → Alzheimer's Caregiver (Patient App)
2. Permissions → Location
3. Should be set to "Allow all the time" for best results

### Step 3: Test Geofence Transition Manually

I've added a `testGeofenceExit()` method to MainActivity. You can trigger it by calling it in the MainActivity, or add a button.

**Add this to test:**
```java
// In MainActivity, add a test button or call in onCreate
if (patientId != null) {
    testGeofenceExit();
}
```

This will:
1. Load geofences from Firebase
2. Trigger a simulated EXIT event
3. Create an alert in Firebase
4. Send FCM notification

### Step 4: Monitor Firebase Alerts

Check if alerts are being created:
```bash
# In Firebase Console or use Firebase CLI
firebase database:get /patients/{patientId}/alerts
```

Or manually check in Firebase Console:
- Go to Realtime Database
- Navigate to `/patients/{patientId}/alerts/`
- Should see alert entries when patient exits

### Step 5: Check FCM Token Registration

Verify caretaker tokens are registered:
```bash
firebase database:get /patient_caretaker_tokens/{patientId}
```

Structure should be:
```json
{
  "{caretakerId}": {
    "token": "fcm_token_here",
    "active": true
  }
}
```

## Common Issues

### Issue 1: Geofences Not Registering

**Symptoms:**
- No logs showing "Geofences registered successfully"
- Patient app logs show permission errors

**Solution:**
1. Check location permissions are granted
2. Verify background location permission for Android 10+
3. Check logs: `adb logcat | grep "Permission"`

### Issue 2: Geofence Transitions Not Detected

**Symptoms:**
- Geofences registered successfully
- No logs when moving outside safe zone

**Possible causes:**
1. GeofenceTransitionReceiver not registered properly
2. Android killed the receiver due to battery optimization
3. Device not meeting geofence location accuracy requirements

**Solution:**
1. Disable battery optimization for the app:
   - Settings → Battery → Background optimization
   - Find "Alzheimer's Caregiver" → Don't optimize

2. Check if GeofenceTransitionReceiver is registered:
   ```bash
   adb shell dumpsys package com.mihir.alzheimerscaregiver | grep -A 5 "receiver"
   ```

### Issue 3: Alerts Created But No FCM Notifications

**Symptoms:**
- Alerts appear in Firebase `/patients/{patientId}/alerts/`
- No push notifications received in CaretakerApp

**Check:**
1. FCM service account configured properly
2. Caretaker tokens are registered at `/patient_caretaker_tokens/{patientId}/`
3. BuildConfig.FIREBASE_PROJECT_ID is not "placeholder"

**Solution:**
```java
// Check logs for FCM errors
adb logcat | grep "FCMNotificationSender"
```

Should see:
```
FCM HTTP v1 notification sent successfully: [patient] - EXIT [zone]
```

## Debug Logging Commands

### Full Geofence Debug
```bash
adb logcat | grep -E "(PatientGeofenceClient|GeofenceTransition|FCMNotificationSender|Enhanced geofence)"
```

### Check Permission Status
```bash
adb logcat | grep "Location permission"
```

### Check Geofence Registration
```bash
adb logcat | grep -E "(Geofences registered|Failed to register)"
```

### Monitor Firebase Writes
```bash
adb logcat | grep -E "(patients.*geofences|patients.*alerts)"
```

## Manual Test Script

Run this test sequence:

1. **Create a geofence** in CaretakerApp at patient's current location
2. **Open Patient App** and check logs for geofence loading
3. **Set emulator location** to inside the safe zone
4. **Move location** to outside the safe zone
5. **Check logs** for geofence transition
6. **Verify Firebase** for alert creation
7. **Check CaretakerApp** for notification

## Quick Fix: Use Test Method

Add this to MainActivity to instantly test the full flow:

```java
// In MainActivity's onCreate, after geofence client initialization
Handler handler = new Handler();
handler.postDelayed(() -> {
    if (geofenceClient != null) {
        testGeofenceExit(); // This will test the full flow
    }
}, 5000); // Wait 5 seconds for Firebase to load geofences
```

This bypasses Android's geofence system and directly tests the alert/FCM flow.

## Next Steps

1. Check logs for "🌍 Loading X geofences" - confirms Firebase connection
2. Run `testGeofenceExit()` method - tests alert creation and FCM
3. If test works but real geofences don't, check Android geofencing permissions
4. Verify FCM tokens are registered correctly

