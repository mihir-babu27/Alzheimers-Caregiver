# Caretaker Notification System for Incomplete Reminders

## Overview

This system monitors patient medication and task reminders and automatically notifies caregivers when patients fail to complete their activities within specified time windows. The system operates across two separate devices - the patient's device and the caretaker's device.

## Problem Solved

- **Issue**: Caregivers need to know when patients haven't taken their medication or completed important tasks
- **Challenge**: Patient app runs on patient's device, CaretakerApp runs on caretaker's device
- **Solution**: Automated cross-device notification system using Firestore and delayed alarm scheduling

## System Architecture

### Components Created

#### 1. IncompleteReminderAlert.java

**Purpose**: Data model for tracking incomplete reminders
**Location**: `app/src/main/java/com/mihir/alzheimerscaregiver/data/entity/IncompleteReminderAlert.java`

**Key Features**:

- Tracks which reminders are incomplete and for how long
- Stores patient ID, reminder details, and delay information
- Provides human-readable delay descriptions ("15 minutes late", "2h 30m late")
- Status tracking: "pending", "resolved", "dismissed"

#### 2. CaretakerNotificationScheduler.java

**Purpose**: Schedules delayed checks for reminder completion
**Location**: `app/src/main/java/com/mihir/alzheimerscaregiver/caretaker/CaretakerNotificationScheduler.java`

**Key Features**:

- Schedules multiple delayed checks (15 min, 1 hour, 3 hours after reminder time)
- Creates Firestore alerts when reminders remain incomplete
- Cancels pending checks when reminders are completed
- Integrates with Android AlarmManager for reliable timing

#### 3. CaretakerNotificationReceiver.java

**Purpose**: BroadcastReceiver that executes delayed reminder checks
**Location**: `app/src/main/java/com/mihir/alzheimerscaregiver/caretaker/CaretakerNotificationReceiver.java`

**Key Features**:

- Triggered by scheduled alarms to check reminder completion status
- Queries Firestore to verify if reminder was completed
- Creates caretaker alerts for incomplete reminders
- Handles both repeating and one-time reminders properly

## How It Works

### 1. Reminder Creation Flow

```
Patient App:
1. User creates reminder (medication, task, etc.)
2. ReminderRepository.addReminder() is called
3. Alarm is scheduled for reminder time
4. CaretakerNotificationScheduler.scheduleCaretakerNotifications() is called
5. Three delayed checks are scheduled:
   - 15 minutes after reminder time
   - 1 hour after reminder time
   - 3 hours after reminder time
```

### 2. Reminder Completion Flow

```
Patient App:
1. User marks reminder as completed
2. ReminderRepository.completeReminder() is called
3. CaretakerNotificationScheduler.resolveIncompleteReminderAlert() is called
4. All pending delayed checks are cancelled
5. Any existing caretaker alerts are marked as "resolved"
```

### 3. Incomplete Reminder Detection Flow

```
Patient Device (Delayed Check):
1. AlarmManager triggers CaretakerNotificationReceiver
2. Receiver checks if reminder was completed
3. If incomplete, creates IncompleteReminderAlert in Firestore
4. Alert includes patient ID, reminder details, and delay time

Caretaker Device:
1. CaretakerApp monitors Firestore for new IncompleteReminderAlert documents
2. When new alert appears, shows notification to caretaker
3. Caretaker can see which patient missed which reminder and when
```

## Configuration

### Timing Configuration

The system uses three configurable delay times:

```java
public static final int DEFAULT_DELAY_MINUTES = 15;    // First alert: 15 minutes
public static final int SECONDARY_DELAY_MINUTES = 60;  // Second alert: 1 hour
public static final int FINAL_DELAY_MINUTES = 180;     // Final alert: 3 hours
```

### Firestore Collections

- **Reminders**: `reminders` (existing collection)
- **Incomplete Alerts**: `incomplete_reminder_alerts` (new collection)

## Integration Points

### 1. ReminderRepository Integration

**File**: `app/src/main/java/com/mihir/alzheimerscaregiver/data/ReminderRepository.java`

**Changes Made**:

- Added CaretakerNotificationScheduler as dependency
- Enhanced `addReminder()` to schedule caretaker notifications
- Enhanced `completeReminder()` to cancel/resolve caretaker notifications
- Added constructor overload to support contexts with caretaker notifications

### 2. AndroidManifest.xml Registration

**File**: `app/src/main/AndroidManifest.xml`

