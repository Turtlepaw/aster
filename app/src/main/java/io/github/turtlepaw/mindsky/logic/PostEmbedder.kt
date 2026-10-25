package io.github.turtlepaw.mindsky.logic

import android.content.Context
import com.ml.shubham0204.sentence_embeddings.SentenceEmbedding
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

class PostEmbedder(private val context: Context) {

    private var sentenceEmbedding: SentenceEmbedding? = null
    private val initMutex = Mutex()

    private suspend fun getModel(): SentenceEmbedding {
        sentenceEmbedding?.let { return it }
        return initMutex.withLock {
            // double-check after acquiring lock, in case another coroutine won the race
            sentenceEmbedding ?: run {
                val modelFile = File(context.filesDir, ModelDownloadWorker.MODEL_FILENAME)
                val tokenizerFile = File(context.filesDir, ModelDownloadWorker.TOKENIZER_FILENAME)

                SentenceEmbedding().apply {
                    init(
                        modelFilepath = modelFile.absolutePath,
                        tokenizerBytes = tokenizerFile.readBytes(),
                        useTokenTypeIds = true,
                        outputTensorName = "sentence_embedding",
                        useFP16 = false,
                        useXNNPack = true,
                        normalizeEmbeddings = true
                    )
                }.also { sentenceEmbedding = it }
            }
        }
    }

    suspend fun encode(text: String): FloatArray = getModel().encode(text)

    suspend fun close() {
        sentenceEmbedding?.close()
        sentenceEmbedding = null
    }
}