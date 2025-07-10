package com.example.nfccardreader

import android.app.PendingIntent
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private var nfcAdapter: NfcAdapter? = null
    private lateinit var statusTextView: TextView
    private lateinit var nfcContentTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusTextView = findViewById(R.id.statusTextView)
        nfcContentTextView = findViewById(R.id.nfcContentTextView)

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        if (nfcAdapter == null) {
            statusTextView.text = "NFC is not available on this device."
        } else if (!nfcAdapter!!.isEnabled) {
            statusTextView.text = "NFC is disabled. Please enable it in settings."
        } else {
            statusTextView.text = "Ready to scan NFC card..."
        }
    }

    override fun onResume() {
        super.onResume()
        // Create a PendingIntent object so the Android system can populate it with the tag details
        val intent = Intent(this, javaClass).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        // Enable foreground dispatch to get the system to deliver the intent to your activity
        nfcAdapter?.enableForegroundDispatch(this, pendingIntent, null, null)
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
        val action = intent.action
        if (NfcAdapter.ACTION_TAG_DISCOVERED == action ||
            NfcAdapter.ACTION_TECH_DISCOVERED == action ||
            NfcAdapter.ACTION_NDEF_DISCOVERED == action
        ) {
            // Get the tag from the intent
            val tag: Tag? = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
            tag?.let {
                // Convert the tag ID to a hex string
                val tagId = bytesToHexString(it.id)
                val tagInfo = """
                    Tag ID: $tagId
                    Tech List: ${it.techList.joinToString(", ")}
                    """.trimIndent()
                
                runOnUiThread {
                    statusTextView.text = "NFC Tag Detected!"
                    nfcContentTextView.text = tagInfo
                }
                
                // You can add more specific handling based on the tag type
                // For example, read NDEF messages if it's an NDEF tag
            } ?: run {
                runOnUiThread {
                    statusTextView.text = "Error: No tag data found"
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
