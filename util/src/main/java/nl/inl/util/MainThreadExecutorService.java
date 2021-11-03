package nl.inl.util;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * Mock executorService that actually just runs all submitted tasks on the main
 * thread. Note: unlike regular ExecutorService, this class is NOT thread-safe.
 */
public class MainThreadExecutorService extends AbstractExecutorService {

    @FunctionalInterface
    public interface RejectedExecutionHandler {
        void rejectedExecution(Runnable r, MainThreadExecutorService e);
    }

    private RejectedExecutionHandler handler;

    private boolean shutdown;

    MainThreadExecutorService() {
        this((r, e) -> {
            throw new RejectedExecutionException("Task " + r.toString() + " rejected from " + e.toString());
        });
    }

    MainThreadExecutorService(RejectedExecutionHandler h) {
        if (h == null)
            throw new IllegalArgumentException("No RejectedExecutionHandler specified");
        this.handler = h;
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        shutdown = true;
        return true;
    }

    @Override
    public boolean isShutdown() {
        return shutdown;
    }

    @Override
    public boolean isTerminated() {
        return shutdown;
    }

    @Override
    public void shutdown() {
        shutdown = true;
    }

    @Override
    public List<Runnable> shutdownNow() {
        return Collections.emptyList();
    }

    @Override
    public void execute(Runnable command) {
        if (shutdown)
            handler.rejectedExecution(command, this);
        command.run();
    }
}
