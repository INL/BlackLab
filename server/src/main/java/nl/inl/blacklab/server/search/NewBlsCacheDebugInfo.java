package nl.inl.blacklab.server.search;

import java.io.PrintWriter;
import java.io.StringWriter;

import nl.inl.blacklab.searches.SearchCount;
import nl.inl.blacklab.server.datastream.DataStream;

class NewBlsCacheDebugInfo {
    
    public void dataStream(DataStream ds, NewBlsCacheEntry<?> entry, boolean debugInfo) {
        boolean isCount = (entry.search() instanceof SearchCount);
        ds.startMap()
                .entry("id", entry.id())
                .entry("class", getClass().getSimpleName())
                .entry("jobDesc", entry.search().toString())
                .startEntry("stats")
                .startMap()
                .entry("type", isCount ? "count" : "search")
                .entry("status", entry.isDone() ? "finished" : (entry.threadPauser().isPaused() ? "paused" : "running"))
                .entry("userWaitTime", entry.timeUserWaited())
                .entry("totalExecTime", entry.timeRunning())
                .entry("notAccessedFor", entry.timeSinceLastAccess())
                .entry("pausedFor", entry.threadPauser().currentPauseLength())
                .endMap()
                .endEntry();
        if (debugInfo) {
            ds.startEntry("debugInfo");
            dataStreamDebugInfo(ds, entry);
            ds.endEntry();
        }
        ds.endMap();
    }

    private void dataStreamDebugInfo(DataStream ds, NewBlsCacheEntry<?> entry) {
        ds.startMap();
        // More information about job state
        ds.entry("timeSinceCreation", entry.timeSinceCreation())
                .entry("timeSinceFinished", entry.timeSinceFinished())
                .entry("timeSinceLastAccess", entry.timeSinceLastAccess())
                .entry("timePausedTotal", entry.threadPauser().pausedTotal())
                .entry("searchCancelled", entry.isCancelled())
                .entry("priorityLevel", entry.threadPauser().isPaused() ? "PAUSED" : "RUNNING")
                .startEntry("thrownException")
                .startMap();
        // Information about thrown exception, if any
        Throwable exceptionThrown = entry.exceptionThrown();
        if (exceptionThrown != null) {
            PrintWriter st = new PrintWriter(new StringWriter());
            exceptionThrown.printStackTrace(st);
            ds
                    .entry("class", exceptionThrown.getClass().getName())
                    .entry("message", exceptionThrown.getMessage())
                    .entry("stackTrace", st.toString());
        }
        ds.endMap()
                .endEntry()
                .startEntry("searchThread")
                .startMap();
        // Information about thread object, if any
        Thread thread = entry.thread();
        if (thread != null) {
            StackTraceElement[] stackTrace = thread.getStackTrace();
            StringBuilder stackTraceStr = new StringBuilder();
            for (StackTraceElement element : stackTrace) {
                stackTraceStr.append(element.toString()).append("\n");
            }
            ds
                    .entry("name", thread.getName())
                    .entry("osPriority", thread.getPriority())
                    .entry("isAlive", thread.isAlive())
                    .entry("isDaemon", thread.isDaemon())
                    .entry("isInterrupted", thread.isInterrupted())
                    .entry("state", thread.getState().toString())
                    .entry("currentlyExecuting", stackTraceStr.toString());
        }
        ds.endMap()
                .endEntry()
                .endMap();
    }
}