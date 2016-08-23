package nl.inl.blacklab.server.jobs;

import java.lang.Thread.UncaughtExceptionHandler;

import org.apache.log4j.Logger;

/**
 * A (background) thread the search is executed in.
 */
final class SearchThread extends Thread implements UncaughtExceptionHandler {
	protected static final Logger logger = Logger.getLogger(SearchThread.class);

	/** The job to execute */
	private Job search;

	/**
	 * Construct a new SearchThread
	 * @param search the search to execute in the thread
	 */
	SearchThread(Job search) {
		this.search = search;
		setUncaughtExceptionHandler(this);
	}

	/**
	 * Run the thread, performing the requested search.
	 */
	@Override
	public void run() {
		try {
			search.performSearchInternal();
			search.setFinished();
		} catch (Throwable e) {
			// NOTE: we catch Throwable here (while it's normally good practice to
			//  catch only Exception and derived classes) because we need to know if
			//  our thread crashed or not. The Throwable will be re-thrown by the
			//  main thread, so any non-Exception Throwables will then go uncaught
			//  as they "should".

			// We've also set an UncaughtExceptionHandler (the thread object itself)
			// which does the same thing, because apparently some exceptions can occur
			// outside the run() method or aren't caught here for some other reason).
			// Even then, some low-level ones (like OutOfMemoryException) seem to slip by.
			if (search != null) {
				e.printStackTrace();
				search.thrownException = e;
				search.setFinished();
			}
		}
		search = null; // make sure Job gets garbage collected
	}

	@Override
	public void uncaughtException(Thread t, Throwable e) {
		logger.debug("Search thread threw an exception, saving it:\n" + e.getClass().getName() + ": " + e.getMessage());
		if (search != null) {
			search.thrownException = e;
			search.setFinished();
		}
		search = null; // make sure Job gets garbage collected
	}

}
