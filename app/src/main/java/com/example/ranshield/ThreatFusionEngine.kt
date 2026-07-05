package com.example.ranshield

import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Stage 2: The ML Inference Engine.
 * Utilizes a natively compiled XGBoost Java model and applies Zero-Day Behavioral Heuristics.
 */
object ThreatFusionEngine {
    private const val TAG = "ThreatFusionEngine"
    private const val STRIKE_THRESHOLD = 3
    private val strikeMap = mutableMapOf<String, Int>()

    private val currentlyLockedPackages = ConcurrentHashMap.newKeySet<String>()

    private val whitelist = setOf(
        "com.android.settings",
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "com.android.systemui",
        "com.google.android.gsf",
        "com.google.android.gms",
        "com.google.android.apps.nexuslauncher",
        "com.google.android.googlequicksearchbox",
        "com.google.android.permissioncontroller",
        "com.android.permissioncontroller",
        "com.google.android.inputmethod.latin",
        "com.android.inputmethod",
        "android",
        "com.google.android.documentsui"
    )

    fun runInference(context: Context, packageName: String, featureVector: FloatArray) {
        if (packageName == context.packageName || 
            whitelist.any { packageName.startsWith(it) } || 
            currentlyLockedPackages.contains(packageName)) {
            return
        }

        try {
            val doubleFeatures = DoubleArray(featureVector.size) { i -> featureVector[i].toDouble() }
            val predictions = RansomwarePredictor.score(doubleFeatures)
            val ransomwareProbability = if (predictions.size > 1) predictions[1] else predictions[0]

            val hijackScore = featureVector[0].toInt()
            val fileBehaviorScore = featureVector[2].toInt()
            val hasOverlayPermission = featureVector[4] > 0
            val requestsAdmin = featureVector[1] > 0

            Log.d(TAG, "🔎 Behavioral Scan [$packageName]: ML: ${(ransomwareProbability * 100).toInt()}% | FileScore: $fileBehaviorScore | Hijack: $hijackScore")

            // --- ZERO-DAY BEHAVIORAL HEURISTICS ---

            // 1. Critical File Attack: If bait files are touched or massive modifications occur
            if (fileBehaviorScore >= 5) {
                Log.e(TAG, "🚨 ZERO-DAY: Critical file activity detected for $packageName")
                triggerLockdown(context, packageName, 100)
                return
            }

            // 2. High-Risk UI Hijack: Blocking screen + requesting Admin/Sensitive perms
            if (hasOverlayPermission && requestsAdmin) {
                Log.e(TAG, "🚨 ZERO-DAY: Persistent UI Hijacking attempt detected for $packageName")
                triggerLockdown(context, packageName, 100)
                return
            }

            // 3. Ransom UI/Wallpaper Detection: Immediate trigger if ransom text or wallpaper change detected
            if (hijackScore >= 5) {
                Log.e(TAG, "🚨 ZERO-DAY: Ransom Note or Wallpaper Change detected for $packageName")
                triggerLockdown(context, packageName, 100)
                return
            }

            // 4. Probabilistic Strike Logic
            if (ransomwareProbability > 0.4 || hijackScore > 0) {
                val currentStrikes = (strikeMap[packageName] ?: 0) + 1
                strikeMap[packageName] = currentStrikes

                Log.w(TAG, "⚠️ STRIKE $currentStrikes for $packageName!")

                val dynamicThreshold = when {
                    ransomwareProbability > 0.90 -> 1
                    ransomwareProbability > 0.70 || hijackScore >= 2 -> 2
                    else -> STRIKE_THRESHOLD
                }

                if (currentStrikes >= dynamicThreshold) {
                    Log.e(TAG, "💀 THRESHOLD REACHED. TRIGGERING HARD LOCKDOWN.")
                    triggerLockdown(context, packageName, (ransomwareProbability * 100).toInt())
                    strikeMap.remove(packageName)
                }
            } else {
                if ((strikeMap[packageName] ?: 0) > 0) {
                    strikeMap[packageName] = 0
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Inference failed", e)
        }
    }

    private fun triggerLockdown(context: Context, pkgName: String, confidence: Int) {
        if (currentlyLockedPackages.contains(pkgName)) return
        currentlyLockedPackages.add(pkgName)

        CoroutineScope(Dispatchers.Main).launch {
            if (context is android.accessibilityservice.AccessibilityService) {
                context.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME)
            }

            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
            val params = android.view.WindowManager.LayoutParams(
                android.view.WindowManager.LayoutParams.MATCH_PARENT,
                android.view.WindowManager.LayoutParams.MATCH_PARENT,
                android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                        android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        android.view.WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD,
                android.graphics.PixelFormat.TRANSLUCENT
            ).apply { screenBrightness = 1.0f }

            val layout = android.widget.LinearLayout(context).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                setBackgroundColor(android.graphics.Color.parseColor("#B71C1C"))
                gravity = android.view.Gravity.CENTER
                setPadding(50, 50, 50, 50)
                isClickable = true
                isFocusable = true
            }

            val title = TextView(context).apply {
                text = "CRITICAL RANSOMWARE ATTACK\n(AI Confidence: $confidence%)"
                setTextColor(android.graphics.Color.WHITE)
                textSize = 24f
                gravity = android.view.Gravity.CENTER
                setPadding(0, 0, 0, 80)
            }

            val statusText = TextView(context).apply {
                text = "Malware '$pkgName' detected. ACTION REQUIRED."
                setTextColor(android.graphics.Color.YELLOW)
                textSize = 16f
                gravity = android.view.Gravity.CENTER
                setPadding(0, 0, 0, 40)
            }

            val btnUninstall = Button(context).apply {
                text = "Eradicate Malware"
                setBackgroundColor(android.graphics.Color.BLACK)
                setTextColor(android.graphics.Color.WHITE)
            }

            val btnFixAdmin = Button(context).apply {
                text = "Deactivate Malware Admin First"
                setBackgroundColor(android.graphics.Color.YELLOW)
                setTextColor(android.graphics.Color.BLACK)
                visibility = View.GONE
            }

            val btnRecover = Button(context).apply {
                text = "Recover Data (V1 Snapshot)"
                setBackgroundColor(android.graphics.Color.WHITE)
                setTextColor(android.graphics.Color.BLACK)
                visibility = View.GONE
            }

            val btnExit = Button(context).apply {
                text = "FINISHED - RETURN TO HOME"
                setBackgroundColor(android.graphics.Color.DKGRAY)
                setTextColor(android.graphics.Color.WHITE)
                visibility = View.GONE
                setPadding(0, 40, 0, 0)
                setOnClickListener {
                    currentlyLockedPackages.remove(pkgName)
                    try { windowManager.removeView(layout) } catch (e: Exception) {}
                }
            }

            val uninstallReceiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    if (intent?.action == Intent.ACTION_PACKAGE_REMOVED) {
                        val removedPkg = intent.data?.encodedSchemeSpecificPart
                        if (removedPkg == pkgName) {
                            currentlyLockedPackages.remove(pkgName)
                            btnUninstall.visibility = View.GONE
                            btnFixAdmin.visibility = View.GONE
                            btnRecover.visibility = View.VISIBLE
                            title.text = "THREAT ERADICATED"
                            title.setTextColor(android.graphics.Color.GREEN)
                            statusText.text = "App removed. You can now restore your data."
                            layout.setBackgroundColor(android.graphics.Color.parseColor("#1B5E20"))
                            try { context.unregisterReceiver(this) } catch (e: Exception) {}
                        }
                    }
                }
            }

