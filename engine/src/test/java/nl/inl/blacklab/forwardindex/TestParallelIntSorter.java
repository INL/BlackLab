package nl.inl.blacklab.forwardindex;

import java.util.Arrays;
import java.util.Random;

import org.junit.Assert;
import org.junit.Test;

public class TestParallelIntSorter {

    public static void assertCorrectSort(int[] array) {
        // Determine the correct sort by calling a standard library method
        int[] expected = Arrays.copyOf(array, array.length);
        Arrays.parallelSort(expected);

        // Determine the result from our parallel sorter
        int[] actual = Arrays.copyOf(array, array.length);
        ParallelIntSorter.parallelSort(actual, Integer::compare);

        Assert.assertArrayEquals("Array length " + array.length, expected, actual);
    }

    public static void assertCorrectSort(int arraySize) {
        int[] array = new int[arraySize];
        Random random = new Random(123_456 + arraySize);
        for (int i = 0; i < array.length; i++) {
            array[i] = random.nextInt();
        }
        assertCorrectSort(array);
    }

    @Test
    public void debug() {
        assertCorrectSort(100_000);
    }

    @Test
    public void testSort() {
        assertCorrectSort(0);
        assertCorrectSort(1);
        assertCorrectSort(2);
        assertCorrectSort(1000);
        assertCorrectSort(10_000);
        assertCorrectSort(100_000);
        assertCorrectSort(1_000_000);
    }

}
