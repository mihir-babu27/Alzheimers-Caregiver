# Geofence Testing Guide

## ✅ Changes Made

### 1. **Geofence Monitoring Initialization**

- Added `PatientGeofenceClient` initialization to `MainActivity`
- Geofence monitoring now starts automatically when the app launches
- Location permissions are requested automatically

### 2. **Key Fixes Applied**

- **Fixed Initial Trigger**: Changed from `INITIAL_TRIGGER_ENTER` to `INITIAL_TRIGGER_ENTER | INITIAL_TRIGGER_EXIT`
- **Auto-Start**: Geofencing now starts automatically in `MainActivity.onCreate()`
- **Permission Handling**: Added automatic location permission requests (fine, coarse, and background)

### 3. **Manual Testing Feature**

- **Long-press the Location Card** in MainActivity to trigger a test geofence exit notification
- This will simulate a patient leaving a safe zone and send a notification to caretakers

## 🧪 Testing Steps

### Step 1: Check Permissions

1. Launch the Patient App
2. Grant all location permissions when prompted:
   - ✅ "While using the app"
   - ✅ "Allow all the time" (for background monitoring)

### Step 2: Verify Geofence Setup

1. Check the logs for: `"Geofence monitoring initialized for patient: [patient_id]"`
2. You should see a toast: "Safety monitoring started"

### Step 3: Monitor Firebase Data

Check Firebase Realtime Database structure:

```
/geofences/[patient_id]/
  ├── geofence1/
  │   ├── id: "geofence1"
  │   ├── label: "Home"
  │   ├── lat: [latitude]
  │   ├── lng: [longitude]
  │   ├── radius: 500
  │   ├── type: "safe_zone"
  │   └── active: true
```

### Step 4: Test Manual Trigger

1. **Long-press the Location Card** (blue location icon) in MainActivity
2. Look for toast: "Testing geofence exit notification..."
3. Check logs for: `"Geofence exit test triggered successfully"`

### Step 5: Verify Notification Sent

Check Firebase for alert creation:

```
/alerts/[patient_id]/[alert_id]/
  ├── geofenceName: "Safe Zone Name"
  ├── transitionType: "EXIT"
  ├── severity: "high"
  ├── patientLocation: {lat: X, lng: Y}
  └── timestamp: [current_time]
```

## 🔍 Debugging Tips

### Check Android Logs

```bash
adb logcat | grep -E "(PatientGeofenceClient|GeofenceTransitionReceiver|FCMNotificationSender)"
```

### Key Log Messages to Look For:

- ✅ `"PatientGeofenceClient initialized for patient: [id]"`
- ✅ `"Geofences registered successfully: [count]"`
- ✅ `"Geofence transition: [id] - EXIT"`
- ✅ `"Enhanced geofence alert sent: [name] - EXIT"`
- ✅ `"FCM notification sent to caretakers"`

### Common Issues:

1. **No notifications**: Check if geofences are loaded from Firebase
2. **Permission denied**: Ensure all location permissions are granted
3. **FCM issues**: Verify `firebase-service-account.json` is in `app/src/main/assets/`

## 🚨 Real-World Testing

### Option 1: Emulator Location Spoofing

1. Open Android Studio AVD Manager
2. Click "Extended Controls" (3 dots) → Location
3. Set location far from your safe zones
4. Should trigger automatic EXIT notifications

### Option 2: Physical Device Testing

1. Set up safe zones around current location in CaretakerApp
2. Move far from the zones (or use mock locations)
3. Real geofence EXIT events should trigger notifications

## 🎯 Expected Behavior

**When patient exits safe zone:**

1. Android geofencing system detects EXIT
2. `GeofenceTransitionReceiver` handles the event
3. `PatientGeofenceClient.handleGeofenceTransition()` processes it
4. Alert saved to Firebase `/alerts/[patient_id]/`
5. FCM notification sent to all caretakers via HTTP v1 API
6. CaretakerApp receives push notification

## 📱 CaretakerApp Integration

The geofence alerts should appear in:

- **Push notifications** on caretaker devices
- **Alerts section** in CaretakerApp dashboard
- **Real-time Firebase listeners** for instant updates

---

**Note**: The manual test (long-press Location Card) is perfect for immediate verification without needing real location changes!
