package nl.inl.util;

import nl.inl.blacklab.search.Pausible;

public interface ThreadPauser extends Pausible {

    @Override
    void pause(boolean paused);

    @Override
    boolean isPaused();

    /**
     * If the thread we're controlling is supposed to be paused, wait until it is 
     * unpaused or it's interrupted.
     *
     * @throws InterruptedException if thread was interrupted from elsewhere (e.g. load manager)
     */
    void waitIfPaused() throws InterruptedException;

    /**
         * How long has this job been paused for currently?
        *
        * This does not include any previous pauses.
        *
        * @return number of ms since the job was paused, or 0 if not paused
        */
    long currentPauseLength();

    /**
        * How long has this job been running currently?
        *
        * This does not include any previous running phases.
        *
        * @return number of ms since the job was set to running, or 0 if not running
        */
    long currentRunPhaseLength();

    /**
        * How long has this job been paused in total?
        *
        * @return total number of ms the job has been paused
        */
    long pausedTotal();

}
