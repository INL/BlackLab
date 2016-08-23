package nl.inl.blacklab.server.jobs;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashSet;
import java.util.Set;

import org.apache.log4j.Logger;

import nl.inl.blacklab.search.Prioritizable;
import nl.inl.blacklab.search.Searcher;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.exceptions.ServiceUnavailable;
import nl.inl.blacklab.server.search.SearchManager;
import nl.inl.util.ExUtil;
import nl.inl.util.ThreadPriority;
import nl.inl.util.ThreadPriority.Level;

public abstract class Job implements Comparable<Job>, Prioritizable {

	protected static final Logger logger = Logger.getLogger(Job.class);

	private static final double ALMOST_ZERO = 0.0001;

	private static final int RUN_PAUSE_PHASE_JUST_STARTED = 5;

	/** How long a job remains "young". Young jobs are treated differently
	 *  than old jobs when it comes to load management, because we want to
	 *  give new searches a fair chance, but we also want to eventually put
	 *  demanding searches on the back burner if the system is overloaded. */
	private static final int YOUTH_THRESHOLD_SEC = 20;

	private static final int REFS_INVALID = -9999;

	/** If true (as it should be for production use), we call cleanup() on jobs that
	 *  aren't referred to anymore in an effor to assist the Java garbage collector.
	 *  EXPERIMENTAL
	 */
	final static boolean ENABLE_JOB_CLEANUP = false;

	/** id for the next job started */
	static long nextJobId = 0;

	/** Unique job id */
	long id = nextJobId++;

	/**
	 * Number of references to this Job. If this reaches 0, and the thread
	 * is not running, we can safely call cleanup().
	 *
	 * Note that the cache itself also counts as a reference to the job,
	 * so if refsToJob == 1, it is only in the cache, not currently referenced
	 * by another job or search request. We can use this to decide when a
	 * search can safely be removed from the cache.
	 */
	int refsToJob = 0;

	/**
	 * The jobs we're waiting for, so we can notify them in case we get cancelled,
	 * and our "load scheduler" knows we're not currently using the CPU.
	 */
	Set<Job> waitingFor = new HashSet<>();

	/**
	 * Wait for the specified job to finish
	 * @param job the job to wait for
	 * @throws BlsException
	 */
	protected void waitForJobToFinish(Job job) throws BlsException {
		synchronized(waitingFor) {
			waitingFor.add(job);
			job.incrRef();
		}
		try {
			job.waitUntilFinished();
		} finally {
			synchronized(waitingFor) {
				job.decrRef();
				waitingFor.remove(job);
			}
		}
	}

	/** The total accumulated paused time so far.
	 * If the search is currently paused,
	 * that time is not taken into account yet.
	 * (it is added when the search is resumed).
	 * So total paused time is:
	 *  pausedTime + (level == PAUSED ? now() - pausedAt : 0)
	 */
	protected long pausedTime;

	/** When this job was started (or -1 if not started yet) */
	protected long startedAt;

	/** When this job was finished (or -1 if not finished yet) */
	protected long finishedAt;

	/** If the search thread threw an exception, it's stored here. */
	protected Throwable thrownException;

	/** The last time the results of this search were accessed (for caching) */
	private long lastAccessed;

	/** If we're paused, this is the time when we were paused */
	private long setLevelPausedAt;

	/** If we're running, this is the time when we were set to running */
	private long setLevelRunningAt;

	/** The index searcher */
	protected Searcher searcher;

	/** Has perform() been called or not? Don't call it twice! */
	private boolean performCalled = false;

	/** Has cancelJob() been called or not? Don't call it twice! */
	private boolean cancelJobCalled = false;

	/** Thread object carrying out the search, if performing the search. */
	private SearchThread searchThread = null;

	/** Description of this job */
	protected JobDescription jobDesc;

	/** The job we're operating on (i.e. the hits to sort, or the docs to group) */
	Job inputJob = null;

	/** The servlet */
	protected SearchManager searchMan;

	/** Who created this job? */
	protected User user;

