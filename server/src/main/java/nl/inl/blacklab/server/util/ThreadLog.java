package nl.inl.blacklab.server.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Allows us to keep a log of what a thread has been up to. Can store both a
 * linear message log and keep a number of counters. Counters are reported in
 * the order of creation.
 */
public class ThreadLog {

    /** Thread logs for all our threads */
    static Map<Long, ThreadLog> threadLogs = new HashMap<>();

    /**
     * Get (or create) thread log for current thread.
     * 
     * @return the thread log
     */
    public static ThreadLog get() {
        long threadId = Thread.currentThread().getId();
        return get(threadId);
    }

    /**
     * Get (or create) thread log for specified thread.
     *
     * @param threadId id of the thread we're interested in
     * @return the thread log
     */
    public static ThreadLog get(long threadId) {
        ThreadLog log = threadLogs.get(threadId);
        if (log == null) {
            log = new ThreadLog();
            threadLogs.put(threadId, log);
        }
        return log;
    }

    /**
     * Delete the log for the specified thread.
     *
     * @param threadId id of the thread we want to delete the log for
     */
    public static void delete(long threadId) {
        threadLogs.remove(threadId);
    }

    /**
     * Add a message to the sequential log for the current thread.
     *
     * @param msg message to add
     */
    public static void log(String msg) {
        get().addMessage(msg);
    }

    /**
     * Change the value of a counter for the current thread.
     *
     * @param name counter name
     * @param delta change to make to the counter value
     */
    public static void count(String name, int delta) {
        get().changeCounter(name, delta);
    }

    List<String> messages = new ArrayList<>();

    Map<String, Integer> counters = new LinkedHashMap<>();

    /**
     * Add a message to the sequential log.
     *
     * @param msg message to add
     */
    public void addMessage(String msg) {
        messages.add(msg);
    }

    /**
     * Change the value of a counter.
     *
     * @param name counter name
     * @param delta change to make to the counter value
     */
    public void changeCounter(String name, int delta) {
        Integer n = counters.get(name);
        if (n == null)
            n = 0;
        n += delta;
        counters.put(name, n);
    }
//
//	/**
//	 * Dump the ThreadLog in DataObject format, so we can return it as JSON or XML.
//	 * @return the DataObject representation
//	 */
//	public DataObject dump() {
//		DataObjectMapElement result = new DataObjectMapElement();
//
//		DataObjectList m = new DataObjectList("message");
//		for (String msg: messages) {
//			m.add(msg);
//		}
//		result.put("messages", m);
//
//		DataObjectMapAttribute c = new DataObjectMapAttribute("counter", "name");
//		for (Map.Entry<String, Integer> counter: counters.entrySet()) {
//			c.put(counter.getKey(), counter.getValue());
//		}
//
//		return result;
//	}
}
