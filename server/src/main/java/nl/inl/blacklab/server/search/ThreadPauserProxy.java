package nl.inl.blacklab.server.search;

import nl.inl.util.ThreadPauser;

/**
 * A proxy for a ThreadPauser that might not be available yet.
 * 
 * Keeps track of the intended pause state of the ThreadPauser, so we can
 * set it as the ThreadPauser becomes available.
 * 
 * This class exists separate from ThreadPauser because we don't have a results object
 * and therefore no  thread pauser when first created, but we still want to track
 * if the search should be paused or not. 
 */
class ThreadPauserProxy implements ThreadPauser {

    /** The result object's threadPauser object, if any */
    private ThreadPauser threadPauser = null;
    
    /** Remember if we're supposed to be paused for when we get a ThreadPauser */
    private boolean shouldBePaused = false;

    public void setThreadPauser(ThreadPauser threadPauser) {
        this.threadPauser = threadPauser;
        if (shouldBePaused)
            threadPauser.pause(true);  // status was set to paused before we had the ThreadPauser
    }
    
    @Override
    public boolean isPaused() {
        return shouldBePaused;
    }
    
    @Override
    public void pause(boolean paused) {
        this.shouldBePaused = paused;
        if (threadPauser != null)
            threadPauser.pause(paused);
    }

    @Override
    public long currentPauseLength() {
        return threadPauser == null ? 0 : threadPauser.currentPauseLength();
    }

    @Override
    public long currentRunPhaseLength() {
        return threadPauser == null ? 0 : threadPauser.currentRunPhaseLength();
    }

    @Override
    public long pausedTotal() {
        return threadPauser == null ? 0 : threadPauser.pausedTotal();
    }

    @Override
    public void waitIfPaused() throws InterruptedException {
        if (threadPauser != null) {
            threadPauser.waitIfPaused();
        }
    }

}