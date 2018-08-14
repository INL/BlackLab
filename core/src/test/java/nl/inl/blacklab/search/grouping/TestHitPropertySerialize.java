package nl.inl.blacklab.search.grouping;

import java.util.Arrays;
import java.util.List;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import nl.inl.blacklab.mocks.MockBlackLabIndex;
import nl.inl.blacklab.mocks.MockForwardIndex;
import nl.inl.blacklab.mocks.MockHits;
import nl.inl.blacklab.mocks.MockTerms;
import nl.inl.blacklab.resultproperty.DocProperty;
import nl.inl.blacklab.resultproperty.DocPropertyDecade;
import nl.inl.blacklab.resultproperty.HitPropValue;
import nl.inl.blacklab.resultproperty.HitPropValueContextWord;
import nl.inl.blacklab.resultproperty.HitPropValueDecade;
import nl.inl.blacklab.resultproperty.HitPropValueMultiple;
import nl.inl.blacklab.resultproperty.HitPropValueString;
import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.resultproperty.HitPropertyContextWords;
import nl.inl.blacklab.resultproperty.HitPropertyContextWords.ContextPart;
import nl.inl.blacklab.resultproperty.HitPropertyContextWords.ContextStart;
import nl.inl.blacklab.resultproperty.HitPropertyDocumentDecade;
import nl.inl.blacklab.resultproperty.HitPropertyDocumentId;
import nl.inl.blacklab.resultproperty.HitPropertyHitText;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.indexmetadata.MetadataField;
import nl.inl.blacklab.search.results.Hits;

public class TestHitPropertySerialize {
    
    private MetadataField decadeField = new MockMetadataField("decade");

    private MockBlackLabIndex mockIndex = new MockBlackLabIndex();

    private Hits hits = new MockHits(mockIndex, mockIndex.mainAnnotatedField());

    private Annotation lemmaAnnotation;

    @Before
    public void setUp() {
        lemmaAnnotation = mockIndex.mainAnnotatedField().annotation("lemma");
        mockIndex.setForwardIndex(lemmaAnnotation, new MockForwardIndex(new MockTerms("aap", "noot", "mies")));
    }

    @Test
    public void testHitPropertySerialize() {
        HitProperty prop;

        prop = new HitPropertyDocumentDecade(decadeField);
        Assert.assertEquals("decade:decade", prop.serialize());

        prop = new HitPropertyDocumentId().reverse();
        String exp = "-docid";
        Assert.assertEquals(exp, prop.serialize());
        Assert.assertEquals(exp, HitProperty.deserialize(hits, exp).serialize());

        prop = new HitPropertyHitText(mockIndex, lemmaAnnotation, MatchSensitivity.SENSITIVE);
        Assert.assertEquals("hit:lemma:s", prop.serialize());

        List<ContextPart> contextParts = Arrays.asList(
                new ContextPart(ContextStart.LEFT_OF_HIT, 1, 1), // second word to left of hit
                new ContextPart(ContextStart.HIT_TEXT_FROM_START, 0, 1), // first two hit words
                new ContextPart(ContextStart.HIT_TEXT_FROM_END, 0, 0) // last hit word
        );
        prop = new HitPropertyContextWords(mockIndex, lemmaAnnotation, MatchSensitivity.SENSITIVE, contextParts);
        Assert.assertEquals("context:lemma:s:L2-2;H1-2;E1-1", prop.serialize());
    }

    @Test
    public void testDocPropertySerialize() {
        DocProperty prop;

        prop = new DocPropertyDecade("decade");
        prop.setReverse(true);
        String exp = "-decade:decade";
        Assert.assertEquals(exp, prop.serialize());
        Assert.assertEquals(exp, DocProperty.deserialize(exp).serialize());
    }

    @Test
    public void testHitPropValueSerialize() {
        HitPropValue val, val1;

        val1 = new HitPropValueContextWord(hits.index(), lemmaAnnotation, 2, MatchSensitivity.SENSITIVE);
        String exp = "cwo:lemma:s:mies";
        Assert.assertEquals(exp, val1.serialize());
        Assert.assertEquals(exp, HitPropValue.deserialize(hits, exp).serialize());

        val1 = new HitPropValueDecade(1980);
        exp = "dec:1980";
        Assert.assertEquals(exp, val1.serialize());
        Assert.assertEquals(exp, HitPropValue.deserialize(hits, exp).serialize());

        val = new HitPropValueMultiple(new HitPropValue[] { val1, new HitPropValueString("blabla") });
        exp = "dec:1980,str:blabla";
        Assert.assertEquals(exp, val.serialize());
        Assert.assertEquals(exp, HitPropValue.deserialize(hits, exp).serialize());

        val = new HitPropValueMultiple(new HitPropValue[] { val1, new HitPropValueString("$bl:ab,la") });
        exp = "dec:1980,str:$DLbl$CLab$CMla";
        Assert.assertEquals(exp, val.serialize());
        Assert.assertEquals(exp, HitPropValue.deserialize(hits, exp).serialize());
    }
}
