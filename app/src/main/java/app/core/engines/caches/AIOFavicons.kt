package app.core.engines.caches

import app.core.AIOApp.Companion.INSTANCE
import lib.networks.URLUtilityKT.getBaseDomain
import lib.networks.URLUtilityKT.getGoogleFaviconUrl
import lib.process.*
import java.io.*
import java.net.*
import javax.net.ssl.*

class AIOFavicons {
	private val logger = LogHelperUtils.from(javaClass)
	private val faviconDir = File(INSTANCE.filesDir, "favicons")
		.apply { if (!exists()) mkdirs() }

	private suspend fun saveFavicon(url: String): String? {
		return withIOContext {
			val baseDomain = getBaseDomain(url) ?: run {
				return@withIOContext null
			}

			val faviconFile = File(faviconDir, "$baseDomain.png")
			if (faviconFile.exists()) {
				return@withIOContext faviconFile.absolutePath
			}

			val faviconUrl = getGoogleFaviconUrl(url)
			return@withIOContext try {
				val openConnection = URL(faviconUrl).openConnection()
				val connection = openConnection as HttpsURLConnection
				connection.inputStream.use { input ->
					FileOutputStream(faviconFile).use { output ->
						input.copyTo(output)
					}
				}
				faviconFile.absolutePath
			} catch (error: Exception) {
				logger.e(error)
				null
			}
		}
	}

	suspend fun getFavicon(url: String): String? {
		return withIOContext {
			if (url.isEmpty()) return@withIOContext null
			val baseDomain = getBaseDomain(url) ?: run { return@withIOContext null }
			val faviconFile = File(faviconDir, "$baseDomain.png")
			return@withIOContext if (faviconFile.exists()) faviconFile.absolutePath
			else saveFavicon(url)
		}
	}
}