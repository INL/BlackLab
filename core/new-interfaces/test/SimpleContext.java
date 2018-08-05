package nl.inl.blacklab.interfaces.test;

import nl.inl.blacklab.interfaces.ContextSize;

/** Simple, symmetric hit context */
final class SimpleContext implements ContextSize {
    
    public static SimpleContext get(int wordsAroundHit) {
        return new SimpleContext(wordsAroundHit);
    }

    private int wordsAroundHit;
    
    private SimpleContext(int wordsAroundHit) {
        this.wordsAroundHit = wordsAroundHit;
    }
    
    @Override
    public int left() {
        return wordsAroundHit;
    }

    @Override
    public int right() {
        return wordsAroundHit;
    }
}