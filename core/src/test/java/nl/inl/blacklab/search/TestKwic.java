package nl.inl.blacklab.search;

import java.util.Arrays;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

public class TestKwic {

	List<String> props = Arrays.asList("punct", "lemma", "pos", "word");
	List<String> tokens = Arrays.asList(
			" ", "de", "lw", "De",
			" ", "snel", "bn", "snelle",
			" ", "bruin", "bn", "bruine",
			" ", "vos", "zn", "vos");
	List<String> left = Arrays.asList(" ", "de", "lw", "De", " ", "snel", "bn", "snelle");
	List<String> match = Arrays.asList(" ", "bruin", "bn", "bruine");
	List<String> right = Arrays.asList(" ", "vos", "zn", "vos");
	String expLeft = "<w lemma=\"de\" pos=\"lw\">De</w> <w lemma=\"snel\" pos=\"bn\">snelle</w> ";
	String expMatch = "<w lemma=\"bruin\" pos=\"bn\">bruine</w>";
	String expRight = " <w lemma=\"vos\" pos=\"zn\">vos</w>";

	@Test
	public void testKwicToConcordance() {
		Kwic kwic = new Kwic(props, tokens, 2, 3);
		Concordance conc = kwic.toConcordance();

		Assert.assertEquals(expLeft, conc.left());
		Assert.assertEquals(expMatch, conc.match());
		Assert.assertEquals(expRight, conc.right());
	}

	@Test
	public void testKwicNewConstructor() {
		Kwic kwic = new Kwic(props, tokens, 2, 3);

		Assert.assertEquals(expLeft + expMatch + expRight, kwic.getFullXml());
		Assert.assertEquals(left, kwic.getLeft());
		Assert.assertEquals(match, kwic.getMatch());
		Assert.assertEquals(right, kwic.getRight());
		Assert.assertEquals(Arrays.asList("De", "snelle"), kwic.getLeft("word"));
	}
}
