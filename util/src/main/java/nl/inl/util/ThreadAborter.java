package nl.inl.util;

public interface ThreadAborter {

    static ThreadAborter create() {
        return new ThreadAborterImpl();
    }

    /**
     * If the thread we're controlling is supposed to be aborted, throw an exception.
     *
     * @throws InterruptedException if thread was interrupted from elsewhere (e.g. load manager)
     */
    void checkAbort() throws InterruptedException;

    /**
    * How long has this job been running currently?
    *
    * This does not include any previous running phases.
    *
    * @return number of ms since the job was set to running, or 0 if not running
    */
    long runningForMs();

}
