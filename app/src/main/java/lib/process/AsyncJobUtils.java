package lib.process;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import java.lang.ref.WeakReference;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.FutureTask;

/**
 * A utility class for managing asynchronous background tasks with automatic result delivery
 * and progress reporting to the UI thread. This class provides a robust framework for
 * executing long-running operations without blocking the main thread, while safely
 * delivering results and progress updates back to the UI thread.
 *
 * <p><b>Key Features:</b></p>
 * <ul>
 *   <li><b>Type Safety:</b> Generic parameter TaskResult ensures compile-time type safety
 *       between background task, result handling, and progress reporting</li>
 *   <li><b>Memory Leak Prevention:</b> Uses WeakReferences for all task references to
 *       prevent leaks when Activities or Fragments are destroyed</li>
 *   <li><b>Flexible Threading:</b> Supports both custom ExecutorService and direct thread
 *       execution</li>
 *   <li><b>Progress Reporting:</b> Built-in progress callback mechanism for real-time
 *       UI updates during long operations</li>
 *   <li><b>Cancellation Support:</b> Ability to cancel running tasks with thread interruption</li>
 *   <li><b>Thread-Safe Result Delivery:</b> Automatic result delivery on the UI thread
 *       via main looper handler</li>
 * </ul>
 *
 * <p><b>Typical Usage Example:</b></p>
 * <pre>
 * // Create an instance for downloading a file
 * AsyncJobUtils&lt;String&gt; downloadTask = new AsyncJobUtils&lt;&gt;();
 *
 * // Configure the task components
 * downloadTask.setBackgroundTask((callback) -> {
 *     callback.onProgress(0);
 *     String result = downloadLargeFile();
 *     callback.onProgress(100);
 *     return result;
 * });
 *
 * downloadTask.setResultTask((result) -> {
 *     updateUIWithDownloadedFile(result);
 *     showCompletionToast();
 * });
 *
 * downloadTask.setProgressUpdateTask((progress) -> {
 *     progressBar.setProgress(progress);
 *     statusText.setText("Downloading: " + progress + "%");
 * });
 *
 * // Start the asynchronous operation
 * downloadTask.start();
 *
 * // Cancel if needed (e.g., user navigates away)
 * // downloadTask.cancel();
 * </pre>
 *
 * <p><b>Threading Model:</b></p>
 * <ul>
 *   <li>Background tasks run on a separate thread (either custom ExecutorService or new Thread)</li>
 *   <li>Progress updates are automatically posted to the UI thread via Handler</li>
 *   <li>Results are delivered on the UI thread for safe UI manipulation</li>
 *   <li>All UI-related operations are guaranteed to execute on the main thread</li>
 * </ul>
 *
 * <p><b>Memory Management:</b></p>
 * This class uses WeakReferences for all task callbacks (BackgroundTask, ResultTask,
 * ProgressUpdateTask). This prevents memory leaks when the owning Activity or Fragment
 * is destroyed while tasks are still pending. However, it also means that if the
 * parent component is garbage collected before task completion, the result will be
 * silently discarded.
 *
 * <p><b>Lifecycle Considerations:</b></p>
 * For proper lifecycle integration, consider:
 * <ul>
 *   <li>Cancelling tasks in onDestroy()/onDestroyView() to prevent unnecessary work</li>
 *   <li>Using ViewModel to retain AsyncJobUtils instances across configuration changes</li>
 *   <li>Checking if the activity is finishing before updating UI in result tasks</li>
 * </ul>
 *
 * <p><b>Cancellation Behavior:</b></p>
 * Cancellation is cooperative. The background task should periodically check
 * Thread.currentThread().isInterrupted() and terminate gracefully. Simply calling
 * cancel() does not guarantee immediate stoppage if the task is not interrupt-aware.
 *
 * @param <TaskResult> The type of result produced by the background task and consumed
 *                     by the result task. This can be any object type, including
 *                     custom classes, primitives (via wrapper classes), or Void for
 *                     operations that don't produce a result
 * @see BackgroundTask
 * @see ResultTask
 * @see ProgressUpdateTask
 * @see ProgressCallback
 * @see #start()
 * @see #cancel()
 */
