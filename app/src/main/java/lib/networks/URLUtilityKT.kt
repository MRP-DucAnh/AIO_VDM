package lib.networks

import app.core.engines.youtube.parser.*
import kotlinx.coroutines.*
import lib.process.*
import okhttp3.*
import org.jsoup.*
import org.jsoup.nodes.*
import java.io.*
import java.net.*
import java.net.HttpURLConnection.*
import java.util.concurrent.*

object URLUtilityKT {

	private val logger = LogHelperUtils.from(javaClass)

	@JvmStatic
	fun extractHostUrl(urlString: String): String {
		try {
			val uri = URI(urlString)
			return "${uri.scheme}://${uri.host}"
		} catch (error: Exception) {
			logger.e("Error found while extracting host:", error)
			return ""
		}
	}

	@JvmStatic
	fun isHostOnly(url: String): Boolean {
		return try {
			val parsedUrl = URL(url)
			val path = parsedUrl.path
			path.isNullOrEmpty() || path == "/"
		} catch (error: Exception) {
			logger.e("Error found while checking host only url:", error)
			false
		}
	}

	@JvmStatic
	fun isValidEmail(email: String): Boolean {
		if (!email.contains("@")) return false

		val parts = email.split("@")
		if (parts.size != 2) return false
		val (local, domain) = parts

		if (local.isEmpty() || domain.isEmpty()) return false
		if (!domain.contains(".")) return false

		// small regex just for sanity
		val simple = "^[A-Za-z0-9._%+-]+$".toRegex()
		return simple.matches(local)
	}

	@JvmStatic
	fun getTitleByParsingHTML(url: String, callback: (String?) -> Unit) {
		try {
			val client = OkHttpClient()
			val request = Request.Builder().url(url).build()

			client.newCall(request).enqueue(object : Callback {
				override fun onFailure(call: Call, e: IOException) {
					callback(null)
				}

				override fun onResponse(call: Call, response: Response) {
					response.use {
						if (!response.isSuccessful) {
							callback(null); return
						}
						val html = response.body.string()
						if (html.isEmpty()) {
							callback(null); return
						}
						val document = Jsoup.parse(html)
						val title = document.title().ifEmpty { null }
						callback(title)
					}
				}
			})
		} catch (error: Exception) {
			logger.e("Error found while parsing title from an url:", error)
			callback(null)
		}
	}

