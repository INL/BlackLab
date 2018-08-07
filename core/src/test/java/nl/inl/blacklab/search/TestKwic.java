package nl.inl.blacklab.search;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.Assert;
import org.junit.Test;

import nl.inl.blacklab.mocks.MockAnnotatedField;
import nl.inl.blacklab.mocks.MockAnnotation;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.Annotation;

public class TestKwic {

    static final List<String> props = Arrays.asList("punct", "lemma", "pos", "word");
    
    static final List<Annotation> annots = props.stream().map(name -> new MockAnnotation(name)).collect(Collectors.toList());
    
    static final AnnotatedField field = new MockAnnotatedField("contents", annots);
    
    static final List<String> tokens = Arrays.asList(
            " ", "de", "lw", "De",
            " ", "snel", "bn", "snelle",
            " ", "bruin", "bn", "bruine",
            " ", "vos", "zn", "vos");
    
    static final List<String> left = Arrays.asList(" ", "de", "lw", "De", " ", "snel", "bn", "snelle");
    
    static final List<String> match = Arrays.asList(" ", "bruin", "bn", "bruine");
    
    static final List<String> right = Arrays.asList(" ", "vos", "zn", "vos");
    
    static final String expLeft = "<w lemma=\"de\" pos=\"lw\">De</w> <w lemma=\"snel\" pos=\"bn\">snelle</w> ";
    
    static final String expMatch = "<w lemma=\"bruin\" pos=\"bn\">bruine</w>";
    
    static final String expRight = " <w lemma=\"vos\" pos=\"zn\">vos</w>";
    
    @Test
    public void testKwicToConcordance() {
        Kwic kwic = new Kwic(annots, tokens, 2, 3);
        Concordance conc = kwic.toConcordance();
    
        Assert.assertEquals(expLeft, conc.left());
        Assert.assertEquals(expMatch, conc.match());
        Assert.assertEquals(expRight, conc.right());
    }

    @Test
    public void testKwicNewConstructor() {
        Kwic kwic = new Kwic(annots, tokens, 2, 3);
    
        Assert.assertEquals(expLeft + expMatch + expRight, kwic.getFullXml());
        Assert.assertEquals(left, kwic.getLeft());
        Assert.assertEquals(match, kwic.getMatch());
        Assert.assertEquals(right, kwic.getRight());
        Assert.assertEquals(Arrays.asList("De", "snelle"), kwic.getLeft(annots.get(3)));
    }

}