public final class AsyncJobUtils<TaskResult> {

	/**
	 * Logger instance for this class, used for debugging, error tracking,
	 * and performance monitoring. The logger is automatically configured
	 * with the class name for easy log filtering and identification.
	 */
	private final LogHelperUtils logger = LogHelperUtils.from(getClass());

	/**
	 * Main thread handler that posts tasks to the UI thread's message queue.
	 * This handler is bound to the main looper and ensures that UI updates
	 * and result deliveries are executed on the correct thread.
	 *
	 * <p>All UI-related operations must be posted through this handler to
	 * avoid threading violations and potential crashes.</p>
	 */
	private static final Handler UI_HANDLER = new Handler(Looper.getMainLooper());

	/**
	 * WeakReference to the background task that performs the asynchronous work.
	 * Using a WeakReference prevents memory leaks when the parent component
	 * (e.g., Activity, Fragment) is destroyed while the background task is
	 * still pending or executing.
	 *
	 * <p>The background task is responsible for:
	 * <ul>
	 *   <li>Performing long-running operations (network, database, computation)</li>
	 *   <li>Reporting progress updates via ProgressCallback</li>
	 *   <li>Returning a result of type TaskResult</li>
	 * </ul>
	 * </p>
	 *
	 * <p><b>Note:</b> This reference may be null if the task was never set
	 * or has been garbage collected.</p>
	 */
	private WeakReference<BackgroundTask<TaskResult>> backgroundTaskRef;

	/**
	 * WeakReference to the result task that processes the background task's output.
	 * Using a WeakReference prevents memory leaks when the parent component is
	 * destroyed before the background task completes.
	 *
	 * <p>The result task is executed on the UI thread and typically:
	 * <ul>
	 *   <li>Updates UI components with the computed result</li>
	 *   <li>Caches the result for future use</li>
	 *   <li>Triggers follow-up operations or navigation</li>
	 *   <li>Displays dialogs or toast messages</li>
	 * </ul>
	 * </p>
	 *
	 * <p><b>Note:</b> If this reference is null when the background task completes,
	 * the result will be silently discarded.</p>
	 */
	private WeakReference<ResultTask<TaskResult>> resultTaskRef;

	/**
	 * WeakReference to the progress update task that handles intermediate progress.
	 * Using a WeakReference prevents memory leaks when the parent component is
	 * destroyed while progress updates are still being reported.
	 *
	 * <p>The progress update task runs on the UI thread and is called multiple
	 * times during background task execution. Typical uses include:
	 * <ul>
	 *   <li>Updating progress bars or loading indicators</li>
	 *   <li>Displaying status messages (e.g., "Loading... 50%")</li>
	 *   <li>Showing or hiding loading overlays</li>
	 *   <li>Enabling/disabling UI elements during long operations</li>
	 * </ul>
	 * </p>
	 *
	 * <p><b>Note:</b> If this reference is null, progress updates are ignored
	 * but the background task continues execution normally.</p>
	 */
	private WeakReference<ProgressUpdateTask> progressUpdateTaskRef;

	/**
	 * ExecutorService for managing background thread pools.
	 * When provided, this executor is used instead of creating a new thread
	 * for each background task. This allows for:
	 *
	 * <ul>
	 *   <li>Thread reuse across multiple async jobs</li>
	 *   <li>Limiting the maximum number of concurrent tasks</li>
	 *   <li>Fine-grained control over thread priorities</li>
	 *   <li>Graceful shutdown of background threads</li>
	 * </ul>
	 *
	 * <p>If this is null, a new Thread is created for each background task.</p>
	 *
	 * <p><b>Important:</b> The caller is responsible for shutting down the
	 * executor service when it's no longer needed to prevent thread leaks.</p>
	 */
	private ExecutorService executorService;

	/**
	 * Reference to the background thread when using direct thread execution.
	 * This is used only when executorService is null and a new thread is
	 * created for the background task.
	 *
	 * <p>This reference is stored to support cancellation via Thread.interrupt()
	 * and to track the execution state. It will be null when:
	 * <ul>
	 *   <li>No background task has been started</li>
	 *   <li>An ExecutorService is being used instead</li>
	 *   <li>The task has completed or been canceled</li>
	 * </ul>
	 * </p>
	 *
	 * @see #cancel()
	 * @see Thread#interrupt()
	 */
	private Thread backgroundThread;

