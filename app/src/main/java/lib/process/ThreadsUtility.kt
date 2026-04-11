package lib.process

import androidx.lifecycle.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import lib.process.ThreadsUtility.executeWithRetry
import kotlin.coroutines.*

/**
 * A centralized coroutine utility providing high-performance threading solutions for the
 * application. This object acts as a global [CoroutineScope] optimized for mobile
 * environments, specifically targeting high-refresh-rate UI and heavy I/O operations.
 *
 * It provides specialized wrappers for:
 * * **Main-Safety**: Effortless transitions between background work and UI updates.
 * * **Performance**: Heavy use of inline and crossinline to minimize object
 * allocations and flatten the call stack.
 * * **Resilience**: Integrated [SupervisorJob] and retry logic to handle flaky
 * operations without crashing the parent scope.
 * * **Lifecycle Integration**: Safe [Flow] collection and scope bridging for
 * Activity and Fragment components.
 */
object ThreadsUtility : CoroutineScope {

	/** * Utility for logging coroutine events and errors, tagged with the class name for
	 * easier debugging in Logcat.
	 */
	val logger = LogHelperUtils.from(javaClass)

	/** * The default timeout duration (30 seconds) applied to background tasks to prevent
	 * infinite execution and resource leaks.
	 */
	const val JOB_TIMEOUT = (30 * 1000L)

	/** * The default number of additional attempts made by [executeWithRetry] before
	 * returning a final error to the caller.
	 */
	const val DEFAULT_RETRY_COUNT = 3

	/** * The default wait time (1 second) between consecutive retry attempts to allow
	 * external systems (like APIs or DBs) to recover.
	 */
	const val DEFAULT_RETRY_DELAY = 1000L

	/**
	 * The underlying [SupervisorJob] for this scope. Using a supervisor ensures that
	 * a failure in one child coroutine does not trigger the cancellation of its
	 * siblings or the entire utility scope.
	 */
	private val job by lazy { SupervisorJob() }

	/**
	 * The execution context for this utility scope.
	 * * Combines [Dispatchers.Default] for background-safe processing with a [SupervisorJob]
	 * to ensure sibling failures don't cancel the entire scope. Includes a centralized
	 * [CoroutineExceptionHandler] for logging uncaught exceptions.
	 */
	override val coroutineContext: CoroutineContext
		get() = Dispatchers.Default + job + CoroutineExceptionHandler { _, throwable ->
			logger.d("Coroutine error: ${throwable.message}")
		}

	/**
	 * Executes a fire-and-forget task on the [Dispatchers.IO] thread with a mandatory
	 * timeout. This is ideal for quick background operations like logging or cache
	 * clearing that shouldn't hang indefinitely.
	 *
	 * @param timeOutInMilli The maximum duration allowed for execution before
	 * automatic cancellation.
	 * @param codeBlock      The asynchronous logic to execute in the background.
	 * @param errorHandler   Optional callback invoked if the task fails or times out.
	 * @return The [Job] instance representing the background task.
	 */
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

	/**
	 * Attempts to execute a task multiple times before failing. This is best for
	 * network-dependent operations or flaky I/O tasks that might succeed after
	 * a short delay.
	 *
	 * @param retryCount   Number of additional attempts to make after the initial failure.
	 * @param retryDelay   Time in milliseconds to wait between consecutive attempts.
	 * @param shouldRetry  Predicate to determine if a specific error justifies a
	 * retry attempt.
	 * @param codeBlock    The task to be executed; expected to return a result of type [T].
	 * @param onSuccess    Callback invoked on the Main thread upon a successful execution.
	 * @param onFinalError Callback invoked on the Main thread if all retry attempts fail.
	 * @return The [Job] instance representing the retry lifecycle.
	 */
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

	/**
	 * Switches execution to the Main thread for UI-related tasks. Inlined to prevent
	 * function object allocation and flatten the call stack.
	 *
	 * @param codeBlock The UI-specific logic to execute on the [Dispatchers.Main] thread.
	 */
	suspend inline fun executeOnMain(crossinline codeBlock: suspend () -> Unit) {
		withContext(Dispatchers.Main) {
			codeBlock()
		}
	}

	/**
	 * Switches execution to the Default dispatcher for CPU-intensive tasks like search
	 * ranking or data transformation. Inlined for maximum performance.
	 *
	 * @param T         The return type of the operation.
	 * @param codeBlock CPU-heavy logic to execute on the [Dispatchers.Default] thread.
	 * @return The result produced by the [codeBlock].
	 */
	suspend inline fun <T> executeOnDefault(crossinline codeBlock: suspend () -> T): T {
		return withContext(Dispatchers.Default) {
			codeBlock()
		}
	}

	/**
	 * Performs a background task and delivers the result to a UI task.
	 *
	 * Launches a coroutine that executes [backgroundTask] on the IO dispatcher and
	 * then invokes [uiTask] with the result. Since the parent scope defaults to
	 * the Main thread, the completion naturally resumes on Main.
	 *
	 * @param backgroundTask The IO-bound logic to execute (e.g., database fetch).
	 * @param uiTask         The callback to handle the result on the Main thread.
	 * @return The [Job] instance representing the asynchronous operation.
	 */
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

