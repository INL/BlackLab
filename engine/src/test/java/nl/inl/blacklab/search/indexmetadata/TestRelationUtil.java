package nl.inl.blacklab.search.indexmetadata;

import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

public class TestRelationUtil {

    @Test
    public void testInlineTagRelationType() {
        String rt = RelationUtil.fullType(RelationUtil.CLASS_INLINE_TAG, "word");
        String tagName = RelationUtil.typeFromFullType(rt);
        Assert.assertEquals("word", tagName);
    }

    @Test
    public void testRelationIndexTerm() {
        String rt = RelationUtil.fullType(RelationUtil.CLASS_INLINE_TAG, "word");

        // Index with attributes in one order - should be sorted alphabetically
        Map<String, String> attr = Map.of("attr3", "v3", "attr2", "v2", "attr1", "v1");
        String term = RelationUtil.indexTerm(rt, attr, false);

        // Now search with attributes in a different order - should again be sorted so the regex matches
        Map<String, String> attrSearch = Map.of("attr1", "v1", "attr3", "v3", "attr2", "v2");
        String regex = RelationUtil.searchRegex(null, rt, attrSearch);
        Assert.assertTrue(term.matches(regex));

        Map<String, String> attrDecoded = RelationUtil.attributesFromIndexedTerm(term);
        Assert.assertEquals(attr, attrDecoded);

        Assert.assertEquals(rt, RelationUtil.fullTypeFromIndexedTerm(term));
    }
}