	/**
	 * FutureTask reference when using ExecutorService execution.
	 * This is used to manage the background task when submitted to an
	 * ExecutorService, providing capabilities such as:
	 *
	 * <ul>
	 *   <li>Cancelling the task with interruption</li>
	 *   <li>Checking if the task is complete</li>
	 *   <li>Waiting for task completion (blocking)</li>
	 *   <li>Retrieving the result (though result is handled via callback)</li>
	 * </ul>
	 *
	 * <p>This reference will be null when:
	 * <ul>
	 *   <li>No ExecutorService is configured</li>
	 *   <li>The task hasn't been started yet</li>
	 *   <li>A direct thread is being used instead</li>
	 * </ul>
	 * </p>
	 *
	 * @see FutureTask
	 * @see ExecutorService
	 * @see #cancel()
	 */
	private FutureTask<?> backgroundFutureTask;

	/**
	 * The result produced by the background task.
	 * This field stores the output from BackgroundTask.runInBackground()
	 * after it completes successfully.
	 *
	 * <p>The result is temporarily stored here until it can be delivered to
	 * the ResultTask on the UI thread. It is accessed from both the background
	 * thread (when the task completes) and the UI thread (when delivering the result).</p>
	 *
	 * <p><b>Thread Safety:</b> Access to this field is thread-safe because:
	 * <ul>
	 *   <li>The background task sets it only once upon completion</li>
	 *   <li>The UI thread reads it after the background task has finished</li>
	 *   <li>There is a happens-before relationship due to the synchronization
	 *       provided by the handler posting mechanism</li>
	 * </ul>
	 * </p>
	 *
	 * <p>This field may be null if:
	 * <ul>
	 *   <li>The background task hasn't completed yet</li>
	 *   <li>The background task returned null as its result</li>
	 *   <li>The background task threw an exception (depending on implementation)</li>
	 *   <li>The task was canceled before completion</li>
	 * </ul>
	 * </p>
	 */
	private TaskResult result;

	/**
	 * Executes a UI task on the main thread.
	 * This method posts the provided UI task to the main thread's message queue
	 * using a dedicated UI handler. The task will be executed asynchronously
	 * on the UI thread when the main thread becomes available.
	 *
	 * <p>This method is safe to call from any thread, including background threads.
	 * It does not block the calling thread and returns immediately.</p>
	 *
	 * <p><b>Important:</b> The UI task should be lightweight and avoid long-running
	 * operations that could block the UI thread and cause Application Not Responding
	 * (ANR) errors.</p>
	 *
	 * @param uiTask The UI task to execute on the main thread (must not be null)
	 * @throws NullPointerException if the provided uiTask is null
	 */
	public static void executeOnMainThread(final @NonNull UITask uiTask) {
		UI_HANDLER.post(uiTask::runOnUIThread);
	}

	/**
	 * Executes a background task on a new thread.
	 * This method creates and starts a new thread to run the provided background task.
	 * The thread is not pooled and will be garbage collected after the task completes.
	 *
	 * <p>This method is suitable for one-off background operations that don't require
	 * thread reuse or complex thread management. For multiple background tasks or
	 * tasks that need coordination, consider using the overloaded version with an
	 * ExecutorService.</p>
	 *
	 * <p><b>Note:</b> The calling thread does not wait for the background task to
	 * complete and continues execution immediately.</p>
	 *
	 * @param backgroundTask The background task to execute (must not be null)
	 * @throws NullPointerException if the provided backgroundTask is null
	 * @see #executeInBackground(BackgroundTaskNoResult, ExecutorService)
	 */
	public static void executeInBackground(
		final @NonNull BackgroundTaskNoResult backgroundTask
	) {
		new Thread(backgroundTask::runInBackground).start();
	}

