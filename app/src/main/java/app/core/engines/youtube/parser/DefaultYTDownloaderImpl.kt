package app.core.engines.youtube.parser

import lib.networks.HttpClientProvider
import lib.process.LogHelperUtils
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.io.IOException

/**
 * A custom implementation of NewPipe's [Downloader] that bridges the NewPipe Extractor library
 * with the application's network layer.
 *
 * This implementation delegates all HTTP/HTTPS network operations to a shared OkHttp client
 * provided by [HttpClientProvider]. By using a centralized client, it ensures efficient resource
 * management, including connection pooling and caching, across the entire application.
 *
 * Key responsibilities:
 * - **Executing Network Requests:** Translates NewPipe [Request] objects into OkHttp requests
 *   and executes them synchronously.
 * - **Response Translation:** Converts OkHttp responses back into the [Response] format
 *   expected by the extractor.
 * - **Centralized Networking:** Leverages the app's standard [okhttp3.OkHttpClient] for all
 *   data extraction needs (e.g., fetching video metadata, comments, and stream URLs).
 * - **Observability:** Integrates with [LogHelperUtils] to provide visibility into network
 *   traffic generated during the extraction process.
 *
 * @see Downloader
 * @see HttpClientProvider
 */
class DefaultYTDownloaderImpl : Downloader() {

	/**
	 * A logger instance for this class, utilized to monitor network request execution
	 * and facilitate debugging of the extraction process.
	 */
	private val logger = LogHelperUtils.from(javaClass)

	/**
	 * The shared [okhttp3.OkHttpClient] instance used to perform network operations.
	 *
	 * This client is retrieved from [HttpClientProvider] to leverage centralized
	 * configuration, including connection pooling and optimized resource management.
	 */
	private val okHttpClient = HttpClientProvider.okHttpClient

	/**
	 * Executes a network request using OkHttp and maps it to a NewPipe [Response].
	 *
	 * This implementation translates the NewPipe [Request] (URL, method, headers, and body)
	 * into an OkHttp request, executes it via the shared [okHttpClient], and wraps the
	 * result. It includes logging for request lifecycle monitoring and debugging.
	 *
	 * @param request The [Request] provided by the NewPipe extractor.
	 * @return A [Response] containing the status code, headers, and response body.
	 * @throws IOException If a network error or timeout occurs.
	 * @throws ReCaptchaException If the response indicates a reCAPTCHA challenge.
	 */
	@Throws(IOException::class, ReCaptchaException::class)
	override fun execute(request: Request): Response {
		// Log the request initiation with method and URL
		val method = request.httpMethod().uppercase()
		logger.d("Executing $method request for URL: ${request.url()}")

		// Build OkHttp request from NewPipe request
		val builder = okhttp3.Request.Builder().url(request.url())

		// Copy all headers from NewPipe request to OkHttp request
		request.headers().forEach { (key, values) ->
			values.forEach { value ->
				builder.addHeader(key, value)
				logger.d("Header added: $key=$value")
			}
		}

		// Set HTTP method and request body based on method type
		when (method) {
			"HEAD" -> builder.head()
			"GET" -> builder.get()
			"POST" -> builder.post((request.dataToSend() ?: ByteArray(0)).toRequestBody())
			"PUT" -> builder.put((request.dataToSend() ?: ByteArray(0)).toRequestBody())
			"DELETE" -> {
				val data = request.dataToSend()
				if (data != null) builder.delete(data.toRequestBody()) else builder.delete()
			}
			else -> {
				// Fallback for unsupported methods (should not occur in normal operation)
				logger.d("Unknown HTTP method '$method', defaulting to GET")
				builder.get()
			}
		}

		// Execute the request using the shared OkHttp client
		val okResponse: okhttp3.Response = okHttpClient.newCall(builder.build()).execute()

		// Log response status and source URL
		logger.d("Response received: ${okResponse.code} from ${okResponse.request.url}")

		// Read the complete response body as string
		// Note: This loads the entire body into memory, which is appropriate for
		// metadata requests but not for large binary content like videos
		val responseBody = okResponse.body.string()
		logger.d("Response body length: ${responseBody.length} characters")

		// Convert OkHttp response to NewPipe response format
		return Response(
			okResponse.code,                    // HTTP status code
			okResponse.message,                 // HTTP status message
			okResponse.headers.toMultimap(),    // Response headers as multimap
			responseBody,                       // Response body content
			okResponse.request.url.toString()   // Final URL (after redirects)
		)
	}
}