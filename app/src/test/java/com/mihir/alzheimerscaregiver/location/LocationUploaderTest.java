package com.mihir.alzheimerscaregiver.location;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import android.location.Location;

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
import org.robolectric.annotation.Config;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Unit tests for LocationUploader Firebase database writes
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
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
    private Location testLocation;
    
    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        
        // Setup mock Firebase database structure
        when(mockDatabase.getReference("locations")).thenReturn(mockLocationRef);
        when(mockLocationRef.child("test-patient-123")).thenReturn(mockPatientRef);
        when(mockPatientRef.push()).thenReturn(mockSpecificLocationRef);
        
        // Create mock Location object
        testLocation = mock(Location.class);
        when(testLocation.getLatitude()).thenReturn(40.7128);
        when(testLocation.getLongitude()).thenReturn(-74.0060);
        when(testLocation.getAccuracy()).thenReturn(85.5f);
        when(testLocation.getProvider()).thenReturn("GPS");
        when(testLocation.isFromMockProvider()).thenReturn(false);
        
        // Create LocationUploader for testing
        locationUploader = new LocationUploader();
    }
    
    @Test
    public void testUploadLocationSuccess() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        final boolean[] successCalled = {false};
        
        // Mock successful database write
        doAnswer(invocation -> {
            LocationUploader.UploadCallback callback = 
                (LocationUploader.UploadCallback) invocation.getArguments()[1];
            callback.onSuccess();
            return null;
        }).when(mockSpecificLocationRef).setValue(any(LocationEntity.class), any(LocationUploader.UploadCallback.class));
        
        // Test upload
        locationUploader.uploadCurrentLocation("test-patient-123", testLocation, new LocationUploader.UploadCallback() {
            @Override
            public void onSuccess() {
                successCalled[0] = true;
                latch.countDown();
            }
            
            @Override
            public void onError(String error) {
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
        verify(mockSpecificLocationRef).setValue(eq(testLocation), any(LocationUploader.UploadCallback.class));
    }
    
    @Test
    public void testUploadLocationError() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        final String[] errorMessage = {null};
        
        // Mock database write failure
        doAnswer(invocation -> {
            LocationUploader.UploadCallback callback = 
                (LocationUploader.UploadCallback) invocation.getArguments()[1];
            callback.onError("Network error");
            return null;
        }).when(mockSpecificLocationRef).setValue(any(LocationEntity.class), any(LocationUploader.UploadCallback.class));
        
        // Test upload failure
        locationUploader.uploadCurrentLocation("test-patient-123", testLocation, new LocationUploader.UploadCallback() {
            @Override
            public void onSuccess() {
                fail("Upload should fail but succeeded");
                latch.countDown();
            }
            
            @Override
            public void onError(String error) {
                errorMessage[0] = error;
                latch.countDown();
            }
        });
        
        // Wait for async operation
        assertTrue("Upload callback should complete within 5 seconds", 
                latch.await(5, TimeUnit.SECONDS));
        assertEquals("Error message should match", "Network error", errorMessage[0]);
        
        // Verify Firebase calls
        verify(mockSpecificLocationRef).setValue(eq(testLocation), any(LocationUploader.UploadCallback.class));
    }
    
    @Test
    public void testUploadLocationWithNullPatientId() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        final String[] errorMessage = {null};
        
        // Test upload with null patient ID
        locationUploader.uploadCurrentLocation(null, testLocation, new LocationUploader.UploadCallback() {
            @Override
            public void onSuccess() {
                fail("Upload should fail with null patient ID");
                latch.countDown();
            }
            
            @Override
            public void onError(String error) {
                errorMessage[0] = error;
                latch.countDown();
            }
        });
        
        // Wait for async operation
        assertTrue("Upload callback should complete within 5 seconds", 
                latch.await(5, TimeUnit.SECONDS));
        assertNotNull("Error message should not be null", errorMessage[0]);
        assertTrue("Error should mention patient ID", errorMessage[0].toLowerCase().contains("patient") || 
                   errorMessage[0].toLowerCase().contains("invalid"));
    }
    
    @Test
    public void testUploadLocationWithNullLocation() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        final String[] errorMessage = {null};
        
        // Test upload with null location
        locationUploader.uploadCurrentLocation("test-patient-123", null, new LocationUploader.UploadCallback() {
            @Override
            public void onSuccess() {
                fail("Upload should fail with null location");
                latch.countDown();
            }
            
            @Override
            public void onError(String error) {
                errorMessage[0] = error;
                latch.countDown();
            }
        });
        
        // Wait for async operation
        assertTrue("Upload callback should complete within 5 seconds", 
                latch.await(5, TimeUnit.SECONDS));
        assertNotNull("Error message should not be null", errorMessage[0]);
        assertTrue("Error should mention location", 
                errorMessage[0].toLowerCase().contains("location") || 
                errorMessage[0].toLowerCase().contains("invalid"));
    }
    
    @Test
    public void testUploadLocationDatabaseFailure() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        final String[] errorMessage = {null};
        
        // Mock database write failure
        doAnswer(invocation -> {
            LocationUploader.UploadCallback callback = 
                (LocationUploader.UploadCallback) invocation.getArguments()[0];
            callback.onError("Database write failed");
            return null;
        }).when(mockSpecificLocationRef).setValue(any(LocationEntity.class));
        
        // Test upload failure
        locationUploader.uploadCurrentLocation("test-patient-123", testLocation, new LocationUploader.UploadCallback() {
            @Override
            public void onSuccess() {
                fail("Upload should fail");
                latch.countDown();
            }
            
            @Override
            public void onError(String error) {
                errorMessage[0] = error;
                latch.countDown();
            }
        });
        
        // Wait for async operation
        assertTrue("Upload callback should complete within 5 seconds", 
                latch.await(5, TimeUnit.SECONDS));
        assertNotNull("Error message should not be null", errorMessage[0]);
    }
    
    @Test
    public void testDatabaseStructure() {
        // Test that the correct database path is used
        locationUploader.uploadCurrentLocation("test-patient-123", testLocation, new LocationUploader.UploadCallback() {
            @Override
            public void onSuccess() {}
            
            @Override
            public void onError(String error) {}
        });
        
        // Verify database path structure: /locations/{patientId}/{pushId}
        verify(mockDatabase).getReference("locations");
        verify(mockLocationRef).child("test-patient-123");
        verify(mockPatientRef).push();
    }
    
    @Test
    public void testLocationDataValidation() {
        // Verify that Location object has valid data for upload
        assertTrue("Latitude should be valid", 
                testLocation.getLatitude() >= -90 && testLocation.getLatitude() <= 90);
        assertTrue("Longitude should be valid", 
                testLocation.getLongitude() >= -180 && testLocation.getLongitude() <= 180);
        assertTrue("Accuracy should be positive", testLocation.getAccuracy() > 0);
        assertNotNull("Provider should not be null", testLocation.getProvider());
    }
    
    @Test
    public void testConcurrentUploads() throws InterruptedException {
        int numUploads = 5;
        CountDownLatch latch = new CountDownLatch(numUploads);
        
        // Mock successful uploads
        doAnswer(invocation -> {
            LocationUploader.UploadCallback callback = 
                (LocationUploader.UploadCallback) invocation.getArguments()[1];
            callback.onSuccess();
            return null;
        }).when(mockSpecificLocationRef).setValue(any(LocationEntity.class), any(LocationUploader.UploadCallback.class));
        
        // Start multiple concurrent uploads
        for (int i = 0; i < numUploads; i++) {
            final int uploadId = i;
            
            // Create mock location for this upload
            Location mockLocation = mock(Location.class);
            when(mockLocation.getLatitude()).thenReturn(40.7128 + uploadId * 0.001);
            when(mockLocation.getLongitude()).thenReturn(-74.0060 + uploadId * 0.001);
            when(mockLocation.getAccuracy()).thenReturn(85.5f);
            when(mockLocation.getProvider()).thenReturn("GPS");
            when(mockLocation.isFromMockProvider()).thenReturn(false);
            
            new Thread(() -> locationUploader.uploadCurrentLocation("patient-" + uploadId, mockLocation, new LocationUploader.UploadCallback() {
                @Override
                public void onSuccess() {
                    latch.countDown();
                }
                
                @Override
                public void onError(String error) {
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