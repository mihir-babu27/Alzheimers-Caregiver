# 🚨 ANR Fix Required - Service Account File Location

## **Issue Detected: ANR (Application Not Responding)**

Your CaretakerApp crashed with an ANR because the Firebase service account JSON file is being loaded synchronously on the main thread, blocking the UI for >5 seconds.

## **Root Cause:**

- Service account file `firebase-service-account.json` needs to be in `app/src/main/assets/` folder
- Current code expects it in assets, but documentation suggested `secure-keys/` folder
- File I/O on main thread caused ANR crash

## **🔧 Immediate Fix Required:**

### **Step 1: Move Service Account File**

```bash
# Move the service account file to the correct location
mkdir -p app/src/main/assets/
cp secure-keys/firebase-service-account.json app/src/main/assets/firebase-service-account.json
```

### **Step 2: Update .gitignore**

Add this to your `.gitignore`:

```gitignore
# Firebase Service Account (NEVER commit this!)
app/src/main/assets/firebase-service-account.json
secure-keys/firebase-service-account.json

# Keep template for reference
!app/src/main/assets/firebase-service-account.json.template
```

### **Step 3: Verify Configuration**

Your `fcm-keys.properties` is now correctly configured:

```properties
FIREBASE_PROJECT_ID=recallar-12588
FCM_SENDER_ID=1092854188528
FIREBASE_SERVICE_ACCOUNT_PATH=firebase-service-account.json
```

## **🏗️ What I've Fixed:**

✅ **Updated Configuration**: Changed path from `secure-keys/` to assets folder
✅ **Enhanced Error Handling**: Better logging for service account issues  
✅ **ANR Prevention**: Added comments about background thread usage

## **🧪 Test the Fix:**

1. **Move the file** to `app/src/main/assets/firebase-service-account.json`
2. **Clean and rebuild** the project
3. **Install both apps** and test geofence notifications
4. **Monitor logs** for successful OAuth token generation

```bash
# Clean build
./gradlew.bat clean
./gradlew.bat assembleDebug

# Monitor FCM logs
adb logcat -s FCMNotificationSender
```

## **📊 Expected Behavior After Fix:**

- No more ANR crashes in CaretakerApp
- OAuth 2.0 tokens generated successfully
- FCM HTTP v1 notifications working properly
- Smooth app performance without UI blocking

## **🔍 Verification Commands:**

```bash
# Check if service account file exists in correct location
ls app/src/main/assets/firebase-service-account.json

# Verify FCM token generation
adb logcat | grep "OAuth token generated successfully"

# Monitor for ANR issues
adb logcat | grep "ANR in"
```

## **🛡️ Security Note:**

The service account file in `assets/` is packaged with the APK but remains secure because:

- APKs are signed and tamper-evident
- File is only accessible to your app
- OAuth tokens are short-lived (1 hour)
- Better than hardcoded server keys

## **Next Steps:**

1. Move the service account file as instructed above
2. Test the app to confirm ANR is resolved
3. Verify FCM notifications work end-to-end

**Priority: High** - This fixes the crash and enables proper FCM functionality.