	/** Is this job running in low priority? */
	protected ThreadPriority.Level level = ThreadPriority.Level.RUNNING;

	public Job(SearchManager searchMan, User user, JobDescription par) throws BlsException {
		super();
		this.searchMan = searchMan;
		this.user = user;
		this.jobDesc = par;
		searcher = searchMan.getIndexManager().getSearcher(par.getIndexName());
		resetLastAccessed();
		startedAt = -1;
		finishedAt = -1;
		thrownException = null;
	}

	public JobDescription getDescription() {
		return jobDesc;
	}

	public Searcher getSearcher() {
		return searcher;
	}

	/**
	 * Compare based on 'worthiness' (descending).
	 *
	 * 'Worthiness' is a measure indicating how important a
	 * job is, and determines what jobs get the CPU and what jobs
	 * are paused or aborted. It also determines what finished
	 * jobs are removed from the cache.
	 *
	 * @param o the other search, to compare to
	 * @return -1 if this search is worthier than o;
	 *   1 if this search less worthy o;
	 *   or 0 if they are equally worthy
	 */
	@Override
	public int compareTo(Job o) {

		// Our return values with more logical names.
		// (1 refers to this object, 2 to the one supplied as a parameter)
		final int WORTHY1 = -1;
		final int WORTHY2 = -WORTHY1;

		// Determine 'finished' status for these searches
		boolean finished1 = finished();
		boolean finished2 = o.finished();
		if (finished1 != finished2) {
			// One is finished, one is not: the unfinished one is worthiest.
			// (because the top searches get the CPU, and the bottom
			//  finished searches might be discarded from the cache)
			return finished2 ? WORTHY1 : WORTHY2;
		}
		if (finished1) {
			// Neither are running: most recently used search is the worthiest.
			// (because we want a LRU cache)
			return notAccessedFor() < o.notAccessedFor() ? WORTHY1 : WORTHY2;
		}
		// Both searches are unfinished.

		// Rules to make sure jobs aren't oscillating between
		// running and not running too much.
		// First, check if job just started running, and if so,
		// try not to pause them again right away.
		double runtime1 = currentRunPhaseLength();
		double runtime2 = o.currentRunPhaseLength();
		boolean justStartedRunning1 = runtime1 > ALMOST_ZERO && runtime1 < RUN_PAUSE_PHASE_JUST_STARTED;
		boolean justStartedRunning2 = runtime2 > ALMOST_ZERO && runtime2 < RUN_PAUSE_PHASE_JUST_STARTED;
		if (justStartedRunning1 || justStartedRunning2) {
			// At least one of the jobs just started running.
			// The shortest-running one is worthiest.
			return runtime1 < runtime2 ? WORTHY1 : WORTHY2;
		}
		// Now, check if job was just paused, and if so,
		// try not to resume it right away.
		double pause1 = currentPauseLength();
		double pause2 = o.currentPauseLength();
		boolean justPaused1 = pause1 > ALMOST_ZERO && pause1 < RUN_PAUSE_PHASE_JUST_STARTED;
		boolean justPaused2 = pause2 > ALMOST_ZERO && pause2 < RUN_PAUSE_PHASE_JUST_STARTED;
		if (justPaused1 || justPaused2) {
			// At least one of the jobs was just paused.
			// The longest-paused one is worthiest.
			return pause1 > pause2 ? WORTHY1 : WORTHY2;
		}
		// Neither job just started running or were just paused.

		// Are these jobs relatively young or relatively old?
		// Young jobs get the CPU in the hope that they will complete
		// quickly; older jobs are paused sooner because they eat up
		// a lot of resources.
		double exec1 = totalExecTime();
		double exec2 = o.totalExecTime();
		boolean young1 = exec1 < YOUTH_THRESHOLD_SEC;
		boolean young2 = exec2 < YOUTH_THRESHOLD_SEC;
		if (young1 != young2) {
			// One is old, one is young: young job is worthiest.
			// (so heavy jobs don't crowd out the light ones)
			return young1 ? WORTHY1 : WORTHY2;
		}
		// Both young or both old. Look at execution time.
		if (young1) {
			// Both young: the oldest is the worthiest.
			// (so light jobs get a fair chance to complete)
			return exec1 > exec2 ? WORTHY1 : WORTHY2;
		}

		// Both jobs are old.
		// Are they searching or counting?
		boolean count1 = this instanceof JobHitsTotal || this instanceof JobDocsTotal;
		boolean count2 = this instanceof JobHitsTotal || this instanceof JobDocsTotal;
		if (count1 != count2) {
			// One is counting, the other is searching: search is worthiest.
			return count2 ? WORTHY1 : WORTHY2;
		}

		// Both searching or both counting; the youngest is worthiest.
		// (so heavy jobs don't crowd out the lighter ones)
		return exec1 < exec2 ? WORTHY1 : WORTHY2;
	}

