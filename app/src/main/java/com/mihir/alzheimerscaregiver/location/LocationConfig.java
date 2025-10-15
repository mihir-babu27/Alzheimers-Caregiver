package com.mihir.alzheimerscaregiver.location;

/**
 * Configuration constants for location tracking system
 * Based on best practices for battery efficiency and data accuracy
 */
public final class LocationConfig {
    
    // Prevent instantiation
    private LocationConfig() {}
    
    // ============================================================================
    // LOCATION UPDATE INTERVALS (in milliseconds)
    // ============================================================================
    
    /**
     * Minimum interval between Firebase uploads (5 minutes)
     * This is enforced regardless of user preferences for battery efficiency
     */
    public static final long MIN_UPLOAD_INTERVAL_MS = 5 * 60 * 1000;
    
    /**
     * Default location request interval (5 minutes)
     * Used when user hasn't specified a preference
     */
    public static final long DEFAULT_LOCATION_INTERVAL_MS = 5 * 60 * 1000;
    
    /**
     * Fastest allowed location request interval (2 minutes)
     * Android location system fastest update rate
     */
    public static final long FASTEST_LOCATION_INTERVAL_MS = 2 * 60 * 1000;
    
    // ============================================================================
    // LOCATION ACCURACY AND DISPLACEMENT
    // ============================================================================
    
    /**
     * Minimum displacement required to trigger location upload (25 meters)
     * Helps reduce battery usage and unnecessary updates when stationary
     */
    public static final float SMALLEST_DISPLACEMENT_METERS = 25.0f;
    
    /**
     * Default GPS accuracy threshold for accepting location readings
     * Locations with accuracy worse than this may be rejected
     */
    public static final float MAX_ACCURACY_METERS = 100.0f;
    
    // ============================================================================
    // DATA RETENTION AND HISTORY
    // ============================================================================
    
    /**
     * Maximum number of location points to retain per day (200 points)
     * With 5-minute intervals, this covers ~16.7 hours of tracking per day
     */
    public static final int HISTORY_RETENTION_PER_DAY = 200;
    
    /**
     * Time threshold for considering location data "stale" (15 minutes)
     * Used by caretaker app to show data freshness indicators
     */
    public static final long STALE_THRESHOLD_MS = 15 * 60 * 1000;
    
    /**
     * Maximum age for location history queries (30 days)
     * Older data should be archived or deleted
     */
    public static final long MAX_HISTORY_AGE_MS = 30L * 24 * 60 * 60 * 1000;
    
    // ============================================================================
    // NETWORK AND RETRY CONFIGURATION
    // ============================================================================
    
    /**
     * Initial retry delay for failed uploads (1 second)
     * Used with exponential backoff for network failures
     */
    public static final long INITIAL_RETRY_DELAY_MS = 1000;
    
    /**
     * Maximum retry delay for failed uploads (5 minutes)
     * Prevents excessive retry intervals during extended outages
     */
    public static final long MAX_RETRY_DELAY_MS = 5 * 60 * 1000;
    
    /**
     * Maximum number of retry attempts for failed uploads
     * After this, the upload is abandoned
     */
    public static final int MAX_RETRY_ATTEMPTS = 3;
    
    /**
     * Network timeout for Firebase operations (30 seconds)
     * Prevents hanging on slow connections
     */
    public static final long NETWORK_TIMEOUT_MS = 30 * 1000;
    
    // ============================================================================
    // SERVICE AND NOTIFICATION CONFIGURATION
    // ============================================================================
    
    /**
     * Notification channel ID for foreground service
     */
    public static final String NOTIFICATION_CHANNEL_ID = "location_tracking";
    
    /**
     * Foreground service notification ID
     */
    public static final int FOREGROUND_NOTIFICATION_ID = 1001;
    
    /**
     * Delay before restarting service after boot (5 seconds)
     * Allows system to fully initialize before starting location tracking
     */
    public static final long BOOT_RESTART_DELAY_MS = 5 * 1000;
    
    // ============================================================================
    // FIREBASE DATABASE PATHS
    // ============================================================================
    
    /**
     * Firebase Realtime Database path for location data
     */
    public static final String FIREBASE_LOCATIONS_PATH = "locations";
    
    /**
     * Firebase Realtime Database path for sharing preferences
     */
    public static final String FIREBASE_SHARING_PATH = "sharing_preferences";
    
    /**
     * Firebase Realtime Database path for geofences
     */
    public static final String FIREBASE_GEOFENCES_PATH = "geofences";
    
    // ============================================================================
    // BATTERY OPTIMIZATION
    // ============================================================================
    
