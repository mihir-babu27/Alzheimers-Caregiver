// Add this method to test FCM in your MainActivity or RemindersActivity
private void testFCMNotification() {
    try {
        // Get current user info
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) {
            Log.e("FCM_TEST", "No authenticated user");
            return;
        }
        
        String patientId = auth.getCurrentUser().getUid();
        String patientName = auth.getCurrentUser().getDisplayName();
        if (patientName == null) patientName = "Test Patient";
        
        // Send test notification
        FCMNotificationSender fcmSender = new FCMNotificationSender(this);
        fcmSender.sendMissedMedicationAlert(patientId, patientName, "Test Medicine", "12:00 PM");
        
        Log.d("FCM_TEST", "Test FCM notification sent for patient: " + patientName);
        Toast.makeText(this, "Test FCM notification sent", Toast.LENGTH_LONG).show();
        
    } catch (Exception e) {
        Log.e("FCM_TEST", "Error sending test FCM notification", e);
        Toast.makeText(this, "FCM test failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
    }
}

// Call this method from a button or menu item
// testFCMNotification();