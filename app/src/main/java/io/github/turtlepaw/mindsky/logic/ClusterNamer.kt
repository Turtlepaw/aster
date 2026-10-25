package io.github.turtlepaw.mindsky.logic

import android.util.Log
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest

class ClusterNamer(
    private val labelEmbeddings: Map<String, FloatArray>
) {
    private val generativeModel by lazy { Generation.getClient() }
    private var nanoStatus: Int? = null // cache per process lifetime

    // Call this opportunistically at app startup/foreground — NOT inside FeedWorker,
    // since triggering a model download mid-background-sync is bad behavior.
    suspend fun primeNanoIfAvailable() {
        val status = generativeModel.checkStatus()
        nanoStatus = status
        if (status == FeatureStatus.DOWNLOADABLE) {
            generativeModel.download().collect { /* log progress if you want */ }
        }
        if (status == FeatureStatus.AVAILABLE) {
            generativeModel.warmup() // reduces first-inference latency
        }
    }

    suspend fun isNanoAvailable(): @FeatureStatus Int {
        return nanoStatus ?: generativeModel.checkStatus()
    }

    suspend fun nameCluster(
        centroid: FloatArray,
        sampleTexts: List<String>,
        fallbackName: String
    ): String {
        val taxonomyGuess = nameClusterFromTaxonomy(centroid, labelEmbeddings)
        val status = nanoStatus ?: generativeModel.checkStatus().also { nanoStatus = it }

        if (status != FeatureStatus.AVAILABLE) {
            Log.d("ClusterNamer", "Nano unavailable ($status), falling back to taxonomy or default")
            return taxonomyGuess ?: fallbackName
        }

        return try {
            val prompt = buildNamingPrompt(sampleTexts)
            val response = generativeModel.generateContent(
                generateContentRequest(TextPart(prompt)) {
                    temperature = 0.2f       // low randomness — this is a labeling task, not creative writing
                    maxOutputTokens = 16     // a name is a few tokens; cap hard
                }
            )

            Log.d("ClusterNamer", "Nano naming response: ${response.candidates.map { it.text }.joinToString()}")
            sanitizeClusterName(response.candidates.firstOrNull()?.text)
                ?: taxonomyGuess ?: fallbackName
        } catch (e: Exception) {
            Log.w("ClusterNamer", "Nano naming failed, falling back", e)
            taxonomyGuess ?: fallbackName
        }
    }

    private fun buildNamingPrompt(samples: List<String>): String {
        val joined = samples.take(8).joinToString("\n") { "- ${it.take(140)}" }
        return """
            Sample posts a user engaged with:
            $joined

            Respond with ONLY a short category name (2-4 words) for the shared theme.
            No explanation, no punctuation, no quotation marks.
        """.trimIndent()
    }

    private fun sanitizeClusterName(raw: String?): String? {
        val cleaned = raw?.trim()?.trim('"', '\'', '.')?.take(40) ?: return null
        val wordCount = cleaned.split(Regex("\\s+")).size
        return cleaned.takeIf { it.isNotBlank() && wordCount in 1..5 }
    }
}