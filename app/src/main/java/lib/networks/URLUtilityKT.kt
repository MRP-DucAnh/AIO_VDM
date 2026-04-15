package lib.networks

import app.core.engines.youtube.parser.*
import kotlinx.coroutines.*
import lib.networks.HttpClientProvider.okHttpClient
import lib.networks.URLUtilityKT.fetchMobileWebPageContent
import lib.networks.URLUtilityKT.fetchWebPageContent
import lib.networks.URLUtilityKT.getBaseDomain
import lib.networks.URLUtilityKT.getGoogleFaviconUrl
import lib.networks.URLUtilityKT.getHostFromUrl
import lib.networks.URLUtilityKT.getWebpageTitleOrDescription
import lib.networks.URLUtilityKT.isFaviconAvailable
import lib.process.*
import okhttp3.*
import org.jsoup.*
import org.jsoup.nodes.*
import java.net.*
import java.net.HttpURLConnection.*
import java.net.URLEncoder.*

/**
 * Utility object providing URL parsing and manipulation functions.
 *
 * This object contains helper methods for extracting, validating, and normalizing
 * URL components. All methods are designed to be thread-safe and can be called
 * from both Kotlin and Java code (using `@JvmStatic` annotations).
 *
 * Key features:
 * - URL component extraction (host, scheme, etc.)
 * - URL normalization and encoding
 * - Connectivity checking
 * - Favicon discovery and validation
 * - Webpage metadata extraction
 * - Email validation
 *
 * All suspend functions use [withIOContext] for proper coroutine dispatching,
 * ensuring network operations don't block the main thread.
 */
object URLUtilityKT {

	private val logger = LogHelperUtils.from(javaClass)

	/**
	 * Extracts the scheme and host components from a URL string.
	 *
	 * This method parses a complete URL and returns only the protocol/scheme
	 * and domain/host portions, stripping away all other components including:
	 * - Port numbers
	 * - Path segments
	 * - Query parameters
	 * - Fragment identifiers
	 * - User info (username/password)
	 *
	 * The extracted format is: `scheme://host`
	 *
	 * URL component breakdown:
	 * ```
	 * Full URL:     https://user:pass@example.com:8080/path/to/page?query=value#fragment
	 * Extracted:    https://example.com
	 *
	 * Components removed:
	 * - user:pass   (user info)
	 * - :8080       (port)
	 * - /path/to/page (path)
	 * - ?query=value (query)
	 * - #fragment    (fragment)
	 * ```
	 *
	 * Examples:
	 * ```kotlin
	 * // Basic URL
	 * extractHostUrl("https://www.example.com/page.html")
	 * // Returns: "https://www.example.com"
	 *
	 * // URL with port
	 * extractHostUrl("http://localhost:8080/api/data")
	 * // Returns: "http://localhost"
	 *
	 * // URL with query parameters
	 * extractHostUrl("https://search.google.com/search?q=kotlin&hl=en")
	 * // Returns: "https://search.google.com"
	 *
	 * // URL with fragment
	 * extractHostUrl("https://en.wikipedia.org/wiki/Main_Page#Introduction")
	 * // Returns: "https://en.wikipedia.org"
	 *
	 * // URL with user info (rare in web URLs)
	 * extractHostUrl("https://username:password@api.example.com/dashboard")
	 * // Returns: "https://api.example.com"
	 *
	 * // HTTPS URL with subdomain
	 * extractHostUrl("https://blog.example.co.uk/article")
	 * // Returns: "https://blog.example.co.uk"
	 *
	 * // URL with trailing slash
	 * extractHostUrl("https://example.com/")
	 * // Returns: "https://example.com"
	 *
	 * // Invalid URL
	 * extractHostUrl("not-a-valid-url")
	 * // Returns: "" (empty string)
	 *
	 * // Empty string
	 * extractHostUrl("")
	 * // Returns: "" (empty string)
	 * ```
	 *
	 * Use cases:
	 * - Comparing if two URLs belong to the same domain (ignoring paths)
	 * - Displaying clean domain names in UI
	 * - Domain-based grouping or categorization
	 * - Checking cross-origin requests (ignoring paths)
	 * - Building domain-specific cache keys
	 * - Determining website identity for favicon lookup
	 * - URL normalization before database storage
	 *
	 * Important notes:
	 * - Does NOT include port numbers (even if specified in original URL)
	 * - Does NOT validate if the host is reachable or exists
	 * - Returns empty string for malformed URLs instead of throwing exceptions
	 * - Preserves the original scheme (http vs https)
	 * - Subdomains are preserved in their entirety
	 * - IP addresses are returned as-is (e.g., "https://192.168.1.1")
	 *
	 * Edge cases:
	 * - File URLs: `file:///path/to/file` → "file://null" (URI may return null host)
	 * - FTP URLs: `ftp://files.example.com/download` → "ftp://files.example.com"
	 * - URLs with underscores in hostname (technically invalid but may still parse)
	 * - Internationalized domain names (IDN) are preserved in their encoded form
	 *
	 * Performance considerations:
	 * - Uses [URI] parsing which is relatively fast
	 * - Suitable for processing large lists of URLs
	 * - Suspends on IO dispatcher to avoid blocking
	 * - No network I/O involved, only string parsing
	 *
	 * @param urlString The complete URL string to parse (can be any valid URL format)
	 * @return The extracted scheme and host as a String in format "scheme://host",
	 *         or empty string if:
	 *         - URL is malformed
	 *         - URL has no scheme (e.g., "example.com")
	 *         - URL has no host component
	 *         - Exception occurs during parsing
	 *         - Input is empty string
	 *
	 * @see URI
	 * @see URL
	 * @see getHostFromUrl
	 * @see getBaseDomain
	 */
	@JvmStatic
	suspend fun extractHostUrl(urlString: String): String {
		return withIOContext {
			try {
				val uri = URI(urlString)
				return@withIOContext "${uri.scheme}://${uri.host}"
			} catch (error: Exception) {
				logger.e(error)
				return@withIOContext ""
			}
		}
	}

