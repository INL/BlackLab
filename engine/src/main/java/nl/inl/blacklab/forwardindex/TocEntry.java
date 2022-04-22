package nl.inl.blacklab.forwardindex;

/** Table of contents entry; stored in docs.dat */
class TocEntry implements Comparable<TocEntry> {
    /** token offset in tokens.dat */
    public long offset;

    /** Number of tokens in document.
     *  NOTE: this INCLUDES the extra closing token at the end.
     */
    public int length;

    /** was this entry deleted? (remove in next compacting run) */
    public boolean deleted;

    public TocEntry(long offset, int length, boolean deleted) {
        super();
        this.offset = offset;
        this.length = length;
        this.deleted = deleted;
    }

    /**
     * Compare this entry to another (for sorting).
     * 
     * @param o the entry to compare with
     * @return the comparison result
     */
    @Override
    public int compareTo(TocEntry o) {
        return Long.compare(offset, o.offset);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (deleted ? 1231 : 1237);
        result = prime * result + length;
        result = prime * result + (int) (offset ^ (offset >>> 32));
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        TocEntry other = (TocEntry) obj;
        return deleted == other.deleted && length == other.length && offset == other.offset;
    }

}
