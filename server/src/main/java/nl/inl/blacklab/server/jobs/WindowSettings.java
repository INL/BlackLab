package nl.inl.blacklab.server.jobs;

public class WindowSettings {

    private final long first;

    private final long size;

    public WindowSettings(long first, long size) {
        super();
        this.first = first;
        this.size = size;
    }

    public long first() {
        return first;
    }

    public long size() {
        return size;
    }

    @Override
    public String toString() {
        return "firstresult=" + first + ", numofresults=" + size;
    }

}
