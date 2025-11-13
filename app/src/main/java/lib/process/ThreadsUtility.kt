package lib.process

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.CoroutineContext

/**
 * # ThreadsUtility - Advanced Coroutine Management Library
 *
 * A comprehensive utility object that simplifies coroutine operations and threading management
 * in Android applications. It provides lifecycle-aware coroutine scopes, automatic error handling,
 * timeout management, and seamless thread switching between background and main threads.
 *
 * ## Key Features:
 * - 🎯 Lifecycle-aware coroutine management
 * - ⏱️ Configurable timeout for background operations
 * - 🛡️ Built-in error handling and logging
 * - 🔄 Seamless background-to-UI thread switching
 * - 🚀 SupervisorJob pattern for independent coroutine failure
 * - 📱 Main thread safety for UI updates
 * - 📊 Flow utilities for reactive programming
 * - 🔁 Retry mechanisms for unreliable operations
 * - ⚡ Efficient debouncing and throttling
 *
 * @see CoroutineScope
 * @see LifecycleOwner
 * @see Dispatchers
 */
object ThreadsUtility : CoroutineScope {

	/**
	 * Logger instance for tracking coroutine execution, errors, and debugging information.
	 * Uses a custom LogHelperUtils for consistent application logging.
	 */
	private val logger = LogHelperUtils.from(javaClass)

	/**
	 * Default timeout duration for background operations (5 seconds).
	 * Prevents long-running tasks from blocking indefinitely.
	 */
	private const val JOB_TIMEOUT = (10 * 1000 * 60L)

	/**
	 * Default retry count for operations that may fail temporarily.
	 */
	private const val DEFAULT_RETRY_COUNT = 3

	/**
	 * Default delay between retries in milliseconds.
	 */
	private const val DEFAULT_RETRY_DELAY = 1000L

	/**
	 * Root SupervisorJob that allows child coroutines to fail independently without affecting siblings.
	 * This enables structured concurrency while maintaining fault isolation.
	 */
	private val job = SupervisorJob()

	/**
	 * The coroutine context that combines:
	 * - Dispatchers.Default: For CPU-intensive operations
	 * - SupervisorJob: For independent coroutine failure handling
	 * - CoroutineExceptionHandler: Global error handling for uncaught exceptions
	 */
	override val coroutineContext: CoroutineContext
		get() = Dispatchers.Default + job + CoroutineExceptionHandler { _, throwable ->
			logger.d("Coroutine error: ${throwable.message}")
		}

	/**
	 * Executes a suspending block of code in the IO dispatcher with configurable timeout
	 * and comprehensive error handling. Ideal for network calls, database operations,
	 * file I/O, and other background tasks.
	 *
	 * ### Usage Example:
	 * ```kotlin
	 * ThreadsUtility.executeInBackground(
	 *     timeOutInMilli = 10000L, // 10 seconds timeout
	 *     codeBlock = {
	 *         // Perform background work
	 *         val data = apiService.fetchData()
	 *         database.save(data)
	 *     },
	 *     errorHandler = { error ->
	 *         // Handle specific errors
	 *         when (error) {
	 *             is TimeoutCancellationException -> showTimeoutError()
	 *             is NetworkException -> showNetworkError()
	 *             else -> showGenericError()
	 *         }
	 *     }
	 * )
	 * ```
	 *
	 * @param timeOutInMilli Maximum execution time in milliseconds (default: 5000ms)
	 * @param codeBlock The suspending lambda containing the background work to execute
	 * @param errorHandler Optional callback for custom error handling and recovery logic
	 * @return Job instance that can be used for cancellation or state monitoring
	 *
	 * @throws TimeoutCancellationException if the operation exceeds the specified timeout
	 * @see withTimeout
	 * @see Dispatchers.IO
	 */
	fun executeInBackground(
		timeOutInMilli: Long = JOB_TIMEOUT,
		codeBlock: suspend () -> Unit,
		errorHandler: ((Throwable) -> Unit)? = null,
	): Job = launch(Dispatchers.IO) {
		try {
			withTimeout(timeOutInMilli) {
				codeBlock()
			}
		} catch (error: Exception) {
			logger.e("Error in executing code in background thread:", error)
			errorHandler?.invoke(error)
		}
	}

