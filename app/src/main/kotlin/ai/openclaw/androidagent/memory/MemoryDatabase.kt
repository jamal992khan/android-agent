package ai.openclaw.androidagent.memory

import android.content.Context
import androidx.room.*

// ─── Type Converters ──────────────────────────────────────────────────────────

class Converters {
    @TypeConverter
    fun fromFloatArray(value: FloatArray?): ByteArray? {
        if (value == null) return null
        val bytes = ByteArray(value.size * 4)
        val buf = java.nio.ByteBuffer.wrap(bytes)
        for (f in value) buf.putFloat(f)
        return bytes
    }

    @TypeConverter
    fun toFloatArray(value: ByteArray?): FloatArray? {
        if (value == null) return null
        val floats = FloatArray(value.size / 4)
        val buf = java.nio.ByteBuffer.wrap(value)
        for (i in floats.indices) floats[i] = buf.float
        return floats
    }

    @TypeConverter
    fun fromStringList(value: List<String>?): String? = value?.joinToString("|")

    @TypeConverter
    fun toStringList(value: String?): List<String>? =
        if (value.isNullOrEmpty()) emptyList() else value.split("|")
}

// ─── Entities ─────────────────────────────────────────────────────────────────

/** Full interaction history — every user↔agent exchange */
@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val userMessage: String,
    val agentResponse: String,
    val toolsUsed: List<String> = emptyList(),
    val wasSuccessful: Boolean = true,
    /** -1 = no feedback, 0 = negative, 1 = positive */
    val feedbackScore: Int = -1
)

/** Semantic memory chunks for RAG retrieval */
@Entity(tableName = "memory_chunks")
data class MemoryChunkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val content: String,
    /** Stored as raw float bytes via TypeConverter */
    val embedding: FloatArray? = null,
    val source: String = "conversation",
    val timestamp: Long = System.currentTimeMillis(),
    val accessCount: Int = 0,
    /** 0.0–1.0; higher = kept longer during pruning */
    val importance: Float = 0.5f
)

/** Skills the agent has learned through experience */
@Entity(tableName = "learned_skills")
data class LearnedSkillEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String,
    val howToUse: String,
    val successCount: Int = 0,
    val failCount: Int = 0,
    val lastUpdated: Long = System.currentTimeMillis()
)

/** Web pages the agent has visited, for memory recall */
@Entity(tableName = "browsing_history")
data class BrowsingHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String,
    val summary: String,
    val timestamp: Long = System.currentTimeMillis(),
    val embedding: FloatArray? = null
)

// ─── DAOs ─────────────────────────────────────────────────────────────────────

@Dao
interface ConversationDao {
    @Insert
    suspend fun insert(conversation: ConversationEntity): Long

    @Update
    suspend fun update(conversation: ConversationEntity)

    @Query("SELECT * FROM conversations ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 50): List<ConversationEntity>

    @Query("SELECT * FROM conversations WHERE wasSuccessful = 0 ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getFailedRecent(limit: Int = 20): List<ConversationEntity>

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun getById(id: Long): ConversationEntity?

    @Query("UPDATE conversations SET feedbackScore = :score WHERE id = :id")
    suspend fun updateFeedback(id: Long, score: Int)

    @Query("DELETE FROM conversations WHERE timestamp < :before")
    suspend fun pruneOlderThan(before: Long)

    @Query("SELECT COUNT(*) FROM conversations")
    suspend fun count(): Int
}

@Dao
interface MemoryChunkDao {
    @Insert
    suspend fun insert(chunk: MemoryChunkEntity): Long

    @Update
    suspend fun update(chunk: MemoryChunkEntity)

    @Query("SELECT * FROM memory_chunks ORDER BY timestamp DESC")
    suspend fun getAll(): List<MemoryChunkEntity>

    @Query("SELECT * FROM memory_chunks ORDER BY importance DESC, accessCount DESC LIMIT :limit")
    suspend fun getTopByImportance(limit: Int): List<MemoryChunkEntity>

    @Query("UPDATE memory_chunks SET accessCount = accessCount + 1 WHERE id = :id")
    suspend fun incrementAccessCount(id: Long)

    @Query("DELETE FROM memory_chunks WHERE importance < :threshold AND accessCount < 2 AND timestamp < :before")
    suspend fun pruneStale(threshold: Float, before: Long)

    @Query("SELECT COUNT(*) FROM memory_chunks")
    suspend fun count(): Int
}

@Dao
interface LearnedSkillDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(skill: LearnedSkillEntity): Long

    @Update
    suspend fun update(skill: LearnedSkillEntity)

    @Query("SELECT * FROM learned_skills ORDER BY successCount DESC")
    suspend fun getAll(): List<LearnedSkillEntity>

    @Query("SELECT * FROM learned_skills WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): LearnedSkillEntity?

    @Query("UPDATE learned_skills SET successCount = successCount + 1, lastUpdated = :now WHERE id = :id")
    suspend fun incrementSuccess(id: Long, now: Long = System.currentTimeMillis())

    @Query("UPDATE learned_skills SET failCount = failCount + 1, lastUpdated = :now WHERE id = :id")
    suspend fun incrementFail(id: Long, now: Long = System.currentTimeMillis())
}

@Dao
interface BrowsingHistoryDao {
    @Insert
    suspend fun insert(entry: BrowsingHistoryEntity): Long

    @Query("SELECT * FROM browsing_history ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 30): List<BrowsingHistoryEntity>

    @Query("SELECT * FROM browsing_history ORDER BY timestamp DESC")
    suspend fun getAll(): List<BrowsingHistoryEntity>

    @Query("DELETE FROM browsing_history WHERE timestamp < :before")
    suspend fun pruneOlderThan(before: Long)
}

// ─── Database ─────────────────────────────────────────────────────────────────

@Database(
    entities = [
        ConversationEntity::class,
        MemoryChunkEntity::class,
        LearnedSkillEntity::class,
        BrowsingHistoryEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class MemoryDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun memoryChunkDao(): MemoryChunkDao
    abstract fun learnedSkillDao(): LearnedSkillDao
    abstract fun browsingHistoryDao(): BrowsingHistoryDao

    companion object {
        @Volatile private var INSTANCE: MemoryDatabase? = null

        fun getInstance(context: Context): MemoryDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    MemoryDatabase::class.java,
                    "agent_memory.db"
                ).build().also { INSTANCE = it }
            }
    }
}