	public long getLastAccessed() {
		return lastAccessed;
	}

	public void resetLastAccessed() {
		lastAccessed = System.currentTimeMillis();
	}

	public JobDescription getParameters() {
		return jobDesc;
	}

	/** Perform the search.
	 *
	 * @param waitTimeMs if < 0, method blocks until the search is finished. For any
	 *   value >= 0, waits for the specified amount of time or until the search is finished,
	 *   then returns.
	 *
	 * @throws BlsException on parse error or other query-related error (e.g. too broad)
	 */
	final public void perform(int waitTimeMs) throws BlsException {
		if (performCalled)
			throw new RuntimeException("Already performing search!");

		// Create and start thread
		// TODO: use thread pooling..?
		startedAt = System.currentTimeMillis();
		setLevelRunningAt = startedAt;
		searchThread = new SearchThread(this);
		searchThread.start();
		performCalled = true;

		waitUntilFinished(waitTimeMs);
	}

	/**
	 * @throws BlsException on error
	 */
	protected void performSearchInternal() throws BlsException {
		JobDescription inputDesc = jobDesc.getInputDesc();
		if (inputDesc != null) {
			// Perform the input job and then call this job's performSearch method
			inputJob = searchMan.search(user, inputDesc, false);
			try {
				waitForJobToFinish(inputJob);
				performSearch();
			} finally {
				inputJob.decrRef();
				inputJob = null;
			}
		} else {
			// No input job.
			performSearch();
		}

	}

	/**
	 * @throws BlsException on error
	 */
	protected void performSearch() throws BlsException {
		// (to override)
	}

	/**
	 * Is this search operation finished?
	 * (i.e. can we start working with the results?)
	 *
	 * @return true iff the search operation is finished and the results are available
	 */
	public boolean finished() {
		if (!performCalled)
			return false;
		return finishedAt >= 0 || thrownException != null;
	}

	/**
	 * Did the search throw an exception?
	 *
	 * @return true iff the search operation threw an exception
	 */
	public boolean threwException() {
		if (!performCalled)
			return false;
		return finished() && thrownException != null;
	}

	/**
	 * Get the exception thrown by the search thread, if any
	 * @return the exception, or null if none was thrown
	 */
	public Throwable getThrownException() {
		return threwException() ? thrownException : null;
	}

	/**
	 * Re-throw the exception thrown by the search thread, if any.

	 * @throws BlsException
	 */
	public void rethrowException() throws BlsException {
		Throwable exception = getThrownException();
		if (exception == null)
			return;
		logger.debug("Re-throwing exception from search thread:\n" + exception.getClass().getName() + ": " + exception.getMessage());
		if (exception instanceof BlsException)
			throw (BlsException)exception;
		throw ExUtil.wrapRuntimeException(exception);
	}

	/**
	 * Wait until this job's finished, an Exception is thrown or the specified
	 * time runs out.
	 *
	 * @param maxWaitMs maximum time to wait, or a negative number for no limit
	 * @throws BlsException
	 */
	public void waitUntilFinished(int maxWaitMs) throws BlsException {
		int defaultWaitStep = 100;
		boolean waitUntilFinished = maxWaitMs < 0;
		while (!performCalled || ( (waitUntilFinished || maxWaitMs > 0) && !finished())) {
			int w = defaultWaitStep;
			if (!waitUntilFinished) {
				if (maxWaitMs < defaultWaitStep)
					w = maxWaitMs;
				maxWaitMs -= w;
			}
			try {
				Thread.sleep(w);
			} catch (InterruptedException e) {
				throw new ServiceUnavailable("The server seems to be under heavy load right now. Please try again later.");
			}
		}
		// If an Exception occurred, re-throw it now.
		rethrowException();
	}