	/**
	 * Executes a suspending block with automatic retry logic for transient failures.
	 * Useful for network operations that might fail due to temporary conditions.
	 *
	 * ### Usage Example:
	 * ```kotlin
	 * ThreadsUtility.executeWithRetry(
	 *     retryCount = 3,
	 *     retryDelay = 2000L,
	 *     codeBlock = { apiService.fetchData() },
	 *     shouldRetry = { error -> error is SocketTimeoutException }
	 * ) { result ->
	 *     // Handle successful result after retries
	 *     updateUI(result)
	 * }
	 * ```
	 *
	 * @param T The type of result returned by the code block
	 * @param retryCount Maximum number of retry attempts (default: 3)
	 * @param retryDelay Delay between retries in milliseconds (default: 1000ms)
	 * @param shouldRetry Predicate to determine if an error should trigger a retry
	 * @param codeBlock The suspending lambda to execute with retry logic
	 * @param onSuccess Callback invoked with the successful result
	 * @param onFinalError Callback invoked when all retries are exhausted
	 * @return Job instance representing the retry operation
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
				onSuccess(result)
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

		onFinalError(lastError ?: Exception("Unknown error after $retryCount retries"))
	}

	/**
	 * Suspends the current coroutine and switches to the Main (UI) thread to execute
	 * the provided code block. Essential for updating UI components safely.
	 *
	 * ### Usage Example:
	 * ```kotlin
	 * // In a suspending function or coroutine
	 * ThreadsUtility.executeOnMain {
	 *     // Safe UI updates
	 *     textView.text = "Updated from main thread"
	 *     progressBar.visibility = View.GONE
	 * }
	 * ```
	 *
	 * @param codeBlock The suspending lambda to execute on the main thread
	 *
	 * @see withContext
	 * @see Dispatchers.Main
	 */
	suspend fun executeOnMain(codeBlock: suspend () -> Unit) {
		withContext(Dispatchers.Main) {
			codeBlock()
		}
	}

	/**
	 * Suspends the current coroutine and switches to the Default dispatcher for
	 * CPU-intensive operations that shouldn't block the main thread.
	 *
	 * ### Usage Example:
	 * ```kotlin
	 * suspend fun processImage(bitmap: Bitmap): Bitmap =
	 *     ThreadsUtility.executeOnDefault {
	 *         // CPU-intensive image processing
	 *         applyFilters(bitmap)
	 *         compressImage(bitmap)
	 *         bitmap
	 *     }
	 * ```
	 *
	 * @param T The type of result returned by the code block
	 * @param codeBlock The suspending lambda to execute on default dispatcher
	 * @return The result of the code block execution
	 */
	suspend fun <T> executeOnDefault(codeBlock: suspend () -> T): T {
		return withContext(Dispatchers.Default) {
			codeBlock()
		}
	}

	/**
	 * Performs a background operation and automatically switches to the main thread
	 * to process the result. This pattern is ideal for the common "load data in background,
	 * update UI with result" workflow.
	 *
	 * ### Usage Example:
	 * ```kotlin
	 * ThreadsUtility.executeAsync(
	 *     backgroundTask = {
	 *         // This runs on background thread
	 *         repository.loadUserData(userId)
	 *     },
	 *     uiTask = { userData ->
	 *         // This automatically runs on main thread
	 *         bindUserDataToViews(userData)
	 *     }
	 * )
	 * ```
	 *
	 * @param T The type of data returned by the background task and consumed by the UI task
	 * @param backgroundTask Suspending lambda that performs background work and returns result
	 * @param uiTask Suspending lambda that receives the background result and updates UI
	 * @return Job instance for managing the async operation lifecycle
	 *
	 * @see withContext
	 */
	fun <T> executeAsync(
		backgroundTask: suspend () -> T,
		uiTask: suspend (T) -> Unit
	): Job = launch {
		try {
			val result = withContext(Dispatchers.IO) {
				backgroundTask()
			}
			executeOnMain {
				uiTask(result)
			}
		} catch (error: Exception) {
			logger.e("Error in executing code in async background thread:", error)
		}
	}

