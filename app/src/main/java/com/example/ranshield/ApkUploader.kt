package com.example.ranshield

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import android.util.Log

object ApkUploader {

    suspend fun uploadApk(
        filePath: String,
        packageName: String,
        appName: String,
        versionName: String,
        versionCode: String
    ) {
        val file = File(filePath)
        if (!file.exists()) {
            Log.e("ApkUploader", "File does not exist: $filePath")
            return
        }

        // Create RequestBody from the file
        val requestFile = file.asRequestBody("application/vnd.android.package-archive".toMediaTypeOrNull())

        // Create MultipartBody.Part from the RequestBody
        // CRITICAL FIX: The first parameter "file" MUST match 'file' in server.py
        val body = MultipartBody.Part.createFormData("file", file.name, requestFile)

        val pkgName = packageName.toRequestBody("text/plain".toMediaTypeOrNull())
        val appNamePart = appName.toRequestBody("text/plain".toMediaTypeOrNull())
        val versionNamePart = versionName.toRequestBody("text/plain".toMediaTypeOrNull())
        val versionCodePart = versionCode.toRequestBody("text/plain".toMediaTypeOrNull())

        try {
            val response = RetrofitClient.apiService.uploadApk(
                body,
                pkgName,
                appNamePart,
                versionNamePart,
                versionCodePart
            )
            if (response.isSuccessful) {
                Log.d("ApkUploader", "Upload successful: ${response.code()}")
            } else {
                Log.e("ApkUploader", "Upload failed: ${response.errorBody()?.string()}")
            }
        } catch (e: Exception) {
            Log.e("ApkUploader", "Error uploading file", e)
        }
    }
}
