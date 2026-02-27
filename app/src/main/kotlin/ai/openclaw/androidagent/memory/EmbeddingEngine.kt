package ai.openclaw.androidagent.memory

import kotlin.math.sqrt

/**
 * On-device text embedding engine.
 *
 * Uses a TF-IDF / word-frequency approach that runs entirely on-device with no
 * network calls or native libraries required.  The vocabulary is built lazily
 * from every text that is embedded; similarity is measured with cosine distance.
 *
 * This is intentionally a lightweight implementation so the app stays
 * self-contained.  Drop-in replacement with a TFLite Universal Sentence
 * Encoder is straightforward: override [embed] and keep [cosineSimilarity].
 */
class EmbeddingEngine {

    // ── Vocabulary ────────────────────────────────────────────────────────────

    /** word → index in the embedding vector */
    private val vocab = mutableMapOf<String, Int>()

    /** document-frequency counts for IDF weighting */
    private val docFreq = mutableMapOf<String, Int>()

    /** total number of documents seen (used for IDF) */
    private var docCount = 0

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Embed [text] into a float vector.
     *
     * The first call seeds the vocabulary; subsequent calls grow it
     * incrementally so vectors are always comparable via [cosineSimilarity].
     */
    fun embed(text: String): FloatArray {
        val tokens = tokenize(text)
        updateVocabAndIdf(tokens)

        // TF-IDF vector over current vocabulary
        val vector = FloatArray(vocab.size)
        val tf = termFrequency(tokens)

        for ((word, idx) in vocab) {
            val tfVal = tf[word] ?: 0f
            val idfVal = idf(word)
            vector[idx] = tfVal * idfVal
        }

        return l2normalize(vector)
    }

    /** Cosine similarity in [−1, 1]; returns 0 for zero-length vectors. */
    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        // Align lengths — vocab may have grown between embeds
        val len = maxOf(a.size, b.size)
        val aP = a.copyOf(len)
        val bP = b.copyOf(len)

        var dot = 0.0
        var normA = 0.0
        var normB = 0.0
        for (i in 0 until len) {
            dot += aP[i] * bP[i]
            normA += aP[i] * aP[i]
            normB += bP[i] * bP[i]
        }

        val denom = sqrt(normA) * sqrt(normB)
        return if (denom < 1e-10) 0f else (dot / denom).toFloat()
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private fun tokenize(text: String): List<String> =
        text.lowercase()
            .replace(Regex("[^a-z0-9 ]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length > 2 }          // drop very short tokens
            .filter { it !in STOP_WORDS }

    private fun updateVocabAndIdf(tokens: List<String>) {
        docCount++
        val unique = tokens.toSet()
        for (word in unique) {
            if (word !in vocab) {
                vocab[word] = vocab.size
            }
            docFreq[word] = (docFreq[word] ?: 0) + 1
        }
    }

    private fun termFrequency(tokens: List<String>): Map<String, Float> {
        val counts = mutableMapOf<String, Int>()
        for (t in tokens) counts[t] = (counts[t] ?: 0) + 1
        val total = tokens.size.toFloat().coerceAtLeast(1f)
        return counts.mapValues { it.value / total }
    }

    private fun idf(word: String): Float {
        val df = docFreq[word] ?: 1
        return Math.log((docCount.toDouble() + 1) / (df.toDouble() + 1)).toFloat() + 1f
    }

    private fun l2normalize(v: FloatArray): FloatArray {
        val norm = sqrt(v.map { it * it }.sum().toDouble()).toFloat()
        return if (norm < 1e-10f) v else FloatArray(v.size) { v[it] / norm }
    }

    companion object {
        /** Common English stop-words excluded from embeddings */
        private val STOP_WORDS = setOf(
            "the", "and", "for", "are", "but", "not", "you", "all",
            "can", "her", "was", "one", "our", "out", "day", "get",
            "has", "him", "his", "how", "man", "new", "now", "old",
            "see", "two", "way", "who", "boy", "did", "its", "let",
            "put", "say", "she", "too", "use", "that", "this", "with",
            "have", "from", "they", "will", "been", "than", "more",
            "also", "into", "some", "just", "then", "when", "what",
            "your", "does", "each", "about", "which", "their", "there"
        )
    }
}
