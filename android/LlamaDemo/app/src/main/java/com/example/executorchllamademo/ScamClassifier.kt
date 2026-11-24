package com.example.executorchllamademo

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import org.pytorch.executorch.EValue
import org.pytorch.executorch.Module
import org.pytorch.executorch.Tensor
import java.io.File
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

class ScamClassifier(
    private val baseDir: String = "/data/local/tmp/llama"
) {
    private var module: Module? = null
    private var vocabMap: Map<String, Int> = emptyMap()
    private var idfArray: FloatArray = FloatArray(5000)
    private var isInitialized = false

    // Exact token regex from preprocessing_config.json: "(?u)\\b\\w\\w+\\b"
    private val tokenRegex = Regex("\\b\\w\\w+\\b")

    // Behavioral rules from detection_rules.json
    private val urgencyPatterns = listOf(
        "\\bimmediately\\b", "\\burgent\\b", "\\bpay now\\b", "\\btonight\\b",
        "\\bact now\\b", "\\bwithin \\d+ hours?\\b", "\\blast chance\\b", "\\bexpires? today\\b"
    ).map { Regex(it, RegexOption.IGNORE_CASE) }

    private val fearPatterns = listOf(
        "\\bblocked\\b", "\\bsuspended\\b", "\\bsuspension\\b", "\\bdeactivated\\b",
        "\\bdisconnected\\b", "\\barrest\\b", "\\blegal action\\b", "\\billegal items\\b", "\\bpenalty\\b"
    ).map { Regex(it, RegexOption.IGNORE_CASE) }

    private val authorityPatterns = listOf(
        "\\bpolice\\b", "\\bgovernment\\b", "\\bbank\\b", "\\bsbi\\b",
        "\\bcbi\\b", "\\brbi\\b", "\\bcustoms\\b", "\\bincome tax\\b"
    ).map { Regex(it, RegexOption.IGNORE_CASE) }

    private val financialPatterns = listOf(
        "\\btransfer money\\b", "\\btransfer rs\\b", "\\bpay rs\\b", "\\bpay now\\b",
        "\\bpay a penalty\\b", "\\bpay processing fee\\b", "\\bpay registration fee\\b",
        "\\bdeposit money\\b", "\\bsend money\\b"
    ).map { Regex(it, RegexOption.IGNORE_CASE) }

    private val credentialPatterns = listOf(
        "\\bshare (your )?otp\\b", "\\benter (your )?upi pin\\b", "\\bsubmit (your )?password\\b",
        "\\bverify (your )?banking details\\b", "\\bprovide (your )?pin\\b", "\\bshare (your )?password\\b"
    ).map { Regex(it, RegexOption.IGNORE_CASE) }

    private val rewardPatterns = listOf(
        "\\blottery\\b", "\\bcash prize\\b", "\\bcongratulations\\b", "\\byou won\\b",
        "\\bearn rs\\b", "\\breward points\\b", "\\bfree reward\\b"
    ).map { Regex(it, RegexOption.IGNORE_CASE) }

    private val safetyPatterns = listOf(
        "\\bnever share\\b", "\\bdo not share\\b", "\\bdon't share\\b",
        "\\bdo not enter\\b", "\\bdon't enter\\b", "\\bnever enter\\b"
    ).map { Regex(it, RegexOption.IGNORE_CASE) }

    private val urlShorteners = listOf("bit.ly", "cutt.ly", "is.gd", "rb.gy", "t.co", "tinyurl.com")
    private val urlKeywords = listOf("verify", "login", "kyc", "password", "otp", "refund", "reward", "update", "secure", "claim")

    @Synchronized
    fun ensureLoaded(): Boolean {
        if (isInitialized && module != null) return true
        return try {
            val modelFile = File("$baseDir/scam_classifier.pte")
            val vocabFile = File("$baseDir/vocabulary.json")
            val idfFile = File("$baseDir/idf_weights.json")

            if (!modelFile.exists() || !vocabFile.exists() || !idfFile.exists()) {
                Log.e("ScamClassifier", "Missing classifier files in $baseDir")
                return false
            }

            // 1. Load vocabulary.json (term -> feature index 0..4999)
            val vocabJson = JSONObject(vocabFile.readText())
            val parsedVocab = HashMap<String, Int>(vocabJson.length())
            val keys = vocabJson.keys()
            while (keys.hasNext()) {
                val term = keys.next()
                parsedVocab[term] = vocabJson.getInt(term)
            }
            vocabMap = parsedVocab

            // 2. Load idf_weights.json (supports JSONArray or index-keyed JSONObject)
            val rawIdf = idfFile.readText().trim()
            val weights = FloatArray(5000) { 1.0f }
            if (rawIdf.startsWith("[")) {
                val arr = JSONArray(rawIdf)
                for (i in 0 until minOf(arr.length(), 5000)) {
                    weights[i] = arr.getDouble(i).toFloat()
                }
            } else {
                val obj = JSONObject(rawIdf)
                val idfKeys = obj.keys()
                while (idfKeys.hasNext()) {
                    val k = idfKeys.next()
                    val idx = k.toIntOrNull()
                    if (idx != null && idx in 0 until 5000) {
                        weights[idx] = obj.getDouble(k).toFloat()
                    }
                }
            }
            idfArray = weights

            // 3. Load ExecuTorch .pte model
            module = Module.load(modelFile.absolutePath)
            isInitialized = true
            Log.i("ScamClassifier", "Loaded scam_classifier.pte (${vocabMap.size} terms)")
            true
        } catch (e: Exception) {
            Log.e("ScamClassifier", "Failed to load scam_classifier.pte", e)
            false
        }
    }

    /**
     * Exact replication of scikit-learn TfidfVectorizer:
     * lowercase -> \b\w\w+\b -> (1,2)-grams -> sublinear_tf -> * idf -> L2 norm
     */
    private fun vectorizeTfIdf(text: String): FloatArray {
        val lower = text.lowercase()
        val unigrams = tokenRegex.findAll(lower).map { it.value }.toList()

        val counts = HashMap<Int, Int>()
        for (i in unigrams.indices) {
            vocabMap[unigrams[i]]?.let { idx ->
                counts[idx] = (counts[idx] ?: 0) + 1
            }
            if (i + 1 < unigrams.size) {
                val bigram = "${unigrams[i]} ${unigrams[i + 1]}"
                vocabMap[bigram]?.let { idx ->
                    counts[idx] = (counts[idx] ?: 0) + 1
                }
            }
        }

        val vector = FloatArray(5000)
        var sumSq = 0.0
        for ((idx, tf) in counts) {
            if (idx in 0 until 5000 && tf > 0) {
                val sublinearTf = 1.0 + ln(tf.toDouble())
                val tfidf = (sublinearTf * idfArray[idx]).toFloat()
                vector[idx] = tfidf
                sumSq += tfidf * tfidf
            }
        }

        if (sumSq > 0.0) {
            val norm = sqrt(sumSq).toFloat()
            for (i in 0 until 5000) {
                if (vector[i] != 0f) {
                    vector[i] /= norm
                }
            }
        }
        return vector
    }

    /**
     * Evaluates behavioral rules from detection_rules.json (0..100 scale)
     */
    private fun computeBehavioralScore(text: String): Float {
        val lower = text.lowercase()
        val hasSafetyAdvisory = safetyPatterns.any { it.containsMatchIn(text) }

        var score = 0f
        val hasUrgency = urgencyPatterns.any { it.containsMatchIn(text) }
        val hasFear = fearPatterns.any { it.containsMatchIn(text) }
        val hasAuthority = authorityPatterns.any { it.containsMatchIn(text) }
        val hasFinancial = financialPatterns.any { it.containsMatchIn(text) }
        val hasCredential = credentialPatterns.any { it.containsMatchIn(text) } && !hasSafetyAdvisory
        val hasReward = rewardPatterns.any { it.containsMatchIn(text) }

        if (hasUrgency) score += 15f
        if (hasFear) score += 20f
        if (hasAuthority) score += 5f
        if (hasFinancial) score += 25f
        if (hasCredential) score += 25f
        if (hasReward) score += 10f

        val hasUrl = lower.contains("http://") || lower.contains("https://") || lower.contains("www.")
        if (hasUrl) {
            if (urlShorteners.any { lower.contains(it) }) score += 25f
            if (urlKeywords.any { lower.contains(it) }) score += 20f
            if (lower.contains(".xyz") || lower.contains(".top") || lower.contains(".buzz")) score += 20f
        }

        // High-risk floors from detection_rules.json
        if (hasCredential) score = maxOf(score, 85f)
        if (hasFinancial) score = maxOf(score, 75f)
        if (hasFear && hasUrgency) score = maxOf(score, 60f)

        return score.coerceIn(0f, 100f)
    }

    fun classify(smsText: String): Pair<String, Int> {
        val loaded = ensureLoaded()
        var mlProbability = 0.25f // Safe default if model file isn't found

        if (loaded && module != null) {
            try {
                val features = vectorizeTfIdf(smsText)
                val inputTensor = Tensor.fromBlob(features, longArrayOf(1, 5000))
                val outputs = module!!.forward(EValue.from(inputTensor))
                val logit = outputs[0].toTensor().dataAsFloatArray[0]
                mlProbability = (1.0f / (1.0f + exp(-logit)))
                Log.i("ScamClassifier", "scam_classifier.pte logit=$logit, prob=$mlProbability")
            } catch (e: Exception) {
                Log.e("ScamClassifier", "Inference error on scam_classifier.pte", e)
            }
        }

        val mlScorePercent = mlProbability * 100f
        val behaviorScore = computeBehavioralScore(smsText)

        // Hybrid fusion from Archana's detection_rules.json: 0.4 * ML + 0.6 * Behavioral
        var combinedRisk = (0.4f * mlScorePercent) + (0.6f * behaviorScore)

        // Legitimate bank receipt / advisory safeguard (e.g., "debited from A/c", "UPI Ref", "1800" toll-free, no URL)
        val lower = smsText.lowercase()
        val hasUrl = lower.contains("http://") || lower.contains("https://") || lower.contains("www.") || lower.contains(".xyz") || lower.contains("bit.ly")
        val looksLikeStandardReceipt = !hasUrl && behaviorScore == 0f &&
                (lower.contains("debited from") || lower.contains("credited to") || lower.contains("upi ref") || lower.contains("1800"))

        if (looksLikeStandardReceipt) {
            combinedRisk = minOf(combinedRisk, 22f)
        }

        val isScam = combinedRisk >= 50f

        return if (isScam) {
            val confidence = maxOf(combinedRisk, behaviorScore, mlScorePercent)
                .toInt().coerceIn(65, 99)
            Pair("SCAM", confidence)
        } else {
            val safeConfidence = (100f - combinedRisk).toInt().coerceIn(70, 99)
            Pair("SAFE", safeConfidence)
        }
    }

}
