# Google Services (Firebase) Configuration

This file (`google-services.json.template`) is a template for the real Firebase configuration file.

## Setup Instructions

1. Download your actual `google-services.json` file from the Firebase Console
2. Replace this template file with the downloaded file
3. Keep the filename as `google-services.json`

## Security Note

The actual `google-services.json` file is excluded from version control (listed in .gitignore)
to prevent API keys and other sensitive information from being exposed in public repositories.

## Template Format

```json
{
  "project_info": {
    "project_number": "YOUR_PROJECT_NUMBER",
    "project_id": "YOUR_PROJECT_ID",
    "storage_bucket": "YOUR_STORAGE_BUCKET"
  },
  "client": [
    {
      "client_info": {
        "mobilesdk_app_id": "YOUR_MOBILESDK_APP_ID",
        "android_client_info": {
          "package_name": "com.mihir.alzheimerscaregiver"
        }
      },
      "oauth_client": [],
      "api_key": [
        {
          "current_key": "YOUR_API_KEY"
        }
      ],
      "services": {
        "appinvite_service": {
          "other_platform_oauth_client": []
        }
      }
    }
  ],
  "configuration_version": "1"
}
```
