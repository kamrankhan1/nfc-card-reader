package com.example.nfccardreader.util

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.nfccardreader.R

/**
 * Utility class for handling runtime permissions
 */
object PermissionUtils {
    
    // List of permissions that require a special rationale
    private val DANGEROUS_PERMISSIONS = listOf(
        Manifest.permission.NFC,
        Manifest.permission.INTERNET,
        Manifest.permission.ACCESS_NETWORK_STATE,
        Manifest.permission.VIBRATE
    )
    
    /**
     * Check if all required permissions are granted
     */
    fun hasAllPermissions(context: Context): Boolean {
        return DANGEROUS_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    /**
     * Request permissions with rationale if needed
     */
    fun requestPermissions(activity: AppCompatActivity, onResult: (Boolean) -> Unit) {
        val permissionsToRequest = DANGEROUS_PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()
        
        if (permissionsToRequest.isEmpty()) {
            onResult(true)
            return
        }
        
        val permissionsToExplain = permissionsToRequest.filter {
            ActivityCompat.shouldShowRequestPermissionRationale(activity, it)
        }
        
        if (permissionsToExplain.isNotEmpty()) {
            // Show rationale dialog
            showPermissionRationale(activity, permissionsToRequest, onResult)
        } else {
            // Request permissions directly
            requestPermissions(activity, permissionsToRequest, onResult)
        }
    }
    
    /**
     * Show permission rationale dialog
     */
    private fun showPermissionRationale(
        activity: AppCompatActivity,
        permissions: Array<out String>,
        onResult: (Boolean) -> Unit
    ) {
        AlertDialog.Builder(activity)
            .setTitle(R.string.permission_rationale_title)
            .setMessage(R.string.permission_rationale_message)
            .setPositiveButton(R.string.grant) { _, _ ->
                requestPermissions(activity, permissions, onResult)
            }
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                onResult(false)
            }
            .setCancelable(false)
            .show()
    }
    
    /**
     * Request permissions using Activity Result API
     */
    private fun requestPermissions(
        activity: AppCompatActivity,
        permissions: Array<out String>,
        onResult: (Boolean) -> Unit
    ) {
        val launcher = activity.registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { results ->
            val allGranted = results.all { it.value }
            onResult(allGranted)
        }
        
        // Convert Array<out String> to Array<String> for compatibility
        launcher.launch(permissions.toList().toTypedArray())
    }
    
    /**
     * Show app settings dialog when permissions are permanently denied
     */
    fun showAppSettingsDialog(activity: Activity) {
        AlertDialog.Builder(activity)
            .setTitle(R.string.permission_required_title)
            .setMessage(R.string.permission_required_message)
            .setPositiveButton(R.string.open_settings) { _, _ ->
                openAppSettings(activity)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .setCancelable(false)
            .show()
    }
    
    /**
     * Open app settings to manually grant permissions
     */
    private fun openAppSettings(activity: Activity) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", activity.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        activity.startActivity(intent)
    }
    
    /**
     * Check if a permission is granted
     */
    private fun isPermissionGranted(context: Context, permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * Check if NFC permission is granted
     */
    fun isNfcPermissionGranted(context: Context): Boolean {
        return isPermissionGranted(context, Manifest.permission.NFC) || 
               Build.VERSION.SDK_INT < Build.VERSION_CODES.M
    }
    
    /**
     * Check if vibration permission is granted
     */
    fun isVibrationPermissionGranted(context: Context): Boolean {
        return isPermissionGranted(context, Manifest.permission.VIBRATE) || 
               Build.VERSION.SDK_INT < Build.VERSION_CODES.M
    }
}
