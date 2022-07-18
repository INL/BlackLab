package nl.inl.blacklab.searches;

import java.util.Iterator;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.resultproperty.HitGroupProperty;
import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.resultproperty.HitPropertyHitText;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.results.HitGroup;
import nl.inl.blacklab.search.results.HitGroups;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.textpattern.TextPattern;
import nl.inl.blacklab.search.textpattern.TextPatternAnnotation;
import nl.inl.blacklab.search.textpattern.TextPatternAnyToken;
import nl.inl.blacklab.testutil.TestIndex;

public class TestSearchHitGroups {

    private static TestIndex testIndex;

    private static BlackLabIndex index;

    private static AnnotatedField contents;

    @BeforeClass
    public static void setUp() {
        testIndex = new TestIndex();
        index = testIndex.index();
        contents = index.mainAnnotatedField();
    }

    @AfterClass
    public static void tearDown() {
        testIndex.close();
    }

    @Test
    public void testHitGroups() throws InvalidQuery {
        testGroup(false); // slow path
        testGroup(true); // fast path
    }

    private void testGroup(boolean fastPath) throws InvalidQuery {
        String title = fastPath ? "group fast path" : "group slow path";
        TextPattern tp = new TextPatternAnnotation("word", new TextPatternAnyToken(1, 1));
        BLSpanQuery query = tp.toQuery(QueryInfo.create(index));
        HitProperty groupBy = new HitPropertyHitText(index, contents.mainAnnotation(), MatchSensitivity.SENSITIVE);
        SearchHits searchHits = index.search(contents, false).find(query);
        SearchHitGroups searchHitGroups = fastPath ? searchHits.groupStats(groupBy, 0) : searchHits.groupWithStoredHits(groupBy, 1);
        SearchHitGroups sortedGroups = searchHitGroups.sort(HitGroupProperty.identity());
        HitGroups groups = sortedGroups.execute();
        Assert.assertEquals(title + " # groups", 25, groups.size());
        Iterator<HitGroup> it = groups.iterator();
        HitGroup g = it.next();
        Assert.assertEquals(title + " 1st group size", 5, g.size());
        Assert.assertEquals(title + " 1st group id", "aap", g.identity().toString());
        g = it.next();
        Assert.assertEquals(title + " 2nd group size", 1, g.size());
        Assert.assertEquals(title + " 2nd group id", "be", g.identity().toString());
    }

}
