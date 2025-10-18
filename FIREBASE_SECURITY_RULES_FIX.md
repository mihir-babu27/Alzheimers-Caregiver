# Firebase Security Rules Update - Fix Permission Denied Errors

## Problem

The app is getting **"PERMISSION_DENIED: Missing or insufficient permissions"** errors when trying to access Firestore collections like `reminders`, `tasks`, `stories`, etc.

## Root Cause

The Firebase Security Rules were only configured for subcollections under `patients/{patientId}`, but the app is using top-level collections with a `patientId` field.

## Solution Applied

### 1. Created New Security Rules (`firestore.rules`)

- ✅ **Reminders Collection**: Allow access to `reminders/{reminderId}` based on `patientId` field
- ✅ **Tasks Collection**: Allow access to `tasks/{taskId}` based on `patientId` field
- ✅ **Stories Collection**: Allow access to `stories/{storyId}` based on `patientId` field
- ✅ **Location Collections**: Allow access to location data and sharing states
- ✅ **Caretaker Integration**: Maintains existing caretaker-patient linking permissions

### 2. Updated Firebase Configuration (`firebase.json`)

Changed from: `"rules": "CaretakerApp/firestore.rules"`
To: `"rules": "firestore.rules"`

## Security Rules Logic

### For Top-Level Collections (reminders, tasks, stories, locations):

```javascript
// Allow access if:
// 1. Patient accessing their own data (patientId matches user ID)
// 2. OR Caretaker linked to the patient
allow read, write: if request.auth != null && (
  request.auth.uid == resource.data.patientId ||
  exists(/databases/$(database)/documents/caretakerPatients/$(request.auth.uid)/linkedPatients/$(resource.data.patientId))
);
```

### For Patient Documents:

```javascript
// Allow access to patients/{patientId} and subcollections
// Same logic as before - patient or linked caretaker
```

## How to Deploy the Rules

### Option 1: Using Firebase CLI (Recommended)

```bash
# Install Firebase CLI if not already installed
npm install -g firebase-tools

# Login to Firebase (if not already logged in)
firebase login

# Navigate to project root
cd "c:\Users\mihir\OneDrive\Desktop\temp\AlzheimersCaregiver"

# Deploy only security rules
firebase deploy --only firestore:rules

# OR deploy everything if needed
firebase deploy
```

### Option 2: Using Firebase Console

1. Open [Firebase Console](https://console.firebase.google.com/)
2. Select your project
3. Go to **Firestore Database** → **Rules**
4. Copy the contents of `firestore.rules` and paste them
5. Click **Publish**

## What This Fixes

### ✅ Resolved Errors:

- `ReminderRepository` - "Error fetching reminders for rescheduling"
- Permission denied when accessing reminders, tasks, stories
- Location sharing permission issues
- Caretaker app integration permission issues

### ✅ Maintained Functionality:

- Patient can access only their own data
- Caretakers can access data for linked patients only
- Secure authentication-based access control
- Cross-app compatibility (Patient app + Caretaker app)

## Verification Steps

After deploying the rules:

1. **Test Reminder Access**: Check if reminder rescheduling works without permission errors
2. **Test Location Sharing**: Verify location tracking doesn't show permission errors
3. **Test Tasks/Stories**: Ensure other collections are accessible
4. **Test Caretaker Access**: Verify caretakers can still access linked patient data

## Error Log Monitoring

Watch for these log messages to confirm the fix:

- ❌ Before: `PERMISSION_DENIED: Missing or insufficient permissions`
- ✅ After: No permission errors, successful data access

## Firebase Project Configuration Required

Make sure your Firebase project has:

- ✅ **Authentication enabled** (for user ID verification)
- ✅ **Firestore Database created** (for data storage)
- ✅ **Security rules deployed** (using steps above)

## Backup Note

The original rules are preserved in:

- `CaretakerApp/firestore.rules` (original caretaker-specific rules)
- `firestore.rules` (new comprehensive rules for both apps)

This ensures backward compatibility while fixing the permission issues.

---

**Next Steps**: Deploy the security rules using Firebase CLI or Console to resolve the permission denied errors.