            context.registerReceiver(uninstallReceiver, IntentFilter(Intent.ACTION_PACKAGE_REMOVED).apply { addDataScheme("package") })

            btnUninstall.setOnClickListener {
                val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                val adminComponent = ComponentName(context, MyDeviceAdminReceiver::class.java)
                if (dpm.isAdminActive(adminComponent)) {
                    try { dpm.lockNow() } catch (e: Exception) {}
                }

                try { windowManager.removeView(layout) } catch (e: Exception) {}

                val uninstallIntent = Intent(Intent.ACTION_DELETE, android.net.Uri.parse("package:$pkgName")).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(uninstallIntent)

                CoroutineScope(Dispatchers.Main).launch {
                    delay(8000) 
                    if (isPackageInstalled(context, pkgName)) {
                        try { windowManager.addView(layout, params) } catch (e: Exception) {}
                        statusText.text = "If uninstall failed, the app might be a Device Admin. Click the yellow button below."
                        btnFixAdmin.visibility = View.VISIBLE
                    }
                }
            }

            btnFixAdmin.setOnClickListener {
                try { windowManager.removeView(layout) } catch (e: Exception) {}
                val intent = Intent()
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                val possibleActions = listOf("android.settings.DEVICE_ADMIN_SETTINGS", Settings.ACTION_SECURITY_SETTINGS, Settings.ACTION_SETTINGS)
                for (action in possibleActions) {
                    try {
                        intent.action = action
                        context.startActivity(intent)
                        break
                    } catch (e: Exception) {}
                }

                CoroutineScope(Dispatchers.Main).launch {
                    delay(15000)
                    if (isPackageInstalled(context, pkgName)) {
                        try { windowManager.addView(layout, params) } catch (e: Exception) {}
                        statusText.text = "Find '$pkgName' in the list and turn it OFF. Then try 'Eradicate' again."
                    }
                }
            }

            btnRecover.setOnClickListener {
                val btn = it as Button
                btn.text = "Recovering Data..."
                btn.isEnabled = false
                CoroutineScope(Dispatchers.IO).launch {
                    val success = SnapshotManager.restoreSnapshot(context)
                    withContext(Dispatchers.Main) {
                        if (success) {
                            btn.text = "Data Restored Successfully!"
                            btnExit.visibility = View.VISIBLE
                        } else {
                            btn.text = "Recovery Failed"
                            btn.isEnabled = true
                        }
                    }
                }
            }

            layout.addView(title)
            layout.addView(statusText)
            layout.addView(btnUninstall)
            layout.addView(btnFixAdmin)
            layout.addView(btnRecover)
            layout.addView(btnExit)

            try {
                windowManager.addView(layout, params)
            } catch (e: Exception) {
                Log.e(TAG, "❌ Overlay failed", e)
                currentlyLockedPackages.remove(pkgName)
            }
        }
    }

    private fun isPackageInstalled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }
}