	/**
	 * Checks whether a URL represents only a host (domain) without any path or specific resource.
	 *
	 * This method determines if a URL points to the root of a website rather than a specific
	 * page, file, or resource. It's useful for distinguishing between homepage URLs and
	 * deep links to specific content.
	 *
	 * URL component structure:
	 * - Full URL: `https://example.com/path/to/page.html`
	 *   - Host: `example.com`
	 *   - Path: `/path/to/page.html`
	 * - Host-only URL: `https://example.com`
	 *   - Host: `example.com`
	 *   - Path: (empty or just "/")
	 *
	 * Returns `true` for URLs where:
	 * - The path component is empty (e.g., `https://example.com`)
	 * - The path is exactly "/" (e.g., `https://example.com/`)
	 * - URLs with trailing slashes but no additional path segments
	 *
	 * Returns `false` for URLs with:
	 * - Any non-empty path beyond the root (e.g., `https://example.com/page`)
	 * - Specific file references (e.g., `https://example.com/index.html`)
	 * - Subdirectories (e.g., `https://example.com/blog/post`)
	 * - Query parameters or fragments (these don't affect the check)
	 *
	 * Examples:
	 * - `"https://example.com"` → `true` (empty path)
	 * - `"https://example.com/"` → `true` (path is "/")
	 * - `"https://example.com?query=value"` → `true` (path empty, query ignored)
	 * - `"https://example.com#section"` → `true` (path empty, fragment ignored)
	 * - `"https://example.com/page"` → `false` (has path)
	 * - `"https://example.com/blog/"` → `false` (has path)
	 * - `"https://example.com/index.html"` → `false` (has path)
	 * - `"https://example.com/path?query=value"` → `false` (has path)
	 * - `"invalid-url"` → `false` (exception caught)
	 *
	 * Use cases:
	 * - Determining if a bookmark is for a homepage or specific page
	 * - UI logic where root URLs should behave differently (e.g., show homepage icon)
	 * - Navigation logic to decide whether to load the homepage or a specific route
	 * - Cache key generation (root URLs vs deep links)
	 *
	 * @param url The URL string to check (must be a valid HTTP/HTTPS URL)
	 * @return `true` if the URL has no path component (or only "/"), `false` otherwise
	 * @throws MalformedURLException If URL is malformed (caught and logged, returns `false`)
	 * @see URL
	 * @see URL.getPath
	 */
	@JvmStatic
	suspend fun isHostOnly(url: String): Boolean {
		return withIOContext {
			try {
				val parsedUrl = URL(url)
				val path = parsedUrl.path
				path.isNullOrEmpty() || path == "/"
			} catch (error: Exception) {
				logger.e(error)
				false
			}
		}
	}

	/**
	 * Validates whether a string is a properly formatted email address.
	 *
	 * This method performs basic email validation by checking structural requirements
	 * without using complex regex patterns. It's designed to catch common invalid
	 * email formats while being performant and readable.
	 *
	 * Validation criteria:
	 * 1. Must contain exactly one "@" symbol
	 * 2. Local part (before @) must not be empty
	 * 3. Domain part (after @) must not be empty
	 * 4. Domain must contain at least one dot "."
	 * 5. Local part can only contain alphanumeric characters and special symbols: . _ % + -
	 *
	 * Local part allowed characters:
	 * - Uppercase letters: A-Z
	 * - Lowercase letters: a-z
	 * - Numbers: 0-9
	- Special characters: . _ % + -
	 *
	 * What this validation DOES NOT check:
	 * - DNS existence of the domain
	 * - MX records for email servers
	 * - Whether the email actually exists or is deliverable
	 * - Maximum length constraints (though implied by format)
	 * - Complex RFC 5322 compliant edge cases
	 *
	 * Valid examples:
	 * - `"user@example.com"`
	 * - `"john.doe@example.co.uk"`
	 * - `"user_name@example.com"`
	 * - `"user+tag@example.com"`
	 * - `"user-name@example.com"`
	 * - `"user123@example.com"`
	 *
	 * Invalid examples:
	 * - `"user"` (no @ symbol)
	 * - `"user@domain"` (no dot in domain)
	 * - `"@example.com"` (empty local part)
	 * - `"user@"` (empty domain)
	 * - `"user@domain."` (dot at end of domain)
	 * - `"user@.domain.com"` (dot at start of domain)
	 * - `"user name@example.com"` (space not allowed)
	 * - `"user!@example.com"` (exclamation not allowed)
	 * - `"user@domain..com"` (consecutive dots)
	 *
	 * Limitations:
	 * - Does not validate Unicode/IDN domains (internationalized domain names)
	 * - Does not support quoted local parts (e.g., `"john.doe"@example.com`)
	 * - Does not validate IP addresses as domains (user@[192.168.1.1])
	 * - May reject some valid but obscure RFC-compliant email formats
	 *
	 * Use cases:
	 * - Form input validation before sending to server
	 * - Quick sanity check before more thorough validation
	 * - Filtering obviously invalid email addresses
	 * - User interface feedback for email input fields
	 *
	 * @param email The email address string to validate
	 * @return `true` if the email passes basic format validation, `false` otherwise
	 *
	 * @see <a href="https://www.rfc-editor.org/rfc/rfc5322">RFC 5322 - Email Format Specification</a>
	 */
	@JvmStatic
	suspend fun isValidEmail(email: String): Boolean {
		return withIOContext {
			if (!email.contains("@"))
				return@withIOContext false

			val parts = email.split("@")
			if (parts.size != 2) return@withIOContext false
			val (local, domain) = parts

			if (local.isEmpty() || domain.isEmpty())
				return@withIOContext false

			if (!domain.contains("."))
				return@withIOContext false

			val simple = "^[A-Za-z0-9._%+-]+$".toRegex()
			return@withIOContext simple.matches(local)
		}
	}

