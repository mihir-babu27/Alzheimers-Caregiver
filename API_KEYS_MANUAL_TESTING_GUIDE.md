# API Keys Manual Testing Guide

## 🧪 How to Test Each API Key Manually

### 1. Google Maps API Key Testing

**Location**: Use in any location-based feature in the app

**Test Steps**:

1. Launch the patient app
2. Go to location/tracking features
3. Check if maps load correctly
4. Verify location pins and markers appear

**Expected Result**: Maps should load without errors, location should be displayed

**Troubleshooting**: If maps show "This page can't load Google Maps correctly":

- Check if MAPS_API_KEY is correctly set in both apps
- Verify API key restrictions in Google Cloud Console
- Ensure Maps SDK for Android is enabled

### 2. Firebase API Testing

**Location**: Authentication, database operations, storage

**Test Steps**:

1. Try to sign up/sign in a new user
2. Create patient-caretaker link
3. Send a test notification
4. Upload an image or file

**Expected Result**: All Firebase operations should work smoothly

**Troubleshooting**: If Firebase operations fail:

- Check Firebase project ID matches in google-services.json
- Verify Firebase Authentication is enabled
- Check Firestore/Realtime Database rules

### 3. Gemini AI API Testing

**Location**: Chatbot features, conversational AI

**Test Steps**:

1. Open chatbot/conversation feature
2. Send a message to the AI
3. Check if AI responds appropriately
4. Test different conversation flows

**Expected Result**: AI should respond with contextual, helpful messages

**Troubleshooting**: If AI doesn't respond:

- Verify GEMINI_API_KEY is correct
- Check API quota in Google AI Studio
- Ensure Gemini API is enabled for your project

### 4. Hugging Face API Testing

**Location**: Image generation features

**Test Steps**:

1. Navigate to image generation feature
2. Enter a prompt for image generation
3. Wait for image generation to complete
4. Verify generated image quality

**Expected Result**: Images should generate based on prompts

**Troubleshooting**: If image generation fails:

- Check HUGGING_FACE_API_KEY is valid
- Verify you have sufficient Hugging Face quota
- Test with simpler prompts first

### 5. OAuth/Google Sign-In Testing (CaretakerApp)

**Location**: CaretakerApp login screen

**Test Steps**:

1. Open CaretakerApp
2. Tap "Sign in with Google"
3. Complete Google authentication flow
4. Verify successful sign-in

**Expected Result**: Should authenticate and redirect to main app

**Troubleshooting**: If OAuth fails:

- Check SHA-1/SHA-256 certificates in Firebase Console
- Verify OAuth client ID in strings.xml
- Ensure Google Sign-In is enabled in Firebase

## 🔍 Quick Validation Commands

### Check API Key Format:

```bash
# Google/Gemini API keys should start with "AIza"
grep "GOOGLE_API_KEY\|GEMINI_API_KEY" secure-keys/api-keys.properties

# Hugging Face keys should start with "hf_"
grep "HUGGING_FACE_API_KEY" secure-keys/api-keys.properties
```

### Test Network Connectivity:

```bash
# Test if APIs are reachable
curl -I https://www.googleapis.com
curl -I https://router.huggingface.co
```

### Validate Firebase Configuration:

```bash
# Check if project IDs match
grep "project_id" app/google-services.json
grep "FIREBASE_PROJECT_ID" secure-keys/api-keys.properties
```

## 📱 Device Testing Checklist

- [ ] Install patient app on device
- [ ] Install CaretakerApp on separate device
- [ ] Test Maps functionality
- [ ] Test Firebase synchronization between apps
- [ ] Test AI chatbot features
- [ ] Test image generation
- [ ] Test Google Sign-In on CaretakerApp
- [ ] Test notifications between apps
- [ ] Monitor for any API rate limiting

## 🚨 Common Issues and Solutions

### "Maps API Key Error"

- Add your package name to API key restrictions
- Enable Maps SDK for Android in Google Cloud Console

### "Firebase Permission Denied"

- Check Firestore security rules
- Verify user authentication status

### "Gemini API Rate Limit"

- Check quota usage in Google AI Studio
- Implement retry logic with exponential backoff

### "Hugging Face Model Loading Error"

- Switch to different model if current one is unavailable
- Check model availability status

### "OAuth Configuration Error"

- Regenerate SHA certificates if needed
- Verify package names match in all configurations

For detailed troubleshooting, check the specific error logs in Android Studio.
