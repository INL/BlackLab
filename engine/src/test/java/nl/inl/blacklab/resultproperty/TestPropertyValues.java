package nl.inl.blacklab.resultproperty;

import java.text.Collator;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

import org.junit.Assert;
import org.junit.Test;

import nl.inl.blacklab.forwardindex.Terms;
import nl.inl.blacklab.mocks.MockTerms;
import nl.inl.blacklab.search.BlackLabIndexImpl;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;

public class TestPropertyValues {

    private static final Collator collator = BlackLabIndexImpl.defaultCollator();

    private static final Terms terms;

    static {
        // Test using MockTerms with a sorted list of very similar words that should not be equal
        List<String> strTerms = Arrays.asList("a", "aa", "ap", "p", "aap", "a ap", "aa p", " aap", "aap ", "a a p", "-aap", "a-ap", "aap-");
        strTerms.sort(collator); // MockTerms requires that terms are already sorted
        terms = new MockTerms(strTerms.toArray(new String[0]));
    }

    @Test
    public void testPropertyValueContextWords() {
        MatchSensitivity sensitivity = MatchSensitivity.INSENSITIVE;
        PropertyValueContextWords twoThreeThree = new PropertyValueContextWords(terms, "lemma", sensitivity, new int[]{2, 3, 3}, false);
        PropertyValueContextWords twoThreeThree2 = new PropertyValueContextWords(terms, "lemma", sensitivity, new int[]{2, 3, 3}, false);
        PropertyValueContextWords oneThreeFour = new PropertyValueContextWords(terms, "lemma", sensitivity, new int[]{1, 3, 4}, false);
        PropertyValueContextWords twoThreeFive = new PropertyValueContextWords(terms, "lemma", sensitivity, new int[]{2, 3, 5}, false);
        Assert.assertEquals(0, twoThreeThree.compareTo(twoThreeThree2));
        Assert.assertEquals(-1, oneThreeFour.compareTo(twoThreeThree));
        Assert.assertEquals(1, twoThreeFive.compareTo(twoThreeThree));
    }

    private static void testPropertyValueSortValue(Supplier<PropertyValue> generator) {
        testPropertyValueSortValue(generator, 1000);
    }

    /**
     * Tests that comparing the sort values yields the same result as direct comparison.
     *
     * Tests a series of random values.
     *
     * @param generator should generate a (repeatably) random PropertyValue of the right type to test
     */
    private static void testPropertyValueSortValue(Supplier<PropertyValue> generator, int tests) {
        for (int i = 0; i < tests; i++) {
            PropertyValue a = generator.get();
            PropertyValue b = generator.get();
            String asv = a.getSortValue();
            String bsv = b.getSortValue();
            int cmpSortValue = collator.compare(asv, bsv);
            int cmpDirect = a.compareTo(b);
            Assert.assertEquals("sort value comparison equals direct comparison: '" + asv + "', '" + bsv + "'", cmpSortValue, cmpDirect);
        }
    }

    @Test
    public void testPropertyValueContextWordSortValue() {
        Random random = new Random(6781);
        testPropertyValueSortValue(() -> {
            int word = random.nextInt(terms.numberOfTerms());
            return new PropertyValueContextWord(terms, "lemma", MatchSensitivity.INSENSITIVE, word);
        });
    }

    @Test
    public void testPropertyValueInt() {
        Random random = new Random(6782);
        testPropertyValueSortValue(() -> new PropertyValueInt(random.nextInt(Integer.MAX_VALUE)));
    }

    @Test
    public void testPropertyValueContextWordsSortValue() {
        Random random = new Random(6783);
        testPropertyValueSortValue(() -> {
            int[] words = new int[] {
                    random.nextInt(terms.numberOfTerms()),
                    random.nextInt(terms.numberOfTerms()),
                    random.nextInt(terms.numberOfTerms())
            };
            return new PropertyValueContextWords(terms, "lemma", MatchSensitivity.INSENSITIVE, words, false);
        }, 100_000);
    }

    @Test
    public void termCollatorVsRegularCollator() {
        //Assert.assertEquals(1, collator.compare("aap", "-aap"));

        // Regular collator ignores all dashes and spaces...
        Assert.assertEquals( 1, collator.compare(" aap | ap | ap", "-aap | a ap | p"));

        // ...but Terms collator does not!
        Collator termsCollator = terms.getCollator(MatchSensitivity.INSENSITIVE);
        Assert.assertEquals(-1, termsCollator.compare(" aap | ap | ap", "-aap | a ap | p"));

    }
}
