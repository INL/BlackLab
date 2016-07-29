package nl.inl.blacklab.server.search;

import java.lang.Thread.UncaughtExceptionHandler;

import org.apache.log4j.Logger;

/**
 * A thread that regularly calls SearchCache.performLoadManagement(null)
 * to ensure that load management continues even if no new requests are coming in.
 */
class LoadManagerThread extends Thread implements UncaughtExceptionHandler {
	private static final Logger logger = Logger.getLogger(LoadManagerThread.class);

	private SearchManager searchMan;

	/**
	 * Construct the load manager thread object.
	 *
	 * @param searchMan the search manager, for calling load management function
	 */
	public LoadManagerThread(SearchManager searchMan) {
		logger.debug("Creating LOADMGR thread...");
		this.searchMan = searchMan;
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

			searchMan.performLoadManagement();
		}
	}

	@Override
	public void uncaughtException(Thread t, Throwable e) {
		logger.debug("LoadManagerThread threw an exception!");
		e.printStackTrace();
	}

}
