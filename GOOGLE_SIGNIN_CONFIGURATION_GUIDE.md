# Google Sign-In Configuration Guide for Patient App

## Current Status ✅

- ✅ **Google Services Plugin**: Already configured in build.gradle
- ✅ **Google Sign-In Dependency**: Already added (play-services-auth:20.7.0)
- ✅ **Firebase Integration**: Already set up with google-services.json
- ❌ **OAuth Client Configuration**: Missing from google-services.json
- ❌ **SHA Certificates**: Need to be configured in Firebase Console

## Step-by-Step Configuration

### 1. **Firebase Console Configuration** 🔧

#### A. Add OAuth Client IDs in Firebase Console

1. **Go to Firebase Console**: https://console.firebase.google.com/
2. **Select your project**: `recallar-12588`
3. **Navigate to**: Project Settings ⚙️ → General tab
4. **Find your Android app**: `com.mihir.alzheimerscaregiver`
5. **Add SHA certificates** (see step 2 below)
6. **Enable Authentication**:
   - Go to Authentication → Sign-in method
   - Enable "Google" provider
   - Add your project's support email

#### B. Generate SHA-1 and SHA-256 Certificates

Run these commands in your project terminal:

```bash
# For Debug Certificate (Development)
cd "/c/Users/mihir/OneDrive/Desktop/temp/AlzheimersCaregiver"
./gradlew signingReport
```

This will output SHA-1 and SHA-256 fingerprints. Copy them and add to Firebase Console.

**Expected Output:**

```
Variant: debug
Config: debug
Store: ~/.android/debug.keystore
Alias: AndroidDebugKey
MD5: XX:XX:XX:...
SHA1: XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX
SHA256: XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX
```

#### C. Add SHA Certificates to Firebase Console

1. **In Firebase Console**: Project Settings → General → Your Apps
2. **Click "Add fingerprint"**
3. **Add both SHA-1 and SHA-256** from the signingReport output
4. **Click "Save"**

### 2. **Download Updated google-services.json** 📥

After adding SHA certificates:

1. **In Firebase Console**: Project Settings → General → Your Apps
2. **Click "google-services.json"** to download the updated file
3. **Replace** the existing `app/google-services.json` with the new file
4. **Verify OAuth clients** are now included in the file

**Expected google-services.json structure after update:**

```json
{
  "client": [
    {
      "oauth_client": [
        {
          "client_id": "xxxxxxxxx-xxxxxxxxxxxxxxx.apps.googleusercontent.com",
          "client_type": 1,
          "android_info": {
            "package_name": "com.mihir.alzheimerscaregiver",
            "certificate_hash": "xxxxxxxxxxxxxxxxxx"
          }
        },
        {
          "client_id": "xxxxxxxxx-xxxxxxxxxxxxxxx.apps.googleusercontent.com",
          "client_type": 3
        }
      ]
    }
  ]
}
```

### 3. **Verify Current Implementation** ✅

The OAuth implementation is already complete:

#### A. **FirebaseAuthManager** ✅

- OAuth methods already implemented
- Context-aware constructor for Google Sign-In client
- Account collision handling

#### B. **AuthenticationActivity** ✅

- Google Sign-In button integration
- OAuth flow handling
- Error management

#### C. **UI Layout** ✅

- Google Sign-In button added to activity_authentication.xml
- Material Design styling
- Google icon included

### 4. **Testing Configuration** 🧪

After updating google-services.json:

```bash
# Clean and rebuild
cd "/c/Users/mihir/OneDrive/Desktop/temp/AlzheimersCaregiver"
./gradlew clean
./gradlew assembleDebug

# Install and test
./gradlew installDebug
```

#### A. **Test OAuth Flow**

1. Open the app
2. Tap "Continue with Google"
3. Select Google account
4. Verify successful sign-in
5. Check Firebase Authentication console for new user

#### B. **Verify Patient Creation**

1. After OAuth sign-in, check Firestore
2. Navigate to "patients" collection
3. Verify new patient document created with:
   - `patientId`: Firebase UID
   - `email`: Google account email
   - `name`: Display name from Google

### 5. **Production Configuration** 🏭

For release builds, you'll need:

#### A. **Release Keystore SHA**

```bash
# Generate release keystore SHA (when you have release keystore)
keytool -list -v -keystore /path/to/release.keystore -alias your_alias
```

#### B. **Add Release SHA to Firebase**

- Add release SHA-1 and SHA-256 to Firebase Console
- Download updated google-services.json
- Test with release build

### 6. **Troubleshooting** 🔍

#### Common Issues:

**"Google Sign-In failed: 10"**

- Missing SHA certificates in Firebase Console
- Incorrect package name
- Old google-services.json file

**"OAuth client not found"**

- SHA certificates not added to Firebase
- google-services.json not updated after adding SHA
- Clean and rebuild needed

**"Account collision"**

- User already exists with email/password
- Guide user to link accounts (implemented in AuthenticationActivity)

### 7. **Security Notes** 🔒

✅ **Current Secure Implementation:**

- No hardcoded client IDs in source code
- Web client ID auto-generated from google-services.json
- Secure token handling with Firebase Auth
- Proper error handling and user guidance

## Next Steps

1. **Run signingReport** to get SHA certificates
2. **Add SHA certificates** to Firebase Console
3. **Download updated google-services.json**
4. **Replace existing google-services.json**
5. **Test OAuth flow** in the app

The OAuth implementation is complete - you just need to configure the Firebase Console with SHA certificates and update the google-services.json file!

## Quick Commands

```bash
# Get SHA certificates
cd "/c/Users/mihir/OneDrive/Desktop/temp/AlzheimersCaregiver"
./gradlew signingReport

# After updating google-services.json:
./gradlew clean assembleDebug

# Test the app
./gradlew installDebug
```