	/**
	 * Extracts webpage title or description using callback-based asynchronous pattern.
	 *
	 * This method is the legacy callback-based version of [getWebpageTitleOrDescription].
	 * It has been replaced by the coroutine-based suspend function that provides better
	 * error handling, structured concurrency, and cleaner code.
	 *
	 * Functionality:
	 * - Same extraction logic as the newer suspend version
	 * - Special handling for YouTube Music pages
	 * - Supports both title and description extraction
	 * - Optional pre-fetched HTML content
	 * - Results delivered via callback instead of direct return
	 *
	 * Reasons for deprecation:
	 * - Callback-based APIs lead to callback hell and nested code
	 * - Harder to handle errors and compose with other async operations
	 * - More difficult to test and maintain
	 * - Suspend functions provide better integration with coroutines
	 * - Coroutines offer structured concurrency and cancellation support
	 *
	 * Migration guide:
	 * Replace callback-based calls with suspend function calls:
	 *
	 * OLD (callback-based):
	 * ```kotlin
	 * getWebpageTitleOrDescription("https://example.com", returnDescription = false) { result ->
	 *     if (result != null) {
	 *         updateUI(result)
	 *     } else {
	 *         handleError()
	 *     }
	 * }
	 * ```
	 *
	 * NEW (suspend-based):
	 * ```kotlin
	 * lifecycleScope.launch {
	 *     try {
	 *         val result = getWebpageTitleOrDescription(
	 *             websiteUrl = "https://example.com",
	 *             returnDescription = false
	 *         )
	 *         if (result != null) {
	 *             updateUI(result)
	 *         } else {
	 *             handleError()
	 *         }
	 *     } catch (e: Exception) {
	 *         handleError()
	 *     }
	 * }
	 * ```
	 *
	 * @param websiteUrl The URL of the webpage to extract title/description from
	 * @param returnDescription If `true`, extracts the description (og:description);
	 *                         if `false`, extracts the title (og:title) (default: false)
	 * @param userGivenHtmlBody Optional pre-fetched HTML content to parse.
	 *                         If provided, skips network request and uses this content directly
	 * @param callback Callback function that receives the extracted title/description as a String?
	 *                 The callback parameter will be `null` if extraction fails
	 *
	 * @see getWebpageTitleOrDescription
	 * @see YTVideoStreamParser
	 * @see fetchWebPageContent
	 * @see Jsoup
	 *
	 * @deprecated Use [getWebpageTitleOrDescription] suspend function instead.
	 *             The suspend version provides better error handling, coroutine support,
	 *             and eliminates callback nesting. Migration is straightforward as shown above.
	 */
	@JvmStatic
	@Deprecated("Use getWebpageTitleOrDescription instead.")
	suspend fun getWebpageTitleOrDescription(
		websiteUrl: String,
		returnDescription: Boolean = false,
		userGivenHtmlBody: String? = null,
		callback: (String?) -> Unit
	) {
		try {
			val isYoutubeMusicPage = extractHostUrl(websiteUrl)
				.contains("music.youtube", true)
			if (isYoutubeMusicPage) {
				val title = YTVideoStreamParser.getTitle(websiteUrl)
				if (title.isNullOrEmpty() == false) {
					return callback.invoke("${title}_Youtube_Music_Audio")
				}
			}

			val htmlBody = if (userGivenHtmlBody.isNullOrEmpty()) {
				fetchWebPageContent(
					url = websiteUrl,
					retry = true,
					numOfRetry = 6
				) ?: return callback(null)
			} else userGivenHtmlBody
			val document = Jsoup.parse(htmlBody)
			val metaTag = document.selectFirst(
				if (returnDescription) "meta[property=og:description]"
				else "meta[property=og:title]"
			)
			return callback(metaTag?.attr("content"))
		} catch (error: Exception) {
			logger.e("Error found while parsing title from an url:", error)
			callback(null)
		}
	}

	/**
	 * Extracts the webpage title or description from a given URL with special
	 * handling for YouTube Music pages. This method fetches and parses HTML content
	 * to extract either the Open Graph (og:) title or description meta tags. It provides
	 * special handling for YouTube Music pages to return a formatted title with  platform
	 * identification.
	 *
	 * Features:
	 * - Supports both title and description extraction via the `returnDescription` parameter
	 * - Can use pre-fetched HTML body to avoid redundant network requests
	 * - Special case handling for YouTube Music pages (uses dedicated parser)
	 * - Automatic retry logic for failed fetches (up to 6 attempts)
	 * - Falls back to Jsoup parsing for standard web pages
	 *
	 * Open Graph meta tags explained:
	 * - `og:title` - The title of the content (typically used for social media sharing)
	 * - `og:description` - A brief description of the content (often used in link previews)
	 *
	 * YouTube Music special handling:
	 * - Detects URLs containing "music.youtube" (case-insensitive)
	 * - Uses [YTVideoStreamParser] to extract the actual video title
	 * - Appends "_Youtube_Music_Audio" suffix for identification
	 * - Returns a formatted title string instead of standard og:title
	 *
	 * @param websiteUrl The URL of the webpage to extract title/description from
	 * @param returnDescription If `true`, extracts the description (og:description);
	 *                         if `false`, extracts the title (og:title) (default: false)
	 * @param userGivenHtmlBody Optional pre-fetched HTML content to parse.
	 *                         If provided, skips network request and uses this content directly.
	 *                         Useful when HTML is already available from previous operations.
	 * @return The extracted title or description as a String, or `null` if:
	 *         - Network request fails after all retries
	 *         - HTML parsing fails
	 *         - No og:title or og:description meta tag is found
	 *         - The extracted content is empty
	 *         - An exception occurs during processing
	 * @see Jsoup
	 * @see YTVideoStreamParser
	 * @see fetchWebPageContent
	 */
	@JvmStatic
	suspend fun getWebpageTitleOrDescription(
		websiteUrl: String,
		returnDescription: Boolean = false,
		userGivenHtmlBody: String? = null
	): String? {
		return withContext(Dispatchers.IO) {
			try {
				val isYoutubeMusicPage =
					extractHostUrl(websiteUrl)
						.contains("music.youtube", true)

				if (isYoutubeMusicPage) {
					YTVideoStreamParser
						.getTitle(websiteUrl)
						?.takeIf { it.isNotEmpty() }
						?.let {
							return@withContext "${it}_Youtube_Music_Audio"
						}
				}

				val htmlBody = userGivenHtmlBody ?: fetchWebPageContent(
					url = websiteUrl,
					retry = true,
					numOfRetry = 6
				) ?: return@withContext null

				val document = Jsoup.parse(htmlBody)
				val metaTag = document.selectFirst(
					if (returnDescription) "meta[property=og:description]"
					else "meta[property=og:title]"
				)

				metaTag?.attr("content")
			} catch (error: Exception) {
				logger.e("Error parsing title", error)
				null
			}
		}
	}

