# FCM Notification Configuration Guide

## Alzheimer's Caregiver App - Push Notifications

### 📱 **Notification Structure Overview**

The FCM notifications for the Alzheimer's Caregiver app use a comprehensive structure with dynamic content based on alert severity and context.

---

## 🎯 **Notification Titles**

### **High Priority Alerts (Safe Zone Exits)**

```
🚨 URGENT: Patient Safety Alert
```

### **Medium Priority Alerts (Safe Zone Activity)**

```
📍 Patient Location Update    (for exits)
✅ Safe Zone Activity        (for entries)
```

---

## 📝 **Notification Body Text**

### **High Severity Exit (Critical)**

```
⚠️ [Patient Name] has LEFT the [Safe Zone Name] safe zone. Please check their status immediately.
```

**Example:** `⚠️ John has LEFT the Home Safe Zone safe zone. Please check their status immediately.`

### **Normal Exit**

```
📤 [Patient Name] has left [Safe Zone Name]. They may be heading out.
```

**Example:** `📤 John has left Home Safe Zone. They may be heading out.`

### **Safe Entry**

```
🏠 [Patient Name] has safely entered [Safe Zone Name].
```

**Example:** `🏠 John has safely entered Home Safe Zone.`

---

## 🎨 **Visual Elements**

### **Notification Icons**

- **App Icon**: `ic_notification_location` (custom location pin with app branding)
- **Small Icon**: Appears in status bar (should be white/transparent with alpha channel)
- **Large Icon**: Patient avatar or app logo (optional)

### **Notification Colors**

- **High Severity**: `#FF4444` (Red) - Critical alerts
- **Normal Alerts**: `#4CAF50` (Green) - Safe zone activities
- **App Brand**: `#6366F1` (Primary blue) - General notifications

### **Large Images (Rich Notifications)**

```
High Severity:    urgent_alert_banner.png     (Red warning with location pin)
Patient Leaving:  patient_leaving_banner.png  (Person walking away from home)
Patient Safe:     patient_safe_banner.png     (Person at home with checkmark)
General:          location_update_banner.png  (Location pin with gentle colors)
```

**Image Specifications:**

- **Size**: 1200x600px (2:1 ratio)
- **Format**: PNG with transparency support
- **Content**: App branding + relevant icon + subtle background
- **Text Overlay**: None (text handled by notification title/body)

---

## 🔊 **Sound Configuration**

### **High Priority Alerts**

```json
"sound": "urgent_alert.mp3"
```

- **Duration**: 3-5 seconds
- **Style**: Attention-grabbing but not alarming (gentle urgency)
- **Volume**: Medium-high (respects device settings)

### **Normal Alerts**

```json
"sound": "gentle_chime.mp3"
```

- **Duration**: 1-2 seconds
- **Style**: Soft, pleasant notification tone
- **Volume**: Medium (respects device settings)

### **Custom Sound Files Location**

Place in CaretakerApp: `app/src/main/res/raw/`

- `urgent_alert.mp3`
- `gentle_chime.mp3`

---

## 🏷️ **App Branding & Identity**

### **App Name Display**

```
"app_name": "CareGuard"  // Or your preferred app name
```

### **Notification Channel Names**

```
High Priority: "Critical Patient Alerts"
Normal: "Location Updates"
General: "App Notifications"
```

### **Notification Tags (for Grouping)**

```
"tag": "geofence_alert_[patientId]"
```

This groups multiple alerts from the same patient together.

---

## ⚡ **Complete FCM Payload Example**

### **High Severity Alert (Patient Left Safe Zone)**

