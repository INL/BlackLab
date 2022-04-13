package nl.inl.blacklab.exceptions;

public class IndexTooOld extends ErrorOpeningIndex {
    
    public IndexTooOld(String message) {
        super(message);
    }

    public IndexTooOld(String message, Throwable e) {
        super(message, e);
    }

    public IndexTooOld(Throwable e) {
        super(e);
    }

}
