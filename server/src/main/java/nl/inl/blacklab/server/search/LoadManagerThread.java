package nl.inl.blacklab.server.search;

import java.lang.Thread.UncaughtExceptionHandler;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A thread that regularly calls SearchCache.performLoadManagement(null)
 * to ensure that load management continues even if no new requests are coming in.
 */
class LoadManagerThread extends Thread implements UncaughtExceptionHandler {
	private static final Logger logger = LogManager.getLogger(LoadManagerThread.class);

	private SearchCache searchCache;

	/**
	 * Construct the load manager thread object.
	 *
	 * @param searchCache cache of running and completed searches, on which we call load management
	 */
	public LoadManagerThread(SearchCache searchCache) {
		logger.debug("Creating LOADMGR thread...");
		this.searchCache = searchCache;
		setUncaughtExceptionHandler(this);
	}

	/**
	 * Run the thread, performing the requested search.
	 */
	@Override
	public void run() {
		while (!interrupted()) {
			try {
				Thread.sleep(500);
			} catch (InterruptedException e) {
				return;
			}

			synchronized(searchCache) {
				searchCache.performLoadManagement(null);
			}
		}
	}

	@Override
	public void uncaughtException(Thread t, Throwable e) {
		logger.debug("LoadManagerThread threw an exception!");
		e.printStackTrace();
	}

}