```json
{
  "to": "fcm_token_here",
  "notification": {
    "title": "🚨 URGENT: Patient Safety Alert",
    "body": "⚠️ John has LEFT the Home Safe Zone safe zone. Please check their status immediately.",
    "icon": "ic_notification_location",
    "image": "https://your-domain.com/urgent_alert_banner.png",
    "sound": "urgent_alert.mp3",
    "color": "#FF4444",
    "tag": "geofence_alert_patient123",
    "click_action": "OPEN_GEOFENCE_MANAGEMENT"
  },
  "data": {
    "alertType": "geofence_alert",
    "patientId": "patient123",
    "patientName": "John",
    "geofenceName": "Home Safe Zone",
    "transitionType": "EXIT",
    "severity": "high",
    "alertId": "alert456",
    "timestamp": "1640995200000"
  },
  "priority": "high",
  "time_to_live": 86400
}
```

### **Normal Alert (Patient Entered Safe Zone)**

```json
{
  "to": "fcm_token_here",
  "notification": {
    "title": "✅ Safe Zone Activity",
    "body": "🏠 John has safely entered Home Safe Zone.",
    "icon": "ic_notification_location",
    "image": "https://your-domain.com/patient_safe_banner.png",
    "sound": "gentle_chime.mp3",
    "color": "#4CAF50",
    "tag": "geofence_alert_patient123",
    "click_action": "OPEN_GEOFENCE_MANAGEMENT"
  },
  "data": {
    "alertType": "geofence_alert",
    "patientId": "patient123",
    "patientName": "John",
    "geofenceName": "Home Safe Zone",
    "transitionType": "ENTER",
    "severity": "medium",
    "alertId": "alert457",
    "timestamp": "1640995300000"
  },
  "priority": "normal",
  "time_to_live": 86400
}
```

---

## 🎯 **Action Buttons**

### **High Priority Alerts**

```json
"actions": [
  {
    "action": "VIEW_LOCATION",
    "title": "📍 View Location",
    "icon": "ic_location_on"
  },
  {
    "action": "ACKNOWLEDGE",
    "title": "✅ Acknowledge",
    "icon": "ic_check"
  }
]
```

### **Normal Alerts**

```json
"actions": [
  {
    "action": "VIEW_DETAILS",
    "title": "👁️ View Details",
    "icon": "ic_visibility"
  }
]
```

---

## 📁 **Required Resources**

### **CaretakerApp Resources to Add**

**Drawable Resources** (`res/drawable/`):

```
ic_notification_location.xml       // Notification icon (vector)
ic_location_on.xml                // Location action icon
ic_check.xml                      // Acknowledge action icon
ic_visibility.xml                 // View details icon
```

**Raw Resources** (`res/raw/`):

```
urgent_alert.mp3                  // High priority sound
gentle_chime.mp3                  // Normal priority sound
```

**String Resources** (`res/values/strings.xml`):

```xml
<string name="notification_channel_critical">Critical Patient Alerts</string>
<string name="notification_channel_location">Location Updates</string>
<string name="notification_channel_general">App Notifications</string>
```

**Color Resources** (`res/values/colors.xml`):

```xml
<color name="notification_urgent">#FF4444</color>
<color name="notification_safe">#4CAF50</color>
<color name="notification_primary">#6366F1</color>
```

---

## 🔧 **Implementation Checklist**

- [ ] Create custom notification icons (vector drawables)
- [ ] Add custom sound files to `res/raw/`
- [ ] Host notification banner images on Firebase Storage or CDN
- [ ] Update FCMNotificationSender with image URLs
- [ ] Test notifications on different Android versions
- [ ] Verify notification channel configuration
- [ ] Test action button functionality
- [ ] Ensure proper notification grouping
- [ ] Test with Do Not Disturb settings
- [ ] Validate notification appearance in collapsed/expanded states

---

## 📱 **Testing Notifications**

Use Firebase Console's "Cloud Messaging" section to test:

1. **Compose Notification**
2. **Target**: Select specific device token
3. **Notification**: Use titles/bodies from this guide
4. **Additional Options**:
   - Add image URL
   - Set sound to custom
   - Configure click action
   - Add custom data payload

This comprehensive structure ensures professional, contextual, and helpful notifications for Alzheimer's caregivers! 🎯