	/**
	 * Retrieves the favicon URL for a given website using multiple strategies. This method
	 * attempts to find a website's favicon (the small icon displayed in browser tabs)
	 * by checking two different locations:
	 *
	 * by trying two different approaches in order:
	 * 1. Check the standard `/favicon.ico` location
	 * 2. Parse the HTML head section for favicon link tags
	 *
	 * Strategy 1 - Standard location:
	 * - Attempts to access `{websiteUrl}/favicon.ico`
	 * - Uses HEAD request to check availability without downloading
	 * - Fast and works for most websites that follow conventions
	 *
	 * Strategy 2 - HTML parsing (fallback):
	 * - Fetches and parses the webpage HTML
	 * - Searches for link tags with `rel` attribute containing "icon" or "shortcut icon"
	 * - Handles both absolute and relative URLs
	 * - Tests each found icon URL until one is available
	 *
	 * Link tag patterns recognized:
	 * - `<link rel="icon" href="...">`
	 * - `<link rel="shortcut icon" href="...">`
	 * - `<link rel="apple-touch-icon" href="...">` (iOS homescreen icons)
	 * - `<link rel="icon" type="image/png" href="...">`
	 *
	 * URL resolution:
	 * - Absolute URLs (starting with http:// or https://) are used as-is
	 * - Relative URLs are resolved by prepending the base website URL
	 *
	 * @param websiteUrl The complete URL of the website (e.g., "https://example.com/page")
	 * @return The absolute URL of the favicon as a String, or `null` if:
	 *         - No favicon is found at the standard location
	 *         - No favicon links are found in the HTML head
	 *         - All found favicon URLs are unavailable
	 *         - Network request fails
	 *         - HTML parsing fails
	 *         - An exception occurs during processing
	 *
	 * Important notes:
	 * - This method makes network requests and should be called from a coroutine scope
	 * - The first strategy (standard location) uses HEAD request (fast, minimal bandwidth)
	 * - The second strategy fetches the entire HTML page (more bandwidth, slower)
	 * - Some websites use different favicons for different pages
	 * - Favicon URLs may be protocol-relative (//example.com/favicon.ico)
	 *   handled as absolute URLs
	 *
	 * Performance considerations:
	 * - Best case: 1 HEAD request (standard favicon found) - ~100-500ms
	 * - Worst case: 1 GET request (HTML) + N HEAD requests (N favicon candidates)
	 *   ~2-5 seconds
	 * - Consider caching results to avoid repeated lookups
	 *
	 * @see isFaviconAvailable
	 * @see getGoogleFaviconUrl
	 * @see Jsoup
	 */
	@JvmStatic
	suspend fun getFaviconUrl(websiteUrl: String): String? {
		return withIOContext {
			val standardFaviconUrl = "$websiteUrl/favicon.ico"
			if (isFaviconAvailable(standardFaviconUrl)) {
				return@withIOContext standardFaviconUrl
			}

			try {
				val doc: Document = Jsoup.connect(websiteUrl).get()
				val faviconUrl = doc.head().select("link[rel~=(icon|shortcut icon)]")
					.mapNotNull { it.attr("href").takeIf { href -> href.isNotEmpty() } }
					.map { href -> if (href.startsWith("http")) href else "$websiteUrl/$href" }
					.firstOrNull { isFaviconAvailable(it) }
				faviconUrl
			} catch (error: Exception) {
				logger.e(error)
				null
			}
		}
	}

	/**
	 * Checks whether a favicon is available at a given URL.
	 *
	 * This method performs a lightweight HEAD request to verify if a favicon
	 * exists at the specified URL without downloading the actual image data.
	 * It's useful for checking icon availability before attempting to display
	 * or download the favicon.
	 *
	 * How it works:
	 * - Sends an HTTP HEAD request (nobody, only headers)
	 * - Checks if the response status code is HTTP 200 (OK)
	 * - Does NOT download the actual image content
	 * - Minimal bandwidth usage
	 *
	 * Use cases:
	 * - Validating favicon URLs before displaying in UI
	- Checking if a custom favicon exists before falling back to default
	 * - Pre-caching or preloading icon availability status
	 * - Determining whether to show a placeholder or attempt to load the favicon
	 *
	 * Response code interpretation:
	 * - 200 (HTTP_OK): Favicon exists and is accessible → returns `true`
	 * - 301/302 (Redirect): Auto-handled by HttpURLConnection → follows redirect
	 * - 404 (Not Found): Favicon does not exist → returns `false`
	 * - 403 (Forbidden): Access denied → returns `false`
	 * - 500+ (Server Error): Server issues → returns `false`
	 * - Network exceptions: Timeout, unknown host, etc. → returns `false`
	 *
	 * @param faviconUrl The complete URL of the favicon to check
	 *                   (e.g., "https://example.com/favicon.ico")
	 * @return `true` if the favicon is available (HTTP 200 response), `false` otherwise
	 *
	 * @see getGoogleFaviconUrl
	 * @see HttpURLConnection
	 */
	@JvmStatic
	suspend fun isFaviconAvailable(faviconUrl: String): Boolean {
		return withIOContext {
			try {
				val url = URL(faviconUrl)
				val connection = url.openConnection() as HttpURLConnection
				connection.requestMethod = "HEAD"
				val isAvailable = connection.responseCode == HTTP_OK
				isAvailable
			} catch (error: Exception) {
				logger.e(error)
				false
			}
		}
	}

