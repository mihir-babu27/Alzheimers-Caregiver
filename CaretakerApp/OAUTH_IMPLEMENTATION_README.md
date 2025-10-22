# OAuth Implementation for CaretakerApp

This document outlines the OAuth implementation for the CaretakerApp, which enables sign-in with Google (and can be extended for other providers) while preserving the existing patient-caretaker linking functionality.

## Overview

The OAuth implementation includes:

- **Google Sign-In**: Users can sign in or register using their Google account
- **Account Linking**: Users with existing email/password accounts can link their Google account
- **Preserved Patient Linking**: Patient-caretaker relationships remain intact using Firebase UID as the patient ID
- **Unified Authentication**: All authentication flows use the same `FirebaseAuthManager` and call `UserManager.initializeCaretaker()`

## Key Components

### 1. FirebaseAuthManager.java

- **Location**: `app/src/main/java/com/mihir/alzheimerscaregiver/caretaker/auth/FirebaseAuthManager.java`
- **Purpose**: Centralized authentication management
- **Features**:
  - Email/password authentication
  - Google OAuth authentication
  - Account linking (`linkWithCredential`)
  - Automatic caretaker initialization after successful sign-in

### 2. Updated Activities

- **LoginActivity**: Added Google Sign-In button and account linking dialogs
- **RegisterActivity**: Added Google Sign-Up option
- **Both activities**: Handle account collision scenarios gracefully

### 3. OAuth Configuration Helper

- **Location**: `app/src/main/java/com/mihir/alzheimerscaregiver/caretaker/auth/OAuthConfigHelper.java`
- **Purpose**: Manages OAuth provider configurations and validation

### 4. UI Components

- **Google Sign-In buttons**: Added to both login and register layouts
- **Account linking dialog**: For handling existing accounts
- **Progress indicators**: Unified across all auth flows

## Setup Instructions

### Firebase Console Configuration

1. **Enable Google Sign-In Provider**:

   - Go to Firebase Console → Authentication → Sign-in method
   - Enable Google provider
   - Add your app's SHA-1 and SHA-256 certificates

2. **Configure OAuth Client**:

   - Go to Google Cloud Console
   - Enable Google Sign-In API
   - Create OAuth 2.0 client IDs for Android
   - Add package name: `com.mihir.alzheimerscaregiver.caretaker`
   - Add SHA-1/SHA-256 certificates

3. **Update google-services.json**:
   - Download the updated `google-services.json` file
   - Replace the existing file in `app/` directory

### Android Project Configuration

1. **Web Client ID**:

   - Update `res/values/strings.xml`
   - Replace `default_web_client_id` with actual web client ID from Firebase Console
   - Format: `YOUR_PROJECT_ID-XXXXXX.apps.googleusercontent.com`

2. **Dependencies** (already added):

   ```gradle
   implementation 'com.google.android.gms:play-services-auth:20.7.0'
   implementation 'com.google.firebase:firebase-auth'
   ```

3. **Permissions** (verify in AndroidManifest.xml):
   ```xml
   <uses-permission android:name="android.permission.INTERNET" />
   ```

## Patient-Caretaker Linking Preservation

The OAuth implementation preserves the existing patient-caretaker linking mechanism:

### How it works:

1. **Firebase UID as Patient ID**: The Firebase Authentication UID serves as the unique patient identifier
2. **Consistent Linking**: `PatientLinkActivity.createCaretakerPatientLink()` remains unchanged
3. **Database Structure**: Existing Firestore/Realtime Database structure is preserved
4. **User Initialization**: `UserManager.initializeCaretaker()` is called after all successful sign-ins

### Key Benefits:

- **No Migration Needed**: Existing patient-caretaker links continue to work
- **Unified User ID**: Whether signed in via email/password or OAuth, the Firebase UID remains the unique identifier
- **Database Compatibility**: All existing database queries and relationships remain functional

## Authentication Flows

### 1. New User with Google Sign-In

```
User clicks "Sign in with Google"
→ Google Sign-In flow
→ Firebase signInWithCredential
→ UserManager.initializeCaretaker
→ Navigate to PatientLinkActivity
```

### 2. Existing Email User Adding Google

```
User with email/password tries Google Sign-In
→ Account collision detected
→ Show linking dialog
→ User enters password
→ Sign in with email/password
→ Link Google credential
→ Navigate to main app
```

### 3. Existing Google User

```
User clicks "Sign in with Google"
→ Google Sign-In flow
→ Firebase signInWithCredential (existing account)
→ Navigate to PatientLinkActivity or MainActivity
```

## Error Handling

### Common Scenarios:

1. **Google Sign-In not configured**: Clear error message with setup instructions
2. **Network errors**: Graceful fallback with retry options
3. **Account collisions**: Guided linking process
4. **Cancelled sign-in**: User-friendly messaging

### Error Messages:

- All error messages are localized in `strings.xml`
- Clear instructions for user actions
- Fallback to email/password option always available

## Testing Checklist

### Prerequisites:

- [ ] Firebase project configured with Google Sign-In enabled
- [ ] SHA certificates added to Firebase project
- [ ] Web client ID updated in `strings.xml`
- [ ] App builds without errors

### Test Cases:

#### New User Registration:

- [ ] Google Sign-Up creates new Firebase account
- [ ] UserManager.initializeCaretaker called successfully
- [ ] User redirected to PatientLinkActivity
- [ ] Patient linking works with new UID

#### Existing User Sign-In:

- [ ] Email/password sign-in continues to work
- [ ] Google Sign-In works for existing Google users
- [ ] Patient linking preserved for existing users

#### Account Linking:

- [ ] Email user can link Google account
- [ ] Linking preserves existing patient relationships
- [ ] Both sign-in methods work after linking

#### Edge Cases:

- [ ] Network failure handling
- [ ] Google Sign-In cancellation
- [ ] Invalid credentials handling
- [ ] App works without Google Play Services

## Security Considerations

1. **Firebase Security Rules**: Ensure rules allow caretaker access to patient data
2. **Token Management**: Firebase handles OAuth tokens securely
3. **User Validation**: All sign-ins go through Firebase Authentication
4. **Data Isolation**: Patient-caretaker relationships maintained through secure UID linking

## Future Enhancements

### Additional Providers:

- Apple Sign-In (for iOS compatibility)
- Facebook Sign-In
- Microsoft Account

### Advanced Features:

- Multi-factor authentication
- Biometric authentication
- Single Sign-On (SSO) support

## Troubleshooting

### Common Issues:

1. **"OAuth not configured" error**:

   - Check `default_web_client_id` in strings.xml
   - Verify Firebase Console configuration
   - Ensure SHA certificates are correct

2. **Sign-in fails silently**:

   - Check network connection
   - Verify Firebase project setup
   - Check device Google Play Services

3. **Patient linking broken**:
   - Verify Firebase UID consistency
   - Check Firestore security rules
   - Ensure UserManager.initializeCaretaker completes

### Debug Steps:

1. Enable Firebase Auth debug logging
2. Check Firebase Console Authentication tab for user creation
3. Verify Firestore documents are created correctly
4. Test patient linking with both auth methods

## Contact

For issues or questions regarding the OAuth implementation:

- Check Firebase Console for authentication errors
- Review Android logs for detailed error messages
- Ensure all setup steps are completed correctly
