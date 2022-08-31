package nl.inl.blacklab.search;

/**
 * Test the integrated index.
 */
public class TestIndexExternal extends TestIndexIntegrated {

    BlackLabIndex.IndexType indexType()  { return BlackLabIndex.IndexType.EXTERNAL_FILES; }

    int numberOfTerms() {
        // HACK. Secondary values were not stored in the terms file in the classic external FI,
        // but they are now in the integrated FI (matching Lucene).
        return 26;
    }

}
