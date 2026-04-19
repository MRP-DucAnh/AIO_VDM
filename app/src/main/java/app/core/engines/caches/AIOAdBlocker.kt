package app.core.engines.caches

import app.core.*
import lib.networks.HttpClientProvider.okHttpClient
import lib.process.*
import okhttp3.*
import java.io.*

class AIOAdBlocker {
	private var adBlockHosts: Set<String> = emptySet()

	fun getAdBlockHosts(): Set<String> {
		return adBlockHosts
	}

	suspend fun fetchAdFilters() {
		withIOContext {
			adBlockHosts = try {
				fetchHostsFromUrl() ?: defaultHosts.toSet()
			} catch (_: IOException) {
				defaultHosts.toSet()
			}
		}
	}

	private suspend fun fetchHostsFromUrl(): Set<String>? {
		return withIOContext {
			val request = Request.Builder()
				.url(APP_GITHUB_RAW_URL)
				.build()

			okHttpClient.newCall(request).execute().use { response ->
				if (!response.isSuccessful) return@use null
				val body = response.body?.string() ?: ""
				body.lines()
					.filterNot { it.startsWith("#") || it.isBlank() }
					.map { it.trim() }
					.toSet()
			}
		}
	}

	private val defaultHosts = listOf(
		"afcdn.net",
		"aucdn.net",
		"tsyndicate.com"
	)
}