package ai.openclaw.androidagent.memory

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Primary interface to the agent's on-device memory.
 *
 * Handles:
 * - Storing and retrieving conversation history
 * - Semantic recall via cosine-similarity over embedded memory chunks
 * - Skill storage and lookup
 * - Browsing history with embedding-based retrieval
 * - Positive / negative feedback on past conversations
 */
class MemoryManager(context: Context) {

    private val db = MemoryDatabase.getInstance(context)
    private val embedding = EmbeddingEngine()
    private val scope = CoroutineScope(Dispatchers.IO)

    // ── Core memory operations ────────────────────────────────────────────────

    /**
     * Store a completed interaction.  Also creates a [MemoryChunkEntity] so the
     * interaction can be retrieved later via semantic recall.
     */
    suspend fun remember(
        userMessage: String,
        agentResponse: String,
        toolsUsed: List<String>,
        success: Boolean
    ) {
        // 1. Persist the full conversation
        val conversationId = db.conversationDao().insert(
            ConversationEntity(
                userMessage = userMessage,
                agentResponse = agentResponse,
                toolsUsed = toolsUsed,
                wasSuccessful = success
            )
        )

        // 2. Create a searchable memory chunk from the exchange
        val chunkContent = "User asked: $userMessage\nAgent answered: $agentResponse"
        val vec = embedding.embed(chunkContent)
        val importance = if (success) 0.6f else 0.4f   // failures are kept but weighted lower

        db.memoryChunkDao().insert(
            MemoryChunkEntity(
                content = chunkContent,
                embedding = vec,
                source = "conversation:$conversationId",
                importance = importance
            )
        )
    }

    /**
     * Semantic recall: find the [limit] most relevant [MemoryChunkEntity] rows
     * for [query] using cosine similarity over stored embeddings.
     */
    suspend fun recall(query: String, limit: Int = 5): List<MemoryChunkEntity> {
        val queryVec = embedding.embed(query)
        val all = db.memoryChunkDao().getAll()

        val scored = all.mapNotNull { chunk ->
            val emb = chunk.embedding ?: return@mapNotNull null
            val score = embedding.cosineSimilarity(queryVec, emb)
            Pair(chunk, score)
        }

        val top = scored
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }

        // Bump access counts asynchronously
        scope.launch {
            top.forEach { db.memoryChunkDao().incrementAccessCount(it.id) }
        }

        return top
    }

    /**
     * Store (or update) a learned skill.  If a skill with the same [name]
     * already exists its description and howToUse are refreshed.
     */
    suspend fun learnSkill(name: String, description: String, howToUse: String) {
        val existing = db.learnedSkillDao().getByName(name)
        if (existing != null) {
            db.learnedSkillDao().update(
                existing.copy(
                    description = description,
                    howToUse = howToUse,
                    lastUpdated = System.currentTimeMillis()
                )
            )
        } else {
            db.learnedSkillDao().insert(
                LearnedSkillEntity(
                    name = name,
                    description = description,
                    howToUse = howToUse
                )
            )
        }
    }

    /**
     * Build a context string for injection into the LLM system prompt.
     * Includes the top relevant memory chunks and the agent's learned skills.
     */
    suspend fun getRelevantContext(query: String): String {
        val memories = recall(query, limit = 5)
        val skills = db.learnedSkillDao().getAll().take(10)

        return buildString {
            if (memories.isNotEmpty()) {
                appendLine("=== Relevant past experiences ===")
                memories.forEachIndexed { i, chunk ->
                    appendLine("${i + 1}. ${chunk.content.take(300)}")
                }
                appendLine()
            }

            if (skills.isNotEmpty()) {
                appendLine("=== Learned skills ===")
                skills.forEach { skill ->
                    appendLine("• ${skill.name}: ${skill.description}")
                    appendLine("  How to use: ${skill.howToUse}")
                }
            }
        }.trim()
    }

    /**
     * Store a visited web page so it can be recalled in future context lookups.
     */
    suspend fun storeBrowsingMemory(url: String, title: String, content: String) {
        val summary = content.take(500)   // keep storage manageable
        val vec = embedding.embed("$title $summary")

        db.browsingHistoryDao().insert(
            BrowsingHistoryEntity(
                url = url,
                title = title,
                summary = summary,
                embedding = vec
            )
        )

        // Also add as a general memory chunk for unified recall
        db.memoryChunkDao().insert(
            MemoryChunkEntity(
                content = "Visited: $title ($url)\n$summary",
                embedding = vec,
                source = "browsing",
                importance = 0.5f
            )
        )
    }

    // ── Feedback ──────────────────────────────────────────────────────────────

    /** Mark a past conversation as good (thumbs up). */
    fun givePositiveFeedback(conversationId: Long) {
        scope.launch {
            db.conversationDao().updateFeedback(conversationId, 1)
        }
    }

    /** Mark a past conversation as bad (thumbs down). */
    fun giveNegativeFeedback(conversationId: Long) {
        scope.launch {
            db.conversationDao().updateFeedback(conversationId, 0)
        }
    }

    // ── Accessors used by SelfImprovementLoop ────────────────────────────────

    suspend fun getRecentFailed(limit: Int = 20) =
        db.conversationDao().getFailedRecent(limit)

    suspend fun getRecentConversations(limit: Int = 50) =
        db.conversationDao().getRecent(limit)

    suspend fun getAllSkills() = db.learnedSkillDao().getAll()

    suspend fun pruneOldMemories(olderThanMs: Long) {
        val before = System.currentTimeMillis() - olderThanMs
        db.memoryChunkDao().pruneStale(threshold = 0.3f, before = before)
        db.conversationDao().pruneOlderThan(before)
        db.browsingHistoryDao().pruneOlderThan(before)
    }

    // ── Companion / singleton ─────────────────────────────────────────────────

    companion object {
        @Volatile private var INSTANCE: MemoryManager? = null

        fun getInstance(context: Context): MemoryManager =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: MemoryManager(context.applicationContext).also { INSTANCE = it }
            }
    }
}
