# FLUX.1-dev API Fixes - October 6, 2025

## Issues Fixed

### 1. API Parameter Error ✅

**Problem**: FLUX.1-dev API was returning 400 error with message:

```
FluxPipeline.__call__() got an unexpected keyword argument 'scheduler'
```

**Root Cause**: The `scheduler` parameter is not supported by the FLUX.1-dev API endpoint.

**Solution**: Removed the `scheduler` parameter from the API payload in `ImageGenerationManager.java`:

```java
// Before (causing error):
parameters.put("scheduler", "DPMSolverMultistepScheduler");

// After (fixed):
// Note: scheduler parameter not supported by FLUX.1-dev API
```

### 2. Auto-Generation Disabled ✅

**Problem**: App was automatically generating photos when opening the stories page, instead of waiting for user to click "Generate Photos" button.

**Root Cause**: In `StoryGenerationActivity.java`, the `displayStory()` method automatically called `generateIllustrationScene()` for stories without illustrations.

**Solution**: Modified the logic to only display existing illustrations and hide the illustration card when no image is available:

```java
// Before (auto-generating):
if (story.getIllustrationUrl() == null || story.getIllustrationUrl().isEmpty()) {
    generateIllustrationScene(); // This was causing auto-generation
}

// After (manual only):
if (story.getIllustrationUrl() != null && !story.getIllustrationUrl().isEmpty()) {
    displayIllustration(story.getIllustrationUrl(), story.getIllustrationDescription());
} else {
    illustrationCard.setVisibility(View.GONE);
}
```

## Current Behavior

1. **Stories Page Load**: No automatic image generation occurs
2. **Generate Photos Button**: Only generates images when user explicitly clicks the button
3. **API Calls**: FLUX.1-dev API calls now work without scheduler parameter errors
4. **Image Display**: Existing illustrations are shown, new images only generated on demand

## Expected User Experience

1. Open stories page → see story text without automatic image generation
2. Click "🎨 Generate Photos" button → FLUX.1-dev starts generating high-quality 1024x1024 image
3. Wait 15-45 seconds → image appears with therapeutic scene based on patient profile
4. No more "image format not supported" errors
5. No more 400 API errors from scheduler parameter

## Technical Details

- **API URL**: https://api-inference.huggingface.co/models/black-forest-labs/FLUX.1-dev
- **Parameters**: prompt, negative_prompt, num_inference_steps (20), guidance_scale (3.5), width (1024), height (1024)
- **Quality**: High-resolution therapeutic images with cultural context
- **Generation Time**: 15-45 seconds per image
- **Caching**: 7-day local cache with MD5 keys

## Files Modified

1. `app/src/main/java/com/mihir/alzheimerscaregiver/utils/ImageGenerationManager.java`
   - Removed scheduler parameter from API payload
2. `app/src/main/java/com/mihir/alzheimerscaregiver/reminiscence/StoryGenerationActivity.java`
   - Disabled automatic image generation in displayStory method
   - Images now only generate when user clicks Generate Photos button

## Build Status

✅ BUILD SUCCESSFUL - All compilation errors resolved
