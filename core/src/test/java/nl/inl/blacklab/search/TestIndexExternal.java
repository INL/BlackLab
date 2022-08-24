package nl.inl.blacklab.search;

/**
 * Test the integrated index.
 */
public class TestIndexExternal extends TestIndexIntegrated {

    BlackLabIndex.IndexType indexType()  { return BlackLabIndex.IndexType.EXTERNAL_FILES; }

}