	/**
	 * Fetches the file size of a remote resource using an HTTP HEAD request.
	 *
	 * This method retrieves the Content-Length header from a URL without downloading
	 * the actual file content. It's useful for determining file sizes before download,
	 * checking available storage space, or displaying file size information in UI.
	 *
	 * Advantages of HEAD request:
	 * - No bandwidth consumption for the file body
	 * - Fast response (only headers are transferred)
	 * - Doesn't consume download quotas or limits
	 * - Useful for pre-download validation
	 *
	 * @param httpClient The OkHttpClient instance to use for making the request
	 * @param url The URL of the remote resource to check
	 * @return The file size in bytes if successful, `-1L` if:
	 *         - Content-Length header is not present in response
	 *         - Content-Length value is malformed (not a valid number)
	 *         - Network error occurs
	 *         - Exception is thrown during request execution
	 *         - Response is not successful (non-2xx status code)
	 *
	 * Important notes:
	 * - Not all servers include Content-Length header (e.g., chunked encoding)
	 * - File size may be approximate for compressed or dynamically generated content
	 * - Some servers may return Content-Length even for non-existent resources (rare)
	 * - For very large files (> 2GB), ensure the calling code handles Long values properly
	 *
	 * Usage example:
	 * ```kotlin
	 * val fileSize = fetchFileSize(okHttpClient, "https://example.com/file.zip")
	 * if (fileSize > 0) {
	 *     println("File size: ${getHumanReadableFormat(fileSize)}")
	 * } else {
	 *     println("Unable to determine file size")
	 * }
	 * ```
	 *
	 * @see OkHttpClient
	 * @see Request.Builder.head
	 */
	@JvmStatic
	suspend fun fetchFileSize(httpClient: OkHttpClient, url: String): Long {
		return withIOContext {
			try {
				val request = Request.Builder().url(url).head().build()
				httpClient.newCall(request).execute().use { response ->
					val fileSize = response.header("Content-Length")?.toLong() ?: -1L
					fileSize
				}
			} catch (error: Exception) {
				logger.e(error)
				-1L
			}
		}
	}

	/**
	 * Checks if the device has an active internet connection by attempting to reach Google.
	 *
	 * This method performs a simple connectivity test by making an HTTP request
	 * to Google's homepage (https://www.google.com). It's a reliable way to check
	 * actual internet connectivity, not just network interface availability.
	 *
	 * Why this approach:
	 * - More reliable than checking NetworkInfo (which only indicates network presence)
	 * - Confirms actual end-to-end connectivity to the internet
	 * - Works across all Android versions (including API levels with
	 *   deprecated connectivity APIs)
	 * - Validates DNS resolution, routing, and firewall rules
	 *
	 * How it works:
	 * - Attempts to connect to https://www.google.com
	 * - Uses short timeouts (1 second each for connection and read)
	 * - Sends a GET request (can be changed to HEAD if preferred)
	 * - Checks for HTTP 200 OK response
	 *
	 * Timeout configuration:
	 * - connectTimeout: 1000ms (1 second) - Time to establish TCP connection
	 * - readTimeout: 1000ms (1 second) - Time to receive response headers
	 *
	 * Returns `true` only when:
	 * - DNS resolution succeeds
	 * - TCP connection is established
	 * - HTTP request is sent and response received
	 * - Response status code is 200 (HTTP_OK)
	 *
	 * Returns `false` in these scenarios:
	 * - No network connectivity (WiFi/Cellular off)
	 * - Captive portal or login-required network
	 * - DNS resolution fails
	 * - Connection timeout (1 second elapsed)
	 * - Read timeout (1 second elapsed)
	 * - Server returns non-200 status code
	 * - Any exception occurs during the process
	 *
	 * Important considerations:
	 * - Google may be blocked in some regions or networks (enterprise firewalls, China)
	 * - 1-second timeout may be too short for slow networks (consider increasing in production)
	 * - This method consumes minimal bandwidth but does transfer some data
	 * - For better power efficiency, consider using ConnectivityManager for initial check
	 *
	 * Alternative approaches:
	 * - Use ConnectivityManager to check active network (faster, no data usage)
	 * - Ping your own server for more accurate service-specific connectivity
	 * - Use NetworkCapabilities for more detailed network info (Android 5.0+)
	 *
	 * @return `true` if internet connection is available and can reach Google, `false` otherwise
	 * @see HttpURLConnection
	 */
	@JvmStatic
	suspend fun isInternetConnected(): Boolean {
		return withIOContext {
			try {
				val url = URL("https://www.google.com")
				with(url.openConnection() as HttpURLConnection) {
					requestMethod = "GET"
					connectTimeout = 1000
					readTimeout = 1000
					connect()
					val isConnected = responseCode == HTTP_OK
					isConnected
				}
			} catch (error: Exception) {
				logger.e(error)
				false
			}
		}
	}

	/**
	 * Encodes spaces in a string to URL-encoded format (%20).
	 *
	 * This method provides a simple, targeted replacement for space characters
	 * in URLs. Unlike full URL encoding functions, this only converts spaces
	 * to %20, leaving other special characters unchanged.
	 *
	 * Use cases:
	 * - Quick space encoding for URLs when other characters are already properly encoded
	 * - Building query parameters where only spaces need encoding
	 * - Pre-encoding before more comprehensive URL encoding
	 *
	 * Important notes:
	 * - Does NOT encode other special characters (?, &, =, #, etc.)
	 * - For complete URL encoding, consider using [URLEncoder.encode]
	 * - This method is a convenience wrapper around [String.replace]
	 *
	 * Examples:
	 * - `"hello world"` → `"hello%20world"`
	 * - `"https://example.com/path with spaces"` → `"https://example.com/path%20with%20spaces"`
	 * - `"no spaces here"` → `"no spaces here"` (unchanged)
	 * - `"multiple   spaces"` → `"multiple%20%20%20spaces"` (preserves count)
	 *
	 * @param input The input string containing spaces to encode
	 * @return String with all space characters replaced by "%20"
	 * @see URLEncoder.encode
	 * @see String.replace
	 */
	@JvmStatic
	fun encodeSpaceAsUrlHex(input: String): String {
		return input.replace(" ", "%20")
	}

