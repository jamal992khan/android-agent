package ai.openclaw.androidagent.tools

import ai.openclaw.androidagent.models.Tool
import ai.openclaw.androidagent.models.ToolParameter
import ai.openclaw.androidagent.models.ToolResult
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.util.concurrent.TimeUnit

/**
 * WebFetchTool — fetches a URL and returns clean, readable text content.
 *
 * Uses OkHttp for the HTTP request and Jsoup for HTML parsing.
 * Strips navigation, footers, ads, and script/style blocks.
 * Truncates content to 4000 characters to keep LLM context manageable.
 */
class WebFetchTool : Tool {
    override val name = "web_fetch"
    override val description = "Fetch the content of a web page and return clean, readable text. Strips HTML, navigation, ads, and other clutter. Useful for reading articles, documentation, or any web content."
    override val parameters = mapOf(
        "url" to ToolParameter("string", "The URL to fetch", required = true),
        "max_chars" to ToolParameter("integer", "Maximum characters to return (default: 4000, max: 8000)", required = false)
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    /** Tags to completely remove (content + tag) */
    private val REMOVE_TAGS = setOf(
        "script", "style", "noscript", "nav", "footer", "header",
        "aside", "advertisement", "ads", "iframe", "form",
        "button", "input", "select", "textarea"
    )

    /** CSS selectors for junk elements */
    private val REMOVE_SELECTORS = listOf(
        "nav", "footer", "header", "aside",
        "[class*='nav']", "[class*='footer']", "[class*='header']",
        "[class*='sidebar']", "[class*='advertisement']", "[class*='cookie']",
        "[class*='popup']", "[class*='modal']", "[class*='banner']",
        "[id*='nav']", "[id*='footer']", "[id*='header']", "[id*='sidebar']",
        "[id*='advertisement']", "[id*='cookie']", "[id*='popup']"
    )

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val url = params["url"] as? String
            ?: return ToolResult(false, error = "Missing required parameter: url")
        val maxChars = (params["max_chars"] as? Number)?.toInt()?.coerceIn(100, 8000) ?: 4000

        // Basic URL validation
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return ToolResult(false, error = "Invalid URL: must start with http:// or https://")
        }

        return try {
            val html = fetchHtml(url)
            val text = extractText(html, url)
            val truncated = if (text.length > maxChars) {
                text.take(maxChars) + "\n\n[...content truncated at $maxChars characters...]"
            } else {
                text
            }
            ToolResult(true, data = "📄 Content from: $url\n\n$truncated")
        } catch (e: Exception) {
            ToolResult(false, error = "Failed to fetch URL: ${e.message}")
        }
    }

    private fun fetchHtml(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 AndroidAgent/1.0")
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "en-US,en;q=0.5")
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw Exception("HTTP ${response.code}: ${response.message}")
        }
        return response.body?.string() ?: throw Exception("Empty response body")
    }

    private fun extractText(html: String, baseUrl: String): String {
        val doc: Document = Jsoup.parse(html, baseUrl)

        // Remove unwanted elements
        REMOVE_TAGS.forEach { tag -> doc.select(tag).remove() }
        REMOVE_SELECTORS.forEach { selector ->
            try { doc.select(selector).remove() } catch (_: Exception) {}
        }

        // Try to find the main content area
        val mainContent = findMainContent(doc)

        // Get text, preserving some structure
        val text = mainContent.wholeText()
            .lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            // Remove very short lines that are likely nav/menu items
            .filter { it.length > 20 || it.endsWith(".") || it.endsWith("?") || it.endsWith("!") }
            .joinToString("\n")

        return if (text.isBlank()) {
            // Last resort: just get all text
            doc.body()?.text() ?: "No readable content found"
        } else {
            text
        }
    }

    private fun findMainContent(doc: Document): org.jsoup.nodes.Element {
        // Priority order for main content selectors
        val mainSelectors = listOf(
            "main",
            "article",
            "[role='main']",
            "#main-content",
            "#content",
            "#main",
            ".main-content",
            ".article-body",
            ".post-content",
            ".entry-content",
            ".content"
        )

        for (selector in mainSelectors) {
            try {
                val element = doc.selectFirst(selector)
                if (element != null && element.text().length > 200) {
                    return element
                }
            } catch (_: Exception) {}
        }

        // Fall back to body
        return doc.body() ?: doc
    }
}
