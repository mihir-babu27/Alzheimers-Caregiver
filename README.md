# Alzheimer's Caregiver App

## Security Setup for API Keys

This project uses Firebase services that require API keys. To protect these keys when pushing to public repositories, follow these steps:

### First Time Setup

1. Create a `secure-keys/api-keys.properties` file with your API keys:

   ```
   FIREBASE_API_KEY=your_firebase_api_key_here
   ```

2. Make sure you have the Firebase configuration file:
   - Place your `google-services.json` file in the `app/` directory
   - This file is not tracked by Git to protect your API keys

### Setting up a New Development Environment

When cloning this repository for the first time:

1. Copy `secure-keys/api-keys.properties.template` to `secure-keys/api-keys.properties`
2. Fill in the proper API key values in `api-keys.properties`
3. Get the `google-services.json` file from a secure location (e.g., Firebase Console) and place it in the `app/` directory

### How the API Keys are Protected

- The real API keys file (`api-keys.properties`) is excluded from Git in `.gitignore`
- The `google-services.json` file is also excluded from Git
- API keys are loaded during build time and stored in BuildConfig fields
- The `SecureKeys` utility class provides safe access to these keys in code
- The `SecureFirebaseInitializer` class initializes Firebase securely using these keys

### Development Workflow

- Always use `SecureKeys.getFirebaseApiKey()` instead of hardcoding the API key values
- Do not commit any files containing actual API keys
- When adding new API keys, update both the template file and documentation

For more information, contact the project maintainers.
