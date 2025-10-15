# Firebase Setup Guide for Alzheimer's Caregiver App

This guide walks you through setting up Firebase for both the Patient App and CaretakerApp with secure configuration.

## 1. Firebase Project Setup

### Create Firebase Project

1. Go to the [Firebase Console](https://console.firebase.google.com/)
2. Click **"Create a project"**
3. Enter project name: `alzheimers-caregiver-app`
4. Enable/disable Google Analytics as needed
5. Click **"Create project"**

### Add Android Apps

You need to add **both** apps to your Firebase project:

#### Patient App (Main App)

1. Click **"Add app"** → Select Android
2. **Android package name**: `com.mihir.alzheimerscaregiver`
3. **App nickname**: `Patient App`
4. **Debug signing certificate SHA-1**: Optional (needed for Google Sign-in)
5. Download `google-services.json`
6. Place it in `app/` directory (replace existing)

#### CaretakerApp

1. Click **"Add app"** → Select Android again
2. **Android package name**: `com.mihir.alzheimerscaregiver.caretaker`
3. **App nickname**: `CaretakerApp`
4. **Debug signing certificate SHA-1**: Optional
5. Download `google-services.json`
6. Place it in `CaretakerApp/app/` directory

## 2. Enable Firebase Services

### Realtime Database

1. In Firebase Console, go to **"Realtime Database"**
2. Click **"Create database"**
3. Choose location (e.g., us-central1)
4. Start in **test mode** for now
5. **Important**: Update rules later for security

### Cloud Messaging (FCM)

1. Go to **"Cloud Messaging"** in left sidebar
2. FCM is automatically enabled
3. Note the **Server Key** (needed for configuration)

### Authentication (Recommended)

1. Go to **"Authentication"** → **"Sign-in method"**
2. Enable **Email/Password**
3. Enable **Google Sign-in** (optional)

## 3. Configure API Keys Securely

### Get Firebase Server Key

1. In Firebase Console, go to **"Project settings"** (gear icon)
2. Click **"Cloud Messaging"** tab
3. Copy the **Server key** (starts with `AAAA...`)

### Configure Local Keys

1. Open `secure-keys/fcm-keys.properties`
2. Replace placeholder with your actual key:
   ```properties
   FIREBASE_SERVER_KEY=AAAA[your-actual-server-key-here]
   FCM_SENDER_ID=123456789012
   ```

### Security Setup

1. **Never commit API keys to version control**
2. Add to `.gitignore`:

   ```gitignore
   # Secure API keys
   secure-keys/fcm-keys.properties
   secure-keys/api-keys.properties

   # Firebase config
   app/google-services.json
   CaretakerApp/app/google-services.json
   ```

## 4. Database Structure & Rules

### Database Schema

```json
{
  "users": {
    "patientId": {
      "name": "Patient Name",
      "email": "patient@example.com",
      "caretakers": ["caretakerId1", "caretakerId2"]
    },
    "caretakerId": {
      "name": "Caretaker Name",
      "email": "caretaker@example.com",
      "fcmToken": "device-fcm-token",
      "patients": ["patientId"]
    }
  },
  "locations": {
    "patientId": {
      "locationId": {
        "latitude": 40.7128,
        "longitude": -74.006,
        "timestamp": 1640995200000,
        "accuracy": 10.5,
        "address": "New York, NY",
        "isEmergency": false
      }
    }
  },
  "geofences": {
    "patientId": {
      "geofenceId": {
        "name": "Home Safe Zone",
        "latitude": 40.7128,
        "longitude": -74.006,
        "radius": 100.0,
        "type": "SAFE_ZONE",
        "isActive": true,
        "createdAt": 1640995200000,
        "createdBy": "caretakerId"
      }
    }
  },
  "alerts": {
    "patientId": {
      "alertId": {
        "type": "GEOFENCE_EXIT",
        "geofenceId": "geofenceId",
        "timestamp": 1640995200000,
        "location": {
          "latitude": 40.7128,
          "longitude": -74.006
        },
        "severity": "HIGH",
        "acknowledged": false
      }
    }
  },
  "fcm_tokens": {
    "userId": {
      "token": "fcm-device-token",
      "timestamp": 1640995200000
    }
  }
}
```

### Production Security Rules

```json
{
  "rules": {
    "users": {
      "$uid": {
        ".read": "$uid === auth.uid",
        ".write": "$uid === auth.uid"
      }
    },
    "locations": {
      "$patientId": {
        ".read": "auth != null && (auth.uid === $patientId || root.child('users').child($patientId).child('caretakers').hasChild(auth.uid))",
        ".write": "auth != null && auth.uid === $patientId"
      }
    },
    "geofences": {
      "$patientId": {
        ".read": "auth != null && (auth.uid === $patientId || root.child('users').child($patientId).child('caretakers').hasChild(auth.uid))",
        ".write": "auth != null && (auth.uid === $patientId || root.child('users').child($patientId).child('caretakers').hasChild(auth.uid))"
      }
    },
    "alerts": {
      "$patientId": {
        ".read": "auth != null && (auth.uid === $patientId || root.child('users').child($patientId).child('caretakers').hasChild(auth.uid))",
        ".write": "auth != null && auth.uid === $patientId"
      }
    },
    "fcm_tokens": {
      "$uid": {
        ".read": "$uid === auth.uid",
        ".write": "$uid === auth.uid"
      }
    }
  }
}
```

## 5. Testing FCM Notifications

### Using Firebase Console

1. Go to **"Cloud Messaging"** → **"Send your first message"**
2. Enter notification title and text
3. Click **"Send test message"**
4. Enter FCM token from device logs
5. Send notification to test connectivity

### Testing Geofence Alerts

1. Install both apps on test devices
2. Create geofence in CaretakerApp
3. Move Patient App outside geofence area
4. Verify notification appears on CaretakerApp

### Debug FCM Token

Add this to Patient App `MainActivity`:

```java
FirebaseMessaging.getInstance().getToken()
    .addOnCompleteListener(new OnCompleteListener<String>() {
        @Override
        public void onComplete(@NonNull Task<String> task) {
            if (!task.isSuccessful()) {
                Log.w(TAG, "Fetching FCM registration token failed", task.getException());
                return;
            }

            String token = task.getResult();
            Log.d(TAG, "FCM Token: " + token);
        }
    });
```

## 6. Production Deployment

### Build Configuration

1. Generate release signing key:

   ```bash
   keytool -genkey -v -keystore release-key.keystore -alias release -keyalg RSA -keysize 2048 -validity 10000
   ```

2. Update `app/build.gradle` signing config:
   ```gradle
   android {
       signingConfigs {
           release {
               storeFile file('release-key.keystore')
               storePassword 'your-keystore-password'
               keyAlias 'release'
               keyPassword 'your-key-password'
           }
       }
   }
   ```

### Security Checklist

- [ ] API keys stored in properties files (not hardcoded)
- [ ] Firebase rules properly configured
- [ ] Authentication enabled and configured
- [ ] Database access restricted by user roles
- [ ] FCM tokens regularly refreshed
- [ ] Release APK signed with production key
- [ ] Proguard/R8 obfuscation enabled

## 7. Monitoring & Analytics

### Firebase Analytics

1. Enable Analytics in Firebase Console
2. Track key events:
   - User sign in/out
   - Geofence creation
   - Alert generation
   - Location updates

### Crashlytics (Recommended)

1. Add Crashlytics SDK to both apps
2. Monitor crash reports
3. Set up alert notifications

## 8. Troubleshooting

### Common Issues

1. **FCM not receiving**: Check token registration, Firebase project config
2. **Database permission denied**: Verify authentication, check rules
3. **Geolocation not updating**: Check permissions, GPS settings
4. **Build failures**: Verify google-services.json placement, dependencies

### Useful Commands

```bash
# Check FCM connectivity
adb logcat -s FirebaseMessaging

# Clear app data for testing
adb shell pm clear com.mihir.alzheimerscaregiver

# Test notification via curl
curl -X POST https://fcm.googleapis.com/fcm/send \
-H "Authorization: key=YOUR_SERVER_KEY" \
-H "Content-Type: application/json" \
-d '{
  "to": "DEVICE_TOKEN",
  "notification": {
    "title": "Test Alert",
    "body": "This is a test notification"
  }
}'
```

## 9. Next Steps

1. **Test thoroughly**: Both apps on multiple devices
2. **User authentication**: Implement proper sign-in flow
3. **Enhanced notifications**: Add custom sounds, vibration patterns
4. **Offline capability**: Cache critical data locally
5. **Battery optimization**: Implement smart location tracking
6. **User management**: Admin portal for caretaker assignment

---

**Important**: Keep your Firebase Server Key secure and never expose it in your source code or logs. Use the secure configuration method implemented in this project.
