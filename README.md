# 🛡️ Ranshield
##Tri-Stage Android Ransomware Defense & Recovery System
Ranshield is a robust, non-root mobile security application designed to detect, intercept, and recover from evasive Android ransomware. By combining remote static analysis with localized, dual-engine runtime heuristics, Ranshield provides a zero-trust safety net for user data—even against zero-day threats.

## 🧠 The Architecture (3-Stage Defense)
Ranshield operates on a continuous monitoring loop, divided into three distinct defense phases:
### Stage 1: Pre-Encryption Detection (Static Analysis)
When a new application is installed, Ranshield intercepts the broadcast event. Before the user can launch the potentially malicious app, Ranshield freezes the UI and prompts for a security scan.<br>
•	The Engine: The APK is transmitted to a remote Python Flask server via Retrofit.<br>
•	The Analysis: The server feeds the APK into the Mobile Security Framework (MobSF) for deep static analysis, parsing the output to generate a Risk Score (0-100).<br>
•	The Pre-Emptive Strike: If the user ignores a high-risk warning and chooses to keep the app, Ranshield immediately generates a "V1 Snapshot"—a secure, zipped backup of critical user directories hidden inside the app's isolated /data/data partition.<br>
### Stage 2: Runtime Behavior Detection (Dual-Sensor Heuristics)
Once a suspicious app is trusted, Ranshield shifts to silent, continuous monitoring using Android's Accessibility Services. It watches for live attack behaviors using two distinct sensors:<br>
•	Sensor A (UI Heuristics): Monitors TYPE_WINDOW_STATE_CHANGED events. If an unknown third-party app aggressively hijacks screen focus to display a persistent lock screen (Screen-Lock Ransomware), it logs a "UI Anomaly".<br>
•	Sensor B (File Heuristics): A VaultObserver monitors a hidden system decoy folder (Documents/Ranshield_Vault). If rapid, successive file modifications occur within a rolling time window, it logs a "File Anomaly".<br>
### Stage 3: Threat Fusion & Targeted Entropy (Data Recovery)
When an anomaly is detected, Ranshield's Threat Fusion Engine initiates a localized cryptographic check to confirm active encryption without crashing the device's memory.<br>
•	The Math: It calculates the SHA-256 Hash and Shannon Entropy (H(X)) of the files in the targeted vault.<br>
•	The Decision Matrix: * If the hash changes but entropy is low (< 7.0), it is a benign user edit. Ranshield drops the old backup and takes a new V1 Snapshot.<br>
o	If the hash changes and entropy is high (>= 7.0), the files are being encrypted into random noise.<br>
•	The Lockdown: Ranshield instantly protects the clean V1 Snapshot, bypasses the malware's screen lock using high-priority intents, and forces a critical Red Alert UI to the foreground, allowing the user to safely uninstall the ransomware and recover their uncorrupted data.
 
## 🛠️ Tech Stack
•	Android: Kotlin, Android SDK, Coroutines, AccessibilityService API, FileObserver.<br>
•	Networking: OkHttp, Retrofit2, Ngrok.<br>
•	Backend: Python, Flask, PyPDF<br>
•	Analysis Engine: Mobile Security Framework (MobSF), XGBoost.
 
## 🚀 Installation & Setup
### Part 1: Backend Server (Python/MobSF)
1.	Ensure MobSF is running locally on port 8000.<br>
2.	Navigate to the server directory and install dependencies:<br>
<p align="center">``<code>pip install flask requests pypdf</code>``</p><br>
3.	Update the MOBSF_API_KEY in server.py with your local MobSF REST API key.<br>
4.	Run the server:<br>
<p align="center">``<code>python server.py</code>``</p><br>
5.	(Optional) Expose the server to the internet using Ngrok: ngrok http 5000.<br>
### Part 2: Android Application
1.	Clone this repository and open it in Android Studio.<br>
2.	Build and install the APK on a physical device or emulator (API 29+ recommended).<br>
3.	CRITICAL: Go to Android Settings > Accessibility > Ranshield and turn the service ON to enable Stage 2 heuristics.<br>
 
## 🧪 How to Test (Live Demo Guide)
Because live ransomware is unpredictable, Ranshield is designed to allow manual triggering of its heuristic traps for demonstration purposes.<br>
1.	Test Stage 1: Drag and drop any APK onto the emulator. Ranshield will catch the install and prompt the server scan.<br>
2.	Test Stage 2 (UI Hijack): Open any standard 3rd-party app. Rapidly press the Home button and reopen the app 6 times in a row. Ranshield will detect the focus-stealing anomaly and trigger the math engine.<br>
3.	Test Stage 3 (Encryption/Entropy): * Navigate to Documents/Ranshield_Vault in your file manager.<br>
o	Open a text file and replace its readable contents with a massive block of randomized, scrambled characters (simulating high-entropy encryption).<br>
o	Trigger the UI Hijack again. Ranshield will calculate the new entropy, confirm the attack, and throw the critical lockdown screen.<br>
 

### Limitations and Future Scope

While the current Accessibility-driven architecture successfully provides a robust, non-root defense mechanism, the original conceptual workflow of Ranshield envisioned a deeper, machine-learning-driven approach. Our pivot to the current heuristic model was a strategic decision to overcome several hard limitations in modern mobile security development.<br>
### Current Limitations
•	OS-Level Intent Restrictions: Newer Android builds (API 30+) strictly sandbox inter-process communication. The OS prevents background apps from passively sniffing implicit intents generated by other packages. This forced our pivot from Intent-Metadata monitoring to the current UI/Accessibility heuristic model.<br>
•	ML Dataset Scarcity & Version Clashes: Training a reliable on-device TF Lite XGBoost model requires massive, highly specific datasets of modern ransomware intent metadata. Current public datasets are often outdated, leading to APK version clashes and high false-positive rates when tested against modern malware structures.<br>
•	Resource-Bound Race Conditions: Calculating cryptographic hashes and Shannon Entropy across an entire storage partition during an active attack takes too much CPU time on standard mobile hardware. We scoped Stage 3 to a "Targeted Vault" to guarantee flawless performance without crashing the device before the ransomware finishes encrypting.<br>  

### Future Scope (The Path to V2.0)
If the hurdles of dataset availability and OS-level sandboxing can be bypassed (potentially by deploying Ranshield via Enterprise Device Administration APIs, MDM profiles, or a custom Android ROM), the future iteration of the app will fully realize the original ML workflow:<br>
•	On-Device XGBoost Engine: Replacing the manual threshold heuristics in Stage 2 with a localized TF Lite model that dynamically classifies malicious intent metadata in real-time to generate a live risk score.<br>
•	True Pre-Write Interception: Upgrading Stage 3 from a reactionary entropy check to a system-level pre-write interceptor, effectively pausing the malicious process before a single byte of genuine user data is touched, allowing for system-wide snapshots instead of localized vaults.<br>
•	Zero-Touch Auto-Suspension: Evolving beyond the manual "Uninstall" prompt by utilizing elevated system privileges to silently kill the ransomware process the millisecond the risk threshold exceeds 40%. <br>
 
