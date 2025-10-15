# FCM Migration to HTTP v1 API - Critical Update

## 🚨 **URGENT: Legacy FCM Server Keys Deprecated**

**Important**: Firebase Cloud Messaging legacy server keys are **deprecated and no longer supported** as of July 22, 2024. All applications must migrate to the **FCM HTTP v1 API** immediately.

---

## 🔄 **Migration Overview**

### **What Changed**

1. **Endpoint**: `https://fcm.googleapis.com/fcm/send` → `https://fcm.googleapis.com/v1/projects/{PROJECT_ID}/messages:send`
2. **Authentication**: Server Keys → OAuth 2.0 Access Tokens
3. **Payload Structure**: Complete JSON restructuring required
4. **Security**: Service Account JSON files instead of server keys

### **Benefits of HTTP v1 API**

- ✅ **Enhanced Security**: Short-lived OAuth 2.0 tokens (1 hour expiry)
- ✅ **Cross-Platform**: Single request for Android, iOS, Web
- ✅ **Better Customization**: Platform-specific overrides
- ✅ **Future-Proof**: Full support for new Firebase features

---

## 📋 **Step-by-Step Migration Guide**

### **1. Update Firebase Project Configuration**

#### Get Service Account JSON

1. Go to [Firebase Console](https://console.firebase.google.com/)
2. Select your project: `alzheimers-caregiver-app`
3. Navigate to **Project Settings** → **Service Accounts**
4. Click **"Generate New Private Key"**
5. Download the JSON file (e.g., `firebase-adminsdk-xxxxx-xxxxxxxx.json`)

#### Secure File Placement

```
AlzheimersCaregiver/
├── secure-keys/
│   ├── firebase-service-account.json      ← Place downloaded file here
│   ├── firebase-service-account.json.template ← Template for reference
│   └── fcm-keys.properties               ← Updated configuration
```

### **2. Update Configuration Files**

#### Configure `secure-keys/fcm-keys.properties`

```properties
# Firebase HTTP v1 API Configuration
FIREBASE_PROJECT_ID=alzheimers-caregiver-app
FCM_SENDER_ID=123456789012
FIREBASE_SERVICE_ACCOUNT_PATH=secure-keys/firebase-service-account.json
```

#### Update `.gitignore`

```gitignore
# Firebase Service Account (NEVER commit this!)
secure-keys/firebase-service-account.json
secure-keys/fcm-keys.properties

# Legacy files (if any)
secure-keys/api-keys.properties

# Generated files
app/google-services.json
CaretakerApp/app/google-services.json
```

### **3. Code Changes Required**

#### Updated Dependencies (already added)

```gradle
// Google Auth Library for OAuth 2.0 tokens
implementation 'com.google.auth:google-auth-library-oauth2-http:1.23.0'
implementation 'com.google.firebase:firebase-messaging'
```

#### Key Code Updates

- ✅ **FCMNotificationSender.java**: Updated for HTTP v1 API
- ✅ **Build Configuration**: New properties loading
- ✅ **OAuth 2.0 Integration**: Service account authentication
- ✅ **JSON Payload**: HTTP v1 message format

### **4. Payload Format Changes**

#### Before (Legacy API)

```json
{
  "to": "device-token",
  "notification": {
    "title": "Alert",
    "body": "Message"
  },
  "data": {
    "key": "value"
  }
}
```

#### After (HTTP v1 API)

```json
{
  "message": {
    "token": "device-token",
    "notification": {
      "title": "Alert",
      "body": "Message"
    },
    "data": {
      "key": "value"
    },
    "android": {
      "notification": {
        "icon": "ic_notification",
        "color": "#FF4444"
      }
    }
  }
}
```

---

## 🛠️ **Implementation Status**

### **✅ Completed Updates**

- [x] Updated `fcm-keys.properties` for HTTP v1 configuration
- [x] Added Google Auth Library dependency
- [x] Updated build.gradle for new configuration loading
- [x] Created service account JSON template
- [x] Updated FCMNotificationSender class structure
- [x] Implemented OAuth 2.0 token generation
- [x] Updated JSON payload format for HTTP v1

### **🔄 In Progress**

- [ ] Fix remaining import issues in FCMNotificationSender
- [ ] Update CaretakerApp to match new structure
- [ ] Complete testing with actual Firebase project

### **⏳ Next Steps**

1. **Download Service Account JSON** from Firebase Console
2. **Place JSON file** in `secure-keys/` directory
3. **Update configuration** with your actual project ID
4. **Test FCM notifications** end-to-end
5. **Update CaretakerApp** with same changes

---

## 🧪 **Testing the Migration**

### **1. Verify Configuration**

```bash
# Check if service account file exists
ls secure-keys/firebase-service-account.json

# Verify project ID in configuration
grep FIREBASE_PROJECT_ID secure-keys/fcm-keys.properties
```

### **2. Test OAuth 2.0 Token Generation**

Add temporary logging to `FCMNotificationSender`:

```java
String accessToken = getAccessToken();
if (accessToken != null) {
    Log.d(TAG, "OAuth token generated successfully (first 20 chars): " +
          accessToken.substring(0, Math.min(20, accessToken.length())) + "...");
} else {
    Log.e(TAG, "Failed to generate OAuth token");
}
```

### **3. Test FCM Notification**

1. Install Patient App and CaretakerApp
2. Create a geofence in CaretakerApp
3. Move patient outside geofence
4. Verify notification received with HTTP v1 API

### **4. Monitor Logs**

```bash
# Check for HTTP v1 API calls
adb logcat -s FCMNotificationSender

# Look for successful responses
adb logcat | grep "FCM HTTP v1 notification sent successfully"
```

---

## ⚠️ **Troubleshooting**

### **Common Issues**

#### Service Account Not Found

```
Error: FileNotFoundException: firebase-service-account.json
Solution: Download JSON from Firebase Console → Service Accounts
```

#### Invalid Project ID

```
Error: FCM URL not configured
Solution: Update FIREBASE_PROJECT_ID in fcm-keys.properties
```

#### OAuth Token Failed

```
Error: Access token not available
Solution: Check service account JSON format and permissions
```

#### HTTP 403 Forbidden

```
Error: Permission denied
Solution: Ensure Firebase Cloud Messaging API is enabled
```

### **Verification Commands**

#### Check Firebase Project Setup

```bash
# Verify project ID matches Firebase Console
grep project_id secure-keys/firebase-service-account.json
```

#### Test Service Account Permissions

- Verify "Firebase Admin SDK Admin Service Agent" role
- Ensure "Firebase Cloud Messaging API" is enabled

---

## 🔐 **Security Best Practices**

### **File Permissions**

```bash
# Restrict service account file access
chmod 600 secure-keys/firebase-service-account.json
```

### **Environment Variables (Optional)**

For additional security, consider environment variables:

```bash
export GOOGLE_APPLICATION_CREDENTIALS="$(pwd)/secure-keys/firebase-service-account.json"
```

### **Monitoring**

- Monitor OAuth token refresh frequency
- Set up Firebase Analytics to track notification delivery
- Use Firebase Console to monitor API usage

---

## 📚 **Additional Resources**

- [Firebase HTTP v1 Migration Guide](https://firebase.google.com/docs/cloud-messaging/migrate-v1)
- [OAuth 2.0 Service Account Authentication](https://developers.google.com/identity/protocols/oauth2/service-account)
- [FCM HTTP v1 API Reference](https://firebase.google.com/docs/reference/fcm/rest/v1/projects.messages)
- [Firebase Service Account Setup](https://firebase.google.com/docs/admin/setup#initialize-sdk)

---

## 🚀 **Production Deployment Checklist**

### **Pre-Deployment**

- [ ] Service account JSON downloaded and secured
- [ ] Configuration files updated with real values
- [ ] OAuth 2.0 token generation tested
- [ ] End-to-end notification flow verified
- [ ] Error handling and logging implemented

### **Security Verification**

- [ ] Service account JSON not in version control
- [ ] Proper file permissions set (600)
- [ ] No hardcoded credentials in source code
- [ ] Firebase rules properly configured
- [ ] API access restricted to necessary services

### **Performance Testing**

- [ ] Token refresh mechanism working
- [ ] Notification delivery latency acceptable
- [ ] Batch notifications for multiple caretakers
- [ ] Proper error handling for failed requests
- [ ] Monitoring and alerting configured

---

**Next Action**: Download your Firebase service account JSON file and complete the configuration to restore FCM functionality.
