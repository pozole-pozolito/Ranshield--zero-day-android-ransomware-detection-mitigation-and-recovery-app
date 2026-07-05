package com.example.ranshield

import android.content.Context
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object SnapshotManager {

    private const val TAG = "SnapshotManager"
    private const val BACKUP_FILE_NAME = "backup_v1.zip"

    /**
     * STAGE 1: Full User Data Backup
     * Zips Photos, Videos, and Documents into the secure app sandbox.
     */
    suspend fun createSnapshot(context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            val rootDir = Environment.getExternalStorageDirectory()
            val targetDirs = listOf(
                File(rootDir, "DCIM"),
                File(rootDir, "Pictures"),
                File(rootDir, "Movies"),
                File(rootDir, "Music"),
                File(rootDir, "Documents"),
                File(rootDir, "Download")
            )

            val secureFolder = File(context.filesDir, "safe_snapshots")
            if (!secureFolder.exists()) secureFolder.mkdirs()

            val zipFile = File(secureFolder, BACKUP_FILE_NAME)
            if (zipFile.exists()) zipFile.delete() // Drop older snapshot if it exists

            Log.d(TAG, "Starting Full V1 Snapshot... This may take a moment.")

            ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zos ->
                for (dir in targetDirs) {
                    if (dir.exists()) {
                        zipDirectory(dir, dir.name, zos)
                    }
                }
            }

            Log.d(TAG, "✅ V1 Snapshot Complete! Saved to ${zipFile.absolutePath}")
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to create V1 snapshot (Check storage space)", e)
            return@withContext false
        }
    }

    private fun zipDirectory(dir: File, basePath: String, zos: ZipOutputStream) {
        val files = dir.listFiles() ?: return
        for (file in files) {
            if (file.isDirectory) {
                zipDirectory(file, "$basePath/${file.name}", zos)
            } else {
                FileInputStream(file).use { fis ->
                    BufferedInputStream(fis).use { bis ->
                        val entry = ZipEntry("$basePath/${file.name}")
                        zos.putNextEntry(entry)
                        bis.copyTo(zos)
                        zos.closeEntry()
                    }
                }
            }
        }
    }

    /**
     * STAGE 3: The Data Recovery Engine
     * Unzips backup_v1.zip and overwrites the ransomware's encrypted files.
     */
    suspend fun restoreSnapshot(context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            val secureFolder = File(context.filesDir, "safe_snapshots")
            val zipFile = File(secureFolder, BACKUP_FILE_NAME)

            if (!zipFile.exists()) {
                Log.e(TAG, "❌ No V1 Snapshot found to restore!")
                return@withContext false
            }

            Log.d(TAG, "Starting Data Recovery from V1 Snapshot...")
            val rootDir = Environment.getExternalStorageDirectory()

            ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val targetFile = File(rootDir, entry.name)

                    if (entry.isDirectory) {
                        targetFile.mkdirs()
                    } else {
                        targetFile.parentFile?.mkdirs()
                        // Overwrite the corrupted/encrypted file with the clean backup
                        FileOutputStream(targetFile).use { fos ->
                            BufferedOutputStream(fos).use { bos ->
                                zis.copyTo(bos)
                            }
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            Log.d(TAG, "✅ Data Recovery Complete! All user files restored.")
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to restore V1 snapshot", e)
            return@withContext false
        }
    }
}