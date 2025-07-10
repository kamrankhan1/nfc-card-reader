package com.example.nfccardreader.util

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.*
import android.os.Build
import android.util.Log
import com.example.nfccardreader.NFCReaderApp
import com.example.nfccardreader.NFCReaderApp.Companion.TAG

/**
 * Utility class for handling NFC operations
 */
object NFCUtils {
    private const val TAG = "NFCUtils"
    
    // NFC tech lists to support various tag types
    private val TECH_LISTS = arrayOf(
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
    
    /**
     * Check if the device supports NFC
     */
    fun isNFCSupported(context: Context): Boolean {
        return NfcAdapter.getDefaultAdapter(context) != null
    }
    
    /**
     * Check if NFC is enabled on the device
     */
    fun isNFCEnabled(context: Context): Boolean {
        val adapter = NfcAdapter.getDefaultAdapter(context)
        return adapter != null && adapter.isEnabled
    }
    
    /**
     * Enable foreground dispatch to receive NFC intents
     */
    fun enableForegroundDispatch(activity: Activity) {
        try {
            val adapter = NfcAdapter.getDefaultAdapter(activity) ?: return
            
            val intent = Intent(activity, activity.javaClass).apply {
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            
            val pendingIntent = PendingIntent.getActivity(
                activity, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            
            val intentFilters = arrayOf(
                IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED),
                IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED),
                IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED)
            )
            
            adapter.enableForegroundDispatch(activity, pendingIntent, intentFilters, TECH_LISTS)
        } catch (e: Exception) {
            Log.e(TAG, "Error enabling NFC foreground dispatch", e)
        }
    }
    
    /**
     * Disable foreground dispatch when the activity is paused
     */
    fun disableForegroundDispatch(activity: Activity) {
        try {
            val adapter = NfcAdapter.getDefaultAdapter(activity) ?: return
            adapter.disableForegroundDispatch(activity)
        } catch (e: Exception) {
            Log.e(TAG, "Error disabling NFC foreground dispatch", e)
        }
    }
    
    /**
     * Get tag ID as a hex string
     */
    fun getTagId(tag: Tag): String {
        return tag.id.joinToString("") { "%02x".format(it) }
    }
    
    /**
     * Get the list of technologies supported by the tag
     */
    fun getTagTechList(tag: Tag): List<String> {
        return tag.techList.toList()
    }
    
    /**
     * Get a human-readable description of the tag type
     */
    fun getTagType(tag: Tag): String {
        return when (tag.techList.firstOrNull()) {
            NfcA::class.java.name -> "NFC-A (ISO 14443-3A)"
            NfcB::class.java.name -> "NFC-B (ISO 14443-3B)"
            NfcF::class.java.name -> "NFC-F (JIS 6319-4)"
            NfcV::class.java.name -> "NFC-V (ISO 15693)"
            IsoDep::class.java.name -> "ISO-DEP (ISO 14443-4)"
            Ndef::class.java.name -> "NDEF"
            NdefFormatable::class.java.name -> "NDEF Formatable"
            MifareClassic::class.java.name -> "MIFARE Classic"
            MifareUltralight::class.java.name -> "MIFARE Ultralight"
            else -> "Unknown"
        }
    }
    
    /**
     * Convert bytes to a hex string
     */
    fun bytesToHex(bytes: ByteArray): String {
        return "0x" + bytes.joinToString("") { "%02x".format(it) }
    }
    
    /**
     * Convert a hex string to a byte array
     */
    fun hexToBytes(hex: String): ByteArray {
        val cleanHex = hex.replace("0x", "").replace(" ", "")
        return cleanHex.chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
    }
    
    /**
     * Format tag information for display
     */
    fun formatTagInfo(tag: Tag): String {
        return """
            === Tag Information ===
            ID: ${getTagId(tag)}
            Type: ${getTagType(tag)}
            Technologies: ${getTagTechList(tag).joinToString(", ")}
            """.trimIndent()
    }
}
