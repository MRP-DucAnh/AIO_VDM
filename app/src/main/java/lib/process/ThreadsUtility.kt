package lib.process

import androidx.lifecycle.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.coroutines.*

object ThreadsUtility : CoroutineScope {

	val logger = LogHelperUtils.from(javaClass)

	const val JOB_TIMEOUT = (30 * 1000L)
	const val DEFAULT_RETRY_COUNT = 3
	const val DEFAULT_RETRY_DELAY = 1000L

	private val job by lazy { SupervisorJob() }

	override val coroutineContext: CoroutineContext
		get() = Dispatchers.Default + job + CoroutineExceptionHandler { _, throwable ->
			logger.d("Coroutine error: ${throwable.message}")
		}

	inline fun executeInBackground(
		timeOutInMilli: Long = JOB_TIMEOUT,
		crossinline codeBlock: suspend () -> Unit,
		noinline errorHandler: ((Throwable) -> Unit)? = null,
	): Job = launch(Dispatchers.IO) {
		try {
			withTimeout(timeOutInMilli) {
				codeBlock()
			}
		} catch (error: Exception) {
			logger.e("Error in background execution:", error)
			errorHandler?.invoke(error)
		}
	}

	fun <T> executeWithRetry(
		retryCount: Int = DEFAULT_RETRY_COUNT,
		retryDelay: Long = DEFAULT_RETRY_DELAY,
		shouldRetry: (Throwable) -> Boolean = { true },
		codeBlock: suspend () -> T,
		onSuccess: (T) -> Unit = {},
		onFinalError: (Throwable) -> Unit = {}
	): Job = launch {
		var currentRetry = 0
		var lastError: Throwable? = null

		while (currentRetry <= retryCount) {
			try {
				val result = codeBlock()
				withContext(Dispatchers.Main) { onSuccess(result) }
				return@launch
			} catch (error: Exception) {
				lastError = error
				logger.e("Attempt ${currentRetry + 1} failed:", error)

				if (currentRetry == retryCount || !shouldRetry(error)) {
					break
				}

				currentRetry++
				delay(retryDelay)
			}
		}

		lastError?.let { error ->
			withContext(Dispatchers.Main) {
				onFinalError(error)
			}
		} ?: withContext(Dispatchers.Main) {
			onFinalError(Exception("Unknown error after $retryCount retries"))
		}
	}

	suspend inline fun executeOnMain(crossinline codeBlock: suspend () -> Unit) {
		withContext(Dispatchers.Main) {
			codeBlock()
		}
	}

	suspend inline fun <T> executeOnDefault(crossinline codeBlock: suspend () -> T): T {
		return withContext(Dispatchers.Default) {
			codeBlock()
		}
	}

	fun <T> executeAsync(
		backgroundTask: suspend () -> T,
		uiTask: suspend (T) -> Unit
	): Job = launch {
		try {
			val result = withContext(Dispatchers.IO) { backgroundTask() }
			uiTask(result)
		} catch (error: Exception) {
			logger.e("Error in executing code in async background thread:", error)
		}
	}

	fun <T> executeAsyncWithProgress(
		backgroundTask: suspend ((Int) -> Unit) -> T,
		onProgress: (Int) -> Unit,
		onResult: (T) -> Unit
	): Job = launch {
		try {
			val result = withContext(Dispatchers.IO) {
				backgroundTask { progress ->
					launch(Dispatchers.Main) { onProgress(progress) }
				}
			}
			onResult(result)
		} catch (error: Exception) {
			logger.e("Error in async operation with progress:", error)
		}
	}

	fun <T> createSafeFlow(block: () -> Flow<T>): Flow<T> {
		return block()
			.flowOn(Dispatchers.IO)
			.catch { error ->
				logger.e("Error in flow:", error)
			}
	}

	fun <T> collectFlowOnLifecycle(
		lifecycleOwner: LifecycleOwner,
		flow: Flow<T>,
		action: (T) -> Unit
	): Job {
		return lifecycleOwner.lifecycleScope.launch {
			flow.collect { value ->
				action(value)
			}
		}
	}

	fun <T> debounced(delayMs: Long, block: (T) -> Unit): (T) -> Unit {
		var job: Job? = null

		return { input: T ->
			job?.cancel()
			job = launch(Dispatchers.Main) {
				delay(delayMs)
				block(input)
			}
		}
	}

	fun cancelAll() = job.cancelChildren()

	fun lifecycleScope(lifecycleOwner: LifecycleOwner)
		: LifecycleCoroutineScope = lifecycleOwner.lifecycleScope
}

suspend inline fun <T> withMainContext(
	crossinline block: suspend CoroutineScope.() -> T
): T {
	return withContext(Dispatchers.Main) {
		block()
	}
}

suspend inline fun <T> withIOContext(
	crossinline block: suspend CoroutineScope.() -> T
): T {
	return withContext(Dispatchers.IO) {
		block()
	}
}