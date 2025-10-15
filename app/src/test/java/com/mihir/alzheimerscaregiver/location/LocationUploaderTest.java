package com.mihir.alzheimerscaregiver.location;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.mihir.alzheimerscaregiver.entities.LocationEntity;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Unit tests for LocationUploader Firebase database writes
 */
@RunWith(RobolectricTestRunner.class)
public class LocationUploaderTest {

    @Mock
    private FirebaseDatabase mockDatabase;
    
    @Mock
    private DatabaseReference mockLocationRef;
    
    @Mock
    private DatabaseReference mockPatientRef;
    
    @Mock
    private DatabaseReference mockSpecificLocationRef;

    private LocationUploader locationUploader;
    private LocationEntity testLocation;
    
    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        
        // Setup mock Firebase database structure
        when(mockDatabase.getReference("locations")).thenReturn(mockLocationRef);
        when(mockLocationRef.child("test-patient-123")).thenReturn(mockPatientRef);
        when(mockPatientRef.push()).thenReturn(mockSpecificLocationRef);
        
        // Create test location entity
        testLocation = new LocationEntity(
                "test-patient-123",
                40.7128, // NYC coordinates
                -74.0060,
                System.currentTimeMillis(),
                85.5f // accuracy
        );
        
        locationUploader = new LocationUploader(mockDatabase);
    }
    
    @Test
    public void testUploadLocationSuccess() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        final boolean[] successCalled = {false};
        
        // Mock successful database write
        doAnswer(invocation -> {
            LocationUploader.LocationUploadCallback callback = 
                (LocationUploader.LocationUploadCallback) invocation.getArguments()[1];
            callback.onSuccess();
            return null;
        }).when(mockSpecificLocationRef).setValue(any(LocationEntity.class), any(LocationUploader.LocationUploadCallback.class));
        
        // Test upload
        locationUploader.uploadLocation(testLocation, new LocationUploader.LocationUploadCallback() {
            @Override
            public void onSuccess() {
                successCalled[0] = true;
                latch.countDown();
            }
            
            @Override
            public void onFailure(String error) {
                fail("Upload should succeed but failed with: " + error);
                latch.countDown();
            }
        });
        
        // Wait for async operation
        assertTrue("Upload callback should complete within 5 seconds", 
                latch.await(5, TimeUnit.SECONDS));
        assertTrue("Success callback should be called", successCalled[0]);
        
        // Verify Firebase calls
        verify(mockDatabase).getReference("locations");
        verify(mockLocationRef).child("test-patient-123");
        verify(mockPatientRef).push();
        verify(mockSpecificLocationRef).setValue(eq(testLocation), any(LocationUploader.LocationUploadCallback.class));
    }
    
    @Test
    public void testUploadLocationFailure() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        final String[] errorMessage = {null};
        
        // Mock database write failure
        doAnswer(invocation -> {
            LocationUploader.LocationUploadCallback callback = 
                (LocationUploader.LocationUploadCallback) invocation.getArguments()[1];
            callback.onFailure("Network error");
            return null;
        }).when(mockSpecificLocationRef).setValue(any(LocationEntity.class), any(LocationUploader.LocationUploadCallback.class));
        
        // Test upload failure
        locationUploader.uploadLocation(testLocation, new LocationUploader.LocationUploadCallback() {
            @Override
            public void onSuccess() {
                fail("Upload should fail but succeeded");
                latch.countDown();
            }
            
            @Override
            public void onFailure(String error) {
                errorMessage[0] = error;
                latch.countDown();
            }
        });
        
        // Wait for async operation
        assertTrue("Upload callback should complete within 5 seconds", 
                latch.await(5, TimeUnit.SECONDS));
        assertEquals("Error message should match", "Network error", errorMessage[0]);
        
        // Verify Firebase calls
        verify(mockSpecificLocationRef).setValue(eq(testLocation), any(LocationUploader.LocationUploadCallback.class));
    }
    
    @Test
    public void testUploadLocationWithNullPatientId() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        final String[] errorMessage = {null};
        
        // Create location with null patient ID
        LocationEntity invalidLocation = new LocationEntity(
                null,
                40.7128,
                -74.0060,
                System.currentTimeMillis(),
                85.5f
        );
        
        // Test upload with invalid data
        locationUploader.uploadLocation(invalidLocation, new LocationUploader.LocationUploadCallback() {
            @Override
            public void onSuccess() {
                fail("Upload should fail with null patient ID");
                latch.countDown();
            }
            
            @Override
            public void onFailure(String error) {
                errorMessage[0] = error;
                latch.countDown();
            }
        });
        
        // Wait for async operation
        assertTrue("Upload callback should complete within 5 seconds", 
                latch.await(5, TimeUnit.SECONDS));
        assertNotNull("Error message should not be null", errorMessage[0]);
        assertTrue("Error should mention patient ID", errorMessage[0].contains("patient"));
    }
    
    @Test
    public void testUploadLocationWithInvalidCoordinates() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        final String[] errorMessage = {null};
        
        // Create location with invalid coordinates
        LocationEntity invalidLocation = new LocationEntity(
                "test-patient-123",
                200.0, // Invalid latitude (>90)
                -200.0, // Invalid longitude (<-180)
                System.currentTimeMillis(),
                85.5f
        );
        
        // Test upload with invalid coordinates
        locationUploader.uploadLocation(invalidLocation, new LocationUploader.LocationUploadCallback() {
            @Override
            public void onSuccess() {
                fail("Upload should fail with invalid coordinates");
                latch.countDown();
            }
            
            @Override
            public void onFailure(String error) {
                errorMessage[0] = error;
                latch.countDown();
            }
        });
        
        // Wait for async operation
        assertTrue("Upload callback should complete within 5 seconds", 
                latch.await(5, TimeUnit.SECONDS));
        assertNotNull("Error message should not be null", errorMessage[0]);
        assertTrue("Error should mention coordinates", 
                errorMessage[0].toLowerCase().contains("coordinate") || 
                errorMessage[0].toLowerCase().contains("invalid"));
    }
    
    @Test
    public void testRetryMechanism() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        final int[] retryCount = {0};
        
        // Mock Firebase reference for retry testing
        DatabaseReference mockRetryRef = mock(DatabaseReference.class);
        when(mockPatientRef.push()).thenReturn(mockRetryRef);
        
        // Mock failure then success
        doAnswer(invocation -> {
            LocationUploader.LocationUploadCallback callback = 
                (LocationUploader.LocationUploadCallback) invocation.getArguments()[1];
            retryCount[0]++;
            if (retryCount[0] < 3) {
                callback.onFailure("Temporary network error");
            } else {
                callback.onSuccess();
            }
            return null;
        }).when(mockRetryRef).setValue(any(LocationEntity.class), any(LocationUploader.LocationUploadCallback.class));
        
        // Test upload with retry
        locationUploader.uploadLocationWithRetry(testLocation, 3, new LocationUploader.LocationUploadCallback() {
            @Override
            public void onSuccess() {
                latch.countDown();
            }
            
            @Override
            public void onFailure(String error) {
                fail("Upload should succeed after retry: " + error);
                latch.countDown();
            }
        });
        
        // Wait for async operation with retries
        assertTrue("Upload with retry should complete within 10 seconds", 
                latch.await(10, TimeUnit.SECONDS));
        assertEquals("Should retry 3 times", 3, retryCount[0]);
    }
    
    @Test
    public void testDatabaseStructure() {
        // Test that the correct database path is used
        locationUploader.uploadLocation(testLocation, new LocationUploader.LocationUploadCallback() {
            @Override
            public void onSuccess() {}
            
            @Override
            public void onFailure(String error) {}
        });
        
        // Verify database path structure: /locations/{patientId}/{pushId}
        verify(mockDatabase).getReference("locations");
        verify(mockLocationRef).child("test-patient-123");
        verify(mockPatientRef).push();
    }
    
    @Test
    public void testLocationEntitySerialization() {
        // Verify that LocationEntity has proper structure for Firebase
        assertNotNull("Patient ID should not be null", testLocation.getPatientId());
        assertTrue("Latitude should be valid", 
                testLocation.getLatitude() >= -90 && testLocation.getLatitude() <= 90);
        assertTrue("Longitude should be valid", 
                testLocation.getLongitude() >= -180 && testLocation.getLongitude() <= 180);
        assertTrue("Timestamp should be positive", testLocation.getTimestamp() > 0);
        assertTrue("Accuracy should be positive", testLocation.getAccuracy() > 0);
    }
    
    @Test
    public void testConcurrentUploads() throws InterruptedException {
        int numUploads = 5;
        CountDownLatch latch = new CountDownLatch(numUploads);
        
        // Mock successful uploads
        doAnswer(invocation -> {
            LocationUploader.LocationUploadCallback callback = 
                (LocationUploader.LocationUploadCallback) invocation.getArguments()[1];
            callback.onSuccess();
            return null;
        }).when(mockSpecificLocationRef).setValue(any(LocationEntity.class), any(LocationUploader.LocationUploadCallback.class));
        
        // Start multiple concurrent uploads
        for (int i = 0; i < numUploads; i++) {
            final int uploadId = i;
            LocationEntity location = new LocationEntity(
                    "patient-" + uploadId,
                    40.7128 + uploadId * 0.001, // Slightly different coordinates
                    -74.0060 + uploadId * 0.001,
                    System.currentTimeMillis(),
                    85.5f
            );
            
            new Thread(() -> locationUploader.uploadLocation(location, new LocationUploader.LocationUploadCallback() {
                @Override
                public void onSuccess() {
                    latch.countDown();
                }
                
                @Override
                public void onFailure(String error) {
                    fail("Concurrent upload " + uploadId + " failed: " + error);
                    latch.countDown();
                }
            })).start();
        }
        
        // Wait for all uploads to complete
        assertTrue("All concurrent uploads should complete within 10 seconds", 
                latch.await(10, TimeUnit.SECONDS));
    }
}