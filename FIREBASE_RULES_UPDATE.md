# Firebase Database Rules Update - Geofencing Permissions

## Issue
Received "Permission denied" error when trying to create geofences in CaretakerApp:
```
com.google.firebase.database.DatabaseException: Firebase Database error: Permission denied
```

## Root Cause
The Firebase Realtime Database security rules did not include permissions for the new `/patients/{patientId}/geofences/` path structure.

## Solution Applied
Updated `firebase-database-rules.json` to add permissions for:
1. `/patients/{patientId}/geofences/` - Read/write by patients and their linked caretakers
2. `/patients/{patientId}/alerts/` - Read by patients and caretakers, write by patients only
3. `/patients/{patientId}/geofenceSettings/` - Read/write by patients and linked caretakers

## New Rules Structure

```json
"patients": {
  "$patientId": {
    ".read": "auth != null && (auth.uid == $patientId || root.child('patientCaretakerLinks').child($patientId).val() === auth.uid)",
    "geofences": {
      ".read": "auth != null && (auth.uid == $patientId || root.child('patientCaretakerLinks').child($patientId).val() === auth.uid)",
      ".write": "auth != null && (auth.uid == $patientId || root.child('patientCaretakerLinks').child($patientId).val() === auth.uid)"
    },
    "alerts": {
      ".read": "auth != null && (auth.uid == $patientId || root.child('patientCaretakerLinks').child($patientId).val() === auth.uid)",
      ".write": "auth != null && auth.uid == $patientId"
    },
    "geofenceSettings": {
      ".read": "auth != null && (auth.uid == $patientId || root.child('patientCaretakerLinks').child($patientId).val() === auth.uid)",
      ".write": "auth != null && (auth.uid == $patientId || root.child('patientCaretakerLinks').child($patientId).val() === auth.uid)"
    }
  }
}
```

## Permission Logic

### Geofences
- **Read**: Both the patient (auth.uid == patientId) AND their linked caretaker can read
- **Write**: Both the patient AND their linked caretaker can write (create/update/delete safe zones)

### Alerts
- **Read**: Both patient and caretaker can read alerts
- **Write**: Only the patient app can write alerts (system-generated when geofence exits)

### Geofence Settings
- **Read**: Both patient and caretaker can read settings
- **Write**: Both patient and caretaker can update settings

## Deployment

Rules have been deployed successfully to:
- **Project**: recallar-12588
- **Database**: recallar-12588-default-rtdb
- **Status**: ✅ Successfully deployed

Console: https://console.firebase.google.com/project/recallar-12588/overview

## Testing

Try creating a geofence in CaretakerApp again. It should now work without permission errors.

The caretaker (when linked to the patient via `patientCaretakerLinks`) can now:
1. Create safe zones at `/patients/{patientId}/geofences/`
2. Read existing geofences
3. Update geofence settings
4. View alerts when patients exit safe zones