	/**
	 * Executes a background task using a provided ExecutorService.
	 * This method submits the background task to the specified executor service,
	 * which manages thread pooling and execution. The task is wrapped in a FutureTask
	 * that allows for cancellation and status checking.
	 *
	 * <p>This method provides better control over thread management compared to
	 * creating a new thread directly. It's ideal for applications that perform
	 * multiple background operations and want to reuse threads or limit concurrency.</p>
	 *
	 * <p><b>Important:</b> The caller is responsible for managing the lifecycle of
	 * the ExecutorService, including proper shutdown when it's no longer needed.</p>
	 *
	 * @param backgroundTask The background task to execute (must not be null)
	 * @param executor       The executor service that will manage the task execution
	 *                       (must not be null)
	 * @return A FutureTask representing the pending background task, which can be
	 * used to check completion status, cancel the task, or wait for its
	 * completion
	 * @throws NullPointerException if either parameter is null
	 * @see #executeInBackground(BackgroundTaskNoResult)
	 * @see FutureTask
	 * @see ExecutorService
	 */
	@NonNull
	public static FutureTask<?> executeInBackground(
		final @NonNull BackgroundTaskNoResult backgroundTask,
		@NonNull ExecutorService executor) {
		FutureTask<?> task = new FutureTask<>(
			backgroundTask::runInBackground, null
		);
		executor.submit(task);
		return task;
	}

	/**
	 * Starts the asynchronous job execution.
	 * This method initiates the background task, manages progress reporting,
	 * and handles result delivery to the UI thread. The method is non-blocking
	 * and returns immediately while the background task executes asynchronously.
	 *
	 * <p>The execution flow is as follows:
	 * <ol>
	 *   <li>Retrieves the background task from its WeakReference</li>
	 *   <li>If the task exists, creates a Runnable that will execute the background work</li>
	 *   <li>Submits the task to either a custom ExecutorService or a new thread</li>
	 *   <li>Progress updates are automatically delivered to the UI thread via UI_HANDLER</li>
	 *   <li>When the background task completes, deliverResult() is called to process the result</li>
	 * </ol>
	 * </p>
	 *
	 * <p><b>Note:</b> This method does nothing if the background task has been
	 * garbage collected or was never set.</p>
	 *
	 * @see #cancel()
	 * @see #deliverResult()
	 */
	public void start() {
		final BackgroundTask<TaskResult> taskInstance =
			backgroundTaskRef != null ? backgroundTaskRef.get() : null;

		if (taskInstance != null) {
			Runnable task = () -> {
				ProgressUpdateTask progressTaskInstance =
					progressUpdateTaskRef != null ? progressUpdateTaskRef.get() : null;

				result = taskInstance.runInBackground(progress -> {
					if (progressTaskInstance != null) {
						UI_HANDLER.post(() ->
							progressTaskInstance.onProgressUpdate(progress));
					}
				});
				deliverResult();
			};

			if (executorService != null) {
				backgroundFutureTask = new FutureTask<>(task, null);
				executorService.submit(backgroundFutureTask);
			} else {
				backgroundThread = new Thread(task);
				backgroundThread.start();
			}
		}
	}

	/**
	 * Delivers the background task result to the result task on the UI thread.
	 * This method is called automatically when the background task completes.
	 * It retrieves the result task from its WeakReference and, if still available,
	 * posts it to the UI thread for execution.
	 *
	 * <p>The result is delivered only if:
	 * <ul>
	 *   <li>The result task has not been garbage collected</li>
	 *   <li>The background task completed successfully without throwing an exception</li>
	 * </ul>
	 * </p>
	 *
	 * <p><b>Note:</b> This method executes on the background thread but posts
	 * the result handling to the UI thread, ensuring thread-safe UI updates.</p>
	 *
	 * @see #start()
	 * @see ResultTask#onResult(Object)
	 */
	private void deliverResult() {
		final ResultTask<TaskResult> resultTaskInstance =
			resultTaskRef != null ? resultTaskRef.get() : null;

		if (resultTaskInstance != null) {
			UI_HANDLER.post(() -> resultTaskInstance.onResult(result));
		}
	}

