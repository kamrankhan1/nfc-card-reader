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
    private lateinit var pendingIntent: PendingIntent
    private val techList = arrayOf(
        arrayOf(android.nfc.tech.Ndef::class.java.name),
        arrayOf(android.nfc.tech.NdefFormatable::class.java.name),
        arrayOf(android.nfc.tech.NfcA::class.java.name),
        arrayOf(android.nfc.tech.NfcB::class.java.name),
        arrayOf(android.nfc.tech.NfcF::class.java.name),
        arrayOf(android.nfc.tech.NfcV::class.java.name),
        arrayOf(android.nfc.tech.IsoDep::class.java.name),
        arrayOf(android.nfc.tech.MifareClassic::class.java.name),
        arrayOf(android.nfc.tech.MifareUltralight::class.java.name),
        arrayOf(android.nfc.tech.NfcBarcode::class.java.name)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusTextView = findViewById(R.id.statusTextView)
        nfcContentTextView = findViewById(R.id.nfcContentTextView)
        readingStatusTextView = findViewById(R.id.readingStatusTextView)
        readingProgressBar = findViewById(R.id.readingProgressBar)
        nfcIcon = findViewById(R.id.nfcIcon)
        contentScrollView = findViewById(R.id.contentScrollView)
        
        // Set initial UI state
        updateUIState(UIState.READY)

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        if (nfcAdapter == null) {
            updateUIState(UIState.ERROR, getString(R.string.nfc_required))
        } else if (!nfcAdapter!!.isEnabled) {
            updateUIState(UIState.ERROR, getString(R.string.nfc_disabled))
        } else {
            updateUIState(UIState.READY)
        }
    }

    override fun onResume() {
        super.onResume()
        
        // Create a generic PendingIntent that will be used to get the tag
        val intent = Intent(this, javaClass).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        // Enable foreground dispatch
        try {
            nfcAdapter?.enableForegroundDispatch(this, pendingIntent, null, techList)
            Log.d(TAG, "NFC Foreground dispatch enabled")
        } catch (e: Exception) {
            Log.e(TAG, "Error enabling NFC foreground dispatch", e)
            runOnUiThread {
                statusTextView.text = "Error enabling NFC"
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // Disable foreground dispatch when the activity is paused
        nfcAdapter?.disableForegroundDispatch(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // This method is called when a new intent is received
        // (when the user scans an NFC tag)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        if (isReading) return // Prevent multiple reads at once
        
        Log.d(TAG, "New intent received: ${intent.action}")
        
        if (intent.action == NfcAdapter.ACTION_TAG_DISCOVERED ||
            intent.action == NfcAdapter.ACTION_TECH_DISCOVERED ||
            intent.action == NfcAdapter.ACTION_NDEF_DISCOVERED
        ) {
            isReading = true
            updateUIState(UIState.READING)
            
            try {
                // Get the tag from the intent
                val tag = intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG)
                if (tag != null) {
                    Log.d(TAG, "Tag discovered: ${bytesToHexString(tag.id)}")
                    
                    // Simulate reading delay for better UX
                    nfcContentTextView.postDelayed({
                        try {
                            // Convert the tag ID to a hex string
                            val tagId = bytesToHexString(tag.id)
                            val tagInfo = """
                                Tag ID: $tagId
                                Tech List: ${tag.techList.joinToString("\n    ", "\n    ")}
                                
                                Raw ID: ${tag.id.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }}
                                """.trimIndent()
                            
                            updateUIState(UIState.READ_SUCCESS, tagInfo)
                            
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
                    
                    // Vibrate for feedback
                    val vibrator = getSystemService(VIBRATOR_SERVICE) as android.os.Vibrator
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        vibrator.vibrate(android.os.VibrationEffect.createOneShot(100, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(100)
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
    
    private fun bytesToHexString(src: ByteArray): String {
        val stringBuilder = StringBuilder("0x")
        for (b in src) {
            // Convert byte to unsigned int and format as hex
            stringBuilder.append("%02x".format(b.toInt() and 0xff))
        }
        return stringBuilder.toString()
    }
}
