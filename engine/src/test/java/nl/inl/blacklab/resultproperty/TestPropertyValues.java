package nl.inl.blacklab.resultproperty;

import org.junit.Assert;
import org.junit.Test;

public class TestPropertyValues {

    @Test
    public void testPropertyValueContextWords() {
        PropertyValueContextWords twoThreeFour = new PropertyValueContextWords(new int[]{2, 3, 3});
        PropertyValueContextWords twoThreeFour2 = new PropertyValueContextWords(new int[]{2, 3, 3});
        PropertyValueContextWords oneThreeFour = new PropertyValueContextWords(new int[]{1, 3, 4});
        PropertyValueContextWords twoThreeFive = new PropertyValueContextWords(new int[]{2, 3, 5});
        Assert.assertEquals(0, twoThreeFour.compareTo(twoThreeFour2));
        Assert.assertEquals(-1, oneThreeFour.compareTo(twoThreeFour));
        Assert.assertEquals(1, twoThreeFive.compareTo(twoThreeFour));
    }
}