	/**
	 * Cancels the currently running background task.
	 * This method attempts to stop the asynchronous job if it's still running.
	 * The cancellation behavior depends on how the task was started:
	 *
	 * <ul>
	 *   <li><b>If using ExecutorService:</b> Calls FutureTask.cancel(true) which
	 *       interrupts the background thread if it's still running</li>
	 *   <li><b>If using direct thread:</b> Calls Thread.interrupt() on the
	 *       background thread</li>
	 * </ul>
	 *
	 * <p><b>Important:</b> Cancellation is a cooperative mechanism. The background
	 * task should periodically check the thread's interrupted status and terminate
	 * gracefully when interrupted. Interrupting a thread does not guarantee immediate
	 * stoppage if the task is not designed to respond to interrupts.</p>
	 *
	 * <p>After cancellation, the result task will not be invoked even if the
	 * background task continues running.</p>
	 *
	 * @see Thread#interrupt()
	 * @see FutureTask#cancel(boolean)
	 * @see #start()
	 */
	public void cancel() {
		if (executorService != null && backgroundFutureTask != null) {
			backgroundFutureTask.cancel(true);
		} else if (backgroundThread != null) {
			backgroundThread.interrupt();
		}
	}

	/**
	 * Returns the ExecutorService used for executing background tasks.
	 * The executor manages the thread pool that runs the background work.
	 * If no custom executor was set, a default executor (implementation-dependent)
	 * will be used.
	 *
	 * @return The ExecutorService instance used for background task execution
	 * @see #setExecutorService(ExecutorService)
	 */
	@NonNull
	public ExecutorService getExecutorService() {
		return executorService;
	}

	/**
	 * Sets the ExecutorService to be used for executing background tasks.
	 * This allows fine-grained control over thread management, including
	 * thread pooling strategies, priority settings, and lifecycle management.
	 *
	 * <p>If not set, the AsyncJobUtils implementation will use a default executor,
	 * typically a cached thread pool or single-thread executor appropriate for
	 * the platform.</p>
	 *
	 * <p><b>Important:</b> The caller is responsible for managing the lifecycle
	 * of the provided ExecutorService, including proper shutdown to prevent
	 * memory leaks and thread exhaustion.</p>
	 *
	 * @param executorService The ExecutorService to use (must not be null)
	 * @throws NullPointerException if the provided executorService is null
	 * @see #getExecutorService()
	 */
	public void setExecutorService(@NonNull ExecutorService executorService) {
		this.executorService = executorService;
	}

	/**
	 * Retrieves the background task wrapped in a WeakReference.
	 * The background task is responsible for performing the main asynchronous work,
	 * such as network calls, database operations, or computational tasks.
	 *
	 * <p>The task is stored in a WeakReference to prevent memory leaks when
	 * the containing component (e.g., Activity, Fragment) is destroyed while
	 * the background task is still pending. If the original task has been
	 * garbage collected, this method returns null.</p>
	 *
	 * @return The BackgroundTask instance if still referenced, null if it has
	 * been garbage collected or was never set
	 * @see #setBackgroundTask(BackgroundTask)
	 */
	public BackgroundTask<TaskResult> getBackgroundTask() {
		return backgroundTaskRef != null ? backgroundTaskRef.get() : null;
	}

	/**
	 * Sets the background task to be executed asynchronously.
	 * The task is stored in a WeakReference to allow garbage collection if the
	 * parent component is destroyed before task completion, preventing memory leaks.
	 *
	 * <p>The background task receives a ProgressCallback parameter that can be used
	 * to report progress updates back to the UI thread. The task should return
	 * a result of type TaskResult which will be passed to the result task.</p>
	 *
	 * <p><b>Note:</b> This method should be called before executing the async job.
	 * The background task is required for meaningful job execution.</p>
	 *
	 * @param backgroundTask The background task to execute (must not be null)
	 * @throws NullPointerException if the provided backgroundTask is null
	 * @see #getBackgroundTask()
	 * @see BackgroundTask#runInBackground(ProgressCallback)
	 */
	public void setBackgroundTask(
		@NonNull BackgroundTask<TaskResult> backgroundTask
	) {
		this.backgroundTaskRef = new WeakReference<>(backgroundTask);
	}