    /**
     * Location request priority for balanced power/accuracy
     * Using PRIORITY_BALANCED_POWER_ACCURACY as per best practices
     */
    public static final int LOCATION_REQUEST_PRIORITY = 102; // PRIORITY_BALANCED_POWER_ACCURACY
    
    /**
     * Maximum wait time for location requests (10 minutes)
     * If no location received within this time, request times out
     */
    public static final long LOCATION_REQUEST_MAX_WAIT_TIME_MS = 10 * 60 * 1000;
    
    // ============================================================================
    // VALIDATION CONSTANTS
    // ============================================================================
    
    /**
     * Valid latitude range: -90 to +90 degrees
     */
    public static final double MIN_LATITUDE = -90.0;
    public static final double MAX_LATITUDE = 90.0;
    
    /**
     * Valid longitude range: -180 to +180 degrees  
     */
    public static final double MIN_LONGITUDE = -180.0;
    public static final double MAX_LONGITUDE = 180.0;
    
    /**
     * Minimum timestamp value (January 1, 2020)
     * Used to validate location timestamps
     */
    public static final long MIN_TIMESTAMP = 1577836800000L; // 2020-01-01 00:00:00 UTC
    
    // ============================================================================
    // DEBUG AND TESTING
    // ============================================================================
    
    /**
     * Enable debug logging for location tracking
     * Should be false for production builds
     */
    public static final boolean DEBUG_ENABLED = true; // TODO: Set to false for production
    
    /**
     * Test mode flag for automated testing and emulator use
     * Enables additional logging, shorter intervals, and no displacement filtering
     */
    public static final boolean TEST_MODE = true; // Set to true for testing/emulator
    
    /**
     * Test mode upload interval (10 seconds)
     * Used only when TEST_MODE is enabled for faster testing
     */
    public static final long TEST_UPLOAD_INTERVAL_MS = 10 * 1000;
    
    /**
     * Test mode displacement threshold (5 meters)
     * Used only when TEST_MODE is enabled
     */
    public static final float TEST_DISPLACEMENT_METERS = 5.0f;
    
    // ============================================================================
    // HELPER METHODS
    // ============================================================================
    
    /**
     * Get the effective upload interval considering user preferences and minimum limits
     * @param userPreferenceMs User's preferred interval in milliseconds
     * @return Effective interval (never less than MIN_UPLOAD_INTERVAL_MS)
     */
    public static long getEffectiveUploadInterval(long userPreferenceMs) {
        if (TEST_MODE) {
            return TEST_UPLOAD_INTERVAL_MS;
        }
        return Math.max(userPreferenceMs, MIN_UPLOAD_INTERVAL_MS);
    }
    
    /**
     * Get the effective displacement threshold considering test mode
     * @return Displacement threshold in meters (0 in test mode for immediate updates)
     */
    public static float getEffectiveDisplacement() {
        return TEST_MODE ? 0.0f : SMALLEST_DISPLACEMENT_METERS; // 0 meters = no displacement filter in test mode
    }
    
    /**
     * Validate latitude coordinate
     * @param latitude Latitude value to validate
     * @return True if latitude is valid
     */
    public static boolean isValidLatitude(double latitude) {
        return latitude >= MIN_LATITUDE && latitude <= MAX_LATITUDE;
    }
    
    /**
     * Validate longitude coordinate
     * @param longitude Longitude value to validate  
     * @return True if longitude is valid
     */
    public static boolean isValidLongitude(double longitude) {
        return longitude >= MIN_LONGITUDE && longitude <= MAX_LONGITUDE;
    }
    
    /**
     * Validate timestamp
     * @param timestamp Timestamp to validate
     * @return True if timestamp is valid
     */
    public static boolean isValidTimestamp(long timestamp) {
        long currentTime = System.currentTimeMillis();
        return timestamp >= MIN_TIMESTAMP && timestamp <= currentTime + 60000; // Allow 1 minute future
    }
    
    /**
     * Calculate exponential backoff delay
     * @param attempt Attempt number (starting from 0)
     * @return Delay in milliseconds
     */
    public static long calculateRetryDelay(int attempt) {
        long delay = INITIAL_RETRY_DELAY_MS * (1L << attempt); // 2^attempt
        return Math.min(delay, MAX_RETRY_DELAY_MS);
    }
    
    /**
     * Check if location data is considered stale
     * @param timestamp Location timestamp
     * @return True if location is stale
     */
    public static boolean isLocationStale(long timestamp) {
        return (System.currentTimeMillis() - timestamp) > STALE_THRESHOLD_MS;
    }
}