# Location Tracking Auto-Restart After Device Reboot

## Overview

This feature automatically restarts location tracking when the device is rebooted, ensuring continuous monitoring for Alzheimer's patients without requiring manual intervention from patients or caregivers.

## Implementation Details

### Components

1. **LocationBootReceiver.java**

   - Broadcast receiver that listens for boot completion events
   - Handles `BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`, and `PACKAGE_REPLACED` intents
   - Authenticates user and checks Firebase settings before restarting service

2. **LocationServiceManager.java**

   - Enhanced with `startTrackingAfterBoot()` method
   - Handles Android 8+ background service restrictions
   - Includes fallback mechanisms with delayed retry

3. **PatientLocationService.java**
   - Existing foreground service that handles location tracking
   - Properly configured to start as foreground service from boot receiver

### Android Version Compatibility

#### Android 8.0+ (API 26+) Restrictions

- **Background Service Limitations**: Android 8+ restricts background service startup
- **Solution**: Use `startForegroundService()` with immediate notification
- **Delay Strategy**: 10-second delay to ensure system is ready
- **Fallback Mechanism**: 30-second delayed retry if initial start fails

#### Android 14+ (API 34+) Enhanced Restrictions

- **Stricter Background Limitations**: Additional restrictions on boot receivers
- **Solution**: Extended delay and proper foreground service handling
- **Battery Optimization**: Service designed to work even with aggressive power management

### Permissions Required

```xml
<!-- Required for receiving boot events -->
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />

<!-- Required for location tracking -->
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION" />

<!-- Required for foreground service -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_LOCATION" />
```

### Manifest Configuration

```xml
<!-- Boot receiver registration -->
<receiver
    android:name=".location.LocationBootReceiver"
    android:enabled="true"
    android:exported="true">
    <intent-filter android:priority="1000">
        <action android:name="android.intent.action.BOOT_COMPLETED" />
        <action android:name="android.intent.action.MY_PACKAGE_REPLACED" />
        <action android:name="android.intent.action.PACKAGE_REPLACED" />
        <data android:scheme="package" />
    </intent-filter>
</receiver>
```

## How It Works

### Boot Sequence

1. **Device Boots**: System broadcasts `BOOT_COMPLETED` intent
2. **Receiver Activated**: `LocationBootReceiver` receives the broadcast
3. **Authentication Check**: Verifies user is signed in to Firebase
4. **Settings Sync**: Checks Firebase for current location sharing preference
5. **Delayed Start**: Waits 10 seconds for system stability
6. **Service Launch**: Starts `PatientLocationService` as foreground service
7. **Fallback Protection**: 30-second retry if initial start fails

### State Management

```java
// Check sequence
1. Firebase Authentication → getCurrentPatientId()
2. Firebase Settings Sync → syncWithFirebase()
3. Local Settings Fallback → isLocationSharingEnabled()
4. Service Startup → startTrackingAfterBoot()
```

### Error Handling

- **No Authentication**: Skip restart if user not signed in
- **Firebase Sync Failed**: Fallback to local SharedPreferences
- **Service Start Failed**: Automatic retry after 30 seconds
- **Background Restrictions**: Proper foreground service handling

## Testing Instructions

### Manual Testing

1. **Enable Location Sharing**:

   - Open patient app
   - Tap "Location" card
   - Enable location sharing
   - Verify service is running in notification area

2. **Simulate Reboot**:

   - Reboot device completely
   - Wait for normal boot completion
   - Check if location service auto-starts (notification should appear)
   - Verify location updates in caregiver app

3. **Test Authentication States**:
   - Sign out user before reboot → Service should NOT restart
   - Sign in user before reboot → Service should restart if sharing enabled

### Automated Testing

```bash
# Force stop app and restart boot receiver
adb shell am broadcast -a android.intent.action.BOOT_COMPLETED

# Check service status
adb shell dumpsys activity services | grep PatientLocationService

# Monitor logs
adb logcat -s LocationBootReceiver:D LocationServiceManager:D PatientLocationService:D
```

## Troubleshooting

### Common Issues

1. **Service Not Starting After Boot**

   - **Cause**: Battery optimization blocking background apps
   - **Solution**: Add app to battery optimization whitelist
   - **Check**: Settings → Battery → Battery Optimization → Allow app

2. **Delayed Startup (>10 seconds)**

   - **Cause**: System under heavy load during boot
   - **Normal**: Up to 30 seconds delay is acceptable
   - **Monitor**: Check logs for "Delayed location tracking start successful"

3. **Permission Denied Errors**
   - **Cause**: Location permissions revoked or not granted for background
   - **Solution**: Ensure all location permissions granted
   - **Check**: Settings → Apps → [App] → Permissions → Location → Always Allow

### Log Monitoring

Key log messages to monitor:

```
LocationBootReceiver: Device boot completed, checking location tracking settings
LocationBootReceiver: Firebase sharing enabled, restarting location service after boot
LocationServiceManager: Starting location tracking service after device boot
LocationServiceManager: Location tracking service started successfully after boot
PatientLocationService: Starting foreground location service
```

### Performance Impact

- **Boot Time**: Minimal impact (~10 seconds delay)
- **Battery Usage**: Same as normal location tracking
- **Memory**: No additional memory overhead
- **Network**: Initial Firebase sync on boot

## Configuration Options

### Timing Adjustments

```java
// In LocationBootReceiver.java
handler.postDelayed(() -> {
    manager.startTrackingAfterBoot();
}, 10000); // Adjust delay as needed (milliseconds)
```

### Priority Settings

```xml
<!-- In AndroidManifest.xml -->
<intent-filter android:priority="1000">  <!-- Higher = earlier execution -->
```

### Retry Behavior

```java
// In LocationServiceManager.java - scheduleDelayedStart()
}, 30000); // Adjust retry delay (milliseconds)
```

## Security Considerations

- **User Authentication**: Always verifies user is signed in before restart
- **Firebase Validation**: Confirms location sharing is actually enabled in cloud
- **Local Fallback**: Uses local preferences only if Firebase unavailable
- **Permission Respect**: Honors all Android location permission requirements

## Research Applications

This auto-restart feature ensures:

- **Continuous Data Collection**: No gaps in location tracking for research
- **Patient Safety**: Immediate restoration of safety monitoring after device restart
- **Caregiver Confidence**: Reliable location sharing without technical intervention
- **Data Integrity**: Consistent location history for longitudinal studies

## Future Enhancements

### Planned Improvements

1. **Smart Delay**: Dynamic delay based on device performance
2. **Network Awareness**: Wait for network connectivity before start
3. **Battery Level Check**: Defer start if battery critically low
4. **Geofence Restoration**: Automatic geofence re-registration after boot

### Advanced Features

1. **Progressive Retry**: Exponential backoff for failed starts
2. **Health Monitoring**: Service health checks and auto-recovery
3. **Analytics Integration**: Boot success/failure reporting
4. **User Notification**: Optional notification when service auto-restarts

---

_This auto-restart implementation ensures reliable, continuous location monitoring for Alzheimer's patients while respecting Android's evolving background execution policies._
