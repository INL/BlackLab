package nl.inl.blacklab.search.grouping;

import java.util.Arrays;
import java.util.List;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import nl.inl.blacklab.MockForwardIndex;
import nl.inl.blacklab.MockHits;
import nl.inl.blacklab.MockSearcher;
import nl.inl.blacklab.MockTerms;
import nl.inl.blacklab.perdocument.DocProperty;
import nl.inl.blacklab.perdocument.DocPropertyDecade;
import nl.inl.blacklab.search.Hits;
import nl.inl.blacklab.search.grouping.HitPropertyContextWords.ContextPart;
import nl.inl.blacklab.search.grouping.HitPropertyContextWords.ContextStart;

public class TestHitPropertySerialize {

	MockSearcher mockSearcher = new MockSearcher();

	Hits hits = new MockHits(mockSearcher);

	@Before
	public void setUp() {
		mockSearcher.setForwardIndex("contents%lemma", new MockForwardIndex(new MockTerms("aap", "noot", "mies")));
	}

	@Test
	public void testHitPropertySerialize() {
		HitProperty prop;

		prop = new HitPropertyDocumentDecade(hits, "decade");
		Assert.assertEquals("decade:decade", prop.serialize());

		prop = new HitPropertyDocumentId(hits);
		prop.setReverse(true);
		String exp = "-docid";
		Assert.assertEquals(exp, prop.serialize());
		Assert.assertEquals(exp, HitProperty.deserialize(hits, exp).serialize());

		prop = new HitPropertyHitText(hits, "contents", "lemma", true);
		Assert.assertEquals("hit:lemma:s", prop.serialize());

		List<ContextPart> contextParts = Arrays.asList(
			new ContextPart(ContextStart.LEFT_OF_HIT, 1, 1),         // second word to left of hit
			new ContextPart(ContextStart.HIT_TEXT_FROM_START, 0, 1), // first two hit words
			new ContextPart(ContextStart.HIT_TEXT_FROM_END, 0, 0)    // last hit word
		);
		prop = new HitPropertyContextWords(hits, "contents", "lemma", true, contextParts);
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

		val1 = new HitPropValueContextWord(hits, "lemma", 2, true);
		String exp = "cwo:lemma:s:mies";
		Assert.assertEquals(exp, val1.serialize());
		Assert.assertEquals(exp, HitPropValue.deserialize(hits, exp).serialize());

		val1 = new HitPropValueDecade(1980);
		exp = "dec:1980";
		Assert.assertEquals(exp, val1.serialize());
		Assert.assertEquals(exp, HitPropValue.deserialize(hits, exp).serialize());

		val = new HitPropValueMultiple(new HitPropValue[] {val1, new HitPropValueString("blabla")});
		exp = "dec:1980,str:blabla";
		Assert.assertEquals(exp, val.serialize());
		Assert.assertEquals(exp, HitPropValue.deserialize(hits, exp).serialize());

		val = new HitPropValueMultiple(new HitPropValue[] {val1, new HitPropValueString("$bl:ab,la")});
		exp = "dec:1980,str:$DLbl$CLab$CMla";
		Assert.assertEquals(exp, val.serialize());
		Assert.assertEquals(exp, HitPropValue.deserialize(hits, exp).serialize());
	}
}
