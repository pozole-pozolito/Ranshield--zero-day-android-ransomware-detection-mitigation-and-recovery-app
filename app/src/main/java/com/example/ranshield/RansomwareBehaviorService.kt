package com.example.ranshield

import android.accessibilityservice.AccessibilityService
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.os.Environment
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

class RansomwareBehaviorService : AccessibilityService() {

    private var vaultObserver: VaultObserver? = null
    private var mlJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var currentForegroundPackage: String = "unknown"
    private val uiHijackCount = AtomicInteger(0)
    private val deviceAdminReq = AtomicInteger(0)
    private val ransomTextDetected = AtomicInteger(0)
    private val wallpaperChanged = AtomicInteger(0)

    // Detect when the malware changes the user's wallpaper (typical ransomware behavior)
    private val wallpaperReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_WALLPAPER_CHANGED) {
                Log.e("RanshieldML", "⚠️ WALLPAPER CHANGE DETECTED - SUSPICIOUS BEHAVIOR")
                wallpaperChanged.set(1)
                // Trigger immediate check on wallpaper change
                triggerImmediateInference()
            }
        }
    }

    private val installReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_PACKAGE_ADDED) {
                val pkgName = intent.data?.encodedSchemeSpecificPart
                if (pkgName != null && pkgName != packageName) {
                    val scanIntent = Intent(this@RansomwareBehaviorService, ScanPromptActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        putExtra("PACKAGE_NAME", pkgName)
                    }
                    startActivity(scanIntent)
                }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        promoteToForegroundService()
        
        val criticalDirs = listOf(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        )

        vaultObserver = VaultObserver(criticalDirs)
        vaultObserver?.startWatching()

        registerReceiver(installReceiver, IntentFilter(Intent.ACTION_PACKAGE_ADDED).apply { addDataScheme("package") })
        registerReceiver(wallpaperReceiver, IntentFilter(Intent.ACTION_WALLPAPER_CHANGED))

        startMlHeartbeat()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString() ?: return
            currentForegroundPackage = pkg

            if (pkg != packageName && !pkg.contains("com.android") && !pkg.contains("com.google.android") && !pkg.contains("systemui")) {
                uiHijackCount.incrementAndGet()
                // Deep scan UI for ransom keywords (Zero-day protection)
                scanForRansomKeywords(rootInActiveWindow)
            }

            val nodeText = event.text.toString().lowercase()
            if (nodeText.contains("device admin") || nodeText.contains("activate")) {
                deviceAdminReq.set(1)
            }
        }
    }

    private fun scanForRansomKeywords(node: AccessibilityNodeInfo?) {
        if (node == null) return
        val text = node.text?.toString()?.lowercase() ?: ""
        
        // Multi-language zero-day keywords (English + Chinese)
        val keywords = listOf(
            "ransom", "encrypted", "payment", "your files", "decrypt", "btc", "bitcoin",
            "wallet", "address", "money", "lycorisradiata", "加密", "解密", "支付", "unlock devices"
        )
        
        if (keywords.any { text.contains(it) }) {
            Log.e("RanshieldML", "🚨 RANSOM NOTE DETECTED IN UI")
            ransomTextDetected.set(1)
            triggerImmediateInference()
            return
        }

        for (i in 0 until node.childCount) {
            scanForRansomKeywords(node.getChild(i))
        }
    }

    private fun triggerImmediateInference() {
        val targetPkg = currentForegroundPackage
        if (targetPkg == "unknown" || targetPkg == packageName) return

        val dynHijack = uiHijackCount.get().toFloat()
        val dynAdmin = deviceAdminReq.get().toFloat()
        val dynFileMods = vaultObserver?.getModificationCount()?.toFloat() ?: 0f
        val ransomUI = ransomTextDetected.get().toFloat()
        val wallHit = wallpaperChanged.get().toFloat()

        val staticFeats = extractStaticFeatures(targetPkg)

        val featureVector = floatArrayOf(
            dynHijack + (ransomUI * 5) + (wallHit * 10),
            dynAdmin,
            dynFileMods,
            0f,
            staticFeats[0],
            staticFeats[1],
            staticFeats[2],
            staticFeats[3],
            staticFeats[4],
            staticFeats[5]
        )

        ThreatFusionEngine.runInference(this, targetPkg, featureVector)
    }

    private fun startMlHeartbeat() {
        mlJob = serviceScope.launch {
            while (isActive) {
                delay(10000)

                val targetPkg = currentForegroundPackage
                if (targetPkg == "unknown" || targetPkg == packageName) continue

                val dynHijack = uiHijackCount.getAndSet(0).toFloat()
                val dynAdmin = deviceAdminReq.getAndSet(0).toFloat()
                val dynFileMods = vaultObserver?.getAndResetModificationCount()?.toFloat() ?: 0f
                val ransomUI = ransomTextDetected.getAndSet(0).toFloat()
                val wallHit = wallpaperChanged.getAndSet(0).toFloat()

                val staticFeats = extractStaticFeatures(targetPkg)

                val featureVector = floatArrayOf(
                    dynHijack + (ransomUI * 5) + (wallHit * 10),
                    dynAdmin,
                    dynFileMods,
                    0f,
                    staticFeats[0],
                    staticFeats[1],
                    staticFeats[2],
                    staticFeats[3],
                    staticFeats[4],
                    staticFeats[5]
                )

                ThreatFusionEngine.runInference(this@RansomwareBehaviorService, targetPkg, featureVector)
            }
        }
    }

    private fun extractStaticFeatures(pkgName: String): FloatArray {
        val feats = FloatArray(6) { 0f }
        try {
            val packageInfo = packageManager.getPackageInfo(pkgName, PackageManager.GET_PERMISSIONS)
            val requestedPermissions = packageInfo.requestedPermissions ?: return feats
            for (perm in requestedPermissions) {
                val p = perm.uppercase()
                if (p.contains("SYSTEM_ALERT_WINDOW")) feats[0] = 1f
                if (p.contains("DISABLE_KEYGUARD")) feats[1] = 1f
                if (p.contains("WAKE_LOCK")) feats[2] = 1f
                if (p.contains("RECEIVE_BOOT_COMPLETED")) feats[3] = 1f
                if (p.contains("WRITE_EXTERNAL_STORAGE") || p.contains("MANAGE_EXTERNAL_STORAGE")) feats[4] = 1f
                if (p.contains("REQUEST_IGNORE_BATTERY_OPTIMIZATIONS")) feats[5] = 1f
            }
        } catch (e: Exception) {}
        return feats
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        mlJob?.cancel()
        vaultObserver?.stopWatching()
        try { unregisterReceiver(installReceiver) } catch (e: Exception) {}
        try { unregisterReceiver(wallpaperReceiver) } catch (e: Exception) {}
    }

    private fun promoteToForegroundService() {
        val channelId = "ranshield_protection_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Ranshield Active Protection", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Ranshield Protection is ON")
            .setContentText("Monitoring for zero-day ransomware...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()
        startForeground(101, notification)
    }
}