**Changes Made**:

```xml
<!-- Caretaker Notification Receiver for incomplete reminder alerts -->
<receiver
    android:name=".caretaker.CaretakerNotificationReceiver"
    android:exported="false" />
```

### 3. Fixed Completion Issues

**Files**: `RemindersActivity.java`, `ReminderEntityAdapter.java`

**Changes Made**:

- Fixed completion toggle to use proper status for repeating vs non-repeating reminders
- Enhanced UI feedback for immediate completion display
- Integrated with caretaker notification resolution

## Data Flow Diagram

```
Patient Creates Reminder
         ↓
Schedule Reminder Alarm + Caretaker Check Alarms
         ↓
Reminder Time Arrives → Patient Completes?
         ↓                    ↓
        No                   Yes → Cancel Caretaker Alarms
         ↓
15 min delay → Check completion → Still incomplete?
         ↓                              ↓
        No                             Yes → Create Firestore Alert
         ↓                              ↓
1 hour delay → Check completion → Still incomplete?
         ↓                              ↓
        No                             Yes → Update Firestore Alert
         ↓                              ↓
3 hour delay → Check completion → Still incomplete?
         ↓                              ↓
        No                             Yes → Final Firestore Alert
                                        ↓
                               CaretakerApp Receives Notification
```

## CaretakerApp Integration

### Required Changes in CaretakerApp

The CaretakerApp needs to implement Firestore listeners to receive these alerts:

```java
// Listen for new incomplete reminder alerts
FirebaseFirestore.getInstance()
    .collection("incomplete_reminder_alerts")
    .whereEqualTo("status", "pending")
    .addSnapshotListener((snapshots, e) -> {
        for (DocumentChange change : snapshots.getDocumentChanges()) {
            if (change.getType() == DocumentChange.Type.ADDED) {
                IncompleteReminderAlert alert = change.getDocument()
                    .toObject(IncompleteReminderAlert.class);
                showCaretakerNotification(alert);
            }
        }
    });
```

### Alert Information Available to Caretakers

- Patient ID (to identify which patient)
- Reminder title ("Take morning medication")
- Reminder type ("medication", "task", etc.)
- Scheduled time (when reminder was supposed to be completed)
- Delay time (how long it's been incomplete)
- Human-readable delay ("2h 30m late")

## Testing the System

### Test Scenario 1: Basic Incomplete Reminder

1. Create a medication reminder for current time + 2 minutes
2. Let reminder trigger but don't mark as completed
3. Wait 15 minutes → Check Firestore for new alert document
4. Verify alert contains correct patient and reminder information

### Test Scenario 2: Late Completion

1. Create reminder and let it trigger
2. Wait 10 minutes, then mark as completed
3. Verify no caretaker alert is created (completed before 15-minute threshold)

### Test Scenario 3: Multiple Delay Levels

1. Create reminder and let it trigger without completion
2. Wait 20 minutes → Verify first alert created
3. Wait 1 hour total → Verify second alert created
4. Wait 3 hours total → Verify final alert created

### Test Scenario 4: Eventual Completion

1. Create reminder and let it go incomplete for 30 minutes
2. Mark reminder as completed
3. Verify existing alert is marked as "resolved"
4. Verify no further alerts are created

## Monitoring and Troubleshooting

### Log Tags for Debugging

- `CaretakerNotificationScheduler`: Scheduling and cancellation events
- `CaretakerNotificationReceiver`: Delayed check execution and alert creation
- `ReminderRepository`: Integration with reminder completion

### Common Issues

1. **Alerts not created**: Check AlarmManager permissions and battery optimization
2. **Multiple alerts for same reminder**: Verify completion logic and alert resolution
3. **CaretakerApp not receiving alerts**: Check Firestore listeners and collection names

## Security and Privacy

- All data is stored in Firestore under patient's authenticated context
- Caretakers only see alerts for patients they have permission to monitor
- Alert data includes only necessary information (no sensitive medical details)
- Automatic cleanup of resolved alerts after 7 days (can be implemented)

## Future Enhancements

1. **Configurable delay times**: Allow caretakers to set custom alert timing
2. **Push notifications**: Direct notifications to CaretakerApp instead of just Firestore
3. **Alert escalation**: Different notification methods for different delay levels
4. **Bulk operations**: Handle multiple incomplete reminders efficiently
5. **Analytics**: Track completion rates and alert frequency per patient
