# Scam Shield — On-Device SMS & Link Scam Detector

An offline Android application that detects fraudulent SMS messages and phishing links—and explains *why* they are risky in plain, easy-to-read bullet points. Built for the **Arm Bharat AI-SoC Student Challenge**, the entire pipeline runs locally on-device using Meta's **ExecuTorch** runtime with zero internet dependency.

## Why We Built This
Digital payment and KYC scams disproportionately target older adults and non-technical users. Existing spam filters either rely on cloud APIs (raising privacy concerns with personal banking SMS) or simply block messages without explaining what is wrong. Scam Shield keeps all user data strictly on the phone while giving clear, high-contrast warnings anyone can understand.

## Architecture & How Both Models Work Together
Scam Shield uses a two-stage local inference pipeline:

1. **Stage 1 — Fast Scam Classifier (`training/`):**
   A lightweight text and URL risk model exported to `.pte` format scans the pasted SMS in milliseconds, checking linguistic manipulation cues and suspicious domain patterns to output a `SCAM` or `SAFE` verdict with a confidence score.
2. **Stage 2 — On-Device Explanation Engine (`android/LlamaDemo/`):**
   Using **ExecuTorch** (`LlmModule`) with the **XNNPACK** CPU backend, `Llama-3.2-1B-Instruct` (quantized `8da4w`, 2k context) takes the raw SMS alongside the Stage 1 verdict and generates two concise bullet points explaining the exact red flags—or why a legitimate alert looks safe.
   * **Single-Turn Context Isolation:** The KV cache is reset (`module?.resetContext()`) before every scan so previous scam checks never bleed into subsequent messages.
   * **Dynamic Prompt Routing:** Separate system prompts are selected at runtime based on the Stage 1 verdict to keep responses focused and prevent hedging.

## On-Device Benchmarks
Tested in Airplane Mode on a **OnePlus Nord 4 (Snapdragon 7+ Gen 3, 8 GB RAM)**:

| Metric | Measured Performance |
| :--- | :--- |
| **LLM Model Size** | ~916 MB (`Llama-3.2-1B-Instruct-8da4w-gptq-2k.pte`) |
| **Cold Model Load Time** | ~0.64 seconds |
| **Generation Speed** | 20.0 – 27.4 tokens/sec |
| **End-to-End Explanation Latency** | ~2.3 – 3.8 seconds |
| **Internet / Cloud Calls** | 0 (100% offline) |

## Repository Structure
```text
ps3-ondevice-scam-shield/
├── android/
│   ├── LlamaDemo/          # Android Studio Jetpack Compose app (ExecuTorch + XNNPACK)
│   └── models/             # Local model staging directory (ignored by Git)
├── training/
│   └── models/             # Classifier training scripts, evaluation & exported .pte artifacts
├── report/
│   └── docs/               # Architecture diagrams, benchmarks, and submission documentation
└── README.md
```

## Setup & Running Locally

### 1. Push Model Weights to Your Android Device
Large `.pte` binaries are excluded from Git via `.gitignore`. Place `Llama-3.2-1B-Instruct-8da4w-gptq-2k.pte` and `tokenizer.model` in your local `Downloads` folder and push them via `adb`:

```bash
adb shell mkdir -p /data/local/tmp/llama
adb push Llama-3.2-1B-Instruct-8da4w-gptq-2k.pte /data/local/tmp/llama/
adb push tokenizer.model /data/local/tmp/llama/
```

### 2. Build & Run in Android Studio
1. Open `android/LlamaDemo` in **Android Studio** (configured with Gradle JVM 21).
2. Connect an Android device with USB Debugging enabled and click **Run (`▶`)**.
3. Tap **Check a Message** on the home screen—the app automatically loads the `.pte` model and tokenizer from `/data/local/tmp/llama/` and is ready for offline testing.

## Integrating the Classifier Model
Inside `ChatViewModel.kt`, `classifySms(smsText: String)` acts as the bridge between Stage 1 and Stage 2. To swap in a newly trained classifier `.pte`:
1. Place the exported `.pte` and vocabulary/tokenizer mapping in `/data/local/tmp/llama/` (or `app/src/main/assets/`).
2. Load the classifier via ExecuTorch's `Module.load(...)` and pass tokenized input IDs to `forward(...)` inside `classifySms()`, returning `Pair("SCAM" or "SAFE", confidencePercent)`.

## Team
* **Diwakar M** — Android App, ExecuTorch Integration & On-Device LLM Pipeline
* **Archana M** — Scam Classifier Training, URL/Manipulation Features & ExecuTorch Export
* **Durgavarshini J S** — Benchmarking, Testing & Technical Documentation