	/**
	 * Retrieves the result task wrapped in a WeakReference.
	 * The result task processes the outcome of the background task on the UI thread.
	 *
	 * <p>The task is stored in a WeakReference to prevent memory leaks when
	 * the containing component is destroyed. If the original task has been
	 * garbage collected, this method returns null.</p>
	 *
	 * @return The ResultTask instance if still referenced, null if it has been
	 * garbage collected or was never set
	 * @see #setResultTask(ResultTask)
	 */
	public ResultTask<TaskResult> getResultTask() {
		return resultTaskRef != null ? resultTaskRef.get() : null;
	}

	/**
	 * Sets the result task that handles the background task's output.
	 * The task is stored in a WeakReference to allow garbage collection if the
	 * parent component is destroyed, preventing memory leaks.
	 *
	 * <p>The result task is executed on the main/UI thread after the background
	 * task completes successfully. It receives the result produced by the background
	 * task and typically updates the UI, caches data, or triggers follow-up operations.</p>
	 *
	 * <p>If the background task throws an exception, the result task will not be
	 * invoked (error handling depends on the implementation).</p>
	 *
	 * @param resultTask The result task to execute on the UI thread (must not be null)
	 * @throws NullPointerException if the provided resultTask is null
	 * @see #getResultTask()
	 * @see ResultTask#onResult(Object)
	 */
	public void setResultTask(@NonNull ResultTask<TaskResult> resultTask) {
		this.resultTaskRef = new WeakReference<>(resultTask);
	}

	/**
	 * Retrieves the progress update task wrapped in a WeakReference.
	 * This task handles intermediate progress updates from the background task.
	 *
	 * <p>The task is stored in a WeakReference to prevent memory leaks when
	 * the containing component is destroyed. If the original task has been
	 * garbage collected, this method returns null.</p>
	 *
	 * @return The ProgressUpdateTask instance if still referenced, null if it
	 * has been garbage collected or was never set
	 * @see #setProgressUpdateTask(ProgressUpdateTask)
	 */
	public ProgressUpdateTask getProgressUpdateTask() {
		return progressUpdateTaskRef != null ? progressUpdateTaskRef.get() : null;
	}

	/**
	 * Sets the progress update task for reporting intermediate progress.
	 * The task is stored in a WeakReference to allow garbage collection if the
	 * parent component is destroyed, preventing memory leaks.
	 *
	 * <p>The progress update task is executed on the main/UI thread each time
	 * the background task calls ProgressCallback.onProgress(). This is useful for
	 * updating progress bars, loading indicators, or status messages during
	 * long-running operations.</p>
	 *
	 * <p>Progress values are typically integers representing completion percentage
	 * (0-100), but the exact range and meaning depend on the implementation.</p>
	 *
	 * @param progressUpdateTask The progress update task to execute on the UI thread
	 *                           (must not be null)
	 * @throws NullPointerException if the provided progressUpdateTask is null
	 * @see #getProgressUpdateTask()
	 * @see ProgressUpdateTask#onProgressUpdate(int)
	 * @see ProgressCallback#onProgress(int)
	 */
	public void setProgressUpdateTask(
		@NonNull ProgressUpdateTask progressUpdateTask
	) {
		this.progressUpdateTaskRef = new WeakReference<>(progressUpdateTask);
	}

	/**
	 * Interface for background tasks that produce a result.
	 * Implementations should perform long-running operations in the runInBackground()
	 * method, which is executed on a background thread.
	 *
	 * @param <Result> The type of result produced by the background task
	 */
	public interface BackgroundTask<Result> {
		/**
		 * Executes the background operation.
		 * This method runs on a background thread and should not perform any UI operations.
		 * It receives a ProgressCallback that can be used to report progress updates
		 * back to the UI thread.
		 *
		 * @param progressCallback Callback for reporting progress to the UI thread
		 * @return The result of the background operation, to be passed to the ResultTask
		 */
		Result runInBackground(@NonNull ProgressCallback progressCallback);
	}

