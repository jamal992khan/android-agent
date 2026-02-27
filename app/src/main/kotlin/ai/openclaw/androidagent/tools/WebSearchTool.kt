package ai.openclaw.androidagent.tools

import ai.openclaw.androidagent.models.Tool
import ai.openclaw.androidagent.models.ToolParameter
import ai.openclaw.androidagent.models.ToolResult
import com.google.gson.Gson
import com.google.gson.JsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * WebSearchTool — searches the web using DuckDuckGo Instant Answer API (no API key required).
 * Falls back to SearXNG public instances if DDG returns no results.
 *
 * Returns up to 5 results with title, URL, and snippet.
 */
class WebSearchTool : Tool {
    override val name = "web_search"
    override val description = "Search the web for information. Returns top results with title, URL, and snippet. Use this to find current information, facts, news, or anything that requires browsing the internet."
    override val parameters = mapOf(
        "query" to ToolParameter("string", "The search query", required = true),
        "max_results" to ToolParameter("integer", "Maximum number of results to return (default: 5, max: 10)", required = false)
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val gson = Gson()

    data class SearchResult(
        val title: String,
        val url: String,
        val snippet: String
    )

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val query = params["query"] as? String
            ?: return ToolResult(false, error = "Missing required parameter: query")
        val maxResults = (params["max_results"] as? Number)?.toInt()?.coerceIn(1, 10) ?: 5

        // Primary: DuckDuckGo Instant Answer API
        val ddgResults = searchDuckDuckGo(query, maxResults)
        if (ddgResults.isNotEmpty()) {
            return ToolResult(true, data = formatResults(query, ddgResults, "DuckDuckGo"))
        }

        // Fallback: SearXNG public instance
        val searxResults = searchSearXNG(query, maxResults)
        if (searxResults.isNotEmpty()) {
            return ToolResult(true, data = formatResults(query, searxResults, "SearXNG"))
        }

        return ToolResult(
            false,
            error = "No results found for query: \"$query\". Both DuckDuckGo and SearXNG returned empty results."
        )
    }

    private fun searchDuckDuckGo(query: String, maxResults: Int): List<SearchResult> {
        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://api.duckduckgo.com/?q=$encoded&format=json&no_html=1&skip_disambig=1"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "AndroidAgent/1.0 (search tool)")
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return emptyList()
            if (!response.isSuccessful) return emptyList()

            val json = gson.fromJson(body, JsonObject::class.java)
            val results = mutableListOf<SearchResult>()

            // AbstractText (featured snippet)
            val abstractText = json.get("AbstractText")?.asString?.takeIf { it.isNotBlank() }
            val abstractUrl = json.get("AbstractURL")?.asString?.takeIf { it.isNotBlank() }
            val abstractTitle = json.get("Heading")?.asString?.takeIf { it.isNotBlank() }
            if (abstractText != null && abstractUrl != null) {
                results.add(
                    SearchResult(
                        title = abstractTitle ?: query,
                        url = abstractUrl,
                        snippet = abstractText.take(300)
                    )
                )
            }

            // RelatedTopics
            val relatedTopics = json.getAsJsonArray("RelatedTopics") ?: return results
            for (element in relatedTopics) {
                if (results.size >= maxResults) break
                try {
                    val topic = element.asJsonObject
                    // Skip sub-category topics (they have a "Topics" array, not a direct result)
                    if (topic.has("Topics")) continue

                    val text = topic.get("Text")?.asString?.takeIf { it.isNotBlank() } ?: continue
                    val firstUrl = topic.get("FirstURL")?.asString?.takeIf { it.isNotBlank() } ?: continue

                    // Split text: usually "Title - Description"
                    val dashIdx = text.indexOf(" - ")
                    val (title, snippet) = if (dashIdx > 0) {
                        text.substring(0, dashIdx) to text.substring(dashIdx + 3)
                    } else {
                        text.take(80) to text
                    }

                    results.add(SearchResult(title = title, url = firstUrl, snippet = snippet.take(300)))
                } catch (_: Exception) {
                    continue
                }
            }

            results
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun searchSearXNG(query: String, maxResults: Int): List<SearchResult> {
        // Public SearXNG instances (try each in order)
        val instances = listOf(
            "https://searx.be",
            "https://search.bus-hit.me",
            "https://opnxng.com"
        )

        for (instance in instances) {
            try {
                val encoded = URLEncoder.encode(query, "UTF-8")
                val url = "$instance/search?q=$encoded&format=json&categories=general"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "AndroidAgent/1.0 (search tool)")
                    .build()

                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: continue
                if (!response.isSuccessful) continue

                val json = gson.fromJson(body, JsonObject::class.java)
                val resultsArray = json.getAsJsonArray("results") ?: continue
                val results = mutableListOf<SearchResult>()

                for (element in resultsArray) {
                    if (results.size >= maxResults) break
                    try {
                        val item = element.asJsonObject
                        val title = item.get("title")?.asString?.takeIf { it.isNotBlank() } ?: continue
                        val url2 = item.get("url")?.asString?.takeIf { it.isNotBlank() } ?: continue
                        val snippet = item.get("content")?.asString?.takeIf { it.isNotBlank() } ?: ""
                        results.add(SearchResult(title = title, url = url2, snippet = snippet.take(300)))
                    } catch (_: Exception) {
                        continue
                    }
                }

                if (results.isNotEmpty()) return results
            } catch (_: Exception) {
                continue
            }
        }
        return emptyList()
    }

    private fun formatResults(query: String, results: List<SearchResult>, source: String): String {
        return buildString {
            appendLine("🔍 Search results for: \"$query\" (via $source)")
            appendLine()
            results.forEachIndexed { index, result ->
                appendLine("${index + 1}. **${result.title}**")
                appendLine("   URL: ${result.url}")
                if (result.snippet.isNotBlank()) {
                    appendLine("   ${result.snippet}")
                }
                appendLine()
            }
        }.trimEnd()
    }
}
