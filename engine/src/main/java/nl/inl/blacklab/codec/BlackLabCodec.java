package nl.inl.blacklab.codec;

import nl.inl.blacklab.search.BlackLabIndex;

public interface BlackLabCodec {
    /**
     * Get the BlackLabIndexWriter.
     *
     * Needed to access the index metadata while indexing.
     *
     * @return the BlackLabIndexWriter
     */
    BlackLabIndex getBlackLabIndex();
}
