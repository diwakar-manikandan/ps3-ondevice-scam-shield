# Scam Shield — On-Device SMS & Link Scam Detector

An offline Android application that detects fraudulent SMS messages and phishing links—and explains *why* they are risky in plain, easy-to-read bullet points. Built for the **Arm Bharat AI-SoC Student Challenge**, the entire pipeline runs locally on-device using Meta's **ExecuTorch** runtime with zero internet dependency.

## Why We Built This
Digital payment, KYC, and "digital arrest" scams disproportionately target older adults and non-technical users. Existing spam filters either rely on cloud APIs (raising privacy concerns with personal banking SMS) or simply block messages without explaining what is wrong. Scam Shield keeps all user data strictly on the phone while presenting high-contrast, plain-English warnings anyone can understand.

## Architecture: 3-Stage Grounded On-Device Pipeline
To prevent small language models from hallucinating red flags while maintaining millisecond detection speeds, Scam Shield separates detection, evidence extraction, and explanation into three local stages:

1. **Stage 1 — Hybrid ML & Behavioral Classifier (`ScamClassifier.kt` + `scam_classifier.pte`):**
   * Replicates a 5,000-feature TF-IDF vectorizer (`(1, 2)`-grams, sublinear TF, L2 normalization) in Kotlin and runs a quantized Logistic Regression model via ExecuTorch (`Module.load`) in `<15 ms`.
   * Combines the ML probability (`0.4` weight) with behavioral risk indicators (`0.6` weight + high-risk floors for OTP/PIN requests, money transfers, and arrest threats) to produce a `SCAM` or `SAFE` verdict and confidence score.
2. **Stage 2 — Deterministic Evidence Extraction (`ScamRules.kt`):**
   * Extracts exact, grounded red-flag evidence from the SMS (arrest threats, urgent money demands, OTP/PIN harvesting, KYC/account suspension threats, suspicious links) so the LLM only explains verified signals present in the message.
   * Wraps untrusted SMS text inside `<sms>...</sms>` tags to defend against prompt-injection attacks and appends deterministic safety guidance (including India's national cybercrime helpline **1930** and **cybercrime.gov.in**).
3. **Stage 3 — Grounded Natural-Language Explanation (`Llama-3.2-1B-Instruct`):**
   * Runs `Llama-3.2-1B-Instruct-8da4w-gptq-2k.pte` via ExecuTorch (`LlmModule`) on the **XNNPACK** CPU backend to turn the extracted evidence into concise, senior-friendly bullet points.
   * Resets the KV cache (`module?.resetContext()`) before every scan so previous scam checks never bleed into subsequent messages.

## On-Device Benchmarks
Tested in Airplane Mode on a **OnePlus Nord 4 (Snapdragon 7+ Gen 3, 8 GB RAM)**:

| Metric | Measured Performance |
| :--- | :--- |
| **Stage 1 Classifier Size & Latency** | `21 KB` (`scam_classifier.pte`) • `<15 ms` |
| **Stage 3 LLM Size** | `~916 MB` (`Llama-3.2-1B-Instruct-8da4w-gptq-2k.pte`) |
| **Cold Model Load Time** | `~0.64 – 0.92 seconds` |
| **LLM Generation Speed** | `20.0 – 29.0 tokens/sec` (peak) |
| **End-to-End Explanation Latency** | `~2.3 – 4.0 seconds` |
| **Internet / Cloud Calls** | `0` (100% offline) |

## Repository Structure
```text
ps3-ondevice-scam-shield/
├── android/
│   ├── LlamaDemo/          # Android Studio Jetpack Compose app (ExecuTorch + XNNPACK)
│   └── models/             # Local model staging directory (ignored by Git)
├── training/
│   └── models/
│       └── ScamSense_Classifier_Integration/  # TF-IDF vocab, IDF weights, rules & Python reference
├── report/
│   └── docs/               # Architecture diagrams, benchmarks, and submission documentation
└── README.md
```

## Setup & Running Locally

### 1. Push Both Models & Vocabulary Files to Your Android Device
Large `.pte` binaries are excluded from Git via `.gitignore`. Push the LLM weights (`Llama-3.2-1B-Instruct-8da4w-gptq-2k.pte` + `tokenizer.model`) and the Stage 1 classifier artifacts (`scam_classifier.pte`, `vocabulary.json`, `idf_weights.json`) to `/data/local/tmp/llama/`:

```bash
adb shell mkdir -p /data/local/tmp/llama

# Stage 3 LLM weights & tokenizer
adb push Llama-3.2-1B-Instruct-8da4w-gptq-2k.pte /data/local/tmp/llama/
adb push tokenizer.model /data/local/tmp/llama/

# Stage 1 Classifier model & TF-IDF lookup files (from training/models/ScamSense_Classifier_Integration/)
adb push training/models/ScamSense_Classifier_Integration/scam_classifier.pte /data/local/tmp/llama/
adb push training/models/ScamSense_Classifier_Integration/vocabulary.json /data/local/tmp/llama/
adb push training/models/ScamSense_Classifier_Integration/idf_weights.json /data/local/tmp/llama/
```

### 2. Build & Run in Android Studio
1. Open `android/LlamaDemo` in **Android Studio** (configured with Gradle JVM 21).
2. Connect an Android device with USB Debugging enabled and click **Run (`▶`)**.
3. Tap **Check a Message** on the home screen—the app automatically loads the `.pte` models from `/data/local/tmp/llama/` and is ready for offline testing in Airplane Mode.

## Team
* **Diwakar Manikandan** — Android App, ExecuTorch Integration & Grounded On-Device Pipeline
* **Archana** — Scam Classifier Training, TF-IDF Feature Engineering & ExecuTorch Export
* **Durgavarshini** — Benchmarking, Testing & Technical Documentation
