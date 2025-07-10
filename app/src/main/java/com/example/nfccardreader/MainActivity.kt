package com.example.nfccardreader

import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.NfcA
import android.nfc.tech.NfcB
import android.nfc.tech.NfcF
import android.nfc.tech.NfcV
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.nfc.tech.MifareClassic
import android.nfc.tech.MifareUltralight
import android.nfc.tech.IsoDep
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.nfccardreader.util.NFCUtils
import com.example.nfccardreader.util.PermissionUtils
import com.google.android.material.snackbar.Snackbar

/**
 * Main activity for the NFC Card Reader application.
 * Handles NFC tag reading and displays tag information.
 */
class MainActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "NFCCardReader"
    }
    
    // UI Components
    private lateinit var statusTextView: TextView
    private lateinit var nfcContentTextView: TextView
    private lateinit var logTextView: TextView
    private lateinit var debugButton: Button
    private lateinit var clearLogsButton: Button
    
    // Log buffer
    private val logBuffer = StringBuilder()
    private val MAX_LOG_LINES = 100
    
    // NFC Components
    private var nfcAdapter: NfcAdapter? = null
    private var isReading = false
    private lateinit var pendingIntent: PendingIntent

    // Activity result launcher for NFC settings
    private val nfcSettingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { /* No action needed on return */ }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize views
        initializeViews()
        
        // Initialize NFC adapter
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        
        // Create a PendingIntent for NFC foreground dispatch
        val intent = Intent(this, javaClass).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Check and request permissions
        checkAndRequestPermissions()
        
        // Handle NFC intent if the app was launched by scanning an NFC tag
        handleIntent(intent)
    }
    
    
    
    private fun initializeViews() {
        statusTextView = findViewById(R.id.statusTextView)
        nfcContentTextView = findViewById(R.id.nfcContentTextView)
        logTextView = findViewById(R.id.logTextView)
        debugButton = findViewById(R.id.debugButton)
        clearLogsButton = findViewById(R.id.clearLogsButton)
        
        // Set up debug button
        debugButton.setOnClickListener {
            showNfcDebugInfo()
        }
        
        // Set up clear logs button
        clearLogsButton.setOnClickListener {
            logBuffer.clear()
            logTextView.text = ""
            log("Logs cleared")
        }
        
        // Set initial status
        updateStatus(getString(R.string.ready_to_scan))
    }
    
    private fun checkAndRequestPermissions() {
        if (!PermissionUtils.isNfcPermissionGranted(this)) {
            // Request NFC permission
            PermissionUtils.requestPermissions(this) { granted ->
                if (granted) {
                    // Permission granted, continue with NFC setup
                    setupNFC()
                } else {
                    // Permission denied, show error
                    showError(getString(R.string.error_permission_denied))
                    // Show app settings dialog
                    PermissionUtils.showAppSettingsDialog(this)
                }
            }
        } else {
            // Permission already granted, continue with NFC setup
            setupNFC()
        }
    }

    private fun setupNFC() {
        if (!NFCUtils.isNFCSupported(this)) {
            showError(getString(R.string.nfc_not_supported))
            return
        }
        
        if (!NFCUtils.isNFCEnabled(this)) {
            showNfcDisabledAlert()
        } else {
            // NFC is supported and enabled
            updateStatus(getString(R.string.ready_to_scan))
            enableNfcForegroundDispatch()
        }
    }
    
    private fun processTag(tag: Tag) {
        val tagId = tag.id.joinToString("") { "%02x".format(it) }
        log("Processing tag: $tagId")
        log("Tag tech list: ${tag.techList.joinToString()}")
        Log.d(TAG, "processTag called with tag: $tagId")
        if (isReading) {
            Log.d(TAG, "Already reading a tag, ignoring new tag")
            return
        }
        
        isReading = true
        updateStatus(getString(R.string.status_reading))
        
        // Vibrate for user feedback
        try {
            val vibrator = getSystemService(VIBRATOR_SERVICE) as android.os.Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(android.os.VibrationEffect.createOneShot(100, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(100)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Vibration not available", e)
        }
        
        // Process the tag in a background thread to avoid ANR
        Thread {
            try {
                val tagInfo = NFCUtils.formatTagInfo(tag)
                
                runOnUiThread {
                    updateStatus(getString(R.string.status_success))
                    nfcContentTextView.text = tagInfo
                    
                    // Show a snackbar with tag ID
                    Snackbar.make(
                        findViewById(android.R.id.content),
                        getString(R.string.tag_detected),
                        Snackbar.LENGTH_LONG
                    ).setAction("DISMISS") {}
                     .show()
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "Security exception while processing tag", e)
                runOnUiThread {
                    showError("Security permission error. Please check NFC permissions in app settings.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing tag", e)
                runOnUiThread {
                    showError("${getString(R.string.nfc_scan_error)}: ${e.localizedMessage}")
                }
            } finally {
                isReading = false
            }
        }.start()
    }
    
    private fun checkNfcStatus() {
        when {
            nfcAdapter == null -> {
                showError(getString(R.string.nfc_not_supported))
                debugButton.visibility = View.GONE
            }
            !nfcAdapter!!.isEnabled -> {
                showNfcDisabledAlert()
                debugButton.visibility = View.VISIBLE
            }
            else -> {
                updateStatus(getString(R.string.ready_to_scan))
                debugButton.visibility = View.VISIBLE
                enableNfcForegroundDispatch()
            }
        }
    }
    
    private fun showNfcDisabledAlert() {
        AlertDialog.Builder(this)
            .setTitle(R.string.nfc_disabled)
            .setMessage(R.string.nfc_disabled)
            .setPositiveButton(R.string.open_settings) { _, _ ->
                // Open NFC settings
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                    nfcSettingsLauncher.launch(Intent(Settings.ACTION_NFC_SETTINGS))
                } else {
                    nfcSettingsLauncher.launch(Intent(Settings.ACTION_WIRELESS_SETTINGS))
                }
            }
            .setNegativeButton(android.R.string.cancel) { dialog, _ ->
                dialog.dismiss()
                showError(getString(R.string.error_nfc_disabled))
            }
            .setCancelable(false)
            .show()
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Handle NFC intents when the app is already running
        handleIntent(intent)
    }
    
    private fun handleIntent(intent: Intent) {
        Log.d(TAG, "handleIntent called with action: ${intent.action}")
        if (intent.action in arrayOf(
                NfcAdapter.ACTION_NDEF_DISCOVERED,
                NfcAdapter.ACTION_TAG_DISCOVERED,
                NfcAdapter.ACTION_TECH_DISCOVERED
            )
        ) {
            Log.d(TAG, "NFC intent received")
            val tag = intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG)
            if (tag != null) {
                Log.d(TAG, "Tag detected with ID: ${tag.id.joinToString("") { "%02x".format(it) }}")
                Log.d(TAG, "Tag tech list: ${tag.techList.joinToString()}")
                processTag(tag)
            } else {
                Log.e(TAG, "Received NFC intent but tag is null")
            }
        }
    }
    
    private fun enableNfcForegroundDispatch() {
        log("Enabling NFC foreground dispatch")
        Log.d(TAG, "enableNfcForegroundDispatch called")
        nfcAdapter?.let { adapter ->
            try {
                Log.d(TAG, "Setting up NFC foreground dispatch")
                val intent = Intent(this, javaClass).apply {
                    addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                
                val pendingIntent = PendingIntent.getActivity(
                    this, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                
                val intentFilters = arrayOf(
                    IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED).apply {
                        try {
                            addDataType("*/*")
                        } catch (e: IntentFilter.MalformedMimeTypeException) {
                            Log.e(TAG, "Malformed MIME type", e)
                        }
                    },
                    IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED),
                    IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED)
                )
                
                // Use the tech lists from NFCUtils
                val techLists = arrayOf(
                    arrayOf(NfcA::class.java.name),
                    arrayOf(NfcB::class.java.name),
                    arrayOf(NfcF::class.java.name),
                    arrayOf(NfcV::class.java.name),
                    arrayOf(Ndef::class.java.name),
                    arrayOf(NdefFormatable::class.java.name),
                    arrayOf(MifareClassic::class.java.name),
                    arrayOf(MifareUltralight::class.java.name),
                    arrayOf(IsoDep::class.java.name)
                )
                adapter.enableForegroundDispatch(this, pendingIntent, intentFilters, techLists)
            } catch (e: Exception) {
                Log.e(TAG, "Error enabling NFC foreground dispatch", e)
            }
        }
    }
    
    private fun disableNfcForegroundDispatch() {
        nfcAdapter?.let { adapter ->
            try {
                adapter.disableForegroundDispatch(this)
            } catch (e: Exception) {
                Log.e(TAG, "Error disabling NFC foreground dispatch", e)
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        log("App resumed")
        Log.d(TAG, "onResume: Enabling NFC foreground dispatch")
        enableNfcForegroundDispatch()
        val nfcEnabled = nfcAdapter?.isEnabled ?: false
        log("NFC adapter state - isEnabled: $nfcEnabled")
        Log.d(TAG, "NFC adapter state - isEnabled: $nfcEnabled")
    }
    
    override fun onPause() {
        super.onPause()
        log("App paused")
        Log.d(TAG, "onPause: Disabling NFC foreground dispatch")
        disableNfcForegroundDispatch()
    }
    
    private fun showNfcDebugInfo() {
        try {
            val debugInfo = """
                === ${getString(R.string.debug_info)} ===
                ${getString(R.string.debug_nfc_status)}: ${
                    when {
                        nfcAdapter == null -> getString(R.string.debug_nfc_not_supported)
                        nfcAdapter?.isEnabled == true -> getString(R.string.debug_nfc_enabled)
                        else -> getString(R.string.debug_nfc_disabled)
                    }
                }
                
                === ${getString(R.string.debug_device_info)} ===
                NFC Status: ${if (nfcAdapter?.isEnabled == true) "Enabled" else "Disabled"}
                
                === ${getString(R.string.debug_app_info)} ===
                ${getString(R.string.app_name)} v${packageManager.getPackageInfo(packageName, 0).versionName}
                Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})
                """.trimIndent()
                
            nfcContentTextView.text = debugInfo
            updateStatus(getString(R.string.debug_info))
            
        } catch (e: Exception) {
            Log.e(TAG, "Error showing debug info", e)
            showError("${getString(R.string.error_occurred)}: ${e.localizedMessage}")
        }
    }
    
    private fun log(message: String) {
        // Add timestamp
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault())
            .format(java.util.Date())
        val logMessage = "$timestamp: $message\n"
        
        // Add to log buffer
        logBuffer.append(logMessage)
        
        // Limit log size
        val lines = logBuffer.split("\n")
        if (lines.size > MAX_LOG_LINES) {
            val start = lines.size - MAX_LOG_LINES
            logBuffer.clear()
            logBuffer.append(lines.subList(start, lines.size).joinToString("\n"))
        }
        
        // Update UI on main thread
        runOnUiThread {
            logTextView.append(logMessage)
            // Auto-scroll to bottom
            val scrollView = logTextView.parent as? android.widget.ScrollView
            scrollView?.post {
                scrollView.fullScroll(android.view.View.FOCUS_DOWN)
            }
        }
    }
    
    private fun updateStatus(status: String) {
        log("Status: $status")
        runOnUiThread {
            statusTextView.text = status
        }
    }
    
    private fun showError(message: String) {
        log("ERROR: $message")
        runOnUiThread {
            statusTextView.text = getString(R.string.status_error)
            nfcContentTextView.text = message
            
            // Show a longer toast for errors
            Toast.makeText(
                this@MainActivity,
                message,
                Toast.LENGTH_LONG
            ).show()
        }
    }
}
