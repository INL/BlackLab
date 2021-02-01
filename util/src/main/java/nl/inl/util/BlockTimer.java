package nl.inl.util;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class BlockTimer implements AutoCloseable {
    private static final Logger logger = LogManager.getLogger(BlockTimer.class); 
    
    private static final long RUNNING = -1;
    
    private long start;
    private long end;
    private TimerGroup group;
    private Thread ownThread;
    
    private BlockTimer(TimerGroup group) {
        this.group = group;
    }
    public Long runtime() { return (this.end - this.start); }

    public BlockTimer child(String message) {
        return this.group.child(message);
    }
    
    public static BlockTimer create(String message) {
        return new TimerGroup(message, null).get();
    }
    
    private void start() {
        if (this.end == RUNNING) throw new IllegalStateException("Timer already running");
        
        this.ownThread = Thread.currentThread();
        this.start = System.nanoTime();
        this.end = RUNNING;
    }
    
    @Override
    public void close() {
        this.end = System.nanoTime();
        this.group.put(this, ownThread);
        this.ownThread = null;
    }
    
    private boolean isClosed() {
        return this.end != RUNNING;
    }
    
    private static class TimerGroup { 
        private String message;
        private long invocations = 0;
        private long running = 0;
        private long runtime = 0;
        private TimerGroup parent;
        private final Thread ownThread;
        private int childTimeInOwnThread = 0;
        
        private List<BlockTimer> instances = new ArrayList<>();
        private final ConcurrentHashMap<String, TimerGroup> children = new ConcurrentHashMap<>();

        
        private TimerGroup(String message, TimerGroup parent) {
            this.message = message;
            this.parent = parent;
            this.ownThread = Thread.currentThread();
        }
        
        private synchronized BlockTimer get() {
            BlockTimer t = instances.isEmpty() ? new BlockTimer(this) : instances.remove(instances.size() - 1);
            ++this.running;
            t.start();
            return t;
        }
        
        private synchronized void put(BlockTimer timer, Thread timerThread) {
            if (!timer.isClosed()) throw new IllegalStateException("Cannot return running timer");
            this.instances.add(timer);
            ++this.invocations;
            --this.running;
            this.runtime += timer.runtime();
            if (this.running < 0) throw new IllegalStateException("Negative running timers!");
            if (timerThread == ownThread)
                childTimeInOwnThread += timer.runtime();
            checkDone();
        }
        
        private BlockTimer child(String message) {
            return this.children.computeIfAbsent(message, __ -> new TimerGroup(message, this)).get();
        }

        private synchronized void checkDone() {
            if (this.running == 0 && this.children.values().stream().allMatch(c -> c.running == 0)) {
                if (this.parent == null) this.print(0, 0);
                else this.parent.checkDone();
            }
        }

        private String print(int lead, int messageLength) {

            final String[] labels = {"total", "av.", "self", "av. self"};
            final Long[] values = {
                runtime / 1_000_000,
                (runtime / invocations) / 1_000_000,
                (runtime - childTimeInOwnThread) / 1_000_000,
                ((runtime - childTimeInOwnThread) / invocations) / 1_000_000
            };
            
            
            final String fmt = "% 6dms %s ";
            StringBuilder msg = new StringBuilder();

            msg.append("\n"+StringUtils.repeat(' ', lead*4) + StringUtils.rightPad(message, messageLength));
            for (int i = 0; i < labels.length; ++i) msg.append(String.format(fmt, values[i], labels[i]));
            msg.append(String.format("% 6dx ", invocations));
            
            int longestMessage = this.children.values().stream().mapToInt(t -> t.message.length()).reduce(0, Math::max);
            for (TimerGroup c : this.children.values()) msg.append(c.print(lead+1, longestMessage));
            
            if (this.parent != null) {
                return msg.toString();
            } else {
                logger.debug(msg.toString());
                return null;
            }      
        }
    }
}
