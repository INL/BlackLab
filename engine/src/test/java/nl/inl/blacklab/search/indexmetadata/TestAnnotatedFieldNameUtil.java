package nl.inl.blacklab.search.indexmetadata;

import org.junit.Assert;
import org.junit.Test;

public class TestAnnotatedFieldNameUtil {

    @Test
    public void testGetBaseName() {
        String fieldName;
        fieldName = AnnotatedFieldNameUtil.annotationField("field", "annotation");
        Assert.assertEquals("field", AnnotatedFieldNameUtil.getBaseName(fieldName));

        fieldName = AnnotatedFieldNameUtil.annotationField("field", "annotation", "sensitivity");
        Assert.assertEquals("field", AnnotatedFieldNameUtil.getBaseName(fieldName));
    }

    @Test
    public void testAnnotatedFieldName() {
        Assert.assertEquals("field" + AnnotatedFieldNameUtil.ANNOT_SEP + "annotation",
                AnnotatedFieldNameUtil.annotationField("field", "annotation"));
        Assert.assertEquals("field" + AnnotatedFieldNameUtil.ANNOT_SEP + "annotation"
                + AnnotatedFieldNameUtil.SENSITIVITY_SEP + "sensitivity",
                AnnotatedFieldNameUtil.annotationField("field", "annotation", "sensitivity"));
        Assert.assertEquals("test" + AnnotatedFieldNameUtil.ANNOT_SEP + "word" + AnnotatedFieldNameUtil.SENSITIVITY_SEP + "s",
                AnnotatedFieldNameUtil.annotationField("test", "word", "s"));
        Assert.assertEquals("hw" + AnnotatedFieldNameUtil.SENSITIVITY_SEP + "s",
                AnnotatedFieldNameUtil.annotationField(null, "hw", "s"));
    }

    public void testArray(String[] expected, String[] actual) {
        Assert.assertEquals(expected.length, actual.length);
        for (int i = 0; i < expected.length; i++) {
            Assert.assertEquals(expected[i], actual[i]);
        }
    }

    @Test
    public void testGetNameComponents() {
        testArray(new String[] { "contents", "lemma" },
                AnnotatedFieldNameUtil.getNameComponents(AnnotatedFieldNameUtil.annotationField("contents", "lemma", null)));
        testArray(new String[] { "contents", "lemma", "s" },
                AnnotatedFieldNameUtil.getNameComponents(AnnotatedFieldNameUtil.annotationField("contents", "lemma", "s")));

        testArray(new String[] { "contents", null, null, "cid" },
                AnnotatedFieldNameUtil.getNameComponents(AnnotatedFieldNameUtil.bookkeepingField("contents", null, "cid")));
        testArray(new String[] { "contents", "lemma", null, "fiid" },
                AnnotatedFieldNameUtil.getNameComponents(AnnotatedFieldNameUtil.bookkeepingField("contents", "lemma", "fiid")));

    }
    
    @Test
    public void testSanitizeXmlElementName() {
        Assert.assertEquals("word", AnnotatedFieldNameUtil.sanitizeXmlElementName("word", false));
        Assert.assertEquals("_0word", AnnotatedFieldNameUtil.sanitizeXmlElementName("0word", false));
        Assert.assertEquals("_xmlword", AnnotatedFieldNameUtil.sanitizeXmlElementName("xmlword", false));
        Assert.assertEquals("fun_word", AnnotatedFieldNameUtil.sanitizeXmlElementName("fun-word", true));
        Assert.assertEquals("fun-word", AnnotatedFieldNameUtil.sanitizeXmlElementName("fun-word", false));
        Assert.assertEquals("fun.word", AnnotatedFieldNameUtil.sanitizeXmlElementName("fun.word", false));
        Assert.assertEquals("fun_word", AnnotatedFieldNameUtil.sanitizeXmlElementName("fun_word", false));
        Assert.assertEquals("word0", AnnotatedFieldNameUtil.sanitizeXmlElementName("word0", false));
    }
}
