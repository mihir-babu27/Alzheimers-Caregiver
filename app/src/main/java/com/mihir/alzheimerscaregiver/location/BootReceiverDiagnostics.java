package com.mihir.alzheimerscaregiver.location;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.util.Log;
import java.util.List;

/**
 * Utility class to help diagnose LocationBootReceiver issues
 */
public class BootReceiverDiagnostics {
    
    private static final String TAG = "BootReceiverDiag";
    
    /**
     * Run comprehensive diagnostics on the LocationBootReceiver setup
     */
    public static void runDiagnostics(Context context) {
        Log.i(TAG, "=== BOOT RECEIVER DIAGNOSTICS ===");
        
        try {
            // Check if receiver is properly registered in manifest
            checkManifestRegistration(context);
            
            // Test if receiver can receive custom broadcasts
            testCustomBroadcast(context);
            
            // Check app permissions
            checkPermissions(context);
            
            // Test manual receiver instantiation
            testManualInstantiation(context);
            
        } catch (Exception e) {
            Log.e(TAG, "Error running diagnostics", e);
        }
        
        Log.i(TAG, "=== DIAGNOSTICS COMPLETE ===");
    }
    
    private static void checkManifestRegistration(Context context) {
        Log.i(TAG, "Checking manifest registration...");
        
        try {
            PackageManager pm = context.getPackageManager();
            PackageInfo packageInfo = pm.getPackageInfo(context.getPackageName(), 
                PackageManager.GET_RECEIVERS);
            
            if (packageInfo.receivers != null) {
                Log.i(TAG, "Found " + packageInfo.receivers.length + " receivers in manifest");
                for (int i = 0; i < packageInfo.receivers.length; i++) {
                    String receiverName = packageInfo.receivers[i].name;
                    Log.i(TAG, "Receiver " + i + ": " + receiverName);
                    if (receiverName.contains("LocationBootReceiver")) {
                        Log.i(TAG, "✅ LocationBootReceiver found in manifest!");
                        Log.i(TAG, "Exported: " + packageInfo.receivers[i].exported);
                        Log.i(TAG, "Enabled: " + packageInfo.receivers[i].enabled);
                    }
                }
            } else {
                Log.w(TAG, "❌ No receivers found in manifest");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking manifest", e);
        }
    }
    
    private static void testCustomBroadcast(Context context) {
        Log.i(TAG, "Testing custom broadcast...");
        
        try {
            Intent testIntent = new Intent("com.mihir.alzheimerscaregiver.TEST_BOOT_RECEIVER");
            testIntent.setPackage(context.getPackageName());
            
            PackageManager pm = context.getPackageManager();
            List<ResolveInfo> receivers = pm.queryBroadcastReceivers(testIntent, 0);
            
            Log.i(TAG, "Found " + receivers.size() + " receivers for custom broadcast");
            for (ResolveInfo info : receivers) {
                Log.i(TAG, "Receiver: " + info.activityInfo.name);
            }
            
            // Send the broadcast
            context.sendBroadcast(testIntent);
            Log.i(TAG, "✅ Custom broadcast sent");
            
        } catch (Exception e) {
            Log.e(TAG, "Error testing custom broadcast", e);
        }
    }
    
    private static void checkPermissions(Context context) {
        Log.i(TAG, "Checking permissions...");
        
        try {
            PackageManager pm = context.getPackageManager();
            
            // Check RECEIVE_BOOT_COMPLETED permission
            int bootPermission = pm.checkPermission(
                android.Manifest.permission.RECEIVE_BOOT_COMPLETED, 
                context.getPackageName());
            
            if (bootPermission == PackageManager.PERMISSION_GRANTED) {
                Log.i(TAG, "✅ RECEIVE_BOOT_COMPLETED permission granted");
            } else {
                Log.e(TAG, "❌ RECEIVE_BOOT_COMPLETED permission DENIED!");
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error checking permissions", e);
        }
    }
    
    private static void testManualInstantiation(Context context) {
        Log.i(TAG, "Testing manual receiver instantiation...");
        
        try {
            // Try to manually create and call the receiver
            LocationBootReceiver receiver = new LocationBootReceiver();
            Log.i(TAG, "✅ LocationBootReceiver instantiated successfully");
            
            // Test with BOOT_COMPLETED intent
            Intent bootIntent = new Intent(Intent.ACTION_BOOT_COMPLETED);
            receiver.onReceive(context, bootIntent);
            Log.i(TAG, "✅ Manual onReceive() call completed");
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Error with manual instantiation", e);
        }
    }
}