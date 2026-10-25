package io.github.turtlepaw.mindsky.logic

object ClusterTaxonomy {
    val CANDIDATE_LABELS = listOf(
        "Technology", "Gaming", "Music", "Art & Design", "Photography",
        "Sports", "Food & Cooking", "Science", "Politics & News",
        "Humor & Memes", "Books & Writing", "Movies & TV", "Fashion",
        "Fitness & Health", "Travel", "Nature & Animals", "Finance",
        "Relationships", "Local Community", "Activism"
        // extend as you see what clusters actually need
    )

    // Compute once (e.g. at app startup or first use), cache in memory
    suspend fun embedLabels(embedder: PostEmbedder): Map<String, FloatArray> =
        CANDIDATE_LABELS.associateWith { embedder.encode(it) }
}

fun nameClusterFromTaxonomy(
    centroid: FloatArray,
    labelEmbeddings: Map<String, FloatArray>,
    confidenceThreshold: Float = 0.35f
): String? {
    val best = labelEmbeddings.maxByOrNull { (_, labelEmbedding) ->
        cosineSimilarity(centroid, labelEmbedding)
    } ?: return null

    val score = cosineSimilarity(centroid, best.value)
    return if (score >= confidenceThreshold) best.key else null
}

private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
    require(a.size == b.size) { "Vectors must be same size" }

    var dotProduct = 0f
    var normA = 0f
    var normB = 0f
    for (i in a.indices) {
        dotProduct += a[i] * b[i]
        normA += a[i] * a[i]
        normB += b[i] * b[i]
    }

    return if (normA == 0f || normB == 0f) {
        0f
    } else {
        dotProduct / (kotlin.math.sqrt(normA) * kotlin.math.sqrt(normB))
    }
}