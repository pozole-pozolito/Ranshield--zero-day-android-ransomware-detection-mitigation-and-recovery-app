package com.example.ranshield

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class RiskAlertActivity : AppCompatActivity() {

    private var packageName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // --- STAGE 3 MTD ELEVATION ---
        // Elevate this Activity to the absolute top of the Android UI stack
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            if (android.provider.Settings.canDrawOverlays(this)) {
                window.setType(android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
            }
        }

        // Keep the screen awake and locked onto the warning
        window.addFlags(
            android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    android.view.WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                    android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )
        setContentView(R.layout.activity_risk_alert)

        packageName = intent.getStringExtra("PACKAGE_NAME")
        val riskScore = intent.getIntExtra("RISK_SCORE", 0)
        val riskLevel = intent.getStringExtra("RISK_LEVEL") ?: "UNKNOWN"
        val isStage3Attack = intent.getBooleanExtra("IS_STAGE_3_ATTACK", false)

        val tvRiskScore = findViewById<TextView>(R.id.tvRiskScore)
        val tvRiskLevel = findViewById<TextView>(R.id.tvRiskLevel)
        val btnKeep = findViewById<Button>(R.id.btnKeep)
        val btnUninstall = findViewById<Button>(R.id.btnUninstall)

        tvRiskScore.text = "Risk Score: $riskScore/100"
        tvRiskLevel.text = "LEVEL: ${riskLevel.uppercase()}"

        // --- STAGE 3 DYNAMIC UI CHANGES ---

        if (isStage3Attack) {
            tvRiskLevel.text = "CRITICAL: RANSOMWARE ATTACK IN PROGRESS"
            btnKeep.text = "Recover Data (V1 Snapshot)"

            btnKeep.setOnClickListener {
                // Launch the recovery on a background thread so the UI doesn't freeze
                btnKeep.text = "Recovering Data... Please Wait"
                btnKeep.isEnabled = false // Prevent double-clicking

                CoroutineScope(Dispatchers.Main).launch {
                    val success = SnapshotManager.restoreSnapshot(this@RiskAlertActivity)
                    if (success) {
                        Toast.makeText(this@RiskAlertActivity, "✅ All files successfully restored!", Toast.LENGTH_LONG).show()
                        btnKeep.text = "Data Restored"
                    } else {
                        Toast.makeText(this@RiskAlertActivity, "❌ Recovery Failed. Snapshot missing.", Toast.LENGTH_LONG).show()
                        btnKeep.text = "Recovery Failed"
                    }
                }
            }
        } else {
            // Normal Stage 1 / Stage 2 behavior (taking the snapshot)...

            btnKeep.setOnClickListener {
                CoroutineScope(Dispatchers.Main).launch {
                    Toast.makeText(this@RiskAlertActivity, "Backing up data to Safe Zone...", Toast.LENGTH_SHORT).show()
                    val success = SnapshotManager.createSnapshot(this@RiskAlertActivity)
                    if (success) {
                        Toast.makeText(this@RiskAlertActivity, "Snapshot Saved!", Toast.LENGTH_SHORT).show()
                        finish()
                    } else {
                        Toast.makeText(this@RiskAlertActivity, "Backup Failed!", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        btnUninstall.setOnClickListener {
            uninstallPackage()
        }
    }
    private fun uninstallPackage() {
        packageName?.let {
            val intent = Intent(Intent.ACTION_DELETE)
            intent.data = Uri.parse("package:$it")
            startActivity(intent)
            finish()
        }
    }
}
