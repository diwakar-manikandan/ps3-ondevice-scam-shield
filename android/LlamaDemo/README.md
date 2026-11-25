# ScamShield — On-Device Threat, Phishing & Scam Detection

**ScamShield** is a privacy-first, zero-cloud-dependency Android security application engineered for on-device detection and plain-language explanation of SMS phishing, scam communications, and digital fraud threats. Developed for **Problem Statement 3 (PS3)** of the **Bharat AI-SoC Challenge 2026–27**, ScamShield deploys a 4-bit quantized **Llama 3.2 1B** model locally on Arm-based edge hardware using Meta's **ExecuTorch** runtime engine.

By combining deterministic threat-extraction rules with PyTorch ExecuTorch XNNPACK CPU execution on Snapdragon 7+ Gen 3 (Arm v9) hardware, ScamShield achieves sub-second model cold-start initialization (**0.967 seconds**) and high-throughput local generation (**12.58 tokens/second**), operating completely air-gapped without transmitting user data or telemetry to external servers.

---

## Executive Overview & Benchmarks

* **100% Air-Gapped Privacy**: Operates entirely on-device with zero internet dependency, verified under active Airplane Mode.
* **Sub-Second Cold-Start**: Model weights (`Llama-3.2-1B-Instruct-8da4w-gptq-2k.pte`) and tokenizer initialize in **0.967 seconds**.
* **High Token Throughput**: Achieves sustained text generation speeds of **12.58 tokens/second** on mobile Arm Cortex cores.
* **Low Memory Footprint**: Operates within a minimal RAM budget (<1 GB execution footprint), preventing thermal throttling and device slowdowns.
* **Dual-Tier Hybrid Engine**: Integrates deterministic regex threat pattern matching (`ScamRules.kt`) with deep contextual reasoning from quantized Llama 3.2 1B (`ChatViewModel.kt`).

---

## Problem Statement Alignment

ScamShield directly addresses **Problem Statement 3: On-Device Threat, Phishing & Scam Detection** under the **Cybersecurity & Digital Safety** theme of the Bharat AI-SoC Challenge 2026–27, organized by the **Ministry of Electronics and Information Technology (MeitY C2S Programme)**, **Arm**, **IIT Delhi**, **UK Technology Security Initiative (TSI)**, and **Meta**.

### Core Requirements Delivered:
1. **On-Device Edge Deployment**: Deploys a Llama 3.2 1B Instruct model on Arm-based Android hardware using ExecuTorch.
2. **Local Threat Classification**: Evaluates incoming SMS messages, phishing links, and financial scam communications locally without cloud API dependencies.
3. **Plain-Language Explanations**: Generates concise, non-technical risk bullet points explaining why a message is fraudulent.
4. **Actionable Remediation**: Delivers safety-hardened advice including reporting via official reporting portals.

---

## System Architecture & Processing Pipeline

ScamShield implements a multi-tier hybrid architecture designed to minimize latency while preventing prompt injection vulnerabilities.

```
+-----------------------------------------------------------------------------------+
|                                 Incoming SMS / Text                                |
+-----------------------------------------------------------------------------------+
                                          |
                                          v
+-----------------------------------------------------------------------------------+
| Stage 1: Fast Heuristic & Risk Scoring (ScamClassifier.kt)                        |
| - Evaluates initial risk score and confidence rating (0-100%)                      |
+-----------------------------------------------------------------------------------+
                                          |
                                          v
+-----------------------------------------------------------------------------------+
| Stage 2: Deterministic Threat Extraction (ScamRules.kt)                           |
| - Utility Disconnection Regex (Electricity / Power bill cutoff threats)           |
| - Unofficial Contact Number Pattern Matching                                      |
| - Digital Coercion & Fake Legal Notice Patterns                                   |
| - Banking KYC / Account Block Phishing Patterns                                   |
| - Credential & OTP Theft Detection                                                |
+-----------------------------------------------------------------------------------+
                                          |
                                          v
+-----------------------------------------------------------------------------------+
| Stage 3: Grounded LLM Explanation (ExecuTorch + Llama 3.2 1B 4-bit GPTQ)          |
| - Formats prompt inside isolated untrusted container tags (<sms>...</sms>)        |
| - Executes local inference via ExecuTorch XNNPACK Arm CPU Backend                 |
| - Cleans and formats structured explanation bullets and helpline advice            |
+-----------------------------------------------------------------------------------+
```

### Deterministic Threat Extraction (`ScamRules.kt`)
The heuristic engine evaluates critical digital threat vectors common in mobile communications:
* **Digital Coercion & Fake Legal Notices**: Detects fake agency, legal action, or warrant threats.
* **Urgent Money Transfers**: Identifies forced financial transfer requests.
* **Credential & OTP Theft**: Identifies attempts to harvest OTPs, PINs, passwords, or security codes.
* **Banking KYC & Account Blocking**: Catches claims regarding suspended bank accounts or expired credentials.
* **Utility Disconnection Scams**: Flags urgent threats to disconnect power or electricity services coupled with personal mobile numbers.
* **Phishing URLs & Malicious Software**: Identifies unverified links and requests to install remote access applications or standalone packages.

---

## Hardware & Edge Optimization

