package com.example.nfccardreader

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import timber.log.Timber

class NFCReaderApp : Application() {
    
    companion object {
        const val NFC_CHANNEL_ID = "nfc_reader_channel"
        const val NOTIFICATION_CHANNEL_NAME = "NFC Reader Notifications"
        const val NOTIFICATION_CHANNEL_DESCRIPTION = "Shows notifications for NFC card reading events"
        
        // Get application context without memory leak
        @JvmStatic
        fun getAppContext(): Context = instance.applicationContext
        
        @Volatile
        private lateinit var instance: NFCReaderApp
            
        @JvmStatic
        fun getInstance(): NFCReaderApp = instance
    }
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        
        // Initialize logging in debug builds
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        
        // Create notification channel for Android O and above
        createNotificationChannel()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(
                NFC_CHANNEL_ID,
                NOTIFICATION_CHANNEL_NAME,
                importance
            ).apply {
                description = NOTIFICATION_CHANNEL_DESCRIPTION
            }
            
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}
