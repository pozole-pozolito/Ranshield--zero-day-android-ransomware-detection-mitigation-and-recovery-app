package com.example.ranshield

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.text.TextUtils
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {

    private lateinit var tvScannedCount: TextView
    private lateinit var tvThreatsCount: TextView
    private lateinit var prefs: SharedPreferences

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            checkManageExternalStorage()
        } else {
            Toast.makeText(this, "Permissions required for protection and recovery", Toast.LENGTH_LONG).show()
        }
    }

    private val manageStorageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        checkOverlayPermission()
    }

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        checkDeviceAdmin()
    }

    private val deviceAdminLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        checkAccessibilityService()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        tvScannedCount = findViewById(R.id.tvScannedCount)
        tvThreatsCount = findViewById(R.id.tvThreatsCount)
        prefs = getSharedPreferences("RanshieldPrefs", Context.MODE_PRIVATE)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        findViewById<Button>(R.id.btnQuickScan).setOnClickListener {
            showAppListDialog()
        }
        
        updateStatsUI()
        startPermissionFlow()
    }

    override fun onResume() {
        super.onResume()
        updateStatsUI()
    }

    private fun updateStatsUI() {
        tvScannedCount.text = prefs.getInt("scanned_count", 0).toString()
        tvThreatsCount.text = prefs.getInt("threats_count", 0).toString()
    }

    private fun showAppListDialog() {
        val pm = packageManager
        val packages = pm.getInstalledPackages(PackageManager.GET_META_DATA)
            .filter { it.applicationInfo != null && (it.applicationInfo!!.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0 }
            .filter { it.packageName != packageName }
            .sortedBy { it.applicationInfo?.let { info -> pm.getApplicationLabel(info).toString().lowercase() } ?: "" }

        if (packages.isEmpty()) {
            Toast.makeText(this, "No third-party apps found to scan", Toast.LENGTH_SHORT).show()
            return
        }

        val appNames = packages.map { it.applicationInfo?.let { info -> pm.getApplicationLabel(info).toString() } ?: "Unknown" }.toTypedArray()
        
        AlertDialog.Builder(this)
            .setTitle("Select App to Scan (Stage 1)")
            .setItems(appNames) { _, which ->
                val selectedPkg = packages[which].packageName
                val intent = Intent(this, ScanPromptActivity::class.java).apply {
                    putExtra("PACKAGE_NAME", selectedPkg)
                }
                startActivity(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun startPermissionFlow() {
        checkAndRequestStoragePermissions()
    }

    private fun checkAndRequestStoragePermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            requestPermissionLauncher.launch(missingPermissions.toTypedArray())
        } else {
            checkManageExternalStorage()
        }
    }

    private fun checkManageExternalStorage() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                showPermissionDialog(
                    "All Files Access Required",
                    "Ranshield needs 'All Files Access' to create secure snapshots and monitor file changes.",
                    {
                        try {
                            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                            intent.data = Uri.parse("package:$packageName")
                            manageStorageLauncher.launch(intent)
                        } catch (e: Exception) {
                            val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                            manageStorageLauncher.launch(intent)
                        }
                    },
                    { checkOverlayPermission() }
                )
            } else {
                checkOverlayPermission()
            }
        } else {
            checkOverlayPermission()
        }
    }

    private fun checkOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            showPermissionDialog(
                "Display Over Other Apps",
                "To block ransomware instantly, Ranshield needs the 'Display over other apps' permission.",
                {
                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                    overlayPermissionLauncher.launch(intent)
                },
                { checkDeviceAdmin() }
            )
        } else {
            checkDeviceAdmin()
        }
    }

    private fun checkDeviceAdmin() {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent = ComponentName(this, MyDeviceAdminReceiver::class.java)
        if (!dpm.isAdminActive(adminComponent)) {
            showPermissionDialog(
                "Device Admin Required",
                "To prevent malicious apps from uninstalling themselves or changing your password, Ranshield needs Device Admin access.",
                {
                    val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                        putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                        putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Ranshield needs admin access to prevent unauthorized uninstallation of security-critical apps.")
                    }
                    deviceAdminLauncher.launch(intent)
                },
                { checkAccessibilityService() }
            )
        } else {
            checkAccessibilityService()
        }
    }

    private fun checkAccessibilityService() {
        if (!isAccessibilityServiceEnabled()) {
            showPermissionDialog(
                "Protection Service Required",
                "To detect malicious app installations and behavior, please enable the Ranshield Accessibility Service.",
                {
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    startActivity(intent)
                },
                { finalizeFlow() }
            )
        } else {
            finalizeFlow()
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expectedComponentName = "$packageName/${RansomwareBehaviorService::class.java.name}"
        val enabledServices = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        if (enabledServices == null) return false
        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServices)
        while (colonSplitter.hasNext()) {
            val componentName = colonSplitter.next()
            if (componentName.equals(expectedComponentName, ignoreCase = true)) {
                return true
            }
        }
        return false
    }

    private fun showPermissionDialog(title: String, message: String, onPositive: () -> Unit, onNegative: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Grant Permission") { _, _ -> onPositive() }
            .setNegativeButton("Later") { _, _ -> onNegative() }
            .setCancelable(false)
            .show()
    }

    private fun finalizeFlow() {
        Toast.makeText(this, "Ranshield Protection Active", Toast.LENGTH_SHORT).show()
    }
}