	/**
	 * Interface for handling the result of a background task.
	 * The onResult() method is executed on the main/UI thread after the background
	 * task completes successfully.
	 *
	 * @param <Result> The type of result produced by the background task
	 */
	public interface ResultTask<Result> {
		/**
		 * Processes the result from the background task.
		 * This method runs on the UI thread and can safely update UI components,
		 * show dialogs, or perform other UI-related operations.
		 *
		 * @param result The result produced by the background task
		 */
		void onResult(Result result);
	}

	/**
	 * Interface for simple UI tasks that don't require parameters or results.
	 * The runOnUIThread() method is executed on the main/UI thread and is useful
	 * for UI updates that don't depend on background task results.
	 */
	public interface UITask {
		/**
		 * Executes a UI operation on the main thread.
		 * This method runs on the UI thread and can safely update UI components,
		 * but should avoid long-running operations that could block the UI.
		 */
		void runOnUIThread();
	}

	/**
	 * Interface for background tasks that don't produce a result.
	 * This is a convenience variant of BackgroundTask for operations that don't
	 * need to return data, such as logging, file operations, or simple data updates.
	 */
	public interface BackgroundTaskNoResult {
		/**
		 * Executes the background operation without returning a result.
		 * This method runs on a background thread and should not perform any UI operations.
		 * For operations that need to report progress, consider using BackgroundTask
		 * with a Void or Unit result type.
		 */
		void runInBackground();
	}

	/**
	 * Interface for handling progress updates from background tasks.
	 * The onProgressUpdate() method is executed on the main/UI thread each time
	 * the background task reports progress.
	 */
	public interface ProgressUpdateTask {
		/**
		 * Called when the background task reports a progress update.
		 * This method runs on the UI thread and can safely update progress indicators,
		 * loading bars, or status text.
		 *
		 * @param progress The current progress value (typically 0-100, but range may
		 *                 vary by implementation)
		 */
		void onProgressUpdate(int progress);
	}

	/**
	 * Interface for reporting progress from background tasks.
	 * Implementations of BackgroundTask receive this callback and should call
	 * onProgress() to report progress updates, which will be delivered to the
	 * ProgressUpdateTask on the UI thread.
	 *
	 * <p>Example usage:
	 * <pre>
	 * new BackgroundTask&lt;String&gt;() {
	 *     public String runInBackground(ProgressCallback callback) {
	 *         callback.onProgress(0);
	 *         // Do some work
	 *         callback.onProgress(50);
	 *         // Do more work
	 *         callback.onProgress(100);
	 *         return "Complete";
	 *     }
	 * }
	 * </pre>
	 * </p>
	 */
	public interface ProgressCallback {
		/**
		 * Reports a progress update from the background thread.
		 * This method can be called multiple times during background task execution.
		 * The progress value will be passed to the ProgressUpdateTask on the UI thread.
		 *
		 * <p><b>Note:</b> This method is thread-safe and can be called from any thread,
		 * but will always deliver the update to the UI thread.</p>
		 *
		 * @param progress The current progress value (typically 0-100)
		 */
		void onProgress(int progress);
	}

	/**
	 * Builder class for constructing AsyncJobUtils instances with type-safe configuration.
	 * This builder follows the fluent builder pattern, allowing method chaining for
	 * convenient setup of asynchronous job components.
	 *
	 * <p>The builder is generic and parameterized with the result type that the background
	 * task will produce. This ensures type safety throughout the asynchronous job pipeline,
	 * from background processing to result handling.</p>
	 *
	 * <p>Example usage:
	 * <pre>
	 * AsyncJobUtils&lt;String&gt; jobUtils = new AsyncJobUtils.Builder&lt;String&gt;()
	 *     .withBackgroundTask(() -> fetchDataFromNetwork())
	 *     .withResultTask(result -> updateUI(result))
	 *     .withProgressUpdateTask(progress -> showProgress(progress))
	 *     .withExecutorService(Executors.newSingleThreadExecutor())
	 *     .build();
	 * </pre>
	 * </p>
	 *
	 * @param <JobResult> The type of result produced by the background task and consumed
	 *                    by the result task
	 */
	public static class Builder<JobResult> {