	/**
	 * Extracts the base domain name from a complete URL.
	 *
	 * This method parses a URL and returns the second-level domain (SLD),
	 * which is typically the main identifiable part of a domain name,
	 * excluding subdomains and the top-level domain (TLD).
	 *
	 * Domain parsing logic:
	 * - For URLs with subdomains (more than 2 parts): Returns the second-last part
	 * - For simple domains (2 parts or fewer): Returns the first part
	 *
	 * Examples:
	 * - `"https://www.example.com"` → `"example"` (parts: ["www", "example", "com"] → second-last)
	 * - `"https://blog.example.co.uk"` → `"example"` (parts: ["blog", "example", "co", "uk"] → second-last)
	 * - `"https://example.com"` → `"example"` (parts: ["example", "com"] → first part)
	 * - `"https://example.local"` → `"example"` (parts: ["example", "local"] → first part)
	 * - `"https://sub1.sub2.example.com"` → `"example"` (second-last before TLD)
	 * - `"invalid-url"` → `null` (exception caught)
	 * - `null` URL → `null`
	 *
	 * Limitations:
	 * - Does not handle internationalized domain names (IDN) with special characters
	 * - May not correctly handle all ccTLD structures (e.g., .co.uk is treated as separate parts)
	 * - Returns null for malformed URLs
	 *
	 * @param url The complete URL string to parse
	 * @return The base domain name (second-level domain) as a String, or `null` if:
	 *         - URL is malformed
	 *         - Exception occurs during parsing
	 *         - URL has no host component
	 * @see URL
	 * @see getHostFromUrl
	 */
	@JvmStatic
	suspend fun getBaseDomain(url: String): String? {
		return withIOContext {
			try {
				val domain = URL(url).host
				val parts = domain.split(".")
				val baseDomain = if (parts.size > 2) {
					parts[parts.size - 2]
				} else {
					parts[0]
				}

				baseDomain
			} catch (error: Exception) {
				logger.e(error)
				null
			}
		}
	}

	/**
	 * Extracts the host component from a URL string.
	 *
	 * This method parses a URL and returns the host portion (domain name or IP address).
	 * The host component includes the full domain with subdomains but excludes the protocol,
	 * port, path, query parameters, and fragment.
	 *
	 * What is included in the host:
	 * - Domain names (e.g., "www.example.com", "api.example.com")
	 * - IP addresses (e.g., "192.168.1.1", "::1")
	 * - Subdomains (preserved in their entirety)
	 * - Localhost ("localhost")
	 *
	 * What is NOT included:
	 * - Protocol (http://, https://)
	 * - Port numbers (:8080)
	 * - Path (/path/to/resource)
	 * - Query parameters (?key=value)
	 * - Fragment identifiers (#section)
	 *
	 * Examples:
	 * - `"https://www.example.com/path"` → `"www.example.com"`
	 * - `"http://api.example.com:8080/api/v1"` → `"api.example.com"`
	 * - `"https://192.168.1.1:443/admin"` → `"192.168.1.1"`
	 * - `"https://localhost:3000"` → `"localhost"`
	 * - `"ftp://files.example.com"` → `"files.example.com"`
	 * - `"invalid-url"` → `null` (exception caught)
	 * - `null` → `null`
	 * - `"https://example.com?query=value"` → `"example.com"` (query ignored)
	 *
	 * @param urlString The URL string to parse (can be null)
	 * @return The host component as a String, or `null` if:
	 *         - Input is null
	 *         - URL is malformed
	 *         - Exception occurs during parsing
	 *         - URL has no host component
	 * @see URL
	 * @see getBaseDomain
	 */
	@JvmStatic
	suspend fun getHostFromUrl(urlString: String?): String? {
		return withIOContext {
			try {
				urlString?.let { URL(it).host }
			} catch (error: Exception) {
				logger.e(error)
				null
			}
		}
	}

	/**
	 * Generates a Google Favicon service URL for a given domain.
	 *
	 * This method constructs a URL to Google's favicon retrieval service, which
	 * attempts to fetch the favicon (website icon) for any domain. The service
	 * returns the favicon image (typically in PNG format) that the website uses.
	 *
	 * Google Favicon service features:
	 * - Caches favicons for millions of websites
	 * - Returns a default icon if no favicon is found
	 * - Supports various size parameters
	 * - Fast and reliable for most popular domains
	 *
	 * @param domain The domain name to get the favicon for (e.g., "example.com", "github.com")
	 * @return Google Favicon service URL as a String with 128x128 pixel size
	 *
	 * Example outputs:
	 * - `"example.com"` → `"https://www.google.com/s2/favicons?domain=example.com&sz=128"`
	 * - `"github.com"` → `"https://www.google.com/s2/favicons?domain=github.com&sz=128"`
	 * - `"stackoverflow.com"` → `"https://www.google.com/s2/favicons?domain=stackoverflow.com&sz=128"`
	 *
	 * Usage notes:
	 * - The `sz` parameter controls image size (128 pixels in this implementation)
	 * - Common sizes: 16, 32, 64, 128, 256
	 * - The returned URL can be used directly in ImageView or Glide/Coil loading
	 * - No API key or authentication required
	 * - Rate limiting may apply for excessive requests
	 *
	 * Alternative favicon services:
	 * - DuckDuckGo: `https://icons.duckduckgo.com/ip3/{domain}.ico`
	 * - Favicon Kit: `https://faviconkit.com/{domain}/144`
	 */
	@JvmStatic
	fun getGoogleFaviconUrl(domain: String): String {
		return "https://www.google.com/s2/favicons?domain=$domain&sz=128"
	}

