package com.example.nfccardreader

import android.app.PendingIntent
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Toast
import android.widget.Button
import android.content.pm.PackageManager
import android.content.IntentFilter
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private val TAG = "NFCDemo"
    private var nfcAdapter: NfcAdapter? = null
    private lateinit var statusTextView: TextView
    private lateinit var nfcContentTextView: TextView
    private lateinit var readingStatusTextView: TextView
    private lateinit var readingProgressBar: ProgressBar
    private lateinit var nfcIcon: ImageView
    private lateinit var contentScrollView: android.widget.ScrollView
    private var isReading = false
    private var pendingIntent: PendingIntent? = null
    private val techList = arrayOf(
        arrayOf(android.nfc.tech.Ndef::class.java.name),
        arrayOf(android.nfc.tech.NdefFormatable::class.java.name),
        arrayOf(android.nfc.tech.MifareClassic::class.java.name),
        arrayOf(android.nfc.tech.MifareUltralight::class.java.name),
        arrayOf(android.nfc.tech.NfcA::class.java.name),
        arrayOf(android.nfc.tech.NfcB::class.java.name),
        arrayOf(android.nfc.tech.NfcF::class.java.name),
        arrayOf(android.nfc.tech.NfcV::class.java.name),
        arrayOf(android.nfc.tech.IsoDep::class.java.name),
        arrayOf(android.nfc.tech.NfcBarcode::class.java.name)
    )

    companion object {
        private const val PERMISSION_REQUEST_CODE = 1001
        private const val TAG = "NFCDemo"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize views
        statusTextView = findViewById(R.id.statusTextView)
        nfcContentTextView = findViewById(R.id.nfcContentTextView)
        readingStatusTextView = findViewById(R.id.readingStatusTextView)
        readingProgressBar = findViewById(R.id.readingProgressBar)
        nfcIcon = findViewById(R.id.nfcIcon)
        contentScrollView = findViewById(R.id.contentScrollView)

        // Set click listeners
        findViewById<View>(R.id.readCardButton).setOnClickListener {
            startReading()
        }
        
        // Set up debug button
        findViewById<View>(R.id.debugButton).setOnClickListener {
            showNfcDebugInfo()
        }

        // Set initial UI state
        updateUIState(UIState.READY)

        // Check if device supports NFC
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        
        Log.d(TAG, "NFC Adapter: $nfcAdapter")
        
        // Check and request NFC permissions
        checkAndRequestPermissions()
        
        if (nfcAdapter == null) {
            // NFC is not supported on this device
            val errorMsg = getString(R.string.nfc_required)
            Log.e(TAG, errorMsg)
            updateUIState(UIState.ERROR, errorMsg)
        } else if (!nfcAdapter!!.isEnabled) {
            // NFC is not enabled
            val errorMsg = getString(R.string.nfc_disabled)
            Log.w(TAG, errorMsg)
            updateUIState(UIState.ERROR, errorMsg)
            
            // Show how to enable NFC
            val enableNfcIntent = Intent(android.provider.Settings.ACTION_NFC_SETTINGS)
            if (enableNfcIntent.resolveActivity(packageManager) != null) {
                startActivity(enableNfcIntent)
            }
        } else {
            // NFC is available and enabled
            Log.d(TAG, "NFC is enabled and ready")
            updateUIState(UIState.READY)
            
            // Check if we have a tag from the intent that launched us
            handleIntent(intent)
            
            // Set up debug button
            findViewById<Button>(R.id.debugButton).setOnClickListener {
                showNfcDebugInfo()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume called")
        
        try {
            // Create a generic PendingIntent that will be used to get the tag
            val intent = Intent(this, javaClass).apply {
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )

            // Enable foreground dispatch
            nfcAdapter?.let { adapter ->
                if (adapter.isEnabled) {
                    try {
                        val intentFilters = arrayOf(
                            IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED).apply {
                                addCategory(Intent.CATEGORY_DEFAULT)
                            },
                            IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED).apply {
                                addCategory(Intent.CATEGORY_DEFAULT)
                            },
                            IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED).apply {
                                addCategory(Intent.CATEGORY_DEFAULT)
                                addDataScheme("http")
                                addDataScheme("https")
                                addDataScheme("vnd.android.nfc")
                                addDataScheme("vnd.android.nfc.ext")
                                addDataType("*/*")
                            }
                        )
                        
                        adapter.enableForegroundDispatch(this, pendingIntent, intentFilters, techList)
                        Log.d(TAG, "NFC Foreground dispatch enabled")
                        updateUIState(UIState.READY)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error setting up NFC foreground dispatch", e)
                        updateUIState(UIState.ERROR, "Error setting up NFC: ${e.localizedMessage}")
                    }
                } else {
                    Log.w(TAG, "NFC is disabled")
                    updateUIState(UIState.ERROR, "NFC is disabled. Please enable it in settings.")
                }
            } ?: run {
                Log.e(TAG, "NFC is not available on this device")
                updateUIState(UIState.ERROR, "NFC is not available on this device")
            }
        } catch (e: Exception) {
            val errorMsg = "Error enabling NFC: ${e.message}"
            Log.e(TAG, errorMsg, e)
            updateUIState(UIState.ERROR, errorMsg)
        }
    }

    override fun onPause() {
        super.onPause()
        // Disable foreground dispatch when the activity is paused
        nfcAdapter?.disableForegroundDispatch(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Log.d(TAG, "onNewIntent called with action: ${intent.action}")
        
        // Log all extras for debugging
        intent.extras?.keySet()?.forEach { key ->
            Log.d(TAG, "Intent extra - $key: ${intent.extras?.get(key)}")
        }
        
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        if (isReading) {
            Log.d(TAG, "Already reading a tag, ignoring new intent")
            return // Prevent multiple reads at once
        }
        
        Log.d(TAG, "New intent received: ${intent.action}")
        
        // Log all extras for debugging
        intent.extras?.keySet()?.forEach { key ->
            Log.d(TAG, "Intent extra - $key: ${intent.extras?.get(key)}")
        }
        
        val validActions = arrayOf(
            NfcAdapter.ACTION_TAG_DISCOVERED,
            NfcAdapter.ACTION_TECH_DISCOVERED,
            NfcAdapter.ACTION_NDEF_DISCOVERED
        )
        
        if (validActions.contains(intent.action)) {
            isReading = true
            updateUIState(UIState.READING)
            
            try {
                // Get the tag from the intent
                val tag = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
                }
                
                if (tag != null) {
                    Log.d(TAG, "Tag discovered - ID: ${bytesToHexString(tag.id)}")
                    Log.d(TAG, "Tag tech list: ${tag.techList.joinToString()}")
                    
                    // Process the tag with a small delay for better UX
                    nfcContentTextView.postDelayed({
                        try {
                            try {
                                // Convert the tag ID to a hex string
                                val tagId = bytesToHexString(tag.id)
                                
                                // Get detailed tech info
                                val techDetails = StringBuilder()
                                tag.techList.forEach { tech ->
                                    techDetails.append("\n    - $tech")
                                }
                                
                                // Build tag information
                                val tagInfo = """
                                    === Tag Detected ===
                                    ID: $tagId
                                    
                                    === Technical Details ===
                                    Raw ID: ${tag.id.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }}
                                    
                                    === Supported Technologies ===${techDetails}
                                    
                                    === Actions ===
                                    - Tap another tag to scan
                                    - Pull down to refresh
                                    """.trimIndent()
                                
                                Log.d(TAG, "Tag processed successfully")
                                updateUIState(UIState.READ_SUCCESS, tagInfo)
                                
                            } catch (e: Exception) {
                                Log.e(TAG, "Error building tag info", e)
                                updateUIState(UIState.ERROR, "Error reading tag data: ${e.localizedMessage}")
                            }
                            
                        } catch (e: Exception) {
                            Log.e(TAG, "Error processing tag", e)
                            updateUIState(UIState.ERROR, "${getString(R.string.nfc_error)}\n${e.localizedMessage}")
                        } finally {
                            isReading = false
                        }
                    }, 500) // Small delay to show the reading state
                    
                } else {
                    updateUIState(UIState.ERROR, getString(R.string.nfc_error))
                    isReading = false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling NFC intent", e)
                updateUIState(UIState.ERROR, "${getString(R.string.nfc_error)}\n${e.localizedMessage}")
                isReading = false
            }
        }
    }

    /**
     * Converts a byte array to a hexadecimal string.
     * @param src the byte array to convert
     * @return a string representation of the byte array in hexadecimal format
     */
    private enum class UIState {
        READY, READING, READ_SUCCESS, ERROR
    }
    
    private fun updateUIState(state: UIState, message: String? = null) {
        runOnUiThread {
            when (state) {
                UIState.READY -> {
                    nfcIcon.setImageResource(R.drawable.ic_nfc)
                    nfcIcon.clearAnimation()
                    readingProgressBar.isVisible = false
                    readingStatusTextView.isVisible = false
                    contentScrollView.isVisible = false
                    statusTextView.text = getString(R.string.ready_to_scan)
                    statusTextView.setTextColor(ContextCompat.getColor(this, R.color.purple_500))
                }
                UIState.READING -> {
                    nfcIcon.setImageResource(R.drawable.ic_nfc_reading)
                    // Add pulse animation
                    val pulse = AnimationUtils.loadAnimation(this, R.anim.pulse).apply {
                        repeatCount = -1 // Infinite
                    }
                    nfcIcon.startAnimation(pulse)
                    readingProgressBar.isVisible = true
                    readingStatusTextView.isVisible = true
                    contentScrollView.isVisible = false
                    statusTextView.text = getString(R.string.reading_tag)
                    statusTextView.setTextColor(ContextCompat.getColor(this, R.color.teal_700))
                }
                UIState.READ_SUCCESS -> {
                    nfcIcon.setImageResource(R.drawable.ic_nfc_success)
                    nfcIcon.clearAnimation()
                    readingProgressBar.isVisible = false
                    readingStatusTextView.isVisible = false
                    contentScrollView.isVisible = true
                    statusTextView.text = getString(R.string.tag_detected)
                    statusTextView.setTextColor(ContextCompat.getColor(this, R.color.teal_700))
                    nfcContentTextView.text = message
                    
                    // Scroll to top
                    contentScrollView.post {
                        contentScrollView.scrollTo(0, 0)
                    }
                    
                    // Vibrate for feedback (only if we have permission)
                    if (checkSelfPermission(android.Manifest.permission.VIBRATE) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        try {
                            val vibrator = getSystemService(VIBRATOR_SERVICE) as android.os.Vibrator
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                vibrator.vibrate(android.os.VibrationEffect.createOneShot(100, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                            } else {
                                @Suppress("DEPRECATION")
                                vibrator.vibrate(100)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error with vibration: ${e.message}")
                        }
                    }
                }
                UIState.ERROR -> {
                    nfcIcon.setImageResource(R.drawable.ic_nfc_error)
                    nfcIcon.clearAnimation()
                    readingProgressBar.isVisible = false
                    readingStatusTextView.isVisible = false
                    contentScrollView.isVisible = false
                    statusTextView.text = message ?: getString(R.string.nfc_error)
                    statusTextView.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
                    
                    // Reset after delay
                    nfcContentTextView.postDelayed({
                        updateUIState(UIState.READY)
                    }, 3000)
                }
            }
        }
    }
    
    private fun startReading() {
        // Start NFC reading process
        updateUIState(UIState.READING)
        // Show a message to the user
        Toast.makeText(this, "Hold your NFC card near the device", Toast.LENGTH_SHORT).show()
        
        // Ensure NFC is enabled
        nfcAdapter?.let { adapter ->
            if (!adapter.isEnabled) {
                updateUIState(UIState.ERROR, "Please enable NFC in settings")
                startActivity(Intent(android.provider.Settings.ACTION_NFC_SETTINGS))
            }
        } ?: run {
            updateUIState(UIState.ERROR, "NFC is not available on this device")
        }
    }
    

    
    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf<String>()
        
        // Check NFC permission
        if (checkSelfPermission(android.Manifest.permission.NFC) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(android.Manifest.permission.NFC)
        }
        
        // Check VIBRATE permission (only needed for Android 12+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            if (checkSelfPermission(android.Manifest.permission.VIBRATE) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(android.Manifest.permission.VIBRATE)
            }
        }
        
        if (permissionsToRequest.isNotEmpty()) {
            // Request missing permissions
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                requestPermissions(
                    permissionsToRequest.toTypedArray(),
                    PERMISSION_REQUEST_CODE
                )
            } else {
                // For older versions, just initialize NFC
                initializeNfc()
            }
        } else {
            // All permissions already granted, initialize NFC
            initializeNfc()
        }
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            PERMISSION_REQUEST_CODE -> {
                var allPermissionsGranted = true
                var nfcPermissionGranted = false
                
                // Check each permission result
                for (i in permissions.indices) {
                    if (grantResults[i] != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        allPermissionsGranted = false
                        if (permissions[i] == android.Manifest.permission.NFC) {
                            updateUIState(UIState.ERROR, "NFC permission is required to read cards")
                            return@onRequestPermissionsResult
                        }
                    } else if (permissions[i] == android.Manifest.permission.NFC) {
                        nfcPermissionGranted = true
                    }
                }
                
                if (allPermissionsGranted || nfcPermissionGranted) {
                    // All required permissions granted, initialize NFC
                    initializeNfc()
                } else {
                    updateUIState(UIState.ERROR, "Required permissions were not granted")
                }
            }
        }
    }
    
    private fun initializeNfc() {
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        if (nfcAdapter == null) {
            updateUIState(UIState.ERROR, "This device doesn't support NFC")
            return
        }
        
        if (!nfcAdapter!!.isEnabled) {
            updateUIState(UIState.ERROR, "Please enable NFC in Settings")
            // Open NFC settings with the correct intent
            val intent = when {
                android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2 -> {
                    // For newer devices, use the NFC settings intent
                    Intent(android.provider.Settings.ACTION_NFC_SETTINGS)
                }
                else -> {
                    // Fallback for older devices
                    Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS)
                }
            }
            startActivity(intent)
        } else {
            updateUIState(UIState.READY)
        }
    }
    
    private fun showNfcDebugInfo() {
        try {
            val nfcAdapter = NfcAdapter.getDefaultAdapter(this)
            val debugInfo = """
                === NFC Debug Information ===
                
                NFC Adapter: ${nfcAdapter?.let { "Available" } ?: "Not available"}
                NFC Enabled: ${nfcAdapter?.isEnabled ?: false}
                
                === Device Information ===
                Model: ${android.os.Build.MODEL}
                Manufacturer: ${android.os.Build.MANUFACTURER}
                Android Version: ${android.os.Build.VERSION.RELEASE} (SDK ${android.os.Build.VERSION.SDK_INT})
                
                === App Configuration ===
                NFC Hardware: ${packageManager.hasSystemFeature(PackageManager.FEATURE_NFC)}
                NFC Host-based Card Emulation: ${packageManager.hasSystemFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION)}
                NFC Host Card Emulation: ${packageManager.hasSystemFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION_NFCF)}
                
                === Debug Actions ===
                1. Ensure NFC is enabled in device settings
                2. Try holding the tag near different parts of the device
                3. Make sure the screen is on and unlocked
                4. Restart the device if issues persist
            """.trimIndent()
            
            updateUIState(UIState.READ_SUCCESS, debugInfo)
            
            // Test NFC functionality
            nfcAdapter?.let { adapter ->
                if (adapter.isEnabled) {
                    Toast.makeText(this, "NFC is enabled and ready", Toast.LENGTH_SHORT).show()
                    
                    // Show NFC settings if needed
                    try {
                        val intent = Intent(android.provider.Settings.ACTION_NFC_SETTINGS)
                        if (intent.resolveActivity(packageManager) != null) {
                            startActivity(Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS))
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error opening NFC settings", e)
                    }
                }
            } ?: run {
                updateUIState(UIState.ERROR, "NFC is not available on this device")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in showNfcDebugInfo", e)
            updateUIState(UIState.ERROR, "Error checking NFC status: ${e.localizedMessage}")
        }
    }
    
    private fun bytesToHexString(src: ByteArray): String {
        val stringBuilder = StringBuilder("0x")
        for (b in src) {
            // Convert byte to unsigned int and format as hex
            stringBuilder.append("%02x".format(b.toInt() and 0xff))
        }
        return stringBuilder.toString()
    }
}
