package ai.openclaw.androidagent.memory

import android.content.Context
import android.util.Log
import androidx.work.*
import java.util.concurrent.TimeUnit

/**
 * Background worker that periodically reviews the agent's experiences and
 * improves its stored knowledge.
 *
 * Scheduled via WorkManager to run every 4 hours.  Each run:
 * 1. Reviews recent failed interactions and extracts lessons.
 * 2. Promotes recurring failure patterns into [LearnedSkillEntity] entries.
 * 3. Prunes stale / low-importance memories to keep the DB lean.
 * 4. Logs a "daily summary" of what changed.
 */
class SelfImprovementLoop(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val TAG = "SelfImprovementLoop"
    private val memoryManager = MemoryManager.getInstance(appContext)

    override suspend fun doWork(): Result {
        Log.i(TAG, "Self-improvement loop started")

        return try {
            reviewFailures()
            pruneStaleMemories()
            logDailySummary()
            Log.i(TAG, "Self-improvement loop completed successfully")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Self-improvement loop failed", e)
            Result.retry()
        }
    }

    // ── Step 1: Review failures and extract lessons ───────────────────────────

    private suspend fun reviewFailures() {
        val failures = memoryManager.getRecentFailed(limit = 20)
        if (failures.isEmpty()) {
            Log.d(TAG, "No recent failures to review")
            return
        }

        Log.d(TAG, "Reviewing ${failures.size} recent failure(s)")

        // Group failures by the tools they used
        val failuresByTool = mutableMapOf<String, Int>()
        val failedTopics = mutableListOf<String>()

        for (failure in failures) {
            for (tool in failure.toolsUsed) {
                failuresByTool[tool] = (failuresByTool[tool] ?: 0) + 1
            }
            // Extract a rough topic from the first ~60 chars of the user message
            val topic = failure.userMessage.take(60).trim()
            if (topic.isNotEmpty()) failedTopics.add(topic)
        }

        // If a tool failed 3+ times, record a lesson about it
        for ((tool, count) in failuresByTool) {
            if (count >= 3) {
                val lesson = "Tool '$tool' has failed $count times recently. " +
                        "Consider verifying permissions and inputs before calling it."
                memoryManager.learnSkill(
                    name = "lesson:$tool",
                    description = "Repeated failures with $tool tool",
                    howToUse = lesson
                )
                Log.d(TAG, "Recorded lesson for tool: $tool ($count failures)")
            }
        }

        // If there are multiple failures with no tools used, record a general lesson
        val noToolFailures = failures.count { it.toolsUsed.isEmpty() }
        if (noToolFailures >= 3) {
            memoryManager.learnSkill(
                name = "lesson:direct-response-failures",
                description = "Direct (no-tool) responses have failed $noToolFailures times recently.",
                howToUse = "When answering without tools, be more cautious and verify the user's intent before responding."
            )
        }
    }

    // ── Step 2: Prune stale memories ─────────────────────────────────────────

    private suspend fun pruneStaleMemories() {
        // Prune memories older than 30 days that were rarely accessed
        val thirtyDaysMs = 30L * 24 * 60 * 60 * 1000
        memoryManager.pruneOldMemories(thirtyDaysMs)
        Log.d(TAG, "Pruned memories older than 30 days with low importance")
    }

    // ── Step 3: Log a daily summary ───────────────────────────────────────────

    private suspend fun logDailySummary() {
        val recent = memoryManager.getRecentConversations(limit = 50)
        val skills = memoryManager.getAllSkills()

        val successes = recent.count { it.wasSuccessful }
        val failures = recent.count { !it.wasSuccessful }
        val positives = recent.count { it.feedbackScore == 1 }
        val negatives = recent.count { it.feedbackScore == 0 }

        val summary = buildString {
            appendLine("=== Agent Self-Improvement Summary ===")
            appendLine("Recent interactions (last 50): $successes succeeded, $failures failed")
            appendLine("Feedback: $positives 👍  $negatives 👎")
            appendLine("Learned skills stored: ${skills.size}")
            if (skills.isNotEmpty()) {
                appendLine("Top skills by success rate:")
                skills.take(5).forEach { skill ->
                    val total = (skill.successCount + skill.failCount).coerceAtLeast(1)
                    val rate = (skill.successCount * 100) / total
                    appendLine("  • ${skill.name}: $rate% success rate")
                }
            }
        }.trim()

        Log.i(TAG, summary)
    }

    // ── Scheduling ────────────────────────────────────────────────────────────

    companion object {
        private const val WORK_NAME = "SelfImprovementLoop"

        /**
         * Schedule the self-improvement loop to run every 4 hours.
         * Safe to call multiple times — WorkManager deduplicates.
         */
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build()

            val request = PeriodicWorkRequestBuilder<SelfImprovementLoop>(
                repeatInterval = 4,
                repeatIntervalTimeUnit = TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        /** Cancel the scheduled worker. */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
