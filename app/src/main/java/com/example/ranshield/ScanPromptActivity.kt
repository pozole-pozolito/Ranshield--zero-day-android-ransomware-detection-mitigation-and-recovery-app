package com.example.ranshield

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit

class ScanPromptActivity : AppCompatActivity() {

    private lateinit var tvAppName: TextView
    private lateinit var ivAppIcon: ImageView
    private lateinit var btnScan: Button
    private lateinit var btnCancel: Button
    private lateinit var tvStatus: TextView
    private lateinit var promptContainer: View
    private lateinit var scanningContainer: View

    private var targetPackageName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scan_prompt)

        targetPackageName = intent.getStringExtra("PACKAGE_NAME")
        if (targetPackageName == null) {
            finish()
            return
        }

        tvAppName = findViewById(R.id.tvAppName)
        ivAppIcon = findViewById(R.id.ivAppIcon)
        btnScan = findViewById(R.id.btnScan)
        btnCancel = findViewById(R.id.btnCancel)
        tvStatus = findViewById(R.id.tvStatus)
        promptContainer = findViewById(R.id.promptContainer)
        scanningContainer = findViewById(R.id.scanningContainer)

        val pm = packageManager
        try {
            val appInfo = pm.getApplicationInfo(targetPackageName!!, 0)
            tvAppName.text = pm.getApplicationLabel(appInfo).toString()
            ivAppIcon.setImageDrawable(pm.getApplicationIcon(appInfo))
        } catch (e: Exception) {
            tvAppName.text = targetPackageName
        }

        btnCancel.setOnClickListener { finish() }

        btnScan.setOnClickListener {
            startUpload()
        }
    }

    private fun startUpload() {
        promptContainer.visibility = View.GONE
        scanningContainer.visibility = View.VISIBLE
        
        tvStatus.text = "Initializing Shield..."

        lifecycleScope.launch {
            val result = uploadApk()
            if (result != null) {
                // UPDATE STATS
                val prefs = getSharedPreferences("RanshieldPrefs", Context.MODE_PRIVATE)
                val currentScanned = prefs.getInt("scanned_count", 0)
                prefs.edit().putInt("scanned_count", currentScanned + 1).apply()

                val score = result.getScore()
                // A threat is an app with a LOW security score (meaning HIGH risk)
                // Threshold: score <= 40 is considered a threat
                if (score <= 40 || result.riskLevel == "High" || result.riskLevel == "Critical") {
                    val currentThreats = prefs.getInt("threats_count", 0)
                    prefs.edit().putInt("threats_count", currentThreats + 1).apply()
                }

                Log.d("ScanPromptActivity", "Success! Displaying Score: $score, Level: ${result.riskLevel}")
                
                tvStatus.text = "SCAN COMPLETE!"
                val intent = Intent(this@ScanPromptActivity, RiskAlertActivity::class.java).apply {
                    putExtra("PACKAGE_NAME", targetPackageName)
                    putExtra("RISK_SCORE", score)
                    putExtra("RISK_LEVEL", result.riskLevel ?: "UNKNOWN")
                }
                startActivity(intent)
                finish()
            } else {
                tvStatus.text = "Scan failed."
                promptContainer.visibility = View.VISIBLE
                scanningContainer.visibility = View.GONE
                Toast.makeText(this@ScanPromptActivity, "Security Scan Failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private suspend fun uploadApk(): ScanResponse? = withContext(Dispatchers.IO) {
        try {
            val pm = packageManager
            val appInfo = pm.getApplicationInfo(targetPackageName!!, 0)
            val packageInfo = pm.getPackageInfo(targetPackageName!!, 0)
            val apkFile = File(appInfo.sourceDir)

            withContext(Dispatchers.Main) {
                tvStatus.text = "SECURELY UPLOADING..."
            }

            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }
            
            val client = OkHttpClient.Builder()
                .addInterceptor(logging)
                .connectTimeout(5, TimeUnit.MINUTES)
                .readTimeout(5, TimeUnit.MINUTES)
                .writeTimeout(5, TimeUnit.MINUTES)
                .build()

            val retrofit = Retrofit.Builder()
                .baseUrl("https://subfractionary-raelyn-exhaustingly.ngrok-free.dev/")
                .addConverterFactory(GsonConverterFactory.create())
                .client(client)
                .build()

            val service = retrofit.create(ApiService::class.java)

            val requestFile = apkFile.asRequestBody("application/vnd.android.package-archive".toMediaTypeOrNull())
            val body = MultipartBody.Part.createFormData("file", apkFile.name, requestFile)

            val pkgName = targetPackageName!!.toRequestBody("text/plain".toMediaTypeOrNull())
            val appName = pm.getApplicationLabel(appInfo).toString().toRequestBody("text/plain".toMediaTypeOrNull())
            val versionName = (packageInfo.versionName ?: "unknown").toRequestBody("text/plain".toMediaTypeOrNull())
            
            val vCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            }
            val versionCode = vCode.toString().toRequestBody("text/plain".toMediaTypeOrNull())

            val response = service.uploadApk(body, pkgName, appName, versionName, versionCode)

            withContext(Dispatchers.Main) {
                tvStatus.text = "FINALIZING ANALYSIS..."
            }

            if (response.isSuccessful) {
                val scanResponse = response.body()
                Log.d("ScanPromptActivity", "Raw Server Response: $scanResponse")
                scanResponse
            } else {
                val errorMsg = response.errorBody()?.string()
                Log.e("ScanPromptActivity", "Upload error: ${response.code()} $errorMsg")
                null
            }
        } catch (e: Exception) {
            Log.e("ScanPromptActivity", "Error during upload", e)
            null
        }
    }
}