	/**
	 * Wait until this job is finished (or an Exception is thrown)
	 *
	 * @throws BlsException
	 */
	public void waitUntilFinished() throws BlsException {
		waitUntilFinished(-1);
	}

	/**
	 * Try to cancel this job.
	 */
	public void cancelJob() {
		if (!performCalled)
			return; // can't cancel, hasn't been started yet (shouldn't happen)
		if (cancelJobCalled)
			return; // don't call this twice!
		cancelJobCalled = true;

		searchThread.interrupt();
		searchThread = null; // ensure garbage collection

		// Tell the jobs we were waiting for we're no longer interested
		if (waitingFor != null) {
			synchronized(waitingFor) {
				for (Job j: waitingFor) {
					j.decrRef(); //decrementClientsWaiting();
				}
				waitingFor.clear();
			}
		}
	}

	/**
	 * Estimate how much memory this Search object holds.
	 *
	 * For now, defaults to an arbitrary 1M.
	 *
	 * TODO: implement size estimation in subclasses
	 *
	 * @return estimated memory size in bytes
	 */
	public long estimateSizeBytes() {
		return 1000000;
	}

	@Override
	public String toString() {
		return id + ": " + jobDesc.toString();
	}

	private String shortUserId() {
		return user.uniqueIdShort();
	}

	public void debug(Logger logger, String msg) {
		logger.debug(shortUserId() + " " + msg);
	}

	public void warn(Logger logger, String msg) {
		logger.warn(shortUserId() + " " + msg);
	}

	public void info(Logger logger, String msg) {
		logger.info(shortUserId() + " " + msg);
	}

	public void error(Logger logger, String msg) {
		logger.error(shortUserId() + " " + msg);
	}

	public void dataStream(DataStream ds, boolean debugInfo) {
		boolean isCount = (this instanceof JobHitsTotal) || (this instanceof JobDocsTotal);
		ds.startMap()
			.entry("id", id)
			.entry("class", getClass().getSimpleName())
			.startEntry("jobDesc");
		jobDesc.dataStreamEntries(ds);
		ds	.endEntry()
			.startEntry("stats")
				.startMap()
					.entry("type", isCount ? "count" : "search")
					.entry("status", status())
					.entry("userWaitTime", userWaitTime())
					.entry("totalExecTime", totalExecTime())
					.entry("notAccessedFor", notAccessedFor())
					.entry("pausedFor", currentPauseLength())
					.entry("createdBy", shortUserId())
					.entry("refsToJob", refsToJob - 1) // (- 1 because the cache always references it)
					.entry("waitingForJobs", waitingFor.size())
				.endMap()
			.endEntry();
		if (debugInfo) {
			ds.startEntry("debugInfo");
			dataStreamDebugInfo(ds);
			ds.endEntry();
		}
		dataStreamSubclassEntries(ds);
		ds.endMap();
	}

	protected void dataStreamSubclassEntries(DataStream ds) {
		// (subclasses can add extra entries here)
	}

