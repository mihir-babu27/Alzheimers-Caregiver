package com.mihir.alzheimerscaregiver.entities;

/**
 * Entity representing a patient's location data for Firebase Realtime Database
 * Used for secure real-time location tracking between Patient and Caretaker apps
 */
public class LocationEntity {
    public String patientId;
    public double latitude;
    public double longitude;
    public long timestamp;
    public float accuracy; // In meters
    public String provider; // GPS, NETWORK, etc.
    public boolean isMockLocation; // Security: detect mock locations
    
    // Default constructor required by Firebase
    public LocationEntity() {}
    
    public LocationEntity(String patientId, double latitude, double longitude, long timestamp) {
        this.patientId = patientId;
        this.latitude = latitude;
        this.longitude = longitude;
        this.timestamp = timestamp;
    }
    
    public LocationEntity(String patientId, double latitude, double longitude, long timestamp, 
                         float accuracy, String provider, boolean isMockLocation) {
        this.patientId = patientId;
        this.latitude = latitude;
        this.longitude = longitude;
        this.timestamp = timestamp;
        this.accuracy = accuracy;
        this.provider = provider;
        this.isMockLocation = isMockLocation;
    }
    
    @Override
    public String toString() {
        return "LocationEntity{" +
                "patientId='" + patientId + '\'' +
                ", lat=" + latitude +
                ", lng=" + longitude +
                ", timestamp=" + timestamp +
                ", accuracy=" + accuracy +
                ", provider='" + provider + '\'' +
                ", isMock=" + isMockLocation +
                '}';
    }
}