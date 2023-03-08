package nl.inl.blacklab.forwardindex;

import java.util.Arrays;
import java.util.Random;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class TestParallelIntSorter {

    /** Test that arrays of different sizes sort correctly (smaller arrays don't use threading). */
    private static final int[] SIZES_TO_TEST = { 0, 1, 2, 10, 100, 1_000, 9_999, 10_000, 10_001, 20_000, 30_000,
            40_000, 100_000, 1_000_000 };

    @BeforeClass
    public static void setUp() {
        ParallelIntSorter.setSeed(654321); // ensure reproducible pivot points (if we run tests single-threaded...)
    }

    private static int[] pseudoRandomArray(int arraySize) {
        int[] array = new int[arraySize];
        Random random = new Random(123456 + arraySize);
        for (int i = 0; i < array.length; i++) {
            array[i] = random.nextInt();
        }
        return array;
    }

    public static void assertCorrectSort(int arraySize) {
        // Fill array with reproducible random numbers
        int[] array = pseudoRandomArray(arraySize);

        // Determine the correct sort by calling a standard library method
        int[] expected = Arrays.copyOf(array, array.length);
        Arrays.parallelSort(expected);

        // Determine the result from our parallel sorter
        int[] actual = Arrays.copyOf(array, array.length);
        ParallelIntSorter.sort(actual, Integer::compare);

        Assert.assertArrayEquals("Array length " + array.length, expected, actual);
    }

    @Test
    public void testSort() {
        for (int size: SIZES_TO_TEST) {
            assertCorrectSort(size);
        }
    }

}
