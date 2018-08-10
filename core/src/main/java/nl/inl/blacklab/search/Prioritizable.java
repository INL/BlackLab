package nl.inl.blacklab.search;

public interface Prioritizable {

    /**
     * Pause or unpause this operation.
     *
     * This can be used to stop a heavy search from consuming CPU resources
     * when other users are waiting.
     * 
     * Pausing actually amounts to "proceeding very slowly".
     *
     * @param pause if true, pause the operation; if false, unpause it
     */
    void pause(boolean pause);

    /**
     * Is this operation currently paused?
     *
     * @return true if operation is paused, false if not
     */
    boolean isPaused();
    
}
