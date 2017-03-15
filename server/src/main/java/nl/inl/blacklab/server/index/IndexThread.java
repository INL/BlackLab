package nl.inl.blacklab.server.index;

import java.lang.Thread.UncaughtExceptionHandler;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A (background) thread for indexing.
 */
final public class IndexThread extends Thread implements UncaughtExceptionHandler {
	protected static final Logger logger = LogManager.getLogger(IndexThread.class);

	/** If search execution failed, this is the exception that was thrown */
	Throwable thrownException = null;

	private IndexTask job;

	/**
	 * Construct a new SearchThread
	 * @param job the job to do
	 */
	public IndexThread(IndexTask job) {
		this.job = job;
		setUncaughtExceptionHandler(this);
	}

	/**
	 * Run the thread, performing the requested search.
	 */
	@Override
	public void run() {
		try {

			// ... index the data...
			job.run();

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
			thrownException = e;
		}
	}

	/**
	 * Has the thread stopped running?
	 * @return true iff the thread has terminated
	 */
	public boolean finished() {
		return getState() == State.TERMINATED;
	}

	/**
	 * Did the thread throw an Exception?
	 * @return true iff it threw an Exception
	 */
	public boolean threwException() {
		return thrownException != null;
	}

	/**
	 * Get the Exception that was thrown by the thread (if any)
	 * @return the thrown Exception, or null if none was thrown
	 */
	public Throwable getThrownException() {
		return thrownException;
	}

	@Override
	public void uncaughtException(Thread t, Throwable e) {
		logger.debug("Index thread threw an exception, saving it:\n" + e.getClass().getName() + ": " + e.getMessage());
		thrownException = e;
	}

}