		private BackgroundTask<JobResult> backgroundTask;
		private ResultTask<JobResult> resultTask;
		private ProgressUpdateTask progressUpdateTask;
		private ExecutorService executorService;

		/**
		 * Sets the background task to be executed asynchronously.
		 * This task runs on a background thread and performs the main work of the job,
		 * such as network operations, database queries, or intensive computations.
		 * The task should return a result of type JobResult which will be passed to
		 * the result task upon completion.
		 *
		 * @param task The background task to execute (must not be null)
		 * @return This builder instance for method chaining
		 * @throws NullPointerException if the provided task is null
		 */
		@NonNull
		public Builder<JobResult> withBackgroundTask(
			@NonNull BackgroundTask<JobResult> task
		) {
			this.backgroundTask = task;
			return this;
		}

		/**
		 * Sets the result task that processes the background task's result.
		 * This task runs on the main/UI thread after the background task completes successfully.
		 * It receives the result produced by the background task and typically updates the UI,
		 * caches the result, or triggers follow-up operations.
		 *
		 * @param task The result task to execute on the main thread (must not be null)
		 * @return This builder instance for method chaining
		 * @throws NullPointerException if the provided task is null
		 */
		@NonNull
		public Builder<JobResult> withResultTask(
			@NonNull ResultTask<JobResult> task
		) {
			this.resultTask = task;
			return this;
		}

		/**
		 * Sets the progress update task for reporting intermediate progress.
		 * This task runs on the main/UI thread and is called whenever the background task
		 * reports progress updates. It's useful for updating progress bars, loading indicators,
		 * or status messages during long-running operations.
		 *
		 * <p>Progress values are typically integers representing percentage complete (0-100),
		 * but the exact type depends on the ProgressUpdateTask implementation.</p>
		 *
		 * @param task The progress update task to execute on the main thread (must not be null)
		 * @return This builder instance for method chaining
		 * @throws NullPointerException if the provided task is null
		 */
		@NonNull
		public Builder<JobResult> withProgressUpdateTask(
			@NonNull ProgressUpdateTask task
		) {
			this.progressUpdateTask = task;
			return this;
		}

		/**
		 * Sets a custom ExecutorService for managing background threads.
		 * If not specified, a default executor (typically a cached thread pool or single-thread executor)
		 * will be used. Custom executors allow fine-grained control over thread management,
		 * such as limiting concurrent tasks, using scheduled execution, or sharing thread pools
		 * across multiple components.
		 *
		 * <p>Important: The caller is responsible for managing the lifecycle of the provided
		 * ExecutorService (e.g., shutting it down when no longer needed).</p>
		 *
		 * @param executor The ExecutorService to use for background task execution (must not be null)
		 * @return This builder instance for method chaining
		 * @throws NullPointerException if the provided executor is null
		 */
		@NonNull
		public Builder<JobResult> withExecutorService(
			@NonNull ExecutorService executor
		) {
			this.executorService = executor;
			return this;
		}

		/**
		 * Builds and returns a fully configured AsyncJobUtils instance.
		 * This method validates that all required components are present and creates
		 * a new AsyncJobUtils object with the configured tasks and executor service.
		 *
		 * <p>All previously set components (background task, result task, progress task,
		 * and executor service) are passed to the constructed AsyncJobUtils instance.
		 * If any required component is missing, the resulting AsyncJobUtils may throw
		 * exceptions when executed, depending on its implementation.</p>
		 *
		 * @return A new AsyncJobUtils instance configured with the builder's settings
		 * @throws IllegalStateException if required components (e.g., background task)
		 *                               are missing and the AsyncJobUtils implementation
		 *                               requires them (implementation-dependent)
		 */
		@NonNull
		public AsyncJobUtils<JobResult> build() {
			AsyncJobUtils<JobResult> asyncJobUtils = new AsyncJobUtils<>();
			asyncJobUtils.setBackgroundTask(backgroundTask);
			asyncJobUtils.setResultTask(resultTask);
			asyncJobUtils.setProgressUpdateTask(progressUpdateTask);
			asyncJobUtils.setExecutorService(executorService);
			return asyncJobUtils;
		}
	}
}