	/**
	 * Performs a background operation with progress updates sent to the UI thread.
	 * Ideal for long-running operations like file downloads or complex computations.
	 *
	 * ### Usage Example:
	 * ```kotlin
	 * ThreadsUtility.executeAsyncWithProgress(
	 *     backgroundTask = { progressCallback ->
	 *         var progress = 0
	 *         while (progress < 100) {
	 *             // Do work...
	 *             progress += 10
	 *             progressCallback(progress)
	 *         }
	 *         "Task completed"
	 *     },
	 *     onProgress = { progress ->
	 *         // Update progress bar on main thread
	 *         progressBar.progress = progress
	 *     },
	 *     onResult = { result ->
	 *         // Handle final result
	 *         showMessage(result)
	 *     }
	 * )
	 * ```
	 *
	 * @param T The type of final result
	 * @param backgroundTask Suspending lambda that accepts progress callback and returns result
	 * @param onProgress Callback invoked on main thread with progress updates
	 * @param onResult Callback invoked on main thread with final result
	 * @return Job instance for managing the operation
	 */
	fun <T> executeAsyncWithProgress(
		backgroundTask: suspend ((Int) -> Unit) -> T,
		onProgress: (Int) -> Unit,
		onResult: (T) -> Unit
	): Job = launch {
		try {
			val result = withContext(Dispatchers.IO) {
				backgroundTask { progress ->
					// Send progress updates to main thread
					launch(Dispatchers.Main) {
						onProgress(progress)
					}
				}
			}
			executeOnMain {
				onResult(result)
			}
		} catch (error: Exception) {
			logger.e("Error in async operation with progress:", error)
		}
	}

	/**
	 * Creates a Flow with built-in error handling and automatic dispatching to background thread.
	 * Wraps the flow creation in a safe context with comprehensive error handling.
	 *
	 * ### Usage Example:
	 * ```kotlin
	 * fun observeUserData(userId: String): Flow<User> =
	 *     ThreadsUtility.createSafeFlow {
	 *         userDao.observeUserById(userId)
	 *     }
	 * ```
	 *
	 * @param T The type of data emitted by the flow
	 * @param block The lambda that creates the original flow
	 * @return A new Flow with error handling and proper threading
	 */
	fun <T> createSafeFlow(block: () -> Flow<T>): Flow<T> {
		return flow {
			block().collect { value ->
				emit(value)
			}
		}.flowOn(Dispatchers.IO).catch { error ->
			logger.e("Error in flow:", error)
		}
	}

	/**
	 * Collects a Flow in a lifecycle-aware manner, automatically cancelling when the
	 * lifecycle owner is destroyed.
	 *
	 * ### Usage Example:
	 * ```kotlin
	 * class MyActivity : AppCompatActivity() {
	 *     override fun onCreate(savedInstanceState: Bundle?) {
	 *         super.onCreate(savedInstanceState)
	 *
	 *         ThreadsUtility.collectFlowOnLifecycle(
	 *             lifecycleOwner = this,
	 *             flow = repository.userFlow,
	 *             action = { user ->
	 *                 // Safely update UI with flow emissions
	 *                 updateUserInfo(user)
	 *             }
	 *         )
	 *     }
	 * }
	 * ```
	 *
	 * @param T The type of data emitted by the flow
	 * @param lifecycleOwner The lifecycle owner that controls the collection scope
	 * @param flow The flow to collect
	 * @param action The action to perform on each emission
	 * @return Job representing the flow collection
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
	 * Executes a function after a specified delay, cancelling previous executions
	 * if they haven't started yet. Useful for search inputs or button clicks.
	 *
	 * ### Usage Example:
	 * ```kotlin
	 * class SearchFragment : Fragment() {
	 *     private val debouncedSearch = ThreadsUtility.debounced<String>(300L) { query ->
	 *         performSearch(query)
	 *     }
	 *
	 *     fun onQueryChanged(query: String) {
	 *         debouncedSearch(query)
	 *     }
	 * }
	 * ```
	 *
	 * @param T The type of input parameter
	 * @param delayMs The debounce delay in milliseconds
	 * @param block The function to execute after debouncing
	 * @return A debounced function that can be called multiple times
	 */
	fun <T> debounced(delayMs: Long, block: (T) -> Unit): (T) -> Unit {
		var job: Job? = null

		return { input: T ->
			job?.cancel()
			job = launch {
				delay(delayMs)
				block(input)
			}
		}
	}

