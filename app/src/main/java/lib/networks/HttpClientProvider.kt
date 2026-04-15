package lib.networks

import lib.process.*
import okhttp3.*
import java.util.concurrent.*

object HttpClientProvider {

	val okHttpClient: OkHttpClient by lazy {
		OkHttpClient.Builder()
			.followRedirects(true)
			.followSslRedirects(true)
			.protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
			.connectTimeout(5, TimeUnit.SECONDS)
			.readTimeout(10, TimeUnit.SECONDS)
			.connectionPool(ConnectionPool(20, 5, TimeUnit.MINUTES))
			.dispatcher(Dispatcher().apply {
				maxRequests = 64
				maxRequestsPerHost = 16
			})
			.build()
	}

	@JvmStatic
	suspend fun initialize() {
		withIOContext {
			okHttpClient
		}
	}
}