	/**
	 * Executes a background task with a progress reporter. This is ideal for long-running
	 * operations like file downloads or database migrations where the UI needs real-time
	 * feedback.
	 * * The [backgroundTask] is offloaded to [Dispatchers.IO]. Progress updates sent via
	 * the provided lambda are automatically jumped back to [Dispatchers.Main] to ensure
	 * thread-safe UI updates. The final [onResult] is delivered on the Main thread after
	 * the background work completes.
	 *
	 * @param backgroundTask Logic to run on IO thread; provides a `(Int) -> Unit` for
	 *                       reporting progress.
	 * @param onProgress Callback invoked on the Main thread whenever progress is reported.
	 * @param onResult Callback invoked on the Main thread with the final result of the task.
	 */
	fun <T> executeAsyncWithProgress(
		backgroundTask: suspend ((Int) -> Unit) -> T,
		onProgress: (Int) -> Unit,
		onResult: (T) -> Unit
	): Job = launch {
		try {
			val result = withContext(Dispatchers.IO) {
				backgroundTask { progress ->
					// Immediate jump to Main to prevent "CalledFromWrongThreadException" on UI elements
					launch(Dispatchers.Main) { onProgress(progress) }
				}
			}
			onResult(result)
		} catch (error: Exception) {
			logger.e("Error in async operation with progress:", error)
		}
	}

	/**
	 * Creates and configures a [Flow] for safe execution and centralized error handling.
	 * * This ensures that the upstream flow collection (the actual data production) is performed on
	 * [Dispatchers.IO], keeping the Main thread free for UI rendering. It also attaches a [catch]
	 * block to log any exceptions in the stream before they can crash the collector.
	 *
	 * @param block A lambda that builds and returns the raw [Flow].
	 * @return A [Flow] instance configured with the IO dispatcher and error logging.
	 */
	fun <T> createSafeFlow(block: () -> Flow<T>): Flow<T> {
		return block()
			.flowOn(Dispatchers.IO)
			.catch { error ->
				logger.e("Error in flow:", error)
			}
	}

	/**
	 * Collects a [Flow] within the context of a [LifecycleOwner]'s scope.
	 *
	 * This utility automates the subscription to reactive streams (such as database
	 * updates or download progress) and ensures they are strictly bound to the
	 * UI lifecycle. When the [lifecycleOwner] (Activity or Fragment) is destroyed,
	 * the collection is automatically canceled, preventing memory leaks and
	 * "Illegal State" exceptions.
	 *
	 * @param T The type of data emitted by the flow.
	 * @param lifecycleOwner The UI component responsible for the lifecycle.
	 * @param flow The reactive stream to be collected.
	 * @param action The callback to execute on the Main thread for each emitted value.
	 * @return The [Job] representing the collection task, allowing for manual cancellation
	 *          if needed.
	 */
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

	/**
	 * Creates a debounced version of the provided [block] that delays execution
	 * until [delayMs] has elapsed since the last time it was invoked.
	 *
	 * This is essential for high-frequency UI events, such as search queries in
	 * the URL bar. It prevents "command flooding" by cancelling any previous
	 * pending execution if a new input arrives before the delay expires.
	 *
	 * Note: The returned lambda launches its coroutine on [Dispatchers.Main] to
	 * ensure thread safety when managing the internal [Job] state.
	 *
	 * @param T The type of the input parameter for the block.
	 * @param delayMs The time to wait (in milliseconds) after the last invocation before
	 *                executing.
	 * @param block The action to be executed once the debounce timer expires.
	 * @return A lambda that handles the debouncing logic for the given input.
	 */
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

	/**
	 * Cancels all active coroutines currently managed by this [ThreadsUtility] scope.
	 *
	 * This uses [Job.cancelChildren] on the underlying [SupervisorJob], meaning the
	 * parent scope itself remains active and can launch new coroutines in the future,
	 * but all currently running background tasks are stopped immediately.
	 * * Use this during app-wide resets or when a major browser engine transition occurs.
	 */
	fun cancelAll() = job.cancelChildren()

	/**
	 * Provides a convenient bridge to access the [LifecycleCoroutineScope] of a
	 * [LifecycleOwner] (typically an Activity or Fragment).
	 *
	 * This ensures that coroutines launched via this scope are automatically canceled
	 * when the UI component is destroyed, preventing memory leaks and illegal
	 * state exceptions when trying to update UI that is no longer visible.
	 *
	 * @param lifecycleOwner The UI component (Activity/Fragment) that owns the lifecycle.
	 * @return The [CoroutineScope] tied to the provided lifecycle.
	 */
	fun lifecycleScope(lifecycleOwner: LifecycleOwner)
		: LifecycleCoroutineScope = lifecycleOwner.lifecycleScope
}

/**
 * Switches the coroutine context to [Dispatchers.Main] to execute the provided [block].
 *
 * This is an optimized inline extension that flattens the call stack and eliminates
 * the allocation of an anonymous function object for the [block]. It should be used
 * when UI updates or interactions with Main-thread-only components are required
 * from within a background coroutine.
 *
 * @param T The return type of the code block.
 * @param block The suspendable code to be executed on the Main thread.
 * @return The result produced by the [block].
 */
suspend inline fun <T> withMainContext(
	crossinline block: suspend CoroutineScope.() -> T
): T {
	return withContext(Dispatchers.Main) {
		block()
	}
}

/**
 * Switches the coroutine context to [Dispatchers.IO] to execute the provided [block].
 *
 * Designed for high-performance data operations, this inline function avoids
 * redundant object allocations, reducing Garbage Collection pressure during
 * frequent database or file I/O tasks. Because it is inlined, nested calls to
 * [withIOContext] are flattened by the compiler, minimizing the overhead of
 * context-switching checks.
 *
 * @param T The return type of the code block.
 * @param block The suspendable code to be executed on the IO thread (e.g., ObjectBox queries).
 * @return The result produced by the [block].
 */
suspend inline fun <T> withIOContext(
	crossinline block: suspend CoroutineScope.() -> T
): T {
	return withContext(Dispatchers.IO) {
		block()
	}
}