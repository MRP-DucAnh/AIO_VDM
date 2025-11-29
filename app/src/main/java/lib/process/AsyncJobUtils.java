package lib.process;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.FutureTask;

public final class AsyncJobUtils<TaskResult> {

    private final LogHelperUtils logger = LogHelperUtils.from(getClass());
    private static final Handler UI_HANDLER = new Handler(Looper.getMainLooper());

    private BackgroundTask<TaskResult> backgroundTask;
    private ResultTask<TaskResult> resultTask;
    private ProgressUpdateTask progressUpdateTask;
    private ExecutorService executorService;
    private Thread backgroundThread;
    private FutureTask<?> backgroundFutureTask;
    private TaskResult result;

    public static void executeOnMainThread(final @NonNull UITask uiTask) {
        UI_HANDLER.post(uiTask::runOnUIThread);
    }

    public static void executeInBackground(
        final @NonNull BackgroundTaskNoResult backgroundTask
    ) {
        new Thread(backgroundTask::runInBackground).start();
    }

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

    public void start() {
        if (backgroundTask != null) {
            Runnable task = () -> {
                result = backgroundTask.runInBackground(progress -> {
                    if (progressUpdateTask != null) {
                        UI_HANDLER.post(() ->
                            progressUpdateTask.onProgressUpdate(progress));
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

    private void deliverResult() {
        if (resultTask != null) {
            UI_HANDLER.post(() -> resultTask.onResult(result));
        }
    }

    public void cancel() {
        if (executorService != null && backgroundFutureTask != null) {
            backgroundFutureTask.cancel(true);
        } else if (backgroundThread != null) {
            backgroundThread.interrupt();
        }
    }

    @NonNull
    public ExecutorService getExecutorService() {
        return executorService;
    }

    public void setExecutorService(@NonNull ExecutorService executorService) {
        this.executorService = executorService;
    }

    @NonNull
    public BackgroundTask<TaskResult> getBackgroundTask() {
        return backgroundTask;
    }

    public void setBackgroundTask(
        @NonNull BackgroundTask<TaskResult> backgroundTask
    ) {
        this.backgroundTask = backgroundTask;
    }

    @NonNull
    public ResultTask<TaskResult> getResultTask() {
        return resultTask;
    }

    public void setResultTask(@NonNull ResultTask<TaskResult> resultTask) {
        this.resultTask = resultTask;
    }

    @NonNull
    public ProgressUpdateTask getProgressUpdateTask() {
        return progressUpdateTask;
    }

    public void setProgressUpdateTask(
        @NonNull ProgressUpdateTask progressUpdateTask
    ) {
        this.progressUpdateTask = progressUpdateTask;
    }

    public interface BackgroundTask<Result> {
        Result runInBackground(@NonNull ProgressCallback progressCallback);
    }

    public interface ResultTask<Result> {
        void onResult(Result result);
    }

    public interface UITask {
        void runOnUIThread();
    }

    public interface BackgroundTaskNoResult {
        void runInBackground();
    }

    public interface ProgressUpdateTask {
        void onProgressUpdate(int progress);
    }

    public interface ProgressCallback {
        void onProgress(int progress);
    }

    public static class Builder<JobResult> {

        private BackgroundTask<JobResult> backgroundTask;
        private ResultTask<JobResult> resultTask;
        private ProgressUpdateTask progressUpdateTask;
        private ExecutorService executorService;

        @NonNull
        public Builder<JobResult> withBackgroundTask(
            @NonNull BackgroundTask<JobResult> task
        ) {
            this.backgroundTask = task;
            return this;
        }

        @NonNull
        public Builder<JobResult> withResultTask(
            @NonNull ResultTask<JobResult> task
        ) {
            this.resultTask = task;
            return this;
        }

        @NonNull
        public Builder<JobResult> withProgressUpdateTask(
            @NonNull ProgressUpdateTask task
        ) {
            this.progressUpdateTask = task;
            return this;
        }

        @NonNull
        public Builder<JobResult> withExecutorService(
            @NonNull ExecutorService executor
        ) {
            this.executorService = executor;
            return this;
        }

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