	private void dataStreamDebugInfo(DataStream ds) {
		ds.startMap()
			.startEntry("waitingForIds")
				.startList();
		// Ids of the jobs this thread is waiting for, if any
		if (waitingFor.size() > 0) {
			for (Job j: waitingFor) {
				ds	.item("jobId", j.id);
			}
		}
		ds		.endList()
			.endEntry()
			// More information about job state
			.entry("startedAt", startedAt)
			.entry("finishedAt", finishedAt)
			.entry("lastAccessed", lastAccessed)
			.entry("pausedTime", pausedTime)
			.entry("setLevelPausedAt", setLevelPausedAt)
			.entry("setLevelRunningAt", setLevelRunningAt)
			.entry("performCalled", performCalled)
			.entry("cancelJobCalled", cancelJobCalled)
			.entry("priorityLevel", level.toString())
			.entry("resultsPriorityLevel", getPriorityOfResultsObject().toString())
			.startEntry("thrownException")
				.startMap();
		// Information about thrown exception, if any
		if (thrownException != null) {
			PrintWriter st = new PrintWriter(new StringWriter());
			thrownException.printStackTrace(st);
			ds
					.entry("class", thrownException.getClass().getName())
					.entry("message", thrownException.getMessage())
					.entry("stackTrace", st.toString());
		}
		ds		.endMap()
			.endEntry()
			.startEntry("searchThread")
				.startMap();
		// Information about thread object, if any
		if (searchThread != null) {
			StackTraceElement[] stackTrace = searchThread.getStackTrace();
			StringBuilder stackTraceStr = new StringBuilder();
			for (StackTraceElement element: stackTrace) {
				stackTraceStr.append(element.toString()).append("\n");
			}
			ds
					.entry("name", searchThread.getName())
					.entry("osPriority", searchThread.getPriority())
					.entry("isAlive", searchThread.isAlive())
					.entry("isDaemon", searchThread.isDaemon())
					.entry("isInterrupted", searchThread.isInterrupted())
					.entry("state", searchThread.getState().toString())
					.entry("currentlyExecuting", stackTraceStr.toString());
		}
		ds		.endMap()
			.endEntry()
		.endMap();
	}

	private String status() {
		if (finished())
			return "finished";
		if (level == null)
			return "(level == NULL!)";
		switch(level) {
		case PAUSED: return "paused";
		case RUNNING_LOW_PRIO:    return "lowprio";
		default: return "running";
		}
	}

	protected void cleanup() {
		logger.debug("Job.cleanup() called");
		if (waitingFor != null) {
			synchronized(waitingFor) {
				for (Job j: waitingFor) {
					j.decrRef();
				}
				waitingFor.clear();
				waitingFor = null;
			}
		}
		thrownException = null;
		searchThread = null;
		jobDesc = null;
		refsToJob = REFS_INVALID;
	}

	public synchronized void incrRef() {
		if (refsToJob == REFS_INVALID)
			throw new RuntimeException("Cannot increment refs, job was already cleaned up!");
		refsToJob++;
	}

	public synchronized void decrRef() {
		if (refsToJob == REFS_INVALID)
			throw new RuntimeException("Cannot decrement refs, job was already cleaned up!");
		refsToJob--;
		if (refsToJob == 1) {
			// Only in cache; set the last accessed time so we
			// know for how long it's been ignored.
			resetLastAccessed();
		} else if (refsToJob == 0) {
			// No references to this job, not even in the cache.
			// We can safely cancel it if it was still
			// running. We optionally call cleanup to
			// assist with garbage collection.
			cancelJob();
			if (ENABLE_JOB_CLEANUP)
				cleanup();
		}
	}

	/**
	 * Return this search's cache age in seconds.
	 *
	 * Cache age is defined as the time between now and the last time
	 * it was accessed (for finished searches only).
	 *
	 * Running searches always have a zero age. Check executionTime()
	 * for search time.
	 *
	 * @return the age in seconds
	 */
	public double cacheAge() {
		if (finished())
			return (System.currentTimeMillis() - lastAccessed) / 1000.0;
		return 0;
	}

	/**
	 * How long the user has waited for this job.
	 *
	 * For finished searches, this is from the start time
	 * to the finish time. For other searches, from the start
	 * time until now.
	 *
	 * @return execution time in ms
	 */
	public double userWaitTime() {
		if (startedAt < 0)
			return -1;
		if (finishedAt < 0)
			return (System.currentTimeMillis() - startedAt) / 1000.0;
		return (finishedAt - startedAt) / 1000.0;
	}