* **Target Device**: OnePlus Nord 4 (Qualcomm Snapdragon 7+ Gen 3 SoC, Arm v9 architecture).
* **Reference Runtime**: PyTorch **ExecuTorch** C++/Java runtime extension (`org.pytorch.executorch.extension.llm.LlmModule`).
* **Execution Backend**: **XNNPACK** CPU engine utilizing Arm NEON vector SIMD instructions for optimized matrix multiplication.
* **Model Quantization**: **4-bit GPTQ Quantization** (`Llama-3.2-1B-Instruct-8da4w-gptq-2k.pte`), reducing weight storage to ~937 MB for rapid memory loading.

---

## Repository Layout

```
ps3-ondevice-scam-shield/
├── android/
│   └── LlamaDemo/
│       ├── app/
│       │   ├── src/
│       │   │   └── main/
│       │   │       ├── java/com/example/executorchllamademo/
│       │   │       │   ├── ScamRules.kt           # Deterministic threat extraction & prompt builder
│       │   │       │   ├── ScamClassifier.kt      # ML classification scoring module
│       │   │       │   ├── ETLogging.kt           # System performance and logging utility
│       │   │       │   └── ui/viewmodel/
│       │   │       │       └── ChatViewModel.kt   # Inference coordinator & ExecuTorch callback
│       │   │       ├── res/                       # App icons, themes, and string assets
│       │   │       └── AndroidManifest.xml        # Android application manifest configuration
│       │   └── build.gradle                       # App-level dependencies and NDK setup
│       └── build.gradle                           # Project-level build script
└── README.md                                      # Primary repository documentation
```

---

## Setup and Installation

### Prerequisites
* **Android Studio**: Android Studio Ladybug / Koala or newer with NDK and CMake configured.
* **Target Device**: Arm-based Android smartphone running API 34 (Android 14) or higher.
* **Model Artifacts**:
  * ExecuTorch Model File: `Llama-3.2-1B-Instruct-8da4w-gptq-2k.pte` (~937 MB)
  * Tokenizer Model File: `tokenizer.model` (~2.1 MB)

### Step-by-Step Instructions

1. **Clone the Repository**:
   ```powershell
   git clone <repository-url>
   cd ps3-ondevice-scam-shield
   ```

2. **Open Project in Android Studio**:
   Open `android/LlamaDemo` in Android Studio and perform a Gradle Sync.

3. **Deploy Model Artifacts to Phone Storage**:
   Push the compiled ExecuTorch model and tokenizer to your Android device storage:
   ```powershell
   adb push Llama-3.2-1B-Instruct-8da4w-gptq-2k.pte /sdcard/Download/
   adb push tokenizer.model /sdcard/Download/
   ```

4. **Build and Run**:
   Connect your target device over USB and build the debug application:
   ```powershell
   cd android/LlamaDemo
   ./gradlew assembleDebug
   ```
   The generated APK will be available at `app/build/outputs/apk/debug/app-debug.apk`.

---

## Verification Scenarios

### Scenario 1: Utility Disconnection Scam (High Threat)
* **Input Text**:
  > `Dear Customer, your electricity bill of Rs. 4,850 is unpaid. Power will be disconnected tonight at 9:30 PM. Call officer at 9876543210 immediately.`
* **System Verdict**:
  * **Badge**: `[SCAM • 95% confidence]`
  * **Extracted Red Flags**: Utility disconnection threat & request to call an unofficial number.
  * **Generated Explanation**: Official power utility departments do not issue personal mobile phone numbers for emergency bill payments over SMS.
  * **Actionable Guidance**: Don't click links, don't share OTP/PIN, and report suspicious incidents at official cybercrime reporting portals.

### Scenario 2: Banking KYC Phishing (High Threat)
* **Input Text**:
  > `ALERT: Your bank account will be blocked today due to pending PAN/KYC verification. Update now at unverified link to keep services active.`
* **System Verdict**:
  * **Badge**: `[SCAM • 95% confidence]`
  * **Extracted Red Flags**: Account block threat & unverified link.
  * **Generated Explanation**: Financial institutions never require credential or PAN updates via unverified SMS web links.

### Scenario 3: Legitimate Bank Credit Alert (Safe)
* **Input Text**:
  > `Your account xx4892 has been credited with Rs. 2,500.00 via UPI Ref 412356789102. Available balance: Rs. 14,230.50.`
* **System Verdict**:
  * **Badge**: `[SAFE • 70% confidence]`
  * **Summary**: The customer's account received a credit of Rs 2,500 via UPI.
  * **Guidance**: No scam signs detected. Verify using official banking apps.

---

## Technical Metrics & Performance Summary

| Metric | Measured Benchmark | Verification Method |
| :--- | :--- | :--- |
| **Model Cold-Start Time** | **0.967 sec** | Native ExecuTorch initialization log |
| **Inference Speed** | **12.58 tokens/sec** | Token evaluation throughput profiler |
| **Network Isolation** | **0 KB Transmitted** | Verified under active Airplane Mode |
| **Model Disk Footprint** | **937.4 MB** | 4-bit GPTQ Quantized `.pte` format |
| **Memory Consumption** | **< 1 GB RAM** | Android `ActivityManager` hardware profiling |

---

## Team & Organization

* **Event**: Bharat AI-SoC Challenge 2026–27 (2nd Edition)
* **Mentor Institute**: IIT Delhi
* **Partners**: Ministry of Electronics and Information Technology (MeitY C2S), Arm, UK Government TSI, Meta.
