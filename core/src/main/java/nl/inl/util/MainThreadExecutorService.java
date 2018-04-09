package nl.inl.util;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * Mock executorService that actually just runs all submitted tasks on the main thread.
 * Note: unlike regular ExecutorService, this class is NOT thread-safe.
 */
public class MainThreadExecutorService extends AbstractExecutorService {

	private boolean shutdown;

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
		    throw new RejectedExecutionException("ExecutorService has been stopped already");
	    command.run();
	}
}
