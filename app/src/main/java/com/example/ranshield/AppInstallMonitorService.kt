package com.example.ranshield

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class AppInstallMonitorService : AccessibilityService() {

    private var lastCheckedEventKey: String? = null

    private val installReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_PACKAGE_ADDED) {
                val pkgName = intent.data?.encodedSchemeSpecificPart
                Log.d("RANSHIELD", "Broadcast Received: Package Added -> $pkgName")
                if (pkgName != null && pkgName != packageName) {
                    detectNewInstallation("Dynamic Broadcast Receiver")
                }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d("RANSHIELD", "Watchdog Active: Listening for installs...")
        
        // Register the receiver dynamically within the service to bypass manifest restrictions
        val filter = IntentFilter(Intent.ACTION_PACKAGE_ADDED).apply {
            addDataScheme("package")
        }
        registerReceiver(installReceiver, filter)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val sourcePackage = event.packageName?.toString() ?: "unknown"
        if (sourcePackage == packageName) return

        // Fallback: Still check on window changes
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {

            val nodeInfo = event.source
            if (nodeInfo != null && checkForInstallKeywords(nodeInfo)) {
                detectNewInstallation("Keyword Match ($sourcePackage)")
            }
            
            if (sourcePackage.contains("packageinstaller", ignoreCase = true)) {
                 detectNewInstallation("Package Installer Active")
            }
        }
    }

    private fun checkForInstallKeywords(node: AccessibilityNodeInfo): Boolean {
        val keywords = listOf("App installed", "Open", "Success", "installed", "Done")
        for (word in keywords) {
            if (node.findAccessibilityNodeInfosByText(word).isNotEmpty()) {
                return true
            }
        }
        return false
    }

    private fun detectNewInstallation(reason: String) {
        val pm = packageManager
        val packages = pm.getInstalledPackages(0)
        
        val latest = packages
            .filter { it.packageName != packageName }
            .maxByOrNull { Math.max(it.firstInstallTime, it.lastUpdateTime) }

        val latestPkgName = latest?.packageName
        if (latestPkgName != null) {
            val now = System.currentTimeMillis()
            val eventTime = Math.max(latest.firstInstallTime, latest.lastUpdateTime)
            val diff = now - eventTime
            val eventKey = "$latestPkgName:$eventTime"

            if (eventKey != lastCheckedEventKey) {
                Log.d("RANSHIELD", "New Package Detected ($reason): $latestPkgName, Time Diff: ${diff/1000}s")
                
                // Allow a larger window for background installs
                if (diff < 600000) { 
                    Log.d("RANSHIELD", "Launching Scan Prompt for: $latestPkgName")
                    lastCheckedEventKey = eventKey
                    showScanPrompt(latestPkgName)
                }
            }
        }
    }

    private fun showScanPrompt(pkgName: String) {
        val intent = Intent(this, ScanPromptActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or 
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or 
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("PACKAGE_NAME", pkgName)
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Log.e("RANSHIELD", "Failed to start ScanPromptActivity", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(installReceiver)
        } catch (e: Exception) {
            Log.e("RANSHIELD", "Error unregistering receiver", e)
        }
    }

    override fun onInterrupt() {}
}