	/**
	 * Returns how long ago this job was last accessed.
	 *
	 * Note that if a client is waiting for this job to complete, this always returns 0.
	 *
	 * @return how long ago this job was last accessed.
	 */
	public double notAccessedFor() {
		if (refsToJob > 1) {
			// More references to this job than just the cache;
			// This counts as being continually accessed, because
			// those jobs apparently need this job.
			return 0;
		}
		return (System.currentTimeMillis() - lastAccessed) / 1000.0;
	}

	/**
	 * How long has this job been paused for currently?
	 *
	 * This does not include any previous pauses.
	 *
	 * @return number of ms since the job was paused, or 0 if not paused
	 */
	public double currentPauseLength() {
		if (level != Level.PAUSED)
			return 0;
		return (System.currentTimeMillis() - setLevelPausedAt) / 1000.0;
	}

	/**
	 * How long has this job been running currently?
	 *
	 * This does not include any previous running phases.
	 *
	 * @return number of ms since the job was set to running, or 0 if not running
	 */
	public double currentRunPhaseLength() {
		if (level == Level.PAUSED)
			return 0;
		return (System.currentTimeMillis() - setLevelRunningAt) / 1000.0;
	}

	/**
	 * How long has this job been paused in total?
	 *
	 * @return total number of ms the job has been paused
	 */
	public double pausedTotal() {
		if (finished() || level != Level.PAUSED)
			return pausedTime / 1000.0;
		return (pausedTime + System.currentTimeMillis() - setLevelPausedAt) / 1000.0;
	}

	/**
	 * How long has this job actually been running in total?
	 *
	 * Running time is the total time minus the paused time.
	 *
	 * @return total number of ms the job has actually been running
	 */
	public double totalExecTime() {
		return userWaitTime() - pausedTotal();
	}

	/**
	 * Is this job waiting for another job or jobs, and
	 * therefore not using the CPU?
	 * @return true if it's waiting, false if not
	 */
	public boolean isWaitingForOtherJob() {
		synchronized(waitingFor) {
			return waitingFor.size() > 0;
		}
	}

	/**
	 * Returns the Prioritizable object we need to set
	 * the thread priority on.
	 *
	 * @return the object to prioritize
	 */
	protected abstract Prioritizable getObjectToPrioritize();

	/**
	 * Set the thread priority level.
	 *
	 * @param level the desired priority level.
	 */
	@Override
	public void setPriorityLevel(ThreadPriority.Level level) {
		if (this.level != level) {
			if (this.level == Level.PAUSED) {
				// Keep track of total paused time
				pausedTime += System.currentTimeMillis() - setLevelPausedAt;
			} else if (level == Level.PAUSED) {
				// Make sure we can keep track of total paused time
				setLevelPausedAt = System.currentTimeMillis();
			}
			if (level != Level.PAUSED) {
				setLevelRunningAt = System.currentTimeMillis();
			}
			this.level = level;
		}
		setPriorityInternal();
	}

	/**
	 * Get the thread priority level.
	 *
	 * @return the current priority level.
	 */
	@Override
	public ThreadPriority.Level getPriorityLevel() {
		return level;
	}

	/**
	 * Set the priority/paused status of a Prioritizable object.
	 *
	 * @param p object to set the priority of
	 */
	protected void setPriority(Prioritizable p) {
		if (p != null) {
			p.setPriorityLevel(level);
		}
	}

	/**
	 * Set the operation to the current priority level
	 * (normal, low or paused).
	 */
	protected void setPriorityInternal() {
		setPriority(getObjectToPrioritize());
	}

	/**
	 * Get the actual priority of the Hits or DocResults object.
	 * @return the priority level
	 */
	protected Level getPriorityOfResultsObject() {
		Prioritizable p = getObjectToPrioritize();
		return p == null ? Level.RUNNING : p.getPriorityLevel();
	}

	public void setFinished() {
		finishedAt = System.currentTimeMillis();
		if (level != Level.RUNNING) {
			// Don't confuse the system by still being in PAUSED
			// (possible because this is cooperative multitasking,
			//  so PAUSED doesn't necessarily mean the thread isn't
			//  running right now and it might actually finish while
			//  "PAUSED")
			setPriorityLevel(Level.RUNNING);
		}
	}

}
