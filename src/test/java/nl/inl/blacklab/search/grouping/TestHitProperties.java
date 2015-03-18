package nl.inl.blacklab.search.grouping;

import nl.inl.blacklab.perdocument.DocProperty;
import nl.inl.blacklab.perdocument.DocPropertyDecade;
import nl.inl.blacklab.search.Hits;
import nl.inl.blacklab.search.Searcher;

import org.junit.Assert;
import org.junit.Test;

public class TestHitProperties {

	Hits hits = new Hits((Searcher)null);

	@Test
	public void testHitPropertySerialize() {
		HitProperty prop;

		// Need stub Searcher object for testing!
//		prop = new HitPropertyDocumentDecade(hits, "decade");
//		Assert.assertEquals("decade:decade", prop.serialize());

		prop = new HitPropertyDocumentId(hits);
		prop.setReverse(true);
		String exp = "-docid";
		Assert.assertEquals(exp, prop.serialize());
		Assert.assertEquals(exp, HitProperty.deserialize(hits, exp).serialize());

//		prop = new HitPropertyHitText(hits, "lemma", "s");
//		Assert.assertEquals("hit:lemma:s", prop.serialize());
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

		val1 = new HitPropValueDecade(1980);
		String exp = "dec:1980";
		Assert.assertEquals(exp, val1.serialize());
		Assert.assertEquals(exp, HitPropValue.deserialize(hits, "dec:1980").serialize());

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
