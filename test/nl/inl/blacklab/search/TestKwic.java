package nl.inl.blacklab.search;

import java.util.Arrays;
import java.util.List;

import junit.framework.Assert;

import org.junit.Test;

public class TestKwic {

	@Test
	public void testKwicToConcordance() {
		List<String> props = Arrays.asList("punct", "lemma", "pos", "word");
		List<String> left = Arrays.asList(" ", "de", "lw", "De", " ", "snel", "bn", "snelle");
		List<String> match = Arrays.asList(" ", "bruin", "bn", "bruine");
		List<String> right = Arrays.asList(" ", "vos", "zn", "vos");

		Kwic kwic = new Kwic(props, left, match, right);
		Concordance conc = kwic.toConcordance();

		Assert.assertEquals("<w lemma=\"de\" pos=\"lw\">De</w> <w lemma=\"snel\" pos=\"bn\">snelle</w> ", conc.left);
		Assert.assertEquals("<w lemma=\"bruin\" pos=\"bn\">bruine</w>", conc.hit);
		Assert.assertEquals(" <w lemma=\"vos\" pos=\"zn\">vos</w>", conc.right);
	}
}
