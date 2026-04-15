package lib.networks

import app.core.engines.youtube.parser.*
import kotlinx.coroutines.*
import lib.networks.HttpClientProvider.okHttpClient
import lib.process.*
import okhttp3.*
import org.jsoup.*
import org.jsoup.nodes.*
import java.net.*
import java.net.HttpURLConnection.*
import java.net.URLEncoder.*

object URLUtilityKT {

	private val logger = LogHelperUtils.from(javaClass)

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

	@JvmStatic
	fun encodeSpaceAsUrlHex(input: String): String {
		return input.replace(" ", "%20")
	}

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

	@JvmStatic
	fun getGoogleFaviconUrl(domain: String): String {
		return "https://www.google.com/s2/favicons?domain=$domain&sz=128"
	}

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

		val maxAttempts = if (retry && numOfRetry > 0) numOfRetry else 1
		for (attempt in 0 until maxAttempts) {
			val result = attemptFetch(attempt)
			if (result != null) return result
			if (retry && attempt < maxAttempts - 1) {
				Thread.sleep(200L * (attempt + 1))
			}
		}
		return null
	}

}