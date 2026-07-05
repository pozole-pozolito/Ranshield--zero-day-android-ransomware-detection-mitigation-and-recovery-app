package com.example.ranshield

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import java.io.File

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("Ranshield", "📱 Device booted. Ranshield BootReceiver activated.")

            // Re-plant the tripwires immediately on boot just in case they were cleared
            plantTripwires()
        }
    }

    private fun plantTripwires() {
        try {
            val vaultDir = File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOCUMENTS), "!000_System_Cache")
            if (!vaultDir.exists()) vaultDir.mkdirs()

            val tripwireNames = listOf("!000_A_DecoyPhoto.jpg", "!000_A_TaxReturn.pdf", "!000_A_Passwords.txt")
            for (name in tripwireNames) {
                val file = File(vaultDir, name)
                if (!file.exists()) {
                    file.writeText("This is a Ranshield decoy file.")
                }
            }
            Log.d("Ranshield", "✅ Boot Sequence: Tripwires secured.")
        } catch (e: Exception) {
            Log.e("Ranshield", "❌ Boot Sequence: Failed to plant tripwires", e)
        }
    }
}