package com.example.ranshield

import android.os.FileObserver
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicInteger

/**
 * Stage 2 (ML Engine): High-Sensitivity Tripwire and Mass-Modification System.
 * Monitors for specific bait files AND rapid file system changes.
 */
class VaultObserver(private val rootDirs: List<File>) {

    private val totalModifications = AtomicInteger(0)
    private val baitHits = AtomicInteger(0)
    private val observers = mutableListOf<InternalObserver>()

    private val baitFiles = listOf(
        "000_Private_Keys.txt",
        "00_Wedding_Backup.jpg",
        "0_Tax_Documents_2024.pdf"
    )

    init {
        for (dir in rootDirs) {
            if (!dir.exists()) dir.mkdirs()
            plantBait(dir)
            
            val observer = InternalObserver(dir.absolutePath)
            observers.add(observer)
            observer.startWatching()
        }
        Log.d("VaultObserver", "🛡️ Behavioral File Monitor Active across ${rootDirs.size} zones.")
    }

    private fun plantBait(dir: File) {
        try {
            for (baitName in baitFiles) {
                val file = File(dir, baitName)
                if (!file.exists()) {
                    FileOutputStream(file).use { fos ->
                        fos.write("Security Bait. Do not modify.".toByteArray())
                    }
                }
            }
        } catch (e: Exception) {}
    }

    fun startWatching() = observers.forEach { it.startWatching() }
    fun stopWatching() = observers.forEach { it.stopWatching() }

    /**
     * Returns a weighted score based on behavior without resetting.
     */
    fun getModificationCount(): Int {
        val baits = baitHits.get()
        val general = totalModifications.get()
        return (baits * 10) + (if (general > 15) 5 else 0)
    }

    /**
     * Returns a weighted score based on behavior:
     * - Bait files touched = extreme priority
     * - High frequency of general changes = suspected encryption loop
     */
    fun getAndResetModificationCount(): Int {
        val baits = baitHits.getAndSet(0)
        val general = totalModifications.getAndSet(0)
        
        // Weight: 1 bait hit is worth 10 general mods
        val finalScore = (baits * 10) + (if (general > 15) 5 else 0)
        
        if (finalScore > 0) {
            Log.d("VaultObserver", "📊 File Behavior Score: $finalScore (Baits: $baits, Activity: $general)")
        }
        return finalScore
    }

    private inner class InternalObserver(path: String) : 
        FileObserver(path, MODIFY or DELETE or MOVED_TO) {
        
        override fun onEvent(event: Int, path: String?) {
            if (path == null) return

            if (baitFiles.contains(path)) {
                baitHits.incrementAndGet()
                Log.e("VaultObserver", "🚨 ZERO-DAY ALERT: Bait file $path was attacked!")
            } else {
                totalModifications.incrementAndGet()
            }
        }
    }
}
