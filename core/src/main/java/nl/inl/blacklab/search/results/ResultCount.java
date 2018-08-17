package nl.inl.blacklab.search.results;

public class ResultCount implements SearchResult {
    
    private int count;

    public ResultCount(int count) {
        this.count = count;
    }
    
    public int value() {
        return count;
    }

}
