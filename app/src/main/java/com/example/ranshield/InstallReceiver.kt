package com.example.ranshield

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class InstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent == null || context == null) return

        if (intent.action == Intent.ACTION_PACKAGE_ADDED) {
            val packageName = intent.data?.encodedSchemeSpecificPart
            Log.d("RANSHIELD", "Package Installed (Broadcast): $packageName")
            
            if (packageName != null && packageName != context.packageName) {
                showScanPrompt(context, packageName)
            }
        }
    }

    private fun showScanPrompt(context: Context, pkgName: String) {
        val intent = Intent(context, ScanPromptActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("PACKAGE_NAME", pkgName)
        }
        context.startActivity(intent)
    }
}
