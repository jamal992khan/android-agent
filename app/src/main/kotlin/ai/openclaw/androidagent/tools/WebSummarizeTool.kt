package ai.openclaw.androidagent.tools

import ai.openclaw.androidagent.models.Tool
import ai.openclaw.androidagent.models.ToolParameter
import ai.openclaw.androidagent.models.ToolResult

/**
 * WebSummarizeTool — combines web search + fetch to gather content on a topic.
 *
 * Searches DuckDuckGo for the topic, fetches the top results, and returns
 * combined content for the LLM to reason over and summarize.
 *
 * This tool is intentionally "dumb" about summarization — it returns raw
 * gathered content and lets the LLM handle the actual synthesis.
 */
class WebSummarizeTool : Tool {
    override val name = "web_summarize"
    override val description = "Research a topic by searching the web and fetching content from multiple sources. Returns combined content from the top results. The LLM will then synthesize a summary. Use this for research questions, current events, or when you need comprehensive information on a topic."
    override val parameters = mapOf(
        "topic" to ToolParameter("string", "The topic or question to research", required = true),
        "num_sources" to ToolParameter("integer", "Number of sources to fetch (default: 3, max: 5)", required = false)
    )

    private val searchTool = WebSearchTool()
    private val fetchTool = WebFetchTool()

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val topic = params["topic"] as? String
            ?: return ToolResult(false, error = "Missing required parameter: topic")
        val numSources = (params["num_sources"] as? Number)?.toInt()?.coerceIn(1, 5) ?: 3

        val output = StringBuilder()
        output.appendLine("📚 Research results for: \"$topic\"")
        output.appendLine("=" .repeat(60))
        output.appendLine()

        // Step 1: Search
        val searchResult = searchTool.execute(mapOf("query" to topic, "max_results" to numSources))
        if (!searchResult.success) {
            return ToolResult(false, error = "Search failed: ${searchResult.error}")
        }

        output.appendLine("## Search Results")
        output.appendLine(searchResult.data ?: "")
        output.appendLine()

        // Step 2: Extract URLs from search results and fetch them
        val urls = extractUrls(searchResult.data ?: "")
        val urlsToFetch = urls.take(numSources)

        if (urlsToFetch.isEmpty()) {
            output.appendLine("⚠️ No URLs found to fetch from search results.")
            return ToolResult(true, data = output.toString().trimEnd())
        }

        output.appendLine("## Fetched Content")
        output.appendLine()

        var successfulFetches = 0
        urlsToFetch.forEachIndexed { index, url ->
            output.appendLine("### Source ${index + 1}: $url")
            output.appendLine()

            val fetchResult = fetchTool.execute(
                mapOf(
                    "url" to url,
                    "max_chars" to 1500  // Limit per-source to keep total manageable
                )
            )

            if (fetchResult.success && fetchResult.data != null) {
                // Strip the "📄 Content from: URL" header since we already showed the URL
                val content = fetchResult.data
                    .removePrefix("📄 Content from: $url\n\n")
                    .trim()
                output.appendLine(content)
                successfulFetches++
            } else {
                output.appendLine("⚠️ Could not fetch this source: ${fetchResult.error}")
            }
            output.appendLine()
        }

        output.appendLine("=" .repeat(60))
        output.appendLine("📊 Successfully gathered content from $successfulFetches/${urlsToFetch.size} sources.")
        output.appendLine()
        output.appendLine("*Please synthesize the above content into a clear, accurate summary.*")

        return ToolResult(true, data = output.toString().trimEnd())
    }

    /**
     * Extract URLs from formatted search result text.
     * Looks for lines starting with "   URL: ..."
     */
    private fun extractUrls(searchOutput: String): List<String> {
        val urlPattern = Regex("""^\s*URL:\s*(https?://\S+)""", RegexOption.MULTILINE)
        return urlPattern.findAll(searchOutput)
            .map { it.groupValues[1].trim() }
            .filter { it.startsWith("http") }
            .distinct()
            .toList()
    }
}