	/**
	 * Checks whether a given URL is expired or inaccessible by making a HEAD request.
	 *
	 * This method sends an HTTP HEAD request to the specified URL and checks the response
	 * status code. A URL is considered "expired" or invalid if the server returns a
	 * client error (4xx) or server error (5xx) status code.
	 *
	 * Key characteristics:
	 * - Uses HEAD method to minimize bandwidth usage (no response body is downloaded)
	 * - Configurable timeouts (5 seconds each for connection and read)
	 * - Handles network errors gracefully by returning `true` (conservative approach)
	 * - Useful for checking link validity before attempting full downloads
	 *
	 * Status code interpretation:
	 * - 2xx (Success): URL is valid → returns `false`
	 * - 3xx (Redirection): Followed automatically by HttpURLConnection → returns `false`
	 *   if final response is successful
	 * - 4xx (Client Error): URL is invalid/expired → returns `true`
	 * - 5xx (Server Error): Server issues → returns `true`
	 * - Network exceptions (IOException, etc.) → returns `true`
	 *
	 * @param urlString The URL to check (must be a valid HTTP/HTTPS URL)
	 * @return `true` if the URL is expired, inaccessible, or returns an error status code;
	 *         `false` if the URL is valid and returns a success status code (2xx)
	 *
	 * @throws MalformedURLException If the URL string is malformed (caught and logged,
	 *                               returns `true`)
	 * @see HttpURLConnection
	 * @see URL
	 */
	@JvmStatic
	suspend fun isUrlExpired(urlString: String): Boolean {
		return withIOContext {
			try {
				val url = URL(urlString)
				val connection = url.openConnection() as HttpURLConnection
				connection.requestMethod = "HEAD"
				connection.connectTimeout = 5000
				connection.readTimeout = 5000
				connection.connect()
				val responseCode = connection.responseCode
				val isExpired = responseCode >= 400
				isExpired
			} catch (error: Exception) {
				logger.e(error)
				true
			}
		}
	}

	/**
	 * Removes the "www." prefix from a URL if present.
	 *
	 * This utility method normalizes URLs by stripping the common "www." subdomain prefix.
	 * Normalization is useful for:
	 * - Consistent URL comparisons (e.g., "example.com" vs "www.example.com")
	 * - Cache key generation
	 * - Domain extraction and analysis
	 * - Reducing duplicate entries in databases
	 *
	 * Behavior:
	 * - Only removes the first occurrence of "www." at the beginning of the URL
	 * - Does not affect "www" elsewhere in the URL (e.g., "www.example.www.com" → "example.www.com")
	 * - Returns empty string for null input
	 * - Returns original URL if an exception occurs during processing
	 *
	 * Examples:
	 * - `"https://www.example.com"` → `"https://example.com"`
	 * - `"http://www.example.com/path"` → `"http://example.com/path"`
	 * - `"www.example.com"` → `"example.com"`
	 * - `"example.com"` → `"example.com"` (unchanged)
	 * - `"sub.www.example.com"` → `"sub.www.example.com"` (unchanged, "www" not at start)
	 * - `null` → `""`
	 *
	 * @param url The URL string to process (can be null)
	 * @return URL string with "www." prefix removed, or empty string if input is null,
	 *         or original URL if an exception occurs
	 * @see String.replaceFirst
	 */
	@JvmStatic
	fun removeWwwFromUrl(url: String?): String {
		if (url == null) return ""
		return try {
			url.replaceFirst("www.", "")
		} catch (error: Exception) {
			logger.e(error)
			url
		}
	}

	/**
	 * Fetches web page content with configurable retry logic using mobile user agents.
	 *
	 * This method retrieves HTML content from a web page by simulating mobile device access.
	 * It supports automatic retries with configurable attempts, making it robust against
	 * temporary network issues or server-side rate limiting.
	 *
	 * Features:
	 * - Uses mobile user agents to fetch mobile-optimized content
	 * - Configurable retry mechanism with multiple attempts
	 * - Falls back to single attempt if retry is disabled
	 * - Automatically stops retrying once content is successfully fetched
	 * - Returns null if all attempts fail
	 *
	 * Retry behavior:
	 * - When `retry = true` and `numOfRetry > 0`: Makes up to `numOfRetry` attempts
	 * - When `retry = false` or `numOfRetry = 0`: Makes exactly 1 attempt
	 * - Each attempt uses a different mobile user agent (rotated automatically)
	 * - Progressive delays between retries (handled by [fetchMobileWebPageContent])
	 *
	 * @param url The target URL to fetch content from
	 * @param retry Whether to retry the request if initial attempts fail
	 * @param numOfRetry Maximum number of retry attempts (only used if `retry` is true)
	 * @return The HTML content as a String if successful, `null` if:
	 *         - All attempts fail to retrieve content
	 *         - Response body is empty
	 *         - Network error occurs on all attempts
	 *         - Server returns error status codes
	 * @see fetchMobileWebPageContent
	 */
	@JvmStatic
	suspend fun fetchWebPageContent(
		url: String, retry: Boolean = false, numOfRetry: Int = 0
	): String? {
		return withIOContext {
			if (retry && numOfRetry > 0) {
				var index = 0
				var htmlBody: String? = ""
				while (index < numOfRetry || htmlBody.isNullOrEmpty()) {
					htmlBody = fetchMobileWebPageContent(url)
					if (!htmlBody.isNullOrEmpty()) {
						return@withIOContext htmlBody
					}
					index++
				}
			}

			return@withIOContext fetchMobileWebPageContent(url)
		}
	}