	@JvmStatic
	@Deprecated("This method is deprecated, use getWebpageTitleOrDescription instead.")
	suspend fun getWebpageTitleOrDescription(
		websiteUrl: String,
		returnDescription: Boolean = false,
		userGivenHtmlBody: String? = null,
		callback: (String?) -> Unit
	) {
		try {
			val isYoutubeMusicPage = extractHostUrl(websiteUrl).contains("music.youtube", true)
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

	@JvmStatic
	suspend fun getWebpageTitleOrDescription(
		websiteUrl: String,
		returnDescription: Boolean = false,
		userGivenHtmlBody: String? = null
	): String? = withContext(Dispatchers.IO) {
		try {
			val isYoutubeMusicPage =
				extractHostUrl(websiteUrl).contains("music.youtube", true)

			if (isYoutubeMusicPage) {
				YTVideoStreamParser
					.getTitle(websiteUrl)
					?.takeIf { it.isNotEmpty() }
					?.let { return@withContext "${it}_Youtube_Music_Audio" }
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

	@JvmStatic
	fun getFaviconUrl(websiteUrl: String): String? {
		val standardFaviconUrl = "$websiteUrl/favicon.ico"
		if (isFaviconAvailable(standardFaviconUrl)) return standardFaviconUrl

		return try {
			val doc: Document = Jsoup.connect(websiteUrl).get()
			val faviconUrl = doc.head().select("link[rel~=(icon|shortcut icon)]")
				.mapNotNull { it.attr("href").takeIf { href -> href.isNotEmpty() } }
				.map { href -> if (href.startsWith("http")) href else "$websiteUrl/$href" }
				.firstOrNull { isFaviconAvailable(it) }
			faviconUrl
		} catch (error: Exception) {
			logger.e("Error found while getting favicon url from a site link:", error)
			null
		}
	}

	@JvmStatic
	fun isFaviconAvailable(faviconUrl: String): Boolean {
		return try {
			val url = URL(faviconUrl)
			val connection = url.openConnection() as HttpURLConnection
			connection.requestMethod = "HEAD"
			val isAvailable = connection.responseCode == HTTP_OK
			isAvailable
		} catch (error: Exception) {
			logger.e("Error found while checking a valid accessible favicon url:", error)
			false
		}
	}

	@JvmStatic
	fun fetchFileSize(httpClient: OkHttpClient, url: String): Long {
		return try {
			val request = Request.Builder().url(url).head().build()
			httpClient.newCall(request).execute().use { response ->
				val fileSize = response.header("Content-Length")?.toLong() ?: -1L
				fileSize
			}
		} catch (error: Exception) {
			logger.e("Error found while getting file size from a url by okhttp():", error)
			-1L
		}
	}

	@JvmStatic
	fun isInternetConnected(): Boolean {
		return try {
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
			logger.e("Error found while checking live working internet connection:", error)
			false
		}
	}

	@JvmStatic
	fun encodeSpaceAsUrlHex(input: String): String {
		return input.replace(" ", "%20")
	}

	@JvmStatic
	fun getBaseDomain(url: String): String? {
		return try {
			val domain = URL(url).host
			val parts = domain.split(".")
			val baseDomain = if (parts.size > 2) {
				parts[parts.size - 2]
			} else {
				parts[0]
			}

			baseDomain
		} catch (error: Exception) {
			logger.e("Error found getting base domain from a url:", error)
			null
		}
	}

	@JvmStatic
	fun getHostFromUrl(urlString: String?): String? {
		return try {
			urlString?.let { URL(it).host }
		} catch (error: Exception) {
			logger.e("Error found getting host domain from a url:", error)
			null
		}
	}

	@JvmStatic
	fun getGoogleFaviconUrl(domain: String): String {
		return "https://www.google.com/s2/favicons?domain=$domain&sz=128"
	}

	@JvmStatic
	fun isUrlExpired(urlString: String): Boolean {
		return try {
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
			logger.e("Error found checking if the url has expired or not:", error)
			true
		}
	}

	@JvmStatic
	fun removeWwwFromUrl(url: String?): String {
		if (url == null) return ""
		return try {
			url.replaceFirst("www.", "")
		} catch (error: Exception) {
			logger.e("Error found removing www from a url:", error)
			url
		}
	}

	@JvmStatic
	fun fetchMobileWebPageContent(
		url: String,
		retry: Boolean = false,
		numOfRetry: Int = 0,
		timeoutSeconds: Int = 30
	): String? {
		// Predefined old mobile browser user-agents
		val oldMobileUserAgents = listOf(
			"Mozilla/5.0 (iPhone; CPU iPhone OS 9_3_5 like Mac OS X) AppleWebKit/601.1.46 (KHTML," +
				" like Gecko) Version/9.0 Mobile/13G36 Safari/601.1",
			"Mozilla/5.0 (Linux; Android 4.4.2; Nexus 5 Build/KOT49H) AppleWebKit/537.36 (KHTML, " +
				"like Gecko) Chrome/34.0.1847.114 Mobile Safari/537.36",
			"Mozilla/5.0 (Linux; U; Android 2.3.6; en-us; GT-I9000 Build/GINGERBREAD) AppleWebKit/533.1 " +
				"(KHTML, like Gecko) Version/4.0 Mobile Safari/533.1"
		)

		// Reuse the same OkHttpClient to benefit from connection pooling
		val client = OkHttpClient.Builder()
			.connectTimeout(timeoutSeconds.toLong(), TimeUnit.SECONDS)
			.readTimeout(timeoutSeconds.toLong(), TimeUnit.SECONDS)
			.writeTimeout(timeoutSeconds.toLong(), TimeUnit.SECONDS)
			.build()

		fun attemptFetch(attempt: Int): String? {
			val request = Request.Builder()
				.url(url)
				.header("User-Agent", oldMobileUserAgents[attempt % oldMobileUserAgents.size])
				.header(
					name = "Accept",
					value = "text/html,application/xhtml+xml,application/xml;q=0.9," +
						"image/webp,image/apng,image/avif,image/jpeg,image/png,image/gif,image/svg+xml,image/*," +
						"*/*;q=0.8"
				)
				.header("Accept-Language", "en-US,en;q=0.5")
				.build()

			return try {
				client.newCall(request).execute().use { response ->
					if (response.isSuccessful) response.body.string().takeIf { it.isNotEmpty() }
					else null
				}
			} catch (error: Exception) {
				logger.e("Error found fetching mobile webpage content from a url:", error)
				null
			}
		}

		// Retry logic
		val maxAttempts = if (retry && numOfRetry > 0) numOfRetry else 1
		for (attempt in 0 until maxAttempts) {
			val result = attemptFetch(attempt)
			if (result != null) return result
			if (retry && attempt < maxAttempts - 1) {
				Thread.sleep(200L * (attempt + 1)) // smaller backoff to speed things up
			}
		}
		return null
	}

	@JvmStatic
	fun fetchWebPageContent(
		url: String,
		retry: Boolean = false,
		numOfRetry: Int = 0
	): String? {
		if (retry && numOfRetry > 0) {
			var index = 0
			var htmlBody: String? = ""
			while (index < numOfRetry || htmlBody.isNullOrEmpty()) {
				htmlBody = fetchMobileWebPageContent(url)
				if (!htmlBody.isNullOrEmpty()) return htmlBody
				index++
			}
		}

		return fetchMobileWebPageContent(url)
	}

	@JvmStatic
	fun normalizeEncodedUrl(url: String): String {
		try {
			val unescapedUrl = url.replace("\\/", "/")
			val uri = URI(unescapedUrl)
			val baseUrl = "${uri.scheme}://${uri.host}${uri.path}"
			val query = uri.query ?: return baseUrl

			val queryParams = query.split("&").associate {
				it.split("=").let { pair ->
					val key = URLDecoder.decode(pair[0], "UTF-8")
					val value = if (pair.size > 1) URLDecoder.decode(pair[1], "UTF-8") else ""
					key to value
				}
			}.toSortedMap()

			val normalizedQuery = queryParams.map { (key, value) ->
				"${URLEncoder.encode(key, "UTF-8")}=${URLEncoder.encode(value, "UTF-8")}"
			}.joinToString("&")

			return "$baseUrl?$normalizedQuery"
		} catch (error: Exception) {
			logger.e("Error found normalize an encoded url:", error)
			return url
		}
	}
}