	/**
	 * Cancels all child coroutines of the SupervisorJob while keeping the parent job active.
	 * Useful for cleanup when the component is being destroyed or when you need to stop
	 * all ongoing operations.
	 *
	 * ### Usage Example:
	 * ```kotlin
	 * override fun onDestroy() {
	 *     super.onDestroy()
	 *     ThreadsUtility.cancelAll() // Clean up all coroutines
	 * }
	 * ```
	 *
	 * @see cancelChildren
	 * @see SupervisorJob
	 */
	fun cancelAll() = job.cancelChildren()

	/**
	 * Provides a lifecycle-aware coroutine scope that automatically cancels when the
	 * associated LifecycleOwner (Activity/Fragment) is destroyed. Prevents memory leaks
	 * and ensures coroutines don't outlive their UI components.
	 *
	 * ### Usage Example:
	 * ```kotlin
	 * class MainActivity : AppCompatActivity() {
	 *     override fun onCreate(savedInstanceState: Bundle?) {
	 *         super.onCreate(savedInstanceState)
	 *
	 *         // Launch coroutine that auto-cancels when activity is destroyed
	 *         ThreadsUtility.lifecycleScope(this).launch {
	 *             val data = loadDataSafely()
	 *             updateUI(data)
	 *         }
	 *     }
	 * }
	 * ```
	 *
	 * @param lifecycleOwner The Activity or Fragment whose lifecycle controls the scope
	 * @return Lifecycle-aware CoroutineScope that cancels with the lifecycle
	 *
	 * @see LifecycleOwner
	 * @see LifecycleOwner.lifecycleScope
	 */
	fun lifecycleScope(lifecycleOwner: LifecycleOwner) = lifecycleOwner.lifecycleScope
}

/**
 * Extension function that launches a coroutine in the IO dispatcher within any CoroutineScope.
 * Provides a clean, readable way to start IO-bound operations.
 *
 * ### Usage Example:
 * ```kotlin
 * class UserRepository {
 *     fun saveUser(user: User) = viewModelScope.executeInIO {
 *         // This runs on IO thread
 *         database.userDao().insert(user)
 *     }
 * }
 * ```
 *
 * @param block The suspending lambda to execute in IO dispatcher
 * @return Job instance representing the launched coroutine
 *
 * @see CoroutineScope
 * @see Dispatchers.IO
 */
fun CoroutineScope.executeInIO(block: suspend CoroutineScope.() -> Unit): Job {
	return launch(Dispatchers.IO) {
		block()
	}
}

/**
 * Suspends the current coroutine and switches to the Main dispatcher to execute
 * the provided block, then returns the result. Useful for UI updates that require
 * returning values.
 *
 * ### Usage Example:
 * ```kotlin
 * suspend fun updateAndGetText(): String = withMainContext {
 *     // Safe UI operations that return a value
 *     textView.text = "Updated"
 *     "Operation completed"
 * }
 * ```
 *
 * @param T The type of result returned by the block
 * @param block The suspending lambda to execute on main thread
 * @return The result of the block execution
 *
 * @see withContext
 * @see Dispatchers.Main
 */
suspend fun <T> withMainContext(block: suspend CoroutineScope.() -> T): T {
	return withContext(Dispatchers.Main) {
		block()
	}
}

/**
 * Suspends the current coroutine and switches to the IO dispatcher to execute
 * the provided block, then returns the result. Useful for IO operations that require
 * returning values.
 *
 * ### Usage Example:
 * ```kotlin
 * suspend fun loadUserData(userId: String): User = withIOContext {
 *     // Perform IO operations
 *     database.userDao().getUserById(userId)
 * }
 * ```
 *
 * @param T The type of result returned by the block
 * @param block The suspending lambda to execute on IO thread
 * @return The result of the block execution
 *
 * @see withContext
 * @see Dispatchers.IO
 */
suspend fun <T> withIOContext(block: suspend CoroutineScope.() -> T): T {
	return withContext(Dispatchers.IO) {
		block()
	}
}