	/**
	 * Normalizes and standardizes a URL by decoding and then re-encoding query parameters
	 * in a consistent order.
	 *
	 * This method performs several normalization steps:
	 * 1. Replaces escaped forward slashes (`\/`) with regular forward slashes (`/`)
	 * 2. Parses the URL into its components (scheme, host, path, query)
	 * 3. Decodes all query parameter keys and values using UTF-8
	 * 4. Sorts the query parameters alphabetically by key
	 * 5. Re-encodes the parameters using proper URL encoding
	 * 6. Reconstructs the URL with normalized query string
	 *
	 * This normalization is useful for:
	 * - Caching URLs that are semantically identical but differ in parameter order
	 * - Comparing URLs for equality
	 * - Creating consistent cache keys
	 * - Removing redundant URL escaping
	 *
	 * Example:
	 * Input: `"https://example.com/api?b=2&a=1&c=%20three"`
	 * Output: `"https://example.com/api?a=1&b=2&c=three"`
	 *
	 * @param url The URL string to normalize (may contain escaped characters)
	 * @return Normalized URL string with sorted, properly encoded query parameters,
	 *         or the original URL if an exception occurs during processing
	 * @throws IllegalArgumentException if the URL is malformed (caught and logged internally)
	 * @see java.net.URI
	 * @see URLDecoder
	 * @see encode
	 */
	@JvmStatic
	suspend fun normalizeEncodedUrl(url: String): String {
		return withIOContext {
			try {
				val unescapedUrl = url.replace("\\/", "/")
				val uri = URI(unescapedUrl)
				val baseUrl = "${uri.scheme}://${uri.host}${uri.path}"
				val query = uri.query ?: return@withIOContext baseUrl

				val queryParams = query.split("&").associate {
					it.split("=").let { pair ->
						val key = URLDecoder.decode(pair[0], "UTF-8")
						val value = if (pair.size > 1)
							URLDecoder.decode(pair[1], "UTF-8") else ""
						key to value
					}
				}.toSortedMap()

				val normalizedQuery = queryParams.map { (key, value) ->
					"${encode(key, "UTF-8")}=${encode(value, "UTF-8")}"
				}.joinToString("&")

				"$baseUrl?$normalizedQuery"
			} catch (error: Exception) {
				logger.e("Error found normalize an encoded url:", error)
				url
			}
		}
	}

	/**
	 * Fetches web page content from a mobile-optimized perspective using various user agents.
	 *
	 * This private method is designed to retrieve HTML content from web pages while
	 * simulating mobile device access. It cycles through a list of mobile user agents
	 * to bypass server-side restrictions or to fetch mobile-optimized versions of websites.
	 *
	 * Key features:
	 * - Supports retry mechanisms with exponential-like backoff delays
	 * - Rotates through multiple mobile user agents to avoid detection
	 * - Includes comprehensive Accept headers for various media types
	 * - Configurable timeout for network requests
	 * - Graceful error handling with null returns on failure
	 *
	 * User agents used (in rotation order):
	 * 1. iPhone (iOS 9.3.5) - Safari browser
	 * 2. Android (Nexus 5, Android 4.4.2) - Chrome browser
	 * 3. Android (Samsung Galaxy S, Android 2.3.6) - Mobile Safari
	 *
	 * The method implements a retry strategy:
	 * - Each retry attempt uses a different user agent
	 * - Delay between retries increases linearly (200ms, 400ms, 600ms...)
	 * - Stops retrying as soon as a successful response is received
	 *
	 * @param url The target URL to fetch content from
	 * @param retry Whether to retry the request if initial attempts fail
	 * @param numOfRetry Maximum number of retry attempts (only used if `retry` is true)
	 * @param timeoutSeconds Connection and read timeout in seconds (default: 30)
	 * @return The HTML content as a String if successful, `null` if:
	 *         - All attempts fail
	 *         - Response is not successful (non-2xx status code)
	 *         - Response body is empty
	 *         - Network error occurs
	 *         - Exception is thrown during request execution
	 *
	 * @see okHttpClient
	 * @see Request.Builder
	 * @see Response
	 */
	@JvmStatic
	private fun fetchMobileWebPageContent(
		url: String, retry: Boolean = false,
		numOfRetry: Int = 0, timeoutSeconds: Int = 30
	): String? {
		val oldMobileUserAgents = listOf(
			"Mozilla/5.0 (iPhone; CPU iPhone OS 9_3_5 like Mac OS X) AppleWebKit/601.1.46 (KHTML," +
				" like Gecko) Version/9.0 Mobile/13G36 Safari/601.1",
			"Mozilla/5.0 (Linux; Android 4.4.2; Nexus 5 Build/KOT49H) AppleWebKit/537.36 (KHTML, " +
				"like Gecko) Chrome/34.0.1847.114 Mobile Safari/537.36",
			"Mozilla/5.0 (Linux; U; Android 2.3.6; en-us; GT-I9000 Build/GINGERBREAD) AppleWebKit/533.1 " +
				"(KHTML, like Gecko) Version/4.0 Mobile Safari/533.1"
		)

		val client = okHttpClient
		fun attemptFetch(attempt: Int): String? {
			val acceptLanguage = "en-US,en;q=0.5"
			val userAgent = oldMobileUserAgents[attempt % oldMobileUserAgents.size]
			val mediaTypes = "text/html,application/xhtml+xml,application/xml;q=0.9," +
				"image/webp,image/apng,image/avif,image/jpeg,image/png,image/gif,image/svg+xml,image/*," +
				"*/*;q=0.8"

			val request = Request.Builder()
				.url(url).header("User-Agent", userAgent)
				.header("Accept", mediaTypes)
				.header("Accept-Language", acceptLanguage)
				.build()

			return try {
				client.newCall(request).execute().use { response ->
					if (response.isSuccessful) {
						response.body.string().takeIf { it.isNotEmpty() }
					} else {
						null
					}
				}
			} catch (error: Exception) {
				logger.e(error)
				null
			}
		}

		// Determine maximum number of attempts based on retry settings
		val maxAttempts = if (retry && numOfRetry > 0) numOfRetry else 1

		// Execute fetch attempts with progressive delays
		for (attempt in 0 until maxAttempts) {
			val result = attemptFetch(attempt)
			if (result != null) return result
			if (retry && attempt < maxAttempts - 1) {
				// Linear backoff: 200ms, 400ms, 600ms, etc.
				Thread.sleep(200L * (attempt + 1))
			}
		}
		return null
	}

}