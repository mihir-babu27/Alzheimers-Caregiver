package com.mihir.alzheimerscaregiver.entities;

import java.util.HashMap;
import java.util.Map;

/**
 * GeofenceDefinition - Entity class for geofence data
 * Used for both CaretakerApp geofence creation and Patient App geofence registration
 */
public class GeofenceDefinition {
    
    public static final String TYPE_SAFE_ZONE = "SAFE_ZONE";
    public static final String DEFAULT_COLOR = "#4CAF50"; // Green for safe zones
    public static final float MIN_RADIUS = 50.0f;  // 50 meters minimum
    public static final float MAX_RADIUS = 500.0f; // 500 meters maximum
    public static final float DEFAULT_RADIUS = 150.0f; // 150 meters default
    
    public String id;
    public String label;
    public String description;
    public double lat;
    public double lng;
    public float radius;
    public String type;
    public String color;
    public boolean active;
    public long createdAt;
    public long updatedAt;
    public String createdBy;
    
    // Default constructor for Firebase
    public GeofenceDefinition() {
        this.type = TYPE_SAFE_ZONE;
        this.color = DEFAULT_COLOR;
        this.active = true;
        this.radius = DEFAULT_RADIUS;
    }
    
    // Full constructor
    public GeofenceDefinition(String id, String label, String description, 
                             double lat, double lng, float radius, String createdBy) {
        this.id = id;
        this.label = label;
        this.description = description;
        this.lat = lat;
        this.lng = lng;
        this.radius = Math.max(MIN_RADIUS, Math.min(radius, MAX_RADIUS));
        this.type = TYPE_SAFE_ZONE;
        this.color = DEFAULT_COLOR;
        this.active = true;
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
        this.createdBy = createdBy;
    }
    
    // Constructor from Firebase data
    public static GeofenceDefinition fromFirebase(Map<String, Object> data) {
        GeofenceDefinition geofence = new GeofenceDefinition();
        
        if (data != null) {
            geofence.id = (String) data.get("id");
            geofence.label = (String) data.get("label");
            geofence.description = (String) data.get("description");
            
            // Handle numeric values safely
            Object latObj = data.get("lat");
            if (latObj instanceof Number) {
                geofence.lat = ((Number) latObj).doubleValue();
            }
            
            Object lngObj = data.get("lng");
            if (lngObj instanceof Number) {
                geofence.lng = ((Number) lngObj).doubleValue();
            }
            
            Object radiusObj = data.get("radius");
            if (radiusObj instanceof Number) {
                geofence.radius = ((Number) radiusObj).floatValue();
            }
            
            geofence.type = (String) data.getOrDefault("type", TYPE_SAFE_ZONE);
            geofence.color = (String) data.getOrDefault("color", DEFAULT_COLOR);
            
            Object activeObj = data.get("active");
            if (activeObj instanceof Boolean) {
                geofence.active = (Boolean) activeObj;
            }
            
            Object createdAtObj = data.get("createdAt");
            if (createdAtObj instanceof Number) {
                geofence.createdAt = ((Number) createdAtObj).longValue();
            }
            
            Object updatedAtObj = data.get("updatedAt");
            if (updatedAtObj instanceof Number) {
                geofence.updatedAt = ((Number) updatedAtObj).longValue();
            }
            
            geofence.createdBy = (String) data.get("createdBy");
        }
        
        return geofence;
    }
    
    // Convert to Firebase-compatible map
    public Map<String, Object> toFirebaseMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("id", id);
        map.put("label", label);
        map.put("description", description);
        map.put("lat", lat);
        map.put("lng", lng);
        map.put("radius", radius);
        map.put("type", type);
        map.put("color", color);
        map.put("active", active);
        map.put("createdAt", createdAt);
        map.put("updatedAt", updatedAt);
        map.put("createdBy", createdBy);
        return map;
    }
    
    // Validation methods
    public boolean isValid() {
        return id != null && !id.isEmpty() &&
               label != null && !label.trim().isEmpty() &&
               lat >= -90.0 && lat <= 90.0 &&
               lng >= -180.0 && lng <= 180.0 &&
               radius >= MIN_RADIUS && radius <= MAX_RADIUS;
    }
    
    public String getValidationError() {
        if (id == null || id.isEmpty()) return "ID is required";
        if (label == null || label.trim().isEmpty()) return "Label is required";
        if (lat < -90.0 || lat > 90.0) return "Invalid latitude";
        if (lng < -180.0 || lng > 180.0) return "Invalid longitude";
        if (radius < MIN_RADIUS) return "Radius must be at least " + MIN_RADIUS + "m";
        if (radius > MAX_RADIUS) return "Radius cannot exceed " + MAX_RADIUS + "m";
        return null;
    }
    
    // Helper methods
    public String getDisplayLabel() {
        return label != null ? label : "Unnamed Zone";
    }
    
    public String getDisplayDescription() {
        if (description != null && !description.trim().isEmpty()) {
            return description;
        }
        return "Safe Zone - Radius: " + (int)radius + "m";
    }
    
    @Override
    public String toString() {
        return "GeofenceDefinition{" +
                "id='" + id + '\'' +
                ", label='" + label + '\'' +
                ", lat=" + lat +
                ", lng=" + lng +
                ", radius=" + radius +
                ", active=" + active +
                '}';
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        GeofenceDefinition that = (GeofenceDefinition) obj;
        return id != null ? id.equals(that.id) : that.id == null;
    }
    
    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }
}